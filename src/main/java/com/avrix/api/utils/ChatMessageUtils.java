package com.avrix.api.utils;

import com.avrix.api.permissions.PermissionsManager;
import zombie.characters.Capability;
import zombie.characters.IsoPlayer;
import zombie.core.raknet.UdpConnection;
import zombie.network.GameServer;
import zombie.network.chat.ChatServer;

/**
 * Server-side helpers for sending messages through the vanilla chat subsystem.
 * <p>
 * Messages are delivered to the server chat tab. Admin chat and server alerts are
 * available through dedicated methods. All helpers are no-ops when the chat server
 * has not been initialized yet. Actor-aware overloads check
 * {@link Capability#DisplayServerMessage} or {@link Capability#AdminChat}.
 */
public final class ChatMessageUtils {

    private ChatMessageUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Checks whether the server chat subsystem is available.
     *
     * @return {@code true} if the server is running and chat has been initialized
     */
    public static boolean isAvailable() {
        return GameServer.server && ChatServer.isInited();
    }

    /**
     * Sends a server chat message to every player.
     *
     * @param message message text
     * @return {@code true} if the message was sent
     */
    public static boolean broadcast(String message) {
        if (!canSend(message)) {
            return false;
        }
        ChatServer.getInstance().sendMessageToServerChat(message.strip());
        return true;
    }

    /**
     * Sends a server chat message to every player after checking the actor's capability.
     *
     * @param actor   player performing the action
     * @param message message text
     * @return {@code true} if the actor is allowed and the message was sent
     */
    public static boolean broadcast(IsoPlayer actor, String message) {
        return PermissionsManager.hasCapability(actor, Capability.DisplayServerMessage)
                && broadcast(message);
    }

    /**
     * Sends a server chat message to a specific player.
     *
     * @param player  target player
     * @param message message text
     * @return {@code true} if the message was sent
     */
    public static boolean sendMessage(IsoPlayer player, String message) {
        if (player == null) {
            return false;
        }
        return PlayerLookupUtils.getConnection(player)
                .map(connection -> sendMessage(connection, message))
                .orElse(false);
    }

    /**
     * Sends a server chat message to a player by username.
     *
     * @param username target username
     * @param message  message text
     * @return {@code true} if the player was found and the message was sent
     */
    public static boolean sendMessage(String username, String message) {
        return PlayerLookupUtils.getPlayerByUsername(username)
                .map(player -> sendMessage(player, message))
                .orElse(false);
    }

    /**
     * Sends a server chat message to a player after checking the actor's capability.
     *
     * @param actor   player performing the action
     * @param target  target player
     * @param message message text
     * @return {@code true} if the actor is allowed and the message was sent
     */
    public static boolean sendMessage(IsoPlayer actor, IsoPlayer target, String message) {
        return PermissionsManager.hasCapability(actor, Capability.DisplayServerMessage)
                && sendMessage(target, message);
    }

    /**
     * Sends a server chat message to a specific network connection.
     *
     * @param connection target connection
     * @param message    message text
     * @return {@code true} if the message was sent
     */
    public static boolean sendMessage(UdpConnection connection, String message) {
        if (connection == null || !canSend(message)) {
            return false;
        }
        ChatServer.getInstance().sendMessageToServerChat(connection, message.strip());
        return true;
    }

    /**
     * Sends a highlighted server alert to every player.
     *
     * @param message alert text
     * @return {@code true} if the alert was sent
     */
    public static boolean broadcastAlert(String message) {
        if (!canSend(message)) {
            return false;
        }
        ChatServer.getInstance().sendServerAlertMessageToServerChat(message.strip());
        return true;
    }

    /**
     * Sends a highlighted server alert after checking the actor's capability.
     *
     * @param actor   player performing the action
     * @param message alert text
     * @return {@code true} if the actor is allowed and the alert was sent
     */
    public static boolean broadcastAlert(IsoPlayer actor, String message) {
        return PermissionsManager.hasCapability(actor, Capability.DisplayServerMessage)
                && broadcastAlert(message);
    }

    /**
     * Sends a highlighted server alert authored by a specific name.
     *
     * @param author  alert author
     * @param message alert text
     * @return {@code true} if the alert was sent
     */
    public static boolean broadcastAlert(String author, String message) {
        if (author == null || author.isBlank() || !canSend(message)) {
            return false;
        }
        ChatServer.getInstance().sendServerAlertMessageToServerChat(author.strip(), message.strip());
        return true;
    }

    /**
     * Sends a message to the admin chat.
     *
     * @param message message text
     * @return {@code true} if the message was sent
     */
    public static boolean sendAdminMessage(String message) {
        if (!canSend(message)) {
            return false;
        }
        ChatServer.getInstance().sendMessageToAdminChat(message.strip());
        return true;
    }

    /**
     * Sends a message to the admin chat after checking the actor's capability.
     *
     * @param actor   player performing the action
     * @param message message text
     * @return {@code true} if the actor is allowed and the message was sent
     */
    public static boolean sendAdminMessage(IsoPlayer actor, String message) {
        return PermissionsManager.hasCapability(actor, Capability.AdminChat)
                && sendAdminMessage(message);
    }

    /**
     * Validates chat availability and message text.
     *
     * @param message message text
     * @return {@code true} if the message can be sent
     */
    private static boolean canSend(String message) {
        return isAvailable() && message != null && !message.isBlank();
    }
}
