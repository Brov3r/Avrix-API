package com.avrix.api.network;

import zombie.characters.IsoPlayer;
import zombie.core.raknet.UdpConnection;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Namespaced network channel for plugin messages and handlers.
 * <p>
 * Channels are built on Project Zomboid's native {@code ClientCommand}/{@code ServerCommand} transport.
 * Register handlers with {@link #on(String, NetworkHandler)} and send messages through the channel methods.
 */
public final class NetworkChannel {

    private final String module;
    private final Map<String, NetworkHandler> handlers = new ConcurrentHashMap<>();
    private final Map<String, BinaryPacketHandler> binaryHandlers = new ConcurrentHashMap<>();

    /**
     * Creates a channel for the given module.
     *
     * @param module validated module name
     */
    NetworkChannel(String module) {
        this.module = module;
    }

    /**
     * Returns the module name used by this channel.
     *
     * @return module name
     */
    public String module() {
        return module;
    }

    /**
     * Registers a handler for an incoming command.
     * <p>
     * Registering a map handler removes any binary handler for the same command. A command uses either
     * the map payload format or the binary payload format, not both.
     *
     * @param command command name
     * @param handler handler to invoke
     * @return this channel
     * @throws IllegalArgumentException if the command is blank or the handler is null
     */
    public NetworkChannel on(String command, NetworkHandler handler) {
        String cleanCommand = NetworkManager.validateCommand(command);
        NetworkHandler cleanHandler = Objects.requireNonNull(handler, "Network handler cannot be null");
        binaryHandlers.remove(cleanCommand);
        handlers.put(cleanCommand, cleanHandler);
        return this;
    }

    /**
     * Registers a binary handler for an incoming command.
     * <p>
     * Registering a binary handler removes any map handler for the same command. A command uses either
     * the map payload format or the binary payload format, not both.
     *
     * @param command command name
     * @param handler binary handler to invoke
     * @return this channel
     * @throws IllegalArgumentException if the command is blank or the handler is null
     */
    public NetworkChannel onBinary(String command, BinaryPacketHandler handler) {
        String cleanCommand = NetworkManager.validateCommand(command);
        BinaryPacketHandler cleanHandler = Objects.requireNonNull(handler, "Binary network handler cannot be null");
        handlers.remove(cleanCommand);
        binaryHandlers.put(cleanCommand, cleanHandler);
        return this;
    }

    /**
     * Removes a command handler.
     *
     * @param command command name
     * @return this channel
     */
    public NetworkChannel off(String command) {
        String cleanCommand = NetworkManager.validateCommand(command);
        handlers.remove(cleanCommand);
        binaryHandlers.remove(cleanCommand);
        return this;
    }

    /**
     * Removes all handlers from this channel.
     */
    public void clear() {
        handlers.clear();
        binaryHandlers.clear();
    }

    /**
     * Resolves a handler for a command.
     *
     * @param command command name
     * @return handler or {@code null}
     */
    NetworkHandler handler(String command) {
        return handlers.get(command);
    }

    /**
     * Resolves a binary handler for a command.
     *
     * @param command command name
     * @return binary handler or {@code null}
     */
    BinaryPacketHandler binaryHandler(String command) {
        return binaryHandlers.get(command);
    }

    /**
     * Sends a message to the server.
     *
     * @param command command name
     * @param payload payload map
     * @return {@code true} if the message was handed to the game transport
     */
    public boolean sendToServer(String command, Map<String, ?> payload) {
        return NetworkManager.sendToServer(module, command, payload);
    }

    /**
     * Sends a message to the server using alternating key/value arguments.
     *
     * @param command   command name
     * @param keyValues alternating payload keys and values
     * @return {@code true} if the message was handed to the game transport
     */
    public boolean sendToServer(String command, Object... keyValues) {
        return NetworkManager.sendToServer(module, command, keyValues);
    }

    /**
     * Sends a binary payload to the server.
     *
     * @param command command name
     * @param payload raw binary payload
     * @return {@code true} if the message was handed to the game transport; {@code false} if the payload
     * is {@code null} or exceeds {@link NetworkManager#MAX_BINARY_PAYLOAD_BYTES}
     */
    public boolean sendToServer(String command, byte[] payload) {
        return NetworkManager.sendToServer(module, command, payload);
    }

    /**
     * Sends a message to a specific connection.
     *
     * @param connection target connection
     * @param command    command name
     * @param payload    payload map
     * @return {@code true} if the message was handed to the game transport
     */
    public boolean sendToClient(UdpConnection connection, String command, Map<String, ?> payload) {
        return NetworkManager.sendToClient(connection, module, command, payload);
    }

    /**
     * Sends a message to a specific connection using alternating key/value arguments.
     *
     * @param connection target connection
     * @param command    command name
     * @param keyValues  alternating payload keys and values
     * @return {@code true} if the message was handed to the game transport
     */
    public boolean sendToClient(UdpConnection connection, String command, Object... keyValues) {
        return NetworkManager.sendToClient(connection, module, command, keyValues);
    }

    /**
     * Sends a binary payload to a specific connection.
     *
     * @param connection target connection
     * @param command    command name
     * @param payload    raw binary payload
     * @return {@code true} if the message was handed to the game transport; {@code false} if the payload
     * is {@code null} or exceeds {@link NetworkManager#MAX_BINARY_PAYLOAD_BYTES}
     */
    public boolean sendToClient(UdpConnection connection, String command, byte[] payload) {
        return NetworkManager.sendToClient(connection, module, command, payload);
    }

    /**
     * Sends a message to a specific player.
     *
     * @param player  target player
     * @param command command name
     * @param payload payload map
     * @return {@code true} if the message was handed to the game transport
     */
    public boolean sendToPlayer(IsoPlayer player, String command, Map<String, ?> payload) {
        return NetworkManager.sendToPlayer(player, module, command, payload);
    }

    /**
     * Sends a message to a specific player using alternating key/value arguments.
     *
     * @param player    target player
     * @param command   command name
     * @param keyValues alternating payload keys and values
     * @return {@code true} if the message was handed to the game transport
     */
    public boolean sendToPlayer(IsoPlayer player, String command, Object... keyValues) {
        return NetworkManager.sendToPlayer(player, module, command, keyValues);
    }

    /**
     * Sends a binary payload to a specific player.
     *
     * @param player  target player
     * @param command command name
     * @param payload raw binary payload
     * @return {@code true} if the message was handed to the game transport; {@code false} if the payload
     * is {@code null} or exceeds {@link NetworkManager#MAX_BINARY_PAYLOAD_BYTES}
     */
    public boolean sendToPlayer(IsoPlayer player, String command, byte[] payload) {
        return NetworkManager.sendToPlayer(player, module, command, payload);
    }

    /**
     * Broadcasts a message to all fully connected clients.
     *
     * @param command command name
     * @param payload payload map
     * @return {@code true} if at least one client received the message
     */
    public boolean broadcast(String command, Map<String, ?> payload) {
        return NetworkManager.broadcast(module, command, payload);
    }

    /**
     * Broadcasts a message using alternating key/value arguments.
     *
     * @param command   command name
     * @param keyValues alternating payload keys and values
     * @return {@code true} if at least one client received the message
     */
    public boolean broadcast(String command, Object... keyValues) {
        return NetworkManager.broadcast(module, command, keyValues);
    }

    /**
     * Broadcasts a binary payload to all fully connected clients.
     *
     * @param command command name
     * @param payload raw binary payload
     * @return {@code true} if at least one client received the message; {@code false} if the payload
     * is {@code null} or exceeds {@link NetworkManager#MAX_BINARY_PAYLOAD_BYTES}
     */
    public boolean broadcast(String command, byte[] payload) {
        return NetworkManager.broadcast(module, command, payload);
    }

    /**
     * Broadcasts a message to all fully connected clients except one connection.
     *
     * @param excluded excluded connection, or {@code null}
     * @param command  command name
     * @param payload  payload map
     * @return {@code true} if at least one client received the message
     */
    public boolean broadcast(UdpConnection excluded, String command, Map<String, ?> payload) {
        return NetworkManager.broadcast(excluded, module, command, payload);
    }

    /**
     * Broadcasts a binary payload to all fully connected clients except one connection.
     *
     * @param excluded excluded connection, or {@code null}
     * @param command  command name
     * @param payload  raw binary payload
     * @return {@code true} if at least one client received the message; {@code false} if the payload
     * is {@code null} or exceeds {@link NetworkManager#MAX_BINARY_PAYLOAD_BYTES}
     */
    public boolean broadcast(UdpConnection excluded, String command, byte[] payload) {
        return NetworkManager.broadcast(excluded, module, command, payload);
    }

    /**
     * Broadcasts a message to fully connected clients near a world position.
     *
     * @param command command name
     * @param x       world X coordinate
     * @param y       world Y coordinate
     * @param payload payload map
     * @return {@code true} if at least one client received the message
     */
    public boolean broadcastRelative(String command, float x, float y, Map<String, ?> payload) {
        return NetworkManager.broadcastRelative(module, command, x, y, payload);
    }

    /**
     * Broadcasts a message to fully connected clients near a world position using alternating key/value arguments.
     *
     * @param command   command name
     * @param x         world X coordinate
     * @param y         world Y coordinate
     * @param keyValues alternating payload keys and values
     * @return {@code true} if at least one client received the message
     */
    public boolean broadcastRelative(String command, float x, float y, Object... keyValues) {
        return NetworkManager.broadcastRelative(module, command, x, y, keyValues);
    }

    /**
     * Broadcasts a binary payload to fully connected clients near a world position.
     *
     * @param command command name
     * @param x       world X coordinate
     * @param y       world Y coordinate
     * @param payload raw binary payload
     * @return {@code true} if at least one client received the message; {@code false} if the payload
     * is {@code null} or exceeds {@link NetworkManager#MAX_BINARY_PAYLOAD_BYTES}
     */
    public boolean broadcastRelative(String command, float x, float y, byte[] payload) {
        return NetworkManager.broadcastRelative(module, command, x, y, payload);
    }
}
