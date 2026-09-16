package fr.tropimon.chatfilter;

import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupMessageProtocolTest {
    @Test
    void roundTripsInvitationMessageAndLeavePayloadsInsidePrivateEnvelopes() {
        String invitation = GroupMessageProtocol.invitation(
                "a1b2c3d4", List.of("Taylor", "Alex", "Bob"));
        var parsedInvitation = GroupMessageProtocol.parse(
                "[Alex 석 Moi] " + invitation).orElseThrow();
        assertEquals(GroupMessageProtocol.Type.I, parsedInvitation.type());
        assertEquals(List.of("Taylor", "Alex", "Bob"), parsedInvitation.members());

        var parsedMessage = GroupMessageProtocol.parse(
                "[Alex 석 Moi] " + GroupMessageProtocol.message("a1b2c3d4", "bonjour à tous"))
                .orElseThrow();
        assertEquals(GroupMessageProtocol.Type.M, parsedMessage.type());
        assertEquals("bonjour à tous", parsedMessage.body());

        var parsedResolvedMessage = GroupMessageProtocol.parse(
                GroupMessageProtocol.resolvedMessage("a1b2c3d4", "12abef", "Hydragon"))
                .orElseThrow();
        assertEquals(GroupMessageProtocol.Type.L, parsedResolvedMessage.type());
        assertEquals("r12abef", parsedResolvedMessage.data());
        assertEquals("Hydragon", parsedResolvedMessage.body());

        var parsedLeave = GroupMessageProtocol.parse(
                GroupMessageProtocol.leave("a1b2c3d4", "Alex")).orElseThrow();
        assertEquals(GroupMessageProtocol.Type.L, parsedLeave.type());
        assertEquals("Alex", parsedLeave.data());
    }

    @Test
    void rejectsMalformedPayloads() {
        assertTrue(GroupMessageProtocol.parse("message normal").isEmpty());
        assertTrue(GroupMessageProtocol.parse("[[TCFG:I:a1b2c3d4:bad name,Alex]]").isEmpty());
        assertTrue(GroupMessageProtocol.parse("[[TCFG:M:a1b2c3d4]]").isEmpty());
    }

    @Test
    void preservesServerStylesInPartyReferences() {
        String payload = GroupMessageProtocol.message(
                "a1b2c3d4", "12abef", "<party:5>");
        Text source = Text.empty()
                .append(Text.literal("[Moi -> Alex] " + payload.substring(
                        0, payload.indexOf("<party:5>"))))
                .append(Text.literal("<party:5>").formatted(Formatting.GREEN));
        var parsed = GroupMessageProtocol.parse(source.getString()).orElseThrow();
        Text body = GroupMessageProtocol.styledBody(source, parsed);
        assertEquals("<party:5>", body.getString());
        assertEquals(Formatting.GREEN.getColorValue(),
                body.getSiblings().getFirst().getStyle().getColor().getRgb());
    }
}
