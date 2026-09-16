package fr.tropimon.chatfilter;

import fr.tropimon.chatfilter.mixin.ChatHudAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.util.ChatMessages;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.util.Formatting;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class ChatMessageDecorator {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.systemDefault());
    // 8 px pour la tête et environ 4 px de marge avant les glyphes de grade Tropimon.
    private static final String HEAD_SPACE = "   ";
    private static final Map<Text, Metadata> METADATA = new IdentityHashMap<>();
    // Derived render objects have their own hard limit, even with extended chat history.
    private static final BoundedIdentityCache<Metadata, CachedPresentation> PRESENTATIONS =
            new BoundedIdentityCache<>(4096);
    private static int nextMetadataPrune = 1_024;
    // Une tête appartient à une ligne rendue précise, jamais à sa position dans la liste.
    private static final Map<OrderedText, String> LINE_SPEAKERS = new WeakHashMap<>();
    private static Object headFontEpoch;
    private static int timestampWidth;

    private ChatMessageDecorator() {
    }

    public static Text decorate(Text source) {
        Metadata metadata = METADATA.computeIfAbsent(
                source, ignored -> new Metadata(source, System.currentTimeMillis()));
        if (!GlobalFilterSettings.showTimestamps()
                && !GlobalFilterSettings.showPlayerHeads()) {
            pruneMetadata(source, metadata);
            return metadata.original();
        }

        Text result = presentation(metadata).decorated();
        METADATA.put(result, metadata);
        pruneMetadata(result, metadata);
        return result;
    }

    /**
     * Découpe le contenu sans faire déborder les lignes de continuation sous
     * l'heure et la tête. Toutes les lignes d'un même message gardent ainsi le
     * même retrait, quelle que soit leur quantité.
     */
    public static List<OrderedText> wrapLines(
            StringVisitable source, int width, TextRenderer renderer) {
        if (!(source instanceof Text text)) {
            return ChatMessages.breakRenderedChatMessageLines(source, width, renderer);
        }
        Metadata metadata = METADATA.get(text);
        if (metadata == null || !GlobalFilterSettings.showTimestamps()
                && !GlobalFilterSettings.showPlayerHeads()) {
            return ChatMessages.breakRenderedChatMessageLines(source, width, renderer);
        }

        Text prefix = decorationPrefix(metadata);
        int spaceWidth = Math.max(1, renderer.getWidth(" "));
        int prefixWidth = renderer.getWidth(prefix);
        int indentSpaces = Math.max(1, (prefixWidth + spaceWidth - 1) / spaceWidth);
        String indent = " ".repeat(indentSpaces);
        int indentWidth = renderer.getWidth(indent);
        int contentWidth = Math.max(1, width - indentWidth);
        List<OrderedText> contentLines = ChatMessages.breakRenderedChatMessageLines(
                metadata.original(), contentWidth, renderer);
        if (contentLines.isEmpty()) {
            return List.of(prefix.asOrderedText());
        }

        List<OrderedText> result = new ArrayList<>(contentLines.size());
        result.add(OrderedText.concat(prefix.asOrderedText(), contentLines.getFirst()));
        // ChatMessages ajoute déjà une espace à chaque ligne de continuation.
        OrderedText continuationIndent = Text.literal(" ".repeat(
                Math.max(0, indentSpaces - 1))).asOrderedText();
        for (int index = 1; index < contentLines.size(); index++) {
            result.add(OrderedText.concat(continuationIndent, contentLines.get(index)));
        }
        if (GlobalFilterSettings.showPlayerHeads()) {
            MessageAnalysisCache.get(metadata.original()).speaker()
                    .ifPresent(speaker -> LINE_SPEAKERS.put(result.getFirst(), speaker));
        }
        return result;
    }

    public static String headSpeaker(OrderedText line) {
        return LINE_SPEAKERS.get(line);
    }

    static void invalidateCachedPresentation() {
        // Keep originals/arrival times for retained history; drop only derived cached Texts.
        PRESENTATIONS.clear();
        headFontEpoch = null;
    }

    private static Text decorationPrefix(Metadata metadata) {
        CachedPresentation presentation = presentation(metadata);
        presentation.updateDecoration();
        return presentation.prefix;
    }

    private static CachedPresentation presentation(Metadata metadata) {
        return PRESENTATIONS.get(metadata, CachedPresentation::new);
    }

    public static Text original(Text text) {
        Metadata metadata = METADATA.get(text);
        return metadata == null ? text : metadata.original();
    }

    public static Presentation stripPresentation(String rendered) {
        String value = rendered == null ? "" : rendered;
        int offset = 0;
        if (value.matches("^\\[\\d{2}:\\d{2}] .*")) {
            offset = 8;
        }
        if (GlobalFilterSettings.showPlayerHeads()
                && value.length() >= offset + HEAD_SPACE.length()
                && value.startsWith(HEAD_SPACE, offset)) {
            offset += HEAD_SPACE.length();
        }
        return new Presentation(value.substring(Math.min(offset, value.length())), offset);
    }

    public static int headX(MinecraftClient client) {
        if (!GlobalFilterSettings.showTimestamps()) return 1;
        Object epoch = RenderCacheEpoch.current();
        if (headFontEpoch != epoch) {
            timestampWidth = client.textRenderer.getWidth("[00:00] ");
            headFontEpoch = epoch;
        }
        return timestampWidth + 1;
    }

    public static void redecorateHistory() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.inGameHud == null) {
            return;
        }
        List<ChatHudLine> messages = ((ChatHudAccessor) client.inGameHud.getChatHud())
                .tropimonChatFilter$messages();
        for (int index = 0; index < messages.size(); index++) {
            ChatHudLine line = messages.get(index);
            Text decorated = decorate(line.content());
            messages.set(index, new ChatHudLine(
                    line.creationTick(), decorated, line.signature(), line.indicator()));
        }
        ChatFilterController.refreshView();
    }

    private static void pruneMetadata(Text current, Metadata currentMetadata) {
        if (METADATA.size() < nextMetadataPrune) {
            return;
        }
        Map<Text, Metadata> retained = new IdentityHashMap<>();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.inGameHud != null) {
            for (ChatHudLine line : ((ChatHudAccessor) client.inGameHud.getChatHud())
                    .tropimonChatFilter$messages()) {
                Metadata metadata = METADATA.get(line.content());
                if (metadata != null) {
                    retained.put(line.content(), metadata);
                    retained.put(metadata.original(), metadata);
                }
            }
        }
        retained.put(current, currentMetadata);
        retained.put(currentMetadata.original(), currentMetadata);
        METADATA.clear();
        METADATA.putAll(retained);
        // MoreChatHistory peut conserver des milliers de lignes. On nettoie
        // alors par lots plutôt que de rescanner tout l'historique à chaque MP.
        nextMetadataPrune = Math.max(1_024, METADATA.size() + 512);
    }

    public record Presentation(String text, int characterOffset) {
    }

    private record Metadata(Text original, long arrivedAt) { }

    private static final class CachedPresentation {
        private final Metadata metadata;
        private String clock;
        private int decoration = -1;
        private Text prefix;
        private Text decorated;

        private CachedPresentation(Metadata metadata) { this.metadata = metadata; }

        void updateDecoration() {
            int flags = (GlobalFilterSettings.showTimestamps() ? 1 : 0)
                    | (GlobalFilterSettings.showPlayerHeads() ? 2 : 0);
            if (flags == decoration) return;
            decoration = flags;
            var result = Text.empty();
            if ((flags & 1) != 0) {
                if (clock == null) clock = "[" + CLOCK.format(Instant.ofEpochMilli(metadata.arrivedAt())) + "] ";
                result.append(Text.literal(clock).formatted(Formatting.DARK_GRAY));
            }
            if ((flags & 2) != 0) result.append(Text.literal(HEAD_SPACE));
            prefix = result;
            decorated = null;
        }

        Text decorated() {
            updateDecoration();
            if (decorated == null) decorated = Text.empty().append(prefix).append(metadata.original().copy());
            return decorated;
        }
    }
}
