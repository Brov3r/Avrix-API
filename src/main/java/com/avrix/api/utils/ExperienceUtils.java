package com.avrix.api.utils;

import com.avrix.api.permissions.PermissionsManager;
import zombie.characters.Capability;
import zombie.characters.IsoPlayer;
import zombie.characters.skills.PerkFactory;
import zombie.network.GameServer;

/**
 * Helpers for reading and changing player perk experience and levels.
 * <p>
 * Actor-aware overloads require the {@link Capability#AddXP} capability.
 */
public final class ExperienceUtils {

    private ExperienceUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Resolves a perk by its identifier or display name.
     *
     * @param name perk identifier or name
     * @return matching perk, or {@code null} when the name is unknown
     */
    public static PerkFactory.Perk getPerk(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }

        String cleanName = name.strip();
        PerkFactory.Perk perk = PerkFactory.getPerkFromName(cleanName);
        if (perk == null) {
            perk = PerkFactory.Perks.FromString(cleanName);
        }
        return perk == PerkFactory.Perks.None || perk == PerkFactory.Perks.MAX ? null : perk;
    }

    /**
     * Returns the current experience for a perk.
     *
     * @param player target player
     * @param perk   target perk
     * @return current experience, or {@code 0} when the player or perk is invalid
     */
    public static float getXp(IsoPlayer player, PerkFactory.Perk perk) {
        return player == null || perk == null ? 0.0f : player.getXp().getXP(perk);
    }

    /**
     * Returns the current level for a perk.
     *
     * @param player target player
     * @param perk   target perk
     * @return perk level, or {@code 0} when the player or perk is invalid
     */
    public static int getLevel(IsoPlayer player, PerkFactory.Perk perk) {
        return player == null || perk == null ? 0 : player.getPerkLevel(perk);
    }

    /**
     * Adds experience to a perk.
     *
     * @param player target player
     * @param perk   target perk
     * @param amount experience amount; may be negative to remove experience
     * @return {@code true} if the change was applied
     */
    public static boolean addXp(IsoPlayer player, PerkFactory.Perk perk, float amount) {
        if (player == null || perk == null || perk == PerkFactory.Perks.None
                || perk == PerkFactory.Perks.MAX || amount == 0.0f || player.isDead()) {
            return false;
        }

        if (GameServer.server && PlayerLookupUtils.getConnection(player).isPresent()) {
            GameServer.addXp(player, perk, amount, false, true);
        } else {
            player.getXp().AddXP(perk, amount, false, true, true);
        }
        return true;
    }

    /**
     * Adds experience to a perk after checking the actor's capability.
     *
     * @param actor  player performing the action
     * @param player target player
     * @param perk   target perk
     * @param amount experience amount
     * @return {@code true} if the actor is allowed and the change was applied
     */
    public static boolean addXp(IsoPlayer actor, IsoPlayer player, PerkFactory.Perk perk, float amount) {
        return PermissionsManager.hasCapability(actor, Capability.AddXP) && addXp(player, perk, amount);
    }

    /**
     * Removes experience from a perk.
     *
     * @param player target player
     * @param perk   target perk
     * @param amount positive experience amount to remove
     * @return {@code true} if the change was applied
     */
    public static boolean removeXp(IsoPlayer player, PerkFactory.Perk perk, float amount) {
        return amount > 0.0f && addXp(player, perk, -amount);
    }

    /**
     * Removes experience from a perk after checking the actor's capability.
     *
     * @param actor  player performing the action
     * @param player target player
     * @param perk   target perk
     * @param amount positive experience amount to remove
     * @return {@code true} if the actor is allowed and the change was applied
     */
    public static boolean removeXp(IsoPlayer actor, IsoPlayer player, PerkFactory.Perk perk, float amount) {
        return PermissionsManager.hasCapability(actor, Capability.AddXP) && removeXp(player, perk, amount);
    }

    /**
     * Sets the absolute experience for a perk.
     *
     * @param player target player
     * @param perk   target perk
     * @param xp     target experience
     * @return {@code true} if the value was applied
     */
    public static boolean setXp(IsoPlayer player, PerkFactory.Perk perk, float xp) {
        if (player == null || perk == null || xp < 0.0f) {
            return false;
        }
        float delta = xp - getXp(player, perk);
        return delta == 0.0f || addXp(player, perk, delta);
    }

    /**
     * Sets the absolute experience for a perk after checking the actor's capability.
     *
     * @param actor  player performing the action
     * @param player target player
     * @param perk   target perk
     * @param xp     target experience
     * @return {@code true} if the actor is allowed and the value was applied
     */
    public static boolean setXp(IsoPlayer actor, IsoPlayer player, PerkFactory.Perk perk, float xp) {
        return PermissionsManager.hasCapability(actor, Capability.AddXP) && setXp(player, perk, xp);
    }

    /**
     * Sets the level of a perk.
     *
     * @param player target player
     * @param perk   target perk
     * @param level  target level
     * @return {@code true} if the value was applied
     */
    public static boolean setLevel(IsoPlayer player, PerkFactory.Perk perk, int level) {
        if (player == null || perk == null || perk == PerkFactory.Perks.None
                || perk == PerkFactory.Perks.MAX || level < 0) {
            return false;
        }
        player.setPerkLevelDebug(perk, level);
        return true;
    }

    /**
     * Sets the level of a perk after checking the actor's capability.
     *
     * @param actor  player performing the action
     * @param player target player
     * @param perk   target perk
     * @param level  target level
     * @return {@code true} if the actor is allowed and the value was applied
     */
    public static boolean setLevel(IsoPlayer actor, IsoPlayer player, PerkFactory.Perk perk, int level) {
        return PermissionsManager.hasCapability(actor, Capability.AddXP) && setLevel(player, perk, level);
    }
}
