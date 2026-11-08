package com.avrix.api.mixins.server;

import com.avrix.api.events.EventManager;
import com.avrix.api.events.PzEventBridge;
import com.avrix.api.events.ServerEvents;
import net.lenni0451.classtransform.annotations.CTarget;
import net.lenni0451.classtransform.annotations.CTransformer;
import net.lenni0451.classtransform.annotations.injection.CInject;
import zombie.characters.IsoPlayer;
import zombie.core.network.ByteBufferReader;
import zombie.core.raknet.UdpConnection;
import zombie.network.GameServer;
import zombie.network.IConnection;
import zombie.network.ServerWorldDatabase;
import zombie.network.ZomboidNetData;

/**
 * ClassTransform mixin for {@link GameServer} injecting hooks into critical server-side
 * networking, player lifecycle, and command execution pipelines.
 */
@CTransformer(value = GameServer.class)
public class GameServerEventsMixin {
    /**
     * Injects into {@link GameServer#receiveClientConnect(UdpConnection, ServerWorldDatabase.LogonResult)}
     * after a free slot has been assigned to the client.
     */
    @CInject(
            method = "receiveClientConnect(Lzombie/core/raknet/UdpConnection;Lzombie/network/ServerWorldDatabase$LogonResult;)V",
            target = @CTarget(
                    value = "INVOKE",
                    target = "Lzombie/core/raknet/UdpConnection;setPlayerDownloadServer(Lzombie/network/PlayerDownloadServer;)V",
                    shift = CTarget.Shift.AFTER
            )
    )
    private static void onReceiveClientConnect(UdpConnection connection, ServerWorldDatabase.LogonResult result) {
        EventManager.invoke(ServerEvents.CLIENT_CONNECT.getEventName(), connection, result);
    }

    /**
     * Injects into {@link GameServer#receivePlayerConnect(ByteBufferReader, IConnection, String)}
     * after the player has finished connecting and is available in the world.
     */
    @CInject(
            method = "receivePlayerConnect(Lzombie/core/network/ByteBufferReader;Lzombie/network/IConnection;Ljava/lang/String;)V",
            target = @CTarget("TAIL")
    )
    private static void onPlayerFullyConnected(ByteBufferReader bb, IConnection connection, String username) {
        if (connection instanceof UdpConnection udpCon && udpCon.players != null) {
            for (IsoPlayer player : udpCon.players) {
                if (player != null && username != null && username.equalsIgnoreCase(player.getUsername())) {
                    EventManager.invoke(ServerEvents.PLAYER_CONNECTED.getEventName(), player, udpCon);
                    break;
                }
            }
        }
    }

    /**
     * Injects into {@link GameServer#disconnectPlayer(IsoPlayer, IConnection)} when a player character
     * is about to be despawned and removed from the active session.
     */
    @CInject(
            method = "disconnectPlayer(Lzombie/characters/IsoPlayer;Lzombie/network/IConnection;)V",
            target = @CTarget("HEAD")
    )
    private static void onPlayerDisconnect(IsoPlayer player, IConnection connection) {
        if (player != null) {
            EventManager.invoke(ServerEvents.PLAYER_DISCONNECT.getEventName(), player, connection);
        }
    }

    /**
     * Injects into {@link GameServer#disconnect(UdpConnection, String)} before the connection is cleaned up.
     */
    @CInject(
            method = "disconnect(Lzombie/core/raknet/UdpConnection;Ljava/lang/String;)V",
            target = @CTarget("HEAD")
    )
    private static void onClientDisconnect(UdpConnection connection, String reason) {
        if (connection != null) {
            EventManager.invoke(ServerEvents.CLIENT_DISCONNECT.getEventName(), connection, reason);
        }
    }

    /**
     * Drains Java events that were queued by game events triggered from non-main threads.
     */
    @CInject(
            method = "mainLoopDealWithNetData(Lzombie/network/ZomboidNetData;)V",
            target = @CTarget("HEAD")
    )
    private static void drainQueuedJavaEvents(ZomboidNetData data) {
        PzEventBridge.drain();
    }
}