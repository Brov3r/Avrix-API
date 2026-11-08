package com.avrix.api.network;

/**
 * Event names published by the Avrix network layer.
 */
public final class NetworkEvents {

    /**
     * Fired when a native Project Zomboid packet is about to be processed.
     * <p>
     * The event runs before packet authorization and native parsing. The payload reader is a
     * duplicate of the remaining bytes, so listeners can inspect it without consuming the game's
     * reader.
     * <p>
     * Listener signature: {@code void onPacket(com.avrix.api.network.PacketReceivedEvent event)}.
     */
    public static final String PACKET_RECEIVED = "Avrix_PacketReceived";

    private NetworkEvents() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}
