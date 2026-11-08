package com.avrix.api.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zombie.characters.IsoPlayer;
import zombie.network.GameServer;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TemporaryModerationUtilsTest {

    private boolean previousServer;
    private List<IsoPlayer> previousPlayers;

    @BeforeEach
    void setUp() {
        previousServer = GameServer.server;
        previousPlayers = new ArrayList<>(GameServer.Players);
        GameServer.Players.clear();
    }

    @AfterEach
    void tearDown() {
        GameServer.server = previousServer;
        GameServer.Players.clear();
        GameServer.Players.addAll(previousPlayers);
    }

    @Test
    void validatesDurationsAndTargets() {
        assertThat(TemporaryModerationUtils.banPlayer("user", "reason", Duration.ZERO).success()).isFalse();
        assertThat(TemporaryModerationUtils.banPlayer(" ", "reason", Duration.ofMinutes(1)).success()).isFalse();
        assertThat(TemporaryModerationUtils.banPlayer(null, "reason", Duration.ofMinutes(1)).success()).isFalse();
        assertThat(TemporaryModerationUtils.banPlayer((IsoPlayer) null, "user", "reason", Duration.ofMinutes(1))
                .success()).isFalse();

        assertThat(TemporaryModerationUtils.banSteamId(" ", "reason", Duration.ofMinutes(1)).success()).isFalse();
        assertThat(TemporaryModerationUtils.banIp("", "reason", Duration.ofMinutes(1)).success()).isFalse();

        assertThat(TemporaryModerationUtils.mutePlayer((IsoPlayer) null, Duration.ofMinutes(1))).isFalse();
        assertThat(TemporaryModerationUtils.mutePlayer((IsoPlayer) null, null, Duration.ofMinutes(1))).isFalse();
        assertThat(TemporaryModerationUtils.unmutePlayer((IsoPlayer) null)).isFalse();
        assertThat(TemporaryModerationUtils.unmutePlayer((String) null)).isFalse();
    }

    @Test
    void expiresTemporaryMuteOnTheNextTick() {
        GameServer.server = true;
        IsoPlayer player = mock(IsoPlayer.class);
        when(player.getUsername()).thenReturn("MutedUser");
        GameServer.Players.add(player);

        assertThat(TemporaryModerationUtils.mutePlayer(player, Duration.ofHours(1))).isTrue();
        assertThat(TemporaryModerationUtils.isTemporarilyMuted("MutedUser")).isTrue();
        verify(player).setAllChatMuted(true);

        TemporaryModerationUtils.enqueueExpired(Instant.now().plus(Duration.ofHours(2)));
        TemporaryModerationUtils.processPendingTasks();

        verify(player).setAllChatMuted(false);
        assertThat(TemporaryModerationUtils.isTemporarilyMuted("MutedUser")).isFalse();
        assertThat(TemporaryModerationUtils.getMuteExpiration("MutedUser")).isEmpty();
    }

    @Test
    void reportsMissingTrackingEntries() {
        assertThat(TemporaryModerationUtils.getBanExpiration("")).isEmpty();
        assertThat(TemporaryModerationUtils.getMuteExpiration(null)).isEmpty();
        assertThat(TemporaryModerationUtils.getSteamBanExpiration(" ")).isEmpty();
        assertThat(TemporaryModerationUtils.getIpBanExpiration(null)).isEmpty();
        assertThat(TemporaryModerationUtils.isTemporarilyBanned(" ")).isFalse();
        assertThat(TemporaryModerationUtils.isTemporarilyMuted(null)).isFalse();
        assertThat(TemporaryModerationUtils.isTemporarilySteamBanned(" ")).isFalse();
        assertThat(TemporaryModerationUtils.isTemporarilyIpBanned(null)).isFalse();
    }
}
