package vorga.phazeclient.implement.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;
import vorga.phazeclient.implement.features.modules.client.Theme;
import java.util.LinkedHashMap;
import java.util.Map;
import static vorga.phazeclient.implement.hud.HudStyle.*;

/** Vanilla text measurement with bounded cache; gameplay fonts stay independent of the menu. */
final class HudTextDraw {
    private HudTextDraw() { }
    static final int HUD_TEXT_WIDTH_CACHE_MAX = 512;
    static final Map<String, Float> HUD_TEXT_WIDTH_CACHE = new LinkedHashMap<>(512, 0.75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Float> entry) { return size() > HUD_TEXT_WIDTH_CACHE_MAX; }
    };
    static float getHudTextWidth(MinecraftClient client, String text, float textSize) {
        String key = "V|" + textSize + "|" + text;
        Float cached = HUD_TEXT_WIDTH_CACHE.get(key);
        if (cached != null) return cached;
        float width = client.textRenderer.getWidth(text);
        HUD_TEXT_WIDTH_CACHE.put(key, width);
        return width;
    }
    static float getTextHudBaseWidth(MinecraftClient client, String text) {
        return Math.max(44, getHudTextWidth(client, text, HUD_TEXT_SIZE) + 14)
                + (text.contains("A") || text.contains("a") ? 2 : 0);
    }
    static int resolveHudTextColor() {
        Theme theme = Theme.getInstance();
        return theme == null ? HUD_TEXT_COLOR : theme.getHudTextColor();
    }
    static void renderHudTextWithAlpha(DrawContext context, MinecraftClient client, String text, float x, float y,
                                      float size, boolean shadow, int alpha) {
        renderHudTextColoredWithAlpha(context, client, text, x, y, size, shadow, resolveHudTextColor(), alpha);
    }
    static void renderHudTextColoredWithAlpha(DrawContext context, MinecraftClient client, String text, float x, float y,
                                             float size, boolean shadow, int rgb, int alpha) {
        if (alpha <= 0) return;
        context.drawText(client.textRenderer, text, Math.round(x), Math.round(y),
                MathHelper.clamp(alpha, 0, 255) << 24 | rgb & 0xFFFFFF, shadow);
    }
    static void renderHudText(DrawContext context, MinecraftClient client, String text, float x, float y, float size, boolean shadow) {
        renderHudTextWithAlpha(context, client, text, x, y, size, shadow, 255);
    }
    static void renderHudTextColored(DrawContext context, MinecraftClient client, String text, float x, float y,
                                    float size, boolean shadow, int rgb) {
        renderHudTextColoredWithAlpha(context, client, text, x, y, size, shadow, rgb, 255);
    }
    static void renderScaledHudText(DrawContext context, MinecraftClient client, String text, float x, float y,
                                    float textX, float textY, float size, float scale, boolean shadow) {
        renderScaledHudTextWithAlpha(context, client, text, x, y, textX, textY, size, scale, shadow, 255);
    }
    static void renderScaledHudTextWithAlpha(DrawContext context, MinecraftClient client, String text, float x, float y,
                                             float textX, float textY, float size, float scale, boolean shadow, int alpha) {
        context.getMatrices().pushMatrix();
        try {
            context.getMatrices().translate(x, y);
            context.getMatrices().scale(scale, scale);
            renderHudTextWithAlpha(context, client, text, textX, textY, size, shadow, alpha);
        } finally { context.getMatrices().popMatrix(); }
    }
    static void renderScaledHudTextColored(DrawContext context, MinecraftClient client, String text, float x, float y,
                                           float textX, float textY, float size, float scale, boolean shadow, int rgb) {
        context.getMatrices().pushMatrix();
        try {
            context.getMatrices().translate(x, y);
            context.getMatrices().scale(scale, scale);
            renderHudTextColored(context, client, text, textX, textY, size, shadow, rgb);
        } finally { context.getMatrices().popMatrix(); }
    }
}
