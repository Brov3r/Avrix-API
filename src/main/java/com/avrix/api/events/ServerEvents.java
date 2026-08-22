package com.avrix.api.events;

/**
 * Standard server event identifiers for type-safe subscription and documentation.
 */
public enum ServerEvents implements Event {

    /**
     * Triggered when a client network socket passes authentication and is assigned a slot on the server.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code UdpConnection connection} — the incoming client UDP connection.</li>
     *   <li>{@code ServerWorldDatabase.LogonResult result} — authorization outcome containing status and assigned role.</li>
     * </ul>
     */
    CLIENT_CONNECT("OnClientConnect"),

    /**
     * Triggered when a network connection is terminated or timed out.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code UdpConnection connection} — the terminating client UDP connection.</li>
     *   <li>{@code String reason} — descriptive reason for the disconnection.</li>
     * </ul>
     */
    CLIENT_DISCONNECT("OnClientDisconnect"),

    /**
     * Triggered immediately after a player entity is loaded, fully synchronized, and spawned into the game world.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code IsoPlayer player} — fully connected in-game player character.</li>
     *   <li>{@code UdpConnection connection} — active network connection belonging to the player.</li>
     * </ul>
     */
    PLAYER_CONNECTED("OnPlayerConnected"),

    /**
     * Triggered when a player character is about to be disconnected and removed from the active session and world.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code IsoPlayer player} — disconnecting player character entity.</li>
     *   <li>{@code IConnection connection} — network connection interface associated with the player.</li>
     * </ul>
     */
    PLAYER_DISCONNECT("OnPlayerDisconnect"),

    /**
     * Triggered when an in-game player is assigned a new role.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code IsoPlayer player} — the target player entity.</li>
     *   <li>{@code Role oldRole} — previously assigned role.</li>
     *   <li>{@code Role newRole} — newly assigned role.</li>
     * </ul>
     */
    PLAYER_ROLE_ASSIGNED("OnPlayerRoleAssigned"),

    /**
     * Triggered when a custom permission node is granted to a player.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code String username} — target player username.</li>
     *   <li>{@code String permissionNode} — granted permission node.</li>
     *   <li>{@code Instant expiration} — expiration timestamp or null if permanent.</li>
     * </ul>
     */
    PERMISSION_GRANTED("OnPermissionGranted"),

    /**
     * Triggered when a custom permission node is revoked from a player.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code String username} — target player username.</li>
     *   <li>{@code String permissionNode} — revoked permission node.</li>
     * </ul>
     */
    PERMISSION_REVOKED("OnPermissionRevoked"),

    /**
     * Triggered immediately before an authorized command or subcommand is executed.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code CommandContext context} — the immutable execution context.</li>
     *   <li>{@code String commandPath} — the full command trigger path (e.g. {@code "kit starter"}).</li>
     * </ul>
     */
    COMMAND_EXECUTE("OnCommandExecute"),

    /**
     * Triggered after a command or subcommand finishes execution successfully.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code CommandContext context} — the immutable execution context.</li>
     *   <li>{@code String commandPath} — the full command trigger path.</li>
     *   <li>{@code String result} — the execution output string returned by the handler.</li>
     *   <li>{@code long elapsedMs} — total execution duration in milliseconds.</li>
     * </ul>
     */
    COMMAND_SUCCESS("OnCommandSuccess"),

    /**
     * Triggered when command execution fails due to security refusal, cooldown, or runtime exception.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code CommandContext context} — the immutable execution context.</li>
     *   <li>{@code String commandPath} — the full command trigger path.</li>
     *   <li>{@code String reason} — user-friendly failure explanation.</li>
     *   <li>{@code Throwable error} — the underlying exception, or {@code null} if validation failure.</li>
     * </ul>
     */
    COMMAND_FAILURE("OnCommandFailure");

    private final String eventName;

    ServerEvents(String eventName) {
        this.eventName = eventName;
    }

    /**
     * Gets the unique string identifier for this event.
     *
     * @return the event name
     */
    public String getEventName() {
        return this.eventName;
    }

    @Override
    public String getName() {
        return this.eventName;
    }
}