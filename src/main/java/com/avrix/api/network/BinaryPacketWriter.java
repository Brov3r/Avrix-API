package com.avrix.api.network;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Growable big-endian binary writer for custom network payloads.
 * <p>
 * The format matches Project Zomboid's {@code ByteBufferWriter} primitives for byte, boolean, short, int,
 * long, float, double, char, enum ordinal, and UTF strings. Use this writer to build binary payloads and
 * {@link com.avrix.api.network.BinaryPacketHandler} to read them.
 */
public final class BinaryPacketWriter {

    private static final int MAX_UTF_BYTES = Short.MAX_VALUE;

    private final ByteArrayOutputStream output;

    /**
     * Creates an empty binary packet writer.
     */
    public BinaryPacketWriter() {
        this.output = new ByteArrayOutputStream();
    }

    /**
     * Creates a binary packet writer with an initial capacity.
     *
     * @param initialCapacity initial buffer capacity
     */
    public BinaryPacketWriter(int initialCapacity) {
        this.output = new ByteArrayOutputStream(Math.max(0, initialCapacity));
    }

    /**
     * Writes one byte.
     *
     * @param value source value
     * @return this writer
     */
    public BinaryPacketWriter putByte(int value) {
        output.write(value);
        return this;
    }

    /**
     * Writes one boolean byte.
     *
     * @param value source value
     * @return this writer
     */
    public BinaryPacketWriter putBoolean(boolean value) {
        output.write(value ? 1 : 0);
        return this;
    }

    /**
     * Writes a big-endian short.
     *
     * @param value source value
     * @return this writer
     */
    public BinaryPacketWriter putShort(int value) {
        output.write((value >>> 8) & 0xFF);
        output.write(value & 0xFF);
        return this;
    }

    /**
     * Writes a big-endian integer.
     *
     * @param value source value
     * @return this writer
     */
    public BinaryPacketWriter putInt(int value) {
        output.write((value >>> 24) & 0xFF);
        output.write((value >>> 16) & 0xFF);
        output.write((value >>> 8) & 0xFF);
        output.write(value & 0xFF);
        return this;
    }

    /**
     * Writes a big-endian long.
     *
     * @param value source value
     * @return this writer
     */
    public BinaryPacketWriter putLong(long value) {
        for (int shift = 56; shift >= 0; shift -= 8) {
            output.write((int) ((value >>> shift) & 0xFF));
        }
        return this;
    }

    /**
     * Writes a big-endian float.
     *
     * @param value source value
     * @return this writer
     */
    public BinaryPacketWriter putFloat(float value) {
        return putInt(Float.floatToIntBits(value));
    }

    /**
     * Writes a big-endian double.
     *
     * @param value source value
     * @return this writer
     */
    public BinaryPacketWriter putDouble(double value) {
        return putLong(Double.doubleToLongBits(value));
    }

    /**
     * Writes a big-endian char.
     *
     * @param value source value
     * @return this writer
     */
    public BinaryPacketWriter putChar(char value) {
        return putShort(value);
    }

    /**
     * Writes a UTF string using a signed two-byte length prefix and UTF-8 bytes.
     *
     * @param value source string, or {@code null} for an empty string
     * @return this writer
     * @throws IllegalArgumentException if the encoded string exceeds 32767 bytes
     */
    public BinaryPacketWriter putUTF(String value) {
        byte[] bytes = value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_UTF_BYTES) {
            throw new IllegalArgumentException("UTF payload exceeds " + MAX_UTF_BYTES + " bytes");
        }
        putShort(bytes.length);
        output.write(bytes, 0, bytes.length);
        return this;
    }

    /**
     * Writes an enum ordinal as one byte.
     *
     * @param value source enum, or {@code null} for ordinal zero
     * @return this writer
     * @throws IllegalArgumentException if the enum has more than 256 constants
     */
    public BinaryPacketWriter putEnum(Enum<?> value) {
        int ordinal = value == null ? 0 : value.ordinal();
        if (ordinal > 255) {
            throw new IllegalArgumentException("Enum ordinal exceeds one byte");
        }
        output.write(ordinal);
        return this;
    }

    /**
     * Writes a raw byte array.
     *
     * @param value source bytes
     * @return this writer
     */
    public BinaryPacketWriter putBytes(byte[] value) {
        Objects.requireNonNull(value, "Byte payload cannot be null");
        output.write(value, 0, value.length);
        return this;
    }

    /**
     * Writes the remaining bytes from a byte buffer.
     *
     * @param value source buffer
     * @return this writer
     */
    public BinaryPacketWriter put(ByteBuffer value) {
        Objects.requireNonNull(value, "Byte buffer cannot be null");
        ByteBuffer duplicate = value.duplicate();
        byte[] bytes = new byte[duplicate.remaining()];
        duplicate.get(bytes);
        return putBytes(bytes);
    }

    /**
     * Returns the number of written bytes.
     *
     * @return current size
     */
    public int size() {
        return output.size();
    }

    /**
     * Returns the written bytes.
     *
     * @return copy of the current payload
     */
    public byte[] toByteArray() {
        return output.toByteArray();
    }
}
