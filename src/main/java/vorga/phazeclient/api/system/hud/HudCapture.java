package vorga.phazeclient.api.system.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.Framebuffer;
import vorga.phazeclient.api.system.shape.batched.BatchedRectangle;
import vorga.phazeclient.api.system.shape.batched.GuiBatch;

/** Nested render-thread capture with caller-target restoration even when drawing fails. */
public final class HudCapture implements AutoCloseable {
    private static HudCapture active;
    private final HudCapture previous;
    private final HudPassState state;
    private final Framebuffer previousFramebuffer;
    private final int previousTarget;
    private final float previousHeight;
    private final java.util.function.Consumer<Boolean> onClose;
    private boolean closed;
    private boolean successful;
    private boolean committed;

    private HudCapture(HudPassState state, Framebuffer target, java.util.function.Consumer<Boolean> onClose) {
        RenderSystem.assertOnRenderThread();
        this.onClose = onClose;
        if (target == null || target.getColorAttachmentView() == null)
            throw new IllegalArgumentException("HUD capture requires a color attachment");
        this.state = state;
        previous = active;
        previousFramebuffer = HudBuffer.activeCaptureFramebuffer;
        previousTarget = HudBuffer.activeCaptureTarget;
        previousHeight = BatchedRectangle.getRenderTargetFbHeightOverride();
        try {
            GuiBatch.flush();
            HudBuffer.activeCaptureFramebuffer = target;
            HudBuffer.activeCaptureTarget = HudBuffer.CAPTURE_ACTIVE;
            RenderSystem.outputColorTextureOverride = target.getColorAttachmentView();
            RenderSystem.outputDepthTextureOverride = target.getDepthAttachmentView();
            RenderSystem.disableScissorForRenderTypeDraws();
            BatchedRectangle.setRenderTargetFbHeight(target.textureHeight);
            active = this;
        } catch (RuntimeException | Error failure) {
            HudBuffer.restoreCaptureTarget(previousTarget, previousFramebuffer);
            throw failure;
        }
    }

    /** Snapshot is taken by the caller before any clear or resize. */
    static HudCapture attach(HudPassState state, Framebuffer target) {
        return attach(state, target, null);
    }

    /** {@code onClose} lets an owning buffer publish or drop its content however the capture is closed. */
    static HudCapture attach(HudPassState state, Framebuffer target, java.util.function.Consumer<Boolean> onClose) {
        try {
            return new HudCapture(state, target, onClose);
        } catch (RuntimeException | Error failure) {
            try { state.close(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    public static HudCapture begin(Framebuffer target) {
        HudPassState state = HudPassState.capture();
        return attach(state, target);
    }

    public static boolean isActive() { return active != null; }

    public boolean isClosed() { return closed; }
    public boolean wasSuccessful() { return successful; }
    public void commit() { committed = true; }

    public void checkCanClose() {
        RenderSystem.assertOnRenderThread();
        if (!closed && active != this) throw new IllegalStateException("HUD captures must close in reverse order");
    }

    private static void discard() {
        vorga.phazeclient.api.system.font.msdf.MsdfBatch.discard();
        BatchedRectangle.discardPending();
    }

    public void close(boolean successful) {
        if (closed) return;
        checkCanClose();
        Throwable failure = null;
        try {
            if (successful) GuiBatch.flush();
            else {
                discard();
                BatchedRectangle.discardPending();
            }
        } catch (RuntimeException | Error problem) {
            failure = problem;
            successful = false;
            try {
                discard();
                BatchedRectangle.discardPending();
            } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
        } finally {
            try {
                BatchedRectangle.restoreRenderTargetFbHeight(previousHeight);
                HudBuffer.restoreCaptureTarget(previousTarget, previousFramebuffer);
                state.close();
            } catch (RuntimeException | Error cleanup) {
                if (failure == null) failure = cleanup;
                else failure.addSuppressed(cleanup);
            } finally {
                closed = true;
                active = previous;
                this.successful = failure == null && successful;
                if (onClose != null) onClose.accept(this.successful);
            }
        }
        if (failure instanceof Error error) throw error;
        if (failure instanceof RuntimeException exception) throw exception;
    }

    @Override public void close() {
        close(committed);
    }

    public static void abortAll() {
        abortThrough(null);
    }

    /** Abort an owner and its nested captures before deleting its framebuffer. */
    public static void abortThrough(HudCapture owner) {
        RenderSystem.assertOnRenderThread();
        if (owner != null && owner.closed) return;
        Throwable failure = null;
        while (active != null) {
            HudCapture current = active;
            try { current.close(false); }
            catch (RuntimeException | Error problem) {
                if (failure == null) failure = problem;
                else failure.addSuppressed(problem);
                if (active == current) {
                    current.closed = true;
                    active = current.previous;
                }
            }
            if (current == owner) break;
        }
        if (failure instanceof Error error) throw error;
        if (failure instanceof RuntimeException exception) throw exception;
    }
}
