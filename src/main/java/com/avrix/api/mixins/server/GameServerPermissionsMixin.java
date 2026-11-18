package com.avrix.api.mixins.server;

import com.avrix.api.permissions.PermissionsManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.characters.IsoPlayer;
import zombie.core.network.ByteBufferReader;
import zombie.core.raknet.UdpConnection;
import zombie.network.GameServer;
import zombie.network.IConnection;

/**
 * Mixin for {@link GameServer} injecting permission synchronization hooks.
 */
@Mixin(GameServer.class)
public class GameServerPermissionsMixin {

    /**
     * Prevents instantiation of the mixin class.
     */
    private GameServerPermissionsMixin() {
        throw new UnsupportedOperationException("Mixin classes cannot be instantiated");
    }

    /**
     * Injects into {@link GameServer#receivePlayerConnect(ByteBufferReader, IConnection, String)}
     * right after the player is marked as fully connected and added to the world.
     *
     * @param reader     packet payload reader
     * @param connection issuing connection
     * @param username   name of the connecting player
     * @param callback   injection callback provided by the mixin framework
     */
    @Inject(
            method = "receivePlayerConnect(Lzombie/core/network/ByteBufferReader;"
                    + "Lzombie/network/IConnection;Ljava/lang/String;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lzombie/network/IConnection;setFullyConnected()V",
                    shift = At.Shift.AFTER
            )
    )
    private static void onPlayerFullyConnected(
            ByteBufferReader reader, IConnection connection, String username, CallbackInfo callback
    ) {
        if (connection instanceof UdpConnection udpConnection && udpConnection.players != null) {
            for (IsoPlayer player : udpConnection.players) {
                if (player != null && username.equalsIgnoreCase(player.getUsername())) {
                    PermissionsManager.syncPlayerLoginRole(player, udpConnection);
                }
            }
        }
    }

    /**
     * Injects into the dedicated-server main loop to process queued expiry work on the server thread.
     *
     * @param arguments command-line arguments passed to the server
     * @param callback  injection callback provided by the mixin framework
     */
    @Inject(
            method = "main([Ljava/lang/String;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lzombie/network/RCONServer;update()V",
                    shift = At.Shift.AFTER
            )
    )
    private static void onServerTick(String[] arguments, CallbackInfo callback) {
        PermissionsManager.processPendingExpiryTasks();
    }
}
