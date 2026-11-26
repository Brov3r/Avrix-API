package com.avrix.api.mixins.client;

import com.avrix.api.ui.AvrixUi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zombie.input.Mouse;

/**
 * Client mixin that hides raw mouse button states from the game while the pointer is over the
 * LixUI overlay.
 */
@Mixin(Mouse.class)
public class MouseMixin {

    /**
     * Prevents instantiation of the mixin class.
     */
    private MouseMixin() {
        throw new UnsupportedOperationException("Mixin classes cannot be instantiated");
    }

    /**
     * Suppresses held button states while the overlay owns the pointer.
     *
     * @param button   mouse button index
     * @param callback injection callback provided by the mixin framework
     */
    @Inject(
            method = "isButtonDown(I)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void avrixBlockButtonDown(int button, CallbackInfoReturnable<Boolean> callback) {
        avrixBlockMouseState(callback);
    }

    /**
     * Suppresses button press edges while the overlay owns the pointer.
     *
     * @param button   mouse button index
     * @param callback injection callback provided by the mixin framework
     */
    @Inject(
            method = "isButtonPressed(I)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void avrixBlockButtonPressed(int button, CallbackInfoReturnable<Boolean> callback) {
        avrixBlockMouseState(callback);
    }

    /**
     * Suppresses button release edges while the overlay owns the pointer.
     *
     * @param button   mouse button index
     * @param callback injection callback provided by the mixin framework
     */
    @Inject(
            method = "isButtonReleased(I)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void avrixBlockButtonReleased(int button, CallbackInfoReturnable<Boolean> callback) {
        avrixBlockMouseState(callback);
    }

    /**
     * Suppresses UI-aware button checks while the overlay owns the pointer.
     *
     * @param button   mouse button index
     * @param callback injection callback provided by the mixin framework
     */
    @Inject(
            method = "isButtonDownUICheck(I)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void avrixBlockButtonUiCheck(int button, CallbackInfoReturnable<Boolean> callback) {
        avrixBlockMouseState(callback);
    }

    /**
     * Suppresses delayed right-click detection while the overlay owns the pointer.
     *
     * @param callback injection callback provided by the mixin framework
     */
    @Inject(
            method = "isRightDelay()Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void avrixBlockRightDelay(CallbackInfoReturnable<Boolean> callback) {
        avrixBlockMouseState(callback);
    }

    private static void avrixBlockMouseState(CallbackInfoReturnable<Boolean> callback) {
        if (AvrixUi.blocksPointer()) {
            callback.setReturnValue(false);
        }
    }
}
