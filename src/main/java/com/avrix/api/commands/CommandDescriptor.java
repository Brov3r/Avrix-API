package com.avrix.api.commands;

import zombie.characters.Capability;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Immutable public description of a registered command.
 *
 * @param name          primary command name
 * @param aliases       successfully registered aliases
 * @param description   human-readable description
 * @param usage         syntax usage guide
 * @param scope         allowed execution scope
 * @param permissions   custom permission nodes
 * @param capabilities  native Project Zomboid capabilities
 * @param cooldown      cooldown duration
 * @param cooldownUnit  cooldown time unit
 * @param subcommands   registered subcommand names
 */
public record CommandDescriptor(
        String name,
        Set<String> aliases,
        String description,
        String usage,
        CommandScope scope,
        List<String> permissions,
        List<Capability> capabilities,
        long cooldown,
        TimeUnit cooldownUnit,
        Set<String> subcommands
) {

    /**
     * Compact constructor normalizing nullable collections.
     *
     * @param name         primary command name
     * @param aliases      aliases
     * @param description  description
     * @param usage        usage
     * @param scope        scope
     * @param permissions  permissions
     * @param capabilities capabilities
     * @param cooldown     cooldown
     * @param cooldownUnit cooldown unit
     * @param subcommands  subcommand names
     */
    public CommandDescriptor {
        aliases = aliases != null ? Set.copyOf(aliases) : Collections.emptySet();
        description = description != null ? description : "";
        usage = usage != null ? usage : "";
        scope = scope != null ? scope : CommandScope.BOTH;
        permissions = permissions != null ? List.copyOf(permissions) : Collections.emptyList();
        capabilities = capabilities != null ? List.copyOf(capabilities) : Collections.emptyList();
        cooldownUnit = cooldownUnit != null ? cooldownUnit : TimeUnit.SECONDS;
        subcommands = subcommands != null ? Set.copyOf(subcommands) : Collections.emptySet();
    }
}
