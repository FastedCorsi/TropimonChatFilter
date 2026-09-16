package fr.tropimon.chatfilter.mixin;

import fr.tropimon.chatfilter.ChatMessageClassifier;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Bloque l'accusé /chat avant qu'un autre mod puisse le recopier dans le HUD. */
@Mixin(value = ClientPlayNetworkHandler.class, priority = 2000)
abstract class ClientPlayNetworkHandlerMixin {
    @Inject(method = "onGameMessage", at = @At("HEAD"), cancellable = true)
    private void tropimonChatFilter$hideServerChannelConfirmation(
            GameMessageS2CPacket packet, CallbackInfo ci) {
        if (!packet.overlay()
                && ChatMessageClassifier.isChannelChangeConfirmation(
                        packet.content().getString())) {
            ci.cancel();
        }
    }
}
