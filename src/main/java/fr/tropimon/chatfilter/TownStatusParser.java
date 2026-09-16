package fr.tropimon.chatfilter;

import java.text.Normalizer;
import java.util.Locale;

public final class TownStatusParser {
    private TownStatusParser() {
    }

    public static boolean isNoTownMessage(String rawMessage) {
        String message = Normalizer.normalize(
                        rawMessage == null ? "" : rawMessage, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
        return message.contains("you are not in a town")
                || message.contains("you aren't in a town")
                || message.contains("you do not have a town")
                || message.contains("you don't have a town")
                || message.contains("not part of a town")
                || message.contains("vous n'avez pas de ville")
                || message.contains("tu n'as pas de ville")
                || message.contains("ne faites partie d'aucune ville")
                || message.contains("ne fais partie d'aucune ville");
    }
}
