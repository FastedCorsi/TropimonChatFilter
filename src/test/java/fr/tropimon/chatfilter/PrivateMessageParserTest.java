package fr.tropimon.chatfilter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrivateMessageParserTest {
    @Test
    void findsIncomingAndOutgoingCounterparts() {
        assertMessage("[Alex -> Taylor] salut", "Taylor", "Alex", true);
        assertMessage("[Taylor -> Alex] salut", "Taylor", "Alex", false);
        assertMessage("[Taylor <- Alex] salut", "Taylor", "Alex", true);
        assertMessage("[MP] Alex: salut", "Taylor", "Alex", true);
        assertMessage("À Alex: salut", "Taylor", "Alex", false);
        assertMessage("Message privé de Alex : salut", "Taylor", "Alex", true);
        assertMessage("Message à Alex : salut", "Taylor", "Alex", false);
        assertMessage("Vous chuchotez à Alex: salut", "Taylor", "Alex", false);
        assertMessage("[ꑤ Alex 석 Moi] salut", "Taylor", "Alex", true);
        assertMessage("[ꑤ Alex 석 Taylor] salut", "Taylor", "Alex", true);
        assertMessage("[Moi 석 ꑤ tempete1] coucou", "FastedCorsi", "tempete1", false);
        assertMessage("[섫 FastedCorsi 석  Bluetopaze1] coucou", "FastedCorsi", "Bluetopaze1", false);
    }

    @Test
    void ignoresGlobalAndTownMessages() {
        assertTrue(PrivateMessageParser.parse("ꈎ Alex: salut", "Taylor").isEmpty());
        assertTrue(PrivateMessageParser.parse("[ꑤ Alex 석 Polaris] salut", "Taylor").isEmpty());
        assertTrue(PrivateMessageParser.parse("[ꑤ Taylor 석 Polaris] salut", "Taylor").isEmpty());
        assertTrue(PrivateMessageParser.parse("[ꑥ Taylor 석 Nouvelle Polaris] salut", "Taylor").isEmpty());
        assertTrue(PrivateMessageParser.parse(
                "ꑤ Taylor: Alex -> Bob est une flèche", "Taylor").isEmpty());
        assertTrue(PrivateMessageParser.parse("[Alex -> Bob] salut", "Taylor").isEmpty());
    }

    private static void assertMessage(
            String raw, String localPlayer, String counterpart, boolean incoming) {
        PrivateMessageParser.Parsed parsed = PrivateMessageParser.parse(raw, localPlayer).orElseThrow();
        assertEquals(counterpart, parsed.counterpart());
        assertEquals(counterpart, raw.substring(parsed.nameStart(), parsed.nameEnd()));
        if (incoming) {
            assertTrue(parsed.incoming());
        } else {
            assertFalse(parsed.incoming());
        }
    }
}
