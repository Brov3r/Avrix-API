package com.avrix.api.events;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Registry of standard Project Zomboid Lua events with signature documentation.
 * <p>
 * Implements {@link Event} for seamless integration with the Avrix event dispatch pipeline.
 */
public enum DefaultEvents implements Event {

    /**
     * OnGameBoot: Triggered after the game finishes starting up. Note: For clients, lua files in lua/server/ will not have ran by the time this event is triggered, so event callbacks added by those files will not be triggered. This does not apply to servers.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_GAME_BOOT("OnGameBoot"),

    /**
     * Triggered by Project Zomboid Lua event {@code "OnPreGameStart"}.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_PRE_GAME_START("OnPreGameStart"),

    /**
     * OnTick: Triggered every game tick.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code number tick} — The number of ticks since the game started.</li>
     * </ul>
     */
    ON_TICK("OnTick"),

    /**
     * OnTickEvenPaused: Triggered every game tick, even if the game is paused.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code number tick} — The number of ticks since the game started. Always zero while paused.</li>
     * </ul>
     */
    ON_TICK_EVEN_PAUSED("OnTickEvenPaused"),

    /**
     * Triggered by Project Zomboid Lua event {@code "OnRenderUpdate"}.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_RENDER_UPDATE("OnRenderUpdate"),

    /**
     * (Client) OnFETick: Triggered every tick while on the main menu.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code 0 unknown} — Purpose unknown: always 0.</li>
     * </ul>
     */
    ON_FE_TICK("OnFETick"),

    /**
     * (Client) OnGameStart: Triggered upon finishing loading and entering the game.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_GAME_START("OnGameStart"),

    /**
     * (Client) OnPreUIDraw: Triggered before every UI render frame
     * <p><b>Parameters:</b> None.</p>
     */
    ON_PRE_UI_DRAW("OnPreUIDraw"),

    /**
     * (Client) OnPostUIDraw: Triggered after every UI render frame
     * <p><b>Parameters:</b> None.</p>
     */
    ON_POST_UI_DRAW("OnPostUIDraw"),

    /**
     * OnCharacterCollide: Triggered when a non-zombie character collides into another (possibly zombie) character.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code IsoGameCharacter character} — The character colliding into the other character.</li>
     *   <li>{@code IsoGameCharacter collidedCharacter} — The character being collided into.</li>
     * </ul>
     */
    ON_CHARACTER_COLLIDE("OnCharacterCollide"),

    /**
     * (Client) OnKeyStartPressed: Triggered when a key starts being pressed.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code integer key} — Key code of the key that was pressed.</li>
     * </ul>
     */
    ON_KEY_START_PRESSED("OnKeyStartPressed"),

    /**
     * (Client) OnKeyPressed: Triggered when a key is released.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code integer key} — Key code of the key that was released.</li>
     * </ul>
     */
    ON_KEY_PRESSED("OnKeyPressed"),

    /**
     * (Client) OnContextKey: Triggered while the player is holding the context key.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code IsoPlayer player} — The player pressing the context key.</li>
     *   <li>{@code number timeMs} — How long, in milliseconds, the context key has been held for.</li>
     * </ul>
     */
    ON_CONTEXT_KEY("OnContextKey"),

    /**
     * OnObjectCollide: Triggered when two objects collide with each other.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code IsoMovingObject object} — The object that collided into the other object.</li>
     *   <li>{@code IsoObject collided} — The object that was collided into.</li>
     * </ul>
     */
    ON_OBJECT_COLLIDE("OnObjectCollide"),

    /**
     * Triggered by Project Zomboid Lua event {@code "OnNPCSurvivorUpdate"}.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_NPC_SURVIVOR_UPDATE("OnNPCSurvivorUpdate"),

    /**
     * (Client) OnPlayerUpdate: Triggered during each local player's update (every tick).
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code IsoPlayer player} — The player being updated.</li>
     * </ul>
     */
    ON_PLAYER_UPDATE("OnPlayerUpdate"),

    /**
     * (Client) OnZombieUpdate: Triggered whenever a zombie updates.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code IsoZombie zombie} — The zombie being updated.</li>
     * </ul>
     */
    ON_ZOMBIE_UPDATE("OnZombieUpdate"),

    /**
     * OnZombieCreate: Triggered when a zombie is being spawned.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code IsoZombie zombie} — The zombie being spawned.</li>
     * </ul>
     */
    ON_ZOMBIE_CREATE("OnZombieCreate"),

    /**
     * OnTriggerNPCEvent: Triggered when the player triggers an NPC event.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code string type}</li>
     *   <li>{@code table data}</li>
     *   <li>{@code BuildingDef def}</li>
     * </ul>
     */
    ON_TRIGGER_NPC_EVENT("OnTriggerNPCEvent"),

    /**
     * OnMultiTriggerNPCEvent: Triggered when the player triggers an NPC event.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code string type}</li>
     *   <li>{@code table data}</li>
     *   <li>{@code BuildingDef def}</li>
     * </ul>
     */
    ON_MULTI_TRIGGER_NPC_EVENT("OnMultiTriggerNPCEvent"),

    /**
     * OnLoadMapZones: Triggered before loading the map zones.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_LOAD_MAP_ZONES("OnLoadMapZones"),

    /**
     * OnLoadedMapZones: Triggered after loading the map zones.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_LOADED_MAP_ZONES("OnLoadedMapZones"),

    /**
     * Triggered by Project Zomboid Lua event {@code "OnAddBuilding"}.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_ADD_BUILDING("OnAddBuilding"),

    /**
     * OnCreateLivingCharacter: Triggered when any IsoLivingCharacter object is created. Most useful for detecting spawning animals.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code IsoLivingCharacter character} — The character who was created.</li>
     *   <li>{@code SurvivorDesc desc} — The character's descriptor.</li>
     * </ul>
     */
    ON_CREATE_LIVING_CHARACTER("OnCreateLivingCharacter"),

    /**
     * (Client) OnChallengeQuery: Triggered when the main menu wants to check for challenge maps.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_CHALLENGE_QUERY("OnChallengeQuery"),

    /**
     * OnClickedAnimalForContext:
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code integer playerIndex}</li>
     *   <li>{@code ISContextMenu context}</li>
     *   <li>{@code IsoAnimal[] animals}</li>
     *   <li>{@code boolean test}</li>
     * </ul>
     */
    ON_CLICKED_ANIMAL_FOR_CONTEXT("OnClickedAnimalForContext"),

    /**
     * (Client) OnFillInventoryObjectContextMenu: Triggered after the context menu for an inventory item is filled.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code integer playerIndex} — The index of the player whose context menu has been filled.</li>
     *   <li>{@code ISContextMenu context} — The context menu that was filled.</li>
     *   <li>{@code InventoryItem[] or
     * umbrella.ContextMenuItemStack[] items} — The items that were selected to fill the context menu. If only full stacks are selected, a table of ContextMenuItemStacks is passed. Otherwise it is a table of InventoryItems.</li>
     * </ul>
     */
    ON_FILL_INVENTORY_OBJECT_CONTEXT_MENU("OnFillInventoryObjectContextMenu"),

    /**
     * (Client) OnPreFillInventoryObjectContextMenu: Triggered when the context menu for an inventory item is created, before it is filled.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code integer playerIndex} — The index of the player whose context menu has been created.</li>
     *   <li>{@code ISContextMenu context} — The context menu that was created.</li>
     *   <li>{@code InventoryItem[] or
     * umbrella.ContextMenuItemStack[] items} — The items that were selected to fill the context menu. If only full stacks are selected, a table of ContextMenuItemStacks is passed. Otherwise it is a table of InventoryItems.</li>
     * </ul>
     */
    ON_PRE_FILL_INVENTORY_OBJECT_CONTEXT_MENU("OnPreFillInventoryObjectContextMenu"),

    /**
     * (Client) OnFillWorldObjectContextMenu: Triggered after a world context menu is filled.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code integer playerIndex} — The index of the player whose context menu has been filled.</li>
     *   <li>{@code ISContextMenu context} — The context menu that was filled.</li>
     *   <li>{@code IsoObject[] worldObjects} — The objects that were right clicked on. The first object is whatever the mouse click hit directly. If one can be found, it will also add a door, a window, a window frame, a thumpable, a hoppable, and a tree. Many kinds of objects will never appear in this list or appear inconsistently so it is a common pattern to get the square from the first object and then loop through its objects.</li>
     *   <li>{@code boolean test} — Whether the context menu was filled to test for interactive objects on the square. If true, the context menu will not actually be displayed.</li>
     * </ul>
     */
    ON_FILL_WORLD_OBJECT_CONTEXT_MENU("OnFillWorldObjectContextMenu"),

    /**
     * (Client) OnPreFillWorldObjectContextMenu: Triggered after the world context menu is created, before it is filled.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code integer playerIndex} — The number of the player whose context menu has been created.</li>
     *   <li>{@code ISContextMenu context} — The context menu that was created.</li>
     *   <li>{@code IsoObject[] worldobjects} — The objects that were selected.</li>
     *   <li>{@code boolean test} — Whether the context menu was created to test for interactive objects on the square. If true, the context menu will not actually be displayed.</li>
     * </ul>
     */
    ON_PRE_FILL_WORLD_OBJECT_CONTEXT_MENU("OnPreFillWorldObjectContextMenu"),

    /**
     * (Client) OnRefreshInventoryWindowContainers: Triggered when the available containers in the inventory UI change.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code ISInventoryPage inventoryPage}</li>
     *   <li>{@code string reason}</li>
     * </ul>
     */
    ON_REFRESH_INVENTORY_WINDOW_CONTAINERS("OnRefreshInventoryWindowContainers"),

    /**
     * (Client) OnGamepadConnect: Triggered after a controller is connected.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code integer controllerId} — ID of the controller.</li>
     * </ul>
     */
    ON_GAMEPAD_CONNECT("OnGamepadConnect"),

    /**
     * (Client) OnGamepadDisconnect: Triggered after a controller is disconnected.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code integer controllerId} — ID of the controller.</li>
     * </ul>
     */
    ON_GAMEPAD_DISCONNECT("OnGamepadDisconnect"),

    /**
     * (Client) OnJoypadActivate: Triggered whenever a controller starts being used during gameplay.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code integer joypadId} — ID of the joypad.</li>
     * </ul>
     */
    ON_JOYPAD_ACTIVATE("OnJoypadActivate"),

    /**
     * (Client) OnJoypadActivateUI: Triggered whenever a controller starts being used outside of gameplay, such as on the main menu.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code integer joypadId} — ID of the joypad.</li>
     * </ul>
     */
    ON_JOYPAD_ACTIVATE_UI("OnJoypadActivateUI"),

    /**
     * (Client) OnJoypadBeforeDeactivate: Triggered when a controller is disconnected, before disconnection is processed.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code integer joypadId} — ID of the joypad.</li>
     * </ul>
     */
    ON_JOYPAD_BEFORE_DEACTIVATE("OnJoypadBeforeDeactivate"),

    /**
     * (Client) OnJoypadDeactivate: Triggered after a controller has been disconnected.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code integer joypadId} — ID of the joypad.</li>
     * </ul>
     */
    ON_JOYPAD_DEACTIVATE("OnJoypadDeactivate"),

    /**
     * (Client) OnJoypadBeforeReactivate: Triggered when a controller is connected, before connection is processed.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code integer joypadId} — ID of the joypad.</li>
     * </ul>
     */
    ON_JOYPAD_BEFORE_REACTIVATE("OnJoypadBeforeReactivate"),

    /**
     * (Client) OnJoypadReactivate: Triggered after a controller has been connected.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code integer joypadId} — ID of the joypad.</li>
     * </ul>
     */
    ON_JOYPAD_REACTIVATE("OnJoypadReactivate"),

    /**
     * (Client) OnJoypadRenderUI: Triggered when rendering controller debug UI.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_JOYPAD_RENDER_UI("OnJoypadRenderUI"),

    /**
     * Triggered by Project Zomboid Lua event {@code "OnJoypadDebugRenderUIOptionSet"}.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_JOYPAD_DEBUG_RENDER_UI_OPTION_SET("OnJoypadDebugRenderUIOptionSet"),

    /**
     * Triggered by Project Zomboid Lua event {@code "OnMakeItem"}.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_MAKE_ITEM("OnMakeItem"),

    /**
     * (Client) OnWeaponHitCharacter: Triggered when a non-zombie character is hit by an attack from a local player.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code IsoGameCharacter attacker} — The character who attacked.</li>
     *   <li>{@code IsoGameCharacter target} — The character who was hit by the attack.</li>
     *   <li>{@code HandWeapon weapon} — The weapon that was attacked with.</li>
     *   <li>{@code number damage} — How much damage the attack did.</li>
     * </ul>
     */
    ON_WEAPON_HIT_CHARACTER("OnWeaponHitCharacter"),

    /**
     * OnWeaponSwing: Triggered when a player begins swinging a weapon.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code IsoPlayer attacker} — The character attacking.</li>
     *   <li>{@code HandWeapon weapon} — The weapon being attacked with.</li>
     * </ul>
     */
    ON_WEAPON_SWING("OnWeaponSwing"),

    /**
     * (Client) OnWeaponHitTree: Triggered when a tree is hit by an attack.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code IsoGameCharacter attacker} — The character hitting the tree.</li>
     *   <li>{@code HandWeapon weapon} — The weapon the tree was hit with.</li>
     * </ul>
     */
    ON_WEAPON_HIT_TREE("OnWeaponHitTree"),

    /**
     * OnWeaponHitXp: Triggered when XP is being granted for an attack.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code IsoGameCharacter attacker} — The character who attacked.</li>
     *   <li>{@code HandWeapon weapon} — The weapon the character attacked with.</li>
     *   <li>{@code IsoMovingObject target} — The target of the attack.</li>
     *   <li>{@code number damage} — The damage of the attack.</li>
     *   <li>{@code 1 hitcount} — Unknown purpose: always 1. Added at some point in B42.</li>
     * </ul>
     */
    ON_WEAPON_HIT_XP("OnWeaponHitXp"),

    /**
     * (Client) OnWeaponSwingHitPoint: Triggered when a local player's attack connects.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code IsoPlayer attacker} — The player attacking.</li>
     *   <li>{@code HandWeapon weapon} — The weapon being attacked with.</li>
     * </ul>
     */
    ON_WEAPON_SWING_HIT_POINT("OnWeaponSwingHitPoint"),

    /**
     * (Client) OnPlayerAttackFinished: Triggered when a local player finishes attacking.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code IsoPlayer player} — The player who attacked.</li>
     *   <li>{@code HandWeapon weapon} — The weapon the player attacked with.</li>
     * </ul>
     */
    ON_PLAYER_ATTACK_FINISHED("OnPlayerAttackFinished"),

    /**
     * Triggered by Project Zomboid Lua event {@code "OnLoginState"}.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_LOGIN_STATE("OnLoginState"),

    /**
     * Triggered by Project Zomboid Lua event {@code "OnLoginStateSuccess"}.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_LOGIN_STATE_SUCCESS("OnLoginStateSuccess"),

    /**
     * Triggered by Project Zomboid Lua event {@code "OnCharacterCreateStats"}.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_CHARACTER_CREATE_STATS("OnCharacterCreateStats"),

    /**
     * (Client) OnLoadSoundBanks: Triggered after the game loads the FMOD sound banks.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_LOAD_SOUND_BANKS("OnLoadSoundBanks"),

    /**
     * (Client) OnObjectLeftMouseButtonDown: Triggered when the player left clicks a world object.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code IsoObject object} — The object that was clicked.</li>
     *   <li>{@code number x} — Screen X co-ordinate of the click.</li>
     *   <li>{@code number y} — Screen Y co-ordinate of the click.</li>
     * </ul>
     */
    ON_OBJECT_LEFT_MOUSE_BUTTON_DOWN("OnObjectLeftMouseButtonDown"),

    /**
     * (Client) OnObjectLeftMouseButtonUp: Triggered when the player releases left click on a world object.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code IsoObject object} — The object that was clicked.</li>
     *   <li>{@code number x} — Screen X co-ordinate of the click.</li>
     *   <li>{@code number y} — Screen Y co-ordinate of the click.</li>
     * </ul>
     */
    ON_OBJECT_LEFT_MOUSE_BUTTON_UP("OnObjectLeftMouseButtonUp"),

    /**
     * (Client) OnObjectRightMouseButtonDown: Triggered when the player right clicks a world object.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code IsoObject object} — The object that was clicked.</li>
     *   <li>{@code number x} — Screen X co-ordinate of the click.</li>
     *   <li>{@code number y} — Screen Y co-ordinate of the click.</li>
     * </ul>
     */
    ON_OBJECT_RIGHT_MOUSE_BUTTON_DOWN("OnObjectRightMouseButtonDown"),

    /**
     * (Client) OnObjectRightMouseButtonUp: Triggered when the player releases right click on a world object.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code IsoObject object} — The object that was clicked.</li>
     *   <li>{@code number x} — Screen X co-ordinate of the click.</li>
     *   <li>{@code number y} — Screen Y co-ordinate of the click.</li>
     * </ul>
     */
    ON_OBJECT_RIGHT_MOUSE_BUTTON_UP("OnObjectRightMouseButtonUp"),

    /**
     * Triggered by Project Zomboid Lua event {@code "OnDoTileBuilding"}.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_DO_TILE_BUILDING("OnDoTileBuilding"),

    /**
     * (Client) OnDoTileBuilding2: Triggered every tick while the local mouse and keyboard player has a build cursor (or other drag).
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code ISBuildingObject cursor} — The build cursor object the player is dragging.</li>
     *   <li>{@code boolean bRender} — Whether the preview should be rendered.</li>
     *   <li>{@code integer x} — World X co-ordinate of the square the build cursor is over.</li>
     *   <li>{@code integer y} — World Y co-ordinate of the square the build cursor is over.</li>
     *   <li>{@code integer z} — World Z co-ordinate of the square the build cursor is over.</li>
     *   <li>{@code IsoGridSquare or
     * nil square} — The square the build cursor is over.</li>
     * </ul>
     */
    ON_DO_TILE_BUILDING_2("OnDoTileBuilding2"),

    /**
     * (Client) OnDoTileBuilding3: Triggered every tick while a controller player has a build cursor (or other drag).
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code ISBuildingObject cursor} — The cursor object the player is dragging.</li>
     *   <li>{@code boolean bRender} — Whether the preview should be rendered.</li>
     *   <li>{@code integer x} — World X co-ordinate of the square the build cursor is over.</li>
     *   <li>{@code integer y} — World Y co-ordinate of the square the build cursor is over.</li>
     *   <li>{@code integer z} — World Z co-ordinate of the square the build cursor is over.</li>
     * </ul>
     */
    ON_DO_TILE_BUILDING_3("OnDoTileBuilding3"),

    /**
     * RenderOpaqueObjectsInWorld:
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code integer playerIndex}</li>
     *   <li>{@code integer x}</li>
     *   <li>{@code integer y}</li>
     *   <li>{@code integer z}</li>
     *   <li>{@code IsoGridSquare square}</li>
     * </ul>
     */
    RENDER_OPAQUE_OBJECTS_IN_WORLD("RenderOpaqueObjectsInWorld"),

    /**
     * (Multiplayer) (Client) OnConnectFailed: Triggered when the client fails to connect to a server.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code string message}</li>
     * </ul>
     */
    ON_CONNECT_FAILED("OnConnectFailed"),

    /**
     * (Multiplayer) (Client) OnConnected: Triggered after successfully connecting to a server on the main menu, before character creation begins.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_CONNECTED("OnConnected"),

    /**
     * (Multiplayer) (Client) OnDisconnect: Triggered when the client disconnects from a server.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_DISCONNECT("OnDisconnect"),

    /**
     * (Multiplayer) (Client) OnConnectionStateChanged: Triggered when the client's connection state is updated while trying to connect to a server.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code string state}</li>
     *   <li>{@code string message}</li>
     * </ul>
     */
    ON_CONNECTION_STATE_CHANGED("OnConnectionStateChanged"),

    /**
     * Triggered by Project Zomboid Lua event {@code "OnQRReceived"}.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_QR_RECEIVED("OnQRReceived"),

    /**
     * Triggered by Project Zomboid Lua event {@code "OnGoogleAuthRequest"}.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_GOOGLE_AUTH_REQUEST("OnGoogleAuthRequest"),

    /**
     * (Multiplayer) (Client) OnScoreboardUpdate: Triggered when the client receives an update to the in-game scoreboard.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code ArrayList<String> usernames}</li>
     *   <li>{@code ArrayList<String> displayNames}</li>
     *   <li>{@code ArrayList<String> steamIDs}</li>
     * </ul>
     */
    ON_SCOREBOARD_UPDATE("OnScoreboardUpdate"),

    /**
     * (Client) OnMouseMove: Triggered every frame, unless mouse movement is eaten by something else.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code number x} — Screen X co-ordinate of the click.</li>
     *   <li>{@code number y} — Screen Y co-ordinate of the click.</li>
     *   <li>{@code number xMultiplied} — Screen X co-ordinate of the click multiplied by zoom level.</li>
     *   <li>{@code number yMultiplied} — Screen Y co-ordinate of the click multiplied by zoom level.</li>
     * </ul>
     */
    ON_MOUSE_MOVE("OnMouseMove"),

    /**
     * (Client) OnMouseDown: Triggered when the player left clicks, as long as the input isn't eaten by UI.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code number x} — Screen X co-ordinate of the click.</li>
     *   <li>{@code number y} — Screen Y co-ordinate of the click.</li>
     * </ul>
     */
    ON_MOUSE_DOWN("OnMouseDown"),

    /**
     * (Client) OnMouseUp: Triggered whenever the player releases the left mouse button, unless the input is eaten by UI.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code number x} — Screen X co-ordinate of the click.</li>
     *   <li>{@code number y} — Screen Y co-ordinate of the click.</li>
     * </ul>
     */
    ON_MOUSE_UP("OnMouseUp"),

    /**
     * (Client) OnRightMouseDown: Triggered when the player right clicks, as long as the input isn't eaten by UI.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code number x} — Screen X co-ordinate of the click.</li>
     *   <li>{@code number y} — Screen Y co-ordinate of the click.</li>
     * </ul>
     */
    ON_RIGHT_MOUSE_DOWN("OnRightMouseDown"),

    /**
     * (Client) OnRightMouseUp: Triggered whenever the player releases the right mouse button, unless the input is eaten by UI.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code number x} — Screen X co-ordinate of the click.</li>
     *   <li>{@code number y} — Screen Y co-ordinate of the click.</li>
     * </ul>
     */
    ON_RIGHT_MOUSE_UP("OnRightMouseUp"),

    /**
     * OnMouseWheel:
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code number wheel}</li>
     * </ul>
     */
    ON_MOUSE_WHEEL("OnMouseWheel"),

    /**
     * Triggered by Project Zomboid Lua event {@code "OnNewSurvivorGroup"}.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_NEW_SURVIVOR_GROUP("OnNewSurvivorGroup"),

    /**
     * Triggered by Project Zomboid Lua event {@code "OnPlayerSetSafehouse"}.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_PLAYER_SET_SAFEHOUSE("OnPlayerSetSafehouse"),

    /**
     * (Client) OnLoad: Triggered upon finishing loading and entering the game.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_LOAD("OnLoad"),

    /**
     * (Client) AddXP: Triggered after a local character gains perk XP, except when the XP source specifically requested not to.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code IsoGameCharacter character} — The character who gained the XP.</li>
     *   <li>{@code PerkFactory.Perk perk} — The perk XP was gained in.</li>
     *   <li>{@code number amount} — The amount of XP gained. This is the final value after all modifiers.</li>
     * </ul>
     */
    ADD_XP("AddXP"),

    /**
     * (Client) LevelPerk: Triggered after a local character gains or loses a perk level.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code IsoGameCharacter character} — The character whose perk level changed.</li>
     *   <li>{@code PerkFactory.Perk perk} — The perk that changed level.</li>
     *   <li>{@code integer level} — The new level of the perk.</li>
     *   <li>{@code boolean increased} — True if the level increased, false if it decreased.</li>
     * </ul>
     */
    LEVEL_PERK("LevelPerk"),

    /**
     * OnSave: Triggered while saving the world, after characters and sandbox options have been saved, but before global mod data and the world have been saved.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_SAVE("OnSave"),

    /**
     * (Client) OnMainMenuEnter: Triggered upon entering the main menu.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_MAIN_MENU_ENTER("OnMainMenuEnter"),

    /**
     * (Client) OnGameStateEnter: Triggers upon entering the Terms Of Service GameState. Probably meant to trigger for other GameStates too, but it doesn't.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code GameState state}</li>
     * </ul>
     */
    ON_GAME_STATE_ENTER("OnGameStateEnter"),

    /**
     * OnPreMapLoad: Triggered before the map starts loading.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_PRE_MAP_LOAD("OnPreMapLoad"),

    /**
     * Triggered by Project Zomboid Lua event {@code "OnPostFloorSquareDraw"}.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_POST_FLOOR_SQUARE_DRAW("OnPostFloorSquareDraw"),

    /**
     * OnPostFloorLayerDraw: Triggered after a floor layer has been rendered.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code integer z} — The Z level that was rendered.</li>
     * </ul>
     */
    ON_POST_FLOOR_LAYER_DRAW("OnPostFloorLayerDraw"),

    /**
     * Triggered by Project Zomboid Lua event {@code "OnPostTilesSquareDraw"}.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_POST_TILES_SQUARE_DRAW("OnPostTilesSquareDraw"),

    /**
     * Triggered by Project Zomboid Lua event {@code "OnPostTileDraw"}.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_POST_TILE_DRAW("OnPostTileDraw"),

    /**
     * Triggered by Project Zomboid Lua event {@code "OnPostWallSquareDraw"}.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_POST_WALL_SQUARE_DRAW("OnPostWallSquareDraw"),

    /**
     * Triggered by Project Zomboid Lua event {@code "OnPostCharactersSquareDraw"}.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_POST_CHARACTERS_SQUARE_DRAW("OnPostCharactersSquareDraw"),

    /**
     * (Client) OnCreateUI: Triggered when the UI is initialised.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_CREATE_UI("OnCreateUI"),

    /**
     * Triggered by Project Zomboid Lua event {@code "OnMapLoadCreateIsoObject"}.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_MAP_LOAD_CREATE_ISO_OBJECT("OnMapLoadCreateIsoObject"),

    /**
     * (Client) OnCreateSurvivor: Triggered when an IsoSurvivor object is created.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code IsoSurvivor survivor} — The survior that was created.</li>
     * </ul>
     */
    ON_CREATE_SURVIVOR("OnCreateSurvivor"),

    /**
     * (Client) OnCreatePlayer: Triggered every time a local player loads into the world.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code integer playerIndex} — The index of the newly-spawned player</li>
     *   <li>{@code IsoPlayer player} — The new player object</li>
     * </ul>
     */
    ON_CREATE_PLAYER("OnCreatePlayer"),

    /**
     * (Client) OnPlayerDeath: Triggered when a local player dies.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code IsoPlayer player} — The player who died.</li>
     * </ul>
     */
    ON_PLAYER_DEATH("OnPlayerDeath"),

    /**
     * OnZombieDead: Triggered when a zombie dies. The zombie's inventory is not filled with loot when this event is triggered, but their clothing and attached items are added. The corpse does not exist until a few seconds later.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code IsoZombie zombie} — The zombie that died.</li>
     * </ul>
     */
    ON_ZOMBIE_DEAD("OnZombieDead"),

    /**
     * OnCharacterDeath: Triggered when any character dies, including zombies, players and animals.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code IsoGameCharacter character} — The character who died.</li>
     * </ul>
     */
    ON_CHARACTER_DEATH("OnCharacterDeath"),

    /**
     * Triggered by Project Zomboid Lua event {@code "OnCharacterMeet"}.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_CHARACTER_MEET("OnCharacterMeet"),

    /**
     * (Client) OnSpawnRegionsLoaded: Triggered when the spawn regions have been loaded.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code table regions}</li>
     * </ul>
     */
    ON_SPAWN_REGIONS_LOADED("OnSpawnRegionsLoaded"),

    /**
     * OnPostMapLoad: Triggered after the map has been loaded.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code IsoCell cell} — The cell that was loaded.</li>
     *   <li>{@code integer x}</li>
     *   <li>{@code integer y}</li>
     * </ul>
     */
    ON_POST_MAP_LOAD("OnPostMapLoad"),

    /**
     * Triggered by Project Zomboid Lua event {@code "OnAIStateExecute"}.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_AI_STATE_EXECUTE("OnAIStateExecute"),

    /**
     * Triggered by Project Zomboid Lua event {@code "OnAIStateEnter"}.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_AI_STATE_ENTER("OnAIStateEnter"),

    /**
     * Triggered by Project Zomboid Lua event {@code "OnAIStateExit"}.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_AI_STATE_EXIT("OnAIStateExit"),

    /**
     * (Client) OnAIStateChange: Triggered when a local zombie or any loaded player changes state.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code IsoGameCharacter character} — The character whose state changed.</li>
     *   <li>{@code State currentState} — The state the character changed to.</li>
     *   <li>{@code State previousState} — The character's previous state.</li>
     * </ul>
     */
    ON_AI_STATE_CHANGE("OnAIStateChange"),

    /**
     * (Client) OnPlayerMove: Triggered during each local player's update if they are walking.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code IsoPlayer character}</li>
     * </ul>
     */
    ON_PLAYER_MOVE("OnPlayerMove"),

    /**
     * OnInitWorld: Triggered after the world has initialised.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_INIT_WORLD("OnInitWorld"),

    /**
     * (Client) OnNewGame: Triggered whenever a local player character is created for the first time.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code IsoPlayer player} — The character that was created.</li>
     *   <li>{@code IsoGridSquare square} — The square the character spawned on.</li>
     * </ul>
     */
    ON_NEW_GAME("OnNewGame"),

    /**
     * Triggered by Project Zomboid Lua event {@code "OnIsoThumpableLoad"}.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_ISO_THUMPABLE_LOAD("OnIsoThumpableLoad"),

    /**
     * Triggered by Project Zomboid Lua event {@code "OnIsoThumpableSave"}.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_ISO_THUMPABLE_SAVE("OnIsoThumpableSave"),

    /**
     * ReuseGridsquare: Triggered before a square is unloaded.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code IsoGridSquare square} — The square being reused.</li>
     * </ul>
     */
    REUSE_GRIDSQUARE("ReuseGridsquare"),

    /**
     * LoadGridsquare: Triggered after a new square is loaded.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code IsoGridSquare square} — The square that was loaded.</li>
     * </ul>
     */
    LOAD_GRIDSQUARE("LoadGridsquare"),

    /**
     * LoadChunk: Triggered when a chunk loads.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code IsoChunk chunk} — Loaded chunk.</li>
     * </ul>
     */
    LOAD_CHUNK("LoadChunk"),

    /**
     * EveryOneMinute: Triggered every in-game minute.
     * <p><b>Parameters:</b> None.</p>
     */
    EVERY_ONE_MINUTE("EveryOneMinute"),

    /**
     * EveryTenMinutes: Triggered every ten in-game minutes.
     * <p><b>Parameters:</b> None.</p>
     */
    EVERY_TEN_MINUTES("EveryTenMinutes"),

    /**
     * EveryDays: Triggered at 0:00 every in-game day.
     * <p><b>Parameters:</b> None.</p>
     */
    EVERY_DAYS("EveryDays"),

    /**
     * EveryHours: Triggered at the start of every in-game hour.
     * <p><b>Parameters:</b> None.</p>
     */
    EVERY_HOURS("EveryHours"),

    /**
     * Triggered by Project Zomboid Lua event {@code "OnDusk"}.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_DUSK("OnDusk"),

    /**
     * Triggered by Project Zomboid Lua event {@code "OnDawn"}.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_DAWN("OnDawn"),

    /**
     * OnEquipPrimary: Triggered when a character changes the item in their primary equip slot.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code IsoGameCharacter character} — The character that equipped the item.</li>
     *   <li>{@code InventoryItem or
     * nil item} — The newly equipped item.</li>
     * </ul>
     */
    ON_EQUIP_PRIMARY("OnEquipPrimary"),

    /**
     * OnEquipSecondary: Triggered when a character changes the item in their secondary equip slot.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code IsoGameCharacter character} — The character that equipped the item.</li>
     *   <li>{@code InventoryItem or
     * nil item} — The newly equipped item.</li>
     * </ul>
     */
    ON_EQUIP_SECONDARY("OnEquipSecondary"),

    /**
     * (Client) OnClothingUpdated: Triggered every time a character's clothing is updated. This includes changing clothes and accumulating dirt or blood.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code IsoGameCharacter character} — The character whose clothing updated.</li>
     * </ul>
     */
    ON_CLOTHING_UPDATED("OnClothingUpdated"),

    /**
     * (Server) OnWeatherPeriodStart: Triggered when a weather period begins.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code WeatherPeriod period}</li>
     * </ul>
     */
    ON_WEATHER_PERIOD_START("OnWeatherPeriodStart"),

    /**
     * (Server) OnWeatherPeriodStage: Triggered when a weather period progresses a stage.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code WeatherPeriod period}</li>
     * </ul>
     */
    ON_WEATHER_PERIOD_STAGE("OnWeatherPeriodStage"),

    /**
     * (Server) OnWeatherPeriodComplete: Triggered when a weather period finishes.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code WeatherPeriod period}</li>
     * </ul>
     */
    ON_WEATHER_PERIOD_COMPLETE("OnWeatherPeriodComplete"),

    /**
     * (Server) OnWeatherPeriodStop: Triggered when a weather period ends early, such as by an admin command.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code WeatherPeriod period}</li>
     * </ul>
     */
    ON_WEATHER_PERIOD_STOP("OnWeatherPeriodStop"),

    /**
     * Triggered by Project Zomboid Lua event {@code "OnRainStart"}.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_RAIN_START("OnRainStart"),

    /**
     * Triggered by Project Zomboid Lua event {@code "OnRainStop"}.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_RAIN_STOP("OnRainStop"),

    /**
     * OnAmbientSound: Triggered whenever a sound meta event or building alarm is triggered.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code string name} — Name of the sound script played.</li>
     *   <li>{@code number x} — World X co-ordinate of the sound.</li>
     *   <li>{@code number y} — World Y co-ordinate of the sound.</li>
     * </ul>
     */
    ON_AMBIENT_SOUND("OnAmbientSound"),

    /**
     * OnWorldSound: Triggered whenever a world sound is created.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code integer x} — World X co-ordinate of the square the sound was created on.</li>
     *   <li>{@code integer y} — World Y co-ordinate of the square the sound was created on.</li>
     *   <li>{@code integer z} — World Z co-ordinate of the square the sound was created on.</li>
     *   <li>{@code integer radius} — Radius of the sound.</li>
     *   <li>{@code integer volume} — Volume of the sound. Zombies are more likely to investigate louder sounds when they have multiple choices.</li>
     *   <li>{@code Object source} — The source of the sound.</li>
     * </ul>
     */
    ON_WORLD_SOUND("OnWorldSound"),

    /**
     * OnResetLua: Triggered after Lua has been reloaded.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code string reason}</li>
     * </ul>
     */
    ON_RESET_LUA("OnResetLua"),

    /**
     * (Client) OnModsModified: Triggered on the main menu when a mod's files have changed.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_MODS_MODIFIED("OnModsModified"),

    /**
     * OnSeeNewRoom: Triggered when a room becomes visible for the first time.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code IsoRoom room} — The room.</li>
     * </ul>
     */
    ON_SEE_NEW_ROOM("OnSeeNewRoom"),

    /**
     * OnNewFire: Triggered when a new fire is started.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code IsoFire fire} — The fire that was created.</li>
     * </ul>
     */
    ON_NEW_FIRE("OnNewFire"),

    /**
     * (Server) OnFillContainer: Triggered whenever a container is first filled with loot, or when loot respawns.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code string roomType} — Distribution type of the room the container is in, or the type of the vehicle.</li>
     *   <li>{@code string containerType} — The type of the container that was filled.</li>
     *   <li>{@code ItemContainer or
     * ItemPickerContainer container} — The container that was filled. An ItemPickerContainer is sometimes passed when a sub-container is spawned and filled, and is probably a bug.</li>
     * </ul>
     */
    ON_FILL_CONTAINER("OnFillContainer"),

    /**
     * Triggered by Project Zomboid Lua event {@code "OnChangeWeather"}.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_CHANGE_WEATHER("OnChangeWeather"),

    /**
     * OnRenderTick: Triggered on every rendering tick.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_RENDER_TICK("OnRenderTick"),

    /**
     * OnDestroyIsoThumpable: Triggered when an IsoThumpable object is destroyed by damage.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code IsoThumpable object} — The thumpable that was destroyed.</li>
     *   <li>{@code nil player} — Purpose unknown: always nil.</li>
     * </ul>
     */
    ON_DESTROY_ISO_THUMPABLE("OnDestroyIsoThumpable"),

    /**
     * OnPostSave: Triggered after saving and exiting the game.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_POST_SAVE("OnPostSave"),

    /**
     * OnResolutionChange: Triggered whenever the window resolution changes.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code integer oldX} — Previous width of the window.</li>
     *   <li>{@code integer oldY} — Previous height of the window.</li>
     *   <li>{@code integer newX} — New width of the window.</li>
     *   <li>{@code integer newY} — New height of the window.</li>
     * </ul>
     */
    ON_RESOLUTION_CHANGE("OnResolutionChange"),

    /**
     * OnWaterAmountChange: Triggered when the amount of fluid (not just water) in an object changes.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code IsoObject object} — The object which has gained/lost fluid.</li>
     *   <li>{@code number previousAmount} — The amount of fluid the object had before the change.</li>
     * </ul>
     */
    ON_WATER_AMOUNT_CHANGE("OnWaterAmountChange"),

    /**
     * (Server) OnClientCommand: Triggered when a client command sent through sendClientCommand is received by the server.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code string module} — The module the command was sent with.</li>
     *   <li>{@code string command} — The command the command was sent with.</li>
     *   <li>{@code IsoPlayer player} — The player who sent the command.</li>
     *   <li>{@code table or nil args} — The arguments table the command was sent with. If the table was empty, nil is passed instead.</li>
     * </ul>
     */
    ON_CLIENT_COMMAND("OnClientCommand"),

    /**
     * (Multiplayer) (Client) OnServerCommand: Triggered when a server command sent through sendServerCommand is received by the client.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code string module} — The module the command was sent with.</li>
     *   <li>{@code string command} — The command the command was sent with.</li>
     *   <li>{@code table or nil args} — The arguments table the command was sent with. If the table was empty, nil is passed instead.</li>
     * </ul>
     */
    ON_SERVER_COMMAND("OnServerCommand"),

    /**
     * (Multiplayer) (Server) OnProcessTransaction:
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code "scrapMoveable" or "pickUpMoveable" or
     * "rotateMoveable" or "placeMoveable" or
     * "dropOnFloor" action}</li>
     *   <li>{@code IsoPlayer character}</li>
     *   <li>{@code InventoryItem or
     * nil item}</li>
     *   <li>{@code ContainerID source}</li>
     *   <li>{@code ContainerID destination}</li>
     *   <li>{@code table or nil args} — When type is "dropOnFloor", has field IsoGridSquare "square". When type is "rotateMoveable" or "placeMoveable", has field string "direction"</li>
     * </ul>
     */
    ON_PROCESS_TRANSACTION("OnProcessTransaction"),

    /**
     * (Multiplayer) (Server) OnProcessAction:
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code "build" action}</li>
     *   <li>{@code IsoPlayer character}</li>
     *   <li>{@code table args}</li>
     * </ul>
     */
    ON_PROCESS_ACTION("OnProcessAction"),

    /**
     * (Client) OnContainerUpdate: Triggered when a container is added or removed from the world.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code any object} — The container that was added or removed.</li>
     * </ul>
     */
    ON_CONTAINER_UPDATE("OnContainerUpdate"),

    /**
     * OnObjectAdded: Triggered when an object is added to the world. Note: usually not called on the client, but is in some cases.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code IsoObject object}</li>
     * </ul>
     */
    ON_OBJECT_ADDED("OnObjectAdded"),

    /**
     * OnObjectAboutToBeRemoved: Triggered before a tile object is destroyed or picked up.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code IsoObject object} — The object about to be removed.</li>
     * </ul>
     */
    ON_OBJECT_ABOUT_TO_BE_REMOVED("OnObjectAboutToBeRemoved"),

    /**
     * (Multiplayer) onLoadModDataFromServer: Triggered when the server sends a square's mod data to the clients, or when the client receives it.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code IsoGridSquare square} — The square that had its mod data updated.</li>
     * </ul>
     */
    ON_LOAD_MOD_DATA_FROM_SERVER("onLoadModDataFromServer"),

    /**
     * OnGameTimeLoaded: Triggered after GameTime is initialised.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_GAME_TIME_LOADED("OnGameTimeLoaded"),

    /**
     * (Client) OnCGlobalObjectSystemInit: Triggered when the client GlobalObject system is being initialised.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_C_GLOBAL_OBJECT_SYSTEM_INIT("OnCGlobalObjectSystemInit"),

    /**
     * (Server) OnSGlobalObjectSystemInit: Triggered when the server GlobalObject system has been initialised.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_S_GLOBAL_OBJECT_SYSTEM_INIT("OnSGlobalObjectSystemInit"),

    /**
     * Triggered by Project Zomboid Lua event {@code "OnWorldMessage"}.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_WORLD_MESSAGE("OnWorldMessage"),

    /**
     * (Client) OnKeyKeepPressed: Triggered every frame while a key is held down.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code integer key} — Key code of the key that was held.</li>
     * </ul>
     */
    ON_KEY_KEEP_PRESSED("OnKeyKeepPressed"),

    /**
     * (Multiplayer) (Server) SendCustomModData: Triggered when a client is requesting server mod data.
     * <p><b>Parameters:</b> None.</p>
     */
    SEND_CUSTOM_MOD_DATA("SendCustomModData"),

    /**
     * (Multiplayer) (Client) ServerPinged: Triggered when receiving a ping response from the server. The 'numClients' string is suffixed with '/512'.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code string clientAddress}</li>
     *   <li>{@code string numClients}</li>
     * </ul>
     */
    SERVER_PINGED("ServerPinged"),

    /**
     * (Multiplayer) (Server) OnServerStarted: Triggered when the server has started and can now be connected to.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_SERVER_STARTED("OnServerStarted"),

    /**
     * OnLoadedTileDefinitions: Triggered after loading the tile definitions.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code IsoSpriteManager spriteManager} — The sprite manager.</li>
     * </ul>
     */
    ON_LOADED_TILE_DEFINITIONS("OnLoadedTileDefinitions"),

    /**
     * (Client) OnPostRender: Triggered after every in-game rendering frame.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_POST_RENDER("OnPostRender"),

    /**
     * DoSpecialTooltip: Triggered when updating the tooltip of an IsoObject with a special tooltip. Used for hover-over information about plants.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code ObjectTooltip tooltip} — Empty tooltip for the object.</li>
     *   <li>{@code IsoGridSquare square} — Square of the object the tooltip is being updated for.</li>
     * </ul>
     */
    DO_SPECIAL_TOOLTIP("DoSpecialTooltip"),

    /**
     * (Client) OnCoopJoinFailed: Triggered when a splitscreen character fails to be added.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code integer playerIndex} — The index of the player who could not be added.</li>
     * </ul>
     */
    ON_COOP_JOIN_FAILED("OnCoopJoinFailed"),

    /**
     * (Multiplayer) (Client) OnServerWorkshopItems: Triggered when receiving an update about the server's Steam Workshop items while connecting. Has a very variable signature depending on the type.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code string type}</li>
     *   <li>{@code ArrayList<String> or
     * ArrayList<SteamUGCDetails> or
     * string or
     * integer or
     * nil items}</li>
     *   <li>{@code string or integer or nil error}</li>
     *   <li>{@code integer or nil maxSize}</li>
     * </ul>
     */
    ON_SERVER_WORKSHOP_ITEMS("OnServerWorkshopItems"),

    /**
     * OnVehicleDamageTexture: Triggered when a vehicle part has become damaged enough to gain a damage overlay.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code IsoGameCharacter driver} — The character driving the vehicle.</li>
     * </ul>
     */
    ON_VEHICLE_DAMAGE_TEXTURE("OnVehicleDamageTexture"),

    /**
     * (Client) OnCustomUIKey: Triggered when a key that is not used by vanilla UI is released.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code integer key} — Key code of the key that was pressed.</li>
     * </ul>
     */
    ON_CUSTOM_UI_KEY("OnCustomUIKey"),

    /**
     * (Client) OnCustomUIKeyPressed: Triggered when a key that is not used by vanilla UI is pressed.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code integer key} — Key code of the key that was pressed.</li>
     * </ul>
     */
    ON_CUSTOM_UI_KEY_PRESSED("OnCustomUIKeyPressed"),

    /**
     * (Client) OnCustomUIKeyReleased: Triggered when a key that is not used by vanilla UI is released.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code integer key} — Key code of the key that was pressed.</li>
     * </ul>
     */
    ON_CUSTOM_UI_KEY_RELEASED("OnCustomUIKeyReleased"),

    /**
     * (Client) OnDeviceText: Triggered whenever a radio displays text.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code string guid} — GUID of the line being displayed.</li>
     *   <li>{@code string codes} — Codes of the line being displayed. These typically contain perk/stat changes, but can be used to associate any arbitrary data with a line.</li>
     *   <li>{@code number x} — World X co-ordinate where the line is being displayed.</li>
     *   <li>{@code number y} — World Y co-ordinate where the line is being displayed.</li>
     *   <li>{@code number z} — World Z co-ordinate where the line is being displayed.</li>
     *   <li>{@code string or
     * ChatMessage text} — The displayed, translated text of the line, or the chat message being displayed.</li>
     *   <li>{@code WaveSignalDevice device} — The device playing the line.</li>
     * </ul>
     */
    ON_DEVICE_TEXT("OnDeviceText"),

    /**
     * Triggered by Project Zomboid Lua event {@code "OnRadioInteraction"}.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_RADIO_INTERACTION("OnRadioInteraction"),

    /**
     * OnLoadRadioScripts: Triggered after ZomboidRadio loads the radio scripts.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code RadioScriptManager scriptManager} — The radio script manager.</li>
     *   <li>{@code boolean newGame} — True when a new save launches for the first time.</li>
     * </ul>
     */
    ON_LOAD_RADIO_SCRIPTS("OnLoadRadioScripts"),

    /**
     * (Client) OnAcceptInvite: Triggered when the client accepts a steam invite to a server.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code string connectString} — Steamworks connection string. Takes the format of '+connect ip:port'</li>
     * </ul>
     */
    ON_ACCEPT_INVITE("OnAcceptInvite"),

    /**
     * (Multiplayer) (Server) OnCoopServerMessage: Triggered when receiving a server message during a co-op (in-game hosted) game.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code string tag}</li>
     *   <li>{@code string cookie}</li>
     *   <li>{@code string payload}</li>
     * </ul>
     */
    ON_COOP_SERVER_MESSAGE("OnCoopServerMessage"),

    /**
     * (Multiplayer) (Client) OnReceiveUserlog: Triggered when receiving another client's Userlogs.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code string username}</li>
     *   <li>{@code ArrayList logs}</li>
     * </ul>
     */
    ON_RECEIVE_USERLOG("OnReceiveUserlog"),

    /**
     * (Multiplayer) (Client) OnAdminMessage: Triggered when a ticket is created and the local player is an admin.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code string message} — The text of the ticket.</li>
     *   <li>{@code integer x} — World X co-ordinate of the player who made the ticket.</li>
     *   <li>{@code integer y} — World Y co-ordinate of the player who made the ticket.</li>
     *   <li>{@code integer z} — World Z co-ordinate of the player who made the ticket.</li>
     * </ul>
     */
    ON_ADMIN_MESSAGE("OnAdminMessage"),

    /**
     * (Multiplayer) (Client) ReceiveFactionInvite: Triggered when the client receives a faction invite.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code string factionName}</li>
     *   <li>{@code string hostUsername}</li>
     * </ul>
     */
    RECEIVE_FACTION_INVITE("ReceiveFactionInvite"),

    /**
     * (Multiplayer) (Client) AcceptedFactionInvite: Triggered when receiving confirmation that a local player has accepted a faction invite.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code string faction} — Name of the faction.</li>
     *   <li>{@code string username} — Username of the faction leader.</li>
     * </ul>
     */
    ACCEPTED_FACTION_INVITE("AcceptedFactionInvite"),

    /**
     * (Multiplayer) (Client) ReceiveSafehouseInvite: Triggered when the client receives a safehouse invite.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code string title}</li>
     *   <li>{@code string hostUsername}</li>
     * </ul>
     */
    RECEIVE_SAFEHOUSE_INVITE("ReceiveSafehouseInvite"),

    /**
     * (Multiplayer) (Client) AcceptedSafehouseInvite: Triggered when a player accepts an invite to a safehouse.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code string safehouse} — Name of the safehouse.</li>
     *   <li>{@code string username} — Username of the safehouse owner.</li>
     * </ul>
     */
    ACCEPTED_SAFEHOUSE_INVITE("AcceptedSafehouseInvite"),

    /**
     * (Multiplayer) (Client) ViewTickets: Triggered when receiving the list of tickets from the server.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code ArrayList tickets}</li>
     * </ul>
     */
    VIEW_TICKETS("ViewTickets"),

    /**
     * (Multiplayer) (Client) ViewBannedIPs: Triggered when receiving the response to a request from getBannedIPs().
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code ArrayList<DBBannedIP> bannedIPs} — List of banned ip database entries.</li>
     * </ul>
     */
    VIEW_BANNED_IPS("ViewBannedIPs"),

    /**
     * ViewBannedSteamIDs:
     * <p><b>Parameters:</b> None.</p>
     */
    VIEW_BANNED_STEAM_IDS("ViewBannedSteamIDs"),

    /**
     * (Multiplayer) (Client) SyncFaction: Triggered when the client receives changes to a faction.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code string faction}</li>
     * </ul>
     */
    SYNC_FACTION("SyncFaction"),

    /**
     * Triggered by Project Zomboid Lua event {@code "SyncFactionServer"}.
     * <p><b>Parameters:</b> None.</p>
     */
    SYNC_FACTION_SERVER("SyncFactionServer"),

    /**
     * Triggered by Project Zomboid Lua event {@code "RefreshCheats"}.
     * <p><b>Parameters:</b> None.</p>
     */
    REFRESH_CHEATS("RefreshCheats"),

    /**
     * (Multiplayer) OnReceiveItemListNet: Triggered when receiving a list of items sent with sendItemListNet. This is not used by vanilla, it is provided for mods to use. Item lists sent by clients cannot be longer than 50 items and all of the items must be in the player's inventory.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code IsoPlayer or
     * nil sender} — The player who sent the item list. Nil if it was sent by the server.</li>
     *   <li>{@code ArrayList<InventoryItem> items} — The list of items.</li>
     *   <li>{@code IsoPlayer or
     * nil receiver} — The specific local player the list was sent to. Nil if it was sent by a client to the server, or by the server to all clients.</li>
     *   <li>{@code string transferID} — Arbitrary string associated with the message. Defaults to -1 if none was given.</li>
     *   <li>{@code string or nil custom} — Arbitrary string associated with the message. Nil if none was given.</li>
     * </ul>
     */
    ON_RECEIVE_ITEM_LIST_NET("OnReceiveItemListNet"),

    /**
     * (Multiplayer) (Client) OnMiniScoreboardUpdate: Triggered when the admin mini-scoreboard is updated.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_MINI_SCOREBOARD_UPDATE("OnMiniScoreboardUpdate"),

    /**
     * (Multiplayer) (Client) OnSafehousesChanged: Triggered every time a safehouse is added, removed or changed.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_SAFEHOUSES_CHANGED("OnSafehousesChanged"),

    /**
     * Triggered by Project Zomboid Lua event {@code "OnWarUpdate"}.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_WAR_UPDATE("OnWarUpdate"),

    /**
     * (Multiplayer) (Client) RequestTrade: Triggered when the client receives a trade request.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code string requester}</li>
     * </ul>
     */
    REQUEST_TRADE("RequestTrade"),

    /**
     * (Multiplayer) (Client) AcceptedTrade: Triggered when the other player in the client's current trade accepts or declines the trade.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code boolean accepted} — Whether the trade was accepted.</li>
     * </ul>
     */
    ACCEPTED_TRADE("AcceptedTrade"),

    /**
     * Triggered by Project Zomboid Lua event {@code "RequestMedicalCheck"}.
     * <p><b>Parameters:</b> None.</p>
     */
    REQUEST_MEDICAL_CHECK("RequestMedicalCheck"),

    /**
     * Triggered by Project Zomboid Lua event {@code "AcceptedMedicalCheck"}.
     * <p><b>Parameters:</b> None.</p>
     */
    ACCEPTED_MEDICAL_CHECK("AcceptedMedicalCheck"),

    /**
     * (Multiplayer) (Client) TradingUIAddItem: Triggered when the other player in a trade adds an item.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code IsoPlayer player} — The player who added the item.</li>
     *   <li>{@code InventoryItem item} — The item that was added.</li>
     * </ul>
     */
    TRADING_UI_ADD_ITEM("TradingUIAddItem"),

    /**
     * (Multiplayer) (Client) TradingUIRemoveItem: Triggered when the other player in a trade removes an item.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code IsoPlayer player} — The player who removed the item.</li>
     *   <li>{@code integer id} — The id of the removed item.</li>
     * </ul>
     */
    TRADING_UI_REMOVE_ITEM("TradingUIRemoveItem"),

    /**
     * (Multiplayer) (Client) TradingUIUpdateState: Triggered when the other player in a trade changes the state of the trade.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code IsoPlayer player} — The player changing the state.</li>
     *   <li>{@code integer state} — The new state.</li>
     * </ul>
     */
    TRADING_UI_UPDATE_STATE("TradingUIUpdateState"),

    /**
     * OnGridBurnt: Triggered when a square is burned by fire.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code IsoGridSquare square} — The square that was burned.</li>
     * </ul>
     */
    ON_GRID_BURNT("OnGridBurnt"),

    /**
     * OnPreDistributionMerge: Triggered after the distribution tables have been merged.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_PRE_DISTRIBUTION_MERGE("OnPreDistributionMerge"),

    /**
     * OnDistributionMerge: Triggered when the distribution tables merge.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_DISTRIBUTION_MERGE("OnDistributionMerge"),

    /**
     * OnPostDistributionMerge: Triggered after the distribution tables have been merged.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_POST_DISTRIBUTION_MERGE("OnPostDistributionMerge"),

    /**
     * (Multiplayer) (Client) MngInvReceiveItems: Triggered when managing a remote player's inventory from the admin menu.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code umbrella.MngInvItemTable inventory} — Details of the player's inventory.</li>
     * </ul>
     */
    MNG_INV_RECEIVE_ITEMS("MngInvReceiveItems"),

    /**
     * OnTileRemoved: Triggered when a tile object is removed.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code IsoObject object} — The object being removed.</li>
     * </ul>
     */
    ON_TILE_REMOVED("OnTileRemoved"),

    /**
     * (Multiplayer) (Server) OnServerStartSaving: Triggered when the server has paused the game to save.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_SERVER_START_SAVING("OnServerStartSaving"),

    /**
     * (Multiplayer) (Client) OnServerFinishSaving: Triggered when the server has finished saving and unpauses the game.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_SERVER_FINISH_SAVING("OnServerFinishSaving"),

    /**
     * OnMechanicActionDone: Triggered after a character completes a mechanic action on a vehicle.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code IsoGameCharacter character} — The character who performed the action.</li>
     *   <li>{@code boolean success} — Whether the action succeeded.</li>
     * </ul>
     */
    ON_MECHANIC_ACTION_DONE("OnMechanicActionDone"),

    /**
     * OnClimateTick: Triggered every climate manager tick.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code ClimateManager climateManager} — The climate manager.</li>
     * </ul>
     */
    ON_CLIMATE_TICK("OnClimateTick"),

    /**
     * (Client) OnThunderEvent: Triggered when a thunder event is enqueued.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code integer x} — World X co-ordinate where the thunder event will happen.</li>
     *   <li>{@code integer y} — World Y co-ordinate where the thunder event will happen.</li>
     *   <li>{@code boolean strike} — Whether the thunder event will make a striking sound.</li>
     *   <li>{@code boolean light} — Whether the thunder event will create light.</li>
     *   <li>{@code boolean rumble} — Whether the thunder event will make a rumbling sound.</li>
     * </ul>
     */
    ON_THUNDER_EVENT("OnThunderEvent"),

    /**
     * (Client) OnEnterVehicle: Triggered when a character enters a vehicle.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code IsoGameCharacter character} — The character that entered the vehicle.</li>
     * </ul>
     */
    ON_ENTER_VEHICLE("OnEnterVehicle"),

    /**
     * (Multiplayer) (Client) OnSteamGameJoin: Triggered when the player joins a game through steam.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_STEAM_GAME_JOIN("OnSteamGameJoin"),

    /**
     * (Multiplayer) (Client) OnTabAdded: Triggered when a tab is added to the chat.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code string title}</li>
     *   <li>{@code integer tabID}</li>
     * </ul>
     */
    ON_TAB_ADDED("OnTabAdded"),

    /**
     * (Multiplayer) (Client) OnSetDefaultTab: Triggered when the player sets their favourite chat window tab.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code string title}</li>
     * </ul>
     */
    ON_SET_DEFAULT_TAB("OnSetDefaultTab"),

    /**
     * (Multiplayer) (Client) OnTabRemoved: Triggered when a tab is removed from the chat.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code string title}</li>
     *   <li>{@code integer tabID}</li>
     * </ul>
     */
    ON_TAB_REMOVED("OnTabRemoved"),

    /**
     * (Multiplayer) (Client) OnAddMessage: Triggered when a message is added to chat.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code ChatMessage message} — The message that was added.</li>
     *   <li>{@code integer tabId} — The ID of the tab the message was added to.</li>
     * </ul>
     */
    ON_ADD_MESSAGE("OnAddMessage"),

    /**
     * (Multiplayer) (Client) SwitchChatStream: Triggered when the client switches chat tabs.
     * <p><b>Parameters:</b> None.</p>
     */
    SWITCH_CHAT_STREAM("SwitchChatStream"),

    /**
     * (Multiplayer) (Client) OnChatWindowInit: Triggered when the chat window is initialised.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_CHAT_WINDOW_INIT("OnChatWindowInit"),

    /**
     * OnAlertMessage: See OnAddMessage
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code ChatMessage message} — The message that was added.</li>
     *   <li>{@code integer tabId} — The ID of the tab the message was added to.</li>
     * </ul>
     */
    ON_ALERT_MESSAGE("OnAlertMessage"),

    /**
     * OnInitSeasons: Triggered when the ErosionManager is created.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code ErosionSeason season}</li>
     * </ul>
     */
    ON_INIT_SEASONS("OnInitSeasons"),

    /**
     * (Client) OnClimateTickDebug: Triggered every climate manager tick, but only on the client and only when debug mode is enabled.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code ClimateManager climateManager} — The climate manager.</li>
     * </ul>
     */
    ON_CLIMATE_TICK_DEBUG("OnClimateTickDebug"),

    /**
     * OnInitModdedWeatherStage: Triggered when a modded weather period is created.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code WeatherPeriod weatherPeriod} — The weather period that was created.</li>
     *   <li>{@code WeatherPeriod.WeatherStage weatherStage} — The weather stage that was created.</li>
     *   <li>{@code number strength}</li>
     * </ul>
     */
    ON_INIT_MODDED_WEATHER_STAGE("OnInitModdedWeatherStage"),

    /**
     * (Server) OnUpdateModdedWeatherStage: Triggered when a modded weather stage tries to be updated.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code WeatherPeriod weatherPeriod}</li>
     *   <li>{@code WeatherPeriod.WeatherStage weatherStage}</li>
     *   <li>{@code number strength}</li>
     * </ul>
     */
    ON_UPDATE_MODDED_WEATHER_STAGE("OnUpdateModdedWeatherStage"),

    /**
     * OnClimateManagerInit: Triggered when the climate manager is initialised.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code ClimateManager climateManager} — The climate manager.</li>
     * </ul>
     */
    ON_CLIMATE_MANAGER_INIT("OnClimateManagerInit"),

    /**
     * (Client) OnPressReloadButton: Triggered when a local player has a gun and presses the button to reload it.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code IsoPlayer player} — The player attempting to reload.</li>
     *   <li>{@code HandWeapon weapon} — The weapon they are attempting to reload.</li>
     * </ul>
     */
    ON_PRESS_RELOAD_BUTTON("OnPressReloadButton"),

    /**
     * (Client) OnPressRackButton: Triggered when a local player has a gun and presses the button to rack it.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code IsoPlayer player} — The player attempting to rack.</li>
     *   <li>{@code HandWeapon weapon} — The weapon they are attempting to rack.</li>
     *   <li>{@code false shift} — Unknown purpose: always false. Added at some point during B42.</li>
     * </ul>
     */
    ON_PRESS_RACK_BUTTON("OnPressRackButton"),

    /**
     * (Client) OnPressWalkTo: Triggered when the local player 1 presses their Walk To keybind.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code 0 arg0} — Always zero.</li>
     *   <li>{@code 0 arg1} — Always zero.</li>
     *   <li>{@code 0 arg2} — Always zero.</li>
     * </ul>
     */
    ON_PRESS_WALK_TO("OnPressWalkTo"),

    /**
     * OnHitZombie: Triggered whenever a zombie is hit by a character.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code IsoZombie zombie} — The zombie that was hit.</li>
     *   <li>{@code IsoGameCharacter attacker} — The character that hit the zombie.</li>
     *   <li>{@code BodyPartType bodyPart} — The type of the body part that was hit.</li>
     *   <li>{@code HandWeapon weapon} — The weapon the zombie was hit with.</li>
     * </ul>
     */
    ON_HIT_ZOMBIE("OnHitZombie"),

    /**
     * Triggered by Project Zomboid Lua event {@code "OnBeingHitByZombie"}.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_BEING_HIT_BY_ZOMBIE("OnBeingHitByZombie"),

    /**
     * (Multiplayer) (Client) OnServerStatisticReceived: Triggered when the MPStatistics have been received from the server.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_SERVER_STATISTIC_RECEIVED("OnServerStatisticReceived"),

    /**
     * (Client) OnDynamicMovableRecipe: Triggered when a local character crafts a dynamically generated Movable scrapping recipe.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code string sprite} — Sprite of the movable.</li>
     *   <li>{@code MovableRecipe recipe} — The movable recipe that was crafted.</li>
     *   <li>{@code Moveable item} — The movable item being scrapped.</li>
     *   <li>{@code IsoGameCharacter character} — The character crafting the recipe.</li>
     * </ul>
     */
    ON_DYNAMIC_MOVABLE_RECIPE("OnDynamicMovableRecipe"),

    /**
     * OnInitGlobalModData: Triggered when GlobalModData is initialised. This is the earliest event after Sandbox Options are loaded.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code boolean newGame} — True if this is the first time the save has started.</li>
     * </ul>
     */
    ON_INIT_GLOBAL_MOD_DATA("OnInitGlobalModData"),

    /**
     * (Multiplayer) OnReceiveGlobalModData: Triggered when receiving a global mod data table.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code string key} — The key of the mod data table that was requested.</li>
     *   <li>{@code table or false data} — The mod data table that was returned. False if there was no mod data table by that key.</li>
     * </ul>
     */
    ON_RECEIVE_GLOBAL_MOD_DATA("OnReceiveGlobalModData"),

    /**
     * OnInitRecordedMedia: Triggered when RecordedMedia is initialised.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code RecordedMedia recordedMedia} — The RecordedMedia object.</li>
     * </ul>
     */
    ON_INIT_RECORDED_MEDIA("OnInitRecordedMedia"),

    /**
     * (Client) onUpdateIcon: Triggered when an ISForageIcon is moved or removed.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code table zoneData}</li>
     *   <li>{@code string iconID}</li>
     *   <li>{@code ISForageIcon icon}</li>
     * </ul>
     */
    ON_UPDATE_ICON("onUpdateIcon"),

    /**
     * preAddForageDefs: Triggered before the foraging system processes any definitions.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code forageSystem system} — The foraging system.</li>
     * </ul>
     */
    PRE_ADD_FORAGE_DEFS("preAddForageDefs"),

    /**
     * preAddSkillDefs: Triggered before the foraging system processes trait and profession definitions.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code forageSystem system} — The foraging system.</li>
     * </ul>
     */
    PRE_ADD_SKILL_DEFS("preAddSkillDefs"),

    /**
     * preAddZoneDefs: Triggered before the foraging system processes zone definitions.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code forageSystem system} — The foraging system.</li>
     * </ul>
     */
    PRE_ADD_ZONE_DEFS("preAddZoneDefs"),

    /**
     * preAddCatDefs: Triggered before the foraging system processes item category definitions.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code forageSystem system} — The foraging system.</li>
     * </ul>
     */
    PRE_ADD_CAT_DEFS("preAddCatDefs"),

    /**
     * preAddItemDefs: Triggered before the foraging system processes item definitions.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code forageSystem system} — The foraging system.</li>
     * </ul>
     */
    PRE_ADD_ITEM_DEFS("preAddItemDefs"),

    /**
     * onAddForageDefs: Triggered after the foraging item definitions are created.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code forageSystem forageSystem} — The foraging system.</li>
     * </ul>
     */
    ON_ADD_FORAGE_DEFS("onAddForageDefs"),

    /**
     * (Client) onFillSearchIconContextMenu: Triggered when opening the context menu for a foraging item.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code ISContextMenu context} — The foraging context menu.</li>
     *   <li>{@code ISBaseIcon icon} — The foraging icon the context menu was created for.</li>
     * </ul>
     */
    ON_FILL_SEARCH_ICON_CONTEXT_MENU("onFillSearchIconContextMenu"),

    /**
     * (Client) onItemFall: Triggered when a local character is forced to drop the items in their hands.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code InventoryItem item} — The item that fell.</li>
     * </ul>
     */
    ON_ITEM_FALL("onItemFall"),

    /**
     * OnTemplateTextInit: Triggered when TemplateText is initialised.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_TEMPLATE_TEXT_INIT("OnTemplateTextInit"),

    /**
     * OnPlayerGetDamage: Triggered every time a local player takes damage. Triggered once per frame by each bleeding body part. Also triggered when zombies are hit by weapons: this is the only case in which the event is triggered on the server.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code IsoGameCharacter character} — The character who took damage.</li>
     *   <li>{@code "POISON" or "HUNGRY" or "SICK" or
     * "BLEEDING" or "THIRST" or "HEAVYLOAD" or
     * "INFECTION" or "LOWWEIGHT" or
     * "FALLDOWN" or "WEAPONHIT" or "CARHITDAMAGE" or
     * "CARCRASHDAMAGE" damageType} — The type of damage the character took.</li>
     *   <li>{@code number damage} — The damage that was taken.</li>
     * </ul>
     */
    ON_PLAYER_GET_DAMAGE("OnPlayerGetDamage"),

    /**
     * (Server) OnWeaponHitThumpable: Triggered when a Thumpable is hit by an attack.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code IsoGameCharacter attacker} — The character attacking the object.</li>
     *   <li>{@code HandWeapon weapon} — The weapon the object was attacked with.</li>
     *   <li>{@code Thumpable object} — The object that was attacked.</li>
     * </ul>
     */
    ON_WEAPON_HIT_THUMPABLE("OnWeaponHitThumpable"),

    /**
     * Triggered by Project Zomboid Lua event {@code "OnFishingActionMPUpdate"}.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_FISHING_ACTION_MP_UPDATE("OnFishingActionMPUpdate"),

    /**
     * OnThrowableExplode: Triggered when a throwable or trap explodes.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code IsoTrap throwable} — The explosive.</li>
     *   <li>{@code IsoGridSquare square} — The square it exploded on.</li>
     * </ul>
     */
    ON_THROWABLE_EXPLODE("OnThrowableExplode"),

    /**
     * OnSourceWindowFileReload: Triggered when a file is reloaded from the debug source viewer.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_SOURCE_WINDOW_FILE_RELOAD("OnSourceWindowFileReload"),

    /**
     * OnSpawnVehicleStart: Trigerred when a vehicle begins spawning, before it has been initialised.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code BaseVehicle vehicle} — Spawning vehicle.</li>
     * </ul>
     */
    ON_SPAWN_VEHICLE_START("OnSpawnVehicleStart"),

    /**
     * OnSpawnVehicleEnd: Triggered when a vehicle finishes spawning.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code BaseVehicle vehicle} — Spawning vehicle.</li>
     * </ul>
     */
    ON_SPAWN_VEHICLE_END("OnSpawnVehicleEnd"),

    /**
     * Triggered by Project Zomboid Lua event {@code "OnMovingObjectCrop"}.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_MOVING_OBJECT_CROP("OnMovingObjectCrop"),

    /**
     * Triggered by Project Zomboid Lua event {@code "OnOverrideSearchManager"}.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_OVERRIDE_SEARCH_MANAGER("OnOverrideSearchManager"),

    /**
     * OnSleepingTick:
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code number playerIndex}</li>
     *   <li>{@code number timeOfDay}</li>
     * </ul>
     */
    ON_SLEEPING_TICK("OnSleepingTick"),

    /**
     * Triggered by Project Zomboid Lua event {@code "OnRolesReceived"}.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_ROLES_RECEIVED("OnRolesReceived"),

    /**
     * Triggered by Project Zomboid Lua event {@code "OnNetworkUsersReceived"}.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_NETWORK_USERS_RECEIVED("OnNetworkUsersReceived"),

    /**
     * Triggered by Project Zomboid Lua event {@code "OnServerCustomizationDataReceived"}.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_SERVER_CUSTOMIZATION_DATA_RECEIVED("OnServerCustomizationDataReceived"),

    /**
     * OnDeadBodySpawn: Triggered when spawning a dead body.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code IsoDeadBody body} — The dead body being spawned.</li>
     * </ul>
     */
    ON_DEAD_BODY_SPAWN("OnDeadBodySpawn"),

    /**
     * Triggered by Project Zomboid Lua event {@code "OnAnimalTracks"}.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_ANIMAL_TRACKS("OnAnimalTracks"),

    /**
     * OnItemFound:
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code IsoPlayer player}</li>
     *   <li>{@code string itemType}</li>
     *   <li>{@code number amount}</li>
     * </ul>
     */
    ON_ITEM_FOUND("OnItemFound"),

    /**
     * (Client) SetDragItem: Triggered before a local player's drag item (typically a build cursor) is set.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code table item} — The drag item being set.</li>
     *   <li>{@code integer playerIndex} — The index of the player whose drag item is being set.</li>
     * </ul>
     */
    SET_DRAG_ITEM("SetDragItem"),

    /**
     * (Client) OnSteamServerResponded: Triggered when receiving a server for the server list.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code integer index}</li>
     * </ul>
     */
    ON_STEAM_SERVER_RESPONDED("OnSteamServerResponded"),

    /**
     * (Client) OnSteamServerResponded2: Triggered when receiving a server for the favourite server list.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code string address}</li>
     *   <li>{@code number port}</li>
     *   <li>{@code Server server}</li>
     * </ul>
     */
    ON_STEAM_SERVER_RESPONDED_2("OnSteamServerResponded2"),

    /**
     * OnSteamServerFailedToRespond2:
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code string host}</li>
     *   <li>{@code number port}</li>
     * </ul>
     */
    ON_STEAM_SERVER_FAILED_TO_RESPOND_2("OnSteamServerFailedToRespond2"),

    /**
     * (Client) OnSteamRulesRefreshComplete: Triggered after a server's rules are retrieved.
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code string address}</li>
     *   <li>{@code number port}</li>
     *   <li>{@code table rules} — Table of information about the server</li>
     * </ul>
     */
    ON_STEAM_RULES_REFRESH_COMPLETE("OnSteamRulesRefreshComplete"),

    /**
     * (Client) OnSteamRefreshInternetServers: Triggered when the steam server list has been refreshed.
     * <p><b>Parameters:</b> None.</p>
     */
    ON_STEAM_REFRESH_INTERNET_SERVERS("OnSteamRefreshInternetServers"),

    /**
     * Triggered by Project Zomboid Lua event {@code "OptionControllerButtonStyleChanged"}.
     * <p><b>Parameters:</b> None.</p>
     */
    OPTION_CONTROLLER_BUTTON_STYLE_CHANGED("OptionControllerButtonStyleChanged"),

    /**
     * Triggered by Project Zomboid Lua event {@code "OptionGamepadBindingPresetChanged"}.
     * <p><b>Parameters:</b> None.</p>
     */
    OPTION_GAMEPAD_BINDING_PRESET_CHANGED("OptionGamepadBindingPresetChanged");

    private final String eventName;
    private static final Map<String, DefaultEvents> LOOKUP_MAP;

    static {
        Map<String, DefaultEvents> map = new HashMap<>();
        for (DefaultEvents event : values()) {
            map.put(event.eventName, event);
        }
        LOOKUP_MAP = Collections.unmodifiableMap(map);
    }

    DefaultEvents(String eventName) {
        this.eventName = eventName;
    }

    /**
     * Retrieves the unique string identifier of this event.
     * <p>
     * Corresponds to the Lua event table key in Project Zomboid (e.g. {@code "OnPlayerUpdate"}).
     *
     * @return The unique, case-sensitive event name
     */
    @Override
    public String getName() {
        return eventName;
    }

    /**
     * Resolves an {@link DefaultEvents} enum by its raw PZ string name.
     *
     * @param eventName The exact case-sensitive string from LuaEventManager
     * @return The matching enum constant, or null if it's a custom/unknown event
     */
    public static DefaultEvents findByName(String eventName) {
        return LOOKUP_MAP.get(eventName);
    }
}