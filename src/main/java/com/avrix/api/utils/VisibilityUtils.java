package com.avrix.api.utils;

import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoGridSquareCollisionData;
import zombie.iso.IsoObject;
import zombie.iso.IsoWorld;
import zombie.iso.LosUtil;
import zombie.core.math.PZMath;

import java.util.Optional;

/**
 * Raycast and line-of-sight helpers for world positions.
 * <p>
 * Closed doors and solid objects block sight. Open doors and windows are treated as clear.
 */
public final class VisibilityUtils {

    private VisibilityUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Checks whether there is a clear line of sight between two world objects.
     *
     * @param from source object
     * @param to   target object
     * @return {@code true} if sight is not blocked
     */
    public static boolean hasLineOfSight(IsoObject from, IsoObject to) {
        if (from == null || to == null) {
            return false;
        }

        IsoCell cell = resolveCell(from);
        return cell != null && hasLineOfSight(
                cell, from.getX(), from.getY(), from.getZ(), to.getX(), to.getY(), to.getZ());
    }

    /**
     * Checks whether there is a clear line of sight between two points.
     *
     * @param fromX source X
     * @param fromY source Y
     * @param fromZ source Z
     * @param toX   target X
     * @param toY   target Y
     * @param toZ   target Z
     * @return {@code true} if sight is not blocked
     */
    public static boolean hasLineOfSight(
            float fromX, float fromY, float fromZ, float toX, float toY, float toZ
    ) {
        IsoCell cell = IsoWorld.instance == null ? null : IsoWorld.instance.currentCell;
        return cell != null && hasLineOfSight(cell, fromX, fromY, fromZ, toX, toY, toZ);
    }

    /**
     * Checks whether there is a clear line of sight between two points in a cell.
     *
     * @param cell  world cell
     * @param fromX source X
     * @param fromY source Y
     * @param fromZ source Z
     * @param toX   target X
     * @param toY   target Y
     * @param toZ   target Z
     * @return {@code true} if sight is not blocked
     */
    public static boolean hasLineOfSight(
            IsoCell cell, float fromX, float fromY, float fromZ, float toX, float toY, float toZ
    ) {
        if (cell == null) {
            return false;
        }

        LosUtil.TestResults result = LosUtil.lineClear(
                cell,
                PZMath.fastfloor(fromX),
                PZMath.fastfloor(fromY),
                PZMath.fastfloor(fromZ),
                PZMath.fastfloor(toX),
                PZMath.fastfloor(toY),
                PZMath.fastfloor(toZ),
                false
        );
        return result != LosUtil.TestResults.Blocked
                && result != LosUtil.TestResults.ClearThroughClosedDoor;
    }

    /**
     * Casts a ray between two world objects and returns the first blocking square.
     *
     * @param from source object
     * @param to   target object
     * @return first blocking square, or empty when the path is clear
     */
    public static Optional<RaycastHit> raycast(IsoObject from, IsoObject to) {
        if (from == null || to == null) {
            return Optional.empty();
        }

        IsoCell cell = resolveCell(from);
        return cell == null
                ? Optional.empty()
                : raycast(cell, from.getX(), from.getY(), from.getZ(), to.getX(), to.getY(), to.getZ());
    }

    /**
     * Casts a ray between two points and returns the first blocking square.
     *
     * @param fromX source X
     * @param fromY source Y
     * @param fromZ source Z
     * @param toX   target X
     * @param toY   target Y
     * @param toZ   target Z
     * @return first blocking square, or empty when the path is clear
     */
    public static Optional<RaycastHit> raycast(
            float fromX, float fromY, float fromZ, float toX, float toY, float toZ
    ) {
        IsoCell cell = IsoWorld.instance == null ? null : IsoWorld.instance.currentCell;
        return cell == null
                ? Optional.empty()
                : raycast(cell, fromX, fromY, fromZ, toX, toY, toZ);
    }

    /**
     * Casts a ray between two points in a cell and returns the first blocking square.
     *
     * @param cell  world cell
     * @param fromX source X
     * @param fromY source Y
     * @param fromZ source Z
     * @param toX   target X
     * @param toY   target Y
     * @param toZ   target Z
     * @return first blocking square, or empty when the path is clear
     */
    public static Optional<RaycastHit> raycast(
            IsoCell cell, float fromX, float fromY, float fromZ, float toX, float toY, float toZ
    ) {
        if (cell == null) {
            return Optional.empty();
        }

        IsoGridSquareCollisionData collision = LosUtil.getFirstBlockingIsoGridSquare(
                cell,
                PZMath.fastfloor(fromX),
                PZMath.fastfloor(fromY),
                PZMath.fastfloor(fromZ),
                PZMath.fastfloor(toX),
                PZMath.fastfloor(toY),
                PZMath.fastfloor(toZ),
                false
        );
        return collision == null
                ? Optional.empty()
                : Optional.of(new RaycastHit(
                        collision.isoGridSquare,
                        collision.hitPosition.x,
                        collision.hitPosition.y,
                        collision.hitPosition.z,
                        collision.testResults
                ));
    }

    /**
     * Resolves the cell owning an object.
     *
     * @param object source object
     * @return owning cell, or {@code null}
     */
    private static IsoCell resolveCell(IsoObject object) {
        IsoCell cell = object.getCell();
        if (cell == null && IsoWorld.instance != null) {
            cell = IsoWorld.instance.currentCell;
        }
        return cell;
    }

    /**
     * Result of a blocked raycast.
     *
     * @param square    first blocking square
     * @param x         hit X
     * @param y         hit Y
     * @param z         hit Z
     * @param testResult vanilla line-of-sight result
     */
    public record RaycastHit(
            IsoGridSquare square,
            float x,
            float y,
            float z,
            LosUtil.TestResults testResult
    ) {
    }
}
