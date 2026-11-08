package com.avrix.api.network;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.krka.kahlua.vm.KahluaTable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link NetworkPayload}.
 */
@DisplayName("Network Payload Conversion")
class NetworkPayloadTest {

    @Test
    @DisplayName("Should round-trip nested payload values through a Kahlua table")
    void shouldRoundTripPayload() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("name", "Avrix");
        source.put("count", 3);
        source.put("enabled", true);
        source.put("nested", Map.of("key", "value"));
        source.put("list", List.of("a", 2, true));

        KahluaTable table = NetworkPayload.toTable(source);
        Map<String, Object> decoded = NetworkPayload.fromTable(table);

        assertThat(decoded)
                .containsEntry("name", "Avrix")
                .containsEntry("count", 3.0)
                .containsEntry("enabled", true);
        assertThat(((Map<?, ?>) decoded.get("nested")).get("key")).isEqualTo("value");
        assertThat(((Map<?, ?>) decoded.get("list")).get("1")).isEqualTo("a");
        assertThat(((Map<?, ?>) decoded.get("list")).get("2")).isEqualTo(2.0);
        assertThat(((Map<?, ?>) decoded.get("list")).get("3")).isEqualTo(true);
    }

    @Test
    @DisplayName("Should build a payload from alternating key/value arguments")
    void shouldBuildPayloadFromVarargs() {
        KahluaTable table = NetworkPayload.toTable("message", "hello", "amount", 2);

        assertThat(NetworkPayload.fromTable(table))
                .containsEntry("message", "hello")
                .containsEntry("amount", 2.0);
    }

    @Test
    @DisplayName("Should return an empty payload for null or empty input")
    void shouldReturnEmptyPayload() {
        assertThat(NetworkPayload.fromTable(null)).isEmpty();
        assertThat(NetworkPayload.fromTable(NetworkPayload.toTable(Map.of()))).isEmpty();
    }

    @Test
    @DisplayName("Should reject odd varargs, blank keys, and unsupported values")
    void shouldRejectInvalidPayloads() {
        assertThatThrownBy(() -> NetworkPayload.toTable("key"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NetworkPayload.toTable(" ", "value"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NetworkPayload.toTable(Map.of("bad", new Object())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should return an immutable decoded payload map")
    void shouldReturnImmutablePayload() {
        Map<String, Object> decoded = NetworkPayload.fromTable(
                NetworkPayload.toTable("key", "value"));

        assertThatThrownBy(() -> decoded.put("other", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
