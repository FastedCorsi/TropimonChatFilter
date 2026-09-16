package fr.tropimon.chatfilter.mixin;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(ChatHud.class)
public interface ChatHudAccessor {
    @Accessor("messages")
    List<ChatHudLine> tropimonChatFilter$messages();

    @Accessor("visibleMessages")
    List<ChatHudLine.Visible> tropimonChatFilter$visibleMessages();

    @Invoker("refresh")
    void tropimonChatFilter$refresh();

    @Invoker("logChatMessage")
    void tropimonChatFilter$logMessage(ChatHudLine line);

    @Invoker("addMessage")
    void tropimonChatFilter$storeMessage(ChatHudLine line);

    @Invoker("toChatLineX")
    double tropimonChatFilter$toChatLineX(double mouseX);

    @Invoker("toChatLineY")
    double tropimonChatFilter$toChatLineY(double mouseY);

    @Invoker("getMessageLineIndex")
    int tropimonChatFilter$getMessageLineIndex(double chatX, double chatY);

}
