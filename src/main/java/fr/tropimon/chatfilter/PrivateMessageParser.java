package fr.tropimon.chatfilter;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PrivateMessageParser {
    private static final int CACHE_LIMIT = 512;
    private static final String NAME = "([A-Za-z0-9_]{2,16})";
    private static final String DECORATION = "(?:[^A-Za-z0-9_\\r\\n]{0,12}\\s*)";
    private static final String ARROW = "(->|<-|→|←|➜|»|<−)";
    private static final Pattern ARROWS = Pattern.compile(
            "^\\s*" + DECORATION + NAME + "\\s*" + ARROW + "\\s*" + NAME,
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern TROPIMON_BRACKET = Pattern.compile(
            "^\\[([^]\\r\\n]{2,48})\\s+석\\s+([^]\\r\\n]{2,48})]\\s+.+$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern TRAILING_NAME = Pattern.compile("([A-Za-z0-9_]{2,16})$");
    private static final Pattern TAG = Pattern.compile(
            "^\\s*" + DECORATION + "\\[(?:mp|msg|pm|priv(?:e)?|private|whisper)]\\s*(?:de\\s+)?" + NAME,
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern FROM = Pattern.compile(
            "^\\s*" + DECORATION + "(?:de|from)\\s+" + NAME + "\\s*(?::|»)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern TO = Pattern.compile(
            "^\\s*" + DECORATION + "(?:a|to)\\s+" + NAME + "\\s*(?::|»)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern WHISPER = Pattern.compile(
            "^\\s*" + DECORATION + NAME + "\\s+(?:vous chuchote|whispers to you)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern MESSAGE_FROM = Pattern.compile(
            "^\\s*" + DECORATION
                    + "(?:(?:message\\s+(?:prive\\s+)?|(?:mp|msg|pm)\\s+)(?:de|from))"
                    + "\\s*[:»-]?\\s*" + NAME + "\\s*(?::|»|-)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern MESSAGE_TO = Pattern.compile(
            "^\\s*" + DECORATION
                    + "(?:(?:message\\s+(?:prive\\s+)?|(?:mp|msg|pm)\\s+)(?:a|to))"
                    + "\\s*[:»-]?\\s*" + NAME + "\\s*(?::|»|-)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern WHISPER_TO = Pattern.compile(
            "^\\s*" + DECORATION + "(?:vous chuchotez a|you whisper to)\\s+" + NAME,
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Map<String, Optional<Parsed>> CACHE =
            new LinkedHashMap<>(128, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(
                        Map.Entry<String, Optional<Parsed>> eldest) {
                    return size() > CACHE_LIMIT;
                }
            };

    private PrivateMessageParser() {
    }

    static void clearCache() { CACHE.clear(); }

    public static Optional<Parsed> parse(String rawMessage, String localPlayer) {
        String message = rawMessage == null ? "" : rawMessage;
        String cacheKey = (localPlayer == null ? "" : localPlayer.toLowerCase(Locale.ROOT))
                + '\n' + message;
        Optional<Parsed> cached = CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        Optional<Parsed> parsed = parseUncached(message, localPlayer);
        CACHE.put(cacheKey, parsed);
        return parsed;
    }

    private static Optional<Parsed> parseUncached(String message, String localPlayer) {
        String normalized = stripAccents(message);

        Matcher arrows = ARROWS.matcher(message);
        if (arrows.find()) {
            String first = arrows.group(1);
            String arrow = arrows.group(2);
            String second = arrows.group(3);
            boolean firstIsSelf = isSelf(first, localPlayer);
            boolean secondIsSelf = isSelf(second, localPlayer);
            if (!firstIsSelf && !secondIsSelf) {
                return Optional.empty();
            }
            String counterpart = firstIsSelf && !secondIsSelf ? second : first;
            int group = counterpart.equals(first) ? 1 : 3;
            boolean pointsLeft = arrow.equals("<-") || arrow.equals("←") || arrow.equals("<−");
            String sender = pointsLeft ? second : first;
            boolean incoming = !isSelf(sender, localPlayer);
            return Optional.of(new Parsed(counterpart, arrows.start(group), arrows.end(group), incoming));
        }

        Matcher bracket = TROPIMON_BRACKET.matcher(message);
        if (bracket.matches()) {
            Optional<NamePart> sender = trailingName(bracket.group(1), bracket.start(1));
            Optional<NamePart> recipient = trailingName(bracket.group(2), bracket.start(2));
            boolean senderIsSelf = isSelfField(bracket.group(1), sender, localPlayer);
            boolean recipientIsSelf = isSelfField(bracket.group(2), recipient, localPlayer);

            if (!senderIsSelf && recipientIsSelf && sender.isPresent()) {
                NamePart counterpart = sender.get();
                return Optional.of(new Parsed(
                        counterpart.name(), counterpart.start(), counterpart.end(), true));
            }
            if (senderIsSelf && !recipientIsSelf
                    && recipient.isPresent() && startsWithRankGlyph(bracket.group(2))) {
                NamePart counterpart = recipient.get();
                return Optional.of(new Parsed(
                        counterpart.name(), counterpart.start(), counterpart.end(), false));
            }
        }

        Optional<Parsed> tagged = capture(TAG, normalized, message, true);
        if (tagged.isPresent()) {
            return tagged;
        }
        Optional<Parsed> from = capture(FROM, normalized, message, true);
        if (from.isPresent()) {
            return from;
        }
        Optional<Parsed> to = capture(TO, normalized, message, false);
        if (to.isPresent()) {
            return to;
        }
        Optional<Parsed> whisper = capture(WHISPER, normalized, message, true);
        if (whisper.isPresent()) {
            return whisper;
        }
        Optional<Parsed> messageFrom = capture(MESSAGE_FROM, normalized, message, true);
        if (messageFrom.isPresent()) {
            return messageFrom;
        }
        Optional<Parsed> messageTo = capture(MESSAGE_TO, normalized, message, false);
        if (messageTo.isPresent()) {
            return messageTo;
        }
        return capture(WHISPER_TO, normalized, message, false);
    }

    private static Optional<Parsed> capture(
            Pattern pattern, String normalized, String original, boolean incoming) {
        Matcher matcher = pattern.matcher(normalized);
        if (!matcher.find()) {
            return Optional.empty();
        }
        // La normalisation ne modifie pas la longueur des pseudos Minecraft (ASCII).
        int start = matcher.start(1);
        int end = matcher.end(1);
        return Optional.of(new Parsed(original.substring(start, end), start, end, incoming));
    }

    private static boolean isSelf(String name, String localPlayer) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.equals("moi") || lower.equals("me") || lower.equals("vous") || lower.equals("you")
                || localPlayer != null && name.equalsIgnoreCase(localPlayer);
    }

    private static boolean isSelfField(
            String field, Optional<NamePart> trailingName, String localPlayer) {
        String stripped = field.strip();
        if (isSelf(stripped, localPlayer)) {
            return true;
        }
        return trailingName.map(part -> isSelf(part.name(), localPlayer)).orElse(false);
    }

    private static Optional<NamePart> trailingName(String field, int absoluteFieldStart) {
        Matcher matcher = TRAILING_NAME.matcher(field.stripTrailing());
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(new NamePart(
                matcher.group(1),
                absoluteFieldStart + matcher.start(1),
                absoluteFieldStart + matcher.end(1)));
    }

    private static boolean startsWithRankGlyph(String field) {
        String stripped = field.stripLeading();
        return !stripped.isEmpty()
                && GlobalMessageClassifier.isPlayerRankGlyph(stripped.codePointAt(0));
    }

    private static String stripAccents(String value) {
        return DIACRITICS.matcher(Normalizer.normalize(value, Normalizer.Form.NFD))
                .replaceAll("");
    }

    public record Parsed(String counterpart, int nameStart, int nameEnd, boolean incoming) {
    }

    private record NamePart(String name, int start, int end) {
    }
}
