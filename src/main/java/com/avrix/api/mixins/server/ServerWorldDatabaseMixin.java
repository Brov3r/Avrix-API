package com.avrix.api.mixins.server;

import com.avrix.api.permissions.DatabasePermissionsHelper;
import com.avrix.api.permissions.ExtendedRole;
import com.avrix.api.permissions.PermissionsManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.characters.Capability;
import zombie.characters.Role;
import zombie.characters.Roles;
import zombie.debug.DebugType;
import zombie.network.ServerWorldDatabase;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Mixin for {@link ServerWorldDatabase} enabling SQLite persistence and startup loading
 * of all {@link ExtendedRole} and permissions configuration.
 */
@Mixin(ServerWorldDatabase.class)
public class ServerWorldDatabaseMixin {

    /**
     * Prevents instantiation of the mixin class.
     */
    private ServerWorldDatabaseMixin() {
        throw new UnsupportedOperationException("Mixin classes cannot be instantiated");
    }

    /**
     * Initializes ExtendedRole database tables and loads {@code permissions.yml}
     * immediately after {@link ServerWorldDatabase#create()} finishes and the SQLite connection is ready.
     *
     * @param callback injection callback provided by the mixin framework
     */
    @Inject(
            method = "create()V",
            at = @At("TAIL")
    )
    private void injectCreateSchema(CallbackInfo callback) {
        ServerWorldDatabase db = (ServerWorldDatabase) (Object) this;
        try {
            Connection conn = getConnection(db);

            DatabasePermissionsHelper.initDatabaseSchema(conn);
            PermissionsManager.loadDatabasePlayerPermissions(conn);
            PermissionsManager.loadDatabaseTemporaryRoles(conn);
        } catch (Exception e) {
            DebugType.General.error("Failed to initialize ExtendedRole schema: " + e.getMessage());
        }
    }

    /**
     * Persists extended role data after the vanilla role save completes.
     *
     * @param role     saved role
     * @param callback injection callback provided by the mixin framework
     */
    @Inject(
            method = "saveRole(Lzombie/characters/Role;)V",
            at = @At("TAIL")
    )
    private void injectSaveRoleData(Role role, CallbackInfo callback) {
        ServerWorldDatabase db = (ServerWorldDatabase) (Object) this;
        try {
            Connection conn = DatabasePermissionsHelper.getConnection();
            if (conn == null) {
                conn = getConnection(db);
            }
            DatabasePermissionsHelper.saveCustomRoleData(conn, role);
        } catch (Exception e) {
            DebugType.General.error(
                    "Failed to save extended data for role '" + role.getName() + "': " + e.getMessage()
            );
        }
    }

    /**
     * Removes extended role data after the vanilla role removal completes.
     *
     * @param role               removed role
     * @param newRoleInsteadExist role that replaces the removed one
     * @param callback            injection callback provided by the mixin framework
     */
    @Inject(
            method = "removeRole(Lzombie/characters/Role;Lzombie/characters/Role;)V",
            at = @At("TAIL")
    )
    private void injectRemoveRoleData(Role role, Role newRoleInsteadExist, CallbackInfo callback) {
        if (role == null || role.getId() <= 0) {
            return;
        }
        ServerWorldDatabase db = (ServerWorldDatabase) (Object) this;
        try {
            Connection conn = DatabasePermissionsHelper.getConnection();
            if (conn == null) {
                conn = getConnection(db);
            }
            if (!roleExists(conn, role.getId())) {
                DatabasePermissionsHelper.deleteCustomRoleData(conn, role.getId());
            }
        } catch (Exception e) {
            DebugType.General.error("Failed to clean up extended role data: " + e.getMessage());
        }
    }

    /**
     * Replaces loaded vanilla roles with {@link ExtendedRole} instances carrying custom data.
     *
     * @param roles    roles loaded by the vanilla database code
     * @param callback injection callback provided by the mixin framework
     */
    @Inject(
            method = "loadRoles(Ljava/util/ArrayList;)V",
            at = @At("TAIL")
    )
    private void injectLoadExtendedRoles(ArrayList<Role> roles, CallbackInfo callback) {
        if (roles == null || roles.isEmpty()) {
            return;
        }

        ServerWorldDatabase db = (ServerWorldDatabase) (Object) this;
        try {
            Connection gameConnection = getConnection(db);
            Connection conn = DatabasePermissionsHelper.getConnection();
            if (conn == null) {
                conn = gameConnection;
            }

            DatabasePermissionsHelper.initDatabaseSchema(gameConnection);

            for (int i = 0; i < roles.size(); i++) {
                Role standardRole = roles.get(i);
                if (standardRole instanceof ExtendedRole) {
                    continue;
                }

                ExtendedRole extendedRole = new ExtendedRole(
                        standardRole.getName(),
                        standardRole.getDescription(),
                        standardRole.getColor()
                );
                extendedRole.setId(standardRole.getId());
                extendedRole.setPosition(standardRole.getPosition());

                for (Capability capability : standardRole.getCapabilities()) {
                    extendedRole.addCapability(capability);
                }

                if (standardRole.isReadOnly()) {
                    extendedRole.setReadOnly();
                }

                DatabasePermissionsHelper.loadCustomRoleData(conn, extendedRole);
                roles.set(i, extendedRole);
                syncRolesStaticField(standardRole.getName(), extendedRole);
            }
        } catch (Exception e) {
            DebugType.General.error("Failed to inject ExtendedRole load: " + e.getMessage());
        }
    }

    /**
     * Resolves the JDBC connection held by the given game database instance.
     *
     * @param db game database instance
     * @return active JDBC connection
     * @throws ReflectiveOperationException if the connection field cannot be read
     */
    @Unique
    private static Connection getConnection(ServerWorldDatabase db) throws ReflectiveOperationException {
        Field connField = ServerWorldDatabase.class.getDeclaredField("conn");
        connField.setAccessible(true);
        return (Connection) connField.get(db);
    }

    /**
     * Checks whether a role row still exists after the vanilla removal path.
     *
     * @param conn   active database connection
     * @param roleId role primary key
     * @return {@code true} if the role still exists
     * @throws SQLException if the lookup fails
     */
    @Unique
    private static boolean roleExists(Connection conn, int roleId) throws SQLException {
        try (PreparedStatement stat = conn.prepareStatement("SELECT 1 FROM role WHERE id = ?")) {
            stat.setInt(1, roleId);
            try (ResultSet rs = stat.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Updates the static {@link Roles} defaults so they point to the extended role instance.
     *
     * @param roleName role name used to select the matching default
     * @param newRole  extended role replacing the vanilla instance
     */
    @Unique
    private static void syncRolesStaticField(String roleName, ExtendedRole newRole) {
        if (roleName == null) {
            return;
        }
        switch (roleName.toLowerCase(Locale.ROOT)) {
            case "admin" -> updateField("defaultForAdmin", newRole);
            case "user" -> {
                updateField("defaultForUser", newRole);
                updateField("defaultForNewUser", newRole);
            }
            case "moderator" -> updateField("defaultForModerator", newRole);
            case "banned" -> updateField("defaultForBanned", newRole);
            case "priority", "priorityuser" -> updateField("defaultForPriorityUser", newRole);
            case "observer" -> updateField("defaultForObserver", newRole);
            case "gm" -> {
                updateField("defaultForGM", newRole);
                updateField("defaultForOverseer", newRole);
            }
            default -> {
            }
        }
    }

    /**
     * Assigns a role to a static {@link Roles} field.
     *
     * @param fieldName name of the static field
     * @param role      role instance to assign
     */
    @Unique
    private static void updateField(String fieldName, Role role) {
        try {
            Field field = Roles.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(null, role);
        } catch (Exception e) {
            DebugType.General.warn("Failed to synchronize Roles." + fieldName + ": " + e.getMessage());
        }
    }
}
