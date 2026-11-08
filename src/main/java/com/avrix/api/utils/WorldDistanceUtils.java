package com.avrix.api.utils;

import zombie.characters.IsoPlayer;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Distance and radius helpers for Project Zomboid world coordinates and entities.
 * <p>
 * Two-dimensional methods ignore the vertical (floor) coordinate and are the usual
 * choice for gameplay ranges. Three-dimensional methods account for height.
 */
public final class WorldDistanceUtils {

    private WorldDistanceUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Calculates the horizontal distance between two points.
     *
     * @param fromX first point X
     * @param fromY first point Y
     * @param toX   second point X
     * @param toY   second point Y
     * @return horizontal distance
     */
    public static float distance2D(float fromX, float fromY, float toX, float toY) {
        return (float) Math.hypot(toX - fromX, toY - fromY);
    }

    /**
     * Calculates the three-dimensional distance between two points.
     *
     * @param fromX first point X
     * @param fromY first point Y
     * @param fromZ first point Z
     * @param toX   second point X
     * @param toY   second point Y
     * @param toZ   second point Z
     * @return three-dimensional distance
     */
    public static float distance3D(float fromX, float fromY, float fromZ, float toX, float toY, float toZ) {
        double dx = toX - fromX;
        double dy = toY - fromY;
        double dz = toZ - fromZ;
        return (float) Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
    }

    /**
     * Calculates the squared horizontal distance between two points.
     * <p>
     * Squared distances are cheaper for comparisons and avoid a square root.
     *
     * @param fromX first point X
     * @param fromY first point Y
     * @param toX   second point X
     * @param toY   second point Y
     * @return squared horizontal distance
     */
    public static float distanceSquared2D(float fromX, float fromY, float toX, float toY) {
        float dx = toX - fromX;
        float dy = toY - fromY;
        return (dx * dx) + (dy * dy);
    }

    /**
     * Calculates the squared three-dimensional distance between two points.
     *
     * @param fromX first point X
     * @param fromY first point Y
     * @param fromZ first point Z
     * @param toX   second point X
     * @param toY   second point Y
     * @param toZ   second point Z
     * @return squared three-dimensional distance
     */
    public static float distanceSquared3D(float fromX, float fromY, float fromZ, float toX, float toY, float toZ) {
        float dx = toX - fromX;
        float dy = toY - fromY;
        float dz = toZ - fromZ;
        return (dx * dx) + (dy * dy) + (dz * dz);
    }

    /**
     * Calculates the Manhattan (taxicab) distance between two points on a plane.
     *
     * @param fromX first point X
     * @param fromY first point Y
     * @param toX   second point X
     * @param toY   second point Y
     * @return Manhattan distance
     */
    public static float distanceManhattan2D(float fromX, float fromY, float toX, float toY) {
        return Math.abs(toX - fromX) + Math.abs(toY - fromY);
    }

    /**
     * Calculates the horizontal distance between two world objects.
     *
     * @param from first object
     * @param to   second object
     * @return horizontal distance
     * @throws NullPointerException if either object is {@code null}
     */
    public static float distance2D(IsoObject from, IsoObject to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        return distance2D(from.getX(), from.getY(), to.getX(), to.getY());
    }

    /**
     * Calculates the squared horizontal distance between two world objects.
     *
     * @param from first object
     * @param to   second object
     * @return squared horizontal distance
     * @throws NullPointerException if either object is {@code null}
     */
    public static float distanceSquared2D(IsoObject from, IsoObject to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        return distanceSquared2D(from.getX(), from.getY(), to.getX(), to.getY());
    }

    /**
     * Calculates the three-dimensional distance between two world objects.
     *
     * @param from first object
     * @param to   second object
     * @return three-dimensional distance
     * @throws NullPointerException if either object is {@code null}
     */
    public static float distance3D(IsoObject from, IsoObject to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        return distance3D(from.getX(), from.getY(), from.getZ(), to.getX(), to.getY(), to.getZ());
    }

    /**
     * Calculates the horizontal distance between a world object and a grid square.
     *
     * @param object source object
     * @param square target square
     * @return horizontal distance
     * @throws NullPointerException if either argument is {@code null}
     */
    public static float distance2D(IsoObject object, IsoGridSquare square) {
        Objects.requireNonNull(object, "object");
        Objects.requireNonNull(square, "square");
        return distance2D(object.getX(), object.getY(), square.getX(), square.getY());
    }

    /**
     * Calculates the horizontal distance between two grid squares.
     *
     * @param from source square
     * @param to   target square
     * @return horizontal distance
     * @throws NullPointerException if either square is {@code null}
     */
    public static float distance2D(IsoGridSquare from, IsoGridSquare to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        return distance2D(from.getX(), from.getY(), to.getX(), to.getY());
    }

    /**
     * Checks whether two world objects are within the given horizontal radius.
     *
     * @param from   first object
     * @param to     second object
     * @param radius maximum distance
     * @return {@code true} if both objects are non-null and the distance does not exceed the radius
     */
    public static boolean isWithinRadius(IsoObject from, IsoObject to, float radius) {
        return from != null && to != null && radius >= 0.0f
                && distanceSquared2D(from.getX(), from.getY(), to.getX(), to.getY()) <= radius * radius;
    }

    /**
     * Checks whether two points are within the given horizontal radius.
     *
     * @param fromX  first point X
     * @param fromY  first point Y
     * @param toX    second point X
     * @param toY    second point Y
     * @param radius maximum distance
     * @return {@code true} if the distance does not exceed the radius
     */
    public static boolean isWithinRadius(float fromX, float fromY, float toX, float toY, float radius) {
        return radius >= 0.0f && distanceSquared2D(fromX, fromY, toX, toY) <= radius * radius;
    }

    /**
     * Returns the objects within the given horizontal radius, nearest first.
     *
     * @param center     radius center
     * @param candidates candidate objects
     * @param radius     maximum distance
     * @param <T>        candidate type
     * @return matching objects sorted by distance, or an empty list
     */
    public static <T extends IsoObject> List<T> getWithinRadius(
            IsoObject center,
            Collection<T> candidates,
            float radius
    ) {
        if (center == null || candidates == null || candidates.isEmpty() || radius < 0.0f) {
            return List.of();
        }

        float maxDistanceSquared = radius * radius;
        List<T> matches = new ArrayList<>();
        for (T candidate : candidates) {
            if (candidate != null && distanceSquared2D(center, candidate) <= maxDistanceSquared) {
                matches.add(candidate);
            }
        }
        matches.sort(distanceComparator(center));
        return List.copyOf(matches);
    }

    /**
     * Returns online players within the given horizontal radius of a world object.
     *
     * @param center radius center
     * @param radius maximum distance
     * @return matching players sorted by distance, or an empty list
     */
    public static List<IsoPlayer> getPlayersWithinRadius(IsoObject center, float radius) {
        return getWithinRadius(center, PlayerLookupUtils.getOnlinePlayers(), radius);
    }

    /**
     * Returns online players within the given horizontal radius of a point.
     *
     * @param centerX radius center X
     * @param centerY radius center Y
     * @param radius  maximum distance
     * @return matching players sorted by distance, or an empty list
     */
    public static List<IsoPlayer> getPlayersWithinRadius(float centerX, float centerY, float radius) {
        if (radius < 0.0f) {
            return List.of();
        }

        float maxDistanceSquared = radius * radius;
        List<IsoPlayer> matches = new ArrayList<>();
        for (IsoPlayer player : PlayerLookupUtils.getOnlinePlayers()) {
            if (distanceSquared2D(centerX, centerY, player.getX(), player.getY()) <= maxDistanceSquared) {
                matches.add(player);
            }
        }
        matches.sort(Comparator.comparingDouble(
                player -> distanceSquared2D(centerX, centerY, player.getX(), player.getY())
        ));
        return List.copyOf(matches);
    }

    /**
     * Returns the nearest candidate to the given origin.
     *
     * @param origin     comparison origin
     * @param candidates candidate objects
     * @param <T>        candidate type
     * @return nearest candidate, or empty when no non-null candidate is available
     */
    public static <T extends IsoObject> Optional<T> getClosest(IsoObject origin, Collection<T> candidates) {
        return getClosest(origin, candidates, Float.POSITIVE_INFINITY);
    }

    /**
     * Returns the nearest candidate within the given horizontal radius.
     *
     * @param origin     comparison origin
     * @param candidates candidate objects
     * @param maxRadius  maximum distance
     * @param <T>        candidate type
     * @return nearest candidate, or empty when no candidate is within range
     */
    public static <T extends IsoObject> Optional<T> getClosest(
            IsoObject origin,
            Collection<T> candidates,
            float maxRadius
    ) {
        if (origin == null || candidates == null || candidates.isEmpty() || maxRadius < 0.0f) {
            return Optional.empty();
        }

        float maxDistanceSquared = maxRadius * maxRadius;
        T closest = null;
        float closestDistanceSquared = Float.POSITIVE_INFINITY;
        for (T candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            float distanceSquared = distanceSquared2D(origin, candidate);
            if (distanceSquared <= maxDistanceSquared && distanceSquared < closestDistanceSquared) {
                closest = candidate;
                closestDistanceSquared = distanceSquared;
            }
        }
        return Optional.ofNullable(closest);
    }

    /**
     * Returns the nearest online player to the given origin.
     *
     * @param origin comparison origin
     * @return nearest online player, or empty when no players are online
     */
    public static Optional<IsoPlayer> getClosestPlayer(IsoObject origin) {
        return getClosest(origin, PlayerLookupUtils.getOnlinePlayers());
    }

    /**
     * Returns the nearest online player within the given horizontal radius.
     *
     * @param origin    comparison origin
     * @param maxRadius maximum distance
     * @return nearest player in range, or empty when no player matches
     */
    public static Optional<IsoPlayer> getClosestPlayer(IsoObject origin, float maxRadius) {
        return getClosest(origin, PlayerLookupUtils.getOnlinePlayers(), maxRadius);
    }

    /**
     * Returns the candidates sorted by horizontal distance from the origin, nearest first.
     *
     * @param origin     comparison origin
     * @param candidates candidate objects
     * @param <T>        candidate type
     * @return sorted candidates, or an empty list
     */
    public static <T extends IsoObject> List<T> sortByDistance(IsoObject origin, Collection<T> candidates) {
        if (origin == null || candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        List<T> sorted = new ArrayList<>(candidates.size());
        for (T candidate : candidates) {
            if (candidate != null) {
                sorted.add(candidate);
            }
        }
        sorted.sort(distanceComparator(origin));
        return List.copyOf(sorted);
    }

    /**
     * Builds a comparator that orders objects by horizontal distance from the origin.
     *
     * @param origin comparison origin
     * @param <T>    compared type
     * @return distance comparator
     */
    private static <T extends IsoObject> Comparator<T> distanceComparator(IsoObject origin) {
        return Comparator.comparingDouble(candidate -> distanceSquared2D(origin, candidate));
    }
}
