package com.avrix.api.lua.script;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.krka.kahlua.converter.KahluaConverterManager;
import se.krka.kahlua.integration.LuaCaller;
import se.krka.kahlua.j2se.J2SEPlatform;
import se.krka.kahlua.luaj.compiler.LuaCompiler;
import se.krka.kahlua.vm.JavaFunction;
import se.krka.kahlua.vm.KahluaTable;
import se.krka.kahlua.vm.KahluaThread;
import se.krka.kahlua.vm.LuaClosure;
import zombie.Lua.KahluaNumberConverter;
import zombie.Lua.LuaManager;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for custom Lua loading, require and isolated environments.
 */
@DisplayName("LuaScriptLoader Tests")
class LuaScriptLoaderTest {

    private J2SEPlatform previousPlatform;
    private KahluaTable previousEnvironment;
    private LuaManager.Exposer previousExposer;
    private KahluaThread previousThread;
    private KahluaConverterManager previousConverterManager;
    private LuaCaller previousCaller;

    private J2SEPlatform platform;
    private KahluaTable environment;
    private KahluaConverterManager converterManager;

    @BeforeEach
    void setUp() {
        LuaScriptLoader.unregisterAll();

        previousPlatform = LuaManager.platform;
        previousEnvironment = LuaManager.env;
        previousExposer = LuaManager.exposer;
        previousThread = LuaManager.thread;
        previousConverterManager = LuaManager.converterManager;
        previousCaller = LuaManager.caller;

        platform = new J2SEPlatform();
        environment = platform.newTable();
        environment.rawset("_G", environment);
        environment.rawset("_VERSION", "test");
        environment.rawset("print", (JavaFunction) (frame, arguments) -> 0);
        environment.rawset("require", (JavaFunction) (frame, arguments) -> {
            Object moduleName = frame.get(0);
            Optional<LuaScriptResult> result = LuaScriptLoader.tryRequire(String.valueOf(moduleName));
            frame.push(result.map(LuaScriptResult::value).orElse(null));
            return 1;
        });

        converterManager = new KahluaConverterManager();
        KahluaNumberConverter.install(converterManager);

        LuaManager.platform = platform;
        LuaManager.env = environment;
        LuaManager.converterManager = converterManager;
        LuaManager.thread = new KahluaThread(platform, environment);
        LuaManager.thread.debugOwnerThread = Thread.currentThread();
        LuaManager.caller = new LuaCaller(converterManager);
        LuaManager.exposer = new LuaManager.Exposer(converterManager, platform, environment);

        LuaScriptLoader.onLuaEnvironmentCreated();
        LuaScriptLoader.onLuaEnvironmentReady();
    }

    @AfterEach
    void tearDown() {
        LuaScriptLoader.unregisterAll();

        LuaManager.platform = previousPlatform;
        LuaManager.env = previousEnvironment;
        LuaManager.exposer = previousExposer;
        LuaManager.thread = previousThread;
        LuaManager.converterManager = previousConverterManager;
        LuaManager.caller = previousCaller;
    }

    @Test
    @DisplayName("Should load a single Lua file from a custom path")
    void shouldLoadSingleFile(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("hello.lua");
        Files.writeString(file, "Hello = 'world'", StandardCharsets.UTF_8);

        LuaScriptLoader.load(file);
        LuaScriptLoader.onGameBoot();

        assertThat(environment.rawget("Hello")).isEqualTo("world");
    }

    @Test
    @DisplayName("Should load a group and resolve require from custom sources")
    void shouldLoadGroupAndRequire(@TempDir Path directory) throws IOException {
        Files.writeString(directory.resolve("a.lua"), """
                local b = require("b")
                A = "a:" .. b
                """, StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("b.lua"), """
                B = (B or 0) + 1
                return "b"
                """, StandardCharsets.UTF_8);
        Files.createDirectories(directory.resolve("sub"));
        Files.writeString(directory.resolve("sub/c.lua"), "C = 'c'", StandardCharsets.UTF_8);

        LuaScriptLoader.loadGroup(directory);
        LuaScriptLoader.applyPending();

        assertThat(environment.rawget("A")).isEqualTo("a:b");
        assertThat(environment.rawget("B")).isEqualTo(1.0);
        assertThat(environment.rawget("C")).isEqualTo("c");

        Optional<LuaScriptResult> cached = LuaScriptLoader.tryRequire("b");
        assertThat(cached).isPresent();
        assertThat(cached.get().value()).isEqualTo("b");
        assertThat(environment.rawget("B")).isEqualTo(1.0);
        assertThat(LuaScriptLoader.tryRequire("missing")).isEmpty();
    }

    @Test
    @DisplayName("Should load files and groups from a JAR")
    void shouldLoadFromJar(@TempDir Path directory) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("lua/hello.lua", "JarHello = 'jar'");
        entries.put("lua/group/a.lua", "JarGroupA = true");
        entries.put("lua/group/sub/b.lua", "JarGroupB = true");
        Path jar = createJar(directory, entries);

        LuaScriptSource source = LuaScriptLoader.addSource(LuaScriptSource.jar(jar, "lua"));
        LuaScriptLoader.load("hello");
        LuaScriptLoader.loadGroup(source, "group");
        LuaScriptLoader.applyPending();

        assertThat(environment.rawget("JarHello")).isEqualTo("jar");
        assertThat(environment.rawget("JarGroupA")).isEqualTo(Boolean.TRUE);
        assertThat(environment.rawget("JarGroupB")).isEqualTo(Boolean.TRUE);
    }

    @Test
    @DisplayName("Should load a JAR directory as a group root")
    void shouldLoadJarGroupRoot(@TempDir Path directory) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("lua/root.lua", "JarRoot = true");
        entries.put("lua/sub/child.lua", "JarChild = true");
        Path jar = createJar(directory, entries);

        LuaScriptLoader.loadGroupFromJar(jar, "lua");
        LuaScriptLoader.applyPending();

        assertThat(environment.rawget("JarRoot")).isEqualTo(Boolean.TRUE);
        assertThat(environment.rawget("JarChild")).isEqualTo(Boolean.TRUE);
    }

    @Test
    @DisplayName("Should reject invalid module names")
    void shouldRejectInvalidModuleNames() {
        assertThat(LuaScriptLoader.normalizeModulePath("module/main")).isEqualTo("module/main.lua");
        assertThat(LuaScriptLoader.normalizeModulePath("module\\main.lua")).isEqualTo("module/main.lua");
        assertThat(LuaScriptLoader.normalizeModulePath("../main")).isNull();
        assertThat(LuaScriptLoader.normalizeModulePath("/main")).isNull();
        assertThat(LuaScriptLoader.normalizeModulePath("C:/main")).isNull();
        assertThat(LuaScriptLoader.normalizeModulePath("  ")).isNull();
    }

    @Test
    @DisplayName("Should hide selected global categories in an isolated environment")
    void shouldHideGlobalCategories() {
        environment.rawset("PzSecret", "pz");
        LuaScriptLoader.onLuaEnvironmentCreated();
        environment.rawset("AvrixSecret", "avrix");
        LuaScriptLoader.onLuaEnvironmentReady();

        LuaVisibilityTracker.beginLoadDir();
        environment.rawset("GameSecret", "game");
        LuaVisibilityTracker.onRunLua("game.lua", false);
        LuaVisibilityTracker.endLoadDir();

        LuaVisibilityTracker.beginLoadDir();
        LuaVisibilityTracker.onRunLua("mod.lua", true);
        environment.rawset("ModSecret", "mod");
        LuaVisibilityTracker.endLoadDir();

        LuaScriptEnvironment isolated = LuaScriptLoader.createEnvironment(
                "isolated", LuaEnvironmentPolicy.isolated());

        Object[] result = runIn(isolated.luaEnvironment(),
                "return GameSecret, ModSecret, PzSecret, AvrixSecret, print");
        assertThat(result[0]).isNull();
        assertThat(result[1]).isNull();
        assertThat(result[2]).isNull();
        assertThat(result[3]).isNull();
        assertThat(result[4]).isNotNull();

        LuaScriptEnvironment allowed = LuaScriptLoader.createEnvironment(
                "allowed", LuaEnvironmentPolicy.isolated().toBuilder().allowGlobal("GameSecret").build());
        Object[] allowedResult = runIn(allowed.luaEnvironment(),
                "return GameSecret, ModSecret");
        assertThat(allowedResult[0]).isEqualTo("game");
        assertThat(allowedResult[1]).isNull();
    }

    @Test
    @DisplayName("Should reclassify a game global overwritten by a shared custom script")
    void shouldReclassifyOverwrittenGameGlobal(@TempDir Path directory) throws IOException {
        LuaVisibilityTracker.beginLoadDir();
        environment.rawset("GameTable", "game");
        LuaVisibilityTracker.onRunLua("game.lua", false);
        LuaVisibilityTracker.endLoadDir();

        Files.writeString(directory.resolve("override.lua"), "GameTable = 'custom'", StandardCharsets.UTF_8);
        LuaScriptLoader.load(directory.resolve("override.lua"));
        LuaScriptLoader.applyPending();

        LuaScriptEnvironment gameOnly = LuaScriptLoader.createEnvironment(
                "game-only", LuaEnvironmentPolicy.isolated().toBuilder().includeGameLua(true).build());
        assertThat(runIn(gameOnly.luaEnvironment(), "return GameTable")[0]).isNull();

        LuaScriptEnvironment custom = LuaScriptLoader.createEnvironment(
                "custom", LuaEnvironmentPolicy.isolated().toBuilder().includeCustomLua(true).build());
        assertThat(runIn(custom.luaEnvironment(), "return GameTable")[0]).isEqualTo("custom");
    }

    @Test
    @DisplayName("Should deny standard globals explicitly")
    void shouldDenyStandardGlobals() {
        LuaScriptEnvironment environment = LuaScriptLoader.createEnvironment(
                "denied", LuaEnvironmentPolicy.isolated().toBuilder().denyGlobal("print").build());

        Object[] result = runIn(environment.luaEnvironment(), "return print");
        assertThat(result[0]).isNull();
    }

    @Test
    @DisplayName("Should load sandbox modules into the isolated environment only")
    void shouldLoadSandboxModules(@TempDir Path directory) throws IOException {
        Files.writeString(directory.resolve("secret.lua"), """
                SandboxOnly = "yes"
                return "ok"
                """, StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("main.lua"), """
                local secret = require("secret")
                MainResult = secret .. ":" .. SandboxOnly
                """, StandardCharsets.UTF_8);

        LuaScriptSource source = LuaScriptLoader.addSource(LuaScriptSource.directory(directory));
        LuaScriptEnvironment isolated = LuaScriptLoader.createEnvironment(
                "sandbox", LuaEnvironmentPolicy.isolated());

        LuaScriptLoader.load(isolated, source, "main");
        LuaScriptLoader.applyPending();

        assertThat(isolated.luaEnvironment().rawget("MainResult")).isEqualTo("ok:yes");
        assertThat(environment.rawget("MainResult")).isNull();
        assertThat(environment.rawget("SandboxOnly")).isNull();
    }

    @Test
    @DisplayName("Should reload registered scripts after Lua environment reset")
    void shouldReloadAfterReset(@TempDir Path directory) throws IOException {
        Files.writeString(directory.resolve("reset.lua"), "ResetValue = 'loaded'", StandardCharsets.UTF_8);
        LuaScriptLoader.load(directory.resolve("reset.lua"));
        LuaScriptLoader.applyPending();
        assertThat(environment.rawget("ResetValue")).isEqualTo("loaded");

        replaceLuaState();

        assertThat(environment.rawget("ResetValue")).isEqualTo("loaded");
    }

    private void replaceLuaState() {
        platform = new J2SEPlatform();
        environment = platform.newTable();
        environment.rawset("_G", environment);
        environment.rawset("_VERSION", "test");
        environment.rawset("print", (JavaFunction) (frame, arguments) -> 0);
        environment.rawset("require", (JavaFunction) (frame, arguments) -> {
            Object moduleName = frame.get(0);
            Optional<LuaScriptResult> result = LuaScriptLoader.tryRequire(String.valueOf(moduleName));
            frame.push(result.map(LuaScriptResult::value).orElse(null));
            return 1;
        });

        converterManager = new KahluaConverterManager();
        KahluaNumberConverter.install(converterManager);

        LuaManager.platform = platform;
        LuaManager.env = environment;
        LuaManager.converterManager = converterManager;
        LuaManager.thread = new KahluaThread(platform, environment);
        LuaManager.thread.debugOwnerThread = Thread.currentThread();
        LuaManager.caller = new LuaCaller(converterManager);
        LuaManager.exposer = new LuaManager.Exposer(converterManager, platform, environment);

        LuaScriptLoader.onLuaEnvironmentCreated();
        LuaScriptLoader.onLuaEnvironmentReady();
        LuaScriptLoader.applyPending();
    }

    private Object[] runIn(KahluaTable targetEnvironment, String source) {
        try {
            LuaClosure closure = LuaCompiler.loadstring(source, "lua-script-test", targetEnvironment);
            Object[] result = new LuaCaller(converterManager).pcall(LuaManager.thread, closure);
            return result.length == 0 ? result : Arrays.copyOfRange(result, 1, result.length);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to compile test Lua script", ex);
        }
    }

    private static Path createJar(Path directory, Map<String, String> entries) throws IOException {
        Path jar = directory.resolve("test-scripts.jar");
        try (OutputStream output = Files.newOutputStream(jar);
             JarOutputStream jarOutput = new JarOutputStream(output)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                JarEntry jarEntry = new JarEntry(entry.getKey());
                jarOutput.putNextEntry(jarEntry);
                jarOutput.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                jarOutput.closeEntry();
            }
        }
        return jar;
    }
}
