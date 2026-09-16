package fr.tropimon.chatfilter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerChannelStateTest {
    @Test
    void suppressesRepeatedRequestsOnTheSameConnection() {
        ServerChannelState state = new ServerChannelState();
        Object connection = new Object();

        assertTrue(state.shouldRequest(connection, ChatChannel.ALL));
        assertFalse(state.shouldRequest(connection, ChatChannel.ALL));
        assertTrue(state.shouldRequest(connection, ChatChannel.TOWN));
        assertFalse(state.shouldRequest(connection, ChatChannel.TOWN));
    }

    @Test
    void resetsWhenThePlayerJoinsAnotherConnection() {
        ServerChannelState state = new ServerChannelState();

        assertTrue(state.shouldRequest(new Object(), ChatChannel.ALL));
        assertTrue(state.shouldRequest(new Object(), ChatChannel.ALL));
    }

    @Test
    void observesCommandsTypedByThePlayer() {
        ServerChannelState state = new ServerChannelState();
        Object connection = new Object();
        state.observe(connection, "/chat TOWN");

        assertFalse(state.shouldRequest(connection, ChatChannel.TOWN));
        assertTrue(state.shouldRequest(connection, ChatChannel.ALL));
        assertEquals(ChatChannel.STAFF,
                ServerChannelState.parseCommand("chat staff").orElseThrow());
        assertTrue(ServerChannelState.parseCommand("/msg joueur test").isEmpty());
    }
}
