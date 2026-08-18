package com.avrix.api.events;

import net.lenni0451.classtransform.annotations.CTarget;
import net.lenni0451.classtransform.annotations.CTransformer;
import net.lenni0451.classtransform.annotations.injection.CInject;
import zombie.Lua.LuaEventManager;

/**
 * ClassTransform transformer for {@link LuaEventManager} that bridges Lua-triggered events
 * to the static {@link EventManager} dispatch system in Avrix.
 * <p>
 * Injects a call to {@code EventManager.invoke(event, ...)} at the {@code HEAD} of each
 * {@code triggerEvent} overload (0 to 8 parameters), enabling direct dispatching into Java subscribers.
 */
@CTransformer(value = LuaEventManager.class)
public class LuaEventManagerMixin {

    /**
     * Injects {@code EventManager.invoke(event)} into {@code triggerEvent(String)}.
     * Descriptor: {@code (Ljava/lang/String;)V}
     *
     * @param event The event identifier
     */
    @CInject(
            method = "triggerEvent(Ljava/lang/String;)V",
            target = @CTarget("HEAD")
    )
    private static void inject0(String event) {
        EventManager.invoke(event);
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
        EventManager.invoke(event, p1);
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
        EventManager.invoke(event, p1, p2);
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
        EventManager.invoke(event, p1, p2, p3);
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
        EventManager.invoke(event, p1, p2, p3, p4);
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
        EventManager.invoke(event, p1, p2, p3, p4, p5);
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
        EventManager.invoke(event, p1, p2, p3, p4, p5, p6);
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
        EventManager.invoke(event, p1, p2, p3, p4, p5, p6, p7);
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
        EventManager.invoke(event, p1, p2, p3, p4, p5, p6, p7, p8);
    }
}