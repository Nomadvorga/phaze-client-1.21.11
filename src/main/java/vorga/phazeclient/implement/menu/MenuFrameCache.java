package vorga.phazeclient.implement.menu;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ScissorState;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix3x2f;
import vorga.phazeclient.api.system.animation.FrameRepaint;
import vorga.phazeclient.api.system.cursor.CursorManager;
import vorga.phazeclient.api.system.draw.ScreenBlit;
import vorga.phazeclient.api.system.hud.HudCapture;
import vorga.phazeclient.api.system.shape.batched.GuiClipState;
import vorga.phazeclient.api.system.shape.batched.GuiDrawStats;

import java.util.function.Consumer;

/**
 * Menu content drawn into an offscreen target only when it can have changed, and composited with a
 * fullscreen blit otherwise. Input, a changed {@code key} or a {@link FrameRepaint} request redraws it;
 * anything else that changes on its own shows up within {@link #IDLE_REFRESH_NANOS}.
 *
 * <p>One {@linkplain #island live island} splits the content into a layer under it and a layer over it,
 * so a part that refreshes on its own clock is drawn every frame without redrawing the rest.
 */
public final class MenuFrameCache {
    private static final long IDLE_REFRESH_NANOS = 250_000_000L;
    /** Keeps drawing live for a moment after input, for effects that settle without reporting. */
    private static final long INPUT_SETTLE_NANOS = 150_000_000L;

    private static boolean enabled = true;
    private static MenuFrameCache capturing;

    private final Layer under = new Layer("phaze/menu_frame_under");
    private final Layer over = new Layer("phaze/menu_frame_over");
    private long repaintAtNanos = Long.MIN_VALUE;
    private long liveUntilNanos;
    private long key;
    private int cursorShape = CursorManager.SHAPE_ARROW;
    private int cursorPriority;

    private HudCapture capture;
    private boolean valid;
    private Island island;

    /** A window-sized premultiplied target; not a HUD buffer, so HUD refresh budgets never see it. */
    private static final class Layer {
        private final String name;
        private SimpleFramebuffer framebuffer;
        private boolean content;

        private Layer(String name) {
            this.name = name;
        }

        HudCapture openCapture() {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.getWindow() == null) return null;
            int width = client.getWindow().getFramebufferWidth(), height = client.getWindow().getFramebufferHeight();
            if (width <= 0 || height <= 0) return null;
            if (framebuffer == null || framebuffer.textureWidth != width || framebuffer.textureHeight != height) {
                if (framebuffer != null) framebuffer.delete();
                framebuffer = new SimpleFramebuffer(name, width, height, true);
            }
            content = false;
            RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
                    framebuffer.getColorAttachment(), 0, framebuffer.getDepthAttachment(), 1.0);
            return HudCapture.begin(framebuffer);
        }

        void closed(HudCapture capture) {
            content = capture.wasSuccessful();
        }

        boolean hasContent() {
            return content && framebuffer != null;
        }

        boolean drawCached() {
            if (!hasContent()) return false;
            ScreenBlit.blitOverMain(framebuffer, FilterMode.NEAREST);
            return true;
        }

        void cleanup() {
            if (framebuffer != null) framebuffer.delete();
            framebuffer = null;
            content = false;
        }
    }

    private record Clip(boolean enabled, int x, int y, int width, int height,
                        boolean guiEnabled, int guiX, int guiY, int guiWidth, int guiHeight) {
        static Clip current() {
            ScissorState state = RenderSystem.getScissorStateForRenderTypeDraws();
            return new Clip(state.isEnabled(), state.getX(), state.getY(), state.getWidth(), state.getHeight(),
                    GuiClipState.isEnabled(), GuiClipState.boxX(), GuiClipState.boxY(),
                    GuiClipState.boxWidth(), GuiClipState.boxHeight());
        }

        void apply() {
            if (enabled) RenderSystem.enableScissorForRenderTypeDraws(x, y, width, height);
            else RenderSystem.disableScissorForRenderTypeDraws();
            if (guiEnabled) GuiClipState.onBox(guiX, guiY, guiWidth, guiHeight);
            else GuiClipState.onDisable();
        }
    }

    private record Island(Consumer<DrawContext> draw, Matrix3x2f matrix, Clip clip) {
        void run(DrawContext context) {
            Clip outer = Clip.current();
            context.getMatrices().pushMatrix();
            try {
                context.getMatrices().set(matrix);
                clip.apply();
                draw.accept(context);
            } finally {
                context.getMatrices().popMatrix();
                outer.apply();
            }
        }
    }

    static void setEnabled(boolean value) {
        enabled = value;
    }

    /**
     * Draws {@code draw} live every frame at this point of the content instead of into the cache. It must
     * not depend on anything but the matrix and clip it is drawn with, and must not request a repaint.
     */
    public static void island(DrawContext context, Consumer<DrawContext> draw) {
        MenuFrameCache cache = capturing;
        if (cache == null || cache.island != null) {
            draw.accept(context);
            if (cache != null) FrameRepaint.now();
            return;
        }
        cache.splitAt(context, draw);
    }

    void invalidate() {
        liveUntilNanos = System.nanoTime() + INPUT_SETTLE_NANOS;
    }

    void render(DrawContext context, long key, boolean cacheable, Runnable content) {
        long now = System.nanoTime();
        if (!enabled || !cacheable || HudCapture.isActive() || capturing != null) {
            valid = false;
            content.run();
            return;
        }
        if (valid && key == this.key && now >= liveUntilNanos && now < repaintAtNanos) {
            CursorManager.replayRequest(cursorShape, cursorPriority);
            if (replay(context)) {
                GuiDrawStats.menuReplays++;
                return;
            }
        }

        int shapeBefore = CursorManager.requestedShape(), priorityBefore = CursorManager.requestedPriority();
        valid = false;
        island = null;
        FrameRepaint.reset();
        capture = under.openCapture();
        if (capture == null) {
            content.run();
            return;
        }
        capturing = this;
        boolean drawn = false;
        try {
            content.run();
            drawn = true;
        } finally {
            capturing = null;
            HudCapture open = capture;
            capture = null;
            if (open != null) {
                if (drawn) open.commit();
                open.close();
                (island == null ? under : over).closed(open);
            }
        }
        GuiDrawStats.menuCaptures++;
        valid = (island == null ? under : over).hasContent();
        this.key = key;
        boolean cursorChanged = CursorManager.requestedShape() != shapeBefore
                || CursorManager.requestedPriority() != priorityBefore;
        cursorShape = cursorChanged ? CursorManager.requestedShape() : CursorManager.SHAPE_ARROW;
        cursorPriority = cursorChanged ? CursorManager.requestedPriority() : 0;
        repaintAtNanos = Math.min(FrameRepaint.deadline(), now + IDLE_REFRESH_NANOS);
        FrameRepaint.reset();
        (island == null ? under : over).drawCached();
    }

    private void splitAt(DrawContext context, Consumer<DrawContext> draw) {
        Clip clip = Clip.current();
        Island split = new Island(draw, new Matrix3x2f(context.getMatrices()), clip);
        HudCapture open = capture;
        capture = null;
        open.commit();
        open.close();
        under.closed(open);
        if (!under.hasContent()) throw new IllegalStateException("Menu layer under the island was not captured");
        under.drawCached();
        island = split;
        split.run(context);
        capture = over.openCapture();
        if (capture == null) throw new IllegalStateException("Menu layer over the island cannot be captured");
        clip.apply();
    }

    private boolean replay(DrawContext context) {
        if (island == null) return under.drawCached();
        if (!under.hasContent() || !over.hasContent()) return false;
        under.drawCached();
        island.run(context);
        return over.drawCached();
    }

    void release() {
        under.cleanup();
        over.cleanup();
        valid = false;
        island = null;
        repaintAtNanos = Long.MIN_VALUE;
    }
}
