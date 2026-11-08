[Главная](../wiki-language.md) > [Документация](wiki-main.md) > Сетевое API

# 🌐 Сетевое API

Сетевое API Avrix позволяет плагинам обмениваться собственными сообщениями между клиентом и сервером,
отправлять нативные пакеты Project Zomboid и инспектировать полученные нативные пакеты.

API построено поверх существующего транспорта `ClientCommand` / `ServerCommand`. Новые константы
`PacketTypes.PacketType` не регистрируются, поэтому API остаётся совместимым с игрой и другими модами.

---

### 📦 Что предоставляет API

| Класс / член | Назначение |
|:-------------|:-----------|
| `NetworkManager` | Точка входа, реестр каналов и статические методы отправки |
| `NetworkChannel` | Именованный канал, принадлежащий модулю плагина |
| `NetworkContext` | Сторона получения, игрок и соединение |
| `NetworkHandler` | Обработчик map-сообщений |
| `BinaryPacketHandler` | Обработчик бинарных сообщений |
| `BinaryPacketWriter` | Big-endian-писатель бинарных payload'ов |
| `NetworkPayload` | Конвертация между `Map<String, Object>` и Kahlua-таблицами |
| `NetworkPackets` | Обёртки для существующих нативных `INetworkPacket` |
| `NetworkEvents.PACKET_RECEIVED` | Событие получения нативного пакета |
| `PacketReceivedEvent` | Тип пакета, сторона, соединение и дубликат payload'а |

---

### 🚀 Создание канала

Канал идентифицируется именем модуля. Используйте идентификатор плагина, чтобы избежать конфликтов.

```java
package com.example.plugin;

import com.avrix.api.network.NetworkChannel;
import com.avrix.api.network.NetworkManager;
import com.avrix.plugins.Plugin;
import com.avrix.plugins.PluginData;

public final class ExamplePlugin implements Plugin {

    private static final String MODULE = "example-network";

    private NetworkChannel channel;

    @Override
    public void onInitialize(PluginData data) {
        channel = NetworkManager.channel(MODULE);

        channel.on("hello", (context, payload) -> {
            System.out.println(payload.get("message"));
        });
    }

    @Override
    public void onShutdown() {
        NetworkManager.unregister(MODULE);
    }
}
```

`NetworkManager.channel(MODULE)` возвращает один и тот же экземпляр для одного модуля. Имя модуля —
до 64 символов, имя команды — до 128 символов. Пустые значения отклоняются.

Регистрируйте обработчики на обеих сторонах, если сообщение может идти в обоих направлениях.

---

### 💬 Map-сообщения

Map-сообщения удобны для простых данных. Значения payload'а конвертируются в Kahlua-значения:

- `String` и `Boolean` сохраняются как есть;
- все `Number` становятся `Double`;
- enum'ы становятся именами констант;
- вложенные map, iterable и массивы становятся вложенными таблицами;
- `null`-значения опускаются;
- существующие `KahluaTable` передаются без изменений.

Ключи должны быть непустыми строками. Неподдерживаемые типы значений отклоняются.

```java
// Клиент -> сервер
channel.sendToServer("hello", "message", "hi", "amount", 2);

// Сервер -> одно соединение
channel.sendToClient(connection, "hello", Map.of("message", "hi"));

// Сервер -> одному игроку
channel.sendToPlayer(player, "hello", "message", "hi");

// Сервер -> все клиенты
channel.broadcast("hello", "message", "hi");

// Сервер -> ближайшие клиенты
channel.broadcastRelative("hello", x, y, Map.of("message", "hi"));
```

Обработчик получения:

```java
channel.on("hello", (context, payload) -> {
    String message = (String) payload.get("message");
    double amount = (Double) payload.get("amount");
});
```

`NetworkContext` даёт сторону получения и, когда доступно, игрока и соединение:

```java
channel.on("hello", (context, payload) -> {
    if (context.isServer() && context.connection() != null) {
        // Сервер может ответить отправителю.
        channel.sendToClient(context.connection(), "hello-reply", Map.of("ok", true));
    }
});
```

---

### 🔢 Бинарные сообщения

Бинарные сообщения используют тот же транспорт, но передают сырые байты. Это рекомендуемый формат для
компактных данных, координат, идентификаторов и сериализованных структур.

#### Запись payload'а

`BinaryPacketWriter` пишет big-endian-примитивы, совместимые с игровым `ByteBufferReader`.

| Метод | Формат в потоке |
|:------|:----------------|
| `putByte(int)` | 1 байт |
| `putBoolean(boolean)` | 1 байт (`1` / `0`) |
| `putShort(int)` | 2 байта, big-endian |
| `putInt(int)` | 4 байта, big-endian |
| `putLong(long)` | 8 байт, big-endian |
| `putFloat(float)` | 4 байта, big-endian IEEE 754 |
| `putDouble(double)` | 8 байт, big-endian IEEE 754 |
| `putChar(char)` | 2 байта, big-endian |
| `putUTF(String)` | знаковая 2-байтовая длина + UTF-8 байты |
| `putEnum(Enum<?>)` | 1 байт порядкового номера |
| `putBytes(byte[])` | сырые байты |
| `put(ByteBuffer)` | оставшиеся байты дубликата |
| `size()` | текущий размер payload'а |
| `toByteArray()` | независимая копия payload'а |

```java
BinaryPacketWriter writer = new BinaryPacketWriter()
        .putInt(entityId)
        .putFloat(x)
        .putFloat(y)
        .putUTF(displayName)
        .putBoolean(visible);

byte[] payload = writer.toByteArray();
```

`putUTF(null)` пишет пустую строку. Одна UTF-строка не может превышать **32767 закодированных байт**:
игра хранит её длину как знаковый short. `put(ByteBuffer)` читает из дубликата и не двигает исходный
буфер.

#### Отправка бинарного payload'а

```java
// Клиент -> сервер
boolean sent = channel.sendToServer("move", payload);

// Сервер -> одно соединение
channel.sendToClient(connection, "move", payload);

// Сервер -> одному игроку
channel.sendToPlayer(player, "move", payload);

// Сервер -> все клиенты
channel.broadcast("move", payload);

// Сервер -> все клиенты, кроме одного соединения
channel.broadcast(excludedConnection, "move", payload);

// Сервер -> ближайшие клиенты
channel.broadcastRelative("move", x, y, payload);
```

Статические методы `NetworkManager` принимают те же аргументы, где первым параметром идёт модуль.

#### Получение бинарного payload'а

Зарегистрируйте бинарный обработчик через `onBinary`. Обработчик получает `ByteBufferReader`,
позиция которого указывает на начало payload'а.

```java
channel.onBinary("move", (context, payload) -> {
    int entityId = payload.getInt();
    float x = payload.getFloat();
    float y = payload.getFloat();
    String name = payload.getUTF();
    boolean visible = payload.getBoolean();

    if (context.isServer()) {
        // Проверьте отправителя перед применением данных.
        BinaryPacketWriter ack = new BinaryPacketWriter().putInt(entityId);
        channel.sendToClient(context.connection(), "move-ack", ack.toByteArray());
    }
});
```

Читайте значения строго в том же порядке, в котором они были записаны. Используйте `payload.bb`,
только если нужен прямой доступ к `ByteBuffer`; предпочтительны методы reader'а.

#### Ограничение размера

Один бинарный payload ограничен **24573 сырыми байтами**. Base64-кодирование такого размера даёт 32764
символа — максимальное значение, которое помещается в знаковую 2-байтовую длину строки транспорта.

```java
int limit = NetworkManager.MAX_BINARY_PAYLOAD_BYTES; // 24573
```

Методы отправки возвращают `false` для `null` или слишком большого payload'а, а не выбрасывают
исключение. Входящие payload'ы с ошибкой формата, некорректным Base64 или превышением лимита
логируются и игнорируются.

Автоматической фрагментации нет. Для больших данных делите их на части, добавляйте в свой payload номер
части и общее количество частей, а затем собирайте на принимающей стороне.

#### Взаимоисключение обработчиков

Команда использует либо map-обработчик, либо бинарный обработчик, но не оба. Регистрация `on(...)`
удаляет бинарный обработчик для этой команды, а регистрация `onBinary(...)` удаляет map-обработчик.

```java
channel.on("data", mapHandler);
channel.onBinary("data", binaryHandler); // Заменяет map-обработчик.
```

Если нужны оба формата, используйте разные имена команд.

---

### 📡 Нативные игровые пакеты

#### Получение нативных пакетов

Подпишитесь на `NetworkEvents.PACKET_RECEIVED`, чтобы инспектировать любой нативный пакет до его
обработки игрой:

```java
import com.avrix.api.events.SubscribeEvent;
import com.avrix.api.network.NetworkEvents;
import com.avrix.api.network.PacketReceivedEvent;

@SubscribeEvent(custom = NetworkEvents.PACKET_RECEIVED)
public void onPacket(PacketReceivedEvent event) {
    if (event.type() == PacketTypes.PacketType.WorldMessage) {
        ByteBufferReader payload = event.payload();
        // Читайте пакет, не сдвигая буфер игры.
    }
}
```

Событие вызывается до авторизации и нативного парсинга. `event.payload()` — **дубликат** reader'а,
поэтому чтение не сдвигает буфер, который использует игра. Не считайте пакет проверенным или
авторизованным.

#### Отправка нативных пакетов

`NetworkPackets` оборачивает нативные методы отправки `INetworkPacket`:

```java
NetworkPackets.sendToServer(PacketTypes.PacketType.SomeClientPacket, values);
NetworkPackets.sendToConnection(connection, PacketTypes.PacketType.WorldMessage, "Module", "hello");
NetworkPackets.sendToPlayer(player, PacketTypes.PacketType.WorldMessage, "Module", "hello");
NetworkPackets.sendToAll(PacketTypes.PacketType.WorldMessage, "Module", "hello");
NetworkPackets.sendToRelative(PacketTypes.PacketType.WorldMessage, x, y, "Module", "hello");
```

Отправлять можно только типы пакетов с непустым `handler`. Значения должны соответствовать контракту
`setData` пакета. API не может зарегистрировать новый тип в enum'е, поэтому для собственных сообщений
используйте `NetworkChannel`.

---

### 🧹 Жизненный цикл и очистка

Вызывайте `NetworkManager.unregister(MODULE)` при завершении работы плагина. Метод удаляет канал и все
зарегистрированные в нём обработчики.

`channel.off(command)` удаляет команду (и map-, и бинарный обработчик), `channel.clear()` очищает весь
канал, не удаляя его из `NetworkManager`.

---

### 🔐 Валидация

Плагины выполняют произвольный Java-код; сетевой слой не является sandbox'ом. Считайте любой входящий
payload недоверенным:

- проверяйте длины и диапазоны чисел перед использованием;
- проверяйте `context.side()` и `context.player()` / `context.connection()` перед действиями;
- никогда не считайте присланное клиентом значение авторизованным;
- держите обработчики быстрыми и не блокируйте игровой поток;
- логируйте и отбрасывайте некорректные сообщения, не повторяя их бесконечно.

---

### 📚 Краткая справка

| Операция | Клиент | Сервер |
|:---------|:-------|:-------|
| Отправить на другую сторону | `channel.sendToServer(cmd, payload)` | `channel.sendToClient(connection, cmd, payload)` |
| Отправить одному игроку | — | `channel.sendToPlayer(player, cmd, payload)` |
| Broadcast | — | `channel.broadcast(cmd, payload)` |
| Broadcast кроме одного | — | `channel.broadcast(excluded, cmd, payload)` |
| Broadcast рядом | — | `channel.broadcastRelative(cmd, x, y, payload)` |
| Map-обработчик | `channel.on(cmd, handler)` | `channel.on(cmd, handler)` |
| Бинарный обработчик | `channel.onBinary(cmd, handler)` | `channel.onBinary(cmd, handler)` |

Map- и бинарные методы отправки возвращают `false`, если транспорт недоступен или payload некорректен.
