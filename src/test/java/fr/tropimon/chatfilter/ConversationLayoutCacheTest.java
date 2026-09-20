package fr.tropimon.chatfilter;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.ToIntFunction;
import static org.junit.jupiter.api.Assertions.*;

class ConversationLayoutCacheTest {
    @Test void longConversationRemainsReachableInTheSpaceReservedOnSmallGuiViewports() {
        var cache = new ConversationLayoutCache();
        var tabs = List.of(new PrivateChatManager.ConversationTab("LongExampleName", 7, false));
        Object session = new Object(), font = new Object();
        for (int available : new int[] {58, 60, 63, 64, 90, 106, 300}) {
            var buttons = cache.get(tabs, 3, available + 4, 0, session, font,
                    name -> name.length() * 6, (name, width) -> name.substring(0, Math.min(name.length(), width / 6)));
            assertEquals(1, buttons.size(), "Reserved width: " + available);
            var button = buttons.getFirst();
            assertTrue(button.x() >= 3);
            assertTrue(button.x() + button.width() <= available + 3);
            assertTrue(button.width() >= 58);
            assertEquals(7, button.tab().unread());
        }
    }

    @Test void idleFramesDoNotRebuildOrRemeasureButChangesInvalidate() {
        var cache = new ConversationLayoutCache();
        var measurements = new AtomicInteger();
        ToIntFunction<String> width = name -> { measurements.incrementAndGet(); return name.length() * 6; };
        var tabs = List.of(new PrivateChatManager.ConversationTab("Alex", 1, true),
                new PrivateChatManager.ConversationTab("LongPlayerName12", 0, false));
        Object session = new Object(), font = new Object();
        var first = cache.get(tabs, 3, 400, 0, session, font, width, (s, w) -> s);
        assertEquals(58, first.getFirst().width());
        assertEquals(341, first.getFirst().x());
        assertEquals(106, first.get(1).width());
        for (int i = 0; i < 10_000; i++) assertSame(first, cache.get(tabs, 3, 400, 0, session, font, width, (s, w) -> s));
        assertEquals(2, measurements.get());
        assertNotSame(first, cache.get(tabs, 3, 401, 0, session, font, width, (s, w) -> s));
        assertNotSame(first, cache.get(tabs, 3, 400, 1, session, font, width, (s, w) -> s));
        assertNotSame(first, cache.get(tabs, 16, 400, 0, session, font, width, (s, w) -> s));
        var unread = List.of(new PrivateChatManager.ConversationTab("Alex", 2, false));
        assertEquals(2, cache.get(unread, 3, 400, 0, session, font, width, (s, w) -> s).getFirst().tab().unread());
        var prior = cache.get(tabs, 3, 400, 0, session, font, width, (s, w) -> s);
        assertNotSame(prior, cache.get(tabs, 3, 400, 0, new Object(), font, width, (s, w) -> s));
        assertNotSame(prior, cache.get(tabs, 3, 400, 0, session, new Object(), width, (s, w) -> s));
        assertTrue(cache.get(tabs, 3, 55, 0, session, font, width, (s, w) -> s).isEmpty());
        assertTrue(cache.get(List.of(), 3, 400, 0, null, font, width, (s, w) -> s).isEmpty());
    }

    @Test void countsOnlyUnreadMessagesHiddenOutsideTheVisibleTabs() {
        var cache = new ConversationLayoutCache();
        ToIntFunction<String> width = name -> 10;
        Object session = new Object(), font = new Object();
        var noUnreadOverflow = List.of(
                new PrivateChatManager.ConversationTab("A", 0, false),
                new PrivateChatManager.ConversationTab("B", 0, false),
                new PrivateChatManager.ConversationTab("C", 0, false),
                new PrivateChatManager.ConversationTab("D", 0, false));
        var withOverflow = cache.get(noUnreadOverflow, 2, 180, 0,
                session, font, width, (s, w) -> s);
        assertEquals(3, withOverflow.size());
        assertEquals(0, cache.hiddenUnreadMessageCount());

        var leftUnread = List.of(
                new PrivateChatManager.ConversationTab("A", 0, false),
                new PrivateChatManager.ConversationTab("B", 0, false),
                new PrivateChatManager.ConversationTab("C", 0, false),
                new PrivateChatManager.ConversationTab("D", 4, false));
        var withUnreadBadge = cache.get(leftUnread, 2, 180, 0,
                session, font, width, (s, w) -> s);
        assertEquals(3, withUnreadBadge.size());
        assertEquals(4, cache.hiddenUnreadMessageCount());

        var bothSidesUnread = List.of(
                new PrivateChatManager.ConversationTab("A", 3, false),
                new PrivateChatManager.ConversationTab("B", 0, false),
                new PrivateChatManager.ConversationTab("C", 0, false),
                new PrivateChatManager.ConversationTab("D", 5, false));
        cache.get(bothSidesUnread, 2, 180, 1,
                session, font, width, (s, w) -> s);
        assertEquals(3, cache.hiddenUnreadMessageCount());
    }

    @Test void keepsThreeTabsWhenTheyFitAndEachOffsetIntroducesTheNextOne() {
        var cache = new ConversationLayoutCache();
        ToIntFunction<String> width = name -> 200;
        Object session = new Object(), font = new Object();
        var tabs = List.of(
                new PrivateChatManager.ConversationTab("A", 0, false),
                new PrivateChatManager.ConversationTab("B", 0, false),
                new PrivateChatManager.ConversationTab("C", 0, false),
                new PrivateChatManager.ConversationTab("D", 0, false));
        var firstPage = cache.get(tabs, 2, 180, 0,
                session, font, width, (s, w) -> s);
        var nextPage = cache.get(tabs, 2, 180, 1,
                session, font, width, (s, w) -> s);
        assertEquals(List.of("A", "B", "C"),
                firstPage.stream().map(button -> button.tab().name()).toList());
        assertEquals(List.of("B", "C", "D"),
                nextPage.stream().map(button -> button.tab().name()).toList());
    }
}
