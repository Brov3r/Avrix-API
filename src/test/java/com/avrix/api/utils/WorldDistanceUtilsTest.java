package com.avrix.api.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zombie.characters.IsoPlayer;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.network.GameServer;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorldDistanceUtilsTest {

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
    void calculatesCoordinateDistances() {
        assertThat(WorldDistanceUtils.distance2D(0.0f, 0.0f, 3.0f, 4.0f)).isEqualTo(5.0f);
        assertThat(WorldDistanceUtils.distance3D(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 5.0f)).isEqualTo(5.0f);
        assertThat(WorldDistanceUtils.distanceSquared2D(0.0f, 0.0f, 3.0f, 4.0f)).isEqualTo(25.0f);
        assertThat(WorldDistanceUtils.distanceSquared3D(0.0f, 0.0f, 0.0f, 3.0f, 4.0f, 0.0f)).isEqualTo(25.0f);
        assertThat(WorldDistanceUtils.distanceManhattan2D(0.0f, 0.0f, 3.0f, 4.0f)).isEqualTo(7.0f);
    }

    @Test
    void calculatesEntityDistances() {
        IsoObject first = object(0.0f, 0.0f, 0.0f);
        IsoObject second = object(3.0f, 4.0f, 0.0f);
        IsoObject elevated = object(0.0f, 0.0f, 5.0f);
        IsoGridSquare square = square(3, 4, 0);

        assertThat(WorldDistanceUtils.distance2D(first, second)).isEqualTo(5.0f);
        assertThat(WorldDistanceUtils.distanceSquared2D(first, second)).isEqualTo(25.0f);
        assertThat(WorldDistanceUtils.distance3D(first, elevated)).isEqualTo(5.0f);
        assertThat(WorldDistanceUtils.distance2D(first, square)).isEqualTo(5.0f);
        assertThat(WorldDistanceUtils.distance2D(square, square(6, 8, 0))).isEqualTo(5.0f);
        assertThatThrownBy(() -> WorldDistanceUtils.distance2D(first, (IsoObject) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void checksRadiusBoundaries() {
        IsoObject center = object(0.0f, 0.0f, 0.0f);
        IsoObject edge = object(3.0f, 4.0f, 0.0f);

        assertThat(WorldDistanceUtils.isWithinRadius(center, edge, 5.0f)).isTrue();
        assertThat(WorldDistanceUtils.isWithinRadius(center, edge, 4.99f)).isFalse();
        assertThat(WorldDistanceUtils.isWithinRadius(0.0f, 0.0f, 3.0f, 4.0f, 5.0f)).isTrue();
        assertThat(WorldDistanceUtils.isWithinRadius(center, null, 5.0f)).isFalse();
        assertThat(WorldDistanceUtils.isWithinRadius(center, edge, -1.0f)).isFalse();
    }

    @Test
    void filtersAndSortsCandidatesByRadius() {
        IsoObject center = object(0.0f, 0.0f, 0.0f);
        IsoObject near = object(1.0f, 0.0f, 0.0f);
        IsoObject middle = object(3.0f, 4.0f, 0.0f);
        IsoObject far = object(10.0f, 0.0f, 0.0f);

        List<IsoObject> within = WorldDistanceUtils.getWithinRadius(center, List.of(far, middle, near), 6.0f);

        assertThat(within).containsExactly(near, middle);
        assertThat(WorldDistanceUtils.sortByDistance(center, List.of(far, middle, near)))
                .containsExactly(near, middle, far);
        assertThat(WorldDistanceUtils.getClosest(center, List.of(far, middle, near))).contains(near);
        assertThat(WorldDistanceUtils.getClosest(center, List.of(far, middle), 4.0f)).isEmpty();
    }

    @Test
    void queriesOnlinePlayersByRadius() {
        IsoPlayer nearPlayer = player(1.0f, 0.0f);
        IsoPlayer middlePlayer = player(5.0f, 0.0f);
        IsoPlayer farPlayer = player(10.0f, 0.0f);
        GameServer.Players.addAll(List.of(farPlayer, middlePlayer, nearPlayer));

        IsoObject center = object(0.0f, 0.0f, 0.0f);

        assertThat(WorldDistanceUtils.getPlayersWithinRadius(center, 6.0f))
                .containsExactly(nearPlayer, middlePlayer);
        assertThat(WorldDistanceUtils.getPlayersWithinRadius(0.0f, 0.0f, 0.5f)).isEmpty();
        assertThat(WorldDistanceUtils.getClosestPlayer(center)).contains(nearPlayer);
        assertThat(WorldDistanceUtils.getClosestPlayer(center, 0.5f)).isEmpty();
    }

    private static IsoObject object(float x, float y, float z) {
        IsoObject object = mock(IsoObject.class);
        when(object.getX()).thenReturn(x);
        when(object.getY()).thenReturn(y);
        when(object.getZ()).thenReturn(z);
        return object;
    }

    private static IsoGridSquare square(int x, int y, int z) {
        IsoGridSquare square = mock(IsoGridSquare.class);
        when(square.getX()).thenReturn(x);
        when(square.getY()).thenReturn(y);
        when(square.getZ()).thenReturn(z);
        return square;
    }

    private static IsoPlayer player(float x, float y) {
        IsoPlayer player = mock(IsoPlayer.class);
        when(player.getX()).thenReturn(x);
        when(player.getY()).thenReturn(y);
        when(player.getZ()).thenReturn(0.0f);
        return player;
    }
}
