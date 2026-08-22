package com.avrix.api.commands;

import java.util.Collections;
import java.util.Map;

/**
 * Base interface for all Avrix commands with optional subcommand support.
 */
@FunctionalInterface
public interface Command {

    /**
     * Executes the root command logic.
     *
     * @param context execution context
     * @return message string returned to sender, or null if void
     * @throws Exception if an error occurs during execution
     */
    String execute(CommandContext context) throws Exception;

    /**
     * Returns mapped subcommands for this command.
     *
     * @return map of subcommand names to implementations
     */
    default Map<String, Subcommand> subcommands() {
        return Collections.emptyMap();
    }
}