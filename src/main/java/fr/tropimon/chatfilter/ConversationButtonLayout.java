package fr.tropimon.chatfilter;

/** Géométrie calculée d'un onglet privé, gardée hors du package mixin. */
public record ConversationButtonLayout(
        PrivateChatManager.ConversationTab tab, int x, int width, String label) {
}
