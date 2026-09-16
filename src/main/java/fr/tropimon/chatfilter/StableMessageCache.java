package fr.tropimon.chatfilter;

import java.util.Objects;
import java.util.function.Function;

/** Bounded facts only; deliberately has no knowledge of filters, read state or selected tabs. */
final class StableMessageCache<K> {
    private final BoundedIdentityCache<K, MessageAnalysis> messages;
    private final Function<K, MessageAnalysis> analyze;
    private Object session;
    private Object language;
    private String player;

    StableMessageCache(int limit, Function<K, String> text) {
        messages = new BoundedIdentityCache<>(limit);
        analyze = key -> MessageAnalysis.analyze(text.apply(key), player);
    }

    boolean context(Object currentSession, Object currentLanguage, String localPlayer) {
        if (session == currentSession && language == currentLanguage && Objects.equals(player, localPlayer)) return false;
        session = currentSession;
        language = currentLanguage;
        player = localPlayer;
        messages.clear();
        return true;
    }
    MessageAnalysis get(K key) { return messages.get(key, analyze); }
    int size() { return messages.size(); }
}
