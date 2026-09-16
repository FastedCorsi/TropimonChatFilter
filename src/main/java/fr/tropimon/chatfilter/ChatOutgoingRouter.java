package fr.tropimon.chatfilter;

public final class ChatOutgoingRouter {
    private static final String OBSOLETE_TOWN_COMMAND = "/tc ";
    private static final String PRIVATE_COMMAND = "/msg ";

    private ChatOutgoingRouter() {
    }

    public static String prepareInput(ChatChannel channel, String current) {
        String input = current == null ? "" : current;
        if (channel == ChatChannel.PRIVATE) {
            return addPrefix(input, PRIVATE_COMMAND);
        }
        return preparePublicInput(input);
    }

    public static String route(ChatChannel channel, String message, String privateTarget) {
        if (message == null || message.isBlank() || isExplicitCommand(message)) {
            return message;
        }
        return switch (channel) {
            case TOWN, STAFF, GROUP -> message;
            case PRIVATE -> privateTarget == null || privateTarget.isBlank()
                    ? PRIVATE_COMMAND + message
                    : PRIVATE_COMMAND + privateTarget + " " + message;
            case ALL -> message;
        };
    }

    public static boolean isExplicitCommand(String message) {
        return message != null && message.startsWith("/");
    }

    private static String addPrefix(String input, String prefix) {
        if (input.startsWith(prefix) || input.startsWith("/")) {
            return input;
        }
        return prefix + input;
    }

    private static String removeObsoleteTownPrefix(String input) {
        return input.startsWith(OBSOLETE_TOWN_COMMAND)
                ? input.substring(OBSOLETE_TOWN_COMMAND.length())
                : input;
    }

    private static String preparePublicInput(String input) {
        String normalized = input.stripLeading().toLowerCase(java.util.Locale.ROOT);
        if (normalized.equals("/msg") || normalized.startsWith("/msg ")
                || normalized.equals("/r") || normalized.startsWith("/r ")) {
            // Ne jamais risquer de publier le brouillon d'un MP dans un canal public.
            return "";
        }
        return removeObsoleteTownPrefix(input);
    }
}
