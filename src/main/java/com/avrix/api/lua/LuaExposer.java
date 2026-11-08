package com.avrix.api.lua;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.krka.kahlua.integration.expose.LuaJavaClassExposer;
import se.krka.kahlua.vm.KahluaTable;
import se.krka.kahlua.vm.KahluaThread;
import zombie.HiddenFromLua;
import zombie.Lua.LuaManager;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Registers Java classes, methods and global functions in the Project Zomboid Lua environment.
 * <p>
 * Registrations are deferred until the game creates its Lua environment. The same registry is
 * reapplied after every {@code LuaManager.init()} call, which is required because the game
 * recreates its Lua state on world loads and Lua resets.
 * <p>
 * Registration methods return an opaque {@link LuaRegistration} handle that can be passed to
 * {@link #unregister(LuaRegistration)}. Unregistering removes the exposure from future Lua
 * environments immediately and performs a best-effort cleanup of the current environment.
 * <p>
 * Class metatables may remain cached by the Kahlua thread after unregistration, but their
 * methods are removed. The same class can therefore be exposed again without restarting Lua.
 * <p>
 * Example:
 * <pre>{@code
 * LuaExposer.exposeClass(MyApi.class);
 * LuaExposer.exposeGlobal("myFunction", myApiInstance, "handle");
 * }</pre>
 */
public final class LuaExposer {

    private static final Logger LOGGER = LoggerFactory.getLogger(LuaExposer.class);
    private static final Object LOCK = new Object();
    private static final AtomicLong IDS = new AtomicLong();
    private static final Map<Object, LuaRegistration> REGISTRATIONS = new LinkedHashMap<>();

    private static KahluaTable appliedEnvironment;

    private LuaExposer() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Exposes a Java class by its simple name and full package path.
     * <p>
     * Public constructors become {@code ClassName.new(...)}, public static methods and fields
     * are available on the class table, and public instance methods are registered on the
     * class metatable. Members annotated with {@link HiddenFromLua} are skipped.
     * <p>
     * Types used in method signatures are not exposed recursively. Register them explicitly
     * when Lua code needs to call their methods as well.
     *
     * @param type Java type to expose
     * @return registration handle
     * @throws NullPointerException     if {@code type} is {@code null}
     * @throws IllegalArgumentException if the type cannot be exposed
     */
    public static LuaRegistration exposeClass(Class<?> type) {
        Objects.requireNonNull(type, "type");
        validateType(type);
        return register(new ClassKey(type), () -> new ClassExposure(type));
    }

    /**
     * Exposes a single public instance method on a Java class metatable.
     *
     * @param method  method to expose; must be public and non-static
     * @param luaName Lua-visible method name
     * @return registration handle
     * @throws NullPointerException     if {@code method} or {@code luaName} is {@code null}
     * @throws IllegalArgumentException if the method cannot be exposed or is static
     */
    public static LuaRegistration exposeMethod(Method method, String luaName) {
        Objects.requireNonNull(method, "method");
        validateLuaName(luaName);
        validateMethod(method);
        if (Modifier.isStatic(method.getModifiers())) {
            throw new IllegalArgumentException("Static methods must be exposed as global functions: " + method);
        }
        return register(new MethodKey(method.getDeclaringClass(), luaName),
                () -> new MethodExposure(method.getDeclaringClass(), method, luaName));
    }

    /**
     * Resolves and exposes a single public instance method on a Java class metatable.
     *
     * @param type           Java type that owns the method
     * @param methodName     Java method name
     * @param luaName        Lua-visible method name
     * @param parameterTypes method parameter types; may be empty
     * @return registration handle
     * @throws NullPointerException     if {@code type}, {@code methodName} or {@code luaName} is {@code null}
     * @throws IllegalArgumentException if the type or method cannot be exposed, or the method is static
     */
    public static LuaRegistration exposeMethod(Class<?> type, String methodName, String luaName,
                                               Class<?>... parameterTypes) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(methodName, "methodName");
        validateType(type);
        validateLuaName(luaName);

        Method method = resolveMethod(type, methodName, parameterTypes);
        validateMethod(method);
        if (Modifier.isStatic(method.getModifiers())) {
            throw new IllegalArgumentException("Static methods must be exposed as global functions: " + method);
        }
        return register(new MethodKey(type, luaName), () -> new MethodExposure(type, method, luaName));
    }

    /**
     * Exposes a Java method as a global Lua function.
     * <p>
     * If {@code target} is a {@link Class}, the method is resolved on that class and must be
     * static. Otherwise the method is resolved on the target object's class and is bound to
     * the target instance.
     *
     * @param luaName        Lua function name
     * @param target         target object or declaring class for static methods
     * @param methodName     Java method name
     * @param parameterTypes method parameter types; may be empty
     * @return registration handle
     * @throws NullPointerException     if {@code target}, {@code methodName} or {@code luaName} is {@code null}
     * @throws IllegalArgumentException if the method cannot be exposed or does not match the target
     */
    public static LuaRegistration exposeGlobal(String luaName, Object target, String methodName,
                                               Class<?>... parameterTypes) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(methodName, "methodName");
        validateLuaName(luaName);

        Class<?> owner = target instanceof Class<?> type ? type : target.getClass();
        Object boundTarget = target instanceof Class<?> ? null : target;
        Method method = resolveMethod(owner, methodName, parameterTypes);
        validateMethod(method);

        if (Modifier.isStatic(method.getModifiers())) {
            boundTarget = null;
        } else if (boundTarget == null) {
            throw new IllegalArgumentException("Instance method requires an object target: " + method);
        } else if (!method.getDeclaringClass().isInstance(boundTarget)) {
            throw new IllegalArgumentException("Target is not compatible with method " + method);
        }

        Object finalTarget = boundTarget;
        return register(new GlobalKey(luaName),
                () -> new GlobalFunctionExposure(luaName, method, finalTarget));
    }

    /**
     * Exposes a Java method as a global Lua function.
     *
     * @param luaName Lua function name
     * @param method  method to expose
     * @param target  bound target for instance methods; ignored for static methods
     * @return registration handle
     * @throws NullPointerException     if {@code method} or {@code luaName} is {@code null}
     * @throws IllegalArgumentException if the method cannot be exposed or the target is incompatible
     */
    public static LuaRegistration exposeGlobal(String luaName, Method method, Object target) {
        Objects.requireNonNull(method, "method");
        validateLuaName(luaName);
        validateMethod(method);

        Object boundTarget = null;
        if (!Modifier.isStatic(method.getModifiers())) {
            if (target == null) {
                throw new IllegalArgumentException("Instance method requires an object target: " + method);
            }
            if (!method.getDeclaringClass().isInstance(target)) {
                throw new IllegalArgumentException("Target is not compatible with method " + method);
            }
            boundTarget = target;
        }

        Object finalTarget = boundTarget;
        return register(new GlobalKey(luaName),
                () -> new GlobalFunctionExposure(luaName, method, finalTarget));
    }

    /**
     * Exposes methods annotated with {@link se.krka.kahlua.integration.annotations.LuaMethod}
     * where the annotation declares {@code global = true}.
     *
     * @param target object whose annotated methods should be exposed
     * @return registration handle
     * @throws NullPointerException     if {@code target} is {@code null}
     * @throws IllegalArgumentException if the target class cannot be exposed
     */
    public static LuaRegistration exposeGlobalObject(Object target) {
        Objects.requireNonNull(target, "target");
        if (LuaJavaClassExposer.isDisallowed(target)) {
            throw new IllegalArgumentException("Class cannot be exposed to Lua: " + target.getClass().getName());
        }
        return register(new IdentityKey(target), () -> new GlobalObjectExposure(target));
    }

    /**
     * Removes a previously registered exposure.
     * <p>
     * The exposure is removed from future Lua environments immediately. If it is currently
     * applied, this method also removes its Lua tables and functions on a best-effort basis.
     *
     * @param registration registration handle
     * @return {@code true} if the registration was active and has been removed
     */
    public static boolean unregister(LuaRegistration registration) {
        if (registration == null) {
            return false;
        }

        synchronized (LOCK) {
            if (REGISTRATIONS.get(registration.key()) != registration) {
                return false;
            }
            REGISTRATIONS.remove(registration.key());

            KahluaTable environment = LuaManager.env;
            if (environment != null && registration.exposure().isAppliedIn(environment)) {
                try {
                    registration.exposure().remove(environment);
                } catch (Exception | LinkageError ex) {
                    LOGGER.error("Failed to remove Lua exposure '{}'", registration, ex);
                }
            }
            registration.exposure().invalidate();

            if (REGISTRATIONS.isEmpty()) {
                appliedEnvironment = null;
            }
            return true;
        }
    }

    /**
     * Removes all registered exposures.
     */
    public static void unregisterAll() {
        synchronized (LOCK) {
            List<LuaRegistration> snapshot = new ArrayList<>(REGISTRATIONS.values());
            REGISTRATIONS.clear();

            KahluaTable environment = LuaManager.env;
            for (LuaRegistration registration : snapshot) {
                try {
                    if (environment != null && registration.exposure().isAppliedIn(environment)) {
                        registration.exposure().remove(environment);
                    }
                } catch (Exception | LinkageError ex) {
                    LOGGER.error("Failed to remove Lua exposure '{}'", registration, ex);
                }
                registration.exposure().invalidate();
            }

            appliedEnvironment = null;
        }
    }

    /**
     * Applies every pending exposure to the current Lua environment.
     * <p>
     * This method is called by the Lua runtime mixin after {@code LuaManager.init()} and from
     * the game event loop so late registrations from background threads can be picked up.
     */
    public static void applyPending() {
        synchronized (LOCK) {
            applyPendingLocked();
        }
    }

    /**
     * Checks whether any other exposure still affects the given Java type.
     *
     * @param type     Java type
     * @param excluded exposure to ignore
     * @return {@code true} if another exposure affects the type
     */
    static boolean hasOtherExposureFor(Class<?> type, LuaExposure excluded) {
        synchronized (LOCK) {
            for (LuaRegistration registration : REGISTRATIONS.values()) {
                if (registration.exposure() != excluded && registration.exposure().affects(type)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static LuaRegistration register(Object key, Supplier<LuaExposure> factory) {
        synchronized (LOCK) {
            LuaRegistration existing = REGISTRATIONS.get(key);
            if (existing != null) {
                LOGGER.debug("Lua exposure '{}' is already registered", key);
                return existing;
            }

            LuaRegistration registration = new LuaRegistration(IDS.incrementAndGet(), key, factory.get());
            REGISTRATIONS.put(key, registration);
            applyPendingLocked();
            return registration;
        }
    }

    private static void applyPendingLocked() {
        if (!isLuaReady() || !isLuaThread()) {
            return;
        }

        KahluaTable environment = LuaManager.env;
        if (environment != appliedEnvironment) {
            for (LuaRegistration registration : REGISTRATIONS.values()) {
                registration.exposure().invalidate();
            }
            appliedEnvironment = environment;
        }

        for (LuaRegistration registration : REGISTRATIONS.values()) {
            if (registration.exposure().isAppliedIn(environment)) {
                continue;
            }
            try {
                registration.exposure().apply(environment);
            } catch (Exception | LinkageError ex) {
                LOGGER.error("Failed to apply Lua exposure '{}'", registration, ex);
            }
        }
    }

    private static boolean isLuaReady() {
        return LuaManager.env != null && LuaManager.exposer != null && LuaManager.platform != null;
    }

    private static boolean isLuaThread() {
        KahluaThread thread = LuaManager.thread;
        return thread == null
                || thread.debugOwnerThread == null
                || thread.debugOwnerThread == Thread.currentThread();
    }

    private static Method resolveMethod(Class<?> owner, String methodName, Class<?>... parameterTypes) {
        try {
            return owner.getMethod(methodName, parameterTypes == null ? new Class<?>[0] : parameterTypes);
        } catch (NoSuchMethodException ex) {
            throw new IllegalArgumentException(
                    "Public method not found: " + owner.getName() + "." + methodName, ex);
        }
    }

    private static void validateType(Class<?> type) {
        if (type.isPrimitive() || type.isArray() || type.isSynthetic()
                || type.isAnonymousClass() || type.isLocalClass() || type.getSimpleName().isBlank()) {
            throw new IllegalArgumentException("Type cannot be exposed to Lua: " + type.getName());
        }
        if (type == Class.class || ClassLoader.class.isAssignableFrom(type)) {
            throw new IllegalArgumentException("Type cannot be exposed to Lua: " + type.getName());
        }
        if (LuaJavaClassExposer.isDisallowed(type)) {
            throw new IllegalArgumentException("Type cannot be exposed to Lua: " + type.getName());
        }
    }

    private static void validateMethod(Method method) {
        if (!Modifier.isPublic(method.getModifiers())) {
            throw new IllegalArgumentException("Only public methods can be exposed: " + method);
        }
        if (method.isAnnotationPresent(HiddenFromLua.class)) {
            throw new IllegalArgumentException("Method is annotated with @HiddenFromLua: " + method);
        }
        if (LuaJavaClassExposer.isDisallowed(method.getDeclaringClass())) {
            throw new IllegalArgumentException(
                    "Declaring class cannot be exposed to Lua: " + method.getDeclaringClass().getName());
        }
    }

    private static void validateLuaName(String luaName) {
        Objects.requireNonNull(luaName, "luaName");
        if (luaName.isBlank()) {
            throw new IllegalArgumentException("Lua name cannot be blank");
        }
    }

    /**
     * Registry key for a class exposure.
     *
     * @param type Java type
     */
    private record ClassKey(Class<?> type) {
    }

    /**
     * Registry key for a single method exposure.
     *
     * @param type    Java type
     * @param luaName Lua method name
     */
    private record MethodKey(Class<?> type, String luaName) {
    }

    /**
     * Registry key for a global function exposure.
     *
     * @param luaName Lua function name
     */
    private record GlobalKey(String luaName) {
    }

    /**
     * Identity-based registry key for a global object exposure.
     *
     * @param target target object
     */
    private record IdentityKey(Object target) {

        /**
         * Compares registry keys by target identity.
         *
         * @param other object to compare
         * @return {@code true} if both keys reference the same target
         */
        @Override
        public boolean equals(Object other) {
            return other instanceof IdentityKey key && key.target == target;
        }

        /**
         * Returns the identity hash code of the target.
         *
         * @return identity hash code
         */
        @Override
        public int hashCode() {
            return System.identityHashCode(target);
        }
    }
}
