package vorga.phazeclient.api.system.font.msdf;

/** CPU-only advance/scale rules shared by layout, MSDF drawing and fallback. */
public final class MsdfTextMetrics {
    public static final float DEFAULT_THICKNESS = 0.05F;
    public static final float DEFAULT_GLYPH_SPACING = DEFAULT_THICKNESS * 0.5F;
    public static final float PEN_OFFSET_X = 0.75F;
    public static final float BASELINE_EM = 0.7F;
    private MsdfTextMetrics() {}

    public static float renderedWidth(float advanceWidth, int glyphCount, float size, float thickness) {
        return advanceWidth + Math.max(0, glyphCount - 1) * thickness * 0.5F * size;
    }

    public static float fallbackScaleY(float size, int vanillaHeight) { return size / Math.max(1, vanillaHeight); }
    public static float fallbackScaleX(float targetWidth, int vanillaWidth, float scaleY) {
        return vanillaWidth > 0 && targetWidth > 0 ? targetWidth / vanillaWidth : scaleY;
    }
}
