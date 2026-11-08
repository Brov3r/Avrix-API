package com.avrix.api.network;

import se.krka.kahlua.vm.KahluaTable;

import java.util.Base64;
import java.util.Map;

/**
 * Encodes and decodes the Base64 envelope used to carry binary payloads through the command transport.
 * <p>
 * The envelope is a single Kahlua table entry. Its value is standard Base64 without line breaks, which fits
 * into the signed two-byte string length used by the game transport.
 */
final class BinaryPacketCodec {

    static final String ENVELOPE_KEY = "__avrix_binary";

    /**
     * Maximum decoded payload size.
     * <p>
     * Base64 needs four characters per three input bytes. The game writes string lengths as a signed short,
     * so the encoded value must not exceed 32767 characters. The largest Base64 length that fits is 32764,
     * which corresponds to 24573 raw bytes.
     */
    static final int MAX_PAYLOAD_BYTES = 24_573;

    private static final int MAX_ENCODED_LENGTH = ((MAX_PAYLOAD_BYTES + 2) / 3) * 4;

    private BinaryPacketCodec() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Wraps a binary payload into a command transport table.
     *
     * @param payload raw payload bytes
     * @return Kahlua table containing the Base64 envelope
     * @throws IllegalArgumentException if the payload is {@code null} or exceeds {@link #MAX_PAYLOAD_BYTES}
     */
    static KahluaTable encode(byte[] payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Binary payload cannot be null");
        }
        if (payload.length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException(
                    "Binary payload is too large: " + payload.length + " > " + MAX_PAYLOAD_BYTES + " bytes");
        }
        String encoded = Base64.getEncoder().encodeToString(payload);
        return NetworkPayload.toTable(Map.of(ENVELOPE_KEY, encoded));
    }

    /**
     * Extracts a binary payload from command arguments.
     *
     * @param args raw command arguments, or {@code null}
     * @return decoded bytes, or {@code null} when the arguments do not contain a binary envelope
     * @throws IllegalArgumentException if the envelope is malformed or exceeds {@link #MAX_PAYLOAD_BYTES}
     */
    static byte[] decode(KahluaTable args) {
        if (args == null) {
            return null;
        }
        Object value = args.rawget(ENVELOPE_KEY);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String encoded)) {
            throw new IllegalArgumentException("Binary envelope value must be a Base64 string");
        }
        if (encoded.length() > MAX_ENCODED_LENGTH) {
            throw new IllegalArgumentException(
                    "Encoded binary payload is too large: " + encoded.length() + " > " + MAX_ENCODED_LENGTH);
        }
        byte[] payload;
        try {
            payload = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Binary envelope contains invalid Base64", ex);
        }
        if (payload.length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException(
                    "Binary payload is too large: " + payload.length + " > " + MAX_PAYLOAD_BYTES + " bytes");
        }
        return payload;
    }
}
