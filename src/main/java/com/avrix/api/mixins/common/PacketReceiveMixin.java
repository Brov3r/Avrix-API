package com.avrix.api.mixins.common;

import com.avrix.api.network.NetworkSide;
import com.avrix.api.network.PacketReceiveDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.core.network.ByteBufferReader;
import zombie.core.raknet.UdpConnection;
import zombie.network.PacketTypes;

/**
 * Mixin that publishes an Avrix event before a native packet is processed.
 */
@Mixin(PacketTypes.PacketType.class)
public class PacketReceiveMixin {

    /**
     * Prevents instantiation of the mixin class.
     */
    private PacketReceiveMixin() {
        throw new UnsupportedOperationException("Mixin classes cannot be instantiated");
    }

    /**
     * Publishes a server-side packet received event.
     *
     * @param reader     packet payload reader
     * @param connection sending client connection
     * @param callback   injection callback provided by the mixin framework
     */
    @Inject(
            method = "onServerPacket(Lzombie/core/network/ByteBufferReader;"
                    + "Lzombie/core/raknet/UdpConnection;)V",
            at = @At("HEAD")
    )
    private void onServerPacketReceived(
            ByteBufferReader reader, UdpConnection connection, CallbackInfo callback
    ) {
        PacketReceiveDispatcher.dispatch(
                (PacketTypes.PacketType) (Object) this,
                NetworkSide.SERVER,
                connection,
                reader
        );
    }

    /**
     * Publishes a client-side packet received event.
     *
     * @param reader   packet payload reader
     * @param callback injection callback provided by the mixin framework
     */
    @Inject(
            method = "onClientPacket(Lzombie/core/network/ByteBufferReader;)V",
            at = @At("HEAD")
    )
    private void onClientPacketReceived(ByteBufferReader reader, CallbackInfo callback) {
        PacketReceiveDispatcher.dispatch(
                (PacketTypes.PacketType) (Object) this,
                NetworkSide.CLIENT,
                null,
                reader
        );
    }
}
