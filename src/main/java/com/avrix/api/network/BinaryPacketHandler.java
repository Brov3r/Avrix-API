package com.avrix.api.network;

import zombie.core.network.ByteBufferReader;

/**
 * Handles an incoming binary custom packet.
 */
@FunctionalInterface
public interface BinaryPacketHandler {

    /**
     * Reads and processes a binary payload.
     *
     * @param context receiving context
     * @param payload big-endian payload reader
     * @throws Exception if the payload is malformed or processing fails
     */
    void handle(NetworkContext context, ByteBufferReader payload) throws Exception;
}
