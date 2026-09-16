package fr.tropimon.chatfilter.mixin;

import fr.tropimon.chatfilter.PartyShareInput;
import fr.tropimon.chatfilter.TeleportPopupPosition;
import fr.tropimon.chatfilter.TeleportPopupRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
abstract class MouseMixin {
    @Shadow private double x;
    @Shadow private double y;

    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void tropimonChatFilter$teleportPopupClick(
            long window, int button, int action, int mods, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (window != client.getWindow().getHandle() || button != 0) {
            return;
        }
        if (action == 0) {
            if (TeleportPopupPosition.stopDrag()) {
                if (client.currentScreen instanceof ChatScreen) {
                    ci.cancel();
                }
                return;
            }
            if (client.currentScreen instanceof ChatScreen chatScreen
                    && PartyShareInput.draggedSlot() != 0) {
                double scaledX = tropimonChatFilter$scaledX(client, x);
                double scaledY = tropimonChatFilter$scaledY(client, y);
                var field = ((ChatScreenAccessor) chatScreen)
                        .tropimonChatFilter$chatField();
                int partySlot = PartyShareInput.finishDrag(
                        scaledX, scaledY, field.isMouseOver(scaledX, scaledY));
                if (partySlot != 0) {
                    field.write(PartyShareInput.tag(partySlot));
                    field.setFocused(true);
                }
                ci.cancel();
                return;
            }
            return;
        }
        if (action != 1 || !(client.currentScreen instanceof ChatScreen)
                || client.options.hudHidden) {
            return;
        }
        double scaledX = tropimonChatFilter$scaledX(client, x);
        double scaledY = tropimonChatFilter$scaledY(client, y);
        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();
        if (TeleportPopupRenderer.click(scaledX, scaledY, width, height)
                || TeleportPopupPosition.startDrag(scaledX, scaledY, width, height)) {
            ci.cancel();
            return;
        }
    }

    @Inject(method = "onCursorPos", at = @At("HEAD"), cancellable = true)
    private void tropimonChatFilter$teleportPopupDrag(
            long window, double mouseX, double mouseY, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (window != client.getWindow().getHandle()) {
            return;
        }
        if (!(client.currentScreen instanceof ChatScreen) || client.options.hudHidden) {
            TeleportPopupPosition.stopDrag();
            PartyShareInput.cancelDrag();
            return;
        }
        if (TeleportPopupPosition.drag(
                mouseX * client.getWindow().getScaledWidth() / client.getWindow().getWidth(),
                mouseY * client.getWindow().getScaledHeight() / client.getWindow().getHeight(),
                client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight())) {
            x = mouseX;
            y = mouseY;
            ci.cancel();
        }
    }

    @Unique
    private static double tropimonChatFilter$scaledX(
            MinecraftClient client, double mouseX) {
        return mouseX * client.getWindow().getScaledWidth() / client.getWindow().getWidth();
    }

    @Unique
    private static double tropimonChatFilter$scaledY(
            MinecraftClient client, double mouseY) {
        return mouseY * client.getWindow().getScaledHeight() / client.getWindow().getHeight();
    }
}
