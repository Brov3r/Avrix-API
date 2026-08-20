package com.avrix.api.config;

import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.loader.HeaderMode;
import org.spongepowered.configurate.objectmapping.ObjectMapper;
import org.spongepowered.configurate.util.NamingSchemes;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Encapsulates an active YAML configuration file bound to a model or raw node tree.
 *
 * @param <T> configuration model type (Record, POJO, or {@link CommentedConfigurationNode}).
 */
public final class Config<T> {

    private final Path file;
    private final Class<T> type;
    private final YamlConfigurationLoader loader;

    private CommentedConfigurationNode rootNode;
    private T data;

    Config(final Path file, final Class<T> type) {
        this.file = Objects.requireNonNull(file, "File path cannot be null");
        this.type = Objects.requireNonNull(type, "Target type cannot be null");
        this.loader = YamlConfigurationLoader.builder()
                .path(file)
                .nodeStyle(NodeStyle.BLOCK)
                .headerMode(HeaderMode.PRESERVE)
                .defaultOptions(opts -> opts
                        .shouldCopyDefaults(true)
                        .serializers(builder -> builder.registerAnnotatedObjects(
                                ObjectMapper.factoryBuilder()
                                        .defaultNamingScheme(NamingSchemes.PASSTHROUGH)
                                        .build()
                        ))
                )
                .build();
    }

    /**
     * Loads or reloads the configuration state from disk.
     *
     * @return this configuration instance.
     * @throws RuntimeException if parsing or mapping fails.
     */
    public Config<T> load() {
        try {
            this.rootNode = loader.load();
            if (CommentedConfigurationNode.class.isAssignableFrom(type)) {
                this.data = type.cast(this.rootNode);
            } else {
                this.data = this.rootNode.get(type);
                if (this.data == null) {
                    throw new IllegalStateException("Mapped data resulted in null for " + type.getSimpleName());
                }
            }
            return this;
        } catch (final ConfigurateException ex) {
            throw new RuntimeException("Failed to load configuration file: " + file, ex);
        }
    }

    /**
     * Returns the deserialized configuration data instance.
     *
     * @return active data model.
     */
    public T get() {
        return Objects.requireNonNull(data, "Configuration has not been loaded");
    }

    /**
     * Sets a new data model instance and updates the in-memory state.
     *
     * @param data new data instance.
     */
    public void set(final T data) {
        this.data = Objects.requireNonNull(data, "Data cannot be null");
    }

    /**
     * Direct access to the underlying {@link CommentedConfigurationNode} tree.
     *
     * @return active root node.
     */
    public CommentedConfigurationNode node() {
        return Objects.requireNonNull(rootNode, "Configuration has not been loaded");
    }

    /**
     * Reloads configuration values directly from disk.
     */
    public void reload() {
        load();
    }

    /**
     * Serializes and saves the active configuration state back to disk.
     *
     * @throws RuntimeException if serialization or I/O fails.
     */
    public void save() {
        try {
            if (!CommentedConfigurationNode.class.isAssignableFrom(type)) {
                this.rootNode.set(type, data);
            }
            loader.save(rootNode);
        } catch (final ConfigurateException ex) {
            throw new RuntimeException("Failed to save configuration to " + file, ex);
        }
    }

    /**
     * Mutates the configuration data and immediately writes it to disk.
     *
     * @param action modifier consumer.
     */
    public void update(final Consumer<T> action) {
        Objects.requireNonNull(action, "Update action cannot be null");
        action.accept(get());
        save();
    }

    /**
     * Returns the physical destination path on disk.
     *
     * @return file path.
     */
    public Path getFile() {
        return file;
    }
}