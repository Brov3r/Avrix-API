package com.avrix.api.mixins.server;

import com.avrix.api.permissions.PermissionsManager;
import net.lenni0451.classtransform.annotations.CTarget;
import net.lenni0451.classtransform.annotations.CTransformer;
import net.lenni0451.classtransform.annotations.injection.CInject;
import zombie.characters.Roles;

/**
 * Bytecode transformer mixin for Project Zomboid's native {@link Roles} subsystem.
 */
@CTransformer(value = Roles.class)
public class RolesMixin {

    /**
     * Prevents instantiation of the transformer class.
     */
    private RolesMixin() {
        throw new UnsupportedOperationException("Mixin classes cannot be instantiated");
    }

    /**
     * Injected at the exit point ({@code TAIL}) of {@link Roles#init()}.
     */
    @CInject(
            method = "init()V",
            target = @CTarget("TAIL")
    )
    private static void injectAfterRolesInit() {
        PermissionsManager.loadStartupPermissionsConfig();
    }
}