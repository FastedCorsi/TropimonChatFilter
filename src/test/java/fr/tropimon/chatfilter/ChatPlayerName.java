package fr.tropimon.chatfilter;

import java.util.Optional;
import java.util.LinkedHashMap;
import java.util.Map;

/** Frozen pre-optimization oracle. Not packaged in the mod. */
public final class ChatPlayerName {
    private static final int CACHE_LIMIT = 256;
    private static final Map<String, Optional<String>> CACHE = new LinkedHashMap<>(64, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Optional<String>> eldest) {
            return size() > CACHE_LIMIT;
        }
    };

    private ChatPlayerName() {
    }

    public static Optional<String> speaker(String rawMessage, String localPlayer) {
        String message = rawMessage == null ? "" : rawMessage;
        String cacheKey = (localPlayer == null ? "" : localPlayer) + '\n' + message;
        Optional<String> cached = CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        Optional<String> result = findSpeaker(message, localPlayer);
        CACHE.put(cacheKey, result);
        return result;
    }

    private static Optional<String> findSpeaker(String message, String localPlayer) {
        Optional<String> group = GroupMessageProtocol.displaySender(message);
        if (group.isPresent()) {
            return group;
        }
        Optional<PrivateMessageParser.Parsed> privateMessage =
                PrivateMessageParser.parse(message, localPlayer);
        if (privateMessage.isPresent()) {
            return privateMessage.get().incoming()
                    ? Optional.of(privateMessage.get().counterpart())
                    : Optional.ofNullable(localPlayer);
        }
        Optional<String> town = ChatMessageClassifier.extractTownSender(message);
        if (town.isPresent()) {
            String sender = town.get();
            return ChatMessageClassifier.isSelfDestination(sender)
                    ? Optional.ofNullable(localPlayer) : town;
        }
        return ClickableUsername.find(message, localPlayer).map(ClickableUsername.Match::name);
    }
}
