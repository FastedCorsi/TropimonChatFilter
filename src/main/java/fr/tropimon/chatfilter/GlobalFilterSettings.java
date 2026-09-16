package fr.tropimon.chatfilter;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

public final class GlobalFilterSettings {
    private static final int[] PREVIEW_SECONDS = {15, 30, 60, 90, 120, 300, 0};
    private static final Path FILE = JsonConfigStore.resolve("tropimon-chat-filter.json");
    private static final Map<GlobalMessageCategory, Boolean> VISIBLE =
            new EnumMap<>(GlobalMessageCategory.class);
    private static boolean showTimestamps;
    private static boolean showPlayerHeads;
    private static boolean showAllChatsInAll;
    private static boolean keepChatVisible;
    private static int previewSeconds = 60;

    static {
        for (GlobalMessageCategory category : GlobalMessageCategory.values()) {
            VISIBLE.put(category, true);
        }
        load();
    }

    private GlobalFilterSettings() {
    }

    public static boolean isVisible(GlobalMessageCategory category) {
        if (category == GlobalMessageCategory.STAFF) {
            return true;
        }
        return VISIBLE.getOrDefault(category, true);
    }

    public static boolean hasHiddenCategories() {
        for (Map.Entry<GlobalMessageCategory, Boolean> entry : VISIBLE.entrySet()) {
            if (entry.getKey() != GlobalMessageCategory.STAFF && !entry.getValue()) {
                return true;
            }
        }
        return false;
    }

    public static void toggle(GlobalMessageCategory category) {
        if (category == GlobalMessageCategory.STAFF) {
            return;
        }
        VISIBLE.put(category, !isVisible(category));
        save();
        ChatFilterController.refreshView();
    }

    public static boolean showTimestamps() {
        return showTimestamps;
    }

    public static boolean showPlayerHeads() {
        return showPlayerHeads;
    }

    public static boolean showAllChatsInAll() {
        return showAllChatsInAll;
    }

    public static boolean keepChatVisible() {
        return keepChatVisible;
    }

    public static int previewSeconds() {
        return previewSeconds;
    }

    public static long previewDurationMillis() {
        return previewSeconds <= 0 ? 0L : previewSeconds * 1_000L;
    }

    public static void toggleTimestamps() {
        showTimestamps = !showTimestamps;
        save();
        ChatMessageDecorator.redecorateHistory();
    }

    public static void togglePlayerHeads() {
        showPlayerHeads = !showPlayerHeads;
        save();
        ChatMessageDecorator.redecorateHistory();
    }

    public static void toggleAllChatsInAll() {
        showAllChatsInAll = !showAllChatsInAll;
        save();
        ChatFilterController.refreshView();
    }

    public static void toggleKeepChatVisible() {
        keepChatVisible = !keepChatVisible;
        save();
    }

    public static void cyclePreviewSeconds() {
        int current = 0;
        for (int index = 0; index < PREVIEW_SECONDS.length; index++) {
            if (PREVIEW_SECONDS[index] == previewSeconds) {
                current = index;
                break;
            }
        }
        previewSeconds = PREVIEW_SECONDS[(current + 1) % PREVIEW_SECONDS.length];
        save();
    }

    private static void load() {
        if (FILE == null || !Files.isRegularFile(FILE)) {
            return;
        }
        try {
            JsonObject json = JsonConfigStore.read(FILE);
            if (json == null) {
                return;
            }
            for (GlobalMessageCategory category : GlobalMessageCategory.values()) {
                if (json.has(category.name())) {
                    VISIBLE.put(category, json.get(category.name()).getAsBoolean());
                }
            }
            if (json.has("showTimestamps")) {
                showTimestamps = json.get("showTimestamps").getAsBoolean();
            }
            if (json.has("showPlayerHeads")) {
                showPlayerHeads = json.get("showPlayerHeads").getAsBoolean();
            }
            if (json.has("showAllChatsInAll")) {
                showAllChatsInAll = json.get("showAllChatsInAll").getAsBoolean();
            }
            if (json.has("keepChatVisible")) {
                keepChatVisible = json.get("keepChatVisible").getAsBoolean();
            } else if (json.has("keepChatOpen")) {
                // Migration de l'option 0.1.25 dont le libellé prêtait à confusion.
                keepChatVisible = json.get("keepChatOpen").getAsBoolean();
            }
            if (json.has("previewSeconds")) {
                previewSeconds = sanitizePreviewSeconds(json.get("previewSeconds").getAsInt());
            }
        } catch (Exception ignored) {
            // Un fichier invalide revient simplement aux valeurs visibles par défaut.
        }
    }

    private static void save() {
        if (FILE == null) {
            return;
        }
        JsonObject json = new JsonObject();
        for (GlobalMessageCategory category : GlobalMessageCategory.values()) {
            json.addProperty(category.name(), isVisible(category));
        }
        json.addProperty("showTimestamps", showTimestamps);
        json.addProperty("showPlayerHeads", showPlayerHeads);
        json.addProperty("showAllChatsInAll", showAllChatsInAll);
        json.addProperty("keepChatVisible", keepChatVisible);
        json.addProperty("previewSeconds", previewSeconds);
        try {
            JsonConfigStore.write(FILE, json);
        } catch (IOException ignored) {
            // Le filtre continue de fonctionner pour la session si le disque est indisponible.
        }
    }

    private static int sanitizePreviewSeconds(int value) {
        for (int candidate : PREVIEW_SECONDS) {
            if (candidate == value) {
                return value;
            }
        }
        return 60;
    }
}
