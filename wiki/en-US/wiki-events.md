[Home](../wiki-language.md) > [Documentation](wiki-main.md) > Game Events

## 🏃 Game Events

The **Avrix** event system allows you to intercept any vanilla game events, define strict execution order via
priorities, and create and trigger custom events for inter-plugin communication.

---

### Subscribing to Standard Game Events

To subscribe to events, declare a method annotated with `@SubscribeEvent` and pass the target `DefaultEvents` constant.

```java
package com.example.plugin;

import com.avrix.api.events.DefaultEvents;
import com.avrix.api.events.SubscribeEvent;
import zombie.characters.IsoPlayer;
import zombie.inventory.types.HandWeapon;

public class PlayerEventListener {

    /**
     * Triggered when a player dies.
     */
    @SubscribeEvent(DefaultEvents.ON_PLAYER_DEATH)
    public void onPlayerDeath(IsoPlayer player) {
        System.out.println("Player died: " + player.getUsername());
    }

    /**
     * If event parameters are not required, the method can be declared with no arguments.
     */
    @SubscribeEvent(DefaultEvents.ON_TICK)
    public void onTick() {
        // Executed on every game tick
    }
}
```

---

### Registering and Unregistering Listeners

Listeners are registered through the static `EventManager` class during plugin initialization:

```java
public class ExamplePlugin implements Plugin {

    private final PlayerEventListener playerListener = new PlayerEventListener();

    @Override
    public void onInitialize() {
        // Register all annotated methods in the listener instance
        EventManager.register(playerListener);

        // Unregister listener when unloading or when no longer needed
        // EventManager.unregister(playerListener);
    }
}
```

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

    // Runs earlier than standard listeners
    @SubscribeEvent(value = DefaultEvents.ON_GAME_START, priority = EventManager.PRIORITY_HIGH)
    public void onEarlyGameStart() {
        System.out.println("Early initialization of plugin modules...");
    }

    // Runs last after all other listeners
    @SubscribeEvent(value = DefaultEvents.ON_GAME_START, priority = EventManager.PRIORITY_LOWEST)
    public void onLateGameStart() {
        System.out.println("Final inspection of world state.");
    }
}
```

---

### Custom Plugin Events

Plugins can define and dispatch custom events in two ways.

#### Option A: Custom Enum (Recommended)

Create a dedicated `enum` implementing the `com.avrix.api.events.Event` contract:

```java
package com.example.economy;

import com.avrix.api.events.Event;

public enum EconomyEvents implements Event {
    ON_BALANCE_CHANGE("Economy_OnBalanceChange"),
    ON_SHOP_PURCHASE("Economy_OnShopPurchase");

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

**Subscription:**

```java

@SubscribeEvent(custom = "Economy_OnBalanceChange")
public void onBalanceChanged(IsoPlayer player, double newBalance) {
    System.out.printf("Player %s balance updated: $%.2f%n", player.getUsername(), newBalance);
}
```

**Triggering the event in code:**

```java
// Dispatch via Event object
EventManager.invoke(EconomyEvents.ON_BALANCE_CHANGE, isoPlayer, 2500.0);
```

#### Option B: Direct String Constant

```java
// Subscription
@SubscribeEvent(custom = "MyMod_CustomEvent")
public void onCustom(String payload, int level) {
    // ...
}

// Dispatch
EventManager.

invoke("MyMod_CustomEvent","test_data",5);
```

---

### Type Matching and Primitive Rules

Avrix includes a built-in type adapter:

1. **Numeric Types**: If an event passes a `number`, you can declare it as `int`, `long`, `float`, or `double` —
   `EventManager` automatically converts the value without throwing `ClassCastException`.
2. **Missing Arguments**: If an event is triggered with fewer arguments than declared in the listener method:
    - Reference types (`String`, `IsoPlayer`, etc.) receive `null`.
    - Primitive types (`int`, `boolean`, `float`, etc.) receive default values (`0`, `false`, `0.0f`), preventing
      `NullPointerException` during unboxing.