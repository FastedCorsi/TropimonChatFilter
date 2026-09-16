package fr.tropimon.chatfilter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TeleportPopupPositionTest {
    @Test
    void restoredAndDraggedPositionRemainsClampedAndDoesNotJump() {
        var position = new TeleportPopupPosition.Position(
                TeleportPopupPosition.WIDTH, TeleportPopupPosition.HEIGHT);
        position.restore(620, 400);
        assertEquals(618, position.left(800));
        assertEquals(400, position.top(600));

        assertTrue(position.start(650, 408, 800, 600));
        assertTrue(position.drag(-100, -100, 800, 600));
        assertEquals(2, position.left(800));
        assertEquals(2, position.top(600));
        assertTrue(position.stop());
        assertFalse(position.drag(500, 500, 800, 600));
    }

    @Test
    void defaultPositionIsCentered() {
        var position = new TeleportPopupPosition.Position(
                TeleportPopupPosition.WIDTH, TeleportPopupPosition.HEIGHT);
        assertEquals(310, position.left(800));
        assertEquals(246, position.top(600));
    }
}
