package fr.tropimon.chatfilter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.ToIntFunction;

/** Une seule disposition (au plus 64 onglets), indépendante de Y et de l'animation. */
public final class ConversationLayoutCache {
    private List<PrivateChatManager.ConversationTab> tabs;
    private Object connection;
    private Object fontEpoch;
    private int minimumLeft;
    private int left;
    private int scroll;
    private List<ConversationButtonLayout> buttons = List.of();
    private int hiddenUnreadMessageCount;

    public List<ConversationButtonLayout> get(List<PrivateChatManager.ConversationTab> currentTabs,
            int minLeft, int mainLeft, int offset, Object session, Object font,
            ToIntFunction<String> measure, BiFunction<String, Integer, String> trim) {
        if (tabs == currentTabs && connection == session && fontEpoch == font
                && minimumLeft == minLeft && left == mainLeft && scroll == offset) return buttons;
        tabs = currentTabs;
        connection = session;
        fontEpoch = font;
        minimumLeft = minLeft;
        left = mainLeft;
        scroll = offset;

        List<ConversationButtonLayout> result = build(
                currentTabs, minLeft, mainLeft, offset, measure, trim);
        hiddenUnreadMessageCount = hiddenUnreadMessageCount(currentTabs, offset, result.size());
        buttons = List.copyOf(result);
        return buttons;
    }

    public int hiddenUnreadMessageCount() {
        return hiddenUnreadMessageCount;
    }

    private static List<ConversationButtonLayout> build(
            List<PrivateChatManager.ConversationTab> currentTabs,
            int minLeft, int mainLeft, int offset,
            ToIntFunction<String> measure, BiFunction<String, Integer, String> trim) {
        List<ConversationButtonLayout> result = new ArrayList<>();
        int cursor = mainLeft - 1;
        int remaining = currentTabs.size() - offset;
        int desired = Math.min(3, remaining);
        int available = mainLeft - minLeft - 1;
        int widthCap = Math.max(58, Math.min(106, available));
        if (desired == 3 && available >= desired * 58 + desired - 1) {
            widthCap = Math.min(widthCap, (available - desired + 1) / desired);
        }
        for (int index = offset; index < currentTabs.size(); index++) {
            var tab = currentTabs.get(index);
            int width = Math.max(58, Math.min(widthCap,
                    measure.applyAsInt(tab.name()) + 15 + 10));
            int x = cursor - width;
            if (x < minLeft) break;
            result.add(new ConversationButtonLayout(tab, x, width, trim.apply(tab.name(), width - 15 - 7)));
            cursor = x - 1;
        }
        return result;
    }

    private static int hiddenUnreadMessageCount(
            List<PrivateChatManager.ConversationTab> tabs, int offset, int visible) {
        int count = 0;
        int visibleEnd = Math.min(tabs.size(), offset + visible);
        for (int index = 0; index < tabs.size(); index++) {
            if (index < offset || index >= visibleEnd) {
                count = Math.min(100, count + Math.max(0, tabs.get(index).unread()));
            }
        }
        return count;
    }

}
