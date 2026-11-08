package com.avrix.api.utils;

import org.junit.jupiter.api.Test;
import zombie.iso.IsoObject;

import static org.assertj.core.api.Assertions.assertThat;

class TeleportVisibilityUtilsTest {

    @Test
    void rejectsTeleportsWithoutTargets() {
        assertThat(TeleportUtils.teleportPlayer(null, 0.0f, 0.0f, 0.0f)).isFalse();
        assertThat(TeleportUtils.teleportPlayer(null, null)).isFalse();
        assertThat(TeleportUtils.teleportPlayerToPlayer(null, null)).isFalse();
        assertThat(TeleportUtils.teleportObject((IsoObject) null, 0.0f, 0.0f, 0.0f)).isFalse();
        assertThat(TeleportUtils.teleportObject((IsoObject) null, null)).isFalse();
        assertThat(TeleportUtils.teleportPlayer(null, null, 0.0f, 0.0f, 0.0f)).isFalse();
        assertThat(TeleportUtils.teleportObject(null, (IsoObject) null, 0.0f, 0.0f, 0.0f)).isFalse();
    }

    @Test
    void rejectsVisibilityQueriesWithoutTargets() {
        assertThat(VisibilityUtils.hasLineOfSight(null, null)).isFalse();
        assertThat(VisibilityUtils.raycast(null, null)).isEmpty();
    }
}
