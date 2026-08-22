package com.avrix.api.permissions;

import zombie.characters.Role;
import zombie.debug.DebugType;
import zombie.debug.LogSeverity;

import java.sql.*;
import java.util.Map;

/**
 * Low-level SQLite persistence helper managing ExtendedRole schemas,
 * metadata, inheritance parents, and permissions.
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