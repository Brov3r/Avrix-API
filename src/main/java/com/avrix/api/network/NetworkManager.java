package com.avrix.api.network;

import com.avrix.api.events.DefaultEvents;
import com.avrix.api.events.EventManager;
import com.avrix.api.events.SubscribeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.krka.kahlua.vm.KahluaTable;
import zombie.characters.IsoPlayer;
import zombie.core.network.ByteBufferReader;
import zombie.core.raknet.UdpConnection;
import zombie.network.GameClient;
import zombie.network.GameServer;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Convenience service for plugin network messages and native packet helpers.
 * <p>
 * {@link NetworkChannel} messages use Project Zomboid's native {@code ClientCommand} transport.
 * Register handlers with {@link #channel(String)} and send messages through the channel. Lower-level
 * packet helpers are available through {@link NetworkPackets}.
 * <p>
 * Binary payloads use the same command transport. They are wrapped into a Base64 envelope, so a single
 * binary payload is limited to {@link #MAX_BINARY_PAYLOAD_BYTES} raw bytes.
 */
public final class NetworkManager {

    /**
     * Maximum raw size of a single binary payload.
     */
    public static final int MAX_BINARY_PAYLOAD_BYTES = BinaryPacketCodec.MAX_PAYLOAD_BYTES;

    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkManager.class);
    private static final int MAX_MODULE_LENGTH = 64;
    private static final int MAX_COMMAND_LENGTH = 128;
    private static final Map<String, NetworkChannel> CHANNELS = new ConcurrentHashMap<>();

    private static final Object EVENT_LISTENER = new Object() {
        @SubscribeEvent(DefaultEvents.ON_CLIENT_COMMAND)
        public void onClientCommand(String module, String command, IsoPlayer player, KahluaTable args) {
            UdpConnection connection = null;
            if (GameServer.server && GameServer.udpEngine != null && player != null) {
                connection = GameServer.getConnectionFromPlayer(player);
            }
            dispatchIncoming(module, command, new NetworkContext(NetworkSide.SERVER, player, connection), args);
        }

        @SubscribeEvent(DefaultEvents.ON_SERVER_COMMAND)
        public void onServerCommand(String module, String command, KahluaTable args) {
            IsoPlayer player = null;
            try {
                if (GameClient.client) {
                    player = IsoPlayer.getInstance();
                }
            } catch (Throwable ignored) {
                // The local player is unavailable outside of an active game session.
            }
            dispatchIncoming(module, command,
                    new NetworkContext(NetworkSide.CLIENT, player, GameClient.connection), args);
        }
    };

    static {
        try {
            EventManager.register(EVENT_LISTENER);
        } catch (Throwable t) {
            LOGGER.warn("Failed to register the Avrix network command listener", t);
        }
    }

    private NetworkManager() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Returns the channel for a module, creating it when absent.
     *
     * @param module module name, recommended to use the plugin identifier
     * @return network channel
     * @throws IllegalArgumentException if the module name is invalid
     */
    public static NetworkChannel channel(String module) {
        String cleanModule = validateModule(module);
        return CHANNELS.computeIfAbsent(cleanModule, NetworkChannel::new);
    }

    /**
     * Removes a channel and all of its handlers.
     *
     * @param module module name
     * @throws IllegalArgumentException if the module name is invalid
     */
    public static void unregister(String module) {
        CHANNELS.remove(validateModule(module));
    }

    /**
     * Removes all registered channels.
     */
    public static void unregisterAll() {
        CHANNELS.clear();
    }

    /**
     * Dispatches a received command to the registered channel handler.
     * <p>
     * This method is used by the internal event bridge and is also available for integration tests.
     *
     * @param module  module name
     * @param command command name
     * @param context receiving context
     * @param args    raw Kahlua arguments
     */
    public static void dispatchIncoming(String module, String command, NetworkContext context, KahluaTable args) {
        if (module == null || module.isBlank() || command == null || command.isBlank() || context == null) {
            return;
        }

        NetworkChannel channel = CHANNELS.get(module.strip());
        if (channel == null) {
            return;
        }

        String cleanCommand = command.strip();
        byte[] binaryPayload;
        try {
            binaryPayload = BinaryPacketCodec.decode(args);
        } catch (IllegalArgumentException ex) {
            LOGGER.error("Rejected binary network message '{}:{}'", module.strip(), cleanCommand, ex);
            return;
        }

        if (binaryPayload != null) {
            BinaryPacketHandler binaryHandler = channel.binaryHandler(cleanCommand);
            if (binaryHandler == null) {
                return;
            }
            try {
                binaryHandler.handle(context, new ByteBufferReader(ByteBuffer.wrap(binaryPayload)));
            } catch (Throwable t) {
                LOGGER.error("Binary network handler for '{}:{}' failed", module.strip(), cleanCommand, t);
            }
            return;
        }

        NetworkHandler handler = channel.handler(cleanCommand);
        if (handler == null) {
            return;
        }

        try {
            handler.handle(context, NetworkPayload.fromTable(args));
        } catch (Throwable t) {
            LOGGER.error("Network handler for '{}:{}' failed", module.strip(), cleanCommand, t);
        }
    }

    /**
     * Sends a message to the server.
     *
     * @param module  module name
     * @param command command name
     * @param payload payload map
     * @return {@code true} if the message was handed to the game transport
     */
    public static boolean sendToServer(String module, String command, Map<String, ?> payload) {
        String cleanModule = validateModule(module);
        String cleanCommand = validateCommand(command);
        KahluaTable table;
        try {
            table = NetworkPayload.toTable(payload);
        } catch (IllegalArgumentException ex) {
            LOGGER.error("Failed to build network payload for '{}:{}'", cleanModule, cleanCommand, ex);
            return false;
        }
        return sendToServerTable(cleanModule, cleanCommand, table);
    }

    /**
     * Sends a binary payload to the server.
     *
     * @param module  module name
     * @param command command name
     * @param payload raw binary payload
     * @return {@code true} if the message was handed to the game transport; {@code false} if the payload
     * is {@code null} or exceeds {@link #MAX_BINARY_PAYLOAD_BYTES}
     */
    public static boolean sendToServer(String module, String command, byte[] payload) {
        String cleanModule = validateModule(module);
        String cleanCommand = validateCommand(command);
        KahluaTable table;
        try {
            table = BinaryPacketCodec.encode(payload);
        } catch (IllegalArgumentException ex) {
            LOGGER.error("Failed to build binary payload for '{}:{}'", cleanModule, cleanCommand, ex);
            return false;
        }
        return sendToServerTable(cleanModule, cleanCommand, table);
    }

    /**
     * Hands a prepared table to the client transport.
     *
     * @param cleanModule  validated module name
     * @param cleanCommand validated command name
     * @param table        prepared payload table
     * @return {@code true} if the message was handed to the game transport
     */
    private static boolean sendToServerTable(String cleanModule, String cleanCommand, KahluaTable table) {
        if (!GameClient.client || GameClient.connection == null || GameClient.instance == null) {
            return false;
        }
        IsoPlayer player = localPlayer();
        if (player == null) {
            return false;
        }
        try {
            GameClient.instance.sendClientCommand(player, cleanModule, cleanCommand, table);
            return true;
        } catch (Exception ex) {
            LOGGER.error("Failed to send network message '{}:{}' to the server", cleanModule, cleanCommand, ex);
            return false;
        }
    }

    /**
     * Sends a message to the server using alternating key/value arguments.
     *
     * @param module    module name
     * @param command   command name
     * @param keyValues alternating payload keys and values
     * @return {@code true} if the message was handed to the game transport
     */
    public static boolean sendToServer(String module, String command, Object... keyValues) {
        try {
            return sendToServer(module, command, NetworkPayload.toMap(keyValues));
        } catch (IllegalArgumentException ex) {
            LOGGER.error("Failed to build network payload for '{}:{}'", module, command, ex);
            return false;
        }
    }

    /**
     * Sends a message to a specific connection.
     *
     * @param connection target connection
     * @param module     module name
     * @param command    command name
     * @param payload    payload map
     * @return {@code true} if the message was handed to the game transport
     */
    public static boolean sendToClient(
            UdpConnection connection,
            String module,
            String command,
            Map<String, ?> payload
    ) {
        String cleanModule = validateModule(module);
        String cleanCommand = validateCommand(command);
        KahluaTable table;
        try {
            table = NetworkPayload.toTable(payload);
        } catch (IllegalArgumentException ex) {
            LOGGER.error("Failed to build network payload for '{}:{}'", cleanModule, cleanCommand, ex);
            return false;
        }
        return sendToClientTable(connection, cleanModule, cleanCommand, table);
    }

    /**
     * Sends a binary payload to a specific connection.
     *
     * @param connection target connection
     * @param module     module name
     * @param command    command name
     * @param payload    raw binary payload
     * @return {@code true} if the message was handed to the game transport; {@code false} if the payload
     * is {@code null} or exceeds {@link #MAX_BINARY_PAYLOAD_BYTES}
     */
    public static boolean sendToClient(
            UdpConnection connection,
            String module,
            String command,
            byte[] payload
    ) {
        String cleanModule = validateModule(module);
        String cleanCommand = validateCommand(command);
        KahluaTable table;
        try {
            table = BinaryPacketCodec.encode(payload);
        } catch (IllegalArgumentException ex) {
            LOGGER.error("Failed to build binary payload for '{}:{}'", cleanModule, cleanCommand, ex);
            return false;
        }
        return sendToClientTable(connection, cleanModule, cleanCommand, table);
    }

    /**
     * Hands a prepared table to a specific connection.
     *
     * @param connection   target connection
     * @param cleanModule  validated module name
     * @param cleanCommand validated command name
     * @param table        prepared payload table
     * @return {@code true} if the message was handed to the game transport
     */
    private static boolean sendToClientTable(
            UdpConnection connection,
            String cleanModule,
            String cleanCommand,
            KahluaTable table
    ) {
        if (!GameServer.server || connection == null) {
            return false;
        }
        try {
            GameServer.sendServerCommand(cleanModule, cleanCommand, table, connection);
            return true;
        } catch (Exception ex) {
            LOGGER.error("Failed to send network message '{}:{}' to a client", cleanModule, cleanCommand, ex);
            return false;
        }
    }

    /**
     * Sends a message to a specific connection using alternating key/value arguments.
     *
     * @param connection target connection
     * @param module     module name
     * @param command    command name
     * @param keyValues  alternating payload keys and values
     * @return {@code true} if the message was handed to the game transport
     */
    public static boolean sendToClient(
            UdpConnection connection,
            String module,
            String command,
            Object... keyValues
    ) {
        try {
            return sendToClient(connection, module, command, NetworkPayload.toMap(keyValues));
        } catch (IllegalArgumentException ex) {
            LOGGER.error("Failed to build network payload for '{}:{}'", module, command, ex);
            return false;
        }
    }

    /**
     * Sends a message to a specific player.
     *
     * @param player  target player
     * @param module  module name
     * @param command command name
     * @param payload payload map
     * @return {@code true} if the message was handed to the game transport
     */
    public static boolean sendToPlayer(IsoPlayer player, String module, String command, Map<String, ?> payload) {
        String cleanModule = validateModule(module);
        String cleanCommand = validateCommand(command);
        if (!GameServer.server || player == null || GameServer.udpEngine == null) {
            return false;
        }
        UdpConnection connection = GameServer.getConnectionFromPlayer(player);
        return sendToClient(connection, cleanModule, cleanCommand, payload);
    }

    /**
     * Sends a binary payload to a specific player.
     *
     * @param player  target player
     * @param module  module name
     * @param command command name
     * @param payload raw binary payload
     * @return {@code true} if the message was handed to the game transport; {@code false} if the payload
     * is {@code null} or exceeds {@link #MAX_BINARY_PAYLOAD_BYTES}
     */
    public static boolean sendToPlayer(IsoPlayer player, String module, String command, byte[] payload) {
        String cleanModule = validateModule(module);
        String cleanCommand = validateCommand(command);
        if (!GameServer.server || player == null || GameServer.udpEngine == null) {
            return false;
        }
        UdpConnection connection = GameServer.getConnectionFromPlayer(player);
        return sendToClient(connection, cleanModule, cleanCommand, payload);
    }

    /**
     * Sends a message to a specific player using alternating key/value arguments.
     *
     * @param player    target player
     * @param module    module name
     * @param command   command name
     * @param keyValues alternating payload keys and values
     * @return {@code true} if the message was handed to the game transport
     */
    public static boolean sendToPlayer(IsoPlayer player, String module, String command, Object... keyValues) {
        try {
            return sendToPlayer(player, module, command, NetworkPayload.toMap(keyValues));
        } catch (IllegalArgumentException ex) {
            LOGGER.error("Failed to build network payload for '{}:{}'", module, command, ex);
            return false;
        }
    }

    /**
     * Broadcasts a message to all fully connected clients.
     *
     * @param module  module name
     * @param command command name
     * @param payload payload map
     * @return {@code true} if at least one client received the message
     */
    public static boolean broadcast(String module, String command, Map<String, ?> payload) {
        return broadcast(null, module, command, payload);
    }

    /**
     * Broadcasts a binary payload to all fully connected clients.
     *
     * @param module  module name
     * @param command command name
     * @param payload raw binary payload
     * @return {@code true} if at least one client received the message; {@code false} if the payload
     * is {@code null} or exceeds {@link #MAX_BINARY_PAYLOAD_BYTES}
     */
    public static boolean broadcast(String module, String command, byte[] payload) {
        return broadcast(null, module, command, payload);
    }

    /**
     * Broadcasts a message using alternating key/value arguments.
     *
     * @param module    module name
     * @param command   command name
     * @param keyValues alternating payload keys and values
     * @return {@code true} if at least one client received the message
     */
    public static boolean broadcast(String module, String command, Object... keyValues) {
        try {
            return broadcast(module, command, NetworkPayload.toMap(keyValues));
        } catch (IllegalArgumentException ex) {
            LOGGER.error("Failed to build network payload for '{}:{}'", module, command, ex);
            return false;
        }
    }

    /**
     * Broadcasts a message to all fully connected clients except one connection.
     *
     * @param excluded excluded connection, or {@code null}
     * @param module   module name
     * @param command  command name
     * @param payload  payload map
     * @return {@code true} if at least one client received the message
     */
    public static boolean broadcast(
            UdpConnection excluded,
            String module,
            String command,
            Map<String, ?> payload
    ) {
        String cleanModule = validateModule(module);
        String cleanCommand = validateCommand(command);
        KahluaTable table;
        try {
            table = NetworkPayload.toTable(payload);
        } catch (IllegalArgumentException ex) {
            LOGGER.error("Failed to build network payload for '{}:{}'", cleanModule, cleanCommand, ex);
            return false;
        }
        return broadcastTable(excluded, cleanModule, cleanCommand, table);
    }

    /**
     * Broadcasts a binary payload to all fully connected clients except one connection.
     *
     * @param excluded excluded connection, or {@code null}
     * @param module   module name
     * @param command  command name
     * @param payload  raw binary payload
     * @return {@code true} if at least one client received the message; {@code false} if the payload
     * is {@code null} or exceeds {@link #MAX_BINARY_PAYLOAD_BYTES}
     */
    public static boolean broadcast(
            UdpConnection excluded,
            String module,
            String command,
            byte[] payload
    ) {
        String cleanModule = validateModule(module);
        String cleanCommand = validateCommand(command);
        KahluaTable table;
        try {
            table = BinaryPacketCodec.encode(payload);
        } catch (IllegalArgumentException ex) {
            LOGGER.error("Failed to build binary payload for '{}:{}'", cleanModule, cleanCommand, ex);
            return false;
        }
        return broadcastTable(excluded, cleanModule, cleanCommand, table);
    }

    /**
     * Hands a prepared table to eligible server connections.
     *
     * @param excluded     excluded connection, or {@code null}
     * @param cleanModule  validated module name
     * @param cleanCommand validated command name
     * @param table        prepared payload table
     * @return {@code true} if at least one client received the message
     */
    private static boolean broadcastTable(
            UdpConnection excluded,
            String cleanModule,
            String cleanCommand,
            KahluaTable table
    ) {
        if (!GameServer.server || GameServer.udpEngine == null || GameServer.udpEngine.connections == null) {
            return false;
        }

        try {
            boolean sent = false;
            for (UdpConnection connection : GameServer.udpEngine.connections) {
                if (!isEligible(connection, excluded)) {
                    continue;
                }
                GameServer.sendServerCommand(cleanModule, cleanCommand, table, connection);
                sent = true;
            }
            return sent;
        } catch (Exception ex) {
            LOGGER.error("Failed to broadcast network message '{}:{}'", cleanModule, cleanCommand, ex);
            return false;
        }
    }

    /**
     * Broadcasts a message to fully connected clients near a world position.
     *
     * @param module  module name
     * @param command command name
     * @param x       world X coordinate
     * @param y       world Y coordinate
     * @param payload payload map
     * @return {@code true} if at least one client received the message
     */
    public static boolean broadcastRelative(
            String module,
            String command,
            float x,
            float y,
            Map<String, ?> payload
    ) {
        String cleanModule = validateModule(module);
        String cleanCommand = validateCommand(command);
        KahluaTable table;
        try {
            table = NetworkPayload.toTable(payload);
        } catch (IllegalArgumentException ex) {
            LOGGER.error("Failed to build network payload for '{}:{}'", cleanModule, cleanCommand, ex);
            return false;
        }
        return broadcastRelativeTable(cleanModule, cleanCommand, x, y, table);
    }

    /**
     * Broadcasts a binary payload to fully connected clients near a world position.
     *
     * @param module  module name
     * @param command command name
     * @param x       world X coordinate
     * @param y       world Y coordinate
     * @param payload raw binary payload
     * @return {@code true} if at least one client received the message; {@code false} if the payload
     * is {@code null} or exceeds {@link #MAX_BINARY_PAYLOAD_BYTES}
     */
    public static boolean broadcastRelative(
            String module,
            String command,
            float x,
            float y,
            byte[] payload
    ) {
        String cleanModule = validateModule(module);
        String cleanCommand = validateCommand(command);
        KahluaTable table;
        try {
            table = BinaryPacketCodec.encode(payload);
        } catch (IllegalArgumentException ex) {
            LOGGER.error("Failed to build binary payload for '{}:{}'", cleanModule, cleanCommand, ex);
            return false;
        }
        return broadcastRelativeTable(cleanModule, cleanCommand, x, y, table);
    }

    /**
     * Hands a prepared table to clients near a world position.
     *
     * @param cleanModule  validated module name
     * @param cleanCommand validated command name
     * @param x            world X coordinate
     * @param y            world Y coordinate
     * @param table        prepared payload table
     * @return {@code true} if at least one client received the message
     */
    private static boolean broadcastRelativeTable(
            String cleanModule,
            String cleanCommand,
            float x,
            float y,
            KahluaTable table
    ) {
        if (!GameServer.server || GameServer.udpEngine == null || GameServer.udpEngine.connections == null) {
            return false;
        }

        try {
            boolean sent = false;
            for (UdpConnection connection : GameServer.udpEngine.connections) {
                if (connection == null || !connection.isFullyConnected() || !connection.isRelevantTo(x, y)) {
                    continue;
                }
                GameServer.sendServerCommand(cleanModule, cleanCommand, table, connection);
                sent = true;
            }
            return sent;
        } catch (Exception ex) {
            LOGGER.error("Failed to broadcast relative network message '{}:{}'", cleanModule, cleanCommand, ex);
            return false;
        }
    }

    /**
     * Broadcasts a message to fully connected clients near a world position using alternating key/value arguments.
     *
     * @param module    module name
     * @param command   command name
     * @param x         world X coordinate
     * @param y         world Y coordinate
     * @param keyValues alternating payload keys and values
     * @return {@code true} if at least one client received the message
     */
    public static boolean broadcastRelative(
            String module,
            String command,
            float x,
            float y,
            Object... keyValues
    ) {
        try {
            return broadcastRelative(module, command, x, y, NetworkPayload.toMap(keyValues));
        } catch (IllegalArgumentException ex) {
            LOGGER.error("Failed to build network payload for '{}:{}'", module, command, ex);
            return false;
        }
    }

    /**
     * Resolves the local client player.
     *
     * @return local player or {@code null}
     */
    private static IsoPlayer localPlayer() {
        try {
            return IsoPlayer.getInstance();
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Checks whether a connection should receive a broadcast.
     *
     * @param connection target connection
     * @param excluded   excluded connection
     * @return {@code true} if the connection is eligible
     */
    private static boolean isEligible(UdpConnection connection, UdpConnection excluded) {
        if (connection == null || !connection.isFullyConnected()) {
            return false;
        }
        return excluded == null
                || connection == excluded
                || connection.getConnectedGUID() != excluded.getConnectedGUID();
    }

    /**
     * Validates and normalizes a module name.
     *
     * @param module raw module name
     * @return normalized module name
     */
    static String validateModule(String module) {
        String clean = Objects.requireNonNull(module, "Network module cannot be null").strip();
        if (clean.isEmpty()) {
            throw new IllegalArgumentException("Network module cannot be blank");
        }
        if (clean.length() > MAX_MODULE_LENGTH) {
            throw new IllegalArgumentException("Network module is too long: " + clean.length());
        }
        return clean;
    }

    /**
     * Validates and normalizes a command name.
     *
     * @param command raw command name
     * @return normalized command name
     */
    static String validateCommand(String command) {
        String clean = Objects.requireNonNull(command, "Network command cannot be null").strip();
        if (clean.isEmpty()) {
            throw new IllegalArgumentException("Network command cannot be blank");
        }
        if (clean.length() > MAX_COMMAND_LENGTH) {
            throw new IllegalArgumentException("Network command is too long: " + clean.length());
        }
        return clean;
    }
}
