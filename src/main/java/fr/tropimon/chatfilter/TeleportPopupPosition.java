package fr.tropimon.chatfilter;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Position en coordonnées d'interface, sauvegardée uniquement à la fin d'un déplacement. */
public final class TeleportPopupPosition {
    public static final int WIDTH = 180;
    public static final int HEIGHT = 107;
    private static final Path FILE = JsonConfigStore.resolve("tropimon-chat-filter-teleport-popup.json");
    private static final Position POSITION = new Position(WIDTH, HEIGHT);

    static {
        load();
    }

    private TeleportPopupPosition() {
    }

    public static int left(int screenWidth) {
        return POSITION.left(screenWidth);
    }

    public static int top(int screenHeight) {
        return POSITION.top(screenHeight);
    }

    public static boolean startDrag(double mouseX, double mouseY, int screenWidth, int screenHeight) {
        if (!TeleportRequestManager.visible()) {
            return false;
        }
        return POSITION.start(mouseX, mouseY, screenWidth, screenHeight);
    }

    public static boolean drag(double mouseX, double mouseY, int screenWidth, int screenHeight) {
        return POSITION.drag(mouseX, mouseY, screenWidth, screenHeight);
    }

    public static boolean stopDrag() {
        if (!POSITION.stop()) {
            return false;
        }
        save();
        return true;
    }

    private static void load() {
        if (FILE == null || !Files.isRegularFile(FILE)) {
            return;
        }
        try {
            JsonObject json = JsonConfigStore.read(FILE);
            if (json != null && json.has("x") && json.has("y")) {
                POSITION.restore(json.get("x").getAsInt(), json.get("y").getAsInt());
            }
        } catch (Exception ignored) {
            // Une position invalide revient à l'emplacement par défaut.
        }
    }

    private static void save() {
        if (FILE == null || !POSITION.positioned()) {
            return;
        }
        JsonObject json = new JsonObject();
        json.addProperty("x", POSITION.x());
        json.addProperty("y", POSITION.y());
        try {
            JsonConfigStore.write(FILE, json);
        } catch (IOException ignored) {
            // Le popup reste utilisable pour la session si la sauvegarde échoue.
        }
    }

    static final class Position {
        private final int width;
        private final int height;
        private boolean positioned;
        private boolean dragging;
        private int x;
        private int y;
        private double offsetX;
        private double offsetY;

        Position(int width, int height) {
            this.width = width;
            this.height = height;
        }

        int left(int screenWidth) {
            return positioned ? clamp(x, screenWidth, width)
                    : Math.max(2, (screenWidth - width) / 2);
        }

        int top(int screenHeight) {
            return positioned ? clamp(y, screenHeight, height)
                    : Math.max(2, (screenHeight - height) / 2);
        }

        void restore(int savedX, int savedY) {
            x = savedX;
            y = savedY;
            positioned = true;
        }

        boolean start(double mouseX, double mouseY, int screenWidth, int screenHeight) {
            int left = left(screenWidth);
            int top = top(screenHeight);
            if (mouseX < left + 3 || mouseX >= left + width - 3
                    || mouseY < top + 3 || mouseY >= top + 19) {
                return false;
            }
            offsetX = mouseX - left;
            offsetY = mouseY - top;
            x = left;
            y = top;
            positioned = true;
            dragging = true;
            return true;
        }

        boolean drag(double mouseX, double mouseY, int screenWidth, int screenHeight) {
            if (!dragging) {
                return false;
            }
            x = clamp((int) Math.round(mouseX - offsetX), screenWidth, width);
            y = clamp((int) Math.round(mouseY - offsetY), screenHeight, height);
            return true;
        }

        boolean stop() {
            boolean wasDragging = dragging;
            dragging = false;
            return wasDragging;
        }

        boolean positioned() {
            return positioned;
        }

        int x() {
            return x;
        }

        int y() {
            return y;
        }

        private static int clamp(int coordinate, int screenSize, int size) {
            return Math.max(2, Math.min(coordinate, Math.max(2, screenSize - size - 2)));
        }
    }
}
