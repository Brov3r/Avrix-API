package com.avrix.api.commands;

import com.avrix.api.events.EventManager;
import com.avrix.api.events.ServerEvents;
import com.avrix.api.permissions.PermissionsManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zombie.characters.Capability;
import zombie.characters.IsoPlayer;
import zombie.core.raknet.UdpConnection;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Thread-safe command management and dispatching engine.
 */
public final class CommandManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommandManager.class);
    private static final Map<String, CommandRegistration> COMMAND_REGISTRY = new ConcurrentHashMap<>();
    private static final Map<String, Long> COOLDOWNS = new ConcurrentHashMap<>();

    private CommandManager() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Registers a command into the dispatch registry.
     *
     * @param command the command instance to register
     * @throws NullPointerException     if {@code command} is null
     * @throws IllegalArgumentException if {@link CommandInfo} is missing or command name is blank
     * @apiNote Replaces existing mapping if collision occurs and logs a warning.
     */
    public static void register(Command command) {
        Objects.requireNonNull(command, "Command instance cannot be null");

        Class<?> clazz = command.getClass();
        CommandInfo info = clazz.getAnnotation(CommandInfo.class);
        if (info == null) {
            throw new IllegalArgumentException("Class '%s' is missing @CommandInfo annotation".formatted(clazz.getName()));
        }

        String primaryName = normalize(info.name());
        if (primaryName.isEmpty()) {
            throw new IllegalArgumentException("Primary command name cannot be blank in class: " + clazz.getName());
        }

        CommandRegistration registration = new CommandRegistration(command, info, primaryName);
        COMMAND_REGISTRY.put(primaryName, registration);

        for (String alias : info.aliases()) {
            String normalizedAlias = normalize(alias);
            if (!normalizedAlias.isEmpty()) {
                COMMAND_REGISTRY.put(normalizedAlias, registration);
            }
        }

        if (info.cooldown() > 0) {
            LOGGER.info("Registered command '/{}' [scope: {}, cooldown: {} {}, aliases: {}]",
                    primaryName, info.scope(), info.cooldown(), info.cooldownUnit(), Arrays.toString(info.aliases()));
        } else {
            LOGGER.info("Registered command '/{}' [scope: {}, aliases: {}]",
                    primaryName, info.scope(), Arrays.toString(info.aliases()));
        }
    }

    /**
     * Unregisters a command and all of its associated aliases.
     *
     * @param commandIdentifier primary name or alias of the command to unregister
     */
    public static void unregister(String commandIdentifier) {
        if (commandIdentifier == null || commandIdentifier.isBlank()) {
            return;
        }

        String key = normalize(commandIdentifier);
        CommandRegistration removed = COMMAND_REGISTRY.remove(key);

        if (removed != null) {
            COMMAND_REGISTRY.remove(removed.primaryName());
            for (String alias : removed.info().aliases()) {
                COMMAND_REGISTRY.remove(normalize(alias));
            }
            LOGGER.info("Successfully unregistered command '/{}' and all associated aliases", removed.primaryName());
        }
    }

    /**
     * Dispatches a raw console or in-game command string.
     *
     * @param rawInput   the complete raw input string (e.g. {@code "/heal \"Player Name\""})
     * @param connection the remote client UDP connection, or {@code null} if dispatched from the server console
     * @return the command output message, or {@code null} if the command is not registered in Avrix (pass-through)
     */
    public static String handleCommand(String rawInput, UdpConnection connection) {
        if (rawInput == null || rawInput.isBlank()) {
            return null;
        }

        var tokens = CommandArgumentParser.parseTokens(rawInput);
        if (tokens.isEmpty()) {
            return null;
        }

        String rawTrigger = tokens.getFirst();
        String trigger = normalize(rawTrigger);

        CommandRegistration reg = COMMAND_REGISTRY.get(trigger);
        if (reg == null) {
            LOGGER.debug("Command '/{}' is not registered in Avrix API. Delegating to vanilla engine.", trigger);
            return null;
        }

        String[] args = tokens.size() > 1
                ? tokens.subList(1, tokens.size()).toArray(String[]::new)
                : new String[0];

        IsoPlayer player = resolvePlayer(connection);
        String senderName = resolveSenderName(connection, player);
        String senderIp = connection != null ? connection.getIP() : "Console";

        CommandContext context = new CommandContext(senderName, player, connection, rawInput, args);

        // Validate CommandScope
        if (!reg.info().scope().allows(context.isPlayer())) {
            String scopeMsg = switch (reg.info().scope()) {
                case CHAT -> "This command can only be executed in-game by a player.";
                case CONSOLE -> "This command can only be executed from the server console.";
                case BOTH -> "Command scope validation failed.";
            };
            EventManager.invoke(ServerEvents.COMMAND_FAILURE, context, trigger, scopeMsg, null);
            return scopeMsg;
        }

        // Authorize parent command permissions
        if (!isAuthorized(context, reg.info().permission(), reg.info().capability())) {
            LOGGER.warn("Access denied for command '/{}' issued by '{}' (IP: {})", trigger, senderName, senderIp);
            String denialMsg = "You do not have permission to execute this command.";
            EventManager.invoke(ServerEvents.COMMAND_FAILURE, context, trigger, denialMsg, null);
            return denialMsg;
        }

        // Resolve execution target: Subcommand or Root Command
        Map<String, Subcommand> subcommands = reg.command().subcommands();
        String subKey = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : null;
        Subcommand subcommand = (subKey != null && subcommands != null) ? subcommands.get(subKey) : null;
        String fullCommandPath = (subcommand != null) ? trigger + " " + subKey : trigger;

        if (subcommand != null) {
            // Validate Subcommand Permissions
            if (subcommand.permission().length > 0 || subcommand.capability().length > 0) {
                if (!isAuthorized(context, subcommand.permission(), subcommand.capability())) {
                    LOGGER.warn("Access denied for subcommand '/{}' issued by '{}' (IP: {})",
                            fullCommandPath, senderName, senderIp);
                    String subDenialMsg = "You do not have permission to execute this subcommand.";
                    EventManager.invoke(ServerEvents.COMMAND_FAILURE, context, fullCommandPath, subDenialMsg, null);
                    return subDenialMsg;
                }
            }

            // Validate Subcommand Cooldown
            if (context.isPlayer() && subcommand.cooldown() > 0) {
                String cdKey = senderName.toLowerCase(Locale.ROOT) + ":" + reg.primaryName() + "." + subKey;
                long remainingMs = checkCooldown(cdKey, subcommand.cooldown(), subcommand.cooldownUnit());
                if (remainingMs > 0) {
                    String cdMsg = "This subcommand is on cooldown. Please wait " + formatDuration(remainingMs) + ".";
                    EventManager.invoke(ServerEvents.COMMAND_FAILURE, context, fullCommandPath, cdMsg, null);
                    return cdMsg;
                }
            }
        } else {
            // Validate Root Command Cooldown
            if (context.isPlayer() && reg.info().cooldown() > 0) {
                String cdKey = senderName.toLowerCase(Locale.ROOT) + ":" + reg.primaryName();
                long remainingMs = checkCooldown(cdKey, reg.info().cooldown(), reg.info().cooldownUnit());
                if (remainingMs > 0) {
                    String cdMsg = "This command is on cooldown. Please wait " + formatDuration(remainingMs) + ".";
                    EventManager.invoke(ServerEvents.COMMAND_FAILURE, context, fullCommandPath, cdMsg, null);
                    return cdMsg;
                }
            }
        }

        // Fire Pre-Execution Event
        EventManager.invoke(ServerEvents.COMMAND_EXECUTE, context, fullCommandPath);

        // Safe Execution
        long startTime = System.nanoTime();
        try {
            String result;
            if (subcommand != null) {
                result = subcommand.execute(context.subContext(1));
            } else {
                result = reg.command().execute(context);
            }

            long elapsedMs = (System.nanoTime() - startTime) / 1_000_000L;
            String finalResponse = result != null ? result : "Command executed successfully.";

            LOGGER.info("Command '/{}' executed by '{}' in {} ms", fullCommandPath, senderName, elapsedMs);

            // Fire Success Event
            EventManager.invoke(ServerEvents.COMMAND_SUCCESS, context, fullCommandPath, finalResponse, elapsedMs);

            return finalResponse;
        } catch (Exception ex) {
            long elapsedMs = (System.nanoTime() - startTime) / 1_000_000L;
            LOGGER.error("Execution failed for command '/{}' issued by '{}' (IP: {}) after {} ms",
                    fullCommandPath, senderName, senderIp, elapsedMs, ex);

            String errorMsg = "An internal error occurred during execution: " + ex.getMessage();

            // Fire Failure Event
            EventManager.invoke(ServerEvents.COMMAND_FAILURE, context, fullCommandPath, errorMsg, ex);

            return errorMsg;
        }
    }

    private static long checkCooldown(String key, long duration, TimeUnit unit) {
        long now = System.currentTimeMillis();
        Long expireTime = COOLDOWNS.get(key);

        if (expireTime != null && now < expireTime) {
            return expireTime - now;
        }

        COOLDOWNS.put(key, now + unit.toMillis(duration));
        return 0L;
    }

    private static boolean isAuthorized(CommandContext ctx, String[] permissions, Capability[] capabilities) {
        if (!ctx.isPlayer()) {
            return true;
        }

        IsoPlayer player = ctx.player();
        if (player == null) {
            return false;
        }

        boolean hasExplicitPermConfig = permissions != null && permissions.length > 0;
        boolean hasExplicitCapConfig = capabilities != null && capabilities.length > 0;

        if (!hasExplicitPermConfig && !hasExplicitCapConfig) {
            return true;
        }

        if (hasExplicitPermConfig) {
            for (String perm : permissions) {
                if (perm != null && !perm.isBlank() && PermissionsManager.hasPermission(player, perm)) {
                    return true;
                }
            }
        }

        if (hasExplicitCapConfig) {
            for (Capability cap : capabilities) {
                if (cap != null && cap != Capability.None && PermissionsManager.hasCapability(player, cap)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static IsoPlayer resolvePlayer(UdpConnection connection) {
        if (connection == null || connection.players == null) {
            return null;
        }
        for (IsoPlayer p : connection.players) {
            if (p != null) {
                return p;
            }
        }
        return null;
    }

    private static String resolveSenderName(UdpConnection connection, IsoPlayer player) {
        if (connection == null) {
            return "ServerConsole";
        }
        if (connection.getUserName() != null && !connection.getUserName().isBlank()) {
            return connection.getUserName();
        }
        if (player != null && player.getUsername() != null) {
            return player.getUsername();
        }
        return "UnknownPlayer";
    }

    private static String normalize(String input) {
        if (input == null) {
            return "";
        }
        String stripped = input.strip();
        if (stripped.startsWith("/")) {
            stripped = stripped.substring(1).stripLeading();
        }
        return stripped.toLowerCase(Locale.ROOT);
    }

    private static String formatDuration(long millis) {
        long seconds = (millis / 1000) % 60;
        long minutes = (millis / (1000 * 60)) % 60;
        long hours = (millis / (1000 * 60 * 60));

        if (hours > 0) {
            return "%dh %dm".formatted(hours, minutes);
        }
        if (minutes > 0) {
            return "%dm %ds".formatted(minutes, seconds);
        }
        return "%ds".formatted(Math.max(1, seconds));
    }

    private record CommandRegistration(Command command, CommandInfo info, String primaryName) {
        private CommandRegistration {
            Objects.requireNonNull(command, "command cannot be null");
            Objects.requireNonNull(info, "info cannot be null");
            Objects.requireNonNull(primaryName, "primaryName cannot be null");
        }
    }
}