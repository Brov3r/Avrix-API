package com.avrix.api.network;

import com.avrix.api.events.EventManager;
import com.avrix.api.events.SubscribeEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zombie.core.network.ByteBufferReader;
import zombie.core.random.RandStandard;
import zombie.network.PacketTypes;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Tests for {@link PacketReceiveDispatcher}.
 */
@DisplayName("Packet Receive Event")
class PacketReceiveDispatcherTest {

    @BeforeEach
    void setUp() {
        try {
            RandStandard.INSTANCE.init();
        } catch (Throwable ignored) {
        }
    }

    @Test
    @DisplayName("Should publish a packet event with a duplicate payload reader")
    void shouldPublishPacketEvent() {
        AtomicReference<PacketReceivedEvent> received = new AtomicReference<>();
        Object listener = new Object() {
            @SubscribeEvent(custom = NetworkEvents.PACKET_RECEIVED)
            public void onPacket(PacketReceivedEvent event) {
                received.set(event);
            }
        };

        ByteBuffer buffer = ByteBuffer.allocate(3);
        buffer.put((byte) 1).put((byte) 2).put((byte) 3);
        buffer.flip();
        ByteBufferReader originalReader = new ByteBufferReader(buffer);

        EventManager.register(listener);
        try {
            PacketReceiveDispatcher.dispatch(
                    PacketTypes.PacketType.Roles, NetworkSide.SERVER, null, originalReader);
        } finally {
            EventManager.unregister(listener);
        }

        assertThat(received.get()).isNotNull();
        assertThat(received.get().type()).isEqualTo(PacketTypes.PacketType.Roles);
        assertThat(received.get().side()).isEqualTo(NetworkSide.SERVER);
        assertThat(received.get().connection()).isNull();
        assertThat(received.get().payload().getByte()).isEqualTo((byte) 1);
        assertThat(received.get().payload().getByte()).isEqualTo((byte) 2);
        assertThat(received.get().payload().getByte()).isEqualTo((byte) 3);
        assertThat(originalReader.position()).isZero();
    }

    @Test
    @DisplayName("Should not fail when no packet listeners are registered")
    void shouldIgnoreWithoutListeners() {
        ByteBuffer buffer = ByteBuffer.allocate(1);
        buffer.put((byte) 1);
        buffer.flip();

        assertThatCode(() -> PacketReceiveDispatcher.dispatch(
                PacketTypes.PacketType.Roles, NetworkSide.SERVER, null, new ByteBufferReader(buffer)))
                .doesNotThrowAnyException();
    }
}
