package fr.tropimon.chatfilter;

import net.minecraft.text.Text;

public enum ChatChannel {
    ALL("tropimon_chat_filter.tab.all"),
    TOWN("tropimon_chat_filter.tab.town"),
    STAFF("tropimon_chat_filter.tab.staff"),
    GROUP("tropimon_chat_filter.tab.group"),
    PRIVATE("tropimon_chat_filter.tab.private");

    private final String translationKey;

    ChatChannel(String translationKey) {
        this.translationKey = translationKey;
    }

    public Text label() {
        return Text.translatable(translationKey);
    }
}
