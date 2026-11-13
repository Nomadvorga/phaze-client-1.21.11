package vorga.phazeclient.api.system.snapshot;

import vorga.phazeclient.base.util.render.GuiMatrix;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.ProjectionMatrix2;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import vorga.phazeclient.api.system.draw.GpuDraw;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.api.system.shape.implement.Rectangle;
import vorga.phazeclient.api.system.shape.batched.BatchedRectangle;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gl.UniformType;

public final class CardSnapshotCache {
    private static final Rectangle RECTANGLE = new Rectangle();

    private static final Map<Object, Snapshot> CACHE = new IdentityHashMap<>();

    private static CaptureState active = null;

    private static ProjectionMatrix2 cardProjection;

    private static final Matrix4f BLIT_GUI_POSE = new Matrix4f();

    private CardSnapshotCache() {
    }

    public static final class Snapshot {

        Framebuffer fbo;
        int fbWidth;
        int fbHeight;

        public int hash;
        public boolean populated;
    }

    private static final class CaptureState {
        Snapshot snapshot;
        vorga.phazeclient.api.system.hud.HudCapture capture;
        vorga.phazeclient.api.system.draw.GuiProjection.TargetSize targetSize;
    }

    public static Snapshot getOrCreate(Object key, int fbWidth, int fbHeight) {
        RenderSystem.assertOnRenderThread();
        if (fbWidth <= 0 || fbHeight <= 0) throw new IllegalArgumentException("Invalid snapshot size");
        Snapshot snap = CACHE.computeIfAbsent(key, k -> new Snapshot());
        if (active != null && active.snapshot == snap) throw new IllegalStateException("Snapshot is capturing");
        if (snap.fbo == null || snap.fbWidth != fbWidth || snap.fbHeight != fbHeight) {
            if (snap.fbo != null) {
                snap.fbo.delete();
            }
            snap.fbo = new SimpleFramebuffer("phaze/card_snapshot", fbWidth, fbHeight, true);
            snap.fbWidth = fbWidth;
            snap.fbHeight = fbHeight;
            snap.populated = false;
        }
        return snap;
    }

    public static void invalidate(Object key) {
        RenderSystem.assertOnRenderThread();
        Snapshot snap = CACHE.remove(key);
        if (active != null && active.snapshot == snap) {
            vorga.phazeclient.api.system.hud.HudCapture.abortThrough(active.capture);
            endCapture(false);
        }
        if (snap != null && snap.fbo != null) {
            snap.fbo.delete();
        }
    }

    public static void clearAll() {
        RenderSystem.assertOnRenderThread();
        if (active != null) {
            vorga.phazeclient.api.system.hud.HudCapture.abortThrough(active.capture);
            endCapture(false);
        }
        List<Snapshot> snapshots = new ArrayList<>(CACHE.values());
        CACHE.clear();
        for (Snapshot s : snapshots) {
            if (s.fbo != null) {
                s.fbo.delete();
            }
        }
        if (cardProjection != null) { cardProjection.close(); cardProjection = null; }
    }

    public static void beginCapture(Snapshot snapshot, float widthGui, float heightGui) {
        if (snapshot == null || snapshot.fbo == null) {
            return;
        }
        RenderSystem.assertOnRenderThread();
        if (active != null) throw new IllegalStateException("A card snapshot is already capturing");
        if (widthGui <= 0 || heightGui <= 0 || !Float.isFinite(widthGui) || !Float.isFinite(heightGui))
            throw new IllegalArgumentException("Invalid snapshot GUI size");
        vorga.phazeclient.api.system.shape.batched.GuiBatch.flush();

        CaptureState s = new CaptureState();
        s.snapshot = snapshot;
        snapshot.populated = false;
        active = s;
        try {
        s.capture = vorga.phazeclient.api.system.hud.HudCapture.begin(snapshot.fbo);
        s.targetSize = vorga.phazeclient.api.system.draw.GuiProjection.targetSize(widthGui, heightGui);

        GpuTexture color = snapshot.fbo.getColorAttachment();
        GpuTexture depth = snapshot.fbo.getDepthAttachment();
        if (depth != null) {
            RenderSystem.getDevice().createCommandEncoder()
                    .clearColorAndDepthTextures(color, 0x00000000, depth, 1.0);
        } else {
            RenderSystem.getDevice().createCommandEncoder().clearColorTexture(color, 0x00000000);
        }

        if (cardProjection == null) {
            cardProjection = new ProjectionMatrix2("phaze/card snapshot", -1000.0F, 1000.0F, true);
        }
        RenderSystem.setProjectionMatrix(
                cardProjection.set(widthGui, heightGui), ProjectionType.ORTHOGRAPHIC);

        } catch (RuntimeException | Error failure) {
            try { endCapture(false); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    public static void endCapture() {
        endCapture(true);
    }

    public static void endCapture(boolean successful) {
        if (active == null) {
            return;
        }
        CaptureState s = active;
        if (s.capture != null) s.capture.checkCanClose();
        active = null;

        try {
            if (s.capture != null) s.capture.close(successful);
            s.snapshot.populated = successful && s.capture != null && s.capture.wasSuccessful();
        } finally {
            if (s.targetSize != null) s.targetSize.close();
        }
    }

    private static final RenderPipeline TRANSLUCENT_PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.of("phaze", "pipeline/card_snapshot"))
            .withVertexShader(Identifier.of("minecraft", "core/position_tex_color"))
            .withFragmentShader(Identifier.of("minecraft", "core/position_tex_color"))
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withSampler("Sampler0")
            .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
            .withBlend(new BlendFunction(SourceFactor.SRC_ALPHA, DestFactor.ONE_MINUS_SRC_ALPHA,
                    SourceFactor.ONE, DestFactor.ZERO))
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build();

    private static final RenderPipeline DST_ALPHA_PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.of("phaze", "pipeline/card_snapshot_rounded"))
            .withVertexShader(Identifier.of("minecraft", "core/position_tex_color"))
            .withFragmentShader(Identifier.of("minecraft", "core/position_tex_color"))
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withSampler("Sampler0")
            .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
            .withBlend(new BlendFunction(SourceFactor.DST_ALPHA, DestFactor.ONE_MINUS_DST_ALPHA))
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build();

    public static void blit(DrawContext context, Snapshot snapshot, float x, float y, float widthGui, float heightGui, float alpha) {
        if (snapshot == null || snapshot.fbo == null || !snapshot.populated) {
            return;
        }

        BatchedRectangle.flushIfBatching();

        Matrix4f matrix = GuiMatrix.mat4(context.getMatrices());

        int alphaByte = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
        int color = (alphaByte << 24) | 0x00FFFFFF;

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        buffer.vertex(matrix, x, y, 0.0F).texture(0.0F, 1.0F).color(color);
        buffer.vertex(matrix, x, y + heightGui, 0.0F).texture(0.0F, 0.0F).color(color);
        buffer.vertex(matrix, x + widthGui, y + heightGui, 0.0F).texture(1.0F, 0.0F).color(color);
        buffer.vertex(matrix, x + widthGui, y, 0.0F).texture(1.0F, 1.0F).color(color);

        BuiltBuffer built = buffer.endNullable();
        if (built != null) {

            vorga.phazeclient.api.system.draw.GuiProjection.begin();
            try {

                GpuDraw.draw(TRANSLUCENT_PIPELINE, built, "Sampler0",
                        snapshot.fbo.getColorAttachmentView(), FilterMode.LINEAR,
                        vorga.phazeclient.api.system.draw.GuiProjection.guiModelView(BLIT_GUI_POSE));
            } finally {
                vorga.phazeclient.api.system.draw.GuiProjection.end();
                built.close();
            }
        }
    }

    private static final RenderPipeline PREMULTIPLIED_PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.of("phaze", "pipeline/card_snapshot_premultiplied"))
            .withVertexShader(Identifier.of("minecraft", "core/position_tex_color"))
            .withFragmentShader(Identifier.of("minecraft", "core/position_tex_color"))
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withSampler("Sampler0")
            .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build();

    /** For captures of GUI special elements, whose composite already premultiplies colour by alpha. */
    public static void blitPremultiplied(DrawContext context, Snapshot snapshot, float x, float y,
                                         float widthGui, float heightGui, float alpha) {
        if (snapshot == null || snapshot.fbo == null || !snapshot.populated) {
            return;
        }
        BatchedRectangle.flushIfBatching();
        Matrix4f matrix = GuiMatrix.mat4(context.getMatrices());
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
        int color = (a << 24) | (a << 16) | (a << 8) | a;
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        buffer.vertex(matrix, x, y, 0.0F).texture(0.0F, 1.0F).color(color);
        buffer.vertex(matrix, x, y + heightGui, 0.0F).texture(0.0F, 0.0F).color(color);
        buffer.vertex(matrix, x + widthGui, y + heightGui, 0.0F).texture(1.0F, 0.0F).color(color);
        buffer.vertex(matrix, x + widthGui, y, 0.0F).texture(1.0F, 1.0F).color(color);
        BuiltBuffer built = buffer.endNullable();
        if (built == null) return;
        vorga.phazeclient.api.system.draw.GuiProjection.begin();
        try {
            GpuDraw.draw(PREMULTIPLIED_PIPELINE, built, "Sampler0",
                    snapshot.fbo.getColorAttachmentView(), FilterMode.LINEAR,
                    vorga.phazeclient.api.system.draw.GuiProjection.guiModelView(BLIT_GUI_POSE));
        } finally {
            vorga.phazeclient.api.system.draw.GuiProjection.end();
            built.close();
        }
    }

    /**
     * Blocking alpha readback, meant for a one-off validation: a shader pack can redirect the capture
     * and leave the snapshot empty. An unreadable snapshot counts as visible, so it is never discarded blindly.
     */
    public static boolean hasVisiblePixels(Snapshot snapshot) {
        if (snapshot == null || snapshot.fbo == null || !snapshot.populated) return false;
        RenderSystem.assertOnRenderThread();
        GpuTexture color = snapshot.fbo.getColorAttachment();
        int bytes = color.getWidth(0) * color.getHeight(0) * 4;
        com.mojang.blaze3d.buffers.GpuBuffer buffer = RenderSystem.getDevice().createBuffer(
                () -> "phaze/card snapshot readback",
                com.mojang.blaze3d.buffers.GpuBuffer.USAGE_COPY_DST | com.mojang.blaze3d.buffers.GpuBuffer.USAGE_MAP_READ,
                bytes);
        int[] result = {-1};
        try {
            RenderSystem.getDevice().createCommandEncoder().copyTextureToBuffer(color, buffer, 0L, () -> {
                try (com.mojang.blaze3d.buffers.GpuBuffer.MappedView view =
                             RenderSystem.getDevice().createCommandEncoder().mapBuffer(buffer, true, false)) {
                    java.nio.ByteBuffer data = view.data();
                    int visible = 0;
                    for (int i = 3; i < bytes; i += 4) {
                        if (data.get(i) != 0) { visible = 1; break; }
                    }
                    result[0] = visible;
                } catch (RuntimeException failure) {
                    result[0] = 1;
                }
            }, 0);
            long deadline = System.nanoTime() + 1_000_000_000L;
            while (result[0] < 0 && System.nanoTime() < deadline) {
                RenderSystem.executePendingTasks();
                Thread.onSpinWait();
            }
        } finally {
            buffer.close();
        }
        return result[0] != 0;
    }

    public static void blitRounded(
            DrawContext context,
            Snapshot snapshot,
            float x,
            float y,
            float widthGui,
            float heightGui,
            float alpha,
            float round,
            int backgroundColor
    ) {
        if (snapshot == null || snapshot.fbo == null || !snapshot.populated) {
            return;
        }

        BatchedRectangle.flushIfBatching();
        RECTANGLE.render(ShapeProperties.create(context.getMatrices(), x, y, widthGui, heightGui)
                .round(round)
                .color(backgroundColor)
                .build());

        Matrix4f matrix = GuiMatrix.mat4(context.getMatrices());
        int alphaByte = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
        int color = (alphaByte << 24) | 0x00FFFFFF;

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        buffer.vertex(matrix, x, y, 0.0F).texture(0.0F, 1.0F).color(color);
        buffer.vertex(matrix, x, y + heightGui, 0.0F).texture(0.0F, 0.0F).color(color);
        buffer.vertex(matrix, x + widthGui, y + heightGui, 0.0F).texture(1.0F, 0.0F).color(color);
        buffer.vertex(matrix, x + widthGui, y, 0.0F).texture(1.0F, 1.0F).color(color);

        BuiltBuffer built = buffer.endNullable();
        if (built != null) {

            vorga.phazeclient.api.system.draw.GuiProjection.begin();
            try {
                GpuDraw.draw(DST_ALPHA_PIPELINE, built, "Sampler0",
                        snapshot.fbo.getColorAttachmentView(), FilterMode.LINEAR,
                        vorga.phazeclient.api.system.draw.GuiProjection.guiModelView(BLIT_GUI_POSE));
            } finally {
                vorga.phazeclient.api.system.draw.GuiProjection.end();
                built.close();
            }
        }
    }

    public static void copyRegionFromFramebuffer(Snapshot snapshot, Framebuffer source, int srcX, int srcY, int srcWidth, int srcHeight) {
        if (snapshot == null || snapshot.fbo == null || source == null || srcWidth <= 0 || srcHeight <= 0) {
            return;
        }

        BatchedRectangle.flushIfBatching();

        GpuTexture src = source.getColorAttachment();
        GpuTexture dst = snapshot.fbo.getColorAttachment();
        if (src == null || dst == null) {
            return;
        }

        int width = Math.min(Math.min(srcWidth, snapshot.fbWidth), Math.max(0, src.getWidth(0) - srcX));
        int height = Math.min(Math.min(srcHeight, snapshot.fbHeight), Math.max(0, src.getHeight(0) - srcY));
        if (width <= 0 || height <= 0) {
            return;
        }

        RenderSystem.getDevice().createCommandEncoder()
                .copyTextureToTexture(src, dst, 0, 0, 0, srcX, srcY, width, height);
        snapshot.populated = true;
    }
}
