package com.avrix.api.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * High-performance lexer splitting command input into tokenized arguments
 * while preserving multi-word strings encapsulated in single or double quotes.
 */
public final class CommandArgumentParser {
    /**
     * Regex matching double-quoted strings, single-quoted strings, or non-whitespace sequences.
     */
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\"([^\"]*)\"|'([^']*)'|(\\S+)");

    private CommandArgumentParser() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Parses a raw command string into a list of clean tokens.
     *
     * @param rawInput the raw input string (e.g. {@code /heal "Miss Bekket" 100})
     * @return a list of tokens where quotes have been stripped
     */
    public static List<String> parseTokens(String rawInput) {
        if (rawInput == null || rawInput.isBlank()) {
            return List.of();
        }

        String sanitized = rawInput.strip();
        if (sanitized.startsWith("/")) {
            sanitized = sanitized.substring(1).stripLeading();
        }

        List<String> tokens = new ArrayList<>();
        Matcher matcher = TOKEN_PATTERN.matcher(sanitized);

        while (matcher.find()) {
            if (matcher.group(1) != null) {
                // Double-quoted match
                tokens.add(matcher.group(1));
            } else if (matcher.group(2) != null) {
                // Single-quoted match
                tokens.add(matcher.group(2));
            } else {
                // Unquoted match: regular word
                tokens.add(matcher.group(3));
            }
        }

        return tokens;
    }
}