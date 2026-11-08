package com.avrix.api.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorldZombieUtilsTest {

    @Test
    void rejectsInvalidTimeValues() {
        assertThat(WorldEnvironmentUtils.setTime(0, 1, 1, 0, 0)).isFalse();
        assertThat(WorldEnvironmentUtils.setTime(2026, 0, 1, 0, 0)).isFalse();
        assertThat(WorldEnvironmentUtils.setTime(2026, 13, 1, 0, 0)).isFalse();
        assertThat(WorldEnvironmentUtils.setTime(2026, 1, 0, 0, 0)).isFalse();
        assertThat(WorldEnvironmentUtils.setTime(2026, 1, 32, 0, 0)).isFalse();
        assertThat(WorldEnvironmentUtils.setTime(2026, 1, 1, 24, 0)).isFalse();
        assertThat(WorldEnvironmentUtils.setTime(2026, 1, 1, 0, 60)).isFalse();
        assertThat(WorldEnvironmentUtils.setTime(null, 2026, 1, 1, 0, 0)).isFalse();
    }

    @Test
    void validatesZombiePopulationInputs() {
        assertThat(ZombiePopulationUtils.setMinPerChunk(-1.0f)).isFalse();
        assertThat(ZombiePopulationUtils.setMaxPerChunk(-1.0f)).isFalse();
        assertThat(ZombiePopulationUtils.createHorde(0, 0, 1, 1, 0)).isFalse();
        assertThat(ZombiePopulationUtils.createHordeInArea(0, 0, 0, 1, 1, 1, 1)).isFalse();
        assertThat(ZombiePopulationUtils.getLoadedZombies()).isNotNull();
    }
}
