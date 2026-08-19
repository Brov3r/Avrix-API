package com.avrix.api.commands;

import com.avrix.api.permissions.PermissionsManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zombie.characters.Capability;
import zombie.characters.IsoPlayer;
import zombie.core.raknet.UdpConnection;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central thread-safe command manager for registering, parsing, authenticating, and dispatching commands.
 */
public final class CommandManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommandManager.class);

    private static final Map<String, CommandRegistration> COMMANDS = new ConcurrentHashMap<>();

    private CommandManager() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Registers a command instance.
     *
     * @param command the command implementation
     * @throws IllegalArgumentException if missing {@link CommandInfo} or command name is blank
     */
    public static void register(Command command) {
        Objects.requireNonNull(command, "Command instance cannot be null");

        Class<?> clazz = command.getClass();
        CommandInfo info = clazz.getAnnotation(CommandInfo.class);
        if (info == null) {
            throw new IllegalArgumentException("Class '%s' is missing @CommandInfo annotation".formatted(clazz.getName()));
        }

        String primaryName = info.name().strip().toLowerCase(Locale.ROOT);
        if (primaryName.isEmpty()) {
            throw new IllegalArgumentException("Command name cannot be empty in class: " + clazz.getName());
        }

        CommandRegistration registration = new CommandRegistration(command, info);
        COMMANDS.put(primaryName, registration);

        for (String alias : info.aliases()) {
            if (alias != null && !alias.isBlank()) {
                COMMANDS.put(alias.strip().toLowerCase(Locale.ROOT), registration);
            }
        }

        LOGGER.info("Registered command '/{}' (scope: {}, aliases: {})",
                primaryName, info.scope(), Arrays.toString(info.aliases()));
    }

    /**
     * Unregisters a command by name.
     *
     * @param commandName the command name
     */
    public static void unregister(String commandName) {
        if (commandName == null) return;
        CommandRegistration removed = COMMANDS.remove(commandName.strip().toLowerCase(Locale.ROOT));
        if (removed != null) {
            for (String alias : removed.info.aliases()) {
                COMMANDS.remove(alias.strip().toLowerCase(Locale.ROOT));
            }
        }
    }

    /**
     * Dispatches a raw command input string.
     *
     * @param rawInput   the command line input (e.g. {@code /heal "Miss Bekket"})
     * @param connection the player connection, or null if server console
     * @return the execution response string if intercepted, or {@code null} to pass through to vanilla Project Zomboid
     */
    public static String handleCommand(String rawInput, UdpConnection connection) {
        if (rawInput == null || rawInput.isBlank()) {
            return null;
        }

        List<String> tokens = CommandArgumentParser.parseTokens(rawInput);
        if (tokens.isEmpty()) {
            return null;
        }

        String trigger = tokens.getFirst().toLowerCase(Locale.ROOT);
        CommandRegistration reg = COMMANDS.get(trigger);
        if (reg == null) {
            return null; // Not an Avrix command -> pass through to vanilla
        }

        // Split args (all tokens after trigger)
        String[] args = tokens.size() > 1
                ? tokens.subList(1, tokens.size()).toArray(String[]::new)
                : new String[0];

        // Resolve sender player
        IsoPlayer player = null;
        String senderName = "ServerConsole";

        if (connection != null) {
            senderName = connection.getUserName();
            if (connection.players != null) {
                for (IsoPlayer p : connection.players) {
                    if (p != null) {
                        player = p;
                        break;
                    }
                }
            }
        }

        CommandContext context = new CommandContext(senderName, player, connection, rawInput, args);

        // Validate CommandScope
        if (!reg.info.scope().allows(context.isPlayer())) {
            return switch (reg.info.scope()) {
                case CHAT -> "This command can only be executed in-game by a player.";
                case CONSOLE -> "This command can only be executed from the server console.";
                case BOTH -> "";
            };
        }

        // Authorize permissions
        if (!isAuthorized(context, reg.info)) {
            return "You do not have permission to execute this command.";
        }

        // Execute command safely
        try {
            String result = reg.command.execute(context);
            return result != null ? result : "Command executed successfully.";
        } catch (Exception ex) {
            LOGGER.error("Error while executing command '/{}'", trigger, ex);
            return "An internal error occurred: " + ex.getMessage();
        }
    }

    /**
     * Evaluates whether the issuer has at least one of the required permissions or capabilities.
     */
    private static boolean isAuthorized(CommandContext ctx, CommandInfo info) {
        // Server console always has root access
        if (!ctx.isPlayer()) {
            return true;
        }

        IsoPlayer player = ctx.player();

        // Custom string permission check
        if (info.permission().length > 0) {
            for (String permNode : info.permission()) {
                if (permNode != null && !permNode.isBlank()) {
                    if (PermissionsManager.hasPermission(player, permNode)) {
                        return true;
                    }
                }
            }
            return false;
        }

        // Standard Capability check
        if (info.capability().length > 0) {
            for (Capability cap : info.capability()) {
                if (cap != null && cap != Capability.None) {
                    if (PermissionsManager.hasCapability(player, cap)) {
                        return true;
                    }
                }
            }
            return false;
        }

        return true; // No permission required
    }

    private record CommandRegistration(Command command, CommandInfo info) {
    }
}