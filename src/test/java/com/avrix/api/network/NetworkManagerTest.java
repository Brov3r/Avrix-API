package com.avrix.api.network;

import com.avrix.api.events.EventManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zombie.core.random.RandStandard;
import zombie.core.raknet.UdpConnection;
import zombie.network.GameClient;
import zombie.network.GameServer;

import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link NetworkManager} and {@link NetworkChannel}.
 */
@DisplayName("Network Channel Manager")
class NetworkManagerTest {

    @BeforeEach
    void setUp() {
        try {
            RandStandard.INSTANCE.init();
        } catch (Throwable ignored) {
        }
    }

    @AfterEach
    void tearDown() {
        NetworkManager.unregisterAll();
    }

    @Test
    @DisplayName("Should dispatch incoming messages to a registered channel handler")
    void shouldDispatchIncomingMessage() {
        AtomicReference<NetworkContext> contextReference = new AtomicReference<>();
        AtomicReference<Map<String, Object>> payloadReference = new AtomicReference<>();

        NetworkManager.channel("test-plugin")
                .on("hello", (context, payload) -> {
                    contextReference.set(context);
                    payloadReference.set(payload);
                });

        NetworkManager.dispatchIncoming(
                "test-plugin",
                "hello",
                new NetworkContext(NetworkSide.SERVER, null, null),
                NetworkPayload.toTable(Map.of("message", "hi"))
        );

        assertThat(contextReference.get().isServer()).isTrue();
        assertThat(payloadReference.get()).containsEntry("message", "hi");
    }

    @Test
    @DisplayName("Should receive bridged OnClientCommand and OnServerCommand events")
    void shouldReceiveBridgedEvents() {
        AtomicReference<String> clientPayload = new AtomicReference<>();
        AtomicReference<String> serverPayload = new AtomicReference<>();

        NetworkManager.channel("event-plugin")
                .on("client", (context, payload) -> clientPayload.set((String) payload.get("value")))
                .on("server", (context, payload) -> serverPayload.set((String) payload.get("value")));

        EventManager.invoke("OnClientCommand", "event-plugin", "client", null,
                NetworkPayload.toTable(Map.of("value", "from-client")));
        EventManager.invoke("OnServerCommand", "event-plugin", "server",
                NetworkPayload.toTable(Map.of("value", "from-server")));

        assertThat(clientPayload.get()).isEqualTo("from-client");
        assertThat(serverPayload.get()).isEqualTo("from-server");
    }

    @Test
    @DisplayName("Should ignore unknown modules and commands")
    void shouldIgnoreUnknownRoutes() {
        AtomicBoolean called = new AtomicBoolean();
        NetworkManager.channel("test-plugin")
                .on("hello", (context, payload) -> called.set(true));

        NetworkManager.dispatchIncoming(
                "other-plugin", "hello", new NetworkContext(NetworkSide.SERVER, null, null), null);
        NetworkManager.dispatchIncoming(
                "test-plugin", "other", new NetworkContext(NetworkSide.SERVER, null, null), null);

        assertThat(called).isFalse();
    }

    @Test
    @DisplayName("Should remove handlers when a channel is unregistered")
    void shouldUnregisterChannel() {
        AtomicBoolean called = new AtomicBoolean();
        NetworkManager.channel("test-plugin")
                .on("hello", (context, payload) -> called.set(true));

        NetworkManager.unregister("test-plugin");
        NetworkManager.dispatchIncoming(
                "test-plugin", "hello", new NetworkContext(NetworkSide.SERVER, null, null), null);

        assertThat(called).isFalse();
    }

    @Test
    @DisplayName("Should isolate handler failures")
    void shouldIsolateHandlerFailures() {
        NetworkManager.channel("test-plugin")
                .on("hello", (context, payload) -> {
                    throw new IllegalStateException("boom");
                });

        assertThatCode(() -> NetworkManager.dispatchIncoming(
                "test-plugin", "hello", new NetworkContext(NetworkSide.SERVER, null, null), null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should validate module, command, and handler arguments")
    void shouldValidateArguments() {
        assertThatThrownBy(() -> NetworkManager.channel(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NetworkManager.channel(null))
                .isInstanceOf(NullPointerException.class);

        NetworkChannel channel = NetworkManager.channel("test-plugin");
        assertThatThrownBy(() -> channel.on(" ", (context, payload) -> {
        })).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> channel.on("hello", null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> channel.off(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> channel.onBinary(" ", (context, payload) -> {
        })).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> channel.onBinary("hello", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Should reuse the same channel instance for a module")
    void shouldReuseChannel() {
        NetworkChannel first = NetworkManager.channel("test-plugin");
        NetworkChannel second = NetworkManager.channel("test-plugin");

        assertThat(first).isSameAs(second);
    }

    @Test
    @DisplayName("Should dispatch binary payloads to a binary handler")
    void shouldDispatchBinaryPayload() {
        AtomicBoolean called = new AtomicBoolean();
        AtomicInteger number = new AtomicInteger();
        AtomicReference<String> text = new AtomicReference<>();

        NetworkManager.channel("test-plugin").onBinary("binary", (context, payload) -> {
            called.set(true);
            number.set(payload.getInt());
            text.set(payload.getUTF());
        });

        byte[] binary = new BinaryPacketWriter().putInt(42).putUTF("binary").toByteArray();
        NetworkManager.dispatchIncoming(
                "test-plugin",
                "binary",
                new NetworkContext(NetworkSide.SERVER, null, null),
                BinaryPacketCodec.encode(binary));

        assertThat(called).isTrue();
        assertThat(number.get()).isEqualTo(42);
        assertThat(text.get()).isEqualTo("binary");
    }

    @Test
    @DisplayName("Should use either a map or a binary handler for one command")
    void shouldKeepMapAndBinaryHandlersExclusive() {
        AtomicBoolean mapCalled = new AtomicBoolean();
        AtomicBoolean binaryCalled = new AtomicBoolean();
        NetworkChannel channel = NetworkManager.channel("test-plugin");

        channel.on("packet", (context, payload) -> mapCalled.set(true))
                .onBinary("packet", (context, payload) -> binaryCalled.set(true));

        NetworkManager.dispatchIncoming(
                "test-plugin", "packet", new NetworkContext(NetworkSide.SERVER, null, null),
                NetworkPayload.toTable(Map.of("value", 1)));
        assertThat(mapCalled).isFalse();

        NetworkManager.dispatchIncoming(
                "test-plugin", "packet", new NetworkContext(NetworkSide.SERVER, null, null),
                BinaryPacketCodec.encode(new byte[]{1}));
        assertThat(binaryCalled).isTrue();

        mapCalled.set(false);
        binaryCalled.set(false);
        channel.on("packet", (context, payload) -> mapCalled.set(true));

        NetworkManager.dispatchIncoming(
                "test-plugin", "packet", new NetworkContext(NetworkSide.SERVER, null, null),
                BinaryPacketCodec.encode(new byte[]{1}));
        assertThat(binaryCalled).isFalse();

        NetworkManager.dispatchIncoming(
                "test-plugin", "packet", new NetworkContext(NetworkSide.SERVER, null, null),
                NetworkPayload.toTable(Map.of("value", 1)));
        assertThat(mapCalled).isTrue();
    }

    @Test
    @DisplayName("Should ignore malformed and oversized binary payloads")
    void shouldIgnoreInvalidBinaryPayloads() {
        AtomicBoolean called = new AtomicBoolean();
        NetworkManager.channel("test-plugin")
                .onBinary("binary", (context, payload) -> called.set(true));

        assertThatCode(() -> NetworkManager.dispatchIncoming(
                "test-plugin", "binary", new NetworkContext(NetworkSide.SERVER, null, null),
                NetworkPayload.toTable(Map.of(BinaryPacketCodec.ENVELOPE_KEY, "not-base64!"))))
                .doesNotThrowAnyException();
        assertThat(called).isFalse();

        String oversized = Base64.getEncoder().encodeToString(
                new byte[BinaryPacketCodec.MAX_PAYLOAD_BYTES + 1]);
        assertThatCode(() -> NetworkManager.dispatchIncoming(
                "test-plugin", "binary", new NetworkContext(NetworkSide.SERVER, null, null),
                NetworkPayload.toTable(Map.of(BinaryPacketCodec.ENVELOPE_KEY, oversized))))
                .doesNotThrowAnyException();
        assertThat(called).isFalse();
    }

    @Test
    @DisplayName("Should isolate binary handler failures")
    void shouldIsolateBinaryHandlerFailures() {
        AtomicBoolean called = new AtomicBoolean();
        NetworkManager.channel("test-plugin").onBinary("binary", (context, payload) -> {
            called.set(true);
            throw new IllegalStateException("boom");
        });

        assertThatCode(() -> NetworkManager.dispatchIncoming(
                "test-plugin", "binary", new NetworkContext(NetworkSide.SERVER, null, null),
                BinaryPacketCodec.encode(new byte[]{1})))
                .doesNotThrowAnyException();
        assertThat(called).isTrue();
    }

    @Test
    @DisplayName("Should remove binary handlers when a channel is cleared")
    void shouldClearBinaryHandlers() {
        AtomicBoolean called = new AtomicBoolean();
        NetworkChannel channel = NetworkManager.channel("test-plugin")
                .onBinary("binary", (context, payload) -> called.set(true));

        channel.clear();
        NetworkManager.dispatchIncoming(
                "test-plugin", "binary", new NetworkContext(NetworkSide.SERVER, null, null),
                BinaryPacketCodec.encode(new byte[]{1}));

        assertThat(called).isFalse();
    }

    @Test
    @DisplayName("Should refuse binary sends when the client or server transport is unavailable")
    void shouldRefuseBinarySendsWithoutTransport() {
        boolean previousServer = GameServer.server;
        boolean previousClient = GameClient.client;
        try {
            GameServer.server = false;
            GameClient.client = false;

            byte[] payload = {1, 2, 3};
            NetworkChannel channel = NetworkManager.channel("test-plugin");
            assertThat(channel.sendToServer("binary", payload)).isFalse();
            assertThat(channel.sendToClient(null, "binary", payload)).isFalse();
            assertThat(channel.sendToPlayer(null, "binary", payload)).isFalse();
            assertThat(channel.broadcast("binary", payload)).isFalse();
            assertThat(channel.broadcast((UdpConnection) null, "binary", payload)).isFalse();
            assertThat(channel.broadcastRelative("binary", 1.0f, 2.0f, payload)).isFalse();
        } finally {
            GameServer.server = previousServer;
            GameClient.client = previousClient;
        }
    }

    @Test
    @DisplayName("Should reject null and oversized binary payloads without throwing from send methods")
    void shouldRejectInvalidBinaryPayloadsGracefully() {
        NetworkChannel channel = NetworkManager.channel("test-plugin");

        assertThat(channel.sendToServer("binary", (byte[]) null)).isFalse();
        assertThat(channel.sendToServer("binary", new byte[BinaryPacketCodec.MAX_PAYLOAD_BYTES + 1])).isFalse();
        assertThat(channel.broadcast("binary", new byte[BinaryPacketCodec.MAX_PAYLOAD_BYTES + 1])).isFalse();
        assertThat(channel.broadcastRelative(
                "binary", 1.0f, 2.0f, new byte[BinaryPacketCodec.MAX_PAYLOAD_BYTES + 1])).isFalse();
    }

    @Test
    @DisplayName("Should refuse sends when the client or server transport is unavailable")
    void shouldRefuseSendsWithoutTransport() {
        boolean previousServer = GameServer.server;
        boolean previousClient = GameClient.client;
        try {
            GameServer.server = false;
            GameClient.client = false;

            NetworkChannel channel = NetworkManager.channel("test-plugin");
            assertThat(channel.sendToServer("hello", Map.of("value", 1))).isFalse();
            assertThat(channel.sendToClient(null, "hello", Map.of("value", 1))).isFalse();
            assertThat(channel.sendToPlayer(null, "hello", Map.of("value", 1))).isFalse();
            assertThat(channel.broadcast("hello", Map.of("value", 1))).isFalse();
            assertThat(channel.broadcastRelative("hello", 1.0f, 2.0f, Map.of("value", 1))).isFalse();
        } finally {
            GameServer.server = previousServer;
            GameClient.client = previousClient;
        }
    }

    @Test
    @DisplayName("Should reject invalid payloads without throwing from send methods")
    void shouldRejectInvalidPayloadsGracefully() {
        NetworkChannel channel = NetworkManager.channel("test-plugin");

        assertThat(channel.sendToServer("hello", "odd")).isFalse();
        assertThat(channel.broadcast("hello", "odd")).isFalse();
        assertThat(channel.broadcastRelative("hello", 1.0f, 2.0f, "odd")).isFalse();
    }
}
