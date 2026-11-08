package com.avrix.api.network;

import zombie.characters.IsoPlayer;
import zombie.core.raknet.UdpConnection;

import java.util.Objects;

/**
 * Context describing a received network message.
 *
 * @param side       side that received the message
 * @param player     player associated with the message, or {@code null} when unavailable
 * @param connection network connection associated with the message, or {@code null} when unavailable
 */
public record NetworkContext(NetworkSide side, IsoPlayer player, UdpConnection connection) {

    /**
     * Validates the required side.
     *
     * @param side       side that received the message
     * @param player     player associated with the message
     * @param connection network connection associated with the message
     */
    public NetworkContext {
        Objects.requireNonNull(side, "Network side cannot be null");
    }

    /**
     * Returns whether this context belongs to the server.
     *
     * @return {@code true} for server-side contexts
     */
    public boolean isServer() {
        return side == NetworkSide.SERVER;
    }

    /**
     * Returns whether this context belongs to the client.
     *
     * @return {@code true} for client-side contexts
     */
    public boolean isClient() {
        return side == NetworkSide.CLIENT;
    }
}
