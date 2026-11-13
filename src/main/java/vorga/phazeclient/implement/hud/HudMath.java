package vorga.phazeclient.implement.hud;

import net.minecraft.util.math.MathHelper;

/** Pure geometry and easing shared by HUD layout and animation. */
final class HudMath {
    private HudMath() { }
    static float wrap360(float angle) {
        float value = angle % 360;
        return value < 0 ? value + 360 : value;
    }
    static float shortestAngleDeg(float target, float current) {
        float difference = (target - current) % 360;
        if (difference > 180) difference -= 360;
        if (difference < -180) difference += 360;
        return difference;
    }
    static float approachAngle(float current, float target, float step) {
        return wrap360(current + shortestAngleDeg(target, current) * MathHelper.clamp(step, 0, 1));
    }
    static boolean isHovered(double mouseX, double mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
    static boolean isNearRect(double mouseX, double mouseY, float x, float y, float width, float height, float distance) {
        return mouseX >= x - distance && mouseX <= x + width + distance
                && mouseY >= y - distance && mouseY <= y + height + distance;
    }
    static float directionEdgeFade(float x, float width) {
        float fadeWidth = Math.max(18, width * 0.16F);
        return smoothStep((x - 4) / fadeWidth) * smoothStep((width - 4 - x) / fadeWidth);
    }
    static float smoothStep(float value) {
        float t = MathHelper.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }
    static float approachExp(float current, float target, float speed, float dt) {
        return current + (target - current) * (1 - (float) Math.exp(-speed * dt));
    }
}
