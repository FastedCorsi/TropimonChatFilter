package fr.tropimon.chatfilter.mixin;

import fr.tropimon.chatfilter.TropimonTownProfile;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.handler.DecoderHandler;
import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/** Passive observation: no codec registration, packet replacement, cancellation or foreign fields. */
@Mixin(DecoderHandler.class)
abstract class DecoderHandlerMixin {
    @WrapOperation(method = "decode", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/codec/PacketCodec;decode(Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object tropimonChatFilter$observe(PacketCodec<?, ?> codec, Object input,
            Operation<Object> original, ChannelHandlerContext context, ByteBuf buffer, List<Object> output) {
        var pending = TropimonTownProfile.inspectFrame((ByteBuf) input);
        Object decoded = original.call(codec, input);
        if (pending != null && decoded instanceof CustomPayloadS2CPacket packet) {
            pending.accept(context.pipeline().get(ClientConnection.class), packet.payload().getId().id().toString());
        }
        return decoded;
    }
}
