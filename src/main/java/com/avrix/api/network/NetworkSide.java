package com.avrix.api.network;

/**
 * Identifies the side of a network message.
 */
public enum NetworkSide {
    /**
     * Message received or sent by the client.
     */
    CLIENT,

    /**
     * Message received or sent by the dedicated server.
     */
    SERVER
}
