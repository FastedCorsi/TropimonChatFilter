package fr.tropimon.chatfilter;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.yggdrasil.ProfileResult;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** Index local des skins déjà présents dans la liste des joueurs, rafraîchi au plus une fois/seconde. */
public final class PlayerHeadCache {
    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_]{2,16}");
    private static final long FAILED_LOOKUP_RETRY_MILLIS = 60_000L;
    private static final int REMOTE_CACHE_LIMIT = 512;
    private static final int MAX_CONCURRENT_LOOKUPS = 4;
    private static final Map<String, PlayerListEntry> PLAYERS = new HashMap<>();
    private static final Map<String, Identifier> REMOTE_TEXTURES = new ConcurrentHashMap<>();
    private static final Map<String, Long> RETRY_AFTER = new ConcurrentHashMap<>();
    private static final Set<String> LOOKUPS_IN_FLIGHT = ConcurrentHashMap.newKeySet();
    private static ClientPlayNetworkHandler handler;
    private static long nextRefresh;

    private PlayerHeadCache() {
    }

    public static Identifier findTexture(String name) {
        refresh();
        if (name == null) {
            return null;
        }
        String key = name.toLowerCase(Locale.ROOT);
        PlayerListEntry localEntry = PLAYERS.get(key);
        if (localEntry != null) {
            return localEntry.getSkinTextures().texture();
        }
        Identifier remoteTexture = REMOTE_TEXTURES.get(key);
        if (remoteTexture != null) {
            return remoteTexture;
        }
        requestRemoteTexture(name, key);
        return null;
    }

    private static void requestRemoteTexture(String name, String key) {
        if (!VALID_NAME.matcher(name).matches()
                || System.currentTimeMillis() < RETRY_AFTER.getOrDefault(key, 0L)
                || LOOKUPS_IN_FLIGHT.size() >= MAX_CONCURRENT_LOOKUPS
                || !LOOKUPS_IN_FLIGHT.add(key)) {
            return;
        }
        // Resolve our immutable identifier on the client thread, never inspect another mod on a worker.
        var playerId = TropimonTownProfile.playerUuid(name);
        if (playerId.isEmpty()) {
            failedLookup(key);
            return;
        }
        Util.getIoWorkerExecutor().execute(() -> resolveRemoteTexture(name, key, playerId.get()));
    }

    private static void resolveRemoteTexture(String name, String key, java.util.UUID playerId) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            ProfileResult result = client.getSessionService().fetchProfile(playerId, true);
            GameProfile profile = result == null
                    ? new GameProfile(playerId, name) : result.profile();
            client.getSkinProvider().fetchSkinTextures(profile).whenComplete((textures, error) -> {
                if (error == null && textures != null) {
                    if (REMOTE_TEXTURES.size() >= REMOTE_CACHE_LIMIT) {
                        REMOTE_TEXTURES.clear();
                    }
                    REMOTE_TEXTURES.put(key, textures.texture());
                    RETRY_AFTER.remove(key);
                    LOOKUPS_IN_FLIGHT.remove(key);
                } else {
                    failedLookup(key);
                }
            });
        } catch (RuntimeException ignored) {
            failedLookup(key);
        }
    }

    private static void failedLookup(String key) {
        if (RETRY_AFTER.size() >= REMOTE_CACHE_LIMIT) {
            long now = System.currentTimeMillis();
            RETRY_AFTER.entrySet().removeIf(entry -> entry.getValue() <= now);
            if (RETRY_AFTER.size() >= REMOTE_CACHE_LIMIT) {
                RETRY_AFTER.clear();
            }
        }
        RETRY_AFTER.put(key, System.currentTimeMillis() + FAILED_LOOKUP_RETRY_MILLIS);
        LOOKUPS_IN_FLIGHT.remove(key);
    }

    private static void refresh() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayNetworkHandler current = client.getNetworkHandler();
        long now = System.currentTimeMillis();
        if (current == handler && now < nextRefresh) {
            return;
        }
        handler = current;
        nextRefresh = now + 1_000L;
        PLAYERS.clear();
        if (current != null) {
            for (PlayerListEntry entry : current.getPlayerList()) {
                PLAYERS.put(entry.getProfile().getName().toLowerCase(Locale.ROOT), entry);
            }
        }
    }
}
