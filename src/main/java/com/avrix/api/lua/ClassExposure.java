package com.avrix.api.lua;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.krka.kahlua.vm.KahluaTable;
import se.krka.kahlua.vm.KahluaUtil;
import zombie.HiddenFromLua;
import zombie.Lua.LuaManager;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.avrix.api.lua.LuaExposureSupport.CLASS_FIELD_NAME;

/**
 * Exposes a Java class to Lua under its simple name and full package path.
 * <p>
 * The exposure mirrors Project Zomboid's own class layout: constructors are available as
 * {@code new}, public static methods and fields live on the class table, and public instance
 * methods are registered on the class metatable. Methods and fields annotated with
 * {@link HiddenFromLua} are skipped.
 */
final class ClassExposure implements LuaExposure {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClassExposure.class);
    private static final String CONSTRUCTOR_NAME = "new";

    private final Class<?> type;
    private final Map<String, Object> instanceMethods = new LinkedHashMap<>();

    private KahluaTable appliedIn;
    private KahluaTable classTable;
    private boolean simpleNameOwned;
    private boolean typeMapOwned;
    private PackageTarget packageTarget;

    /**
     * Creates a class exposure.
     *
     * @param type Java type to expose
     */
    ClassExposure(Class<?> type) {
        this.type = type;
    }

    /**
     * Applies the class exposure to the given Lua environment.
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

        KahluaTable table = LuaManager.platform.newTable();
        table.rawset(CLASS_FIELD_NAME, type);

        exposeConstructors(exposer, table);
        exposeStaticMethods(exposer, table);
        exposeStaticFields(table);
        exposeInstanceMethods(exposer, environment, existingMetatable);

        classTable = table;

        installSimpleName(environment, table);
        installPackagePath(environment, table);
        installTypeMap(exposer, environment);

        appliedIn = environment;
    }

    /**
     * Removes the class exposure from the given Lua environment.
     *
     * @param environment Lua environment to clean up
     */
    @Override
    public void remove(KahluaTable environment) {
        if (appliedIn != environment) {
            return;
        }

        removeInstanceMethods(environment);

        if (simpleNameOwned) {
            String simpleName = type.getSimpleName();
            if (environment.rawget(simpleName) == classTable) {
                environment.rawset(simpleName, null);
            }
        }

        if (packageTarget != null && packageTarget.parent().rawget(packageTarget.key()) == classTable) {
            packageTarget.parent().rawset(packageTarget.key(), null);
        }

        LuaManager.Exposer exposer = LuaManager.exposer;
        if (typeMapOwned && exposer != null && !LuaExposer.hasOtherExposureFor(type, this)) {
            exposer.typeMap.remove(type.getSimpleName(), type);
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
        classTable = null;
        simpleNameOwned = false;
        typeMapOwned = false;
        packageTarget = null;
        instanceMethods.clear();
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

    private void exposeConstructors(LuaManager.Exposer exposer, KahluaTable table) {
        for (Constructor<?> constructor : type.getConstructors()) {
            if (constructor.isAnnotationPresent(HiddenFromLua.class)) {
                continue;
            }
            try {
                exposer.exposeGlobalClassFunction(table, type, constructor, CONSTRUCTOR_NAME);
            } catch (Exception | LinkageError ex) {
                LOGGER.error("Failed to expose constructor '{}' of '{}'", constructor, type.getName(), ex);
            }
        }
    }

    private void exposeStaticMethods(LuaManager.Exposer exposer, KahluaTable table) {
        for (Method method : type.getMethods()) {
            if (method.isAnnotationPresent(HiddenFromLua.class) || !Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            try {
                exposer.exposeGlobalClassFunction(table, type, method, method.getName());
            } catch (Exception | LinkageError ex) {
                LOGGER.error("Failed to expose static method '{}.{}'",
                        type.getName(), method.getName(), ex);
            }
        }
    }

    private void exposeStaticFields(KahluaTable table) {
        for (Field field : type.getFields()) {
            if (field.isAnnotationPresent(HiddenFromLua.class) || !Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            try {
                Object value = field.get(null);
                if (value == null) {
                    continue;
                }
                if (table.rawget(field.getName()) != null) {
                    LOGGER.warn("Skipping static field '{}.{}': Lua name is already in use",
                            type.getName(), field.getName());
                    continue;
                }
                table.rawset(field.getName(), value);
            } catch (IllegalAccessException ex) {
                LOGGER.warn("Failed to expose static field '{}.{}'", type.getName(), field.getName(), ex);
            }
        }
    }

    private void exposeInstanceMethods(LuaManager.Exposer exposer, KahluaTable environment,
                                       KahluaTable existingMetatable) {
        Map<String, List<Method>> methodsByName = new LinkedHashMap<>();
        for (Method method : type.getMethods()) {
            if (method.isAnnotationPresent(HiddenFromLua.class) || Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            methodsByName.computeIfAbsent(method.getName(), ignored -> new ArrayList<>()).add(method);
        }

        for (Map.Entry<String, List<Method>> entry : methodsByName.entrySet()) {
            String luaName = entry.getKey();
            if (LuaExposureSupport.hasIndexEntry(existingMetatable, luaName)) {
                LOGGER.warn("Skipping instance methods '{}' of '{}': Lua name is already exposed",
                        luaName, type.getName());
                continue;
            }

            for (Method method : entry.getValue()) {
                try {
                    exposer.exposeMethod(type, method, luaName, environment);
                } catch (Exception | LinkageError ex) {
                    LOGGER.error("Failed to expose instance method '{}.{}'",
                            type.getName(), method.getName(), ex);
                }
            }

            KahluaTable index = LuaExposureSupport.getIndexTable(type, environment);
            Object invoker = index == null ? null : index.rawget(luaName);
            if (invoker != null) {
                instanceMethods.put(luaName, invoker);
            }
        }
    }

    private void installSimpleName(KahluaTable environment, KahluaTable table) {
        String simpleName = type.getSimpleName();
        Object existing = environment.rawget(simpleName);
        if (existing == null) {
            environment.rawset(simpleName, table);
            simpleNameOwned = true;
            return;
        }
        if (LuaExposureSupport.isClassTable(existing, type)) {
            LOGGER.debug("Class '{}' is already exposed as '{}'", type.getName(), simpleName);
            return;
        }
        LOGGER.warn("Cannot expose class '{}' as global '{}': the name is already in use",
                type.getName(), simpleName);
    }

    private void installPackagePath(KahluaTable environment, KahluaTable table) {
        PackageTarget target = resolvePackageTarget(environment, type);
        if (target == null) {
            return;
        }

        Object existing = target.parent().rawget(target.key());
        if (existing == null) {
            target.parent().rawset(target.key(), table);
            packageTarget = target;
            return;
        }
        if (LuaExposureSupport.isClassTable(existing, type)) {
            LOGGER.debug("Class '{}' is already exposed under '{}'", type.getName(), target.key());
            return;
        }
        LOGGER.warn("Cannot expose class '{}' under package path '{}': the name is already in use",
                type.getName(), target.key());
    }

    private void installTypeMap(LuaManager.Exposer exposer, KahluaTable environment) {
        if (!simpleNameOwned && !LuaExposureSupport.isClassTable(environment.rawget(type.getSimpleName()), type)) {
            return;
        }

        String simpleName = type.getSimpleName();
        Class<?> existing = exposer.typeMap.get(simpleName);
        if (existing == null) {
            exposer.typeMap.put(simpleName, type);
            typeMapOwned = true;
            return;
        }
        if (existing != type) {
            LOGGER.warn("Cannot register Lua type name '{}': it already maps to '{}'",
                    simpleName, existing.getName());
        }
    }

    private void removeInstanceMethods(KahluaTable environment) {
        KahluaTable index = LuaExposureSupport.getIndexTable(type, environment);
        if (index == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : instanceMethods.entrySet()) {
            if (index.rawget(entry.getKey()) == entry.getValue()) {
                index.rawset(entry.getKey(), null);
            }
        }
    }

    private static PackageTarget resolvePackageTarget(KahluaTable environment, Class<?> type) {
        String normalizedName = type.getName().replace('$', '.');
        String[] parts = normalizedName.split("\\.");
        if (parts.length < 2) {
            return null;
        }

        KahluaTable parent = environment;
        for (int index = 0; index < parts.length - 1; index++) {
            Object next = parent.rawget(parts[index]);
            if (next instanceof KahluaTable table) {
                parent = table;
                continue;
            }
            if (next != null) {
                LOGGER.warn("Cannot expose '{}' under package path: '{}' is not a table",
                        type.getName(), parts[index]);
                return null;
            }
            KahluaTable created = LuaManager.platform.newTable();
            parent.rawset(parts[index], created);
            parent = created;
        }
        return new PackageTarget(parent, parts[parts.length - 1]);
    }

    /**
     * Returns a diagnostic representation of this exposure.
     *
     * @return exposure description
     */
    @Override
    public String toString() {
        return "ClassExposure[" + type.getName() + "]";
    }

    /**
     * Parent table and key of a class table inside the full package path.
     *
     * @param parent parent table
     * @param key    class table key
     */
    private record PackageTarget(KahluaTable parent, String key) {
    }
}
