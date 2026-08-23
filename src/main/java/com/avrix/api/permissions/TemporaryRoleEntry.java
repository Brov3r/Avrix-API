package com.avrix.api.permissions;

import java.time.Instant;

/**
 * Represents a temporary role assignment with an expiration time and a fallback role.
 *
 * @param roleName     the name of the temporary role assigned to the user
 * @param expiration   the {@link Instant} at which this role assignment expires,
 *                     or {@code null} if the role is permanent
 * @param fallbackRole the name of the role to restore after expiration (e.g., {@code "user"})
 */
public record TemporaryRoleEntry(String roleName, Instant expiration, String fallbackRole) {

    /**
     * Checks whether this temporary role assignment has expired.
     *
     * @return {@code true} if the role has expired, {@code false} otherwise
     */
    public boolean isExpired() {
        return expiration != null && Instant.now().isAfter(expiration);
    }
}