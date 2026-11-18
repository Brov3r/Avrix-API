package com.avrix.api.mixins.server;

import com.avrix.api.commands.CommandManager;
import com.avrix.api.commands.CommandSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zombie.core.raknet.UdpConnection;
import zombie.network.GameServer;

/**
 * Mixin prioritizing Avrix custom commands over vanilla Project Zomboid commands.
 */
@Mixin(GameServer.class)
public class GameServerCommandsMixin {

    /**
     * Prevents instantiation of the mixin class.
     */
    private GameServerCommandsMixin() {
        throw new UnsupportedOperationException("Mixin classes cannot be instantiated");
    }

    /**
     * Dispatches the command to the Avrix {@link CommandManager} before the vanilla handler runs.
     * <p>
     * When the Avrix manager does not recognize the command, the vanilla Project Zomboid
     * implementation continues as usual.
     *
     * @param input      raw command input
     * @param connection issuing server connection
     * @param callback   injection callback used to return the Avrix command result
     */
    @Inject(
            method = "handleServerCommand(Ljava/lang/String;"
                    + "Lzombie/core/raknet/UdpConnection;)Ljava/lang/String;",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void dispatchAvrixCommand(
            String input, UdpConnection connection, CallbackInfoReturnable<String> callback
    ) {
        if (input == null) {
            callback.setReturnValue(null);
            return;
        }

        String avrixResult = CommandManager.dispatchCommand(input, CommandSource.of(connection));
        if (avrixResult != null) {
            callback.setReturnValue(avrixResult);
        }
    }
}
