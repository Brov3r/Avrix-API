package com.avrix.api.commands;

import com.avrix.api.events.EventManager;
import com.avrix.api.events.SubscribeEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import zombie.characters.IsoPlayer;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Advanced tests for command ownership, conflicts, descriptors, sources, and events.
 */
@DisplayName("Advanced Command Registry Tests")
class CommandSystemAdvancedTest {

    @AfterEach
    void tearDown() {
        CommandManager.unregister("alpha");
        CommandManager.unregister("ownedcmd");
        CommandManager.unregister("casecmd");
        CommandManager.unregister("clientchat");
        CommandManager.unregister("save");
    }

    @Test
    @DisplayName("Should reject a command whose primary name is occupied by another command's alias")
    void shouldNotStealAliasFromAnotherCommand() {
        CommandManager.register(new AlphaCommand());
        CommandManager.register(new SharedCommand());

        assertThat(CommandManager.getDescriptor("shared")).isNotNull();
        assertThat(CommandManager.getDescriptor("shared").name()).isEqualTo("alpha");
        assertThat(CommandManager.handleCommand("/shared", null)).isEqualTo("alpha");
        assertThat(CommandManager.handleCommand("/shared-primary", null)).isNull();
    }

    @Test
    @DisplayName("Should unregister all commands associated with an owner")
    void shouldUnregisterCommandsByOwner() {
        Object owner = new Object();
        CommandManager.register(owner, new OwnedCommand());

        assertThat(CommandManager.handleCommand("/ownedcmd", null)).isEqualTo("owned");
        CommandManager.unregisterAll(owner);
        assertThat(CommandManager.handleCommand("/ownedcmd", null)).isNull();
    }

    @Test
    @DisplayName("Should normalize subcommand keys to lowercase")
    void shouldNormalizeSubcommandKeys() {
        CommandManager.register(new CaseCommand());

        assertThat(CommandManager.handleCommand("/casecmd vip", null)).isEqualTo("case-ok");
    }

    @Test
    @DisplayName("Should dispatch client-origin commands with CHAT scope")
    void shouldDispatchClientCommands() {
        CommandManager.register(new ClientChatCommand());
        IsoPlayer player = Mockito.mock(IsoPlayer.class);
        when(player.getUsername()).thenReturn("LocalPlayer");

        String result = CommandManager.handleClientCommand("/clientchat", player);

        assertThat(result).isEqualTo("client-LocalPlayer");
    }

    @Test
    @DisplayName("Should expose command descriptors and primary commands")
    void shouldExposeCommandDescriptors() {
        CommandManager.register(new AlphaCommand());

        CommandDescriptor descriptor = CommandManager.getDescriptor("alpha");
        assertThat(descriptor).isNotNull();
        assertThat(descriptor.name()).isEqualTo("alpha");
        assertThat(descriptor.aliases()).contains("shared");
        assertThat(descriptor.scope()).isEqualTo(CommandScope.BOTH);
        assertThat(CommandManager.getPrimaryCommands()).containsKey("alpha");
        assertThat(CommandManager.getDescriptors()).containsKey("alpha");
    }

    @Test
    @DisplayName("Should fire command registration and unregistration events")
    void shouldFireCommandLifecycleEvents() {
        AtomicReference<CommandDescriptor> registered = new AtomicReference<>();
        AtomicReference<CommandDescriptor> unregistered = new AtomicReference<>();

        Object listener = new Object() {
            @SubscribeEvent(custom = "OnCommandRegistered")
            void onRegistered(CommandDescriptor descriptor) {
                if ("alpha".equals(descriptor.name())) {
                    registered.set(descriptor);
                }
            }

            @SubscribeEvent(custom = "OnCommandUnregistered")
            void onUnregistered(CommandDescriptor descriptor) {
                if ("alpha".equals(descriptor.name())) {
                    unregistered.set(descriptor);
                }
            }
        };

        EventManager.register(listener);
        try {
            CommandManager.register(new AlphaCommand());
            assertThat(registered.get()).isNotNull();

            CommandManager.unregister("alpha");
            assertThat(unregistered.get()).isNotNull();
        } finally {
            EventManager.unregister(listener);
            CommandManager.unregister("alpha");
        }
    }

    @Test
    @DisplayName("Should keep CommandContext arguments immutable")
    void shouldKeepContextArgumentsImmutable() {
        CommandContext context = new CommandContext(
                "Tester", null, null, "/test first second", new String[]{"first", "second"}
        );

        context.args()[0] = "changed";

        assertThat(context.getString(0, "")).isEqualTo("first");
        assertThat(context.joinArgs(0)).isEqualTo("first second");
    }

    @Test
    @DisplayName("Should reject vanilla command conflicts unless overrideVanilla is enabled")
    void shouldHandleVanillaConflictPolicy() {
        CommandManager.register(new VanillaSaveCommand());
        assertThat(CommandManager.getDescriptor("save")).isNull();

        CommandManager.register(new OverrideSaveCommand());
        assertThat(CommandManager.getDescriptor("save")).isNotNull();
    }

    @CommandInfo(name = "alpha", aliases = {"shared", "alpha-alias"}, scope = CommandScope.BOTH)
    private static final class AlphaCommand implements Command {
        @Override
        public String execute(CommandContext context) {
            return "alpha";
        }
    }

    @CommandInfo(name = "shared", scope = CommandScope.BOTH)
    private static final class SharedCommand implements Command {
        @Override
        public String execute(CommandContext context) {
            return "shared-primary";
        }
    }

    @CommandInfo(name = "ownedcmd", scope = CommandScope.BOTH)
    private static final class OwnedCommand implements Command {
        @Override
        public String execute(CommandContext context) {
            return "owned";
        }
    }

    @CommandInfo(name = "casecmd", scope = CommandScope.BOTH)
    private static final class CaseCommand implements Command {
        @Override
        public Map<String, Subcommand> subcommands() {
            return Map.of("VIP", context -> "case-ok");
        }

        @Override
        public String execute(CommandContext context) {
            return "root";
        }
    }

    @CommandInfo(name = "clientchat", scope = CommandScope.CHAT)
    private static final class ClientChatCommand implements Command {
        @Override
        public String execute(CommandContext context) {
            return "client-" + context.senderName();
        }
    }

    @CommandInfo(name = "save", scope = CommandScope.BOTH)
    private static final class VanillaSaveCommand implements Command {
        @Override
        public String execute(CommandContext context) {
            return "vanilla-save";
        }
    }

    @CommandInfo(name = "save", scope = CommandScope.BOTH, overrideVanilla = true)
    private static final class OverrideSaveCommand implements Command {
        @Override
        public String execute(CommandContext context) {
            return "override-save";
        }
    }
}
