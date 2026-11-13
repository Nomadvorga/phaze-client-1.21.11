package vorga.phazeclient.api.system.hud;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;

public final class BatchedHudBuffer {
    public static final BatchedHudBuffer INSTANCE = new BatchedHudBuffer();

    private static final int CLEAR_ARGB = 0x00000000;

    private static final int CAPTURE_ACTIVE = 1;

    private SimpleFramebuffer fbo;
    private int lastWidth = -1;
    private int lastHeight = -1;
    private long lastRefreshMs = 0L;
    private boolean dirty = true;
    private boolean hasContent = false;
    private int targetFps = 30;

    private Framebuffer realMainFramebuffer;
    private HudCapture capture;
    private final HudAnimationRefreshClock refreshClock = new HudAnimationRefreshClock(30);
    private long captureStartedNanos;

    private BatchedHudBuffer() {
    }

    public void setTargetFps(int fps) {
        this.targetFps = Math.max(1, Math.min(360, fps));
        refreshClock.setFps(this.targetFps);
    }

    public int getTargetFps() {
        return targetFps;
    }

    public void invalidate() {
        dirty = true;
    }

    public boolean hasContent() {
        return hasContent;
    }

    public SimpleFramebuffer getActiveCaptureFramebuffer() {
        return HudBuffer.activeCaptureFramebuffer == fbo ? fbo : null;
    }

    public Framebuffer getRealMainFramebuffer() {
        return realMainFramebuffer;
    }

    public boolean shouldRefresh(boolean force) {
        MinecraftClient client = MinecraftClient.getInstance();
        boolean resized = client != null && client.getWindow() != null
                && (lastWidth != client.getWindow().getFramebufferWidth()
                || lastHeight != client.getWindow().getFramebufferHeight());
        if (force || dirty || !hasContent || resized) {
            return true;
        }
        return refreshClock.due(System.nanoTime());
    }

    public boolean ensureFramebuffer() {
        try (Render2DState state = Render2DState.capture()) {
            return ensureFramebufferInternal();
        }
    }

    private boolean ensureFramebufferInternal() {
        RenderSystem.assertOnRenderThread();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null) {
            return false;
        }
        int w = mc.getWindow().getFramebufferWidth();
        int h = mc.getWindow().getFramebufferHeight();
        if (w <= 0 || h <= 0) {
            return false;
        }

        if (fbo == null || w != lastWidth || h != lastHeight) {
            if (capture != null && !capture.isClosed())
                throw new IllegalStateException("Cannot resize a capturing HUD framebuffer");
            if (fbo != null) {

                if (HudBuffer.activeCaptureFramebuffer == fbo) {
                    HudBuffer.restoreCaptureTarget(-1, null);
                }
                fbo.delete();
                fbo = null;
            }

            fbo = new SimpleFramebuffer("phaze/batched_hud", w, h, true);
            lastWidth = w;
            lastHeight = h;
            hasContent = false;
            dirty = true;
        }
        return true;
    }

    private boolean capturing;

    public boolean isCaptureActive() {
        return capturing && capture != null && !capture.isClosed();
    }

    public void beginCapture() {
        openCapture();
    }

    public HudCapture openCapture() {
        if (capture != null && capture.isClosed()) {
            capture = null;
            capturing = false;
            hasContent = false;
            dirty = true;
        }
        if (capturing) throw new IllegalStateException("Batched HUD is already capturing");
        HudPassState state = HudPassState.capture();
        try {
            if (!ensureFramebufferInternal()) {
                state.close();
                return null;
            }
            MinecraftClient mc = MinecraftClient.getInstance();
            if (HudBuffer.activeCaptureTarget < 0) realMainFramebuffer = mc != null ? mc.getFramebuffer() : null;
            clear();
            long started = System.nanoTime();
            captureStartedNanos = started;
            capture = HudCapture.attach(state, fbo, successful -> {
                capture = null;
                capturing = false;
                hasContent = successful && fbo != null;
                dirty = !hasContent;
                if (hasContent) {
                    lastRefreshMs = System.currentTimeMillis();
                    refreshClock.captured(started);
                }
            });
            capturing = true;
            return capture;
        } catch (RuntimeException | Error failure) {
            if (capture == null || capture.isClosed()) {
                try { state.close(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            }
            throw failure;
        }
    }

    private void clear() {
        clearFbo();
    }

    private void clearFbo() {
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        if (fbo.getDepthAttachment() != null) {
            encoder.clearColorAndDepthTextures(
                    fbo.getColorAttachment(), CLEAR_ARGB,
                    fbo.getDepthAttachment(), 1.0);
        } else {
            encoder.clearColorTexture(fbo.getColorAttachment(), CLEAR_ARGB);
        }
    }

    public void endCapture() {
        if (capture == null) return;
        capture.commit();
        endCapture(true);
    }

    public void endCapture(boolean successful) {
        if (!capturing) return;
        capture.checkCanClose();
        capturing = false;
        HudCapture started = capture;
        capture = null;
        hasContent = false;
        dirty = true;
        started.close(successful);
        successful &= started.wasSuccessful();
        if (successful && fbo != null) {
            hasContent = true;
            dirty = false;
            lastRefreshMs = System.currentTimeMillis();
            refreshClock.captured(captureStartedNanos);
        }
    }

    public void blit() {
        if (fbo == null || !hasContent) {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getFramebuffer() == null) {
            return;
        }

        HudBlit.draw(fbo, com.mojang.blaze3d.textures.FilterMode.NEAREST);
    }

    public void cleanup() {
        RenderSystem.assertOnRenderThread();
        if (capturing) {
            HudCapture.abortThrough(capture);
            endCapture(false);
        }

        if (HudBuffer.activeCaptureFramebuffer == fbo) {
            HudBuffer.activeCaptureFramebuffer = null;
            HudBuffer.activeCaptureTarget = -1;
        }
        if (fbo != null) {
            fbo.delete();
            fbo = null;
        }
        hasContent = false;
        dirty = true;
        lastWidth = -1;
        lastHeight = -1;
        realMainFramebuffer = null;
        refreshClock.reset();
    }
}
