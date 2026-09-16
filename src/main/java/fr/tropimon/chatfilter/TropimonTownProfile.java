package fr.tropimon.chatfilter;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.ClientConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;

/** Client-thread state exclusively owned by Chat Filter. Network observation never sends a packet. */
public final class TropimonTownProfile {
    private static final Logger LOGGER = LoggerFactory.getLogger("tropimon_chat_filter");
    private static final ServerProfileState STATE = new ServerProfileState();
    private static Object warnedConnection;

    private TropimonTownProfile() { }

    public static Lookup lookup(MinecraftClient client) {
        refreshSession();
        if (client.player == null) return new Lookup(false, "", false);
        var town = STATE.lookup(client.player.getUuid());
        return new Lookup(town.known(), town.name(), town.fromProfile());
    }

    static long revision() {
        refreshSession();
        return STATE.revision();
    }

    public static Optional<UUID> playerUuid(String name) {
        refreshSession();
        return STATE.playerId(name);
    }

    static boolean isStaff(UUID player) {
        refreshSession();
        return STATE.staff(player);
    }

    public static void refreshSession() {
        ClientConnection current = connection();
        STATE.session(current);
        if (current == null) warnedConnection = null;
    }

    private static ClientConnection connection() {
        var handler = MinecraftClient.getInstance().getNetworkHandler();
        return handler == null ? null : handler.getConnection();
    }

    /** Immutable observation, no ByteBuf retained across vanilla decode or the client queue. */
    public static Pending inspectFrame(ByteBuf frame) {
        var observation = ServerProfileWire.inspectFrame(frame);
        return observation == null ? null : new Pending(observation);
    }

    public static final class Pending {
        private final ServerProfileWire.Observation observation;
        private Pending(ServerProfileWire.Observation observation) { this.observation = observation; }

        public void accept(ClientConnection source, String decodedPayloadId) {
            if (source == null || !observation.id().equals(decodedPayloadId)) return;
            MinecraftClient.getInstance().execute(() -> {
                if (source != connection()) return;
                refreshSession();
                if (observation.error() != null) { warn(source, observation.id(), observation.error()); return; }
                try {
                    STATE.apply(observation.update());
                    TownChatNotifications.refreshFromProfile();
                }
                catch (RuntimeException invalid) { warn(source, observation.id(), invalid); }
            });
        }
    }

    private static void warn(Object source, String payloadId, RuntimeException invalid) {
        if (warnedConnection == source) return;
        warnedConnection = source;
        LOGGER.warn("Cannot read {}: retaining town fallback (no packet intercepted)", payloadId, invalid);
    }

    public record Lookup(boolean available, String townName, boolean fromProfile) {
        public boolean hasTown() { return available && !townName.isBlank(); }
    }
}
