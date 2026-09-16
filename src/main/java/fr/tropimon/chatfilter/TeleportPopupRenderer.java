package fr.tropimon.chatfilter;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Popup compact inspiré de l'interface Tropimon, sans ressource ni dépendance externe. */
public final class TeleportPopupRenderer {
    private static final Identifier FRAME = Identifier.of(
            "tropimon_chat_filter", "textures/gui/teleport_frame.png");
    private static final int BUTTON_Y = 81;
    private static final int BUTTON_HEIGHT = 17;
    private static final int BUTTON_WIDTH = 75;
    private static final int ACCEPT_X = 12;
    private static final int DECLINE_X = 93;
    private static final Text TITLE = Text.translatable("tropimon_chat_filter.teleport.title");
    private static final Text ACCEPT = Text.translatable("tropimon_chat_filter.teleport.accept");
    private static final Text DECLINE = Text.translatable("tropimon_chat_filter.teleport.decline");

    private TeleportPopupRenderer() {
    }

    public static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        TeleportRequestQueue.Entry request = TeleportRequestManager.current();
        if (request == null || client.player == null || client.options.hudHidden) {
            return;
        }

        int x = TeleportPopupPosition.left(context.getScaledWindowWidth());
        int y = TeleportPopupPosition.top(context.getScaledWindowHeight());
        int width = TeleportPopupPosition.WIDTH;
        int height = TeleportPopupPosition.HEIGHT;
        double mouseX = -1;
        double mouseY = -1;
        if (client.currentScreen != null) {
            mouseX = client.mouse.getX() * context.getScaledWindowWidth()
                    / client.getWindow().getWidth();
            mouseY = client.mouse.getY() * context.getScaledWindowHeight()
                    / client.getWindow().getHeight();
        }

        drawScaled(context, FRAME, x, y, 345, 205, width, height);
        context.fill(x + 7, y + 6, x + width - 7, y + height - 6, 0xEA111A22);
        context.fill(x + 8, y + 23, x + width - 8, y + 24, 0xAA58DFF4);

        context.drawTextWithShadow(client.textRenderer, TITLE, x + 12, y + 10, 0xFF7CF0D1);
        int count = TeleportRequestManager.count();
        Text counter = Text.literal("1/" + count);
        context.drawTextWithShadow(client.textRenderer, counter,
                x + width - client.textRenderer.getWidth(counter) - 12, y + 10, 0xFFFFD84D);

        drawHead(context, request.player(), x + 12, y + 31);
        Text player = Text.literal(client.textRenderer.trimToWidth(request.player(), 112));
        context.drawTextWithShadow(client.textRenderer, player, x + 51, y + 29, 0xFFFFFFFF);
        context.drawTextWithShadow(client.textRenderer, request.role().label(),
                x + 51, y + 41, request.roleColor());
        Text detail = Text.translatable(request.direction() == TeleportRequestParser.Direction.TO_REQUESTER
                ? "tropimon_chat_filter.teleport.to_requester"
                : "tropimon_chat_filter.teleport.to_you");
        context.drawTextWithShadow(client.textRenderer,
                Text.literal(client.textRenderer.trimToWidth(detail.getString(), 112)),
                x + 51, y + 53, 0xFFB8C4CA);
        context.drawTextWithShadow(client.textRenderer,
                Text.translatable("tropimon_chat_filter.teleport.open_chat"),
                x + 51, y + 65, 0xFF87979F);

        boolean ready = request.ready();
        drawButton(context, x + ACCEPT_X, y + BUTTON_Y, ACCEPT,
                ready, contains(x + ACCEPT_X, y + BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT,
                        mouseX, mouseY), true);
        drawButton(context, x + DECLINE_X, y + BUTTON_Y, DECLINE,
                ready, contains(x + DECLINE_X, y + BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT,
                        mouseX, mouseY), false);
    }

    public static boolean click(double mouseX, double mouseY, int screenWidth, int screenHeight) {
        TeleportRequestQueue.Entry request = TeleportRequestManager.current();
        if (request == null || !request.ready()) {
            return false;
        }
        int x = TeleportPopupPosition.left(screenWidth);
        int y = TeleportPopupPosition.top(screenHeight);
        if (contains(x + ACCEPT_X, y + BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT, mouseX, mouseY)) {
            return TeleportRequestManager.choose(true);
        }
        if (contains(x + DECLINE_X, y + BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT, mouseX, mouseY)) {
            return TeleportRequestManager.choose(false);
        }
        return false;
    }

    private static void drawHead(DrawContext context, String player, int x, int y) {
        context.fill(x, y, x + 34, y + 34, 0xCC26323A);
        Identifier texture = PlayerHeadCache.findTexture(player);
        if (texture == null) {
            return;
        }
        // Agrandit seulement les carrés 8x8 du visage et de son chapeau.
        // L'autre surcharge utilise la taille de destination comme taille source,
        // ce qui affichait une mosaïque de plusieurs parties de la skin.
        context.drawTexture(texture, x + 1, y + 1, 32, 32,
                8.0F, 8.0F, 8, 8, 64, 64);
        context.drawTexture(texture, x + 1, y + 1, 32, 32,
                40.0F, 8.0F, 8, 8, 64, 64);
    }

    private static void drawButton(DrawContext context, int x, int y, Text label,
                                   boolean enabled, boolean hovered, boolean accept) {
        int color;
        if (!enabled) {
            color = 0xCC30383D;
        } else if (accept) {
            color = hovered ? 0xE33A8A5A : 0xD52B6845;
        } else {
            color = hovered ? 0xE39A4545 : 0xD56E3333;
        }
        context.fill(x, y, x + BUTTON_WIDTH, y + BUTTON_HEIGHT, color);
        context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, label,
                x + BUTTON_WIDTH / 2, y + 5, enabled ? 0xFFFFFFFF : 0xFF89949A);
    }

    private static void drawScaled(DrawContext context, Identifier texture, int x, int y,
                                   int sourceWidth, int sourceHeight, int width, int height) {
        context.getMatrices().push();
        try {
            context.getMatrices().translate(x, y, 0.0F);
            context.getMatrices().scale(width / (float) sourceWidth,
                    height / (float) sourceHeight, 1.0F);
            context.drawTexture(texture, 0, 0, 0.0F, 0.0F,
                    sourceWidth, sourceHeight, sourceWidth, sourceHeight);
        } finally {
            context.getMatrices().pop();
        }
    }

    private static boolean contains(int x, int y, int width, int height,
                                    double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
