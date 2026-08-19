[Главная](../wiki-language.md) > [Документация](wiki-main.md) > Чат/консоль команды

## 💬 Чат/консоль команды

Система команд **Avrix** предоставляет декларативный API для создания серверных и внутриигровых команд. Она включает
автоматический лексический анализ аргументов с поддержкой кавычек, разграничение областей выполнения через
`CommandScope`, а также интеграцию с подсистемой прав доступа `PermissionsManager` и нативными возможностями
`Capability` Project Zomboid.

---

### Создание команды

Для создания команды реализуйте функциональный интерфейс `Command` и добавьте аннотацию `@CommandInfo`.

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
        description = "Полностью восстанавливает здоровье игрока",
        usage = "/heal [\"имя игрока\"]",
        permission = {"avrix.commands.heal", "avrix.admin.*"},
        scope = CommandScope.BOTH
)
public class HealCommand implements Command {

    @Override
    public String execute(CommandContext context) {
        // Если передан аргумент с никнеймом цели
        if (context.args().length > 0) {
            String targetName = context.args();
            return "Игрок " + targetName + " успешно исцелен.";
        }

        // Если команда вызвана игроком для себя
        if (context.isPlayer()) {
            IsoPlayer player = context.player();
            player.getBodyDamage().RestoreToFullHealth();
            return "Вы успешно восстановили свое здоровье!";
        }

        return "Использование из консоли: /heal <\"имя игрока\">";
    }
}
```

---

### Параметры аннотации `@CommandInfo`

| Параметр      | Тип            | По умолчанию        | Описание                                                                                             |
|:--------------|:---------------|:--------------------|:-----------------------------------------------------------------------------------------------------|
| `name`        | `String`       | *(Обязательно)*     | Основной триггер вызова команды (без начального слэша).                                              |
| `aliases`     | `String[]`     | `{}`                | Дополнительные синонимы и сокращения для вызова команды.                                             |
| `description` | `String`       | `""`                | Человекочитаемое описание назначения команды.                                                        |
| `usage`       | `String`       | `""`                | Подсказка по синтаксису и ожидаемым параметрам.                                                      |
| `permission`  | `String[]`     | `{}`                | Строковые узлы прав (`permissions`). Доступ разрешен, если у отправителя есть **хотя бы один** узел. |
| `capability`  | `Capability[]` | `{}`                | Нативные capabilities Project Zomboid, необходимые для выполнения.                                   |
| `scope`       | `CommandScope` | `CommandScope.BOTH` | Область допустимого исполнения: только чат (`CHAT`), только консоль (`CONSOLE`) или везде (`BOTH`).  |

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

При вызове метода `execute` передается неизменяемый объект `CommandContext`, содержащий всю метаинформацию о текущем
запросе:

```java
public record CommandContext(
        String senderName,        // Имя отправителя (ник игрока или "ServerConsole")
        IsoPlayer player,         // Объект игрока IsoPlayer (или null для консоли)
        UdpConnection connection, // Сетевой UDP-сокет (или null для консоли)
        String rawCommand,        // Исходная строка ввода целиком
        String[] args             // Массив очищенных аргументов (кавычки удалены)
) {
    public boolean isPlayer();             // true, если команда вызвана онлайн-игроком

    public Optional<IsoPlayer> getPlayer(); // Безопасное получение сущности игрока

    public Optional<String> getArg(int i); // Получение аргумента по индексу с защитой от IndexOutOfBounds
}
```

---

### Парсер многословных аргументов и кавычек

Встроенный лексер `CommandManager` автоматически объединяет слова в кавычках (`""` или `''`) в единый аргумент и очищает
их от внешних символов обрамления:

| Введенная команда                       | `context.args()[0]` | `context.args()[1]` | `context.args()[2]` |
|:----------------------------------------|:--------------------|:--------------------|:--------------------|
| `/heal "Miss Bekket"`                   | `Miss Bekket`       | *(отсутствует)*     | *(отсутствует)*     |
| `/tp 'John Doe' "Safe Zone 1"`          | `John Doe`          | `Safe Zone 1`       | *(отсутствует)*     |
| `/give Brov3r Base.Axe 2`               | `Brov3r`            | `Base.Axe`          | `2`                 |
| `/warn "Bad Player" 3 "Griefing walls"` | `Bad Player`        | `3`                 | `Griefing walls`    |

---

### Проверка прав и авторизация

Права проверяются автоматически до вызова метода `execute()`:

1. **Серверная консоль** всегда обладает абсолютным приоритетом и обходит любые проверки прав.
2. **Игроки** проверяются через `PermissionsManager`:
    - Поддерживаются префиксы и маски (`avrix.commands.*`, `*`).
    - Поддерживается наследование ролей (`ExtendedRole`).
    - Поддерживаются персональные права пользователей.

---

### Регистрация и удаление команд

Регистрация выполняется через статический класс `CommandManager` в точке входа вашего плагина (`onInitialize`):

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
        // Регистрация экземпляров команд
        CommandManager.register(new HealCommand());
        CommandManager.register(new TeleportCommand());

        // Отмена регистрации команды, например, при выгрузке
        // CommandManager.unregister("heal");
    }
}
```