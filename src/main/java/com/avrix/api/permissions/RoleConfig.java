package com.avrix.api.permissions;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Declarative configuration model representing an individual group or role definition.
 *
 * @param description  human-readable description of the role's purpose
 * @param color        display color formatted as RGBA (e.g., {@code "255,50,50,255"})
 * @param prefix       visual chat and name prefix displayed before the player's name
 * @param suffix       visual chat and name suffix displayed after the player's name
 * @param permissions  collection of string permission nodes granted to this group (supports {@code "-node"} negation)
 * @param capabilities collection of native Project Zomboid {@link zombie.characters.Capability} enum names granted to this role
 * @param parents      collection of parent group names from which this role inherits permissions and metadata
 * @param metadata     arbitrary key-value metadata storage associated with this role
 */
@ConfigSerializable
public record RoleConfig(
        @Comment("Human-readable description of the role's purpose")
        String description,

        @Comment("Display color formatted as RGBA (e.g., '255,50,50,255')")
        String color,

        @Comment("Visual chat prefix displayed before the player's name")
        String prefix,

        @Comment("Visual chat suffix displayed after the player's name")
        String suffix,

        @Comment("List of granted permission nodes (supports wildcards '.*' and negations '-node')")
        List<String> permissions,

        @Comment("List of native Project Zomboid Capability enum names granted to this role")
        List<String> capabilities,

        @Comment("List of parent role names inherited by this role")
        List<String> parents,

        @Comment("Arbitrary key-value metadata storage (e.g. max_homes, limits)")
        Map<String, String> metadata
) {

    /**
     * Compact constructor enforcing null-safety, default fallbacks, and immutable collections.
     *
     * @param description  human-readable description
     * @param color        display color RGBA string
     * @param prefix       visual chat prefix
     * @param suffix       visual chat suffix
     * @param permissions  list of granted permission nodes
     * @param capabilities list of native capability names
     * @param parents      list of inherited parent role names
     * @param metadata     map of arbitrary key-value metadata
     */
    public RoleConfig {
        description = (description != null) ? description : "";
        color = (color != null && !color.isBlank()) ? color : "255,255,255,255";
        prefix = (prefix != null) ? prefix : "";
        suffix = (suffix != null) ? suffix : "";
        permissions = (permissions != null) ? Collections.unmodifiableList(permissions) : Collections.emptyList();
        capabilities = (capabilities != null) ? Collections.unmodifiableList(capabilities) : Collections.emptyList();
        parents = (parents != null) ? Collections.unmodifiableList(parents) : Collections.emptyList();
        metadata = (metadata != null) ? Collections.unmodifiableMap(metadata) : Collections.emptyMap();
    }
}