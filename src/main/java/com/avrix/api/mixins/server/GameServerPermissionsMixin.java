package com.avrix.api.mixins.server;

import com.avrix.api.permissions.PermissionsManager;
import net.lenni0451.classtransform.annotations.CTarget;
import net.lenni0451.classtransform.annotations.CTransformer;
import net.lenni0451.classtransform.annotations.injection.CInject;
import zombie.characters.IsoPlayer;
import zombie.core.network.ByteBufferReader;
import zombie.core.raknet.UdpConnection;
import zombie.network.GameServer;
import zombie.network.IConnection;

/**
 * ClassTransform mixin for {@link GameServer} injecting hooks
 */
@CTransformer(value = GameServer.class)
public class GameServerPermissionsMixin {

    /**
     * Prevents instantiation of the transformer class.
     */
    private GameServerPermissionsMixin() {
        throw new UnsupportedOperationException("Mixin classes cannot be instantiated");
    }

    /**
     * Injects into {@link GameServer#receivePlayerConnect(ByteBufferReader, IConnection, String)}
     * right after the player is marked as fully connected and added to the world.
     */
    @CInject(
            method = "receivePlayerConnect(Lzombie/core/network/ByteBufferReader;Lzombie/network/IConnection;Ljava/lang/String;)V",
            target = @CTarget(
                    value = "INVOKE",
                    target = "Lzombie/network/IConnection;setFullyConnected()V",
                    shift = CTarget.Shift.AFTER
            )
    )
    private static void onPlayerFullyConnected(ByteBufferReader bb, IConnection connection, String username) {
        if (connection instanceof UdpConnection udpCon && udpCon.players != null) {
            for (IsoPlayer player : udpCon.players) {
                if (player != null && username.equalsIgnoreCase(player.getUsername())) {
                    PermissionsManager.syncPlayerLoginRole(player, udpCon);
                }
            }
        }
    }

    /**
     * Injects into the dedicated-server main loop to process queued expiry work on the server thread.
     */
    @CInject(
            method = "main([Ljava/lang/String;)V",
            target = @CTarget(
                    value = "INVOKE",
                    target = "Lzombie/network/RCONServer;update()V",
                    shift = CTarget.Shift.AFTER
            )
    )
    private static void onServerTick() {
        PermissionsManager.processPendingExpiryTasks();
    }
}