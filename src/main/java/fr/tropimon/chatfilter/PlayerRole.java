package fr.tropimon.chatfilter;

import net.minecraft.text.Text;

enum PlayerRole {
    UNRANKED("tropimon_chat_filter.role.unranked", 0xFFAAAAAA, 0),
    CHAMPION("tropimon_chat_filter.role.champion", 0xFFFFAA00, 1),
    SUPER("tropimon_chat_filter.role.super", 0xFF55FF55, 2),
    HYPER("tropimon_chat_filter.role.hyper", 0xFF5555FF, 3),
    MASTER("tropimon_chat_filter.role.master", 0xFFFF55FF, 4),
    STAFF("tropimon_chat_filter.role.staff", 0xFF55FFFF, 5),
    GUIDE("tropimon_chat_filter.role.guide", 0xFF55FF55, 6),
    MODERATOR("tropimon_chat_filter.role.moderator", 0xFF55FFFF, 7),
    RESPONSABLE("tropimon_chat_filter.role.responsable", 0xFFFFAA00, 8),
    ADMIN("tropimon_chat_filter.role.admin", 0xFFFF5555, 9);

    private final String translationKey;
    private final int color;
    private final int priority;

    PlayerRole(String translationKey, int color, int priority) {
        this.translationKey = translationKey;
        this.color = color;
        this.priority = priority;
    }

    Text label() {
        return Text.translatable(translationKey);
    }

    int color() {
        return color;
    }

    int priority() {
        return priority;
    }
}
