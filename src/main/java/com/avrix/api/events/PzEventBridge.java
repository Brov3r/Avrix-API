package com.avrix.api.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zombie.Lua.LuaEventManager;
import zombie.Lua.LuaManager;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Bridges Project Zomboid Lua events into the Avrix {@link EventManager}.
 * <p>
 * Events originating from a non-main game thread are queued and replayed on the main
 * game thread. Forwarding an Avrix event back to Lua is guarded against recursive
 * re-dispatch into the Java bus.
 */
public final class PzEventBridge {

    private static final Logger LOGGER = LoggerFactory.getLogger(PzEventBridge.class);
    private static final Object[] EMPTY_ARGS = new Object[0];
    private static final int MAX_LUA_ARGUMENTS = 8;

    private static final ConcurrentLinkedQueue<PendingEvent> PENDING = new ConcurrentLinkedQueue<>();
    private static final ThreadLocal<String> FORWARDING_EVENT = new ThreadLocal<>();

    private PzEventBridge() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Dispatches a Project Zomboid event to Java listeners.
     * <p>
     * The dispatch is ignored before the Lua environment is ready and while the event
     * is being forwarded to Lua. Non-main-thread events are queued for replay on the
     * main game thread.
     *
     * @param eventName game event name
     * @param args      positional event arguments
     */
    public static void dispatch(String eventName, Object... args) {
        if (eventName == null || eventName.isBlank() || LuaManager.env == null) {
            return;
        }

        String forwardingEvent = FORWARDING_EVENT.get();
        if (forwardingEvent != null && forwardingEvent.equals(eventName)) {
            return;
        }

        if (isMainThread()) {
            drain();
            if (EventManager.hasListeners(eventName)) {
                EventManager.invoke(eventName, args);
            }
            return;
        }

        if (EventManager.hasListeners(eventName)) {
            Object[] safeArgs = args == null ? EMPTY_ARGS : args.clone();
            PENDING.add(new PendingEvent(eventName, safeArgs));
        }
    }

    /**
     * Replays pending events on the main game thread.
     */
    public static void drain() {
        if (!isMainThread()) {
            return;
        }

        PendingEvent pending;
        while ((pending = PENDING.poll()) != null) {
            if (EventManager.hasListeners(pending.eventName())) {
                EventManager.invoke(pending.eventName(), pending.args());
            }
        }
    }

    /**
     * Forwards an event to Project Zomboid Lua callbacks.
     * <p>
     * Lua receives at most eight positional arguments, matching the game's event API.
     * The Java listener bus is not invoked again for forwarded events.
     *
     * @param eventName event name
     * @param args      positional event arguments
     * @return {@code true} if the event was forwarded to Lua
     */
    public static boolean forwardToLua(String eventName, Object... args) {
        if (eventName == null || eventName.isBlank()) {
            return false;
        }
        if (LuaManager.env == null) {
            LOGGER.debug("Cannot forward event '{}' to Lua: Lua environment is not ready", eventName);
            return false;
        }

        Object[] actualArgs = args == null ? EMPTY_ARGS : args;
        if (actualArgs.length > MAX_LUA_ARGUMENTS) {
            LOGGER.warn("Cannot forward event '{}' to Lua: {} arguments provided, maximum is {}",
                    eventName, actualArgs.length, MAX_LUA_ARGUMENTS);
            return false;
        }

        String previous = FORWARDING_EVENT.get();
        FORWARDING_EVENT.set(eventName);
        try {
            switch (actualArgs.length) {
                case 0 -> LuaEventManager.triggerEvent(eventName);
                case 1 -> LuaEventManager.triggerEvent(eventName, actualArgs[0]);
                case 2 -> LuaEventManager.triggerEvent(eventName, actualArgs[0], actualArgs[1]);
                case 3 -> LuaEventManager.triggerEvent(eventName, actualArgs[0], actualArgs[1], actualArgs[2]);
                case 4 -> LuaEventManager.triggerEvent(eventName, actualArgs[0], actualArgs[1], actualArgs[2],
                        actualArgs[3]);
                case 5 -> LuaEventManager.triggerEvent(eventName, actualArgs[0], actualArgs[1], actualArgs[2],
                        actualArgs[3], actualArgs[4]);
                case 6 -> LuaEventManager.triggerEvent(eventName, actualArgs[0], actualArgs[1], actualArgs[2],
                        actualArgs[3], actualArgs[4], actualArgs[5]);
                case 7 -> LuaEventManager.triggerEvent(eventName, actualArgs[0], actualArgs[1], actualArgs[2],
                        actualArgs[3], actualArgs[4], actualArgs[5], actualArgs[6]);
                case 8 -> LuaEventManager.triggerEvent(eventName, actualArgs[0], actualArgs[1], actualArgs[2],
                        actualArgs[3], actualArgs[4], actualArgs[5], actualArgs[6], actualArgs[7]);
                default -> throw new IllegalStateException("Unexpected argument count: " + actualArgs.length);
            }
            return true;
        } catch (Exception | LinkageError ex) {
            LOGGER.error("Failed to forward event '{}' to Lua", eventName, ex);
            return false;
        } finally {
            if (previous == null) {
                FORWARDING_EVENT.remove();
            } else {
                FORWARDING_EVENT.set(previous);
            }
        }
    }

    private static boolean isMainThread() {
        return LuaManager.thread == null
                || LuaManager.thread.debugOwnerThread == null
                || LuaManager.thread.debugOwnerThread == Thread.currentThread();
    }

    private record PendingEvent(String eventName, Object[] args) {
    }
}
