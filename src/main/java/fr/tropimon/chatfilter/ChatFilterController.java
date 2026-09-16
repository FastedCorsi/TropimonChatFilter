package fr.tropimon.chatfilter;

import fr.tropimon.chatfilter.mixin.ChatHudAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.text.Text;

import java.util.Optional;

public final class ChatFilterController {
    private static final long COMMAND_RESPONSE_WINDOW_MILLIS = 5_000L;
    private static final BoundedIdentityCache<Text, Boolean> COMMAND_RESPONSES =
            new BoundedIdentityCache<>(256);
    private static ChatChannel selected = ChatChannel.ALL;
    private static long previewDeadline;
    private static long commandResponseDeadline;
    private static final ServerChannelState SERVER_CHANNEL = new ServerChannelState();
    private static Object connection;

    private ChatFilterController() {
    }

    public static ChatChannel selected() {
        refreshSession();
        return selected;
    }

    public static boolean shouldShow(ChatHudLine line) {
        refreshSession();
        Text original = ChatMessageDecorator.original(line.content());
        if (COMMAND_RESPONSES.containsKey(original)) {
            return true;
        }
        MessageAnalysis analysis = MessageAnalysisCache.get(original);
        boolean groupMessage = GroupChatManager.isGroupMessage(original);
        return MessageVisibility.show(analysis, selected,
                selected == ChatChannel.PRIVATE ? PrivateChatManager.currentName() : null,
                groupMessage, GroupChatManager.active(),
                GlobalFilterSettings.showAllChatsInAll(),
                GlobalFilterSettings::isVisible);
    }

    public static void select(ChatChannel channel) {
        refreshSession();
        previewDeadline = 0L;
        boolean changed = selected != channel;
        selected = channel;

        if (channel == ChatChannel.TOWN) {
            TownChatNotifications.markRead();
        }
        if (channel == ChatChannel.STAFF) {
            if (!StaffStatus.isStaff()) {
                selected = ChatChannel.ALL;
                channel = ChatChannel.ALL;
            } else {
                StaffChatNotifications.markRead();
            }
        }
        if (channel == ChatChannel.GROUP) {
            if (!GroupChatManager.active()) {
                selected = ChatChannel.ALL;
                channel = ChatChannel.ALL;
            } else {
                GroupChatManager.markRead();
            }
        }
        if (channel == ChatChannel.PRIVATE) {
            if (PrivateChatManager.currentName() == null) {
                PrivateChatManager.openLatestUnread();
            } else {
                PrivateChatManager.markSelectedRead();
            }
            previewDeadline = returnToAllDeadline();
        }

        if (changed) {
            refreshView();
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            if (channel == ChatChannel.TOWN) {
                requestServerChannel(ChatChannel.TOWN);
            } else if (channel == ChatChannel.STAFF) {
                requestServerChannel(ChatChannel.STAFF);
            } else if (channel == ChatChannel.ALL) {
                requestServerChannel(ChatChannel.ALL);
            }
        }
    }

    public static void refreshView() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.inGameHud != null) {
            ((ChatHudAccessor) client.inGameHud.getChatHud()).tropimonChatFilter$refresh();
            client.inGameHud.getChatHud().resetScroll();
        }
    }

    /**
     * Réaligne le canal d'écriture du serveur sur l'onglet affiché.
     * Tropimon mémorise parfois TOWN/STAFF entre deux sessions alors que le mod repart sur ALL.
     */
    public static void synchronizeServerChannel() {
        refreshSession();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }
        if (selected == ChatChannel.TOWN) {
            requestServerChannel(ChatChannel.TOWN);
        } else if (selected == ChatChannel.STAFF && StaffStatus.isStaff()) {
            requestServerChannel(ChatChannel.STAFF);
        } else if (selected == ChatChannel.ALL) {
            requestServerChannel(ChatChannel.ALL);
        }
    }

    /** Suit aussi une commande /chat tapée manuellement pour éviter de la renvoyer à l'ouverture. */
    public static void observeOutgoingCommand(String message) {
        refreshSession();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            SERVER_CHANNEL.observe(client.player.networkHandler, message);
            if (ChatOutgoingRouter.isExplicitCommand(message)) {
                commandResponseDeadline = System.currentTimeMillis()
                        + COMMAND_RESPONSE_WINDOW_MILLIS;
            }
        }
    }

    /**
     * Une réponse à une commande manuelle doit rester consultable même si le
     * staff travaille dans un onglet qui masque normalement les messages système.
     */
    public static void observeIncoming(Text original, MessageAnalysis analysis) {
        refreshSession();
        long now = System.currentTimeMillis();
        if (commandResponseDeadline != 0L && now <= commandResponseDeadline
                && MessageVisibility.standaloneSystemMessage(analysis)) {
            COMMAND_RESPONSES.put(ChatMessageDecorator.original(original), Boolean.TRUE);
        }
        if (commandResponseDeadline != 0L && now > commandResponseDeadline) {
            commandResponseDeadline = 0L;
        }
    }

    /** Affiche temporairement le canal du dernier message recu lorsque T est ferme. */
    public static void previewIncoming(MessageAnalysis message) {
        refreshSession();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen instanceof ChatScreen || client.player == null) {
            return;
        }

        String localPlayer = client.player.getGameProfile().getName();
        if (message.staff()) {
            boolean sentByLocalPlayer = message.sentByLocal(localPlayer);
            if (!sentByLocalPlayer) {
                beginPreview(ChatChannel.STAFF, false);
            }
            return;
        }
        Optional<PrivateMessageParser.Parsed> privateMessage =
                message.privateMessage()
                        .filter(PrivateMessageParser.Parsed::incoming);
        if (privateMessage.isPresent()) {
            String previousConversation = PrivateChatManager.currentName();
            PrivateChatManager.preview(privateMessage.get().counterpart());
            boolean conversationChanged = previousConversation == null
                    || !previousConversation.equalsIgnoreCase(privateMessage.get().counterpart());
            beginPreview(ChatChannel.PRIVATE, conversationChanged);
            return;
        }

        boolean sentByLocalPlayer = message.sentByLocal(localPlayer);
        if (!sentByLocalPlayer && message.town()) {
            beginPreview(ChatChannel.TOWN, false);
        }
    }

    public static void previewGroupIncoming() {
        refreshSession();
        MinecraftClient client = MinecraftClient.getInstance();
        if (GroupChatManager.active()
                && !(client.currentScreen instanceof ChatScreen) && client.player != null) {
            beginPreview(ChatChannel.GROUP, true);
        }
    }

    public static void acknowledgeReply(ChatChannel channel) {
        if (selected == channel) {
            previewDeadline = channel == ChatChannel.PRIVATE
                    ? returnToAllDeadline() : 0L;
        }
    }

    public static void tickPreview() {
        boolean sessionReset = refreshSession();
        if (sessionReset) {
            refreshView();
            return;
        }
        if (selected == ChatChannel.GROUP && !GroupChatManager.active()
                || selected == ChatChannel.STAFF && !StaffStatus.isStaff()) {
            previewDeadline = 0L;
            selected = ChatChannel.ALL;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                requestServerChannel(ChatChannel.ALL);
            }
            refreshView();
            return;
        }
        if (previewDeadline == 0L || System.currentTimeMillis() < previewDeadline) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen instanceof ChatScreen) {
            return;
        }
        previewDeadline = 0L;
        if (selected == ChatChannel.ALL) {
            return;
        }
        selected = ChatChannel.ALL;
        if (client.player != null) {
            requestServerChannel(ChatChannel.ALL);
        }
        refreshView();
    }

    private static void beginPreview(ChatChannel channel, boolean forceRefresh) {
        boolean changed = selected != channel;
        selected = channel;
        previewDeadline = returnToAllDeadline();
        if (changed || forceRefresh) {
            refreshView();
        }
    }

    private static long returnToAllDeadline() {
        long duration = GlobalFilterSettings.previewDurationMillis();
        return duration == 0L ? Long.MAX_VALUE : System.currentTimeMillis() + duration;
    }

    private static void requestServerChannel(ChatChannel channel) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null
                || !SERVER_CHANNEL.shouldRequest(client.player.networkHandler, channel)) {
            return;
        }
        String destination = switch (channel) {
            case ALL -> "GLOBAL";
            case TOWN -> "TOWN";
            case STAFF -> "STAFF";
            default -> null;
        };
        if (destination != null) {
            client.player.networkHandler.sendChatCommand("chat " + destination);
        }
    }

    /** Un onglet sélectionné sur un serveur ne doit jamais piloter le suivant. */
    private static boolean refreshSession() {
        Object current = MinecraftClient.getInstance().getNetworkHandler();
        if (current == connection) {
            return false;
        }
        boolean changed = selected != ChatChannel.ALL || previewDeadline != 0L;
        connection = current;
        selected = ChatChannel.ALL;
        previewDeadline = 0L;
        commandResponseDeadline = 0L;
        COMMAND_RESPONSES.clear();
        return changed;
    }
}
