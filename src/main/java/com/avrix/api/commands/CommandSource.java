package com.avrix.api.commands;

import zombie.characters.IsoPlayer;
import zombie.core.raknet.UdpConnection;

import java.util.Locale;
import java.util.Objects;

/**
 * Immutable description of the origin of a command invocation.
 * <p>
 * Server connections, the server console, RCON and client-side commands are
 * represented explicitly so command scopes and diagnostics do not have to infer
 * the source from a nullable player object.
 *
 * @param type        source type
 * @param senderName  display name of the sender
 * @param player      issuing player, or {@code null} for console/RCON
 * @param connection  server connection, or {@code null} for console/RCON/client
 * @param address     sender address or a symbolic address such as {@code "Console"}
 */
public record CommandSource(
        Type type,
        String senderName,
        IsoPlayer player,
        UdpConnection connection,
        String address
) {

    /**
     * Supported command source types.
     */
    public enum Type {
        /**
         * In-game player on a dedicated or hosted server.
         */
        PLAYER,

        /**
         * Local client-side player command.
         */
        CLIENT,

        /**
         * Server console or terminal.
         */
        CONSOLE,

        /**
         * Remote console (RCON).
         */
        RCON
    }

    /**
     * Compact constructor validating required source fields.
     *
     * @throws NullPointerException if {@code type}, {@code senderName} or {@code address} is null
     */
    public CommandSource {
        Objects.requireNonNull(type, "Command source type cannot be null");
        Objects.requireNonNull(senderName, "Command sender name cannot be null");
        Objects.requireNonNull(address, "Command source address cannot be null");
    }

    /**
     * Creates a server-console source.
     *
     * @return console command source
     */
    public static CommandSource serverConsole() {
        return new CommandSource(Type.CONSOLE, "ServerConsole", null, null, "Console");
    }

    /**
     * Creates an RCON source.
     *
     * @return RCON command source
     */
    public static CommandSource rcon() {
        return new CommandSource(Type.RCON, "RCON", null, null, "RCON");
    }

    /**
     * Creates a client-side player source.
     *
     * @param player local player
     * @return client command source
     * @throws NullPointerException if {@code player} is null
     */
    public static CommandSource client(IsoPlayer player) {
        Objects.requireNonNull(player, "Player cannot be null");
        return new CommandSource(Type.CLIENT, resolvePlayerName(player), player, null, "Client");
    }

    /**
     * Creates a server player source with an explicitly selected player.
     *
     * @param connection server connection
     * @param player     issuing player
     * @return player command source
     * @throws NullPointerException if {@code connection} or {@code player} is null
     */
    public static CommandSource player(UdpConnection connection, IsoPlayer player) {
        Objects.requireNonNull(connection, "Connection cannot be null");
        Objects.requireNonNull(player, "Player cannot be null");
        return new CommandSource(Type.PLAYER, resolvePlayerName(player), player, connection, resolveAddress(connection));
    }

    /**
     * Infers a source from a server connection.
     * <p>
     * A connection without an active player is treated as a console-origin source.
     *
     * @param connection server connection, or {@code null} for the server console
     * @return inferred command source
     */
    public static CommandSource of(UdpConnection connection) {
        if (connection == null) {
            return serverConsole();
        }

        IsoPlayer player = firstPlayer(connection);
        String name = resolveConnectionName(connection, player);
        String address = resolveAddress(connection);
        Type type = player != null ? Type.PLAYER : Type.CONSOLE;
        return new CommandSource(type, name, player, connection, address);
    }

    /**
     * Creates a source for legacy code that only has a player and connection.
     *
     * @param player     player, or {@code null}
     * @param connection connection, or {@code null}
     * @return inferred command source
     */
    public static CommandSource fromLegacy(IsoPlayer player, UdpConnection connection) {
        if (player != null) {
            String name = resolvePlayerName(player);
            String address = connection != null ? resolveAddress(connection) : "Client";
            Type type = connection != null ? Type.PLAYER : Type.CLIENT;
            return new CommandSource(type, name, player, connection, address);
        }
        return connection != null ? of(connection) : serverConsole();
    }

    /**
     * Checks whether this source represents an in-game player.
     *
     * @return {@code true} for {@link Type#PLAYER} and {@link Type#CLIENT}
     */
    public boolean isPlayer() {
        return type == Type.PLAYER || type == Type.CLIENT;
    }

    /**
     * Checks whether this source represents a console or RCON.
     *
     * @return {@code true} for {@link Type#CONSOLE} and {@link Type#RCON}
     */
    public boolean isConsole() {
        return type == Type.CONSOLE || type == Type.RCON;
    }

    /**
     * Returns a stable identity key used for cooldown tracking.
     *
     * @return lowercase player name for player sources, otherwise a source-type key
     */
    public String identityKey() {
        if (isPlayer() && senderName != null && !senderName.isBlank()) {
            return senderName.strip().toLowerCase(Locale.ROOT);
        }
        return type.name().toLowerCase(Locale.ROOT);
    }

    private static IsoPlayer firstPlayer(UdpConnection connection) {
        if (connection.players == null) {
            return null;
        }
        for (IsoPlayer player : connection.players) {
            if (player != null) {
                return player;
            }
        }
        return null;
    }

    private static String resolvePlayerName(IsoPlayer player) {
        String username = player.getUsername();
        return username != null && !username.isBlank() ? username.strip() : "UnknownPlayer";
    }

    private static String resolveConnectionName(UdpConnection connection, IsoPlayer player) {
        String username = connection.getUserName();
        if (username != null && !username.isBlank()) {
            return username.strip();
        }
        return player != null ? resolvePlayerName(player) : "UnknownPlayer";
    }

    private static String resolveAddress(UdpConnection connection) {
        String ip = connection.getIP();
        return ip != null && !ip.isBlank() ? ip : "Unknown";
    }
}
