package com.avrix.api.commands;

import zombie.core.Translator;

import java.util.Locale;

/**
 * Centralized, localization-ready command messages.
 * <p>
 * Project Zomboid translation keys are used when available; otherwise an English
 * fallback is returned. This keeps command feedback stable in unit tests and on
 * installations without Avrix translation files.
 */
public final class CommandMessages {

    /**
     * Translation key for the player-only command scope message.
     */
    public static final String SCOPE_CHAT = "Avrix_Command_ScopeChat";

    /**
     * Translation key for the server-console-only command scope message.
     */
    public static final String SCOPE_CONSOLE = "Avrix_Command_ScopeConsole";

    /**
     * Translation key for the invalid command scope message.
     */
    public static final String SCOPE_BOTH = "Avrix_Command_ScopeBoth";

    /**
     * Translation key for the root command permission-denied message.
     */
    public static final String NO_PERMISSION = "Avrix_Command_NoPermission";

    /**
     * Translation key for the subcommand permission-denied message.
     */
    public static final String NO_SUBCOMMAND_PERMISSION = "Avrix_Command_NoSubcommandPermission";

    /**
     * Translation key for the root command cooldown message.
     */
    public static final String COMMAND_COOLDOWN = "Avrix_Command_Cooldown";

    /**
     * Translation key for the subcommand cooldown message.
     */
    public static final String SUBCOMMAND_COOLDOWN = "Avrix_Command_SubcommandCooldown";

    /**
     * Translation key for the command success message.
     */
    public static final String SUCCESS = "Avrix_Command_Success";

    /**
     * Translation key for the internal command error message.
     */
    public static final String INTERNAL_ERROR = "Avrix_Command_InternalError";

    /**
     * Translation key for the invalid command syntax message.
     */
    public static final String INVALID_SYNTAX = "Avrix_Command_InvalidSyntax";

    private CommandMessages() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Returns the message for a command scope.
     *
     * @param scope command scope
     * @return localized or fallback message
     */
    public static String scope(CommandScope scope) {
        return switch (scope) {
            case CHAT -> get(SCOPE_CHAT, "This command can only be executed in-game by a player.");
            case CONSOLE -> get(SCOPE_CONSOLE, "This command can only be executed from the server console.");
            case BOTH -> get(SCOPE_BOTH, "Command scope validation failed.");
        };
    }

    /**
     * Returns the permission-denied message for a root command.
     *
     * @return localized or fallback message
     */
    public static String noPermission() {
        return get(NO_PERMISSION, "You do not have permission to execute this command.");
    }

    /**
     * Returns the permission-denied message for a subcommand.
     *
     * @return localized or fallback message
     */
    public static String noSubcommandPermission() {
        return get(NO_SUBCOMMAND_PERMISSION, "You do not have permission to execute this subcommand.");
    }

    /**
     * Returns the cooldown message for a root command.
     *
     * @param duration formatted remaining duration
     * @return localized or fallback message
     */
    public static String commandCooldown(String duration) {
        return get(COMMAND_COOLDOWN, "This command is on cooldown. Please wait %s.", duration);
    }

    /**
     * Returns the cooldown message for a subcommand.
     *
     * @param duration formatted remaining duration
     * @return localized or fallback message
     */
    public static String subcommandCooldown(String duration) {
        return get(SUBCOMMAND_COOLDOWN, "This subcommand is on cooldown. Please wait %s.", duration);
    }

    /**
     * Returns the default success message.
     *
     * @return localized or fallback message
     */
    public static String success() {
        return get(SUCCESS, "Command executed successfully.");
    }

    /**
     * Returns a generic internal-error message without exposing exception details.
     *
     * @param errorId short correlation identifier
     * @return localized or fallback message
     */
    public static String internalError(String errorId) {
        return get(INTERNAL_ERROR,
                "An internal error occurred during command execution. Check server logs (id: %s).", errorId);
    }

    /**
     * Returns the invalid-syntax message.
     *
     * @param reason parser reason
     * @return localized or fallback message
     */
    public static String invalidSyntax(String reason) {
        return get(INVALID_SYNTAX, "Invalid command syntax: %s", reason);
    }

    /**
     * Resolves a translation or returns a formatted English fallback.
     *
     * @param key      translation key
     * @param fallback fallback format string
     * @param args     format arguments
     * @return resolved message
     */
    public static String get(String key, String fallback, Object... args) {
        try {
            String translated = Translator.getTextOrNull(key, args);
            if (translated != null && !translated.isBlank()) {
                return translated;
            }
        } catch (Throwable ignored) {
            // Translation is optional; fallback keeps command feedback usable.
        }
        return String.format(Locale.ROOT, fallback, args);
    }
}
