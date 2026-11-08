package com.avrix.api.utils;

import org.junit.jupiter.api.Test;
import zombie.characters.skills.PerkFactory;

import static org.assertj.core.api.Assertions.assertThat;

class ItemExperienceUtilsTest {

    @Test
    void rejectsInvalidItemOperations() {
        assertThat(ItemUtils.create(" ")).isNull();
        assertThat(ItemUtils.addItem((zombie.characters.IsoPlayer) null, "Base.Axe")).isNull();
        assertThat(ItemUtils.addItem((zombie.characters.IsoPlayer) null, "Base.Axe", 2)).isEmpty();
        assertThat(ItemUtils.addItem((zombie.characters.IsoPlayer) null, "Base.Axe", 0)).isEmpty();
        assertThat(ItemUtils.removeItem((zombie.characters.IsoPlayer) null, "Base.Axe", 1)).isZero();
        assertThat(ItemUtils.countItem((zombie.characters.IsoPlayer) null, "Base.Axe")).isZero();
        assertThat(ItemUtils.hasItem(null, "Base.Axe", 1)).isFalse();
        assertThat(ItemUtils.getItem(null, "Base.Axe")).isNull();
    }

    @Test
    void resolvesPerksAndRejectsInvalidXpChanges() {
        assertThat(ExperienceUtils.getPerk(null)).isNull();
        assertThat(ExperienceUtils.getPerk(" ")).isNull();
        assertThat(ExperienceUtils.getPerk("Aiming")).isNotNull();
        assertThat(ExperienceUtils.addXp(null, PerkFactory.Perks.Aiming, 1.0f)).isFalse();
        assertThat(ExperienceUtils.addXp(null, null, 1.0f)).isFalse();
        assertThat(ExperienceUtils.removeXp(null, PerkFactory.Perks.Aiming, 1.0f)).isFalse();
        assertThat(ExperienceUtils.setXp(null, PerkFactory.Perks.Aiming, 10.0f)).isFalse();
        assertThat(ExperienceUtils.setLevel(null, PerkFactory.Perks.Aiming, 2)).isFalse();
        assertThat(ExperienceUtils.getXp(null, PerkFactory.Perks.Aiming)).isZero();
        assertThat(ExperienceUtils.getLevel(null, PerkFactory.Perks.Aiming)).isZero();
    }
}
