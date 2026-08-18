package com.avrix.api.events;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Comprehensive unit test suite for {@link EventManager}.
 * <p>
 * Covers default PZ events, custom string events, custom Event enums, priority ordering,
 * argument adaptation, primitive unboxing and defaults, numeric conversion, resilience,
 * unregistration, and concurrency.
 */
@DisplayName("EventManager Comprehensive Unit Tests")
class EventManagerTest {

    /**
     * Custom Enum implementing Event for testing plugin-defined event registries.
     */
    enum CustomPluginEvents implements Event {
        ON_CUSTOM_QUEST_START("CustomPlugin_OnQuestStart"),
        ON_CUSTOM_SHOP_BUY("CustomPlugin_OnShopBuy");

        private final String eventName;

        CustomPluginEvents(String eventName) {
            this.eventName = eventName;
        }

        @Override
        public String getName() {
            return eventName;
        }
    }

    // =========================================================================
    // Default Game Events (Vanilla DefaultEvents)
    // =========================================================================

    @Nested
    @DisplayName("Default Game Events")
    class DefaultEventsTests {

        static class VanillaListener {
            boolean tickCalled = false;
            String deathPlayerName = null;

            @SubscribeEvent(DefaultEvents.ON_TICK)
            void onTick() {
                this.tickCalled = true;
            }

            @SubscribeEvent(DefaultEvents.ON_PLAYER_DEATH)
            void onPlayerDeath(String playerName) {
                this.deathPlayerName = playerName;
            }
        }

        @Test
        @DisplayName("Should invoke zero-argument handler for DefaultEvents")
        void shouldInvokeZeroArgVanillaHandler() {
            VanillaListener listener = new VanillaListener();
            EventManager.register(listener);

            EventManager.invoke(DefaultEvents.ON_TICK);

            assertThat(listener.tickCalled).isTrue();
            EventManager.unregister(listener);
        }

        @Test
        @DisplayName("Should invoke parameterized handler for DefaultEvents")
        void shouldInvokeParameterizedVanillaHandler() {
            VanillaListener listener = new VanillaListener();
            EventManager.register(listener);

            EventManager.invoke(DefaultEvents.ON_PLAYER_DEATH, "SurvivorBob");

            assertThat(listener.deathPlayerName).isEqualTo("SurvivorBob");
            EventManager.unregister(listener);
        }
    }

    // =========================================================================
    // Custom Events (String & Custom Enum)
    // =========================================================================

    @Nested
    @DisplayName("Custom Events")
    class CustomEventsTests {

        static class CustomEventListener {
            String questId = null;
            int questReward = 0;
            String shopItem = null;

            @SubscribeEvent(custom = "CustomPlugin_OnQuestStart")
            void onQuestStart(String questId, Integer reward) {
                this.questId = questId;
                this.questReward = reward != null ? reward : 0;
            }

            @SubscribeEvent(custom = "CustomPlugin_OnShopBuy")
            void onShopBuy(String item) {
                this.shopItem = item;
            }
        }

        @Test
        @DisplayName("Should invoke custom event via string identifier")
        void shouldInvokeCustomEventByString() {
            CustomEventListener listener = new CustomEventListener();
            EventManager.register(listener);

            EventManager.invoke("CustomPlugin_OnQuestStart", "quest_01", 500);

            assertThat(listener.questId).isEqualTo("quest_01");
            assertThat(listener.questReward).isEqualTo(500);

            EventManager.unregister(listener);
        }

        @Test
        @DisplayName("Should invoke custom event via custom Event enum")
        void shouldInvokeCustomEventByEnum() {
            CustomEventListener listener = new CustomEventListener();
            EventManager.register(listener);

            EventManager.invoke(CustomPluginEvents.ON_CUSTOM_SHOP_BUY, "FireAxe");

            assertThat(listener.shopItem).isEqualTo("FireAxe");

            EventManager.unregister(listener);
        }
    }

    // =========================================================================
    // Priority Execution Order
    // =========================================================================

    @Nested
    @DisplayName("Priority Execution Order")
    class PriorityTests {

        static class PriorityListener {
            final List<String> executionLog = new ArrayList<>();

            @SubscribeEvent(value = DefaultEvents.ON_GAME_START, priority = -100)
            void onLow() {
                executionLog.add("LOW");
            }

            @SubscribeEvent(value = DefaultEvents.ON_GAME_START, priority = 1000)
            void onHighest() {
                executionLog.add("HIGHEST");
            }

            @SubscribeEvent(value = DefaultEvents.ON_GAME_START, priority = 0)
            void onNormal() {
                executionLog.add("NORMAL");
            }

            @SubscribeEvent(value = DefaultEvents.ON_GAME_START, priority = 500)
            void onHigh() {
                executionLog.add("HIGH");
            }
        }

        @Test
        @DisplayName("Should execute listeners strictly in descending priority order")
        void shouldExecuteInDescendingPriorityOrder() {
            PriorityListener listener = new PriorityListener();
            EventManager.register(listener);

            EventManager.invoke(DefaultEvents.ON_GAME_START);

            assertThat(listener.executionLog)
                    .containsExactly("HIGHEST", "HIGH", "NORMAL", "LOW");

            EventManager.unregister(listener);
        }
    }

    // =========================================================================
    // Argument Adaptation (Signature Matching)
    // =========================================================================

    @Nested
    @DisplayName("Argument Adaptation")
    class ArgumentAdaptationTests {

        static class AdaptiveListener {
            boolean noArgsCalled = false;
            String firstArg = null;
            String secondArg = null;
            Object thirdArg = null;

            @SubscribeEvent(custom = "Test_AdaptiveArgs")
            void onNoArgs() {
                this.noArgsCalled = true;
            }

            @SubscribeEvent(custom = "Test_AdaptiveArgs")
            void onSingleArg(String arg1) {
                this.firstArg = arg1;
            }

            @SubscribeEvent(custom = "Test_AdaptiveArgs")
            void onMultipleArgs(String arg1, String arg2, Object arg3) {
                this.secondArg = arg2;
                this.thirdArg = arg3;
            }
        }

        @Test
        @DisplayName("Should adapt arguments when game passes more args than method requires")
        void shouldAdaptWhenMoreArgsProvided() {
            AdaptiveListener listener = new AdaptiveListener();
            EventManager.register(listener);

            EventManager.invoke("Test_AdaptiveArgs", "Param1", "Param2", 42, "Extra1", "Extra2");

            assertThat(listener.noArgsCalled).isTrue();
            assertThat(listener.firstArg).isEqualTo("Param1");
            assertThat(listener.secondArg).isEqualTo("Param2");
            assertThat(listener.thirdArg).isEqualTo(42);

            EventManager.unregister(listener);
        }

        @Test
        @DisplayName("Should fill missing trailing reference arguments with null when fewer args provided")
        void shouldFillMissingReferenceArgsWithNull() {
            AdaptiveListener listener = new AdaptiveListener();
            EventManager.register(listener);

            EventManager.invoke("Test_AdaptiveArgs", "OnlyOne");

            assertThat(listener.firstArg).isEqualTo("OnlyOne");
            assertThat(listener.secondArg).isNull();
            assertThat(listener.thirdArg).isNull();

            EventManager.unregister(listener);
        }
    }

    // =========================================================================
    // Primitive Types, Boxing & Numeric Adaptation
    // =========================================================================

    @Nested
    @DisplayName("Primitive Types & Boxing")
    class PrimitiveTypesTests {

        static class PrimitiveListener {
            int receivedInt = -1;
            float receivedFloat = -1.0f;
            double receivedDouble = -1.0d;
            boolean receivedBoolean = false;
            long receivedLong = -1L;
            short receivedShort = -1;
            byte receivedByte = -1;

            @SubscribeEvent(custom = "Test_Primitives")
            void onPrimitives(int a, float b, double c, boolean d, long e, short f, byte g) {
                this.receivedInt = a;
                this.receivedFloat = b;
                this.receivedDouble = c;
                this.receivedBoolean = d;
                this.receivedLong = e;
                this.receivedShort = f;
                this.receivedByte = g;
            }

            @SubscribeEvent(custom = "Test_PrimitiveDefaults")
            void onMissingPrimitives(int a, boolean b, float c, double d, long e) {
                this.receivedInt = a;
                this.receivedBoolean = b;
                this.receivedFloat = c;
                this.receivedDouble = d;
                this.receivedLong = e;
            }

            @SubscribeEvent(custom = "Test_LuaNumberAdaptation")
            void onLuaNumbers(int damageInt, float damageFloat, long xpLong) {
                this.receivedInt = damageInt;
                this.receivedFloat = damageFloat;
                this.receivedLong = xpLong;
            }
        }

        @Test
        @DisplayName("Should successfully unbox and invoke with exact primitives")
        void shouldHandleExactPrimitives() {
            PrimitiveListener listener = new PrimitiveListener();
            EventManager.register(listener);

            EventManager.invoke("Test_Primitives", 100, 25.5f, 99.99d, true, 123456789L, (short) 12, (byte) 4);

            assertThat(listener.receivedInt).isEqualTo(100);
            assertThat(listener.receivedFloat).isEqualTo(25.5f);
            assertThat(listener.receivedDouble).isEqualTo(99.99d);
            assertThat(listener.receivedBoolean).isTrue();
            assertThat(listener.receivedLong).isEqualTo(123456789L);
            assertThat(listener.receivedShort).isEqualTo((short) 12);
            assertThat(listener.receivedByte).isEqualTo((byte) 4);

            EventManager.unregister(listener);
        }

        @Test
        @DisplayName("Should populate primitive defaults (0, false) instead of NPE when fewer args provided")
        void shouldFillPrimitiveDefaultsOnMissingArgs() {
            PrimitiveListener listener = new PrimitiveListener();
            EventManager.register(listener);

            // Handler expects (int, boolean, float, double, long), but we pass only the first int
            EventManager.invoke("Test_PrimitiveDefaults", 42);

            assertThat(listener.receivedInt).isEqualTo(42);
            assertThat(listener.receivedBoolean).isFalse();
            assertThat(listener.receivedFloat).isEqualTo(0.0f);
            assertThat(listener.receivedDouble).isEqualTo(0.0d);
            assertThat(listener.receivedLong).isEqualTo(0L);

            EventManager.unregister(listener);
        }

        @Test
        @DisplayName("Should automatically adapt Lua Double numbers into int, float, and long parameters")
        void shouldAdaptLuaDoublesToPrimitives() {
            PrimitiveListener listener = new PrimitiveListener();
            EventManager.register(listener);

            // In PZ Kahlua VM, all numbers originate as Double objects
            Double luaDamage1 = 50.0d;
            Double luaDamage2 = 75.5d;
            Double luaXp = 1000.0d;

            EventManager.invoke("Test_LuaNumberAdaptation", luaDamage1, luaDamage2, luaXp);

            assertThat(listener.receivedInt).isEqualTo(50);
            assertThat(listener.receivedFloat).isEqualTo(75.5f);
            assertThat(listener.receivedLong).isEqualTo(1000L);

            EventManager.unregister(listener);
        }
    }

    // =========================================================================
    // Unregistration
    // =========================================================================

    @Nested
    @DisplayName("Unregistration")
    class UnregistrationTests {

        static class SimpleListener {
            int callCount = 0;

            @SubscribeEvent(DefaultEvents.ON_TICK)
            void onTick() {
                callCount++;
            }
        }

        @Test
        @DisplayName("Should not receive events after unregister is called")
        void shouldNotReceiveEventsAfterUnregister() {
            SimpleListener listener = new SimpleListener();
            EventManager.register(listener);

            EventManager.invoke(DefaultEvents.ON_TICK);
            assertThat(listener.callCount).isEqualTo(1);

            EventManager.unregister(listener);

            EventManager.invoke(DefaultEvents.ON_TICK);
            assertThat(listener.callCount).isEqualTo(1);
        }
    }

    // =========================================================================
    // Resilience & Error Handling
    // =========================================================================

    @Nested
    @DisplayName("Resilience & Error Handling")
    class ResilienceTests {

        static class FaultyListener {
            @SubscribeEvent(custom = "Test_Faulty")
            void throwError() {
                throw new RuntimeException("Simulated listener explosion");
            }
        }

        static class HealthyListener {
            boolean survived = false;

            @SubscribeEvent(custom = "Test_Faulty")
            void onEvent() {
                this.survived = true;
            }
        }

        @Test
        @DisplayName("Exception in one listener must not prevent subsequent listeners from executing")
        void exceptionShouldNotBreakPipeline() {
            FaultyListener faulty = new FaultyListener();
            HealthyListener healthy = new HealthyListener();

            EventManager.register(faulty);
            EventManager.register(healthy);

            assertThatCode(() -> EventManager.invoke("Test_Faulty"))
                    .doesNotThrowAnyException();

            assertThat(healthy.survived).isTrue();

            EventManager.unregister(faulty);
            EventManager.unregister(healthy);
        }

        static class TypeMismatchListener {
            @SubscribeEvent(custom = "Test_TypeMismatch")
            void expectInteger(Integer number) {
                // Throws ClassCastException if String is passed
            }
        }

        @Test
        @DisplayName("ClassCastException on parameter type mismatch should be gracefully caught and logged")
        void typeMismatchShouldBeGraceful() {
            TypeMismatchListener listener = new TypeMismatchListener();
            EventManager.register(listener);

            assertThatCode(() -> EventManager.invoke("Test_TypeMismatch", "NotAnInteger"))
                    .doesNotThrowAnyException();

            EventManager.unregister(listener);
        }
    }

    // =========================================================================
    // Edge Cases & Concurrency
    // =========================================================================

    @Nested
    @DisplayName("Edge Cases & Concurrency")
    class EdgeCasesTests {

        static class EmptyClass {
            // No @SubscribeEvent annotations
        }

        @Test
        @DisplayName("Registering class without annotations should be a safe no-op")
        void shouldHandleClassWithoutAnnotations() {
            EmptyClass listener = new EmptyClass();
            assertThatCode(() -> {
                EventManager.register(listener);
                EventManager.unregister(listener);
            }).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Invoking event with null name or null arguments should be safe")
        void shouldHandleNullInvocations() {
            assertThatCode(() -> {
                EventManager.invoke((String) null);
                EventManager.invoke((Event) null);
                EventManager.invoke("UnregisteredEvent", (Object[]) null);
            }).doesNotThrowAnyException();
        }

        static class ConcurrentListener {
            final AtomicInteger counter = new AtomicInteger(0);

            @SubscribeEvent(custom = "Test_Concurrent")
            void onConcurrent() {
                counter.incrementAndGet();
            }
        }

        @Test
        @DisplayName("Should handle concurrent invocations safely across multiple worker threads")
        void shouldHandleConcurrentInvocations() throws InterruptedException {
            ConcurrentListener listener = new ConcurrentListener();
            EventManager.register(listener);

            int threads = 10;
            int callsPerThread = 100;
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CountDownLatch latch = new CountDownLatch(threads);

            for (int t = 0; t < threads; t++) {
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < callsPerThread; i++) {
                            EventManager.invoke("Test_Concurrent");
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            boolean completed = latch.await(5, TimeUnit.SECONDS);
            executor.shutdown();

            assertThat(completed).isTrue();
            assertThat(listener.counter.get()).isEqualTo(threads * callsPerThread);

            EventManager.unregister(listener);
        }
    }
}
