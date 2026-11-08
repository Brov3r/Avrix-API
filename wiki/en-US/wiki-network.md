[Home](../wiki-language.md) > [Documentation](wiki-main.md) > Network API

# 🌐 Network API

The Avrix network API lets plugins exchange their own messages between client and server, send native
Project Zomboid packets, and inspect received native packets.

It is built on the existing `ClientCommand` / `ServerCommand` transport. No new `PacketTypes.PacketType`
constants are registered, so the API stays compatible with the game and other mods.

---

### 📦 What the API Provides

| Class / member | Purpose |
|:---------------|:--------|
| `NetworkManager` | Entry point, channel registry, and static send helpers |
| `NetworkChannel` | Namespaced channel owned by one plugin module |
| `NetworkContext` | Receiving side, player, and connection |
| `NetworkHandler` | Handler for map-based messages |
| `BinaryPacketHandler` | Handler for binary messages |
| `BinaryPacketWriter` | Big-endian writer for binary payloads |
| `NetworkPayload` | Conversion between `Map<String, Object>` and Kahlua tables |
| `NetworkPackets` | Helpers for existing native `INetworkPacket` types |
| `NetworkEvents.PACKET_RECEIVED` | Event fired for received native packets |
| `PacketReceivedEvent` | Native packet type, side, connection, and a payload duplicate |

---

### 🚀 Creating a Channel

A channel is identified by a module name. Use the plugin identifier to avoid collisions.

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

`NetworkManager.channel(MODULE)` returns the same instance for the same module. The module may contain up
to 64 characters; a command may contain up to 128 characters. Blank values are rejected.

Register handlers on both sides when the same message can travel in either direction.

---

### 💬 Map Messages

Map messages are convenient for simple data. Payload values are converted to Kahlua values:

- `String` and `Boolean` are preserved;
- all `Number` values become `Double`;
- enums become their constant name;
- nested maps, iterables, and arrays become nested tables;
- `null` values are omitted;
- existing `KahluaTable` values are passed through.

Keys must be non-blank strings. Unsupported value types are rejected.

```java
// Client -> server
channel.sendToServer("hello", "message", "hi", "amount", 2);

// Server -> one connection
channel.sendToClient(connection, "hello", Map.of("message", "hi"));

// Server -> one player
channel.sendToPlayer(player, "hello", "message", "hi");

// Server -> all clients
channel.broadcast("hello", "message", "hi");

// Server -> nearby clients
channel.broadcastRelative("hello", x, y, Map.of("message", "hi"));
```

Receiving handler:

```java
channel.on("hello", (context, payload) -> {
    String message = (String) payload.get("message");
    double amount = (Double) payload.get("amount");
});
```

`NetworkContext` exposes the receiving side and, when available, the player and connection:

```java
channel.on("hello", (context, payload) -> {
    if (context.isServer() && context.connection() != null) {
        // The server can reply to the sender.
        channel.sendToClient(context.connection(), "hello-reply", Map.of("ok", true));
    }
});
```

---

### 🔢 Binary Messages

Binary messages use the same transport but carry raw bytes. This is the recommended format for compact
data, coordinates, identifiers, and serialized structures.

#### Writing a Payload

`BinaryPacketWriter` writes big-endian primitives compatible with the game's `ByteBufferReader`.

| Method | Wire format |
|:-------|:------------|
| `putByte(int)` | 1 byte |
| `putBoolean(boolean)` | 1 byte (`1` / `0`) |
| `putShort(int)` | 2 bytes, big-endian |
| `putInt(int)` | 4 bytes, big-endian |
| `putLong(long)` | 8 bytes, big-endian |
| `putFloat(float)` | 4 bytes, big-endian IEEE 754 |
| `putDouble(double)` | 8 bytes, big-endian IEEE 754 |
| `putChar(char)` | 2 bytes, big-endian |
| `putUTF(String)` | signed 2-byte byte length + UTF-8 bytes |
| `putEnum(Enum<?>)` | 1 byte ordinal |
| `putBytes(byte[])` | raw bytes |
| `put(ByteBuffer)` | remaining bytes of a duplicate |
| `size()` | current payload size |
| `toByteArray()` | independent copy of the payload |

```java
BinaryPacketWriter writer = new BinaryPacketWriter()
        .putInt(entityId)
        .putFloat(x)
        .putFloat(y)
        .putUTF(displayName)
        .putBoolean(visible);

byte[] payload = writer.toByteArray();
```

`putUTF(null)` writes an empty string. A single UTF value cannot exceed **32767 encoded bytes** because
the game stores its length as a signed short. `put(ByteBuffer)` reads from a duplicate and does not move
the source buffer.

#### Sending a Binary Payload

```java
// Client -> server
boolean sent = channel.sendToServer("move", payload);

// Server -> one connection
channel.sendToClient(connection, "move", payload);

// Server -> one player
channel.sendToPlayer(player, "move", payload);

// Server -> all clients
channel.broadcast("move", payload);

// Server -> all clients except one connection
channel.broadcast(excludedConnection, "move", payload);

// Server -> nearby clients
channel.broadcastRelative("move", x, y, payload);
```

The static `NetworkManager` methods accept the same arguments with the module as the first parameter.

#### Receiving a Binary Payload

Register a binary handler with `onBinary`. The handler receives a `ByteBufferReader` positioned at the
start of the payload.

```java
channel.onBinary("move", (context, payload) -> {
    int entityId = payload.getInt();
    float x = payload.getFloat();
    float y = payload.getFloat();
    String name = payload.getUTF();
    boolean visible = payload.getBoolean();

    if (context.isServer()) {
        // Validate the sender before applying the data.
        BinaryPacketWriter ack = new BinaryPacketWriter().putInt(entityId);
        channel.sendToClient(context.connection(), "move-ack", ack.toByteArray());
    }
});
```

Read values in exactly the same order they were written. Use `payload.bb` only when you need direct
`ByteBuffer` access; prefer the reader methods.

#### Size Limit

A single binary payload is limited to **24573 raw bytes**. Base64 encoding of that size produces 32764
characters, which is the largest value that fits into the signed two-byte string length used by the
transport.

```java
int limit = NetworkManager.MAX_BINARY_PAYLOAD_BYTES; // 24573
```

Send methods return `false` for `null` or oversized payloads instead of throwing. Incoming payloads that
are malformed, contain invalid Base64, or exceed the limit are logged and ignored.

There is no automatic fragmentation. For larger data, split it into chunks and include a chunk index and
chunk count in your own payload, then reassemble on the receiving side.

#### Handler Exclusivity

A command uses either a map handler or a binary handler, not both. Registering `on(...)` removes the
binary handler for that command, and registering `onBinary(...)` removes the map handler.

```java
channel.on("data", mapHandler);
channel.onBinary("data", binaryHandler); // Replaces the map handler.
```

Use different command names if both formats are needed.

---

### 📡 Native Game Packets

#### Receiving Native Packets

Subscribe to `NetworkEvents.PACKET_RECEIVED` to inspect any native packet before the game processes it:

```java
import com.avrix.api.events.SubscribeEvent;
import com.avrix.api.network.NetworkEvents;
import com.avrix.api.network.PacketReceivedEvent;

@SubscribeEvent(custom = NetworkEvents.PACKET_RECEIVED)
public void onPacket(PacketReceivedEvent event) {
    if (event.type() == PacketTypes.PacketType.WorldMessage) {
        ByteBufferReader payload = event.payload();
        // Inspect the packet without consuming the game's reader.
    }
}
```

The event fires before authorization and native parsing. `event.payload()` is a **duplicate** reader, so
reading it does not move the buffer used by the game. Listeners should not assume that the packet is
valid or authorized.

#### Sending Native Packets

`NetworkPackets` wraps the native `INetworkPacket` send helpers:

```java
NetworkPackets.sendToServer(PacketTypes.PacketType.SomeClientPacket, values);
NetworkPackets.sendToConnection(connection, PacketTypes.PacketType.WorldMessage, "Module", "hello");
NetworkPackets.sendToPlayer(player, PacketTypes.PacketType.WorldMessage, "Module", "hello");
NetworkPackets.sendToAll(PacketTypes.PacketType.WorldMessage, "Module", "hello");
NetworkPackets.sendToRelative(PacketTypes.PacketType.WorldMessage, x, y, "Module", "hello");
```

Only packet types with a non-null `handler` can be sent. The values must match the packet's `setData`
contract. The API cannot register a new packet type in the enum, so use `NetworkChannel` for custom
messages.

---

### 🧹 Lifecycle and Cleanup

Call `NetworkManager.unregister(MODULE)` during plugin shutdown. It removes the channel and every
handler registered on it.

Use `channel.off(command)` to remove both handler kinds for one command, or `channel.clear()` to remove
everything from the channel without unregistering it.

---

### 🔐 Validation

Plugins run arbitrary Java code; the network layer is not a sandbox. Treat every incoming payload as
untrusted:

- validate lengths and numeric ranges before using them;
- check `context.side()` and `context.player()` / `context.connection()` before acting;
- never assume a client-sent value is authorized;
- keep handlers fast and avoid blocking the game thread;
- log and drop malformed messages instead of retrying them indefinitely.

---

### 📚 Quick Reference

| Operation | Client | Server |
|:----------|:-------|:-------|
| Send to other side | `channel.sendToServer(cmd, payload)` | `channel.sendToClient(connection, cmd, payload)` |
| Send to one player | — | `channel.sendToPlayer(player, cmd, payload)` |
| Broadcast | — | `channel.broadcast(cmd, payload)` |
| Broadcast except one | — | `channel.broadcast(excluded, cmd, payload)` |
| Broadcast nearby | — | `channel.broadcastRelative(cmd, x, y, payload)` |
| Map handler | `channel.on(cmd, handler)` | `channel.on(cmd, handler)` |
| Binary handler | `channel.onBinary(cmd, handler)` | `channel.onBinary(cmd, handler)` |

Map and binary send methods return `false` when the transport is unavailable or the payload is invalid.
