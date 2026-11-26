package com.avrix.api.mixins.client;

import com.avrix.api.ui.AvrixUi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zombie.Lua.LuaEventManager;
import zombie.input.GameKeyboard;

/**
 * Client mixin that suppresses game keyboard input while an overlay widget owns keyboard focus.
 */
@Mixin(GameKeyboard.class)
public class GameKeyboardMixin {

    /**
     * Prevents instantiation of the mixin class.
     */
    private GameKeyboardMixin() {
        throw new UnsupportedOperationException("Mixin classes cannot be instantiated");
    }

    /**
     * Reports raw key states as released while the overlay owns keyboard focus.
     *
     * @param key      key code
     * @param callback injection callback provided by the mixin framework
     */
    @Inject(
            method = "isKeyDownRaw(I)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void avrixBlockRawKey(int key, CallbackInfoReturnable<Boolean> callback) {
        if (AvrixUi.isKeyboardCaptured()) {
            callback.setReturnValue(false);
        }
    }

    /**
     * Reports bound key states as released while the overlay owns keyboard focus.
     *
     * @param key      key code
     * @param callback injection callback provided by the mixin framework
     */
    @Inject(
            method = "isKeyDown(I)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void avrixBlockKey(int key, CallbackInfoReturnable<Boolean> callback) {
        if (AvrixUi.isKeyboardCaptured()) {
            callback.setReturnValue(false);
        }
    }

    /**
     * Drops one-argument game key events while the overlay owns keyboard focus.
     *
     * @param event key event name
     * @param value event argument, usually a key code
     */
    @Redirect(
            method = "update()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lzombie/Lua/LuaEventManager;triggerEvent(Ljava/lang/String;Ljava/lang/Object;)V"
            )
    )
    private static void avrixFilterKeyEvent(String event, Object value) {
        if (!AvrixUi.isKeyboardCaptured()) {
            LuaEventManager.triggerEvent(event, value);
        }
    }
}
