package fr.tropimon.chatfilter;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class StableMessageCacheTest {
    @Test void reusesIdentityWithoutRereadingTextAndIsBounded() {
        var reads = new AtomicInteger();
        var cache = new StableMessageCache<String>(2, text -> { reads.incrementAndGet(); return text; });
        String first = new String("[Alex -> Taylor] salut");
        cache.context(new Object(), new Object(), "Taylor");
        var facts = cache.get(first);
        for (int i = 0; i < 10_000; i++) assertSame(facts, cache.get(first));
        assertEquals(1, reads.get());
        assertNotSame(facts, cache.get(new String(first)));
        cache.get("third");
        assertEquals(2, cache.size());
        assertNotSame(facts, cache.get(first));
    }

    @Test void invalidatesOnSessionAccountLanguageAndDisconnect() {
        var cache = new StableMessageCache<String>(10, s -> s);
        Object session = new Object(), language = new Object();
        cache.context(session, language, "Taylor");
        String raw = "[Alex -> Taylor] salut";
        var first = cache.get(raw);
        assertFalse(cache.context(session, language, "Taylor"));
        assertSame(first, cache.get(raw));
        assertTrue(cache.context(session, language, "Alex"));
        assertFalse(cache.get(raw).privateMessage().orElseThrow().incoming());
        assertTrue(cache.context(session, new Object(), "Alex"));
        assertEquals(0, cache.size());
        cache.get(raw);
        assertTrue(cache.context(new Object(), language, "Alex"));
        assertEquals(0, cache.size());
        cache.get(raw);
        assertTrue(cache.context(null, language, null));
        assertEquals(0, cache.size());
    }
}
