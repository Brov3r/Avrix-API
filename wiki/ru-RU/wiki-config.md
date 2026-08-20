[Главная](../wiki-language.md) > [Документация](wiki-main.md) > Файлы конфигурации

## ⚙️ Файлы конфигурации

Система конфигураций **Avrix** предоставляет декларативный API для создания, чтения, модификации и автосохранения YAML-файлов плагинов. Она построена на базе библиотеки **SpongePowered Configurate 4**.

Подсистема гарантирует изоляцию дефолтных ресурсов каждого мода, поддерживает неизменяемые модели данных (Java 25 Records), динамический доступ без объявления классов, а также потокобезопасную модификацию в рантайме.

---

### 📁 Архитектура и изоляция ресурсов

Все конфигурационные файлы плагинов сохраняются по стандартизированному пути:  
`plugins/{pluginId}/{fileName}`

```text
plugins/
└── my-mod/
    ├── config.yml           # Основной файл конфигурации
    └── languages/
        └── ru.yml           # Дополнительный конфигурационный файл
```

#### 🛡️ Защита от коллизий в ClassLoader
В отличие от стандартного механизма `ClassLoader.getResourceAsStream()`, который в общем classpath может вернуть чужой файл с тем же именем, `ConfigManager` извлекает шаблонные файлы **напрямую из физического JAR-архива конкретного плагина** (`PluginData.getPluginFile()`).

- 📄 **Файл уже существует на диске** — он считывается напрямую из файловой системы.
- 📦 **Файла нет на диске** — он автоматически извлекается из корня или подпапки JAR-архива вашего плагина с сохранением всех комментариев и структуры.
- 📝 **Ресурс в JAR отсутствует** — на диске создается пустой конфигурационный файл.

---

### 🧩 1. Строго типизированные конфигурации (Java Records)

Рекомендуемый подход — декларативное описание конфигурации через неизменяемые `record` с аннотацией `@ConfigSerializable`.

#### Шаг 1: Создание шаблона в ресурсах мода
Создайте файл `config.yml` в папке `src/main/resources/config.yml` вашего проекта:

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

#### Шаг 2: Создание модели данных

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
     * Compact constructor для валидации и установки значений по умолчанию
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
    ) {}
}
```

#### Шаг 3: Загрузка и использование

Загрузка выполняется в методе `onInitialize` точки входа плагина:

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
        // Загрузка config.yml (если файла нет на диске, он скопируется из JAR)
        this.configHolder = ConfigManager.loadDefaultConfig(pluginData, ModConfig.class);

        // Получение активных данных
        ModConfig config = configHolder.get();
        System.out.println("Сервер: " + config.serverName());
        System.out.println("Множитель лута: " + config.modules().dropMultiplier());
    }
}
```

---

### 🌳 2. Динамический доступ без классов (`CommentedConfigurationNode`)

Если структура конфигурации заранее неизвестна или имеет произвольные пользовательские секции, можно использовать прямое древовидное API узлов:

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
        // Загрузка без DTO/Record
        Config<CommentedConfigurationNode> config = ConfigManager.loadDefaultConfig(pluginData);

        // Чтение значений с fallback дефолтами
        String host = config.node().node("database", "host").getString("127.0.0.1");
        int port = config.node().node("database", "port").getInt(3306);
        boolean debug = config.node().node("settings", "debug").getBoolean(false);

        // Изменение значений узлов
        config.node().node("database", "host").set("192.168.1.100");
        config.node().node("custom", "newKey").set("Значение");

        // Сохранение изменений на диск
        config.save();
    }
}
```

---

### 📂 3. Работа с дополнительными и вложенными файлами

Плагин может загружать неограниченное количество конфигураций с любыми именами и подпутями:

```java
// Загрузка файла plugins/{pluginId}/database.yml
Config<DbConfig> dbConfig = ConfigManager.load(pluginData, "database.yml", DbConfig.class);

// Загрузка файла в подпапке plugins/{pluginId}/languages/ru.yml
Config<CommentedConfigurationNode> langConfig = ConfigManager.load(
        pluginData, 
        "languages/ru.yml", 
        CommentedConfigurationNode.class
);
```

---

### 🔄 4. Модификация, сохранение и перезагрузка

Контейнер `Config<T>` предоставляет полный набор методов управления жизненным циклом конфигурационного файла:

```java
Config<ModConfig> configHolder = ConfigManager.loadDefaultConfig(pluginData, ModConfig.class);

// 1. Полная замена объекта и сохранение
configHolder.set(new ModConfig("New Title", 200, false, null));
configHolder.save();

// 2. Атомарное обновление через Consumer
configHolder.update(current -> {
    configHolder.set(new ModConfig(
            "Updated Title",
            current.maxZombies() + 50,
            current.pvpEnabled(),
            current.modules()
    ));
});

// 3. Принудительная перезагрузка файла с диска (например, по команде /reload)
configHolder.reload();
```

---

### 🛠️ Справочник методов `ConfigManager`

| Метод | Возвращает | Описание |
|:---|:---|:---|
| `loadDefaultConfig(pluginData, type)` | `Config<T>` | 📥 Загружает/извлекает `config.yml` мода в типизированную модель. |
| `loadDefaultConfig(pluginData)` | `Config<CommentedConfigurationNode>` | 🌳 Загружает/извлекает `config.yml` в динамическое дерево нод. |
| `load(pluginData, fileName, type)` | `Config<T>` | 📄 Загружает/извлекает файл по относительному пути (например, `lang/ru.yml`). |
| `get(pluginId, fileName)` | `Config<T>` | 🔍 Получает уже загруженный экземпляр конфигурации из кэша. |
| `resolvePath(pluginId, fileName)` | `Path` | 📍 Возвращает нормализованный абсолютный путь к файлу на диске. |
| `saveAll()` | `void` | 💾 Сохраняет все открытые конфигурации всех модов на диск. |
| `reloadAll()` | `void` | 🔄 Перезагружает все открытые конфигурации всех модов с диска. |

---

### 🧰 Справочник методов `Config<T>`

| Метод | Возвращает | Описание |
|:---|:---|:---|
| `get()` | `T` | 📦 Возвращает активный объект данных (Record/POJO/Node). |
| `set(T data)` | `void` | ✏️ Заменяет активный объект данных новым экземпляром. |
| `node()` | `CommentedConfigurationNode` | 🌿 Предоставляет прямой доступ к корневому узлу Configurate. |
| `save()` | `void` | 💾 Сериализует текущее состояние и записывает его в файл на диск. |
| `reload()` | `void` | 🔄 Перечитывает файл с диска и обновляет состояние объекта в памяти. |
| `update(Consumer<T> action)` | `void` | ⚡ Применяет функцию-мутатор и сразу сохраняет изменения на диск. |
| `getFile()` | `Path` | 📁 Возвращает физический путь к файлу конфигурации на диске. |