package com.avrix.api.events;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.krka.kahlua.vm.KahluaTable;
import zombie.Lua.LuaManager;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit tests for the Project Zomboid event bridge behavior that does not require a running Lua VM.
 */
@DisplayName("PzEventBridge Tests")
class PzEventBridgeTest {

    private KahluaTable previousEnvironment;

    @BeforeEach
    void disableLuaEnvironment() {
        previousEnvironment = LuaManager.env;
        LuaManager.env = null;
    }

    @AfterEach
    void restoreLuaEnvironment() {
        LuaManager.env = previousEnvironment;
    }

    @Test
    @DisplayName("Should ignore dispatch and Lua forwarding when the Lua environment is not ready")
    void shouldIgnoreBridgeWhenLuaEnvironmentIsMissing() {
        assertThatCode(() -> {
            PzEventBridge.dispatch("Test_Dynamic");
            PzEventBridge.drain();
        }).doesNotThrowAnyException();

        assertThat(PzEventBridge.forwardToLua("Test_Dynamic")).isFalse();
        assertThat(PzEventBridge.forwardToLua("Test_Dynamic", 1, 2, 3)).isFalse();
    }

    @Test
    @DisplayName("Should publish to Java listeners even when Lua forwarding is unavailable")
    void shouldPublishToJavaWithoutLuaEnvironment() {
        AtomicInteger calls = new AtomicInteger();
        Object listener = new Object() {
            @SubscribeEvent(custom = "Test_PublishBoth")
            void onEvent() {
                calls.incrementAndGet();
            }
        };

        EventManager.register(listener);
        try {
            boolean forwarded = EventManager.publishBoth(new CustomEvent("Test_PublishBoth"));
            assertThat(calls.get()).isEqualTo(1);
            assertThat(forwarded).isFalse();
        } finally {
            EventManager.unregister(listener);
        }
    }
}
