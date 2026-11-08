package com.avrix.api.utils;

import com.avrix.api.permissions.PermissionsManager;
import zombie.characters.Capability;
import zombie.characters.IsoPlayer;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoMovingObject;
import zombie.iso.IsoObject;
import zombie.iso.IsoWorld;
import zombie.network.GameServer;

/**
 * Teleports players and world objects to coordinates or grid squares.
 * <p>
 * Moving entities are updated in place and, for online players, synchronized through
 * the vanilla teleport packet. Static world objects are moved between grid squares.
 */
public final class TeleportUtils {

    private TeleportUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Teleports a player to the given coordinates.
     *
     * @param player target player
     * @param x      destination X
     * @param y      destination Y
     * @param z      destination Z
     * @return {@code true} if the destination square exists and the player was moved
     */
    public static boolean teleportPlayer(IsoPlayer player, float x, float y, float z) {
        if (player == null) {
            return false;
        }

        IsoGridSquare square = findSquare(player, x, y, z);
        if (square == null) {
            return false;
        }

        player.setPosition(x, y, z);
        player.setSquare(square);
        player.setCurrentSquare(square);
        if (GameServer.server && PlayerLookupUtils.getConnection(player).isPresent()) {
            GameServer.sendTeleport(player, x, y, z);
        }
        return true;
    }

    /**
     * Teleports a player to a grid square.
     *
     * @param player target player
     * @param square destination square
     * @return {@code true} if the player was moved
     */
    public static boolean teleportPlayer(IsoPlayer player, IsoGridSquare square) {
        if (square == null) {
            return false;
        }
        return teleportPlayer(player, square.getX() + 0.5f, square.getY() + 0.5f, square.getZ());
    }

    /**
     * Teleports a player to another player's position.
     *
     * @param player      target player
     * @param destination destination player
     * @return {@code true} if the player was moved
     */
    public static boolean teleportPlayerToPlayer(IsoPlayer player, IsoPlayer destination) {
        if (destination == null) {
            return false;
        }
        return teleportPlayer(player, destination.getX(), destination.getY(), destination.getZ());
    }

    /**
     * Teleports a player after checking the actor's teleport capability.
     *
     * @param actor  player performing the action
     * @param target target player
     * @param x      destination X
     * @param y      destination Y
     * @param z      destination Z
     * @return {@code true} if the actor is allowed and the player was moved
     */
    public static boolean teleportPlayer(IsoPlayer actor, IsoPlayer target, float x, float y, float z) {
        Capability capability = actor == target
                ? Capability.TeleportToCoordinates
                : Capability.TeleportPlayerToAnotherPlayer;
        if (!PermissionsManager.hasCapability(actor, capability)) {
            return false;
        }
        return teleportPlayer(target, x, y, z);
    }

    /**
     * Teleports a player to another player after checking the actor's capability.
     *
     * @param actor       player performing the action
     * @param target      target player
     * @param destination destination player
     * @return {@code true} if the actor is allowed and the player was moved
     */
    public static boolean teleportPlayerToPlayer(IsoPlayer actor, IsoPlayer target, IsoPlayer destination) {
        Capability capability = actor == target
                ? Capability.TeleportToPlayer
                : Capability.TeleportPlayerToAnotherPlayer;
        if (!PermissionsManager.hasCapability(actor, capability)) {
            return false;
        }
        return teleportPlayerToPlayer(target, destination);
    }

    /**
     * Teleports a world object to the given coordinates.
     * <p>
     * Moving objects are repositioned directly. Static objects are removed from their
     * current square and added to the destination square.
     *
     * @param object target object
     * @param x      destination X
     * @param y      destination Y
     * @param z      destination Z
     * @return {@code true} if the object was moved
     */
    public static boolean teleportObject(IsoObject object, float x, float y, float z) {
        if (object == null) {
            return false;
        }

        IsoGridSquare square = findSquare(object, x, y, z);
        if (square == null) {
            return false;
        }

        if (object instanceof IsoMovingObject moving) {
            moving.setPosition(x, y, z);
            moving.setSquare(square);
            moving.setCurrentSquare(square);
            return true;
        }

        object.removeFromSquare();
        object.setSquare(square);
        square.getObjects().add(object);
        return true;
    }

    /**
     * Teleports a world object to a grid square.
     *
     * @param object target object
     * @param square destination square
     * @return {@code true} if the object was moved
     */
    public static boolean teleportObject(IsoObject object, IsoGridSquare square) {
        if (square == null) {
            return false;
        }
        return teleportObject(object, square.getX() + 0.5f, square.getY() + 0.5f, square.getZ());
    }

    /**
     * Teleports a world object after checking the actor's teleport capability.
     *
     * @param actor  player performing the action
     * @param object target object
     * @param x      destination X
     * @param y      destination Y
     * @param z      destination Z
     * @return {@code true} if the actor is allowed and the object was moved
     */
    public static boolean teleportObject(IsoPlayer actor, IsoObject object, float x, float y, float z) {
        return PermissionsManager.hasCapability(actor, Capability.TeleportToCoordinates)
                && teleportObject(object, x, y, z);
    }

    /**
     * Finds a loaded destination square for an object.
     *
     * @param object object used to resolve the current cell
     * @param x      destination X
     * @param y      destination Y
     * @param z      destination Z
     * @return destination square, or {@code null} when the area is not loaded
     */
    private static IsoGridSquare findSquare(IsoObject object, float x, float y, float z) {
        IsoCell cell = object.getCell();
        if (cell == null && IsoWorld.instance != null) {
            cell = IsoWorld.instance.currentCell;
        }
        if (cell == null && IsoCell.getInstance() != null) {
            cell = IsoCell.getInstance();
        }
        return cell == null ? null : cell.getGridSquare(x, y, z);
    }
}
