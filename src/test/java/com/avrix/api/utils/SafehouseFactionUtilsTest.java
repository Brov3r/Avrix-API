package com.avrix.api.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SafehouseFactionUtilsTest {

    @Test
    void rejectsInvalidSafehouseQueries() {
        assertThat(SafehouseUtils.getByOwner(null)).isNull();
        assertThat(SafehouseUtils.getByOwner(" ")).isNull();
        assertThat(SafehouseUtils.getByTitle(null)).isNull();
        assertThat(SafehouseUtils.getAt((zombie.iso.IsoGridSquare) null)).isNull();
        assertThat(SafehouseUtils.getForPlayer(null)).isNull();
        assertThat(SafehouseUtils.create(null, 0, 0, 1, 1, "test")).isNull();
        assertThat(SafehouseUtils.remove((zombie.iso.areas.SafeHouse) null)).isFalse();
        assertThat(SafehouseUtils.addMember(null, "user")).isFalse();
        assertThat(SafehouseUtils.removeMember(null, "user")).isFalse();
        assertThat(SafehouseUtils.kickMember(null, "user")).isFalse();
        assertThat(SafehouseUtils.isMember(null, "user")).isFalse();
        assertThat(SafehouseUtils.isAllowed(null, null)).isFalse();
        assertThat(SafehouseUtils.canClaim(null)).isFalse();
        assertThat(SafehouseUtils.getClaimRejection(null, null)).isNotBlank();
    }

    @Test
    void rejectsInvalidFactionOperations() {
        assertThat(FactionUtils.getByName(null)).isNull();
        assertThat(FactionUtils.getByName(" ")).isNull();
        assertThat(FactionUtils.getForPlayer((zombie.characters.IsoPlayer) null)).isNull();
        assertThat(FactionUtils.getForPlayer((String) null)).isNull();
        assertThat(FactionUtils.create(null, null)).isNull();
        assertThat(FactionUtils.create("ab", "owner")).isNull();
        assertThat(FactionUtils.addMember(null, "user")).isFalse();
        assertThat(FactionUtils.removeMember(null, "user")).isFalse();
        assertThat(FactionUtils.isMember(null, "user")).isFalse();
        assertThat(FactionUtils.areInSameFaction(null, null)).isFalse();
        assertThat(FactionUtils.setTag(null, "TAG")).isFalse();
        assertThat(FactionUtils.getFactions()).isNotNull();
    }
}
