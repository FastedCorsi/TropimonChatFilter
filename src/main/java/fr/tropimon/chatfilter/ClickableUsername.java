package fr.tropimon.chatfilter;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ClickableUsername {
    private static final String NAME = "([A-Za-z0-9_]{2,16})";
    private static final Pattern TOWN = Pattern.compile(
            "^\\[[^]\\r\\n]*?" + NAME + "\\s+[\\uAC00-\\uD7AF]\\s+[^]\\r\\n]+]\\s+.+$");
    private static final Pattern VANILLA = Pattern.compile("^<" + NAME + ">");
    private static final Pattern PLAIN = Pattern.compile("^" + NAME + "\\s*:");
    private static final Pattern TROPIMON_GLOBAL = Pattern.compile(
            "^[^A-Za-z0-9_\\s]{1,8}\\s+" + NAME + "(?:[\\uE000-\\uF8FF]+)?\\s*:");
    private static final Pattern TROPIMON_RANKED = Pattern.compile(
            "^[^A-Za-z0-9_\\s]{1,8}\\s+" + NAME + "[\\uE000-\\uF8FF]+\\s+");
    private static final Pattern GROUP = Pattern.compile("^\\[(?:Groupe|Group)]\\s+" + NAME + "\\s*:",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private ClickableUsername() {
    }

    public static Optional<Match> find(String message, String localPlayer) {
        return findParsed(message, PrivateMessageParser.parse(message, localPlayer));
    }

    static Optional<Match> findParsed(String message, Optional<PrivateMessageParser.Parsed> privateMessage) {
        if (privateMessage.isPresent()) {
            PrivateMessageParser.Parsed parsed = privateMessage.get();
            return Optional.of(new Match(
                    parsed.nameStart(), parsed.nameEnd(), parsed.counterpart(), true));
        }
        Optional<Match> match = groupOne(GROUP, message)
                .or(() -> groupOne(TOWN, message))
                .or(() -> groupOne(VANILLA, message))
                .or(() -> groupOne(PLAIN, message))
                .or(() -> groupOne(TROPIMON_GLOBAL, message))
                .or(() -> groupOne(TROPIMON_RANKED, message));
        return match.map(value -> new Match(value.start(), value.end(), value.name(), false));
    }

    private static Optional<Match> groupOne(Pattern pattern, String message) {
        Matcher matcher = pattern.matcher(message);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(new Match(
                matcher.start(1), matcher.end(1), matcher.group(1), false));
    }

    public record Match(int start, int end, String name, boolean privateReply) {
        public String suggestion() {
            return privateReply ? "/msg " + name + " " : "@" + name + " ";
        }
    }
}
