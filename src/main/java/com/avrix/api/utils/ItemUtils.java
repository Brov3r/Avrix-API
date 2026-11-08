package com.avrix.api.utils;

import com.avrix.api.permissions.PermissionsManager;
import zombie.characters.Capability;
import zombie.characters.IsoPlayer;
import zombie.inventory.InventoryItem;
import zombie.inventory.InventoryItemFactory;
import zombie.inventory.ItemContainer;

import java.util.ArrayList;
import java.util.List;

/**
 * Helpers for creating items and moving them in or out of player inventories and containers.
 * <p>
 * Actor-aware overloads require the {@link Capability#AddItem} capability.
 */
public final class ItemUtils {

    private ItemUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Creates a new item by its full type name.
     *
     * @param itemType item type, for example {@code Base.Axe}
     * @return created item, or {@code null} when the type is unknown
     */
    public static InventoryItem create(String itemType) {
        if (itemType == null || itemType.isBlank()) {
            return null;
        }
        return InventoryItemFactory.CreateItem(itemType.strip());
    }

    /**
     * Adds a single item to a player's inventory.
     *
     * @param player   target player
     * @param itemType item type
     * @return added item, or {@code null} when it could not be created or added
     */
    public static InventoryItem addItem(IsoPlayer player, String itemType) {
        if (player == null) {
            return null;
        }
        ItemContainer inventory = player.getInventory();
        return inventory == null ? null : inventory.AddItem(itemType);
    }

    /**
     * Adds items to a player's inventory.
     *
     * @param player   target player
     * @param itemType item type
     * @param count    number of items
     * @return added items, or an empty list
     */
    public static List<InventoryItem> addItem(IsoPlayer player, String itemType, int count) {
        return addItem(player == null ? null : player.getInventory(), itemType, count);
    }

    /**
     * Adds a single item to a player's inventory after checking the actor's capability.
     *
     * @param actor    player performing the action
     * @param player   target player
     * @param itemType item type
     * @return added item, or {@code null} when the actor is not allowed
     */
    public static InventoryItem addItem(IsoPlayer actor, IsoPlayer player, String itemType) {
        return PermissionsManager.hasCapability(actor, Capability.AddItem) ? addItem(player, itemType) : null;
    }

    /**
     * Adds items to a player's inventory after checking the actor's capability.
     *
     * @param actor    player performing the action
     * @param player   target player
     * @param itemType item type
     * @param count    number of items
     * @return added items, or an empty list when the actor is not allowed
     */
    public static List<InventoryItem> addItem(IsoPlayer actor, IsoPlayer player, String itemType, int count) {
        return PermissionsManager.hasCapability(actor, Capability.AddItem)
                ? addItem(player, itemType, count)
                : List.of();
    }

    /**
     * Adds a single item to a container.
     *
     * @param container target container
     * @param itemType  item type
     * @return added item, or {@code null} when it could not be added
     */
    public static InventoryItem addItem(ItemContainer container, String itemType) {
        return container == null ? null : container.AddItem(itemType);
    }

    /**
     * Adds items to a container.
     *
     * @param container target container
     * @param itemType  item type
     * @param count     number of items
     * @return added items, or an empty list
     */
    public static List<InventoryItem> addItem(ItemContainer container, String itemType, int count) {
        if (container == null || itemType == null || itemType.isBlank() || count <= 0) {
            return List.of();
        }
        ArrayList<InventoryItem> added = container.AddItems(itemType.strip(), count);
        return added == null ? List.of() : List.copyOf(added);
    }

    /**
     * Removes items of a type from a player's inventory.
     *
     * @param player   target player
     * @param itemType item type
     * @param count    number of items, or a non-positive value to remove all matching items
     * @return number of removed items
     */
    public static int removeItem(IsoPlayer player, String itemType, int count) {
        return removeItem(player == null ? null : player.getInventory(), itemType, count);
    }

    /**
     * Removes items of a type from a player's inventory after checking the actor's capability.
     *
     * @param actor    player performing the action
     * @param player   target player
     * @param itemType item type
     * @param count    number of items, or a non-positive value to remove all matching items
     * @return number of removed items, or {@code 0} when the actor is not allowed
     */
    public static int removeItem(IsoPlayer actor, IsoPlayer player, String itemType, int count) {
        return PermissionsManager.hasCapability(actor, Capability.AddItem)
                ? removeItem(player, itemType, count)
                : 0;
    }

    /**
     * Removes items of a type from a container.
     *
     * @param container target container
     * @param itemType  item type
     * @param count     number of items, or a non-positive value to remove all matching items
     * @return number of removed items
     */
    public static int removeItem(ItemContainer container, String itemType, int count) {
        if (container == null || itemType == null || itemType.isBlank()) {
            return 0;
        }

        ArrayList<InventoryItem> removed = count > 0
                ? container.RemoveAll(itemType.strip(), count)
                : container.RemoveAll(itemType.strip());
        return removed == null ? 0 : removed.size();
    }

    /**
     * Counts items of a type in a player's inventory.
     *
     * @param player   target player
     * @param itemType item type
     * @return item count
     */
    public static int countItem(IsoPlayer player, String itemType) {
        return countItem(player == null ? null : player.getInventory(), itemType);
    }

    /**
     * Counts items of a type in a container.
     *
     * @param container target container
     * @param itemType  item type
     * @return item count
     */
    public static int countItem(ItemContainer container, String itemType) {
        return container == null || itemType == null || itemType.isBlank()
                ? 0
                : container.getItemCount(itemType.strip());
    }

    /**
     * Checks whether a player has at least the given number of items.
     *
     * @param player   target player
     * @param itemType item type
     * @param count    required amount
     * @return {@code true} if the player has enough items
     */
    public static boolean hasItem(IsoPlayer player, String itemType, int count) {
        return count > 0 && countItem(player, itemType) >= count;
    }

    /**
     * Finds the first item of a type in a player's inventory.
     *
     * @param player   target player
     * @param itemType item type
     * @return matching item, or {@code null}
     */
    public static InventoryItem getItem(IsoPlayer player, String itemType) {
        ItemContainer inventory = player == null ? null : player.getInventory();
        return inventory == null || itemType == null || itemType.isBlank()
                ? null
                : inventory.getItemFromType(itemType.strip());
    }
}
