package com.avrix.api.permissions;

import com.avrix.api.config.ConfigManager;
import com.avrix.api.events.DefaultEvents;
import com.avrix.api.events.EventManager;
import com.avrix.api.events.ServerEvents;
import com.avrix.api.events.SubscribeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.loader.HeaderMode;
import org.spongepowered.configurate.objectmapping.ObjectMapper;
import org.spongepowered.configurate.util.NamingSchemes;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;
import zombie.characters.Capability;
import zombie.characters.IsoPlayer;
import zombie.characters.Role;
import zombie.characters.Roles;
import zombie.core.Color;
import zombie.core.Core;
import zombie.core.raknet.UdpConnection;
import zombie.debug.DebugType;
import zombie.debug.LogSeverity;
import zombie.network.GameServer;
import zombie.network.PacketTypes;
import zombie.network.ServerWorldDatabase;
import zombie.network.chat.ChatServer;
import zombie.network.packets.INetworkPacket;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Thread-safe central domain service managing role lifecycles, SQLite persistence,
 * declarative {@code permissions.yml} configuration loading,
 * SteamID64 identity mapping, and fine-grained authorization evaluations.
 * <p>
 * Expiry work is enqueued by the background scanner and executed from the server tick listener,
 * so role changes, chat notifications, and events run on the server thread whenever the event
 * bridge is available.
 */
public final class PermissionsManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(PermissionsManager.class);

    private static final String PERMISSIONS_FILE = "permissions.yml";

    private static final Map<String, String> CONFIGURED_USER_GROUPS = new ConcurrentHashMap<>();
    private static final Map<String, String> CONFIGURED_USER_PREFIXES = new ConcurrentHashMap<>();
    private static final Map<String, String> CONFIGURED_USER_SUFFIXES = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, PlayerPermission>> CUSTOM_PLAYER_PERMISSIONS = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, PlayerPermission>> YAML_PLAYER_PERMISSIONS = new ConcurrentHashMap<>();
    private static final Set<String> YAML_CREATED_ROLES = ConcurrentHashMap.newKeySet();
    private static final Map<String, TemporaryRoleEntry> TEMPORARY_USER_ROLES = new ConcurrentHashMap<>();
    private static final AtomicBoolean STARTUP_CONFIG_LOADED = new AtomicBoolean();
    private static final Queue<Runnable> PENDING_EXPIRY_TASKS = new ConcurrentLinkedQueue<>();
    private static final AtomicBoolean EXPIRY_TICK_REGISTERED = new AtomicBoolean();

    private static final Object EXPIRY_TICK_LISTENER = new Object() {
        @SubscribeEvent(DefaultEvents.ON_TICK)
        public void onTick() {
            processPendingExpiryTasks();
        }
    };

    private PermissionsManager() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    private static final ScheduledExecutorService EXPIRATION_SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "Avrix-Permissions-Expiry-Tracker");
        thread.setDaemon(true);
        return thread;
    });

    static {
        registerExpiryTickListener();
        EXPIRATION_SCHEDULER.scheduleAtFixedRate(() -> {
            try {
                if (EXPIRY_TICK_REGISTERED.get()) {
                    enqueueExpiredPermissions();
                    enqueueExpiredRoles();
                } else {
                    checkAllExpiredPermissions();
                    checkAllExpiredRoles();
                }
            } catch (Throwable t) {
                LOGGER.error("Error occurred during periodic permissions/roles expiry check", t);
            }
        }, 15, 15, TimeUnit.SECONDS);
    }

    /**
     * Registers the server tick listener used to execute expiry work on the main thread.
     */
    private static void registerExpiryTickListener() {
        try {
            EventManager.register(EXPIRY_TICK_LISTENER);
            EXPIRY_TICK_REGISTERED.set(true);
        } catch (Throwable t) {
            LOGGER.warn("Failed to register permissions expiry tick listener; using scheduler thread fallback", t);
        }
    }

    /**
     * Stops the background expiry scheduler and releases runtime caches.
     */
    public static void shutdown() {
        EXPIRATION_SCHEDULER.shutdownNow();
        if (EXPIRY_TICK_REGISTERED.compareAndSet(true, false)) {
            EventManager.unregister(EXPIRY_TICK_LISTENER);
        }
        PENDING_EXPIRY_TASKS.clear();
        CUSTOM_PLAYER_PERMISSIONS.clear();
        YAML_PLAYER_PERMISSIONS.clear();
        CONFIGURED_USER_GROUPS.clear();
        CONFIGURED_USER_PREFIXES.clear();
        CONFIGURED_USER_SUFFIXES.clear();
        TEMPORARY_USER_ROLES.clear();
        YAML_CREATED_ROLES.clear();
        STARTUP_CONFIG_LOADED.set(false);
        DatabasePermissionsHelper.closeConnection();
    }

    /**
     * Periodic scanner across all active cached permissions.
     */
    public static void checkAllExpiredPermissions() {
        for (String userKey : new ArrayList<>(CUSTOM_PLAYER_PERMISSIONS.keySet())) {
            Map<String, PlayerPermission> nodes = CUSTOM_PLAYER_PERMISSIONS.get(userKey);
            if (nodes == null) {
                continue;
            }
            for (String node : new ArrayList<>(nodes.keySet())) {
                expirePermissionNode(userKey, node);
            }
        }
    }

    /**
     * Enqueues expired permission cleanup for execution on the server thread.
     */
    private static void enqueueExpiredPermissions() {
        for (Map.Entry<String, Map<String, PlayerPermission>> entry : CUSTOM_PLAYER_PERMISSIONS.entrySet()) {
            for (Map.Entry<String, PlayerPermission> node : entry.getValue().entrySet()) {
                if (node.getValue() != null && node.getValue().isExpired()) {
                    String userKey = entry.getKey();
                    String nodeKey = node.getKey();
                    PENDING_EXPIRY_TASKS.offer(() -> expirePermissionNode(userKey, nodeKey));
                }
            }
        }
    }

    /**
     * Assigns a temporary role with a duration (e.g. 7 DAYS).
     *
     * @param identifier   player username or SteamID64
     * @param roleName     temporary role name
     * @param duration     duration value
     * @param unit         duration unit
     * @param fallbackRole role restored after expiration
     * @return {@code true} if the role was assigned
     */
    public static boolean assignTemporaryRole(String identifier, String roleName, long duration, TimeUnit unit, String fallbackRole) {
        if (duration <= 0 || unit == null) {
            return false;
        }
        return assignTemporaryRole(identifier, roleName, Instant.now().plusMillis(unit.toMillis(duration)), fallbackRole);
    }

    /**
     * Assigns a temporary role expiring at a specific timestamp.
     *
     * @param identifier   player username or SteamID64
     * @param roleName     temporary role name
     * @param expiration   expiration instant
     * @param fallbackRole role restored after expiration
     * @return {@code true} if the role was assigned
     */
    public static boolean assignTemporaryRole(String identifier, String roleName, Instant expiration, String fallbackRole) {
        if (identifier == null || identifier.isBlank() || roleName == null || roleName.isBlank()
                || expiration == null || !expiration.isAfter(Instant.now())) {
            return false;
        }

        String fallback = fallbackRole != null && !fallbackRole.isBlank() ? fallbackRole : "user";

        boolean success = assignRole(identifier, roleName);
        if (!success) return false;

        String key = normalizeIdentifier(identifier);
        TEMPORARY_USER_ROLES.put(key, new TemporaryRoleEntry(roleName, expiration, fallback));

        Connection conn = getDatabaseConnection();
        if (conn != null && !DatabasePermissionsHelper.saveTemporaryRole(
                conn, key, roleName, expiration, fallback)) {
            TEMPORARY_USER_ROLES.remove(key);
            assignRole(identifier, fallback);
            LOGGER.error("Failed to persist temporary role in SQLite for user '{}'", key);
            return false;
        }
        return true;
    }

    /**
     * Periodic check to evict expired roles and revert players back to fallback role.
     */
    public static void checkAllExpiredRoles() {
        for (String identifier : new ArrayList<>(TEMPORARY_USER_ROLES.keySet())) {
            expireTemporaryRole(identifier);
        }
    }

    /**
     * Enqueues expired temporary role cleanup for execution on the server thread.
     */
    private static void enqueueExpiredRoles() {
        for (Map.Entry<String, TemporaryRoleEntry> entry : TEMPORARY_USER_ROLES.entrySet()) {
            if (entry.getValue().isExpired()) {
                String identifier = entry.getKey();
                PENDING_EXPIRY_TASKS.offer(() -> expireTemporaryRole(identifier));
            }
        }
    }

    /**
     * Processes a bounded batch of queued expiry operations.
     */
    public static void processPendingExpiryTasks() {
        Runnable task;
        int processed = 0;
        while (processed < 256 && (task = PENDING_EXPIRY_TASKS.poll()) != null) {
            try {
                task.run();
            } catch (Throwable t) {
                LOGGER.error("Failed to process a queued permission expiry task", t);
            }
            processed++;
        }
    }

    /**
     * Expires a single temporary permission node and persists the revocation.
     *
     * @param userKey normalized user identifier
     * @param nodeKey normalized permission node
     */
    private static void expirePermissionNode(String userKey, String nodeKey) {
        Map<String, PlayerPermission> nodes = CUSTOM_PLAYER_PERMISSIONS.get(userKey);
        if (nodes == null) {
            return;
        }
        PlayerPermission permission = nodes.get(nodeKey);
        if (permission == null || !permission.isExpired()) {
            return;
        }

        Connection conn = getDatabaseConnection();
        if (conn != null && !DatabasePermissionsHelper.deletePlayerPermission(conn, userKey, nodeKey)) {
            LOGGER.error("Failed to delete expired permission '{}' for user '{}' from SQLite; retrying later",
                    nodeKey, userKey);
            return;
        }

        if (!nodes.remove(nodeKey, permission)) {
            return;
        }
        if (nodes.isEmpty()) {
            CUSTOM_PLAYER_PERMISSIONS.remove(userKey, nodes);
        }

        notifyPlayerExpired(userKey, nodeKey);
        EventManager.invoke(ServerEvents.PERMISSION_REVOKED, userKey, nodeKey);
        LOGGER.debug("Temporary permission '{}' expired and was evicted for user '{}'", nodeKey, userKey);
    }

    /**
     * Expires a single temporary role and restores its fallback role.
     *
     * @param identifier normalized user identifier
     */
    private static void expireTemporaryRole(String identifier) {
        TemporaryRoleEntry tempRole = TEMPORARY_USER_ROLES.get(identifier);
        if (tempRole == null || !tempRole.isExpired()) {
            return;
        }
        if (!TEMPORARY_USER_ROLES.remove(identifier, tempRole)) {
            return;
        }

        String fallback = tempRole.fallbackRole();
        if (!assignRole(identifier, fallback)) {
            TEMPORARY_USER_ROLES.putIfAbsent(identifier, tempRole);
            LOGGER.warn("Temporary role '{}' expired for user '{}' but fallback role '{}' could not be assigned; retrying later",
                    tempRole.roleName(), identifier, fallback);
            return;
        }

        if (TEMPORARY_USER_ROLES.containsKey(identifier)) {
            return;
        }
        Connection conn = getDatabaseConnection();
        if (conn != null && !DatabasePermissionsHelper.deleteTemporaryRole(conn, identifier)) {
            LOGGER.error("Failed to delete expired temporary role from SQLite for user '{}'; retrying later", identifier);
        }

        notifyRoleExpired(identifier, tempRole.roleName(), fallback);
        LOGGER.info("Temporary role '{}' expired for user '{}'. Reverted to '{}'", tempRole.roleName(), identifier, fallback);
    }

    private static void notifyRoleExpired(String identifier, String oldRole, String newRole) {
        if (!GameServer.server || GameServer.Players == null) return;

        for (int i = 0; i < GameServer.Players.size(); i++) {
            IsoPlayer p = GameServer.Players.get(i);
            if (p != null && (identifier.equalsIgnoreCase(p.getUsername()) || identifier.equals(getSteamID(p)))) {
                UdpConnection conn = GameServer.getConnectionFromPlayer(p);
                if (conn != null && ChatServer.getInstance() != null) {
                    ChatServer.getInstance().sendMessageToServerChat(
                            conn,
                            String.format("Your temporary role '%s' has expired. You are now '%s'.", oldRole, newRole)
                    );
                }
                break;
            }
        }
    }

    /**
     * Loads or creates the {@code permissions.yml} configuration from the default plugin directory.
     * If the configuration file does not exist, a template structure will be created automatically.
     *
     * @return {@code true} if the configuration was loaded successfully
     */
    public static synchronized boolean loadPermissionsConfig() {
        return loadPermissionsConfig(defaultPermissionsPath());
    }

    /**
     * Loads {@code permissions.yml} once during server startup.
     * <p>
     * The {@code Roles.init()} mixin may run more than once on a fresh database. This method guards against
     * applying the same declarative file repeatedly while still allowing {@link #loadPermissionsConfig()}
     * to be used for explicit runtime reloads.
     *
     * @return {@code true} if the startup configuration was loaded or had already been loaded
     */
    public static synchronized boolean loadStartupPermissionsConfig() {
        if (!STARTUP_CONFIG_LOADED.compareAndSet(false, true)) {
            return true;
        }
        boolean loaded = loadPermissionsConfig(defaultPermissionsPath());
        if (!loaded) {
            STARTUP_CONFIG_LOADED.set(false);
        }
        return loaded;
    }

    /**
     * Resolves the default permissions file path.
     *
     * @return normalized path to {@code plugins/avrix-api/permissions.yml}
     */
    private static Path defaultPermissionsPath() {
        return ConfigManager.resolvePath("avrix-api", PERMISSIONS_FILE);
    }

    /**
     * Loads or creates the {@code permissions.yml} configuration from the specified target path.
     *
     * @param configPath absolute or relative path to the permissions.yml file
     * @return {@code true} if the file was loaded and applied successfully
     * @throws NullPointerException if {@code configPath} is null
     */
    public static synchronized boolean loadPermissionsConfig(Path configPath) {
        Objects.requireNonNull(configPath, "configPath cannot be null");
        try {
            if (Files.notExists(configPath)) {
                Path parent = configPath.getParent();
                if (parent != null && Files.notExists(parent)) {
                    Files.createDirectories(parent);
                }
                writeDefaultPermissionsFile(configPath);
                LOGGER.debug("Generated default permissions configuration at: {}", configPath);
            }

            YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                    .path(configPath)
                    .nodeStyle(NodeStyle.BLOCK)
                    .headerMode(HeaderMode.PRESERVE)
                    .defaultOptions(opts -> opts
                            .shouldCopyDefaults(true)
                            .serializers(b -> b.registerAnnotatedObjects(
                                    ObjectMapper.factoryBuilder().defaultNamingScheme(NamingSchemes.PASSTHROUGH).build()
                            ))
                    )
                    .build();

            CommentedConfigurationNode rootNode = loader.load();
            PermissionsConfig config = rootNode.get(PermissionsConfig.class);

            if (config != null) {
                applyConfig(config);
                LOGGER.debug("Successfully loaded permissions configuration from: {}", configPath);
                return true;
            }
            LOGGER.warn("Permissions configuration at '{}' did not contain a valid root model", configPath);
            return false;
        } catch (Exception e) {
            LOGGER.error("Failed to load permissions configuration from: {}", configPath, e);
            return false;
        }
    }

    /**
     * Applies loaded configuration data by registering or updating roles and assigning user permissions.
     * Overwrites vanilla default configurations if specified in YAML (YAML is authoritative).
     *
     * @param config the parsed permissions configuration
     */
    private static void applyConfig(PermissionsConfig config) {
        if (config == null) {
            LOGGER.warn("Attempted to apply null permissions configuration");
            return;
        }

        int groupCount = config.groups() != null ? config.groups().size() : 0;
        int userCount = config.users() != null ? config.users().size() : 0;
        LOGGER.info("Applying permissions.yml configuration (Groups: {}, Users: {})...", groupCount, userCount);

        Set<String> configuredRoleNames = config.groups() == null
                ? Set.of()
                : config.groups().keySet().stream()
                .filter(Objects::nonNull)
                .map(PermissionsManager::normalizeIdentifier)
                .filter(name -> !name.isEmpty())
                .collect(Collectors.toCollection(HashSet::new));
        YAML_CREATED_ROLES.removeIf(name -> {
            if (configuredRoleNames.contains(name)) {
                return false;
            }
            try {
                deleteRole(name, "permissions.yml");
                return true;
            } catch (RuntimeException ex) {
                LOGGER.warn("Failed to delete YAML-managed role '{}'; it will be retried on the next reload", name, ex);
                return false;
            }
        });

        List<Role> allRoles = Roles.getRoles();

        if (config.groups() != null) {
            for (Map.Entry<String, RoleConfig> entry : config.groups().entrySet()) {
                String roleName = entry.getKey() != null ? entry.getKey().strip() : "";
                if (roleName.isEmpty()) continue;

                RoleConfig rCfg = entry.getValue();
                if (rCfg == null) continue;

                // Find existing role index by case-insensitive name
                int existingIndex = -1;
                Role existingRole = null;
                if (allRoles != null) {
                    for (int i = 0; i < allRoles.size(); i++) {
                        Role r = allRoles.get(i);
                        if (r != null && r.getName() != null && roleName.equalsIgnoreCase(r.getName().strip())) {
                            existingIndex = i;
                            existingRole = r;
                            break;
                        }
                    }
                }

                ExtendedRole extendedRole;

                if (existingRole instanceof ExtendedRole er) {
                    extendedRole = er;
                    extendedRole.clearPermissions();
                    extendedRole.clearParents();
                    extendedRole.clearMetadata();
                    extendedRole.cleanCapability();
                    extendedRole.setDescription(rCfg.description() != null ? rCfg.description() : "");
                    extendedRole.setColor(parseColor(rCfg.color()));
                } else {
                    extendedRole = new ExtendedRole(roleName, rCfg.description(), parseColor(rCfg.color()));

                    if (existingRole != null) {
                        extendedRole.setId(existingRole.getId());
                        extendedRole.setPosition(existingRole.getPosition());
                    } else {
                        YAML_CREATED_ROLES.add(normalizeIdentifier(roleName));
                    }

                    if (allRoles != null) {
                        if (existingIndex != -1) {
                            allRoles.set(existingIndex, extendedRole);
                        } else {
                            allRoles.add(extendedRole);
                        }
                    }
                }

                if (isImmutableRole(roleName)) {
                    extendedRole.setReadOnly();
                }

                extendedRole.setPrefix(rCfg.prefix());
                extendedRole.setSuffix(rCfg.suffix());

                boolean bannedRole = "banned".equalsIgnoreCase(roleName);

                // Guaranteed LoginOnServer capability for all non-banned roles
                if (!bannedRole) {
                    extendedRole.addCapability(Capability.LoginOnServer);
                }

                // Capabilities mapping
                if (rCfg.capabilities() != null) {
                    for (String capName : rCfg.capabilities()) {
                        if (capName == null || capName.isBlank()) continue;
                        if ("*".equals(capName.strip())) {
                            for (Capability c : Capability.values()) {
                                if (c != null && c != Capability.None
                                        && !(bannedRole && c == Capability.LoginOnServer)) {
                                    extendedRole.addCapability(c);
                                }
                            }
                        } else {
                            Capability cap = findStandardCapability(capName.strip());
                            if (cap == null || cap == Capability.None) {
                                LOGGER.warn("Unknown capability '{}' specified for role '{}'", capName, roleName);
                            } else if (bannedRole && cap == Capability.LoginOnServer) {
                                LOGGER.warn("Ignoring LoginOnServer capability for banned role '{}'", roleName);
                            } else {
                                extendedRole.addCapability(cap);
                            }
                        }
                    }
                }

                // Custom permissions mapping
                if (rCfg.permissions() != null) {
                    for (String perm : rCfg.permissions()) {
                        if (perm != null && !perm.isBlank()) {
                            extendedRole.addPermission(perm);
                        }
                    }
                }

                // Parent inheritance mapping
                if (rCfg.parents() != null) {
                    for (String parent : rCfg.parents()) {
                        if (parent != null && !parent.isBlank()) {
                            extendedRole.addParent(parent);
                        }
                    }
                }

                // Custom metadata mapping
                if (rCfg.metadata() != null) {
                    rCfg.metadata().forEach(extendedRole::setMeta);
                }

                LOGGER.debug("Configured role '{}' [ID: {}, Pos: {}] -> Prefix: '{}', Suffix: '{}', Caps: {}, Perms: {}, Parents: {}",
                        roleName,
                        extendedRole.getId(),
                        extendedRole.getPosition(),
                        extendedRole.getPrefix(),
                        extendedRole.getSuffix(),
                        extendedRole.getCapabilities() != null ? extendedRole.getCapabilities().size() : 0,
                        extendedRole.getPermissions().size(),
                        extendedRole.getParents().isEmpty() ? "[]" : extendedRole.getParents()
                );

                syncRolesStaticField(roleName, extendedRole);
            }
        }

        // Sort roles list by position if present
        if (allRoles != null && allRoles.size() > 1) {
            allRoles.sort(Comparator.comparingInt(Role::getPosition));
        }

        if (config.groups() != null && !config.groups().isEmpty()) {
            persistAllRoles();
        }

        // Clear previous runtime in-memory user bindings
        CONFIGURED_USER_GROUPS.clear();
        CONFIGURED_USER_PREFIXES.clear();
        CONFIGURED_USER_SUFFIXES.clear();
        Map<String, Map<String, PlayerPermission>> desiredPermissions = new HashMap<>();

        // Collect declarative user mappings from YAML configuration
        if (config.users() != null) {
            for (Map.Entry<String, UserPermissionConfig> entry : config.users().entrySet()) {
                String rawIdentifier = entry.getKey();
                if (rawIdentifier == null || rawIdentifier.isBlank()) continue;

                String identifier = normalizeIdentifier(rawIdentifier);
                UserPermissionConfig uCfg = entry.getValue();
                if (uCfg == null) continue;

                // Cache configured group in memory ONLY. Never create stub DB accounts!
                if (uCfg.group() != null && !uCfg.group().isBlank()) {
                    String grp = uCfg.group().strip();
                    CONFIGURED_USER_GROUPS.put(identifier, grp);
                    LOGGER.debug("Mapped user '{}' -> Configured Group: '{}'", rawIdentifier, grp);
                }
                if (!uCfg.prefix().isBlank()) {
                    CONFIGURED_USER_PREFIXES.put(identifier, uCfg.prefix());
                }
                if (!uCfg.suffix().isBlank()) {
                    CONFIGURED_USER_SUFFIXES.put(identifier, uCfg.suffix());
                }

                if (uCfg.permissions() != null) {
                    for (String perm : uCfg.permissions()) {
                        if (perm != null && !perm.isBlank()) {
                            String node = perm.strip().toLowerCase(Locale.ROOT);
                            desiredPermissions.computeIfAbsent(identifier, _ -> new LinkedHashMap<>())
                                    .put(node, PlayerPermission.permanent(node));
                        }
                    }
                }

                if (uCfg.temporaryPermissions() != null) {
                    for (Map.Entry<String, String> tempEntry : uCfg.temporaryPermissions().entrySet()) {
                        if (tempEntry.getKey() == null || tempEntry.getValue() == null) continue;
                        try {
                            Instant expireAt = Instant.parse(tempEntry.getValue().strip());
                            if (!expireAt.isAfter(Instant.now())) {
                                continue;
                            }
                            String node = tempEntry.getKey().strip().toLowerCase(Locale.ROOT);
                            desiredPermissions.computeIfAbsent(identifier, _ -> new LinkedHashMap<>())
                                    .put(node, PlayerPermission.temporary(node, expireAt));
                        } catch (Exception _) {
                            LOGGER.warn("Invalid ISO-8601 timestamp '{}' for permission '{}' on user '{}'",
                                    tempEntry.getValue(), tempEntry.getKey(), identifier);
                        }
                    }
                }
            }
        }

        Map<String, Map<String, PlayerPermission>> appliedPermissions = new HashMap<>();

        // Remove YAML-managed permissions that are no longer declared.
        for (Map.Entry<String, Map<String, PlayerPermission>> oldEntry : YAML_PLAYER_PERMISSIONS.entrySet()) {
            String user = oldEntry.getKey();
            Map<String, PlayerPermission> desired = desiredPermissions.getOrDefault(user, Map.of());
            for (Map.Entry<String, PlayerPermission> oldNode : oldEntry.getValue().entrySet()) {
                PlayerPermission desiredPermission = desired.get(oldNode.getKey());
                if (desiredPermission == null
                        || !Objects.equals(desiredPermission.expiration(), oldNode.getValue().expiration())) {
                    if (!revokePermissionFromPlayer(user, oldNode.getKey())) {
                        appliedPermissions.computeIfAbsent(user, _ -> new ConcurrentHashMap<>())
                                .put(oldNode.getKey(), oldNode.getValue());
                    }
                }
            }
        }

        // Apply only new or changed declarative permissions.
        for (Map.Entry<String, Map<String, PlayerPermission>> desiredEntry : desiredPermissions.entrySet()) {
            String user = desiredEntry.getKey();
            Map<String, PlayerPermission> oldNodes = YAML_PLAYER_PERMISSIONS.getOrDefault(user, Map.of());
            Map<String, PlayerPermission> currentNodes = CUSTOM_PLAYER_PERMISSIONS.getOrDefault(user, Map.of());
            for (Map.Entry<String, PlayerPermission> nodeEntry : desiredEntry.getValue().entrySet()) {
                PlayerPermission desiredPermission = nodeEntry.getValue();
                PlayerPermission oldPermission = oldNodes.get(nodeEntry.getKey());
                PlayerPermission currentPermission = currentNodes.get(nodeEntry.getKey());
                boolean unchanged = oldPermission != null
                        && Objects.equals(oldPermission.expiration(), desiredPermission.expiration())
                        && currentPermission != null
                        && Objects.equals(currentPermission.expiration(), desiredPermission.expiration());
                if (unchanged || grantPermissionToPlayer(
                        user, desiredPermission.node(), desiredPermission.expiration())) {
                    appliedPermissions.computeIfAbsent(user, _ -> new ConcurrentHashMap<>())
                            .put(nodeEntry.getKey(), desiredPermission);
                }
            }
        }

        YAML_PLAYER_PERMISSIONS.clear();
        appliedPermissions.forEach((user, nodes) ->
                YAML_PLAYER_PERMISSIONS.put(user, new ConcurrentHashMap<>(nodes)));

        LOGGER.info("Successfully loaded and registered all permissions and role models.");
    }

    /**
     * Checks whether the role is a permanent immutable root role.
     *
     * @param roleName role name
     * @return {@code true} for immutable system roles
     */
    public static boolean isImmutableRole(String roleName) {
        if (roleName == null || roleName.isBlank()) return false;
        String name = roleName.strip().toLowerCase(Locale.ROOT);
        return "user".equals(name) || "banned".equals(name);
    }

    /**
     * Ensures a connecting player receives their YAML-configured group if defined.
     *
     * @param player     connecting player
     * @param connection the client UDP connection
     */
    public static void syncPlayerLoginRole(IsoPlayer player, UdpConnection connection) {
        if (player == null && connection == null) {
            return;
        }

        String rawUsername = (player != null && player.getUsername() != null && !player.getUsername().isBlank())
                ? player.getUsername().strip()
                : (connection != null && connection.getUserName() != null && !connection.getUserName().isBlank()
                ? connection.getUserName().strip()
                : "");

        String lookupKey = normalizeIdentifier(rawUsername);

        long steamId = connection != null ? connection.getSteamId() : (player != null ? player.getSteamID() : 0L);
        String steamIdStr = steamId != 0L ? Long.toUnsignedString(steamId) : null;

        String targetGroup = null;
        if (!lookupKey.isEmpty() && CONFIGURED_USER_GROUPS.containsKey(lookupKey)) {
            targetGroup = CONFIGURED_USER_GROUPS.get(lookupKey);
        } else if (steamIdStr != null && CONFIGURED_USER_GROUPS.containsKey(steamIdStr)) {
            targetGroup = CONFIGURED_USER_GROUPS.get(steamIdStr);
        }

        if (targetGroup == null || targetGroup.isBlank()) {
            return;
        }

        Role role = findRole(targetGroup);
        if (role == null) {
            LOGGER.warn("Configured role '{}' for user '{}' not found in registered roles registry", targetGroup, rawUsername);
            return;
        }

        try {
            Role oldRole = player != null ? player.getRole() : (connection != null ? connection.getRole() : null);

            if (connection != null) {
                connection.setRole(role);
            }
            if (player != null) {
                player.setRole(role);
            }

            if (!rawUsername.isBlank()) {
                persistUserRole(rawUsername, role);
            }

            if (GameServer.server && player != null) {
                GameServer.sendPlayerExtraInfo(player, connection);
            }

            LOGGER.debug("Synchronized user '{}' (SteamID: {}) to role '{}'", rawUsername, steamIdStr, role.getName());
            if (oldRole != role) {
                EventManager.invoke(ServerEvents.PLAYER_ROLE_ASSIGNED, player, oldRole, role);
            }
        } catch (RuntimeException ex) {
            LOGGER.error("Failed to synchronize login role for user '{}'", rawUsername, ex);
        }
    }

    /**
     * Generates the default starter {@code permissions.yml} content on initial startup.
     *
     * @param path destination path on disk
     * @throws IOException if writing fails
     */
    private static void writeDefaultPermissionsFile(Path path) throws IOException {
        String defaultYaml = """
                # =========================================================================
                # Avrix Permissions & Roles Configuration File
                # =========================================================================
                #
                # FORMAT GUIDE:
                # GROUPS (Roles):
                #   - description: Human-readable explanation of the role.
                #   - color: Display color formatted as "R,G,B,A" (values 0-255).
                #   - prefix: Chat prefix displayed before player name (e.g. "[Admin] ").
                #   - suffix: Chat suffix displayed after player name (e.g. " ★").
                #   - permissions: List of custom string permission nodes.
                #                  Supports wildcards: "avrix.commands.*"
                #                  Supports negations (refusals): "-avrix.commands.stop"
                #   - capabilities: Standard Project Zomboid native capabilities.
                #                   Use "*" to grant all game capabilities.
                #   - parents: List of parent group names to inherit permissions from.
                #
                # USERS:
                #   - Keys can be player Usernames (e.g. "John Doe") OR 64-bit SteamIDs.
                #   - group: The primary role name assigned to the player.
                #   - permissions: Direct permanent permission nodes for this player.
                #   - temporaryPermissions: Map of permission node -> ISO-8601 expiration date.
                #                           Format: "YYYY-MM-DDTHH:MM:SSZ" (e.g. "2026-12-31T23:59:59Z")
                # =========================================================================
                
                groups:
                  admin:
                    description: "Server Administrator with full root access"
                    color: "255,50,50,255"
                    prefix: "Admin "
                    suffix: ""
                    permissions:
                      - "*"
                    capabilities:
                      - "*"
                    parents: []
                
                  moderator:
                    description: "Server Moderator - all capabilities except core server control"
                    color: "50,255,50,255"
                    prefix: "Mod "
                    suffix: ""
                    permissions: []
                    capabilities:
                      - "LoginOnServer"
                      - "PriorityLogin"
                      - "CantBeKickedIfTooLaggy"
                      - "ToggleGodModHimself"
                      - "ToggleGodModEveryone"
                      - "ToggleInvisibleHimself"
                      - "ToggleInvisibleEveryone"
                      - "ToggleNoclipHimself"
                      - "ToggleNoclipEveryone"
                      - "SeePlayersConnected"
                      - "TeleportToPlayer"
                      - "TeleportToCoordinates"
                      - "TeleportPlayerToAnotherPlayer"
                      - "SeePublicServerOptions"
                      - "CanOpenLockedDoors"
                      - "CanGoInsideSafehouses"
                      - "CanAlwaysJoinServer"
                      - "SeesInvisiblePlayers"
                      - "CanSeePlayersStats"
                      - "CanSeeMessageForAdmin"
                      - "PVPLogTool"
                      - "CantBeKickedByAnticheat"
                      - "CantBeBannedByAnticheat"
                      - "SeeWorldMap"
                      - "UIManagerProcessCommands"
                      - "MakeEventsAlarmGunshot"
                      - "StartStopRain"
                      - "AddItem"
                      - "AddXP"
                      - "SeeNetworkUsers"
                      - "CreateStory"
                      - "UseLootZed"
                      - "UseLootLog"
                      - "UseDebugContextMenu"
                      - "KickUser"
                      - "BanUnbanUser"
                      - "DisplayServerMessage"
                      - "AdminChat"
                      - "InspectPlayerInventory"
                    parents:
                      - "user"
                
                  priority:
                    description: "Player with server login queue priority"
                    color: "230,230,230,255"
                    prefix: "Priority "
                    suffix: ""
                    permissions: []
                    capabilities:
                      - "LoginOnServer"
                      - "PriorityLogin"
                      - "CantBeKickedIfTooLaggy"
                    parents:
                      - "user"
                
                  vip:
                    description: "VIP Supporter role example"
                    color: "255,215,0,255"
                    prefix: "VIP "
                    suffix: ""
                    permissions: []
                    capabilities:
                      - "PriorityLogin"
                    parents:
                      - "user"
                
                  user:
                    description: "Default Survivor"
                    color: "255,255,255,255"
                    prefix: ""
                    suffix: ""
                    permissions: []
                    capabilities:
                      - "LoginOnServer"
                    parents: []
                
                users: {}
                  # Add user-specific assignments here. Examples are intentionally commented out
                  # so a fresh installation never grants elevated access to a real player.
                  #
                  # "Example UserName":
                  #   group: "admin"
                  #   permissions:
                  #     - "example.custom.permission"
                  #
                  # 76561198012345678:
                  #   group: "vip"
                  #   temporaryPermissions:
                  #     "example.temporary.boost": "2026-12-31T23:59:59Z" # ISO 8601
                """;
        Path absolutePath = path.toAbsolutePath();
        Path parent = absolutePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temporary = Files.createTempFile(parent, "avrix-permissions-", ".tmp");
        try {
            Files.writeString(temporary, defaultYaml);
            try {
                Files.move(temporary, absolutePath,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, absolutePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /**
     * Programmatically creates and registers a new {@link ExtendedRole}.
     *
     * @param name                the unique role name
     * @param description         the human-readable role description
     * @param color               the display color
     * @param defaultCapabilities collection of native capabilities to grant
     * @param customPermissions   collection of custom permission strings
     * @return the newly registered {@link ExtendedRole}
     * @throws NullPointerException     if {@code name} is null
     * @throws IllegalArgumentException if {@code name} is blank or already exists
     */
    public static ExtendedRole createRole(String name,
                                          String description,
                                          Color color,
                                          Collection<Capability> defaultCapabilities,
                                          Collection<String> customPermissions) {
        Objects.requireNonNull(name, "Role name cannot be null");
        String cleanName = name.strip();
        if (cleanName.isEmpty()) {
            throw new IllegalArgumentException("Role name cannot be blank");
        }

        if (findRole(cleanName) != null) {
            throw new IllegalArgumentException("Role '%s' already exists".formatted(cleanName));
        }

        ExtendedRole role = new ExtendedRole(
                cleanName,
                description != null ? description : "",
                color != null ? color : Color.white
        );

        boolean bannedRole = "banned".equalsIgnoreCase(cleanName);
        if (!bannedRole) {
            role.addCapability(Capability.LoginOnServer);
        }
        if (defaultCapabilities != null) {
            for (Capability capability : defaultCapabilities) {
                if (capability != null && capability != Capability.None
                        && !(bannedRole && capability == Capability.LoginOnServer)) {
                    role.addCapability(capability);
                }
            }
        }

        if (customPermissions != null) {
            for (String permission : customPermissions) {
                if (permission != null && !permission.isBlank()) {
                    role.addPermission(permission);
                }
            }
        }

        if (isImmutableRole(cleanName)) {
            role.setReadOnly();
        }

        List<Role> allRoles = Roles.getRoles();
        if (!allRoles.contains(role)) {
            int position = allRoles.isEmpty() ? 0 : allRoles.getFirst().getPosition() - 1;
            role.setPosition(position);
            allRoles.add(role);
            allRoles.sort(Comparator.comparingInt(Role::getPosition));
        }

        syncRolesStaticField(cleanName, role);
        saveRoleToDatabase(role);
        return role;
    }

    /**
     * Programmatically creates and registers a new {@link ExtendedRole} with varargs permissions.
     *
     * @param name                the unique role name
     * @param description         the role description
     * @param color               the display color
     * @param defaultCapabilities collection of native capabilities
     * @param customPermissions   varargs of permission strings
     * @return the newly registered {@link ExtendedRole}
     */
    public static ExtendedRole createRole(String name,
                                          String description,
                                          Color color,
                                          Collection<Capability> defaultCapabilities,
                                          String... customPermissions) {
        return createRole(
                name,
                description,
                color,
                defaultCapabilities,
                customPermissions != null ? Arrays.asList(customPermissions) : Collections.emptyList()
        );
    }

    /**
     * Returns the dedicated permissions database connection.
     *
     * @return active connection or {@code null}
     */
    private static Connection getDatabaseConnection() {
        return DatabasePermissionsHelper.getConnection();
    }

    /**
     * Persists a role definition to the SQLite database and synchronizes it across connected clients if network is ready.
     *
     * @param role the role instance to persist
     */
    public static void saveRoleToDatabase(Role role) {
        if (role == null) return;
        if (GameServer.server && ServerWorldDatabase.instance != null) {
            try {
                ServerWorldDatabase.instance.saveRole(role);
                Roles.save();

                if (GameServer.udpEngine != null && GameServer.udpEngine.connections != null) {
                    INetworkPacket.sendToAll(PacketTypes.PacketType.Roles, new Object[0]);
                }
            } catch (Exception ex) {
                LOGGER.error("Failed to persist role '{}' into database", role.getName(), ex);
            }
        }
    }

    /**
     * Persists all registered roles and broadcasts a single roles update packet.
     */
    private static void persistAllRoles() {
        if (!GameServer.server || ServerWorldDatabase.instance == null) {
            return;
        }
        try {
            Roles.save();
            if (GameServer.udpEngine != null && GameServer.udpEngine.connections != null) {
                INetworkPacket.sendToAll(PacketTypes.PacketType.Roles, new Object[0]);
            }
        } catch (Exception ex) {
            LOGGER.error("Failed to persist the role registry", ex);
        }
    }

    /**
     * Deletes a custom role from memory, database, and connected clients.
     *
     * @param roleName  the unique name of the role to delete
     * @param adminName the username of the administrator issuing the deletion
     * @return {@code true} if deleted successfully; {@code false} if not found or read-only
     */
    public static boolean deleteRole(String roleName, String adminName) {
        if (roleName == null || roleName.isBlank()) return false;
        if (isImmutableRole(roleName)) {
            LOGGER.warn("Attempted to delete immutable system role: '{}'", roleName);
            return false;
        }

        Role role = findRole(roleName);
        if (role == null || role.isReadOnly()) {
            return false;
        }

        if (GameServer.server) {
            Roles.deleteRole(role.getName(), adminName != null ? adminName : "Server");
        } else {
            List<Role> allRoles = Roles.getRoles();
            if (allRoles != null) {
                allRoles.remove(role);
            }
        }
        return true;
    }

    /**
     * Assigns a role to an online player entity, updating active network connections and SQLite database.
     *
     * @param player   the online player entity
     * @param roleName the name of the role to assign
     * @return {@code true} if assigned successfully; {@code false} if not found
     */
    public static boolean assignRole(IsoPlayer player, String roleName) {
        if (player == null || roleName == null || roleName.isBlank()) return false;
        Role role = findRole(roleName);
        if (role == null) return false;

        Role oldRole = player.getRole();
        String username = player.getUsername();
        boolean changed = canUseVanillaRoleChange(username) && applyVanillaRoleChange(username, role);
        if (!changed) {
            player.setRole(role);
            updateConnectionRole(username, role);
            if (username != null && !username.isBlank()) {
                persistUserRole(username, role);
            }
            changed = true;
        }

        clearTemporaryRole(username, getSteamID(player));
        if (oldRole != role) {
            LOGGER.info("Assigned role '{}' to '{}'", role.getName(), username);
            EventManager.invoke(ServerEvents.PLAYER_ROLE_ASSIGNED, player, oldRole, role);
        }
        return changed;
    }

    /**
     * Assigns a role to a specific active network connection and its associated player entities.
     *
     * @param connection the target client network connection
     * @param roleName   the name of the role to assign
     * @return {@code true} if assigned successfully; {@code false} if not found
     */
    public static boolean assignRole(UdpConnection connection, String roleName) {
        if (connection == null || roleName == null || roleName.isBlank()) return false;
        Role role = findRole(roleName);
        if (role == null) return false;

        boolean changed = false;
        if (connection.players != null) {
            for (IsoPlayer player : connection.players) {
                if (player == null) continue;
                String username = player.getUsername();
                Role oldRole = player.getRole();
                boolean applied = canUseVanillaRoleChange(username) && applyVanillaRoleChange(username, role);
                if (!applied) {
                    player.setRole(role);
                    if (username != null && !username.isBlank()) {
                        persistUserRole(username, role);
                    }
                }
                clearTemporaryRole(username, getSteamID(player));
                if (oldRole != role) {
                    LOGGER.info("Assigned role '{}' to '{}'", role.getName(), username);
                    EventManager.invoke(ServerEvents.PLAYER_ROLE_ASSIGNED, player, oldRole, role);
                }
                changed = true;
            }
        }

        if (changed) {
            connection.setRole(role);
            return true;
        }

        connection.setRole(role);
        String username = connection.getUserName();
        long steamId = connection.getSteamId();
        String steamIdStr = steamId != 0L ? Long.toUnsignedString(steamId) : null;
        boolean persisted = username != null && !username.isBlank()
                && (canUseVanillaRoleChange(username)
                ? applyVanillaRoleChange(username, role)
                : persistUserRole(username, role));
        clearTemporaryRole(username, steamIdStr);
        return persisted || username == null || username.isBlank();
    }

    /**
     * Assigns a role to a player identified by Username (including spaces) or 64-bit SteamID.
     *
     * @param identifier in-game username or SteamID64
     * @param roleName   the target role name
     * @return {@code true} if assigned; {@code false} if not found or persistence failed
     */
    public static boolean assignRole(String identifier, String roleName) {
        if (identifier == null || identifier.isBlank() || roleName == null || roleName.isBlank()) return false;

        String clean = normalizeIdentifier(identifier);
        Role role = findRole(roleName);
        if (role == null) return false;

        if (GameServer.server) {
            if (GameServer.Players != null) {
                for (int i = 0; i < GameServer.Players.size(); i++) {
                    IsoPlayer player = GameServer.Players.get(i);
                    if (player != null) {
                        String pName = player.getUsername() != null ? normalizeIdentifier(player.getUsername()) : "";
                        String pSteamId = getSteamID(player);
                        if (clean.equalsIgnoreCase(pName) || clean.equals(pSteamId)) {
                            return assignRole(player, role.getName());
                        }
                    }
                }
            }

            boolean persisted = persistUserRole(clean, role);
            if (persisted) {
                LOGGER.info("Assigned role '{}' to '{}'", role.getName(), clean);
                clearTemporaryRole(clean, clean);
            }
            return persisted;
        }
        return false;
    }

    /**
     * Returns whether the vanilla role-change pipeline can be used for the given user.
     *
     * @param username target username
     * @return {@code true} when the server database is available
     */
    private static boolean canUseVanillaRoleChange(String username) {
        return GameServer.server
                && GameServer.udpEngine != null
                && ServerWorldDatabase.instance != null
                && username != null
                && !username.isBlank();
    }

    /**
     * Applies a role change through the vanilla pipeline to preserve client and gameplay side effects.
     *
     * @param username target username
     * @param role     target role
     * @return {@code true} if the change was applied
     */
    private static boolean applyVanillaRoleChange(String username, Role role) {
        try {
            String result = GameServer.changeRole("Avrix", null, username, role.getName());
            IsoPlayer changed = GameServer.getPlayerByUserName(username);
            boolean applied = changed != null
                    && changed.getRole() != null
                    && changed.getRole().getName().equalsIgnoreCase(role.getName());
            if (!applied) {
                LOGGER.warn("Vanilla role change for '{}' failed: {}", username, result);
            }
            return applied;
        } catch (SQLException ex) {
            LOGGER.error("Failed to change role for '{}' through the vanilla pipeline", username, ex);
            return false;
        }
    }

    /**
     * Updates the network connection that owns the given username when available.
     *
     * @param username target username
     * @param role     assigned role
     */
    private static void updateConnectionRole(String username, Role role) {
        if (!GameServer.server || GameServer.udpEngine == null || username == null || username.isBlank()) {
            return;
        }
        for (int i = 0; i < GameServer.udpEngine.connections.size(); i++) {
            UdpConnection connection = GameServer.udpEngine.connections.get(i);
            if (connection != null && connection.hasPlayer(username)) {
                connection.setRole(role);
                return;
            }
        }
    }

    /**
     * Removes temporary role tracking for the given username and SteamID.
     *
     * @param username username or {@code null}
     * @param steamId  SteamID64 or {@code null}
     */
    private static void clearTemporaryRole(String username, String steamId) {
        if (username != null && !username.isBlank()) {
            TEMPORARY_USER_ROLES.remove(normalizeIdentifier(username));
        }
        if (steamId != null && !steamId.isBlank()) {
            TEMPORARY_USER_ROLES.remove(normalizeIdentifier(steamId));
        }
    }

    /**
     * Finds a role in the registry by name using case-insensitive matching.
     *
     * @param roleName role identifier name
     * @return matching role or null if not found
     */
    private static Role findRole(String roleName) {
        if (roleName == null || roleName.isBlank()) return null;
        String clean = normalizeIdentifier(roleName);

        List<Role> allRoles = Roles.getRoles();
        if (allRoles != null) {
            for (Role r : allRoles) {
                if (r != null && r.getName() != null && normalizeIdentifier(r.getName()).equalsIgnoreCase(clean)) {
                    return r;
                }
            }
        }
        return Roles.getRole(roleName);
    }

    /**
     * Helper delegating role association persistence to {@link ServerWorldDatabase#setRole(String, Role)}.
     *
     * @param username the player's username
     * @param role     the target role
     * @return {@code true} if the role was written to the whitelist
     */
    private static boolean persistUserRole(String username, Role role) {
        if (username == null || username.isBlank() || role == null) return false;
        if (!GameServer.server || ServerWorldDatabase.instance == null) return false;
        try {
            String target = username;
            if (!ServerWorldDatabase.instance.containsUser(target)) {
                Connection conn = getDatabaseConnection();
                if (conn != null) {
                    String resolved = DatabasePermissionsHelper.findUsernameByIdentifier(
                            conn, target, Core.gameSaveWorld);
                    if (resolved != null && !resolved.isBlank()) {
                        target = resolved;
                    }
                }
            }
            if (!ServerWorldDatabase.instance.containsUser(target)) {
                return false;
            }
            ServerWorldDatabase.instance.setRole(target, role);
            return true;
        } catch (Exception ex) {
            DebugType.Multiplayer.printException(
                    ex,
                    "Failed to persist role for user: " + username,
                    LogSeverity.Error
            );
            return false;
        }
    }

    /**
     * Grants a permanent custom permission node directly to a player's username or SteamID64.
     *
     * @param identifier     the player username or SteamID64
     * @param permissionNode the permission string (can start with '-' for explicit negation)
     * @return {@code true} if granted, {@code false} if parameters are invalid
     */
    public static boolean grantPermissionToPlayer(String identifier, String permissionNode) {
        return grantPermissionToPlayer(identifier, permissionNode, (Instant) null);
    }

    /**
     * Grants a temporary custom permission node expiring after a specific relative duration.
     *
     * @param identifier     the player username or SteamID64
     * @param permissionNode the permission string
     * @param duration       duration amount
     * @param unit           time unit
     * @return {@code true} if granted, {@code false} if parameters are invalid
     */
    public static boolean grantPermissionToPlayer(String identifier, String permissionNode, long duration, TimeUnit unit) {
        if (duration <= 0 || unit == null) {
            return false;
        }
        return grantPermissionToPlayer(identifier, permissionNode, Instant.now().plusMillis(unit.toMillis(duration)));
    }

    /**
     * Grants a temporary custom permission node expiring at a specific absolute timestamp.
     *
     * @param identifier     the player username or SteamID64
     * @param permissionNode the permission string
     * @param expirationDate absolute expiration instant (or {@code null} for permanent)
     * @return {@code true} if granted, {@code false} if parameters are invalid
     */
    public static boolean grantPermissionToPlayer(String identifier, String permissionNode, Instant expirationDate) {
        if (identifier == null || identifier.isBlank() || permissionNode == null || permissionNode.isBlank()
                || (expirationDate != null && !expirationDate.isAfter(Instant.now()))) {
            return false;
        }

        String userKey = normalizeIdentifier(identifier);
        String nodeKey = permissionNode.strip().toLowerCase(Locale.ROOT);
        PlayerPermission record = new PlayerPermission(nodeKey, expirationDate);

        Connection conn = getDatabaseConnection();
        if (conn != null && !DatabasePermissionsHelper.savePlayerPermission(
                conn, userKey, nodeKey, expirationDate)) {
            LOGGER.error("Failed to persist permission '{}' for user '{}' into SQLite", nodeKey, userKey);
            return false;
        }

        CUSTOM_PLAYER_PERMISSIONS
                .computeIfAbsent(userKey, _ -> new ConcurrentHashMap<>())
                .put(nodeKey, record);

        LOGGER.info("Granted permission '{}' to '{}' (expires: {})", nodeKey, userKey, expirationDate);
        EventManager.invoke(ServerEvents.PERMISSION_GRANTED, identifier, permissionNode, expirationDate);
        return true;
    }

    /**
     * Revokes a custom permission node from a player's username or SteamID64.
     *
     * @param identifier     the player username or SteamID64
     * @param permissionNode the permission string to revoke
     * @return {@code true} if revoked; {@code false} if absent or invalid
     */
    public static boolean revokePermissionFromPlayer(String identifier, String permissionNode) {
        if (identifier == null || identifier.isBlank() || permissionNode == null || permissionNode.isBlank()) {
            return false;
        }

        String userKey = normalizeIdentifier(identifier);
        String nodeKey = permissionNode.strip().toLowerCase(Locale.ROOT);

        Map<String, PlayerPermission> userNodes = CUSTOM_PLAYER_PERMISSIONS.get(userKey);
        if (userNodes == null || !userNodes.containsKey(nodeKey)) {
            return false;
        }

        Connection conn = getDatabaseConnection();
        if (conn != null && !DatabasePermissionsHelper.deletePlayerPermission(conn, userKey, nodeKey)) {
            LOGGER.error("Failed to delete permission '{}' for user '{}' from SQLite", nodeKey, userKey);
            return false;
        }

        userNodes.remove(nodeKey);
        Map<String, PlayerPermission> yamlNodes = YAML_PLAYER_PERMISSIONS.get(userKey);
        if (yamlNodes != null) {
            yamlNodes.remove(nodeKey);
            if (yamlNodes.isEmpty()) {
                YAML_PLAYER_PERMISSIONS.remove(userKey, yamlNodes);
            }
        }
        LOGGER.info("Revoked permission '{}' from '{}'", nodeKey, userKey);
        EventManager.invoke(ServerEvents.PERMISSION_REVOKED, identifier, permissionNode);
        return true;
    }

    /**
     * Loads all persistent player permissions from SQLite into RAM cache.
     *
     * @param fallbackConnection connection used when the dedicated one is unavailable
     */
    public static void loadDatabasePlayerPermissions(Connection fallbackConnection) {
        // During a fresh-database migration YAML is applied before the schema hook runs.
        // Preserve that declarative state instead of wiping it with an empty database load.
        if (YAML_PLAYER_PERMISSIONS.isEmpty() && CONFIGURED_USER_GROUPS.isEmpty()) {
            CUSTOM_PLAYER_PERMISSIONS.clear();
        }
        Connection connection = getDatabaseConnection();
        DatabasePermissionsHelper.loadAllPlayerPermissions(
                connection != null ? connection : fallbackConnection,
                CUSTOM_PLAYER_PERMISSIONS
        );
    }

    /**
     * Loads all persistent temporary role assignments from SQLite into RAM cache.
     *
     * @param fallbackConnection connection used when the dedicated one is unavailable
     */
    public static void loadDatabaseTemporaryRoles(Connection fallbackConnection) {
        TEMPORARY_USER_ROLES.clear();
        Connection connection = getDatabaseConnection();
        DatabasePermissionsHelper.loadAllTemporaryRoles(
                connection != null ? connection : fallbackConnection,
                TEMPORARY_USER_ROLES
        );
    }

    /**
     * Sends an in-game chat message to an online player when their permission expires.
     */
    private static void notifyPlayerExpired(String identifier, String node) {
        if (!GameServer.server || GameServer.Players == null) {
            return;
        }

        for (int i = 0; i < GameServer.Players.size(); i++) {
            IsoPlayer player = GameServer.Players.get(i);
            if (player != null && player.getUsername() != null) {
                String pName = normalizeIdentifier(player.getUsername());
                String pSteam = getSteamID(player);

                if (identifier.equalsIgnoreCase(pName) || identifier.equals(pSteam)) {
                    UdpConnection connection = GameServer.getConnectionFromPlayer(player);
                    if (connection != null && ChatServer.getInstance() != null) {
                        ChatServer.getInstance().sendMessageToServerChat(
                                connection,
                                String.format("Your temporary permission '%s' has expired.", node)
                        );
                    }
                    break;
                }
            }
        }
    }

    /**
     * Clears all personal permissions assigned directly to a player.
     *
     * @param identifier the target username or SteamID64
     */
    public static void clearPlayerPermissions(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return;
        }

        String userKey = normalizeIdentifier(identifier);
        Map<String, PlayerPermission> nodes = CUSTOM_PLAYER_PERMISSIONS.get(userKey);
        if (nodes == null || nodes.isEmpty()) {
            return;
        }

        Connection conn = getDatabaseConnection();
        if (conn != null && !DatabasePermissionsHelper.deleteAllPlayerPermissions(conn, userKey)) {
            LOGGER.error("Failed to clear persistent permissions for user '{}'", userKey);
            return;
        }

        Set<String> removedNodes = new HashSet<>(nodes.keySet());
        CUSTOM_PLAYER_PERMISSIONS.remove(userKey);
        YAML_PLAYER_PERMISSIONS.remove(userKey);
        LOGGER.info("Cleared {} permission(s) for '{}'", removedNodes.size(), userKey);
        for (String node : removedNodes) {
            EventManager.invoke(ServerEvents.PERMISSION_REVOKED, userKey, node);
        }
    }

    /**
     * Retrieves an unmodifiable snapshot view of active permission string nodes assigned to a player.
     * Automatically filters out and purges expired entries.
     *
     * @param identifier the target username or SteamID64
     * @return an unmodifiable set of active permission nodes
     */
    public static Set<String> getPlayerPermissions(String identifier) {
        return getPlayerPermissionEntries(identifier).stream()
                .filter(permission -> !permission.isNegated())
                .map(PlayerPermission::node)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Retrieves an unmodifiable collection of active {@link PlayerPermission} domain records for a player.
     *
     * @param identifier the target username or SteamID64
     * @return collection of player permissions with expiration metadata
     */
    public static Collection<PlayerPermission> getPlayerPermissionEntries(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return Collections.emptyList();
        }

        String userKey = normalizeIdentifier(identifier);
        Map<String, PlayerPermission> map = CUSTOM_PLAYER_PERMISSIONS.get(userKey);
        if (map == null || map.isEmpty()) {
            return Collections.emptyList();
        }

        List<PlayerPermission> active = new ArrayList<>();
        for (PlayerPermission permission : map.values()) {
            if (permission == null) {
                continue;
            }
            if (permission.isExpired()) {
                PENDING_EXPIRY_TASKS.offer(() -> expirePermissionNode(userKey, permission.node()));
            } else {
                active.add(permission);
            }
        }
        return Collections.unmodifiableCollection(active);
    }

    /**
     * Evaluates whether a player possesses a specific permission node or native capability.
     * Supports negative nodes (e.g. {@code "-avrix.admin.stop"}), wildcards, and checks both Username and SteamID64.
     *
     * @param player         the target player
     * @param permissionNode the permission node or capability name
     * @return {@code true} if authorized; {@code false} otherwise
     */
    public static boolean hasPermission(IsoPlayer player, String permissionNode) {
        if (player == null || permissionNode == null || permissionNode.isBlank()) {
            return false;
        }

        if (Role.isUsingDebugMode()) {
            return true;
        }

        String target = permissionNode.strip().toLowerCase(Locale.ROOT);
        String username = player.getUsername() != null ? normalizeIdentifier(player.getUsername()) : "";
        String steamId = getSteamID(player);

        Role role = player.getRole();
        if (role == null) {
            role = Roles.getDefaultForUser();
        }

        // Explicit denials always win, regardless of the identity source.
        if (role instanceof ExtendedRole extendedRole && extendedRole.deniesPermission(target)) return false;
        if (!username.isEmpty() && checkDirectPermissions(username, target, true)) return false;
        if (steamId != null && checkDirectPermissions(steamId, target, true)) return false;

        // Positive direct grants.
        if (!username.isEmpty() && checkDirectPermissions(username, target, false)) return true;
        if (steamId != null && checkDirectPermissions(steamId, target, false)) return true;

        return hasPermission(role, permissionNode);
    }

    /**
     * Internal helper evaluating direct personal permissions mapped to an identifier.
     *
     * @param key           identifier key
     * @param target        target permission node
     * @param negationCheck whether checking for negative permissions
     * @return {@code true} if matched
     */
    private static boolean checkDirectPermissions(String key, String target, boolean negationCheck) {
        Map<String, PlayerPermission> nodes = CUSTOM_PLAYER_PERMISSIONS.get(key);
        if (nodes == null || nodes.isEmpty()) return false;

        if (negationCheck) {
            for (PlayerPermission perm : nodes.values()) {
                if (perm != null && !perm.isExpired() && perm.isNegated()
                        && evaluateWildcards(Set.of(perm.cleanNode()), target)) {
                    return true;
                }
            }
            return false;
        }

        Set<String> positive = nodes.values().stream()
                .filter(p -> p != null && !p.isExpired() && !p.isNegated())
                .map(PlayerPermission::node)
                .collect(Collectors.toSet());

        return evaluateWildcards(positive, target);
    }

    /**
     * Evaluates whether an active UDP connection possesses a specific permission node.
     *
     * @param connection     the network connection
     * @param permissionNode the permission node
     * @return {@code true} if authorized
     */
    public static boolean hasPermission(UdpConnection connection, String permissionNode) {
        if (connection == null || permissionNode == null || permissionNode.isBlank()) {
            return false;
        }

        if (Role.isUsingDebugMode()) {
            return true;
        }

        String target = permissionNode.strip().toLowerCase(Locale.ROOT);
        if (connection.players != null) {
            for (IsoPlayer player : connection.players) {
                if (player != null && hasPermission(player, permissionNode)) {
                    return true;
                }
            }
        }

        String username = connection.getUserName() != null ? normalizeIdentifier(connection.getUserName()) : "";
        long steamId = connection.getSteamId();
        String steamIdStr = steamId != 0L ? Long.toUnsignedString(steamId) : null;

        if (!username.isEmpty() && checkDirectPermissions(username, target, true)) return false;
        if (steamIdStr != null && checkDirectPermissions(steamIdStr, target, true)) return false;
        if (!username.isEmpty() && checkDirectPermissions(username, target, false)) return true;
        if (steamIdStr != null && checkDirectPermissions(steamIdStr, target, false)) return true;

        Role role = connection.getRole();
        if (role == null) {
            role = Roles.getDefaultForUser();
        }

        return hasPermission(role, permissionNode);
    }

    /**
     * Evaluates whether a role possesses a specific permission node or capability.
     *
     * @param role           the role to evaluate
     * @param permissionNode the permission node or capability name
     * @return {@code true} if authorized
     */
    public static boolean hasPermission(Role role, String permissionNode) {
        if (role == null || permissionNode == null || permissionNode.isBlank()) return false;
        if (Role.isUsingDebugMode()) return true;

        String cleanNode = permissionNode.strip();
        if (role instanceof ExtendedRole er && er.hasPermission(cleanNode)) {
            return true;
        }

        Capability cap = findStandardCapability(cleanNode);
        return cap != null && role.hasCapability(cap);
    }

    /**
     * Evaluates whether an in-game player possesses a standard Project Zomboid {@link Capability}.
     *
     * @param player     the target player
     * @param capability the native capability to check
     * @return {@code true} if authorized
     */
    public static boolean hasCapability(IsoPlayer player, Capability capability) {
        if (player == null || capability == null || capability == Capability.None) return false;
        return Role.hasCapability(player, capability);
    }

    /**
     * Evaluates whether an active UDP network connection possesses a standard Project Zomboid {@link Capability}.
     *
     * @param connection the target network connection
     * @param capability the native capability to check
     * @return {@code true} if authorized
     */
    public static boolean hasCapability(UdpConnection connection, Capability capability) {
        if (connection == null || capability == null || capability == Capability.None) return false;
        if (Role.isUsingDebugMode()) return true;

        Role role = connection.getRole();
        if (role == null) {
            role = Roles.getDefaultForUser();
        }

        return role != null && role.hasCapability(capability);
    }

    /**
     * Evaluates whether a {@link Role} possesses a standard Project Zomboid {@link Capability}.
     *
     * @param role       the target role
     * @param capability the native capability to check
     * @return {@code true} if authorized
     */
    public static boolean hasCapability(Role role, Capability capability) {
        if (role == null || capability == null || capability == Capability.None) return false;
        return role.hasCapability(capability);
    }

    /**
     * Evaluates whether a player possesses a capability by their username.
     *
     * @param username   the target username
     * @param capability the native capability
     * @return {@code true} if authorized
     */
    public static boolean hasCapability(String username, Capability capability) {
        if (username == null || username.isBlank() || capability == null || capability == Capability.None) {
            return false;
        }

        if (Role.isUsingDebugMode()) return true;

        String clean = normalizeIdentifier(username);

        if (GameServer.server && GameServer.Players != null) {
            for (int i = 0; i < GameServer.Players.size(); i++) {
                IsoPlayer p = GameServer.Players.get(i);
                if (p != null && p.getUsername() != null && clean.equalsIgnoreCase(p.getUsername())) {
                    return hasCapability(p, capability);
                }
            }
        }

        if (ServerWorldDatabase.instance == null) {
            return false;
        }

        Connection conn = getDatabaseConnection();
        if (conn != null) {
            String resolved = DatabasePermissionsHelper.findUsernameByIdentifier(
                    conn, clean, Core.gameSaveWorld);
            if (resolved != null && !resolved.isBlank()) {
                clean = resolved;
            }
        }

        Role role = ServerWorldDatabase.instance.getUserRoleNameByUsername(clean);
        return role != null && role.hasCapability(capability);
    }

    /**
     * Resolves metadata key configured on the player's assigned {@link ExtendedRole}.
     *
     * @param player       target player
     * @param key          metadata key
     * @param defaultValue fallback value
     * @return metadata string value or defaultValue
     */
    public static String getRoleMeta(IsoPlayer player, String key, String defaultValue) {
        if (player != null && player.getRole() instanceof ExtendedRole extendedRole) {
            String val = extendedRole.getMeta(key);
            if (val != null) {
                return val;
            }
        }
        return defaultValue;
    }

    /**
     * Resolves the chat prefix for a player, preferring a YAML personal override over the role prefix.
     *
     * @param player       target player
     * @param defaultValue fallback value
     * @return resolved prefix
     */
    public static String getChatPrefix(IsoPlayer player, String defaultValue) {
        String personal = getPersonalStyle(player, CONFIGURED_USER_PREFIXES);
        if (personal != null) {
            return personal;
        }
        if (player != null && player.getRole() instanceof ExtendedRole extendedRole) {
            return extendedRole.getPrefix();
        }
        return defaultValue;
    }

    /**
     * Resolves the chat suffix for a player, preferring a YAML personal override over the role suffix.
     *
     * @param player       target player
     * @param defaultValue fallback value
     * @return resolved suffix
     */
    public static String getChatSuffix(IsoPlayer player, String defaultValue) {
        String personal = getPersonalStyle(player, CONFIGURED_USER_SUFFIXES);
        if (personal != null) {
            return personal;
        }
        if (player != null && player.getRole() instanceof ExtendedRole extendedRole) {
            return extendedRole.getSuffix();
        }
        return defaultValue;
    }

    /**
     * Looks up a personal style override by username or SteamID64.
     *
     * @param player  target player
     * @param styles  style map
     * @return override value or {@code null}
     */
    private static String getPersonalStyle(IsoPlayer player, Map<String, String> styles) {
        if (player == null || styles.isEmpty()) {
            return null;
        }
        if (player.getUsername() != null) {
            String value = styles.get(normalizeIdentifier(player.getUsername()));
            if (value != null) {
                return value;
            }
        }
        String steamId = getSteamID(player);
        return steamId != null ? styles.get(steamId) : null;
    }

    /**
     * Extracts and stringifies the 64-bit numeric SteamID from an in-game player entity.
     *
     * @param player the player entity
     * @return numeric SteamID string or {@code null} if not playing in Steam mode
     */
    private static String getSteamID(IsoPlayer player) {
        if (player == null) return null;
        long steamId = player.getSteamID();
        if (steamId != 0L) {
            return Long.toUnsignedString(steamId);
        }
        return null;
    }

    /**
     * Matches a target node against a set of granted patterns supporting hierarchic wildcards.
     *
     * @param grantedNodes the set of granted permission strings
     * @param targetNode   the normalized target permission node
     * @return {@code true} if matches
     */
    private static boolean evaluateWildcards(Set<String> grantedNodes, String targetNode) {
        if (grantedNodes.contains("*") || grantedNodes.contains(targetNode)
                || grantedNodes.contains(targetNode + ".*")) {
            return true;
        }

        String[] segments = targetNode.split("\\.");
        StringBuilder prefixBuilder = new StringBuilder();

        for (int i = 0; i < segments.length - 1; i++) {
            prefixBuilder.append(segments[i]).append(".");
            if (grantedNodes.contains(prefixBuilder + "*")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Resolves a {@link Capability} enum value ignoring case.
     *
     * @param name the capability name
     * @return matching capability or null
     */
    private static Capability findStandardCapability(String name) {
        for (Capability capability : Capability.values()) {
            if (capability.name().equalsIgnoreCase(name)) {
                return capability;
            }
        }
        return null;
    }

    /**
     * Parses a string representation of RGBA color values into a {@link Color} object.
     *
     * @param rgba formatted RGBA string (e.g., {@code "255,50,50,255"})
     * @return parsed Project Zomboid {@link Color}
     */
    private static Color parseColor(String rgba) {
        if (rgba == null || rgba.isBlank()) return Color.white;
        try {
            String[] parts = rgba.split(",");
            if (parts.length < 3) {
                LOGGER.warn("Invalid color '{}'; expected R,G,B[,A]", rgba);
                return Color.white;
            }
            int r = Integer.parseInt(parts[0].trim());
            int g = Integer.parseInt(parts[1].trim());
            int b = Integer.parseInt(parts[2].trim());
            int a = parts.length > 3 ? Integer.parseInt(parts[3].trim()) : 255;
            if (isOutsideByteRange(r) || isOutsideByteRange(g)
                    || isOutsideByteRange(b) || isOutsideByteRange(a)) {
                LOGGER.warn("Color '{}' has values outside 0-255; values will be clamped", rgba);
            }
            return new Color(clampColor(r) / 255.0f, clampColor(g) / 255.0f,
                    clampColor(b) / 255.0f, clampColor(a) / 255.0f);
        } catch (Exception _) {
            LOGGER.warn("Invalid color '{}'; falling back to white", rgba);
            return Color.white;
        }
    }

    /**
     * Checks whether a color component is outside the byte range.
     *
     * @param value color component
     * @return {@code true} if the value needs clamping
     */
    private static boolean isOutsideByteRange(int value) {
        return value < 0 || value > 255;
    }

    /**
     * Clamps a color component to the 0-255 range.
     *
     * @param value color component
     * @return clamped value
     */
    private static int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
    }

    /**
     * Cleans enclosing quotes, strips whitespace, and normalizes an identifier to lowercase.
     *
     * @param raw raw input identifier
     * @return normalized lowercase identifier
     */
    public static String normalizeIdentifier(String raw) {
        if (raw == null) return "";
        String s = raw.strip();
        if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) {
            if (s.length() >= 2) {
                s = s.substring(1, s.length() - 1).strip();
            }
        }
        return s.toLowerCase(Locale.ROOT);
    }

    /**
     * Synchronizes static default role pointers in {@link Roles} when standard roles are overridden.
     *
     * @param roleName role identifier name
     * @param newRole  the updated ExtendedRole instance
     */
    private static void syncRolesStaticField(String roleName, ExtendedRole newRole) {
        if (roleName == null) return;
        switch (roleName.toLowerCase(Locale.ROOT)) {
            case "admin" -> updateRolesField("defaultForAdmin", newRole);
            case "user" -> {
                updateRolesField("defaultForUser", newRole);
                updateRolesField("defaultForNewUser", newRole);
            }
            case "moderator" -> updateRolesField("defaultForModerator", newRole);
            case "banned" -> updateRolesField("defaultForBanned", newRole);
            case "priority", "priorityuser" -> updateRolesField("defaultForPriorityUser", newRole);
            case "observer" -> updateRolesField("defaultForObserver", newRole);
            case "gm" -> {
                updateRolesField("defaultForGM", newRole);
                updateRolesField("defaultForOverseer", newRole);
            }
            default -> {
            }
        }
    }

    /**
     * Reflectively updates a static role field in {@link Roles}.
     *
     * @param fieldName the static field name
     * @param role      the role instance
     */
    private static void updateRolesField(String fieldName, Role role) {
        try {
            Field field = Roles.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(null, role);
        } catch (Exception e) {
            DebugType.General.warn("Failed to synchronize Roles." + fieldName + ": " + e.getMessage());
        }
    }
}