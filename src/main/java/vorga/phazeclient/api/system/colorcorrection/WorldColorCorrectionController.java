package vorga.phazeclient.api.system.colorcorrection;

import vorga.phazeclient.implement.features.modules.other.ColorCorrection;
import java.util.ArrayDeque;
import java.util.Deque;

/** CPU-only target scopes and the exact source color arithmetic. Resolve factors once per pass. */
public final class WorldColorCorrectionController {
    private static final ThreadLocal<Deque<Target>> TARGET_STACK = ThreadLocal.withInitial(ArrayDeque::new);
    private WorldColorCorrectionController() {}
    public enum Target { NONE, BLOCKS, FLUIDS, SKY, CLOUDS, PLAYERS, ENTITIES }

    public static void push(Target target) { TARGET_STACK.get().push(target == null ? Target.NONE : target); }
    public static void pop() { if (!TARGET_STACK.get().isEmpty()) TARGET_STACK.get().pop(); }
    public static Target current() { return TARGET_STACK.get().isEmpty() ? Target.NONE : TARGET_STACK.get().peek(); }
    public static boolean isTargetActive(Target target) {
        ColorCorrection module = module();
        return target != null && target != Target.NONE && module != null && module.isEnabled() && module.hasAdvancedCorrection(target);
    }
    public static boolean supportsAlpha(Target target) {
        ColorCorrection module = module();
        return module != null && module.supportsAdvancedAlpha(target);
    }
    public static boolean needsBlend(Target target) { return supportsAlpha(target) && isTargetActive(target) && getAlpha(target) < 0.999F; }
    public static boolean needsColorTransform(Target target) {
        return isTargetActive(target) && (getRed(target) != 1 || getGreen(target) != 1 || getBlue(target) != 1
                || getBrightness(target) != 0 || getSaturation(target) != 1);
    }
    public static boolean needsAlphaTransform(Target target) { return supportsAlpha(target) && isTargetActive(target) && getAlpha(target) != 1; }
    public static float getRed(Target target) { ColorCorrection m = module(); return m == null ? 1 : m.getAdvancedRed(target); }
    public static float getGreen(Target target) { ColorCorrection m = module(); return m == null ? 1 : m.getAdvancedGreen(target); }
    public static float getBlue(Target target) { ColorCorrection m = module(); return m == null ? 1 : m.getAdvancedBlue(target); }
    public static float getAlpha(Target target) { ColorCorrection m = module(); return m == null ? 1 : m.getAdvancedAlpha(target); }
    public static float getBrightness(Target target) { ColorCorrection m = module(); return m == null ? 0 : m.getAdvancedBrightness(target); }
    public static float getSaturation(Target target) { ColorCorrection m = module(); return m == null ? 1 : m.getAdvancedSaturation(target); }

    // Vertex consumers use an explicit target to avoid correcting unrelated GUI/world batches.
    public static int applyToArgb(int argb) { return argb; }
    public static int applyToArgb(Target target, int argb) {
        if (!isTargetActive(target)) return argb;
        int alpha = argb >>> 24;
        return ((supportsAlpha(target) ? applyAlphaInt(alpha, target) : alpha) << 24)
                | applyRgb((argb >>> 16) & 255, (argb >>> 8) & 255, argb & 255, target);
    }
    public static int applyRgb(int red, int green, int blue, Target target) {
        if (!isTargetActive(target)) return ((red & 255) << 16) | ((green & 255) << 8) | (blue & 255);
        return applyRgbWith(red, green, blue, getRed(target), getGreen(target), getBlue(target), getSaturation(target), getBrightness(target));
    }
    public static int applyRgbWith(int red, int green, int blue, float rm, float gm, float bm, float saturation, float brightness) {
        float r = clamp01(clamp01(red / 255.0F) * rm);
        float g = clamp01(clamp01(green / 255.0F) * gm);
        float b = clamp01(clamp01(blue / 255.0F) * bm);
        float gray = r * 0.2126F + g * 0.7152F + b * 0.0722F;
        r = applyBrightness(gray + (r - gray) * saturation, brightness);
        g = applyBrightness(gray + (g - gray) * saturation, brightness);
        b = applyBrightness(gray + (b - gray) * saturation, brightness);
        return (clamp255(Math.round(r * 255)) << 16) | (clamp255(Math.round(g * 255)) << 8) | clamp255(Math.round(b * 255));
    }
    public static int applyAlphaIntWith(int alpha, boolean blend, float multiplier) { return clamp255(blend ? Math.round(alpha * multiplier) : alpha); }
    public static int[] applyChannels(int red, int green, int blue, Target target) {
        int rgb = applyRgb(red, green, blue, target);
        return new int[] { (rgb >>> 16) & 255, (rgb >>> 8) & 255, rgb & 255 };
    }
    public static float applyAlpha(float alpha, Target target) { return needsBlend(target) ? clamp01(alpha * getAlpha(target)) : alpha; }
    public static int applyAlphaInt(int alpha, Target target) { return applyAlphaIntWith(alpha, needsBlend(target), getAlpha(target)); }
    private static float applyBrightness(float value, float brightness) {
        value = clamp01(value);
        if (brightness > 0) return clamp01(value + (1 - value) * brightness);
        if (brightness < 0) return clamp01(value * (1 + brightness));
        return value;
    }
    private static float clamp01(float value) { return Math.max(0, Math.min(1, value)); }
    private static int clamp255(int value) { return Math.max(0, Math.min(255, value)); }
    private static ColorCorrection module() { return ColorCorrection.getInstance(); }
}
