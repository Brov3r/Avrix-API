package com.avrix.api.config;

import com.avrix.core.Environment;
import com.avrix.core.Metadata;
import com.avrix.plugins.PluginData;
import com.avrix.utils.Constants;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;
import org.spongepowered.configurate.serialize.SerializationException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.*;

/**
 * Exhaustive test suite for {@link ConfigManager} and {@link Config}, covering multi-plugin isolation,
 * concurrency, fallback mechanisms, and edge cases.
 */
class ConfigManagerTest {

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

    private void cleanupEnvironment() throws IOException {
        try {
            Field cacheField = ConfigManager.class.getDeclaredField("CACHE");
            cacheField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Config<?>> cache = (Map<String, Config<?>>) cacheField.get(null);
            cache.clear();
        } catch (Exception ex) {
            throw new RuntimeException("Failed to reset ConfigManager cache via reflection", ex);
        }

        String pluginsRootName = Constants.PLUGINS_FOLDER_NAME != null ? Constants.PLUGINS_FOLDER_NAME : "plugins";
        Path pluginsDir = Path.of(pluginsRootName).toAbsolutePath();
        if (Files.exists(pluginsDir)) {
            Files.walkFileTree(pluginsDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    @ConfigSerializable
    public record SimpleConfig(
            String pluginName,
            int multiplier,
            boolean enabled
    ) {
    }

    @ConfigSerializable
    public record ComplexRootConfig(
            @Comment("Main server configuration block")
            ServerBlock server,
            Map<String, ModuleSettings> modules,
            List<String> activeFlags
    ) {
        public ComplexRootConfig {
            if (server == null) server = new ServerBlock("Default", 100);
            if (modules == null) modules = Map.of();
            if (activeFlags == null) activeFlags = List.of();
        }
    }

    @ConfigSerializable
    public record ServerBlock(String title, int tickRate) {
    }

    @ConfigSerializable
    public record ModuleSettings(boolean active, double priority, List<String> tags) {
        public ModuleSettings {
            if (tags == null) tags = List.of();
        }
    }

    // =========================================================================
    // Multi-Plugin Collision & Isolation Tests (CRITICAL)
    // =========================================================================

    @Nested
    @DisplayName("Multi-Plugin Isolation & Collision Resistance")
    class MultiPluginIsolationTests {

        @Test
        @DisplayName("Multiple plugins with identical config.yml resources must extract and load completely independently")
        void testMultiplePluginsDefaultConfigIsolation() throws IOException {
            File jarA = createPluginJar("zomboid-weapons", Map.of(
                    "config.yml", "pluginName: \"Weapons Mod\"\nmultiplier: 10\nenabled: true\n"
            ));
            File jarB = createPluginJar("zomboid-vehicles", Map.of(
                    "config.yml", "pluginName: \"Vehicles Mod\"\nmultiplier: 50\nenabled: false\n"
            ));
            File jarC = createPluginJar("zomboid-chat", Map.of(
                    "config.yml", "pluginName: \"Chat Mod\"\nmultiplier: 1\nenabled: true\n"
            ));

            PluginData pluginA = createPluginData("zomboid-weapons", jarA);
            PluginData pluginB = createPluginData("zomboid-vehicles", jarB);
            PluginData pluginC = createPluginData("zomboid-chat", jarC);

            Config<SimpleConfig> configA = ConfigManager.loadDefaultConfig(pluginA, SimpleConfig.class);
            Config<SimpleConfig> configB = ConfigManager.loadDefaultConfig(pluginB, SimpleConfig.class);
            Config<SimpleConfig> configC = ConfigManager.loadDefaultConfig(pluginC, SimpleConfig.class);

            assertThat(configA.get().pluginName()).isEqualTo("Weapons Mod");
            assertThat(configA.get().multiplier()).isEqualTo(10);
            assertThat(configA.get().enabled()).isTrue();

            assertThat(configB.get().pluginName()).isEqualTo("Vehicles Mod");
            assertThat(configB.get().multiplier()).isEqualTo(50);
            assertThat(configB.get().enabled()).isFalse();

            assertThat(configC.get().pluginName()).isEqualTo("Chat Mod");
            assertThat(configC.get().multiplier()).isEqualTo(1);
            assertThat(configC.get().enabled()).isTrue();

            assertThat(configA.getFile()).isNotEqualTo(configB.getFile());
            assertThat(configB.getFile()).isNotEqualTo(configC.getFile());

            assertThat(configA.getFile().toString()).contains(Path.of("plugins", "zomboid-weapons", "config.yml").toString());
            assertThat(configB.getFile().toString()).contains(Path.of("plugins", "zomboid-vehicles", "config.yml").toString());
            assertThat(configC.getFile().toString()).contains(Path.of("plugins", "zomboid-chat", "config.yml").toString());

            assertThat(ConfigManager.get("zomboid-weapons", "config.yml")).isSameAs(configA);
            assertThat(ConfigManager.get("zomboid-vehicles", "config.yml")).isSameAs(configB);
            assertThat(ConfigManager.get("zomboid-chat", "config.yml")).isSameAs(configC);
        }

        @Test
        @DisplayName("Modifying and saving config in one plugin must never affect another plugin's config")
        void testCrossPluginMutationIsolation() throws IOException {
            File jarA = createPluginJar("mod-alpha", Map.of("config.yml", "pluginName: \"Alpha\"\nmultiplier: 1\nenabled: true\n"));
            File jarB = createPluginJar("mod-beta", Map.of("config.yml", "pluginName: \"Beta\"\nmultiplier: 2\nenabled: true\n"));

            PluginData pluginA = createPluginData("mod-alpha", jarA);
            PluginData pluginB = createPluginData("mod-beta", jarB);

            Config<SimpleConfig> configA = ConfigManager.loadDefaultConfig(pluginA, SimpleConfig.class);
            Config<SimpleConfig> configB = ConfigManager.loadDefaultConfig(pluginB, SimpleConfig.class);

            configA.set(new SimpleConfig("Alpha-Modified", 999, false));
            configA.save();

            configB.reload();

            assertThat(configA.get().pluginName()).isEqualTo("Alpha-Modified");
            assertThat(configA.get().multiplier()).isEqualTo(999);
            assertThat(configA.get().enabled()).isFalse();

            assertThat(configB.get().pluginName()).isEqualTo("Beta");
            assertThat(configB.get().multiplier()).isEqualTo(2);
            assertThat(configB.get().enabled()).isTrue();

            assertThat(Files.readString(configA.getFile())).contains("Alpha-Modified");
            assertThat(Files.readString(configB.getFile())).contains("Beta").doesNotContain("Alpha-Modified");
        }

        @Test
        @DisplayName("Plugins sharing nested subpaths like lang/en.yml must not collide")
        void testNestedSubpathIsolation() {
            File jar1 = createPluginJar("quest-mod", Map.of("lang/en.yml", "pluginName: \"Quests EN\"\nmultiplier: 1\nenabled: true\n"));
            File jar2 = createPluginJar("loot-mod", Map.of("lang/en.yml", "pluginName: \"Loot EN\"\nmultiplier: 2\nenabled: true\n"));

            PluginData plugin1 = createPluginData("quest-mod", jar1);
            PluginData plugin2 = createPluginData("loot-mod", jar2);

            Config<SimpleConfig> lang1 = ConfigManager.load(plugin1, "lang/en.yml", SimpleConfig.class);
            Config<SimpleConfig> lang2 = ConfigManager.load(plugin2, "lang/en.yml", SimpleConfig.class);

            assertThat(lang1.get().pluginName()).isEqualTo("Quests EN");
            assertThat(lang2.get().pluginName()).isEqualTo("Loot EN");
            assertThat(lang1.getFile()).isNotEqualTo(lang2.getFile());
        }
    }

    // =========================================================================
    // Deep Structures & Complex Serialization
    // =========================================================================

    @Nested
    @DisplayName("Complex Hierarchical Models")
    class ComplexHierarchyTests {

        @Test
        @DisplayName("Should serialize and deserialize deeply nested maps, lists, and records")
        void testComplexNestedGraph() {
            String complexYaml = """
                    server:
                      title: "Avrix Hardcore Survival"
                      tickRate: 120
                    modules:
                      anti-cheat:
                        active: true
                        priority: 1.0
                        tags:
                          - "security"
                          - "network"
                      dynamic-loot:
                        active: false
                        priority: 2.5
                        tags:
                          - "economy"
                    activeFlags:
                      - "pvp_allowed"
                      - "perma_death"
                    """;

            File jar = createPluginJar("hardcore-engine", Map.of("config.yml", complexYaml));
            PluginData pluginData = createPluginData("hardcore-engine", jar);

            Config<ComplexRootConfig> config = ConfigManager.loadDefaultConfig(pluginData, ComplexRootConfig.class);
            ComplexRootConfig data = config.get();

            assertThat(data.server().title()).isEqualTo("Avrix Hardcore Survival");
            assertThat(data.server().tickRate()).isEqualTo(120);
            assertThat(data.activeFlags()).containsExactly("pvp_allowed", "perma_death");

            assertThat(data.modules()).hasSize(2);
            ModuleSettings antiCheat = data.modules().get("anti-cheat");
            assertThat(antiCheat.active()).isTrue();
            assertThat(antiCheat.priority()).isEqualTo(1.0);
            assertThat(antiCheat.tags()).containsExactly("security", "network");

            ModuleSettings dynamicLoot = data.modules().get("dynamic-loot");
            assertThat(dynamicLoot.active()).isFalse();
            assertThat(dynamicLoot.priority()).isEqualTo(2.5);

            // Mutate and save
            config.set(new ComplexRootConfig(
                    new ServerBlock("Mutated Server", 60),
                    Map.of("custom-module", new ModuleSettings(true, 5.0, List.of("custom"))),
                    List.of("flag_new")
            ));
            config.save();

            config.reload();
            assertThat(config.get().server().title()).isEqualTo("Mutated Server");
            assertThat(config.get().modules().get("custom-module").tags()).containsExactly("custom");
        }
    }

    // =========================================================================
    // Dynamic Node-Level (Schemaless) API
    // =========================================================================

    @Nested
    @DisplayName("Dynamic Schemaless Node API")
    class DynamicNodeTests {

        @Test
        @DisplayName("Should read and write arbitrary YAML hierarchies without Java DTO classes")
        void testSchemalessNodeOperations() throws SerializationException {
            File jar = createPluginJar("schemaless-mod", Map.of(
                    "config.yml", "nested:\n  deep:\n    flag: true\n    value: 42\n"
            ));
            PluginData pluginData = createPluginData("schemaless-mod", jar);

            Config<CommentedConfigurationNode> config = ConfigManager.loadDefaultConfig(pluginData);

            assertThat(config.node().node("nested", "deep", "flag").getBoolean()).isTrue();
            assertThat(config.node().node("nested", "deep", "value").getInt()).isEqualTo(42);

            // Add dynamic subtree
            config.node().node("nested", "deep", "value").set(100);
            config.node().node("new_branch", "items").set(List.of("axe", "shotgun"));
            config.save();

            config.reload();
            assertThat(config.node().node("nested", "deep", "value").getInt()).isEqualTo(100);
            assertThat(config.node().node("new_branch", "items").childrenList()).hasSize(2);
        }
    }

    // =========================================================================
    // Virtual Threads & Concurrency Stress
    // =========================================================================

    @Nested
    @DisplayName("Concurrency & High Load")
    class ConcurrencyTests {

        @Test
        @DisplayName("Parallel operations from 50 virtual threads across multiple plugins must remain stable")
        void testMultiPluginConcurrentAccess() throws InterruptedException {
            final int pluginCount = 10;
            final int threadsPerPlugin = 5;
            final int totalThreads = pluginCount * threadsPerPlugin;

            List<PluginData> pluginList = new java.util.ArrayList<>();
            for (int i = 0; i < pluginCount; i++) {
                String id = "concurrent-mod-" + i;
                File jar = createPluginJar(id, Map.of("config.yml", "pluginName: \"" + id + "\"\nmultiplier: 0\nenabled: true\n"));
                pluginList.add(createPluginData(id, jar));
            }

            final CountDownLatch startLatch = new CountDownLatch(1);
            final CountDownLatch finishLatch = new CountDownLatch(totalThreads);

            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                for (PluginData plugin : pluginList) {
                    for (int t = 0; t < threadsPerPlugin; t++) {
                        final int threadIndex = t;
                        executor.submit(() -> {
                            try {
                                startLatch.await();
                                for (int iteration = 0; iteration < 20; iteration++) {
                                    Config<SimpleConfig> cfg = ConfigManager.loadDefaultConfig(plugin, SimpleConfig.class);
                                    cfg.set(new SimpleConfig(plugin.id() + "-T" + threadIndex, iteration, true));
                                    cfg.save();
                                    assertThat(cfg.get()).isNotNull();
                                }
                            } catch (Exception ex) {
                                throw new RuntimeException(ex);
                            } finally {
                                finishLatch.countDown();
                            }
                        });
                    }
                }

                startLatch.countDown();
                boolean completed = finishLatch.await(15, TimeUnit.SECONDS);
                assertThat(completed).isTrue();
            }

            assertThatCode(ConfigManager::saveAll).doesNotThrowAnyException();
            assertThatCode(ConfigManager::reloadAll).doesNotThrowAnyException();
        }
    }

    // =========================================================================
    // Edge Cases, Failures, and Robustness
    // =========================================================================

    @Nested
    @DisplayName("Edge Cases & Error Handling")
    class EdgeCasesTests {

        @Test
        @DisplayName("Plugin WITHOUT config.yml in JAR must NOT steal config.yml from classpath/other plugins")
        void testPluginDoesNotStealConfigFromClassLoader() {
            File jarA = createPluginJar("plugin-with-config", Map.of("config.yml", "pluginName: \"Owner\"\nmultiplier: 10\nenabled: true\n"));
            File jarB = createPluginJar("plugin-without-config", Map.of());

            PluginData pluginA = createPluginData("plugin-with-config", jarA);
            PluginData pluginB = createPluginData("plugin-without-config", jarB);

            Config<SimpleConfig> configA = ConfigManager.loadDefaultConfig(pluginA, SimpleConfig.class);
            assertThat(configA.get().pluginName()).isEqualTo("Owner");

            Config<CommentedConfigurationNode> configB = ConfigManager.loadDefaultConfig(pluginB);

            assertThat(configB.node().node("pluginName").getString()).isNull();
            assertThat(configB.getFile()).exists();
        }

        @Test
        @DisplayName("Virtual / Core plugin without JAR file should fallback to ClassLoader")
        void testCorePluginWithoutJarFallback() {
            Metadata meta = new Metadata.Builder()
                    .schema(Constants.METADATA_SCHEMA)
                    .id("avrix-core-synthetic")
                    .name("Avrix Core")
                    .version("1.0.0")
                    .build();

            PluginData virtualPlugin = new PluginData(meta);

            Config<CommentedConfigurationNode> config = ConfigManager.load(virtualPlugin, "fallback-config.yml", CommentedConfigurationNode.class);

            assertThat(config).isNotNull();
            assertThat(Files.exists(config.getFile())).isTrue();
        }

        @Test
        @DisplayName("Corrupted YAML content should fail fast with descriptive exception")
        void testCorruptedYamlThrows() {
            File jar = createPluginJar("corrupted-plugin", Map.of("config.yml", ": invalid: yaml : [unclosed"));
            PluginData plugin = createPluginData("corrupted-plugin", jar);

            assertThatThrownBy(() -> ConfigManager.loadDefaultConfig(plugin, SimpleConfig.class))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to load configuration file");
        }

        @Test
        @DisplayName("Non-existent resource inside JAR creates empty config file safely")
        void testMissingResourceInJarCreatesEmptyFile() {
            File jar = createPluginJar("empty-jar-plugin", Map.of());
            PluginData plugin = createPluginData("empty-jar-plugin", jar);

            Config<CommentedConfigurationNode> config = ConfigManager.load(plugin, "not-in-jar.yml", CommentedConfigurationNode.class);

            assertThat(Files.exists(config.getFile())).isTrue();
            assertThat(config.node().empty()).isTrue();
        }

        @Test
        @DisplayName("Attempting to instantiate utility ConfigManager directly must fail")
        void testPrivateConstructor() throws NoSuchMethodException {
            Constructor<ConfigManager> constructor = ConfigManager.class.getDeclaredConstructor();
            constructor.setAccessible(true);

            assertThatThrownBy(constructor::newInstance)
                    .isInstanceOf(InvocationTargetException.class)
                    .hasCauseInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("Querying uncached config via get() must throw IllegalArgumentException")
        void testGetUncachedConfig() {
            assertThatThrownBy(() -> ConfigManager.get("non-existent-plugin", "config.yml"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("No config loaded");
        }

        @Test
        @DisplayName("Null arguments across public methods must throw NullPointerException immediately")
        void testNullSafety() {
            Metadata meta = new Metadata.Builder()
                    .schema(Constants.METADATA_SCHEMA)
                    .id("valid-plugin")
                    .name("Valid Plugin")
                    .version("1.0.0")
                    .build();
            PluginData data = new PluginData(meta);

            assertThatThrownBy(() -> ConfigManager.loadDefaultConfig(null, SimpleConfig.class))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> ConfigManager.loadDefaultConfig(data, null))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> ConfigManager.load(data, null, SimpleConfig.class))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // =========================================================================
    // Test Mock Utilities
    // =========================================================================

    private File createPluginJar(String pluginId, Map<String, String> entries) {
        try {
            File jarFile = Files.createTempFile(tempDirectory, "plugin-" + pluginId + "-", ".jar").toFile();
            jarFile.deleteOnExit();

            try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarFile))) {
                for (Map.Entry<String, String> entry : entries.entrySet()) {
                    JarEntry jarEntry = new JarEntry(entry.getKey());
                    jos.putNextEntry(jarEntry);
                    jos.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                    jos.closeEntry();
                }
            }
            return jarFile;
        } catch (IOException e) {
            throw new RuntimeException("Failed to assemble test mock JAR for plugin: " + pluginId, e);
        }
    }

    private PluginData createPluginData(String pluginId, File jarFile) {
        Metadata metadata = new Metadata.Builder()
                .schema(Constants.METADATA_SCHEMA)
                .id(pluginId)
                .name(pluginId + " Display Name")
                .version("1.0.0")
                .description("Test mod description")
                .environment(Environment.BOTH)
                .build();

        return new PluginData(jarFile, null, null, metadata);
    }

    // =========================================================================
    // YAML Commentary Preservation & Generation Tests
    // =========================================================================

    // =========================================================================
    // 6. YAML Commentary Tests
    // =========================================================================

    @Nested
    @DisplayName("6. YAML Comments Handling")
    class CommentHandlingTests {

        @Test
        @DisplayName("Default config.yml extracted from JAR must preserve all comments, headers, and formatting verbatim")
        void testJarExtractedCommentsPreserved() throws IOException {
            String yamlWithComments = """
                    # ==========================================
                    # Avrix Zomboid Mod Configuration Header
                    # ==========================================
                    
                    # Name of the game instance
                    pluginName: "Avrix Survival"
                    
                    # Concurrency limit
                    multiplier: 32
                    enabled: true
                    """;

            File jar = createPluginJar("comment-plugin", Map.of("config.yml", yamlWithComments));
            PluginData pluginData = createPluginData("comment-plugin", jar);

            Config<SimpleConfig> config = ConfigManager.loadDefaultConfig(pluginData, SimpleConfig.class);

            // Verify Java mapping succeeded
            assertThat(config.get().pluginName()).isEqualTo("Avrix Survival");
            assertThat(config.get().multiplier()).isEqualTo(32);

            // Verify raw disk file retained all comments and layout exactly as in JAR
            String diskContent = Files.readString(config.getFile());
            assertThat(diskContent)
                    .contains("# ==========================================")
                    .contains("# Avrix Zomboid Mod Configuration Header")
                    .contains("# Name of the game instance")
                    .contains("# Concurrency limit");
        }

        @Test
        @DisplayName("Memory CommentedConfigurationNode supports programmatic comments during runtime")
        void testInMemoryNodeComments() {
            File jar = createPluginJar("node-comment-plugin", Map.of("config.yml", "pluginName: \"Node\"\nmultiplier: 1\nenabled: true\n"));
            PluginData pluginData = createPluginData("node-comment-plugin", jar);

            Config<CommentedConfigurationNode> config = ConfigManager.loadDefaultConfig(pluginData);

            // Set in-memory comment on node
            config.node().node("multiplier").comment("Custom in-memory multiplier comment");

            // Verify in-memory retention
            assertThat(config.node().node("multiplier").comment()).isEqualTo("Custom in-memory multiplier comment");
        }
    }
}