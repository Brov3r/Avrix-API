package com.avrix.api.commands;

/**
 * Functional contract representing an executable console/chat command.
 * Classes implementing this interface MUST be annotated with {@link CommandInfo}.
 */
@FunctionalInterface
public interface Command {

    /**
     * Executes the command logic with the supplied context.
     *
     * @param context the execution context
     * @return the result message to be returned to the server pipeline and sender
     */
    String execute(CommandContext context);
}