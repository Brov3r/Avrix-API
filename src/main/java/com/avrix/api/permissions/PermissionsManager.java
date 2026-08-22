package com.avrix.api.permissions;

import com.avrix.api.events.EventManager;
import com.avrix.api.events.ServerEvents;
import com.avrix.utils.Constants;
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
import zombie.core.raknet.UdpConnection;
import zombie.debug.DebugType;
import zombie.debug.LogSeverity;
import zombie.network.GameServer;
import zombie.network.PacketTypes;
import zombie.network.ServerWorldDatabase;
import zombie.network.packets.INetworkPacket;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Thread-safe central domain service managing role lifecycles, SQLite persistence,
 * declarative {@code permissions.yml} configuration loading,
 * SteamID64 identity mapping, and fine-grained authorization evaluations.
 */
public final class PermissionsManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(PermissionsManager.class);
    private static final String PERMISSIONS_FILE = "permissions.yml";

    /**
     * Map of normalized username/SteamID -> configured primary group name from permissions.yml.
     */
    private static final Map<String, String> CONFIGURED_USER_GROUPS = new ConcurrentHashMap<>();

    /**
     * In-memory storage for user-specific custom permissions mapped by normalized identifier (username or SteamID64).
     * Maps: Normalized Identifier -> (Normalized Node -> PlayerPermission record).
     */
    private static final Map<String, Map<String, PlayerPermission>> CUSTOM_PLAYER_PERMISSIONS = new ConcurrentHashMap<>();

    private PermissionsManager() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Loads or creates the {@code permissions.yml} configuration from the default plugin directory.
     * If the configuration file does not exist, a template structure will be created automatically.
     */
    public static void loadPermissionsConfig() {
        String rootDir = Objects.requireNonNullElse(Constants.PLUGINS_FOLDER_NAME, "plugins");
        Path configPath = Path.of(rootDir, "avrix-api", PERMISSIONS_FILE).toAbsolutePath().normalize();
        loadPermissionsConfig(configPath);
    }

    /**
     * Loads or creates the {@code permissions.yml} configuration from the specified target path.
     *
     * @param configPath absolute or relative path to the permissions.yml file
     * @throws NullPointerException if {@code configPath} is null
     */
    public static void loadPermissionsConfig(Path configPath) {
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
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load permissions configuration from: {}", configPath, e);
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
                    extendedRole.cleanCapability();
                    extendedRole.setDescription(rCfg.description() != null ? rCfg.description() : "");
                    extendedRole.setColor(parseColor(rCfg.color()));
                } else {
                    extendedRole = new ExtendedRole(roleName, rCfg.description(), parseColor(rCfg.color()));

                    if (existingRole != null) {
                        extendedRole.setId(existingRole.getId());
                        extendedRole.setPosition(existingRole.getPosition());
                        if (existingRole.isReadOnly()) {
                            extendedRole.setReadOnly();
                        }
                    }

                    if (allRoles != null) {
                        if (existingIndex != -1) {
                            allRoles.set(existingIndex, extendedRole);
                        } else {
                            allRoles.add(extendedRole);
                        }
                    }
                }

                extendedRole.setPrefix(rCfg.prefix());
                extendedRole.setSuffix(rCfg.suffix());

                // Guaranteed LoginOnServer capability for all non-banned roles
                if (!"banned".equalsIgnoreCase(roleName)) {
                    extendedRole.addCapability(Capability.LoginOnServer);
                }

                // Capabilities mapping
                if (rCfg.capabilities() != null) {
                    for (String capName : rCfg.capabilities()) {
                        if (capName == null || capName.isBlank()) continue;
                        if ("*".equals(capName.strip())) {
                            for (Capability c : Capability.values()) {
                                if (c != null && c != Capability.None) {
                                    extendedRole.addCapability(c);
                                }
                            }
                        } else {
                            Capability cap = findStandardCapability(capName.strip());
                            if (cap != null && cap != Capability.None) {
                                extendedRole.addCapability(cap);
                            } else {
                                LOGGER.warn("Unknown capability '{}' specified for role '{}'", capName, roleName);
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

                LOGGER.info("Configured role '{}' [ID: {}, Pos: {}] -> Prefix: '{}', Suffix: '{}', Caps: {}, Perms: {}, Parents: {}",
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
                saveRoleToDatabase(extendedRole);
            }
        }

        // Sort roles list by position if present
        if (allRoles != null && allRoles.size() > 1) {
            allRoles.sort(Comparator.comparingInt(Role::getPosition));
        }

        // Clear previous runtime in-memory user bindings
        CONFIGURED_USER_GROUPS.clear();

        // Register direct user mappings from YAML configuration
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
                    LOGGER.info("Mapped user '{}' -> Configured Group: '{}'", rawIdentifier, grp);
                }

                if (uCfg.permissions() != null) {
                    for (String perm : uCfg.permissions()) {
                        if (perm != null && !perm.isBlank()) {
                            grantPermissionToPlayer(identifier, perm, (Instant) null);
                        }
                    }
                }

                if (uCfg.temporaryPermissions() != null) {
                    for (Map.Entry<String, String> tempEntry : uCfg.temporaryPermissions().entrySet()) {
                        if (tempEntry.getKey() == null || tempEntry.getValue() == null) continue;
                        try {
                            Instant expireAt = Instant.parse(tempEntry.getValue().strip());
                            grantPermissionToPlayer(identifier, tempEntry.getKey(), expireAt);
                        } catch (Exception _) {
                            LOGGER.warn("Invalid ISO-8601 timestamp '{}' for permission '{}' on user '{}'",
                                    tempEntry.getValue(), tempEntry.getKey(), identifier);
                        }
                    }
                }
            }
        }

        LOGGER.info("Successfully loaded and registered all permissions and role models.");
    }

    /**
     * Ensures a connecting player receives their YAML-configured group if defined.
     *
     * @param connection the client UDP connection
     */
    public static void syncPlayerLoginRole(IsoPlayer player, UdpConnection connection) {
        if (player == null && connection == null) return;

        String rawUsername = player != null && player.getUsername() != null
                ? player.getUsername()
                : (connection != null && connection.getUserName() != null ? connection.getUserName() : "");

        String lookupKey = normalizeIdentifier(rawUsername);
        long steamId = connection != null ? connection.getSteamId() : player.getSteamID();
        String steamIdStr = steamId != 0L ? Long.toUnsignedString(steamId) : null;

        String targetGroup = null;
        if (!lookupKey.isEmpty() && CONFIGURED_USER_GROUPS.containsKey(lookupKey)) {
            targetGroup = CONFIGURED_USER_GROUPS.get(lookupKey);
        } else if (steamIdStr != null && CONFIGURED_USER_GROUPS.containsKey(steamIdStr)) {
            targetGroup = CONFIGURED_USER_GROUPS.get(steamIdStr);
        }

        if (targetGroup != null) {
            Role role = findRole(targetGroup);
            if (role != null) {
                if (player != null) {
                    assignRole(player, targetGroup);
                } else {
                    assignRole(connection, targetGroup);
                }
                LOGGER.info("Successfully applied YAML-configured role '{}' to user '{}'", targetGroup, rawUsername);
            }
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
                    prefix: "[Admin] "
                    suffix: ""
                    permissions:
                      - "*"
                    capabilities:
                      - "*"
                    parents: []
                
                  moderator:
                    description: "Server Moderator - all capabilities except core server control"
                    color: "50,255,50,255"
                    prefix: "[Mod] "
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
                    prefix: "[Priority] "
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
                    prefix: "[VIP] "
                    suffix: " ★"
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
                
                  banned:
                    description: "Banned role - forbidden from logging in"
                    color: "128,128,128,255"
                    prefix: "[Banned] "
                    suffix: ""
                    permissions: []
                    capabilities: []
                    parents: []
                
                users:
                  # Example of assigning a role and personal permissions by Username
                  "Example UserName":
                    group: "admin"
                    permissions:
                      - "example.custom.permission"
                
                  # Example of assigning a role and temporary permissions by SteamID64
                  76561198012345678:
                    group: "vip"
                    temporaryPermissions:
                      "example.temporary.boost": "2026-12-31T23:59:59Z"
                """;
        Files.writeString(path, defaultYaml);
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

        role.addCapability(Capability.LoginOnServer);
        if (defaultCapabilities != null) {
            for (Capability capability : defaultCapabilities) {
                if (capability != null && capability != Capability.None) {
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

        List<Role> allRoles = Roles.getRoles();
        if (!allRoles.contains(role)) {
            allRoles.add(role);
            allRoles.sort(Comparator.comparingInt(Role::getPosition));
        }

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
     * Deletes a custom role from memory, database, and connected clients.
     *
     * @param roleName  the unique name of the role to delete
     * @param adminName the username of the administrator issuing the deletion
     * @return {@code true} if deleted successfully; {@code false} if not found or read-only
     */
    public static boolean deleteRole(String roleName, String adminName) {
        if (roleName == null || roleName.isBlank()) return false;
        Role role = findRole(roleName);
        if (role == null || role.isReadOnly()) return false;

        Roles.getRoles().remove(role);

        if (GameServer.server) {
            Roles.deleteRole(role.getName(), adminName != null ? adminName : "Server");
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
        player.setRole(role);
        String username = player.getUsername();

        if (GameServer.server && GameServer.udpEngine != null && username != null) {
            for (int i = 0; i < GameServer.udpEngine.connections.size(); i++) {
                UdpConnection connection = GameServer.udpEngine.connections.get(i);
                if (connection != null && connection.hasPlayer(username)) {
                    connection.setRole(role);
                    break;
                }
            }
        }

        if (username != null) {
            persistUserRole(username, role);
        }

        EventManager.invoke(ServerEvents.PLAYER_ROLE_ASSIGNED, player, oldRole, role);
        return true;
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

        connection.setRole(role);

        if (connection.players != null) {
            for (IsoPlayer player : connection.players) {
                if (player != null) {
                    Role oldRole = player.getRole();
                    player.setRole(role);
                    if (player.getUsername() != null) {
                        persistUserRole(player.getUsername(), role);
                    }
                    EventManager.invoke(ServerEvents.PLAYER_ROLE_ASSIGNED, player, oldRole, role);
                }
            }
        }

        return true;
    }

    /**
     * Assigns a role to a player identified by Username (including spaces) or 64-bit SteamID.
     *
     * @param identifier in-game username or SteamID64
     * @param roleName   the target role name
     * @return {@code true} if assigned; {@code false} if not found
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
                        String pName = player.getUsername() != null ? player.getUsername().strip() : "";
                        String pSteamId = getSteamID(player);
                        if (clean.equalsIgnoreCase(pName) || clean.equals(pSteamId)) {
                            return assignRole(player, role.getName());
                        }
                    }
                }
            }
            persistUserRole(clean, role);
            return true;
        }
        return false;
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
     */
    private static void persistUserRole(String username, Role role) {
        if (username == null || username.isBlank() || role == null) return;
        if (GameServer.server && ServerWorldDatabase.instance != null) {
            try {
                if (ServerWorldDatabase.instance.containsUser(username)) {
                    ServerWorldDatabase.instance.setRole(username, role);
                }
            } catch (Exception ex) {
                DebugType.Multiplayer.printException(
                        ex,
                        "Failed to persist role for user: " + username,
                        LogSeverity.Error
                );
            }
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
            return grantPermissionToPlayer(identifier, permissionNode, (Instant) null);
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
        if (identifier == null || identifier.isBlank() || permissionNode == null || permissionNode.isBlank()) {
            return false;
        }

        String userKey = normalizeIdentifier(identifier);
        String nodeKey = permissionNode.strip().toLowerCase(Locale.ROOT);
        PlayerPermission record = new PlayerPermission(nodeKey, expirationDate);

        CUSTOM_PLAYER_PERMISSIONS
                .computeIfAbsent(userKey, _ -> new ConcurrentHashMap<>())
                .put(nodeKey, record);

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
        if (userNodes != null && userNodes.remove(nodeKey) != null) {
            EventManager.invoke(ServerEvents.PERMISSION_REVOKED, identifier, permissionNode);
            return true;
        }
        return false;
    }

    /**
     * Clears all personal permissions assigned directly to a player.
     *
     * @param identifier the target username or SteamID64
     */
    public static void clearPlayerPermissions(String identifier) {
        if (identifier != null && !identifier.isBlank()) {
            CUSTOM_PLAYER_PERMISSIONS.remove(normalizeIdentifier(identifier));
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

        Map<String, PlayerPermission> map = CUSTOM_PLAYER_PERMISSIONS.get(normalizeIdentifier(identifier));
        if (map == null || map.isEmpty()) {
            return Collections.emptyList();
        }

        map.values().removeIf(PlayerPermission::isExpired);
        return Collections.unmodifiableCollection(new ArrayList<>(map.values()));
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

        // Check personal permissions by Username (Negations FIRST)
        if (!username.isEmpty()) {
            if (checkDirectPermissions(username, target, true)) return false;
            if (checkDirectPermissions(username, target, false)) return true;
        }

        // Check personal permissions by SteamID64 (Negations FIRST)
        if (steamId != null) {
            if (checkDirectPermissions(steamId, target, true)) return false;
            if (checkDirectPermissions(steamId, target, false)) return true;
        }

        // Check assigned role permissions
        Role role = player.getRole();
        if (role == null) {
            role = Roles.getDefaultForUser();
        }

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

        nodes.values().removeIf(PlayerPermission::isExpired);

        if (negationCheck) {
            if (nodes.containsKey("-" + target)) return true;
            for (PlayerPermission perm : nodes.values()) {
                if (perm.isNegated() && evaluateWildcards(Set.of(perm.cleanNode()), target)) {
                    return true;
                }
            }
            return false;
        }

        Set<String> positive = nodes.values().stream()
                .filter(p -> !p.isNegated())
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
                if (p != null && clean.equalsIgnoreCase(p.getUsername())) {
                    return hasCapability(p, capability);
                }
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
        if (grantedNodes.contains("*") || grantedNodes.contains(targetNode)) {
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
            int r = Integer.parseInt(parts[0].trim());
            int g = Integer.parseInt(parts[1].trim());
            int b = Integer.parseInt(parts[2].trim());
            int a = parts.length > 3 ? Integer.parseInt(parts[3].trim()) : 255;
            return new Color(r / 255.0f, g / 255.0f, b / 255.0f, a / 255.0f);
        } catch (Exception _) {
            return Color.white;
        }
    }

    /**
     * Cleans enclosing quotes, strips whitespace, and normalizes an identifier to lowercase.
     *
     * @param raw raw input identifier
     * @return normalized lowercase identifier
     */
    private static String normalizeIdentifier(String raw) {
        if (raw == null) return "";
        String s = raw.strip();
        if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) {
            if (s.length() >= 2) {
                s = s.substring(1, s.length() - 1).strip();
            }
        }
        return s;
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