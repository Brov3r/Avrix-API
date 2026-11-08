package com.avrix.api.lua.script;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.krka.kahlua.vm.KahluaTable;
import se.krka.kahlua.vm.KahluaThread;
import zombie.Lua.LuaManager;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Loads custom Lua files and keeps them working across Project Zomboid Lua resets.
 * <p>
 * Scripts are executed after game and mod Lua files have finished loading, so they can see
 * and override their globals. A {@code require} hook resolves custom modules before falling
 * back to the game implementation.
 * <p>
 * Example:
 * <pre>{@code
 * LuaScriptSource source = LuaScriptSource.directory(Path.of("C:/MyPlugin/lua"));
 * LuaScriptLoader.addSource(source);
 * LuaScriptLoader.load("feature/main");
 * LuaScriptLoader.loadGroup(source, "feature");
 * }</pre>
 */
public final class LuaScriptLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(LuaScriptLoader.class);
    private static final Object LOCK = new Object();
    private static final LuaScriptEnvironment SHARED =
            new LuaScriptEnvironment("shared", LuaEnvironmentPolicy.shared(), true);
    private static final Map<String, LuaScriptEnvironment> ENVIRONMENTS = new LinkedHashMap<>();
    private static final List<LoadRequest> REQUESTS = new ArrayList<>();
    private static final Set<LoadRequest> APPLIED_REQUESTS = new HashSet<>();

    private static volatile List<LuaScriptSource> sources = List.of();
    private static KahluaTable appliedEnvironment;
    private static boolean latePhaseReady;

    private LuaScriptLoader() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Returns the shared environment that executes scripts directly in the game environment.
     *
     * @return shared Lua environment handle
     */
    public static LuaScriptEnvironment sharedEnvironment() {
        return SHARED;
    }

    /**
     * Registers a custom Lua source.
     *
     * @param source source to register
     * @return the effective source instance; may be an already registered source with the same id
     * @throws NullPointerException if {@code source} is {@code null}
     */
    public static LuaScriptSource addSource(LuaScriptSource source) {
        Objects.requireNonNull(source, "source");

        synchronized (LOCK) {
            for (LuaScriptSource existing : sources) {
                if (existing == source) {
                    return existing;
                }
                if (existing.id().equals(source.id())) {
                    LOGGER.warn("Lua script source '{}' is already registered", source.id());
                    closeQuietly(source);
                    return existing;
                }
            }

            List<LuaScriptSource> updated = new ArrayList<>(sources);
            updated.add(source);
            sources = List.copyOf(updated);
            invalidateResolutions();
            return source;
        }
    }

    /**
     * Removes a custom Lua source and closes it.
     *
     * @param source source to remove
     * @return {@code true} if the source was registered
     */
    public static boolean removeSource(LuaScriptSource source) {
        if (source == null) {
            return false;
        }

        synchronized (LOCK) {
            List<LuaScriptSource> updated = new ArrayList<>(sources);
            if (!updated.remove(source)) {
                return false;
            }
            sources = List.copyOf(updated);
            closeQuietly(source);
            invalidateResolutions();
            return true;
        }
    }

    /**
     * Creates a new isolated Lua environment.
     *
     * @param name   unique environment name
     * @param policy visibility policy
     * @return environment handle
     * @throws IllegalArgumentException if the name is blank, reserved or already used
     * @throws NullPointerException     if an argument is {@code null}
     */
    public static LuaScriptEnvironment createEnvironment(String name, LuaEnvironmentPolicy policy) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(policy, "policy");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Environment name cannot be blank");
        }
        if ("shared".equalsIgnoreCase(name)) {
            throw new IllegalArgumentException("'shared' is reserved for the shared Lua environment");
        }

        synchronized (LOCK) {
            if (ENVIRONMENTS.containsKey(name)) {
                throw new IllegalArgumentException("Lua environment already exists: " + name);
            }
            LuaScriptEnvironment environment = new LuaScriptEnvironment(name, policy, false);
            ENVIRONMENTS.put(name, environment);
            if (isReady() && isLuaThread()) {
                environment.initialize(LuaManager.env);
            }
            return environment;
        }
    }

    /**
     * Returns an isolated environment by name.
     *
     * @param name environment name
     * @return environment, or {@code null} if it does not exist
     */
    public static LuaScriptEnvironment getEnvironment(String name) {
        synchronized (LOCK) {
            return ENVIRONMENTS.get(name);
        }
    }

    /**
     * Removes an isolated environment and its queued load requests.
     *
     * @param name environment name
     * @return {@code true} if the environment existed
     */
    public static boolean removeEnvironment(String name) {
        synchronized (LOCK) {
            LuaScriptEnvironment environment = ENVIRONMENTS.remove(name);
            if (environment == null) {
                return false;
            }
            environment.invalidate();
            REQUESTS.removeIf(request -> request.environment() == environment);
            APPLIED_REQUESTS.removeIf(request -> request.environment() == environment);
            return true;
        }
    }

    /**
     * Loads one custom module into the shared environment.
     *
     * @param moduleName module name such as {@code feature/main}
     */
    public static void load(String moduleName) {
        load(SHARED, moduleName);
    }

    /**
     * Loads one custom module into the given environment.
     *
     * @param environment target environment
     * @param moduleName  module name such as {@code feature/main}
     */
    public static void load(LuaScriptEnvironment environment, String moduleName) {
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(moduleName, "moduleName");
        register(new LoadRequest(environment, RequestType.SINGLE, List.of(moduleName), null, null));
    }

    /**
     * Loads one module explicitly from a source.
     *
     * @param source     script source
     * @param moduleName module name relative to the source root
     */
    public static void load(LuaScriptSource source, String moduleName) {
        load(SHARED, source, moduleName);
    }

    /**
     * Loads one module explicitly from a source into the given environment.
     *
     * @param environment target environment
     * @param source      script source
     * @param moduleName  module name relative to the source root
     */
    public static void load(LuaScriptEnvironment environment, LuaScriptSource source, String moduleName) {
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(moduleName, "moduleName");
        LuaScriptSource effective = addSource(source);
        register(new LoadRequest(environment, RequestType.SINGLE, List.of(moduleName), effective, null));
    }

    /**
     * Loads several custom modules into the shared environment.
     *
     * @param moduleNames module names
     */
    public static void loadAll(String... moduleNames) {
        loadAll(SHARED, List.of(moduleNames));
    }

    /**
     * Loads several custom modules into the given environment.
     *
     * @param environment target environment
     * @param moduleNames module names
     */
    public static void loadAll(LuaScriptEnvironment environment, String... moduleNames) {
        loadAll(environment, List.of(moduleNames));
    }

    /**
     * Loads several custom modules into the given environment.
     *
     * @param environment target environment
     * @param moduleNames module names
     */
    public static void loadAll(LuaScriptEnvironment environment, Collection<String> moduleNames) {
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(moduleNames, "moduleNames");
        if (moduleNames.isEmpty()) {
            return;
        }
        register(new LoadRequest(environment, RequestType.MULTIPLE, List.copyOf(moduleNames), null, null));
    }

    /**
     * Loads a single Lua file from the file system and registers its parent directory as a source.
     * <p>
     * The parent directory becomes the {@code require} root for this file. Register a broader
     * source explicitly when modules use paths above that directory.
     *
     * @param file Lua file
     */
    public static void load(Path file) {
        Objects.requireNonNull(file, "file");
        Path absolute = file.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent == null) {
            throw new IllegalArgumentException("Lua file has no parent directory: " + file);
        }

        LuaScriptSource source = addSource(LuaScriptSource.directory(parent));
        register(new LoadRequest(SHARED, RequestType.SINGLE,
                List.of(absolute.getFileName().toString()), source, null));
    }

    /**
     * Loads a single Lua entry from a JAR and registers the entry's parent directory as a source.
     * <p>
     * The entry's parent becomes the {@code require} root for this file. Register a broader
     * source explicitly when modules use paths above that directory.
     *
     * @param jar   JAR path
     * @param entry entry name such as {@code lua/module/main.lua}
     */
    public static void loadFromJar(Path jar, String entry) {
        Objects.requireNonNull(jar, "jar");
        String normalized = normalizeEntryPath(entry);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("JAR entry cannot be blank");
        }
        Path entryPath = Path.of(normalized);
        Path parent = entryPath.getParent();
        String prefix = parent == null ? "" : parent.toString().replace('\\', '/');

        LuaScriptSource source = addSource(LuaScriptSource.jar(jar, prefix));
        register(new LoadRequest(SHARED, RequestType.SINGLE,
                List.of(entryPath.getFileName().toString()), source, null));
    }

    /**
     * Clears cached source resolutions, for example after adding files to an existing directory.
     */
    public static void invalidate() {
        synchronized (LOCK) {
            invalidateResolutions();
        }
    }

    /**
     * Loads all Lua files recursively from a directory and registers the directory as a source.
     *
     * @param directory directory root
     */
    public static void loadGroup(Path directory) {
        Objects.requireNonNull(directory, "directory");
        LuaScriptSource source = addSource(LuaScriptSource.directory(directory));
        register(new LoadRequest(SHARED, RequestType.GROUP, List.of(), source, ""));
    }

    /**
     * Loads all Lua files recursively from a registered source directory.
     * <p>
     * The directory is relative to the source root. When the directory itself should become
     * the {@code require} root, create a source with that prefix and load group {@code ""}.
     *
     * @param source    script source
     * @param directory directory relative to the source root; empty for the root
     */
    public static void loadGroup(LuaScriptSource source, String directory) {
        loadGroup(SHARED, source, directory);
    }

    /**
     * Loads all Lua files recursively from a source directory into the given environment.
     * <p>
     * The directory is relative to the source root. When the directory itself should become
     * the {@code require} root, create a source with that prefix and load group {@code ""}.
     *
     * @param environment target environment
     * @param source      script source
     * @param directory   directory relative to the source root; empty for the root
     */
    public static void loadGroup(LuaScriptEnvironment environment, LuaScriptSource source, String directory) {
        Objects.requireNonNull(environment, "environment");
        LuaScriptSource effective = addSource(source);
        register(new LoadRequest(environment, RequestType.GROUP, List.of(), effective,
                directory == null ? "" : directory));
    }

    /**
     * Loads all Lua files recursively from a JAR directory and registers it as a source.
     *
     * @param jar       JAR path
     * @param directory directory inside the JAR
     */
    public static void loadGroupFromJar(Path jar, String directory) {
        Objects.requireNonNull(jar, "jar");
        String normalized = normalizeEntryPath(directory);
        LuaScriptSource source = addSource(LuaScriptSource.jar(jar, normalized));
        register(new LoadRequest(SHARED, RequestType.GROUP, List.of(), source, ""));
    }

    /**
     * Tries to load a custom module for the shared {@code require} call.
     *
     * @param moduleName module name
     * @return load result, or empty when no custom source provides the module
     */
    public static Optional<LuaScriptResult> tryRequire(String moduleName) {
        if (moduleName == null || sources.isEmpty() || LuaManager.env == null) {
            return Optional.empty();
        }
        return SHARED.require(moduleName);
    }

    /**
     * Runtime hook called after the game creates a new Lua environment.
     */
    public static void onLuaEnvironmentCreated() {
        synchronized (LOCK) {
            try {
                appliedEnvironment = LuaManager.env;
                latePhaseReady = false;
                APPLIED_REQUESTS.clear();
                LuaVisibilityTracker.reset(LuaManager.env);
                SHARED.invalidate();
                for (LuaScriptEnvironment environment : ENVIRONMENTS.values()) {
                    environment.invalidate();
                }
            } catch (Exception | LinkageError ex) {
                LOGGER.error("Failed to initialize Lua script state", ex);
            }
        }
    }

    /**
     * Runtime hook called after Avrix exposures have been applied to a new Lua environment.
     */
    public static void onLuaEnvironmentReady() {
        synchronized (LOCK) {
            if (LuaManager.env == null) {
                return;
            }
            try {
                LuaVisibilityTracker.captureAvrixExposureKeys();
                SHARED.initialize(LuaManager.env);
                for (LuaScriptEnvironment environment : ENVIRONMENTS.values()) {
                    environment.initialize(LuaManager.env);
                }
            } catch (Exception | LinkageError ex) {
                LOGGER.error("Failed to prepare Lua script environments", ex);
            }
        }
    }

    /**
     * Runtime hook called before {@code LuaManager.LoadDirBase}.
     */
    public static void onLoadDirBegin() {
        LuaVisibilityTracker.beginLoadDir();
    }

    /**
     * Runtime hook called for every Lua file loaded by {@code LuaManager.LoadDirBase}.
     *
     * @param filename absolute Lua file path
     */
    public static void onLuaFileLoaded(String filename) {
        LuaVisibilityTracker.onRunLua(filename);
    }

    /**
     * Runtime hook called after {@code LuaManager.LoadDirBase}.
     */
    public static void onLoadDirEnd() {
        LuaVisibilityTracker.endLoadDir();
    }

    /**
     * Runtime hook called when the game triggers {@code OnGameBoot}.
     */
    public static void onGameBoot() {
        LuaVisibilityTracker.finishLuaLoad();
        synchronized (LOCK) {
            latePhaseReady = true;
        }
        drainLate();
    }

    /**
     * Applies queued load requests that have not yet been executed for the current Lua state.
     */
    public static void applyPending() {
        synchronized (LOCK) {
            latePhaseReady = true;
            applyPendingLocked();
        }
    }

    /**
     * Applies pending requests only after the late load phase has started.
     */
    public static void drainLate() {
        synchronized (LOCK) {
            if (!latePhaseReady) {
                return;
            }
            applyPendingLocked();
        }
    }

    private static void applyPendingLocked() {
        if (!isReady() || !isLuaThread()) {
            return;
        }
        if (LuaManager.env != appliedEnvironment) {
            onLuaEnvironmentCreated();
            onLuaEnvironmentReady();
            latePhaseReady = true;
        }
        try {
            if (!SHARED.isInitialized()) {
                SHARED.initialize(LuaManager.env);
            }
            for (LuaScriptEnvironment environment : ENVIRONMENTS.values()) {
                if (!environment.isInitialized()) {
                    environment.initialize(LuaManager.env);
                }
            }
        } catch (Exception | LinkageError ex) {
            LOGGER.error("Failed to prepare Lua script environments", ex);
            return;
        }

        List<LoadRequest> snapshot = new ArrayList<>(REQUESTS);
        for (LoadRequest request : snapshot) {
            if (APPLIED_REQUESTS.add(request)) {
                execute(request);
            }
        }
    }

    /**
     * Removes all sources, environments and queued requests.
     */
    public static void unregisterAll() {
        synchronized (LOCK) {
            REQUESTS.clear();
            APPLIED_REQUESTS.clear();
            for (LuaScriptEnvironment environment : ENVIRONMENTS.values()) {
                environment.invalidate();
            }
            ENVIRONMENTS.clear();

            List<LuaScriptSource> oldSources = sources;
            sources = List.of();
            for (LuaScriptSource source : oldSources) {
                closeQuietly(source);
            }

            SHARED.invalidate();
            LuaVisibilityTracker.reset(null);
            appliedEnvironment = null;
            latePhaseReady = false;
        }
    }

    /**
     * Releases all sources and environments. Equivalent to {@link #unregisterAll()}.
     */
    public static void close() {
        unregisterAll();
    }

    /**
     * Returns the current source snapshot.
     *
     * @return immutable source list
     */
    static List<LuaScriptSource> sourcesSnapshot() {
        return sources;
    }

    /**
     * Normalizes a Lua module name to a relative path.
     *
     * @param moduleName module name
     * @return normalized relative path, or {@code null} if the name is invalid
     */
    static String normalizeModulePath(String moduleName) {
        if (moduleName == null) {
            return null;
        }

        String path = moduleName.trim().replace('\\', '/');
        while (path.startsWith("./")) {
            path = path.substring(2);
        }
        if (path.isEmpty() || path.startsWith("/") || path.contains(":")) {
            return null;
        }

        StringBuilder normalized = new StringBuilder(path.length());
        for (String segment : path.split("/")) {
            if (segment.isEmpty() || ".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment)) {
                return null;
            }
            if (!normalized.isEmpty()) {
                normalized.append('/');
            }
            normalized.append(segment);
        }
        if (normalized.isEmpty()) {
            return null;
        }

        String result = normalized.toString();
        if (!result.toLowerCase(Locale.ROOT).endsWith(".lua")) {
            result = result + ".lua";
        }
        return result;
    }

    private static void register(LoadRequest request) {
        synchronized (LOCK) {
            REQUESTS.add(request);
            if (latePhaseReady && isReady() && isLuaThread()) {
                applyPendingLocked();
            }
        }
    }

    private static void execute(LoadRequest request) {
        switch (request.type()) {
            case SINGLE -> executeSingle(request, request.modules().get(0));
            case MULTIPLE -> request.modules().forEach(module -> executeSingle(request, module));
            case GROUP -> executeGroup(request);
        }
    }

    private static void executeSingle(LoadRequest request, String moduleName) {
        LuaScriptResult result;
        if (request.source() == null) {
            result = request.environment().loadModule(moduleName);
        } else {
            String path = normalizeModulePath(moduleName);
            if (path == null) {
                LOGGER.warn("Invalid Lua module name '{}' in environment '{}'",
                        moduleName, request.environment().name());
                return;
            }
            LuaScriptModule module = new LuaScriptModule(
                    request.source(), path, request.source().id() + "!/" + path);
            result = request.environment().loadModule(module);
        }

        if (!result.isSuccess()) {
            LOGGER.warn("Failed to load Lua module '{}' in environment '{}': {}",
                    moduleName, request.environment().name(), result.error().getMessage());
        }
    }

    private static void executeGroup(LoadRequest request) {
        LuaScriptSource source = request.source();
        if (source == null) {
            LOGGER.warn("Cannot load Lua group in environment '{}': source is missing",
                    request.environment().name());
            return;
        }

        List<String> paths;
        try {
            paths = new ArrayList<>(source.list(request.directory()));
        } catch (IOException | RuntimeException ex) {
            LOGGER.error("Failed to list Lua files in source '{}'", source.id(), ex);
            return;
        }
        paths.sort(String.CASE_INSENSITIVE_ORDER);

        for (String path : paths) {
            LuaScriptModule module = new LuaScriptModule(source, path, source.id() + "!/" + path);
            LuaScriptResult result = request.environment().loadModule(module);
            if (!result.isSuccess()) {
                LOGGER.warn("Failed to load Lua module '{}' in environment '{}': {}",
                        module.id(), request.environment().name(), result.error().getMessage());
            }
        }
    }

    private static void invalidateResolutions() {
        synchronized (LOCK) {
            SHARED.invalidateResolutions();
            for (LuaScriptEnvironment environment : ENVIRONMENTS.values()) {
                environment.invalidateResolutions();
            }
        }
    }

    private static boolean isReady() {
        return LuaManager.env != null
                && LuaManager.thread != null
                && LuaManager.caller != null
                && LuaManager.platform != null;
    }

    private static boolean isLuaThread() {
        KahluaThread thread = LuaManager.thread;
        return thread == null
                || thread.debugOwnerThread == null
                || thread.debugOwnerThread == Thread.currentThread();
    }

    private static String normalizeEntryPath(String path) {
        if (path == null) {
            return "";
        }
        String normalized = path.replace('\\', '/').trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static void closeQuietly(LuaScriptSource source) {
        try {
            source.close();
        } catch (IOException ex) {
            LOGGER.warn("Failed to close Lua script source '{}'", source.id(), ex);
        }
    }

    /**
     * Requested script load operation.
     */
    private enum RequestType {
        SINGLE,
        MULTIPLE,
        GROUP
    }

    /**
     * Queued script load request.
     *
     * @param environment target environment
     * @param type        request type
     * @param modules     module names
     * @param source      explicit source for group/source loads
     * @param directory   group directory
     */
    private record LoadRequest(LuaScriptEnvironment environment, RequestType type, List<String> modules,
                               LuaScriptSource source, String directory) {
    }
}
