package fr.tropimon.chatfilter;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;

/** Compte uniquement les messages du canal Staff officiel. */
public final class StaffChatNotifications {
    private static int unread;
    private static Object connection;

    private StaffChatNotifications() {
    }

    public static void observe(MessageAnalysis message) {
        refreshSession();
        if (!message.staff()) {
            return;
        }
        StaffStatus.confirmFromStaffMessage();
        MinecraftClient client = MinecraftClient.getInstance();
        String local = client.player == null ? null : client.player.getGameProfile().getName();
        boolean sentByLocal = message.sentByLocal(local);
        if (sentByLocal || ChatFilterController.selected() == ChatChannel.STAFF
                && client.currentScreen instanceof ChatScreen) {
            return;
        }
        unread = Math.min(99, unread + 1);
    }

    public static int unread() {
        refreshSession();
        return unread;
    }

    public static void markRead() {
        refreshSession();
        unread = 0;
    }

    private static void refreshSession() {
        Object current = MinecraftClient.getInstance().getNetworkHandler();
        if (current != connection) {
            connection = current;
            unread = 0;
        }
    }
}
