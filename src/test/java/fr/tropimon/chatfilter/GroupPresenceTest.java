package fr.tropimon.chatfilter;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GroupPresenceTest {
    @Test
    void countsOnlyConnectedMembersWithoutLosingOfflineMembers() {
        assertEquals(2, GroupChatManager.countConnected(
                List.of("Taylor", "Alex", "Bob"), Set.of("taylor", "bob")));
        assertEquals(0, GroupChatManager.countConnected(
                List.of("Taylor", "Alex"), Set.of("quelquun_dautre")));
    }
}
