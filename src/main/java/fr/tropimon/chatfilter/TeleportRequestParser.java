package fr.tropimon.chatfilter;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reconnaît uniquement les formulations de demande de téléportation du serveur. */
final class TeleportRequestParser {
    private static final String PREFIX = "^(?:\\s*\\[[0-9]{2}:[0-9]{2}]\\s*)?[^A-Za-z0-9_]*";
    private static final String PLAYER = "([A-Za-z0-9_]{2,16})";
    private static final Pattern TO_REQUESTER = Pattern.compile(
            PREFIX + PLAYER + "\\s+demande\\s+a\\s+ce\\s+que\\s+tu\\s+te\\s+teleport(?:e|es)\\s+a\\s+lui\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TO_LOCAL_PLAYER = Pattern.compile(
            PREFIX + PLAYER + "\\s+demande\\s+a\\s+se\\s+teleporter\\s+(?:a|vers)\\s+toi\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ENGLISH_TO_REQUESTER = Pattern.compile(
            PREFIX + PLAYER + "\\s+(?:has\\s+)?requested\\s+(?:that\\s+)?you\\s+teleport\\s+to\\s+(?:them|him|her)\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ENGLISH_TO_LOCAL_PLAYER = Pattern.compile(
            PREFIX + PLAYER + "\\s+(?:has\\s+)?requested\\s+to\\s+teleport\\s+to\\s+you\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");

    private TeleportRequestParser() {
    }

    static Observation analyze(String raw) {
        String normalized = normalize(raw);
        String lower = normalized.toLowerCase(Locale.ROOT).trim();
        Optional<Request> request = match(normalized, TO_REQUESTER, Direction.TO_REQUESTER);
        if (request.isPresent()) {
            return new Observation(request, false, false);
        }
        request = match(normalized, TO_LOCAL_PLAYER, Direction.TO_LOCAL_PLAYER)
                .or(() -> match(normalized, ENGLISH_TO_REQUESTER, Direction.TO_REQUESTER))
                .or(() -> match(normalized, ENGLISH_TO_LOCAL_PLAYER, Direction.TO_LOCAL_PLAYER));
        boolean options = lower.startsWith("options:")
                && (lower.contains("accepter") || lower.contains("accept"))
                && (lower.contains("decliner") || lower.contains("refuser")
                || lower.contains("decline") || lower.contains("deny"));
        String resolution = lower.replaceFirst("^[^a-z]*", "");
        boolean resolved = resolution.startsWith("teleportation acceptee")
                || resolution.startsWith("teleportation refusee")
                || resolution.startsWith("teleportation declinee")
                || resolution.startsWith("teleport request accepted")
                || resolution.startsWith("teleport request denied");
        return new Observation(request, options, resolved);
    }

    static Action action(String label, String command) {
        String value = (normalize(label) + " " + normalize(command)).toLowerCase(Locale.ROOT);
        if (value.contains("accepter") || value.contains("accept")
                || value.contains("tpaccept") || value.contains("tpyes")) {
            return Action.ACCEPT;
        }
        if (value.contains("decliner") || value.contains("refuser")
                || value.contains("decline") || value.contains("deny")
                || value.contains("tpdeny") || value.contains("tpno")) {
            return Action.DECLINE;
        }
        return Action.UNKNOWN;
    }

    private static Optional<Request> match(String value, Pattern pattern, Direction direction) {
        Matcher matcher = pattern.matcher(value);
        return matcher.matches()
                ? Optional.of(new Request(matcher.group(1), direction)) : Optional.empty();
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return DIACRITICS.matcher(Normalizer.normalize(
                value.replace('\u00A0', ' '), Normalizer.Form.NFD)).replaceAll("");
    }

    enum Direction {
        TO_REQUESTER,
        TO_LOCAL_PLAYER
    }

    enum Action {
        ACCEPT,
        DECLINE,
        UNKNOWN
    }

    record Request(String player, Direction direction) {
    }

    record Observation(Optional<Request> request, boolean options, boolean resolved) {
    }
}
