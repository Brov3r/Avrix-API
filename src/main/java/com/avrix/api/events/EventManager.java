package com.avrix.api.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * High-performance, thread-safe central event dispatcher for Avrix and Project Zomboid.
 */
public final class EventManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventManager.class);

    public static final int PRIORITY_HIGHEST = 1000;
    public static final int PRIORITY_HIGH = 100;
    public static final int PRIORITY_NORMAL = 0;
    public static final int PRIORITY_LOW = -100;
    public static final int PRIORITY_LOWEST = -1000;

    /**
     * Map storing event subscribers: EventName -> Immutable Sorted Array of Invokers
     */
    private static final Map<String, HandlerInvoker[]> HANDLERS = new ConcurrentHashMap<>();
    private static final ReentrantLock REGISTRATION_LOCK = new ReentrantLock();

    private EventManager() {
        throw new UnsupportedOperationException("EventManager is a static utility class");
    }

    /**
     * Registers all methods annotated with {@link SubscribeEvent} inside the given object.
     *
     * @param listener Object containing subscriber methods
     * @throws NullPointerException if listener is null
     */
    public static void register(Object listener) {
        Objects.requireNonNull(listener, "Listener instance cannot be null");
        Class<?> clazz = listener.getClass();

        REGISTRATION_LOCK.lock();
        try {
            for (Method method : clazz.getDeclaredMethods()) {
                SubscribeEvent annotation = method.getAnnotation(SubscribeEvent.class);
                if (annotation == null) {
                    continue;
                }

                String eventName = resolveEventName(annotation);
                int priority = annotation.priority();

                try {
                    method.setAccessible(true);
                    MethodHandle handle = MethodHandles.lookup().unreflect(method).bindTo(listener);
                    HandlerInvoker invoker = new HandlerInvoker(
                            listener, handle, method.getParameterTypes(), priority, eventName
                    );

                    HandlerInvoker[] current = HANDLERS.getOrDefault(eventName, new HandlerInvoker[0]);
                    List<HandlerInvoker> list = new ArrayList<>(Arrays.asList(current));
                    list.add(invoker);

                    // Sort descending by priority: highest priority executes first
                    list.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
                    HANDLERS.put(eventName, list.toArray(HandlerInvoker[]::new));
                } catch (IllegalAccessException e) {
                    LOGGER.error("Failed to bind event listener method '{}' in class '{}'", method.getName(), clazz.getName(), e);
                }
            }
        } finally {
            REGISTRATION_LOCK.unlock();
        }
    }

    /**
     * Unregisters all event handlers belonging to the specified listener object.
     *
     * @param listener The listener instance to unregister
     */
    public static void unregister(Object listener) {
        if (listener == null) {
            return;
        }

        REGISTRATION_LOCK.lock();
        try {
            HANDLERS.forEach((eventName, currentArray) -> {
                List<HandlerInvoker> list = new ArrayList<>(Arrays.asList(currentArray));
                boolean removed = list.removeIf(invoker -> invoker.target() == listener);
                if (removed) {
                    if (list.isEmpty()) {
                        HANDLERS.remove(eventName);
                    } else {
                        HANDLERS.put(eventName, list.toArray(HandlerInvoker[]::new));
                    }
                }
            });
        } finally {
            REGISTRATION_LOCK.unlock();
        }
    }

    /**
     * Invokes all subscribers for the specified {@link Event}.
     *
     * @param event The event contract
     * @param args  Positional event arguments
     */
    public static void invoke(Event event, Object... args) {
        if (event == null) {
            return;
        }
        invoke(event.getName(), args);
    }

    /**
     * Core dispatch method called directly from mixins and plugin code.
     *
     * @param eventName The string identifier of the event
     * @param args      Positional event arguments
     */
    public static void invoke(String eventName, Object... args) {
        if (eventName == null) {
            return;
        }

        HandlerInvoker[] array = HANDLERS.get(eventName);
        if (array == null) {
            return;
        }

        for (HandlerInvoker invoker : array) {
            invoker.invoke(args);
        }
    }

    private static String resolveEventName(SubscribeEvent annotation) {
        if (!annotation.custom().isBlank()) {
            return annotation.custom().trim();
        }
        return annotation.value().getName();
    }

    /**
     * Internal invoker for high-performance execution of an event listener method.
     */
    private record HandlerInvoker(
            Object target,
            MethodHandle handle,
            Class<?>[] parameterTypes,
            int priority,
            String eventName
    ) {
        public void invoke(Object[] incomingArgs) {
            try {
                int paramCount = parameterTypes.length;
                if (paramCount == 0) {
                    handle.invoke();
                    return;
                }

                int available = incomingArgs != null ? incomingArgs.length : 0;

                // Fast-Path: Argument count matches and types are directly compatible (Zero-Allocation)
                if (paramCount == available && isDirectCompatible(incomingArgs)) {
                    handle.invokeWithArguments(incomingArgs);
                    return;
                }

                // Slow-Path: Auto-cast Lua numbers and supply primitive default values if argument missing
                Object[] boundArgs = new Object[paramCount];
                for (int i = 0; i < paramCount; i++) {
                    Class<?> targetType = parameterTypes[i];
                    Object rawValue = (i < available) ? incomingArgs[i] : null;

                    if (rawValue == null && targetType.isPrimitive()) {
                        boundArgs[i] = getDefaultPrimitiveValue(targetType);
                    } else if (rawValue instanceof Number number && targetType.isPrimitive()) {
                        boundArgs[i] = adaptNumber(number, targetType);
                    } else {
                        boundArgs[i] = rawValue;
                    }
                }

                handle.invokeWithArguments(boundArgs);
            } catch (ClassCastException e) {
                LOGGER.error("Type mismatch when invoking listener for event '{}': {}", eventName, e.getMessage());
            } catch (Throwable t) {
                LOGGER.error("Unhandled exception in event listener for event '{}'", eventName, t);
            }
        }

        private boolean isDirectCompatible(Object[] args) {
            for (int i = 0; i < parameterTypes.length; i++) {
                Object arg = args[i];
                if (arg == null) {
                    if (parameterTypes[i].isPrimitive()) return false;
                } else if (!parameterTypes[i].isInstance(arg)) {
                    return false;
                }
            }
            return true;
        }

        private static Object getDefaultPrimitiveValue(Class<?> type) {
            if (type == boolean.class) return false;
            if (type == int.class) return 0;
            if (type == float.class) return 0.0f;
            if (type == double.class) return 0.0d;
            if (type == long.class) return 0L;
            if (type == short.class) return (short) 0;
            if (type == byte.class) return (byte) 0;
            if (type == char.class) return '\0';
            return null;
        }

        private static Object adaptNumber(Number number, Class<?> targetType) {
            if (targetType == int.class) return number.intValue();
            if (targetType == float.class) return number.floatValue();
            if (targetType == double.class) return number.doubleValue();
            if (targetType == long.class) return number.longValue();
            if (targetType == byte.class) return number.byteValue();
            if (targetType == short.class) return number.shortValue();
            return number;
        }
    }
}