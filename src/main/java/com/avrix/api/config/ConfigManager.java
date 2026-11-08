package com.avrix.api.config;

import com.avrix.plugins.PluginData;
import com.avrix.utils.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.configurate.CommentedConfigurationNode;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Thread-safe static configuration manager for Avrix plugins.
 * <p>
 * Cached configurations are keyed by normalized path and enforce a single model type
 * per file. Each plugin can unload its own configurations through {@link #unload(PluginData)}.
 */
public final class ConfigManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigManager.class);
    private static final String DEFAULT_CONFIG_FILE = "config.yml";
    private static final String DEFAULT_PLUGINS_ROOT = "plugins";
    private static final Map<Path, Config<?>> CACHE = new ConcurrentHashMap<>();

    private ConfigManager() {
        throw new UnsupportedOperationException("Static utility class cannot be instantiated");
    }

    /**
     * Loads the default {@code config.yml} for the given plugin into a typed model.
     *
     * @param data plugin runtime information container
     * @param type target configuration model class
     * @param <T>  model type
     * @return loaded configuration container
     */
    public static <T> Config<T> loadDefaultConfig(final PluginData data, final Class<T> type) {
        return load(data, DEFAULT_CONFIG_FILE, type);
    }

    /**
     * Loads the default {@code config.yml} as a raw dynamic {@link CommentedConfigurationNode}.
     *
     * @param data plugin runtime information container
     * @return loaded raw node configuration
     */
    public static Config<CommentedConfigurationNode> loadDefaultConfig(final PluginData data) {
        return load(data, DEFAULT_CONFIG_FILE, CommentedConfigurationNode.class);
    }

    /**
     * Loads or creates a specific named configuration file.
     *
     * @param data     plugin runtime information container
     * @param fileName relative path or name of the YAML file
     * @param type     target configuration model class
     * @param <T>      model type
     * @return loaded configuration container
     */
    @SuppressWarnings("unchecked")
    public static <T> Config<T> load(
            final PluginData data,
            final String fileName,
            final Class<T> type
    ) {
        Objects.requireNonNull(data, "PluginData cannot be null");
        Objects.requireNonNull(fileName, "FileName cannot be null");
        Objects.requireNonNull(type, "Config type class cannot be null");

        final String pluginId = data.id();
        final Path targetPath = resolvePath(pluginId, fileName);

        return (Config<T>) CACHE.compute(targetPath, (path, existing) -> {
            if (existing != null) {
                if (!existing.type().equals(type)) {
                    throw new IllegalArgumentException("Config '%s' is already loaded as '%s'"
                            .formatted(path, existing.type().getName()));
                }
                return existing;
            }
            ensureFileExtracted(data, fileName, targetPath);
            return new Config<>(targetPath, type).load();
        });
    }

    /**
     * Retrieves an already loaded configuration instance for the given file.
     * <p>
     * Because a file can only be loaded with one model type, the result is safe to cast
     * to the type used during {@link #load(PluginData, String, Class)}.
     *
     * @param pluginId plugin identifier
     * @param fileName file name or relative subpath
     * @param <T>      model type
     * @return cached config instance
     * @throws IllegalArgumentException if the configuration was not loaded
     */
    @SuppressWarnings("unchecked")
    public static <T> Config<T> get(final String pluginId, final String fileName) {
        final Path path = resolvePath(pluginId, fileName);
        final Config<?> config = CACHE.get(path);
        if (config == null) {
            throw new IllegalArgumentException("No config loaded for '%s'".formatted(path));
        }
        return (Config<T>) config;
    }

    /**
     * Retrieves an already loaded configuration instance with a runtime type check.
     *
     * @param pluginId plugin identifier
     * @param fileName file name or relative subpath
     * @param type     expected model type
     * @param <T>      model type
     * @return cached config instance
     * @throws IllegalArgumentException if the configuration was not loaded with this type
     */
    @SuppressWarnings("unchecked")
    public static <T> Config<T> get(final String pluginId, final String fileName, final Class<T> type) {
        Objects.requireNonNull(type, "Config type class cannot be null");
        final Path path = resolvePath(pluginId, fileName);
        final Config<?> config = CACHE.get(path);
        if (config == null) {
            throw new IllegalArgumentException("No config loaded for '%s' as '%s'"
                    .formatted(path, type.getName()));
        }
        if (!config.type().equals(type)) {
            throw new IllegalArgumentException("Config '%s' is loaded as '%s', not '%s'"
                    .formatted(path, config.type().getName(), type.getName()));
        }
        return (Config<T>) config;
    }

    /**
     * Resolves the validated target path on disk: {@code plugins/{pluginId}/{fileName}}.
     *
     * @param pluginId plugin identifier
     * @param fileName relative file path
     * @return normalized absolute target path
     * @throws IllegalArgumentException if the path escapes the plugin directory or contains invalid segments
     */
    public static Path resolvePath(final String pluginId, final String fileName) {
        Objects.requireNonNull(pluginId, "Plugin id cannot be null");
        Objects.requireNonNull(fileName, "FileName cannot be null");

        String safePluginId = validatePluginId(pluginId);
        String normalizedFileName = fileName.replace('\\', '/').strip();
        if (normalizedFileName.isEmpty()) {
            throw new IllegalArgumentException("Config file name cannot be blank");
        }

        Path filePath = Path.of(normalizedFileName);
        if (filePath.isAbsolute()) {
            throw new IllegalArgumentException("Config file name must be relative: " + fileName);
        }

        Path pluginRoot = pluginRoot(safePluginId);
        Path target = pluginRoot.resolve(filePath).normalize();
        if (!target.startsWith(pluginRoot)) {
            throw new IllegalArgumentException("Config path escapes plugin directory: " + fileName);
        }
        return target;
    }

    /**
     * Saves all currently loaded configurations. A single failing config does not stop the others.
     */
    public static void saveAll() {
        CACHE.forEach((path, config) -> {
            try {
                config.save();
            } catch (RuntimeException ex) {
                LOGGER.error("Failed to save config '{}' ({})", path, config.type().getName(), ex);
            }
        });
    }

    /**
     * Reloads all currently loaded configurations. A single failing config does not stop the others.
     */
    public static void reloadAll() {
        CACHE.forEach((path, config) -> {
            try {
                config.reload();
            } catch (RuntimeException ex) {
                LOGGER.error("Failed to reload config '{}' ({})", path, config.type().getName(), ex);
            }
        });
    }

    /**
     * Unloads all configurations belonging to a plugin.
     *
     * @param data plugin runtime information container
     */
    public static void unload(final PluginData data) {
        Objects.requireNonNull(data, "PluginData cannot be null");
        unload(data.id());
    }

    /**
     * Unloads all configurations belonging to a plugin identifier.
     *
     * @param pluginId plugin identifier
     */
    public static void unload(final String pluginId) {
        String safePluginId = validatePluginId(pluginId);
        Path pluginRoot = pluginRoot(safePluginId);
        CACHE.keySet().removeIf(path -> path.startsWith(pluginRoot));
    }

    /**
     * Clears the entire configuration cache.
     */
    public static void unloadAll() {
        CACHE.clear();
    }

    private static void ensureFileExtracted(
            final PluginData data,
            final String resourceName,
            final Path targetPath
    ) {
        try {
            final Path parent = targetPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            if (Files.exists(targetPath)) {
                if (!Files.isRegularFile(targetPath)) {
                    throw new IOException("Configuration path is not a regular file: " + targetPath);
                }
                return;
            }

            final String cleanResource = normalizeResource(resourceName);
            final Optional<File> jarFileOpt = data.getPluginFile();

            if (jarFileOpt.isPresent()) {
                final File jarFile = jarFileOpt.get();
                if (!jarFile.isFile()) {
                    throw new IOException("Plugin JAR is not a regular file: " + jarFile);
                }
                if (extractFromJar(jarFile, cleanResource, targetPath)) {
                    LOGGER.debug("Extracted default resource [{}] from JAR for plugin [{}]",
                            cleanResource, data.id());
                    return;
                }

                createEmptyConfig(targetPath);
                LOGGER.warn("Resource [{}] was not found in [{}] JAR. Created empty config at [{}]",
                        cleanResource, data.id(), targetPath);
                return;
            }

            final ClassLoader classLoader = data.getPluginInstance()
                    .map(plugin -> plugin.getClass().getClassLoader())
                    .orElse(ConfigManager.class.getClassLoader());

            try (InputStream input = classLoader.getResourceAsStream(cleanResource)) {
                if (input != null) {
                    writeAtomically(input, targetPath);
                    LOGGER.debug("Extracted [{}] from ClassLoader for virtual plugin [{}]",
                            cleanResource, data.id());
                    return;
                }
            }

            createEmptyConfig(targetPath);
            LOGGER.warn("Resource [{}] was not found for virtual plugin [{}]. Created empty config at [{}]",
                    cleanResource, data.id(), targetPath);
        } catch (IOException ex) {
            throw new RuntimeException("Failed to prepare config file at " + targetPath, ex);
        }
    }

    private static boolean extractFromJar(
            final File jarFile,
            final String resourcePath,
            final Path targetPath
    ) throws IOException {
        try (JarFile jar = new JarFile(jarFile)) {
            final JarEntry entry = jar.getJarEntry(resourcePath);
            if (entry == null) {
                return false;
            }
            try (InputStream input = jar.getInputStream(entry)) {
                writeAtomically(input, targetPath);
                return true;
            }
        }
    }

    private static void createEmptyConfig(Path targetPath) throws IOException {
        writeAtomically(InputStream.nullInputStream(), targetPath);
    }

    private static void writeAtomically(InputStream input, Path targetPath) throws IOException {
        Path parent = targetPath.getParent();
        if (parent == null) {
            parent = targetPath.toAbsolutePath().getParent();
        }
        Files.createDirectories(parent);

        Path temporary = Files.createTempFile(parent, "avrix-config-", ".tmp");
        try {
            try (InputStream source = input; OutputStream output = Files.newOutputStream(temporary)) {
                source.transferTo(output);
            }
            moveNew(temporary, targetPath);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void moveNew(Path source, Path target) throws IOException {
        if (Files.exists(target) && !Files.isRegularFile(target)) {
            throw new IOException("Configuration path is not a regular file: " + target);
        }

        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (FileAlreadyExistsException ex) {
            verifyExistingTarget(target, ex);
        } catch (AtomicMoveNotSupportedException ex) {
            try {
                Files.move(source, target);
            } catch (FileAlreadyExistsException alreadyExists) {
                verifyExistingTarget(target, alreadyExists);
            }
        }
    }

    private static void verifyExistingTarget(Path target, FileAlreadyExistsException cause) throws IOException {
        if (!Files.isRegularFile(target)) {
            throw new IOException("Configuration path is not a regular file: " + target, cause);
        }
        // Another thread or process created the config first; keep the existing file.
    }

    private static String normalizeResource(String resourceName) {
        String resource = resourceName.replace('\\', '/').strip();
        while (resource.startsWith("/")) {
            resource = resource.substring(1);
        }
        if (resource.isEmpty()) {
            throw new IllegalArgumentException("Config resource name cannot be blank");
        }
        return resource;
    }

    private static String validatePluginId(String pluginId) {
        String clean = Objects.requireNonNull(pluginId, "Plugin id cannot be null").strip();
        if (clean.isEmpty()) {
            throw new IllegalArgumentException("Plugin id cannot be blank");
        }
        if (clean.contains("/") || clean.contains("\\") || clean.contains("..")) {
            throw new IllegalArgumentException("Plugin id contains invalid path characters: " + pluginId);
        }
        return clean;
    }

    private static Path pluginRoot(String pluginId) {
        String pluginsRoot = Objects.requireNonNullElse(Constants.PLUGINS_FOLDER_NAME, DEFAULT_PLUGINS_ROOT);
        return Path.of(pluginsRoot, pluginId).toAbsolutePath().normalize();
    }
}
