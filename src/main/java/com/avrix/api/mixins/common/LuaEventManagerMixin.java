package com.avrix.api.mixins.common;

import com.avrix.api.events.EventManager;
import com.avrix.api.events.PzEventBridge;
import com.avrix.api.lua.script.LuaScriptLoader;
import net.lenni0451.classtransform.annotations.CTarget;
import net.lenni0451.classtransform.annotations.CTransformer;
import net.lenni0451.classtransform.annotations.injection.CInject;
import zombie.Lua.LuaEventManager;

/**
 * ClassTransform transformer for {@link LuaEventManager} that bridges Lua-triggered events
 * to the Avrix event bus.
 * <p>
 * Event names are forwarded dynamically, so game updates do not require adding new
 * constants to the API. The bridge respects the Lua environment readiness and queues
 * events originating from non-main threads for replay on the main game thread.
 */
@CTransformer(value = LuaEventManager.class)
public class LuaEventManagerMixin {

    /**
     * Injects {@code EventManager.invoke(event)} into {@code triggerEvent(String)} and starts
     * the late custom Lua load phase for {@code OnGameBoot}.
     * <p>
     * The {@code OnGameBoot} hook is placed here because dedicated servers do not run
     * {@code LuaEventManager.RunQueuedEvents()} and may skip Lua event callbacks entirely.
     * Descriptor: {@code (Ljava/lang/String;)V}
     *
     * @param event The event identifier
     */
    @CInject(
            method = "triggerEvent(Ljava/lang/String;)V",
            target = @CTarget("HEAD")
    )
    private static void inject0(String event) {
        PzEventBridge.dispatch(event);
        if ("OnGameBoot".equals(event)) {
            LuaScriptLoader.onGameBoot();
        }
    }

    /**
     * Injects {@code EventManager.invoke(event, p1)} into {@code triggerEvent(String, Object)}.
     * Descriptor: {@code (Ljava/lang/String;Ljava/lang/Object;)V}
     *
     * @param event The event identifier
     * @param p1    First parameter
     */
    @CInject(
            method = "triggerEvent(Ljava/lang/String;Ljava/lang/Object;)V",
            target = @CTarget("HEAD")
    )
    private static void inject1(String event, Object p1) {
        PzEventBridge.dispatch(event, p1);
    }

    /**
     * Injects {@code EventManager.invoke(event, p1, p2)} into {@code triggerEvent(String, Object, Object)}.
     * Descriptor: {@code (Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V}
     *
     * @param event The event identifier
     * @param p1    First parameter
     * @param p2    Second parameter
     */
    @CInject(
            method = "triggerEvent(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V",
            target = @CTarget("HEAD")
    )
    private static void inject2(String event, Object p1, Object p2) {
        PzEventBridge.dispatch(event, p1, p2);
    }

    /**
     * Injects {@code EventManager.invoke(event, p1, p2, p3)} into {@code triggerEvent(String, Object, Object, Object)}.
     * Descriptor: {@code (Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V}
     *
     * @param event The event identifier
     * @param p1    First parameter
     * @param p2    Second parameter
     * @param p3    Third parameter
     */
    @CInject(
            method = "triggerEvent(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V",
            target = @CTarget("HEAD")
    )
    private static void inject3(String event, Object p1, Object p2, Object p3) {
        PzEventBridge.dispatch(event, p1, p2, p3);
    }

    /**
     * Injects {@code EventManager.invoke(event, p1, p2, p3, p4)}.
     * Descriptor: {@code (Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V}
     *
     * @param event The event identifier
     * @param p1    First parameter
     * @param p2    Second parameter
     * @param p3    Third parameter
     * @param p4    Fourth parameter
     */
    @CInject(
            method = "triggerEvent(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V",
            target = @CTarget("HEAD")
    )
    private static void inject4(String event, Object p1, Object p2, Object p3, Object p4) {
        PzEventBridge.dispatch(event, p1, p2, p3, p4);
    }

    /**
     * Injects {@code EventManager.invoke(event, p1, p2, p3, p4, p5)}.
     * Descriptor: {@code (Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V}
     *
     * @param event The event identifier
     * @param p1    First parameter
     * @param p2    Second parameter
     * @param p3    Third parameter
     * @param p4    Fourth parameter
     * @param p5    Fifth parameter
     */
    @CInject(
            method = "triggerEvent(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V",
            target = @CTarget("HEAD")
    )
    private static void inject5(String event, Object p1, Object p2, Object p3, Object p4, Object p5) {
        PzEventBridge.dispatch(event, p1, p2, p3, p4, p5);
    }

    /**
     * Injects {@code EventManager.invoke(event, p1..p6)}.
     * Descriptor: {@code (Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V}
     *
     * @param event The event identifier
     * @param p1    First parameter
     * @param p2    Second parameter
     * @param p3    Third parameter
     * @param p4    Fourth parameter
     * @param p5    Fifth parameter
     * @param p6    Sixth parameter
     */
    @CInject(
            method = "triggerEvent(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V",
            target = @CTarget("HEAD")
    )
    private static void inject6(String event, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6) {
        PzEventBridge.dispatch(event, p1, p2, p3, p4, p5, p6);
    }

    /**
     * Injects {@code EventManager.invoke(event, p1..p7)}.
     * Descriptor: {@code (Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V}
     *
     * @param event The event identifier
     * @param p1    First parameter
     * @param p2    Second parameter
     * @param p3    Third parameter
     * @param p4    Fourth parameter
     * @param p5    Fifth parameter
     * @param p6    Sixth parameter
     * @param p7    Seventh parameter
     */
    @CInject(
            method = "triggerEvent(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V",
            target = @CTarget("HEAD")
    )
    private static void inject7(String event, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7) {
        PzEventBridge.dispatch(event, p1, p2, p3, p4, p5, p6, p7);
    }

    /**
     * Injects {@code EventManager.invoke(event, p1..p8)}.
     * Descriptor: {@code (Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V}
     *
     * @param event The event identifier
     * @param p1    First parameter
     * @param p2    Second parameter
     * @param p3    Third parameter
     * @param p4    Fourth parameter
     * @param p5    Fifth parameter
     * @param p6    Sixth parameter
     * @param p7    Seventh parameter
     * @param p8    Eighth parameter
     */
    @CInject(
            method = "triggerEvent(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V",
            target = @CTarget("HEAD")
    )
    private static void inject8(String event, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8) {
        PzEventBridge.dispatch(event, p1, p2, p3, p4, p5, p6, p7, p8);
    }

    /**
     * Replays Java events that were queued from non-main game threads.
     */
    @CInject(
            method = "RunQueuedEvents()V",
            target = @CTarget("HEAD")
    )
    private static void drainQueuedJavaEvents() {
        PzEventBridge.drain();
        LuaScriptLoader.drainLate();
    }

    /**
     * Refreshes the dynamic Project Zomboid event catalog after Lua event registration.
     */
    @CInject(
            method = "register(Lse/krka/kahlua/vm/Platform;Lse/krka/kahlua/vm/KahluaTable;)V",
            target = @CTarget("TAIL")
    )
    private static void refreshGameEventCatalog() {
        EventManager.refreshGameEvents();
    }
}