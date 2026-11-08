package com.avrix.api.lua.script;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.krka.kahlua.integration.LuaReturn;
import se.krka.kahlua.luaj.compiler.LuaCompiler;
import se.krka.kahlua.vm.JavaFunction;
import se.krka.kahlua.vm.KahluaTable;
import se.krka.kahlua.vm.LuaCallFrame;
import se.krka.kahlua.vm.LuaClosure;
import zombie.Lua.LuaManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One custom Lua environment.
 * <p>
 * The shared environment executes scripts directly in {@code LuaManager.env}. Isolated
 * environments use a separate table with a configurable visibility fallback to the shared
 * environment.
 */
public final class LuaScriptEnvironment {

    private static final Logger LOGGER = LoggerFactory.getLogger(LuaScriptEnvironment.class);
    private static final char UTF8_BOM = '\uFEFF';

    private final String name;
    private final LuaEnvironmentPolicy policy;
    private final boolean shared;

    private final Map<String, Optional<LuaScriptModule>> resolvedModules = new ConcurrentHashMap<>();
    private final Map<String, Object> loadedResults = new HashMap<>();
    private final Set<String> loadedModules = new HashSet<>();
    private final Set<String> loadingModules = new HashSet<>();

    private KahluaTable luaEnvironment;
    private Object vanillaRequire;
    private boolean initialized;

    /**
     * Creates a Lua environment.
     *
     * @param name   environment name
     * @param policy visibility policy
     * @param shared whether this is the shared environment
     */
    LuaScriptEnvironment(String name, LuaEnvironmentPolicy policy, boolean shared) {
        this.name = Objects.requireNonNull(name, "name");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.shared = shared;
    }

    /**
     * Returns the environment name.
     *
     * @return environment name
     */
    public String name() {
        return name;
    }

    /**
     * Returns the visibility policy.
     *
     * @return visibility policy
     */
    public LuaEnvironmentPolicy policy() {
        return policy;
    }

    /**
     * Checks whether this is the shared environment.
     *
     * @return {@code true} for the shared environment
     */
    public boolean isShared() {
        return shared;
    }

    /**
     * Checks whether the environment is initialized for the current Lua state.
     *
     * @return {@code true} if initialized
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Returns the underlying Kahlua table for this environment.
     *
     * @return Lua table, or {@code null} before initialization
     */
    KahluaTable luaEnvironment() {
        return luaEnvironment;
    }

    /**
     * Initializes the environment for the given shared Lua environment.
     *
     * @param mainEnvironment shared Lua environment
     */
    void initialize(KahluaTable mainEnvironment) {
        clearCaches();

        if (shared) {
            luaEnvironment = mainEnvironment;
            vanillaRequire = mainEnvironment == null ? null : mainEnvironment.rawget("require");
            initialized = luaEnvironment != null;
            return;
        }

        KahluaTable sandbox = LuaManager.platform.newTable();
        if (policy.includeStandardLibrary()) {
            for (String key : LuaVisibilityTracker.standardGlobals()) {
                if (policy.deniedGlobals().contains(key)) {
                    continue;
                }
                Object value = mainEnvironment.rawget(key);
                if (value != null) {
                    sandbox.rawset(key, value);
                }
            }
        }

        if (!policy.deniedGlobals().contains("_G")) {
            sandbox.rawset("_G", sandbox);
        }
        if (!policy.deniedGlobals().contains("require")) {
            sandbox.rawset("require", new SandboxRequireFunction(this));
        }

        KahluaTable metatable = LuaManager.platform.newTable();
        metatable.rawset("__index", new VisibilityIndexFunction(this));
        metatable.rawset("__metatable", "locked");
        sandbox.setMetatable(metatable);

        luaEnvironment = sandbox;
        vanillaRequire = mainEnvironment == null ? null : mainEnvironment.rawget("require");
        initialized = true;
    }

    /**
     * Clears Lua-state-specific data after the game resets its Lua environment.
     */
    void invalidate() {
        luaEnvironment = null;
        vanillaRequire = null;
        initialized = false;
        clearCaches();
    }

    /**
     * Loads one custom module into this environment.
     *
     * @param moduleName module name such as {@code feature/main} or {@code feature/main.lua}
     * @return load result
     */
    public LuaScriptResult loadModule(String moduleName) {
        if (!initialized) {
            return new LuaScriptResult(moduleName, null,
                    new IllegalStateException("Lua environment is not initialized: " + name));
        }
        if (!isLuaThread()) {
            return new LuaScriptResult(moduleName, null,
                    new IllegalStateException("Lua scripts must be loaded on the Lua thread"));
        }

        String path = LuaScriptLoader.normalizeModulePath(moduleName);
        if (path == null) {
            return new LuaScriptResult(moduleName, null,
                    new IllegalArgumentException("Invalid Lua module name: " + moduleName));
        }

        Optional<LuaScriptModule> module = resolveModule(path);
        if (module.isEmpty()) {
            return new LuaScriptResult(path, null,
                    new IllegalArgumentException("Lua module not found: " + moduleName));
        }
        return loadModule(module.get());
    }

    /**
     * Loads a resolved module.
     *
     * @param module resolved module
     * @return load result
     */
    LuaScriptResult loadModule(LuaScriptModule module) {
        if (!initialized) {
            return new LuaScriptResult(module.id(), null,
                    new IllegalStateException("Lua environment is not initialized: " + name));
        }
        if (!isLuaThread()) {
            return new LuaScriptResult(module.id(), null,
                    new IllegalStateException("Lua scripts must be loaded on the Lua thread"));
        }

        if (loadedModules.contains(module.id())) {
            return new LuaScriptResult(module.id(), loadedResults.get(module.id()), null);
        }

        if (!loadingModules.add(module.id())) {
            LOGGER.warn("Recursive require detected in environment '{}': {}", name, module.id());
            return new LuaScriptResult(module.id(), null, null);
        }

        Map<String, Object> beforeValues = shared ? LuaVisibilityTracker.snapshotValues() : null;
        try {
            LuaClosure closure;
            try (InputStream input = module.source().open(module.path());
                 Reader reader = openReader(input)) {
                closure = compile(reader, module.id());
            }

            LuaReturn luaReturn = LuaManager.caller.protectedCall(
                    LuaManager.thread, closure, new Object[0]);
            if (!luaReturn.isSuccess()) {
                logLuaError(module, luaReturn);
                loadedModules.add(module.id());
                loadedResults.remove(module.id());
                return new LuaScriptResult(module.id(), null,
                        new IllegalStateException("Lua module failed: " + module.id()));
            }

            Object value = luaReturn.isEmpty() ? null : luaReturn.getFirst();
            loadedModules.add(module.id());
            if (value == null) {
                loadedResults.remove(module.id());
            } else {
                loadedResults.put(module.id(), value);
            }
            return new LuaScriptResult(module.id(), value, null);
        } catch (IOException | RuntimeException | LinkageError ex) {
            LOGGER.error("Failed to load Lua module '{}' from '{}'", module.id(), name, ex);
            return new LuaScriptResult(module.id(), null, ex);
        } finally {
            loadingModules.remove(module.id());
            if (shared) {
                LuaVisibilityTracker.markCustomKeys(beforeValues);
            }
        }
    }

    /**
     * Resolves and loads one custom module for {@code require}.
     *
     * @param moduleName module name
     * @return module result, or empty when the module is not provided by custom sources
     */
    Optional<LuaScriptResult> require(String moduleName) {
        if (!initialized) {
            return Optional.empty();
        }

        String path = LuaScriptLoader.normalizeModulePath(moduleName);
        if (path == null) {
            return Optional.empty();
        }

        Optional<LuaScriptModule> module = resolveModule(path);
        if (module.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(loadModule(module.get()));
    }

    /**
     * Resolves a global from the shared environment according to the visibility policy.
     *
     * @param key global name
     * @return visible value or {@code null}
     */
    private Object resolveGlobal(String key) {
        if (policy.deniedGlobals().contains(key)) {
            return null;
        }
        if (policy.allowedGlobals().contains(key)) {
            return LuaManager.env == null ? null : LuaManager.env.rawget(key);
        }
        if (LuaVisibilityTracker.standardGlobals().contains(key)) {
            return null;
        }

        if (LuaManager.env == null) {
            return null;
        }
        Object value = LuaManager.env.rawget(key);
        if (value == null) {
            return null;
        }

        if (LuaVisibilityTracker.isProjectZomboidExposure(key)) {
            return policy.includeProjectZomboidExposure() ? value : null;
        }
        if (LuaVisibilityTracker.isAvrixExposure(key)) {
            return policy.includeAvrixExposure() ? value : null;
        }
        if (LuaVisibilityTracker.isGameLua(key)) {
            return policy.includeGameLua() ? value : null;
        }
        if (LuaVisibilityTracker.isModLua(key)) {
            return policy.includeModLua() ? value : null;
        }
        if (LuaVisibilityTracker.isCustomLua(key)) {
            return policy.includeCustomLua() ? value : null;
        }
        return policy.includeUnknownGlobals() ? value : null;
    }

    /**
     * Clears cached source resolutions after the source list changes.
     */
    void invalidateResolutions() {
        resolvedModules.clear();
    }

    private Optional<LuaScriptModule> resolveModule(String path) {
        Optional<LuaScriptModule> cached = resolvedModules.get(path);
        if (cached != null) {
            return cached;
        }

        for (LuaScriptSource source : LuaScriptLoader.sourcesSnapshot()) {
            if (source.contains(path)) {
                LuaScriptModule module = new LuaScriptModule(source, path, source.id() + "!/" + path);
                Optional<LuaScriptModule> result = Optional.of(module);
                resolvedModules.put(path, result);
                return result;
            }
        }

        Optional<LuaScriptModule> result = Optional.empty();
        resolvedModules.put(path, result);
        return result;
    }

    private LuaClosure compile(Reader reader, String moduleId) throws IOException {
        boolean previousRewriteEvents = LuaCompiler.rewriteEvents;
        LuaCompiler.rewriteEvents = false;
        try {
            return LuaCompiler.loadis(reader, moduleId, luaEnvironment);
        } finally {
            LuaCompiler.rewriteEvents = previousRewriteEvents;
        }
    }

    private static Reader openReader(InputStream input) throws IOException {
        PushbackReader reader = new PushbackReader(
                new InputStreamReader(input, StandardCharsets.UTF_8), 1);
        int first = reader.read();
        if (first != -1 && first != UTF8_BOM) {
            reader.unread(first);
        }
        return new BufferedReader(reader);
    }

    private void logLuaError(LuaScriptModule module, LuaReturn luaReturn) {
        LOGGER.error("Lua module '{}' failed in environment '{}': {}",
                module.id(), name, luaReturn.getErrorString());
        if (luaReturn.getJavaException() != null) {
            LOGGER.error("Lua module '{}' Java exception", module.id(), luaReturn.getJavaException());
        }
        if (luaReturn.getLuaStackTrace() != null) {
            LOGGER.error("Lua module '{}' stack trace:\n{}", module.id(), luaReturn.getLuaStackTrace());
        }
    }

    private static boolean isLuaThread() {
        return LuaManager.thread == null
                || LuaManager.thread.debugOwnerThread == null
                || LuaManager.thread.debugOwnerThread == Thread.currentThread();
    }

    private void clearCaches() {
        resolvedModules.clear();
        loadedResults.clear();
        loadedModules.clear();
        loadingModules.clear();
    }

    @Override
    public String toString() {
        return "LuaScriptEnvironment[" + name + "]";
    }

    /**
     * {@code __index} function that applies the visibility policy.
     */
    private static final class VisibilityIndexFunction implements JavaFunction {

        private final LuaScriptEnvironment environment;

        private VisibilityIndexFunction(LuaScriptEnvironment environment) {
            this.environment = environment;
        }

        @Override
        public int call(LuaCallFrame frame, int arguments) {
            Object key = frame.get(1);
            Object value = key instanceof String string ? environment.resolveGlobal(string) : null;
            frame.push(value);
            return 1;
        }
    }

    /**
     * {@code require} function installed in isolated environments.
     */
    private final class SandboxRequireFunction implements JavaFunction {

        private final LuaScriptEnvironment environment;

        private SandboxRequireFunction(LuaScriptEnvironment environment) {
            this.environment = environment;
        }

        @Override
        public int call(LuaCallFrame frame, int arguments) {
            Object moduleName = frame.get(0);
            if (moduleName instanceof String name) {
                Optional<LuaScriptResult> result = environment.require(name);
                if (result.isPresent()) {
                    frame.push(result.get().value());
                    return 1;
                }
            }

            if (!environment.policy.allowVanillaRequire()) {
                frame.push(null);
                return 1;
            }

            if (environment.vanillaRequire instanceof JavaFunction function) {
                return function.call(frame, arguments);
            }
            if (environment.vanillaRequire != null && moduleName instanceof String name) {
                Object[] result = LuaManager.caller.pcall(
                        LuaManager.thread, environment.vanillaRequire, name);
                frame.push(result.length > 1 ? result[1] : null);
                return 1;
            }

            frame.push(null);
            return 1;
        }
    }
}
