package fr.tropimon.chatfilter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatPlayerNameTest {
    @Test
    void findsActualSpeakerAcrossChannels() {
        assertSpeaker("mxrerr", "[ꑤ mxrerr 석 Polaris] salut", "Taylor");
        assertSpeaker("mxrerr", "[ꑤ mxrerr 석 Moi] salut", "Taylor");
        assertSpeaker("Taylor", "[Moi 석 ꑤ mxrerr] réponse", "Taylor");
        assertSpeaker("Alex", "[Groupe] Alex: bonjour", "Taylor");
        assertSpeaker("Alex", "[Group] Alex: hello", "Taylor");
    }

    private static void assertSpeaker(String expected, String message, String local) {
        assertEquals(expected, MessageAnalysis.analyze(message, local).speaker().orElseThrow());
    }
}
