package vorga.phazeclient.implement.hud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;
import vorga.phazeclient.api.system.hud.HudBackgroundGeometryRenderer;
import vorga.phazeclient.api.system.hud.HudBackgroundGeometryGradients;

/** Batched backgrounds and shared colour math; no per-widget textures. */
final class HudPaint {
    private HudPaint() { }
    static int withAlpha(int rgb, int alpha) { return MathHelper.clamp(alpha, 0, 255) << 24 | rgb & 0xFFFFFF; }
    static float getClampedHudCornerRadius(float radius, float width, float height) {
        return MathHelper.clamp(radius, 0, Math.max(0, Math.min(width, height) * 0.5F));
    }
    static void renderSolidHudBackground(DrawContext context, float x, float y, float width, float height, int color, float requestedRadius) {
        float radius = getClampedHudCornerRadius(requestedRadius, width, height);
        if (radius <= 0.01F) context.fill(Math.round(x), Math.round(y), Math.round(x + width), Math.round(y + height), color);
        else HudBackgroundGeometryRenderer.solidRounded(context, x, y, width, height, color, radius);
    }
    static void renderGradientRect(DrawContext context, float x, float y, float width, float height, int hoverFill,
                                   int startColor, int endColor, String direction, float offset, boolean animated, float requestedRadius) {
        if (hoverFill != 0) { startColor = blendARGB(startColor, hoverFill); endColor = blendARGB(endColor, hoverFill); }
        int left = Math.round(x), top = Math.round(y);
        int right = Math.max(left + 1, Math.round(x + width)), bottom = Math.max(top + 1, Math.round(y + height));
        direction = direction == null ? "Left to Right" : direction;
        float radius = getClampedHudCornerRadius(requestedRadius, width, height);
        if (radius > 0.01F) {
            renderRoundedGradient(context, x, y, x + width, y + height, startColor, endColor, direction, offset, animated, radius);
        } else if (!animated) {
            renderStaticGradient(context, left, top, right, bottom, startColor, endColor, direction);
        } else if ("Pulse".equals(direction)) {
            context.fill(left, top, right, bottom, movingGradientColor(startColor, endColor, offset));
        } else if ("Top to Bottom".equals(direction) || "Bottom to Top".equals(direction)) {
            renderMovingAxisGradient(context, left, top, right, bottom, startColor, endColor,
                    ("Top to Bottom".equals(direction) ? -1 : 1) * offset, false);
        } else if ("Left to Right".equals(direction) || "Right to Left".equals(direction)) {
            renderMovingAxisGradient(context, left, top, right, bottom, startColor, endColor,
                    ("Right to Left".equals(direction) ? 1 : -1) * offset, true);
        } else HudBackgroundGeometryRenderer.movingDiagonal(context, left, top, right, bottom, startColor, endColor, direction, offset);
    }
    static void renderRoundedGradient(DrawContext context, float left, float top, float right, float bottom,
                                      int start, int end, String direction, float offset, boolean animated, float radius) {
        HudBackgroundGeometryRenderer.roundedGradient(context, left, top, right, bottom, start, end, direction, offset, animated, radius);
    }
    static void renderStaticGradient(DrawContext context, int left, int top, int right, int bottom, int start, int end, String direction) {
        HudBackgroundGeometryRenderer.staticGradient(context, left, top, right, bottom, start, end, direction);
    }
    static void renderMovingAxisGradient(DrawContext context, float left, float top, float right, float bottom,
                                         int start, int end, float phase, boolean horizontal) {
        float position = 0;
        for (int segment = 0; segment < 4 && position < 0.99999F; segment++) {
            float coordinate = position + phase;
            float stop = ((float) Math.floor(coordinate * 2 + 0.00001F) + 1) * 0.5F - phase;
            float endPosition = MathHelper.clamp(stop, position + 0.0001F, 1);
            int from = movingLinearGradientColor(start, end, coordinate), to = movingLinearGradientColor(start, end, endPosition + phase);
            if (horizontal) {
                float x = MathHelper.lerp(position, left, right);
                drawHorizontalGradient(context, x, top, MathHelper.lerp(endPosition, left, right) - x, bottom - top, from, to);
            } else {
                float y = MathHelper.lerp(position, top, bottom);
                drawVerticalGradient(context, left, y, right - left, MathHelper.lerp(endPosition, top, bottom) - y, from, to);
            }
            position = endPosition;
        }
    }
    static void drawVerticalGradient(DrawContext context, float x, float y, float width, float height, int start, int end) {
        if (width > 0 && height > 0) HudBackgroundGeometryRenderer.quad(context, x, y, x + width, y + height, start, end, end, start);
    }
    static void drawHorizontalGradient(DrawContext context, float x, float y, float width, float height, int start, int end) {
        if (width > 0 && height > 0) HudBackgroundGeometryRenderer.quad(context, x, y, x + width, y + height, start, start, end, end);
    }
    static int movingGradientColor(int start, int end, float coordinate) { return HudBackgroundGeometryGradients.moving(start, end, coordinate); }
    static int movingLinearGradientColor(int start, int end, float coordinate) {
        float wrapped = coordinate - (float) Math.floor(coordinate);
        return lerpGradientColor(start, end, wrapped < 0.5F ? wrapped * 2 : (1 - wrapped) * 2);
    }
    static int lerpGradientColor(int first, int second, float progress) { return HudBackgroundGeometryGradients.lerp(first, second, progress); }
    static void drawOutlineNoOverlap(DrawContext context, int x, int y, int width, int height, int color) {
        if (width <= 2 || height <= 2) return;
        context.fill(x, y, x + width, y + 1, color);
        context.fill(x, y + height - 1, x + width, y + height, color);
        context.fill(x, y + 1, x + 1, y + height - 1, color);
        context.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }
    static void drawOuterOutline(DrawContext context, float x, float y, float width, float height, int thickness, int color) {
        if (thickness <= 0) return;
        int left = MathHelper.floor(x), top = MathHelper.floor(y), right = MathHelper.ceil(x + width), bottom = MathHelper.ceil(y + height);
        context.fill(left - thickness, top - thickness, right + thickness, top, color);
        context.fill(left - thickness, bottom, right + thickness, bottom + thickness, color);
        context.fill(left - thickness, top, left, bottom, color);
        context.fill(right, top, right + thickness, bottom, color);
    }
    static int blendARGB(int base, int over) {
        float oa = (over >>> 24) / 255F;
        if (oa <= 0) return base;
        float ba = (base >>> 24) / 255F, out = oa + ba * (1 - oa);
        if (out <= 0) return 0;
        int result = MathHelper.clamp(Math.round(out * 255), 0, 255) << 24;
        for (int shift = 0; shift < 24; shift += 8) {
            int a = base >>> shift & 255, b = over >>> shift & 255;
            result |= MathHelper.clamp(Math.round((b * oa + a * ba * (1 - oa)) / out), 0, 255) << shift;
        }
        return result;
    }
    static int approachColorExp(int current, int target, float speed, float dt) {
        float t = 1 - (float) Math.exp(-speed * dt);
        int result = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int a = current >>> shift & 255, b = target >>> shift & 255;
            result |= MathHelper.clamp(Math.round(a + (b - a) * t), 0, 255) << shift;
        }
        return result;
    }
}
