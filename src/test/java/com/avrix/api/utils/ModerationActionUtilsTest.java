package com.avrix.api.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zombie.characters.IsoPlayer;
import zombie.network.GameServer;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModerationActionUtilsTest {

    private boolean previousServer;
    private List<IsoPlayer> previousPlayers;

    @BeforeEach
    void setUp() {
        previousServer = GameServer.server;
        previousPlayers = new ArrayList<>(GameServer.Players);
        GameServer.Players.clear();
        GameServer.server = true;
    }

    @AfterEach
    void tearDown() {
        GameServer.server = previousServer;
        GameServer.Players.clear();
        GameServer.Players.addAll(previousPlayers);
    }

    @Test
    void rejectsKicksForMissingPlayers() {
        assertThat(ModerationActionUtils.kickPlayer((IsoPlayer) null, "reason")).isFalse();
        assertThat(ModerationActionUtils.kickPlayer("Missing", "reason")).isFalse();
        assertThat(ModerationActionUtils.kickPlayer(null, (IsoPlayer) null, "reason")).isFalse();
        assertThat(ModerationActionUtils.banPlayer(null, "user", "reason").success()).isFalse();
    }

    @Test
    void togglesPlayerChatMute() {
        IsoPlayer player = mock(IsoPlayer.class);
        when(player.isAllChatMuted()).thenReturn(false);

        assertThat(ModerationActionUtils.setMuted(player, true)).isTrue();
        verify(player).setAllChatMuted(true);

        assertThat(ModerationActionUtils.mutePlayer(null)).isFalse();
        assertThat(ModerationActionUtils.unmutePlayer(null)).isFalse();
    }

    @Test
    void readsMuteState() {
        IsoPlayer muted = mock(IsoPlayer.class);
        when(muted.isAllChatMuted()).thenReturn(true);

        assertThat(ModerationActionUtils.isMuted(muted)).isTrue();
        assertThat(ModerationActionUtils.isMuted((IsoPlayer) null)).isFalse();
        assertThat(ModerationActionUtils.isMuted((zombie.core.raknet.UdpConnection) null)).isFalse();
    }

    @Test
    void rejectsBlankBanIdentifiers() {
        assertThat(ModerationActionUtils.banPlayer(" ", "reason").success()).isFalse();
        assertThat(ModerationActionUtils.unbanPlayer(null).success()).isFalse();
        assertThat(ModerationActionUtils.banSteamId("", "reason").success()).isFalse();
        assertThat(ModerationActionUtils.banIp(null, "reason").success()).isFalse();
    }
}
