[Главная](../wiki-language.md) > [Документация](wiki-main.md) > Чат/консоль команды

## 💬 Чат/консоль команды

Система команд **Avrix** предоставляет декларативный API для создания серверных и внутриигровых команд. Она включает
автоматический лексический анализ аргументов с поддержкой кавычек, дерево подкоманд (`Subcommand`), независимые таймеры
перезарядки (`Cooldowns`), типобезопасный доступ к аргументам в `CommandContext`, а также бесшовную интеграцию с
`PermissionsManager` и нативными `Capability` Project Zomboid.

---

### Создание базовой команды

Для создания команды реализуйте интерфейс `Command` и добавьте аннотацию `@CommandInfo`.

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
        description = "Полностью восстанавливает здоровье игрока",
        usage = "/heal [\"имя игрока\"]",
        permission = {"avrix.commands.heal", "avrix.admin.*"},
        cooldown = 10,
        cooldownUnit = TimeUnit.SECONDS,
        scope = CommandScope.BOTH
)
public class HealCommand implements Command {

    @Override
    public String execute(CommandContext context) {
        // Реализация команды ...
        return "Здоровье игрока " + target.getUsername() + " успешно восстановлено!";
    }
}
```

---

### Создание иерархических подкоманд

Команды могут делегировать выполнение дочерним подкомандам через метод `subcommands()`. Каждая подкоманда способна
определять собственные права доступа, нативные `Capability` и индивидуальные кулдауны.

```java
package com.example.plugin.commands;

import com.avrix.api.commands.*;
import zombie.characters.IsoPlayer;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@CommandInfo(
        name = "kit",
        description = "Получение наборов предметов",
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
        return "Доступные наборы: starter, vip. Использование: /kit <название>";
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
            return "Стартовый набор успешно получен!";
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
            return "VIP набор успешно получен!";
        }
    }
}
```

---

### Параметры аннотации `@CommandInfo`

| Параметр       | Тип            | По умолчанию        | Описание                                                                                             |
|:---------------|:---------------|:--------------------|:-----------------------------------------------------------------------------------------------------|
| `name`         | `String`       | *(Обязательно)*     | Основной триггер вызова команды (без начального слэша).                                              |
| `aliases`      | `String[]`     | `{}`                | Дополнительные синонимы и сокращения для вызова команды.                                             |
| `description`  | `String`       | `""`                | Человекочитаемое описание назначения команды.                                                        |
| `usage`        | `String`       | `""`                | Подсказка по синтаксису и ожидаемым параметрам.                                                      |
| `permission`   | `String[]`     | `{}`                | Строковые узлы прав (`permissions`). Доступ разрешен, если у отправителя есть **хотя бы один** узел. |
| `capability`   | `Capability[]` | `{}`                | Нативные capabilities Project Zomboid, необходимые для выполнения.                                   |
| `cooldown`     | `long`         | `0`                 | Длительность задержки между повторными вызовами команды игроком.                                     |
| `cooldownUnit` | `TimeUnit`     | `TimeUnit.SECONDS`  | Единица времени для параметра `cooldown`.                                                            |
| `scope`        | `CommandScope` | `CommandScope.BOTH` | Область допустимого исполнения: только чат (`CHAT`), только консоль (`CONSOLE`) или везде (`BOTH`).  |

---

### Области выполнения (`CommandScope`)

Перечисление `CommandScope` жестко изолирует вызовы в зависимости от среды отправителя:

```java
public enum CommandScope {
    CHAT,    // Выполняется ТОЛЬКО живым игроком из игрового чата
    CONSOLE, // Выполняется ТОЛЬКО администратором из терминала/RCON сервера
    BOTH     // Доступно как в чате, так и в консоли сервера
}
```

- Если консоль попытается вызвать команду со `scope = CommandScope.CHAT`, система вернет:  
  `"This command can only be executed in-game by a player."`
- Если игрок попытается вызвать команду со `scope = CommandScope.CONSOLE`, система вернет:  
  `"This command can only be executed from the server console."`

---

### Контекст команды (`CommandContext`)

При вызове метода `execute` передается неизменяемый объект `CommandContext`, предоставляющий типобезопасные методы
извлечения параметров без необходимости ручной обработки исключений:

```java
public record CommandContext(
        String senderName,        // Имя отправителя (ник игрока или "ServerConsole")
        IsoPlayer player,         // Объект игрока IsoPlayer (или null для консоли)
        UdpConnection connection, // Сетевой UDP-сокет (или null для консоли)
        String rawCommand,        // Исходная строка ввода целиком
        String[] args             // Массив очищенных аргументов (кавычки удалены)
) {
    public boolean isPlayer();                             // true, если команда вызвана онлайн-игроком

    public Optional<IsoPlayer> getPlayer();                // Безопасное получение сущности отправителя

    public int length();                                   // Количество переданных аргументов

    public Optional<String> getArg(int index);             // Получение аргумента в Optional

    public String getString(int index, String fallback);   // Строка с дефолтным значением

    public Optional<Integer> getInt(int index);            // Парсинг integer

    public int getInt(int index, int fallback);            // Парсинг integer с дефолтным значением

    public Optional<Double> getDouble(int index);          // Парсинг double

    public double getDouble(int index, double fallback);   // Парсинг double с дефолтным значением

    public Optional<Boolean> getBoolean(int index);        // Парсинг boolean (true/false, 1/0, yes/no)

    public boolean getBoolean(int index, boolean fallback);// Парсинг boolean с дефолтным значением

    public String joinArgs(int startIndex);                // Склейка всех оставшихся аргументов через пробел

    public CommandContext subContext(int shift);           // Создание дочернего контекста со сдвигом аргументов
}
```

---

### Парсер многословных аргументов и кавычек

Встроенный лексер `CommandArgumentParser` автоматически объединяет слова в кавычках (`""` или `''`) в единый аргумент и
очищает их от внешних символов обрамления:

| Введенная команда                       | `context.args()[0]` | `context.args()[1]` | `context.args()[2]` |
|:----------------------------------------|:--------------------|:--------------------|:--------------------|
| `/heal "Miss Bekket"`                   | `Miss Bekket`       | *(отсутствует)*     | *(отсутствует)*     |
| `/tp 'John Doe' "Safe Zone 1"`          | `John Doe`          | `Safe Zone 1`       | *(отсутствует)*     |
| `/give Brov3r Base.Axe 2`               | `Brov3r`            | `Base.Axe`          | `2`                 |
| `/warn "Bad Player" 3 "Griefing walls"` | `Bad Player`        | `3`                 | `Griefing walls`    |

---

### Проверка прав и кулдауны

1. **Приоритет консоли:** Серверная консоль всегда обладает root-доступом, обходит любые ограничения прав и игнорирует
   кулдауны.
2. **Проверка прав (OR-логика):** Доступ разрешен, если игрок обладает хотя бы одним строковым правом (`permission`) или
   нативным свойством (`capability`).
3. **Иерархия подкоманд (AND-логика):** Для вызова `/kit starter` игрок обязан обладать как правом корневой команды (
   `kit.use`), так и правом конкретной ветки (`kit.starter`).
4. **Изоляция кулдаунов:** Таймеры перезарядки вычисляются раздельно для каждого игрока и сохраняются независимо для
   корневых команд и каждой отдельной подкоманды.

---

### Регистрация и удаление команд

Регистрация выполняется через статический класс `CommandManager` в точке инициализации плагина:

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
        // Регистрация команд
        CommandManager.register(new HealCommand());
        CommandManager.register(new KitCommand());

        // Отмена регистрации команды при выгрузке/перезагрузке
        // CommandManager.unregister("heal");
    }
}
```