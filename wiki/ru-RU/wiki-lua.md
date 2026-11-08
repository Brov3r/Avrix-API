[Главная](../wiki-language.md) > [Документация](wiki-main.md) > Lua-скрипты

## 📜 Lua-скрипты

Avrix API позволяет плагинам:

- загружать собственные Lua-файлы из директории или JAR;
- использовать `require` между кастомными, игровыми и модовыми модулями;
- переопределять таблицы игровых и модовых Lua-скриптов;
- запускать скрипты в общем или отдельном Lua-окружении;
- управлять видимостью игровых, модовых и Java-экспозиций;
- вызывать Java-код из Lua и передавать события между Java и Lua.

Загрузчик работает после игровых и модовых Lua-файлов. Скрипты не изолированы от JVM: это загрузчик и
настраиваемая видимость, а не security-sandbox.

---

### Источники скриптов

Поддерживаются два источника:

```java
LuaScriptSource directory = LuaScriptSource.directory(Path.of("C:/MyPlugin/lua"));
LuaScriptSource jar = LuaScriptSource.jar(Path.of("plugins/MyPlugin.jar"), "lua");
```

- `directory(root)` — обычная папка.
- `jar(jar, prefix)` — записи JAR под указанным префиксом.
- JAR открывается лениво и один раз; файлы не распаковываются во временную папку.
- Пути модулей всегда используют `/`; переходы `..` и абсолютные пути запрещены.

Источник нужно зарегистрировать:

```java
LuaScriptSource source = LuaScriptLoader.addSource(
        LuaScriptSource.directory(Path.of("C:/MyPlugin/lua"))
);
```

Повторная регистрация источника с тем же `id` возвращает уже зарегистрированный экземпляр.

---

### Загрузка файлов

Один модуль из зарегистрированных источников:

```java
LuaScriptLoader.load("feature/main");
```

Несколько выбранных модулей:

```java
LuaScriptLoader.loadAll("feature/main", "feature/config", "feature/ui");
```

Группа — все `.lua` рекурсивно:

```java
LuaScriptLoader.loadGroup(source, "feature");
```

Из директории с автоматической регистрацией источника:

```java
LuaScriptLoader.loadGroup(Path.of("C:/MyPlugin/lua/feature"));
```

Один файл с автоматической регистрацией родительской папки:

```java
LuaScriptLoader.load(Path.of("C:/MyPlugin/lua/feature/main.lua"));
```

Один файл из JAR:

```java
LuaScriptLoader.loadFromJar(pluginJar, "lua/feature/main.lua");
```

Вся директория JAR:

```java
LuaScriptLoader.loadGroupFromJar(pluginJar, "lua/feature");
```

`load(Path)` и `loadFromJar(...)` делают родительскую папку файла корнем для `require`.
Если модули используют пути выше этой папки, зарегистрируйте более широкий источник явно через
`addSource(...)`.

---

### `require`

Кастомный `require` работает так:

1. Имя модуля нормализуется: `\` заменяется на `/`, при необходимости добавляется `.lua`.
2. Модуль ищется в кастомных источниках в порядке регистрации.
3. Если найден — загружается и кэшируется.
4. Если не найден — вызывается штатный `require` Project Zomboid.

Пример:

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

`helper.lua` возвращает значение:

```lua
return "helper-ok"
```

Особенности:

- кастомные источники имеют приоритет над штатными;
- повторный `require` возвращает закэшированный результат;
- рекурсивный `require` того же модуля логируется и возвращает `nil`;
- кастомный модуль может требовать игровые/модовые модули;
- игровые/модовые Lua-файлы могут требовать кастомные модули;
- `.` не заменяется на `/`: используйте имена вида `feature/helper`, как в Project Zomboid.

---

### Когда загружаются скрипты

Порядок:

1. `LuaManager.init()` — создаётся Lua-окружение, применяются экспозиции Avrix.
2. `LuaManager.LoadDirBase()` — загружаются игровые и модовые Lua-файлы.
3. `OnGameBoot` — Avrix загружает кастомные скрипты.
4. Lua-обработчики `OnGameBoot` (включая зарегистрированные кастомными скриптами).

Загрузка выполняется в `HEAD` обработчика `OnGameBoot`. Это важно:

- на выделенном сервере `OnGameBoot` вызывается, но `OnTick` и
  `LuaEventManager.RunQueuedEvents()` в том же виде, что на клиенте, не используются;
- `OnGameBoot` может не иметь Lua-колбэков; загрузка всё равно должна произойти.

Запросы `load(...)`, сделанные в `onInitialize`, не выполняются сразу — они ждут `OnGameBoot`.
Если `load(...)` вызван после `OnGameBoot` в главном потоке, скрипт загружается сразу.

Отложенные загрузки с фоновых потоков подхватываются:

- на клиенте — в `LuaEventManager.RunQueuedEvents()`;
- на сервере — в `GameServer.mainLoopDealWithNetData(...)`.

---

### Переопределение игровых и модовых таблиц

Кастомные скрипты загружаются после игровых и модовых `.lua`-файлов, поэтому видят итоговые глобальные таблицы.

Рекомендуется менять таблицу **на месте**:

```lua
function ISBaseObject:someMethod(...)
    -- новая реализация
end

ISBaseObject.SOME_FIELD = 42
```

Почему на месте:

- все ссылки на ту же таблицу видят изменения;
- производные таблицы/классы, у которых `__index` указывает на эту таблицу, тоже видят изменения;
- не ломается наследование.

Полная замена:

```lua
SomeTable = {
    -- ...
}
```

работает только для будущих обращений по глобальному имени. Локальные алиасы и уже созданные производные
таблицы останутся на старом объекте.

Если нужно переопределить изменения, которые моды делают в своих `OnGameBoot`-обработчиках, зарегистрируйте
свой `Events.OnGameBoot.Add(...)` из кастомного скрипта: он будет добавлен после уже загруженных модовых
обработчиков и выполнится последним.

---

### Java-экспозиция в Lua

Используйте `LuaExposer`:

```java
LuaExposer.exposeClass(MyApi.class);
LuaExposer.exposeMethod(MyApi.class, "doWork", "work");
LuaExposer.exposeGlobal("globalFunction", myObject, "handle");
LuaExposer.exposeGlobalObject(new MyLuaFunctions());
```

Из Lua:

```lua
local api = MyApi.new()
api:doWork()
api:work()

MyApi.staticMethod()
globalFunction()
myAnnotatedGlobal()
```

`exposeClass` добавляет:

- конструкторы как `ClassName.new(...)`;
- public static-методы и поля;
- public instance-методы;
- короткое имя и полный package-path.

`@HiddenFromLua` исключает член из экспозиции. Коллизии имён не перезаписываются: Avrix пишет warning и
оставляет существующее значение.

Регистрации возвращают `LuaRegistration`:

```java
LuaRegistration registration = LuaExposer.exposeClass(MyApi.class);
LuaExposer.unregister(registration);
```

Экспозиции применяются после каждого `LuaManager.init()` и доступны как общим, так и изолированным
Lua-окружениям в соответствии с их политикой видимости.

---

### События Java ↔ Lua

#### Игровое/кастомное событие → Java

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

Сначала зарегистрируйте событие в Lua:

```java
LuaEventManager.AddEvent("my:event");
```

Затем отправьте его:

```java
EventManager.publishBoth("my:event", "payload"); // Java + Lua
EventManager.publishToLua("my:event", "payload"); // только Lua
```

Lua:

```lua
Events["my:event"].Add(function(payload)
    print(tostring(payload))
end)
```

`publishBoth` сначала вызывает Java-слушателей, затем передаёт событие в Lua. Повторного вызова Java-слушателей
при forward в Lua не происходит. Поддерживается до восьми аргументов.

---

### Изолированные Lua-окружения

По умолчанию скрипты загружаются в общее окружение:

```java
LuaScriptLoader.load("feature/main");
```

Для отдельного окружения:

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

Политика управляет видимостью:

| Флаг                             | Что включает                                      |
|:---------------------------------|:--------------------------------------------------|
| `includeStandardLibrary`         | стандартные `print`, `table`, `string`, `math` и т.п. |
| `includeProjectZomboidExposure`  | Java-классы/функции, экспортированные самим PZ    |
| `includeAvrixExposure`           | экспозиции Avrix через `LuaExposer`               |
| `includeGameLua`                 | глобалы, созданные игровыми Lua-файлами           |
| `includeModLua`                  | глобалы, созданные модовыми Lua-файлами           |
| `includeCustomLua`               | глобалы других кастомных скриптов общего окружения |
| `includeUnknownGlobals`          | глобалы с неизвестной категорией                  |
| `allowVanillaRequire`            | fallback на штатный `require`                     |

Точечно:

```java
LuaEnvironmentPolicy policy = LuaEnvironmentPolicy.isolated()
        .toBuilder()
        .allowGlobal("SomeGameTable")
        .denyGlobal("os")
        .build();
```

`allowGlobal` разрешает конкретное имя даже при выключенной категории. `denyGlobal` всегда имеет приоритет.

`sandbox-require` загружает кастомные модули внутрь этого окружения. Если `allowVanillaRequire=true`, а
кастомный модуль не найден, вызывается штатный `require`; его глобалы попадут в общее окружение и будут
видны sandbox только по правилам категорий.

Важно: это **изоляция видимости**, а не security-sandbox. Разрешённые таблицы и функции передаются по ссылке;
скрипт может изменять их содержимое.

---

### Жизненный цикл и очистка

```java
LuaScriptLoader.removeSource(source);
LuaScriptLoader.removeEnvironment("sandbox");
LuaScriptLoader.invalidate();
LuaScriptLoader.unregisterAll();
```

- `removeSource` закрывает JAR-источник.
- `removeEnvironment` удаляет окружение и его запросы.
- `invalidate` сбрасывает кэш найденных модулей (полезно, если файлы добавились в существующую папку).
- `unregisterAll` удаляет источники, окружения и запросы.

После каждого `LuaManager.init()` загрузчик заново применяет зарегистрированные запросы, а кэши загрузки
очищаются.

---

### Оптимизация

- JAR не распаковывается; используется один открытый `JarFile`.
- `require` кэширует найденные модули и отрицательные результаты поиска.
- Результаты выполнения модулей кэшируются: повторный `require` не компилирует файл.
- Групповая загрузка сканирует файлы один раз и сортирует их детерминированно.
- При отсутствии кастомных источников `require`-хук завершается сразу.
- Горячий путь `require` не использует блокировки: Lua однопоточен.

---

### Ограничения

- Не поддерживается file-level override с предотвращением выполнения оригинального файла: переопределяйте
  таблицы и функции на месте.
- Типы из сигнатур Java-методов не экспортируются рекурсивно; регистрируйте их через `LuaExposer` отдельно.
- Локальные таблицы внутри Lua-модуля нельзя переопределить снаружи, если модуль их не возвращает и не
  публикует глобально.
- `require` не делает путь относительным текущему файлу: имя разрешается от корня источника, как в PZ.
