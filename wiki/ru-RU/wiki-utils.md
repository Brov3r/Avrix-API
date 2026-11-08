[Главная](../wiki-language.md) > [Документация](wiki-main.md) > Утилиты

# 🧰 API утилит

Пакет `com.avrix.api.utils` содержит специализированные помощники для типовых задач плагинов:
поиска игроков, расчёта дистанций, преобразования координат, телепортации, работы с safehouse,
фракциями, предметами, опытом, окружающим миром, популяцией зомби, видимостью, модерацией,
временной модерацией и чатом.

Большинство изменяющих методов имеют две формы:

- метод без actor — для доверенного кода плагина или консоли;
- перегрузка с `IsoPlayer actor`, которая проверяет нужную capability через систему прав Avrix.

Методы только для чтения не требуют прав.

---

### 📦 Обзор классов

| Класс | Сторона | Ответственность |
|:------|:--------|:----------------|
| `PlayerLookupUtils` | Сервер | Снимки списка игроков и поиск |
| `WorldDistanceUtils` | Обе | Дистанции 2D/3D, радиусы, ближайшие объекты |
| `ClientScreenUtils` | Клиент | Размеры окна/viewport и world/screen-преобразования |
| `TeleportUtils` | Сервер | Телепортация игроков и объектов |
| `SafehouseUtils` | Сервер | Поиск safehouse и управление участниками |
| `FactionUtils` | Сервер | Поиск фракций и управление участниками |
| `ItemUtils` | Сервер | Создание, выдача, изъятие и подсчёт предметов |
| `ExperienceUtils` | Сервер | XP и уровни перков |
| `WorldEnvironmentUtils` | Сервер | Время, календарь и погода |
| `ZombiePopulationUtils` | Сервер | Количество зомби, лимиты, орды и ячейки популяции |
| `VisibilityUtils` | Обе | Прямая видимость и raycast |
| `ModerationActionUtils` | Сервер | Кик, бан, разбан и мут |
| `TemporaryModerationUtils` | Сервер | Временные баны и муты с автоистечением |
| `ChatMessageUtils` | Сервер | Сообщения в серверный и админский чат |

---

### 👤 Поиск игроков

`PlayerLookupUtils` возвращает неизменяемые снимки и не отдаёт изменяемый список игроков сервера.

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

`getSteamId(...)` использует SteamID сетевого соединения, если у сущности игрока его нет.
`getConnection(...)` возвращает `UdpConnection`, если игрок подключён.

---

### 📏 Дистанции и радиусы

`WorldDistanceUtils` поддерживает координаты, `IsoObject`, `IsoPlayer` и `IsoGridSquare`.

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

Обобщённые методы работают с любыми наследниками `IsoObject`:

```java
List<IsoZombie> zombiesInRange = WorldDistanceUtils.getWithinRadius(player, zombies, 12.0f);
List<IsoZombie> sorted = WorldDistanceUtils.sortByDistance(player, zombies);
```

Методы `distanceSquared...` не вычисляют квадратный корень и лучше подходят для сравнений.

---

### 🖥️ Экран клиента и координаты

`ClientScreenUtils` работает только на клиенте. Он использует текущую камеру и viewport выбранного
игрока split-screen.

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

Также доступны скалярные методы `worldToScreenX/Y` и `screenToWorldX/Y`.

---

### 🚀 Телепортация

```java
TeleportUtils.teleportPlayer(player, x, y, z);
TeleportUtils.teleportPlayer(player, square);
TeleportUtils.teleportPlayerToPlayer(player, destination);

TeleportUtils.teleportObject(object, x, y, z);
TeleportUtils.teleportObject(object, square);
```

Перегрузки с actor проверяют `TeleportToPlayer`, `TeleportToCoordinates` или
`TeleportPlayerToAnotherPlayer`:

```java
TeleportUtils.teleportPlayer(actor, target, x, y, z);
TeleportUtils.teleportPlayerToPlayer(actor, target, destination);
TeleportUtils.teleportObject(actor, object, x, y, z);
```

Движущиеся объекты перемещаются напрямую. Статические объекты удаляются со старого квадрата и
добавляются на новый. Телепортация онлайн-игроков синхронизируется ванильным пакетом телепорта.

---

### 🏠 Safehouse

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

Изменения синхронизируются ванильным пакетом safehouse и чатом safehouse. Перегрузки с actor
требуют `CanSetupSafehouses`.

---

### 🤝 Фракции

```java
Faction faction = FactionUtils.getByName("Survivors");
Faction playerFaction = FactionUtils.getForPlayer(player);

Faction created = FactionUtils.create("Survivors", "Brov3r");
FactionUtils.addMember(faction, "Survivor");
FactionUtils.removeMember(faction, "Survivor");
FactionUtils.setTag(faction, "SURV");
FactionUtils.sync(faction);
```

Имя фракции подчиняется ванильному ограничению 3–15 символов. Перегрузки с actor требуют
`FactionCheat`.

---

### 🎒 Предметы

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

Неположительное количество при изъятии удаляет все подходящие предметы. Перегрузки с actor требуют
`AddItem`.

---

### ⭐ Опыт

```java
PerkFactory.Perk aiming = ExperienceUtils.getPerk("Aiming");

float xp = ExperienceUtils.getXp(player, aiming);
int level = ExperienceUtils.getLevel(player, aiming);

ExperienceUtils.addXp(player, aiming, 100.0f);
ExperienceUtils.removeXp(player, aiming, 50.0f);
ExperienceUtils.setXp(player, aiming, 250.0f);
ExperienceUtils.setLevel(player, aiming, 3);
```

Перегрузки с actor требуют `AddXP`. Изменения XP онлайн-игрока отправляются ванильным серверным
путём.

---

### 🌦️ Время и погода

Календарные значения в `WorldEnvironmentUtils` считаются с единицы: месяцы `1-12`, дни `1-31`.

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

Перегрузки изменения времени с actor требуют `ClimateManager`. Управление дождём требует
`StartStopRain`.

---

### 🧟 Популяция зомби

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

Права для перегрузок с actor:

- орды — `CreateHorde`;
- лимиты на чанк — `PopmanManage`;
- очистка и немедленный спавн ячеек — `ManipulateZombie`.

---

### 👁️ Видимость и raycast

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

Закрытые двери и твёрдые объекты блокируют обзор. Открытые двери и окна считаются прозрачными.

---

### 🛡️ Модерация

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

`ModerationResult` содержит `success` и человекочитаемое `message`.

Права для перегрузок с actor: `KickUser`, `BanUnbanUser` и
`CanModifyPlayerStatsInThePlayerStatsUI` для мута чата.

Ванильные защиты `CantBeKickedByUser` и `CantBeBannedByUser` соблюдаются всегда.

---

### ⏳ Временные баны и муты

`TemporaryModerationUtils` сохраняет сроки в
`plugins/avrix-api/temporary-moderation.properties`, проверяет их на серверном тике и повторно
применяет временный мут при входе игрока.

```java
TemporaryModerationUtils.banPlayer("Brov3r", "Cool down", Duration.ofHours(12));
TemporaryModerationUtils.banSteamId("76561198000000001", "Cool down", Duration.ofDays(3));
TemporaryModerationUtils.banIp("203.0.113.10", "Abuse", Duration.ofHours(6));

TemporaryModerationUtils.mutePlayer(player, Duration.ofMinutes(30));
TemporaryModerationUtils.unmutePlayer(player);

Optional<Instant> expires = TemporaryModerationUtils.getMuteExpiration("Brov3r");
boolean timedOut = TemporaryModerationUtils.isTemporarilyMuted("Brov3r");
```

Ручное снятие удаляет отслеживаемый срок:

```java
TemporaryModerationUtils.unbanPlayer("Brov3r");
TemporaryModerationUtils.unbanSteamId("76561198000000001");
TemporaryModerationUtils.unbanIp("203.0.113.10");
```

Обработка истечения выполняется в серверном потоке через `OnTick`. Вызывайте
`TemporaryModerationUtils.shutdown()` только при остановке runtime плагина.

---

### 💬 Чат

```java
ChatMessageUtils.broadcast("Server restart in 5 minutes");
ChatMessageUtils.broadcastAlert("Restart", "Server restart in 5 minutes");
ChatMessageUtils.sendAdminMessage("Player Brov3r was kicked");

ChatMessageUtils.sendMessage(player, "Welcome!");
ChatMessageUtils.sendMessage(connection, "Welcome!");
ChatMessageUtils.sendMessage("Brov3r", "Welcome!");
```

Перегрузки с actor требуют `DisplayServerMessage` для серверных сообщений и `AdminChat` для
админского чата.

---

### 🔐 Сводка прав

| Capability | Область утилит |
|:-----------|:---------------|
| `KickUser` | `ModerationActionUtils.kickPlayer(actor, ...)` |
| `BanUnbanUser` | Баны по игроку/SteamID/IP и временные баны |
| `CanModifyPlayerStatsInThePlayerStatsUI` | Мут чата |
| `TeleportToPlayer` | Телепорт к игроку |
| `TeleportToCoordinates` | Телепорт по координатам |
| `TeleportPlayerToAnotherPlayer` | Перемещение другого игрока |
| `CanSetupSafehouses` | Управление safehouse |
| `FactionCheat` | Управление фракциями |
| `AddItem` | Выдача и изъятие предметов |
| `AddXP` | Изменение XP и уровней |
| `ClimateManager` | Изменение времени |
| `StartStopRain` | Управление дождём и погодой |
| `CreateHorde` | Создание орд |
| `PopmanManage` | Лимиты зомби на чанк |
| `ManipulateZombie` | Очистка и спавн ячеек зомби |
| `DisplayServerMessage` | Серверные сообщения и алерты |
| `AdminChat` | Сообщения в админский чат |

---

### ⚠️ Примечания

- `PlayerLookupUtils`, `TeleportUtils`, `SafehouseUtils`, `FactionUtils`, `ItemUtils`,
  `ExperienceUtils`, `WorldEnvironmentUtils`, `ZombiePopulationUtils`,
  `ModerationActionUtils`, `TemporaryModerationUtils` и `ChatMessageUtils` предназначены для сервера.
- `ClientScreenUtils` требует инициализированного клиентского дисплея и камеры.
- Перегрузки с actor не заменяют авторизацию ваших команд: они проверяют только конкретную
  capability для запрошенного действия.
