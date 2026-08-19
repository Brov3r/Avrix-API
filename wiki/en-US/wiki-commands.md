[Main](../wiki-language.md) > [Documentation](wiki-main.md) > Chat / Console Commands

## 💬 Chat and Console Commands

The **Avrix** command system provides a declarative API for creating server-side and in-game commands. It features an
automated argument lexer with quote encapsulation support, strict execution environment scoping via `CommandScope`, and
deep integration with the `PermissionsManager` permission tree and Project Zomboid's native `Capability` subsystem.

---

### Creating a Command

To create a command, implement the functional `Command` interface and annotate the class with `@CommandInfo`.

```java
package com.example.plugin.commands;

import com.avrix.api.commands.Command;
import com.avrix.api.commands.CommandContext;
import com.avrix.api.commands.CommandInfo;
import com.avrix.api.commands.CommandScope;
import zombie.characters.IsoPlayer;

@CommandInfo(
        name = "heal",
        aliases = {"healme", "hp"},
        description = "Fully restores player health and body parts",
        usage = "/heal [\"player name\"]",
        permission = {"avrix.commands.heal", "avrix.admin.*"},
        scope = CommandScope.BOTH
)
public class HealCommand implements Command {

    @Override
    public String execute(CommandContext context) {
        // If target player username argument is provided
        if (context.args().length > 0) {
            String targetName = context.args();
            return "Player " + targetName + " has been fully healed.";
        }

        // If executed by an in-game player for self-healing
        if (context.isPlayer()) {
            IsoPlayer player = context.player();
            player.getBodyDamage().RestoreToFullHealth();
            return "You have been fully healed!";
        }

        return "Console usage: /heal <\"player name\">";
    }
}
```

---

### `@CommandInfo` Annotation Parameters

| Parameter     | Type           | Default             | Description                                                                                    |
|:--------------|:---------------|:--------------------|:-----------------------------------------------------------------------------------------------|
| `name`        | `String`       | *(Required)*        | Primary command trigger (without leading slash).                                               |
| `aliases`     | `String[]`     | `{}`                | Alternative aliases and abbreviations for invoking the command.                                |
| `description` | `String`       | `""`                | Human-readable description of the command's purpose.                                           |
| `usage`       | `String`       | `""`                | Syntax usage guide and expected parameter structure.                                           |
| `permission`  | `String[]`     | `{}`                | Required permission nodes. Access is granted if the sender holds **any** of the listed nodes.  |
| `capability`  | `Capability[]` | `{}`                | Standard Project Zomboid native capabilities required for execution.                           |
| `scope`       | `CommandScope` | `CommandScope.BOTH` | Allowed execution environment: chat only (`CHAT`), console only (`CONSOLE`), or both (`BOTH`). |

---

### Execution Scope (`CommandScope`)

The `CommandScope` enum isolates command execution based on the sender's origin:

```java
public enum CommandScope {
    CHAT,    // Executable ONLY by an active in-game player
    CONSOLE, // Executable ONLY by an administrator via server terminal or RCON
    BOTH     // Executable from both in-game chat and the server console
}
```

- If the server console attempts to run a `CommandScope.CHAT` command, the system returns:  
  `"This command can only be executed in-game by a player."`
- If an in-game player attempts to run a `CommandScope.CONSOLE` command, the system returns:  
  `"This command can only be executed from the server console."`

---

### Command Context (`CommandContext`)

When the `execute` method is invoked, an immutable `CommandContext` object is provided containing request metadata:

```java
public record CommandContext(
        String senderName,        // Sender username or "ServerConsole"
        IsoPlayer player,         // In-game IsoPlayer instance (or null if console)
        UdpConnection connection, // Network UDP socket (or null if console)
        String rawCommand,        // Full unprocessed command string
        String[] args             // Tokenized arguments array (enclosing quotes stripped)
) {
    public boolean isPlayer();             // true if invoked by an active in-game player

    public Optional<IsoPlayer> getPlayer(); // Safe access to player character entity

    public Optional<String> getArg(int i); // Safe indexed argument retrieval with bounds check
}
```

---

### Quoted Multi-Word Argument Lexer

The built-in lexer automatically aggregates phrases encapsulated in quotes (`""` or `''`) into single argument tokens
and strips outer quotation marks:

| Executed Command                        | `context.args()[0]` | `context.args()[1]` | `context.args()[2]` |
|:----------------------------------------|:--------------------|:--------------------|:--------------------|
| `/heal "Miss Bekket"`                   | `Miss Bekket`       | *(empty)*           | *(empty)*           |
| `/tp 'John Doe' "Safe Zone 1"`          | `John Doe`          | `Safe Zone 1`       | *(empty)*           |
| `/give Brov3r Base.Axe 2`               | `Brov3r`            | `Base.Axe`          | `2`                 |
| `/warn "Bad Player" 3 "Griefing walls"` | `Bad Player`        | `3`                 | `Griefing walls`    |

---

### Authorization and Permission Checks

Permissions are evaluated automatically prior to calling `execute()`:

1. **Server Console** always holds root privileges and bypasses permission checks.
2. **In-game Players** are authenticated against `PermissionsManager`:
    - Supports prefixes and wildcards (`avrix.commands.*`, `*`).
    - Supports role inheritance trees (`ExtendedRole`).
    - Supports personal user-specific permission nodes.

---

### Registering and Unregistering Commands

Command registration is handled via the static `CommandManager` class inside your plugin initialization entrypoint (
`onInitialize`):

```java
package com.example.plugin;

import com.avrix.api.commands.CommandManager;
import com.avrix.plugins.Plugin;
import com.avrix.plugins.PluginData;
import com.example.plugin.commands.HealCommand;
import com.example.plugin.commands.TeleportCommand;

public class ExamplePlugin implements Plugin {

    @Override
    public void onInitialize(PluginData pluginData) {
        // Registering command instances
        CommandManager.register(new HealCommand());
        CommandManager.register(new TeleportCommand());

        // Unregistering command upon plugin unload if necessary
        // CommandManager.unregister("heal");
    }
}
```