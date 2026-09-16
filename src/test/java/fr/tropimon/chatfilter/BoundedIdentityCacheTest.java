package fr.tropimon.chatfilter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundedIdentityCacheTest {
    @Test
    void explicitEntriesStayIdentityBasedAndBounded() {
        BoundedIdentityCache<String, Boolean> cache = new BoundedIdentityCache<>(2);
        String first = new String("same");
        String equalButDistinct = new String("same");
        String second = new String("second");
        String third = new String("third");

        cache.put(first, Boolean.TRUE);
        assertTrue(cache.containsKey(first));
        assertFalse(cache.containsKey(equalButDistinct));

        cache.put(second, Boolean.TRUE);
        cache.put(third, Boolean.TRUE);
        assertFalse(cache.containsKey(first));
        assertTrue(cache.containsKey(second));
        assertTrue(cache.containsKey(third));
    }
}
