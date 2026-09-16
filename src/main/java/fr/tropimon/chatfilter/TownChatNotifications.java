package fr.tropimon.chatfilter;

import com.google.gson.JsonObject;
import fr.tropimon.chatfilter.mixin.ChatHudAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.screen.ChatScreen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Suit les messages de ville non lus sans les recopier dans le chat global. */
public final class TownChatNotifications {
    private static final int MAX_KNOWN_PLAYERS = 32;
    private static final Path FILE = JsonConfigStore.resolve(
            "tropimon-chat-filter-towns.json");
    private static final Map<String, String> KNOWN_TOWNS = new HashMap<>();
    private static int unread;
    private static Object connection;
    private static String playerKey;
    private static String townName;
    private static boolean noTown;
    private static boolean confirmedThisSession;
    private static boolean profileResolved;
    private static long lastProfileRevision = Long.MIN_VALUE;

    static {
        load();
    }

    private TownChatNotifications() {
    }

    public static void observe(MessageAnalysis message, boolean privateMessage) {
        refreshFromProfile();
        if (privateMessage) {
            return;
        }
        var detectedTown = message.townName();
        // Chat/history are fallbacks only; they cannot overwrite the player's own profile.
        if (!profileResolved) {
            detectedTown.ifPresent(TownChatNotifications::setTown);
            if (message.noTown()) {
                setNoTown();
            }
        }
        MinecraftClient client = MinecraftClient.getInstance();
        String localPlayer = client.player == null
                ? null : client.player.getGameProfile().getName();
        boolean sentByLocalPlayer = message.sentByLocal(localPlayer);
        if (sentByLocalPlayer) {
            return;
        }
        boolean townOpenAndVisible = ChatFilterController.selected() == ChatChannel.TOWN
                && client.currentScreen instanceof ChatScreen;
        if (townOpenAndVisible
                || detectedTown.isEmpty() && !message.town()) {
            return;
        }
        unread = Math.min(99, unread + 1);
    }

    public static int unread() {
        return unread;
    }

    public static String townName() {
        return townName;
    }

    public static boolean hasNoTown() {
        return noTown;
    }

    public static void refreshPlayer() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }
        String current = client.player.getGameProfile().getName().toLowerCase(Locale.ROOT);
        Object currentConnection = client.getNetworkHandler();
        if (current.equals(playerKey) && currentConnection == connection) {
            return;
        }
        connection = currentConnection;
        playerKey = current;
        townName = KNOWN_TOWNS.get(current);
        if (ChatMessageClassifier.isSelfDestination(townName)
                || townName != null && !ChatMessageClassifier.isPlausibleTownName(townName)) {
            townName = null;
            if (KNOWN_TOWNS.remove(current) != null) {
                save();
            }
        }
        noTown = false;
        confirmedThisSession = false;
        profileResolved = false;
        lastProfileRevision = Long.MIN_VALUE;
        unread = 0;
    }

    public static void detectFromHistory() {
        refreshFromProfile();
        if (profileResolved) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.inGameHud == null) {
            return;
        }
        String localPlayer = client.player.getGameProfile().getName();
        for (ChatHudLine line : ((ChatHudAccessor) client.inGameHud.getChatHud())
                .tropimonChatFilter$messages()) {
            MessageAnalysis analysis = MessageAnalysisCache.get(line.content());
            if (analysis.noTown()) {
                setNoTown();
                return;
            }
            if (analysis.privateMessage().isPresent()) {
                continue;
            }
            boolean sentByLocalPlayer = analysis.townSender()
                    .map(sender -> sender.equalsIgnoreCase(localPlayer))
                    .orElse(false);
            if (sentByLocalPlayer) {
                // Un retour de /msg sortant a le même séparateur que le chat de ville.
                continue;
            }
            var detectedTown = analysis.townName();
            if (detectedTown.isPresent()) {
                setTown(detectedTown.get());
                return;
            }
        }
    }

    public static void refreshFromProfile() {
        refreshPlayer();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        // The mod owns an indexed snapshot now: no reflection, polling delay or repeated scan.
        long revision = TropimonTownProfile.revision();
        if (revision == lastProfileRevision) return;
        lastProfileRevision = revision;
        TropimonTownProfile.Lookup lookup = TropimonTownProfile.lookup(client);
        profileResolved = lookup.fromProfile();
        if (!lookup.available()) {
            // A profile with a city UUID but a pending name is not a player without a town.
            if (profileResolved) noTown = false;
            return;
        }
        if (lookup.hasTown()) {
            setTown(lookup.townName(), true);
        } else {
            setNoTown();
        }
    }

    public static void markRead() {
        unread = 0;
    }

    private static void setTown(String detected) {
        setTown(detected, false);
    }

    private static void setTown(String detected, boolean serverConfirmed) {
        if (!serverConfirmed && (ChatMessageClassifier.isSelfDestination(detected)
                || !ChatMessageClassifier.isPlausibleTownName(detected))) {
            return;
        }
        noTown = false;
        confirmedThisSession = true;
        if (townName != null && detected.equalsIgnoreCase(townName)) {
            return;
        }
        townName = detected;
        if (playerKey != null) {
            KNOWN_TOWNS.put(playerKey, detected);
            save();
        }
    }

    private static void setNoTown() {
        noTown = true;
        confirmedThisSession = true;
        townName = null;
        unread = 0;
        if (playerKey != null && KNOWN_TOWNS.remove(playerKey) != null) {
            save();
        }
    }

    public static void rejectPrivateCounterpart(String name) {
        boolean sameName = townName != null && name != null
                && (townName.equalsIgnoreCase(name)
                || ChatMessageClassifier.extractTrailingPlayerName(townName)
                        .map(player -> player.equalsIgnoreCase(name))
                        .orElse(false));
        if (confirmedThisSession || !sameName) {
            return;
        }
        townName = null;
        if (playerKey != null && KNOWN_TOWNS.remove(playerKey) != null) {
            save();
        }
    }

    private static void load() {
        if (FILE == null || !Files.isRegularFile(FILE)) {
            return;
        }
        try {
            JsonObject json = JsonConfigStore.read(FILE);
            if (json != null) {
                for (var entry : json.entrySet()) {
                    if (KNOWN_TOWNS.size() >= MAX_KNOWN_PLAYERS
                            || !entry.getKey().matches("[A-Za-z0-9_]{2,16}")
                            || !entry.getValue().isJsonPrimitive()
                            || !entry.getValue().getAsJsonPrimitive().isString()) {
                        continue;
                    }
                    String town = entry.getValue().getAsString().strip();
                    if (ChatMessageClassifier.isPlausibleTownName(town)) {
                        KNOWN_TOWNS.put(entry.getKey().toLowerCase(Locale.ROOT), town);
                    }
                }
            }
        } catch (Exception ignored) {
            KNOWN_TOWNS.clear();
        }
    }

    private static void save() {
        if (FILE == null) {
            return;
        }
        JsonObject json = new JsonObject();
        KNOWN_TOWNS.forEach(json::addProperty);
        try {
            JsonConfigStore.write(FILE, json);
        } catch (IOException ignored) {
            // La détection continue de fonctionner pour la session courante.
        }
    }
}
