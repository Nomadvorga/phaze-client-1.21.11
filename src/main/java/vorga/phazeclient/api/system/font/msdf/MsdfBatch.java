package vorga.phazeclient.api.system.font.msdf;

import com.mojang.blaze3d.textures.GpuTextureView;
import vorga.phazeclient.api.system.shape.batched.BatchedRectangle;
import vorga.phazeclient.api.system.shape.batched.GuiBatch;

/**
 * Deferred MSDF text and icons. Quads join the single {@link GuiBatch} run, so a capture-target
 * change, scissor change, or failed capture flushes or drops them with the rest of that run.
 */
public final class MsdfBatch {
    private static boolean enabled = true;
    private static boolean flushing;
    private static final java.util.ArrayList<Runnable> fallbacks = new java.util.ArrayList<>();

    private MsdfBatch() {}

    public static boolean isEnabled() { return enabled; }

    public static boolean canDefer() {
        return enabled && !flushing && BatchedRectangle.isBatching();
    }

    public static void setEnabled(boolean value) {
        if (enabled && !value) flush();
        enabled = value;
    }

    /** One string's glyphs. Outline, fadeout and label fade stay on the run key. */
    static GuiBatch.Run begin(GpuTextureView view, float range, float thickness, float smoothness,
                              MsdfRenderer.TextEffects effects, float textX) {
        return GuiBatch.msdf(view, range, thickness, smoothness, effects, textX);
    }

    /** Icon quads. They share the text run when the atlas and parameters match. */
    public static GuiBatch.Run beginIcon(GpuTextureView view, float range, float thickness, float smoothness) {
        if (!Float.isFinite(range) || !Float.isFinite(thickness) || !Float.isFinite(smoothness)) {
            throw new IllegalArgumentException("MSDF icon parameters must be finite");
        }
        return GuiBatch.msdf(view, range, thickness, smoothness, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
    }

    static void fallbackOnFailure(Runnable fallback) {
        fallbacks.add(fallback);
    }

    public static void flush() {
        if (flushing) return;
        flushing = true;
        boolean failed = false;
        try {
            GuiBatch.flushMsdf();
        } catch (Exception failure) {
            MsdfRenderer.reportFailure(failure);
            failed = true;
            GuiBatch.discardMsdf();
        } finally {
            try {
                if (failed) {
                    for (Runnable fallback : fallbacks) fallback.run();
                }
            } finally {
                fallbacks.clear();
                flushing = false;
            }
        }
    }

    public static void discard() {
        GuiBatch.discardMsdf();
        fallbacks.clear();
    }

    public static void cleanup() {
        discard();
    }
}
