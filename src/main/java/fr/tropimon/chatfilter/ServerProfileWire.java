package fr.tropimon.chatfilter;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.airlift.compress.zstd.ZstdDecompressor;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Minimal, read-only decoder. No payload registrations or foreign runtime models. */
final class ServerProfileWire {
    static final int MAX_COMPRESSED_BYTES = 1_048_576;
    static final int MAX_RAW_BYTES = 16_777_216;
    private static final Set<String> STAFF = Set.of("GUIDE", "MODERATOR", "STAFF", "RESPONSABLE", "ADMIN");

    private ServerProfileWire() { }

    static boolean supports(String id) {
        return switch (id) {
            case "tropimon:update_towns", "tropimon:update_town_packet", "tropimon:delete_town_packet",
                    "tropimon:update_player_data_packet", "tropimon:set_current_server_packet" -> true;
            default -> false;
        };
    }

    /** Inspect before vanilla/third-party codecs can consume or transform the frame. */
    static Observation inspectFrame(ByteBuf frame) {
        // Cheap, allocation-free rejection for ordinary movement/chunk/chat packets.
        int position = frame.readerIndex(), end = frame.writerIndex();
        int bytes = 0, part;
        do {
            if (position >= end || bytes++ == 5) return null;
            part = frame.getUnsignedByte(position++);
        } while ((part & 128) != 0);
        if (position >= end) return null;
        int length = frame.getUnsignedByte(position++); // our short ASCII IDs use one VarInt byte
        if (length < 8 || length > 64 || end - position < length) return null;
        String prefix = "tropimon:";
        for (int index = 0; index < prefix.length(); index++) {
            if (frame.getByte(position + index) != prefix.charAt(index)) return null;
        }
        String id = frame.toString(position, length, StandardCharsets.UTF_8);
        if (!supports(id)) return null;
        ByteBuf input = frame.duplicate().readerIndex(position + length);
        try { return new Observation(id, decode(id, input), null); }
        catch (RuntimeException invalid) { return new Observation(id, null, invalid); }
    }

    record Observation(String id, ServerProfileState.Update update, RuntimeException error) { }

    static ServerProfileState.Update decode(String id, ByteBuf source) {
        // The caller and all other mods retain their buffer indices and ownership.
        ByteBuf input = source.duplicate();
        return switch (id) {
            case "tropimon:update_towns" -> {
                ByteBuf raw = decompress(input);
                try {
                    int count = count(raw, ServerProfileState.MAX_TOWNS);
                    List<ServerProfileState.Town> towns = new ArrayList<>(count);
                    int citizens = 0;
                    for (int i = 0; i < count; i++) {
                        var town = town(raw);
                        citizens += town.citizens().size();
                        if (citizens > ServerProfileState.MAX_CITIZENS) throw invalid();
                        towns.add(town);
                    }
                    if (raw.isReadable()) throw invalid();
                    yield new ServerProfileState.Towns(towns, true);
                } finally { raw.release(); }
            }
            case "tropimon:update_town_packet" -> new ServerProfileState.Towns(List.of(town(input)), false);
            case "tropimon:delete_town_packet" -> new ServerProfileState.Delete(uuid(input));
            case "tropimon:set_current_server_packet" -> new ServerProfileState.Region(json(input).get("name").getAsString());
            case "tropimon:update_player_data_packet" -> {
                ByteBuf raw = decompress(input);
                try {
                    JsonObject profile = json(raw);
                    UUID player = UUID.fromString(profile.get("uuid").getAsString());
                    boolean staff = false;
                    var manager = profile.getAsJsonObject("rankManager");
                    if (manager != null && manager.has("playerRanks")) {
                        for (var rank : manager.getAsJsonArray("playerRanks")) {
                            staff |= STAFF.contains(rank.getAsString());
                        }
                    }
                    // Badge records follow the JSON; they are not used by this mod.
                    yield new ServerProfileState.Profile(player, staff, membership(profile));
                } finally { raw.release(); }
            }
            default -> null;
        };
    }

    private static ServerProfileState.Membership membership(JsonObject profile) {
        // The JSON omits null fields: a present, empty townInfo is a confirmed absence.
        // Missing townInfo altogether, however, is not enough to erase a known membership.
        if (!profile.has("townInfo") || !profile.get("townInfo").isJsonObject()) {
            return ServerProfileState.Membership.UNKNOWN;
        }
        JsonObject town = profile.getAsJsonObject("townInfo");
        UUID id = town.has("cityId") && !town.get("cityId").isJsonNull()
                ? UUID.fromString(town.get("cityId").getAsString()) : null;
        String name = town.has("cityName") && !town.get("cityName").isJsonNull()
                ? town.get("cityName").getAsString().strip() : "";
        return new ServerProfileState.Membership(true, id, name);
    }

    private static ServerProfileState.Town town(ByteBuf input) {
        UUID id = uuid(input);
        String name = string(input, 32767);
        if (input.readBoolean()) string(input, 32767);
        input.readBoolean();
        int chunks = count(input, MAX_RAW_BYTES / 8);
        input.skipBytes(Math.multiplyExact(chunks, 8));
        int count = count(input, ServerProfileState.MAX_CITIZENS);
        var citizens = new HashMap<UUID, String>();
        for (int i = 0; i < count; i++) {
            String player = string(input, 32767);
            UUID playerId = uuid(input);
            varInt(input); // town role (not needed for membership)
            input.readBoolean(); // online, not a skin-cache invalidation
            citizens.put(playerId, player);
        }
        return new ServerProfileState.Town(id, name, citizens);
    }

    private static ByteBuf decompress(ByteBuf input) {
        int size = count(input, MAX_COMPRESSED_BYTES);
        if (size < 4 || size > input.readableBytes()) throw invalid();
        int rawSize = input.readInt();
        if (rawSize < 0 || rawSize > MAX_RAW_BYTES) throw invalid();
        byte[] compressed = new byte[size - 4];
        input.readBytes(compressed);
        byte[] raw = new byte[rawSize];
        int actual = new ZstdDecompressor().decompress(compressed, 0, compressed.length, raw, 0, rawSize);
        if (actual != rawSize) throw invalid();
        return Unpooled.wrappedBuffer(raw);
    }

    private static JsonObject json(ByteBuf input) {
        return JsonParser.parseString(string(input, 32767)).getAsJsonObject();
    }

    static String string(ByteBuf input, int maxCharacters) {
        int length = count(input, maxCharacters * 3);
        if (length > input.readableBytes()) throw invalid();
        String value = input.toString(input.readerIndex(), length, StandardCharsets.UTF_8);
        input.skipBytes(length);
        if (value.length() > maxCharacters) throw invalid();
        return value;
    }

    private static UUID uuid(ByteBuf input) { return new UUID(input.readLong(), input.readLong()); }
    private static int count(ByteBuf input, int max) {
        int count = varInt(input);
        if (count < 0 || count > max) throw invalid();
        return count;
    }
    static int varInt(ByteBuf input) {
        int result = 0;
        for (int i = 0; i < 5; i++) {
            int value = input.readUnsignedByte();
            result |= (value & 127) << (i * 7);
            if ((value & 128) == 0) return result;
        }
        throw invalid();
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid profile payload"); }
}
