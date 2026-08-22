package com.avrix.api.config;

import com.avrix.plugins.PluginData;
import com.avrix.utils.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.configurate.CommentedConfigurationNode;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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
 */
public final class ConfigManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigManager.class);
    private static final String DEFAULT_CONFIG_FILE = "config.yml";
    private static final Map<String, Config<?>> CACHE = new ConcurrentHashMap<>();

    private ConfigManager() {
        throw new UnsupportedOperationException("Static utility class cannot be instantiated");
    }

    /**
     * Loads the default {@code config.yml} for the given plugin into a typed model.
     *
     * @param data plugin runtime information container.
     * @param type target configuration model class.
     * @param <T>  model type.
     * @return loaded {@link Config} container.
     */
    public static <T> Config<T> loadDefaultConfig(final PluginData data, final Class<T> type) {
        return load(data, DEFAULT_CONFIG_FILE, type);
    }

    /**
     * Loads the default {@code config.yml} as a raw dynamic {@link CommentedConfigurationNode}.
     *
     * @param data plugin runtime information container.
     * @return loaded raw node configuration.
     */
    public static Config<CommentedConfigurationNode> loadDefaultConfig(final PluginData data) {
        return load(data, DEFAULT_CONFIG_FILE, CommentedConfigurationNode.class);
    }

    /**
     * Loads or creates a specific named configuration file.
     *
     * @param data     plugin runtime information container.
     * @param fileName relative path or name of the YAML file.
     * @param type     target configuration model class.
     * @param <T>      model type.
     * @return loaded {@link Config} container.
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
        final String cacheKey = (pluginId + ":" + fileName).toLowerCase();

        return (Config<T>) CACHE.computeIfAbsent(cacheKey, _ -> {
            final Path targetPath = resolvePath(pluginId, fileName);
            ensureFileExtracted(data, fileName, targetPath);

            final Config<T> config = new Config<>(targetPath, type);
            return config.load();
        });
    }

    /**
     * Retrieves an already loaded configuration instance from the cache.
     *
     * @param pluginId the plugin identifier.
     * @param fileName file name or relative subpath.
     * @param <T>      model type.
     * @return cached config instance.
     * @throws IllegalArgumentException if the configuration has not been loaded.
     */
    @SuppressWarnings("unchecked")
    public static <T> Config<T> get(final String pluginId, final String fileName) {
        final String cacheKey = (pluginId + ":" + fileName).toLowerCase();
        final Config<?> config = CACHE.get(cacheKey);
        if (config == null) {
            throw new IllegalArgumentException("No config loaded for '%s'".formatted(cacheKey));
        }
        return (Config<T>) config;
    }

    /**
     * Resolves the target path on disk: {@code plugins/{pluginId}/{fileName}}.
     *
     * @param pluginId the plugin ID.
     * @param fileName relative file path.
     * @return normalized target path.
     */
    public static Path resolvePath(final String pluginId, final String fileName) {
        final String pluginsRoot = Constants.PLUGINS_FOLDER_NAME;
        return Path.of(pluginsRoot, pluginId, fileName).toAbsolutePath().normalize();
    }

    /**
     * Saves all currently loaded configurations to disk.
     */
    public static void saveAll() {
        CACHE.values().forEach(Config::save);
    }

    /**
     * Reloads all currently loaded configurations from disk.
     */
    public static void reloadAll() {
        CACHE.values().forEach(Config::reload);
    }

    /**
     * Ensures parent directories and the target file exist.
     */
    private static void ensureFileExtracted(
            final PluginData data,
            final String resourceName,
            final Path targetPath
    ) {
        try {
            final Path parent = targetPath.getParent();
            if (parent != null && Files.notExists(parent)) {
                Files.createDirectories(parent);
            }

            if (Files.exists(targetPath)) {
                return;
            }

            final String cleanResource = resourceName.startsWith("/") ? resourceName.substring(1) : resourceName;
            final Optional<File> jarFileOpt = data.getPluginFile();

            if (jarFileOpt.isPresent()) {
                // Check ONLY within this plugin's physical JAR
                final File jarFile = jarFileOpt.get();
                if (jarFile.exists() && extractFromJar(jarFile, cleanResource, targetPath)) {
                    LOGGER.debug("Extracted default resource [{}] from JAR for plugin [{}]", cleanResource, data.id());
                    return;
                }

                // If not found in the plugin's own JAR, create an empty config file
                Files.createFile(targetPath);
                LOGGER.debug("Resource [{}] not found in [{}] JAR. Created empty config at [{}]", cleanResource, data.id(), targetPath);
                return;
            }

            // Virtual / Core plugins only (no physical JAR): Look in local classloader
            final ClassLoader cl = data.getPluginInstance()
                    .map(p -> p.getClass().getClassLoader())
                    .orElse(ConfigManager.class.getClassLoader());

            try (final InputStream in = cl.getResourceAsStream(cleanResource)) {
                if (in != null) {
                    Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    LOGGER.debug("Extracted [{}] from ClassLoader for virtual plugin [{}]", cleanResource, data.id());
                    return;
                }
            }

            Files.createFile(targetPath);
            LOGGER.debug("Created empty config file at [{}] for virtual plugin [{}]", targetPath, data.id());

        } catch (final IOException ex) {
            throw new RuntimeException("Failed to prepare config file at " + targetPath, ex);
        }
    }

    private static boolean extractFromJar(final File jarFile, final String resourcePath, final Path targetPath) {
        try (final JarFile jar = new JarFile(jarFile)) {
            final JarEntry entry = jar.getJarEntry(resourcePath);
            if (entry == null) {
                return false;
            }
            try (final InputStream in = jar.getInputStream(entry)) {
                Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
                return true;
            }
        } catch (final IOException e) {
            LOGGER.warn("Failed to read entry [{}] from JAR [{}]", resourcePath, jarFile.getName(), e);
            return false;
        }
    }
}