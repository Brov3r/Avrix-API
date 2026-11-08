[Home](../wiki-language.md) > [Documentation](wiki-main.md) > Utility API

# 🧰 Utility API

The `com.avrix.api.utils` package contains focused helpers for common plugin tasks: player lookup,
distance checks, screen conversion, teleporting, safehouses, factions, items, experience, world
environment, zombie population, visibility checks, moderation, temporary moderation and chat.

Most mutating helpers have two forms:

- a method without an actor, intended for trusted plugin or console code;
- an overload with an `IsoPlayer actor`, which checks the relevant capability through the Avrix
  permissions system.

Query-only helpers do not require permissions.

---

### 📦 Class Overview

| Class | Side | Responsibility |
|:------|:-----|:---------------|
| `PlayerLookupUtils` | Server | Online player snapshots and lookups |
| `WorldDistanceUtils` | Both | 2D/3D distance, radius filtering, nearest objects |
| `ClientScreenUtils` | Client | Window/viewport size and world/screen conversion |
| `TeleportUtils` | Server | Teleport players and world objects |
| `SafehouseUtils` | Server | Safehouse lookup and membership management |
| `FactionUtils` | Server | Faction lookup and membership management |
| `ItemUtils` | Server | Item creation, adding, removing and counting |
| `ExperienceUtils` | Server | Perk XP and level manipulation |
| `WorldEnvironmentUtils` | Server | Time, calendar and weather |
| `ZombiePopulationUtils` | Server | Zombie counts, limits, hordes and population cells |
| `VisibilityUtils` | Both | Line of sight and raycasts |
| `ModerationActionUtils` | Server | Kick, ban, unban and mute |
| `TemporaryModerationUtils` | Server | Timed bans and mutes with automatic expiry |
| `ChatMessageUtils` | Server | Server chat, alerts and admin chat messages |

---

### 👤 Player Lookup

`PlayerLookupUtils` returns immutable snapshots and never exposes the mutable server player list.

```java
List<IsoPlayer> players = PlayerLookupUtils.getOnlinePlayers();

Optional<IsoPlayer> exact = PlayerLookupUtils.getPlayerByUsername("Brov3r");
List<IsoPlayer> partial = PlayerLookupUtils.getPlayersByUsernamePart("bro");
Optional<IsoPlayer> byOnlineId = PlayerLookupUtils.getPlayerByOnlineId((short) 12);
Optional<IsoPlayer> bySteamId = PlayerLookupUtils.getPlayerBySteamId("76561198000000001");

List<IsoPlayer> admins = PlayerLookupUtils.getPlayersByRole("admin");
List<IsoPlayer> banUsers = PlayerLookupUtils.getPlayersWithCapability(Capability.BanUnbanUser);
List<IsoPlayer> nodeUsers = PlayerLookupUtils.getPlayersWithPermission("example.node");
```

`getSteamId(...)` falls back to the owning network connection when the player entity does not have a
SteamID. `getConnection(...)` returns the owning `UdpConnection` when the player is connected.

---

### 📏 Distances and Radius Queries

`WorldDistanceUtils` supports raw coordinates, `IsoObject`, `IsoPlayer` and `IsoGridSquare`.

```java
float distance = WorldDistanceUtils.distance2D(player, target);
float fullDistance = WorldDistanceUtils.distance3D(player, target);
float squared = WorldDistanceUtils.distanceSquared2D(player, target);
float manhattan = WorldDistanceUtils.distanceManhattan2D(x1, y1, x2, y2);

boolean inRange = WorldDistanceUtils.isWithinRadius(player, target, 10.0f);

List<IsoPlayer> nearby = WorldDistanceUtils.getPlayersWithinRadius(player, 25.0f);
Optional<IsoPlayer> closest = WorldDistanceUtils.getClosestPlayer(player);
Optional<IsoPlayer> closestInRange = WorldDistanceUtils.getClosestPlayer(player, 8.0f);
```

Generic helpers work with any `IsoObject` subtype:

```java
List<IsoZombie> zombiesInRange = WorldDistanceUtils.getWithinRadius(player, zombies, 12.0f);
List<IsoZombie> sorted = WorldDistanceUtils.sortByDistance(player, zombies);
```

Squared distance methods avoid a square root and are preferable for comparisons.

---

### 🖥️ Client Screen and Coordinates

`ClientScreenUtils` is client-side only. It uses the current camera and viewport for the selected
split-screen player index.

```java
int width = ClientScreenUtils.getScreenWidth();
int height = ClientScreenUtils.getScreenHeight();
float aspect = ClientScreenUtils.getAspectRatio();

int viewportWidth = ClientScreenUtils.getViewportWidth(0);
float zoom = ClientScreenUtils.getZoom(0);

ClientScreenUtils.ScreenPoint screen =
        ClientScreenUtils.worldToScreen(player.getX(), player.getY(), player.getZ(), 0);

ClientScreenUtils.WorldPoint world =
        ClientScreenUtils.screenToWorld(screen.x(), screen.y(), 0, 0);

boolean visible = ClientScreenUtils.isOnScreen(screen, 0);
```

Scalar helpers are available as `worldToScreenX/Y` and `screenToWorldX/Y`.

---

### 🚀 Teleporting

```java
TeleportUtils.teleportPlayer(player, x, y, z);
TeleportUtils.teleportPlayer(player, square);
TeleportUtils.teleportPlayerToPlayer(player, destination);

TeleportUtils.teleportObject(object, x, y, z);
TeleportUtils.teleportObject(object, square);
```

Actor-aware overloads check `TeleportToPlayer`, `TeleportToCoordinates` or
`TeleportPlayerToAnotherPlayer`:

```java
TeleportUtils.teleportPlayer(actor, target, x, y, z);
TeleportUtils.teleportPlayerToPlayer(actor, target, destination);
TeleportUtils.teleportObject(actor, object, x, y, z);
```

Moving objects are repositioned directly. Static objects are removed from their old square and added
to the destination square. Online player teleports are synchronized through the vanilla teleport packet.

---

### 🏠 Safehouses

```java
SafeHouse safehouse = SafehouseUtils.getByOwner("Brov3r");
SafeHouse at = SafehouseUtils.getAt(x, y);
SafeHouse own = SafehouseUtils.getForPlayer(player);

SafeHouse created = SafehouseUtils.create(owner, x, y, w, h, "Base");
SafehouseUtils.addMember(safehouse, "Survivor");
SafehouseUtils.removeMember(safehouse, "Survivor");
SafehouseUtils.kickMember(safehouse, "Intruder");
SafehouseUtils.remove(safehouse);

boolean allowed = SafehouseUtils.isAllowed(safehouse, player);
boolean canClaim = SafehouseUtils.canClaim(player);
String reason = SafehouseUtils.getClaimRejection(square, player);
```

Changes are synchronized through the vanilla safehouse packet and safehouse chat. Actor-aware
overloads require `CanSetupSafehouses`.

---

### 🤝 Factions

```java
Faction faction = FactionUtils.getByName("Survivors");
Faction playerFaction = FactionUtils.getForPlayer(player);

Faction created = FactionUtils.create("Survivors", "Brov3r");
FactionUtils.addMember(faction, "Survivor");
FactionUtils.removeMember(faction, "Survivor");
FactionUtils.setTag(faction, "SURV");
FactionUtils.sync(faction);
```

Faction names follow the vanilla length limit of 3–15 characters. Actor-aware overloads require
`FactionCheat`.

---

### 🎒 Items

```java
InventoryItem item = ItemUtils.create("Base.Axe");

InventoryItem added = ItemUtils.addItem(player, "Base.Axe");
List<InventoryItem> stack = ItemUtils.addItem(player, "Base.Nails", 20);

int removed = ItemUtils.removeItem(player, "Base.Nails", 5);
int count = ItemUtils.countItem(player, "Base.Nails");
boolean has = ItemUtils.hasItem(player, "Base.Nails", 10);

ItemUtils.addItem(container, "Base.WaterBottleFull", 2);
ItemUtils.removeItem(container, "Base.WaterBottleFull", 1);
```

A non-positive removal count removes all matching items. Actor-aware overloads require `AddItem`.

---

### ⭐ Experience

```java
PerkFactory.Perk aiming = ExperienceUtils.getPerk("Aiming");

float xp = ExperienceUtils.getXp(player, aiming);
int level = ExperienceUtils.getLevel(player, aiming);

ExperienceUtils.addXp(player, aiming, 100.0f);
ExperienceUtils.removeXp(player, aiming, 50.0f);
ExperienceUtils.setXp(player, aiming, 250.0f);
ExperienceUtils.setLevel(player, aiming, 3);
```

Actor-aware overloads require `AddXP`. Online XP changes are sent through the vanilla server XP path.

---

### 🌦️ Time and Weather

Calendar values exposed by `WorldEnvironmentUtils` are one-based: months are `1-12`, days are `1-31`.

```java
int year = WorldEnvironmentUtils.getYear();
int month = WorldEnvironmentUtils.getMonth();
int day = WorldEnvironmentUtils.getDay();
int hour = WorldEnvironmentUtils.getHour();
int minute = WorldEnvironmentUtils.getMinute();

WorldEnvironmentUtils.setTime(2026, 11, 8, 18, 30);

boolean raining = WorldEnvironmentUtils.isRaining();
float rain = WorldEnvironmentUtils.getRainIntensity();
float temperature = WorldEnvironmentUtils.getTemperature();
float windKph = WorldEnvironmentUtils.getWindSpeedKph();

WorldEnvironmentUtils.startRain(0.75f);
WorldEnvironmentUtils.stopRain();
WorldEnvironmentUtils.stopWeather();
```

Time changes use `ClimateManager` permission in actor-aware overloads. Rain controls require
`StartStopRain`.

---

### 🧟 Zombie Population

```java
int loaded = ZombiePopulationUtils.getLoadedZombieCount();
List<IsoZombie> zombies = ZombiePopulationUtils.getLoadedZombies();
boolean enabled = ZombiePopulationUtils.isZombiesEnabled();

ZombiePopulationUtils.setMinPerChunk(1.0f);
ZombiePopulationUtils.setMaxPerChunk(50.0f);

ZombiePopulationUtils.createHorde(spawnX, spawnY, targetX, targetY, 80);
ZombiePopulationUtils.createHordeInArea(spawnX, spawnY, w, h, targetX, targetY, 80);
ZombiePopulationUtils.clearCell(cellX, cellY);
ZombiePopulationUtils.spawnNow(cellX, cellY);
```

Actor-aware permissions:

- hordes — `CreateHorde`;
- per-chunk limits — `PopmanManage`;
- cell clearing and immediate spawning — `ManipulateZombie`.

---

### 👁️ Visibility and Raycasts

```java
boolean clear = VisibilityUtils.hasLineOfSight(player, target);
boolean clearAtPoint = VisibilityUtils.hasLineOfSight(x1, y1, z1, x2, y2, z2);

Optional<VisibilityUtils.RaycastHit> hit = VisibilityUtils.raycast(player, target);
hit.ifPresent(result -> {
    IsoGridSquare square = result.square();
    float hitX = result.x();
    float hitY = result.y();
    float hitZ = result.z();
});
```

Closed doors and solid objects block sight. Open doors and windows are treated as clear.

---

### 🛡️ Moderation

```java
ModerationActionUtils.kickPlayer(player, "AFK");
ModerationActionUtils.kickPlayer("Brov3r", "AFK");

ModerationResult ban = ModerationActionUtils.banPlayer("Brov3r", "Cheating");
ModerationResult unban = ModerationActionUtils.unbanPlayer("Brov3r");

ModerationActionUtils.banSteamId("76561198000000001", "Cheating");
ModerationActionUtils.banIp("203.0.113.10", "Abuse");
ModerationActionUtils.banPlayerIp("Brov3r", "Abuse");

ModerationActionUtils.mutePlayer(player);
ModerationActionUtils.unmutePlayer(player);
boolean muted = ModerationActionUtils.isMuted(player);
```

`ModerationResult` contains `success` and a human-readable `message`.

Actor-aware permissions: `KickUser`, `BanUnbanUser` and
`CanModifyPlayerStatsInThePlayerStatsUI` for chat mute.

The vanilla protection capabilities `CantBeKickedByUser` and `CantBeBannedByUser` are always respected.

---

### ⏳ Temporary Bans and Mutes

`TemporaryModerationUtils` stores expirations in
`plugins/avrix-api/temporary-moderation.properties`, checks them on the server tick and reapplies
temporary mutes when the player reconnects.

```java
TemporaryModerationUtils.banPlayer("Brov3r", "Cool down", Duration.ofHours(12));
TemporaryModerationUtils.banSteamId("76561198000000001", "Cool down", Duration.ofDays(3));
TemporaryModerationUtils.banIp("203.0.113.10", "Abuse", Duration.ofHours(6));

TemporaryModerationUtils.mutePlayer(player, Duration.ofMinutes(30));
TemporaryModerationUtils.unmutePlayer(player);

Optional<Instant> expires = TemporaryModerationUtils.getMuteExpiration("Brov3r");
boolean timedOut = TemporaryModerationUtils.isTemporarilyMuted("Brov3r");
```

Manual unban/unmute removes the tracked expiration:

```java
TemporaryModerationUtils.unbanPlayer("Brov3r");
TemporaryModerationUtils.unbanSteamId("76561198000000001");
TemporaryModerationUtils.unbanIp("203.0.113.10");
```

Expiration processing runs on the server thread through `OnTick`. Call
`TemporaryModerationUtils.shutdown()` only when the plugin runtime is being torn down.

---

### 💬 Chat

```java
ChatMessageUtils.broadcast("Server restart in 5 minutes");
ChatMessageUtils.broadcastAlert("Restart", "Server restart in 5 minutes");
ChatMessageUtils.sendAdminMessage("Player Brov3r was kicked");

ChatMessageUtils.sendMessage(player, "Welcome!");
ChatMessageUtils.sendMessage(connection, "Welcome!");
ChatMessageUtils.sendMessage("Brov3r", "Welcome!");
```

Actor-aware chat methods require `DisplayServerMessage` for server messages and `AdminChat` for
admin chat.

---

### 🔐 Permission Summary

| Capability | Utility area |
|:-----------|:-------------|
| `KickUser` | `ModerationActionUtils.kickPlayer(actor, ...)` |
| `BanUnbanUser` | Player/SteamID/IP bans and temporary bans |
| `CanModifyPlayerStatsInThePlayerStatsUI` | Chat mute |
| `TeleportToPlayer` | Teleporting yourself to a player |
| `TeleportToCoordinates` | Teleporting to coordinates |
| `TeleportPlayerToAnotherPlayer` | Moving another player |
| `CanSetupSafehouses` | Safehouse management |
| `FactionCheat` | Faction management |
| `AddItem` | Item add/remove |
| `AddXP` | XP and level changes |
| `ClimateManager` | Time changes |
| `StartStopRain` | Rain and weather changes |
| `CreateHorde` | Horde creation |
| `PopmanManage` | Per-chunk zombie limits |
| `ManipulateZombie` | Zombie cell clearing/spawning |
| `DisplayServerMessage` | Server chat messages and alerts |
| `AdminChat` | Admin chat messages |

---

### ⚠️ Notes

- `PlayerLookupUtils`, `TeleportUtils`, `SafehouseUtils`, `FactionUtils`, `ItemUtils`,
  `ExperienceUtils`, `WorldEnvironmentUtils`, `ZombiePopulationUtils`,
  `ModerationActionUtils`, `TemporaryModerationUtils` and `ChatMessageUtils` are server-side.
- `ClientScreenUtils` requires an initialized client display and camera.
- Actor-aware overloads do not replace your own command authorization: they only verify the
  specific capability for the requested action.
