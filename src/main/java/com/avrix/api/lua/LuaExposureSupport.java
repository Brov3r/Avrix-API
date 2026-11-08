package com.avrix.api.lua;

import se.krka.kahlua.vm.KahluaTable;
import se.krka.kahlua.vm.KahluaUtil;
import zombie.Lua.LuaManager;

/**
 * Shared low-level helpers for Java-to-Lua exposures.
 */
final class LuaExposureSupport {

    /**
     * Field name used by Project Zomboid class tables to store the Java class.
     */
    static final String CLASS_FIELD_NAME = "class";

    /**
     * Metatable field name used for method lookup.
     */
    static final String INDEX_FIELD_NAME = "__index";

    private LuaExposureSupport() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Returns the metatable registered for the given Java type.
     *
     * @param type        Java type
     * @param environment Lua environment
     * @return metatable, or {@code null} when the type has no metatable
     */
    static KahluaTable getMetatable(Class<?> type, KahluaTable environment) {
        if (type == null || environment == null || LuaManager.platform == null) {
            return null;
        }
        KahluaTable metatables = KahluaUtil.getClassMetatables(LuaManager.platform, environment);
        Object metatable = metatables.rawget(type);
        return metatable instanceof KahluaTable table ? table : null;
    }

    /**
     * Returns the {@code __index} table of the given Java type.
     *
     * @param type        Java type
     * @param environment Lua environment
     * @return index table, or {@code null} when absent
     */
    static KahluaTable getIndexTable(Class<?> type, KahluaTable environment) {
        KahluaTable metatable = getMetatable(type, environment);
        if (metatable == null) {
            return null;
        }
        Object index = metatable.rawget(INDEX_FIELD_NAME);
        return index instanceof KahluaTable table ? table : null;
    }

    /**
     * Checks whether the given metatable already has a method with the requested Lua name.
     *
     * @param metatable metatable to inspect
     * @param luaName   Lua method name
     * @return {@code true} if the name is already occupied
     */
    static boolean hasIndexEntry(KahluaTable metatable, String luaName) {
        if (metatable == null) {
            return false;
        }
        Object index = metatable.rawget(INDEX_FIELD_NAME);
        return index instanceof KahluaTable table && table.rawget(luaName) != null;
    }

    /**
     * Checks whether a value is a class table for the given Java type.
     *
     * @param candidate candidate value
     * @param type      expected Java type
     * @return {@code true} if the value is the class table
     */
    static boolean isClassTable(Object candidate, Class<?> type) {
        return candidate instanceof KahluaTable table && table.rawget(CLASS_FIELD_NAME) == type;
    }
}
