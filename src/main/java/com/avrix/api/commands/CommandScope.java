package com.avrix.api.commands;

/**
 * Defines the execution context scope for a command.
 */
public enum CommandScope {

    /**
     * The command can only be executed in-game by a connected player (via chat or client packet).
     */
    CHAT,

    /**
     * The command can only be executed via server console / terminal or RCON.
     */
    CONSOLE,

    /**
     * The command can be executed both from in-game chat and from the server console.
     */
    BOTH;

    /**
     * Validates whether the given execution origin satisfies this scope.
     *
     * @param isPlayerOrigin {@code true} if sent by an in-game player, {@code false} if server console/RCON
     * @return {@code true} if allowed
     */
    public boolean allows(boolean isPlayerOrigin) {
        return switch (this) {
            case CHAT -> isPlayerOrigin;
            case CONSOLE -> !isPlayerOrigin;
            case BOTH -> true;
        };
    }
}