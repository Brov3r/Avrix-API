package com.avrix.api.permissions;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;

import java.util.Collections;
import java.util.Map;

/**
 * Root configuration model for the {@code permissions.yml} declarative permissions file.
 *
 * @param groups map of group/role identifiers to their detailed configurations
 * @param users  map of individual user identifiers (username or SteamID64) to personal permission overrides
 */
@ConfigSerializable
public record PermissionsConfig(
        @Comment("Global role definitions, permissions, native capabilities, and inheritance hierarchies")
        Map<String, RoleConfig> groups,

        @Comment("User-specific permission nodes, temporary grants, and group bindings indexed by Username or SteamID64")
        Map<String, UserPermissionConfig> users
) {

    /**
     * Compact constructor enforcing null-safety and defensive immutable copies.
     *
     * @param groups map of group identifiers to role configurations
     * @param users  map of user identifiers to user-specific configurations
     */
    public PermissionsConfig {
        groups = (groups != null) ? Collections.unmodifiableMap(groups) : Collections.emptyMap();
        users = (users != null) ? Collections.unmodifiableMap(users) : Collections.emptyMap();
    }
}