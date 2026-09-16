package fr.tropimon.chatfilter;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record PrivateOutgoingMessage(String target, String body) {
    private static final Pattern COMMAND = Pattern.compile(
            "^/msg\\s+([A-Za-z0-9_]{2,16})\\s+(.+)$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern REPLY_COMMAND = Pattern.compile(
            "^/r\\s+(.+)$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    public static Optional<PrivateOutgoingMessage> parse(String command, String replyTarget) {
        String normalized = command == null ? "" : command.strip();
        Matcher matcher = COMMAND.matcher(normalized);
        if (!matcher.matches() || matcher.group(2).isBlank()) {
            Matcher reply = REPLY_COMMAND.matcher(normalized);
            if (replyTarget == null || replyTarget.isBlank()
                    || !reply.matches() || reply.group(1).isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new PrivateOutgoingMessage(replyTarget, reply.group(1).strip()));
        }
        return Optional.of(new PrivateOutgoingMessage(matcher.group(1), matcher.group(2).strip()));
    }
}
