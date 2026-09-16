package fr.tropimon.chatfilter;

import java.text.Normalizer;
import java.util.Set;
import java.util.regex.Pattern;

public final class GlobalMessageClassifier {
    private static final Set<Integer> STAFF = Set.of(
            0xA302, 0xA303, 0xA304, 0xF804, 0xF81E, 0xF82B, 0xC170);
    private static final Pattern STAFF_TEXT = Pattern.compile(
            "^\\s*\\[(?:staff|admin|modo|moderateur|moderatrice|guide|equipe|responsable)]",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern STAFF_ROLE_TEXT = Pattern.compile(
            "(?:^|\\s)(?:staff|admin|modo|moderateur|moderatrice|guide|equipe|responsable)(?:\\s|$)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern CAPTURE_ANNOUNCEMENT = Pattern.compile(
            "^[\\uC60F\\uF828\\uF82A]\\s+[A-Za-z0-9_]{2,16}\\s+a\\s+captur(?:é|e)\\s+un\\s+.+"
                    + "\\s+[\\u901E\\uF825\\uF826]\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern BOOST_ANNOUNCEMENT = Pattern.compile(
            "^(?:\\uF80F\\s+.*\\bboosts?\\b.*"
                    + "|\\uA308\\s+no\\s+boosts?\\b.*"
                    + "|-\\s*(?:shiny\\s+x\\d+|ivs\\s+\\+\\d+|talent\\s+cach(?:é|e)\\s+\\d+%)"
                    + "\\s*\\(end\\s+in\\s+.+\\))\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Set<Integer> UNRANKED = Set.of((int) 'ꈎ', 0xF802);
    private static final Set<Integer> CHAMPIONS = Set.of(
            0xC129, 0xC12A, 0xC12B, 0xC12C, 0xC12D,
            0xC150, 0xC151, 0xC152, 0xC153, 0xC155, 0xC156,
            0xC157, 0xC158, 0xC159, 0xC15A, 0xC15B, 0xC15C, 0xC15D);
    private static final Set<Integer> SUPER = Set.of(
            (int) 'ꑣ', 0xF81F, 0xF833, 0xF836, 0xF837,
            0xF838, 0xF839, 0xF83A, 0xF83C, 0xF83D, 0xF83E);
    private static final Set<Integer> HYPER = Set.of(
            (int) 'ꑤ', 0xF821, 0xF822, 0xF823, 0xF824);
    private static final Set<Integer> MASTER = Set.of(
            (int) 'ꑥ', 0xF812, 0xF813, 0xF814, 0xF815, 0xF816,
            0xF817, 0xF818, 0xF819, 0xF81A, 0xF81B, 0xF81C, 0xF81D,
            0xF820, 0xF82C, 0xF82D, 0xF82E, 0xF82F);

    private GlobalMessageClassifier() {
    }

    /**
     * Indique si le glyphe est une icone de grade joueur connue de Tropimon.
     * Sert aussi a distinguer un destinataire de MP decore d'un nom de ville.
     */
    public static boolean isPlayerRankGlyph(int codePoint) {
        return STAFF.contains(codePoint)
                || UNRANKED.contains(codePoint)
                || CHAMPIONS.contains(codePoint)
                || SUPER.contains(codePoint)
                || HYPER.contains(codePoint)
                || MASTER.contains(codePoint);
    }

    public static boolean looksLikeStaffIdentity(String rawText) {
        String text = rawText == null ? "" : rawText.stripLeading();
        if (text.isEmpty()) {
            return false;
        }
        String searchable = stripAccents(text);
        return STAFF.contains(text.codePointAt(0)) || STAFF_TEXT.matcher(searchable).find()
                || STAFF_ROLE_TEXT.matcher(searchable).find();
    }

    public static GlobalMessageCategory classify(String rawMessage) {
        String message = rawMessage == null ? "" : rawMessage.stripLeading();
        if (message.isEmpty()) {
            return GlobalMessageCategory.SYSTEM;
        }
        // Ces formats sont des annonces serveur, même si un de leurs glyphes
        // entre un jour en collision avec une icône de grade joueur.
        if (CAPTURE_ANNOUNCEMENT.matcher(message).matches()
                || BOOST_ANNOUNCEMENT.matcher(message).matches()) {
            return GlobalMessageCategory.SYSTEM;
        }
        int icon = message.codePointAt(0);
        // Dans le chat, seul un préfixe officiel compte. Un joueur qui écrit
        // « admin » dans son texte ne doit jamais quitter sa catégorie de grade.
        if (STAFF.contains(icon) || STAFF_TEXT.matcher(stripAccents(message)).find()) {
            return GlobalMessageCategory.STAFF;
        }
        if (UNRANKED.contains(icon)) {
            return GlobalMessageCategory.UNRANKED;
        }
        if (CHAMPIONS.contains(icon)) {
            return GlobalMessageCategory.CHAMPION;
        }
        if (SUPER.contains(icon)) {
            return GlobalMessageCategory.SUPER;
        }
        if (HYPER.contains(icon)) {
            return GlobalMessageCategory.HYPER;
        }
        if (MASTER.contains(icon)) {
            return GlobalMessageCategory.MASTER;
        }
        return GlobalMessageCategory.SYSTEM;
    }

    private static String stripAccents(String value) {
        return DIACRITICS.matcher(Normalizer.normalize(value, Normalizer.Form.NFD))
                .replaceAll("");
    }
}
