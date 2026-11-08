package com.avrix.api.mixins.common;

import com.avrix.api.network.NetworkSide;
import com.avrix.api.network.PacketReceiveDispatcher;
import net.lenni0451.classtransform.annotations.CTarget;
import net.lenni0451.classtransform.annotations.CTransformer;
import net.lenni0451.classtransform.annotations.injection.CInject;
import zombie.core.network.ByteBufferReader;
import zombie.core.raknet.UdpConnection;
import zombie.network.PacketTypes;

/**
 * ClassTransform transformer that publishes an Avrix event before a native packet is processed.
 */
@CTransformer(value = PacketTypes.PacketType.class)
public class PacketReceiveMixin {

    /**
     * Prevents instantiation of the transformer class.
     */
    private PacketReceiveMixin() {
        throw new UnsupportedOperationException("Mixin classes cannot be instantiated");
    }

    /**
     * Publishes a server-side packet received event.
     *
     * @param reader     packet payload reader
     * @param connection sending client connection
     */
    @CInject(
            method = "onServerPacket(Lzombie/core/network/ByteBufferReader;Lzombie/core/raknet/UdpConnection;)V",
            target = @CTarget("HEAD")
    )
    private void onServerPacketReceived(ByteBufferReader reader, UdpConnection connection) {
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
     * @param reader packet payload reader
     */
    @CInject(
            method = "onClientPacket(Lzombie/core/network/ByteBufferReader;)V",
            target = @CTarget("HEAD")
    )
    private void onClientPacketReceived(ByteBufferReader reader) {
        PacketReceiveDispatcher.dispatch(
                (PacketTypes.PacketType) (Object) this,
                NetworkSide.CLIENT,
                null,
                reader
        );
    }
}
