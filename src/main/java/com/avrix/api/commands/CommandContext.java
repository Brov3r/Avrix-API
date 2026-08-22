package com.avrix.api.commands;

import zombie.characters.IsoPlayer;
import zombie.core.raknet.UdpConnection;

import java.util.Arrays;
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
     * Retrieves the issuing player entity wrapped in an {@link Optional}.
     *
     * @return optional player
     */
    public Optional<IsoPlayer> getPlayer() {
        return Optional.ofNullable(player);
    }

    /**
     * Total number of passed arguments.
     *
     * @return argument count
     */
    public int length() {
        return args.length;
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

    /**
     * Retrieves an argument by index with fallback default value.
     *
     * @param index        argument index
     * @param defaultValue default value if index is out of bounds
     * @return argument string or default value
     */
    public String getString(int index, String defaultValue) {
        return getArg(index).orElse(defaultValue);
    }

    /**
     * Safely parses an integer argument at index.
     *
     * @param index argument index
     * @return optional parsed integer, or empty if out of bounds or invalid format
     */
    public Optional<Integer> getInt(int index) {
        return getArg(index).flatMap(val -> {
            try {
                return Optional.of(Integer.parseInt(val));
            } catch (NumberFormatException _) {
                return Optional.empty();
            }
        });
    }

    /**
     * Safely parses an integer argument at index with fallback default.
     *
     * @param index        argument index
     * @param defaultValue fallback integer
     * @return parsed integer or default value
     */
    public int getInt(int index, int defaultValue) {
        return getInt(index).orElse(defaultValue);
    }

    /**
     * Safely parses a double argument at index.
     *
     * @param index argument index
     * @return optional parsed double, or empty if invalid
     */
    public Optional<Double> getDouble(int index) {
        return getArg(index).flatMap(val -> {
            try {
                return Optional.of(Double.parseDouble(val));
            } catch (NumberFormatException _) {
                return Optional.empty();
            }
        });
    }

    /**
     * Safely parses a double argument at index with fallback default.
     *
     * @param index        argument index
     * @param defaultValue fallback double
     * @return parsed double or default value
     */
    public double getDouble(int index, double defaultValue) {
        return getDouble(index).orElse(defaultValue);
    }

    /**
     * Safely parses a boolean argument at index.
     * Accepts: {@code true/false}, {@code 1/0}, {@code yes/no}.
     *
     * @param index argument index
     * @return optional parsed boolean, or empty if invalid
     */
    public Optional<Boolean> getBoolean(int index) {
        return getArg(index).flatMap(val -> {
            if ("true".equalsIgnoreCase(val) || "1".equals(val) || "yes".equalsIgnoreCase(val)) {
                return Optional.of(true);
            }
            if ("false".equalsIgnoreCase(val) || "0".equals(val) || "no".equalsIgnoreCase(val)) {
                return Optional.of(false);
            }
            return Optional.empty();
        });
    }

    /**
     * Safely parses a boolean argument at index with fallback default.
     *
     * @param index        argument index
     * @param defaultValue fallback boolean
     * @return parsed boolean or default value
     */
    public boolean getBoolean(int index, boolean defaultValue) {
        return getBoolean(index).orElse(defaultValue);
    }

    /**
     * Combines all remaining arguments starting from {@code startIndex} into a single space-separated string.
     *
     * @param startIndex start index (inclusive)
     * @return joined string or empty string
     */
    public String joinArgs(int startIndex) {
        if (startIndex < 0 || startIndex >= args.length) {
            return "";
        }
        return String.join(" ", Arrays.copyOfRange(args, startIndex, args.length));
    }

    /**
     * Creates a child sub-context with shifted argument array (useful for subcommands delegation).
     *
     * @param shift amount of arguments to skip from the beginning
     * @return new child CommandContext
     */
    public CommandContext subContext(int shift) {
        if (shift <= 0) {
            return this;
        }
        if (shift >= args.length) {
            return new CommandContext(senderName, player, connection, rawCommand, new String[0]);
        }
        String[] shiftedArgs = Arrays.copyOfRange(args, shift, args.length);
        return new CommandContext(senderName, player, connection, rawCommand, shiftedArgs);
    }
}