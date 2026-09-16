package fr.tropimon.chatfilter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TeleportRequestParserTest {
    @Test
    void recognizesBothFrenchTeleportDirectionsWithoutMatchingOrdinaryChat() {
        var toRequester = TeleportRequestParser.analyze(
                "ꌈ Sh1ntu demande à ce que tu te téléporte à lui");
        assertEquals("Sh1ntu", toRequester.request().orElseThrow().player());
        assertEquals(TeleportRequestParser.Direction.TO_REQUESTER,
                toRequester.request().orElseThrow().direction());

        var toLocal = TeleportRequestParser.analyze(
                "[23:19] ꌈ Example_2 demande à se téléporter vers toi");
        assertEquals(TeleportRequestParser.Direction.TO_LOCAL_PLAYER,
                toLocal.request().orElseThrow().direction());

        assertTrue(TeleportRequestParser.analyze(
                "Joueur: Sh1ntu demande à ce que tu te téléporte à lui").request().isEmpty());
        assertTrue(TeleportRequestParser.analyze("Téléportation réussie").request().isEmpty());
    }

    @Test
    void isolatesOptionsActionsAndResolutionMessages() {
        assertTrue(TeleportRequestParser.analyze(
                "Options: [✔ Accepter]   [❌ Décliner]").options());
        assertFalse(TeleportRequestParser.analyze(
                "Clique pour accepter le règlement").options());
        assertEquals(TeleportRequestParser.Action.ACCEPT,
                TeleportRequestParser.action("✔ Accepter", "/server-action 1"));
        assertEquals(TeleportRequestParser.Action.DECLINE,
                TeleportRequestParser.action("❌ Décliner", "/server-action 2"));
        assertTrue(TeleportRequestParser.analyze("ꌇTéléportation acceptée").resolved());
        assertFalse(TeleportRequestParser.analyze("ꌇ Téléportation réussie").resolved());
    }
}
