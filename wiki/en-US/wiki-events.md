[Home](../wiki-language.md) > [Documentation](wiki-main.md) > Game Events

## 🏃 Game Events

The **Avrix** event system lets plugins receive Project Zomboid events, define execution order via priorities, and
create custom events for inter-plugin communication.

Game event names are discovered dynamically from Project Zomboid. You do not need to add constants to the API when the
game adds or renames an event.

Events are notifications only. Avrix does not cancel game actions from an event handler.

---

### Subscribing to Game Events

Use the `@SubscribeEvent` annotation with either a `DefaultEvents` constant or the event name as a string.

```java
package com.example.plugin;

import com.avrix.api.events.SubscribeEvent;
import zombie.characters.IsoPlayer;
import zombie.characters.IsoZombie;

public class PlayerEventListener {

    @SubscribeEvent(custom = "OnZombieUpdate")
    public void onZombieUpdate(IsoZombie zombie) {
        // Runs whenever Project Zomboid triggers the OnZombieUpdate event.
    }

    @SubscribeEvent(custom = "OnPlayerDeath")
    public void onPlayerDeath(IsoPlayer player) {
        System.out.println("Player died: " + player.getUsername());
    }

    @SubscribeEvent(custom = "OnTick")
    public void onTick() {
        // Parameters are optional.
    }
}
```

`DefaultEvents` remains available as a convenience catalog:

```java
@SubscribeEvent(DefaultEvents.ON_PLAYER_DEATH)
public void onPlayerDeath(IsoPlayer player) {
    // ...
}
```

New game events are delivered automatically. To inspect the event names known to the current game runtime:

```java
Set<String> gameEvents = EventManager.getKnownGameEvents();
```

Call `EventManager.refreshGameEvents()` after registering custom Lua events if you need them in the catalog immediately.

---

### Registering and Unregistering Listeners

Register listeners during plugin initialization. Passing `PluginData` as the owner lets Avrix remove every listener
belonging to the plugin in one call.

```java
package com.example.plugin;

import com.avrix.api.events.EventManager;
import com.avrix.plugins.Plugin;
import com.avrix.plugins.PluginData;

public class ExamplePlugin implements Plugin {

    private final PlayerEventListener playerListener = new PlayerEventListener();
    private PluginData pluginData;

    @Override
    public void onInitialize(PluginData pluginData) {
        this.pluginData = pluginData;
        EventManager.register(pluginData, playerListener);
    }

    @Override
    public void onShutdown() {
        EventManager.unregisterAll(pluginData);
    }
}
```

You can also use `EventManager.register(listener)` and `EventManager.unregister(listener)` when owner-scoped
registration is not needed.

Registering the same listener method twice does not create duplicate calls. Static and inherited annotated methods
are supported.

---

### Execution Priorities (`priority`)

The `priority` parameter accepts any integer (`int`) value. **Higher** values execute **earlier**:

| Priority                        |  Value  | Description                                            |
|:--------------------------------|:-------:|:-------------------------------------------------------|
| `EventManager.PRIORITY_HIGHEST` | `1000`  | Executes first (critical pre-processing)               |
| `EventManager.PRIORITY_HIGH`    |  `100`  | Early execution                                        |
| `EventManager.PRIORITY_NORMAL`  |   `0`   | Default priority                                       |
| `EventManager.PRIORITY_LOW`     | `-100`  | Late execution                                         |
| `EventManager.PRIORITY_LOWEST`  | `-1000` | Executes last (post-processing / monitoring / logging) |

```java
public class PriorityExampleListener {

    @SubscribeEvent(value = DefaultEvents.ON_GAME_START, priority = EventManager.PRIORITY_HIGH)
    public void onEarlyGameStart() {
        System.out.println("Early initialization of plugin modules...");
    }

    @SubscribeEvent(value = DefaultEvents.ON_GAME_START, priority = EventManager.PRIORITY_LOWEST)
    public void onLateGameStart() {
        System.out.println("Final inspection of world state.");
    }
}
```

For listeners with equal priorities, registration order is preserved.

---

### Custom Plugin Events

#### Option A: Named Event (Recommended)

Use a namespaced name to avoid collisions with game events and other plugins:

```java
Event event = Event.custom("economy:balance_changed");

// Notify Java listeners only.
EventManager.publish(event, isoPlayer, 2500.0);
```

Subscription:

```java
@SubscribeEvent(custom = "economy:balance_changed")
public void onBalanceChanged(IsoPlayer player, double newBalance) {
    System.out.printf("Player %s balance updated: $%.2f%n", player.getUsername(), newBalance);
}
```

#### Option B: Custom Enum

A custom enum implementing `Event` is useful when you want compile-time constants:

```java
package com.example.economy;

import com.avrix.api.events.Event;

public enum EconomyEvents implements Event {
    ON_BALANCE_CHANGE("economy:balance_changed"),
    ON_SHOP_PURCHASE("economy:shop_purchase");

    private final String eventName;

    EconomyEvents(String eventName) {
        this.eventName = eventName;
    }

    @Override
    public String getName() {
        return eventName;
    }
}
```

```java
EventManager.publish(EconomyEvents.ON_BALANCE_CHANGE, isoPlayer, 2500.0);
```

---

### Sending Custom Events to Lua

Avrix can forward a Java event to Project Zomboid Lua callbacks:

```java
// Notify Java listeners and Lua callbacks.
EventManager.publishBoth(event, isoPlayer, 2500.0);

// Forward to Lua only.
EventManager.publishToLua(event, isoPlayer, 2500.0);
```

Lua receives the same positional arguments. Up to eight arguments are supported, matching the Project Zomboid event
API. Forwarding is ignored when the Lua environment is not ready. Forwarded events do not re-enter the Java listener
bus recursively.

Lua mods can register a callback through the standard `Events` table:

```lua
local function onBalanceChanged(player, newBalance)
    print("Balance changed: " .. tostring(newBalance))
end

Events["economy:balance_changed"].Add(onBalanceChanged)
```

---

### Threading

- Project Zomboid game events are dispatched on the main game thread. Events triggered from other game threads are
  queued and replayed on the main thread.
- Custom Java events are dispatched on the thread that calls `publish` or `invoke`.
- `publishBoth` performs the Java dispatch first, then forwards the event to Lua.

Listener methods should not block the game thread.

---

### Type Matching and Primitive Rules

Avrix includes a built-in type adapter:

1. **Numeric Types**: If an event passes a `number`, you can declare it as `int`, `long`, `float`, or `double` —
   `EventManager` automatically converts the value without throwing `ClassCastException`.
2. **Missing Arguments**: If an event is triggered with fewer arguments than declared in the listener method:
    - Reference types (`String`, `IsoPlayer`, etc.) receive `null`.
    - Primitive types (`int`, `boolean`, `float`, etc.) receive default values (`0`, `false`, `0.0f`), preventing
      `NullPointerException` during unboxing.
3. **Exact wrapper types** are passed directly and are the fastest path. Primitive conversions are performed only when
   necessary.

Listener methods should return `void`. Return values are ignored and produce a warning during registration.

---

### Diagnostics

```java
EventManager.hasListeners("OnZombieUpdate");
EventManager.getSubscriberCount("OnZombieUpdate");
EventManager.getRegisteredEvents();
EventManager.getKnownGameEvents();
```

A listener method that throws an exception is logged and does not prevent other listeners from running.
