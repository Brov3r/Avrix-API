package com.avrix.api.utils;

import com.avrix.api.config.ConfigManager;
import com.avrix.api.events.DefaultEvents;
import com.avrix.api.events.EventManager;
import com.avrix.api.events.SubscribeEvent;
import com.avrix.api.permissions.PermissionsManager;
import com.avrix.api.utils.ModerationActionUtils.ModerationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zombie.characters.Capability;
import zombie.characters.IsoPlayer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/**
 * Time-limited bans and chat mutes with automatic expiration.
 * <p>
 * Pending expirations are persisted in {@code plugins/avrix-api/temporary-moderation.properties}
 * and processed on the server tick. Temporary mutes are reapplied when the player reconnects.
 * Ban expiry uses the vanilla unban pipeline.
 */
public final class TemporaryModerationUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(TemporaryModerationUtils.class);
    private static final Path STATE_FILE = ConfigManager.resolvePath(
            "avrix-api", "temporary-moderation.properties");

    private static final Map<String, TemporaryEntry> BANS = new ConcurrentHashMap<>();
    private static final Map<String, TemporaryEntry> STEAM_BANS = new ConcurrentHashMap<>();
    private static final Map<String, TemporaryEntry> IP_BANS = new ConcurrentHashMap<>();
    private static final Map<String, TemporaryEntry> MUTES = new ConcurrentHashMap<>();
    private static final Set<String> PENDING_EXPIRATIONS = ConcurrentHashMap.newKeySet();
    private static final Queue<Runnable> PENDING_TASKS = new ConcurrentLinkedQueue<>();
    private static final AtomicBoolean TICK_REGISTERED = new AtomicBoolean();
    private static final AtomicBoolean LOGIN_REGISTERED = new AtomicBoolean();

    private static final Object TICK_LISTENER = new Object() {
        @SubscribeEvent(DefaultEvents.ON_TICK)
        public void onTick() {
            processPendingTasks();
        }
    };

    private static final Object LOGIN_LISTENER = new Object() {
        @SubscribeEvent(custom = "OnPlayerConnected")
        public void onPlayerConnected(IsoPlayer player) {
            applyMuteOnLogin(player);
        }
    };

    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "Avrix-Temporary-Moderation");
        thread.setDaemon(true);
        return thread;
    });

    static {
        loadState();
        registerListeners();
        SCHEDULER.scheduleAtFixedRate(() -> {
            try {
                if (TICK_REGISTERED.get()) {
                    enqueueExpired(Instant.now());
                } else {
                    expireNow(Instant.now());
                }
            } catch (Throwable t) {
                LOGGER.error("Failed to process temporary moderation expirations", t);
            }
        }, 10, 10, TimeUnit.SECONDS);
    }

    private TemporaryModerationUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Temporarily bans a user.
     *
     * @param username target username
     * @param reason   ban reason
     * @param duration ban duration
     * @return result of the initial ban action
     */
    public static ModerationResult banPlayer(String username, String reason, Duration duration) {
        if (!isValidDuration(duration)) {
            return ModerationResult.failure("Duration must be positive");
        }

        ModerationResult result = ModerationActionUtils.banPlayer(username, reason);
        String cleanName = normalizeTarget(username);
        if (result.success() && !cleanName.isEmpty()) {
            BANS.put(cleanName, new TemporaryEntry(
                    username.strip(), Instant.now().plus(duration), reason == null ? "" : reason.strip()));
            saveState();
        }
        return result;
    }

    /**
     * Temporarily bans a user after checking the actor's capability.
     *
     * @param actor    player performing the action
     * @param username target username
     * @param reason   ban reason
     * @param duration ban duration
     * @return result of the initial ban action
     */
    public static ModerationResult banPlayer(
            IsoPlayer actor, String username, String reason, Duration duration
    ) {
        return PermissionsManager.hasCapability(actor, Capability.BanUnbanUser)
                ? banPlayer(username, reason, duration)
                : ModerationResult.failure("Insufficient permissions");
    }

    /**
     * Removes a temporary ban before its natural expiration.
     *
     * @param username target username
     * @return result of the unban action
     */
    public static ModerationResult unbanPlayer(String username) {
        ModerationResult result = ModerationActionUtils.unbanPlayer(username);
        if (result.success() && BANS.remove(normalizeTarget(username)) != null) {
            saveState();
        }
        return result;
    }

    /**
     * Temporarily bans a SteamID.
     *
     * @param steamId  unsigned SteamID64 string
     * @param reason   ban reason
     * @param duration ban duration
     * @return result of the initial ban action
     */
    public static ModerationResult banSteamId(String steamId, String reason, Duration duration) {
        if (!isValidDuration(duration)) {
            return ModerationResult.failure("Duration must be positive");
        }

        ModerationResult result = ModerationActionUtils.banSteamId(steamId, reason);
        String key = normalizeTarget(steamId);
        if (result.success() && !key.isEmpty()) {
            STEAM_BANS.put(key, new TemporaryEntry(
                    steamId.strip(), Instant.now().plus(duration), reason == null ? "" : reason.strip()));
            saveState();
        }
        return result;
    }

    /**
     * Temporarily bans a SteamID after checking the actor's capability.
     *
     * @param actor    player performing the action
     * @param steamId  unsigned SteamID64 string
     * @param reason   ban reason
     * @param duration ban duration
     * @return result of the initial ban action
     */
    public static ModerationResult banSteamId(
            IsoPlayer actor, String steamId, String reason, Duration duration
    ) {
        return PermissionsManager.hasCapability(actor, Capability.BanUnbanUser)
                ? banSteamId(steamId, reason, duration)
                : ModerationResult.failure("Insufficient permissions");
    }

    /**
     * Removes a temporary SteamID ban before its natural expiration.
     *
     * @param steamId unsigned SteamID64 string
     * @return result of the unban action
     */
    public static ModerationResult unbanSteamId(String steamId) {
        ModerationResult result = ModerationActionUtils.unbanSteamId(steamId);
        if (result.success() && STEAM_BANS.remove(normalizeTarget(steamId)) != null) {
            saveState();
        }
        return result;
    }

    /**
     * Temporarily bans an IP address.
     *
     * @param ip       IP address
     * @param reason   ban reason
     * @param duration ban duration
     * @return result of the initial ban action
     */
    public static ModerationResult banIp(String ip, String reason, Duration duration) {
        if (!isValidDuration(duration)) {
            return ModerationResult.failure("Duration must be positive");
        }

        ModerationResult result = ModerationActionUtils.banIp(ip, reason);
        String key = normalizeTarget(ip);
        if (result.success() && !key.isEmpty()) {
            IP_BANS.put(key, new TemporaryEntry(
                    ip.strip(), Instant.now().plus(duration), reason == null ? "" : reason.strip()));
            saveState();
        }
        return result;
    }

    /**
     * Temporarily bans an IP address after checking the actor's capability.
     *
     * @param actor    player performing the action
     * @param ip       IP address
     * @param reason   ban reason
     * @param duration ban duration
     * @return result of the initial ban action
     */
    public static ModerationResult banIp(IsoPlayer actor, String ip, String reason, Duration duration) {
        return PermissionsManager.hasCapability(actor, Capability.BanUnbanUser)
                ? banIp(ip, reason, duration)
                : ModerationResult.failure("Insufficient permissions");
    }

    /**
     * Removes a temporary IP ban before its natural expiration.
     *
     * @param ip IP address
     * @return result of the unban action
     */
    public static ModerationResult unbanIp(String ip) {
        ModerationResult result = ModerationActionUtils.unbanIp(ip);
        if (result.success() && IP_BANS.remove(normalizeTarget(ip)) != null) {
            saveState();
        }
        return result;
    }

    /**
     * Temporarily mutes all chat for a player.
     *
     * @param player   target player
     * @param duration mute duration
     * @return {@code true} if the mute was applied
     */
    public static boolean mutePlayer(IsoPlayer player, Duration duration) {
        if (!isValidDuration(duration) || player == null || player.getUsername() == null
                || player.getUsername().isBlank() || !ModerationActionUtils.mutePlayer(player)) {
            return false;
        }

        MUTES.put(normalizeTarget(player.getUsername()), new TemporaryEntry(
                player.getUsername().strip(), Instant.now().plus(duration), ""));
        saveState();
        return true;
    }

    /**
     * Temporarily mutes all chat for a player after checking the actor's capability.
     *
     * @param actor    player performing the action
     * @param player   target player
     * @param duration mute duration
     * @return {@code true} if the actor is allowed and the mute was applied
     */
    public static boolean mutePlayer(IsoPlayer actor, IsoPlayer player, Duration duration) {
        return PermissionsManager.hasCapability(actor, Capability.CanModifyPlayerStatsInThePlayerStatsUI)
                && mutePlayer(player, duration);
    }

    /**
     * Removes a temporary mute before its natural expiration.
     *
     * @param player target player
     * @return {@code true} if the player was unmuted
     */
    public static boolean unmutePlayer(IsoPlayer player) {
        if (player == null || !ModerationActionUtils.unmutePlayer(player)) {
            return false;
        }
        if (player.getUsername() != null) {
            MUTES.remove(normalizeTarget(player.getUsername()));
            saveState();
        }
        return true;
    }

    /**
     * Removes a temporary mute by username.
     *
     * @param username target username
     * @return {@code true} if the player was online and unmuted
     */
    public static boolean unmutePlayer(String username) {
        return PlayerLookupUtils.getPlayerByUsername(username)
                .map(TemporaryModerationUtils::unmutePlayer)
                .orElse(false);
    }

    /**
     * Returns the expiration time of an active temporary ban.
     *
     * @param username target username
     * @return expiration instant, or empty when the user has no tracked temporary ban
     */
    public static Optional<Instant> getBanExpiration(String username) {
        TemporaryEntry entry = BANS.get(normalizeTarget(username));
        return entry == null ? Optional.empty() : Optional.of(entry.expiresAt());
    }

    /**
     * Returns the expiration time of an active temporary mute.
     *
     * @param username target username
     * @return expiration instant, or empty when the user has no tracked temporary mute
     */
    public static Optional<Instant> getMuteExpiration(String username) {
        TemporaryEntry entry = MUTES.get(normalizeTarget(username));
        return entry == null ? Optional.empty() : Optional.of(entry.expiresAt());
    }

    /**
     * Returns the expiration time of an active temporary SteamID ban.
     *
     * @param steamId unsigned SteamID64 string
     * @return expiration instant, or empty when the SteamID has no tracked temporary ban
     */
    public static Optional<Instant> getSteamBanExpiration(String steamId) {
        TemporaryEntry entry = STEAM_BANS.get(normalizeTarget(steamId));
        return entry == null ? Optional.empty() : Optional.of(entry.expiresAt());
    }

    /**
     * Returns the expiration time of an active temporary IP ban.
     *
     * @param ip IP address
     * @return expiration instant, or empty when the IP has no tracked temporary ban
     */
    public static Optional<Instant> getIpBanExpiration(String ip) {
        TemporaryEntry entry = IP_BANS.get(normalizeTarget(ip));
        return entry == null ? Optional.empty() : Optional.of(entry.expiresAt());
    }

    /**
     * Checks whether a user has an active temporary ban entry.
     *
     * @param username target username
     * @return {@code true} if the ban has not expired
     */
    public static boolean isTemporarilyBanned(String username) {
        return isActive(BANS, username);
    }

    /**
     * Checks whether a user has an active temporary mute entry.
     *
     * @param username target username
     * @return {@code true} if the mute has not expired
     */
    public static boolean isTemporarilyMuted(String username) {
        return isActive(MUTES, username);
    }

    /**
     * Checks whether a SteamID has an active temporary ban entry.
     *
     * @param steamId unsigned SteamID64 string
     * @return {@code true} if the ban has not expired
     */
    public static boolean isTemporarilySteamBanned(String steamId) {
        return isActive(STEAM_BANS, steamId);
    }

    /**
     * Checks whether an IP has an active temporary ban entry.
     *
     * @param ip IP address
     * @return {@code true} if the ban has not expired
     */
    public static boolean isTemporarilyIpBanned(String ip) {
        return isActive(IP_BANS, ip);
    }

    /**
     * Stops the background expiration worker and unregisters listeners.
     */
    public static void shutdown() {
        SCHEDULER.shutdownNow();
        if (TICK_REGISTERED.compareAndSet(true, false)) {
            EventManager.unregister(TICK_LISTENER);
        }
        if (LOGIN_REGISTERED.compareAndSet(true, false)) {
            EventManager.unregister(LOGIN_LISTENER);
        }
        PENDING_TASKS.clear();
        PENDING_EXPIRATIONS.clear();
        BANS.clear();
        STEAM_BANS.clear();
        IP_BANS.clear();
        MUTES.clear();
    }

    /**
     * Scans for expired entries and schedules their cleanup.
     *
     * @param now comparison instant
     */
    static void enqueueExpired(Instant now) {
        enqueueExpired(now, "ban", BANS, TemporaryModerationUtils::expireBan);
        enqueueExpired(now, "steamban", STEAM_BANS, TemporaryModerationUtils::expireSteamBan);
        enqueueExpired(now, "ipban", IP_BANS, TemporaryModerationUtils::expireIpBan);

        for (Map.Entry<String, TemporaryEntry> entry : MUTES.entrySet()) {
            if (entry.getValue().isExpired(now) && PENDING_EXPIRATIONS.add("mute:" + entry.getKey())) {
                TemporaryEntry value = entry.getValue();
                PENDING_TASKS.offer(() -> {
                    try {
                        expireMute(entry.getKey(), value);
                    } finally {
                        PENDING_EXPIRATIONS.remove("mute:" + entry.getKey());
                    }
                });
            }
        }
    }

    /**
     * Enqueues expired entries from a ban map.
     *
     * @param now      comparison instant
     * @param prefix   pending key prefix
     * @param entries  entry map
     * @param expirer  expiration action
     */
    private static void enqueueExpired(
            Instant now,
            String prefix,
            Map<String, TemporaryEntry> entries,
            BiConsumer<String, TemporaryEntry> expirer
    ) {
        for (Map.Entry<String, TemporaryEntry> entry : entries.entrySet()) {
            String pendingKey = prefix + ":" + entry.getKey();
            if (entry.getValue().isExpired(now) && PENDING_EXPIRATIONS.add(pendingKey)) {
                TemporaryEntry value = entry.getValue();
                PENDING_TASKS.offer(() -> {
                    try {
                        expirer.accept(entry.getKey(), value);
                    } finally {
                        PENDING_EXPIRATIONS.remove(pendingKey);
                    }
                });
            }
        }
    }

    /**
     * Processes a bounded batch of queued expiration tasks.
     */
    static void processPendingTasks() {
        Runnable task;
        int processed = 0;
        while (processed < 64 && (task = PENDING_TASKS.poll()) != null) {
            try {
                task.run();
            } catch (Throwable t) {
                LOGGER.error("Failed to process a temporary moderation expiration", t);
            }
            processed++;
        }
    }

    /**
     * Processes expired entries immediately when the tick listener is unavailable.
     *
     * @param now comparison instant
     */
    private static void expireNow(Instant now) {
        for (Map.Entry<String, TemporaryEntry> entry : BANS.entrySet()) {
            if (entry.getValue().isExpired(now)) {
                expireBan(entry.getKey(), entry.getValue());
            }
        }
        for (Map.Entry<String, TemporaryEntry> entry : STEAM_BANS.entrySet()) {
            if (entry.getValue().isExpired(now)) {
                expireSteamBan(entry.getKey(), entry.getValue());
            }
        }
        for (Map.Entry<String, TemporaryEntry> entry : IP_BANS.entrySet()) {
            if (entry.getValue().isExpired(now)) {
                expireIpBan(entry.getKey(), entry.getValue());
            }
        }
        for (Map.Entry<String, TemporaryEntry> entry : MUTES.entrySet()) {
            if (entry.getValue().isExpired(now)) {
                expireMute(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Expires one temporary ban.
     *
     * @param key   normalized username
     * @param entry entry snapshot
     */
    private static void expireBan(String key, TemporaryEntry entry) {
        ModerationResult result = ModerationActionUtils.unbanPlayer(entry.target());
        if (!result.success()) {
            LOGGER.warn("Failed to expire temporary ban for '{}': {}", entry.target(), result.message());
            return;
        }

        if (BANS.remove(key, entry)) {
            saveState();
            LOGGER.info("Temporary ban expired for '{}'", entry.target());
        }
    }

    /**
     * Expires one temporary SteamID ban.
     *
     * @param key   normalized SteamID
     * @param entry entry snapshot
     */
    private static void expireSteamBan(String key, TemporaryEntry entry) {
        ModerationResult result = ModerationActionUtils.unbanSteamId(entry.target());
        if (!result.success()) {
            LOGGER.warn("Failed to expire temporary SteamID ban for '{}': {}", entry.target(), result.message());
            return;
        }

        if (STEAM_BANS.remove(key, entry)) {
            saveState();
            LOGGER.info("Temporary SteamID ban expired for '{}'", entry.target());
        }
    }

    /**
     * Expires one temporary IP ban.
     *
     * @param key   normalized IP
     * @param entry entry snapshot
     */
    private static void expireIpBan(String key, TemporaryEntry entry) {
        ModerationResult result = ModerationActionUtils.unbanIp(entry.target());
        if (!result.success()) {
            LOGGER.warn("Failed to expire temporary IP ban for '{}': {}", entry.target(), result.message());
            return;
        }

        if (IP_BANS.remove(key, entry)) {
            saveState();
            LOGGER.info("Temporary IP ban expired for '{}'", entry.target());
        }
    }

    /**
     * Expires one temporary mute.
     *
     * @param key   normalized username
     * @param entry entry snapshot
     */
    private static void expireMute(String key, TemporaryEntry entry) {
        PlayerLookupUtils.getPlayerByUsername(entry.target())
                .ifPresent(ModerationActionUtils::unmutePlayer);
        if (MUTES.remove(key, entry)) {
            saveState();
            LOGGER.info("Temporary mute expired for '{}'", entry.target());
        }
    }

    /**
     * Reapplies an active temporary mute when a player connects.
     *
     * @param player connected player
     */
    private static void applyMuteOnLogin(IsoPlayer player) {
        if (player == null || player.getUsername() == null) {
            return;
        }

        String key = normalizeTarget(player.getUsername());
        TemporaryEntry entry = MUTES.get(key);
        if (entry == null) {
            return;
        }
        if (entry.isExpired(Instant.now())) {
            MUTES.remove(key, entry);
            saveState();
            return;
        }
        ModerationActionUtils.setMuted(player, true);
    }

    /**
     * Registers tick and login listeners.
     */
    private static void registerListeners() {
        try {
            EventManager.register(TICK_LISTENER);
            TICK_REGISTERED.set(true);
        } catch (Throwable t) {
            LOGGER.warn("Failed to register temporary moderation tick listener; using scheduler fallback", t);
        }
        try {
            EventManager.register(LOGIN_LISTENER);
            LOGIN_REGISTERED.set(true);
        } catch (Throwable t) {
            LOGGER.warn("Failed to register temporary moderation login listener", t);
        }
    }

    /**
     * Checks whether a tracked entry is still active.
     *
     * @param entries  entry map
     * @param username target username
     * @return {@code true} if an active entry exists
     */
    private static boolean isActive(Map<String, TemporaryEntry> entries, String username) {
        TemporaryEntry entry = entries.get(normalizeTarget(username));
        return entry != null && !entry.isExpired(Instant.now());
    }

    /**
     * Validates a temporary action duration.
     *
     * @param duration requested duration
     * @return {@code true} if the duration is positive
     */
    private static boolean isValidDuration(Duration duration) {
        return duration != null && !duration.isZero() && !duration.isNegative();
    }

    /**
     * Normalizes a username for case-insensitive tracking.
     *
     * @param username raw username
     * @return normalized key, or an empty string
     */
    private static String normalizeTarget(String username) {
        return username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
    }

    /**
     * Loads persisted temporary moderation entries.
     */
    private static synchronized void loadState() {
        if (!Files.isRegularFile(STATE_FILE)) {
            return;
        }

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(STATE_FILE)) {
            properties.load(input);
            loadEntries(properties, "ban", BANS);
            loadEntries(properties, "steamban", STEAM_BANS);
            loadEntries(properties, "ipban", IP_BANS);
            loadEntries(properties, "mute", MUTES);
        } catch (IOException | RuntimeException ex) {
            LOGGER.error("Failed to load temporary moderation state from '{}'", STATE_FILE, ex);
        }
    }

    /**
     * Loads a group of persisted entries.
     *
     * @param properties state properties
     * @param prefix     entry prefix
     * @param target     destination map
     */
    private static void loadEntries(Properties properties, String prefix, Map<String, TemporaryEntry> target) {
        for (int index = 0; ; index++) {
            String key = prefix + "." + index + ".";
            String username = properties.getProperty(key + "target");
            if (username == null) {
                return;
            }

            String expiration = properties.getProperty(key + "expires");
            if (expiration == null) {
                continue;
            }
            try {
                TemporaryEntry entry = new TemporaryEntry(
                        username,
                        Instant.parse(expiration),
                        properties.getProperty(key + "reason", "")
                );
                target.put(normalizeTarget(username), entry);
            } catch (RuntimeException ex) {
                LOGGER.warn("Ignoring invalid temporary moderation entry for '{}'", username, ex);
            }
        }
    }

    /**
     * Persists all temporary moderation entries.
     */
    private static synchronized void saveState() {
        Properties properties = new Properties();
        writeEntries(properties, "ban", BANS);
        writeEntries(properties, "steamban", STEAM_BANS);
        writeEntries(properties, "ipban", IP_BANS);
        writeEntries(properties, "mute", MUTES);

        try {
            Path parent = STATE_FILE.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temporary = Files.createTempFile(parent, "avrix-temporary-moderation-", ".tmp");
            try {
                try (OutputStream output = Files.newOutputStream(temporary)) {
                    properties.store(output, "Avrix temporary moderation state");
                }
                try {
                    Files.move(temporary, STATE_FILE,
                            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ex) {
                    Files.move(temporary, STATE_FILE, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException ex) {
            LOGGER.error("Failed to save temporary moderation state to '{}'", STATE_FILE, ex);
        }
    }

    /**
     * Writes a group of entries into the state properties.
     *
     * @param properties state properties
     * @param prefix     entry prefix
     * @param entries    source map
     */
    private static void writeEntries(
            Properties properties, String prefix, Map<String, TemporaryEntry> entries
    ) {
        int index = 0;
        for (TemporaryEntry entry : entries.values()) {
            String key = prefix + "." + index + ".";
            properties.setProperty(key + "target", entry.target());
            properties.setProperty(key + "expires", entry.expiresAt().toString());
            properties.setProperty(key + "reason", entry.reason());
            index++;
        }
    }

    /**
     * Temporary moderation entry.
     *
     * @param target    original username
     * @param expiresAt expiration instant
     * @param reason    configured reason
     */
    private record TemporaryEntry(String target, Instant expiresAt, String reason) {

        /**
         * Checks whether this entry has expired.
         *
         * @param now comparison instant
         * @return {@code true} if the expiration time is not in the future
         */
        private boolean isExpired(Instant now) {
            return !expiresAt.isAfter(now);
        }
    }
}
