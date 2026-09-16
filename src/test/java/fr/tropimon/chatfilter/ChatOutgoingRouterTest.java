package fr.tropimon.chatfilter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatOutgoingRouterTest {
    @Test
    void preparesTheSelectedChannel() {
        assertEquals("", ChatOutgoingRouter.prepareInput(ChatChannel.TOWN, ""));
        assertEquals("bonjour", ChatOutgoingRouter.prepareInput(ChatChannel.TOWN, "bonjour"));
        assertEquals("/msg ", ChatOutgoingRouter.prepareInput(ChatChannel.PRIVATE, ""));
    }

    @Test
    void routesPlainMessagesButPreservesExplicitCommands() {
        assertEquals("bonjour", ChatOutgoingRouter.route(ChatChannel.TOWN, "bonjour", null));
        assertEquals("/msg Alex salut", ChatOutgoingRouter.route(
                ChatChannel.PRIVATE, "Alex salut", null));
        assertEquals("/msg Alex salut", ChatOutgoingRouter.route(
                ChatChannel.PRIVATE, "salut", "Alex"));
        assertEquals("bonjour", ChatOutgoingRouter.route(ChatChannel.ALL, "bonjour", null));
        assertEquals("/spawn", ChatOutgoingRouter.route(ChatChannel.TOWN, "/spawn", null));
        assertEquals("/r salut", ChatOutgoingRouter.route(
                ChatChannel.PRIVATE, "/r salut", "Alex"));
        assertEquals("/ban TestPlayer raison", ChatOutgoingRouter.route(
                ChatChannel.GROUP, "/ban TestPlayer raison", null));
        assertEquals("/warn TestPlayer raison", ChatOutgoingRouter.route(
                ChatChannel.TOWN, "/warn TestPlayer raison", null));
    }

    @Test
    void recognizesOnlyCommandsMinecraftWouldSendAsCommands() {
        assertEquals(true, ChatOutgoingRouter.isExplicitCommand("/staffcommand"));
        assertEquals(false, ChatOutgoingRouter.isExplicitCommand(" /staffcommand"));
        assertEquals(false, ChatOutgoingRouter.isExplicitCommand("message"));
    }

    @Test
    void leavingTownRestoresThePlainDraft() {
        assertEquals("bonjour", ChatOutgoingRouter.prepareInput(ChatChannel.TOWN, "/tc bonjour"));
        assertEquals("bonjour", ChatOutgoingRouter.prepareInput(ChatChannel.ALL, "/tc bonjour"));
    }

    @Test
    void leavingPrivateNeverLeaksThePrivateDraftIntoPublicChat() {
        assertEquals("", ChatOutgoingRouter.prepareInput(ChatChannel.ALL, "/msg Alex secret"));
        assertEquals("", ChatOutgoingRouter.prepareInput(ChatChannel.TOWN, "/r secret"));
    }

    @Test
    void partyReferencesReachEveryServerChatUnchanged() {
        assertEquals("regardez <party:5>", ChatOutgoingRouter.route(
                ChatChannel.ALL, "regardez <party:5>", null));
        assertEquals("regardez <party:5>", ChatOutgoingRouter.route(
                ChatChannel.TOWN, "regardez <party:5>", null));
        assertEquals("regardez <party:5>", ChatOutgoingRouter.route(
                ChatChannel.STAFF, "regardez <party:5>", null));
        assertEquals("/msg Alex regardez <party:5>", ChatOutgoingRouter.route(
                ChatChannel.PRIVATE, "regardez <party:5>", "Alex"));
    }
}
