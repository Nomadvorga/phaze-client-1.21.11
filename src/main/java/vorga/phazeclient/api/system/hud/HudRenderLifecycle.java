package vorga.phazeclient.api.system.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import vorga.phazeclient.api.system.draw.GuiProjection;
import vorga.phazeclient.api.system.font.msdf.MsdfRenderer;
import vorga.phazeclient.api.system.render.WorldOverlayRenderer;
import vorga.phazeclient.api.system.shape.batched.BatchedRectangle;
import vorga.phazeclient.api.system.shape.batched.GuiBatch;
import vorga.phazeclient.api.system.shape.implement.Blur;
import vorga.phazeclient.api.system.snapshot.CardSnapshotCache;

/** Call on the render thread at resource reload/stop, before the graphics context is destroyed. */
public final class HudRenderLifecycle {
    private HudRenderLifecycle() {}

    public static void cleanup() {
        RenderSystem.assertOnRenderThread();
        // Restore caller targets before deleting captures; never draw abandoned queued geometry.
        Runnable[] steps = {
                GuiBatch::discard,
                HudBuffer::abortAllCaptures,
                CardSnapshotCache::clearAll,
                HudBuffer::cleanupAll,
                BatchedHudBuffer.INSTANCE::cleanup,
                Blur.INSTANCE::cleanup,
                WorldOverlayRenderer::clear,
                vorga.phazeclient.api.system.colorcorrection.WorldColorUniforms::close,
                MsdfRenderer::close,
                vorga.phazeclient.api.system.font.glyph.GlyphMap::clearAll,
                GuiProjection::close,
                BatchedRectangle::cleanup,
                Render2DBuffers::cleanup,
                HudRoundedContour::cleanup,
                HudBuffer::resetCaptureTarget,
                GuiBatch::close
        };
        Throwable failure = null;
        for (Runnable step : steps) {
            try { step.run(); }
            catch (RuntimeException | Error problem) {
                if (failure == null) failure = problem;
                else failure.addSuppressed(problem);
            }
        }
        if (failure instanceof Error error) throw error;
        if (failure instanceof RuntimeException exception) throw exception;
    }
}
