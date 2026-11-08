package com.avrix.api.lua;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.krka.kahlua.converter.KahluaConverterManager;
import se.krka.kahlua.integration.LuaCaller;
import se.krka.kahlua.integration.annotations.LuaMethod;
import se.krka.kahlua.j2se.J2SEPlatform;
import se.krka.kahlua.luaj.compiler.LuaCompiler;
import se.krka.kahlua.vm.KahluaTable;
import se.krka.kahlua.vm.KahluaThread;
import se.krka.kahlua.vm.LuaClosure;
import zombie.HiddenFromLua;
import zombie.Lua.KahluaNumberConverter;
import zombie.Lua.LuaManager;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for Java-to-Lua exposure without running the full game.
 */
@DisplayName("LuaExposer Tests")
class LuaExposerTest {

    private J2SEPlatform previousPlatform;
    private KahluaTable previousEnvironment;
    private LuaManager.Exposer previousExposer;
    private KahluaThread previousThread;
    private KahluaConverterManager previousConverterManager;

    private J2SEPlatform platform;
    private KahluaTable environment;
    private LuaManager.Exposer exposer;
    private KahluaConverterManager converterManager;

    @BeforeEach
    void setUp() {
        LuaExposer.unregisterAll();

        previousPlatform = LuaManager.platform;
        previousEnvironment = LuaManager.env;
        previousExposer = LuaManager.exposer;
        previousThread = LuaManager.thread;
        previousConverterManager = LuaManager.converterManager;

        platform = new J2SEPlatform();
        environment = newEnvironment(platform);
        converterManager = new KahluaConverterManager();
        KahluaNumberConverter.install(converterManager);

        LuaManager.platform = platform;
        LuaManager.env = environment;
        LuaManager.converterManager = converterManager;
        LuaManager.thread = new KahluaThread(platform, environment);
        LuaManager.thread.debugOwnerThread = Thread.currentThread();
        LuaManager.exposer = new LuaManager.Exposer(converterManager, platform, environment);
        exposer = LuaManager.exposer;
    }

    @AfterEach
    void tearDown() {
        LuaExposer.unregisterAll();

        LuaManager.platform = previousPlatform;
        LuaManager.env = previousEnvironment;
        LuaManager.exposer = previousExposer;
        LuaManager.thread = previousThread;
        LuaManager.converterManager = previousConverterManager;
    }

    @Test
    @DisplayName("Should expose a class with constructors, statics and instance methods")
    void shouldExposeClass() throws IOException {
        LuaExposer.exposeClass(SampleApi.class);
        LuaExposer.applyPending();

        KahluaTable classTable = (KahluaTable) environment.rawget("SampleApi");
        assertThat(classTable).isNotNull();
        assertThat(classTable.rawget("class")).isEqualTo(SampleApi.class);
        assertThat(classTable.rawget("new")).isNotNull();
        assertThat(classTable.rawget("staticValue")).isNotNull();
        assertThat(classTable.rawget("STATIC_FIELD")).isEqualTo("field");
        assertThat(exposer.typeMap).containsEntry("SampleApi", SampleApi.class);

        KahluaTable index = LuaExposureSupport.getIndexTable(SampleApi.class, environment);
        assertThat(index).isNotNull();
        assertThat(index.rawget("getValue")).isNotNull();
        assertThat(index.rawget("hidden")).isNull();

        Object[] result = runLua("""
                local api = SampleApi.new('hello')
                return api:getValue(), SampleApi.staticValue()
                """);
        assertThat(result).containsExactly("hello", "static");
    }

    @Test
    @DisplayName("Should expose a selected instance method and remove it on unregister")
    void shouldExposeSelectedMethod() {
        LuaRegistration registration = LuaExposer.exposeMethod(SampleApi.class, "getValue", "value");
        LuaExposer.applyPending();

        KahluaTable index = LuaExposureSupport.getIndexTable(SampleApi.class, environment);
        assertThat(index).isNotNull();
        assertThat(index.rawget("value")).isNotNull();
        assertThat(index.rawget("getValue")).isNull();

        assertThat(LuaExposer.unregister(registration)).isTrue();
        KahluaTable indexAfterUnregister = LuaExposureSupport.getIndexTable(SampleApi.class, environment);
        assertThat(indexAfterUnregister).isNotNull();
        assertThat(indexAfterUnregister.rawget("value")).isNull();
        assertThat(LuaExposer.unregister(registration)).isFalse();
    }

    @Test
    @DisplayName("Should expose a bound instance method as a global function")
    void shouldExposeBoundGlobalFunction() throws IOException {
        LuaRegistration registration = LuaExposer.exposeGlobal(
                "sampleBound", new SampleApi("bound"), "getValue");
        LuaExposer.applyPending();

        assertThat(runLua("return sampleBound()")).containsExactly("bound");

        assertThat(LuaExposer.unregister(registration)).isTrue();
        assertThat(environment.rawget("sampleBound")).isNull();
    }

    @Test
    @DisplayName("Should expose a static method as a global function")
    void shouldExposeStaticGlobalFunction() throws IOException {
        LuaRegistration registration = LuaExposer.exposeGlobal(
                "sampleStaticFn", SampleApi.class, "staticValue");
        LuaExposer.applyPending();

        assertThat(runLua("return sampleStaticFn()")).containsExactly("static");

        assertThat(LuaExposer.unregister(registration)).isTrue();
        assertThat(environment.rawget("sampleStaticFn")).isNull();
    }

    @Test
    @DisplayName("Should expose annotated methods of a global object")
    void shouldExposeGlobalObject() throws IOException {
        LuaRegistration registration = LuaExposer.exposeGlobalObject(new GlobalFunctions());
        LuaExposer.applyPending();

        assertThat(runLua("return sampleGlobal('hello')")).containsExactly("hello");

        assertThat(LuaExposer.unregister(registration)).isTrue();
        assertThat(environment.rawget("sampleGlobal")).isNull();
    }

    @Test
    @DisplayName("Should defer registration until the Lua environment is ready")
    void shouldDeferRegistration() {
        LuaManager.env = null;
        LuaManager.exposer = null;

        LuaExposer.exposeClass(SampleApi.class);
        assertThat(environment.rawget("SampleApi")).isNull();

        LuaManager.env = environment;
        LuaManager.exposer = exposer;
        LuaExposer.applyPending();

        assertThat(environment.rawget("SampleApi")).isNotNull();
    }

    @Test
    @DisplayName("Should reapply registrations after a Lua environment reset")
    void shouldReapplyAfterEnvironmentReset() {
        LuaExposer.exposeClass(SampleApi.class);
        LuaExposer.applyPending();
        assertThat(environment.rawget("SampleApi")).isNotNull();

        J2SEPlatform newPlatform = new J2SEPlatform();
        KahluaTable newEnvironment = newEnvironment(newPlatform);
        KahluaConverterManager newConverterManager = new KahluaConverterManager();
        KahluaNumberConverter.install(newConverterManager);

        LuaManager.platform = newPlatform;
        LuaManager.env = newEnvironment;
        LuaManager.converterManager = newConverterManager;
        LuaManager.thread = new KahluaThread(newPlatform, newEnvironment);
        LuaManager.thread.debugOwnerThread = Thread.currentThread();
        LuaManager.exposer = new LuaManager.Exposer(newConverterManager, newPlatform, newEnvironment);

        LuaExposer.applyPending();
        assertThat(newEnvironment.rawget("SampleApi")).isNotNull();
    }

    @Test
    @DisplayName("Should not overwrite an occupied global class name")
    void shouldNotOverwriteOccupiedClassName() {
        KahluaTable foreign = platform.newTable();
        environment.rawset("SampleApi", foreign);

        LuaRegistration registration = LuaExposer.exposeClass(SampleApi.class);
        LuaExposer.applyPending();

        assertThat(environment.rawget("SampleApi")).isSameAs(foreign);
        assertThat(exposer.typeMap).doesNotContainKey("SampleApi");

        assertThat(LuaExposer.unregister(registration)).isTrue();
        assertThat(environment.rawget("SampleApi")).isSameAs(foreign);
    }

    @Test
    @DisplayName("Should remove all exposures")
    void shouldRemoveAllExposures() {
        LuaExposer.exposeClass(SampleApi.class);
        LuaExposer.exposeGlobal("sampleStaticFn", SampleApi.class, "staticValue");
        LuaExposer.exposeGlobalObject(new GlobalFunctions());
        LuaExposer.applyPending();

        LuaExposer.unregisterAll();

        assertThat(environment.rawget("SampleApi")).isNull();
        assertThat(environment.rawget("sampleStaticFn")).isNull();
        assertThat(environment.rawget("sampleGlobal")).isNull();
        assertThat(exposer.typeMap).doesNotContainKey("SampleApi");
    }

    @Test
    @DisplayName("Should return the existing registration for a duplicate exposure")
    void shouldReturnExistingRegistration() {
        LuaRegistration first = LuaExposer.exposeGlobal("duplicate", SampleApi.class, "staticValue");
        LuaRegistration second = LuaExposer.exposeGlobal("duplicate", SampleApi.class, "staticValue");

        assertThat(second).isSameAs(first);
    }

    @Test
    @DisplayName("Should allow exposing a class again after unregister")
    void shouldAllowReExposingClass() throws IOException {
        LuaRegistration first = LuaExposer.exposeClass(SampleApi.class);
        LuaExposer.applyPending();
        assertThat(LuaExposer.unregister(first)).isTrue();
        assertThat(environment.rawget("SampleApi")).isNull();

        LuaRegistration second = LuaExposer.exposeClass(SampleApi.class);
        LuaExposer.applyPending();

        assertThat(environment.rawget("SampleApi")).isNotNull();
        assertThat(runLua("local api = SampleApi.new('again') return api:getValue()"))
                .containsExactly("again");
        assertThat(LuaExposer.unregister(second)).isTrue();
    }

    @Test
    @DisplayName("Should reject static methods as instance exposures")
    void shouldRejectStaticInstanceExposure() {
        assertThatThrownBy(() -> LuaExposer.exposeMethod(SampleApi.class, "staticValue", "staticValue"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Static");
    }

    @Test
    @DisplayName("Should reject methods annotated with @HiddenFromLua")
    void shouldRejectHiddenMethod() {
        assertThatThrownBy(() -> LuaExposer.exposeMethod(SampleApi.class, "hidden", "hidden"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HiddenFromLua");
    }

    private Object[] runLua(String source) throws IOException {
        LuaClosure closure = LuaCompiler.loadstring(source, "lua-exposer-test", environment);
        Object[] result = new LuaCaller(converterManager).pcall(LuaManager.thread, closure);
        return result.length == 0 ? result : java.util.Arrays.copyOfRange(result, 1, result.length);
    }

    private static KahluaTable newEnvironment(J2SEPlatform platform) {
        KahluaTable environment = platform.newTable();
        environment.rawset("_G", environment);
        return environment;
    }

    /**
     * Sample class used to verify class and method exposure.
     */
    public static final class SampleApi {

        /**
         * Public static field exposed to Lua.
         */
        public static final String STATIC_FIELD = "field";

        private final String value;

        /**
         * Creates a sample API instance.
         *
         * @param value value returned by {@link #getValue()}
         */
        public SampleApi(String value) {
            this.value = value;
        }

        /**
         * Returns a static value.
         *
         * @return static value
         */
        public static String staticValue() {
            return "static";
        }

        /**
         * Returns the instance value.
         *
         * @return instance value
         */
        public String getValue() {
            return value;
        }

        /**
         * Method hidden from Lua.
         *
         * @return hidden value
         */
        @HiddenFromLua
        public String hidden() {
            return "hidden";
        }
    }

    /**
     * Sample global function holder used to verify {@code @LuaMethod} exposure.
     */
    public static final class GlobalFunctions {

        /**
         * Returns the supplied value.
         *
         * @param value value to return
         * @return the supplied value
         */
        @LuaMethod(name = "sampleGlobal", global = true)
        public String sampleGlobal(String value) {
            return value;
        }
    }
}
