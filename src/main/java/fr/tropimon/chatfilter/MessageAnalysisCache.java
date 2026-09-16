package fr.tropimon.chatfilter;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Language;

/** Cache client-thread uniquement. Une reconnexion ou un rechargement de langue l'invalide. */
public final class MessageAnalysisCache {
    private static final StableMessageCache<Text> CACHE = new StableMessageCache<>(4096, Text::getString);

    private MessageAnalysisCache() { }

    public static MessageAnalysis get(Text text) {
        refreshSession();
        return CACHE.get(ChatMessageDecorator.original(text));
    }

    public static void refreshSession() {
        MinecraftClient client = MinecraftClient.getInstance();
        Object current = client.getNetworkHandler();
        String player = client.player == null ? null : client.player.getGameProfile().getName();
        Language currentLanguage = Language.getInstance();
        if (CACHE.context(current, currentLanguage, player)) {
            PrivateMessageParser.clearCache();
            ChatMessageDecorator.invalidateCachedPresentation();
            RenderCacheEpoch.invalidate();
        }
    }
}
