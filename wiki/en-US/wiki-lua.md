[Home](../wiki-language.md) > [Documentation](wiki-main.md) > Lua Scripts

## 📜 Lua Scripts

Avrix API lets plugins:

- load custom Lua files from directories or JAR files;
- use `require` between custom, game and mod modules;
- override game and mod Lua tables;
- run scripts in the shared or an isolated Lua environment;
- control visibility of game, mod and Java exposure;
- call Java code from Lua and exchange events between Java and Lua.

The loader runs after game and mod Lua files. Scripts are not isolated from the JVM: this is a loader and
configurable visibility layer, not a security sandbox.

---

### Script Sources

Two source types are supported:

```java
LuaScriptSource directory = LuaScriptSource.directory(Path.of("C:/MyPlugin/lua"));
LuaScriptSource jar = LuaScriptSource.jar(Path.of("plugins/MyPlugin.jar"), "lua");
```

- `directory(root)` — a regular directory.
- `jar(jar, prefix)` — JAR entries under the prefix.
- The JAR is opened lazily and kept open; files are never extracted to a temporary directory.
- Module paths always use `/`; `..` traversal and absolute paths are rejected.

Register the source:

```java
LuaScriptSource source = LuaScriptLoader.addSource(
        LuaScriptSource.directory(Path.of("C:/MyPlugin/lua"))
);
```

Adding a source with an already registered id returns the existing instance.

---

### Loading Files

One module from registered sources:

```java
LuaScriptLoader.load("feature/main");
```

Several selected modules:

```java
LuaScriptLoader.loadAll("feature/main", "feature/config", "feature/ui");
```

A recursive group:

```java
LuaScriptLoader.loadGroup(source, "feature");
```

A directory group with automatic source registration:

```java
LuaScriptLoader.loadGroup(Path.of("C:/MyPlugin/lua/feature"));
```

A single file with automatic parent-directory registration:

```java
LuaScriptLoader.load(Path.of("C:/MyPlugin/lua/feature/main.lua"));
```

A single JAR entry:

```java
LuaScriptLoader.loadFromJar(pluginJar, "lua/feature/main.lua");
```

A whole JAR directory:

```java
LuaScriptLoader.loadGroupFromJar(pluginJar, "lua/feature");
```

`load(Path)` and `loadFromJar(...)` make the file's parent directory the `require` root.
Register a broader source explicitly when modules use paths above that directory.

---

### `require`

Custom `require` works as follows:

1. Normalize the module name: replace `\` with `/`, append `.lua` if needed.
2. Search custom sources in registration order.
3. Load and cache the module if found.
4. Fall back to the Project Zomboid `require` when not found.

Example:

```text
lua/
  feature/
    init.lua
    helper.lua
```

```java
LuaScriptSource source = LuaScriptLoader.addSource(
        LuaScriptSource.directory(Path.of("C:/MyPlugin/lua"))
);
LuaScriptLoader.loadGroup(source, "feature");
```

`init.lua`:

```lua
local helper = require("feature/helper")
```

`helper.lua`:

```lua
return "helper-ok"
```

Notes:

- custom sources take precedence over vanilla sources;
- a repeated `require` returns the cached result;
- recursive `require` of the same module is logged and returns `nil`;
- custom modules may require game/mod modules;
- game/mod Lua files may require custom modules;
- dots are not converted to slashes: use `feature/helper` naming like Project Zomboid.

---

### Load Timing

Order:

1. `LuaManager.init()` — the Lua environment is created and Avrix exposures are applied.
2. `LuaManager.LoadDirBase()` — game and mod Lua files are loaded.
3. `OnGameBoot` — Avrix loads custom scripts.
4. Lua `OnGameBoot` callbacks, including callbacks registered by custom scripts.

The load starts in the `HEAD` of `OnGameBoot` because:

- dedicated servers call `OnGameBoot`, but do not use `OnTick` and
  `LuaEventManager.RunQueuedEvents()` the same way the client does;
- `OnGameBoot` may have no Lua callbacks and would otherwise return before a `RETURN` hook.

Requests made in `onInitialize` wait for `OnGameBoot`. If `load(...)` is called after `OnGameBoot` on the
main thread, the script loads immediately.

Late loads from background threads are drained:

- on the client — in `LuaEventManager.RunQueuedEvents()`;
- on the server — in `GameServer.mainLoopDealWithNetData(...)`.

---

### Overriding Game and Mod Tables

Custom scripts load after game and mod `.lua` files, so they see the final global tables.

Prefer changing the table **in place**:

```lua
function ISBaseObject:someMethod(...)
    -- new implementation
end

ISBaseObject.SOME_FIELD = 42
```

Why in place:

- every reference to the same table sees the change;
- derived tables/classes whose `__index` points to the table see the change;
- inheritance keeps working.

Full replacement:

```lua
SomeTable = {
    -- ...
}
```

only affects future global lookups. Local aliases and already created derived tables keep the old object.

If you need to win over mod changes made in their own `OnGameBoot` handlers, register
`Events.OnGameBoot.Add(...)` from a custom script: it is appended after already loaded mod handlers and runs
last.

---

### Java Exposure in Lua

Use `LuaExposer`:

```java
LuaExposer.exposeClass(MyApi.class);
LuaExposer.exposeMethod(MyApi.class, "doWork", "work");
LuaExposer.exposeGlobal("globalFunction", myObject, "handle");
LuaExposer.exposeGlobalObject(new MyLuaFunctions());
```

From Lua:

```lua
local api = MyApi.new()
api:doWork()
api:work()

MyApi.staticMethod()
globalFunction()
myAnnotatedGlobal()
```

`exposeClass` adds:

- constructors as `ClassName.new(...)`;
- public static methods and fields;
- public instance methods;
- the simple class name and the full package path.

`@HiddenFromLua` excludes a member. Name collisions are never overwritten: Avrix logs a warning and keeps the
existing value.

Registrations return a `LuaRegistration`:

```java
LuaRegistration registration = LuaExposer.exposeClass(MyApi.class);
LuaExposer.unregister(registration);
```

Exposures are reapplied after every `LuaManager.init()` and are visible to shared and isolated Lua environments
according to their visibility policy.

---

### Events Java ↔ Lua

#### Game/custom event → Java

```java
@SubscribeEvent(custom = "OnGameBoot")
public void onGameBoot() {
    // ...
}
```

```java
EventManager.register(listener);
```

#### Java → Lua

First register the event in Lua:

```java
LuaEventManager.AddEvent("my:event");
```

Then publish it:

```java
EventManager.publishBoth("my:event", "payload"); // Java + Lua
EventManager.publishToLua("my:event", "payload"); // Lua only
```

Lua:

```lua
Events["my:event"].Add(function(payload)
    print(tostring(payload))
end)
```

`publishBoth` calls Java listeners first, then forwards the event to Lua. Forwarding does not invoke Java
listeners again. Up to eight arguments are supported.

---

### Isolated Lua Environments

By default, scripts load into the shared environment:

```java
LuaScriptLoader.load("feature/main");
```

For a separate environment:

```java
LuaEnvironmentPolicy policy = LuaEnvironmentPolicy.isolated()
        .toBuilder()
        .includeAvrixExposure(true)
        .includeProjectZomboidExposure(false)
        .includeGameLua(false)
        .includeModLua(false)
        .includeCustomLua(false)
        .allowGlobal("print")
        .build();

LuaScriptEnvironment environment = LuaScriptLoader.createEnvironment("sandbox", policy);

LuaScriptLoader.load(environment, "feature/main");
LuaScriptLoader.loadGroup(environment, source, "feature");
```

Policy flags:

| Flag                            | Visibility category                                  |
|:--------------------------------|:-----------------------------------------------------|
| `includeStandardLibrary`        | standard `print`, `table`, `string`, `math`, etc.    |
| `includeProjectZomboidExposure` | Java classes/functions exposed by Project Zomboid    |
| `includeAvrixExposure`          | Avrix exposures registered via `LuaExposer`          |
| `includeGameLua`                | globals created by game Lua files                    |
| `includeModLua`                 | globals created by mod Lua files                     |
| `includeCustomLua`              | globals created by other custom scripts in the shared environment |
| `includeUnknownGlobals`         | globals with an unknown category                     |
| `allowVanillaRequire`           | fallback to the vanilla `require`                    |

Per-name overrides:

```java
LuaEnvironmentPolicy policy = LuaEnvironmentPolicy.isolated()
        .toBuilder()
        .allowGlobal("SomeGameTable")
        .denyGlobal("os")
        .build();
```

`allowGlobal` allows one name even when its category is disabled. `denyGlobal` always wins.

Sandbox `require` loads custom modules into that environment. If `allowVanillaRequire=true` and the module is
not found in custom sources, the vanilla `require` is called; its globals go to the shared environment and are
visible to the sandbox only through the configured categories.

Important: this is **visibility isolation**, not a security sandbox. Allowed tables and functions are shared by
reference and can be modified by the script.

---

### Lifecycle and Cleanup

```java
LuaScriptLoader.removeSource(source);
LuaScriptLoader.removeEnvironment("sandbox");
LuaScriptLoader.invalidate();
LuaScriptLoader.unregisterAll();
```

- `removeSource` closes JAR sources.
- `removeEnvironment` removes the environment and its requests.
- `invalidate` clears the resolved-module cache, useful when files are added to an existing directory.
- `unregisterAll` removes sources, environments and requests.

After every `LuaManager.init()`, the loader reapplies registered requests and clears load caches.

---

### Optimization

- JARs are not extracted; one open `JarFile` is used.
- `require` caches resolved modules and negative lookups.
- Module results are cached; repeated `require` does not compile the file again.
- Group loading scans files once and sorts them deterministically.
- With no custom sources, the `require` hook exits immediately.
- The hot `require` path is lock-free because Lua is single-threaded.

---

### Limitations

- File-level override that prevents the original file from running is not supported; override tables and
  functions in place.
- Types in Java method signatures are not exposed recursively; register them separately with `LuaExposer`.
- Local tables inside a Lua module cannot be overridden from outside unless the module returns or publishes them.
- `require` does not resolve paths relative to the current file; names are resolved from the source root, as in PZ.
