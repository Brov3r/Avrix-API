[Главная](../wiki-language.md) > [Документация](wiki-main.md) > Команды чата и консоли

## 💬 Команды чата и консоли

Фреймворк команд **Avrix** предоставляет декларативную регистрацию, алиасы, subcommand'ы, области выполнения,
permissions, capabilities, cooldown'ы, события жизненного цикла команд и поддержку серверных и клиентских источников.

Команды выполняют логику из `execute(...)` и возвращают строку ответа.

---

### Создание базовой команды

Реализуйте `Command` и добавьте аннотацию `@CommandInfo`.

```java
package com.example.plugin.commands;

import com.avrix.api.commands.Command;
import com.avrix.api.commands.CommandContext;
import com.avrix.api.commands.CommandInfo;
import com.avrix.api.commands.CommandScope;

@CommandInfo(
        name = "heal",
        aliases = {"healme", "hp"},
        description = "Восстанавливает здоровье отправителю",
        usage = "/heal",
        permission = {"avrix.commands.heal"},
        cooldown = 10,
        scope = CommandScope.BOTH
)
public class HealCommand implements Command {

    @Override
    public String execute(CommandContext context) {
        if (!context.isPlayer()) {
            return "Команда требует игрока.";
        }

        return "Лечение: " + context.senderName();
    }
}
```

Возвращённая строка отправляется обычному каналу ответа команды. При возврате `null` используется сообщение
`"Command executed successfully."`.

---

### Регистрация и отмена регистрации

Используйте owner-токен, например `PluginData`, чтобы удалить все команды плагина одной операцией.

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

Также доступны `CommandManager.unregister("heal")`, `CommandManager.unregister(command)` и legacy-метод
`CommandManager.register(command)`.

Правила регистрации:

- имена и алиасы нечувствительны к регистру;
- имена с пробелами отклоняются;
- primary-имя не может забрать алиас другой команды;
- конфликтующие алиасы логируются и пропускаются;
- повторная регистрация команды того же класса заменяет предыдущий экземпляр;
- ванильную команду PZ нельзя заменить без `overrideVanilla = true`.

---

### Источники команд и клиентские команды

`CommandSource` явно описывает источник:

```java
public enum Type {
    PLAYER,  // игрок на сервере
    CLIENT,  // локальный клиентский игрок
    CONSOLE, // серверная консоль/терминал
    RCON     // удалённая консоль
}
```

Вызов с явным источником:

```java
CommandManager.dispatchCommand("/heal", CommandSource.serverConsole());
CommandManager.dispatchCommand("/heal", CommandSource.rcon());
CommandManager.dispatchCommand("/heal", CommandSource.player(connection, player));
```

Клиентский helper:

```java
String response = CommandManager.handleClientCommand("/heal", localPlayer);
```

Legacy-серверный вызов сохранён:

```java
String response = CommandManager.handleCommand("/heal", connection);
```

Поддержка клиентских команд — API-уровня: плагин сам вызывает `handleClientCommand` из клиентского UI, keybind,
чата или другого клиентского хука. Встроенный игровой mixin покрывает только серверный пайплайн.

Правила `CommandScope`:

- `CHAT` разрешает `PLAYER` и `CLIENT`;
- `CONSOLE` разрешает `CONSOLE` и `RCON`;
- `BOTH` разрешает все источники.

---

### Subcommand'ы

```java
@CommandInfo(
        name = "kit",
        description = "Получение наборов предметов",
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
        return "Доступные наборы: starter, vip";
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
            return "Стартовый набор получен!";
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
            return "VIP-набор получен!";
        }
    }
}
```

Имена subcommand'ов нормализуются в lowercase. Snapshot subcommand'ов делается при регистрации родительской команды,
поэтому возвращайте immutable map и не меняйте её во время работы.

---

### Параметры `@CommandInfo`

| Параметр          | Тип            | По умолчанию        | Описание                                                                 |
|:------------------|:---------------|:--------------------|:-------------------------------------------------------------------------|
| `name`            | `String`       | *(обязательно)*     | Основной триггер команды без ведущего slash.                             |
| `aliases`         | `String[]`     | `{}`                | Альтернативные триггеры.                                                 |
| `description`     | `String`       | `""`                | Описание для help/UI.                                                    |
| `usage`           | `String`       | `""`                | Подсказка по синтаксису.                                                 |
| `permission`      | `String[]`     | `{}`                | Пользовательские permission-ноды. Достаточно совпадения одной.            |
| `capability`      | `Capability[]` | `{}`                | Нативные capabilities PZ. Достаточно одной.                              |
| `scope`           | `CommandScope` | `CommandScope.BOTH` | Разрешённые источники.                                                   |
| `cooldown`        | `long`         | `0`                 | Cooldown для игровых источников.                                         |
| `cooldownUnit`    | `TimeUnit`     | `TimeUnit.SECONDS`  | Единица cooldown'а.                                                      |
| `overrideVanilla` | `boolean`      | `false`             | Разрешает замену ванильной команды PZ с таким же именем или алиасом.     |

---

### Права и cooldown

1. **Консоль и RCON** обходят проверку прав и cooldown.
2. **OR-логика** внутри `permission` и `capability`.
3. **AND-логика** между родительской командой и выбранным subcommand'ом.
4. Cooldown'ы учитываются по identity источника и пути команды/subcommand'а.
5. Проверка cooldown атомарна, просроченные записи периодически очищаются.

---

### CommandContext

`CommandContext` immutable. Аксессор `args()` возвращает защищённую копию.

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

Legacy-конструктор с пятью аргументами сохранён и сам выводит `CommandSource` из player/connection.

---

### Кавычки и экранирование

`CommandArgumentParser` поддерживает одинарные, двойные кавычки и backslash-экранирование.

| Ввод                                      | `args()[0]`    | `args()[1]`    |
|:------------------------------------------|:---------------|:---------------|
| `/heal "Miss Bekket"`                     | `Miss Bekket`  | *(нет)*        |
| `/tp 'John Doe' "Safe Zone 1"`            | `John Doe`     | `Safe Zone 1`  |
| `/heal "Player \"One\""`                  | `Player "One"` | *(нет)*        |
| `/give Brov3r Base.Axe 2`                 | `Brov3r`       | `Base.Axe`     |

Незакрытые кавычки отклоняются с ответом invalid-syntax, а не превращаются в мусорные токены.

---

### Дескрипторы и help

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

`getCommands()` по-прежнему возвращает legacy-map с алиасами.

---

### События команд

Жизненный цикл и выполнение команд используют event bus Avrix:

| Событие                            | Параметры                                               | Описание                                      |
|:-----------------------------------|:--------------------------------------------------------|:----------------------------------------------|
| `ServerEvents.COMMAND_REGISTERED`   | `CommandDescriptor descriptor`                          | Команда успешно зарегистрирована              |
| `ServerEvents.COMMAND_UNREGISTERED` | `CommandDescriptor descriptor`                          | Команда удалена                               |
| `ServerEvents.COMMAND_EXECUTE`      | `CommandContext context, String commandPath`            | Перед выполнением команды/subcommand'а        |
| `ServerEvents.COMMAND_SUCCESS`      | `CommandContext context, String commandPath, String result, long elapsedMs` | После успешного выполнения |
| `ServerEvents.COMMAND_FAILURE`      | `CommandContext context, String commandPath, String reason, Throwable error` | Ошибка валидации или исключение |

```java
@SubscribeEvent(custom = "OnCommandRegistered")
public void onCommandRegistered(CommandDescriptor descriptor) {
    System.out.println("Зарегистрирована: " + descriptor.name());
}
```

События регистрации/удаления также приходят для клиентских команд, зарегистрированных через API.

---

### Сообщения и ошибки

Сообщения валидации проходят через `CommandMessages`: сначала ищется translation key Project Zomboid, затем
используется английский fallback. Детали исключения не отправляются игроку: в ответе только короткий correlation id,
а полный stack trace пишется в лог сервера.
