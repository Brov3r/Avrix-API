package com.avrix.api.mixins.common;

import com.avrix.api.events.EventManager;
import com.avrix.api.events.PzEventBridge;
import com.avrix.api.lua.script.LuaScriptLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.krka.kahlua.vm.KahluaTable;
import se.krka.kahlua.vm.Platform;
import zombie.Lua.LuaEventManager;

/**
 * Mixin for {@link LuaEventManager} that bridges Lua-triggered events to the Avrix event bus.
 * <p>
 * Event names are forwarded dynamically, so game updates do not require adding new
 * constants to the API. The bridge respects the Lua environment readiness and queues
 * events originating from non-main threads for replay on the main game thread.
 */
@Mixin(LuaEventManager.class)
public class LuaEventManagerMixin {

    /**
     * Prevents instantiation of the mixin class.
     */
    private LuaEventManagerMixin() {
        throw new UnsupportedOperationException("Mixin classes cannot be instantiated");
    }

    /**
     * Forwards {@code triggerEvent(String)} to the event bus and starts the late custom Lua load phase
     * for {@code OnGameBoot}.
     * <p>
     * The {@code OnGameBoot} hook is placed here because dedicated servers do not run
     * {@code LuaEventManager.RunQueuedEvents()} and may skip Lua event callbacks entirely.
     *
     * @param event    the event identifier
     * @param callback injection callback provided by the mixin framework
     */
    @Inject(
            method = "triggerEvent(Ljava/lang/String;)V",
            at = @At("HEAD")
    )
    private static void inject0(String event, CallbackInfo callback) {
        PzEventBridge.dispatch(event);
        if ("OnGameBoot".equals(event)) {
            LuaScriptLoader.onGameBoot();
        }
    }

    /**
     * Forwards {@code triggerEvent(String, Object)} to the event bus.
     *
     * @param event    the event identifier
     * @param p1       first argument
     * @param callback injection callback provided by the mixin framework
     */
    @Inject(
            method = "triggerEvent(Ljava/lang/String;Ljava/lang/Object;)V",
            at = @At("HEAD")
    )
    private static void inject1(String event, Object p1, CallbackInfo callback) {
        PzEventBridge.dispatch(event, p1);
    }

    /**
     * Forwards {@code triggerEvent(String, Object, Object)} to the event bus.
     *
     * @param event    the event identifier
     * @param p1       first argument
     * @param p2       second argument
     * @param callback injection callback provided by the mixin framework
     */
    @Inject(
            method = "triggerEvent(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V",
            at = @At("HEAD")
    )
    private static void inject2(String event, Object p1, Object p2, CallbackInfo callback) {
        PzEventBridge.dispatch(event, p1, p2);
    }

    /**
     * Forwards {@code triggerEvent(String, Object, Object, Object)} to the event bus.
     *
     * @param event    the event identifier
     * @param p1       first argument
     * @param p2       second argument
     * @param p3       third argument
     * @param callback injection callback provided by the mixin framework
     */
    @Inject(
            method = "triggerEvent(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V",
            at = @At("HEAD")
    )
    private static void inject3(String event, Object p1, Object p2, Object p3, CallbackInfo callback) {
        PzEventBridge.dispatch(event, p1, p2, p3);
    }

    /**
     * Forwards the four-argument {@code triggerEvent} overload to the event bus.
     *
     * @param event    the event identifier
     * @param p1       first argument
     * @param p2       second argument
     * @param p3       third argument
     * @param p4       fourth argument
     * @param callback injection callback provided by the mixin framework
     */
    @Inject(
            method = "triggerEvent(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;"
                    + "Ljava/lang/Object;Ljava/lang/Object;)V",
            at = @At("HEAD")
    )
    private static void inject4(String event, Object p1, Object p2, Object p3, Object p4, CallbackInfo callback) {
        PzEventBridge.dispatch(event, p1, p2, p3, p4);
    }

    /**
     * Forwards the five-argument {@code triggerEvent} overload to the event bus.
     *
     * @param event    the event identifier
     * @param p1       first argument
     * @param p2       second argument
     * @param p3       third argument
     * @param p4       fourth argument
     * @param p5       fifth argument
     * @param callback injection callback provided by the mixin framework
     */
    @Inject(
            method = "triggerEvent(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;"
                    + "Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V",
            at = @At("HEAD")
    )
    private static void inject5(
            String event, Object p1, Object p2, Object p3, Object p4, Object p5, CallbackInfo callback
    ) {
        PzEventBridge.dispatch(event, p1, p2, p3, p4, p5);
    }

    /**
     * Forwards the six-argument {@code triggerEvent} overload to the event bus.
     *
     * @param event    the event identifier
     * @param p1       first argument
     * @param p2       second argument
     * @param p3       third argument
     * @param p4       fourth argument
     * @param p5       fifth argument
     * @param p6       sixth argument
     * @param callback injection callback provided by the mixin framework
     */
    @Inject(
            method = "triggerEvent(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;"
                    + "Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V",
            at = @At("HEAD")
    )
    private static void inject6(
            String event, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, CallbackInfo callback
    ) {
        PzEventBridge.dispatch(event, p1, p2, p3, p4, p5, p6);
    }

    /**
     * Forwards the seven-argument {@code triggerEvent} overload to the event bus.
     *
     * @param event    the event identifier
     * @param p1       first argument
     * @param p2       second argument
     * @param p3       third argument
     * @param p4       fourth argument
     * @param p5       fifth argument
     * @param p6       sixth argument
     * @param p7       seventh argument
     * @param callback injection callback provided by the mixin framework
     */
    @Inject(
            method = "triggerEvent(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;"
                    + "Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V",
            at = @At("HEAD")
    )
    private static void inject7(
            String event, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7,
            CallbackInfo callback
    ) {
        PzEventBridge.dispatch(event, p1, p2, p3, p4, p5, p6, p7);
    }

    /**
     * Forwards the eight-argument {@code triggerEvent} overload to the event bus.
     *
     * @param event    the event identifier
     * @param p1       first argument
     * @param p2       second argument
     * @param p3       third argument
     * @param p4       fourth argument
     * @param p5       fifth argument
     * @param p6       sixth argument
     * @param p7       seventh argument
     * @param p8       eighth argument
     * @param callback injection callback provided by the mixin framework
     */
    @Inject(
            method = "triggerEvent(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;"
                    + "Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;"
                    + "Ljava/lang/Object;Ljava/lang/Object;)V",
            at = @At("HEAD")
    )
    private static void inject8(
            String event, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8,
            CallbackInfo callback
    ) {
        PzEventBridge.dispatch(event, p1, p2, p3, p4, p5, p6, p7, p8);
    }

    /**
     * Replays Java events that were queued from non-main game threads.
     *
     * @param callback injection callback provided by the mixin framework
     */
    @Inject(
            method = "RunQueuedEvents()V",
            at = @At("HEAD")
    )
    private static void drainQueuedJavaEvents(CallbackInfo callback) {
        PzEventBridge.drain();
        LuaScriptLoader.drainLate();
    }

    /**
     * Refreshes the dynamic Project Zomboid event catalog after Lua event registration.
     *
     * @param platform Lua platform instance
     * @param table    Lua table receiving the registered events
     * @param callback injection callback provided by the mixin framework
     */
    @Inject(
            method = "register(Lse/krka/kahlua/vm/Platform;Lse/krka/kahlua/vm/KahluaTable;)V",
            at = @At("TAIL")
    )
    private static void refreshGameEventCatalog(Platform platform, KahluaTable table, CallbackInfo callback) {
        EventManager.refreshGameEvents();
    }
}
