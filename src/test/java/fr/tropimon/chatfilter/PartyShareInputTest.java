package fr.tropimon.chatfilter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PartyShareInputTest {
    @Test
    void mapsScaledCobblemonPartySlots() {
        assertEquals(1, PartyShareInput.slotAt(400, 20, 100, 180, 280));
        assertEquals(2, PartyShareInput.slotAt(400, 20, 134, 180, 280));
        assertEquals(6, PartyShareInput.slotAt(400, 20, 270, 180, 280));
        assertEquals(0, PartyShareInput.slotAt(400, 70, 100, 180, 280));
        assertEquals(0, PartyShareInput.slotAt(400, 20, 131, 180, 280));
    }

    @Test
    void chatSurfaceWinsOverOverlappingPartySlots() {
        assertEquals(0, PartyShareInput.slotAt(400, 20, 270, 180, 250));
        assertEquals(6, PartyShareInput.slotAt(400, 20, 270, 10, 250));
    }

    @Test
    void createsOnlyOfficialPartyTags() {
        assertEquals("<party:1>", PartyShareInput.tag(1));
        assertEquals("<party:6>", PartyShareInput.tag(6));
        assertThrows(IllegalArgumentException.class, () -> PartyShareInput.tag(0));
        assertThrows(IllegalArgumentException.class, () -> PartyShareInput.tag(7));
    }

    @Test
    void insertsOnClickOrDropOnly() {
        PartyShareInput.startDrag(3, 20, 200);
        assertEquals(3, PartyShareInput.finishDrag(21, 201, false));

        PartyShareInput.startDrag(4, 20, 200);
        assertEquals(4, PartyShareInput.finishDrag(200, 390, true));

        PartyShareInput.startDrag(5, 20, 200);
        assertEquals(0, PartyShareInput.finishDrag(200, 300, false));
        assertEquals(0, PartyShareInput.draggedSlot());
    }
}
