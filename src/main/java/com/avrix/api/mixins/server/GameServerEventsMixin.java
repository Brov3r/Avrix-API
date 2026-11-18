package com.avrix.api.mixins.server;

import com.avrix.api.events.EventManager;
import com.avrix.api.events.PzEventBridge;
import com.avrix.api.events.ServerEvents;
import com.avrix.api.lua.script.LuaScriptLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.characters.IsoPlayer;
import zombie.core.network.ByteBufferReader;
import zombie.core.raknet.UdpConnection;
import zombie.network.GameServer;
import zombie.network.IConnection;
import zombie.network.ServerWorldDatabase;
import zombie.network.ZomboidNetData;

/**
 * Mixin for {@link GameServer} injecting hooks into critical server-side networking,
 * player lifecycle, and command execution pipelines.
 */
@Mixin(GameServer.class)
public class GameServerEventsMixin {

    /**
     * Prevents instantiation of the mixin class.
     */
    private GameServerEventsMixin() {
        throw new UnsupportedOperationException("Mixin classes cannot be instantiated");
    }

    /**
     * Injects into {@link GameServer#receiveClientConnect(UdpConnection, ServerWorldDatabase.LogonResult)}
     * after a free slot has been assigned to the client.
     *
     * @param connection issuing server connection
     * @param result     logon result produced by the database
     * @param callback   injection callback provided by the mixin framework
     */
    @Inject(
            method = "receiveClientConnect(Lzombie/core/raknet/UdpConnection;"
                    + "Lzombie/network/ServerWorldDatabase$LogonResult;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lzombie/core/raknet/UdpConnection;"
                            + "setPlayerDownloadServer(Lzombie/network/PlayerDownloadServer;)V",
                    shift = At.Shift.AFTER
            )
    )
    private static void onReceiveClientConnect(
            UdpConnection connection, ServerWorldDatabase.LogonResult result, CallbackInfo callback
    ) {
        EventManager.invoke(ServerEvents.CLIENT_CONNECT.getEventName(), connection, result);
    }

    /**
     * Injects into {@link GameServer#receivePlayerConnect(ByteBufferReader, IConnection, String)}
     * after the player has finished connecting and is available in the world.
     *
     * @param reader     packet payload reader
     * @param connection issuing connection
     * @param username   name of the connecting player
     * @param callback   injection callback provided by the mixin framework
     */
    @Inject(
            method = "receivePlayerConnect(Lzombie/core/network/ByteBufferReader;"
                    + "Lzombie/network/IConnection;Ljava/lang/String;)V",
            at = @At("TAIL")
    )
    private static void onPlayerFullyConnected(
            ByteBufferReader reader, IConnection connection, String username, CallbackInfo callback
    ) {
        if (connection instanceof UdpConnection udpConnection && udpConnection.players != null) {
            for (IsoPlayer player : udpConnection.players) {
                if (player != null && username != null && username.equalsIgnoreCase(player.getUsername())) {
                    EventManager.invoke(ServerEvents.PLAYER_CONNECTED.getEventName(), player, udpConnection);
                    break;
                }
            }
        }
    }

    /**
     * Injects into {@link GameServer#disconnectPlayer(IsoPlayer, IConnection)} when a player character
     * is about to be despawned and removed from the active session.
     *
     * @param player     disconnecting player
     * @param connection issuing connection
     * @param callback   injection callback provided by the mixin framework
     */
    @Inject(
            method = "disconnectPlayer(Lzombie/characters/IsoPlayer;Lzombie/network/IConnection;)V",
            at = @At("HEAD")
    )
    private static void onPlayerDisconnect(IsoPlayer player, IConnection connection, CallbackInfo callback) {
        if (player != null) {
            EventManager.invoke(ServerEvents.PLAYER_DISCONNECT.getEventName(), player, connection);
        }
    }

    /**
     * Injects into {@link GameServer#disconnect(UdpConnection, String)} before the connection is cleaned up.
     *
     * @param connection disconnected client connection
     * @param reason     disconnect reason
     * @param callback   injection callback provided by the mixin framework
     */
    @Inject(
            method = "disconnect(Lzombie/core/raknet/UdpConnection;Ljava/lang/String;)V",
            at = @At("HEAD")
    )
    private static void onClientDisconnect(UdpConnection connection, String reason, CallbackInfo callback) {
        if (connection != null) {
            EventManager.invoke(ServerEvents.CLIENT_DISCONNECT.getEventName(), connection, reason);
        }
    }

    /**
     * Drains Java events and late Lua loads that were queued from non-main threads.
     *
     * @param data received network data
     * @param callback injection callback provided by the mixin framework
     */
    @Inject(
            method = "mainLoopDealWithNetData(Lzombie/network/ZomboidNetData;)V",
            at = @At("HEAD")
    )
    private static void drainQueuedJavaEvents(ZomboidNetData data, CallbackInfo callback) {
        PzEventBridge.drain();
        LuaScriptLoader.drainLate();
    }
}
