package vorga.phazeclient.api.system.font.msdf;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import vorga.phazeclient.api.system.draw.GpuDraw;
import vorga.phazeclient.api.system.shape.batched.GuiBatch;
import vorga.phazeclient.api.system.shape.batched.GuiDrawStats;
import net.minecraft.client.gl.UniformType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import org.lwjgl.system.MemoryStack;
import org.joml.Vector4f;
import vorga.phazeclient.api.system.draw.GuiProjection;
import vorga.phazeclient.mixins.RenderLayerSetupAccessor;

public final class MsdfRenderer {
    private static final MinecraftClient MC = MinecraftClient.getInstance();
    private static Throwable lastFailure;
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("PhazeMsdfText");
    private static BufferAllocator glyphAllocator;
    private static GpuBuffer effectsBuffer;
    private static final Vector4f WHITE = new Vector4f(1, 1, 1, 1);

    public record TextEffects(boolean outline, float outlineThickness, int outlineColor,
                              boolean fadeout, float fadeStart, float fadeEnd, float maxWidth,
                              float left, float right, float fadeWidth, float leftStrength, float rightStrength) {
        public static final TextEffects NONE = new TextEffects(false, 0, 0, false, 0, 1, 0, 0, 0, 0, 0, 0);
    }

    public static boolean isUsingFallback() { return lastFailure != null; }
    public static Throwable getLastFailure() { return lastFailure; }
    public static void onResourcesReloaded() {
        RenderSystem.assertOnRenderThread();
        if (lastFailure != null) LOGGER.info("Resources reloaded; retrying MSDF rendering");
        lastFailure = null;
        close();
    }
    public static void close() {
        RenderSystem.assertOnRenderThread();
        MsdfBatch.cleanup();
        if (glyphAllocator != null) { glyphAllocator.close(); glyphAllocator = null; }
        if (effectsBuffer != null) { effectsBuffer.close(); effectsBuffer = null; }
    }
    static void reportFailure(Exception failure) {
        if (lastFailure != null) return;
        lastFailure = failure;
        LOGGER.error("MSDF rendering failed; using sized vanilla text until F3+T", failure);
    }

    private static final VertexFormatElement MSDF_PARAMS;
    private static final VertexFormat FORMAT;
    private static final RenderPipeline PIPELINE;

    static {
        VertexFormatElement params = null;
        for (int id = 7; id < 32 && params == null; id++) {
            if (VertexFormatElement.byId(id) == null) {
                params = VertexFormatElement.register(
                        id, 0, VertexFormatElement.Type.FLOAT,
                        VertexFormatElement.Usage.GENERIC, 4);
            }
        }
        if (params == null) {
            throw new IllegalStateException("No free VertexFormatElement slot for MsdfRenderer");
        }
        MSDF_PARAMS = params;

        FORMAT = VertexFormat.builder()
                .add("Position", VertexFormatElement.POSITION)
                .add("UV0", VertexFormatElement.UV0)
                .add("Color", VertexFormatElement.COLOR)
                .add("MsdfParams", MSDF_PARAMS)
                .build();

        PIPELINE = RenderPipeline.builder()
                .withLocation(Identifier.of("phaze", "pipeline/msdf_font"))
                .withVertexShader(Identifier.of("phaze", "core/msdf_font"))
                .withFragmentShader(Identifier.of("phaze", "core/msdf_font"))
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withUniform("MsdfEffects", UniformType.UNIFORM_BUFFER)
                .withSampler("Sampler0")
                .withVertexFormat(FORMAT, VertexFormat.DrawMode.QUADS)
                .withBlend(BlendFunction.TRANSLUCENT)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(false)
                .withCull(false)
                .build();
    }

    private static final Matrix4f MSDF_GUI_POSE = new Matrix4f();

    private MsdfRenderer() {
    }

    public static VertexFormat format() { return FORMAT; }

    /** Position, UV, color, then the per-vertex range / thickness / smoothness used by {@code msdf_font}. */
    public static void vertex(BufferBuilder builder, Matrix4f matrix, float x, float y, float z,
                              float u, float v, int color, float range, float thickness, float smoothness) {
        builder.vertex(matrix, x, y, z).texture(u, v).color(color);
        long ptr = builder.beginElement(MSDF_PARAMS);
        org.lwjgl.system.MemoryUtil.memPutFloat(ptr, range);
        org.lwjgl.system.MemoryUtil.memPutFloat(ptr + 4L, thickness);
        org.lwjgl.system.MemoryUtil.memPutFloat(ptr + 8L, smoothness);
        org.lwjgl.system.MemoryUtil.memPutFloat(ptr + 12L, 0.0F);
    }

    /** One MSDF draw. {@code drawGlyphs} counts it in {@link GuiDrawStats#msdfDraws}. */
    public static void drawBatched(BuiltBuffer built, com.mojang.blaze3d.textures.GpuTextureView texture,
                                   TextEffects effects, float textX) {
        try (TextRenderState ignored = new TextRenderState()) {
            drawGlyphs(built, texture, effects == null ? TextEffects.NONE : effects, textX);
        }
    }

    public static void renderText(MsdfFont font, String text, float size, int color, Matrix4f matrix, float x, float y, float z) {
        renderText(font, text, size, color, matrix, x, y, z, MsdfTextMetrics.DEFAULT_THICKNESS, 0.5F);
    }

    public static void renderText(MsdfFont font, String text, float size, int color, Matrix4f matrix, float x, float y, float z, float thickness, float smoothness) {
        renderText(font, text, size, color, matrix, x, y, z, thickness, smoothness, TextEffects.NONE);
    }

    public static void renderTextFaded(MsdfFont font, String text, float size, int color, Matrix4f matrix,
                                       float x, float y, float z, float left, float right,
                                       float fadeWidth, float leftStrength, float rightStrength) {
        renderText(font, text, size, color, matrix, x, y, z, MsdfTextMetrics.DEFAULT_THICKNESS, 0.5F,
                new TextEffects(false, 0, 0, false, 0, 1, 0, left, right, fadeWidth, leftStrength, rightStrength));
    }

    public static void renderText(MsdfFont font, String text, float size, int color, Matrix4f matrix,
                                   float x, float y, float z, float thickness, float smoothness, TextEffects effects) {
        if (text == null || text.isEmpty() || (color >>> 24) == 0 || !Float.isFinite(size) || size <= 0) return;
        RenderSystem.assertOnRenderThread();
        if (isUsingFallback() || font == null) {
            vorga.phazeclient.api.system.shape.batched.GuiBatch.flush();
            fallback(font, text, size, thickness, color, matrix, x, y, z);
            return;
        }
        if (MsdfBatch.canDefer()) {
            try {
                GuiBatch.Run run = MsdfBatch.begin(font.getTextureView(), font.getAtlas().range(), thickness, smoothness, effects, x);
                font.applyGlyphs(matrix, run, text, size, thickness * 0.5F * size, 0,
                        x - MsdfTextMetrics.PEN_OFFSET_X, y + size * MsdfTextMetrics.BASELINE_EM, z, color);
                Matrix4f pose = new Matrix4f(matrix);
                MsdfBatch.fallbackOnFailure(() -> fallback(font, text, size, thickness, color, pose, x, y, z));
                return;
            } catch (Exception failure) {
                MsdfBatch.discard();
                reportFailure(failure);
                fallback(font, text, size, thickness, color, matrix, x, y, z);
                return;
            }
        }
        vorga.phazeclient.api.system.shape.batched.GuiBatch.flush();
        try {
            BufferBuilder builder = beginGlyphs();
            font.applyGlyphs(
                    matrix, builder, text, size,
                    thickness * 0.5F * size, 0.0F,
                    x - MsdfTextMetrics.PEN_OFFSET_X, y + size * MsdfTextMetrics.BASELINE_EM, z, color,
                    MSDF_PARAMS, font.getAtlas().range(), thickness, smoothness);
            try (BuiltBuffer built = builder.endNullable()) {
                if (built != null) drawGlyphs(built, font.getTextureView(), effects, x);
            }
        } catch (Exception failure) {
            reportFailure(failure);
            fallback(font, text, size, thickness, color, matrix, x, y, z);
        } finally {
            drainGlyphs();
        }
    }

    public static void drawAtlasQuad(com.mojang.blaze3d.textures.GpuTextureView textureView,
                                     Matrix4f matrix,
                                     float x1, float y1, float x2, float y2,
                                     float minU, float minV, float maxU, float maxV,
                                     int color, float range, float thickness, float smoothness,
                                     boolean flipV) {
        if (isUsingFallback() || textureView == null) {
            return;
        }
        vorga.phazeclient.api.system.shape.batched.GuiBatch.flush();
        try {
            BufferBuilder builder = beginGlyphs();
            if (flipV) {
                msdfVertex(builder, matrix, x1, y2, minU, minV, color, range, thickness, smoothness);
                msdfVertex(builder, matrix, x2, y2, minU, maxV, color, range, thickness, smoothness);
                msdfVertex(builder, matrix, x2, y1, maxU, maxV, color, range, thickness, smoothness);
                msdfVertex(builder, matrix, x1, y1, maxU, minV, color, range, thickness, smoothness);
            } else {
                msdfVertex(builder, matrix, x1, y1, minU, minV, color, range, thickness, smoothness);
                msdfVertex(builder, matrix, x1, y2, minU, maxV, color, range, thickness, smoothness);
                msdfVertex(builder, matrix, x2, y2, maxU, maxV, color, range, thickness, smoothness);
                msdfVertex(builder, matrix, x2, y1, maxU, minV, color, range, thickness, smoothness);
            }
            try (BuiltBuffer built = builder.endNullable()) {
                if (built != null) drawGlyphs(built, textureView, TextEffects.NONE, x1);
            }
        } catch (Exception failure) {
            reportFailure(failure);
        } finally {
            drainGlyphs();
        }
    }

    private static BufferBuilder beginGlyphs() {
        if (glyphAllocator == null) glyphAllocator = new BufferAllocator(4096);
        return new BufferBuilder(glyphAllocator, VertexFormat.DrawMode.QUADS, FORMAT);
    }

    private static void drainGlyphs() {
        if (glyphAllocator == null) return;
        try (var unused = glyphAllocator.getAllocated()) {} finally { glyphAllocator.clear(); }
    }

    static void drawGlyphs(BuiltBuffer built, com.mojang.blaze3d.textures.GpuTextureView texture,
                                    TextEffects effects, float textX) {
        // An invalid pipeline would skip the draw silently; failing here selects the vanilla fallback instead.
        if (!RenderSystem.getDevice().precompilePipeline(PIPELINE).isValid()) {
            throw new IllegalStateException("Missing MSDF shader: " + PIPELINE.getLocation());
        }
        GuiDrawStats.msdfDraws++;
        if (effectsBuffer == null || effectsBuffer.isClosed()) {
            effectsBuffer = RenderSystem.getDevice().createBuffer(() -> "phaze/MSDF effects",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, 80);
        }
        int outline = effects.outlineColor;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var data = Std140Builder.onStack(stack, 80)
                    .putVec4(((outline >> 16) & 255) / 255F, ((outline >> 8) & 255) / 255F,
                            (outline & 255) / 255F, (outline >>> 24) / 255F)
                    .putVec4(effects.outline ? 1 : 0, effects.outlineThickness, effects.fadeout ? 1 : 0, textX)
                    .putVec4(effects.fadeStart, effects.fadeEnd, effects.maxWidth, effects.fadeWidth > 0 ? 1 : 0)
                    .putVec4(effects.left, effects.right, Math.max(0, effects.fadeWidth), 0)
                    .putVec4(effects.leftStrength, effects.rightStrength, 0, 0).get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(effectsBuffer.slice(), data);
        } catch (RuntimeException rejected) {
            throw new IllegalStateException("MSDF shader is missing uniform block: MsdfEffects", rejected);
        }
        GuiProjection.begin();
        try {
            GpuDraw.drawWithUniforms(PIPELINE, built, "Sampler0", texture, null, null, FilterMode.LINEAR,
                    GuiProjection.guiModelView(MSDF_GUI_POSE), WHITE, "MsdfEffects", effectsBuffer);
        } finally { GuiProjection.end(); }
    }

    private static void msdfVertex(BufferBuilder builder, Matrix4f matrix,
                                   float x, float y, float u, float v, int color,
                                   float range, float thickness, float smoothness) {
        vertex(builder, matrix, x, y, 0.0F, u, v, color, range, thickness, smoothness);
    }

    private static void fallback(MsdfFont font, String text, float size, float thickness, int color,
                                  Matrix4f matrix, float x, float y, float z) {
        TextRenderer textRenderer = MC.textRenderer;
        if (textRenderer == null) return;
        float sy = MsdfTextMetrics.fallbackScaleY(size, textRenderer.fontHeight);
        float targetWidth = font == null ? 0 : font.getRenderedWidth(text, size, thickness);
        float sx = MsdfTextMetrics.fallbackScaleX(targetWidth, textRenderer.getWidth(text), sy);
        Matrix4f scaled = new Matrix4f(matrix).translate(x, y, z).scale(sx, sy, 1);
        java.util.Map<RenderLayer, BufferAllocator> allocators = new java.util.LinkedHashMap<>();
        java.util.Map<RenderLayer, BufferBuilder> builders = new java.util.LinkedHashMap<>();
        VertexConsumerProvider consumers = layer -> builders.computeIfAbsent(layer, key -> {
            BufferAllocator allocator = new BufferAllocator(4096);
            allocators.put(key, allocator);
            return new BufferBuilder(allocator, key.getDrawMode(), key.getVertexFormat());
        });
        GuiProjection.begin();
        try {
            // Vanilla promotes vertex alpha 1..3 to opaque; multiply alpha in DynamicTransforms instead.
            textRenderer.draw(text, 0, 0, color | 0xFF000000, false, scaled, consumers,
                    TextRenderer.TextLayerType.NORMAL, 0, 15728880);
            Vector4f tint = new Vector4f(1, 1, 1, (color >>> 24) / 255F);
            for (var entry : builders.entrySet()) {
                try (BuiltBuffer built = entry.getValue().endNullable()) {
                    if (built != null) GpuDraw.drawWithTextures(entry.getKey().getRenderPipeline(), built,
                            ((RenderLayerSetupAccessor) entry.getKey()).phaze$getRenderSetup().resolveTextures(),
                            GuiProjection.guiModelView(MSDF_GUI_POSE), tint);
                }
            }
        } finally {
            GuiProjection.end();
            for (BufferAllocator allocator : allocators.values()) allocator.close();
        }
    }
}
