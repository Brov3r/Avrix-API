package com.avrix.api.network;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.krka.kahlua.vm.KahluaTable;

import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link BinaryPacketCodec}.
 */
@DisplayName("Binary Packet Codec")
class BinaryPacketCodecTest {

    @Test
    @DisplayName("Should round-trip a binary payload through the Base64 envelope")
    void shouldRoundTripPayload() {
        byte[] payload = {0, 1, 2, 127, -1, -128};

        KahluaTable envelope = BinaryPacketCodec.encode(payload);

        assertThat(BinaryPacketCodec.decode(envelope)).containsExactly(payload);
        assertThat(envelope.rawget(BinaryPacketCodec.ENVELOPE_KEY))
                .isEqualTo(Base64.getEncoder().encodeToString(payload));
    }

    @Test
    @DisplayName("Should round-trip empty payloads")
    void shouldRoundTripEmptyPayload() {
        KahluaTable envelope = BinaryPacketCodec.encode(new byte[0]);

        assertThat(envelope.rawget(BinaryPacketCodec.ENVELOPE_KEY)).isEqualTo("");
        assertThat(BinaryPacketCodec.decode(envelope)).isEmpty();
    }

    @Test
    @DisplayName("Should accept the maximum payload size")
    void shouldAcceptMaximumPayload() {
        KahluaTable envelope = BinaryPacketCodec.encode(new byte[BinaryPacketCodec.MAX_PAYLOAD_BYTES]);
        String encoded = (String) envelope.rawget(BinaryPacketCodec.ENVELOPE_KEY);

        assertThat(encoded).hasSize(32764);
        assertThat(BinaryPacketCodec.decode(envelope)).hasSize(BinaryPacketCodec.MAX_PAYLOAD_BYTES);
    }

    @Test
    @DisplayName("Should reject oversized, null, and malformed payloads")
    void shouldRejectInvalidPayloads() {
        assertThatThrownBy(() -> BinaryPacketCodec.encode(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BinaryPacketCodec.encode(new byte[BinaryPacketCodec.MAX_PAYLOAD_BYTES + 1]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BinaryPacketCodec.decode(
                NetworkPayload.toTable(Map.of(BinaryPacketCodec.ENVELOPE_KEY, "not-base64!"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BinaryPacketCodec.decode(
                NetworkPayload.toTable(Map.of(BinaryPacketCodec.ENVELOPE_KEY, 42))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BinaryPacketCodec.decode(
                NetworkPayload.toTable(Map.of(
                        BinaryPacketCodec.ENVELOPE_KEY,
                        Base64.getEncoder().encodeToString(
                                new byte[BinaryPacketCodec.MAX_PAYLOAD_BYTES + 1])))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should return null when no binary envelope is present")
    void shouldReturnNullWithoutEnvelope() {
        assertThat(BinaryPacketCodec.decode(null)).isNull();
        assertThat(BinaryPacketCodec.decode(NetworkPayload.toTable(Map.of()))).isNull();
        assertThat(BinaryPacketCodec.decode(NetworkPayload.toTable(Map.of("message", "hi")))).isNull();
    }
}
