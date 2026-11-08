package com.avrix.api.utils;

import com.avrix.api.permissions.PermissionsManager;
import zombie.characters.Capability;
import zombie.characters.Faction;
import zombie.characters.IsoPlayer;
import zombie.network.GameServer;
import zombie.network.PacketTypes;
import zombie.network.chat.ChatServer;
import zombie.network.packets.INetworkPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-side helpers for creating, querying and modifying factions.
 * <p>
 * Changes are broadcast through the vanilla faction synchronization packet.
 * Actor-aware overloads require the {@link Capability#FactionCheat} capability.
 */
public final class FactionUtils {

    private FactionUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Returns all registered factions.
     *
     * @return immutable faction snapshot
     */
    public static List<Faction> getFactions() {
        ArrayList<Faction> factions = Faction.getFactions();
        return factions == null ? List.of() : List.copyOf(factions);
    }

    /**
     * Finds a faction by name.
     *
     * @param name faction name
     * @return matching faction, or {@code null}
     */
    public static Faction getByName(String name) {
        return name == null || name.isBlank() ? null : Faction.getFaction(name.strip());
    }

    /**
     * Returns the faction of a player, whether they are the owner or a member.
     *
     * @param player target player
     * @return player faction, or {@code null}
     */
    public static Faction getForPlayer(IsoPlayer player) {
        return player == null ? null : Faction.getPlayerFaction(player);
    }

    /**
     * Returns the faction of a player by username.
     *
     * @param username target username
     * @return player faction, or {@code null}
     */
    public static Faction getForPlayer(String username) {
        return username == null || username.isBlank() ? null : Faction.getPlayerFaction(username.strip());
    }

    /**
     * Creates a faction and synchronizes it with clients.
     *
     * @param name  faction name
     * @param owner owner username
     * @return created faction, or {@code null} when the name is invalid or already used
     */
    public static Faction create(String name, String owner) {
        if (name == null || owner == null) {
            return null;
        }

        String cleanName = name.strip();
        String cleanOwner = owner.strip();
        if (cleanName.length() < 3 || cleanName.length() > 15
                || cleanOwner.isEmpty() || Faction.factionExist(cleanName)) {
            return null;
        }

        Faction faction = new Faction(cleanName, cleanOwner);
        Faction.getFactions().add(faction);
        sync(faction);
        registerChat(faction);
        return faction;
    }

    /**
     * Creates a faction after checking the actor's capability.
     *
     * @param actor player performing the action
     * @param name  faction name
     * @param owner owner username
     * @return created faction, or {@code null} when the actor is not allowed
     */
    public static Faction create(IsoPlayer actor, String name, String owner) {
        return PermissionsManager.hasCapability(actor, Capability.FactionCheat)
                ? create(name, owner)
                : null;
    }

    /**
     * Adds a player to a faction.
     *
     * @param faction  target faction
     * @param username player username
     * @return {@code true} if the member list changed
     */
    public static boolean addMember(Faction faction, String username) {
        if (faction == null || username == null || username.isBlank() || isMember(faction, username)) {
            return false;
        }
        faction.addPlayer(username.strip());
        sync(faction);
        return true;
    }

    /**
     * Adds a player to a faction after checking the actor's capability.
     *
     * @param actor    player performing the action
     * @param faction  target faction
     * @param username player username
     * @return {@code true} if the actor is allowed and the member was added
     */
    public static boolean addMember(IsoPlayer actor, Faction faction, String username) {
        return PermissionsManager.hasCapability(actor, Capability.FactionCheat)
                && addMember(faction, username);
    }

    /**
     * Removes a player from a faction.
     *
     * @param faction  target faction
     * @param username player username
     * @return {@code true} if the member list changed
     */
    public static boolean removeMember(Faction faction, String username) {
        if (faction == null || username == null || username.isBlank()
                || faction.isOwner(username) || !isMember(faction, username)) {
            return false;
        }
        faction.removePlayer(username.strip());
        sync(faction);
        return true;
    }

    /**
     * Removes a player from a faction after checking the actor's capability.
     *
     * @param actor    player performing the action
     * @param faction  target faction
     * @param username player username
     * @return {@code true} if the actor is allowed and the member was removed
     */
    public static boolean removeMember(IsoPlayer actor, Faction faction, String username) {
        return PermissionsManager.hasCapability(actor, Capability.FactionCheat)
                && removeMember(faction, username);
    }

    /**
     * Checks whether a username is the owner or a member of a faction.
     *
     * @param faction  target faction
     * @param username player username
     * @return {@code true} if the player belongs to the faction
     */
    public static boolean isMember(Faction faction, String username) {
        return faction != null && username != null
                && (faction.isOwner(username) || faction.getPlayers().contains(username));
    }

    /**
     * Checks whether two players are members of the same faction.
     *
     * @param first  first player
     * @param second second player
     * @return {@code true} if both players share a faction
     */
    public static boolean areInSameFaction(IsoPlayer first, IsoPlayer second) {
        return first != null && second != null && Faction.isInSameFaction(first, second);
    }

    /**
     * Sets a faction tag and synchronizes it with clients.
     *
     * @param faction target faction
     * @param tag     new tag, or {@code null} to clear it
     * @return {@code true} if the tag was updated
     */
    public static boolean setTag(Faction faction, String tag) {
        if (faction == null) {
            return false;
        }
        faction.setTag(tag == null || tag.isBlank() ? null : tag.strip());
        sync(faction);
        return true;
    }

    /**
     * Synchronizes a faction with connected clients and its chat room.
     *
     * @param faction target faction
     */
    public static void sync(Faction faction) {
        if (faction == null) {
            return;
        }
        if (GameServer.server && GameServer.udpEngine != null) {
            INetworkPacket.sendToAll(PacketTypes.PacketType.FactionSync, faction);
        }
        if (ChatServer.isInited()) {
            ChatServer.getInstance().syncFactionChatMembers(
                    faction.getName(), faction.getOwner(), faction.getPlayers());
        }
    }

    /**
     * Creates the faction chat room and synchronizes its members.
     *
     * @param faction target faction
     */
    private static void registerChat(Faction faction) {
        if (!ChatServer.isInited()) {
            return;
        }
        ChatServer.getInstance().createFactionChat(faction.getName());
        ChatServer.getInstance().syncFactionChatMembers(
                faction.getName(), faction.getOwner(), faction.getPlayers());
    }
}
