package com.avrix.api.utils;

import com.avrix.api.permissions.ExtendedRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zombie.characters.Capability;
import zombie.characters.IsoPlayer;
import zombie.characters.Role;
import zombie.network.GameServer;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlayerLookupUtilsTest {

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
    void returnsEmptySnapshotWhenServerIsNotRunning() {
        GameServer.Players.add(player("Online", 0L, (short) 1, null));
        GameServer.server = false;

        assertThat(PlayerLookupUtils.getOnlinePlayers()).isEmpty();
        assertThat(PlayerLookupUtils.getOnlinePlayerCount()).isZero();
        assertThat(PlayerLookupUtils.getPlayerByUsername("Online")).isEmpty();
    }

    @Test
    void returnsImmutableSnapshotWithoutNullEntries() {
        IsoPlayer online = player("Online", 0L, (short) 1, null);
        GameServer.Players.add(online);
        GameServer.Players.add(null);

        List<IsoPlayer> players = PlayerLookupUtils.getOnlinePlayers();

        assertThat(players).containsExactly(online);
        assertThat(players).isUnmodifiable();
        assertThat(PlayerLookupUtils.getOnlinePlayerCount()).isEqualTo(1);
    }

    @Test
    void findsPlayerByUsernameCaseInsensitively() {
        IsoPlayer online = player("John Survivor", 0L, (short) 1, null);
        GameServer.Players.add(online);

        assertThat(PlayerLookupUtils.getPlayerByUsername(" john survivor ")).contains(online);
        assertThat(PlayerLookupUtils.getPlayerByUsername("missing")).isEmpty();
        assertThat(PlayerLookupUtils.getPlayerByUsername(" ")).isEmpty();
        assertThat(PlayerLookupUtils.getPlayerByUsername(null)).isEmpty();
    }

    @Test
    void findsPlayersByUsernamePart() {
        IsoPlayer first = player("Alice", 0L, (short) 1, null);
        IsoPlayer second = player("Alicia", 0L, (short) 2, null);
        IsoPlayer third = player("Bob", 0L, (short) 3, null);
        GameServer.Players.addAll(List.of(first, second, third));

        assertThat(PlayerLookupUtils.getPlayersByUsernamePart("ali")).containsExactly(first, second);
        assertThat(PlayerLookupUtils.getPlayerByUsernamePart("BOB")).contains(third);
        assertThat(PlayerLookupUtils.getPlayersByUsernamePart("")).isEmpty();
    }

    @Test
    void findsPlayerByOnlineIdAndSteamId() {
        IsoPlayer online = player("Identified", 76561198000000001L, (short) 42, null);
        GameServer.Players.add(online);

        assertThat(PlayerLookupUtils.getPlayerByOnlineId((short) 42)).contains(online);
        assertThat(PlayerLookupUtils.getPlayerByOnlineId((short) 7)).isEmpty();
        assertThat(PlayerLookupUtils.getPlayerBySteamId(76561198000000001L)).contains(online);
        assertThat(PlayerLookupUtils.getPlayerBySteamId("76561198000000001")).contains(online);
        assertThat(PlayerLookupUtils.getPlayerBySteamId("not-a-number")).isEmpty();
        assertThat(PlayerLookupUtils.getPlayerBySteamId("")).isEmpty();
        assertThat(PlayerLookupUtils.getSteamId(online)).contains("76561198000000001");
    }

    @Test
    void findsPlayersByRole() {
        Role admin = new Role("admin");
        Role user = new Role("user");
        IsoPlayer administrator = player("AdminUser", 0L, (short) 1, admin);
        IsoPlayer survivor = player("Survivor", 0L, (short) 2, user);
        IsoPlayer roleless = player("Roleless", 0L, (short) 3, null);
        GameServer.Players.addAll(List.of(administrator, survivor, roleless));

        assertThat(PlayerLookupUtils.getPlayersByRole("ADMIN")).containsExactly(administrator);
        assertThat(PlayerLookupUtils.getPlayersByRole(admin)).containsExactly(administrator);
        assertThat(PlayerLookupUtils.getPlayersByRole("missing")).isEmpty();
        assertThat(PlayerLookupUtils.getPlayersByRole((Role) null)).isEmpty();
    }

    @Test
    void findsPlayersByCapabilityAndPermission() {
        Role capable = new Role("capable");
        capable.addCapability(Capability.AddItem);
        ExtendedRole privileged = new ExtendedRole("privileged");
        privileged.addPermission("avrix.test.node");

        IsoPlayer capablePlayer = player("Capable", 0L, (short) 1, capable);
        IsoPlayer privilegedPlayer = player("Privileged", 0L, (short) 2, privileged);
        IsoPlayer regularPlayer = player("Regular", 0L, (short) 3, new Role("user"));
        GameServer.Players.addAll(List.of(capablePlayer, privilegedPlayer, regularPlayer));

        assertThat(PlayerLookupUtils.getPlayersWithCapability(Capability.AddItem)).containsExactly(capablePlayer);
        assertThat(PlayerLookupUtils.getPlayersWithCapability(Capability.None)).isEmpty();
        assertThat(PlayerLookupUtils.getPlayersWithCapability(null)).isEmpty();
        assertThat(PlayerLookupUtils.getPlayersWithPermission("avrix.test.node")).containsExactly(privilegedPlayer);
        assertThat(PlayerLookupUtils.getPlayersWithPermission("")).isEmpty();
    }

    @Test
    void identifiesOnlinePlayers() {
        IsoPlayer online = player("Online", 0L, (short) 1, null);
        GameServer.Players.add(online);

        assertThat(PlayerLookupUtils.isOnline(online)).isTrue();
        assertThat(PlayerLookupUtils.isOnline(player("Online", 0L, (short) 9, null))).isTrue();
        assertThat(PlayerLookupUtils.isOnline(player("Offline", 0L, (short) 9, null))).isFalse();
        assertThat(PlayerLookupUtils.isOnline(null)).isFalse();
    }

    private static IsoPlayer player(String username, long steamId, short onlineId, Role role) {
        IsoPlayer player = mock(IsoPlayer.class);
        when(player.getUsername()).thenReturn(username);
        when(player.getSteamID()).thenReturn(steamId);
        when(player.getOnlineID()).thenReturn(onlineId);
        when(player.getRole()).thenReturn(role);
        return player;
    }
}
