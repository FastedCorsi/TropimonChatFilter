package fr.tropimon.chatfilter;

import io.airlift.compress.zstd.ZstdCompressor;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ServerProfileWireTest {
    private static final UUID PLAYER = UUID.fromString("12345678-1234-1234-1234-123456789abc");
    private static final UUID TOWN = UUID.fromString("abcdef01-1234-1234-1234-123456789abc");

    @Test void readsSingleTownWithoutAnyOtherModAndPreservesTheBuffer() {
        ByteBuf input = townPacket();
        try {
            int reader = input.readerIndex(), writer = input.writerIndex(), refs = input.refCnt();
            var update = (ServerProfileState.Towns) ServerProfileWire.decode("tropimon:update_town_packet", input);
            assertFalse(update.batch());
            assertEquals("Polaris", update.towns().getFirst().name());
            assertEquals("Alex", update.towns().getFirst().citizens().get(PLAYER));
            assertEquals(reader, input.readerIndex());
            assertEquals(writer, input.writerIndex());
            assertEquals(refs, input.refCnt());
            // A second consumer can still decode exactly the same packet.
            assertEquals(update, ServerProfileWire.decode("tropimon:update_town_packet", input));
        } finally { input.release(); }
    }

    @Test void readsCompressedBatchesAndProfileRanks() {
        ByteBuf town = townPacket(), raw = Unpooled.buffer();
        putVarInt(raw, 1);
        raw.writeBytes(town);
        ByteBuf payload = compress(raw);
        try {
            var update = (ServerProfileState.Towns) ServerProfileWire.decode("tropimon:update_towns", payload);
            assertTrue(update.batch());
            assertEquals(TOWN, update.towns().getFirst().id());
        } finally { payload.release(); town.release(); raw.release(); }
        for (String rank : List.of("GUIDE", "MODERATOR", "STAFF", "RESPONSABLE", "ADMIN", "MASTER", "PLAYER")) {
            raw = Unpooled.buffer();
            putString(raw, "{\"uuid\":\"" + PLAYER + "\",\"rankManager\":{\"playerRanks\":[\"" + rank + "\"]}}");
            raw.writeInt(0); // remaining unrelated badge fields
            payload = compress(raw);
            try {
                var profile = (ServerProfileState.Profile) ServerProfileWire.decode("tropimon:update_player_data_packet", payload);
                assertEquals(PLAYER, profile.player());
                assertEquals(!rank.equals("MASTER") && !rank.equals("PLAYER"), profile.staff());
            } finally { raw.release(); payload.release(); }
        }
    }

    @Test void preservesIncrementalUpdatesNoTownFallbackAndSessionIsolation() {
        var state = new ServerProfileState();
        Object connection = new Object();
        state.session(connection);
        assertFalse(state.available());
        state.apply(new ServerProfileState.Region("spawn"));
        state.apply(new ServerProfileState.Towns(List.of(new ServerProfileState.Town(TOWN, "Polaris", Map.of(PLAYER, "Alex"))), true));
        state.apply(new ServerProfileState.Profile(PLAYER, true));
        assertEquals("Polaris", state.town(PLAYER));
        assertTrue(state.staff(PLAYER));
        assertEquals(PLAYER, state.playerId("aLEX").orElseThrow());
        state.session(connection);
        assertTrue(state.available());
        state.apply(new ServerProfileState.Towns(List.of(new ServerProfileState.Town(UUID.randomUUID(), "Other", Map.of())), true));
        assertEquals("Polaris", state.town(PLAYER)); // batch is not a full snapshot
        state.apply(new ServerProfileState.Towns(List.of(new ServerProfileState.Town(TOWN, "Polaris", Map.of())), false));
        assertTrue(state.available());
        assertEquals("", state.town(PLAYER)); // valid absence != unavailable
        assertTrue(state.playerId("Alex").isEmpty());
        state.session(null);
        assertFalse(state.available());
        assertFalse(state.staff(PLAYER));
        state.session(new Object());
        assertFalse(state.available());
    }

    @Test void regionChangesAndDeletionsDoNotLeaveStaleMembership() {
        var state = new ServerProfileState();
        state.session(new Object());
        state.apply(new ServerProfileState.Region("spawn"));
        state.apply(new ServerProfileState.Towns(List.of(new ServerProfileState.Town(TOWN, "Polaris", Map.of(PLAYER, "Alex"))), true));
        state.apply(new ServerProfileState.Delete(TOWN));
        assertFalse(state.available());
        state.apply(new ServerProfileState.Towns(List.of(new ServerProfileState.Town(TOWN, "Polaris", Map.of(PLAYER, "Alex"))), true));
        state.apply(new ServerProfileState.Region("other"));
        state.apply(new ServerProfileState.Towns(List.of(), true));
        assertFalse(state.available());
        assertTrue(state.playerId("Alex").isEmpty());
    }

    @Test void unknownTruncatedAndOversizedPacketsCannotPolluteStateOrAllocateWithoutBound() {
        ByteBuf input = Unpooled.buffer();
        try {
            assertNull(ServerProfileWire.decode("other:packet", input));
            assertEquals(0, input.readerIndex());
            assertThrows(RuntimeException.class, () -> ServerProfileWire.decode("tropimon:update_town_packet", input));
            putVarInt(input, 4);
            input.writeInt(ServerProfileWire.MAX_RAW_BYTES + 1);
            assertThrows(IllegalArgumentException.class, () -> ServerProfileWire.decode("tropimon:update_towns", input));
            assertEquals(0, input.readerIndex());
            input.clear();
            putVarInt(input, ServerProfileWire.MAX_COMPRESSED_BYTES + 1);
            assertThrows(IllegalArgumentException.class, () -> ServerProfileWire.decode("tropimon:update_towns", input));
        } finally { input.release(); }
    }

    @Test void playerProfileContainsTownEvenWhenRegionalTownListDoesNot() {
        var state = new ServerProfileState();
        state.session(new Object());
        state.apply(new ServerProfileState.Towns(List.of(new ServerProfileState.Town(UUID.randomUUID(), "Other", Map.of())), true));
        assertFalse(state.lookup(PLAYER).known(), "a partial list must not say No town");
        ByteBuf raw = Unpooled.buffer();
        putString(raw, "{\"uuid\":\"" + PLAYER + "\",\"townInfo\":{\"cityId\":\"" + TOWN
                + "\",\"cityName\":\"Polaris\"},\"rankManager\":{\"playerRanks\":[\"MASTER\"]}}");
        ByteBuf payload = compress(raw);
        try {
            state.apply(ServerProfileWire.decode("tropimon:update_player_data_packet", payload));
            assertTrue(state.lookup(PLAYER).known());
            assertEquals("Polaris", state.lookup(PLAYER).name());
            state.apply(new ServerProfileState.Region("another-region"));
            state.apply(new ServerProfileState.Towns(List.of(), true));
            assertEquals("Polaris", state.lookup(PLAYER).name());
            state.apply(new ServerProfileState.Profile(PLAYER, true)); // incomplete profile refresh
            assertEquals("Polaris", state.lookup(PLAYER).name());
            state.session(null);
            assertFalse(state.lookup(PLAYER).known());
        } finally { payload.release(); raw.release(); }
    }

    @Test void onlyExplicitEmptyMembershipMeansNoTown() {
        for (String info : List.of("", ",\"townInfo\":null", ",\"townInfo\":{}",
                ",\"townInfo\":{\"cityId\":null,\"cityName\":null}",
                ",\"townInfo\":{\"cityId\":\"" + TOWN + "\"}")) {
            ByteBuf raw = Unpooled.buffer();
            putString(raw, "{\"uuid\":\"" + PLAYER + "\"" + info + "}");
            ByteBuf payload = compress(raw);
            try {
                var state = new ServerProfileState();
                state.apply(ServerProfileWire.decode("tropimon:update_player_data_packet", payload));
                boolean noTown = info.equals(",\"townInfo\":{}") || info.contains("\"cityId\":null");
                assertEquals(noTown, state.lookup(PLAYER).known(), info);
                assertEquals("", state.lookup(PLAYER).name());
                if (info.contains(TOWN.toString())) {
                    state.apply(new ServerProfileState.Towns(List.of(new ServerProfileState.Town(TOWN, "Polaris", Map.of())), false));
                    assertEquals("Polaris", state.lookup(PLAYER).name());
                }
            } finally { payload.release(); raw.release(); }
        }
    }

    @Test void batchesBeforeRegionInfoRemainIncremental() {
        var state = new ServerProfileState();
        state.apply(new ServerProfileState.Towns(List.of(new ServerProfileState.Town(TOWN, "Polaris", Map.of(PLAYER, "Alex"))), true));
        state.apply(new ServerProfileState.Towns(List.of(new ServerProfileState.Town(UUID.randomUUID(), "Other", Map.of())), true));
        assertEquals("Polaris", state.lookup(PLAYER).name());
    }

    @Test void profileRemainsAuthoritativeThroughTownListUpdatesAndLeavingTown() {
        var state = new ServerProfileState();
        Object session = new Object();
        state.session(session);
        state.apply(new ServerProfileState.Towns(List.of(new ServerProfileState.Town(
                UUID.randomUUID(), "Old town", Map.of(PLAYER, "Alex"))), true));
        assertFalse(state.lookup(PLAYER).fromProfile());
        state.apply(new ServerProfileState.Profile(PLAYER, false,
                new ServerProfileState.Membership(true, TOWN, "Polaris")));
        assertEquals(new ServerProfileState.TownLookup(true, "Polaris", true), state.lookup(PLAYER));
        state.apply(new ServerProfileState.Towns(List.of(), true));
        assertEquals("Polaris", state.lookup(PLAYER).name());

        state.apply(new ServerProfileState.Profile(PLAYER, false,
                new ServerProfileState.Membership(true, null, "")));
        assertEquals(new ServerProfileState.TownLookup(true, "", true), state.lookup(PLAYER));
        state.apply(new ServerProfileState.Towns(List.of(new ServerProfileState.Town(
                TOWN, "Polaris", Map.of(PLAYER, "Alex"))), false));
        assertEquals("", state.lookup(PLAYER).name(), "an old citizen list cannot override leaving a town");

        state.session(new Object());
        assertEquals(new ServerProfileState.TownLookup(false, "", false), state.lookup(PLAYER));
    }

    @Test void pendingProfileNameResolvesByCityIdNotByGuessingTheCitizenList() {
        var state = new ServerProfileState();
        state.apply(new ServerProfileState.Towns(List.of(new ServerProfileState.Town(
                UUID.randomUUID(), "Wrong town", Map.of(PLAYER, "Alex"))), true));
        state.apply(new ServerProfileState.Profile(PLAYER, false,
                new ServerProfileState.Membership(true, TOWN, "")));
        assertEquals(new ServerProfileState.TownLookup(false, "", true), state.lookup(PLAYER));
        state.apply(new ServerProfileState.Towns(List.of(new ServerProfileState.Town(
                TOWN, "Polaris", Map.of())), false));
        assertEquals(new ServerProfileState.TownLookup(true, "Polaris", true), state.lookup(PLAYER));
    }

    @Test void profileRevisionChangesOnUpdatesAndDisconnectNotOnReads() {
        var state = new ServerProfileState();
        Object session = new Object();
        state.session(session);
        long connected = state.revision();
        for (int tick = 0; tick < 100; tick++) {
            state.session(session);
            state.lookup(PLAYER);
        }
        assertEquals(connected, state.revision());
        state.apply(new ServerProfileState.Profile(PLAYER, false,
                new ServerProfileState.Membership(true, TOWN, "Polaris")));
        assertNotEquals(connected, state.revision());
        long updated = state.revision();
        state.session(null);
        assertNotEquals(updated, state.revision());
        assertFalse(state.lookup(PLAYER).known());
    }

    @Test void frameObservationSurvivesCodecConsumptionAndRejectsUnrelatedData() {
        ByteBuf body = townPacket(), frame = Unpooled.buffer();
        frame.writeInt(0x12345678); // a nonzero reader offset, as with a sliced network frame
        frame.readerIndex(4);
        putVarInt(frame, 25);
        putString(frame, "tropimon:update_town_packet");
        frame.writeBytes(body);
        try {
            int reader = frame.readerIndex(), writer = frame.writerIndex(), refs = frame.refCnt();
            var observed = ServerProfileWire.inspectFrame(frame);
            assertNotNull(observed);
            assertNull(observed.error());
            assertEquals(reader, frame.readerIndex());
            assertEquals(writer, frame.writerIndex());
            assertEquals(refs, frame.refCnt());
            frame.setZero(0, writer).readerIndex(writer); // no dependence on the buffer after decoding
            assertEquals("Polaris", ((ServerProfileState.Towns) observed.update()).towns().getFirst().name());
            assertNull(ServerProfileWire.inspectFrame(frame));
            frame.clear().writeInt(-1);
            assertNull(ServerProfileWire.inspectFrame(frame));
            frame.clear().writeByte(1).writeByte(127);
            assertNull(ServerProfileWire.inspectFrame(frame));
        } finally { body.release(); frame.release(); }
    }

    private static ByteBuf townPacket() {
        ByteBuf input = Unpooled.buffer();
        input.writeLong(TOWN.getMostSignificantBits()).writeLong(TOWN.getLeastSignificantBits());
        putString(input, "Polaris");
        input.writeBoolean(true);
        putString(input, "spawn");
        input.writeBoolean(false);
        putVarInt(input, 1);
        input.writeInt(-7).writeInt(42);
        putVarInt(input, 1);
        putString(input, "Alex");
        input.writeLong(PLAYER.getMostSignificantBits()).writeLong(PLAYER.getLeastSignificantBits());
        putVarInt(input, 1);
        input.writeBoolean(true);
        return input;
    }

    private static ByteBuf compress(ByteBuf raw) {
        byte[] bytes = new byte[raw.readableBytes()];
        raw.getBytes(raw.readerIndex(), bytes);
        ZstdCompressor compressor = new ZstdCompressor();
        byte[] output = new byte[compressor.maxCompressedLength(bytes.length)];
        int count = compressor.compress(bytes, 0, bytes.length, output, 0, output.length);
        ByteBuf payload = Unpooled.buffer();
        putVarInt(payload, count + 4);
        payload.writeInt(bytes.length).writeBytes(output, 0, count);
        return payload;
    }
    private static void putString(ByteBuf buffer, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        putVarInt(buffer, bytes.length);
        buffer.writeBytes(bytes);
    }
    private static void putVarInt(ByteBuf buffer, int value) {
        while ((value & ~127) != 0) { buffer.writeByte((value & 127) | 128); value >>>= 7; }
        buffer.writeByte(value);
    }
}
