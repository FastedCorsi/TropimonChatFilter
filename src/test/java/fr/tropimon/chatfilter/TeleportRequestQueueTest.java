package fr.tropimon.chatfilter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TeleportRequestQueueTest {
    @Test
    void queuesRequestsAndAttachesEachFollowingOptionsLineInOrder() {
        TeleportRequestQueue queue = new TeleportRequestQueue();
        queue.offer(request("FirstPlayer"), role(PlayerRole.ADMIN, 0xFFFF5555), 1_000L);
        assertTrue(queue.attachActions("/accept first", "/deny first", 1_010L));
        queue.offer(request("SecondPlayer"), role(PlayerRole.MASTER, 0xFFFF55FF), 1_020L);
        assertTrue(queue.attachActions("/accept second", "/deny second", 1_030L));

        assertEquals(2, queue.size());
        assertEquals("FirstPlayer", queue.current().player());
        assertEquals(PlayerRole.ADMIN, queue.current().role());
        assertEquals(0xFFFF5555, queue.current().roleColor());
        assertEquals("/accept first", queue.current().command(true));
        assertTrue(queue.resolveCurrent());
        assertEquals("SecondPlayer", queue.current().player());
        assertEquals(PlayerRole.MASTER, queue.current().role());
        assertEquals("/deny second", queue.current().command(false));
    }

    @Test
    void expiresOldRequestsCountsBurstsAndStaysBounded() {
        TeleportRequestQueue queue = new TeleportRequestQueue();
        queue.offer(request("Repeated"), role(PlayerRole.UNRANKED, 0xFFAAAAAA), 1_000L);
        queue.offer(request("Repeated"), role(PlayerRole.UNRANKED, 0xFFAAAAAA), 1_500L);
        assertEquals(2, queue.size());
        queue.expire(1_000L + TeleportRequestQueue.REQUEST_LIFETIME_MILLIS);
        assertEquals(1, queue.size());
        queue.expire(1_500L + TeleportRequestQueue.REQUEST_LIFETIME_MILLIS);
        assertNull(queue.current());

        for (int index = 0; index < TeleportRequestQueue.MAX_REQUESTS + 3; index++) {
            queue.offer(request("Player_" + index), role(PlayerRole.CHAMPION, 0xFFFFAA00),
                    100_000L + index * 3_000L);
        }
        queue.expire(140_000L);
        assertEquals(TeleportRequestQueue.MAX_REQUESTS, queue.size());
        assertEquals("Player_3", queue.current().player());
    }

    private static TeleportRequestParser.Request request(String player) {
        return new TeleportRequestParser.Request(
                player, TeleportRequestParser.Direction.TO_REQUESTER);
    }

    private static PlayerRoleResolver.Resolved role(PlayerRole role, int color) {
        return new PlayerRoleResolver.Resolved(role, color);
    }
}
