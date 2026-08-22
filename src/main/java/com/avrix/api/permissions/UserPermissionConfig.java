package com.avrix.api.permissions;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Declarative configuration model representing personal permission and role overrides for an individual player.
 *
 * @param group                primary group/role explicitly assigned to this user
 * @param permissions          list of permanent permission nodes assigned directly to this user (supports {@code "-node"} negation)
 * @param temporaryPermissions map of permission nodes to their ISO-8601 expiration timestamps (e.g., {@code "2026-12-31T23:59:59Z"})
 * @param prefix               personal visual chat prefix overriding group defaults
 * @param suffix               personal visual chat suffix overriding group defaults
 */
@ConfigSerializable
public record UserPermissionConfig(
        @Comment("Primary group or role assigned to this user")
        String group,

        @Comment("List of permanent permission nodes assigned directly to this user")
        List<String> permissions,

        @Comment("Map of temporary permission nodes to their ISO-8601 expiration timestamps (e.g. '2026-12-31T23:59:59Z')")
        Map<String, String> temporaryPermissions,

        @Comment("Personal visual chat prefix overriding group defaults")
        String prefix,

        @Comment("Personal visual chat suffix overriding group defaults")
        String suffix
) {

    /**
     * Compact constructor enforcing null-safety, default fallbacks, and immutable collections.
     *
     * @param group                primary assigned group name
     * @param permissions          list of permanent permission nodes
     * @param temporaryPermissions map of temporary permission nodes to expiration dates
     * @param prefix               personal chat prefix
     * @param suffix               personal chat suffix
     */
    public UserPermissionConfig {
        group = (group != null) ? group : "";
        permissions = (permissions != null) ? Collections.unmodifiableList(permissions) : Collections.emptyList();
        temporaryPermissions = (temporaryPermissions != null) ? Collections.unmodifiableMap(temporaryPermissions) : Collections.emptyMap();
        prefix = (prefix != null) ? prefix : "";
        suffix = (suffix != null) ? suffix : "";
    }
}