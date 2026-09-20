package fr.tropimon.chatfilter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;

/** Exercises the production ChatScreen mixin in a disposable offline world. */
final class ChatGuiSmoke {
    private int scale = 1, phase, wait;
    private Screen screen;

    boolean advance(MinecraftClient client) throws Exception {
        if (++wait < 12) return false;
        wait = 0;
        switch (phase++) {
            case 0 -> {
                if (scale == 1) client.getServer().submit(() ->
                        client.getServer().getCommandManager().getDispatcher().register(
                                net.minecraft.server.command.CommandManager.literal("chat")
                                        .then(net.minecraft.server.command.CommandManager.argument("channel",
                                                com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                                .executes(context -> 1)))).get();
                client.options.getGuiScale().setValue(scale);
                client.onResolutionChanged();
                client.options.getChatWidth().setValue(1.0);
                client.options.pauseOnLostFocus = false;
                client.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
                client.getToastManager().clear();
                client.setScreen(new ChatScreen(""));
                screen = client.currentScreen;
                var hud = client.inGameHud.getChatHud();
                hud.clear(false);
                hud.addMessage(Text.literal("[ꑤ Alex 석 VilleAvecUnNomVraimentTresLong] Message de ville fictif"));
                hud.addMessage(Text.literal("[ꑤ ExamplePlayer1234 석 Moi] Message privé fictif"));
                StaffStatus.confirmFromStaffMessage();
                ChatFilterController.select(ChatChannel.ALL);
                check(GroupChatManager.active(), "group fixture retained");
            }
            case 1 -> {
                check(client.getWindow().getScaleFactor() == scale, "effective GUI scale " + scale);
                check(screen.width == client.getWindow().getScaledWidth(), "screen resized");
                checkLayout();
                capture(client, "tabs");
                for (ChatChannel channel : List.of(ChatChannel.ALL, ChatChannel.TOWN, ChatChannel.STAFF, ChatChannel.GROUP)) {
                    call("updateLayoutMetrics");
                    int x = (int) call("mainTabX", new Class<?>[]{ChatChannel.class}, channel);
                    screen.mouseClicked(x + 4, number("tabY") + 7, 0);
                    check(ChatFilterController.selected() == channel, "click " + channel);
                }
                client.options.getChatWidth().setValue(0.0);
                client.inGameHud.getChatHud().reset();
            }
            case 2 -> {
                checkLayout();
                var tabs = buttons();
                check(!tabs.isEmpty(), "private tab remains with narrow chat");
                var tab = tabs.getFirst();
                screen.mouseClicked(tab.x() + 3, number("tabY") + 7, 0);
                check(ChatFilterController.selected() == ChatChannel.PRIVATE, "private click");
                check(PrivateChatManager.currentName().equals(tab.tab().name()), "correct private recipient");
                call("updateLayoutMetrics");
                screen.mouseClicked(number("filterButtonX") + 6, number("tabY") + 7, 0);
                check((boolean) field("tropimonChatFilter$filterOpen"), "filter opens");
            }
            case 3 -> {
                rectangle("filterPanel");
                capture(client, "filters-narrow");
                boolean before = GlobalFilterSettings.showTimestamps();
                int y = (int) call("settingsOptionY", new Class<?>[]{int.class}, 0);
                screen.mouseClicked(number("filterPanelX") + 5, y + 6, 0);
                check(GlobalFilterSettings.showTimestamps() != before, "filter setting click");
                screen.mouseClicked(number("filterPanelX") + 5, y + 6, 0);
                screen.mouseClicked(number("filterPanelX") + 5, number("groupOptionY") + 6, 0);
                check((boolean) field("tropimonChatFilter$groupPopupOpen"), "group popup opens");
            }
            case 4 -> {
                rectangle("groupPopup");
                var search = (TextFieldWidget) field("tropimonChatFilter$groupSearch");
                check(search.getX() >= 0 && search.getX() + search.getWidth() <= screen.width,
                        "group input bounds");
                screen.mouseClicked(search.getX() + 4, search.getY() + 5, 0);
                screen.charTyped('E', 0);
                check(search.getText().equals("E"), "group input typing");
                capture(client, "group");
                screen.mouseClicked(number("groupPopupX") + number("groupPopupWidth") - 10,
                        number("groupPopupY") + 9, 0);
                check(!(boolean) field("tropimonChatFilter$groupPopupOpen"), "group closes");
                System.out.println("CHAT_GUI_OK requested=" + scale + " effective=" + client.getWindow().getScaleFactor()
                        + " viewport=" + screen.width + "x" + screen.height + " tabs,filters,group,private,narrow");
                phase = 0;
                if (++scale > 4) return true;
            }
            default -> throw new AssertionError("Unexpected stage");
        }
        return false;
    }

    private void checkLayout() throws Exception {
        call("updateLayoutMetrics");
        for (String option : List.of("timestamps", "heads", "all_chats_in_all", "keep_chat_visible")) {
            for (String state : List.of("yes", "no")) {
                Text label = Text.translatable("tropimon_chat_filter.option.toggle",
                        Text.translatable("tropimon_chat_filter.option." + option),
                        Text.translatable("tropimon_chat_filter.option." + state));
                check(MinecraftClient.getInstance().textRenderer.getWidth(label) <= number("filterPanelWidth") - 8,
                        "complete setting label and value: " + option + "/" + state);
            }
        }
        check(number("mainTabsLeft") >= 2, "main tabs inside screen");
        check(number("filterButtonX") + 16 <= screen.width, "filter inside screen");
        check(number("tabY") >= 0 && number("tabY") + 16 <= screen.height, "tabs vertical bounds");
        for (var tab : buttons()) check(tab.x() >= 2 && tab.x() + tab.width() < number("mainTabsLeft"), "private bounds");
    }
    @SuppressWarnings("unchecked")
    private List<ConversationButtonLayout> buttons() throws Exception {
        return (List<ConversationButtonLayout>) call("calculateConversationButtons");
    }
    private void rectangle(String prefix) throws Exception {
        check(number(prefix + "X") >= 0 && number(prefix + "Y") >= 0
                && number(prefix + "X") + number(prefix + "Width") <= screen.width
                && number(prefix + "Y") + number(prefix + "Height") <= screen.height, prefix + " bounds");
    }
    private Object field(String name) throws Exception {
        var field = screen.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(screen);
    }
    private int number(String name) throws Exception { return (int) call(name); }
    private Object call(String name) throws Exception { return call(name, new Class<?>[0]); }
    private Object call(String name, Class<?>[] types, Object... args) throws Exception {
        var method = screen.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(screen, args);
    }
    private void capture(MinecraftClient client, String name) throws Exception {
        Path folder = client.runDirectory.toPath().resolve("verification");
        Files.createDirectories(folder);
        try (var image = ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) {
            image.writeTo(folder.resolve("chat-gui" + scale + "-" + name + ".png"));
        }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
