[Home](../wiki-language.md) > [Documentation](wiki-main.md) > Configuration Files

## ⚙️ Configuration Files

The **Avrix** configuration system provides a declarative API for creating, reading, modifying, and auto-saving plugin
YAML files. It is built on top of the **SpongePowered Configurate 4** library and is optimized for the Project Zomboid
architecture and the `KnotClassLoader` flat classloading environment.

The subsystem guarantees absolute default resource isolation for every mod, supports modern immutable data models (Java
25 Records), schemaless dynamic access without class declarations, and thread-safe runtime mutations.

---

### 📁 Architecture and Resource Isolation

All plugin configuration files are stored at a standardized location:  
`plugins/{pluginId}/{fileName}`

```text
plugins/
└── my-mod/
    ├── config.yml           # Primary configuration file
    └── languages/
        └── en.yml           # Additional configuration file
```

#### 🛡️ ClassLoader Collision Protection

Unlike the standard `ClassLoader.getResourceAsStream()` method, which can return an arbitrary file with a matching name
from a shared flat classpath, `ConfigManager` extracts bundled default files **directly from the physical JAR archive of
the specific plugin** (`PluginData.getPluginFile()`).

- 📄 **File already exists on disk** — it is loaded directly from the filesystem.
- 📦 **File is missing on disk** — it is automatically extracted from the root or subfolder of your plugin JAR archive,
  preserving all original comments and structure.
- 📝 **Resource is not in JAR** — an empty configuration file is created on disk.

---

### 🧩 1. Strongly Typed Configurations (Java Records)

The recommended approach is declaring configurations using immutable `record` components annotated with
`@ConfigSerializable`.

#### Step 1: Create a Template in Mod Resources

Create a `config.yml` file in your project's `src/main/resources/config.yml` folder:

```yaml
# ==========================================
# Avrix Zomboid Mod Configuration
# ==========================================

serverName: "Avrix Hardcore Survival"
maxZombies: 500
pvpEnabled: true

modules:
  antiCheat: true
  dropMultiplier: 1.5
```

#### Step 2: Define the Data Model

```java
package com.example.plugin.config;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@ConfigSerializable
public record ModConfig(
        String serverName,
        int maxZombies,
        boolean pvpEnabled,
        ModulesConfig modules
) {
    /**
     * Compact constructor enforcing non-null invariants and setting default values
     */
    public ModConfig {
        if (serverName == null) serverName = "Default Server";
        if (maxZombies <= 0) maxZombies = 100;
        if (modules == null) modules = new ModulesConfig(true, 1.0);
    }

    @ConfigSerializable
    public record ModulesConfig(
            boolean antiCheat,
            double dropMultiplier
    ) {
    }
}
```

#### Step 3: Load and Use

Load the configuration inside the plugin entrypoint's `onInitialize` method:

```java
package com.example.plugin;

import com.avrix.api.config.Config;
import com.avrix.api.config.ConfigManager;
import com.example.plugin.config.ModConfig;
import com.avrix.plugins.Plugin;
import com.avrix.plugins.PluginData;

public class ExamplePlugin implements Plugin {

    private Config<ModConfig> configHolder;

    @Override
    public void onInitialize(PluginData pluginData) {
        // Load config.yml (copied from JAR if not present on disk)
        this.configHolder = ConfigManager.loadDefaultConfig(pluginData, ModConfig.class);

        // Access active data
        ModConfig config = configHolder.get();
        System.out.println("Server: " + config.serverName());
        System.out.println("Loot Multiplier: " + config.modules().dropMultiplier());
    }
}
```

---

### 🌳 2. Schemaless Dynamic Access (`CommentedConfigurationNode`)

If the configuration layout is dynamic or contains arbitrary user-defined sections, you can interact with the raw node
tree directly without DTO/Record classes:

```java
package com.example.plugin;

import com.avrix.api.config.Config;
import com.avrix.api.config.ConfigManager;
import com.avrix.plugins.Plugin;
import com.avrix.plugins.PluginData;
import org.spongepowered.configurate.CommentedConfigurationNode;

public class DynamicExamplePlugin implements Plugin {

    @Override
    public void onInitialize(PluginData pluginData) {
        // Load without DTO/Record
        Config<CommentedConfigurationNode> config = ConfigManager.loadDefaultConfig(pluginData);

        // Read values with fallback defaults
        String host = config.node().node("database", "host").getString("127.0.0.1");
        int port = config.node().node("database", "port").getInt(3306);
        boolean debug = config.node().node("settings", "debug").getBoolean(false);

        // Mutate node values
        config.node().node("database", "host").set("192.168.1.100");
        config.node().node("custom", "newKey").set("Value");

        // Save modifications to disk
        config.save();
    }
}
```

---

### 📂 3. Working with Additional and Nested Files

Plugins can load any number of custom configuration files using relative subpaths:

```java
// Load plugins/{pluginId}/database.yml
Config<DbConfig> dbConfig = ConfigManager.load(pluginData, "database.yml", DbConfig.class);

// Load nested plugins/{pluginId}/languages/en.yml
Config<CommentedConfigurationNode> langConfig = ConfigManager.load(
        pluginData,
        "languages/en.yml",
        CommentedConfigurationNode.class
);
```

---

### 🔄 4. Mutation, Persistence, and Reloading

The `Config<T>` container provides full lifecycle management methods:

```java
Config<ModConfig> configHolder = ConfigManager.loadDefaultConfig(pluginData, ModConfig.class);

// 1. Full instance replacement and save
configHolder.

set(new ModConfig("New Title", 200,false,null));
        configHolder.

save();

// 2. Atomic update using a Consumer
configHolder.

update(current ->{
        configHolder.

set(new ModConfig(
            "Updated Title",
    current.maxZombies() +50,
        current.

pvpEnabled(),
            current.

modules()
    ));
            });

// 3. Force reload from disk (e.g., triggered by a /reload command)
            configHolder.

reload();
```

---

### 🛠️ `ConfigManager` Method Reference

| Method                                | Return Type                          | Description                                                       |
|:--------------------------------------|:-------------------------------------|:------------------------------------------------------------------|
| `loadDefaultConfig(pluginData, type)` | `Config<T>`                          | 📥 Loads/extracts the mod's `config.yml` into a typed model.      |
| `loadDefaultConfig(pluginData)`       | `Config<CommentedConfigurationNode>` | 🌳 Loads/extracts `config.yml` into a dynamic raw node tree.      |
| `load(pluginData, fileName, type)`    | `Config<T>`                          | 📄 Loads/extracts a file by relative path (e.g., `lang/en.yml`).  |
| `get(pluginId, fileName)`             | `Config<T>`                          | 🔍 Retrieves an already loaded configuration instance from cache. |
| `resolvePath(pluginId, fileName)`     | `Path`                               | 📍 Returns the normalized absolute file path on disk.             |
| `saveAll()`                           | `void`                               | 💾 Persists all cached configurations across all mods to disk.    |
| `reloadAll()`                         | `void`                               | 🔄 Reloads all cached configurations across all mods from disk.   |

---

### 🧰 `Config<T>` Method Reference

| Method                       | Return Type                  | Description                                                        |
|:-----------------------------|:-----------------------------|:-------------------------------------------------------------------|
| `get()`                      | `T`                          | 📦 Returns the active data model object (Record/POJO/Node).        |
| `set(T data)`                | `void`                       | ✏️ Replaces the active data model with a new instance.             |
| `node()`                     | `CommentedConfigurationNode` | 🌿 Provides direct access to the underlying Configurate root node. |
| `save()`                     | `void`                       | 💾 Serializes and persists the current state to disk.              |
| `reload()`                   | `void`                       | 🔄 Reloads from disk and refreshes the in-memory state.            |
| `update(Consumer<T> action)` | `void`                       | ⚡ Applies a mutating action and immediately saves changes to disk. |
| `getFile()`                  | `Path`                       | 📁 Returns the physical path to the configuration file.            |