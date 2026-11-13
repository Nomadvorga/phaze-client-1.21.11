package vorga.phazeclient.api.system.shape.batched;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.util.BufferAllocator;
import org.joml.Matrix4f;
import vorga.phazeclient.api.system.font.msdf.MsdfRenderer;
import vorga.phazeclient.api.system.hud.HudBuffer;
import vorga.phazeclient.api.system.hud.HudPassState;
import vorga.phazeclient.mixins.GameRendererGuiAccessor;

/** Orders the deferred 1.21.11 GUI queue against immediate Phaze draw passes. */
public final class GuiBatch {
    /** Quarter-turn UVs, matching source {@code UiMsdfIconAtlas} legacy icons. */
    public static final int UV_SWAP = 1 << 7;

    private static final Run RUN = new Run();
    private static DrawContext context;
    private static boolean flushing;
    private static int flushHold;
    private static boolean drawingMsdf;
    private static boolean pending;
    private static BufferAllocator allocator;
    private static BufferBuilder builder;
    private static int quads;
    private static boolean clipEnabled;
    private static int clipX, clipY, clipW, clipH;
    private static int captureTarget = Integer.MIN_VALUE;
    private static Framebuffer captureFramebuffer;

    private GuiBatch() {}

    public static void use(DrawContext value) {
        if (context == value) return;
        flush();
        context = value;
    }

    public static void queued(DrawContext value) {
        if (flushing) return;
        if (context != value) use(value);
        queuedState(value.state);
    }

    public static boolean isFlushing() { return flushing; }

    public static DrawContext currentContext() { return context; }

    /** Keeps a foreign draw from flushing this batch in the middle of its own upload. */
    public static void holdFlush() {
        flushHold++;
        flushing = true;
    }

    public static void dropFlush() {
        if (flushHold > 0) flushHold--;
        flushing = flushHold > 0;
    }

    public static void queuedState(GuiRenderState state) {
        if (flushing) return;
        var client = MinecraftClient.getInstance();
        if (client != null && client.gameRenderer != null
                && state == ((GameRendererGuiAccessor) client.gameRenderer).phaze$getGuiState()) {
            BatchedRectangle.flushIfBatching();
            pending = true;
        }
    }

    public static void clearedState(GuiRenderState state) {
        var client = MinecraftClient.getInstance();
        if (client != null && client.gameRenderer != null
                && state == ((GameRendererGuiAccessor) client.gameRenderer).phaze$getGuiState()) pending = false;
    }

    /**
     * Opens or continues one MSDF run on {@code phaze:pipeline/msdf_font}.
     * Consecutive calls with the same atlas, range, thickness, smoothness, label fade,
     * {@link GuiClipState} and capture framebuffer append to the same {@link Run}
     * and become one draw. The returned run is the shared instance.
     *
     * <p>Ui callers replace {@code MsdfBatch.beginIcon} with
     * {@code GuiBatch.msdf(view, range, thickness, smoothness, 0f, 0f, 0f, 0f, 0f)}
     * and write quads through {@link Run#quad}.
     */
    public static Run msdf(GpuTextureView atlas, float range, float thickness, float smoothness,
                           float fadeLeft, float fadeRight, float fadeWidth,
                           float fadeLeftStrength, float fadeRightStrength) {
        return msdf(atlas, range, thickness, smoothness,
                fadeEffects(fadeLeft, fadeRight, fadeWidth, fadeLeftStrength, fadeRightStrength), 0.0F);
    }

    /**
     * Text entry. {@code textX} is the fadeout origin and is ignored unless fadeout is enabled.
     * Icons use the seven-float overload, which stores {@link MsdfRenderer.TextEffects#NONE}.
     */
    public static Run msdf(GpuTextureView atlas, float range, float thickness, float smoothness,
                           MsdfRenderer.TextEffects effects, float textX) {
        RenderSystem.assertOnRenderThread();
        if (atlas == null) throw new IllegalArgumentException("MSDF atlas is required");
        if (effects == null) effects = MsdfRenderer.TextEffects.NONE;
        if (!effects.fadeout() && effects.fadeWidth() <= 0.0F) textX = 0.0F;
        if (!Float.isFinite(range) || !Float.isFinite(thickness) || !Float.isFinite(smoothness)
                || !Float.isFinite(textX) || !Float.isFinite(effects.outlineThickness())
                || !Float.isFinite(effects.fadeStart()) || !Float.isFinite(effects.fadeEnd())
                || !Float.isFinite(effects.maxWidth()) || !Float.isFinite(effects.left())
                || !Float.isFinite(effects.right()) || !Float.isFinite(effects.fadeWidth())
                || !Float.isFinite(effects.leftStrength()) || !Float.isFinite(effects.rightStrength())) {
            throw new IllegalArgumentException("MSDF parameters must be finite");
        }
        if (drawingMsdf) return RUN;
        if (!flushing) {
            BatchedRectangle.flush();
            flushVanilla();
            if (builder != null && !same(atlas, range, thickness, smoothness, effects, textX)) flushMsdf();
        } else if (builder != null && !same(atlas, range, thickness, smoothness, effects, textX)) {
            return RUN;
        }
        RUN.assign(atlas, range, thickness, smoothness, effects, textX);
        if (builder == null) openBuilder();
        return RUN;
    }

    public static void discard() {
        discardMsdf();
        vorga.phazeclient.api.system.font.msdf.MsdfBatch.discard();
        BatchedRectangle.discardPending();
        if (pending) {
            ((GameRendererGuiAccessor) MinecraftClient.getInstance().gameRenderer).phaze$getGuiState().clear();
        }
        pending = false;
    }

    /** Drops queued MSDF quads without drawing them. */
    public static void discardMsdf() {
        builder = null;
        quads = 0;
        RUN.atlas = null;
        if (!drawingMsdf) drain();
    }

        public static void flush() {
        if (flushing) return;
        BatchedRectangle.flushIfBatching();
        vorga.phazeclient.api.system.font.msdf.MsdfBatch.flush();
        flushVanilla();
    }

    /**
     * Draws the open MSDF run. Also runs on a key change, a {@link GuiClipState} change,
     * a capture-target change, a rectangle submit, scope end, and {@link #flush()}.
     */
    public static void flushMsdf() {
        if (builder == null || flushing || drawingMsdf) return;
        BufferBuilder pendingBuilder = builder;
        int instances = quads;
        GpuTextureView view = RUN.atlas;
        boolean scissor = clipEnabled;
        int sx = clipX, sy = clipY, sw = clipW, sh = clipH;
        int target = captureTarget;
        Framebuffer framebuffer = captureFramebuffer;
        MsdfRenderer.TextEffects effects = RUN.effects;
        float textX = RUN.textX;
        builder = null;
        quads = 0;
        BuiltBuffer built = null;
        try {
            if (instances <= 0 || view == null) return;
            built = pendingBuilder.endNullable();
            if (built == null) return;
            GuiDrawStats.batchInstances += instances;
            drawingMsdf = true;
            holdFlush();
            try (HudPassState ignored = HudPassState.capture()) {
                if (target >= 0 && framebuffer != null && framebuffer.getColorAttachmentView() != null) {
                    RenderSystem.outputColorTextureOverride = framebuffer.getColorAttachmentView();
                    RenderSystem.outputDepthTextureOverride = framebuffer.getDepthAttachmentView();
                } else {
                    RenderSystem.outputColorTextureOverride = null;
                    RenderSystem.outputDepthTextureOverride = null;
                }
                if (scissor) RenderSystem.enableScissorForRenderTypeDraws(sx, sy, sw, sh);
                else RenderSystem.disableScissorForRenderTypeDraws();
                MsdfRenderer.drawBatched(built, view, effects, textX);
            } finally {
                drawingMsdf = false;
                dropFlush();
            }
        } finally {
            if (built != null) built.close();
            if (builder == null) drain();
        }
    }

    public static void flushVanilla() {
        if (flushing || !pending) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.gameRenderer == null) return;
        GameRendererGuiAccessor access = (GameRendererGuiAccessor) client.gameRenderer;
        // Only global-state elements mark pending; a foreign preview queue is never submitted here.
        RenderSystem.assertOnRenderThread();
        pending = false;
        holdFlush();
        try (HudPassState ignored = HudPassState.capture()) {
            access.phaze$getGuiRenderer().render(access.phaze$getFogRenderer().getFogBuffer(FogRenderer.FogType.NONE));
        } finally {
            dropFlush();
        }
    }

    public static void close() {
        discard();
        context = null;
        pending = false;
        flushing = false;
        flushHold = 0;
        BufferAllocator old = allocator;
        allocator = null;
        if (old != null) old.close();
    }

    private static void openBuilder() {
        if (RUN.atlas == null || drawingMsdf) return;
        if (allocator == null) allocator = new BufferAllocator(16 * 1024);
        builder = new BufferBuilder(allocator, VertexFormat.DrawMode.QUADS, MsdfRenderer.format());
        quads = 0;
        clipEnabled = GuiClipState.isEnabled();
        clipX = GuiClipState.boxX();
        clipY = GuiClipState.boxY();
        clipW = GuiClipState.boxWidth();
        clipH = GuiClipState.boxHeight();
        captureTarget = HudBuffer.activeCaptureTarget;
        captureFramebuffer = HudBuffer.activeCaptureFramebuffer;
    }

    private static boolean same(GpuTextureView atlas, float range, float thickness, float smoothness,
                                MsdfRenderer.TextEffects effects, float textX) {
        return RUN.atlas == atlas
                && RUN.range == range
                && RUN.thickness == thickness
                && RUN.smoothness == smoothness
                && textX == RUN.textX
                && effects.equals(RUN.effects)
                && clipMatches()
                && captureMatches();
    }

    private static MsdfRenderer.TextEffects cachedFade = MsdfRenderer.TextEffects.NONE;

    private static MsdfRenderer.TextEffects fadeEffects(float left, float right, float width,
                                                        float leftStrength, float rightStrength) {
        if (left == 0.0F && right == 0.0F && width <= 0.0F && leftStrength == 0.0F && rightStrength == 0.0F) {
            return MsdfRenderer.TextEffects.NONE;
        }
        MsdfRenderer.TextEffects cached = cachedFade;
        if (cached.left() == left && cached.right() == right && cached.fadeWidth() == width
                && cached.leftStrength() == leftStrength && cached.rightStrength() == rightStrength
                && !cached.outline() && !cached.fadeout()) {
            return cached;
        }
        cachedFade = cached = new MsdfRenderer.TextEffects(false, 0.0F, 0, false, 0.0F, 1.0F, 0.0F,
                left, right, width, leftStrength, rightStrength);
        return cached;
    }

    private static boolean clipMatches() {
        return clipEnabled == GuiClipState.isEnabled()
                && clipX == GuiClipState.boxX()
                && clipY == GuiClipState.boxY()
                && clipW == GuiClipState.boxWidth()
                && clipH == GuiClipState.boxHeight();
    }

    private static boolean captureMatches() {
        return captureTarget == HudBuffer.activeCaptureTarget
                && captureFramebuffer == HudBuffer.activeCaptureFramebuffer;
    }

    private static void emit(Matrix4f matrix, float x, float y, float w, float h, float z,
                             float u0, float v0, float u1, float v1, int color, boolean swap) {
        float x1 = x + w;
        float y1 = y + h;
        float leftU, leftV, bottomU, bottomV, rightU, rightV, topU, topV;
        if (swap) {
            leftU = u0; leftV = v0;
            bottomU = u1; bottomV = v0;
            rightU = u1; rightV = v1;
            topU = u0; topV = v1;
        } else {
            leftU = u0; leftV = v0;
            bottomU = u0; bottomV = v1;
            rightU = u1; rightV = v1;
            topU = u1; topV = v0;
        }
        float range = RUN.range;
        float thickness = RUN.thickness;
        float smoothness = RUN.smoothness;
        MsdfRenderer.vertex(builder, matrix, x, y, z, leftU, leftV, color, range, thickness, smoothness);
        MsdfRenderer.vertex(builder, matrix, x, y1, z, bottomU, bottomV, color, range, thickness, smoothness);
        MsdfRenderer.vertex(builder, matrix, x1, y1, z, rightU, rightV, color, range, thickness, smoothness);
        MsdfRenderer.vertex(builder, matrix, x1, y, z, topU, topV, color, range, thickness, smoothness);
    }

    private static void drain() {
        if (allocator == null) return;
        try (var unused = allocator.getAllocated()) {
        } finally {
            allocator.clear();
        }
    }

    /**
     * Quads for one atlas and parameter set. {@code u} runs {@code u0..u1} along x and
     * {@code v} runs {@code v0..v1} along y, or, with {@link #UV_SWAP}, {@code u} along y and {@code v} along x.
     */
    public static final class Run {
        private GpuTextureView atlas;
        private float range, thickness, smoothness;
        private float textX;
        private MsdfRenderer.TextEffects effects = MsdfRenderer.TextEffects.NONE;

        private Run() {}

        private void assign(GpuTextureView view, float msdfRange, float msdfThickness, float msdfSmoothness,
                            MsdfRenderer.TextEffects textEffects, float originX) {
            atlas = view;
            range = msdfRange;
            thickness = msdfThickness;
            smoothness = msdfSmoothness;
            effects = textEffects;
            textX = originX;
        }

        public void quad(Matrix4f matrix, float x, float y, float w, float h, float z,
                         float u0, float v0, float u1, float v1, int color, int flags) {
            if (atlas == null || drawingMsdf || matrix == null) return;
            if (!flushing && builder != null && (!clipMatches() || !captureMatches())) {
                vorga.phazeclient.api.system.font.msdf.MsdfBatch.flush();
            }
            if (builder == null) openBuilder();
            if (builder == null) return;
            emit(matrix, x, y, w, h, z, u0, v0, u1, v1, color, (flags & UV_SWAP) != 0);
            quads++;
        }
    }
}
