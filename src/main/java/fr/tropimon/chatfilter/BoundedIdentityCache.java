package fr.tropimon.chatfilter;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.function.Function;

/** FIFO bornée : aucune égalité structurelle de Text, aucune allocation sur un hit. */
final class BoundedIdentityCache<K, V> {
    private final int capacity;
    private final IdentityHashMap<K, V> entries = new IdentityHashMap<>();
    private final ArrayDeque<K> order = new ArrayDeque<>();

    BoundedIdentityCache(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity");
        this.capacity = capacity;
    }

    V get(K key, Function<K, V> factory) {
        V value = entries.get(key);
        if (value != null) return value;
        value = factory.apply(key);
        put(key, value);
        return value;
    }

    void put(K key, V value) {
        if (entries.containsKey(key)) {
            entries.put(key, value);
            return;
        }
        if (entries.size() == capacity) entries.remove(order.removeFirst());
        entries.put(key, value);
        order.addLast(key);
    }

    boolean containsKey(K key) {
        return entries.containsKey(key);
    }

    void clear() {
        entries.clear();
        order.clear();
    }

    int size() { return entries.size(); }
}
