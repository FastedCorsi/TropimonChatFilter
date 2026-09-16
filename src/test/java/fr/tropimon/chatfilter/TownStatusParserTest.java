package fr.tropimon.chatfilter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TownStatusParserTest {
    @Test
    void recognizesFrenchAndEnglishNoTownResponses() {
        assertTrue(TownStatusParser.isNoTownMessage("Tu n'as pas de ville."));
        assertTrue(TownStatusParser.isNoTownMessage("Vous ne faites partie d'aucune ville."));
        assertTrue(TownStatusParser.isNoTownMessage("You are not in a town."));
    }

    @Test
    void neverTreatsTownChatAsAnError() {
        assertFalse(TownStatusParser.isNoTownMessage("[FastedCorsi 석 Polaris] test"));
    }
}
