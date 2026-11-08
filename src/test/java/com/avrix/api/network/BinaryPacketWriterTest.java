package com.avrix.api.network;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zombie.core.network.ByteBufferReader;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link BinaryPacketWriter}.
 */
@DisplayName("Binary Packet Writer")
class BinaryPacketWriterTest {

    private enum Sample {
        FIRST,
        SECOND,
        THIRD
    }

    @Test
    @DisplayName("Should write big-endian primitives readable by ByteBufferReader")
    void shouldWritePrimitives() {
        BinaryPacketWriter writer = new BinaryPacketWriter()
                .putByte(0x12)
                .putBoolean(true)
                .putShort(0x3456)
                .putInt(0x789ABCDE)
                .putLong(0x0102030405060708L)
                .putFloat(1.5f)
                .putDouble(-2.25)
                .putChar('Z')
                .putUTF("hi");

        byte[] bytes = writer.toByteArray();
        assertThat(bytes).containsExactly(
                (byte) 0x12,
                (byte) 0x01,
                (byte) 0x34, (byte) 0x56,
                (byte) 0x78, (byte) 0x9A, (byte) 0xBC, (byte) 0xDE,
                (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04,
                (byte) 0x05, (byte) 0x06, (byte) 0x07, (byte) 0x08,
                (byte) 0x3F, (byte) 0xC0, (byte) 0x00, (byte) 0x00,
                (byte) 0xC0, (byte) 0x02, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x5A, (byte) 0x00, (byte) 0x02, (byte) 0x68, (byte) 0x69);

        ByteBufferReader reader = new ByteBufferReader(ByteBuffer.wrap(bytes));
        assertThat(reader.getByte()).isEqualTo((byte) 0x12);
        assertThat(reader.getBoolean()).isTrue();
        assertThat(reader.getShort()).isEqualTo((short) 0x3456);
        assertThat(reader.getInt()).isEqualTo(0x789ABCDE);
        assertThat(reader.getLong()).isEqualTo(0x0102030405060708L);
        assertThat(reader.getFloat()).isEqualTo(1.5f);
        assertThat(reader.getDouble()).isEqualTo(-2.25);
        assertThat(reader.getChar()).isEqualTo('Z');
        assertThat(reader.getUTF()).isEqualTo("hi");
        assertThat(reader.bb.remaining()).isZero();
    }

    @Test
    @DisplayName("Should round-trip enums using their ordinal")
    void shouldWriteEnumOrdinal() {
        byte[] bytes = new BinaryPacketWriter()
                .putEnum(Sample.SECOND)
                .toByteArray();

        assertThat(bytes).containsExactly((byte) 1);
        assertThat(new ByteBufferReader(ByteBuffer.wrap(bytes)).getEnum(Sample.class))
                .isEqualTo(Sample.SECOND);
    }

    @Test
    @DisplayName("Should write raw bytes and copy the remaining bytes of a buffer")
    void shouldWriteBytesAndBuffers() {
        ByteBuffer source = ByteBuffer.wrap(new byte[]{4, 5, 6});
        BinaryPacketWriter writer = new BinaryPacketWriter()
                .putBytes(new byte[]{1, 2, 3})
                .put(source);

        assertThat(writer.toByteArray()).containsExactly((byte) 1, (byte) 2, (byte) 3, (byte) 4, (byte) 5, (byte) 6);
        assertThat(source.position()).isZero();
    }

    @Test
    @DisplayName("Should use an empty string for null UTF values")
    void shouldWriteNullUtfAsEmpty() {
        byte[] bytes = new BinaryPacketWriter().putUTF(null).toByteArray();

        assertThat(bytes).containsExactly((byte) 0, (byte) 0);
        assertThat(new ByteBufferReader(ByteBuffer.wrap(bytes)).getUTF()).isEmpty();
    }

    @Test
    @DisplayName("Should accept the maximum signed short UTF length")
    void shouldAcceptMaximumUtfLength() {
        String value = "a".repeat(Short.MAX_VALUE);
        byte[] bytes = new BinaryPacketWriter().putUTF(value).toByteArray();

        assertThat(new ByteBufferReader(ByteBuffer.wrap(bytes)).getUTF()).hasSize(Short.MAX_VALUE);
    }

    @Test
    @DisplayName("Should reject UTF values that do not fit a signed short length")
    void shouldRejectOversizedUtf() {
        BinaryPacketWriter writer = new BinaryPacketWriter();

        assertThatThrownBy(() -> writer.putUTF("a".repeat(Short.MAX_VALUE + 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32767");
        assertThat(writer.size()).isZero();
    }

    @Test
    @DisplayName("Should report size and return an independent copy")
    void shouldReportSizeAndReturnCopy() {
        BinaryPacketWriter writer = new BinaryPacketWriter(1)
                .putInt(7)
                .putShort(8);

        byte[] first = writer.toByteArray();
        first[0] = 99;

        assertThat(writer.size()).isEqualTo(6);
        assertThat(writer.toByteArray()[0]).isNotEqualTo((byte) 99);
    }

    @Test
    @DisplayName("Should reject null byte arrays and buffers")
    void shouldRejectNullInputs() {
        BinaryPacketWriter writer = new BinaryPacketWriter();

        assertThatThrownBy(() -> writer.putBytes(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> writer.put((ByteBuffer) null)).isInstanceOf(NullPointerException.class);
    }
}
