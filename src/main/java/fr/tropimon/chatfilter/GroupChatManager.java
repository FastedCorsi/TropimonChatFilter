package fr.tropimon.chatfilter;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/** Gère un groupe temporaire en répliquant ses messages via les MP officiels. */
public final class GroupChatManager {
    public static final int MAX_MEMBERS = 8;
    private static final long SEND_INTERVAL_MILLIS = 150L;
    private static final int MAX_PENDING_COMMANDS = 64;
    private static final int MAX_PENDING_RICH_MESSAGES = 32;
    private static final long RICH_MESSAGE_FALLBACK_MILLIS = 5_000L;
    private static final int MAX_SAVED_PLAYERS = 32;
    private static final Path GROUPS_FILE = JsonConfigStore.resolve(
            "tropimon-chat-filter-groups.json");
    private static final Deque<String> OUTGOING = new ArrayDeque<>();
    private static final Map<String, String> MEMBERS = new LinkedHashMap<>();
    private static final Map<String, SavedGroup> SAVED_GROUPS = new HashMap<>();
    private static final Map<Text, Boolean> DISPLAY_MESSAGES = new WeakHashMap<>();
    private static final Map<String, PendingRichMessage> PENDING_LOCAL_RICH_MESSAGES =
            new LinkedHashMap<>();
    private static final Map<String, PendingIncomingRichMessage> PENDING_INCOMING_RICH_MESSAGES =
            new LinkedHashMap<>();
    private static String groupId;
    private static Object connection;
    private static String playerKey;
    private static int unread;
    private static long nextSendAt;
    private static long nextPresenceRefresh;
    private static int connectedMembers;

    static {
        loadSavedGroups();
    }

    private GroupChatManager() {
    }

    public static boolean active() {
        refreshPlayer();
        return groupId != null && MinecraftClient.getInstance().player != null
                && hasMember(localPlayer());
    }

    public static int unread() {
        return unread;
    }

    public static int memberCount() {
        refreshPlayer();
        return MEMBERS.size();
    }

    public static int connectedMemberCount() {
        refreshPlayer();
        long now = System.currentTimeMillis();
        if (now < nextPresenceRefresh) {
            return connectedMembers;
        }
        nextPresenceRefresh = now + 1_000L;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() == null || MEMBERS.isEmpty()) {
            connectedMembers = 0;
            return 0;
        }
        Set<String> connected = new HashSet<>();
        client.getNetworkHandler().getPlayerList().forEach(entry ->
                connected.add(key(entry.getProfile().getName())));
        connectedMembers = countConnected(MEMBERS.keySet(), connected);
        return connectedMembers;
    }

    public static boolean isMember(String name) {
        refreshPlayer();
        return hasMember(name);
    }

    public static boolean isGroupMessage(Text message) {
        return DISPLAY_MESSAGES.containsKey(ChatMessageDecorator.original(message));
    }

    public static Observation observeNetwork(Text original, MessageAnalysis message) {
        refreshPlayer();
        Optional<GroupMessageProtocol.Payload> parsed =
                message.groupPayload();
        if (parsed.isEmpty()) {
            return Observation.notConsumed();
        }
        Optional<PrivateMessageParser.Parsed> privateMessage =
                message.privateMessage();
        if (privateMessage.isEmpty()) {
            return Observation.consumedOnly();
        }
        PrivateMessageParser.Parsed envelope = privateMessage.get();
        if (!envelope.incoming()) {
            resolveOutgoingRichMessage(original, parsed.get());
            return Observation.consumedOnly();
        }

        GroupMessageProtocol.Payload payload = parsed.get();
        String sender = envelope.counterpart();
        if (payload.type() == GroupMessageProtocol.Type.I) {
            return acceptInvitation(sender, payload);
        }
        if (groupId == null || !groupId.equals(payload.groupId()) || !hasMember(sender)) {
            return Observation.consumedOnly();
        }
        if (isResolvedToken(payload.data())) {
            PendingIncomingRichMessage pending = PENDING_INCOMING_RICH_MESSAGES.remove(
                    incomingRichKey(sender, payload.data().substring(1)));
            if (pending == null) {
                return Observation.consumedOnly();
            }
            Text display = groupMessage(sender,
                    GroupMessageProtocol.styledBody(original, payload));
            countUnread();
            return new Observation(true, display, true);
        }
        if (payload.type() == GroupMessageProtocol.Type.L) {
            String leaving = payload.data();
            if (leaving.equalsIgnoreCase(sender) && hasMember(leaving)) {
                MEMBERS.remove(key(leaving));
                invalidatePresence();
                Text notice = groupNotice(Text.translatable(
                        "tropimon_chat_filter.group.member_left", leaving));
                countUnread();
                persistCurrentGroup();
                return new Observation(true, notice, true);
            }
            return Observation.consumedOnly();
        }
        if (isRichToken(payload.data()) && containsPartyReference(payload.body())) {
            rememberIncomingRichMessage(sender, payload.data(), payload.body());
            return Observation.consumedOnly();
        }

        Text display = groupMessage(sender,
                GroupMessageProtocol.styledBody(original, payload));
        countUnread();
        return new Observation(true, display, true);
    }

    public static boolean send(String body, boolean addToHistory) {
        refreshPlayer();
        String message = body == null ? "" : body.strip();
        if (!active() || message.isBlank()) {
            notice(Text.translatable("tropimon_chat_filter.group.empty"));
            return false;
        }
        int recipients = Math.max(0, MEMBERS.size() - 1);
        if (OUTGOING.size() + recipients > MAX_PENDING_COMMANDS) {
            notice(Text.translatable("tropimon_chat_filter.group.delivery_busy"));
            return false;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (addToHistory && client.inGameHud != null) {
            client.inGameHud.getChatHud().addToMessageHistory(body);
        }
        String local = localPlayer();
        boolean richPartyReference = containsPartyReference(message)
                && recipients > 0;
        String messageToken = richPartyReference
                ? UUID.randomUUID().toString().replace("-", "").substring(0, 6) : null;
        if (richPartyReference) {
            rememberPendingRichMessage(messageToken, message);
        } else {
            addDisplay(groupMessage(local, message));
        }
        for (String member : memberNames()) {
            if (!member.equalsIgnoreCase(local)) {
                String payload = messageToken == null
                        ? GroupMessageProtocol.message(groupId,
                                truncateFor(member, message, 34))
                        : GroupMessageProtocol.message(groupId, messageToken,
                                truncateFor(member, message, 41));
                queueMessage(member, payload);
            }
        }
        ChatFilterController.acknowledgeReply(ChatChannel.GROUP);
        return true;
    }

    public static void invite(String target) {
        refreshPlayer();
        String local = localPlayer();
        if (target == null || !target.matches("[A-Za-z0-9_]{2,16}")) {
            notice(Text.translatable("tropimon_chat_filter.group.invalid_player"));
            return;
        }
        if (target.equalsIgnoreCase(local)) {
            notice(Text.translatable("tropimon_chat_filter.group.invite_self"));
            return;
        }
        if (hasMember(target)) {
            notice(Text.translatable("tropimon_chat_filter.group.already_member", target));
            return;
        }
        if (MEMBERS.size() >= MAX_MEMBERS) {
            notice(Text.translatable("tropimon_chat_filter.group.full", MAX_MEMBERS));
            return;
        }
        int invitationRecipients = groupId == null ? 1 : MEMBERS.size();
        if (OUTGOING.size() + invitationRecipients > MAX_PENDING_COMMANDS) {
            notice(Text.translatable("tropimon_chat_filter.group.delivery_busy"));
            return;
        }
        if (groupId == null) {
            groupId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            MEMBERS.clear();
            putMember(local);
        }
        putMember(target);
        persistCurrentGroup();
        String invitation = GroupMessageProtocol.invitation(groupId, memberNames());
        for (String member : memberNames()) {
            if (!member.equalsIgnoreCase(local)) {
                queueMessage(member, invitation);
            }
        }
        addDisplay(groupNotice(Text.translatable(
                "tropimon_chat_filter.group.member_invited", local, target)));
        ChatFilterController.select(ChatChannel.GROUP);
    }

    public static void leave() {
        refreshPlayer();
        if (!active()) {
            closeLocalAndForget();
            return;
        }
        String local = localPlayer();
        String payload = GroupMessageProtocol.leave(groupId, local);
        // Quitter annule les anciens envois : personne ne doit recevoir un
        // message de groupe après la notification de départ.
        OUTGOING.clear();
        for (String member : memberNames()) {
            if (!member.equalsIgnoreCase(local)) {
                queueMessage(member, payload);
            }
        }
        closeLocalAndForget();
        if (ChatFilterController.selected() == ChatChannel.GROUP) {
            ChatFilterController.select(ChatChannel.ALL);
        }
    }

    public static void markRead() {
        unread = 0;
    }

    public static void tick() {
        refreshPlayer();
        displayExpiredRichMessages(System.currentTimeMillis());
        if (OUTGOING.isEmpty() || System.currentTimeMillis() < nextSendAt) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }
        client.player.networkHandler.sendChatCommand(OUTGOING.removeFirst());
        nextSendAt = System.currentTimeMillis() + SEND_INTERVAL_MILLIS;
    }

    private static Observation acceptInvitation(
            String sender, GroupMessageProtocol.Payload payload) {
        String local = localPlayer();
        if (payload.members().stream().noneMatch(name -> name.equalsIgnoreCase(local))
                || payload.members().stream().noneMatch(name -> name.equalsIgnoreCase(sender))) {
            return Observation.consumedOnly();
        }
        if (groupId != null && !groupId.equals(payload.groupId())) {
            notice(Text.translatable("tropimon_chat_filter.group.busy", sender));
            return Observation.consumedOnly();
        }
        if (groupId != null && !hasMember(sender)) {
            return Observation.consumedOnly();
        }
        boolean firstInvite = groupId == null;
        List<String> previous = memberNames();
        groupId = payload.groupId();
        MEMBERS.clear();
        payload.members().stream().limit(MAX_MEMBERS).forEach(GroupChatManager::putMember);
        if (MEMBERS.size() < 2) {
            closeLocalAndForget();
            return Observation.consumedOnly();
        }
        persistCurrentGroup();
        String added = payload.members().stream()
                .filter(name -> previous.stream().noneMatch(old -> old.equalsIgnoreCase(name)))
                .filter(name -> !name.equalsIgnoreCase(local))
                .reduce((first, second) -> second).orElse(sender);
        Text display = firstInvite
                ? groupNotice(Text.translatable(
                        "tropimon_chat_filter.group.received_invite", sender))
                : groupNotice(Text.translatable(
                        "tropimon_chat_filter.group.member_invited", sender, added));
        countUnread();
        return new Observation(true, display, true);
    }

    private static void countUnread() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!(ChatFilterController.selected() == ChatChannel.GROUP
                && client.currentScreen instanceof ChatScreen)) {
            unread = Math.min(99, unread + 1);
        }
    }

    private static void addDisplay(Text message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.inGameHud != null) {
            client.inGameHud.getChatHud().addMessage(message);
        }
    }

    private static Text groupMessage(String sender, String body) {
        return groupMessage(sender, Text.literal(body));
    }

    private static Text groupMessage(String sender, Text body) {
        Text message = Text.translatable("tropimon_chat_filter.group.prefix")
                .formatted(Formatting.AQUA)
                .append(Text.literal(sender).formatted(Formatting.YELLOW))
                .append(Text.literal(": ").formatted(Formatting.WHITE))
                .append(Text.empty().formatted(Formatting.WHITE).append(body.copy()));
        DISPLAY_MESSAGES.put(message, Boolean.TRUE);
        return message;
    }

    private static void rememberPendingRichMessage(String token, String body) {
        if (PENDING_LOCAL_RICH_MESSAGES.size() >= MAX_PENDING_RICH_MESSAGES) {
            Iterator<PendingRichMessage> oldest =
                    PENDING_LOCAL_RICH_MESSAGES.values().iterator();
            if (oldest.hasNext()) {
                addDisplay(groupMessage(localPlayer(), oldest.next().body()));
                oldest.remove();
            }
        }
        PENDING_LOCAL_RICH_MESSAGES.put(token,
                new PendingRichMessage(body, System.currentTimeMillis()));
    }

    private static void rememberIncomingRichMessage(
            String sender, String token, String body) {
        if (PENDING_INCOMING_RICH_MESSAGES.size() >= MAX_PENDING_RICH_MESSAGES) {
            Iterator<PendingIncomingRichMessage> oldest =
                    PENDING_INCOMING_RICH_MESSAGES.values().iterator();
            if (oldest.hasNext()) {
                PendingIncomingRichMessage message = oldest.next();
                addDisplay(groupMessage(message.sender(), message.body()));
                countUnread();
                oldest.remove();
            }
        }
        PENDING_INCOMING_RICH_MESSAGES.put(incomingRichKey(sender, token),
                new PendingIncomingRichMessage(sender, body, System.currentTimeMillis()));
    }

    private static void resolveOutgoingRichMessage(
            Text original, GroupMessageProtocol.Payload payload) {
        if (payload.type() != GroupMessageProtocol.Type.M
                || !isRichToken(payload.data()) || !payload.groupId().equals(groupId)) {
            return;
        }
        PendingRichMessage pending = PENDING_LOCAL_RICH_MESSAGES.remove(
                payload.data().toLowerCase(Locale.ROOT));
        if (pending != null) {
            Text resolved = GroupMessageProtocol.styledBody(original, payload);
            addDisplay(groupMessage(localPlayer(), resolved));
            relayResolvedMessage(payload.data(), resolved.getString(), pending.body());
        }
    }

    private static void relayResolvedMessage(String token, String resolved, String fallback) {
        String body = resolved == null || resolved.isBlank() ? fallback : resolved;
        String local = localPlayer();
        for (String member : memberNames()) {
            if (!member.equalsIgnoreCase(local) && OUTGOING.size() < MAX_PENDING_COMMANDS) {
                queueMessage(member, GroupMessageProtocol.resolvedMessage(groupId, token,
                        truncateFor(member, body, 42)));
            }
        }
    }

    private static void displayExpiredRichMessages(long now) {
        Iterator<PendingRichMessage> local = PENDING_LOCAL_RICH_MESSAGES.values().iterator();
        while (local.hasNext()) {
            PendingRichMessage message = local.next();
            if (now - message.createdAt() < RICH_MESSAGE_FALLBACK_MILLIS) {
                break;
            }
            addDisplay(groupMessage(localPlayer(), message.body()));
            local.remove();
        }
        Iterator<PendingIncomingRichMessage> incoming =
                PENDING_INCOMING_RICH_MESSAGES.values().iterator();
        while (incoming.hasNext()) {
            PendingIncomingRichMessage message = incoming.next();
            if (now - message.createdAt() < RICH_MESSAGE_FALLBACK_MILLIS) {
                break;
            }
            addDisplay(groupMessage(message.sender(), message.body()));
            countUnread();
            incoming.remove();
        }
    }

    private static boolean containsPartyReference(String body) {
        return body != null && body.matches("(?is).*<party:[1-6]>.*");
    }

    private static boolean isRichToken(String token) {
        return token != null && token.matches("[a-f0-9]{6}");
    }

    private static boolean isResolvedToken(String token) {
        return token != null && token.matches("r[a-f0-9]{6}");
    }

    private static String incomingRichKey(String sender, String token) {
        return key(sender) + ':' + token.toLowerCase(Locale.ROOT);
    }

    private static Text groupNotice(Text body) {
        Text message = Text.translatable("tropimon_chat_filter.group.prefix")
                .formatted(Formatting.AQUA)
                .append(body.copy().formatted(Formatting.GRAY));
        DISPLAY_MESSAGES.put(message, Boolean.TRUE);
        return message;
    }

    private static void notice(Text message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(message, true);
        }
    }

    private static void queueMessage(String target, String payload) {
        if (OUTGOING.size() < MAX_PENDING_COMMANDS) {
            OUTGOING.addLast("msg " + target + " " + payload);
        }
    }

    private static String truncateFor(String target, String body, int protocolOverhead) {
        int maximum = Math.max(1, 256 - "msg ".length() - target.length()
                - protocolOverhead - 2);
        if (body.length() <= maximum) {
            return body;
        }
        int end = maximum;
        if (end > 0 && Character.isHighSurrogate(body.charAt(end - 1))) {
            end--;
        }
        return body.substring(0, Math.max(1, end));
    }

    private static void refreshPlayer() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            if (connection != null) {
                OUTGOING.clear();
                PENDING_LOCAL_RICH_MESSAGES.clear();
                PENDING_INCOMING_RICH_MESSAGES.clear();
            }
            connection = null;
            return;
        }
        Object currentConnection = client.getNetworkHandler();
        String currentKey = key(client.player.getGameProfile().getName());
        if (connection == currentConnection && currentKey.equals(playerKey)) {
            return;
        }
        OUTGOING.clear();
        PENDING_LOCAL_RICH_MESSAGES.clear();
        PENDING_INCOMING_RICH_MESSAGES.clear();
        connection = currentConnection;
        playerKey = currentKey;
        invalidatePresence();
        restoreSavedGroup();
    }

    private static void closeLocalAndForget() {
        groupId = null;
        MEMBERS.clear();
        PENDING_LOCAL_RICH_MESSAGES.clear();
        PENDING_INCOMING_RICH_MESSAGES.clear();
        unread = 0;
        invalidatePresence();
        if (playerKey != null && SAVED_GROUPS.remove(playerKey) != null) {
            saveSavedGroups();
        }
    }

    private static boolean hasMember(String name) {
        return name != null && MEMBERS.containsKey(key(name));
    }

    private static void putMember(String name) {
        MEMBERS.putIfAbsent(key(name), name);
        invalidatePresence();
    }

    private static List<String> memberNames() {
        return new ArrayList<>(MEMBERS.values());
    }

    static int countConnected(Iterable<String> members, Set<String> connected) {
        int count = 0;
        for (String member : members) {
            if (connected.contains(key(member))) {
                count++;
            }
        }
        return count;
    }

    private static void restoreSavedGroup() {
        groupId = null;
        MEMBERS.clear();
        unread = 0;
        SavedGroup saved = SAVED_GROUPS.get(playerKey);
        if (saved == null || !isValidGroupId(saved.groupId())) {
            return;
        }
        saved.members().stream().filter(GroupChatManager::isValidPlayerName)
                .limit(MAX_MEMBERS).forEach(GroupChatManager::putMember);
        if (!hasMember(localPlayer())) {
            MEMBERS.clear();
            SAVED_GROUPS.remove(playerKey);
            saveSavedGroups();
            return;
        }
        groupId = saved.groupId();
    }

    private static void persistCurrentGroup() {
        if (playerKey == null || groupId == null || !hasMember(localPlayer())) {
            return;
        }
        SavedGroup saved = new SavedGroup(groupId, List.copyOf(memberNames()));
        if (!saved.equals(SAVED_GROUPS.put(playerKey, saved))) {
            saveSavedGroups();
        }
    }

    private static void loadSavedGroups() {
        if (GROUPS_FILE == null || !Files.isRegularFile(GROUPS_FILE)) {
            return;
        }
        try {
            JsonObject root = JsonConfigStore.read(GROUPS_FILE);
            if (root == null) {
                return;
            }
            for (var entry : root.entrySet()) {
                if (SAVED_GROUPS.size() >= MAX_SAVED_PLAYERS
                        || !isValidPlayerName(entry.getKey())
                        || !entry.getValue().isJsonObject()) {
                    continue;
                }
                try {
                    JsonObject group = entry.getValue().getAsJsonObject();
                    if (!group.has("groupId") || !group.has("members")) {
                        continue;
                    }
                    String id = group.get("groupId").getAsString().toLowerCase(Locale.ROOT);
                    if (!isValidGroupId(id) || !group.get("members").isJsonArray()) {
                        continue;
                    }
                    List<String> members = new ArrayList<>();
                    group.getAsJsonArray("members").forEach(element -> {
                        if (element.isJsonPrimitive()
                                && element.getAsJsonPrimitive().isString()) {
                            String name = element.getAsString();
                            if (isValidPlayerName(name)
                                    && members.stream().noneMatch(name::equalsIgnoreCase)
                                    && members.size() < MAX_MEMBERS) {
                                members.add(name);
                            }
                        }
                    });
                    if (!members.isEmpty()) {
                        SAVED_GROUPS.put(key(entry.getKey()),
                                new SavedGroup(id, List.copyOf(members)));
                    }
                } catch (RuntimeException ignored) {
                    // Une entrée invalide ne doit pas effacer les autres groupes valides.
                }
            }
        } catch (Exception ignored) {
            SAVED_GROUPS.clear();
        }
    }

    private static void saveSavedGroups() {
        if (GROUPS_FILE == null) {
            return;
        }
        JsonObject root = new JsonObject();
        SAVED_GROUPS.forEach((player, saved) -> {
            JsonObject group = new JsonObject();
            group.addProperty("groupId", saved.groupId());
            JsonArray members = new JsonArray();
            saved.members().forEach(members::add);
            group.add("members", members);
            root.add(player, group);
        });
        try {
            JsonConfigStore.write(GROUPS_FILE, root);
        } catch (IOException ignored) {
            // Le groupe reste actif pour la session si le disque est indisponible.
        }
    }

    private static boolean isValidGroupId(String value) {
        return value != null && value.matches("[a-f0-9]{8}");
    }

    private static boolean isValidPlayerName(String value) {
        return value != null && value.matches("[A-Za-z0-9_]{2,16}");
    }

    private static void invalidatePresence() {
        nextPresenceRefresh = 0L;
        connectedMembers = 0;
    }

    private static String localPlayer() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.player == null ? "Moi" : client.player.getGameProfile().getName();
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private record SavedGroup(String groupId, List<String> members) {
    }

    private record PendingRichMessage(String body, long createdAt) {
    }

    private record PendingIncomingRichMessage(String sender, String body, long createdAt) {
    }

    public record Observation(boolean consumed, Text display, boolean incoming) {
        private static Observation notConsumed() {
            return new Observation(false, null, false);
        }

        private static Observation consumedOnly() {
            return new Observation(true, null, false);
        }
    }
}
