package com.avrix.api.commands;

import zombie.characters.IsoPlayer;
import zombie.core.raknet.UdpConnection;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable context provided to a command during execution.
 *
 * @param senderName the name of the sender (player username or "Server")
 * @param player     the player instance (or {@code null} if executed from server console/RCON)
 * @param connection the UDP network connection (or {@code null} if console)
 * @param rawCommand the raw command input string
 * @param args       parsed positional arguments (quotes stripped)
 */
public record CommandContext(
        String senderName,
        IsoPlayer player,
        UdpConnection connection,
        String rawCommand,
        String[] args
) {

    public CommandContext {
        Objects.requireNonNull(senderName, "Sender name cannot be null");
        Objects.requireNonNull(rawCommand, "Raw command cannot be null");
        args = (args != null) ? args : new String[0];
    }

    /**
     * Checks if this command was executed by an in-game player.
     *
     * @return {@code true} if sent by an active player
     */
    public boolean isPlayer() {
        return player != null;
    }

    /**
     * Retrieves the player entity wrapped in an {@link Optional}.
     *
     * @return optional player
     */
    public Optional<IsoPlayer> getPlayer() {
        return Optional.ofNullable(player);
    }

    /**
     * Retrieves an argument by index, or empty if out of bounds.
     *
     * @param index argument index
     * @return optional argument string
     */
    public Optional<String> getArg(int index) {
        if (index >= 0 && index < args.length) {
            return Optional.of(args[index]);
        }
        return Optional.empty();
    }
}