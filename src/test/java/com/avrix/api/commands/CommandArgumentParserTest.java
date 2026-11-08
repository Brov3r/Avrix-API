package com.avrix.api.commands;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the command argument lexer.
 */
@DisplayName("CommandArgumentParser Tests")
class CommandArgumentParserTest {

    @Test
    @DisplayName("Should parse quoted and unquoted tokens")
    void shouldParseQuotedTokens() {
        List<String> tokens = CommandArgumentParser.parseTokens(
                "/heal \"Miss Bekket\" 100 'Special Reason'"
        );

        assertThat(tokens).containsExactly("heal", "Miss Bekket", "100", "Special Reason");
    }

    @Test
    @DisplayName("Should support escaped quotes inside quoted arguments")
    void shouldSupportEscapedQuotes() {
        List<String> tokens = CommandArgumentParser.parseTokens(
                "/heal \"Player \\\"One\\\"\""
        );

        assertThat(tokens).containsExactly("heal", "Player \"One\"");
    }

    @Test
    @DisplayName("Should keep an empty quoted token")
    void shouldKeepEmptyQuotedToken() {
        assertThat(CommandArgumentParser.parseTokens("/cmd \"\""))
                .containsExactly("cmd", "");
    }

    @Test
    @DisplayName("Should reject unclosed quotes")
    void shouldRejectUnclosedQuotes() {
        assertThatThrownBy(() -> CommandArgumentParser.parseTokens("/cmd \"unclosed"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unclosed");
    }

    @Test
    @DisplayName("Should return an immutable token list")
    void shouldReturnImmutableTokens() {
        List<String> tokens = CommandArgumentParser.parseTokens("/cmd arg");

        assertThatThrownBy(() -> tokens.add("changed"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Should return empty tokens for null and blank input")
    void shouldHandleBlankInput() {
        assertThat(CommandArgumentParser.parseTokens(null)).isEmpty();
        assertThat(CommandArgumentParser.parseTokens("   ")).isEmpty();
        assertThat(CommandArgumentParser.parseTokens("/")).isEmpty();
    }
}
