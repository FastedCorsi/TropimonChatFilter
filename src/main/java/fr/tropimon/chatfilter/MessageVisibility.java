package fr.tropimon.chatfilter;

import java.util.function.Predicate;

/** Recalculé à chaque consultation : ne mémorise aucun onglet, filtre ou groupe actif. */
public final class MessageVisibility {
    private MessageVisibility() { }

    public static boolean show(MessageAnalysis message, ChatChannel selected, String privateName,
                               boolean groupMessage, boolean groupActive,
                               boolean showAllChatsInAll,
                               Predicate<GlobalMessageCategory> visible) {
        if (message.channelConfirmation()) return false;
        boolean privateMessage = message.privateMessage().isPresent();
        return switch (selected) {
            case ALL -> {
                if (showAllChatsInAll) {
                    yield groupMessage ? groupActive
                            : message.staff() || privateMessage || message.town()
                            || message.category() == GlobalMessageCategory.STAFF
                            || visible.test(message.category());
                }
                yield !groupMessage && !message.staff() && !privateMessage && !message.town()
                        && (message.category() == GlobalMessageCategory.STAFF
                        || visible.test(message.category()));
            }
            case PRIVATE -> !groupMessage && privateName != null && message.privateMessage()
                    .map(parsed -> parsed.counterpart().equalsIgnoreCase(privateName)).orElse(false);
            case STAFF -> message.staff();
            case GROUP -> groupMessage && groupActive;
            case TOWN -> !groupMessage && !message.staff() && !privateMessage && message.town();
        };
    }

    /** Réponse serveur plausible, sans englober un canal de discussion. */
    public static boolean standaloneSystemMessage(MessageAnalysis message) {
        return !message.channelConfirmation()
                && message.category() == GlobalMessageCategory.SYSTEM
                && message.privateMessage().isEmpty()
                && !message.staff()
                && !message.town()
                && message.groupPayload().isEmpty();
    }
}
