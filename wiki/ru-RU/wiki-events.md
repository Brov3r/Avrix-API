[Главная](../wiki-language.md) > [Документация](wiki-main.md) > Игровые события

## 🏃 Игровые события

Система событий **Avrix** позволяет плагинам получать события Project Zomboid, задавать порядок выполнения через
приоритеты и создавать собственные события для взаимодействия между плагинами.

Имена игровых событий определяются динамически из Project Zomboid. При добавлении или переименовании события в игре
не нужно добавлять новую константу в API.

События являются только уведомлениями. Avrix не отменяет игровые действия из обработчика события.

---

### Подписка на игровые события

Используйте аннотацию `@SubscribeEvent` с константой `DefaultEvents` или со строковым именем события.

```java
package com.example.plugin;

import com.avrix.api.events.SubscribeEvent;
import zombie.characters.IsoPlayer;
import zombie.characters.IsoZombie;

public class PlayerEventListener {

    @SubscribeEvent(custom = "OnZombieUpdate")
    public void onZombieUpdate(IsoZombie zombie) {
        // Вызывается при каждом событии OnZombieUpdate от Project Zomboid.
    }

    @SubscribeEvent(custom = "OnPlayerDeath")
    public void onPlayerDeath(IsoPlayer player) {
        System.out.println("Игрок погиб: " + player.getUsername());
    }

    @SubscribeEvent(custom = "OnTick")
    public void onTick() {
        // Параметры необязательны.
    }
}
```

`DefaultEvents` остаётся доступным как удобный каталог:

```java
@SubscribeEvent(DefaultEvents.ON_PLAYER_DEATH)
public void onPlayerDeath(IsoPlayer player) {
    // ...
}
```

Новые игровые события доставляются автоматически. Посмотреть известные текущей игре имена можно так:

```java
Set<String> gameEvents = EventManager.getKnownGameEvents();
```

Если вы регистрируете собственные Lua-события и хотите сразу видеть их в каталоге, вызовите
`EventManager.refreshGameEvents()`.

---

### Регистрация и отписка слушателей

Регистрируйте слушателей при инициализации плагина. Если передать `PluginData` как владельца, Avrix сможет одной
командой удалить все слушатели плагина.

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

Если привязка к владельцу не нужна, используйте `EventManager.register(listener)` и
`EventManager.unregister(listener)`.

Повторная регистрация того же метода не создаёт дублирующих вызовов. Статические и унаследованные
аннотированные методы поддерживаются.

---

### Приоритеты выполнения (`priority`)

Параметр `priority` принимает любое целочисленное значение `int`. Чем **выше** число, тем **раньше** выполнится метод:

| Приоритет                       | Значение | Описание                                                 |
|:--------------------------------|:--------:|:---------------------------------------------------------|
| `EventManager.PRIORITY_HIGHEST` |  `1000`  | Выполняется самым первым (критическая пре-обработка)     |
| `EventManager.PRIORITY_HIGH`    |  `100`   | Раннее выполнение                                        |
| `EventManager.PRIORITY_NORMAL`  |   `0`    | Приоритет по умолчанию                                   |
| `EventManager.PRIORITY_LOW`     |  `-100`  | Позднее выполнение                                       |
| `EventManager.PRIORITY_LOWEST`  | `-1000`  | Выполняется в самом конце (пост-обработка / логирование) |

```java
public class PriorityExampleListener {

    @SubscribeEvent(value = DefaultEvents.ON_GAME_START, priority = EventManager.PRIORITY_HIGH)
    public void onEarlyGameStart() {
        System.out.println("Ранняя инициализация модулей плагина...");
    }

    @SubscribeEvent(value = DefaultEvents.ON_GAME_START, priority = EventManager.PRIORITY_LOWEST)
    public void onLateGameStart() {
        System.out.println("Финальный опрос состояния мира.");
    }
}
```

Для слушателей с одинаковым приоритетом сохраняется порядок регистрации.

---

### Кастомные события плагинов

#### Вариант А: Именованное событие (рекомендуется)

Используйте пространство имён, чтобы не пересекаться с игровыми событиями и другими плагинами:

```java
Event event = Event.custom("economy:balance_changed");

// Только Java-слушатели.
EventManager.publish(event, isoPlayer, 2500.0);
```

Подписка:

```java
@SubscribeEvent(custom = "economy:balance_changed")
public void onBalanceChanged(IsoPlayer player, double newBalance) {
    System.out.printf("Баланс игрока %s обновлен: $%.2f%n", player.getUsername(), newBalance);
}
```

#### Вариант Б: Собственный Enum

Собственный enum, реализующий `Event`, удобен, если нужны compile-time константы:

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

### Передача кастомных событий в Lua

Avrix умеет передавать Java-событие в Lua-callback'и Project Zomboid:

```java
// Уведомить Java-слушателей и Lua-callback'и.
EventManager.publishBoth(event, isoPlayer, 2500.0);

// Только в Lua.
EventManager.publishToLua(event, isoPlayer, 2500.0);
```

Lua получает те же позиционные аргументы. Поддерживается до восьми аргументов — столько же, сколько в API событий
Project Zomboid. Передача игнорируется, пока Lua-окружение не готово. Пересланное событие не вызывает Java-слушателей
повторно.

Lua-моды могут зарегистрировать callback через стандартную таблицу `Events`:

```lua
local function onBalanceChanged(player, newBalance)
    print("Balance changed: " .. tostring(newBalance))
end

Events["economy:balance_changed"].Add(onBalanceChanged)
```

---

### Потоки

- Игровые события Project Zomboid доставляются в главном игровом потоке. События из других потоков ставятся в
  очередь и выполняются позже в главном потоке.
- Кастомные Java-события выполняются в том потоке, который вызвал `publish` или `invoke`.
- `publishBoth` сначала уведомляет Java-слушателей, затем передаёт событие в Lua.

Обработчики не должны блокировать игровой поток.

---

### Правила сопоставления типов и примитивов

В Avrix встроен адаптер типов:

1. **Числа**: Если событие передаёт `number`, вы можете принимать его как `int`, `long`, `float` или `double` —
   `EventManager` автоматически преобразует значение без ошибок `ClassCastException`.
2. **Недостающие аргументы**: Если событие вызвано с меньшим количеством аргументов, чем указано в методе:
    - Для ссылочных типов (`String`, `IsoPlayer` и т.д.) передаётся `null`.
    - Для примитивных типов (`int`, `boolean`, `float`) подставляются значения по умолчанию (`0`, `false`, `0.0f`),
      что защищает от `NullPointerException` при анбоксинге.
3. **Точные wrapper-типы** передаются напрямую и работают быстрее всего. Примитивные преобразования выполняются
   только при необходимости.

Методы-слушатели должны возвращать `void`. Возвращаемые значения игнорируются, а при регистрации выводится
предупреждение.

---

### Диагностика

```java
EventManager.hasListeners("OnZombieUpdate");
EventManager.getSubscriberCount("OnZombieUpdate");
EventManager.getRegisteredEvents();
EventManager.getKnownGameEvents();
```

Если обработчик выбрасывает исключение, оно логируется и не мешает остальным слушателям.
