package fr.tropimon.chatfilter;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.Optional;

/** Associe la demande et sa ligne d'options sans supposer les commandes du serveur. */
public final class TeleportRequestManager {
    private static final TeleportRequestQueue QUEUE = new TeleportRequestQueue();
    private static Object connection;
    private static int pendingLocalResolutions;
    private static long localResolutionDeadline;

    private TeleportRequestManager() {
    }

    public static void observe(Text message) {
        refreshSession();
        long now = System.currentTimeMillis();
        String plain = message.getString();
        TeleportRequestParser.Observation observation = TeleportRequestParser.analyze(plain);
        observation.request().ifPresent(request ->
                QUEUE.offer(request, PlayerRoleResolver.resolve(request.player()), now));
        if (observation.options()) {
            attachActions(message, now);
        } else if (observation.resolved()) {
            if (pendingLocalResolutions > 0 && now <= localResolutionDeadline) {
                pendingLocalResolutions--;
            } else {
                pendingLocalResolutions = 0;
                QUEUE.resolveCurrent();
            }
        }
    }

    public static void tick() {
        refreshSession();
        long now = System.currentTimeMillis();
        QUEUE.expire(now);
        if (pendingLocalResolutions > 0 && now > localResolutionDeadline) {
            pendingLocalResolutions = 0;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen == null || client.options.hudHidden || !client.isWindowFocused()) {
            TeleportPopupPosition.stopDrag();
        }
    }

    public static boolean visible() {
        refreshSession();
        MinecraftClient client = MinecraftClient.getInstance();
        return client.player != null && QUEUE.current() != null;
    }

    static TeleportRequestQueue.Entry current() {
        refreshSession();
        return QUEUE.current();
    }

    static int count() {
        refreshSession();
        return QUEUE.size();
    }

    static boolean choose(boolean accept) {
        refreshSession();
        TeleportRequestQueue.Entry request = QUEUE.current();
        MinecraftClient client = MinecraftClient.getInstance();
        if (request == null || client.player == null || !request.ready()) {
            return false;
        }
        String command = request.command(accept);
        if (command == null || command.isBlank()) {
            return false;
        }
        client.player.networkHandler.sendChatCommand(
                command.charAt(0) == '/' ? command.substring(1) : command);
        QUEUE.resolveCurrent();
        pendingLocalResolutions = Math.min(
                TeleportRequestQueue.MAX_REQUESTS, pendingLocalResolutions + 1);
        localResolutionDeadline = System.currentTimeMillis() + 5_000L;
        client.execute(() -> {
            if (client.currentScreen instanceof ChatScreen) {
                client.setScreen(null);
            }
        });
        return true;
    }

    private static void attachActions(Text message, long now) {
        String[] commands = new String[2];
        message.visit((style, segment) -> {
            ClickEvent click = style.getClickEvent();
            if (click == null || click.getAction() != ClickEvent.Action.RUN_COMMAND
                    || click.getValue() == null || click.getValue().isBlank()) {
                return Optional.empty();
            }
            TeleportRequestParser.Action action =
                    TeleportRequestParser.action(segment, click.getValue());
            if (action == TeleportRequestParser.Action.ACCEPT) {
                commands[0] = click.getValue();
            } else if (action == TeleportRequestParser.Action.DECLINE) {
                commands[1] = click.getValue();
            }
            return Optional.empty();
        }, Style.EMPTY);
        QUEUE.attachActions(commands[0], commands[1], now);
    }

    private static void refreshSession() {
        Object current = MinecraftClient.getInstance().getNetworkHandler();
        if (current == connection) {
            return;
        }
        connection = current;
        QUEUE.clear();
        pendingLocalResolutions = 0;
        localResolutionDeadline = 0L;
        TeleportPopupPosition.stopDrag();
    }
}
