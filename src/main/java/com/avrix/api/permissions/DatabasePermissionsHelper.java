package com.avrix.api.permissions;

import zombie.ZomboidFileSystem;
import zombie.characters.Role;
import zombie.debug.DebugType;
import zombie.debug.LogSeverity;
import zombie.network.GameServer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Low-level SQLite persistence helper managing ExtendedRole schemas,
 * metadata, inheritance parents, and individual player permissions.
 */
public final class DatabasePermissionsHelper {

    private static final ReentrantLock DATABASE_LOCK = new ReentrantLock();
    private static final AtomicBoolean SHUTDOWN_HOOK_REGISTERED = new AtomicBoolean();
    private static volatile Connection permissionConnection;

    private DatabasePermissionsHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Returns the dedicated connection used for permission tables.
     * <p>
     * The connection is opened lazily against the server database file and uses a busy timeout so short
     * permission writes can wait for the vanilla connection instead of sharing its JDBC object.
     *
     * @return dedicated connection or {@code null} when the server database is not ready
     */
    public static Connection getConnection() {
        DATABASE_LOCK.lock();
        try {
            if (permissionConnection != null && !permissionConnection.isClosed()) {
                return permissionConnection;
            }
            if (!GameServer.server || GameServer.udpEngine == null
                    || GameServer.serverName == null || ZomboidFileSystem.instance == null) {
                return null;
            }

            Path databasePath = Path.of(
                    ZomboidFileSystem.instance.getCacheDir(), "db", GameServer.serverName + ".db");
            if (!Files.isRegularFile(databasePath)) {
                return null;
            }

            Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
            try (Statement stat = connection.createStatement()) {
                stat.execute("PRAGMA busy_timeout = 5000");
            }
            permissionConnection = connection;
            registerShutdownHook();
            return connection;
        } catch (Exception e) {
            DebugType.Multiplayer.printException(
                    e, "Failed to open the dedicated permissions database connection", LogSeverity.Error);
            return null;
        } finally {
            DATABASE_LOCK.unlock();
        }
    }

    /**
     * Closes the dedicated permissions connection if it is open.
     */
    public static void closeConnection() {
        DATABASE_LOCK.lock();
        try {
            if (permissionConnection != null) {
                try {
                    permissionConnection.close();
                } catch (SQLException e) {
                    DebugType.Multiplayer.printException(
                            e, "Failed to close the dedicated permissions database connection", LogSeverity.Error);
                } finally {
                    permissionConnection = null;
                }
            }
        } finally {
            DATABASE_LOCK.unlock();
        }
    }

    /**
     * Registers a one-time JVM shutdown hook for the dedicated connection.
     */
    private static void registerShutdownHook() {
        if (SHUTDOWN_HOOK_REGISTERED.compareAndSet(false, true)) {
            Runtime.getRuntime().addShutdownHook(new Thread(
                    DatabasePermissionsHelper::closeConnection,
                    "Avrix-Permissions-Database-Shutdown"
            ));
        }
    }

    /**
     * Runs a SQL action while holding the shared permission-database lock.
     *
     * @param errorMessage message used when the action fails
     * @param action       SQL action to execute
     * @return {@code true} if the action completed without SQL errors
     */
    private static boolean executeLocked(String errorMessage, SqlAction action) {
        DATABASE_LOCK.lock();
        try {
            action.run();
            return true;
        } catch (SQLException e) {
            DebugType.Multiplayer.printException(e, errorMessage, LogSeverity.Error);
            return false;
        } finally {
            DATABASE_LOCK.unlock();
        }
    }

    /**
     * SQL action that may throw a checked {@link SQLException}.
     */
    @FunctionalInterface
    private interface SqlAction {
        /**
         * Executes the action.
         *
         * @throws SQLException if the SQL operation fails
         */
        void run() throws SQLException;
    }

    /**
     * Initializes all required database tables and columns if not already present.
     *
     * @param conn the active SQLite database connection
     */
    public static void initDatabaseSchema(Connection conn) {
        if (conn == null) return;

        try (Statement stat = conn.createStatement()) {
            stat.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS role_permissions (
                            role INTEGER NOT NULL,
                            node TEXT NOT NULL
                        )
                    """);
            stat.executeUpdate("""
                        CREATE UNIQUE INDEX IF NOT EXISTS idx_role_permissions_unique 
                        ON role_permissions (role, node)
                    """);

            stat.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS role_parents (
                            role INTEGER NOT NULL,
                            parent TEXT NOT NULL
                        )
                    """);
            stat.executeUpdate("""
                        CREATE UNIQUE INDEX IF NOT EXISTS idx_role_parents_unique 
                        ON role_parents (role, parent)
                    """);

            stat.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS role_metadata (
                            role INTEGER NOT NULL,
                            meta_key TEXT NOT NULL,
                            meta_val TEXT NOT NULL
                        )
                    """);
            stat.executeUpdate("""
                        CREATE UNIQUE INDEX IF NOT EXISTS idx_role_metadata_unique 
                        ON role_metadata (role, meta_key)
                    """);

            // Schema for direct user permissions (both permanent and temporary)
            stat.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS player_permissions (
                            identifier TEXT NOT NULL,
                            node TEXT NOT NULL,
                            expiration INTEGER
                        )
                    """);
            stat.executeUpdate("""
                        CREATE UNIQUE INDEX IF NOT EXISTS idx_player_permissions_unique 
                        ON player_permissions (identifier, node)
                    """);

            stat.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS player_temporary_roles (
                            identifier TEXT NOT NULL PRIMARY KEY,
                            role TEXT NOT NULL,
                            expiration INTEGER NOT NULL,
                            fallback_role TEXT DEFAULT 'user'
                        )
                    """);

            // Normalize legacy mixed-case identifiers/nodes before adding case-insensitive logic.
            stat.executeUpdate("""
                        DELETE FROM player_permissions
                        WHERE rowid NOT IN (
                            SELECT MIN(rowid) FROM player_permissions
                            GROUP BY LOWER(identifier), LOWER(node)
                        )
                    """);
            stat.executeUpdate("""
                        UPDATE player_permissions
                        SET identifier = LOWER(identifier), node = LOWER(node)
                    """);
            stat.executeUpdate("""
                        DELETE FROM player_temporary_roles
                        WHERE rowid NOT IN (
                            SELECT MAX(rowid) FROM player_temporary_roles
                            GROUP BY LOWER(identifier)
                        )
                    """);
            stat.executeUpdate("""
                        UPDATE player_temporary_roles
                        SET identifier = LOWER(identifier)
                    """);
            stat.executeUpdate("""
                        DELETE FROM role_permissions
                        WHERE rowid NOT IN (
                            SELECT MIN(rowid) FROM role_permissions
                            GROUP BY role, LOWER(node)
                        )
                    """);
            stat.executeUpdate("""
                        UPDATE role_permissions SET node = LOWER(node)
                    """);

            // Safely add prefix and suffix columns to base role table if absent
            DatabaseMetaData md = conn.getMetaData();
            try (ResultSet rs = md.getColumns(null, null, "role", "prefix")) {
                if (!rs.next()) {
                    stat.executeUpdate("ALTER TABLE role ADD COLUMN prefix TEXT DEFAULT ''");
                }
            }
            try (ResultSet rs = md.getColumns(null, null, "role", "suffix")) {
                if (!rs.next()) {
                    stat.executeUpdate("ALTER TABLE role ADD COLUMN suffix TEXT DEFAULT ''");
                }
            }
        } catch (SQLException e) {
            DebugType.Multiplayer.printException(e, "Failed to initialize ExtendedRole database schema", LogSeverity.Error);
        }
    }

    /**
     * Persists or updates a temporary role assignment in SQLite.
     *
     * @param conn         database connection
     * @param identifier   normalized username or SteamID64
     * @param role         temporary role name
     * @param expiration   expiration instant
     * @param fallbackRole role restored after expiration
     * @return {@code true} if the record was saved
     */
    public static boolean saveTemporaryRole(Connection conn, String identifier, String role, Instant expiration, String fallbackRole) {
        if (conn == null || identifier == null || role == null || expiration == null) return false;

        String sql = """
                INSERT INTO player_temporary_roles (identifier, role, expiration, fallback_role)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(identifier) DO UPDATE SET 
                    role = excluded.role,
                    expiration = excluded.expiration,
                    fallback_role = excluded.fallback_role
                """;

        String normalizedIdentifier = PermissionsManager.normalizeIdentifier(identifier);
        return executeLocked("Failed to persist temporary role for user: " + identifier, () -> {
            try (PreparedStatement stat = conn.prepareStatement(sql)) {
                stat.setString(1, normalizedIdentifier);
                stat.setString(2, role.strip());
                stat.setLong(3, expiration.toEpochMilli());
                stat.setString(4, fallbackRole != null ? fallbackRole.strip() : "user");
                stat.executeUpdate();
            }
        });
    }

    /**
     * Deletes a temporary role record when removed or reverted.
     *
     * @param conn       database connection
     * @param identifier normalized username or SteamID64
     * @return {@code true} if the record was deleted
     */
    public static boolean deleteTemporaryRole(Connection conn, String identifier) {
        if (conn == null || identifier == null) return false;
        return executeLocked("Failed to delete temporary role for user: " + identifier, () -> {
            try (PreparedStatement stat = conn.prepareStatement(
                    "DELETE FROM player_temporary_roles WHERE identifier = ?")) {
                stat.setString(1, PermissionsManager.normalizeIdentifier(identifier));
                stat.executeUpdate();
            }
        });
    }

    /**
     * Synchronizes temporary roles on server boot: reverts expired roles in the whitelist and loads active ones into memory.
     * <p>
     * Expired records are only deleted after the fallback role was successfully written. If the fallback role is
     * unavailable, the record is kept so a later expiry pass can retry the revert.
     *
     * @param conn      active SQLite connection
     * @param targetMap destination in-memory temporary role map
     */
    public static void loadAllTemporaryRoles(Connection conn, Map<String, TemporaryRoleEntry> targetMap) {
        if (conn == null || targetMap == null) return;

        DATABASE_LOCK.lock();
        try {
            long now = System.currentTimeMillis();
            List<TemporaryRoleRow> rows = new ArrayList<>();

            try (Statement stat = conn.createStatement();
             ResultSet rs = stat.executeQuery(
                     "SELECT identifier, role, expiration, fallback_role FROM player_temporary_roles")) {
            while (rs.next()) {
                String identifier = PermissionsManager.normalizeIdentifier(rs.getString("identifier"));
                String role = rs.getString("role");
                Instant expiration = Instant.ofEpochMilli(rs.getLong("expiration"));
                String fallback = rs.getString("fallback_role");
                if (!identifier.isEmpty() && role != null && !role.isBlank() && expiration != null) {
                    rows.add(new TemporaryRoleRow(identifier, role, expiration, fallback));
                }
            }
        } catch (SQLException e) {
            DebugType.Multiplayer.printException(e, "Failed to read temporary roles", LogSeverity.Error);
            return;
        }

        for (TemporaryRoleRow row : rows) {
            if (row.expiration().toEpochMilli() > now) {
                targetMap.put(row.identifier(), new TemporaryRoleEntry(
                        row.roleName(), row.expiration(), row.fallbackRole()
                ));
                continue;
            }

                if (revertExpiredTemporaryRole(conn, row)) {
                    deleteTemporaryRole(conn, row.identifier());
                } else {
                    DebugType.Multiplayer.warn(
                            "Temporary role '%s' for user '%s' expired but fallback role '%s' could not be applied",
                            row.roleName(), row.identifier(), row.fallbackRole()
                    );
                }
            }
        } finally {
            DATABASE_LOCK.unlock();
        }
    }

    /**
     * Reverts an expired temporary role in the whitelist for the current world.
     *
     * @param conn active SQLite connection
     * @param row  expired temporary role row
     * @return {@code true} if the fallback was applied or the user is not present in the whitelist
     */
    private static boolean revertExpiredTemporaryRole(Connection conn, TemporaryRoleRow row) {
        String fallback = row.fallbackRole() != null && !row.fallbackRole().isBlank()
                ? row.fallbackRole().strip()
                : "user";

        int fallbackRoleId;
        try (PreparedStatement stat = conn.prepareStatement(
                "SELECT id FROM role WHERE name = ? COLLATE NOCASE")) {
            stat.setString(1, fallback);
            try (ResultSet rs = stat.executeQuery()) {
                if (!rs.next()) {
                    return false;
                }
                fallbackRoleId = rs.getInt("id");
            }
        } catch (SQLException e) {
            DebugType.Multiplayer.printException(
                    e, "Failed to resolve fallback role '" + fallback + "'", LogSeverity.Error);
            return false;
        }

        try (PreparedStatement stat = conn.prepareStatement("""
                UPDATE whitelist
                SET role = ?
                WHERE username = ? COLLATE NOCASE OR steamid = ? COLLATE NOCASE
                """)) {
            stat.setInt(1, fallbackRoleId);
            stat.setString(2, row.identifier());
            stat.setString(3, row.identifier());
            int updated = stat.executeUpdate();
            if (updated == 0) {
                DebugType.Multiplayer.warn(
                        "Temporary role entry for user '%s' has no matching whitelist account",
                        row.identifier()
                );
            }
            return true;
        } catch (SQLException e) {
            DebugType.Multiplayer.printException(
                    e, "Failed to revert expired temporary role for user '" + row.identifier() + "'", LogSeverity.Error);
            return false;
        }
    }

    /**
     * Immutable row loaded from {@code player_temporary_roles}.
     *
     * @param identifier temporary role identifier
     * @param roleName   assigned role name
     * @param expiration expiration instant
     * @param fallbackRole fallback role name
     */
    private record TemporaryRoleRow(
            String identifier,
            String roleName,
            Instant expiration,
            String fallbackRole
    ) {
    }

    /**
     * Persists a direct player permission into SQLite.
     *
     * @param conn       database connection
     * @param identifier normalized username or SteamID64
     * @param node       permission node
     * @param expiration expiration instant or null for permanent
     * @return {@code true} if the permission was saved
     */
    public static boolean savePlayerPermission(Connection conn, String identifier, String node, Instant expiration) {
        if (conn == null || identifier == null || identifier.isBlank() || node == null || node.isBlank()) {
            return false;
        }

        String sql = """
                INSERT INTO player_permissions (identifier, node, expiration)
                VALUES (?, ?, ?)
                ON CONFLICT(identifier, node) DO UPDATE SET expiration = excluded.expiration
                """;

        return executeLocked("Failed to persist player permission for user: " + identifier, () -> {
            try (PreparedStatement stat = conn.prepareStatement(sql)) {
                stat.setString(1, PermissionsManager.normalizeIdentifier(identifier));
                stat.setString(2, node.strip().toLowerCase(Locale.ROOT));
                if (expiration != null) {
                    stat.setLong(3, expiration.toEpochMilli());
                } else {
                    stat.setNull(3, Types.BIGINT);
                }
                stat.executeUpdate();
            }
        });
    }

    /**
     * Deletes a direct player permission from SQLite.
     *
     * @param conn       database connection
     * @param identifier normalized username or SteamID64
     * @param node       permission node
     * @return {@code true} if the permission was deleted
     */
    public static boolean deletePlayerPermission(Connection conn, String identifier, String node) {
        if (conn == null || identifier == null || identifier.isBlank() || node == null || node.isBlank()) {
            return false;
        }

        return executeLocked("Failed to delete player permission for user: " + identifier, () -> {
            try (PreparedStatement stat = conn.prepareStatement(
                    "DELETE FROM player_permissions WHERE identifier = ? AND node = ?")) {
                stat.setString(1, PermissionsManager.normalizeIdentifier(identifier));
                stat.setString(2, node.strip().toLowerCase(Locale.ROOT));
                stat.executeUpdate();
            }
        });
    }

    /**
     * Deletes every direct permission row assigned to the given identity.
     *
     * @param conn       database connection
     * @param identifier normalized username or SteamID64
     * @return {@code true} if the delete completed without SQL errors
     */
    public static boolean deleteAllPlayerPermissions(Connection conn, String identifier) {
        if (conn == null || identifier == null || identifier.isBlank()) {
            return false;
        }

        return executeLocked("Failed to delete all player permissions for user: " + identifier, () -> {
            try (PreparedStatement stat = conn.prepareStatement(
                    "DELETE FROM player_permissions WHERE identifier = ?")) {
                stat.setString(1, PermissionsManager.normalizeIdentifier(identifier));
                stat.executeUpdate();
            }
        });
    }

    /**
     * Resolves a canonical username from a username or SteamID64.
     *
     * @param conn       active SQLite connection
     * @param identifier username or SteamID64
     * @param world      target world
     * @return resolved username or {@code null}
     */
    public static String findUsernameByIdentifier(Connection conn, String identifier, String world) {
        if (conn == null || identifier == null || identifier.isBlank()) {
            return null;
        }
        DATABASE_LOCK.lock();
        try (PreparedStatement stat = conn.prepareStatement("""
                SELECT username FROM whitelist
                WHERE world = ?
                  AND (username = ? COLLATE NOCASE OR steamid = ? COLLATE NOCASE)
                LIMIT 1
                """)) {
            stat.setString(1, world);
            stat.setString(2, identifier);
            stat.setString(3, identifier);
            try (ResultSet rs = stat.executeQuery()) {
                return rs.next() ? rs.getString("username") : null;
            }
        } catch (SQLException e) {
            DebugType.Multiplayer.printException(
                    e, "Failed to resolve username for identifier: " + identifier, LogSeverity.Error);
            return null;
        } finally {
            DATABASE_LOCK.unlock();
        }
    }

    /**
     * Loads all active player permissions from SQLite into memory on server boot.
     * Expired records are automatically cleaned up.
     *
     * @param conn      database connection
     * @param targetMap destination memory cache in PermissionsManager
     */
    public static void loadAllPlayerPermissions(Connection conn, Map<String, Map<String, PlayerPermission>> targetMap) {
        if (conn == null || targetMap == null) {
            return;
        }

        DATABASE_LOCK.lock();
        try {
            long now = System.currentTimeMillis();

            // Delete already expired permissions from DB
            try (PreparedStatement delStat = conn.prepareStatement(
                    "DELETE FROM player_permissions WHERE expiration IS NOT NULL AND expiration <= ?")) {
                delStat.setLong(1, now);
                int purged = delStat.executeUpdate();
                if (purged > 0) {
                    DebugType.Multiplayer.trace("Purged %d expired player permissions from database.", purged);
                }
            } catch (SQLException e) {
                DebugType.Multiplayer.printException(
                        e, "Failed to clean expired player permissions", LogSeverity.Error);
            }

            // Load remaining active permissions
            try (Statement stat = conn.createStatement();
                 ResultSet rs = stat.executeQuery(
                         "SELECT identifier, node, expiration FROM player_permissions")) {
                while (rs.next()) {
                    String identifier = PermissionsManager.normalizeIdentifier(rs.getString("identifier"));
                    String node = rs.getString("node");
                    long expMillis = rs.getLong("expiration");
                    boolean wasNull = rs.wasNull();

                    Instant expiration = wasNull ? null : Instant.ofEpochMilli(expMillis);
                    PlayerPermission perm = new PlayerPermission(node, expiration);

                    targetMap.computeIfAbsent(identifier, _ -> new ConcurrentHashMap<>()).put(node, perm);
                }
            } catch (SQLException e) {
                DebugType.Multiplayer.printException(
                        e, "Failed to load player permissions from database", LogSeverity.Error);
            }
        } finally {
            DATABASE_LOCK.unlock();
        }
    }

    /**
     * Resolves the primary key ID of a role from SQLite by its unique name.
     *
     * @param conn the database connection
     * @param role the role to resolve ID for
     * @return the resolved role ID from DB, or -1 if not found
     */
    private static int resolveRoleId(Connection conn, Role role) {
        if (role == null || role.getName() == null) return -1;
        if (role.getId() > 0) return role.getId();

        DATABASE_LOCK.lock();
        try (PreparedStatement stat = conn.prepareStatement("SELECT id FROM role WHERE name = ? COLLATE NOCASE")) {
            stat.setString(1, role.getName().strip());
            try (ResultSet rs = stat.executeQuery()) {
                if (rs.next()) {
                    int dbId = rs.getInt("id");
                    role.setId(dbId);
                    return dbId;
                }
            }
        } catch (SQLException e) {
            DebugType.Multiplayer.printException(e, "Failed to resolve role ID for: " + role.getName(), LogSeverity.Error);
        } finally {
            DATABASE_LOCK.unlock();
        }
        return -1;
    }

    /**
     * Persists all extended attributes (prefix, suffix, permissions, parents, metadata) of an {@link ExtendedRole}.
     *
     * @param conn the database connection
     * @param role the role being saved
     * @return {@code true} if the extended role data was saved
     */
    public static boolean saveCustomRoleData(Connection conn, Role role) {
        if (conn == null || !(role instanceof ExtendedRole extendedRole)) {
            return false;
        }

        int roleId = resolveRoleId(conn, role);
        if (roleId <= 0) {
            return false;
        }

        DATABASE_LOCK.lock();
        try {
            executeStatement(conn, "SAVEPOINT avrix_role_data");
            try {
                saveCustomRoleDataInternal(conn, role, extendedRole, roleId);
                executeStatement(conn, "RELEASE SAVEPOINT avrix_role_data");
                return true;
            } catch (SQLException e) {
                rollbackSavepoint(conn, "avrix_role_data", e);
                DebugType.Multiplayer.printException(
                        e, "Failed to persist extended data for role: " + role.getName(), LogSeverity.Error);
                return false;
            }
        } catch (SQLException e) {
            DebugType.Multiplayer.printException(
                    e, "Failed to start role data transaction for: " + role.getName(), LogSeverity.Error);
            return false;
        } finally {
            DATABASE_LOCK.unlock();
        }
    }

    /**
     * Writes role metadata inside an open savepoint.
     *
     * @param conn         active connection
     * @param role         role being saved
     * @param extendedRole extended role view
     * @param roleId       resolved role ID
     * @throws SQLException if any write fails
     */
    private static void saveCustomRoleDataInternal(
            Connection conn,
            Role role,
            ExtendedRole extendedRole,
            int roleId
    ) throws SQLException {
        // Force update base role table (bypassing PZ's 'readonly = false' lock)
        try (PreparedStatement stat = conn.prepareStatement(
                "UPDATE role SET description = ?, colorR = ?, colorG = ?, colorB = ?, prefix = ?, suffix = ? WHERE id = ?")) {
            stat.setString(1, role.getDescription() != null ? role.getDescription() : "");
            stat.setFloat(2, role.getColor() != null ? role.getColor().r : 1.0f);
            stat.setFloat(3, role.getColor() != null ? role.getColor().g : 1.0f);
            stat.setFloat(4, role.getColor() != null ? role.getColor().b : 1.0f);
            stat.setString(5, extendedRole.getPrefix() != null ? extendedRole.getPrefix() : "");
            stat.setString(6, extendedRole.getSuffix() != null ? extendedRole.getSuffix() : "");
            stat.setInt(7, roleId);
            stat.executeUpdate();
        }

        // Synchronize permissions
        try (PreparedStatement deleteStat = conn.prepareStatement("DELETE FROM role_permissions WHERE role = ?")) {
            deleteStat.setInt(1, roleId);
            deleteStat.executeUpdate();
        }
        if (!extendedRole.getPermissions().isEmpty()) {
            try (PreparedStatement insertStat = conn.prepareStatement(
                    "INSERT INTO role_permissions (role, node) VALUES (?, ?)")) {
                for (String permission : extendedRole.getPermissions()) {
                    if (permission != null && !permission.isBlank()) {
                        insertStat.setInt(1, roleId);
                        insertStat.setString(2, permission.strip());
                        insertStat.executeUpdate();
                    }
                }
            }
        }

        // Synchronize parents
        try (PreparedStatement deleteStat = conn.prepareStatement("DELETE FROM role_parents WHERE role = ?")) {
            deleteStat.setInt(1, roleId);
            deleteStat.executeUpdate();
        }
        if (!extendedRole.getParents().isEmpty()) {
            try (PreparedStatement insertStat = conn.prepareStatement(
                    "INSERT INTO role_parents (role, parent) VALUES (?, ?)")) {
                for (String parent : extendedRole.getParents()) {
                    if (parent != null && !parent.isBlank()) {
                        insertStat.setInt(1, roleId);
                        insertStat.setString(2, parent.strip());
                        insertStat.executeUpdate();
                    }
                }
            }
        }

        // Synchronize metadata
        try (PreparedStatement deleteStat = conn.prepareStatement("DELETE FROM role_metadata WHERE role = ?")) {
            deleteStat.setInt(1, roleId);
            deleteStat.executeUpdate();
        }
        if (!extendedRole.getMetadata().isEmpty()) {
            try (PreparedStatement insertStat = conn.prepareStatement(
                    "INSERT INTO role_metadata (role, meta_key, meta_val) VALUES (?, ?, ?)")) {
                for (Map.Entry<String, String> entry : extendedRole.getMetadata().entrySet()) {
                    insertStat.setInt(1, roleId);
                    insertStat.setString(2, entry.getKey());
                    insertStat.setString(3, entry.getValue());
                    insertStat.executeUpdate();
                }
            }
        }
    }

    /**
     * Executes a raw SQL statement.
     *
     * @param conn active connection
     * @param sql  SQL statement
     * @throws SQLException if execution fails
     */
    private static void executeStatement(Connection conn, String sql) throws SQLException {
        try (Statement stat = conn.createStatement()) {
            stat.execute(sql);
        }
    }

    /**
     * Rolls back a named savepoint and releases it.
     *
     * @param conn  active connection
     * @param name  savepoint name
     * @param cause original failure
     */
    private static void rollbackSavepoint(Connection conn, String name, SQLException cause) {
        try (Statement stat = conn.createStatement()) {
            stat.execute("ROLLBACK TO SAVEPOINT " + name);
            stat.execute("RELEASE SAVEPOINT " + name);
        } catch (SQLException rollbackEx) {
            cause.addSuppressed(rollbackEx);
        }
    }

    /**
     * Loads all extended attributes from SQLite tables into the specified {@link ExtendedRole}.
     *
     * @param conn the database connection
     * @param role the extended role instance
     */
    public static void loadCustomRoleData(Connection conn, ExtendedRole role) {
        if (conn == null || role == null) {
            return;
        }

        DATABASE_LOCK.lock();
        try {
            int roleId = resolveRoleId(conn, role);
            if (roleId <= 0) {
                return;
            }

            role.clearPermissions();
            role.clearParents();
            role.clearMetadata();

            // Load prefix and suffix
            try (PreparedStatement stat = conn.prepareStatement("SELECT prefix, suffix FROM role WHERE id = ?")) {
                stat.setInt(1, roleId);
                try (ResultSet rs = stat.executeQuery()) {
                    if (rs.next()) {
                        String prefix = rs.getString("prefix");
                        String suffix = rs.getString("suffix");
                        if (prefix != null) role.setPrefix(prefix);
                        if (suffix != null) role.setSuffix(suffix);
                    }
                }
            }

            // Load permissions
            try (PreparedStatement stat = conn.prepareStatement("SELECT node FROM role_permissions WHERE role = ?")) {
                stat.setInt(1, roleId);
                try (ResultSet rs = stat.executeQuery()) {
                    while (rs.next()) {
                        String node = rs.getString("node");
                        if (node != null && !node.isBlank()) {
                            role.addPermission(node);
                        }
                    }
                }
            }

            // Load parents
            try (PreparedStatement stat = conn.prepareStatement("SELECT parent FROM role_parents WHERE role = ?")) {
                stat.setInt(1, roleId);
                try (ResultSet rs = stat.executeQuery()) {
                    while (rs.next()) {
                        String parent = rs.getString("parent");
                        if (parent != null && !parent.isBlank()) {
                            role.addParent(parent);
                        }
                    }
                }
            }

            // Load metadata
            try (PreparedStatement stat = conn.prepareStatement("SELECT meta_key, meta_val FROM role_metadata WHERE role = ?")) {
                stat.setInt(1, roleId);
                try (ResultSet rs = stat.executeQuery()) {
                    while (rs.next()) {
                        String key = rs.getString("meta_key");
                        String val = rs.getString("meta_val");
                        if (key != null && val != null) {
                            role.setMeta(key, val);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            DebugType.Multiplayer.printException(e, "Failed to load extended data for role: " + role.getName(), LogSeverity.Error);
        } finally {
            DATABASE_LOCK.unlock();
        }
    }

    /**
     * Removes all extended records associated with a deleted role.
     *
     * @param conn   the database connection
     * @param roleId the ID of the deleted role
     * @return {@code true} if the extended role data was deleted
     */
    public static boolean deleteCustomRoleData(Connection conn, int roleId) {
        if (conn == null || roleId <= 0) return false;

        DATABASE_LOCK.lock();
        try {
            executeStatement(conn, "SAVEPOINT avrix_role_delete");
            try {
                try (PreparedStatement stat = conn.prepareStatement("DELETE FROM role_permissions WHERE role = ?")) {
                    stat.setInt(1, roleId);
                    stat.executeUpdate();
                }
                try (PreparedStatement stat = conn.prepareStatement("DELETE FROM role_parents WHERE role = ?")) {
                    stat.setInt(1, roleId);
                    stat.executeUpdate();
                }
                try (PreparedStatement stat = conn.prepareStatement("DELETE FROM role_metadata WHERE role = ?")) {
                    stat.setInt(1, roleId);
                    stat.executeUpdate();
                }
                executeStatement(conn, "RELEASE SAVEPOINT avrix_role_delete");
                return true;
            } catch (SQLException e) {
                rollbackSavepoint(conn, "avrix_role_delete", e);
                DebugType.Multiplayer.printException(
                        e, "Failed to delete extended data for role ID: " + roleId, LogSeverity.Error);
                return false;
            }
        } catch (SQLException e) {
            DebugType.Multiplayer.printException(
                    e, "Failed to start role data deletion for ID: " + roleId, LogSeverity.Error);
            return false;
        } finally {
            DATABASE_LOCK.unlock();
        }
    }
}