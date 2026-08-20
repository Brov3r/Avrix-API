package com.avrix.api.events;

/**
 * Standard server event identifiers for type-safe subscription and documentation.
 */
public enum ServerEvents {
    /**
     * Triggered when a client network socket passes authentication and is assigned a slot on the server.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code UdpConnection connection} — the incoming client UDP connection.</li>
     *   <li>{@code ServerWorldDatabase.LogonResult result} — the authorization outcome containing client status and assigned role.</li>
     * </ul>
     */
    CLIENT_CONNECT("OnClientConnect"),

    /**
     * Triggered when a network connection is terminated or timed out.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code UdpConnection connection} — the terminating client UDP connection.</li>
     *   <li>{@code String reason} — the descriptive reason for the disconnection.</li>
     * </ul>
     */
    CLIENT_DISCONNECT("OnClientDisconnect"),

    /**
     * Triggered immediately after a player entity is loaded, fully synchronized, and spawned into the game world.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code IsoPlayer player} — the fully connected in-game player character.</li>
     *   <li>{@code UdpConnection connection} — the active network connection belonging to the player.</li>
     * </ul>
     */
    PLAYER_CONNECTED("OnPlayerConnected"),

    /**
     * Triggered when a player character is about to be disconnected and removed from the active session and world.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code IsoPlayer player} — the disconnecting player character entity.</li>
     *   <li>{@code IConnection connection} — the network connection interface associated with the player.</li>
     * </ul>
     */
    PLAYER_DISCONNECT("OnPlayerDisconnect");

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
}