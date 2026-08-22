[Home](../wiki-language.md) > [Documentation](wiki-main.md) > Chat / Console Commands

## 💬 Chat / Console Commands

The **Avrix** command framework provides a declarative API for creating server and in-game commands. It features
automated quoted argument lexing, a hierarchical subcommand tree (`Subcommand`), independent execution cooldown timers (
`Cooldowns`), type-safe argument access within `CommandContext`, and seamless integration with `PermissionsManager` and
native Project Zomboid `Capability` flags.

---

### Creating a Basic Command

To create a command, implement the `Command` interface and annotate the class with `@CommandInfo`.

```java
package com.example.plugin.commands;

import com.avrix.api.commands.Command;
import com.avrix.api.commands.CommandContext;
import com.avrix.api.commands.CommandInfo;
import com.avrix.api.commands.CommandScope;
import zombie.characters.IsoPlayer;

import java.util.concurrent.TimeUnit;

@CommandInfo(
        name = "heal",
        aliases = {"healme", "hp"},
        description = "Restores full health to a target player or self",
        usage = "/heal [\"player name\"]",
        permission = {"avrix.commands.heal", "avrix.admin.*"},
        cooldown = 10,
        cooldownUnit = TimeUnit.SECONDS,
        scope = CommandScope.BOTH
)
public class HealCommand implements Command {

    @Override
    public String execute(CommandContext context) {
        return "Successfully restored health for: " + target.getUsername();
    }
}
```

---

### Creating Hierarchical Subcommands

Commands can delegate execution to child subcommands by overriding `subcommands()`. Each subcommand can define its own
permissions, native capabilities, and isolated cooldown rules.

```java
package com.example.plugin.commands;

import com.avrix.api.commands.*;
import zombie.characters.IsoPlayer;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@CommandInfo(
        name = "kit",
        description = "Claim player item kits",
        usage = "/kit <starter|vip>",
        permission = {"kit.use"},
        scope = CommandScope.CHAT
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
        return "Available kits: starter, vip. Usage: /kit <name>";
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

---

### `@CommandInfo` Annotation Parameters

| Parameter      | Type           | Default             | Description                                                                                      |
|:---------------|:---------------|:--------------------|:-------------------------------------------------------------------------------------------------|
| `name`         | `String`       | *(Required)*        | Primary command trigger (without leading slash).                                                 |
| `aliases`      | `String[]`     | `{}`                | Additional aliases and shortcuts for invoking the command.                                       |
| `description`  | `String`       | `""`                | Human-readable description of the command purpose.                                               |
| `usage`        | `String`       | `""`                | Syntax guide and expected parameter format.                                                      |
| `permission`   | `String[]`     | `{}`                | Custom permission nodes. Access is granted if the issuer holds **at least one** node (OR logic). |
| `capability`   | `Capability[]` | `{}`                | Native Project Zomboid capability flags required for execution.                                  |
| `cooldown`     | `long`         | `0`                 | Cooldown duration between consecutive command executions by a player.                            |
| `cooldownUnit` | `TimeUnit`     | `TimeUnit.SECONDS`  | Time unit for the `cooldown` parameter.                                                          |
| `scope`        | `CommandScope` | `CommandScope.BOTH` | Allowed execution environment: chat only (`CHAT`), console only (`CONSOLE`), or both (`BOTH`).   |

---

### Execution Scope (`CommandScope`)

The `CommandScope` enum isolates invocation based on the sender's environment:

```java
public enum CommandScope {
    CHAT,    // Executable ONLY by an active in-game player via chat
    CONSOLE, // Executable ONLY by an administrator from the server terminal or RCON
    BOTH     // Accessible from both player chat and server console
}
```

- If the server console attempts to invoke a command with `scope = CommandScope.CHAT`, the engine returns:  
  `"This command can only be executed in-game by a player."`
- If an in-game player attempts to invoke a command with `scope = CommandScope.CONSOLE`, the engine returns:  
  `"This command can only be executed from the server console."`

---

### Command Context (`CommandContext`)

When `execute` is called, an immutable `CommandContext` object is supplied, providing type-safe parsing helpers and
eliminating boilerplate exception handling:

```java
public record CommandContext(
        String senderName,        // Sender display name (player username or "ServerConsole")
        IsoPlayer player,         // IsoPlayer instance (or null for server console)
        UdpConnection connection, // Client network UDP connection (or null for console)
        String rawCommand,        // Full raw unparsed command string
        String[] args             // Clean tokenized arguments (quotes stripped)
) {
    public boolean isPlayer();                             // true if issued by an active player

    public Optional<IsoPlayer> getPlayer();                // Safe optional accessor for the sender entity

    public int length();                                   // Total argument count

    public Optional<String> getArg(int index);             // Retrieves argument wrapped in Optional

    public String getString(int index, String fallback);   // String argument with default fallback

    public Optional<Integer> getInt(int index);            // Integer parsing

    public int getInt(int index, int fallback);            // Integer parsing with default fallback

    public Optional<Double> getDouble(int index);          // Double parsing

    public double getDouble(int index, double fallback);   // Double parsing with default fallback

    public Optional<Boolean> getBoolean(int index);        // Boolean parsing (true/false, 1/0, yes/no)

    public boolean getBoolean(int index, boolean fallback);// Boolean parsing with default fallback

    public String joinArgs(int startIndex);                // Joins all remaining arguments with single spaces

    public CommandContext subContext(int shift);           // Creates child context with shifted arguments
}
```

---

### Quoted String Lexing

The built-in `CommandArgumentParser` automatically groups multi-word strings enclosed in single (`''`) or double (`""`)
quotes into a single argument token and strips external quote wrappers:

| Input Command                           | `context.args()[0]` | `context.args()[1]` | `context.args()[2]` |
|:----------------------------------------|:--------------------|:--------------------|:--------------------|
| `/heal "Miss Bekket"`                   | `Miss Bekket`       | *(absent)*          | *(absent)*          |
| `/tp 'John Doe' "Safe Zone 1"`          | `John Doe`          | `Safe Zone 1`       | *(absent)*          |
| `/give Brov3r Base.Axe 2`               | `Brov3r`            | `Base.Axe`          | `2`                 |
| `/warn "Bad Player" 3 "Griefing walls"` | `Bad Player`        | `3`                 | `Griefing walls`    |

---

### Permissions and Cooldown Rules

1. **Console Priority:** The server console always maintains root access, bypassing all permission constraints and
   ignoring cooldown timers.
2. **Permission Evaluation (OR Logic):** Access is granted if the issuer possesses at least one configured string
   permission node (`permission`) or native capability (`capability`).
3. **Subcommand Hierarchy (AND Logic):** To execute `/kit starter`, a player must possess both the root command
   permission (`kit.use`) **and** the specific subcommand permission (`kit.starter`).
4. **Cooldown Isolation:** Cooldown timers are tracked per-player and stored independently for root commands and
   distinct subcommands.

---

### Registering and Unregistering Commands

Register commands via the static `CommandManager` during plugin initialization:

```java
package com.example.plugin;

import com.avrix.api.commands.CommandManager;
import com.avrix.plugins.Plugin;
import com.avrix.plugins.PluginData;
import com.example.plugin.commands.HealCommand;
import com.example.plugin.commands.KitCommand;

public class ExamplePlugin implements Plugin {

    @Override
    public void onInitialize(PluginData pluginData) {
        // Register command instances
        CommandManager.register(new HealCommand());
        CommandManager.register(new KitCommand());

        // Unregister command when plugin unloads or reloads
        // CommandManager.unregister("heal");
    }
}
```