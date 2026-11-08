package com.avrix.api.utils;

import com.avrix.api.permissions.PermissionsManager;
import zombie.characters.Capability;
import zombie.characters.IsoPlayer;
import zombie.iso.IsoGridSquare;
import zombie.iso.areas.SafeHouse;
import zombie.network.GameServer;
import zombie.network.PacketTypes;
import zombie.network.chat.ChatServer;
import zombie.network.packets.INetworkPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-side helpers for creating, querying and modifying safehouses.
 * <p>
 * Changes are synchronized to connected clients through the vanilla safehouse packets.
 * Actor-aware overloads require the {@link Capability#CanSetupSafehouses} capability.
 */
public final class SafehouseUtils {

    private SafehouseUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Returns all registered safehouses.
     *
     * @return immutable safehouse snapshot
     */
    public static List<SafeHouse> getSafehouses() {
        ArrayList<SafeHouse> safehouses = SafeHouse.getSafehouseList();
        return safehouses == null ? List.of() : List.copyOf(safehouses);
    }

    /**
     * Finds a safehouse owned by the given username.
     *
     * @param username owner username
     * @return matching safehouse, or {@code null}
     */
    public static SafeHouse getByOwner(String username) {
        return username == null || username.isBlank() ? null : SafeHouse.getSafehouseByOwner(username.strip());
    }

    /**
     * Finds a safehouse by its title.
     *
     * @param title safehouse title
     * @return matching safehouse, or {@code null}
     */
    public static SafeHouse getByTitle(String title) {
        return title == null || title.isBlank() ? null : SafeHouse.getSafeHouse(title.strip());
    }

    /**
     * Finds the safehouse containing a grid square.
     *
     * @param square target square
     * @return safehouse, or {@code null}
     */
    public static SafeHouse getAt(IsoGridSquare square) {
        return square == null ? null : SafeHouse.getSafeHouse(square);
    }

    /**
     * Finds the safehouse containing a world position.
     *
     * @param x world X
     * @param y world Y
     * @return safehouse, or {@code null}
     */
    public static SafeHouse getAt(float x, float y) {
        for (SafeHouse safehouse : getSafehouses()) {
            if (safehouse.containsLocation(x, y)) {
                return safehouse;
            }
        }
        return null;
    }

    /**
     * Returns the safehouse of a player, whether they are the owner or a member.
     *
     * @param player target player
     * @return safehouse, or {@code null}
     */
    public static SafeHouse getForPlayer(IsoPlayer player) {
        return player == null ? null : SafeHouse.hasSafehouse(player);
    }

    /**
     * Creates a safehouse for a player.
     *
     * @param owner owner player
     * @param x     safehouse X
     * @param y     safehouse Y
     * @param w     safehouse width
     * @param h     safehouse height
     * @param title display title
     * @return created safehouse, or {@code null} when creation failed
     */
    public static SafeHouse create(IsoPlayer owner, int x, int y, int w, int h, String title) {
        if (owner == null || owner.getUsername() == null || w <= 0 || h <= 0) {
            return null;
        }

        SafeHouse safehouse = SafeHouse.addSafeHouse(x, y, w, h, owner.getUsername());
        if (safehouse == null) {
            return null;
        }
        if (title != null && !title.isBlank()) {
            safehouse.setTitle(title.strip());
        }
        sync(safehouse);
        registerChat(safehouse);
        return safehouse;
    }

    /**
     * Creates a safehouse for a player after checking the actor's capability.
     *
     * @param actor player performing the action
     * @param owner owner player
     * @param x     safehouse X
     * @param y     safehouse Y
     * @param w     safehouse width
     * @param h     safehouse height
     * @param title display title
     * @return created safehouse, or {@code null} when the actor is not allowed
     */
    public static SafeHouse create(IsoPlayer actor, IsoPlayer owner, int x, int y, int w, int h, String title) {
        return PermissionsManager.hasCapability(actor, Capability.CanSetupSafehouses)
                ? create(owner, x, y, w, h, title)
                : null;
    }

    /**
     * Removes a safehouse.
     *
     * @param safehouse target safehouse
     * @return {@code true} if the safehouse was removed
     */
    public static boolean remove(SafeHouse safehouse) {
        if (safehouse == null) {
            return false;
        }
        String chatId = safehouse.getId();
        SafeHouse.removeSafeHouse(safehouse);
        if (GameServer.server && GameServer.udpEngine != null) {
            INetworkPacket.sendToAll(PacketTypes.PacketType.SafehouseRelease, safehouse);
        }
        if (chatId != null && ChatServer.isInited()) {
            ChatServer.getInstance().removeSafehouseChat(chatId);
        }
        return true;
    }

    /**
     * Removes a safehouse after checking the actor's capability.
     *
     * @param actor     player performing the action
     * @param safehouse target safehouse
     * @return {@code true} if the actor is allowed and the safehouse was removed
     */
    public static boolean remove(IsoPlayer actor, SafeHouse safehouse) {
        return PermissionsManager.hasCapability(actor, Capability.CanSetupSafehouses)
                && remove(safehouse);
    }

    /**
     * Adds a player to a safehouse.
     *
     * @param safehouse target safehouse
     * @param username  player username
     * @return {@code true} if the member list changed
     */
    public static boolean addMember(SafeHouse safehouse, String username) {
        if (safehouse == null || username == null || username.isBlank() || isMember(safehouse, username)) {
            return false;
        }
        safehouse.addPlayer(username.strip());
        sync(safehouse);
        return true;
    }

    /**
     * Adds a player to a safehouse after checking the actor's capability.
     *
     * @param actor     player performing the action
     * @param safehouse target safehouse
     * @param username  player username
     * @return {@code true} if the actor is allowed and the member was added
     */
    public static boolean addMember(IsoPlayer actor, SafeHouse safehouse, String username) {
        return PermissionsManager.hasCapability(actor, Capability.CanSetupSafehouses)
                && addMember(safehouse, username);
    }

    /**
     * Removes a player from a safehouse without moving them out.
     *
     * @param safehouse target safehouse
     * @param username  player username
     * @return {@code true} if the member list changed
     */
    public static boolean removeMember(SafeHouse safehouse, String username) {
        if (safehouse == null || username == null || username.isBlank() || !isMember(safehouse, username)) {
            return false;
        }
        safehouse.removePlayer(username.strip());
        sync(safehouse);
        return true;
    }

    /**
     * Removes a player from a safehouse after checking the actor's capability.
     *
     * @param actor     player performing the action
     * @param safehouse target safehouse
     * @param username  player username
     * @return {@code true} if the actor is allowed and the member was removed
     */
    public static boolean removeMember(IsoPlayer actor, SafeHouse safehouse, String username) {
        return PermissionsManager.hasCapability(actor, Capability.CanSetupSafehouses)
                && removeMember(safehouse, username);
    }

    /**
     * Kicks a player from a safehouse and teleports them outside when necessary.
     *
     * @param safehouse target safehouse
     * @param username  player username
     * @return {@code true} if the player was kicked
     */
    public static boolean kickMember(SafeHouse safehouse, String username) {
        if (safehouse == null || username == null || username.isBlank() || !isMember(safehouse, username)) {
            return false;
        }
        SafeHouse.kickUserFromSafehouse(safehouse, username.strip());
        syncChat(safehouse);
        return true;
    }

    /**
     * Kicks a player from a safehouse after checking the actor's capability.
     *
     * @param actor     player performing the action
     * @param safehouse target safehouse
     * @param username  player username
     * @return {@code true} if the actor is allowed and the player was kicked
     */
    public static boolean kickMember(IsoPlayer actor, SafeHouse safehouse, String username) {
        return PermissionsManager.hasCapability(actor, Capability.CanSetupSafehouses)
                && kickMember(safehouse, username);
    }

    /**
     * Checks whether a username is the owner or a member of a safehouse.
     *
     * @param safehouse target safehouse
     * @param username  player username
     * @return {@code true} if the player belongs to the safehouse
     */
    public static boolean isMember(SafeHouse safehouse, String username) {
        return safehouse != null
                && username != null
                && (safehouse.isOwner(username) || safehouse.getPlayers().contains(username));
    }

    /**
     * Checks whether a player is allowed to enter and use a safehouse.
     *
     * @param safehouse target safehouse
     * @param player    target player
     * @return {@code true} if the player is allowed
     */
    public static boolean isAllowed(SafeHouse safehouse, IsoPlayer player) {
        return safehouse != null && player != null && safehouse.playerAllowed(player);
    }

    /**
     * Checks whether a player meets the vanilla requirements to claim a safehouse.
     *
     * @param player target player
     * @return {@code true} if the player can claim a safehouse
     */
    public static boolean canClaim(IsoPlayer player) {
        return player != null && SafeHouse.allowSafeHouse(player);
    }

    /**
     * Returns the reason why a square cannot be claimed as a safehouse.
     *
     * @param square target square
     * @param player claiming player
     * @return rejection reason, or an empty string when the square can be claimed
     */
    public static String getClaimRejection(IsoGridSquare square, IsoPlayer player) {
        return square == null || player == null ? "Invalid safehouse claim" : SafeHouse.canBeSafehouse(square, player);
    }

    /**
     * Synchronizes a safehouse with connected clients and its chat room.
     *
     * @param safehouse target safehouse
     */
    public static void sync(SafeHouse safehouse) {
        if (safehouse == null) {
            return;
        }
        if (GameServer.server && GameServer.udpEngine != null) {
            INetworkPacket.sendToAll(PacketTypes.PacketType.SafehouseSync, safehouse);
        }
        syncChat(safehouse);
    }

    /**
     * Creates the safehouse chat room and synchronizes its members.
     *
     * @param safehouse target safehouse
     */
    private static void registerChat(SafeHouse safehouse) {
        if (!ChatServer.isInited()) {
            return;
        }
        ChatServer.getInstance().createSafehouseChat(safehouse.getId());
        syncChat(safehouse);
    }

    /**
     * Synchronizes safehouse chat members.
     *
     * @param safehouse target safehouse
     */
    private static void syncChat(SafeHouse safehouse) {
        if (!ChatServer.isInited() || safehouse == null) {
            return;
        }
        ChatServer.getInstance().syncSafehouseChatMembers(
                safehouse.getId(), safehouse.getOwner(), safehouse.getPlayers());
    }
}
