package com.avrix.api.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zombie.characters.IsoPlayer;
import zombie.core.raknet.UdpConnection;
import zombie.network.GameClient;
import zombie.network.GameServer;
import zombie.network.IConnection;
import zombie.network.PacketTypes;
import zombie.network.packets.INetworkPacket;

/**
 * Thin convenience wrapper around Project Zomboid's native {@link INetworkPacket} send helpers.
 * <p>
 * This class is intended for plugins that already use a game packet type and its {@code setData}
 * contract. For plugin-owned messages, prefer {@link NetworkManager} and {@link NetworkChannel}.
 */
public final class NetworkPackets {

    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkPackets.class);

    private NetworkPackets() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Sends a packet from the client to the server.
     *
     * @param type   packet type
     * @param values packet values
     * @return {@code true} if the send was attempted
     */
    public static boolean sendToServer(PacketTypes.PacketType type, Object... values) {
        if (!isValid(type) || !GameClient.client) {
            return false;
        }
        try {
            INetworkPacket.send(type, values);
            return true;
        } catch (Exception ex) {
            LOGGER.error("Failed to send packet '{}' to the server", type, ex);
            return false;
        }
    }

    /**
     * Sends a packet to a specific connection.
     *
     * @param connection target connection
     * @param type       packet type
     * @param values     packet values
     * @return {@code true} if the send was attempted
     */
    public static boolean sendToConnection(IConnection connection, PacketTypes.PacketType type, Object... values) {
        if (connection == null || !isValid(type)) {
            return false;
        }
        try {
            INetworkPacket.send(connection, type, values);
            return true;
        } catch (Exception ex) {
            LOGGER.error("Failed to send packet '{}' to a connection", type, ex);
            return false;
        }
    }

    /**
     * Sends a packet to a specific player.
     *
     * @param player target player
     * @param type   packet type
     * @param values packet values
     * @return {@code true} if the send was attempted
     */
    public static boolean sendToPlayer(IsoPlayer player, PacketTypes.PacketType type, Object... values) {
        if (player == null || !isValid(type) || !GameServer.server) {
            return false;
        }
        try {
            INetworkPacket.send(player, type, values);
            return true;
        } catch (Exception ex) {
            LOGGER.error("Failed to send packet '{}' to a player", type, ex);
            return false;
        }
    }

    /**
     * Sends a packet to all fully connected clients.
     *
     * @param type   packet type
     * @param values packet values
     * @return {@code true} if the send was attempted
     */
    public static boolean sendToAll(PacketTypes.PacketType type, Object... values) {
        if (!isValid(type) || !GameServer.server) {
            return false;
        }
        try {
            INetworkPacket.sendToAll(type, values);
            return true;
        } catch (Exception ex) {
            LOGGER.error("Failed to broadcast packet '{}'", type, ex);
            return false;
        }
    }

    /**
     * Sends a packet to all fully connected clients except one connection.
     *
     * @param type     packet type
     * @param excluded excluded connection
     * @param values   packet values
     * @return {@code true} if the send was attempted
     */
    public static boolean sendToAll(
            PacketTypes.PacketType type,
            IConnection excluded,
            Object... values
    ) {
        if (!isValid(type) || !GameServer.server) {
            return false;
        }
        try {
            INetworkPacket.sendToAll(type, excluded, values);
            return true;
        } catch (Exception ex) {
            LOGGER.error("Failed to broadcast packet '{}'", type, ex);
            return false;
        }
    }

    /**
     * Sends a packet to fully connected clients near a world position.
     *
     * @param type   packet type
     * @param x      world X coordinate
     * @param y      world Y coordinate
     * @param values packet values
     * @return {@code true} if the send was attempted
     */
    public static boolean sendToRelative(
            PacketTypes.PacketType type,
            float x,
            float y,
            Object... values
    ) {
        if (!isValid(type) || !GameServer.server) {
            return false;
        }
        try {
            INetworkPacket.sendToRelative(type, x, y, values);
            return true;
        } catch (Exception ex) {
            LOGGER.error("Failed to send relative packet '{}'", type, ex);
            return false;
        }
    }

    /**
     * Sends a packet to fully connected clients near a world position except one connection.
     *
     * @param type     packet type
     * @param excluded excluded connection
     * @param x        world X coordinate
     * @param y        world Y coordinate
     * @param values   packet values
     * @return {@code true} if the send was attempted
     */
    public static boolean sendToRelative(
            PacketTypes.PacketType type,
            IConnection excluded,
            float x,
            float y,
            Object... values
    ) {
        if (!isValid(type) || !GameServer.server) {
            return false;
        }
        try {
            INetworkPacket.sendToRelative(type, excluded, x, y, values);
            return true;
        } catch (Exception ex) {
            LOGGER.error("Failed to send relative packet '{}'", type, ex);
            return false;
        }
    }

    /**
     * Validates a packet type.
     *
     * @param type packet type
     * @return {@code true} if the packet type is usable
     */
    private static boolean isValid(PacketTypes.PacketType type) {
        if (type == null) {
            LOGGER.warn("Cannot send a packet with a null type");
            return false;
        }
        if (type.handler == null) {
            LOGGER.warn("Packet type '{}' has no INetworkPacket handler; use NetworkManager for custom messages", type);
            return false;
        }
        return true;
    }
}
