package com.avrix.api.lua;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.krka.kahlua.vm.KahluaTable;
import se.krka.kahlua.vm.KahluaUtil;
import zombie.Lua.LuaManager;

import java.lang.reflect.Method;

/**
 * Exposes a single public instance method on a Java class metatable.
 * <p>
 * The method is exposed under an explicit Lua name. If the name is already occupied when the
 * exposure is applied, the exposure is skipped instead of overriding an existing function.
 */
final class MethodExposure implements LuaExposure {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodExposure.class);

    private final Class<?> type;
    private final Method method;
    private final String luaName;

    private KahluaTable appliedIn;
    private Object invoker;

    /**
     * Creates a method exposure.
     *
     * @param type    declaring Java type
     * @param method  method to expose
     * @param luaName Lua-visible method name
     */
    MethodExposure(Class<?> type, Method method, String luaName) {
        this.type = type;
        this.method = method;
        this.luaName = luaName;
    }

    /**
     * Applies the method exposure to the given Lua environment.
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

        KahluaTable metatables = KahluaUtil.getClassMetatables(LuaManager.platform, environment);
        Object existing = metatables.rawget(type);
        KahluaTable existingMetatable = existing instanceof KahluaTable meta ? meta : null;

        if (LuaExposureSupport.hasIndexEntry(existingMetatable, luaName)) {
            LOGGER.warn("Cannot expose method '{}.{}' as '{}': Lua name is already in use",
                    type.getName(), method.getName(), luaName);
            appliedIn = environment;
            return;
        }

        try {
            exposer.exposeMethod(type, method, luaName, environment);
            KahluaTable index = LuaExposureSupport.getIndexTable(type, environment);
            invoker = index == null ? null : index.rawget(luaName);
        } catch (Exception | LinkageError ex) {
            LOGGER.error("Failed to expose method '{}.{}' as '{}'",
                    type.getName(), method.getName(), luaName, ex);
        }

        appliedIn = environment;
    }

    /**
     * Removes the method exposure from the given Lua environment.
     *
     * @param environment Lua environment to clean up
     */
    @Override
    public void remove(KahluaTable environment) {
        if (appliedIn != environment) {
            return;
        }

        if (invoker != null) {
            KahluaTable index = LuaExposureSupport.getIndexTable(type, environment);
            if (index != null && index.rawget(luaName) == invoker) {
                index.rawset(luaName, null);
            }
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
        invoker = null;
    }

    /**
     * Checks whether this exposure affects the given Java type.
     *
     * @param candidate Java type
     * @return {@code true} if the type is exposed by this entry
     */
    @Override
    public boolean affects(Class<?> candidate) {
        return type == candidate;
    }

    /**
     * Returns a diagnostic representation of this exposure.
     *
     * @return exposure description
     */
    @Override
    public String toString() {
        return "MethodExposure[" + type.getName() + "." + method.getName() + " -> " + luaName + "]";
    }
}
