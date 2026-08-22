package com.avrix.api.commands;

import zombie.characters.Capability;

import java.util.concurrent.TimeUnit;

/**
 * Represents a subcommand with its own permission, capability, and cooldown constraints.
 */
public interface Subcommand {

    /**
     * Shared empty string array to avoid GC allocations on default permission checks.
     */
    String[] EMPTY_PERMISSIONS = new String[0];

    /**
     * Shared empty capability array to avoid GC allocations on default capability checks.
     */
    Capability[] EMPTY_CAPABILITIES = new Capability[0];

    /**
     * Executes the subcommand logic.
     *
     * @param context execution context with shifted arguments (args[0] is the first argument AFTER subcommand name)
     * @return response message for sender, or null if void
     * @throws Exception if an error occurs during execution
     */
    String execute(CommandContext context) throws Exception;

    /**
     * Specific permissions required for this subcommand.
     * If empty, inherits access from the parent command.
     *
     * @return array of permission strings
     */
    default String[] permission() {
        return EMPTY_PERMISSIONS;
    }

    /**
     * Specific capabilities required for this subcommand.
     *
     * @return array of native capabilities
     */
    default Capability[] capability() {
        return EMPTY_CAPABILITIES;
    }

    /**
     * Individual cooldown duration for this specific subcommand.
     *
     * @return cooldown amount (0 for no cooldown)
     */
    default long cooldown() {
        return 0L;
    }

    /**
     * Time unit for the subcommand cooldown.
     *
     * @return time unit (defaults to SECONDS)
     */
    default TimeUnit cooldownUnit() {
        return TimeUnit.SECONDS;
    }

    /**
     * Short description of the subcommand.
     *
     * @return description string
     */
    default String description() {
        return "";
    }
}