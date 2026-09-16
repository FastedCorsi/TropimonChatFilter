package fr.tropimon.chatfilter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalMessageClassifierTest {
    @Test
    void recognizesOfficialTropimonRankGlyphs() {
        assertCategory(GlobalMessageCategory.UNRANKED, "ꈎ Joueur: salut");
        assertCategory(GlobalMessageCategory.CHAMPION, "섫 ChampionDragon: salut");
        assertCategory(GlobalMessageCategory.SUPER, "ꑣ SuperJoueur: salut");
        assertCategory(GlobalMessageCategory.HYPER, "ꑤ HyperJoueur: salut");
        assertCategory(GlobalMessageCategory.MASTER, "ꑥ MasterJoueur: salut");
        assertCategory(GlobalMessageCategory.MASTER, " MasterCyan: salut");
    }

    @Test
    void treatsAnnouncementsAndStaffAsServerMessages() {
        assertCategory(GlobalMessageCategory.SYSTEM, "ꌈ Suppression des objets au sol");
        assertCategory(GlobalMessageCategory.STAFF, "ꌂ Admin: annonce");
        assertCategory(GlobalMessageCategory.STAFF, "ꌃ Modérateur: annonce");
        assertCategory(GlobalMessageCategory.STAFF, "ꌄ Équipe: annonce");
        assertCategory(GlobalMessageCategory.STAFF, " Guide: bienvenue");
        assertCategory(GlobalMessageCategory.STAFF, "[Modérateur] contrôle");
    }

    @Test
    void keepsOfficialCapturesAndBoostsInSystemFilter() {
        assertCategory(GlobalMessageCategory.SYSTEM,
                "옏 CaptureTest a capturé un Pachirisu 逞");
        assertCategory(GlobalMessageCategory.SYSTEM,
                " CaptureTest a capturé un Mew ");
        assertCategory(GlobalMessageCategory.SYSTEM,
                " CaptureTest a capturé un Kyogre ");
        assertCategory(GlobalMessageCategory.SYSTEM,
                " BoosterTest triggered a Boost Shiny x2 for an hour !");
        assertCategory(GlobalMessageCategory.SYSTEM,
                " 3 boosts are active");
        assertCategory(GlobalMessageCategory.SYSTEM,
                "- Shiny x2 (end in 29 minutes and 4 seconds)");
        assertCategory(GlobalMessageCategory.SYSTEM,
                "- IVs +10 (end in 12 minutes)");
        assertCategory(GlobalMessageCategory.SYSTEM,
                "- Talent Caché 25% (end in 1 minute and 1 second)");
        assertCategory(GlobalMessageCategory.SYSTEM,
                "ꌈ No boosts are currently active.");
        assertCategory(GlobalMessageCategory.SYSTEM,
                " A boost has expired...");
    }

    @Test
    void doesNotPromotePlayerDiscussionAboutCapturesOrBoostsToSystem() {
        assertCategory(GlobalMessageCategory.MASTER,
                "ꑥ MasterJoueur: j'ai activé un boost shiny");
        assertCategory(GlobalMessageCategory.HYPER,
                "ꑤ HyperJoueur: Alex a capturé un Mew");
    }

    @Test
    void detectsStaffRoleTextWithoutTreatingOrdinaryPlayersAsStaff() {
        assertTrue(GlobalMessageClassifier.looksLikeStaffIdentity("[Admin] Taylor"));
        assertTrue(GlobalMessageClassifier.looksLikeStaffIdentity("Responsable Taylor"));
        assertTrue(GlobalMessageClassifier.looksLikeStaffIdentity("ꌃ RodLeJoueur"));
        assertFalse(GlobalMessageClassifier.looksLikeStaffIdentity("ꑤ tempete1"));
        assertCategory(GlobalMessageCategory.HYPER,
                "ꑤ HyperJoueur: un admin arrive");
        assertCategory(GlobalMessageCategory.UNRANKED,
                "ꈎ Joueur: le staff est connecté");
    }

    private static void assertCategory(GlobalMessageCategory expected, String message) {
        assertEquals(expected, GlobalMessageClassifier.classify(message));
    }
}
