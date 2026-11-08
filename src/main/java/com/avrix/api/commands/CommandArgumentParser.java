package com.avrix.api.commands;

import java.util.ArrayList;
import java.util.List;

/**
 * Lexer splitting command input into positional arguments.
 * <p>
 * Single and double quotes group whitespace-containing arguments. A backslash
 * escapes the following character, including quotes and whitespace. Unclosed
 * quotes are rejected instead of silently producing malformed tokens.
 */
public final class CommandArgumentParser {

    private CommandArgumentParser() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Parses a raw command string into an immutable list of tokens.
     *
     * @param rawInput raw input string (for example {@code /heal "Miss Bekket" 100})
     * @return immutable list of tokens with quote wrappers removed
     * @throws IllegalArgumentException if a quote is not closed
     */
    public static List<String> parseTokens(String rawInput) {
        if (rawInput == null || rawInput.isBlank()) {
            return List.of();
        }

        String input = rawInput.strip();
        if (input.startsWith("/")) {
            input = input.substring(1).stripLeading();
        }
        if (input.isEmpty()) {
            return List.of();
        }

        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        boolean tokenStarted = false;

        for (int index = 0; index < input.length(); index++) {
            char character = input.charAt(index);

            if (character == '\\' && index + 1 < input.length()) {
                current.append(input.charAt(++index));
                tokenStarted = true;
                continue;
            }

            if (quote != 0) {
                if (character == quote) {
                    quote = 0;
                } else {
                    current.append(character);
                }
                tokenStarted = true;
                continue;
            }

            if (character == '\'' || character == '"') {
                quote = character;
                tokenStarted = true;
                continue;
            }

            if (Character.isWhitespace(character)) {
                if (tokenStarted) {
                    tokens.add(current.toString());
                    current.setLength(0);
                    tokenStarted = false;
                }
                continue;
            }

            current.append(character);
            tokenStarted = true;
        }

        if (quote != 0) {
            throw new IllegalArgumentException("Unclosed " + (quote == '"' ? "double" : "single") + " quote");
        }
        if (tokenStarted) {
            tokens.add(current.toString());
        }

        return List.copyOf(tokens);
    }
}
