package fr.tropimon.chatfilter.mixin;

import fr.tropimon.chatfilter.ChatFilterController;
import fr.tropimon.chatfilter.GroupChatManager;
import fr.tropimon.chatfilter.StaffStatus;
import fr.tropimon.chatfilter.MessageAnalysisCache;
import fr.tropimon.chatfilter.TropimonTownProfile;
import fr.tropimon.chatfilter.TownChatNotifications;
import fr.tropimon.chatfilter.RenderCacheEpoch;
import fr.tropimon.chatfilter.TeleportRequestManager;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
abstract class MinecraftClientMixin {
    @Inject(method = "onFontOptionsChanged", at = @At("TAIL"))
    private void tropimonChatFilter$invalidateFontMeasurements(CallbackInfo ci) {
        RenderCacheEpoch.invalidate();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void tropimonChatFilter$tickPreviewTimeout(CallbackInfo ci) {
        MessageAnalysisCache.refreshSession();
        TropimonTownProfile.refreshSession();
        TownChatNotifications.refreshFromProfile();
        GroupChatManager.tick();
        StaffStatus.refresh();
        ChatFilterController.tickPreview();
        TeleportRequestManager.tick();
    }
}
