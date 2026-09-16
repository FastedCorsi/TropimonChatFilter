package fr.tropimon.chatfilter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatMessageClassifierTest {
    @Test
    void recognizesRealTropimonTownFormat() {
        assertEquals(ChatChannel.TOWN,
                ChatMessageClassifier.classify("[ꑤ mxrerr 석 Polaris] y a brad dans igloo.."));
        assertEquals(ChatChannel.TOWN,
                ChatMessageClassifier.classify("[ Bluetopaze1 석 Polaris] bah bon courage a corsi"));
        assertEquals("Polaris", ChatMessageClassifier.extractTownName(
                "[섫 FastedCorsi 석 Polaris] test").orElseThrow());
        assertEquals("FastedCorsi", ChatMessageClassifier.extractTownSender(
                "[섫 FastedCorsi 석 Polaris] test").orElseThrow());
        assertEquals(true, ChatMessageClassifier.extractTownName(
                "[섫 FastedCorsi 석  Bluetopaze1] coucou").isEmpty());
        assertEquals("Bluetopaze1", ChatMessageClassifier.extractTownDestination(
                        "[섫 FastedCorsi 석  Bluetopaze1] coucou")
                .flatMap(ChatMessageClassifier::extractTrailingPlayerName).orElseThrow());
        assertEquals(ChatChannel.PRIVATE,
                ChatMessageClassifier.classify("[ꑤ tempete1 석 Moi] oui"));
        assertEquals(ChatChannel.PRIVATE,
                ChatMessageClassifier.classify("[Moi 석 ꑤ tempete1] coucou"));
        assertEquals(true, ChatMessageClassifier.extractTownName(
                "[ꑤ tempete1 석 Moi] oui").isEmpty());
        assertEquals(true, ChatMessageClassifier.extractTownName(
                "[Moi 석 ꑤ tempete1] coucou").isEmpty());
    }

    @Test
    void keepsNormalAndSystemMessagesInAllOnly() {
        assertEquals(ChatChannel.ALL, ChatMessageClassifier.classify("ꈎ Kingder420: c good le deco reco thx"));
        assertEquals(ChatChannel.ALL, ChatMessageClassifier.classify("ꌈ Suppression des entités dans 1 minute"));
    }

    @Test
    void recognizesCommonPrivateMessageFormats() {
        assertEquals(ChatChannel.PRIVATE, ChatMessageClassifier.classify("[MP] Alex: salut"));
        assertEquals(ChatChannel.PRIVATE, ChatMessageClassifier.classify("Alex -> moi : salut"));
        assertEquals(ChatChannel.PRIVATE, ChatMessageClassifier.classify("[Alex -> moi] salut"));
        assertEquals(ChatChannel.PRIVATE, ChatMessageClassifier.classify("De Alex: salut"));
        assertEquals(ChatChannel.PRIVATE, ChatMessageClassifier.classify("À Alex: salut"));
        assertEquals(ChatChannel.PRIVATE, ChatMessageClassifier.classify("Alex vous chuchote: salut"));
        assertEquals(ChatChannel.PRIVATE,
                ChatMessageClassifier.classify("Message privé de Alex : salut"));
    }

    @Test
    void privateMessagesCanNeverBeClassifiedAsTown() {
        assertEquals(ChatChannel.PRIVATE,
                ChatMessageClassifier.classify("[Alex -> moi] message de ville ?"));
        assertEquals(ChatChannel.PRIVATE,
                ChatMessageClassifier.classify("[MP] Alex: Polaris"));
    }

    @Test
    void separatesStaffDestinationFromTown() {
        assertEquals(ChatChannel.STAFF,
                ChatMessageClassifier.classify("[ꌃ RodLeJoueur 석 Staff] contrôle"));
        assertTrue(ChatMessageClassifier.isStaff("[Staff] RodLeJoueur: contrôle"));
        assertFalse(ChatMessageClassifier.isTown("[ꌃ RodLeJoueur 석 Staff] contrôle"));
        assertEquals(ChatChannel.TOWN,
                ChatMessageClassifier.classify("[ꌃ RodLeJoueur 석 Polaris] salut"));
    }

    @Test
    void recognizesOnlyServerChannelChangeConfirmations() {
        assertTrue(ChatMessageClassifier.isChannelChangeConfirmation(
                "ꌇ Your chat has been successfully added to: GLOBAL"));
        assertTrue(ChatMessageClassifier.isChannelChangeConfirmation(
                "Your chat has been successfully added to: TOWN"));
        assertTrue(ChatMessageClassifier.isChannelChangeConfirmation(
                "ꌇ Your chat has been successfully added to: STAFF"));
        assertTrue(ChatMessageClassifier.isChannelChangeConfirmation(
                "ꌇ Votre chat a été ajouté avec succès à : GLOBAL"));
        assertTrue(ChatMessageClassifier.isChannelChangeConfirmation(
                "Vous avez changé de chat : VILLE"));
        assertTrue(ChatMessageClassifier.isChannelChangeConfirmation(
                "Vous êtes désormais dans le canal STAFF."));
        assertTrue(ChatMessageClassifier.isChannelChangeConfirmation(
                "You have switched your channel to: TOWN"));
        assertTrue(ChatMessageClassifier.isChannelChangeConfirmation(
                "ꌇ Ton chat a bien été mis sur : TOWN"));
        assertTrue(ChatMessageClassifier.isChannelChangeConfirmation(
                "ꌇ Ton chat a bien été mis sur : GLOBAL"));
        assertTrue(ChatMessageClassifier.isChannelChangeConfirmation(
                "ꌇ\u00a0Ton chat a bien été mis sur\u202f:\u00a0TOWN"));
        assertFalse(ChatMessageClassifier.isChannelChangeConfirmation(
                "FastedCorsi: Your chat has been successfully added to: GLOBAL"));
        assertFalse(ChatMessageClassifier.isChannelChangeConfirmation(
                "ꌃ FastedCorsi: Vous avez changé de chat : GLOBAL"));
        assertFalse(ChatMessageClassifier.isChannelChangeConfirmation(
                "ꌃ FastedCorsi: Ton chat a bien été mis sur : TOWN"));
        assertFalse(ChatMessageClassifier.isChannelChangeConfirmation(
                "ꌇ Téléportation réussie"));
        assertFalse(ChatMessageClassifier.isChannelChangeConfirmation(
                "ꌈ Suppression des objets au sol dans 30 secondes"));
        assertFalse(ChatMessageClassifier.isChannelChangeConfirmation(
                "Votre récompense quotidienne est disponible"));
        assertFalse(ChatMessageClassifier.isChannelChangeConfirmation(
                "Maintenance du serveur à 20h"));
    }
}
