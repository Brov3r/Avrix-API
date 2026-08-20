package com.avrix.api.config;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;

import java.util.List;
import java.util.Map;

/**
 * Complex domain structures for edge-case configuration deserialization tests.
 */
public final class ComplexTestModels {

    private ComplexTestModels() {
    }

    @ConfigSerializable
    public record RootConfig(
            @Comment("Server header")
            ServerSettings server,
            Map<String, ZoneEntry> zones,
            List<String> tags
    ) {
        public RootConfig {
            if (server == null) server = new ServerSettings("Default", 100);
            if (zones == null) zones = Map.of();
            if (tags == null) tags = List.of();
        }
    }

    @ConfigSerializable
    public record ServerSettings(
            String title,
            int maxPlayers
    ) {
    }

    @ConfigSerializable
    public record ZoneEntry(
            double radius,
            boolean pvp,
            List<ItemDrop> drops
    ) {
        public ZoneEntry {
            if (drops == null) drops = List.of();
        }
    }

    @ConfigSerializable
    public record ItemDrop(
            String id,
            double chance
    ) {
    }
}