package fr.tropimon.chatfilter;

import java.util.ArrayDeque;
import java.util.Deque;

/** Petite file bornée : une seule demande est affichée, les suivantes attendent. */
final class TeleportRequestQueue {
    static final int MAX_REQUESTS = 8;
    static final long REQUEST_LIFETIME_MILLIS = 60_000L;
    private final Deque<Entry> entries = new ArrayDeque<>();

    void offer(TeleportRequestParser.Request request, PlayerRoleResolver.Resolved role, long now) {
        expire(now);
        if (entries.size() >= MAX_REQUESTS) {
            entries.removeFirst();
        }
        entries.addLast(new Entry(request.player(), request.direction(), role.role(), role.color(),
                now + REQUEST_LIFETIME_MILLIS));
    }

    boolean attachActions(String acceptCommand, String declineCommand, long now) {
        expire(now);
        if (acceptCommand == null || declineCommand == null) {
            return false;
        }
        for (Entry entry : entries) {
            if (!entry.ready()) {
                entry.acceptCommand = acceptCommand;
                entry.declineCommand = declineCommand;
                return true;
            }
        }
        return false;
    }

    Entry current() {
        return entries.peekFirst();
    }

    int size() {
        return entries.size();
    }

    boolean resolveCurrent() {
        return entries.pollFirst() != null;
    }

    void clear() {
        entries.clear();
    }

    void expire(long now) {
        int count = entries.size();
        for (int index = 0; index < count; index++) {
            Entry entry = entries.removeFirst();
            if (entry.expiresAt > now) {
                entries.addLast(entry);
            }
        }
    }

    static final class Entry {
        private final String player;
        private final TeleportRequestParser.Direction direction;
        private final PlayerRole role;
        private final int roleColor;
        private long expiresAt;
        private String acceptCommand;
        private String declineCommand;

        private Entry(String player, TeleportRequestParser.Direction direction, PlayerRole role,
                      int roleColor, long expiresAt) {
            this.player = player;
            this.direction = direction;
            this.role = role;
            this.roleColor = roleColor;
            this.expiresAt = expiresAt;
        }

        String player() {
            return player;
        }

        TeleportRequestParser.Direction direction() {
            return direction;
        }

        PlayerRole role() {
            return role;
        }

        int roleColor() {
            return roleColor;
        }

        long expiresAt() {
            return expiresAt;
        }

        String command(boolean accept) {
            return accept ? acceptCommand : declineCommand;
        }

        boolean ready() {
            return acceptCommand != null && declineCommand != null;
        }
    }
}
