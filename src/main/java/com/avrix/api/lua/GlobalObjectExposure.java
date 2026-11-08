package com.avrix.api.lua;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.krka.kahlua.integration.annotations.LuaMethod;
import se.krka.kahlua.integration.expose.AnnotationUtil;
import se.krka.kahlua.vm.KahluaTable;
import zombie.HiddenFromLua;
import zombie.Lua.LuaManager;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exposes public target-object methods annotated with {@link LuaMethod} as global functions.
 * <p>
 * Only methods with {@code global = true} are considered. Existing Lua names are never
 * overwritten, and all names added by this exposure are tracked for later removal.
 */
final class GlobalObjectExposure implements LuaExposure {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalObjectExposure.class);

    private final Object target;
    private final Map<String, Object> exposedValues = new LinkedHashMap<>();

    private KahluaTable appliedIn;

    /**
     * Creates a global object exposure.
     *
     * @param target object whose annotated methods should be exposed
     */
    GlobalObjectExposure(Object target) {
        this.target = target;
    }

    @Override
    public void apply(KahluaTable environment) {
        if (appliedIn == environment) {
            return;
        }

        LuaManager.Exposer exposer = LuaManager.exposer;
        if (exposer == null) {
            return;
        }

        Map<String, List<Method>> methodsByName = new LinkedHashMap<>();
        for (Method method : target.getClass().getMethods()) {
            if (method.isAnnotationPresent(HiddenFromLua.class)) {
                continue;
            }

            LuaMethod annotation = AnnotationUtil.getAnnotation(method, LuaMethod.class);
            if (annotation == null || !annotation.global()) {
                continue;
            }

            String luaName = annotation.name().isEmpty() ? method.getName() : annotation.name();
            methodsByName.computeIfAbsent(luaName, ignored -> new ArrayList<>()).add(method);
        }

        for (Map.Entry<String, List<Method>> entry : methodsByName.entrySet()) {
            String luaName = entry.getKey();
            if (environment.rawget(luaName) != null) {
                LOGGER.warn("Cannot expose global function '{}' from '{}': Lua name is already in use",
                        luaName, target.getClass().getName());
                continue;
            }

            for (Method method : entry.getValue()) {
                try {
                    exposer.exposeGlobalObjectFunction(environment, target, method, luaName);
                } catch (Exception | LinkageError ex) {
                    LOGGER.error("Failed to expose global function '{}' for '{}.{}'",
                            luaName, target.getClass().getName(), method.getName(), ex);
                }
            }

            Object exposedValue = environment.rawget(luaName);
            if (exposedValue != null) {
                exposedValues.put(luaName, exposedValue);
            }
        }

        appliedIn = environment;
    }

    @Override
    public void remove(KahluaTable environment) {
        if (appliedIn != environment) {
            return;
        }

        for (Map.Entry<String, Object> entry : exposedValues.entrySet()) {
            if (environment.rawget(entry.getKey()) == entry.getValue()) {
                environment.rawset(entry.getKey(), null);
            }
        }

        invalidate();
    }

    @Override
    public boolean isAppliedIn(KahluaTable environment) {
        return appliedIn == environment;
    }

    @Override
    public void invalidate() {
        appliedIn = null;
        exposedValues.clear();
    }

    @Override
    public boolean affects(Class<?> type) {
        return false;
    }

    @Override
    public String toString() {
        return "GlobalObjectExposure[" + target.getClass().getName() + "]";
    }
}
