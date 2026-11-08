package com.avrix.api.network;

import java.util.Map;

/**
 * Handles an incoming message on a registered network channel.
 */
@FunctionalInterface
public interface NetworkHandler {

    /**
     * Processes an incoming message.
     *
     * @param context receiving context
     * @param payload decoded message payload
     */
    void handle(NetworkContext context, Map<String, Object> payload);
}
