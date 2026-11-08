package com.avrix.api.lua.script;

import se.krka.kahlua.vm.KahluaTable;
import se.krka.kahlua.vm.KahluaTableIterator;
import zombie.ZomboidFileSystem;
import zombie.Lua.LuaManager;
import zombie.iso.enums.ChunkGenerationStatus;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Tracks which globals in the shared Lua environment were created by Project Zomboid,
 * Avrix exposures, game Lua, mod Lua and custom Avrix scripts.
 * <p>
 * The classification is used by isolated Lua environments to decide which globals they are
 * allowed to see. It is intentionally best-effort: a global overwritten by game or mod Lua
 * keeps its original category, while a global modified by a custom shared script is moved to
 * the custom category.
 */
final class LuaVisibilityTracker {

    private static final Set<String> STANDARD_GLOBALS = Set.of(
            "_G", "_VERSION",
            "array", "assert", "bytecodeloader", "collectgarbage", "coroutine", "debug",
            "debugstacktrace", "dofile", "error", "getfenv", "getmetatable", "ipairs",
            "load", "loadfile", "loadstream", "loadstring", "math", "newrandom", "next",
            "os", "pairs", "pcall", "print", "rawequal", "rawget", "rawset", "select",
            "setfenv", "setmetatable", "string", "table", "tonumber", "tostring", "type",
            "unpack", "xpcall"
    );

    private static final Set<String> PROJECT_ZOMBOID_EXPOSURES = new HashSet<>();
    private static final Set<String> AVRIX_EXPOSURES = new HashSet<>();
    private static final Set<String> GAME_LUA_GLOBALS = new HashSet<>();
    private static final Set<String> MOD_LUA_GLOBALS = new HashSet<>();
    private static final Set<String> CUSTOM_LUA_GLOBALS = new HashSet<>();

    private static Set<String> currentLoadDirGameGlobals;
    private static boolean currentLoadDirModStarted;

    private LuaVisibilityTracker() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Resets the tracker for a new Lua environment.
     *
     * @param environment new Lua environment
     */
    static void reset(KahluaTable environment) {
        PROJECT_ZOMBOID_EXPOSURES.clear();
        AVRIX_EXPOSURES.clear();
        GAME_LUA_GLOBALS.clear();
        MOD_LUA_GLOBALS.clear();
        CUSTOM_LUA_GLOBALS.clear();
        currentLoadDirGameGlobals = null;
        currentLoadDirModStarted = false;

        if (environment != null) {
            Set<String> current = snapshotKeys(environment);
            current.removeAll(STANDARD_GLOBALS);
            PROJECT_ZOMBOID_EXPOSURES.addAll(current);
        }
    }

    /**
     * Captures globals added by Avrix after Project Zomboid exposed its own classes.
     */
    static void captureAvrixExposureKeys() {
        if (LuaManager.env == null) {
            return;
        }

        Set<String> current = snapshotKeys(LuaManager.env);
        current.removeAll(STANDARD_GLOBALS);
        current.removeAll(PROJECT_ZOMBOID_EXPOSURES);
        AVRIX_EXPOSURES.addAll(current);
    }

    /**
     * Starts tracking a {@code LuaManager.LoadDirBase} call.
     */
    static void beginLoadDir() {
        currentLoadDirGameGlobals = new HashSet<>();
        currentLoadDirModStarted = false;
    }

    /**
     * Records a Lua file loaded during {@code LoadDirBase}.
     *
     * @param filename absolute Lua file path
     */
    static void onRunLua(String filename) {
        onRunLua(filename, isModFile(filename));
    }

    /**
     * Records a Lua file loaded during {@code LoadDirBase} with an explicit origin.
     *
     * @param filename absolute Lua file path
     * @param modFile  whether the file belongs to a mod
     */
    static void onRunLua(String filename, boolean modFile) {
        if (currentLoadDirGameGlobals == null || currentLoadDirModStarted || filename == null) {
            return;
        }
        if (modFile) {
            currentLoadDirGameGlobals.addAll(currentGameOrModGlobals());
            currentLoadDirModStarted = true;
        }
    }

    /**
     * Finishes tracking a {@code LuaManager.LoadDirBase} call.
     */
    static void endLoadDir() {
        if (currentLoadDirGameGlobals == null) {
            return;
        }

        Set<String> current = currentGameOrModGlobals();
        if (!currentLoadDirModStarted) {
            GAME_LUA_GLOBALS.addAll(current);
        } else {
            GAME_LUA_GLOBALS.addAll(currentLoadDirGameGlobals);
            current.removeAll(currentLoadDirGameGlobals);
            MOD_LUA_GLOBALS.addAll(current);
        }

        currentLoadDirGameGlobals = null;
        currentLoadDirModStarted = false;
    }

    /**
     * Finalizes category tracking when game and mod Lua files have finished loading.
     */
    static void finishLuaLoad() {
        endLoadDir();
    }

    /**
     * Marks globals added or changed by a custom script loaded in the shared environment.
     *
     * @param beforeValues global values captured before script execution
     */
    static void markCustomKeys(Map<String, Object> beforeValues) {
        if (LuaManager.env == null || beforeValues == null) {
            return;
        }

        Set<String> current = snapshotKeys(LuaManager.env);
        for (String key : current) {
            if (STANDARD_GLOBALS.contains(key)) {
                continue;
            }

            Object previous = beforeValues.get(key);
            Object value = LuaManager.env.rawget(key);
            if (!beforeValues.containsKey(key) || !Objects.equals(previous, value)) {
                CUSTOM_LUA_GLOBALS.add(key);
                PROJECT_ZOMBOID_EXPOSURES.remove(key);
                AVRIX_EXPOSURES.remove(key);
                GAME_LUA_GLOBALS.remove(key);
                MOD_LUA_GLOBALS.remove(key);
            }
        }
    }

    /**
     * Returns a snapshot of the current global values.
     *
     * @return global name to value map
     */
    static Map<String, Object> snapshotValues() {
        Map<String, Object> values = new HashMap<>();
        if (LuaManager.env == null) {
            return values;
        }

        KahluaTableIterator iterator = LuaManager.env.iterator();
        while (iterator.advance()) {
            Object key = iterator.getKey();
            if (key instanceof String string) {
                values.put(string, iterator.getValue());
            }
        }
        return values;
    }

    /**
     * Returns the standard Kahlua global names.
     *
     * @return immutable standard global names
     */
    static Set<String> standardGlobals() {
        return STANDARD_GLOBALS;
    }

    /**
     * Checks whether a global belongs to Project Zomboid's Java exposure.
     *
     * @param key global name
     * @return {@code true} if the key belongs to the category
     */
    static boolean isProjectZomboidExposure(String key) {
        return PROJECT_ZOMBOID_EXPOSURES.contains(key);
    }

    /**
     * Checks whether a global belongs to Avrix's Java exposure.
     *
     * @param key global name
     * @return {@code true} if the key belongs to the category
     */
    static boolean isAvrixExposure(String key) {
        return AVRIX_EXPOSURES.contains(key);
    }

    /**
     * Checks whether a global was created by game Lua.
     *
     * @param key global name
     * @return {@code true} if the key belongs to the category
     */
    static boolean isGameLua(String key) {
        return GAME_LUA_GLOBALS.contains(key);
    }

    /**
     * Checks whether a global was created by mod Lua.
     *
     * @param key global name
     * @return {@code true} if the key belongs to the category
     */
    static boolean isModLua(String key) {
        return MOD_LUA_GLOBALS.contains(key);
    }

    /**
     * Checks whether a global was created by a custom script in the shared environment.
     *
     * @param key global name
     * @return {@code true} if the key belongs to the category
     */
    static boolean isCustomLua(String key) {
        return CUSTOM_LUA_GLOBALS.contains(key);
    }

    private static Set<String> currentGameOrModGlobals() {
        Set<String> current = snapshotKeys(LuaManager.env);
        current.removeAll(STANDARD_GLOBALS);
        current.removeAll(PROJECT_ZOMBOID_EXPOSURES);
        current.removeAll(AVRIX_EXPOSURES);
        current.removeAll(CUSTOM_LUA_GLOBALS);
        return current;
    }

    private static boolean isModFile(String filename) {
        try {
            if (ZomboidFileSystem.instance == null) {
                return false;
            }
            if (ZomboidFileSystem.instance.isModded(filename) == ChunkGenerationStatus.MODDED) {
                return true;
            }

            String normalized = filename.replace('\\', '/').toLowerCase(Locale.ROOT);
            for (String modId : ZomboidFileSystem.instance.getModIDs()) {
                String modDirectory = ZomboidFileSystem.instance.getModDir(modId);
                if (modDirectory == null) {
                    continue;
                }
                String root = modDirectory.replace('\\', '/').toLowerCase(Locale.ROOT);
                if (normalized.startsWith(root)) {
                    return true;
                }
            }
        } catch (Exception | LinkageError ex) {
            return false;
        }
        return false;
    }

    private static Set<String> snapshotKeys(KahluaTable environment) {
        Set<String> keys = new HashSet<>();
        if (environment == null) {
            return keys;
        }
        KahluaTableIterator iterator = environment.iterator();
        while (iterator.advance()) {
            Object key = iterator.getKey();
            if (key instanceof String string) {
                keys.add(string);
            }
        }
        return keys;
    }
}
