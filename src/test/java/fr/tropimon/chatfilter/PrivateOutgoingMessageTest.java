package fr.tropimon.chatfilter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrivateOutgoingMessageTest {
    @Test
    void extractsTargetAndBodyFromMsgCommand() {
        PrivateOutgoingMessage parsed = PrivateOutgoingMessage.parse(
                "/msg Alex salut toi", null)
                .orElseThrow();
        assertEquals("Alex", parsed.target());
        assertEquals("salut toi", parsed.body());

        PrivateOutgoingMessage reply = PrivateOutgoingMessage.parse("/r rebonjour", "Alex")
                .orElseThrow();
        assertEquals("Alex", reply.target());
        assertEquals("rebonjour", reply.body());
    }

    @Test
    void ignoresIncompleteOrUnrelatedCommands() {
        assertTrue(PrivateOutgoingMessage.parse("/msg Alex", null).isEmpty());
        assertTrue(PrivateOutgoingMessage.parse("/r salut", null).isEmpty());
        assertTrue(PrivateOutgoingMessage.parse("/spawn", null).isEmpty());
    }
}
