package vorga.phazeclient.implement.hud;

/** Shared widget dimensions, colours and legacy layer identifiers. */
final class HudStyle {
    private HudStyle() { }
    static final float BASE_WIDTH = 60, BASE_HEIGHT = 20, HUD_TEXT_SIZE = 8;
    static final int HUD_TEXT_COLOR = 0xFFFFFFFF, HANDLE_COLOR = 0xFF72F7D4;
    static final float BASE_HOVER_OUTLINE_THICKNESS = 1;
    static final float HUD_TEXT_RENDER_Z = 1000, HUD_RENDER_Z = 400, HANDLE_RENDER_Z = 450;
}
