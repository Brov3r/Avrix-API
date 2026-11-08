package com.avrix.api.utils;

import com.avrix.api.permissions.PermissionsManager;
import zombie.characters.Capability;
import zombie.characters.IsoPlayer;
import zombie.characters.Role;
import zombie.core.raknet.UdpConnection;
import zombie.network.GameServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Server-side helpers for querying online {@link IsoPlayer} instances.
 * <p>
 * All lookups operate on a snapshot of {@link GameServer#Players}, so callers never
 * receive the mutable server list. Methods return empty results when the server is not
 * running or no player matches the query.
 */
public final class PlayerLookupUtils {

    private PlayerLookupUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Returns an immutable snapshot of all online players.
     *
     * @return online players, or an empty list when the server is not running
     */
    public static List<IsoPlayer> getOnlinePlayers() {
        if (!GameServer.server || GameServer.Players == null || GameServer.Players.isEmpty()) {
            return List.of();
        }

        List<IsoPlayer> snapshot = new ArrayList<>(GameServer.Players.size());
        for (IsoPlayer player : new ArrayList<>(GameServer.Players)) {
            if (player != null) {
                snapshot.add(player);
            }
        }
        return List.copyOf(snapshot);
    }

    /**
     * Returns the number of online players.
     *
     * @return online player count
     */
    public static int getOnlinePlayerCount() {
        return getOnlinePlayers().size();
    }

    /**
     * Finds an online player by exact username.
     * <p>
     * The comparison is case-insensitive and surrounding whitespace is ignored.
     *
     * @param username target username
     * @return matching player, or empty if no player is online under that name
     */
    public static Optional<IsoPlayer> getPlayerByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }

        String cleanName = username.strip();
        for (IsoPlayer player : getOnlinePlayers()) {
            String playerName = player.getUsername();
            if (playerName != null && playerName.equalsIgnoreCase(cleanName)) {
                return Optional.of(player);
            }
        }
        return Optional.empty();
    }

    /**
     * Finds all online players whose username contains the given text.
     * <p>
     * The comparison is case-insensitive and surrounding whitespace is ignored.
     *
     * @param usernamePart username fragment
     * @return matching players in server iteration order, or an empty list
     */
    public static List<IsoPlayer> getPlayersByUsernamePart(String usernamePart) {
        if (usernamePart == null || usernamePart.isBlank()) {
            return List.of();
        }

        String needle = usernamePart.strip().toLowerCase(Locale.ROOT);
        List<IsoPlayer> matches = new ArrayList<>();
        for (IsoPlayer player : getOnlinePlayers()) {
            String playerName = player.getUsername();
            if (playerName != null && playerName.toLowerCase(Locale.ROOT).contains(needle)) {
                matches.add(player);
            }
        }
        return List.copyOf(matches);
    }

    /**
     * Finds the first online player whose username contains the given text.
     *
     * @param usernamePart username fragment
     * @return first matching player, or empty if no player matches
     * @see #getPlayersByUsernamePart(String)
     */
    public static Optional<IsoPlayer> getPlayerByUsernamePart(String usernamePart) {
        return getPlayersByUsernamePart(usernamePart).stream().findFirst();
    }

    /**
     * Finds an online player by the network online identifier.
     *
     * @param onlineId short online identifier assigned by the server
     * @return matching player, or empty if no player has that identifier
     */
    public static Optional<IsoPlayer> getPlayerByOnlineId(short onlineId) {
        for (IsoPlayer player : getOnlinePlayers()) {
            if (player.getOnlineID() == onlineId) {
                return Optional.of(player);
            }
        }
        return Optional.empty();
    }

    /**
     * Finds an online player by 64-bit SteamID.
     *
     * @param steamId SteamID64 value
     * @return matching player, or empty if no player has that SteamID
     */
    public static Optional<IsoPlayer> getPlayerBySteamId(long steamId) {
        if (steamId == 0L) {
            return Optional.empty();
        }

        for (IsoPlayer player : getOnlinePlayers()) {
            if (resolveSteamId(player) == steamId) {
                return Optional.of(player);
            }
        }
        return Optional.empty();
    }

    /**
     * Finds an online player by an unsigned 64-bit SteamID string.
     *
     * @param steamId SteamID64 as an unsigned decimal string
     * @return matching player, or empty if the value is invalid or no player matches
     */
    public static Optional<IsoPlayer> getPlayerBySteamId(String steamId) {
        if (steamId == null || steamId.isBlank()) {
            return Optional.empty();
        }

        try {
            return getPlayerBySteamId(Long.parseUnsignedLong(steamId.strip()));
        } catch (NumberFormatException _) {
            return Optional.empty();
        }
    }

    /**
     * Finds all online players assigned to the given role.
     * <p>
     * Role names are compared case-insensitively.
     *
     * @param roleName role name
     * @return matching players, or an empty list
     */
    public static List<IsoPlayer> getPlayersByRole(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            return List.of();
        }

        String cleanName = roleName.strip();
        List<IsoPlayer> matches = new ArrayList<>();
        for (IsoPlayer player : getOnlinePlayers()) {
            Role role = player.getRole();
            if (role != null && role.getName() != null && role.getName().equalsIgnoreCase(cleanName)) {
                matches.add(player);
            }
        }
        return List.copyOf(matches);
    }

    /**
     * Finds all online players assigned to the given role.
     *
     * @param role target role
     * @return matching players, or an empty list when {@code role} is {@code null}
     */
    public static List<IsoPlayer> getPlayersByRole(Role role) {
        return role != null ? getPlayersByRole(role.getName()) : List.of();
    }

    /**
     * Finds all online players possessing a native Project Zomboid capability.
     *
     * @param capability target capability
     * @return matching players, or an empty list
     */
    public static List<IsoPlayer> getPlayersWithCapability(Capability capability) {
        if (capability == null || capability == Capability.None) {
            return List.of();
        }

        List<IsoPlayer> matches = new ArrayList<>();
        for (IsoPlayer player : getOnlinePlayers()) {
            if (PermissionsManager.hasCapability(player, capability)) {
                matches.add(player);
            }
        }
        return List.copyOf(matches);
    }

    /**
     * Finds all online players possessing a custom permission node.
     *
     * @param permissionNode permission node or capability name
     * @return matching players, or an empty list
     */
    public static List<IsoPlayer> getPlayersWithPermission(String permissionNode) {
        if (permissionNode == null || permissionNode.isBlank()) {
            return List.of();
        }

        List<IsoPlayer> matches = new ArrayList<>();
        for (IsoPlayer player : getOnlinePlayers()) {
            if (PermissionsManager.hasPermission(player, permissionNode)) {
                matches.add(player);
            }
        }
        return List.copyOf(matches);
    }

    /**
     * Checks whether the given player instance is currently online.
     *
     * @param player player to check
     * @return {@code true} if the same player or a player with the same username is online
     */
    public static boolean isOnline(IsoPlayer player) {
        if (player == null) {
            return false;
        }

        String username = player.getUsername();
        for (IsoPlayer online : getOnlinePlayers()) {
            if (online == player) {
                return true;
            }
            if (username != null && online.getUsername() != null
                    && online.getUsername().equalsIgnoreCase(username)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves the unsigned 64-bit SteamID of a player.
     * <p>
     * When the player entity has no SteamID, the owning network connection is consulted.
     *
     * @param player target player
     * @return unsigned SteamID64 string, or empty when unavailable
     */
    public static Optional<String> getSteamId(IsoPlayer player) {
        long steamId = resolveSteamId(player);
        return steamId == 0L ? Optional.empty() : Optional.of(Long.toUnsignedString(steamId));
    }

    /**
     * Resolves the network connection that owns the given player.
     *
     * @param player target player
     * @return owning connection, or empty when the player is not connected
     */
    public static Optional<UdpConnection> getConnection(IsoPlayer player) {
        if (player == null || !GameServer.server || GameServer.udpEngine == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(GameServer.getConnectionFromPlayer(player));
    }

    /**
     * Resolves the SteamID from the player entity or its connection.
     *
     * @param player target player
     * @return SteamID64 value, or {@code 0} when unavailable
     */
    private static long resolveSteamId(IsoPlayer player) {
        if (player == null) {
            return 0L;
        }

        long steamId = player.getSteamID();
        if (steamId != 0L) {
            return steamId;
        }

        UdpConnection connection = getConnection(player).orElse(null);
        return connection != null ? connection.getSteamId() : 0L;
    }
}
