package fr.tropimon.chatfilter;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reconnaît les formats de chat envoyés par Tropimon sans dépendre de ses classes internes. */
public final class ChatMessageClassifier {
    private static final Pattern TROPIMON_TOWN = Pattern.compile(
            "^\\[[^]\\r\\n]*?([A-Za-z0-9_]{2,16})\\s+석\\s+([^]\\r\\n]{2,32})]\\s+.+$");
    private static final Pattern NAMED_TOWN = Pattern.compile(
            "^\\s*\\[(?:ville|town|v)\\]", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern NAMED_STAFF = Pattern.compile(
            "^\\s*\\[(?:staff|equipe|équipe|moderation|modération)\\]",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern TRAILING_PLAYER_NAME = Pattern.compile(
            "([A-Za-z0-9_]{2,16})$");
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern CHANNEL_CHANGE_CONFIRMATION = Pattern.compile(
            "^\\s*(?:[^\\x00-\\x7F]\\s*)*(?:"
                    + "your\\s+(?:chat|channel)\\s+(?:has\\s+been\\s+)?"
                    + "(?:successfully\\s+)?(?:added|changed|switched|set)(?:\\s+to)?"
                    + "|you\\s+(?:have\\s+)?(?:changed|switched|set)\\s+"
                    + "(?:your\\s+)?(?:chat|channel)(?:\\s+to)?"
                    + "|you\\s+are\\s+now\\s+(?:chatting|talking)\\s+(?:in|on)"
                    + "|votre\\s+(?:chat|canal)\\s+(?:a\\s+ete\\s+)?"
                    + "(?:ajoute|change|bascule|defini)(?:\\s+avec\\s+succes)?"
                    + "(?:\\s+(?:a|sur|vers))?"
                    + "|vous\\s+(?:avez\\s+)?(?:change|passe|bascule|defini)\\s+"
                    + "(?:de\\s+|votre\\s+)?(?:chat|canal)(?:\\s+(?:a|sur|vers))?"
                    + "|vous\\s+etes\\s+(?:desormais\\s+)?(?:dans|sur)\\s+"
                    + "(?:le\\s+)?(?:chat|canal)"
                    + "|ton\\s+(?:chat|canal)\\s+a\\s+(?:bien\\s+)?ete\\s+"
                    + "(?:mis|place|bascule|change)\\s+(?:sur|a|vers)"
                    + ")\\s*:?\\s*(?:global|town|staff|ville|equipe)\\s*[.!]?\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private ChatMessageClassifier() {
    }

    static Details details(String raw) {
        String message = raw == null ? "" : raw.strip();
        Matcher matcher = TROPIMON_TOWN.matcher(message);
        boolean matches = matcher.matches();
        Optional<String> sender = matches ? Optional.of(matcher.group(1)) : Optional.empty();
        Optional<String> destination = matches ? Optional.of(matcher.group(2).strip()) : Optional.empty();
        String searchable = stripAccents(message).toLowerCase(Locale.ROOT);
        boolean staff = NAMED_STAFF.matcher(searchable).find()
                || destination.map(ChatMessageClassifier::isStaffDestination).orElse(false);
        Optional<String> name = destination.filter(value -> !isSelfDestination(value))
                .filter(ChatMessageClassifier::isPlausibleTownName);
        return new Details(staff, !staff && (name.isPresent() || NAMED_TOWN.matcher(searchable).find()),
                name, sender);
    }

    record Details(boolean staff, boolean town, Optional<String> name, Optional<String> sender) { }

    public static ChatChannel classify(String rawMessage) {
        String message = rawMessage == null ? "" : rawMessage.strip();

        if (PrivateMessageParser.parse(message, null).isPresent()) {
            return ChatChannel.PRIVATE;
        }
        if (isStaff(message)) {
            return ChatChannel.STAFF;
        }
        if (isTown(message)) {
            return ChatChannel.TOWN;
        }
        return ChatChannel.ALL;
    }

    public static boolean isTown(String rawMessage) {
        String message = rawMessage == null ? "" : rawMessage.strip();
        String searchable = stripAccents(message).toLowerCase(Locale.ROOT);
        return !isStaff(message)
                && (extractTownName(message).isPresent() || NAMED_TOWN.matcher(searchable).find());
    }

    public static boolean isStaff(String rawMessage) {
        String message = rawMessage == null ? "" : rawMessage.strip();
        String searchable = stripAccents(message).toLowerCase(Locale.ROOT);
        return NAMED_STAFF.matcher(searchable).find()
                || extractTownDestination(message).map(ChatMessageClassifier::isStaffDestination)
                        .orElse(false);
    }

    public static boolean isChannelChangeConfirmation(String rawMessage) {
        String message = rawMessage == null ? "" : rawMessage.strip();
        String searchable = normalizeServerText(message);
        return CHANNEL_CHANGE_CONFIRMATION.matcher(searchable).matches();
    }

    public static Optional<String> extractTownName(String rawMessage) {
        return extractTownDestination(rawMessage)
                .filter(candidate -> !isSelfDestination(candidate))
                .filter(ChatMessageClassifier::isPlausibleTownName);
    }

    public static Optional<String> extractTownSender(String rawMessage) {
        String message = rawMessage == null ? "" : rawMessage.strip();
        Matcher matcher = TROPIMON_TOWN.matcher(message);
        return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    public static Optional<String> extractTownDestination(String rawMessage) {
        String message = rawMessage == null ? "" : rawMessage.strip();
        Matcher matcher = TROPIMON_TOWN.matcher(message);
        return matcher.matches() ? Optional.of(matcher.group(2).strip()) : Optional.empty();
    }

    public static Optional<String> extractTrailingPlayerName(String value) {
        Matcher matcher = TRAILING_PLAYER_NAME.matcher(
                value == null ? "" : value.strip());
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    public static boolean isSelfDestination(String value) {
        String normalized = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
        return normalized.equals("moi") || normalized.equals("me")
                || normalized.equals("vous") || normalized.equals("you");
    }

    public static boolean isPlausibleTownName(String value) {
        String candidate = value == null ? "" : value.strip();
        return !candidate.isEmpty()
                && Character.isLetterOrDigit(candidate.codePointAt(0))
                && !isStaffDestination(candidate)
                && !GlobalMessageClassifier.isPlayerRankGlyph(candidate.codePointAt(0));
    }

    public static boolean isStaffDestination(String value) {
        String normalized = stripAccents(value == null ? "" : value)
                .strip().toLowerCase(Locale.ROOT);
        return normalized.equals("staff") || normalized.equals("equipe")
                || normalized.equals("moderation") || normalized.equals("modo");
    }

    private static String stripAccents(String value) {
        return DIACRITICS.matcher(Normalizer.normalize(value, Normalizer.Form.NFD))
                .replaceAll("");
    }

    private static String normalizeServerText(String value) {
        String normalized = stripAccents(value).toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder(normalized.length());
        normalized.codePoints().forEach(codePoint -> {
            int type = Character.getType(codePoint);
            if (type == Character.SPACE_SEPARATOR || type == Character.LINE_SEPARATOR
                    || type == Character.PARAGRAPH_SEPARATOR) {
                result.append(' ');
            } else if (type != Character.FORMAT) {
                result.appendCodePoint(codePoint);
            }
        });
        return result.toString().strip();
    }
}
