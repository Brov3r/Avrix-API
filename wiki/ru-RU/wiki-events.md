[Главная](../wiki-language.md) > [Документация](wiki-main.md) > Игровые события

## 🏃 Игровые события

Система событий **Avrix** позволяет перехватывать любые ванильные события игры, настраивать строгий порядок их обработки
через приоритеты, а также создавать и вызывать собственные кастомные события для взаимодействия между плагинами.

---

### Подписка на стандартные события игры

Для подписки на события объявите метод с аннотацией `@SubscribeEvent` и передайте в неё константу `DefaultEvents`.

```java
package com.example.plugin;

import com.avrix.api.events.DefaultEvents;
import com.avrix.api.events.SubscribeEvent;
import zombie.characters.IsoPlayer;
import zombie.inventory.types.HandWeapon;

public class PlayerEventListener {

    /**
     * Вызывается при смерти игрока.
     */
    @SubscribeEvent(DefaultEvents.ON_PLAYER_DEATH)
    public void onPlayerDeath(IsoPlayer player) {
        System.out.println("Игрок погиб: " + player.getUsername());
    }

    /**
     * Если параметры события не нужны, метод может быть без аргументов.
     */
    @SubscribeEvent(DefaultEvents.ON_TICK)
    public void onTick() {
        // Выполняется каждый игровой тик
    }
}
```

---

### Регистрация и отписка слушателей

Регистрация объектов со слушателями выполняется через статический класс `EventManager` при инициализации плагина:

```java
public class ExamplePlugin implements Plugin {

    private final PlayerEventListener playerListener = new PlayerEventListener();

    @Override
    public void onInitialize() {
        // Регистрация всех аннотированных методов в объекте
        EventManager.register(playerListener);

        // Отписка слушателей при выгрузке плагина или при необходимости
        // EventManager.unregister(playerListener);
    }
}
```

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

    // Выполнится раньше обычных слушателей
    @SubscribeEvent(value = DefaultEvents.ON_GAME_START, priority = EventManager.PRIORITY_HIGH)
    public void onEarlyGameStart() {
        System.out.println("Ранняя инициализация модулей плагина...");
    }

    // Выполнится позже всех
    @SubscribeEvent(value = DefaultEvents.ON_GAME_START, priority = EventManager.PRIORITY_LOWEST)
    public void onLateGameStart() {
        System.out.println("Финальный опрос состояния мира.");
    }
}
```

---

### Кастомные события плагинов

Плагины могут определять и вызывать собственные события двумя способами.

#### Вариант А: Через собственный Enum (Рекомендуется)

Создайте собственный `enum`, реализующий контракт `com.avrix.api.events.Event`:

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

**Подписка:**

```java

@SubscribeEvent(custom = "Economy_OnBalanceChange")
public void onBalanceChanged(IsoPlayer player, double newBalance) {
    System.out.printf("Баланс игрока %s обновлен: $%.2f%n", player.getUsername(), newBalance);
}
```

**Вызов события в коде:**

```java
// Вызов по объекту Event
EventManager.invoke(EconomyEvents.ON_BALANCE_CHANGE, isoPlayer, 2500.0);
```

#### Вариант Б: Через прямую строковую константу

```java
// Подписка
@SubscribeEvent(custom = "MyMod_CustomEvent")
public void onCustom(String payload, int level) {
    // ...
}

// Вызов
EventManager.invoke("MyMod_CustomEvent","test_data",5);
```

---

### Правила сопоставления типов и примитивов

В Avrix встроен адаптер типов:

1. **Числа**: Если событие передает `number`, вы можете свободно принимать его как `int`, `long`, `float` или `double` —
   `EventManager` автоматически преобразует значение без ошибок `ClassCastException`.
2. **Недостающие аргументы**: Если событие вызвалось с меньшим количеством аргументов, чем указано в методе:
    - Для ссылочных типов (`String`, `IsoPlayer` и т.д.) передается `null`.
    - Для примитивных типов (`int`, `boolean`, `float`) подставляются значения по умолчанию (`0`, `false`, `0.0f`), что
      гарантирует защиту от `NullPointerException` при анбоксинге.