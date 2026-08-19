package com.avrix.api.commands;

import com.avrix.api.permissions.ExtendedRole;
import com.avrix.api.permissions.PermissionsManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import zombie.characters.IsoPlayer;
import zombie.characters.Roles;
import zombie.core.Color;
import zombie.core.raknet.UdpConnection;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Comprehensive test suite verifying command registration, quoted argument lexing,
 * scope enforcement, authorization rules, and execution pipelines in {@link CommandManager}.
 */
@DisplayName("CommandManager & Command Dispatching Tests")
class CommandManagerTest {

    @BeforeEach
    void setUp() {
        Roles.getRoles().clear();
    }

    @AfterEach
    void tearDown() {
        CommandManager.unregister("heal");
        CommandManager.unregister("consoleonly");
        CommandManager.unregister("chatonly");
        CommandManager.unregister("testargs");
        CommandManager.unregister("erroneous");
        PermissionsManager.clearPlayerPermissions("VIPPlayer");
        PermissionsManager.clearPlayerPermissions("AdminPlayer");
        PermissionsManager.clearPlayerPermissions("RegularUser");
    }

    @Nested
    @DisplayName("Command Registration and Invariant Tests")
    class RegistrationTests {

        @Test
        @DisplayName("Should successfully register a valid command and its aliases")
        void shouldRegisterCommandWithAliases() {
            TestHealCommand command = new TestHealCommand();
            CommandManager.register(command);

            String resultViaPrimary = CommandManager.handleCommand("/heal", null);
            String resultViaAlias = CommandManager.handleCommand("/hp", null);

            assertThat(resultViaPrimary).isEqualTo("Healed: ServerConsole");
            assertThat(resultViaAlias).isEqualTo("Healed: ServerConsole");
        }

        @Test
        @DisplayName("Should reject command class missing @CommandInfo annotation")
        void shouldRejectUnannotatedCommand() {
            Command unannotated = ctx -> "OK";

            assertThatThrownBy(() -> CommandManager.register(unannotated))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("missing @CommandInfo");
        }

        @Test
        @DisplayName("Should reject command with blank name in annotation")
        void shouldRejectBlankCommandName() {
            @CommandInfo(name = "   ")
            class BlankNameCommand implements Command {
                @Override
                public String execute(CommandContext context) {
                    return "OK";
                }
            }

            assertThatThrownBy(() -> CommandManager.register(new BlankNameCommand()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Command name cannot be empty");
        }

        @Test
        @DisplayName("Should unregister command and its aliases properly")
        void shouldUnregisterCommand() {
            CommandManager.register(new TestHealCommand());
            assertThat(CommandManager.handleCommand("/heal", null)).isNotNull();

            CommandManager.unregister("heal");

            assertThat(CommandManager.handleCommand("/heal", null)).isNull();
            assertThat(CommandManager.handleCommand("/hp", null)).isNull();
        }
    }

    @Nested
    @DisplayName("Argument Parsing and Quoted String Tokenization")
    class ArgumentParsingTests {

        @Test
        @DisplayName("Should parse multi-word arguments wrapped in quotes without stripping spaces")
        void shouldParseQuotedTokens() {
            List<String> tokens = CommandArgumentParser.parseTokens("/heal \"Miss Bekket\" 100 'Special Reason'");

            assertThat(tokens).containsExactly("heal", "Miss Bekket", "100", "Special Reason");
        }

        @Test
        @DisplayName("Should pass tokenized arguments properly into CommandContext")
        void shouldPassArgumentsIntoCommandContext() {
            CommandManager.register(new ArgumentVerifyCommand());

            String result = CommandManager.handleCommand("/testargs \"Player One\" 50", null);

            assertThat(result).isEqualTo("Target: Player One, Amount: 50");
        }
    }

    @Nested
    @DisplayName("CommandScope Enforcement Tests")
    class ScopeTests {

        @Test
        @DisplayName("CommandScope.CHAT should allow players and reject server console")
        void shouldEnforceChatScope() {
            CommandManager.register(new TestChatOnlyCommand());

            // Execute as server console (connection == null)
            String consoleResult = CommandManager.handleCommand("/chatonly", null);
            assertThat(consoleResult).isEqualTo("This command can only be executed in-game by a player.");

            // Execute as in-game player
            UdpConnection mockConnection = createMockPlayerConnection("Brov3r", null);
            String playerResult = CommandManager.handleCommand("/chatonly", mockConnection);
            assertThat(playerResult).isEqualTo("Chat command success for: Brov3r");
        }

        @Test
        @DisplayName("CommandScope.CONSOLE should allow server console and reject players")
        void shouldEnforceConsoleScope() {
            CommandManager.register(new TestConsoleOnlyCommand());

            // Execute as server console
            String consoleResult = CommandManager.handleCommand("/consoleonly", null);
            assertThat(consoleResult).isEqualTo("Console command success.");

            // Execute as in-game player
            UdpConnection mockConnection = createMockPlayerConnection("Brov3r", null);
            String playerResult = CommandManager.handleCommand("/consoleonly", mockConnection);
            assertThat(playerResult).isEqualTo("This command can only be executed from the server console.");
        }
    }

    @Nested
    @DisplayName("Authorization and Permission Evaluation Tests")
    class PermissionTests {

        @Test
        @DisplayName("Should grant access to player possessing required permission node")
        void shouldAuthorizePlayerWithPermission() {
            CommandManager.register(new TestPermissionCommand());

            ExtendedRole vipRole = new ExtendedRole("VIP", "VIP Role", Color.white);
            vipRole.addPermission("avrix.commands.heal");

            UdpConnection playerConnection = createMockPlayerConnection("VIPPlayer", vipRole);

            String authorizedResult = CommandManager.handleCommand("/heal", playerConnection);
            assertThat(authorizedResult).isEqualTo("Healed: VIPPlayer");
        }

        @Test
        @DisplayName("Should reject player missing required permission")
        void shouldRejectUnauthorizedPlayer() {
            CommandManager.register(new TestPermissionCommand());

            ExtendedRole defaultRole = new ExtendedRole("User", "User Role", Color.white);
            defaultRole.addPermission("avrix.chat.base");

            UdpConnection playerConnection = createMockPlayerConnection("RegularUser", defaultRole);

            String deniedResult = CommandManager.handleCommand("/heal", playerConnection);
            assertThat(deniedResult).isEqualTo("You do not have permission to execute this command.");
        }

        @Test
        @DisplayName("Should grant access via hierarchical wildcard permission node")
        void shouldAuthorizePlayerWithWildcard() {
            CommandManager.register(new TestPermissionCommand());

            ExtendedRole adminRole = new ExtendedRole("Admin", "Admin Role", Color.red);
            adminRole.addPermission("avrix.commands.*");

            UdpConnection playerConnection = createMockPlayerConnection("AdminPlayer", adminRole);

            String result = CommandManager.handleCommand("/heal", playerConnection);
            assertThat(result).isEqualTo("Healed: AdminPlayer");
        }

        @Test
        @DisplayName("Server console should always bypass permission checks")
        void consoleShouldAlwaysBypassPermissions() {
            CommandManager.register(new TestPermissionCommand());

            String result = CommandManager.handleCommand("/heal", null);
            assertThat(result).isEqualTo("Healed: ServerConsole");
        }
    }

    @Nested
    @DisplayName("Fallback and Safety Routing Tests")
    class RoutingTests {

        @Test
        @DisplayName("Should return null for unknown commands to allow vanilla PZ execution")
        void shouldReturnNullForUnknownCommands() {
            String result = CommandManager.handleCommand("/vanilla_save_command", null);
            assertThat(result).isNull();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t", "\n"})
        @DisplayName("Should return null for blank or empty inputs")
        void shouldReturnNullForBlankInputs(String blankInput) {
            assertThat(CommandManager.handleCommand(blankInput, null)).isNull();
        }

        @Test
        @DisplayName("Should catch internal command exceptions and return user-friendly error message")
        void shouldHandleCommandExecutionExceptions() {
            CommandManager.register(new TestErroneousCommand());

            String result = CommandManager.handleCommand("/erroneous", null);

            assertThat(result).contains("An internal error occurred: Simulated command crash");
        }
    }

    // =========================================================================
    // Mock & Dummy Test Command Helpers
    // =========================================================================

    private static UdpConnection createMockPlayerConnection(String username, ExtendedRole assignedRole) {
        UdpConnection connection = Mockito.mock(UdpConnection.class);
        IsoPlayer player = Mockito.mock(IsoPlayer.class);

        when(player.getUsername()).thenReturn(username);
        when(connection.getUserName()).thenReturn(username);

        if (assignedRole != null) {
            when(player.getRole()).thenReturn(assignedRole);
            when(connection.getRole()).thenReturn(assignedRole);
        }

        connection.players = new IsoPlayer[]{player};
        return connection;
    }

    @CommandInfo(
            name = "heal",
            aliases = {"hp", "healme"},
            description = "Heals target or self",
            scope = CommandScope.BOTH
    )
    private static class TestHealCommand implements Command {
        @Override
        public String execute(CommandContext context) {
            return "Healed: " + context.senderName();
        }
    }

    @CommandInfo(
            name = "chatonly",
            scope = CommandScope.CHAT
    )
    private static class TestChatOnlyCommand implements Command {
        @Override
        public String execute(CommandContext context) {
            return "Chat command success for: " + context.senderName();
        }
    }

    @CommandInfo(
            name = "consoleonly",
            scope = CommandScope.CONSOLE
    )
    private static class TestConsoleOnlyCommand implements Command {
        @Override
        public String execute(CommandContext context) {
            return "Console command success.";
        }
    }

    @CommandInfo(
            name = "heal",
            permission = {"avrix.commands.heal"},
            scope = CommandScope.BOTH
    )
    private static class TestPermissionCommand implements Command {
        @Override
        public String execute(CommandContext context) {
            return "Healed: " + context.senderName();
        }
    }

    @CommandInfo(
            name = "testargs",
            scope = CommandScope.BOTH
    )
    private static class ArgumentVerifyCommand implements Command {
        @Override
        public String execute(CommandContext context) {
            return String.format("Target: %s, Amount: %s",
                    context.getArg(0).orElse("none"),
                    context.getArg(1).orElse("0"));
        }
    }

    @CommandInfo(
            name = "erroneous",
            scope = CommandScope.BOTH
    )
    private static class TestErroneousCommand implements Command {
        @Override
        public String execute(CommandContext context) {
            throw new IllegalStateException("Simulated command crash");
        }
    }
}