package fr.tropimon.chatfilter;

import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Petit protocole texte transporté par les MP Tropimon pour les groupes temporaires. */
public final class GroupMessageProtocol {
    private static final Pattern PAYLOAD = Pattern.compile(
            "\\[\\[TCFG:([IML]):([a-f0-9]{8})(?::([^]\\r\\n]*))?]](?:\\s*(.*))?",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern PLAYER = Pattern.compile("^[A-Za-z0-9_]{2,16}$");
    private static final Pattern DISPLAY = Pattern.compile(
            "^\\[(?:Groupe|Group)]\\s+([A-Za-z0-9_]{2,16})"
                    + "(?:\\s*:|\\s+a\\s+|\\s+invited\\s+|\\s+left\\s+)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    public static Optional<String> displaySender(String rawMessage) {
        Matcher matcher = DISPLAY.matcher(rawMessage == null ? "" : rawMessage.strip());
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private GroupMessageProtocol() {
    }

    public static String invitation(String groupId, List<String> members) {
        return "[[TCFG:I:" + validateId(groupId) + ":" + String.join(",", cleanMembers(members)) + "]]";
    }

    public static String message(String groupId, String body) {
        return "[[TCFG:M:" + validateId(groupId) + "]] " + (body == null ? "" : body.strip());
    }

    public static String message(String groupId, String token, String body) {
        String value = validateMessageToken(token);
        return "[[TCFG:M:" + validateId(groupId) + ":" + value + "]] "
                + (body == null ? "" : body.strip());
    }

    /** Réponse résolue par le serveur pour une balise {@code <party:x>}. */
    public static String resolvedMessage(String groupId, String token, String body) {
        // Les anciens clients consomment déjà les départs inconnus sans les afficher.
        // Ce type évite donc un doublon pendant la mise à jour du groupe.
        return "[[TCFG:L:" + validateId(groupId) + ":r"
                + validateMessageToken(token) + "]] "
                + (body == null ? "" : body.strip());
    }

    public static String leave(String groupId, String player) {
        return "[[TCFG:L:" + validateId(groupId) + ":" + cleanPlayer(player) + "]]";
    }

    public static Optional<Payload> parse(String rawMessage) {
        Matcher matcher = PAYLOAD.matcher(rawMessage == null ? "" : rawMessage);
        if (!matcher.find()) {
            return Optional.empty();
        }
        Type type = Type.valueOf(matcher.group(1).toUpperCase(Locale.ROOT));
        String data = matcher.group(3) == null ? "" : matcher.group(3).strip();
        String body = matcher.group(4) == null ? "" : matcher.group(4).strip();
        List<String> members;
        try {
            members = type == Type.I ? cleanMembers(List.of(data.split(","))) : List.of();
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
        if (type == Type.I && members.size() < 2 || type == Type.M && body.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new Payload(type, matcher.group(2).toLowerCase(Locale.ROOT),
                data, body, members, matcher.start(4)));
    }

    /** Extrait le corps sans perdre les couleurs, survols ou clics ajoutés par le serveur. */
    static Text styledBody(Text message, Payload payload) {
        if (message == null || payload == null || payload.bodyStart() < 0) {
            return Text.literal(payload == null ? "" : payload.body());
        }
        var result = Text.empty();
        int[] offset = {0};
        message.visit((style, segment) -> {
            int start = offset[0];
            int end = start + segment.length();
            if (end > payload.bodyStart()) {
                int from = Math.max(0, payload.bodyStart() - start);
                result.append(Text.literal(segment.substring(from)).setStyle(style));
            }
            offset[0] = end;
            return Optional.empty();
        }, Style.EMPTY);
        return result.getString().equals(payload.body())
                ? result : Text.literal(payload.body());
    }

    private static List<String> cleanMembers(List<String> rawMembers) {
        Set<String> seen = new LinkedHashSet<>();
        List<String> result = new ArrayList<>();
        for (String raw : rawMembers) {
            String player = cleanPlayer(raw);
            String key = player.toLowerCase(Locale.ROOT);
            if (seen.add(key)) {
                result.add(player);
            }
        }
        return List.copyOf(result);
    }

    private static String cleanPlayer(String raw) {
        String value = raw == null ? "" : raw.strip();
        if (!PLAYER.matcher(value).matches()) {
            throw new IllegalArgumentException("Pseudo Minecraft invalide");
        }
        return value;
    }

    private static String validateId(String raw) {
        String value = raw == null ? "" : raw.toLowerCase(Locale.ROOT);
        if (!value.matches("[a-f0-9]{8}")) {
            throw new IllegalArgumentException("Identifiant de groupe invalide");
        }
        return value;
    }

    private static String validateMessageToken(String raw) {
        String value = raw == null ? "" : raw.toLowerCase(Locale.ROOT);
        if (!value.matches("[a-f0-9]{6}")) {
            throw new IllegalArgumentException("Jeton de message invalide");
        }
        return value;
    }

    public enum Type {
        I, M, L
    }

    public record Payload(Type type, String groupId, String data, String body,
                          List<String> members, int bodyStart) {
    }
}
