package com.avrix.api.config;

import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.loader.HeaderMode;
import org.spongepowered.configurate.objectmapping.ObjectMapper;
import org.spongepowered.configurate.util.NamingSchemes;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Thread-safe YAML configuration container bound to a typed model or a raw node tree.
 *
 * @param <T> configuration model type (Record, POJO, or {@link CommentedConfigurationNode})
 */
public final class Config<T> {

    private final Path file;
    private final Class<T> type;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private CommentedConfigurationNode rootNode;
    private T data;
    private String rawHeader;

    Config(final Path file, final Class<T> type) {
        this.file = Objects.requireNonNull(file, "File path cannot be null");
        this.type = Objects.requireNonNull(type, "Target type cannot be null");
    }

    /**
     * Loads or reloads the configuration state from disk.
     * <p>
     * The previous in-memory state is replaced only after the new node and model
     * have been parsed successfully.
     *
     * @return this configuration instance
     * @throws RuntimeException if parsing or mapping fails
     */
    public Config<T> load() {
        lock.writeLock().lock();
        try {
            CommentedConfigurationNode loadedNode = createLoader(file).load();
            T loadedData;
            if (isRawNodeType()) {
                loadedData = type.cast(loadedNode);
            } else {
                loadedData = loadedNode.get(type);
                if (loadedData == null) {
                    throw new IllegalStateException("Mapped data resulted in null for " + type.getSimpleName());
                }
            }
            this.rootNode = loadedNode;
            this.data = loadedData;
            // Configurate only treats a comment block as a header when it is followed
            // by a blank line. Capture other leading comment blocks ourselves.
            String configurateHeader = loadedNode.options().header();
            this.rawHeader = configurateHeader == null || configurateHeader.isBlank()
                    ? readLeadingHeader(file)
                    : null;
            return this;
        } catch (final ConfigurateException ex) {
            throw new RuntimeException("Failed to load configuration file: " + file, ex);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Returns the deserialized configuration data instance.
     *
     * @return active data model
     */
    public T get() {
        lock.readLock().lock();
        try {
            return requireData();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Replaces the active data model and updates the in-memory node state.
     *
     * @param data new data instance
     * @throws RuntimeException if the typed model cannot be serialized into the node tree
     */
    public void set(final T data) {
        lock.writeLock().lock();
        try {
            T replacement = Objects.requireNonNull(data, "Data cannot be null");
            applyDataToNode(replacement);
            this.data = replacement;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Direct access to the underlying configuration node tree.
     * <p>
     * The returned node is live and mutable. Mutations made directly through it are
     * persisted by {@link #save()}, but are not reflected in {@link #get()} until
     * the configuration is reloaded.
     *
     * @return active root node
     */
    public CommentedConfigurationNode node() {
        lock.readLock().lock();
        try {
            return requireNode();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Reloads configuration values directly from disk.
     */
    public void reload() {
        load();
    }

    /**
     * Atomically serializes and saves the active configuration state back to disk.
     *
     * @throws RuntimeException if serialization or I/O fails
     */
    public void save() {
        lock.writeLock().lock();
        try {
            saveLocked();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Mutates a mutable configuration model and immediately writes it to disk.
     * <p>
     * This method is intended for mutable POJOs and raw node trees. Immutable
     * record models should use {@link #updateAndGet(Function)}.
     *
     * @param action modifier consumer
     */
    public void update(final Consumer<T> action) {
        Objects.requireNonNull(action, "Update action cannot be null");
        lock.writeLock().lock();
        try {
            action.accept(requireData());
            saveLocked();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Replaces the configuration model with a value returned by the function and saves it.
     * <p>
     * Unlike {@link #update(Consumer)}, this method supports immutable record models.
     *
     * @param action transformation function
     * @return the new active model
     */
    public T updateAndGet(final Function<T, T> action) {
        Objects.requireNonNull(action, "Update function cannot be null");
        lock.writeLock().lock();
        try {
            T replacement = Objects.requireNonNull(action.apply(requireData()), "Updated data cannot be null");
            applyDataToNode(replacement);
            this.data = replacement;
            saveLocked();
            return replacement;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Returns the physical destination path on disk.
     *
     * @return file path
     */
    public Path getFile() {
        return file;
    }

    /**
     * Returns the configured model type.
     *
     * @return model type
     */
    public Class<T> type() {
        return type;
    }

    private void saveLocked() {
        try {
            if (!isRawNodeType()) {
                applyDataToNode(requireData());
            }
            writeAtomically(requireNode(), file, rawHeader);
        } catch (final ConfigurateException ex) {
            throw new RuntimeException("Failed to save configuration to " + file, ex);
        } catch (final IOException ex) {
            throw new RuntimeException("Failed to write configuration to " + file, ex);
        }
    }

    private void applyDataToNode(T replacement) {
        if (isRawNodeType()) {
            this.rootNode = (CommentedConfigurationNode) replacement;
            return;
        }

        CommentedConfigurationNode current = requireNode();
        CommentedConfigurationNode comments = current.copy();
        try {
            current.set(type, replacement);
            copyComments(comments, current);
        } catch (final ConfigurateException ex) {
            throw new RuntimeException("Failed to apply configuration data to " + file, ex);
        }
    }

    private static void copyComments(CommentedConfigurationNode source, CommentedConfigurationNode target) {
        String comment = source.comment();
        if (comment != null && !comment.isBlank()) {
            target.comment(comment);
        }

        for (Map.Entry<Object, ? extends ConfigurationNode> entry : source.childrenMap().entrySet()) {
            ConfigurationNode sourceChild = entry.getValue();
            ConfigurationNode targetChild = target.node(entry.getKey());
            if (sourceChild instanceof CommentedConfigurationNode commentedSource
                    && targetChild instanceof CommentedConfigurationNode commentedTarget) {
                copyComments(commentedSource, commentedTarget);
            }
        }
    }

    private boolean isRawNodeType() {
        return CommentedConfigurationNode.class.isAssignableFrom(type);
    }

    private T requireData() {
        return Objects.requireNonNull(data, "Configuration has not been loaded");
    }

    private CommentedConfigurationNode requireNode() {
        return Objects.requireNonNull(rootNode, "Configuration has not been loaded");
    }

    private static void writeAtomically(
            CommentedConfigurationNode node,
            Path target,
            String rawHeader
    ) throws IOException, ConfigurateException {
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path temporary = Files.createTempFile(parent, "avrix-config-", ".tmp");
        try {
            createLoader(temporary).save(node);
            if (rawHeader != null && !rawHeader.isBlank()) {
                Files.writeString(temporary, rawHeader + System.lineSeparator() + Files.readString(temporary));
            }
            moveReplacing(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String readLeadingHeader(Path file) {
        if (!Files.isRegularFile(file)) {
            return null;
        }

        try {
            StringBuilder header = new StringBuilder();
            for (String line : Files.readAllLines(file)) {
                String trimmed = line.strip();
                if (trimmed.isEmpty()) {
                    if (header.isEmpty()) {
                        continue;
                    }
                    break;
                }
                if (!trimmed.startsWith("#")) {
                    break;
                }
                if (!header.isEmpty()) {
                    header.append(System.lineSeparator());
                }
                header.append(line.stripTrailing());
            }
            return header.isEmpty() ? null : header.toString();
        } catch (IOException ex) {
            throw new RuntimeException("Failed to read configuration header from " + file, ex);
        }
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static YamlConfigurationLoader createLoader(Path path) {
        return YamlConfigurationLoader.builder()
                .path(path)
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
}
