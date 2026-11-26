package com.avrix.api.mixins.client;

import com.avrix.api.ui.AvrixUi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zombie.core.Core;
import zombie.ui.UIManager;

/**
 * Client mixin that lets the LixUI overlay consume mouse input before the game handles it.
 */
@Mixin(UIManager.class)
public class UIManagerMixin {

    /**
     * Prevents instantiation of the mixin class.
     */
    private UIManagerMixin() {
        throw new UnsupportedOperationException("Mixin classes cannot be instantiated");
    }

    /**
     * Marks mouse buttons as consumed while the pointer is over an overlay widget, so the game
     * neither activates its own UI nor fires world interaction events.
     *
     * @param mouseX   pointer X coordinate
     * @param mouseY   pointer Y coordinate
     * @param callback injection callback provided by the mixin framework
     */
    @Inject(
            method = "updateMouseButtons(II)I",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void avrixBlockMouseButtons(int mouseX, int mouseY, CallbackInfoReturnable<Integer> callback) {
        if (AvrixUi.blocksPointer()) {
            callback.setReturnValue(3);
        }
    }

    /**
     * Prevents the mouse wheel from zooming the game camera while the pointer is over an overlay
     * widget that handles scrolling.
     *
     * @param core        game core instance
     * @param playerIndex player whose camera would zoom
     * @param delta       zoom step
     */
    @Redirect(
            method = "update()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lzombie/core/Core;doZoomScroll(II)V"
            )
    )
    private static void avrixBlockZoom(Core core, int playerIndex, int delta) {
        if (!AvrixUi.blocksPointer()) {
            core.doZoomScroll(playerIndex, delta);
        }
    }
}
