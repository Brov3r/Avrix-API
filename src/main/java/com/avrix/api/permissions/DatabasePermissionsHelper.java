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
            // Role permissions table
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

            // Role inheritance parents table
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

            // Role metadata table
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
     * Persists all extended attributes (prefix, suffix, permissions, parents, metadata) of an {@link ExtendedRole}.
     *
     * @param conn the database connection
     * @param role the role being saved
     */
    public static void saveCustomRoleData(Connection conn, Role role) {
        if (conn == null || !(role instanceof ExtendedRole extendedRole) || role.getId() == -1) {
            return;
        }

        int roleId = role.getId();

        try {
            // Update prefix and suffix in role table
            try (PreparedStatement stat = conn.prepareStatement("UPDATE role SET prefix = ?, suffix = ? WHERE id = ?")) {
                stat.setString(1, extendedRole.getPrefix());
                stat.setString(2, extendedRole.getSuffix());
                stat.setInt(3, roleId);
                stat.executeUpdate();
            }

            // Permissions sync
            try (PreparedStatement deleteStat = conn.prepareStatement("DELETE FROM role_permissions WHERE role = ?")) {
                deleteStat.setInt(1, roleId);
                deleteStat.executeUpdate();
            }
            if (!extendedRole.getPermissions().isEmpty()) {
                try (PreparedStatement insertStat = conn.prepareStatement("INSERT INTO role_permissions (role, node) VALUES (?, ?)")) {
                    for (String permission : extendedRole.getPermissions()) {
                        insertStat.setInt(1, roleId);
                        insertStat.setString(2, permission);
                        insertStat.executeUpdate();
                    }
                }
            }

            // Parents sync
            try (PreparedStatement deleteStat = conn.prepareStatement("DELETE FROM role_parents WHERE role = ?")) {
                deleteStat.setInt(1, roleId);
                deleteStat.executeUpdate();
            }
            if (!extendedRole.getParents().isEmpty()) {
                try (PreparedStatement insertStat = conn.prepareStatement("INSERT INTO role_parents (role, parent) VALUES (?, ?)")) {
                    for (String parent : extendedRole.getParents()) {
                        insertStat.setInt(1, roleId);
                        insertStat.setString(2, parent);
                        insertStat.executeUpdate();
                    }
                }
            }

            // Metadata sync
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
        if (conn == null || role == null || role.getId() == -1) {
            return;
        }

        int roleId = role.getId();

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
        if (conn == null || roleId == -1) return;

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