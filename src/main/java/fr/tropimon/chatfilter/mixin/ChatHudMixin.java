package fr.tropimon.chatfilter.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import fr.tropimon.chatfilter.ChatFilterController;
import fr.tropimon.chatfilter.MessageAnalysis;
import fr.tropimon.chatfilter.MessageAnalysisCache;
import fr.tropimon.chatfilter.ChatMessageDecorator;
import fr.tropimon.chatfilter.GlobalFilterSettings;
import fr.tropimon.chatfilter.GroupChatManager;
import fr.tropimon.chatfilter.PlayerHeadCache;
import fr.tropimon.chatfilter.PrivateChatManager;
import fr.tropimon.chatfilter.StaffChatNotifications;
import fr.tropimon.chatfilter.TownChatNotifications;
import fr.tropimon.chatfilter.TeleportRequestManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = ChatHud.class, priority = 900)
abstract class ChatHudMixin {
    @Unique
    private static final int PLAYER_ICON_SIZE = 7;
    @Unique
    private static final int PLAYER_ICON_Y_OFFSET = 0;

    @Inject(
            method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD"), cancellable = true)
    private void tropimonChatFilter$routeWithoutHiddenAnimation(
            Text message, MessageSignatureData signature, MessageIndicator indicator,
            CallbackInfo ci) {
        TeleportRequestManager.observe(message);
        MessageAnalysis analysis = MessageAnalysisCache.get(message);
        if (analysis.channelConfirmation()) {
            ci.cancel();
            return;
        }
        GroupChatManager.Observation group = GroupChatManager.observeNetwork(message, analysis);
        if (group.consumed()) {
            if (group.display() != null) {
                storeSynthetic(group.display(), signature, indicator);
            }
            if (group.incoming()) {
                ChatFilterController.previewGroupIncoming();
            }
            ci.cancel();
            return;
        }
        boolean privateMessage = PrivateChatManager.observe(analysis);
        TownChatNotifications.observe(analysis, privateMessage);
        StaffChatNotifications.observe(analysis);
        ChatFilterController.observeIncoming(message, analysis);
        ChatFilterController.previewIncoming(analysis);

        ChatHudLine line = new ChatHudLine(
                net.minecraft.client.MinecraftClient.getInstance().inGameHud.getTicks(),
                message, signature, indicator);
        if (ChatFilterController.shouldShow(line)) {
            return;
        }

        Text decorated = ChatMessageDecorator.decorate(message);
        line = new ChatHudLine(line.creationTick(), decorated, signature, indicator);
        ChatHudAccessor self = (ChatHudAccessor) this;
        self.tropimonChatFilter$logMessage(line);
        self.tropimonChatFilter$storeMessage(line);
        ci.cancel();
    }

    @ModifyVariable(
            method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Text tropimonChatFilter$makeUsernameClickable(Text message) {
        return ChatMessageDecorator.decorate(message);
    }

    @Inject(method = "addVisibleMessage", at = @At("HEAD"), cancellable = true)
    private void tropimonChatFilter$filterVisibleMessage(ChatHudLine line, CallbackInfo ci) {
        if (!ChatFilterController.shouldShow(line)) {
            ci.cancel();
        }
    }

    @Redirect(
            method = "addVisibleMessage",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/util/ChatMessages;breakRenderedChatMessageLines(Lnet/minecraft/text/StringVisitable;ILnet/minecraft/client/font/TextRenderer;)Ljava/util/List;"))
    private List<OrderedText> tropimonChatFilter$wrapDecoratedMessage(
            StringVisitable message, int width, TextRenderer renderer) {
        return ChatMessageDecorator.wrapLines(message, width, renderer);
    }

    @ModifyExpressionValue(
            method = "render",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/hud/ChatHudLine$Visible;addedTime()I"))
    private int tropimonChatFilter$keepMessagesVisible(int addedTime) {
        if (!GlobalFilterSettings.keepChatVisible()) {
            return addedTime;
        }
        return MinecraftClient.getInstance().inGameHud.getTicks();
    }

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;III)I",
                    ordinal = 0))
    private int tropimonChatFilter$renderTextAndSynchronizedHead(
            DrawContext context, TextRenderer renderer, OrderedText line,
            int x, int y, int color) {
        if (GlobalFilterSettings.showPlayerHeads()) {
            String speaker = ChatMessageDecorator.headSpeaker(line);
            var texture = PlayerHeadCache.findTexture(speaker);
            if (texture != null) {
                float alpha = ((color >>> 24) & 0xFF) / 255.0F;
                context.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
                int headX = x + ChatMessageDecorator.headX(MinecraftClient.getInstance());
                int headY = y + PLAYER_ICON_Y_OFFSET;
                context.drawTexture(texture, headX, headY,
                        8.0F, 8.0F, PLAYER_ICON_SIZE, PLAYER_ICON_SIZE, 64, 64);
                context.drawTexture(texture, headX, headY,
                        40.0F, 8.0F, PLAYER_ICON_SIZE, PLAYER_ICON_SIZE, 64, 64);
                context.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            }
        }
        return context.drawTextWithShadow(renderer, line, x, y, color);
    }

    private void storeSynthetic(
            Text message, MessageSignatureData signature, MessageIndicator indicator) {
        Text decorated = ChatMessageDecorator.decorate(message);
        ChatHudLine line = new ChatHudLine(
                MinecraftClient.getInstance().inGameHud.getTicks(),
                decorated, signature, indicator);
        ChatHudAccessor self = (ChatHudAccessor) this;
        self.tropimonChatFilter$logMessage(line);
        self.tropimonChatFilter$storeMessage(line);
    }
}
