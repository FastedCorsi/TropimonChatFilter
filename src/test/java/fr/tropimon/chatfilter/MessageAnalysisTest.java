package fr.tropimon.chatfilter;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MessageAnalysisTest {
    static final List<String> MESSAGES = Arrays.asList(null, "", "  ",
            "[ꑤ mxrerr 석 Polaris] ville", "[ꑤ Taylor 석 Polaris] réponse ville",
            "[Moi 석 Polaris] réponse", "[ꑥ Taylor 석 Nouvelle Polaris] salut",
            "[ꑤ Alex 석 Moi] privé", "[ꑤ Alex 석 Taylor] privé",
            "[Moi 석 ꑤ Alex] réponse", "[ꑥ Taylor 석 ꑤ Alex] réponse",
            "[Alex -> Taylor] salut", "[Taylor -> Alex] salut", "[Taylor <- Alex] salut",
            "[Alex -> Bob] salut", "[MP] Alex: salut", "À Alex: salut", "De Alex: salut",
            "Message privé de Alex : salut", "Vous chuchotez à Alex: salut",
            "[ꌃ RodLeJoueur 석 Staff] contrôle", "[Staff] Alex: contrôle",
            "[équipe] Taylor: contrôle", "[ville] Alex: salut",
            "ꈎ Alex: salut", "ꌃ Alex: salut", "ꑤ Alex: salut", "ꑥ Alex: salut",
            "ꌇ Ton chat a bien été mis sur : TOWN", "Vous avez changé de chat : VILLE",
            "ꌇ Téléportation réussie", "Votre récompense quotidienne est disponible",
            "ꌃ Alex: Ton chat a bien été mis sur : TOWN", "Vous n'avez pas de ville",
            "[Groupe] Alex : groupe", "[Group] Taylor : group",
            "[ꑤ Alex 석 Moi] [[TCFG:M:abcdef12]] groupe",
            "[ꑤ Alex 석 Moi] [[TCFG:I:abcdef12:Alex,Taylor]]",
            "[ꑤ Alex 석 Moi] [[TCFG:L:abcdef12:Alex]]",
            "Alex: message sur\nplusieurs lignes");

    @Test void stableFactsMatchLegacyParsersForAllFormatsAndPlayers() {
        for (String local : Arrays.asList(null, "Taylor", "Alex", "taylor")) {
            for (String raw : MESSAGES) {
                MessageAnalysis actual = MessageAnalysis.analyze(raw, local);
                assertEquals(PrivateMessageParser.parse(raw, local), actual.privateMessage(), raw);
                assertEquals(ChatMessageClassifier.isStaff(raw), actual.staff(), raw);
                assertEquals(ChatMessageClassifier.isTown(raw), actual.town(), raw);
                assertEquals(ChatMessageClassifier.extractTownName(raw), actual.townName(), raw);
                assertEquals(ChatMessageClassifier.extractTownSender(raw), actual.townSender(), raw);
                assertEquals(ChatMessageClassifier.isChannelChangeConfirmation(raw), actual.channelConfirmation(), raw);
                assertEquals(GlobalMessageClassifier.classify(raw), actual.category(), raw);
                assertEquals(TownStatusParser.isNoTownMessage(raw), actual.noTown(), raw);
                assertEquals(ChatPlayerName.speaker(raw, local), actual.speaker(), raw);
                assertEquals(GroupMessageProtocol.parse(raw), actual.groupPayload(), raw);
            }
        }
    }

    @Test void cachedFactsNeverFreezeVisibilityOrConversationMembership() {
        for (String raw : MESSAGES) {
            var facts = MessageAnalysis.analyze(raw, "Taylor");
            for (ChatChannel channel : ChatChannel.values()) {
                for (String peer : Arrays.asList(null, "Alex", "alex", "Bob")) {
                    for (boolean group : List.of(false, true)) {
                        for (boolean active : List.of(false, true)) {
                            for (boolean filter : List.of(false, true)) {
                                boolean expected = legacyVisible(raw, channel, peer, group, active, filter);
                                assertEquals(expected, MessageVisibility.show(
                                                facts, channel, peer, group, active, false, c -> filter),
                                        raw + " / " + channel + " / " + peer);
                            }
                        }
                    }
                }
            }
        }
    }

    @Test void allCanIncludeEveryAvailableChatWithoutBypassingGlobalFilters() {
        assertTrue(showEveryChat("[ville] Alex: salut", false, false, false));
        assertTrue(showEveryChat("[Staff] Alex: contrôle", false, false, false));
        assertTrue(showEveryChat("De Alex: salut", false, false, false));
        assertTrue(showEveryChat("[ꑤ Alex 석 Moi] [[TCFG:M:abcdef12]] groupe",
                true, true, false));
        assertFalse(showEveryChat("[ꑤ Alex 석 Moi] [[TCFG:M:abcdef12]] groupe",
                true, false, true));
        assertFalse(showEveryChat("ꑤ Alex: salut", false, false, false));
        assertTrue(showEveryChat("ꑤ Alex: salut", false, false, true));
        assertFalse(showEveryChat("Ton chat a bien été mis sur : TOWN",
                false, false, true));
    }

    @Test void identifiesStandaloneSystemRepliesWithoutCapturingChats() {
        assertTrue(MessageVisibility.standaloneSystemMessage(
                MessageAnalysis.analyze("Sanction appliquée", "Taylor")));
        assertFalse(MessageVisibility.standaloneSystemMessage(
                MessageAnalysis.analyze("ꑥ StaffTest: sanction appliquée", "Taylor")));
        assertFalse(MessageVisibility.standaloneSystemMessage(
                MessageAnalysis.analyze("[ꑤ StaffTest 석 Polaris] contrôle", "Taylor")));
        assertFalse(MessageVisibility.standaloneSystemMessage(
                MessageAnalysis.analyze("[Staff] StaffTest: contrôle", "Taylor")));
    }

    private static boolean legacyVisible(String raw, ChatChannel channel, String peer,
            boolean group, boolean active, boolean filter) {
        if (ChatMessageClassifier.isChannelChangeConfirmation(raw)) return false;
        var pm = PrivateMessageParser.parse(raw, "Taylor");
        return switch (channel) {
            case ALL -> !group && !ChatMessageClassifier.isStaff(raw) && pm.isEmpty()
                    && !ChatMessageClassifier.isTown(raw)
                    && (GlobalMessageClassifier.classify(raw) == GlobalMessageCategory.STAFF || filter);
            case TOWN -> !group && !ChatMessageClassifier.isStaff(raw) && pm.isEmpty() && ChatMessageClassifier.isTown(raw);
            case STAFF -> ChatMessageClassifier.isStaff(raw);
            case PRIVATE -> !group && peer != null && pm.map(p -> p.counterpart().equalsIgnoreCase(peer)).orElse(false);
            case GROUP -> group && active;
        };
    }

    private static boolean showEveryChat(
            String raw, boolean group, boolean active, boolean globalFilter) {
        return MessageVisibility.show(MessageAnalysis.analyze(raw, "Taylor"),
                ChatChannel.ALL, null, group, active, true, category -> globalFilter);
    }
}
