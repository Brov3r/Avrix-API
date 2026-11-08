package com.avrix.api.utils;

import com.avrix.api.permissions.PermissionsManager;
import zombie.characters.Capability;
import zombie.characters.IsoPlayer;
import zombie.characters.IsoZombie;
import zombie.iso.IsoCell;
import zombie.iso.IsoWorld;
import zombie.network.GameServer;
import zombie.popman.ZombiePopulationManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Helpers for querying and controlling the zombie population.
 * <p>
 * Actor-aware overloads require {@link Capability#CreateHorde},
 * {@link Capability#PopmanManage} or {@link Capability#ManipulateZombie}
 * depending on the operation.
 */
public final class ZombiePopulationUtils {

    private ZombiePopulationUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Returns the loaded zombie entities in the current cell.
     *
     * @return immutable zombie snapshot
     */
    public static List<IsoZombie> getLoadedZombies() {
        IsoCell cell = getCell();
        if (cell == null) {
            return List.of();
        }

        List<IsoZombie> snapshot = new ArrayList<>();
        for (IsoZombie zombie : cell.getZombieList()) {
            if (zombie != null) {
                snapshot.add(zombie);
            }
        }
        return List.copyOf(snapshot);
    }

    /**
     * Returns the number of loaded zombie entities.
     *
     * @return loaded zombie count
     */
    public static int getLoadedZombieCount() {
        IsoCell cell = getCell();
        return cell == null ? 0 : cell.getZombieList().size();
    }

    /**
     * Checks whether zombies are enabled on this world.
     *
     * @return {@code true} if zombies are enabled
     */
    public static boolean isZombiesEnabled() {
        return IsoWorld.getZombiesEnabled();
    }

    /**
     * Sets the minimum zombie count per chunk.
     *
     * @param count minimum count; negative values are ignored
     * @return {@code true} if the value was applied
     */
    public static boolean setMinPerChunk(float count) {
        if (count < 0.0f || !isServerReady()) {
            return false;
        }
        ZombiePopulationManager.instance.setZombiesMinPerChunk(count);
        return true;
    }

    /**
     * Sets the minimum zombie count per chunk after checking the actor's capability.
     *
     * @param actor player performing the action
     * @param count minimum count
     * @return {@code true} if the actor is allowed and the value was applied
     */
    public static boolean setMinPerChunk(IsoPlayer actor, float count) {
        return PermissionsManager.hasCapability(actor, Capability.PopmanManage)
                && setMinPerChunk(count);
    }

    /**
     * Sets the maximum zombie count per chunk.
     *
     * @param count maximum count; negative values are ignored
     * @return {@code true} if the value was applied
     */
    public static boolean setMaxPerChunk(float count) {
        if (count < 0.0f || !isServerReady()) {
            return false;
        }
        ZombiePopulationManager.instance.setZombiesMaxPerChunk(count);
        return true;
    }

    /**
     * Sets the maximum zombie count per chunk after checking the actor's capability.
     *
     * @param actor player performing the action
     * @param count maximum count
     * @return {@code true} if the actor is allowed and the value was applied
     */
    public static boolean setMaxPerChunk(IsoPlayer actor, float count) {
        return PermissionsManager.hasCapability(actor, Capability.PopmanManage)
                && setMaxPerChunk(count);
    }

    /**
     * Creates a horde that moves from a spawn area to a target point.
     *
     * @param spawnX  spawn X
     * @param spawnY  spawn Y
     * @param targetX target X
     * @param targetY target Y
     * @param count   zombie count
     * @return {@code true} if the horde was created
     */
    public static boolean createHorde(int spawnX, int spawnY, int targetX, int targetY, int count) {
        if (!isServerReady() || count <= 0) {
            return false;
        }
        ZombiePopulationManager.instance.createHordeFromTo(spawnX, spawnY, targetX, targetY, count);
        return true;
    }

    /**
     * Creates a horde after checking the actor's capability.
     *
     * @param actor   player performing the action
     * @param spawnX  spawn X
     * @param spawnY  spawn Y
     * @param targetX target X
     * @param targetY target Y
     * @param count   zombie count
     * @return {@code true} if the actor is allowed and the horde was created
     */
    public static boolean createHorde(
            IsoPlayer actor, int spawnX, int spawnY, int targetX, int targetY, int count
    ) {
        return PermissionsManager.hasCapability(actor, Capability.CreateHorde)
                && createHorde(spawnX, spawnY, targetX, targetY, count);
    }

    /**
     * Creates a horde from a rectangular spawn area to a target point.
     *
     * @param spawnX  spawn area X
     * @param spawnY  spawn area Y
     * @param spawnW  spawn area width
     * @param spawnH  spawn area height
     * @param targetX target X
     * @param targetY target Y
     * @param count   zombie count
     * @return {@code true} if the horde was created
     */
    public static boolean createHordeInArea(
            int spawnX, int spawnY, int spawnW, int spawnH, int targetX, int targetY, int count
    ) {
        if (!isServerReady() || count <= 0 || spawnW <= 0 || spawnH <= 0) {
            return false;
        }
        ZombiePopulationManager.instance.createHordeInAreaTo(
                spawnX, spawnY, spawnW, spawnH, targetX, targetY, count);
        return true;
    }

    /**
     * Creates a horde from an area after checking the actor's capability.
     *
     * @param actor   player performing the action
     * @param spawnX  spawn area X
     * @param spawnY  spawn area Y
     * @param spawnW  spawn area width
     * @param spawnH  spawn area height
     * @param targetX target X
     * @param targetY target Y
     * @param count   zombie count
     * @return {@code true} if the actor is allowed and the horde was created
     */
    public static boolean createHordeInArea(
            IsoPlayer actor, int spawnX, int spawnY, int spawnW, int spawnH,
            int targetX, int targetY, int count
    ) {
        return PermissionsManager.hasCapability(actor, Capability.CreateHorde)
                && createHordeInArea(spawnX, spawnY, spawnW, spawnH, targetX, targetY, count);
    }

    /**
     * Clears the zombie population data for a population cell.
     *
     * @param cellX population cell X
     * @param cellY population cell Y
     * @return {@code true} if the request was applied
     */
    public static boolean clearCell(int cellX, int cellY) {
        if (!isServerReady()) {
            return false;
        }
        ZombiePopulationManager.instance.dbgClearZombies(cellX, cellY);
        return true;
    }

    /**
     * Clears a population cell after checking the actor's capability.
     *
     * @param actor player performing the action
     * @param cellX population cell X
     * @param cellY population cell Y
     * @return {@code true} if the actor is allowed and the request was applied
     */
    public static boolean clearCell(IsoPlayer actor, int cellX, int cellY) {
        return PermissionsManager.hasCapability(actor, Capability.ManipulateZombie)
                && clearCell(cellX, cellY);
    }

    /**
     * Spawns the queued zombie population for a population cell immediately.
     *
     * @param cellX population cell X
     * @param cellY population cell Y
     * @return {@code true} if the request was applied
     */
    public static boolean spawnNow(int cellX, int cellY) {
        if (!isServerReady()) {
            return false;
        }
        ZombiePopulationManager.instance.dbgSpawnNow(cellX, cellY);
        return true;
    }

    /**
     * Spawns a population cell immediately after checking the actor's capability.
     *
     * @param actor player performing the action
     * @param cellX population cell X
     * @param cellY population cell Y
     * @return {@code true} if the actor is allowed and the request was applied
     */
    public static boolean spawnNow(IsoPlayer actor, int cellX, int cellY) {
        return PermissionsManager.hasCapability(actor, Capability.ManipulateZombie)
                && spawnNow(cellX, cellY);
    }

    /**
     * Resolves the current world cell.
     *
     * @return current cell, or {@code null}
     */
    private static IsoCell getCell() {
        return IsoWorld.instance == null ? null : IsoWorld.instance.currentCell;
    }

    /**
     * Checks whether the server world is ready for population changes.
     *
     * @return {@code true} if the server is running and a world is loaded
     */
    private static boolean isServerReady() {
        return GameServer.server && IsoWorld.instance != null;
    }
}
