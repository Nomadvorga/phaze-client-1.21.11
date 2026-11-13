package vorga.phazeclient.api.system.shape.batched;

/**
 * Mirror of the scissor box last submitted through Phaze or the GUI renderer.
 * 1.21.11 GUI draws are deferred, so the box is also re-applied when that queue is flushed.
 */
public final class GuiClipState {
    private static boolean enabled;
    private static int x, y, width, height;

    private GuiClipState() {
    }

    public static void onEnable() {
        if (enabled) return;
        vorga.phazeclient.api.system.font.msdf.MsdfBatch.flush();
        enabled = true;
    }

    public static void onDisable() {
        if (!enabled) return;
        vorga.phazeclient.api.system.font.msdf.MsdfBatch.flush();
        enabled = false;
    }

    public static void onBox(int boxX, int boxY, int boxWidth, int boxHeight) {
        if (enabled && x == boxX && y == boxY && width == boxWidth && height == boxHeight) return;
        vorga.phazeclient.api.system.font.msdf.MsdfBatch.flush();
        x = boxX;
        y = boxY;
        width = boxWidth;
        height = boxHeight;
        enabled = true;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static int boxX() { return x; }
    public static int boxY() { return y; }
    public static int boxWidth() { return width; }
    public static int boxHeight() { return height; }
}
