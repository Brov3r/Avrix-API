package com.avrix.api.permissions;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/**
 * Immutable domain representation of an assigned player permission node.
 *
 * @param node       the normalized permission string
 * @param expiration absolute expiration timestamp, or {@code null} if permanent
 */
public record PlayerPermission(String node, Instant expiration) {

    public PlayerPermission {
        Objects.requireNonNull(node, "Permission node cannot be null");
        node = node.strip().toLowerCase(Locale.ROOT);
    }

    /**
     * Creates a permanent permission entry.
     *
     * @param node the permission node
     * @return permanent permission record
     */
    public static PlayerPermission permanent(String node) {
        return new PlayerPermission(node, null);
    }

    /**
     * Creates a temporary permission entry expiring at the given instant.
     *
     * @param node       the permission node
     * @param expiration expiration instant
     * @return temporary permission record
     */
    public static PlayerPermission temporary(String node, Instant expiration) {
        return new PlayerPermission(node, expiration);
    }

    /**
     * Checks if this permission has an expiration timestamp.
     *
     * @return {@code true} if temporary; {@code false} if permanent
     */
    public boolean isTemporary() {
        return expiration != null;
    }

    /**
     * Checks if this permission is expired relative to current wall-clock time.
     *
     * @return {@code true} if expired; {@code false} if still active
     */
    public boolean isExpired() {
        return expiration != null && Instant.now().isAfter(expiration);
    }

    /**
     * Checks whether this permission explicitly negates/revokes access (starts with {@code '-'}).
     *
     * @return {@code true} if negated
     */
    public boolean isNegated() {
        return node.startsWith("-");
    }

    /**
     * Returns the clean permission node without negation prefix if present.
     *
     * @return clean node string
     */
    public String cleanNode() {
        return isNegated() ? node.substring(1) : node;
    }
}