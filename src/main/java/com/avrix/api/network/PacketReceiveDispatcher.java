package com.avrix.api.network;

import com.avrix.api.events.EventManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zombie.core.network.ByteBufferReader;
import zombie.core.raknet.UdpConnection;
import zombie.network.PacketTypes;

import java.nio.ByteBuffer;

/**
 * Dispatches {@link NetworkEvents#PACKET_RECEIVED} from the native packet receive mixin.
 */
public final class PacketReceiveDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(PacketReceiveDispatcher.class);

    private PacketReceiveDispatcher() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Publishes a packet received event when listeners are registered.
     *
     * @param type       native packet type
     * @param side       receiving side
     * @param connection server-side connection, or {@code null} on the client
     * @param reader     native packet payload reader
     */
    public static void dispatch(
            PacketTypes.PacketType type,
            NetworkSide side,
            UdpConnection connection,
            ByteBufferReader reader
    ) {
        if (type == null || side == null || reader == null || !EventManager.hasListeners(NetworkEvents.PACKET_RECEIVED)) {
            return;
        }

        try {
            ByteBuffer duplicate = reader.bb.duplicate().order(reader.bb.order());
            EventManager.invoke(NetworkEvents.PACKET_RECEIVED, new PacketReceivedEvent(
                    type, side, connection, new ByteBufferReader(duplicate)
            ));
        } catch (Throwable t) {
            LOGGER.error("Failed to dispatch packet received event for '{}'", type, t);
        }
    }
}
