package com.avrix.api.permissions;

import zombie.characters.Role;
import zombie.debug.DebugType;
import zombie.debug.LogSeverity;

import java.sql.*;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Low-level SQLite persistence helper managing ExtendedRole schemas,
 * metadata, inheritance parents, and individual player permissions.
 */
public final class DatabasePermissionsHelper {

    private DatabasePermissionsHelper() {
        throw new UnsupportedOperationException("Utility class");
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
     */
    public static void saveTemporaryRole(Connection conn, String identifier, String role, Instant expiration, String fallbackRole) {
        if (conn == null || identifier == null || role == null || expiration == null) return;

        String sql = """
                INSERT INTO player_temporary_roles (identifier, role, expiration, fallback_role)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(identifier) DO UPDATE SET 
                    role = excluded.role,
                    expiration = excluded.expiration,
                    fallback_role = excluded.fallback_role
                """;

        try (PreparedStatement stat = conn.prepareStatement(sql)) {
            stat.setString(1, identifier.strip());
            stat.setString(2, role.strip());
            stat.setLong(3, expiration.toEpochMilli());
            stat.setString(4, fallbackRole != null ? fallbackRole.strip() : "user");
            stat.executeUpdate();
        } catch (SQLException e) {
            DebugType.Multiplayer.printException(e, "Failed to persist temporary role for user: " + identifier, LogSeverity.Error);
        }
    }

    /**
     * Deletes a temporary role record when removed or reverted.
     */
    public static void deleteTemporaryRole(Connection conn, String identifier) {
        if (conn == null || identifier == null) return;
        try (PreparedStatement stat = conn.prepareStatement("DELETE FROM player_temporary_roles WHERE identifier = ?")) {
            stat.setString(1, identifier.strip());
            stat.executeUpdate();
        } catch (SQLException e) {
            DebugType.Multiplayer.printException(e, "Failed to delete temporary role for user: " + identifier, LogSeverity.Error);
        }
    }

    /**
     * Synchronizes temporary roles on server boot: reverts expired roles in DB and loads active ones into memory.
     */
    public static void loadAllTemporaryRoles(Connection conn, Map<String, TemporaryRoleEntry> targetMap) {
        if (conn == null || targetMap == null) return;

        long now = System.currentTimeMillis();

        // Revert expired roles in SQLite 'users' table and purge temporary records
        String purgeSql = """
                UPDATE users SET role = (
                    SELECT fallback_role FROM player_temporary_roles 
                    WHERE player_temporary_roles.identifier = users.username COLLATE NOCASE
                )
                WHERE username IN (
                    SELECT identifier FROM player_temporary_roles WHERE expiration <= ?
                )
                """;
        try (PreparedStatement stat = conn.prepareStatement(purgeSql)) {
            stat.setLong(1, now);
            stat.executeUpdate();
        } catch (SQLException e) {
            DebugType.Multiplayer.printException(e, "Failed to revert expired roles in users table", LogSeverity.Error);
        }

        try (PreparedStatement delStat = conn.prepareStatement("DELETE FROM player_temporary_roles WHERE expiration <= ?")) {
            delStat.setLong(1, now);
            delStat.executeUpdate();
        } catch (SQLException e) {
            DebugType.Multiplayer.printException(e, "Failed to purge expired temporary roles", LogSeverity.Error);
        }

        // Load active temporary roles into memory
        try (Statement stat = conn.createStatement();
             ResultSet rs = stat.executeQuery("SELECT identifier, role, expiration, fallback_role FROM player_temporary_roles")) {
            while (rs.next()) {
                String id = rs.getString("identifier");
                String role = rs.getString("role");
                Instant exp = Instant.ofEpochMilli(rs.getLong("expiration"));
                String fallback = rs.getString("fallback_role");

                targetMap.put(id, new TemporaryRoleEntry(role, exp, fallback));
            }
        } catch (SQLException e) {
            DebugType.Multiplayer.printException(e, "Failed to load active temporary roles", LogSeverity.Error);
        }
    }

    /**
     * Persists a direct player permission into SQLite.
     *
     * @param conn       database connection
     * @param identifier normalized username or SteamID64
     * @param node       permission node
     * @param expiration expiration instant or null for permanent
     */
    public static void savePlayerPermission(Connection conn, String identifier, String node, Instant expiration) {
        if (conn == null || identifier == null || identifier.isBlank() || node == null || node.isBlank()) {
            return;
        }

        String sql = """
                INSERT INTO player_permissions (identifier, node, expiration)
                VALUES (?, ?, ?)
                ON CONFLICT(identifier, node) DO UPDATE SET expiration = excluded.expiration
                """;

        try (PreparedStatement stat = conn.prepareStatement(sql)) {
            stat.setString(1, identifier.strip());
            stat.setString(2, node.strip().toLowerCase(Locale.ROOT));
            if (expiration != null) {
                stat.setLong(3, expiration.toEpochMilli());
            } else {
                stat.setNull(3, Types.BIGINT);
            }
            stat.executeUpdate();
        } catch (SQLException e) {
            DebugType.Multiplayer.printException(e, "Failed to persist player permission for user: " + identifier, LogSeverity.Error);
        }
    }

    /**
     * Deletes a direct player permission from SQLite.
     *
     * @param conn       database connection
     * @param identifier normalized username or SteamID64
     * @param node       permission node
     */
    public static void deletePlayerPermission(Connection conn, String identifier, String node) {
        if (conn == null || identifier == null || identifier.isBlank() || node == null || node.isBlank()) {
            return;
        }

        try (PreparedStatement stat = conn.prepareStatement("DELETE FROM player_permissions WHERE identifier = ? AND node = ?")) {
            stat.setString(1, identifier.strip());
            stat.setString(2, node.strip().toLowerCase(Locale.ROOT));
            stat.executeUpdate();
        } catch (SQLException e) {
            DebugType.Multiplayer.printException(e, "Failed to delete player permission for user: " + identifier, LogSeverity.Error);
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

        long now = System.currentTimeMillis();

        //  Delete already expired permissions from DB
        try (PreparedStatement delStat = conn.prepareStatement("DELETE FROM player_permissions WHERE expiration IS NOT NULL AND expiration <= ?")) {
            delStat.setLong(1, now);
            int purged = delStat.executeUpdate();
            if (purged > 0) {
                DebugType.Multiplayer.trace("Purged %d expired player permissions from database.", purged);
            }
        } catch (SQLException e) {
            DebugType.Multiplayer.printException(e, "Failed to clean expired player permissions", LogSeverity.Error);
        }

        // Load remaining active permissions
        try (Statement stat = conn.createStatement();
             ResultSet rs = stat.executeQuery("SELECT identifier, node, expiration FROM player_permissions")) {

            while (rs.next()) {
                String identifier = rs.getString("identifier");
                String node = rs.getString("node");
                long expMillis = rs.getLong("expiration");
                boolean wasNull = rs.wasNull();

                Instant expiration = wasNull ? null : Instant.ofEpochMilli(expMillis);
                PlayerPermission perm = new PlayerPermission(node, expiration);

                targetMap.computeIfAbsent(identifier, _ -> new ConcurrentHashMap<>()).put(node, perm);
            }
        } catch (SQLException e) {
            DebugType.Multiplayer.printException(e, "Failed to load player permissions from database", LogSeverity.Error);
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
        }
        return -1;
    }

    /**
     * Persists all extended attributes (prefix, suffix, permissions, parents, metadata) of an {@link ExtendedRole}.
     *
     * @param conn the database connection
     * @param role the role being saved
     */
    public static void saveCustomRoleData(Connection conn, Role role) {
        if (conn == null || !(role instanceof ExtendedRole extendedRole)) {
            return;
        }

        int roleId = resolveRoleId(conn, role);
        if (roleId <= 0) {
            return;
        }

        try {
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
                try (PreparedStatement insertStat = conn.prepareStatement("INSERT INTO role_permissions (role, node) VALUES (?, ?)")) {
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
                try (PreparedStatement insertStat = conn.prepareStatement("INSERT INTO role_parents (role, parent) VALUES (?, ?)")) {
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
                try (PreparedStatement insertStat = conn.prepareStatement("INSERT INTO role_metadata (role, meta_key, meta_val) VALUES (?, ?, ?)")) {
                    for (Map.Entry<String, String> entry : extendedRole.getMetadata().entrySet()) {
                        insertStat.setInt(1, roleId);
                        insertStat.setString(2, entry.getKey());
                        insertStat.setString(3, entry.getValue());
                        insertStat.executeUpdate();
                    }
                }
            }
        } catch (SQLException e) {
            DebugType.Multiplayer.printException(e, "Failed to persist extended data for role: " + role.getName(), LogSeverity.Error);
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

        int roleId = resolveRoleId(conn, role);
        if (roleId <= 0) {
            return;
        }

        try {
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
        }
    }

    /**
     * Removes all extended records associated with a deleted role.
     *
     * @param conn   the database connection
     * @param roleId the ID of the deleted role
     */
    public static void deleteCustomRoleData(Connection conn, int roleId) {
        if (conn == null || roleId <= 0) return;

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
        } catch (SQLException e) {
            DebugType.Multiplayer.printException(e, "Failed to delete extended data for role ID: " + roleId, LogSeverity.Error);
        }
    }
}