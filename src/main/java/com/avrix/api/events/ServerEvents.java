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