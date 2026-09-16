package fr.tropimon.chatfilter;

import java.util.Locale;
import java.util.Optional;

/** Mémorise le dernier canal réellement demandé au serveur pour supprimer les commandes redondantes. */
public final class ServerChannelState {
    private Object connection;
    private ChatChannel requested;

    public boolean shouldRequest(Object currentConnection, ChatChannel channel) {
        if (currentConnection == null || !isServerChannel(channel)) {
            return false;
        }
        resetForConnection(currentConnection);
        if (requested == channel) {
            return false;
        }
        requested = channel;
        return true;
    }

    public void observe(Object currentConnection, String rawCommand) {
        if (currentConnection == null) {
            return;
        }
        parseCommand(rawCommand).ifPresent(channel -> {
            resetForConnection(currentConnection);
            requested = channel;
        });
    }

    public static Optional<ChatChannel> parseCommand(String rawCommand) {
        String command = rawCommand == null ? "" : rawCommand.strip();
        if (command.startsWith("/")) {
            command = command.substring(1).stripLeading();
        }
        String[] parts = command.split("\\s+");
        if (parts.length != 2 || !parts[0].equalsIgnoreCase("chat")) {
            return Optional.empty();
        }
        return switch (parts[1].toUpperCase(Locale.ROOT)) {
            case "GLOBAL", "ALL" -> Optional.of(ChatChannel.ALL);
            case "TOWN", "VILLE" -> Optional.of(ChatChannel.TOWN);
            case "STAFF" -> Optional.of(ChatChannel.STAFF);
            default -> Optional.empty();
        };
    }

    private void resetForConnection(Object currentConnection) {
        if (connection != currentConnection) {
            connection = currentConnection;
            requested = null;
        }
    }

    private static boolean isServerChannel(ChatChannel channel) {
        return channel == ChatChannel.ALL || channel == ChatChannel.TOWN
                || channel == ChatChannel.STAFF;
    }
}
