package com.avrix.api.permissions;

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

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe domain service managing role lifecycles, SQLite persistence,
 * network broadcasting, and fine-grained player permission evaluations.
 */
public final class PermissionsManager {

    /**
     * In-memory storage for user-specific custom permissions mapped by normalized username.
     */
    private static final Map<String, Set<String>> CUSTOM_PLAYER_PERMISSIONS = new ConcurrentHashMap<>();

    private PermissionsManager() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Creates, registers, persists, and broadcasts a new {@link ExtendedRole}.
     *
     * @param name                the unique role name
     * @param description         the role description
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
        String normalizedName = name.strip();
        if (normalizedName.isEmpty()) {
            throw new IllegalArgumentException("Role name cannot be blank");
        }

        if (Roles.getRole(normalizedName) != null) {
            throw new IllegalArgumentException("Role '%s' already exists".formatted(normalizedName));
        }

        ExtendedRole role = new ExtendedRole(
                normalizedName,
                description != null ? description : "",
                color != null ? color : Color.white
        );

        // Always ensure baseline capability to join and authenticate on the server
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

        // Register in native Roles registry
        List<Role> allRoles = Roles.getRoles();
        if (!allRoles.contains(role)) {
            allRoles.add(role);
            allRoles.sort(Comparator.comparingInt(Role::getPosition));
        }

        saveRoleToDatabase(role);
        return role;
    }

    /**
     * Creates and registers a custom role with varargs custom permissions.
     *
     * @param name                the unique role name
     * @param description         the role description
     * @param color               the display color
     * @param defaultCapabilities standard capabilities
     * @param customPermissions   custom permission strings
     * @return the registered {@link ExtendedRole}
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
     * Persists a role definition to the SQLite database and synchronizes it across clients.
     *
     * @param role the role to persist
     */
    public static void saveRoleToDatabase(Role role) {
        if (role == null) {
            return;
        }

        if (GameServer.server) {
            ServerWorldDatabase.instance.saveRole(role);
            Roles.save();
            INetworkPacket.sendToAll(PacketTypes.PacketType.Roles, new Object[0]);
        }
    }

    /**
     * Deletes a custom role from memory, database, and connected clients.
     *
     * @param roleName  the name of the role to delete
     * @param adminName the username of the administrator executing the deletion
     * @return {@code true} if deleted successfully, {@code false} if not found or read-only
     */
    public static boolean deleteRole(String roleName, String adminName) {
        if (roleName == null || roleName.isBlank()) {
            return false;
        }

        Role role = Roles.getRole(roleName.strip());
        if (role == null || role.isReadOnly()) {
            return false;
        }

        Roles.deleteRole(role.getName(), adminName != null ? adminName : "Server");
        return true;
    }

    /**
     * Assigns a role to an online player entity, active network connection, and persists to SQLite database.
     *
     * @param player   the online player
     * @param roleName the name of the role to assign
     * @return {@code true} if assigned successfully
     */
    public static boolean assignRole(IsoPlayer player, String roleName) {
        if (player == null || roleName == null || roleName.isBlank()) {
            return false;
        }

        Role role = Roles.getRole(roleName.strip());
        if (role == null) {
            return false;
        }

        // Assign to player entity in-memory
        player.setRole(role);

        String username = player.getUsername();

        // Update active UDP network connection on the server
        if (GameServer.server && GameServer.udpEngine != null) {
            for (int i = 0; i < GameServer.udpEngine.connections.size(); i++) {
                UdpConnection connection = GameServer.udpEngine.connections.get(i);
                if (connection != null && connection.hasPlayer(username)) {
                    connection.setRole(role);
                    break;
                }
            }
        }

        // Persist assignment to SQLite database whitelist table
        persistUserRole(username, role);

        return true;
    }

    /**
     * Assigns a role to a specific active network connection and its associated player entities.
     *
     * @param connection the client network connection
     * @param roleName   the name of the role
     * @return {@code true} if assigned successfully
     */
    public static boolean assignRole(UdpConnection connection, String roleName) {
        if (connection == null || roleName == null || roleName.isBlank()) {
            return false;
        }

        Role role = Roles.getRole(roleName.strip());
        if (role == null) {
            return false;
        }

        connection.setRole(role);

        if (GameServer.server && connection.players != null) {
            for (IsoPlayer player : connection.players) {
                if (player != null) {
                    player.setRole(role);
                    persistUserRole(player.getUsername(), role);
                }
            }
        }

        return true;
    }

    /**
     * Assigns a role to a player by username (handling both online and offline accounts).
     *
     * @param username the target username
     * @param roleName the target role name
     * @return {@code true} if the role was found and assigned
     */
    public static boolean assignRole(String username, String roleName) {
        if (username == null || username.isBlank() || roleName == null || roleName.isBlank()) {
            return false;
        }

        String cleanUsername = username.strip();
        Role role = Roles.getRole(roleName.strip());
        if (role == null) {
            return false;
        }

        if (GameServer.server) {
            boolean onlineAssigned = false;
            if (GameServer.Players != null) {
                for (int i = 0; i < GameServer.Players.size(); i++) {
                    IsoPlayer player = GameServer.Players.get(i);
                    if (player != null && cleanUsername.equalsIgnoreCase(player.getUsername())) {
                        assignRole(player, role.getName());
                        onlineAssigned = true;
                        break;
                    }
                }
            }

            if (!onlineAssigned) {
                persistUserRole(cleanUsername, role);
            }
            return true;
        }

        return false;
    }

    /**
     * Helper delegating role association persistence to {@link ServerWorldDatabase#setRole(String, Role)}.
     *
     * @param username the player's username
     * @param role     the target role
     */
    private static void persistUserRole(String username, Role role) {
        if (GameServer.server && ServerWorldDatabase.instance != null) {
            try {
                ServerWorldDatabase.instance.setRole(username, role);
            } catch (SQLException ex) {
                DebugType.Multiplayer.printException(
                        ex,
                        "Failed to persist role '%s' for user '%s'".formatted(role.getName(), username),
                        LogSeverity.Error
                );
            }
        }
    }

    /**
     * Grants a custom permission node directly to a player's username.
     *
     * @param username       the player username
     * @param permissionNode the permission string
     * @return {@code true} if granted, {@code false} if parameters are invalid
     */
    public static boolean grantPermissionToPlayer(String username, String permissionNode) {
        if (username == null || username.isBlank() || permissionNode == null || permissionNode.isBlank()) {
            return false;
        }

        return CUSTOM_PLAYER_PERMISSIONS
                .computeIfAbsent(username.strip().toLowerCase(Locale.ROOT), _ -> ConcurrentHashMap.newKeySet())
                .add(permissionNode.strip().toLowerCase(Locale.ROOT));
    }

    /**
     * Revokes a custom permission node from a player's username.
     *
     * @param username       the player username
     * @param permissionNode the permission string
     * @return {@code true} if revoked, {@code false} if absent or invalid
     */
    public static boolean revokePermissionFromPlayer(String username, String permissionNode) {
        if (username == null || username.isBlank() || permissionNode == null || permissionNode.isBlank()) {
            return false;
        }

        Set<String> nodes = CUSTOM_PLAYER_PERMISSIONS.get(username.strip().toLowerCase(Locale.ROOT));
        return nodes != null && nodes.remove(permissionNode.strip().toLowerCase(Locale.ROOT));
    }

    /**
     * Clears all personal permissions assigned to a specific player.
     *
     * @param username the player username
     */
    public static void clearPlayerPermissions(String username) {
        if (username != null && !username.isBlank()) {
            CUSTOM_PLAYER_PERMISSIONS.remove(username.strip().toLowerCase(Locale.ROOT));
        }
    }

    /**
     * Retrieves an unmodifiable snapshot of permissions assigned directly to a player.
     *
     * @param username the target username
     * @return an unmodifiable set of permissions
     */
    public static Set<String> getPlayerPermissions(String username) {
        if (username == null || username.isBlank()) {
            return Collections.emptySet();
        }

        Set<String> nodes = CUSTOM_PLAYER_PERMISSIONS.get(username.strip().toLowerCase(Locale.ROOT));
        return nodes != null ? Collections.unmodifiableSet(nodes) : Collections.emptySet();
    }

    /**
     * Evaluates whether a player possesses a specific permission node or native capability.
     *
     * @param player         the target player
     * @param permissionNode the permission node or capability name
     * @return {@code true} if authorized
     */
    public static boolean hasPermission(IsoPlayer player, String permissionNode) {
        if (player == null || permissionNode == null || permissionNode.isBlank()) {
            return false;
        }

        if (Role.isUsingDebugMode()) {
            return true;
        }

        String target = permissionNode.strip().toLowerCase(Locale.ROOT);

        // Check personal user permissions
        Set<String> userNodes = CUSTOM_PLAYER_PERMISSIONS.get(player.getUsername().toLowerCase(Locale.ROOT));
        if (userNodes != null && evaluateWildcards(userNodes, target)) {
            return true;
        }

        // Check assigned role permissions
        Role role = player.getRole();
        if (role == null) {
            role = Roles.getDefaultForUser();
        }

        return hasPermission(role, permissionNode);
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
        if (role == null || permissionNode == null || permissionNode.isBlank()) {
            return false;
        }

        if (Role.isUsingDebugMode()) {
            return true;
        }

        String cleanNode = permissionNode.strip();

        // Delegate to ExtendedRole if available
        if (role instanceof ExtendedRole extendedRole && extendedRole.hasPermission(cleanNode)) {
            return true;
        }

        // Match against native Capability enum
        Capability capability = findStandardCapability(cleanNode);
        return capability != null && role.hasCapability(capability);
    }

    /**
     * Evaluates whether an in-game player possesses a standard Project Zomboid {@link Capability}.
     *
     * @param player     the target player
     * @param capability the native capability to check
     * @return {@code true} if authorized
     */
    public static boolean hasCapability(IsoPlayer player, Capability capability) {
        if (player == null || capability == null || capability == Capability.None) {
            return false;
        }
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
        if (connection == null || capability == null || capability == Capability.None) {
            return false;
        }

        if (Role.isUsingDebugMode()) {
            return true;
        }

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
        if (role == null || capability == null || capability == Capability.None) {
            return false;
        }

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

        if (Role.isUsingDebugMode()) {
            return true;
        }

        if (GameServer.server && GameServer.Players != null) {
            for (int i = 0; i < GameServer.Players.size(); i++) {
                IsoPlayer p = GameServer.Players.get(i);
                if (p != null && username.equalsIgnoreCase(p.getUsername())) {
                    return hasCapability(p, capability);
                }
            }
        }

        Role role = ServerWorldDatabase.instance.getUserRoleNameByUsername(username.strip());
        return role != null && role.hasCapability(capability);
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
}