package com.avrix.api.mixins.client;

import com.avrix.api.ui.AvrixUi;
import org.lwjglx.opengl.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client mixin that drives the LixUI overlay from the game's display lifecycle.
 */
@Mixin(Display.class)
public class DisplayMixin {

    /**
     * Prevents instantiation of the mixin class.
     */
    private DisplayMixin() {
        throw new UnsupportedOperationException("Mixin classes cannot be instantiated");
    }

    /**
     * Advances and draws the overlay after the game has submitted its frame but before the
     * back buffer is swapped.
     *
     * @param processMessages whether the display also polls window messages
     * @param callback        injection callback provided by the mixin framework
     */
    @Inject(
            method = "update(Z)V",
            at = @At("HEAD")
    )
    private static void avrixRenderOverlay(boolean processMessages, CallbackInfo callback) {
        AvrixUi.frame();
    }

    /**
     * Releases overlay resources before the game destroys its GLFW window.
     *
     * @param callback injection callback provided by the mixin framework
     */
    @Inject(
            method = "create()V",
            at = @At("HEAD")
    )
    private static void avrixBeforeDisplayRecreate(CallbackInfo callback) {
        AvrixUi.beforeDisplayRecreate();
    }

    /**
     * Releases overlay resources while the OpenGL context is still current.
     *
     * @param callback injection callback provided by the mixin framework
     */
    @Inject(
            method = "destroy()V",
            at = @At("HEAD")
    )
    private static void avrixShutdownOverlay(CallbackInfo callback) {
        AvrixUi.shutdown();
    }
}
