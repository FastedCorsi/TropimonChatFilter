package fr.tropimon.chatfilter.mixin;

import fr.tropimon.chatfilter.ChatChannel;
import fr.tropimon.chatfilter.ChatFilterController;
import fr.tropimon.chatfilter.ChatMessageDecorator;
import fr.tropimon.chatfilter.ChatOutgoingRouter;
import fr.tropimon.chatfilter.ClickableUsername;
import fr.tropimon.chatfilter.ConversationButtonLayout;
import fr.tropimon.chatfilter.ConversationLayoutCache;
import fr.tropimon.chatfilter.RenderCacheEpoch;
import fr.tropimon.chatfilter.GlobalFilterSettings;
import fr.tropimon.chatfilter.GlobalMessageCategory;
import fr.tropimon.chatfilter.GroupChatManager;
import fr.tropimon.chatfilter.PartyShareInput;
import fr.tropimon.chatfilter.PrivateChatManager;
import fr.tropimon.chatfilter.PrivateOutgoingMessage;
import fr.tropimon.chatfilter.StaffChatNotifications;
import fr.tropimon.chatfilter.StaffStatus;
import fr.tropimon.chatfilter.TownChatNotifications;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import net.minecraft.text.OrderedText;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.ToIntFunction;
import java.util.function.BiFunction;

@Mixin(value = ChatScreen.class, priority = 900)
abstract class ChatScreenMixin extends Screen {
    private static final GlobalMessageCategory[] FILTER_CATEGORIES = {
            GlobalMessageCategory.SYSTEM,
            GlobalMessageCategory.UNRANKED,
            GlobalMessageCategory.CHAMPION,
            GlobalMessageCategory.SUPER,
            GlobalMessageCategory.HYPER,
            GlobalMessageCategory.MASTER
    };
    private static final int ALL_BUTTON_WIDTH = 48;
    private static final int STAFF_BUTTON_WIDTH = 52;
    private static final int GROUP_BUTTON_WIDTH = 58;
    private static final int TOWN_MIN_WIDTH = 48;
    private static final int TOWN_MAX_WIDTH = 140;
    private static final int BUTTON_HEIGHT = 16;
    private static final int BUTTON_GAP = 1;
    private static final int PRIVATE_CLOSE_WIDTH = 15;
    private static final int FILTER_BUTTON_WIDTH = 16;
    private static final int FILTER_OPTION_WIDTH = 78;
    private static final int FILTER_OPTION_HEIGHT = 15;
    private static final int FILTER_SETTINGS_COUNT = 5;
    private static final int FILTER_ROWS = 9;
    private static final int TAB_RIGHT_OFFSET = 8;
    private static final int GROUP_POPUP_WIDTH = 176;
    private static final int GROUP_POPUP_HEIGHT = 138;
    private static final int GROUP_SEARCH_HEIGHT = 16;
    private static final int GROUP_ROW_HEIGHT = 16;
    private static final int GROUP_VISIBLE_ROWS = 6;
    private static final long GROUP_LIST_CACHE_MILLIS = 250L;
    private static final List<ChatChannel> BASIC_CHANNELS =
            List.of(ChatChannel.ALL, ChatChannel.TOWN);
    private static final List<ChatChannel> STAFF_CHANNELS =
            List.of(ChatChannel.ALL, ChatChannel.TOWN, ChatChannel.STAFF);
    private static final List<ChatChannel> GROUP_CHANNELS =
            List.of(ChatChannel.ALL, ChatChannel.TOWN, ChatChannel.GROUP);
    private static final List<ChatChannel> STAFF_GROUP_CHANNELS =
            List.of(ChatChannel.ALL, ChatChannel.TOWN, ChatChannel.STAFF, ChatChannel.GROUP);

    @Unique
    private boolean tropimonChatFilter$filterOpen;

    @Unique
    private List<ConversationButtonLayout> tropimonChatFilter$conversationButtons = List.of();

    @Unique private final ConversationLayoutCache tropimonChatFilter$layout = new ConversationLayoutCache();
    @Unique private ToIntFunction<String> tropimonChatFilter$measure;
    @Unique private BiFunction<String, Integer, String> tropimonChatFilter$trim;
    @Unique private int tropimonChatFilter$tabY;
    @Unique private int tropimonChatFilter$chatRight;
    @Unique private List<ChatChannel> tropimonChatFilter$channels = BASIC_CHANNELS;
    @Unique private String tropimonChatFilter$measuredTown;
    @Unique private Object tropimonChatFilter$fontEpoch;
    @Unique private int tropimonChatFilter$townWidth = TOWN_MIN_WIDTH;
    @Unique private Text tropimonChatFilter$townLabel;
    @Unique private final Text tropimonChatFilter$noTownLabel = Text.translatable("tropimon_chat_filter.tab.no_town");
    @Unique private final Text tropimonChatFilter$unknownTownLabel = Text.translatable("tropimon_chat_filter.tab.town_unknown");
    @Unique private final java.util.Map<ChatChannel, Text> tropimonChatFilter$channelLabels = new java.util.EnumMap<>(ChatChannel.class);
    @Unique private Text tropimonChatFilter$groupLabel;
    @Unique private int tropimonChatFilter$groupOnline = -1;
    @Unique private int tropimonChatFilter$groupTotal = -1;

    @Unique
    private int tropimonChatFilter$conversationScroll;

    @Unique private int tropimonChatFilter$hiddenUnreadMessageCount;

    @Unique
    private boolean tropimonChatFilter$groupPopupOpen;

    @Unique
    private TextFieldWidget tropimonChatFilter$groupSearch;

    @Unique
    private int tropimonChatFilter$groupPlayerScroll;

    @Unique
    private List<String> tropimonChatFilter$cachedGroupPlayers = List.of();

    @Unique
    private Object tropimonChatFilter$cachedGroupHandler;

    @Unique
    private String tropimonChatFilter$cachedGroupQuery = "";

    @Unique
    private long tropimonChatFilter$nextGroupListRefresh;

    @Shadow
    protected TextFieldWidget chatField;

    protected ChatScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void tropimonChatFilter$restorePrivateConversation(CallbackInfo ci) {
        TownChatNotifications.detectFromHistory();
        if (ChatFilterController.selected() == ChatChannel.STAFF && !StaffStatus.isStaff()
                || ChatFilterController.selected() == ChatChannel.GROUP
                && !GroupChatManager.active()) {
            ChatFilterController.select(ChatChannel.ALL);
        }
        ChatFilterController.synchronizeServerChannel();
        if (ChatFilterController.selected() != ChatChannel.PRIVATE) {
            if (ChatFilterController.selected() == ChatChannel.TOWN) {
                TownChatNotifications.markRead();
            } else if (ChatFilterController.selected() == ChatChannel.STAFF) {
                StaffChatNotifications.markRead();
            } else if (ChatFilterController.selected() == ChatChannel.GROUP) {
                GroupChatManager.markRead();
            }
            ChatFilterController.refreshView();
            return;
        }
        if (PrivateChatManager.currentName() == null) {
            ChatFilterController.select(ChatChannel.ALL);
            return;
        }
        PrivateChatManager.markSelectedRead();
        ChatFilterController.refreshView();
        chatField.setText(PrivateChatManager.retargetDraft(chatField.getText()));
        chatField.setCursorToEnd(false);
    }

    @Inject(method = "removed", at = @At("TAIL"))
    private void tropimonChatFilter$restoreCollapsedAllView(CallbackInfo ci) {
        PartyShareInput.cancelDrag();
        ChatFilterController.refreshView();
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void tropimonChatFilter$renderTabs(
            DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        TownChatNotifications.refreshFromProfile();
        if (TownChatNotifications.hasNoTown()
                && ChatFilterController.selected() == ChatChannel.TOWN) {
            ChatFilterController.select(ChatChannel.ALL);
            chatField.setText("");
            showNoTownNotice();
        }
        updateLayoutMetrics();
        tropimonChatFilter$conversationButtons = calculateConversationButtons();
        for (ChatChannel channel : mainChannels()) {
            int x = mainTabX(channel);
            int buttonWidth = mainTabWidth(channel);
            drawMainTab(context, channel, x, tabY(), buttonWidth,
                    containsBox(x, tabY(), buttonWidth, BUTTON_HEIGHT, mouseX, mouseY),
                    channel == ChatChannel.GROUP && containsBox(
                            x + buttonWidth - PRIVATE_CLOSE_WIDTH, tabY(),
                            PRIVATE_CLOSE_WIDTH, BUTTON_HEIGHT, mouseX, mouseY),
                    ChatFilterController.selected() == channel);
        }

        for (ConversationButtonLayout button : tropimonChatFilter$conversationButtons) {
            drawConversationButton(context, button, mouseX, mouseY);
        }
        drawHiddenUnreadNotification(context);

        ChatChannel selected = ChatFilterController.selected();
        drawFilterButton(context, mouseX, mouseY);
        if (tropimonChatFilter$filterOpen) {
            drawFilterPanel(context, mouseX, mouseY);
        }
        if (tropimonChatFilter$groupPopupOpen) {
            drawGroupPopup(context, mouseX, mouseY, delta);
        }
        tropimonChatFilter$drawPartyDrag(context, mouseX, mouseY);
        boolean empty = ((ChatHudAccessor) client.inGameHud.getChatHud())
                .tropimonChatFilter$visibleMessages().isEmpty();
        if (empty && (selected != ChatChannel.ALL
                || GlobalFilterSettings.hasHiddenCategories())) {
            String emptyKey = switch (selected) {
                case ALL -> "tropimon_chat_filter.empty.filtered";
                case TOWN -> "tropimon_chat_filter.empty.town";
                case STAFF -> "tropimon_chat_filter.empty.staff";
                case GROUP -> "tropimon_chat_filter.empty.group";
                case PRIVATE -> "tropimon_chat_filter.empty.private";
            };
            context.drawTextWithShadow(
                    textRenderer,
                    Text.translatable(emptyKey),
                    5,
                    tabY() + BUTTON_HEIGHT + 5,
                    0xFFB8C4CA);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void tropimonChatFilter$clickTab(
            double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        updateLayoutMetrics();
        if (tropimonChatFilter$groupPopupOpen) {
            clickGroupPopup(mouseX, mouseY, button);
            cir.setReturnValue(true);
            return;
        }
        if (button == 1 && rightClickUsername(mouseX, mouseY)) {
            setFocused(chatField);
            cir.setReturnValue(true);
            return;
        }
        if (button != 0) {
            return;
        }
        tropimonChatFilter$conversationButtons = calculateConversationButtons();

        if (containsBox(filterButtonX(), tabY(),
                FILTER_BUTTON_WIDTH, BUTTON_HEIGHT, mouseX, mouseY)) {
            tropimonChatFilter$filterOpen = !tropimonChatFilter$filterOpen;
            setFocused(chatField);
            cir.setReturnValue(true);
            return;
        }
        if (tropimonChatFilter$filterOpen && clickFilterOption(mouseX, mouseY)) {
            setFocused(chatField);
            cir.setReturnValue(true);
            return;
        }

        for (ConversationButtonLayout conversation : tropimonChatFilter$conversationButtons) {
            if (!containsBox(conversation.x(), tabY(), conversation.width(), BUTTON_HEIGHT,
                    mouseX, mouseY)) {
                continue;
            }
            tropimonChatFilter$filterOpen = false;
            if (mouseX >= conversation.x() + conversation.width() - PRIVATE_CLOSE_WIDTH) {
                boolean closedCurrent = conversation.tab().selected();
                PrivateChatManager.close(conversation.tab().name());
                if (closedCurrent && ChatFilterController.selected() == ChatChannel.PRIVATE) {
                    if (PrivateChatManager.currentName() == null) {
                        ChatFilterController.select(ChatChannel.ALL);
                        chatField.setText(ChatOutgoingRouter.prepareInput(
                                ChatChannel.ALL, chatField.getText()));
                    } else {
                        refreshPrivateConversation();
                    }
                } else {
                    ChatFilterController.refreshView();
                }
            } else {
                PrivateChatManager.select(conversation.tab().name());
                boolean alreadyPrivate = ChatFilterController.selected() == ChatChannel.PRIVATE;
                ChatFilterController.select(ChatChannel.PRIVATE);
                if (alreadyPrivate) {
                    ChatFilterController.refreshView();
                }
                chatField.setText(PrivateChatManager.retargetDraft(chatField.getText()));
                chatField.setCursorToEnd(false);
            }
            setFocused(chatField);
            cir.setReturnValue(true);
            return;
        }

        for (ChatChannel channel : mainChannels()) {
            int channelX = mainTabX(channel);
            int channelWidth = mainTabWidth(channel);
            if (!containsBox(channelX, tabY(), channelWidth, BUTTON_HEIGHT,
                    mouseX, mouseY)) {
                continue;
            }
            if (channel == ChatChannel.GROUP
                    && mouseX >= channelX + channelWidth - PRIVATE_CLOSE_WIDTH) {
                GroupChatManager.leave();
                // Ne jamais transporter un brouillon de groupe vers le chat public.
                chatField.setText("");
                chatField.setCursorToEnd(false);
                tropimonChatFilter$filterOpen = false;
                setFocused(chatField);
                cir.setReturnValue(true);
                return;
            }
            if (channel == ChatChannel.TOWN && TownChatNotifications.hasNoTown()) {
                showNoTownNotice();
                setFocused(chatField);
                cir.setReturnValue(true);
                return;
            }
            tropimonChatFilter$filterOpen = false;
            ChatFilterController.select(channel);
            chatField.setText(ChatOutgoingRouter.prepareInput(channel, chatField.getText()));
            chatField.setCursorToEnd(false);
            setFocused(chatField);
            cir.setReturnValue(true);
            return;
        }

        // Toute la surface du chat reste prioritaire si le HUD Cobblemon se
        // trouve visuellement derrière elle (messages cliquables compris).
        if (!client.options.hudHidden && !chatField.isMouseOver(mouseX, mouseY)) {
            int partySlot = PartyShareInput.slotAt(
                    height, mouseX, mouseY, chatRight(), tabY());
            if (partySlot != 0) {
                PartyShareInput.startDrag(partySlot, mouseX, mouseY);
                cir.setReturnValue(true);
            }
        }
    }

    @Unique
    private void tropimonChatFilter$drawPartyDrag(
            DrawContext context, int mouseX, int mouseY) {
        int partySlot = PartyShareInput.draggedSlot();
        if (partySlot == 0) {
            return;
        }
        String tag = PartyShareInput.tag(partySlot);
        int boxWidth = textRenderer.getWidth(tag) + 8;
        int x = Math.max(2, Math.min(width - boxWidth - 2, mouseX + 8));
        int y = Math.max(2, Math.min(height - 15, mouseY - 12));
        context.fill(x, y, x + boxWidth, y + 13, 0xFF72BECB);
        context.fill(x + 1, y + 1, x + boxWidth - 1, y + 12, 0xEE18252A);
        context.drawTextWithShadow(textRenderer, tag, x + 4, y + 2, 0xFFCBF6FF);
    }

    private boolean rightClickUsername(double mouseX, double mouseY) {
        var chatHud = client.inGameHud.getChatHud();
        ChatHudAccessor accessor = (ChatHudAccessor) chatHud;
        double chatX = accessor.tropimonChatFilter$toChatLineX(mouseX);
        double chatY = accessor.tropimonChatFilter$toChatLineY(mouseY);
        int lineIndex = accessor.tropimonChatFilter$getMessageLineIndex(chatX, chatY);
        List<net.minecraft.client.gui.hud.ChatHudLine.Visible> visible =
                accessor.tropimonChatFilter$visibleMessages();
        if (lineIndex < 0 || lineIndex >= visible.size()) {
            return false;
        }

        OrderedText line = visible.get(lineIndex).content();
        StringBuilder plain = new StringBuilder();
        line.accept((index, style, codePoint) -> {
            plain.appendCodePoint(codePoint);
            return true;
        });
        String localPlayer = client.player == null
                ? null : client.player.getGameProfile().getName();
        var presentation = ChatMessageDecorator.stripPresentation(plain.toString());
        var found = ClickableUsername.find(presentation.text(), localPlayer);
        if (found.isEmpty()) {
            return false;
        }

        ClickableUsername.Match rawMatch = found.get();
        ClickableUsername.Match match = new ClickableUsername.Match(
                rawMatch.start() + presentation.characterOffset(),
                rawMatch.end() + presentation.characterOffset(),
                rawMatch.name(), rawMatch.privateReply());
        float[] bounds = {0.0F, 0.0F};
        int[] characterOffset = {0};
        line.accept((index, style, codePoint) -> {
            float glyphWidth = textRenderer.getWidth(OrderedText.styled(codePoint, style));
            if (characterOffset[0] < match.start()) {
                bounds[0] += glyphWidth;
            }
            if (characterOffset[0] < match.end()) {
                bounds[1] += glyphWidth;
            }
            characterOffset[0] += Character.charCount(codePoint);
            return true;
        });
        if (chatX < bounds[0] || chatX >= bounds[1]) {
            return false;
        }

        chatField.setText(match.suggestion());
        chatField.setCursorToEnd(false);
        return true;
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void tropimonChatFilter$scrollPrivateTabs(
            double mouseX, double mouseY, double horizontalAmount, double verticalAmount,
            CallbackInfoReturnable<Boolean> cir) {
        updateLayoutMetrics();
        if (tropimonChatFilter$groupPopupOpen
                && containsBox(groupPopupX(), groupPopupY(), groupPopupWidth(),
                groupPopupHeight(), mouseX, mouseY)) {
            double wheel = verticalAmount != 0.0 ? verticalAmount : horizontalAmount;
            if (wheel != 0.0) {
                int maximum = Math.max(0,
                        availableGroupPlayers().size() - groupVisibleRows());
                tropimonChatFilter$groupPlayerScroll = Math.max(0, Math.min(maximum,
                        tropimonChatFilter$groupPlayerScroll + (wheel < 0.0 ? 1 : -1)));
            }
            cir.setReturnValue(true);
            return;
        }
        tropimonChatFilter$conversationButtons = calculateConversationButtons();
        boolean overPrivateTab = tropimonChatFilter$conversationButtons.stream()
                .anyMatch(button -> containsBox(
                        button.x(), tabY(), button.width(), BUTTON_HEIGHT,
                        mouseX, mouseY));
        if (!overPrivateTab) {
            return;
        }
        double wheel = verticalAmount != 0.0 ? verticalAmount : horizontalAmount;
        if (wheel == 0.0) {
            return;
        }
        int oldScroll = tropimonChatFilter$conversationScroll;
        if (wheel < 0.0) {
            List<PrivateChatManager.ConversationTab> tabs = PrivateChatManager.tabs();
            boolean lastTabVisible = !tabs.isEmpty()
                    && tropimonChatFilter$conversationButtons.stream()
                    .anyMatch(button -> button.tab().name()
                            .equalsIgnoreCase(tabs.get(tabs.size() - 1).name()));
            if (!lastTabVisible) {
                tropimonChatFilter$conversationScroll++;
            }
        } else {
            tropimonChatFilter$conversationScroll = Math.max(
                    0, tropimonChatFilter$conversationScroll - 1);
        }
        if (oldScroll != tropimonChatFilter$conversationScroll) {
            tropimonChatFilter$conversationButtons = calculateConversationButtons();
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void tropimonChatFilter$handleGroupPopupKeyboard(
            int keyCode, int scanCode, int modifiers,
            CallbackInfoReturnable<Boolean> cir) {
        if (!tropimonChatFilter$groupPopupOpen) {
            return;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closeGroupPopup();
            cir.setReturnValue(true);
            return;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            List<String> players = availableGroupPlayers();
            if (!players.isEmpty()) {
                inviteGroupPlayer(players.getFirst());
            }
            cir.setReturnValue(true);
            return;
        }
        if (tropimonChatFilter$groupSearch != null) {
            tropimonChatFilter$groupSearch.keyPressed(keyCode, scanCode, modifiers);
        }
        cir.setReturnValue(true);
    }

    @Inject(method = "sendMessage", at = @At("HEAD"), cancellable = true)
    private void tropimonChatFilter$sendGroupMessage(
            String message, boolean addToHistory, CallbackInfo ci) {
        if (ChatFilterController.selected() == ChatChannel.GROUP
                && message != null && !message.isBlank() && !message.startsWith("/")) {
            GroupChatManager.send(message, addToHistory);
            ci.cancel();
        }
    }

    @ModifyVariable(method = "sendMessage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private String tropimonChatFilter$routeOutgoingMessage(String message) {
        if (ChatFilterController.selected() == ChatChannel.TOWN
                && TownChatNotifications.hasNoTown()
                && !ChatOutgoingRouter.isExplicitCommand(message)) {
            showNoTownNotice();
            return "";
        }
        String routed = ChatOutgoingRouter.route(
                ChatFilterController.selected(), message, PrivateChatManager.currentName());
        ChatFilterController.observeOutgoingCommand(routed);
        PrivateChatManager.observeOutgoingCommand(routed);
        if (PrivateOutgoingMessage.parse(routed, PrivateChatManager.currentName()).isPresent()) {
            ChatFilterController.acknowledgeReply(ChatChannel.PRIVATE);
        } else if (ChatFilterController.selected() == ChatChannel.TOWN
                && routed != null && !routed.isBlank() && !routed.startsWith("/")) {
            ChatFilterController.acknowledgeReply(ChatChannel.TOWN);
        } else if (ChatFilterController.selected() == ChatChannel.STAFF
                && routed != null && !routed.isBlank() && !routed.startsWith("/")) {
            ChatFilterController.acknowledgeReply(ChatChannel.STAFF);
        }
        return routed;
    }

    private void drawMainTab(
            DrawContext context, ChatChannel channel, int x, int y, int buttonWidth,
            boolean hovered, boolean closeHovered, boolean active) {
        int unread = switch (channel) {
            case TOWN -> TownChatNotifications.unread();
            case STAFF -> StaffChatNotifications.unread();
            case GROUP -> GroupChatManager.unread();
            default -> 0;
        };
        boolean disabled = channel == ChatChannel.TOWN && TownChatNotifications.hasNoTown();
        boolean attention = !active && unread > 0
                && (System.currentTimeMillis() / 350L & 1L) == 0L;
        int outer = disabled ? 0xFF70434B : attention ? 0xFF69E58E
                : active ? 0xFF8C9BA2 : hovered ? 0xFF65747B : 0xFF303B40;
        int fill = disabled ? 0xF02C2023 : attention ? 0xE0225131
                : active ? 0xF529343A : hovered ? 0xF02A3439 : 0xF0182024;
        int text = disabled ? 0xFFFFAAB6 : attention ? 0xFFCFFFD9
                : active ? 0xFFFFFFFF : hovered ? 0xFFFFFFFF : 0xFFC7D0D4;
        drawButtonBase(context, x, y, buttonWidth, outer, fill,
                attention ? 0xFF75F29A : active ? 0xFFE6B94A : 0);
        Text label;
        if (channel == ChatChannel.TOWN && TownChatNotifications.hasNoTown()) {
            label = tropimonChatFilter$noTownLabel;
        } else if (channel == ChatChannel.TOWN && TownChatNotifications.townName() != null) {
            label = tropimonChatFilter$townLabel;
        } else if (channel == ChatChannel.TOWN) {
            label = tropimonChatFilter$unknownTownLabel;
        } else if (channel == ChatChannel.GROUP) {
            int online = GroupChatManager.connectedMemberCount();
            int total = GroupChatManager.memberCount();
            if (online != tropimonChatFilter$groupOnline || total != tropimonChatFilter$groupTotal) {
                tropimonChatFilter$groupOnline = online;
                tropimonChatFilter$groupTotal = total;
                tropimonChatFilter$groupLabel = Text.translatable("tropimon_chat_filter.tab.group_count", online, total);
            }
            label = tropimonChatFilter$groupLabel;
        } else {
            label = tropimonChatFilter$channelLabels.computeIfAbsent(channel, ChatChannel::label);
        }
        int labelCenter = x + buttonWidth / 2;
        if (channel == ChatChannel.GROUP) {
            int closeX = x + buttonWidth - PRIVATE_CLOSE_WIDTH;
            context.fill(closeX, y + 1, x + buttonWidth - 1, y + BUTTON_HEIGHT - 1,
                    closeHovered ? 0xE0965064 : 0x402D2025);
            context.drawCenteredTextWithShadow(textRenderer, "×",
                    closeX + PRIVATE_CLOSE_WIDTH / 2, y + 4,
                    closeHovered ? 0xFFFFFFFF : 0xFFBFC8CC);
            labelCenter = (x + closeX) / 2;
        }
        context.drawCenteredTextWithShadow(
                textRenderer, label, labelCenter, y + 4, text);
        if (unread > 0) {
            drawUnreadBadge(context, unread, x + buttonWidth - 2, y);
        }
    }

    private void drawConversationButton(
            DrawContext context, ConversationButtonLayout button, int mouseX, int mouseY) {
        boolean hovered = containsBox(button.x(), tabY(), button.width(), BUTTON_HEIGHT,
                mouseX, mouseY);
        boolean closeHovered = hovered
                && mouseX >= button.x() + button.width() - PRIVATE_CLOSE_WIDTH;
        boolean attention = button.tab().unread() > 0
                && (System.currentTimeMillis() / 350L & 1L) == 0L;
        boolean active = ChatFilterController.selected() == ChatChannel.PRIVATE
                && button.tab().selected();
        int outer = attention ? 0xFFFF5FA2 : active ? 0xFF72BECB
                : hovered ? 0xFF65747B : 0xFF303B40;
        int fill = attention ? 0xE04B1830 : active ? 0xE0244650
                : hovered ? 0xF02A3439 : 0xF0182024;
        drawButtonBase(context, button.x(), tabY(), button.width(), outer, fill,
                active ? 0xFF6FE7F2 : 0);

        int closeX = button.x() + button.width() - PRIVATE_CLOSE_WIDTH;
        context.fill(closeX, tabY() + 1, button.x() + button.width() - 1,
                tabY() + BUTTON_HEIGHT - 1, closeHovered ? 0xE0965064 : 0x402D2025);
        context.drawCenteredTextWithShadow(textRenderer, "×",
                closeX + PRIVATE_CLOSE_WIDTH / 2, tabY() + 4,
                closeHovered ? 0xFFFFFFFF : 0xFFBFC8CC);

        String label = button.label();
        int labelCenter = (button.x() + closeX) / 2;
        context.drawCenteredTextWithShadow(textRenderer, label, labelCenter, tabY() + 4,
                attention ? 0xFFFFD5E7 : active ? 0xFFCBF6FF : 0xFFD0D7DB);
        if (button.tab().unread() > 0) {
            drawUnreadBadge(context, button.tab().unread(), closeX + 2, tabY());
        }
    }

    private void drawHiddenUnreadNotification(DrawContext context) {
        if (tropimonChatFilter$hiddenUnreadMessageCount > 0) {
            String badge = tropimonChatFilter$hiddenUnreadMessageCount > 99
                    ? "99+" : Integer.toString(tropimonChatFilter$hiddenUnreadMessageCount);
            int badgeWidth = Math.max(11, textRenderer.getWidth(badge) + 4);
            drawUnreadBadge(context, tropimonChatFilter$hiddenUnreadMessageCount,
                    2 + badgeWidth, tabY());
        }
    }

    private void drawUnreadBadge(DrawContext context, int count, int right, int tabTop) {
        String badge = count > 99 ? "99+" : Integer.toString(count);
        int badgeWidth = Math.max(11, textRenderer.getWidth(badge) + 4);
        int left = right - badgeWidth;
        int top = Math.max(1, tabTop - 6);
        int bottom = top + 10;
        context.fill(left + 1, top, right - 1, bottom, 0xFFFF5FA2);
        context.fill(left, top + 1, right, bottom - 1, 0xFFFF5FA2);
        context.fill(left + 1, top + 1, right - 1, bottom - 1, 0xFF7A2345);
        context.drawCenteredTextWithShadow(
                textRenderer, badge, left + badgeWidth / 2, top + 1, 0xFFFFFFFF);
    }

    private void drawButtonBase(
            DrawContext context, int x, int y, int buttonWidth,
            int outer, int fill, int activeLine) {
        int right = x + buttonWidth;
        int bottom = y + BUTTON_HEIGHT;
        context.fill(x, y, right, bottom, outer);
        context.fill(x + 1, y + 1, right - 1, bottom - 1, fill);
        if (activeLine != 0) {
            context.fill(x + 1, bottom - 2, right - 1, bottom, activeLine);
        }
    }

    private void drawFilterButton(DrawContext context, int mouseX, int mouseY) {
        int x = filterButtonX();
        int y = tabY();
        boolean hovered = containsBox(x, y, FILTER_BUTTON_WIDTH, BUTTON_HEIGHT, mouseX, mouseY);
        boolean filtering = GlobalFilterSettings.hasHiddenCategories();
        int outer = tropimonChatFilter$filterOpen ? 0xFF72BECB
                : filtering ? 0xFFE6B94A : hovered ? 0xFF65747B : 0xFF303B40;
        int fill = tropimonChatFilter$filterOpen ? 0xE0244650
                : filtering ? 0xF03A3220 : hovered ? 0xF02A3439 : 0xF0182024;
        drawButtonBase(context, x, y, FILTER_BUTTON_WIDTH, outer, fill,
                filtering ? 0xFFFFCC55 : 0);
        int iconColor = tropimonChatFilter$filterOpen ? 0xFFCBF6FF
                : filtering ? 0xFFFFE49A : 0xFFD0D7DB;
        int lineLeft = x + 4;
        int lineRight = x + FILTER_BUTTON_WIDTH - 4;
        context.fill(lineLeft, y + 4, lineRight, y + 6, iconColor);
        context.fill(lineLeft, y + 7, lineRight, y + 9, iconColor);
        context.fill(lineLeft, y + 10, lineRight, y + 12, iconColor);
    }

    private void drawFilterPanel(DrawContext context, int mouseX, int mouseY) {
        int x = filterPanelX();
        int y = filterPanelY();
        int panelWidth = filterPanelWidth();
        int panelHeight = filterPanelHeight();
        context.fill(x - 2, y - 2, x + panelWidth + 2, y + panelHeight + 2, 0xF8000000);
        context.fill(x - 1, y - 1, x + panelWidth + 1, y + panelHeight + 1, 0xFF485860);
        context.fill(x, y, x + panelWidth, y + panelHeight, 0xFC12181C);

        for (int index = 0; index < FILTER_CATEGORIES.length; index++) {
            GlobalMessageCategory category = FILTER_CATEGORIES[index];
            int optionX = filterOptionX(index);
            int optionY = filterOptionY(index);
            boolean visible = GlobalFilterSettings.isVisible(category);
            boolean hovered = containsBox(optionX, optionY,
                    FILTER_OPTION_WIDTH, FILTER_OPTION_HEIGHT, mouseX, mouseY);
            int fill = visible
                    ? hovered ? 0xE0356653 : 0xE0274E40
                    : hovered ? 0xE0643439 : 0xE044292D;
            int border = visible ? 0xFF60C98B : 0xFF9D5960;
            context.fill(optionX, optionY,
                    optionX + FILTER_OPTION_WIDTH, optionY + FILTER_OPTION_HEIGHT, border);
            context.fill(optionX + 1, optionY + 1,
                    optionX + FILTER_OPTION_WIDTH - 1, optionY + FILTER_OPTION_HEIGHT - 1, fill);
            context.drawTextWithShadow(textRenderer, visible ? "✓" : "×",
                    optionX + 4, optionY + 3, visible ? 0xFF9CFFC2 : 0xFFFFA7AF);
            String label = textRenderer.trimToWidth(
                    category.label().getString(), FILTER_OPTION_WIDTH - 18);
            context.drawTextWithShadow(textRenderer, label,
                    optionX + 15, optionY + 3, visible ? 0xFFE6FFF0 : 0xFFFFD8DB);
        }

        drawBooleanSetting(context, settingsOptionY(0), mouseX, mouseY,
                Text.translatable("tropimon_chat_filter.option.timestamps"),
                GlobalFilterSettings.showTimestamps());
        drawBooleanSetting(context, settingsOptionY(1), mouseX, mouseY,
                Text.translatable("tropimon_chat_filter.option.heads"),
                GlobalFilterSettings.showPlayerHeads());
        int previewY = settingsOptionY(2);
        boolean previewHovered = containsBox(filterPanelX(), previewY,
                filterPanelWidth(), FILTER_OPTION_HEIGHT, mouseX, mouseY);
        drawFullOption(context, previewY, previewHovered, true,
                Text.translatable("tropimon_chat_filter.option.return_all",
                        previewLabel()));
        drawBooleanSetting(context, settingsOptionY(3), mouseX, mouseY,
                Text.translatable("tropimon_chat_filter.option.all_chats_in_all"),
                GlobalFilterSettings.showAllChatsInAll());
        drawBooleanSetting(context, settingsOptionY(4), mouseX, mouseY,
                Text.translatable("tropimon_chat_filter.option.keep_chat_visible"),
                GlobalFilterSettings.keepChatVisible());

        int groupY = groupOptionY();
        if (!GroupChatManager.active()) {
            boolean hovered = containsBox(filterPanelX(), groupY,
                    filterPanelWidth(), FILTER_OPTION_HEIGHT, mouseX, mouseY);
            drawFullOption(context, groupY, hovered, false,
                    Text.translatable("tropimon_chat_filter.group.create"));
        } else {
            int leftWidth = FILTER_OPTION_WIDTH;
            boolean inviteHovered = containsBox(filterPanelX(), groupY,
                    leftWidth, FILTER_OPTION_HEIGHT, mouseX, mouseY);
            boolean leaveHovered = containsBox(filterPanelX() + leftWidth + BUTTON_GAP, groupY,
                    FILTER_OPTION_WIDTH, FILTER_OPTION_HEIGHT, mouseX, mouseY);
            drawHalfOption(context, filterPanelX(), groupY, inviteHovered, false,
                    Text.translatable("tropimon_chat_filter.group.invite"));
            drawHalfOption(context, filterPanelX() + leftWidth + BUTTON_GAP, groupY,
                    leaveHovered, false,
                    Text.translatable("tropimon_chat_filter.group.leave"));
        }
    }

    private boolean clickFilterOption(double mouseX, double mouseY) {
        for (int index = 0; index < FILTER_CATEGORIES.length; index++) {
            GlobalMessageCategory category = FILTER_CATEGORIES[index];
            if (containsBox(filterOptionX(index), filterOptionY(index),
                    FILTER_OPTION_WIDTH, FILTER_OPTION_HEIGHT, mouseX, mouseY)) {
                GlobalFilterSettings.toggle(category);
                return true;
            }
        }
        if (containsBox(filterPanelX(), settingsOptionY(0),
                filterPanelWidth(), FILTER_OPTION_HEIGHT, mouseX, mouseY)) {
            GlobalFilterSettings.toggleTimestamps();
            return true;
        }
        if (containsBox(filterPanelX(), settingsOptionY(1),
                filterPanelWidth(), FILTER_OPTION_HEIGHT, mouseX, mouseY)) {
            GlobalFilterSettings.togglePlayerHeads();
            return true;
        }
        if (containsBox(filterPanelX(), settingsOptionY(2),
                filterPanelWidth(), FILTER_OPTION_HEIGHT, mouseX, mouseY)) {
            GlobalFilterSettings.cyclePreviewSeconds();
            return true;
        }
        if (containsBox(filterPanelX(), settingsOptionY(3),
                filterPanelWidth(), FILTER_OPTION_HEIGHT, mouseX, mouseY)) {
            GlobalFilterSettings.toggleAllChatsInAll();
            return true;
        }
        if (containsBox(filterPanelX(), settingsOptionY(4),
                filterPanelWidth(), FILTER_OPTION_HEIGHT, mouseX, mouseY)) {
            GlobalFilterSettings.toggleKeepChatVisible();
            return true;
        }
        if (containsBox(filterPanelX(), groupOptionY(),
                FILTER_OPTION_WIDTH, FILTER_OPTION_HEIGHT, mouseX, mouseY)
                || !GroupChatManager.active() && containsBox(
                        filterPanelX(), groupOptionY(), filterPanelWidth(),
                        FILTER_OPTION_HEIGHT, mouseX, mouseY)) {
            openGroupPopup();
            return true;
        }
        if (GroupChatManager.active() && containsBox(
                filterPanelX() + FILTER_OPTION_WIDTH + BUTTON_GAP, groupOptionY(),
                FILTER_OPTION_WIDTH, FILTER_OPTION_HEIGHT, mouseX, mouseY)) {
            GroupChatManager.leave();
            tropimonChatFilter$filterOpen = false;
            return true;
        }
        return false;
    }

    private void drawBooleanSetting(
            DrawContext context, int y, int mouseX, int mouseY, Text label, boolean enabled) {
        boolean hovered = containsBox(filterPanelX(), y,
                filterPanelWidth(), FILTER_OPTION_HEIGHT, mouseX, mouseY);
        drawFullOption(context, y, hovered, enabled,
                Text.translatable("tropimon_chat_filter.option.toggle", label,
                        Text.translatable(enabled
                                ? "tropimon_chat_filter.option.yes"
                                : "tropimon_chat_filter.option.no")));
    }

    private void drawFullOption(
            DrawContext context, int y, boolean hovered, boolean enabled, Text label) {
        int fill = hovered ? 0xE0354650 : 0xE0212B30;
        int border = enabled ? 0xFF60C98B : 0xFF52636B;
        context.fill(filterPanelX(), y,
                filterPanelX() + filterPanelWidth(), y + FILTER_OPTION_HEIGHT, border);
        context.fill(filterPanelX() + 1, y + 1,
                filterPanelX() + filterPanelWidth() - 1, y + FILTER_OPTION_HEIGHT - 1, fill);
        String text = textRenderer.trimToWidth(label.getString(), filterPanelWidth() - 8);
        context.drawTextWithShadow(textRenderer, text,
                filterPanelX() + 4, y + 3, hovered ? 0xFFFFFFFF : 0xFFDCE5E9);
    }

    private void drawHalfOption(
            DrawContext context, int x, int y, boolean hovered, boolean enabled, Text label) {
        int fill = hovered ? 0xE0354650 : 0xE0212B30;
        int border = enabled ? 0xFF60C98B : 0xFF52636B;
        context.fill(x, y, x + FILTER_OPTION_WIDTH, y + FILTER_OPTION_HEIGHT, border);
        context.fill(x + 1, y + 1,
                x + FILTER_OPTION_WIDTH - 1, y + FILTER_OPTION_HEIGHT - 1, fill);
        String text = textRenderer.trimToWidth(label.getString(), FILTER_OPTION_WIDTH - 8);
        context.drawCenteredTextWithShadow(textRenderer, text,
                x + FILTER_OPTION_WIDTH / 2, y + 3,
                hovered ? 0xFFFFFFFF : 0xFFDCE5E9);
    }

    private String previewLabel() {
        int seconds = GlobalFilterSettings.previewSeconds();
        return seconds == 0
                ? Text.translatable("tropimon_chat_filter.option.never").getString()
                : Text.translatable("tropimon_chat_filter.option.seconds", seconds).getString();
    }

    private void openGroupPopup() {
        tropimonChatFilter$filterOpen = false;
        tropimonChatFilter$groupPopupOpen = true;
        tropimonChatFilter$groupPlayerScroll = 0;
        if (tropimonChatFilter$groupSearch == null) {
            tropimonChatFilter$groupSearch = new TextFieldWidget(
                    textRenderer, groupPopupX() + 4, groupSearchY(),
                    groupPopupWidth() - 8, GROUP_SEARCH_HEIGHT,
                    Text.translatable("tropimon_chat_filter.group.popup.search"));
            tropimonChatFilter$groupSearch.setMaxLength(16);
            tropimonChatFilter$groupSearch.setTextPredicate(
                    value -> value.matches("[A-Za-z0-9_]*"));
            tropimonChatFilter$groupSearch.setPlaceholder(
                    Text.translatable("tropimon_chat_filter.group.popup.search"));
            tropimonChatFilter$groupSearch.setChangedListener(
                    value -> {
                        tropimonChatFilter$groupPlayerScroll = 0;
                        tropimonChatFilter$nextGroupListRefresh = 0L;
                    });
            // Reçoit la saisie native, mais son rendu reste intégré à notre panneau.
            addSelectableChild(tropimonChatFilter$groupSearch);
        }
        tropimonChatFilter$groupSearch.setX(groupPopupX() + 4);
        tropimonChatFilter$groupSearch.setY(groupSearchY());
        tropimonChatFilter$groupSearch.setWidth(groupPopupWidth() - 8);
        tropimonChatFilter$groupSearch.setText("");
        tropimonChatFilter$groupSearch.setVisible(true);
        tropimonChatFilter$groupSearch.setFocused(true);
        setFocused(tropimonChatFilter$groupSearch);
    }

    private void closeGroupPopup() {
        tropimonChatFilter$groupPopupOpen = false;
        if (tropimonChatFilter$groupSearch != null) {
            tropimonChatFilter$groupSearch.setFocused(false);
            tropimonChatFilter$groupSearch.setVisible(false);
        }
        setFocused(chatField);
    }

    private void drawGroupPopup(
            DrawContext context, int mouseX, int mouseY, float delta) {
        int x = groupPopupX();
        int y = groupPopupY();
        int popupWidth = groupPopupWidth();
        int popupHeight = groupPopupHeight();
        int visibleRows = groupVisibleRows();
        context.fill(x - 2, y - 2, x + popupWidth + 2,
                y + popupHeight + 2, 0xF8000000);
        context.fill(x - 1, y - 1, x + popupWidth + 1,
                y + popupHeight + 1, 0xFF596A72);
        context.fill(x, y, x + popupWidth,
                y + popupHeight, 0xFC12181C);

        String title = Text.translatable("tropimon_chat_filter.group.popup.title",
                Math.max(1, GroupChatManager.memberCount()), GroupChatManager.MAX_MEMBERS)
                .getString();
        context.drawTextWithShadow(textRenderer,
                textRenderer.trimToWidth(title, popupWidth - 30),
                x + 5, y + 6, 0xFFE8F2F5);
        boolean closeHovered = containsBox(x + popupWidth - 18, y + 3,
                15, 15, mouseX, mouseY);
        context.fill(x + popupWidth - 18, y + 3,
                x + popupWidth - 3, y + 18,
                closeHovered ? 0xFF874B5B : 0xFF29343A);
        context.drawCenteredTextWithShadow(textRenderer, "×",
                x + popupWidth - 11, y + 6,
                closeHovered ? 0xFFFFFFFF : 0xFFC8D2D6);

        tropimonChatFilter$groupSearch.setX(x + 4);
        tropimonChatFilter$groupSearch.setY(groupSearchY());
        tropimonChatFilter$groupSearch.setWidth(popupWidth - 8);
        tropimonChatFilter$groupSearch.render(context, mouseX, mouseY, delta);

        List<String> players = availableGroupPlayers();
        int maximumScroll = Math.max(0, players.size() - visibleRows);
        tropimonChatFilter$groupPlayerScroll = Math.max(0, Math.min(
                tropimonChatFilter$groupPlayerScroll, maximumScroll));
        if (players.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.translatable("tropimon_chat_filter.group.popup.empty"),
                    x + popupWidth / 2, groupRowsY() + 6, 0xFF94A2A8);
            return;
        }

        int end = Math.min(players.size(),
                tropimonChatFilter$groupPlayerScroll + visibleRows);
        for (int index = tropimonChatFilter$groupPlayerScroll; index < end; index++) {
            int row = index - tropimonChatFilter$groupPlayerScroll;
            int rowY = groupRowsY() + row * GROUP_ROW_HEIGHT;
            boolean hovered = containsBox(x + 4, rowY,
                    popupWidth - 8, GROUP_ROW_HEIGHT - 1, mouseX, mouseY);
            context.fill(x + 4, rowY, x + popupWidth - 4,
                    rowY + GROUP_ROW_HEIGHT - 1,
                    hovered ? 0xFF386071 : 0xE0222D32);
            context.fill(x + 4, rowY, x + 6,
                    rowY + GROUP_ROW_HEIGHT - 1,
                    hovered ? 0xFF6FE7F2 : 0xFF485860);
            context.drawTextWithShadow(textRenderer, players.get(index),
                    x + 10, rowY + 3, hovered ? 0xFFFFFFFF : 0xFFD7E1E5);
        }
    }

    private void clickGroupPopup(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return;
        }
        int x = groupPopupX();
        int y = groupPopupY();
        int popupWidth = groupPopupWidth();
        if (!containsBox(x, y, popupWidth, groupPopupHeight(),
                mouseX, mouseY)) {
            closeGroupPopup();
            return;
        }
        if (containsBox(x + popupWidth - 18, y + 3,
                15, 15, mouseX, mouseY)) {
            closeGroupPopup();
            return;
        }
        if (tropimonChatFilter$groupSearch.mouseClicked(mouseX, mouseY, button)) {
            tropimonChatFilter$groupSearch.setFocused(true);
            setFocused(tropimonChatFilter$groupSearch);
            return;
        }
        List<String> players = availableGroupPlayers();
        int row = (int) ((mouseY - groupRowsY()) / GROUP_ROW_HEIGHT);
        int index = tropimonChatFilter$groupPlayerScroll + row;
        if (row >= 0 && row < groupVisibleRows() && index >= 0 && index < players.size()
                && containsBox(x + 4, groupRowsY() + row * GROUP_ROW_HEIGHT,
                popupWidth - 8, GROUP_ROW_HEIGHT - 1, mouseX, mouseY)) {
            inviteGroupPlayer(players.get(index));
        }
    }

    private void inviteGroupPlayer(String player) {
        GroupChatManager.invite(player);
        tropimonChatFilter$groupPlayerScroll = 0;
        tropimonChatFilter$nextGroupListRefresh = 0L;
        tropimonChatFilter$groupSearch.setText("");
        tropimonChatFilter$groupSearch.setFocused(true);
        setFocused(tropimonChatFilter$groupSearch);
    }

    private List<String> availableGroupPlayers() {
        Object currentHandler = client.getNetworkHandler();
        if (currentHandler == null) {
            tropimonChatFilter$cachedGroupPlayers = List.of();
            tropimonChatFilter$cachedGroupHandler = null;
            return List.of();
        }
        String local = client.player == null
                ? "" : client.player.getGameProfile().getName();
        String query = tropimonChatFilter$groupSearch == null
                ? "" : tropimonChatFilter$groupSearch.getText().toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        if (currentHandler == tropimonChatFilter$cachedGroupHandler
                && query.equals(tropimonChatFilter$cachedGroupQuery)
                && now < tropimonChatFilter$nextGroupListRefresh) {
            return tropimonChatFilter$cachedGroupPlayers;
        }
        tropimonChatFilter$cachedGroupHandler = currentHandler;
        tropimonChatFilter$cachedGroupQuery = query;
        tropimonChatFilter$nextGroupListRefresh = now + GROUP_LIST_CACHE_MILLIS;
        tropimonChatFilter$cachedGroupPlayers = client.getNetworkHandler().getPlayerList().stream()
                .map(PlayerListEntry::getProfile)
                .map(profile -> profile.getName())
                .filter(name -> !name.equalsIgnoreCase(local))
                .filter(name -> !GroupChatManager.isMember(name))
                .filter(name -> name.toLowerCase(Locale.ROOT).contains(query))
                .sorted(Comparator.comparing(
                        name -> name.toLowerCase(Locale.ROOT)))
                .toList();
        return tropimonChatFilter$cachedGroupPlayers;
    }

    private int groupPopupX() {
        int popupWidth = groupPopupWidth();
        return Math.max(3, Math.min(chatRight() - popupWidth,
                width - popupWidth - 3));
    }

    private int groupPopupY() {
        int popupHeight = groupPopupHeight();
        int above = tabY() - popupHeight - 4;
        int preferred = above >= 3 ? above : tabY() + BUTTON_HEIGHT + 4;
        return Math.max(3, Math.min(preferred, height - popupHeight - 3));
    }

    private int groupPopupWidth() {
        return Math.max(120, Math.min(GROUP_POPUP_WIDTH, width - 6));
    }

    private int groupVisibleRows() {
        int availableHeight = Math.max(58, Math.min(GROUP_POPUP_HEIGHT, height - 6));
        return Math.max(1, Math.min(GROUP_VISIBLE_ROWS,
                (availableHeight - 42) / GROUP_ROW_HEIGHT));
    }

    private int groupPopupHeight() {
        return 42 + groupVisibleRows() * GROUP_ROW_HEIGHT;
    }

    private int groupSearchY() {
        return groupPopupY() + 20;
    }

    private int groupRowsY() {
        return groupPopupY() + 39;
    }

    private void refreshPrivateConversation() {
        PrivateChatManager.markSelectedRead();
        ChatFilterController.refreshView();
        chatField.setText(PrivateChatManager.retargetDraft(chatField.getText()));
        chatField.setCursorToEnd(false);
    }

    private void showNoTownNotice() {
        if (client.player != null) {
            client.player.sendMessage(
                    Text.translatable("tropimon_chat_filter.notice.no_town"), true);
        }
    }

    private List<ConversationButtonLayout> calculateConversationButtons() {
        List<PrivateChatManager.ConversationTab> tabs = PrivateChatManager.tabs();
        tropimonChatFilter$conversationScroll = Math.max(0, Math.min(
                tropimonChatFilter$conversationScroll, Math.max(0, tabs.size() - 1)));
        if (tropimonChatFilter$measure == null) {
            tropimonChatFilter$measure = textRenderer::getWidth;
            tropimonChatFilter$trim = textRenderer::trimToWidth;
        }
        List<ConversationButtonLayout> buttons = tropimonChatFilter$layout.get(tabs,
                2, mainTabsLeft(), tropimonChatFilter$conversationScroll,
                client.getNetworkHandler(), RenderCacheEpoch.current(),
                tropimonChatFilter$measure, tropimonChatFilter$trim);
        tropimonChatFilter$hiddenUnreadMessageCount =
                tropimonChatFilter$layout.hiddenUnreadMessageCount();
        return buttons;
    }

    private int mainTabX(ChatChannel channel) {
        int x = mainTabsLeft();
        for (ChatChannel candidate : mainChannels()) {
            if (candidate == channel) {
                return x;
            }
            x += mainTabWidth(candidate) + BUTTON_GAP;
        }
        return x;
    }

    private int mainTabsLeft() {
        return filterButtonX() - BUTTON_GAP - mainTabsTotalWidth();
    }

    private int mainTabsTotalWidth() {
        List<ChatChannel> channels = mainChannels();
        int width = 0;
        for (ChatChannel channel : channels) {
            width += mainTabWidth(channel);
        }
        return width + Math.max(0, channels.size() - 1) * BUTTON_GAP;
    }

    private int mainTabWidth(ChatChannel channel) {
        return switch (channel) {
            case TOWN -> townButtonWidth();
            case STAFF -> STAFF_BUTTON_WIDTH;
            case GROUP -> GROUP_BUTTON_WIDTH;
            default -> ALL_BUTTON_WIDTH;
        };
    }

    private List<ChatChannel> mainChannels() {
        return tropimonChatFilter$channels;
    }

    private List<ChatChannel> detectMainChannels() {
        boolean staff = StaffStatus.isStaff();
        boolean group = GroupChatManager.active();
        if (staff && group) {
            return STAFF_GROUP_CHANNELS;
        }
        if (staff) {
            return STAFF_CHANNELS;
        }
        return group ? GROUP_CHANNELS : BASIC_CHANNELS;
    }

    private int townButtonWidth() {
        return tropimonChatFilter$townWidth;
    }

    private void updateLayoutMetrics() {
        tropimonChatFilter$channels = detectMainChannels();
        tropimonChatFilter$tabY = calculateTabY();
        tropimonChatFilter$chatRight = calculateChatRight();
        String town = TownChatNotifications.townName();
        Object fontEpoch = RenderCacheEpoch.current();
        if (!Objects.equals(town, tropimonChatFilter$measuredTown) || fontEpoch != tropimonChatFilter$fontEpoch) {
            tropimonChatFilter$measuredTown = town;
            tropimonChatFilter$fontEpoch = fontEpoch;
            tropimonChatFilter$townWidth = town == null || town.isBlank() ? TOWN_MIN_WIDTH
                    : Math.max(TOWN_MIN_WIDTH, Math.min(TOWN_MAX_WIDTH, textRenderer.getWidth(town) + 10));
            tropimonChatFilter$townLabel = town == null ? null
                    : Text.literal(textRenderer.trimToWidth(town, tropimonChatFilter$townWidth - 6));
        }
    }

    private int chatRight() {
        return tropimonChatFilter$chatRight;
    }

    private int calculateChatRight() {
        double chatScale = client.inGameHud.getChatHud().getChatScale();
        return Math.min(width,
                4 + (int) Math.ceil(client.inGameHud.getChatHud().getWidth() * chatScale)
                        + TAB_RIGHT_OFFSET);
    }

    private int filterButtonX() {
        return chatRight() - FILTER_BUTTON_WIDTH;
    }

    private int filterPanelX() {
        return Math.max(3, Math.min(filterButtonX(), width - filterPanelWidth() - 3));
    }

    private int filterPanelY() {
        int above = tabY() - filterPanelHeight() - 4;
        return above >= 3 ? above : tabY() + BUTTON_HEIGHT + 4;
    }

    private int filterPanelWidth() {
        return FILTER_OPTION_WIDTH * 2 + BUTTON_GAP;
    }

    private int filterPanelHeight() {
        return FILTER_OPTION_HEIGHT * FILTER_ROWS + BUTTON_GAP * (FILTER_ROWS - 1);
    }

    private int filterOptionX(int index) {
        return filterPanelX() + index % 2 * (FILTER_OPTION_WIDTH + BUTTON_GAP);
    }

    private int filterOptionY(int index) {
        return filterPanelY() + index / 2 * (FILTER_OPTION_HEIGHT + BUTTON_GAP);
    }

    private int settingsOptionY(int index) {
        return filterPanelY() + (3 + index) * (FILTER_OPTION_HEIGHT + BUTTON_GAP);
    }

    private int groupOptionY() {
        return filterPanelY() + (3 + FILTER_SETTINGS_COUNT)
                * (FILTER_OPTION_HEIGHT + BUTTON_GAP);
    }

    private int tabY() {
        return tropimonChatFilter$tabY;
    }

    private int calculateTabY() {
        var chatHud = client.inGameHud.getChatHud();
        double chatScale = chatHud.getChatScale();
        int maximumLines = Math.max(1, chatHud.getVisibleLineCount());
        int occupiedLines = Math.min(maximumLines,
                ((ChatHudAccessor) chatHud).tropimonChatFilter$visibleMessages().size());
        int lineHeight = Math.max(1, chatHud.getHeight() / maximumLines);
        int occupiedHeight = (int) Math.ceil(occupiedLines * lineHeight * chatScale);
        int chatTop = height - 40 - occupiedHeight;
        return Math.max(3, chatTop - BUTTON_HEIGHT);
    }

    private boolean containsBox(
            int x, int y, int boxWidth, int boxHeight, double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + boxWidth
                && mouseY >= y && mouseY < y + boxHeight;
    }

}
