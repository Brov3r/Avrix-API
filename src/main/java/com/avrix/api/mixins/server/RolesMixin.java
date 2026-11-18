package com.avrix.api.mixins.server;

import com.avrix.api.permissions.PermissionsManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.characters.Roles;

/**
 * Mixin for Project Zomboid's native {@link Roles} subsystem.
 */
@Mixin(Roles.class)
public class RolesMixin {

    /**
     * Prevents instantiation of the mixin class.
     */
    private RolesMixin() {
        throw new UnsupportedOperationException("Mixin classes cannot be instantiated");
    }

    /**
     * Loads the startup permissions configuration after {@link Roles#init()} completes.
     *
     * @param callback injection callback provided by the mixin framework
     */
    @Inject(
            method = "init()V",
            at = @At("TAIL")
    )
    private static void injectAfterRolesInit(CallbackInfo callback) {
        PermissionsManager.loadStartupPermissionsConfig();
    }
}
