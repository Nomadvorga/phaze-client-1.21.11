package vorga.phazeclient.api.system.hud;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;

public class HudBuffer {
    private static final java.util.Set<HudBuffer> BUFFERS = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    private HudCapture capture;

    public HudBuffer() { this(0); }

    /** 0 = nearest composition, 1 = linear. The editor buffer owns the linear policy. */
    public HudBuffer(int textureFilter) {
        this.textureFilter = textureFilter;
        BUFFERS.add(this);
    }

    private static final int CLEAR_ARGB = 0x00000000;

    public static final int CAPTURE_ACTIVE = 1;

    public static volatile int activeCaptureTarget = -1;

    public static volatile Framebuffer activeCaptureFramebuffer = null;

    private final int textureFilter;
    private final HudAnimationRefreshClock refreshClock = new HudAnimationRefreshClock(60);

    private SimpleFramebuffer framebuffer;
    private long lastRenderTimeMs = 0;
    private long lastLogicUpdateTimeMs = 0;
    private long lastDataUpdateTimeMs = 0;
    private float accumulatedLogicDeltaSeconds = 0.0f;
    private boolean hasContent = false;
    private int lastScreenWidth = 0;
    private int lastScreenHeight = 0;
    private long captureStartedNanos;

    public static Framebuffer getActiveCaptureFramebuffer() {
        return activeCaptureTarget >= 0 ? activeCaptureFramebuffer : null;
    }

    public static void restoreCaptureTarget(int target, Framebuffer framebuffer) {
        if (activeCaptureTarget == target && activeCaptureFramebuffer == framebuffer) return;
        vorga.phazeclient.api.system.font.msdf.MsdfBatch.flush();
        activeCaptureTarget = target;
        activeCaptureFramebuffer = framebuffer;
    }

    public boolean shouldUpdate(int targetFps) {
        if (targetFps <= 0) {
            return true;
        }
        refreshClock.setFps(targetFps);
        MinecraftClient client = MinecraftClient.getInstance();
        boolean resized = client != null && client.getWindow() != null
                && (lastScreenWidth != client.getWindow().getFramebufferWidth()
                || lastScreenHeight != client.getWindow().getFramebufferHeight());
        return !hasContent || resized || refreshClock.due(System.nanoTime());
    }

    public float getThrottledDelta(int targetFps, float deltaSeconds) {
        if (targetFps <= 0) {
            return deltaSeconds;
        }

        long now = System.currentTimeMillis();
        long intervalMs = 1000L / targetFps;
        accumulatedLogicDeltaSeconds += deltaSeconds;
        if (lastLogicUpdateTimeMs == 0 || now - lastLogicUpdateTimeMs >= intervalMs) {
            float result = accumulatedLogicDeltaSeconds;
            accumulatedLogicDeltaSeconds = 0.0f;
            lastLogicUpdateTimeMs = now;
            return result;
        }
        return 0.0f;
    }

    public boolean shouldUpdateData(int targetFps) {
        if (targetFps <= 0) {
            return true;
        }

        long now = System.currentTimeMillis();
        long intervalMs = 1000L / targetFps;
        if (lastDataUpdateTimeMs == 0 || now - lastDataUpdateTimeMs >= intervalMs) {
            lastDataUpdateTimeMs = now;
            return true;
        }
        return false;
    }

    public void beginCapture() {
        openCapture();
    }

    public HudCapture openCapture() {
        RenderSystem.assertOnRenderThread();
        if (capture != null && capture.isClosed()) { capture = null; invalidate(); }
        if (capture != null) throw new IllegalStateException("HUD buffer is already capturing");
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null) return null;
        int width = mc.getWindow().getFramebufferWidth();
        int height = mc.getWindow().getFramebufferHeight();
        if (width <= 0 || height <= 0) return null;

        HudPassState state = HudPassState.capture();
        try {
            if (framebuffer == null || lastScreenWidth != width || lastScreenHeight != height) {
                if (framebuffer != null) {
                    framebuffer.delete();
                    framebuffer = null;
                }
                framebuffer = new SimpleFramebuffer("phaze/hud_buffer", width, height, true);
                lastScreenWidth = width;
                lastScreenHeight = height;
                hasContent = false;
            }
            clear();
            long started = System.nanoTime();
            captureStartedNanos = started;
            capture = HudCapture.attach(state, framebuffer, successful -> {
                capture = null;
                hasContent = successful;
                if (successful) {
                    lastRenderTimeMs = System.currentTimeMillis();
                    refreshClock.captured(started);
                } else {
                    invalidate();
                }
            });
            return capture;
        } catch (RuntimeException | Error failure) {
            if (capture == null) {
                try { state.close(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            }
            throw failure;
        }
    }

    public void endCapture() {
        if (capture == null) return;
        capture.commit();
        endCapture(true);
    }

    public void endCapture(boolean successful) {
        HudCapture started = capture;
        if (started == null) return;
        if (!started.isClosed()) started.checkCanClose();
        capture = null;
        hasContent = false;
        try {
            if (!started.isClosed()) {
                if (successful) started.commit();
                started.close(successful);
                successful &= started.wasSuccessful();
            } else {
                successful = false;
            }
            hasContent = successful;
            if (successful) {
                lastRenderTimeMs = System.currentTimeMillis();
                refreshClock.captured(captureStartedNanos);
            }
        } finally {
            if (!successful) invalidate();
        }
    }

    public void bindCaptureTarget() {
        if (framebuffer != null) {
            restoreCaptureTarget(CAPTURE_ACTIVE, framebuffer);
        }
    }

    private void clear() {
        clearCaptureTarget();
    }

    private void clearCaptureTarget() {
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        if (framebuffer.getDepthAttachment() != null) {
            encoder.clearColorAndDepthTextures(
                    framebuffer.getColorAttachment(), CLEAR_ARGB,
                    framebuffer.getDepthAttachment(), 1.0);
        } else {
            encoder.clearColorTexture(framebuffer.getColorAttachment(), CLEAR_ARGB);
        }
    }

    public boolean hasContent() {
        return hasContent;
    }

    public boolean drawCached() {
        if (framebuffer == null || !hasContent) {
            return false;
        }
        return HudBlit.draw(framebuffer, samplerFilter());
    }

    public boolean drawCached(HudCachedTransform captured, float x, float y, float scale) {
        return framebuffer != null && hasContent && captured != null
                && HudBlit.drawTransformed(framebuffer, captured, x, y, scale, samplerFilter());
    }

    /** GL_LINEAR (9729) and the editor's linear policy (1) sample linearly. Everything else stays nearest. */
    private FilterMode samplerFilter() {
        return textureFilter == 9729 || textureFilter == 1 ? FilterMode.LINEAR : FilterMode.NEAREST;
    }

    public void invalidate() {
        hasContent = false;
        refreshClock.reset();
        lastLogicUpdateTimeMs = 0;
        lastDataUpdateTimeMs = 0;
        accumulatedLogicDeltaSeconds = 0.0f;
    }

    public void cleanup() {
        RenderSystem.assertOnRenderThread();
        if (capture != null) {
            HudCapture.abortThrough(capture);
            capture = null;
        }
        if (activeCaptureFramebuffer == framebuffer) {
            restoreCaptureTarget(-1, null);
        }
        if (framebuffer != null) {
            framebuffer.delete();
            framebuffer = null;
        }
        invalidate();
        lastRenderTimeMs = 0;
        lastScreenWidth = lastScreenHeight = 0;
    }

    public static void abortAllCaptures() {
        try { HudCapture.abortAll(); }
        finally {
            for (HudBuffer buffer : BUFFERS) {
                if (buffer.capture != null) buffer.invalidate();
                buffer.capture = null;
            }
        }
    }

    public static void cleanupAll() {
        for (HudBuffer buffer : java.util.List.copyOf(BUFFERS)) buffer.cleanup();
    }

    public static void resetCaptureTarget() {
        restoreCaptureTarget(-1, null);
    }
}
