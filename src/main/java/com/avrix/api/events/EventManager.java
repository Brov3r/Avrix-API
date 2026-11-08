package com.avrix.api.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe central event dispatcher for Avrix and Project Zomboid events.
 * <p>
 * The dispatcher keeps immutable snapshots of listener arrays, which makes event
 * dispatch lock-free while registrations and unregistrations remain serialized.
 * Listener methods are invoked on the calling thread. Project Zomboid events that
 * originate from a non-main game thread are queued by {@link PzEventBridge} and
 * replayed on the main game thread.
 */
public final class EventManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventManager.class);
    private static final HandlerInvoker[] EMPTY_HANDLERS = new HandlerInvoker[0];

    /**
     * Highest listener priority; handlers run before all lower priorities.
     */
    public static final int PRIORITY_HIGHEST = 1000;

    /**
     * High listener priority.
     */
    public static final int PRIORITY_HIGH = 100;

    /**
     * Default listener priority.
     */
    public static final int PRIORITY_NORMAL = 0;

    /**
     * Low listener priority.
     */
    public static final int PRIORITY_LOW = -100;

    /**
     * Lowest listener priority; handlers run after all higher priorities.
     */
    public static final int PRIORITY_LOWEST = -1000;

    /**
     * Map storing event subscribers: event name to an immutable sorted array of invokers.
     */
    private static final Map<String, HandlerInvoker[]> HANDLERS = new ConcurrentHashMap<>();

    /**
     * Map storing subscriber sets by owner token, allowing scoped unregistration.
     */
    private static final Map<Object, Set<HandlerInvoker>> OWNED_HANDLERS = new ConcurrentHashMap<>();

    private static final ReentrantLock REGISTRATION_LOCK = new ReentrantLock();

    private EventManager() {
        throw new UnsupportedOperationException("EventManager is a static utility class");
    }

    /**
     * Registers all methods annotated with {@link SubscribeEvent} inside the given object.
     *
     * @param listener object containing subscriber methods
     * @throws NullPointerException if {@code listener} is null
     */
    public static void register(Object listener) {
        register(null, listener);
    }

    /**
     * Registers all annotated listener methods and associates them with an owner token.
     * <p>
     * The owner can be any stable object, typically a plugin data container. It allows
     * all handlers belonging to that owner to be removed with {@link #unregisterAll(Object)}.
     *
     * @param owner    owner token, or {@code null} for an unowned registration
     * @param listener object containing subscriber methods
     * @throws NullPointerException if {@code listener} is null
     */
    public static void register(Object owner, Object listener) {
        Objects.requireNonNull(listener, "Listener instance cannot be null");
        Class<?> listenerClass = listener.getClass();

        REGISTRATION_LOCK.lock();
        try {
            Set<String> seenMethods = new HashSet<>();
            for (Class<?> type : collectTypes(listenerClass)) {
                for (Method method : type.getDeclaredMethods()) {
                    if (method.isSynthetic() || method.isBridge()) {
                        continue;
                    }

                    String signature = methodSignature(method);
                    if (!seenMethods.add(signature)) {
                        continue;
                    }

                    SubscribeEvent annotation = method.getAnnotation(SubscribeEvent.class);
                    if (annotation == null) {
                        continue;
                    }

                    String eventName = resolveEventName(annotation);
                    if (eventName == null) {
                        LOGGER.warn("Ignoring listener method '{}' in class '{}': event name is blank",
                                method.getName(), listenerClass.getName());
                        continue;
                    }

                    if (method.getReturnType() != void.class) {
                        LOGGER.warn("Listener method '{}.{}' should return void; return value is ignored",
                                listenerClass.getName(), method.getName());
                    }

                    try {
                        method.setAccessible(true);
                        MethodHandle handle = MethodHandles.lookup().unreflect(method);
                        if (!Modifier.isStatic(method.getModifiers())) {
                            handle = handle.bindTo(listener);
                        }

                        HandlerInvoker invoker = new HandlerInvoker(
                                listener, owner, method, handle, method.getParameterTypes(),
                                annotation.priority(), eventName
                        );

                        HandlerInvoker[] current = HANDLERS.getOrDefault(eventName, EMPTY_HANDLERS);
                        if (containsHandler(current, invoker)) {
                            LOGGER.debug("Ignoring duplicate listener '{}.{}' for event '{}'",
                                    listenerClass.getName(), method.getName(), eventName);
                            continue;
                        }

                        List<HandlerInvoker> list = new ArrayList<>(Arrays.asList(current));
                        list.add(invoker);
                        list.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
                        HANDLERS.put(eventName, list.toArray(HandlerInvoker[]::new));

                        if (owner != null) {
                            OWNED_HANDLERS
                                    .computeIfAbsent(owner, ignored -> ConcurrentHashMap.newKeySet())
                                    .add(invoker);
                        }

                        LOGGER.debug("Registered listener '{}.{}' for event '{}' (priority: {}, owner: {})",
                                listenerClass.getName(), method.getName(), eventName,
                                annotation.priority(), owner);
                    } catch (Exception | LinkageError ex) {
                        LOGGER.error("Failed to bind event listener method '{}.{}'",
                                listenerClass.getName(), method.getName(), ex);
                    }
                }
            }
        } finally {
            REGISTRATION_LOCK.unlock();
        }
    }

    /**
     * Unregisters all event handlers belonging to the specified listener object.
     *
     * @param listener listener instance to unregister
     */
    public static void unregister(Object listener) {
        if (listener == null) {
            return;
        }

        REGISTRATION_LOCK.lock();
        try {
            List<HandlerInvoker> removed = new ArrayList<>();
            HANDLERS.forEach((eventName, currentArray) -> {
                List<HandlerInvoker> list = new ArrayList<>(Arrays.asList(currentArray));
                boolean changed = list.removeIf(invoker -> {
                    if (invoker.target() == listener) {
                        removed.add(invoker);
                        return true;
                    }
                    return false;
                });
                if (changed) {
                    if (list.isEmpty()) {
                        HANDLERS.remove(eventName);
                    } else {
                        HANDLERS.put(eventName, list.toArray(HandlerInvoker[]::new));
                    }
                }
            });

            for (HandlerInvoker invoker : removed) {
                removeFromOwner(invoker);
            }
        } finally {
            REGISTRATION_LOCK.unlock();
        }
    }

    /**
     * Unregisters every listener previously registered with the given owner token.
     *
     * @param owner owner token passed to {@link #register(Object, Object)}
     */
    public static void unregisterAll(Object owner) {
        if (owner == null) {
            return;
        }

        REGISTRATION_LOCK.lock();
        try {
            Set<HandlerInvoker> owned = OWNED_HANDLERS.remove(owner);
            if (owned == null) {
                return;
            }
            for (HandlerInvoker invoker : owned) {
                removeHandler(invoker);
            }
        } finally {
            REGISTRATION_LOCK.unlock();
        }
    }

    /**
     * Invokes all subscribers for the specified {@link Event}.
     *
     * @param event event contract
     * @param args  positional event arguments
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
     * @param eventName string identifier of the event
     * @param args      positional event arguments
     */
    public static void invoke(String eventName, Object... args) {
        String key = normalizeEventName(eventName);
        if (key == null) {
            return;
        }

        HandlerInvoker[] array = HANDLERS.get(key);
        if (array == null) {
            return;
        }

        for (HandlerInvoker invoker : array) {
            invoker.invoke(args);
        }
    }

    /**
     * Publishes a typed event to Java listeners.
     *
     * @param event event contract
     * @param args  positional event arguments
     */
    public static void publish(Event event, Object... args) {
        invoke(event, args);
    }

    /**
     * Publishes a named event to Java listeners.
     *
     * @param eventName event name
     * @param args      positional event arguments
     */
    public static void publish(String eventName, Object... args) {
        invoke(eventName, args);
    }

    /**
     * Publishes a typed event to Java listeners and then forwards it to Project Zomboid Lua callbacks.
     *
     * @param event event contract
     * @param args  positional event arguments
     * @return {@code true} if the event was forwarded to Lua
     */
    public static boolean publishBoth(Event event, Object... args) {
        if (event == null) {
            return false;
        }
        invoke(event, args);
        return PzEventBridge.forwardToLua(event.getName(), args);
    }

    /**
     * Publishes a named event to Java listeners and then forwards it to Project Zomboid Lua callbacks.
     *
     * @param eventName event name
     * @param args      positional event arguments
     * @return {@code true} if the event was forwarded to Lua
     */
    public static boolean publishBoth(String eventName, Object... args) {
        invoke(eventName, args);
        return PzEventBridge.forwardToLua(eventName, args);
    }

    /**
     * Forwards a typed event to Project Zomboid Lua callbacks without notifying Java listeners.
     *
     * @param event event contract
     * @param args  positional event arguments
     * @return {@code true} if the event was forwarded to Lua
     */
    public static boolean publishToLua(Event event, Object... args) {
        return event != null && PzEventBridge.forwardToLua(event.getName(), args);
    }

    /**
     * Forwards a named event to Project Zomboid Lua callbacks without notifying Java listeners.
     *
     * @param eventName event name
     * @param args      positional event arguments
     * @return {@code true} if the event was forwarded to Lua
     */
    public static boolean publishToLua(String eventName, Object... args) {
        return PzEventBridge.forwardToLua(eventName, args);
    }

    /**
     * Creates a lightweight custom event by name.
     *
     * @param eventName unique custom event name
     * @return immutable event instance
     */
    public static Event createEvent(String eventName) {
        return Event.custom(eventName);
    }

    /**
     * Returns a snapshot of event names currently known to Project Zomboid.
     * <p>
     * The catalog is populated from {@code LuaEventManager} and therefore follows
     * game updates automatically. Custom Lua events can be refreshed explicitly with
     * {@link #refreshGameEvents()}.
     *
     * @return unmodifiable set of known game event names
     */
    public static Set<String> getKnownGameEvents() {
        return PzEventCatalog.getEventNames();
    }

    /**
     * Refreshes the Project Zomboid event-name catalog from the game runtime.
     */
    public static void refreshGameEvents() {
        PzEventCatalog.refresh();
    }

    /**
     * Checks whether the given name is currently known to Project Zomboid.
     *
     * @param eventName event name
     * @return {@code true} if the event name is present in the game catalog
     */
    public static boolean isGameEvent(String eventName) {
        return eventName != null && PzEventCatalog.getEventNames().contains(eventName.strip());
    }

    /**
     * Checks whether at least one listener is registered for the event.
     *
     * @param eventName event name
     * @return {@code true} if the event has listeners
     */
    public static boolean hasListeners(String eventName) {
        String key = normalizeEventName(eventName);
        if (key == null) {
            return false;
        }
        HandlerInvoker[] array = HANDLERS.get(key);
        return array != null && array.length > 0;
    }

    /**
     * Returns the number of listeners registered for the event.
     *
     * @param eventName event name
     * @return listener count, or zero if the event is unknown
     */
    public static int getSubscriberCount(String eventName) {
        String key = normalizeEventName(eventName);
        if (key == null) {
            return 0;
        }
        HandlerInvoker[] array = HANDLERS.get(key);
        return array == null ? 0 : array.length;
    }

    /**
     * Returns a snapshot of all event names with registered listeners.
     *
     * @return unmodifiable set of event names
     */
    public static Set<String> getRegisteredEvents() {
        return Collections.unmodifiableSet(new HashSet<>(HANDLERS.keySet()));
    }

    private static String resolveEventName(SubscribeEvent annotation) {
        String custom = annotation.custom().strip();
        if (!custom.isEmpty()) {
            return custom;
        }
        return normalizeEventName(annotation.value().getName());
    }

    private static String normalizeEventName(String eventName) {
        if (eventName == null) {
            return null;
        }
        String normalized = eventName.strip();
        return normalized.isEmpty() ? null : normalized;
    }

    private static List<Class<?>> collectTypes(Class<?> listenerClass) {
        Set<Class<?>> types = new LinkedHashSet<>();
        collectTypes(listenerClass, types);
        return new ArrayList<>(types);
    }

    private static void collectTypes(Class<?> type, Set<Class<?>> target) {
        if (type == null || type == Object.class || !target.add(type)) {
            return;
        }
        for (Class<?> iface : type.getInterfaces()) {
            collectTypes(iface, target);
        }
        collectTypes(type.getSuperclass(), target);
    }

    private static String methodSignature(Method method) {
        return method.getName() + Arrays.toString(method.getParameterTypes());
    }

    private static boolean containsHandler(HandlerInvoker[] handlers, HandlerInvoker candidate) {
        for (HandlerInvoker handler : handlers) {
            if (handler.target() == candidate.target() && handler.method().equals(candidate.method())) {
                return true;
            }
        }
        return false;
    }

    private static void removeHandler(HandlerInvoker invoker) {
        HandlerInvoker[] current = HANDLERS.get(invoker.eventName());
        if (current == null) {
            return;
        }

        List<HandlerInvoker> list = new ArrayList<>(Arrays.asList(current));
        if (!list.removeIf(handler -> handler == invoker)) {
            return;
        }

        if (list.isEmpty()) {
            HANDLERS.remove(invoker.eventName());
        } else {
            HANDLERS.put(invoker.eventName(), list.toArray(HandlerInvoker[]::new));
        }
    }

    private static void removeFromOwner(HandlerInvoker invoker) {
        Object owner = invoker.owner();
        if (owner == null) {
            return;
        }

        Set<HandlerInvoker> owned = OWNED_HANDLERS.get(owner);
        if (owned == null) {
            return;
        }

        owned.remove(invoker);
        if (owned.isEmpty()) {
            OWNED_HANDLERS.remove(owner, owned);
        }
    }

    /**
     * Internal invoker for high-performance execution of an event listener method.
     */
    private record HandlerInvoker(
            Object target,
            Object owner,
            Method method,
            MethodHandle handle,
            Class<?>[] parameterTypes,
            int priority,
            String eventName
    ) {
        private void invoke(Object[] incomingArgs) {
            try {
                int paramCount = parameterTypes.length;
                if (paramCount == 0) {
                    handle.invoke();
                    return;
                }

                int available = incomingArgs != null ? incomingArgs.length : 0;

                if (paramCount == available && isDirectCompatible(incomingArgs)) {
                    handle.invokeWithArguments(incomingArgs);
                    return;
                }

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
            } catch (ClassCastException ex) {
                LOGGER.error("Type mismatch when invoking listener method '{}' for event '{}'",
                        method, eventName, ex);
            } catch (VirtualMachineError fatal) {
                throw fatal;
            } catch (Throwable ex) {
                LOGGER.error("Unhandled exception in listener method '{}' for event '{}'",
                        method, eventName, ex);
            }
        }

        private boolean isDirectCompatible(Object[] args) {
            for (int i = 0; i < parameterTypes.length; i++) {
                Class<?> parameterType = parameterTypes[i];
                Object arg = args[i];

                if (arg == null) {
                    if (parameterType.isPrimitive()) {
                        return false;
                    }
                } else if (parameterType.isPrimitive()) {
                    if (!isPrimitiveWrapperCompatible(parameterType, arg.getClass())) {
                        return false;
                    }
                } else if (!parameterType.isInstance(arg)) {
                    return false;
                }
            }
            return true;
        }

        private static boolean isPrimitiveWrapperCompatible(Class<?> primitive, Class<?> wrapper) {
            if (primitive == boolean.class) {
                return wrapper == Boolean.class;
            }
            if (primitive == byte.class) {
                return wrapper == Byte.class;
            }
            if (primitive == short.class) {
                return wrapper == Short.class;
            }
            if (primitive == int.class) {
                return wrapper == Integer.class;
            }
            if (primitive == long.class) {
                return wrapper == Long.class;
            }
            if (primitive == float.class) {
                return wrapper == Float.class;
            }
            if (primitive == double.class) {
                return wrapper == Double.class;
            }
            if (primitive == char.class) {
                return wrapper == Character.class;
            }
            return false;
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
            if (targetType == char.class) return (char) number.intValue();
            return number;
        }
    }
}
