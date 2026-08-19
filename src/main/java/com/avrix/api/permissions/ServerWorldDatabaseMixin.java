package com.avrix.api.permissions.mixins;

import com.avrix.api.permissions.ExtendedRole;
import com.avrix.api.permissions.internal.DatabasePermissionsHelper;
import net.lenni0451.classtransform.annotations.CTarget;
import net.lenni0451.classtransform.annotations.CTransformer;
import net.lenni0451.classtransform.annotations.injection.CInject;
import zombie.characters.Capability;
import zombie.characters.Role;
import zombie.debug.DebugType;
import zombie.network.ServerWorldDatabase;

import java.sql.Connection;
import java.util.ArrayList;

/**
 * ClassTransform mixin for {@link ServerWorldDatabase} enabling comprehensive SQLite persistence
 * of all {@link ExtendedRole} features (permissions, inheritance trees, formatting, and metadata).
 */
@CTransformer(value = ServerWorldDatabase.class)
public class ServerWorldDatabaseMixin {

    /**
     * Injects database schema verification at the end of {@link ServerWorldDatabase#create()}.
     */
    @CInject(
            method = "create()V",
            target = @CTarget("TAIL")
    )
    private void injectCreateSchema() {
        ServerWorldDatabase db = (ServerWorldDatabase) (Object) this;
        try {
            java.lang.reflect.Field connField = ServerWorldDatabase.class.getDeclaredField("conn");
            connField.setAccessible(true);
            Connection conn = (Connection) connField.get(db);
            DatabasePermissionsHelper.initDatabaseSchema(conn);
        } catch (Exception e) {
            DebugType.General.error("Failed to inject ExtendedRole tables: " + e.getMessage());
        }
    }

    /**
     * Injects extended data saving at the end of {@link ServerWorldDatabase#saveRole(Role)}.
     *
     * @param role the role being persisted
     */
    @CInject(
            method = "saveRole(Lzombie/characters/Role;)V",
            target = @CTarget("TAIL")
    )
    private void injectSaveRoleData(Role role) {
        ServerWorldDatabase db = (ServerWorldDatabase) (Object) this;
        try {
            java.lang.reflect.Field connField = ServerWorldDatabase.class.getDeclaredField("conn");
            connField.setAccessible(true);
            Connection conn = (Connection) connField.get(db);
            DatabasePermissionsHelper.saveCustomRoleData(conn, role);
        } catch (Exception e) {
            DebugType.General.error("Failed to save extended data for role '" + role.getName() + "': " + e.getMessage());
        }
    }

    /**
     * Injects clean-up of extended data into {@link ServerWorldDatabase#removeRole(Role, Role)}.
     *
     * @param role                the role being removed
     * @param newRoleInsteadExist the replacement role
     */
    @CInject(
            method = "removeRole(Lzombie/characters/Role;Lzombie/characters/Role;)V",
            target = @CTarget("HEAD")
    )
    private void injectRemoveRoleData(Role role, Role newRoleInsteadExist) {
        if (role == null) return;
        ServerWorldDatabase db = (ServerWorldDatabase) (Object) this;
        try {
            java.lang.reflect.Field connField = ServerWorldDatabase.class.getDeclaredField("conn");
            connField.setAccessible(true);
            Connection conn = (Connection) connField.get(db);
            DatabasePermissionsHelper.deleteCustomRoleData(conn, role.getId());
        } catch (Exception e) {
            DebugType.General.error("Failed to clean up extended role data: " + e.getMessage());
        }
    }

    /**
     * Injects comprehensive ExtendedRole initialization at the end of {@link ServerWorldDatabase#loadRoles(ArrayList)}.
     *
     * @param roles the list of loaded roles
     */
    @CInject(
            method = "loadRoles(Ljava/util/ArrayList;)V",
            target = @CTarget("TAIL")
    )
    private void injectLoadExtendedRoles(ArrayList<Role> roles) {
        if (roles == null || roles.isEmpty()) return;

        ServerWorldDatabase db = (ServerWorldDatabase) (Object) this;
        try {
            java.lang.reflect.Field connField = ServerWorldDatabase.class.getDeclaredField("conn");
            connField.setAccessible(true);
            Connection conn = (Connection) connField.get(db);

            DatabasePermissionsHelper.initDatabaseSchema(conn);

            for (int i = 0; i < roles.size(); i++) {
                Role standardRole = roles.get(i);
                if (standardRole instanceof ExtendedRole) {
                    continue;
                }

                // Construct ExtendedRole copy preserving all standard attributes
                ExtendedRole extendedRole = new ExtendedRole(
                        standardRole.getName(),
                        standardRole.getDescription(),
                        standardRole.getColor()
                );
                extendedRole.setId(standardRole.getId());
                extendedRole.setPosition(standardRole.getPosition());

                for (Capability cap : standardRole.getCapabilities()) {
                    extendedRole.addCapability(cap);
                }

                if (standardRole.isReadOnly()) {
                    extendedRole.setReadOnly();
                }

                // Load all extended state (prefix, suffix, permissions, parents, metadata)
                DatabasePermissionsHelper.loadCustomRoleData(conn, extendedRole);

                // Replace vanilla Role with ExtendedRole
                roles.set(i, extendedRole);
            }
        } catch (Exception e) {
            DebugType.General.error("Failed to inject ExtendedRole load: " + e.getMessage());
        }
    }
}