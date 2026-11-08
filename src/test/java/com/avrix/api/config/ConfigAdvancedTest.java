package com.avrix.api.config;

import com.avrix.core.Environment;
import com.avrix.core.Metadata;
import com.avrix.plugins.PluginData;
import com.avrix.utils.Constants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.serialize.SerializationException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Advanced configuration tests for type safety, path guards, lifecycle, atomicity and updates.
 */
@DisplayName("Advanced Config System Tests")
class ConfigAdvancedTest {

    @TempDir
    Path tempDirectory;

    @BeforeEach
    void setUp() throws IOException {
        cleanupEnvironment();
    }

    @AfterEach
    void tearDown() throws IOException {
        cleanupEnvironment();
    }

    @Test
    @DisplayName("Should reject loading the same file with a different model type")
    void shouldRejectTypeMismatchForSameFile() {
        PluginData plugin = createPluginData("typed-plugin", createPluginJar("typed-plugin", Map.of(
                "config.yml", "name: \"Typed\"\nvalue: 10\nenabled: true\n"
        )));

        Config<TestConfig> typed = ConfigManager.loadDefaultConfig(plugin, TestConfig.class);

        assertThatThrownBy(() -> ConfigManager.loadDefaultConfig(plugin))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already loaded");

        assertThat(ConfigManager.get("typed-plugin", "config.yml", TestConfig.class)).isSameAs(typed);
    }

    @Test
    @DisplayName("Should reject paths outside the plugin configuration directory")
    void shouldRejectPathTraversal() {
        PluginData plugin = createPluginData("path-plugin", createPluginJar("path-plugin", Map.of()));

        assertThatThrownBy(() -> ConfigManager.load(plugin, "../outside.yml", TestConfig.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("escapes");

        assertThatThrownBy(() -> ConfigManager.load(
                plugin, Path.of("absolute.yml").toAbsolutePath().toString(), TestConfig.class
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("relative");
    }

    @Test
    @DisplayName("Should unload all configurations belonging to a plugin")
    void shouldUnloadPluginConfigurations() {
        PluginData plugin = createPluginData("unload-plugin", createPluginJar("unload-plugin", Map.of(
                "config.yml", "name: \"Unload\"\nvalue: 1\nenabled: true\n"
        )));

        ConfigManager.loadDefaultConfig(plugin, TestConfig.class);
        ConfigManager.unload(plugin);

        assertThatThrownBy(() -> ConfigManager.get("unload-plugin", "config.yml"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should update an immutable record through update(Function)")
    void shouldUpdateImmutableRecord() {
        PluginData plugin = createPluginData("update-plugin", createPluginJar("update-plugin", Map.of(
                "config.yml", "name: \"Before\"\nvalue: 1\nenabled: true\n"
        )));
        Config<TestConfig> config = ConfigManager.loadDefaultConfig(plugin, TestConfig.class);

        TestConfig updated = config.updateAndGet(current ->
                new TestConfig("After", current.value() + 41, false));

        assertThat(updated.name()).isEqualTo("After");
        assertThat(config.get()).isEqualTo(updated);
        config.reload();
        assertThat(config.get()).isEqualTo(updated);
    }

    @Test
    @DisplayName("Should synchronize raw node replacement with save")
    void shouldSynchronizeRawNodeReplacement() throws Exception {
        PluginData plugin = createPluginData("raw-plugin", createPluginJar("raw-plugin", Map.of(
                "config.yml", "value: \"old\"\n"
        )));
        Config<CommentedConfigurationNode> config = ConfigManager.loadDefaultConfig(plugin);

        CommentedConfigurationNode replacement = CommentedConfigurationNode.root();
        replacement.node("value").set("new");
        config.set(replacement);
        config.save();
        config.reload();

        assertThat(config.node().node("value").getString()).isEqualTo("new");
    }

    @Test
    @DisplayName("Should mutate raw configuration through update(Consumer)")
    void shouldUpdateRawNode() {
        PluginData plugin = createPluginData("raw-update-plugin", createPluginJar("raw-update-plugin", Map.of(
                "config.yml", "value: \"old\"\n"
        )));
        Config<CommentedConfigurationNode> config = ConfigManager.loadDefaultConfig(plugin);

        config.update(node -> {
            try {
                node.node("value").set("consumer");
            } catch (SerializationException ex) {
                throw new RuntimeException(ex);
            }
        });

        config.reload();
        assertThat(config.node().node("value").getString()).isEqualTo("consumer");
    }

    @Test
    @DisplayName("Should preserve comments after typed save")
    void shouldPreserveCommentsAfterSave() throws IOException {
        PluginData plugin = createPluginData("comment-save-plugin", createPluginJar("comment-save-plugin", Map.of(
                "config.yml", """
                        # Persistent comment
                        name: "Comment"
                        value: 1
                        enabled: true
                        """
        )));
        Config<TestConfig> config = ConfigManager.loadDefaultConfig(plugin, TestConfig.class);

        config.set(new TestConfig("Changed", 2, false));
        config.save();

        assertThat(Files.readString(config.getFile())).contains("# Persistent comment");
    }

    @Test
    @DisplayName("Should preserve a blank-separated header after typed save")
    void shouldPreserveBlankSeparatedHeaderAfterSave() throws IOException {
        PluginData plugin = createPluginData("header-plugin", createPluginJar("header-plugin", Map.of(
                "config.yml", """
                        # Blank separated header

                        name: "Comment"
                        value: 1
                        enabled: true
                        """
        )));
        Config<TestConfig> config = ConfigManager.loadDefaultConfig(plugin, TestConfig.class);

        config.set(new TestConfig("Changed", 2, false));
        config.save();

        assertThat(Files.readString(config.getFile())).contains("# Blank separated header");
    }

    @Test
    @DisplayName("Should continue saveAll and reloadAll when one config fails")
    void shouldContinueBulkOperationsAfterFailure() throws IOException {
        PluginData first = createPluginData("bulk-first", createPluginJar("bulk-first", Map.of(
                "config.yml", "name: \"First\"\nvalue: 1\nenabled: true\n"
        )));
        PluginData second = createPluginData("bulk-second", createPluginJar("bulk-second", Map.of(
                "config.yml", "name: \"Second\"\nvalue: 2\nenabled: true\n"
        )));

        Config<TestConfig> firstConfig = ConfigManager.loadDefaultConfig(first, TestConfig.class);
        Config<TestConfig> secondConfig = ConfigManager.loadDefaultConfig(second, TestConfig.class);
        firstConfig.set(new TestConfig("FirstChanged", 3, false));
        Files.delete(secondConfig.getFile());
        Files.createDirectory(secondConfig.getFile());

        assertThatCode(ConfigManager::saveAll).doesNotThrowAnyException();
        assertThat(Files.readString(firstConfig.getFile())).contains("FirstChanged");

        Files.delete(secondConfig.getFile());
        Files.writeString(secondConfig.getFile(), "not: valid: yaml: [");
        assertThatCode(ConfigManager::reloadAll).doesNotThrowAnyException();
        assertThat(firstConfig.get().name()).isEqualTo("FirstChanged");
    }

    @Test
    @DisplayName("Should keep final state consistent under concurrent update(Function)")
    void shouldKeepConcurrentUpdatesConsistent() throws InterruptedException {
        PluginData plugin = createPluginData("concurrent-plugin", createPluginJar("concurrent-plugin", Map.of(
                "config.yml", "name: \"Initial\"\nvalue: 0\nenabled: true\n"
        )));
        Config<TestConfig> config = ConfigManager.loadDefaultConfig(plugin, TestConfig.class);

        int threads = 8;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                executor.submit(() -> {
                    try {
                        start.await();
                        config.updateAndGet(current -> new TestConfig("Concurrent", current.value() + 1, true));
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(config.get().value()).isEqualTo(threads);
        config.reload();
        assertThat(config.get().value()).isEqualTo(threads);
    }

    @Test
    @DisplayName("Should create a usable file for a typed config missing from the JAR")
    void shouldHandleMissingTypedResource() {
        PluginData plugin = createPluginData("empty-typed-plugin", createPluginJar("empty-typed-plugin", Map.of()));

        assertThatCode(() -> ConfigManager.loadDefaultConfig(plugin, TestConfig.class))
                .doesNotThrowAnyException();
    }

    private void cleanupEnvironment() throws IOException {
        ConfigManager.unloadAll();

        String pluginsRootName = Constants.PLUGINS_FOLDER_NAME != null ? Constants.PLUGINS_FOLDER_NAME : "plugins";
        Path pluginsDir = Path.of(pluginsRootName).toAbsolutePath();
        if (!Files.exists(pluginsDir)) {
            return;
        }

        try (var paths = Files.walk(pluginsDir)) {
            List<Path> sorted = paths.sorted((left, right) -> right.getNameCount() - left.getNameCount()).toList();
            for (Path path : sorted) {
                Files.deleteIfExists(path);
            }
        }
    }

    private File createPluginJar(String pluginId, Map<String, String> entries) {
        try {
            File jarFile = Files.createTempFile(tempDirectory, "plugin-" + pluginId + "-", ".jar").toFile();
            try (JarOutputStream output = new JarOutputStream(new FileOutputStream(jarFile))) {
                for (Map.Entry<String, String> entry : entries.entrySet()) {
                    output.putNextEntry(new JarEntry(entry.getKey()));
                    output.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                    output.closeEntry();
                }
            }
            return jarFile;
        } catch (IOException ex) {
            throw new RuntimeException("Failed to assemble test JAR: " + pluginId, ex);
        }
    }

    private PluginData createPluginData(String pluginId, File jarFile) {
        Metadata metadata = new Metadata.Builder()
                .schema(Constants.METADATA_SCHEMA)
                .id(pluginId)
                .name(pluginId)
                .version("1.0.0")
                .environment(Environment.BOTH)
                .build();
        return new PluginData(jarFile, null, null, metadata);
    }

    @ConfigSerializable
    public record TestConfig(String name, int value, boolean enabled) {
    }
}
