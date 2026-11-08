package com.avrix.api.network;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zombie.core.random.RandStandard;
import zombie.network.GameClient;
import zombie.network.GameServer;
import zombie.network.PacketTypes;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link NetworkPackets}.
 */
@DisplayName("Native Packet Helpers")
class NetworkPacketsTest {

    @BeforeEach
    void setUp() {
        try {
            RandStandard.INSTANCE.init();
        } catch (Throwable ignored) {
        }
    }

    @Test
    @DisplayName("Should reject null packet types and connections")
    void shouldRejectNullInputs() {
        assertThat(NetworkPackets.sendToServer(null)).isFalse();
        assertThat(NetworkPackets.sendToConnection(null, PacketTypes.PacketType.Roles)).isFalse();
        assertThat(NetworkPackets.sendToPlayer(null, PacketTypes.PacketType.Roles)).isFalse();
    }

    @Test
    @DisplayName("Should refuse server broadcasts when the server side is inactive")
    void shouldRefuseBroadcastWithoutServer() {
        boolean previousServer = GameServer.server;
        boolean previousClient = GameClient.client;
        try {
            GameServer.server = false;
            GameClient.client = false;

            assertThat(NetworkPackets.sendToAll(PacketTypes.PacketType.Roles)).isFalse();
            assertThat(NetworkPackets.sendToRelative(PacketTypes.PacketType.Roles, 1.0f, 2.0f)).isFalse();
        } finally {
            GameServer.server = previousServer;
            GameClient.client = previousClient;
        }
    }

    @Test
    @DisplayName("Should reject packet types without an INetworkPacket handler")
    void shouldRejectPacketTypesWithoutHandler() {
        assertThat(NetworkPackets.sendToAll(PacketTypes.PacketType.ClientCommand)).isFalse();
    }
}
