package com.avrix.api.lua;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.krka.kahlua.vm.KahluaTable;
import zombie.Lua.LuaManager;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Exposes a single Java method as a global Lua function.
 * <p>
 * Static methods are bound to their declaring class. Instance methods are bound to the
 * supplied target object. An occupied Lua name is never overwritten.
 */
final class GlobalFunctionExposure implements LuaExposure {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalFunctionExposure.class);

    private final String luaName;
    private final Method method;
    private final Object target;

    private KahluaTable appliedIn;
    private Object exposedValue;

    /**
     * Creates a global function exposure.
     *
     * @param luaName Lua function name
     * @param method  Java method to expose
     * @param target  bound target for instance methods, or {@code null} for static methods
     */
    GlobalFunctionExposure(String luaName, Method method, Object target) {
        this.luaName = luaName;
        this.method = method;
        this.target = target;
    }

    /**
     * Applies the global function exposure to the given Lua environment.
     *
     * @param environment target Lua environment
     */
    @Override
    public void apply(KahluaTable environment) {
        if (appliedIn == environment) {
            return;
        }

        LuaManager.Exposer exposer = LuaManager.exposer;
        if (exposer == null) {
            return;
        }

        if (environment.rawget(luaName) != null) {
            LOGGER.warn("Cannot expose global function '{}': Lua name is already in use", luaName);
            appliedIn = environment;
            return;
        }

        try {
            if (Modifier.isStatic(method.getModifiers())) {
                exposer.exposeGlobalClassFunction(environment, method.getDeclaringClass(), method, luaName);
            } else {
                exposer.exposeGlobalObjectFunction(environment, target, method, luaName);
            }
            exposedValue = environment.rawget(luaName);
        } catch (Exception | LinkageError ex) {
            LOGGER.error("Failed to expose global function '{}' for '{}.{}'",
                    luaName, method.getDeclaringClass().getName(), method.getName(), ex);
        }

        appliedIn = environment;
    }

    /**
     * Removes the global function exposure from the given Lua environment.
     *
     * @param environment Lua environment to clean up
     */
    @Override
    public void remove(KahluaTable environment) {
        if (appliedIn != environment) {
            return;
        }

        if (exposedValue != null && environment.rawget(luaName) == exposedValue) {
            environment.rawset(luaName, null);
        }

        invalidate();
    }

    /**
     * Checks whether this exposure is applied to the given environment.
     *
     * @param environment Lua environment to check
     * @return {@code true} if applied
     */
    @Override
    public boolean isAppliedIn(KahluaTable environment) {
        return appliedIn == environment;
    }

    /**
     * Clears environment-specific state after a Lua reset.
     */
    @Override
    public void invalidate() {
        appliedIn = null;
        exposedValue = null;
    }

    /**
     * Checks whether this exposure affects the given Java type.
     *
     * @param type Java type
     * @return always {@code false}
     */
    @Override
    public boolean affects(Class<?> type) {
        return false;
    }

    /**
     * Returns a diagnostic representation of this exposure.
     *
     * @return exposure description
     */
    @Override
    public String toString() {
        return "GlobalFunctionExposure[" + luaName + " -> "
                + method.getDeclaringClass().getName() + "." + method.getName() + "]";
    }
}
