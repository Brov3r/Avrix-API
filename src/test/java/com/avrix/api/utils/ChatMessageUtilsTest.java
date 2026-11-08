package com.avrix.api.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zombie.network.GameServer;

import static org.assertj.core.api.Assertions.assertThat;

class ChatMessageUtilsTest {

    private boolean previousServer;

    @BeforeEach
    void setUp() {
        previousServer = GameServer.server;
        GameServer.server = false;
    }

    @AfterEach
    void tearDown() {
        GameServer.server = previousServer;
    }

    @Test
    void reportsChatUnavailableOutsideTheServer() {
        assertThat(ChatMessageUtils.isAvailable()).isFalse();
        assertThat(ChatMessageUtils.broadcast("hello")).isFalse();
        assertThat(ChatMessageUtils.broadcastAlert("hello")).isFalse();
        assertThat(ChatMessageUtils.broadcastAlert("Avrix", "hello")).isFalse();
        assertThat(ChatMessageUtils.sendAdminMessage("hello")).isFalse();
        assertThat(ChatMessageUtils.sendMessage((zombie.characters.IsoPlayer) null, "hello")).isFalse();
        assertThat(ChatMessageUtils.sendMessage((zombie.core.raknet.UdpConnection) null, "hello")).isFalse();
        assertThat(ChatMessageUtils.broadcast((zombie.characters.IsoPlayer) null, "hello")).isFalse();
        assertThat(ChatMessageUtils.sendAdminMessage((zombie.characters.IsoPlayer) null, "hello")).isFalse();
    }

    @Test
    void rejectsBlankMessagesAndAuthors() {
        assertThat(ChatMessageUtils.broadcast(" ")).isFalse();
        assertThat(ChatMessageUtils.broadcast(null)).isFalse();
        assertThat(ChatMessageUtils.broadcastAlert("", "hello")).isFalse();
    }
}
