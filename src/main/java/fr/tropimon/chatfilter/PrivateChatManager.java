package fr.tropimon.chatfilter;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PrivateChatManager {
    private static final int MAX_CONVERSATIONS = 64;
    private static final Pattern MSG_COMMAND = Pattern.compile(
            "^/msg\\s+([A-Za-z0-9_]{2,16})(?:\\s+.*)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern REPLY_DRAFT = Pattern.compile(
            "^\\s*/r(?:\\s+(.*))?$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Map<String, Conversation> CONVERSATIONS = new LinkedHashMap<>();
    private static Object connection;
    private static String selectedKey;
    private static List<ConversationTab> cachedTabs = List.of();
    private static boolean tabsDirty = true;

    private PrivateChatManager() {
    }

    public static boolean observe(MessageAnalysis message) {
        refreshSession();
        String localPlayer = localPlayer();
        Optional<PrivateMessageParser.Parsed> parsed =
                message.privateMessage();
        if (parsed.isEmpty()) {
            return false;
        }

        PrivateMessageParser.Parsed privateMessage = parsed.get();
        if (isLocalIdentity(privateMessage.counterpart(), localPlayer)) {
            return true;
        }
        TownChatNotifications.rejectPrivateCounterpart(privateMessage.counterpart());
        String key = key(privateMessage.counterpart());
        Conversation conversation = CONVERSATIONS.computeIfAbsent(
                key, ignored -> new Conversation(privateMessage.counterpart()));
        conversation.lastActivity = System.currentTimeMillis();
        if (privateMessage.incoming()
                && !(ChatFilterController.selected() == ChatChannel.PRIVATE
                && key.equals(selectedKey)
                && MinecraftClient.getInstance().currentScreen instanceof ChatScreen)) {
            conversation.unread = Math.min(100, conversation.unread + 1);
        }
        if (selectedKey == null) {
            selectedKey = key;
        }
        pruneConversations(key);
        tabsDirty = true;
        return true;
    }

    public static void observeOutgoingCommand(String command) {
        refreshSession();
        if (command == null) {
            return;
        }
        Matcher matcher = MSG_COMMAND.matcher(command.strip());
        if (matcher.matches()) {
            selectOrCreate(matcher.group(1));
        }
    }

    public static void openLatestUnread() {
        refreshSession();
        Conversation selected = selectedConversation();
        if (selected == null || unreadTotal() > 0 && selected.unread == 0) {
            CONVERSATIONS.entrySet().stream()
                    .filter(entry -> entry.getValue().unread > 0)
                    .max(Comparator.comparingLong(entry -> entry.getValue().lastActivity))
                    .or(() -> CONVERSATIONS.entrySet().stream()
                            .max(Comparator.comparingLong(entry -> entry.getValue().lastActivity)))
                    .ifPresent(entry -> selectedKey = entry.getKey());
        }
        tabsDirty = true;
        markCurrentRead();
    }

    public static void select(String name) {
        select(name, true);
    }

    /** Selectionne la conversation pour l'apercu ferme sans acquitter ses non-lus. */
    public static void preview(String name) {
        select(name, false);
    }

    private static void select(String name, boolean markRead) {
        refreshSession();
        if (name == null) {
            return;
        }
        String candidate = key(name);
        if (!CONVERSATIONS.containsKey(candidate)) {
            return;
        }
        selectedKey = candidate;
        tabsDirty = true;
        if (markRead) {
            markCurrentRead();
        }
    }

    public static void markSelectedRead() {
        refreshSession();
        markCurrentRead();
    }

    private static void closeCurrent() {
        if (selectedKey == null) {
            return;
        }
        List<String> keys = orderedKeys();
        int oldIndex = Math.max(0, keys.indexOf(selectedKey));
        CONVERSATIONS.remove(selectedKey);
        keys = orderedKeys();
        selectedKey = keys.isEmpty() ? null : keys.get(Math.min(oldIndex, keys.size() - 1));
        tabsDirty = true;
        markCurrentRead();
    }

    public static void close(String name) {
        refreshSession();
        if (name == null) {
            return;
        }
        String candidate = key(name);
        if (!CONVERSATIONS.containsKey(candidate)) {
            return;
        }
        if (candidate.equals(selectedKey)) {
            closeCurrent();
        } else {
            CONVERSATIONS.remove(candidate);
            tabsDirty = true;
        }
    }

    private static int unreadTotal() {
        return CONVERSATIONS.values().stream().mapToInt(conversation -> conversation.unread).sum();
    }

    public static String currentName() {
        refreshSession();
        Conversation conversation = selectedConversation();
        return conversation == null ? null : conversation.displayName;
    }

    public static List<ConversationTab> tabs() {
        refreshSession();
        if (!tabsDirty) {
            return cachedTabs;
        }
        List<ConversationTab> updated = CONVERSATIONS.entrySet().stream()
                .map(entry -> new ConversationTab(
                        entry.getValue().displayName,
                        entry.getValue().unread,
                        entry.getKey().equals(selectedKey)))
                .toList();
        if (!updated.equals(cachedTabs)) cachedTabs = updated;
        tabsDirty = false;
        return cachedTabs;
    }

    public static String retargetDraft(String current) {
        String name = currentName();
        if (name == null) {
            return "/msg ";
        }
        String input = current == null ? "" : current;
        Matcher reply = REPLY_DRAFT.matcher(input);
        if (reply.matches()) {
            String body = reply.group(1);
            return "/msg " + name + " " + (body == null ? "" : body);
        }
        Matcher matcher = MSG_COMMAND.matcher(input.strip());
        if (matcher.matches()) {
            int nameEnd = input.toLowerCase(Locale.ROOT).indexOf(matcher.group(1).toLowerCase(Locale.ROOT))
                    + matcher.group(1).length();
            String body = nameEnd < input.length() ? input.substring(nameEnd) : " ";
            return "/msg " + name + body;
        }
        if (input.isBlank() || input.equalsIgnoreCase("/msg")) {
            return "/msg " + name + " ";
        }
        if (!input.startsWith("/")) {
            return "/msg " + name + " " + input;
        }
        return input;
    }

    private static void selectOrCreate(String name) {
        TownChatNotifications.rejectPrivateCounterpart(name);
        String key = key(name);
        Conversation conversation = CONVERSATIONS.computeIfAbsent(key, ignored -> new Conversation(name));
        conversation.lastActivity = System.currentTimeMillis();
        selectedKey = key;
        pruneConversations(key);
        tabsDirty = true;
    }

    private static void pruneConversations(String newestKey) {
        while (CONVERSATIONS.size() > MAX_CONVERSATIONS) {
            String oldest = CONVERSATIONS.entrySet().stream()
                    .filter(entry -> !entry.getKey().equals(newestKey))
                    .filter(entry -> !entry.getKey().equals(selectedKey))
                    .min(Comparator.comparingLong(entry -> entry.getValue().lastActivity))
                    .map(Map.Entry::getKey)
                    .orElse(null);
            if (oldest == null) {
                return;
            }
            CONVERSATIONS.remove(oldest);
        }
    }

    private static void markCurrentRead() {
        Conversation conversation = selectedConversation();
        if (conversation != null && conversation.unread != 0) {
            conversation.unread = 0;
            tabsDirty = true;
        }
    }

    private static Conversation selectedConversation() {
        return selectedKey == null ? null : CONVERSATIONS.get(selectedKey);
    }

    private static List<String> orderedKeys() {
        return new ArrayList<>(CONVERSATIONS.keySet());
    }

    private static String localPlayer() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.player == null ? null : client.player.getGameProfile().getName();
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    /** Empêche les onglets privés d'un serveur ou d'un compte de fuir dans le suivant. */
    private static void refreshSession() {
        Object current = MinecraftClient.getInstance().getNetworkHandler();
        if (current == connection) {
            return;
        }
        connection = current;
        CONVERSATIONS.clear();
        selectedKey = null;
        cachedTabs = List.of();
        tabsDirty = true;
    }

    private static boolean isLocalIdentity(String name, String localPlayer) {
        String normalized = name.toLowerCase(Locale.ROOT);
        return normalized.equals("moi") || normalized.equals("me")
                || normalized.equals("vous") || normalized.equals("you")
                || localPlayer != null && name.equalsIgnoreCase(localPlayer);
    }

    private static final class Conversation {
        private final String displayName;
        private int unread;
        private long lastActivity = System.currentTimeMillis();

        private Conversation(String displayName) {
            this.displayName = displayName;
        }
    }

    public record ConversationTab(String name, int unread, boolean selected) {
    }
}
