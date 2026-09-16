package fr.tropimon.chatfilter;

/** Géométrie autonome des six emplacements du HUD d'équipe Cobblemon. */
public final class PartyShareInput {
    private static final int SLOT_COUNT = 6;
    private static final int SLOT_WIDTH = 68;
    private static final int SLOT_HEIGHT = 30;
    private static final int SLOT_SPACING = 4;
    private static int draggedSlot;
    private static double dragStartX;
    private static double dragStartY;

    private PartyShareInput() {
    }

    public static int slotAt(
            int screenHeight, double mouseX, double mouseY,
            int chatRight, int chatTop) {
        // Le chat est dessiné au-dessus du HUD Cobblemon. Toute sa surface
        // visible doit donc garder la priorité, pas seulement ses boutons.
        if (mouseX >= 0 && mouseX < chatRight && mouseY >= chatTop) {
            return 0;
        }
        if (mouseX < 0 || mouseX >= SLOT_WIDTH) {
            return 0;
        }
        int top = screenHeight / 2 - SLOT_COUNT * SLOT_HEIGHT / 2 - 10;
        for (int index = 0; index < SLOT_COUNT; index++) {
            int slotTop = top + index * (SLOT_HEIGHT + SLOT_SPACING);
            if (mouseY >= slotTop && mouseY < slotTop + SLOT_HEIGHT) {
                return index + 1;
            }
        }
        return 0;
    }

    public static String tag(int slot) {
        if (slot < 1 || slot > SLOT_COUNT) {
            throw new IllegalArgumentException("Emplacement d'équipe invalide");
        }
        return "<party:" + slot + ">";
    }

    public static void startDrag(int slot, double mouseX, double mouseY) {
        tag(slot);
        draggedSlot = slot;
        dragStartX = mouseX;
        dragStartY = mouseY;
    }

    public static int draggedSlot() {
        return draggedSlot;
    }

    public static int finishDrag(double mouseX, double mouseY, boolean overInput) {
        int slot = draggedSlot;
        draggedSlot = 0;
        double deltaX = mouseX - dragStartX;
        double deltaY = mouseY - dragStartY;
        boolean simpleClick = deltaX * deltaX + deltaY * deltaY < 16.0;
        return simpleClick || overInput ? slot : 0;
    }

    public static void cancelDrag() {
        draggedSlot = 0;
    }
}
