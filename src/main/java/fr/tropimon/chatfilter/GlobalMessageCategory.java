package fr.tropimon.chatfilter;

import net.minecraft.text.Text;

public enum GlobalMessageCategory {
    SYSTEM("tropimon_chat_filter.filter.system"),
    STAFF("tropimon_chat_filter.filter.staff"),
    UNRANKED("tropimon_chat_filter.filter.unranked"),
    CHAMPION("tropimon_chat_filter.filter.champion"),
    SUPER("tropimon_chat_filter.filter.super"),
    HYPER("tropimon_chat_filter.filter.hyper"),
    MASTER("tropimon_chat_filter.filter.master");

    private final String translationKey;

    GlobalMessageCategory(String translationKey) {
        this.translationKey = translationKey;
    }

    public Text label() {
        return Text.translatable(translationKey);
    }
}
