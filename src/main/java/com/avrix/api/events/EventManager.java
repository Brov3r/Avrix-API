package com.avrix.api.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Static central event manager for Avrix.
 * <p>
 * Handles listener registration and event dispatching across Project Zomboid and Avrix plugins.
 */
public final class EventManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventManager.class);

    /**
     * Common priority presets for convenience.
     */
    public static final int PRIORITY_HIGHEST = 1000;
    public static final int PRIORITY_HIGH = 100;
    public static final int PRIORITY_NORMAL = 0;
    public static final int PRIORITY_LOW = -100;
    public static final int PRIORITY_LOWEST = -1000;

    /**
     * Map storing event subscribers: EventName -> Sorted List of Invokers
     */
    private static final Map<String, List<HandlerInvoker>> HANDLERS = new ConcurrentHashMap<>();

    private EventManager() {
        throw new UnsupportedOperationException("EventManager is a static utility class");
    }

    /**
     * Registers all methods annotated with {@link SubscribeEvent} inside the given object.
     *
     * @param listener Object containing subscriber methods
     */
    public static void register(Object listener) {
        Objects.requireNonNull(listener, "Listener instance cannot be null");
        Class<?> clazz = listener.getClass();

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
                HandlerInvoker invoker = new HandlerInvoker(listener, handle, method.getParameterTypes(), priority, eventName);

                List<HandlerInvoker> list = HANDLERS.computeIfAbsent(eventName, _ -> new CopyOnWriteArrayList<>());
                list.add(invoker);

                // Sort descending by priority: highest priority executes first
                list.sort((a, b) -> Integer.compare(b.priority, a.priority));
            } catch (IllegalAccessException e) {
                LOGGER.error("Failed to bind event listener method '{}' in class '{}'", method.getName(), clazz.getName(), e);
            }
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

        for (List<HandlerInvoker> list : HANDLERS.values()) {
            list.removeIf(invoker -> invoker.target == listener);
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

        List<HandlerInvoker> list = HANDLERS.get(eventName);
        if (list == null || list.isEmpty()) {
            return;
        }

        for (HandlerInvoker invoker : list) {
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
     * Internal wrapper for direct high-performance execution of an event listener method.
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
                if (parameterTypes.length == 0) {
                    handle.invoke();
                    return;
                }

                Object[] boundArgs = new Object[parameterTypes.length];
                int available = incomingArgs != null ? incomingArgs.length : 0;

                for (int i = 0; i < parameterTypes.length; i++) {
                    Class<?> targetType = parameterTypes[i];
                    Object rawValue = (i < available) ? incomingArgs[i] : null;

                    if (rawValue == null && targetType.isPrimitive()) {
                        // Prevent NullPointerException during unboxing by providing primitive default
                        boundArgs[i] = getDefaultPrimitiveValue(targetType);
                    } else if (rawValue instanceof Number number && targetType.isPrimitive()) {
                        // Auto-widen/narrow numbers (e.g. Double from Lua into float/int in Java)
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

        private static Object getDefaultPrimitiveValue(Class<?> type) {
            if (type == boolean.class) return false;
            if (type == byte.class) return (byte) 0;
            if (type == short.class) return (short) 0;
            if (type == int.class) return 0;
            if (type == long.class) return 0L;
            if (type == float.class) return 0.0f;
            if (type == double.class) return 0.0d;
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