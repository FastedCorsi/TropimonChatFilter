package fr.tropimon.chatfilter;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Résout un rôle depuis les informations publiques déjà reçues dans la liste des joueurs. */
final class PlayerRoleResolver {
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern ADMIN = word("admin(?:istrateur|istrator)?");
    private static final Pattern RESPONSABLE = word("responsable");
    private static final Pattern MODERATOR = word("modo|moderateur|moderatrice|moderator");
    private static final Pattern GUIDE = word("guide");
    private static final Pattern STAFF = word("staff|equipe");
    private static final Pattern MASTER = word("master");
    private static final Pattern HYPER = word("hyper");
    private static final Pattern SUPER = word("super");
    private static final Pattern CHAMPION = word("champion(?:s)?");

    private PlayerRoleResolver() {
    }

    static Resolved resolve(String player) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (player == null || client.getNetworkHandler() == null) {
            return fallback(PlayerRole.UNRANKED);
        }
        for (PlayerListEntry entry : client.getNetworkHandler().getPlayerList()) {
            if (entry.getProfile().getName().equalsIgnoreCase(player)) {
                PlayerRole role = fromText(entry.getDisplayName(), player);
                Team team = entry.getScoreboardTeam();
                role = better(role, fromTeam(team, player));
                int color = colorFromText(entry.getDisplayName(), player, -1);
                if (color == -1 && team != null) {
                    color = colorFromText(team.getPrefix(), player, -1);
                }
                if (color == -1 && team != null) {
                    color = colorFromText(team.getSuffix(), player, -1);
                }
                return new Resolved(role, color == -1 ? role.color() : color);
            }
        }
        return fallback(PlayerRole.UNRANKED);
    }

    private static Resolved fallback(PlayerRole role) {
        return new Resolved(role, role.color());
    }

    static PlayerRole fromLabel(String label, String player) {
        String visible = label == null ? "" : label.stripLeading();
        if (!visible.isEmpty()) {
            int icon = visible.codePointAt(0);
            if (icon == 0xA302) return PlayerRole.ADMIN;
            if (icon == 0xA303) return PlayerRole.MODERATOR;
            if (icon == 0xA304) return PlayerRole.RESPONSABLE;
        }
        String text = normalize(label);
        if (player != null && !player.isBlank()) {
            text = text.replace(player.toLowerCase(Locale.ROOT), " ");
        }
        if (ADMIN.matcher(text).find()) return PlayerRole.ADMIN;
        if (RESPONSABLE.matcher(text).find()) return PlayerRole.RESPONSABLE;
        if (MODERATOR.matcher(text).find()) return PlayerRole.MODERATOR;
        if (GUIDE.matcher(text).find()) return PlayerRole.GUIDE;
        if (STAFF.matcher(text).find()) return PlayerRole.STAFF;

        GlobalMessageCategory category = GlobalMessageClassifier.classify(
                visible);
        if (category == GlobalMessageCategory.STAFF) return PlayerRole.STAFF;
        if (category == GlobalMessageCategory.MASTER || MASTER.matcher(text).find()) {
            return PlayerRole.MASTER;
        }
        if (category == GlobalMessageCategory.HYPER || HYPER.matcher(text).find()) {
            return PlayerRole.HYPER;
        }
        if (category == GlobalMessageCategory.SUPER || SUPER.matcher(text).find()) {
            return PlayerRole.SUPER;
        }
        if (category == GlobalMessageCategory.CHAMPION || CHAMPION.matcher(text).find()) {
            return PlayerRole.CHAMPION;
        }
        return PlayerRole.UNRANKED;
    }

    private static PlayerRole fromText(Text text, String player) {
        return text == null ? PlayerRole.UNRANKED : fromLabel(text.getString(), player);
    }

    private static PlayerRole fromTeam(Team team, String player) {
        if (team == null) {
            return PlayerRole.UNRANKED;
        }
        PlayerRole role = fromText(team.getPrefix(), player);
        role = better(role, fromText(team.getSuffix(), player));
        return better(role, fromLabel(team.getName(), player));
    }

    private static PlayerRole better(PlayerRole first, PlayerRole second) {
        return second.priority() > first.priority() ? second : first;
    }

    static int colorFromText(Text text, String player, int fallback) {
        if (text == null) {
            return fallback;
        }
        int[] first = {-1};
        int[] named = {-1};
        String expected = player == null ? "" : player.toLowerCase(Locale.ROOT);
        text.visit((style, segment) -> {
            if (!segment.isBlank() && style.getColor() != null) {
                int color = 0xFF000000 | style.getColor().getRgb();
                if (first[0] == -1) {
                    first[0] = color;
                }
                if (!expected.isEmpty()
                        && segment.toLowerCase(Locale.ROOT).contains(expected)) {
                    named[0] = color;
                }
            }
            return Optional.empty();
        }, Style.EMPTY);
        return named[0] != -1 ? named[0] : first[0] != -1 ? first[0] : fallback;
    }

    private static Pattern word(String expression) {
        return Pattern.compile("(?:^|[^a-z])(?:" + expression + ")(?:$|[^a-z])");
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return DIACRITICS.matcher(Normalizer.normalize(value, Normalizer.Form.NFD))
                .replaceAll("").toLowerCase(Locale.ROOT);
    }

    record Resolved(PlayerRole role, int color) { }
}
