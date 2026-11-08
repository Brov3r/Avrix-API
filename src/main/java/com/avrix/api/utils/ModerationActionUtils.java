package com.avrix.api.utils;

import com.avrix.api.permissions.PermissionsManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zombie.characters.Capability;
import zombie.characters.IsoPlayer;
import zombie.characters.Role;
import zombie.core.logger.LoggerManager;
import zombie.core.network.ByteBufferWriter;
import zombie.core.raknet.UdpConnection;
import zombie.network.BanSystem;
import zombie.network.GameServer;
import zombie.network.PacketTypes;
import zombie.network.ServerWorldDatabase;
import zombie.network.Userlog;

import java.sql.SQLException;
import java.util.Locale;

/**
 * Server-side helpers for common moderation actions: kick, ban and chat mute.
 * <p>
 * Ban and kick actions follow the vanilla server pipeline. Overloads that accept an
 * {@code actor} verify the relevant capability through the Avrix permissions system;
 * overloads without an actor are intended for trusted plugin or console code. The
 * vanilla protection capabilities ({@code CantBeKickedByUser} and
 * {@code CantBeBannedByUser}) are always respected.
 */
public final class ModerationActionUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModerationActionUtils.class);
    private static final String ISSUED_BY = "Avrix";

    private ModerationActionUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Kicks an online player.
     *
     * @param player target player
     * @param reason kick reason shown to the player, or {@code null} for the default message
     * @return {@code true} if the player was kicked
     */
    public static boolean kickPlayer(IsoPlayer player, String reason) {
        if (player == null || !GameServer.server) {
            return false;
        }
        UdpConnection connection = PlayerLookupUtils.getConnection(player).orElse(null);
        return connection != null && kickConnection(player, connection, reason);
    }

    /**
     * Kicks an online player by username.
     *
     * @param username target username
     * @param reason   kick reason shown to the player, or {@code null} for the default message
     * @return {@code true} if the player was found and kicked
     */
    public static boolean kickPlayer(String username, String reason) {
        return PlayerLookupUtils.getPlayerByUsername(username)
                .map(player -> kickPlayer(player, reason))
                .orElse(false);
    }

    /**
     * Kicks an online player after checking the actor's capability.
     *
     * @param actor  player performing the action
     * @param target target player
     * @param reason kick reason
     * @return {@code true} if the actor is allowed and the player was kicked
     */
    public static boolean kickPlayer(IsoPlayer actor, IsoPlayer target, String reason) {
        return PermissionsManager.hasCapability(actor, Capability.KickUser)
                && kickPlayer(target, reason);
    }

    /**
     * Kicks an online player by username after checking the actor's capability.
     *
     * @param actor    player performing the action
     * @param username target username
     * @param reason   kick reason
     * @return {@code true} if the actor is allowed and the player was kicked
     */
    public static boolean kickPlayer(IsoPlayer actor, String username, String reason) {
        return PermissionsManager.hasCapability(actor, Capability.KickUser)
                && kickPlayer(username, reason);
    }

    /**
     * Bans a user account by username and disconnects the player if online.
     *
     * @param username target username
     * @param reason   ban reason stored in the user log
     * @return action result with the message reported by the vanilla ban pipeline
     */
    public static ModerationResult banPlayer(String username, String reason) {
        return setUserBanned(username, reason, true);
    }

    /**
     * Removes a username ban.
     *
     * @param username target username
     * @return action result with the message reported by the vanilla ban pipeline
     */
    public static ModerationResult unbanPlayer(String username) {
        return setUserBanned(username, "", false);
    }

    /**
     * Bans a user account after checking the actor's capability.
     *
     * @param actor    player performing the action
     * @param username target username
     * @param reason   ban reason
     * @return action result
     */
    public static ModerationResult banPlayer(IsoPlayer actor, String username, String reason) {
        return PermissionsManager.hasCapability(actor, Capability.BanUnbanUser)
                ? banPlayer(username, reason)
                : ModerationResult.failure("Insufficient permissions");
    }

    /**
     * Removes a username ban after checking the actor's capability.
     *
     * @param actor    player performing the action
     * @param username target username
     * @return action result
     */
    public static ModerationResult unbanPlayer(IsoPlayer actor, String username) {
        return PermissionsManager.hasCapability(actor, Capability.BanUnbanUser)
                ? unbanPlayer(username)
                : ModerationResult.failure("Insufficient permissions");
    }

    /**
     * Bans a SteamID and disconnects linked accounts if online.
     *
     * @param steamId unsigned SteamID64 string
     * @param reason  ban reason
     * @return action result with the message reported by the vanilla ban pipeline
     */
    public static ModerationResult banSteamId(String steamId, String reason) {
        return setSteamIdBanned(steamId, reason, true);
    }

    /**
     * Removes a SteamID ban.
     *
     * @param steamId unsigned SteamID64 string
     * @return action result with the message reported by the vanilla ban pipeline
     */
    public static ModerationResult unbanSteamId(String steamId) {
        return setSteamIdBanned(steamId, "", false);
    }

    /**
     * Bans a SteamID after checking the actor's capability.
     *
     * @param actor   player performing the action
     * @param steamId unsigned SteamID64 string
     * @param reason  ban reason
     * @return action result
     */
    public static ModerationResult banSteamId(IsoPlayer actor, String steamId, String reason) {
        return PermissionsManager.hasCapability(actor, Capability.BanUnbanUser)
                ? banSteamId(steamId, reason)
                : ModerationResult.failure("Insufficient permissions");
    }

    /**
     * Removes a SteamID ban after checking the actor's capability.
     *
     * @param actor   player performing the action
     * @param steamId unsigned SteamID64 string
     * @return action result
     */
    public static ModerationResult unbanSteamId(IsoPlayer actor, String steamId) {
        return PermissionsManager.hasCapability(actor, Capability.BanUnbanUser)
                ? unbanSteamId(steamId)
                : ModerationResult.failure("Insufficient permissions");
    }

    /**
     * Bans an IP address.
     *
     * @param ip     IP address
     * @param reason ban reason
     * @return action result with the message reported by the vanilla ban pipeline
     */
    public static ModerationResult banIp(String ip, String reason) {
        return setIpBanned(ip, reason, true);
    }

    /**
     * Removes an IP ban.
     *
     * @param ip IP address
     * @return action result with the message reported by the vanilla ban pipeline
     */
    public static ModerationResult unbanIp(String ip) {
        return setIpBanned(ip, "", false);
    }

    /**
     * Bans an IP address after checking the actor's capability.
     *
     * @param actor  player performing the action
     * @param ip     IP address
     * @param reason ban reason
     * @return action result
     */
    public static ModerationResult banIp(IsoPlayer actor, String ip, String reason) {
        return PermissionsManager.hasCapability(actor, Capability.BanUnbanUser)
                ? banIp(ip, reason)
                : ModerationResult.failure("Insufficient permissions");
    }

    /**
     * Removes an IP ban after checking the actor's capability.
     *
     * @param actor player performing the action
     * @param ip    IP address
     * @return action result
     */
    public static ModerationResult unbanIp(IsoPlayer actor, String ip) {
        return PermissionsManager.hasCapability(actor, Capability.BanUnbanUser)
                ? unbanIp(ip)
                : ModerationResult.failure("Insufficient permissions");
    }

    /**
     * Bans the IP address currently used by an online player.
     *
     * @param username target username
     * @param reason   ban reason
     * @return action result with the message reported by the vanilla ban pipeline
     */
    public static ModerationResult banPlayerIp(String username, String reason) {
        return setUserIpBanned(username, reason, true);
    }

    /**
     * Removes the IP ban associated with a username.
     *
     * @param username target username
     * @return action result with the message reported by the vanilla ban pipeline
     */
    public static ModerationResult unbanPlayerIp(String username) {
        return setUserIpBanned(username, "", false);
    }

    /**
     * Bans the IP address associated with a player after checking the actor's capability.
     *
     * @param actor    player performing the action
     * @param username target username
     * @param reason   ban reason
     * @return action result
     */
    public static ModerationResult banPlayerIp(IsoPlayer actor, String username, String reason) {
        return PermissionsManager.hasCapability(actor, Capability.BanUnbanUser)
                ? banPlayerIp(username, reason)
                : ModerationResult.failure("Insufficient permissions");
    }

    /**
     * Removes the IP ban associated with a username after checking the actor's capability.
     *
     * @param actor    player performing the action
     * @param username target username
     * @return action result
     */
    public static ModerationResult unbanPlayerIp(IsoPlayer actor, String username) {
        return PermissionsManager.hasCapability(actor, Capability.BanUnbanUser)
                ? unbanPlayerIp(username)
                : ModerationResult.failure("Insufficient permissions");
    }

    /**
     * Enables or disables the all-chat mute for a player.
     * <p>
     * The server-side connection flag is updated as well, so muted players cannot send
     * {@code /all} messages even if their client is not synchronized yet.
     *
     * @param player target player
     * @param muted  {@code true} to mute, {@code false} to unmute
     * @return {@code true} if the player state was updated
     */
    public static boolean setMuted(IsoPlayer player, boolean muted) {
        if (player == null || !GameServer.server) {
            return false;
        }

        player.setAllChatMuted(muted);
        UdpConnection owner = PlayerLookupUtils.getConnection(player).orElse(null);
        if (owner != null) {
            owner.setAllChatMuted(muted);
            syncPlayerStats(player);
        }
        return true;
    }

    /**
     * Enables or disables the all-chat mute after checking the actor's capability.
     *
     * @param actor  player performing the action
     * @param player target player
     * @param muted  {@code true} to mute, {@code false} to unmute
     * @return {@code true} if the actor is allowed and the player state was updated
     */
    public static boolean setMuted(IsoPlayer actor, IsoPlayer player, boolean muted) {
        return PermissionsManager.hasCapability(actor, Capability.CanModifyPlayerStatsInThePlayerStatsUI)
                && setMuted(player, muted);
    }

    /**
     * Mutes all chat for a player after checking the actor's capability.
     *
     * @param actor  player performing the action
     * @param player target player
     * @return {@code true} if the actor is allowed and the player was muted
     */
    public static boolean mutePlayer(IsoPlayer actor, IsoPlayer player) {
        return PermissionsManager.hasCapability(actor, Capability.CanModifyPlayerStatsInThePlayerStatsUI)
                && mutePlayer(player);
    }

    /**
     * Removes the all-chat mute after checking the actor's capability.
     *
     * @param actor  player performing the action
     * @param player target player
     * @return {@code true} if the actor is allowed and the player was unmuted
     */
    public static boolean unmutePlayer(IsoPlayer actor, IsoPlayer player) {
        return PermissionsManager.hasCapability(actor, Capability.CanModifyPlayerStatsInThePlayerStatsUI)
                && unmutePlayer(player);
    }

    /**
     * Mutes all chat for a player.
     *
     * @param player target player
     * @return {@code true} if the player state was updated
     */
    public static boolean mutePlayer(IsoPlayer player) {
        return setMuted(player, true);
    }

    /**
     * Removes the all-chat mute from a player.
     *
     * @param player target player
     * @return {@code true} if the player state was updated
     */
    public static boolean unmutePlayer(IsoPlayer player) {
        return setMuted(player, false);
    }

    /**
     * Checks whether a player is muted in all chat.
     *
     * @param player target player
     * @return {@code true} if the player is muted
     */
    public static boolean isMuted(IsoPlayer player) {
        return player != null && player.isAllChatMuted();
    }

    /**
     * Checks whether a network connection is muted in all chat.
     *
     * @param connection target connection
     * @return {@code true} if the connection is muted
     */
    public static boolean isMuted(UdpConnection connection) {
        return connection != null && connection.isAllChatMuted();
    }

    /**
     * Broadcasts fresh player stats so clients reflect the new mute state.
     *
     * @param player target player
     */
    private static void syncPlayerStats(IsoPlayer player) {
        if (GameServer.udpEngine == null || GameServer.udpEngine.connections == null) {
            return;
        }

        for (UdpConnection connection : GameServer.udpEngine.connections) {
            if (connection == null) {
                continue;
            }
            try {
                ByteBufferWriter buffer = connection.startPacket();
                PacketTypes.PacketType.ChangePlayerStats.doPacket(buffer);
                player.createPlayerStats(buffer, ISSUED_BY);
                PacketTypes.PacketType.ChangePlayerStats.send(connection);
            } catch (RuntimeException ex) {
                LOGGER.warn("Failed to synchronize mute state for '{}'", player.getUsername(), ex);
                return;
            }
        }
    }

    /**
     * Kicks a player using an already resolved connection.
     *
     * @param player     target player
     * @param connection owning connection
     * @param reason     kick reason
     * @return {@code true} if the player was kicked
     */
    private static boolean kickConnection(IsoPlayer player, UdpConnection connection, String reason) {
        Role role = connection.getRole();
        if (role != null && role.hasCapability(Capability.CantBeKickedByUser)) {
            return false;
        }

        String username = player.getUsername();
        String cleanReason = normalize(reason);
        try {
            LoggerManager.getLogger("admin").write(ISSUED_BY + " kicked user " + username
                    + (cleanReason.isEmpty() ? "" : " (" + cleanReason + ")"));
            if (ServerWorldDatabase.instance != null && username != null && !username.isBlank()) {
                ServerWorldDatabase.instance.addUserlog(
                        username, Userlog.UserlogType.Kicked, cleanReason, ISSUED_BY, 1);
            }
        } catch (RuntimeException ex) {
            LOGGER.warn("Failed to write kick audit log for '{}'", username, ex);
        }

        if (cleanReason.isEmpty()) {
            GameServer.kick(connection, "UI_Policy_Kick", null);
        } else {
            GameServer.kick(connection, "UI_Policy_KickReason", cleanReason);
        }
        connection.forceDisconnect("avrix-kick");
        return true;
    }

    /**
     * Bans or unbans a username through the vanilla pipeline.
     *
     * @param username target username
     * @param reason   ban reason
     * @param ban      {@code true} to ban, {@code false} to unban
     * @return action result
     */
    private static ModerationResult setUserBanned(String username, String reason, boolean ban) {
        String cleanName = normalize(username);
        if (cleanName.isEmpty()) {
            return ModerationResult.failure("Username cannot be blank");
        }
        if (!isDatabaseReady()) {
            return ModerationResult.failure("Server database is not available");
        }
        if (ban && isProtectedFromBan(cleanName)) {
            return ModerationResult.failure("This user can't be banned");
        }

        try {
            String message = BanSystem.BanUser(cleanName, null, normalize(reason), ban);
            return ModerationResult.fromMessage(message);
        } catch (SQLException | RuntimeException ex) {
            LOGGER.error("Failed to {} user '{}'", ban ? "ban" : "unban", cleanName, ex);
            return ModerationResult.failure("Database error: " + ex.getMessage());
        }
    }

    /**
     * Bans or unbans a SteamID through the vanilla pipeline.
     *
     * @param steamId target SteamID64
     * @param reason  ban reason
     * @param ban     {@code true} to ban, {@code false} to unban
     * @return action result
     */
    private static ModerationResult setSteamIdBanned(String steamId, String reason, boolean ban) {
        String cleanSteamId = normalize(steamId);
        if (cleanSteamId.isEmpty()) {
            return ModerationResult.failure("SteamID cannot be blank");
        }
        if (!isDatabaseReady()) {
            return ModerationResult.failure("Server database is not available");
        }

        try {
            String message = BanSystem.BanUserBySteamID(cleanSteamId, null, normalize(reason), ban);
            if (message == null || message.isBlank()) {
                return ModerationResult.failure("SteamID bans require Steam mode");
            }
            return ModerationResult.fromMessage(message);
        } catch (SQLException | RuntimeException ex) {
            LOGGER.error("Failed to {} SteamID '{}'", ban ? "ban" : "unban", cleanSteamId, ex);
            return ModerationResult.failure("Database error: " + ex.getMessage());
        }
    }

    /**
     * Bans or unbans an IP through the vanilla pipeline.
     *
     * @param ip     target IP
     * @param reason ban reason
     * @param ban    {@code true} to ban, {@code false} to unban
     * @return action result
     */
    private static ModerationResult setIpBanned(String ip, String reason, boolean ban) {
        String cleanIp = normalize(ip);
        if (cleanIp.isEmpty()) {
            return ModerationResult.failure("IP address cannot be blank");
        }
        if (!isDatabaseReady()) {
            return ModerationResult.failure("Server database is not available");
        }

        try {
            return ModerationResult.fromMessage(BanSystem.BanIP(cleanIp, null, normalize(reason), ban));
        } catch (SQLException | RuntimeException ex) {
            LOGGER.error("Failed to {} IP '{}'", ban ? "ban" : "unban", cleanIp, ex);
            return ModerationResult.failure("Database error: " + ex.getMessage());
        }
    }

    /**
     * Bans or unbans the IP address associated with a username.
     *
     * @param username target username
     * @param reason   ban reason
     * @param ban      {@code true} to ban, {@code false} to unban
     * @return action result
     */
    private static ModerationResult setUserIpBanned(String username, String reason, boolean ban) {
        String cleanName = normalize(username);
        if (cleanName.isEmpty()) {
            return ModerationResult.failure("Username cannot be blank");
        }
        if (!isDatabaseReady()) {
            return ModerationResult.failure("Server database is not available");
        }

        try {
            return ModerationResult.fromMessage(BanSystem.BanUserByIP(cleanName, null, normalize(reason), ban));
        } catch (SQLException | RuntimeException ex) {
            LOGGER.error("Failed to {} IP for user '{}'", ban ? "ban" : "unban", cleanName, ex);
            return ModerationResult.failure("Database error: " + ex.getMessage());
        }
    }

    /**
     * Checks whether the server world database is ready for moderation persistence.
     *
     * @return {@code true} if the database is available
     */
    private static boolean isDatabaseReady() {
        return GameServer.server && ServerWorldDatabase.instance != null;
    }

    /**
     * Checks the vanilla ban protection capability for a username.
     *
     * @param username target username
     * @return {@code true} if the assigned role cannot be banned
     */
    private static boolean isProtectedFromBan(String username) {
        try {
            Role role = ServerWorldDatabase.instance.getUserRoleNameByUsername(username);
            return role != null && role.hasCapability(Capability.CantBeBannedByUser);
        } catch (RuntimeException ex) {
            LOGGER.warn("Failed to resolve the role of '{}' before banning", username, ex);
            return false;
        }
    }

    /**
     * Normalizes optional text values.
     *
     * @param value raw value
     * @return stripped value, or an empty string when {@code value} is {@code null}
     */
    private static String normalize(String value) {
        return value == null ? "" : value.strip();
    }

    /**
     * Outcome of a moderation action.
     *
     * @param success whether the action was applied
     * @param message human-readable message returned by the vanilla pipeline
     */
    public record ModerationResult(boolean success, String message) {

        /**
         * Creates a successful result.
         *
         * @param message result message
         * @return successful result
         */
        public static ModerationResult success(String message) {
            return new ModerationResult(true, message);
        }

        /**
         * Creates a failed result.
         *
         * @param message failure reason
         * @return failed result
         */
        public static ModerationResult failure(String message) {
            return new ModerationResult(false, message);
        }

        /**
         * Derives success from a vanilla pipeline response.
         *
         * @param message response text
         * @return parsed result
         */
        private static ModerationResult fromMessage(String message) {
            if (message == null || message.isBlank()) {
                return failure("The server did not return a result");
            }

            String lower = message.toLowerCase(Locale.ROOT);
            boolean failed = lower.contains("can't be banned")
                    || lower.contains("don't have capability")
                    || lower.contains("not in the whitelist")
                    || lower.contains("cannot ban ip")
                    || lower.contains("player not found")
                    || lower.contains("connection not found")
                    || lower.contains("steam mode");
            return new ModerationResult(!failed, message);
        }
    }
}
