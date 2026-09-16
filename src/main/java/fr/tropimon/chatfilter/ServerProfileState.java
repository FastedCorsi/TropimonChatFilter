package fr.tropimon.chatfilter;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Our own bounded view of the public S2C data, not a bridge to another mod. */
final class ServerProfileState {
    static final int MAX_TOWNS = 4096;
    static final int MAX_CITIZENS = 65536;
    private Object session;
    private long revision;
    private String region;
    private String townsRegion;
    private Map<UUID, Town> towns = Map.of();
    private Map<UUID, String> playerTowns = Map.of();
    private Map<String, UUID> playerIds = Map.of();
    private UUID profilePlayer;
    private boolean profileStaff;
    private Membership profileTown = Membership.UNKNOWN;

    record Town(UUID id, String name, Map<UUID, String> citizens) {
        Town { citizens = Map.copyOf(citizens); }
    }
    sealed interface Update permits Towns, Delete, Profile, Region { }
    record Towns(List<Town> towns, boolean batch) implements Update {
        Towns { towns = List.copyOf(towns); }
    }
    record Delete(UUID id) implements Update { }
    record Membership(boolean known, UUID id, String name) {
        static final Membership UNKNOWN = new Membership(false, null, "");
    }
    record Profile(UUID player, boolean staff, Membership town) implements Update {
        Profile(UUID player, boolean staff) { this(player, staff, Membership.UNKNOWN); }
    }
    record Region(String name) implements Update { }
    record TownLookup(boolean known, String name, boolean fromProfile) { }

    long revision() { return revision; }

    void session(Object current) {
        if (session == current) return;
        session = current;
        revision++;
        towns = Map.of();
        playerTowns = Map.of();
        playerIds = Map.of();
        region = townsRegion = null;
        profilePlayer = null;
        profileStaff = false;
        profileTown = Membership.UNKNOWN;
    }

    void apply(Update update) {
        if (update instanceof Region value) {
            region = value.name();
        } else if (update instanceof Profile value) {
            if (!value.player().equals(profilePlayer)) profileTown = Membership.UNKNOWN;
            profilePlayer = value.player();
            profileStaff = value.staff();
            if (value.town().known()) profileTown = value.town();
        } else {
            Map<UUID, Town> next = new HashMap<>(towns);
            String nextRegion = townsRegion;
            if (update instanceof Towns value) {
                // Town batches are incremental within a region, not full replacements.
                if (value.batch() && (townsRegion == null ? region != null
                        : region == null || !townsRegion.equalsIgnoreCase(region))) {
                    next.clear();
                }
                if (value.batch()) nextRegion = region;
                for (Town town : value.towns()) next.put(town.id(), town);
            } else if (update instanceof Delete value) {
                next.remove(value.id());
            }
            if (next.size() > MAX_TOWNS) throw new IllegalArgumentException("town limit");
            Map<UUID, String> townIndex = new HashMap<>();
            Map<String, UUID> nameIndex = new HashMap<>();
            int citizens = 0;
            for (Town town : next.values()) {
                citizens += town.citizens().size();
                if (citizens > MAX_CITIZENS) throw new IllegalArgumentException("citizen limit");
                for (var citizen : town.citizens().entrySet()) {
                    townIndex.putIfAbsent(citizen.getKey(), town.name().strip());
                    nameIndex.putIfAbsent(citizen.getValue().strip().toLowerCase(Locale.ROOT), citizen.getKey());
                }
            }
            // Publish only a complete, validated update.
            towns = Map.copyOf(next);
            townsRegion = nextRegion;
            playerTowns = Map.copyOf(townIndex);
            playerIds = Map.copyOf(nameIndex);
        }
        revision++;
    }

    boolean available() { return !towns.isEmpty(); }
    String town(UUID player) { return playerTowns.getOrDefault(player, ""); }
    TownLookup lookup(UUID player) {
        if (player != null && player.equals(profilePlayer) && profileTown.known()) {
            if (!profileTown.name().isBlank()) return new TownLookup(true, profileTown.name(), true);
            if (profileTown.id() == null) return new TownLookup(true, "", true);
            Town town = towns.get(profileTown.id());
            return town == null ? new TownLookup(false, "", true) : new TownLookup(true, town.name(), true);
        }
        // A town list is incremental/region-specific. Missing membership is not proof of no town.
        String name = town(player);
        return new TownLookup(!name.isBlank(), name, false);
    }
    Optional<UUID> playerId(String name) {
        return name == null ? Optional.empty()
                : Optional.ofNullable(playerIds.get(name.toLowerCase(Locale.ROOT)));
    }
    boolean staff(UUID player) { return player != null && player.equals(profilePlayer) && profileStaff; }
}
