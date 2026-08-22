package com.avrix.api.commands;

import com.avrix.api.permissions.ExtendedRole;
import com.avrix.api.permissions.PermissionsManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import zombie.characters.Capability;
import zombie.characters.IsoPlayer;
import zombie.characters.Roles;
import zombie.core.Color;
import zombie.core.raknet.UdpConnection;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Exhaustive test suite verifying command registration, quoted lexing,
 * subcommands routing, cooldowns, scoped execution, and permission evaluations.
 */
@DisplayName("CommandManager & Command Dispatching Tests")
class CommandManagerTest {

    @BeforeEach
    void setUp() {
        Roles.getRoles().clear();
        resetCooldowns();
    }

    @AfterEach
    void tearDown() {
        CommandManager.unregister("heal");
        CommandManager.unregister("consoleonly");
        CommandManager.unregister("chatonly");
        CommandManager.unregister("testargs");
        CommandManager.unregister("erroneous");
        CommandManager.unregister("kit");
        CommandManager.unregister("cooldowncmd");
        CommandManager.unregister("teleport");

        PermissionsManager.clearPlayerPermissions("VIPPlayer");
        PermissionsManager.clearPlayerPermissions("AdminPlayer");
        PermissionsManager.clearPlayerPermissions("RegularUser");
        PermissionsManager.clearPlayerPermissions("CooldownUser");
        PermissionsManager.clearPlayerPermissions("DirectPermUser");
    }

    private void resetCooldowns() {
        try {
            Field cooldownsField = CommandManager.class.getDeclaredField("COOLDOWNS");
            cooldownsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Long> cooldowns = (Map<String, Long>) cooldownsField.get(null);
            cooldowns.clear();
        } catch (Exception e) {
            throw new RuntimeException("Failed to reset cooldowns via reflection", e);
        }
    }

    // =========================================================================
    // 1. Registration & Invariant Tests
    // =========================================================================

    @Nested
    @DisplayName("1. Command Registration & Registry Invariants")
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
                    .hasMessageContaining("Primary command name cannot be blank");
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

    // =========================================================================
    // 2. Lexing & CommandContext Type-Safe Parsing Tests
    // =========================================================================

    @Nested
    @DisplayName("2. Argument Parsing & Context Type Helpers")
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

        @Test
        @DisplayName("CommandContext should correctly parse typed values and handle defaults")
        void testTypedArgumentParsing() {
            CommandContext ctx = new CommandContext(
                    "Tester",
                    null,
                    null,
                    "/teleport 105 200.5 true message for all",
                    new String[]{"105", "200.5", "true", "message", "for", "all"}
            );

            assertThat(ctx.getInt(0, 0)).isEqualTo(105);
            assertThat(ctx.getInt(0)).contains(105);
            assertThat(ctx.getInt(99, 42)).isEqualTo(42);
            assertThat(ctx.getInt(1)).isEmpty(); // "200.5" is not int

            assertThat(ctx.getDouble(1, 0.0)).isEqualTo(200.5);
            assertThat(ctx.getDouble(1)).contains(200.5);

            assertThat(ctx.getBoolean(2, false)).isTrue();
            assertThat(ctx.getBoolean(2)).contains(true);

            assertThat(ctx.joinArgs(3)).isEqualTo("message for all");
            assertThat(ctx.joinArgs(10)).isEmpty();
        }

        @Test
        @DisplayName("CommandContext subContext should correctly shift arguments")
        void testSubContextShifting() {
            CommandContext rootCtx = new CommandContext(
                    "Tester", null, null, "/cmd sub arg1 arg2", new String[]{"sub", "arg1", "arg2"}
            );

            CommandContext shifted = rootCtx.subContext(1);
            assertThat(shifted.length()).isEqualTo(2);
            assertThat(shifted.getString(0, "")).isEqualTo("arg1");
            assertThat(shifted.getString(1, "")).isEqualTo("arg2");

            CommandContext fullShifted = rootCtx.subContext(5);
            assertThat(fullShifted.length()).isEqualTo(0);
        }
    }

    // =========================================================================
    // 3. Subcommands Hierarchy Tests
    // =========================================================================

    @Nested
    @DisplayName("3. Subcommand Routing & Granular Permissions")
    class SubcommandTests {

        @Test
        @DisplayName("Should route to appropriate subcommand and pass shifted context")
        void shouldDispatchToSubcommand() {
            CommandManager.register(new KitCommand());

            ExtendedRole userRole = new ExtendedRole("User", "User Role", Color.white);
            userRole.addPermission("kit.use");
            userRole.addPermission("kit.starter");

            UdpConnection playerConnection = createMockPlayerConnection("StarterGuy", userRole);

            String result = CommandManager.handleCommand("/kit starter", playerConnection);
            assertThat(result).isEqualTo("Starter kit claimed by StarterGuy!");
        }

        @Test
        @DisplayName("Should execute parent command when no matching subcommand exists")
        void shouldFallbackToRootWhenNoSubcommandMatches() {
            CommandManager.register(new KitCommand());

            ExtendedRole userRole = new ExtendedRole("User", "User Role", Color.white);
            userRole.addPermission("kit.use");

            UdpConnection playerConnection = createMockPlayerConnection("RegularUser", userRole);

            String result = CommandManager.handleCommand("/kit unknownSub", playerConnection);
            assertThat(result).isEqualTo("Available kits: starter, vip");
        }

        @Test
        @DisplayName("Should enforce permissions on specific subcommands independently")
        void shouldEnforceSubcommandPermissions() {
            CommandManager.register(new KitCommand());

            ExtendedRole starterRole = new ExtendedRole("StarterRole", "Starter", Color.white);
            starterRole.addPermission("kit.use");
            starterRole.addPermission("kit.starter");

            UdpConnection playerConnection = createMockPlayerConnection("RegularUser", starterRole);

            // Access granted to starter
            String starterResult = CommandManager.handleCommand("/kit starter", playerConnection);
            assertThat(starterResult).isEqualTo("Starter kit claimed by RegularUser!");

            // Access denied to VIP
            String vipResult = CommandManager.handleCommand("/kit vip", playerConnection);
            assertThat(vipResult).isEqualTo("You do not have permission to execute this subcommand.");
        }
    }

    // =========================================================================
    // 4. Cooldown Enforcement Tests
    // =========================================================================

    @Nested
    @DisplayName("4. Cooldown Timers & Isolation")
    class CooldownTests {

        @Test
        @DisplayName("Should enforce cooldown on root command for players")
        void shouldEnforceRootCommandCooldown() {
            CommandManager.register(new CooldownTestCommand());

            UdpConnection playerConnection = createMockPlayerConnection("CooldownUser", null);

            // First execution: Success
            String firstRun = CommandManager.handleCommand("/cooldowncmd", playerConnection);
            assertThat(firstRun).isEqualTo("Cooldown command executed");

            // Second execution immediately: Cooldown active
            String secondRun = CommandManager.handleCommand("/cooldowncmd", playerConnection);
            assertThat(secondRun).contains("This command is on cooldown. Please wait");
        }

        @Test
        @DisplayName("Server console must bypass all cooldowns")
        void serverConsoleShouldBypassCooldown() {
            CommandManager.register(new CooldownTestCommand());

            String firstRun = CommandManager.handleCommand("/cooldowncmd", null);
            String secondRun = CommandManager.handleCommand("/cooldowncmd", null);

            assertThat(firstRun).isEqualTo("Cooldown command executed");
            assertThat(secondRun).isEqualTo("Cooldown command executed");
        }

        @Test
        @DisplayName("Subcommands must maintain independent cooldown timers")
        void shouldEnforceSubcommandIndependentCooldowns() {
            CommandManager.register(new KitCommand());

            ExtendedRole allKitsRole = new ExtendedRole("AllKits", "All", Color.white);
            allKitsRole.addPermission("kit.use");
            allKitsRole.addPermission("kit.starter");
            allKitsRole.addPermission("kit.vip");

            UdpConnection playerConnection = createMockPlayerConnection("VIPPlayer", allKitsRole);

            // Claim Starter kit (puts starter on cooldown)
            String starter1 = CommandManager.handleCommand("/kit starter", playerConnection);
            assertThat(starter1).isEqualTo("Starter kit claimed by VIPPlayer!");

            String starter2 = CommandManager.handleCommand("/kit starter", playerConnection);
            assertThat(starter2).contains("This subcommand is on cooldown");

            // Claim VIP kit (VIP should not be affected by starter cooldown)
            String vip1 = CommandManager.handleCommand("/kit vip", playerConnection);
            assertThat(vip1).isEqualTo("VIP kit claimed by VIPPlayer!");
        }
    }

    // =========================================================================
    // 5. CommandScope Enforcement Tests
    // =========================================================================

    @Nested
    @DisplayName("5. CommandScope Scope Verification")
    class ScopeTests {

        @Test
        @DisplayName("CommandScope.CHAT should allow players and reject server console")
        void shouldEnforceChatScope() {
            CommandManager.register(new TestChatOnlyCommand());

            // Console rejected
            String consoleResult = CommandManager.handleCommand("/chatonly", null);
            assertThat(consoleResult).isEqualTo("This command can only be executed in-game by a player.");

            // Player allowed
            UdpConnection mockConnection = createMockPlayerConnection("Brov3r", null);
            String playerResult = CommandManager.handleCommand("/chatonly", mockConnection);
            assertThat(playerResult).isEqualTo("Chat command success for: Brov3r");
        }

        @Test
        @DisplayName("CommandScope.CONSOLE should allow server console and reject players")
        void shouldEnforceConsoleScope() {
            CommandManager.register(new TestConsoleOnlyCommand());

            // Console allowed
            String consoleResult = CommandManager.handleCommand("/consoleonly", null);
            assertThat(consoleResult).isEqualTo("Console command success.");

            // Player rejected
            UdpConnection mockConnection = createMockPlayerConnection("Brov3r", null);
            String playerResult = CommandManager.handleCommand("/consoleonly", mockConnection);
            assertThat(playerResult).isEqualTo("This command can only be executed from the server console.");
        }
    }

    // =========================================================================
    // 6. Authorization, Wildcards & Inheritance Tests
    // =========================================================================

    @Nested
    @DisplayName("6. Permission Wildcards, Direct User Grants & Role Inheritance")
    class PermissionTests {

        @Test
        @DisplayName("Should grant access to player possessing exact permission node via ExtendedRole")
        void shouldAuthorizePlayerWithPermission() {
            CommandManager.register(new TestPermissionCommand());

            ExtendedRole vipRole = new ExtendedRole("VIP", "VIP Role", Color.white);
            vipRole.addPermission("avrix.commands.heal");

            UdpConnection playerConnection = createMockPlayerConnection("VIPPlayer", vipRole);

            String authorizedResult = CommandManager.handleCommand("/heal", playerConnection);
            assertThat(authorizedResult).isEqualTo("Healed: VIPPlayer");
        }

        @Test
        @DisplayName("Should grant access via direct player personal permissions")
        void shouldAuthorizeViaDirectPlayerPermission() {
            CommandManager.register(new TestPermissionCommand());

            PermissionsManager.grantPermissionToPlayer("DirectPermUser", "avrix.commands.heal");
            UdpConnection playerConnection = createMockPlayerConnection("DirectPermUser", null);

            String result = CommandManager.handleCommand("/heal", playerConnection);
            assertThat(result).isEqualTo("Healed: DirectPermUser");
        }

        @Test
        @DisplayName("Should grant access via hierarchical wildcard node in ExtendedRole")
        void shouldAuthorizePlayerWithWildcard() {
            CommandManager.register(new TestPermissionCommand());

            ExtendedRole adminRole = new ExtendedRole("Admin", "Admin Role", Color.red);
            adminRole.addPermission("avrix.commands.*");

            UdpConnection playerConnection = createMockPlayerConnection("AdminPlayer", adminRole);

            String result = CommandManager.handleCommand("/heal", playerConnection);
            assertThat(result).isEqualTo("Healed: AdminPlayer");
        }

        @Test
        @DisplayName("Should grant access inherited through parent roles")
        void shouldAuthorizeThroughRoleInheritance() {
            CommandManager.register(new TestPermissionCommand());

            ExtendedRole parentRole = new ExtendedRole("ParentRole");
            parentRole.addPermission("avrix.commands.heal");
            Roles.getRoles().add(parentRole);

            ExtendedRole childRole = new ExtendedRole("ChildRole");
            childRole.addParent("ParentRole");

            UdpConnection playerConnection = createMockPlayerConnection("ChildUser", childRole);

            String result = CommandManager.handleCommand("/heal", playerConnection);
            assertThat(result).isEqualTo("Healed: ChildUser");
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
        @DisplayName("Server console should always bypass permission checks")
        void consoleShouldAlwaysBypassPermissions() {
            CommandManager.register(new TestPermissionCommand());

            String result = CommandManager.handleCommand("/heal", null);
            assertThat(result).isEqualTo("Healed: ServerConsole");
        }
    }

    // =========================================================================
    // 7. Fallback & Exception Safety Tests
    // =========================================================================

    @Nested
    @DisplayName("7. Fallback Routing & Exception Handling")
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

            assertThat(result).contains("An internal error occurred during execution: Simulated command crash");
        }
    }

    // =========================================================================
    // 8. Advanced Scenarios, Capabilities, Scopes & Edge Cases
    // =========================================================================

    @Nested
    @DisplayName("8. Advanced Coverage: Capabilities, OR-Logic, Expiration & Lookups")
    class AdvancedCoverageTests {

        @AfterEach
        void tearDownAdvanced() {
            CommandManager.unregister("capcmd");
            CommandManager.unregister("multiperm");
            CommandManager.unregister("bothcmd");
            CommandManager.unregister("errsubcmd");
            CommandManager.unregister("findplayer");
            CommandManager.unregister("shortcd");
        }

        @Test
        @DisplayName("Should authorize player when possessing native Project Zomboid Capability")
        void shouldAuthorizeNativeCapability() {
            CommandManager.register(new CapabilityRestrictedCommand());

            ExtendedRole adminRole = new ExtendedRole("AdminRole", "Admin", Color.red);
            adminRole.addCapability(Capability.AddItem);

            UdpConnection playerConnection = createMockPlayerConnection("AdminCapUser", adminRole);

            String result = CommandManager.handleCommand("/capcmd", playerConnection);
            assertThat(result).isEqualTo("Capability command success!");
        }

        @Test
        @DisplayName("Should reject player when missing required native Capability")
        void shouldRejectMissingCapability() {
            CommandManager.register(new CapabilityRestrictedCommand());

            ExtendedRole userRole = new ExtendedRole("UserRole", "User", Color.white);
            UdpConnection playerConnection = createMockPlayerConnection("RegularUser", userRole);

            String result = CommandManager.handleCommand("/capcmd", playerConnection);
            assertThat(result).isEqualTo("You do not have permission to execute this command.");
        }

        @Test
        @DisplayName("Should satisfy authorization when player possesses at least ONE permission node (OR-logic)")
        void shouldAuthorizeWhenAnyPermissionNodeMatches() {
            CommandManager.register(new DisjunctivePermissionCommand());

            ExtendedRole roleWithSecondPermOnly = new ExtendedRole("RoleB", "B", Color.white);
            roleWithSecondPermOnly.addPermission("avrix.perm.b");

            UdpConnection playerConnection = createMockPlayerConnection("PermBUser", roleWithSecondPermOnly);

            String result = CommandManager.handleCommand("/multiperm", playerConnection);
            assertThat(result).isEqualTo("Multi-permission command success!");
        }

        @Test
        @DisplayName("CommandScope.BOTH should allow execution from both Server Console and Player")
        void shouldAllowBothConsoleAndPlayerForScopeBoth() {
            CommandManager.register(new BothScopeCommand());

            // Server Console execution
            String consoleResult = CommandManager.handleCommand("/bothcmd", null);
            assertThat(consoleResult).isEqualTo("Both scope executed by ServerConsole");

            // Player execution
            UdpConnection playerConnection = createMockPlayerConnection("PlayerInGame", null);
            String playerResult = CommandManager.handleCommand("/bothcmd", playerConnection);
            assertThat(playerResult).isEqualTo("Both scope executed by PlayerInGame");
        }

        @Test
        @DisplayName("Should catch internal exception thrown within a Subcommand")
        void shouldCatchSubcommandExecutionException() {
            CommandManager.register(new CrashingSubcommandRoot());

            String result = CommandManager.handleCommand("/errsubcmd crash", null);
            assertThat(result).contains("An internal error occurred during execution: Subcommand boom!");
        }

        @Test
        @DisplayName("Should allow command execution again after cooldown timer expires")
        void shouldAllowExecutionAfterCooldownExpiration() throws InterruptedException {
            CommandManager.register(new ShortCooldownCommand());

            UdpConnection playerConnection = createMockPlayerConnection("TimerUser", null);

            // 1st run: OK
            String run1 = CommandManager.handleCommand("/shortcd", playerConnection);
            assertThat(run1).isEqualTo("Short cooldown executed");

            // Immediate 2nd run: Blocked
            String run2 = CommandManager.handleCommand("/shortcd", playerConnection);
            assertThat(run2).contains("This command is on cooldown");

            // Wait for cooldown expiration (100ms duration)
            Thread.sleep(120);

            // 3rd run after expiration: OK
            String run3 = CommandManager.handleCommand("/shortcd", playerConnection);
            assertThat(run3).isEqualTo("Short cooldown executed");
        }
    }
    // =========================================================================
    // Advanced Test Support Commands
    // =========================================================================

    @CommandInfo(
            name = "capcmd",
            capability = {Capability.AddItem},
            scope = CommandScope.BOTH
    )
    private static class CapabilityRestrictedCommand implements Command {
        @Override
        public String execute(CommandContext context) {
            return "Capability command success!";
        }
    }

    @CommandInfo(
            name = "multiperm",
            permission = {"avrix.perm.a", "avrix.perm.b"},
            scope = CommandScope.BOTH
    )
    private static class DisjunctivePermissionCommand implements Command {
        @Override
        public String execute(CommandContext context) {
            return "Multi-permission command success!";
        }
    }

    @CommandInfo(
            name = "bothcmd",
            scope = CommandScope.BOTH
    )
    private static class BothScopeCommand implements Command {
        @Override
        public String execute(CommandContext context) {
            return "Both scope executed by " + context.senderName();
        }
    }

    @CommandInfo(
            name = "errsubcmd",
            scope = CommandScope.BOTH
    )
    private static class CrashingSubcommandRoot implements Command {
        @Override
        public Map<String, Subcommand> subcommands() {
            return Map.of("crash", _ -> {
                throw new IllegalStateException("Subcommand boom!");
            });
        }

        @Override
        public String execute(CommandContext context) {
            return "Root";
        }
    }

    @CommandInfo(
            name = "shortcd",
            cooldown = 100,
            cooldownUnit = TimeUnit.MILLISECONDS,
            scope = CommandScope.BOTH
    )
    private static class ShortCooldownCommand implements Command {
        @Override
        public String execute(CommandContext context) {
            return "Short cooldown executed";
        }
    }

    @CommandInfo(
            name = "findplayer",
            scope = CommandScope.BOTH
    )
    private static class FindPlayerCommand implements Command {
        @Override
        public String execute(CommandContext context) {
            return context.getPlayer()
                    .map(p -> "Found player: %s (ID: %d)".formatted(p.getUsername(), p.getOnlineID()))
                    .orElse("Player not found.");
        }
    }

    // =========================================================================
    // Mock Helper & Dummy Command Classes
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
                    context.getString(0, "none"),
                    context.getInt(1, 0));
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

    @CommandInfo(
            name = "cooldowncmd",
            scope = CommandScope.BOTH,
            cooldown = 10,
            cooldownUnit = TimeUnit.SECONDS
    )
    private static class CooldownTestCommand implements Command {
        @Override
        public String execute(CommandContext context) {
            return "Cooldown command executed";
        }
    }

    @CommandInfo(
            name = "kit",
            permission = {"kit.use"},
            scope = CommandScope.BOTH
    )
    private static class KitCommand implements Command {

        @Override
        public Map<String, Subcommand> subcommands() {
            return Map.of(
                    "starter", new StarterKitSubcommand(),
                    "vip", new VipKitSubcommand()
            );
        }

        @Override
        public String execute(CommandContext context) {
            return "Available kits: starter, vip";
        }

        private static class StarterKitSubcommand implements Subcommand {
            @Override
            public String[] permission() {
                return new String[]{"kit.starter"};
            }

            @Override
            public long cooldown() {
                return 60;
            }

            @Override
            public TimeUnit cooldownUnit() {
                return TimeUnit.SECONDS;
            }

            @Override
            public String execute(CommandContext context) {
                return "Starter kit claimed by " + context.senderName() + "!";
            }
        }

        private static class VipKitSubcommand implements Subcommand {
            @Override
            public String[] permission() {
                return new String[]{"kit.vip"};
            }

            @Override
            public long cooldown() {
                return 120;
            }

            @Override
            public TimeUnit cooldownUnit() {
                return TimeUnit.SECONDS;
            }

            @Override
            public String execute(CommandContext context) {
                return "VIP kit claimed by " + context.senderName() + "!";
            }
        }
    }
}