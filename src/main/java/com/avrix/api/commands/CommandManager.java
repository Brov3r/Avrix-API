package com.avrix.api.commands;

import com.avrix.api.events.EventManager;
import com.avrix.api.events.ServerEvents;
import com.avrix.api.permissions.PermissionsManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zombie.characters.Capability;
import zombie.characters.IsoPlayer;
import zombie.commands.CommandBase;
import zombie.core.raknet.UdpConnection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Thread-safe command registration and dispatching engine.
 * <p>
 * Commands can be registered globally or with an owner token, which makes it
 * possible to remove every command belonging to a plugin with one call.
 * Server and client command sources are represented explicitly by
 * {@link CommandSource}; the legacy {@link UdpConnection} overload remains for
 * existing server integrations.
 */
public final class CommandManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommandManager.class);
    private static final int COOLDOWN_PRUNE_THRESHOLD = 256;

    private static final Map<String, CommandRegistration> COMMAND_REGISTRY = new ConcurrentHashMap<>();
    private static final Map<Object, Set<CommandRegistration>> OWNED_COMMANDS = new ConcurrentHashMap<>();
    private static final Map<CooldownKey, Long> COOLDOWNS = new ConcurrentHashMap<>();
    private static final AtomicLong ERROR_SEQUENCE = new AtomicLong();

    private CommandManager() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Returns an unmodifiable snapshot view of all registered commands, including aliases.
     *
     * @return read-only map of command triggers to command instances
     */
    public static Map<String, Command> getCommands() {
        return COMMAND_REGISTRY.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().command()
                ));
    }

    /**
     * Returns an unmodifiable snapshot of primary command names.
     *
     * @return read-only map of primary names to command instances
     */
    public static Map<String, Command> getPrimaryCommands() {
        Map<String, Command> result = new TreeMap<>();
        for (CommandRegistration registration : new LinkedHashSet<>(COMMAND_REGISTRY.values())) {
            result.put(registration.primaryName(), registration.command());
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * Returns an unmodifiable snapshot of command descriptors by primary name.
     *
     * @return read-only map of command descriptors
     */
    public static Map<String, CommandDescriptor> getDescriptors() {
        Map<String, CommandDescriptor> result = new TreeMap<>();
        for (CommandRegistration registration : new LinkedHashSet<>(COMMAND_REGISTRY.values())) {
            result.put(registration.primaryName(), registration.descriptor());
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * Resolves a command descriptor by primary name or alias.
     *
     * @param commandIdentifier primary name or alias
     * @return descriptor, or {@code null} if the command is not registered
     */
    public static CommandDescriptor getDescriptor(String commandIdentifier) {
        String key = normalize(commandIdentifier);
        if (key.isEmpty()) {
            return null;
        }
        CommandRegistration registration = COMMAND_REGISTRY.get(key);
        return registration != null ? registration.descriptor() : null;
    }

    /**
     * Registers a command into the dispatch registry without an owner.
     *
     * @param command command instance
     * @throws NullPointerException     if {@code command} is null
     * @throws IllegalArgumentException if {@link CommandInfo} is missing or a name is invalid
     */
    public static void register(Command command) {
        register(null, command);
    }

    /**
     * Registers a command and associates it with an owner token.
     * <p>
     * Primary-name conflicts with a different command class are rejected and logged.
     * Alias conflicts are skipped. Re-registering a command of the same class replaces
     * the previous instance, which makes plugin reloads predictable.
     *
     * @param owner   owner token, or {@code null} for an unowned command
     * @param command command instance
     * @throws NullPointerException     if {@code command} is null
     * @throws IllegalArgumentException if {@link CommandInfo} is missing or a name is invalid
     */
    public static void register(Object owner, Command command) {
        Objects.requireNonNull(command, "Command instance cannot be null");

        Class<?> commandClass = command.getClass();
        CommandInfo info = commandClass.getAnnotation(CommandInfo.class);
        if (info == null) {
            throw new IllegalArgumentException("Class '%s' is missing @CommandInfo annotation"
                    .formatted(commandClass.getName()));
        }

        String primaryName = validateName(info.name(), "Primary command name", commandClass);
        CommandRegistration previous = COMMAND_REGISTRY.get(primaryName);
        if (previous != null && !isSameCommandType(previous, command)) {
            LOGGER.warn("Command '/{}' from '{}' is already registered by '{}'; registration skipped",
                    primaryName, commandClass.getName(), previous.command().getClass().getName());
            return;
        }

        if (!info.overrideVanilla() && isVanillaCommand(primaryName)) {
            LOGGER.warn("Command '/{}' from '{}' conflicts with a vanilla Project Zomboid command; " +
                            "set overrideVanilla = true to replace it",
                    primaryName, commandClass.getName());
            return;
        }

        Set<String> aliases = new LinkedHashSet<>();
        for (String rawAlias : info.aliases()) {
            if (rawAlias == null || rawAlias.isBlank()) {
                continue;
            }
            String alias = normalize(rawAlias);
            if (alias.isEmpty() || alias.equals(primaryName) || !aliases.add(alias)) {
                continue;
            }

            CommandRegistration aliasOwner = COMMAND_REGISTRY.get(alias);
            if (aliasOwner != null && aliasOwner != previous && !isSameCommandType(aliasOwner, command)) {
                LOGGER.warn("Alias '/{}' from '{}' is already used by '{}'; alias skipped",
                        alias, commandClass.getName(), aliasOwner.command().getClass().getName());
                aliases.remove(alias);
                continue;
            }

            if (!info.overrideVanilla() && isVanillaCommand(alias)) {
                LOGGER.warn("Alias '/{}' from '{}' conflicts with a vanilla Project Zomboid command; alias skipped",
                        alias, commandClass.getName());
                aliases.remove(alias);
            }
        }

        Map<String, Subcommand> subcommands = snapshotSubcommands(command, commandClass);
        CommandDescriptor descriptor = buildDescriptor(info, primaryName, aliases, subcommands);
        CommandRegistration registration = new CommandRegistration(
                command, info, primaryName, aliases, owner, subcommands, descriptor
        );

        if (previous != null) {
            removeRegistration(previous, false);
        }

        COMMAND_REGISTRY.put(primaryName, registration);
        for (String alias : aliases) {
            COMMAND_REGISTRY.put(alias, registration);
        }

        if (owner != null) {
            OWNED_COMMANDS
                    .computeIfAbsent(owner, ignored -> ConcurrentHashMap.newKeySet())
                    .add(registration);
        }

        LOGGER.debug("Registered command '/{}' [owner: {}, scope: {}, aliases: {}, subcommands: {}]",
                primaryName, owner, info.scope(), aliases, subcommands.keySet());
        EventManager.invoke(ServerEvents.COMMAND_REGISTERED, descriptor);
    }

    /**
     * Unregisters a command by primary name or alias.
     *
     * @param commandIdentifier primary name or alias
     */
    public static void unregister(String commandIdentifier) {
        String key = normalize(commandIdentifier);
        if (key.isEmpty()) {
            return;
        }

        CommandRegistration registration = COMMAND_REGISTRY.get(key);
        if (registration != null) {
            removeRegistration(registration, true);
        }
    }

    /**
     * Unregisters a specific command instance.
     *
     * @param command command instance
     */
    public static void unregister(Command command) {
        if (command == null) {
            return;
        }

        for (CommandRegistration registration : new LinkedHashSet<>(COMMAND_REGISTRY.values())) {
            if (registration.command() == command) {
                removeRegistration(registration, true);
            }
        }
    }

    /**
     * Unregisters every command associated with the given owner token.
     *
     * @param owner owner token
     */
    public static void unregisterAll(Object owner) {
        if (owner == null) {
            return;
        }

        Set<CommandRegistration> registrations = OWNED_COMMANDS.remove(owner);
        if (registrations == null) {
            return;
        }
        for (CommandRegistration registration : new ArrayList<>(registrations)) {
            removeRegistration(registration, true);
        }
    }

    /**
     * Dispatches a raw server command string using a legacy connection source.
     *
     * @param rawInput   raw command input
     * @param connection server connection, or {@code null} for the server console
     * @return command output, or {@code null} if the command should be delegated to vanilla
     */
    public static String handleCommand(String rawInput, UdpConnection connection) {
        return dispatchCommand(rawInput, CommandSource.of(connection));
    }

    /**
     * Dispatches a raw client-side command for a local player.
     *
     * @param rawInput raw command input
     * @param player   local player
     * @return command output, or {@code null} if the command is not registered
     * @throws NullPointerException if {@code player} is null
     */
    public static String handleClientCommand(String rawInput, IsoPlayer player) {
        return dispatchCommand(rawInput, CommandSource.client(player));
    }

    /**
     * Dispatches a raw command string with an explicit command source.
     *
     * @param rawInput raw command input
     * @param source   command source
     * @return command output, or {@code null} if the command is not registered
     */
    public static String dispatchCommand(String rawInput, CommandSource source) {
        if (rawInput == null || rawInput.isBlank()) {
            return null;
        }

        CommandSource effectiveSource = source != null ? source : CommandSource.serverConsole();

        List<String> tokens;
        try {
            tokens = CommandArgumentParser.parseTokens(rawInput);
        } catch (IllegalArgumentException ex) {
            CommandContext context = new CommandContext(
                    effectiveSource.senderName(), effectiveSource, effectiveSource.player(),
                    effectiveSource.connection(), rawInput, new String[0]
            );
            String message = CommandMessages.invalidSyntax(ex.getMessage());
            EventManager.invoke(ServerEvents.COMMAND_FAILURE, context, rawInput.strip(), message, ex);
            return message;
        }

        if (tokens.isEmpty()) {
            return null;
        }

        String rawTrigger = tokens.getFirst();
        String trigger = normalize(rawTrigger);
        CommandRegistration registration = COMMAND_REGISTRY.get(trigger);
        if (registration == null) {
            LOGGER.debug("Command '/{}' is not registered in Avrix API. Delegating to vanilla engine.", trigger);
            return null;
        }

        String[] args = tokens.size() > 1
                ? tokens.subList(1, tokens.size()).toArray(String[]::new)
                : new String[0];

        CommandContext context = new CommandContext(
                effectiveSource.senderName(), effectiveSource, effectiveSource.player(),
                effectiveSource.connection(), rawInput, args
        );
        String senderName = effectiveSource.senderName();
        String senderIp = effectiveSource.address();
        String fullCommandPath = trigger;

        try {
            if (!registration.info().scope().allows(effectiveSource)) {
                String scopeMessage = CommandMessages.scope(registration.info().scope());
                EventManager.invoke(ServerEvents.COMMAND_FAILURE, context, fullCommandPath, scopeMessage, null);
                return scopeMessage;
            }

            if (!isAuthorized(context, registration.info().permission(), registration.info().capability())) {
                LOGGER.warn("Access denied for command '/{}' issued by '{}' (IP: {})",
                        trigger, senderName, senderIp);
                String denialMessage = CommandMessages.noPermission();
                EventManager.invoke(ServerEvents.COMMAND_FAILURE, context, fullCommandPath, denialMessage, null);
                return denialMessage;
            }

            String subKey = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : null;
            Subcommand subcommand = subKey != null ? registration.subcommands().get(subKey) : null;
            if (subcommand != null) {
                fullCommandPath = trigger + " " + subKey;

                if (!isAuthorized(context, subcommand.permission(), subcommand.capability())) {
                    LOGGER.warn("Access denied for subcommand '/{}' issued by '{}' (IP: {})",
                            fullCommandPath, senderName, senderIp);
                    String denialMessage = CommandMessages.noSubcommandPermission();
                    EventManager.invoke(ServerEvents.COMMAND_FAILURE, context, fullCommandPath, denialMessage, null);
                    return denialMessage;
                }

                if (effectiveSource.isPlayer() && subcommand.cooldown() > 0) {
                    CooldownKey cooldownKey = new CooldownKey(
                            effectiveSource.identityKey(),
                            registration.primaryName() + "." + subKey
                    );
                    long remainingMs = checkCooldown(
                            cooldownKey, subcommand.cooldown(), subcommand.cooldownUnit()
                    );
                    if (remainingMs > 0) {
                        String cooldownMessage = CommandMessages.subcommandCooldown(formatDuration(remainingMs));
                        EventManager.invoke(ServerEvents.COMMAND_FAILURE, context, fullCommandPath,
                                cooldownMessage, null);
                        return cooldownMessage;
                    }
                }
            } else if (effectiveSource.isPlayer() && registration.info().cooldown() > 0) {
                CooldownKey cooldownKey = new CooldownKey(
                        effectiveSource.identityKey(),
                        registration.primaryName()
                );
                long remainingMs = checkCooldown(
                        cooldownKey, registration.info().cooldown(), registration.info().cooldownUnit()
                );
                if (remainingMs > 0) {
                    String cooldownMessage = CommandMessages.commandCooldown(formatDuration(remainingMs));
                    EventManager.invoke(ServerEvents.COMMAND_FAILURE, context, fullCommandPath,
                            cooldownMessage, null);
                    return cooldownMessage;
                }
            }

            EventManager.invoke(ServerEvents.COMMAND_EXECUTE, context, fullCommandPath);

            long startTime = System.nanoTime();
            String result = subcommand != null
                    ? subcommand.execute(context.subContext(1))
                    : registration.command().execute(context);
            long elapsedMs = (System.nanoTime() - startTime) / 1_000_000L;
            String finalResponse = result != null ? result : CommandMessages.success();

            LOGGER.debug("Command '/{}' executed by '{}' in {} ms", fullCommandPath, senderName, elapsedMs);
            EventManager.invoke(ServerEvents.COMMAND_SUCCESS, context, fullCommandPath, finalResponse, elapsedMs);
            return finalResponse;
        } catch (Exception ex) {
            String errorId = Long.toHexString(ERROR_SEQUENCE.incrementAndGet());
            LOGGER.error("Execution failed for command '/{}' issued by '{}' (IP: {}) [id: {}]",
                    fullCommandPath, senderName, senderIp, errorId, ex);

            String errorMessage = CommandMessages.internalError(errorId);
            EventManager.invoke(ServerEvents.COMMAND_FAILURE, context, fullCommandPath, errorMessage, ex);
            return errorMessage;
        }
    }

    private static CommandRegistration removeRegistration(CommandRegistration registration, boolean fireEvent) {
        if (registration == null) {
            return null;
        }

        COMMAND_REGISTRY.remove(registration.primaryName(), registration);
        for (String alias : registration.aliases()) {
            COMMAND_REGISTRY.remove(alias, registration);
        }

        removeFromOwner(registration);
        clearCooldowns(registration);

        if (fireEvent) {
            EventManager.invoke(ServerEvents.COMMAND_UNREGISTERED, registration.descriptor());
        }
        LOGGER.debug("Unregistered command '/{}'", registration.primaryName());
        return registration;
    }

    private static void removeFromOwner(CommandRegistration registration) {
        Object owner = registration.owner();
        if (owner == null) {
            return;
        }

        Set<CommandRegistration> owned = OWNED_COMMANDS.get(owner);
        if (owned != null) {
            owned.remove(registration);
            if (owned.isEmpty()) {
                OWNED_COMMANDS.remove(owner, owned);
            }
        }
    }

    private static boolean isSameCommandType(CommandRegistration registration, Command command) {
        return registration.command().getClass() == command.getClass();
    }

    private static Map<String, Subcommand> snapshotSubcommands(Command command, Class<?> commandClass) {
        Map<String, Subcommand> result = new java.util.LinkedHashMap<>();
        try {
            Map<String, Subcommand> declared = command.subcommands();
            if (declared == null) {
                return Collections.emptyMap();
            }
            for (Map.Entry<String, Subcommand> entry : declared.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                String key = normalize(entry.getKey());
                if (key.isEmpty() || containsWhitespace(key)) {
                    LOGGER.warn("Ignoring invalid subcommand key '{}' in '{}'",
                            entry.getKey(), commandClass.getName());
                    continue;
                }
                result.put(key, entry.getValue());
            }
        } catch (Exception ex) {
            LOGGER.error("Failed to snapshot subcommands for '{}'", commandClass.getName(), ex);
        }
        return Collections.unmodifiableMap(result);
    }

    private static CommandDescriptor buildDescriptor(CommandInfo info,
                                                     String primaryName,
                                                     Set<String> aliases,
                                                     Map<String, Subcommand> subcommands) {
        List<String> permissions = new ArrayList<>();
        for (String permission : info.permission()) {
            if (permission != null && !permission.isBlank()) {
                permissions.add(permission.strip());
            }
        }

        List<Capability> capabilities = new ArrayList<>();
        for (Capability capability : info.capability()) {
            if (capability != null && capability != Capability.None) {
                capabilities.add(capability);
            }
        }

        return new CommandDescriptor(
                primaryName,
                new LinkedHashSet<>(aliases),
                info.description(),
                info.usage(),
                info.scope(),
                permissions,
                capabilities,
                info.cooldown(),
                info.cooldownUnit(),
                new LinkedHashSet<>(subcommands.keySet())
        );
    }

    private static boolean isAuthorized(CommandContext context, String[] permissions, Capability[] capabilities) {
        if (!context.isPlayer()) {
            return true;
        }

        IsoPlayer player = context.player();
        if (player == null) {
            return false;
        }

        boolean hasPermissionConstraint = hasPermissionConstraint(permissions);
        boolean hasCapabilityConstraint = hasCapabilityConstraint(capabilities);
        if (!hasPermissionConstraint && !hasCapabilityConstraint) {
            return true;
        }

        if (hasPermissionConstraint) {
            for (String permission : permissions) {
                if (permission != null && !permission.isBlank()
                        && PermissionsManager.hasPermission(player, permission)) {
                    return true;
                }
            }
        }

        if (hasCapabilityConstraint) {
            for (Capability capability : capabilities) {
                if (capability != null && capability != Capability.None
                        && PermissionsManager.hasCapability(player, capability)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean hasPermissionConstraint(String[] permissions) {
        if (permissions == null) {
            return false;
        }
        for (String permission : permissions) {
            if (permission != null && !permission.isBlank()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasCapabilityConstraint(Capability[] capabilities) {
        if (capabilities == null) {
            return false;
        }
        for (Capability capability : capabilities) {
            if (capability != null && capability != Capability.None) {
                return true;
            }
        }
        return false;
    }

    private static long checkCooldown(CooldownKey key, long duration, TimeUnit unit) {
        if (unit == null || duration <= 0) {
            return 0L;
        }

        long now = System.nanoTime();
        long durationNanos = unit.toNanos(duration);
        long[] remaining = new long[1];

        COOLDOWNS.compute(key, (ignored, expiresAt) -> {
            if (expiresAt != null && expiresAt > now) {
                remaining[0] = TimeUnit.NANOSECONDS.toMillis(expiresAt - now);
                return expiresAt;
            }

            long next;
            try {
                next = Math.addExact(now, durationNanos);
            } catch (ArithmeticException ignoredOverflow) {
                next = Long.MAX_VALUE;
            }
            if (next < now) {
                next = Long.MAX_VALUE;
            }
            remaining[0] = 0L;
            return next;
        });

        if (COOLDOWNS.size() > COOLDOWN_PRUNE_THRESHOLD) {
            pruneCooldowns(now);
        }
        return remaining[0];
    }

    private static void pruneCooldowns(long now) {
        COOLDOWNS.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    private static void clearCooldowns(CommandRegistration registration) {
        String primaryName = registration.primaryName();
        COOLDOWNS.keySet().removeIf(key -> key.commandPath().equals(primaryName)
                || key.commandPath().startsWith(primaryName + "."));
    }

    private static String validateName(String rawName, String label, Class<?> commandClass) {
        String normalized = normalize(rawName);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank in class: " + commandClass.getName());
        }
        if (containsWhitespace(normalized)) {
            throw new IllegalArgumentException(label + " cannot contain whitespace in class: "
                    + commandClass.getName());
        }
        return normalized;
    }

    private static boolean containsWhitespace(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isWhitespace(value.charAt(index))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isVanillaCommand(String commandName) {
        try {
            return CommandBase.findCommandCls(commandName) != null;
        } catch (RuntimeException ex) {
            LOGGER.warn("Failed to check vanilla command conflict for '/{}'", commandName, ex);
            return false;
        }
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

    private record CommandRegistration(
            Command command,
            CommandInfo info,
            String primaryName,
            Set<String> aliases,
            Object owner,
            Map<String, Subcommand> subcommands,
            CommandDescriptor descriptor
    ) {
        private CommandRegistration {
            Objects.requireNonNull(command, "command cannot be null");
            Objects.requireNonNull(info, "info cannot be null");
            Objects.requireNonNull(primaryName, "primaryName cannot be null");
            aliases = aliases != null ? Set.copyOf(aliases) : Collections.emptySet();
            subcommands = subcommands != null ? Map.copyOf(subcommands) : Collections.emptyMap();
            Objects.requireNonNull(descriptor, "descriptor cannot be null");
        }
    }

    private record CooldownKey(String identity, String commandPath) {
    }
}
