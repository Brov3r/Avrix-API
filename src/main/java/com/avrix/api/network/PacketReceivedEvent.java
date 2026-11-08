package com.avrix.api.network;

import zombie.core.network.ByteBufferReader;
import zombie.core.raknet.UdpConnection;
import zombie.network.PacketTypes;

import java.util.Objects;

/**
 * Event describing a native Project Zomboid packet received by the client or server.
 * <p>
 * The event is published before packet authorization and native parsing. The {@link #payload()} reader
 * is a duplicate of the packet's remaining bytes, so reading it does not advance the reader used by
 * the game. Listeners should not assume that native consistency or authorization checks have passed.
 *
 * @param type       native packet type
 * @param side       side that received the packet
 * @param connection server-side connection, or {@code null} on the client
 * @param payload    duplicate packet payload reader
 */
public record PacketReceivedEvent(
        PacketTypes.PacketType type,
        NetworkSide side,
        UdpConnection connection,
        ByteBufferReader payload
) {

    /**
     * Validates the packet event.
     *
     * @param type       native packet type
     * @param side       receiving side
     * @param connection server-side connection
     * @param payload    duplicate payload reader
     */
    public PacketReceivedEvent {
        Objects.requireNonNull(type, "Packet type cannot be null");
        Objects.requireNonNull(side, "Network side cannot be null");
        Objects.requireNonNull(payload, "Packet payload cannot be null");
    }
}
