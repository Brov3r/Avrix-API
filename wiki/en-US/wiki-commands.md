[Home](../wiki-language.md) > [Documentation](wiki-main.md) > Chat / Console Commands

## 💬 Chat / Console Commands

The **Avrix** command framework provides declarative command registration, aliases, subcommands, execution scopes,
permissions, capabilities, cooldowns, command lifecycle events, and support for server or client command sources.

Commands are notification-style actions: they run the logic from `execute(...)` and return a response string.

---

### Creating a Basic Command

Implement `Command` and annotate the class with `@CommandInfo`.

```java
package com.example.plugin.commands;

import com.avrix.api.commands.Command;
import com.avrix.api.commands.CommandContext;
import com.avrix.api.commands.CommandInfo;
import com.avrix.api.commands.CommandScope;

@CommandInfo(
        name = "heal",
        aliases = {"healme", "hp"},
        description = "Restores health to the issuing player",
        usage = "/heal",
        permission = {"avrix.commands.heal"},
        cooldown = 10,
        scope = CommandScope.BOTH
)
public class HealCommand implements Command {

    @Override
    public String execute(CommandContext context) {
        if (!context.isPlayer()) {
            return "This command requires a player.";
        }

        return "Healing " + context.senderName();
    }
}
```

The return value is sent back through the normal command response channel. Returning `null` uses the default
`"Command executed successfully."` message.

---

### Registering and Unregistering Commands

Use an owner token such as `PluginData` so every command belonging to the plugin can be removed at once.

```java
public class ExamplePlugin implements Plugin {

    private final HealCommand healCommand = new HealCommand();
    private PluginData pluginData;

    @Override
    public void onInitialize(PluginData pluginData) {
        this.pluginData = pluginData;
        CommandManager.register(pluginData, healCommand);
    }

    @Override
    public void onShutdown() {
        CommandManager.unregisterAll(pluginData);
    }
}
```

`CommandManager.unregister("heal")`, `CommandManager.unregister(command)` and the legacy
`CommandManager.register(command)` are also available.

Registration rules:

- primary names and aliases are case-insensitive;
- names and aliases containing whitespace are rejected;
- a primary name cannot steal another command's alias;
- alias conflicts are logged and the conflicting alias is skipped;
- re-registering the same command class replaces the previous instance;
- a vanilla Project Zomboid command cannot be replaced unless `overrideVanilla = true` is set.

---

### Command Sources and Client Commands

`CommandSource` explicitly describes who issued a command:

```java
public enum Type {
    PLAYER,  // in-game player on a server
    CLIENT,  // local client-side player
    CONSOLE, // server console/terminal
    RCON     // remote console
}
```

Dispatch with an explicit source:

```java
CommandManager.dispatchCommand("/heal", CommandSource.serverConsole());
CommandManager.dispatchCommand("/heal", CommandSource.rcon());
CommandManager.dispatchCommand("/heal", CommandSource.player(connection, player));
```

Client-side dispatch helper:

```java
String response = CommandManager.handleClientCommand("/heal", localPlayer);
```

The legacy server overload remains available:

```java
String response = CommandManager.handleCommand("/heal", connection);
```

Client command support is API-level: the plugin is responsible for calling `handleClientCommand` from its client UI,
keybind, chat hook, or another client-side integration point. The built-in game mixin bridges only the server command
pipeline.

`CommandScope` rules:

- `CHAT` allows `PLAYER` and `CLIENT`;
- `CONSOLE` allows `CONSOLE` and `RCON`;
- `BOTH` allows every source.

---

### Subcommands

```java
@CommandInfo(
        name = "kit",
        description = "Claim player item kits",
        usage = "/kit <starter|vip>",
        permission = {"kit.use"},
        scope = CommandScope.BOTH
)
public class KitCommand implements Command {

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
            return 1;
        }

        @Override
        public TimeUnit cooldownUnit() {
            return TimeUnit.HOURS;
        }

        @Override
        public String execute(CommandContext context) {
            return "Starter kit claimed successfully!";
        }
    }

    private static class VipKitSubcommand implements Subcommand {
        @Override
        public String[] permission() {
            return new String[]{"kit.vip"};
        }

        @Override
        public long cooldown() {
            return 24;
        }

        @Override
        public TimeUnit cooldownUnit() {
            return TimeUnit.HOURS;
        }

        @Override
        public String execute(CommandContext context) {
            return "VIP kit claimed successfully!";
        }
    }
}
```

Subcommand names are normalized to lowercase. Subcommands are snapshotted when the parent command is registered, so
return an immutable map and avoid changing it at runtime.

---

### `@CommandInfo` Parameters

| Parameter          | Type           | Default             | Description                                                                  |
|:-------------------|:---------------|:--------------------|:-----------------------------------------------------------------------------|
| `name`             | `String`       | *(Required)*        | Primary command trigger without a leading slash.                             |
| `aliases`          | `String[]`     | `{}`                | Alternative triggers.                                                       |
| `description`      | `String`       | `""`                | Human-readable description for help/UI.                                     |
| `usage`            | `String`       | `""`                | Syntax guide.                                                               |
| `permission`       | `String[]`     | `{}`                | Custom permission nodes. Access is granted if at least one matches.          |
| `capability`       | `Capability[]` | `{}`                | Native Project Zomboid capabilities. Access is granted if at least one matches. |
| `scope`            | `CommandScope` | `CommandScope.BOTH` | Allowed source types.                                                       |
| `cooldown`         | `long`         | `0`                 | Cooldown duration for player sources.                                       |
| `cooldownUnit`     | `TimeUnit`     | `TimeUnit.SECONDS`  | Cooldown time unit.                                                         |
| `overrideVanilla`  | `boolean`      | `false`             | Allows replacing a vanilla PZ command with the same primary name or alias.   |

---

### Permissions and Cooldowns

1. **Console and RCON** bypass permission checks and cooldowns.
2. **OR logic** applies inside `permission` and `capability`.
3. **AND logic** applies between the root command and a selected subcommand.
4. Cooldowns are tracked per source identity and per command/subcommand path.
5. Cooldown checks are atomic and expired entries are pruned periodically.

---

### Command Context

`CommandContext` is immutable. The `args()` accessor returns a defensive copy.

```java
public record CommandContext(
        String senderName,
        CommandSource source,
        IsoPlayer player,
        UdpConnection connection,
        String rawCommand,
        String[] args
) {
    boolean isPlayer();
    Optional<IsoPlayer> getPlayer();
    int length();
    Optional<String> getArg(int index);
    String getString(int index, String fallback);
    Optional<Integer> getInt(int index);
    int getInt(int index, int fallback);
    Optional<Double> getDouble(int index);
    double getDouble(int index, double fallback);
    Optional<Boolean> getBoolean(int index);
    boolean getBoolean(int index, boolean fallback);
    String joinArgs(int startIndex);
    CommandContext subContext(int shift);
}
```

The legacy five-argument constructor remains available and infers a `CommandSource` from the player/connection.

---

### Quoted and Escaped Arguments

`CommandArgumentParser` supports single quotes, double quotes, and backslash escaping.

| Input command                              | `args()[0]`    | `args()[1]`    |
|:-------------------------------------------|:---------------|:---------------|
| `/heal "Miss Bekket"`                      | `Miss Bekket`  | *(absent)*     |
| `/tp 'John Doe' "Safe Zone 1"`             | `John Doe`     | `Safe Zone 1`  |
| `/heal "Player \"One\""`                   | `Player "One"` | *(absent)*     |
| `/give Brov3r Base.Axe 2`                  | `Brov3r`       | `Base.Axe`     |

Unclosed quotes are rejected with an invalid-syntax response instead of producing malformed tokens.

---

### Command Descriptors and Help

```java
Map<String, CommandDescriptor> descriptors = CommandManager.getDescriptors();
Map<String, Command> primaryCommands = CommandManager.getPrimaryCommands();

CommandDescriptor heal = CommandManager.getDescriptor("heal");
if (heal != null) {
    System.out.println(heal.name());
    System.out.println(heal.aliases());
    System.out.println(heal.description());
    System.out.println(heal.usage());
    System.out.println(heal.scope());
    System.out.println(heal.subcommands());
}
```

`getCommands()` still returns the legacy alias-inclusive map.

---

### Command Events

Command lifecycle and execution events use the Avrix event bus:

| Event                              | Parameters                                              | Description                                      |
|:-----------------------------------|:--------------------------------------------------------|:-------------------------------------------------|
| `ServerEvents.COMMAND_REGISTERED`   | `CommandDescriptor descriptor`                           | Command successfully registered                  |
| `ServerEvents.COMMAND_UNREGISTERED` | `CommandDescriptor descriptor`                           | Command removed                                  |
| `ServerEvents.COMMAND_EXECUTE`      | `CommandContext context, String commandPath`             | Before command/subcommand execution              |
| `ServerEvents.COMMAND_SUCCESS`      | `CommandContext context, String commandPath, String result, long elapsedMs` | After successful execution |
| `ServerEvents.COMMAND_FAILURE`      | `CommandContext context, String commandPath, String reason, Throwable error` | Validation failure or exception |

```java
@SubscribeEvent(custom = "OnCommandRegistered")
public void onCommandRegistered(CommandDescriptor descriptor) {
    System.out.println("Registered: " + descriptor.name());
}
```

Command registration/unregistration events are also delivered for client-side commands registered through the API.

---

### Messages and Error Handling

Validation messages are resolved through `CommandMessages` using Project Zomboid translation keys with English
fallbacks. Exception details are never returned to the sender; the response contains a short correlation id while the
full stack trace is written to the server log.
