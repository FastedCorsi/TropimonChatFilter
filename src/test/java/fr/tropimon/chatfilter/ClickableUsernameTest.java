package fr.tropimon.chatfilter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClickableUsernameTest {
    @Test
    void extractsTownAndGlobalPlayersForMentions() {
        assertSuggestion("[ꑤ mxrerr 석 Polaris] message", "Taylor", "@mxrerr ");
        assertSuggestion("ꈎ Kingder420: message", "Taylor", "@Kingder420 ");
        assertSuggestion("<Alex> message", "Taylor", "@Alex ");
        assertSuggestion("[Groupe] Alex: message", "Taylor", "@Alex ");
    }

    @Test
    void extractsPrivateCounterpartForReplies() {
        assertSuggestion("[MP] Alex: salut", "Taylor", "/msg Alex ");
        assertSuggestion("[Alex -> Taylor] salut", "Taylor", "/msg Alex ");
        assertSuggestion("[Taylor -> Alex] salut", "Taylor", "/msg Alex ");
        assertSuggestion("À Alex: salut", "Taylor", "/msg Alex ");
        assertSuggestion("[ꑤ tempete1 석 Moi] oui", "Taylor", "/msg tempete1 ");
        assertSuggestion("[Moi 석 ꑤ tempete1] coucou", "FastedCorsi", "/msg tempete1 ");
    }

    @Test
    void ignoresSystemMessages() {
        assertTrue(ClickableUsername.find("ꌈ Suppression des entités dans 1 minute", "Taylor").isEmpty());
    }

    private static void assertSuggestion(String message, String localPlayer, String expected) {
        ClickableUsername.Match match = ClickableUsername.find(message, localPlayer).orElseThrow();
        assertEquals(expected, match.suggestion());
        assertEquals(match.name(), message.substring(match.start(), match.end()));
    }
}
