package vorga.phazeclient.api.system.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import vorga.phazeclient.api.system.draw.GpuDraw;
import vorga.phazeclient.api.system.hud.HudPassState;
import vorga.phazeclient.implement.features.modules.other.*;
import vorga.phazeclient.implement.hitrange.HitRangeCircleRenderer;
import vorga.phazeclient.mixins.RenderLayerSetupAccessor;

import java.util.LinkedHashMap;
import java.util.Map;

/** Late camera-relative geometry pass. Pipelines, not RenderLayer phases, own its GPU state. */
public final class WorldOverlayRenderer {
    private static final Map<Key, Batch> BATCHES = new LinkedHashMap<>();
    private static final Map<Key, RenderPipeline> PIPELINES = new LinkedHashMap<>();
    private static final Matrix4f VIEW = new Matrix4f();
    private static final Matrix4f PROJECTION = new Matrix4f();
    private static RawProjectionMatrix projectionBuffer;
    private static boolean recording;
    private static long frame;
    private static Vec3d camera;
    private static RenderTickCounter tickCounter;
    private static final PlayerEntityRenderState SELF_STATE = new PlayerEntityRenderState();
    private static final MatrixStack PLAYER_MATRICES = new MatrixStack();
    private WorldOverlayRenderer() {}

    public static void beginFrame(Matrix4f view, Matrix4f projection, Camera worldCamera, RenderTickCounter counter) {
        if (IrisCompat.isShadowPass()) return;
        vorga.phazeclient.api.system.colorcorrection.WorldColorUniforms.beginFrame();
        for (var iterator = BATCHES.entrySet().iterator(); iterator.hasNext();) {
            Batch batch = iterator.next().getValue();
            batch.discard();
            if (frame - batch.lastUsed > 300) {
                batch.allocator.close();
                iterator.remove();
            }
        }
        frame++;
        VIEW.set(view);
        PROJECTION.set(projection);
        camera = worldCamera.getCameraPos();
        tickCounter = counter;
        recording = true;
    }

    public static boolean isRecording() { return recording && !IrisCompat.isShadowPass(); }

    /** A defensive copy of the world projection, including bob/nausea but never GUI/hand projection. */
    public static Matrix4f projectionMatrix() { return new Matrix4f(PROJECTION); }

    public static BufferBuilder buffer(VertexFormat.DrawMode mode, VertexFormat format, float width, boolean depth) {
        return buffer(new Key(mode, format, null, Math.max(1, width), depth, null, false, false));
    }

    public static BufferBuilder texturedBuffer(Identifier texture, boolean depth) {
        return buffer(new Key(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR, texture, 1, depth, null, false, false));
    }

    private static BufferBuilder buffer(Key key) {
        Batch batch = BATCHES.computeIfAbsent(key, ignored -> new Batch());
        batch.lastUsed = frame;
        if (batch.builder == null) batch.builder = new BufferBuilder(batch.allocator, key.mode, key.format);
        return batch.builder;
    }

    /** Model/font compatibility adapter: a layer supplies only pipeline/texture metadata. */
    public static final VertexConsumerProvider CONSUMERS = layer -> {
        if (layer.getVertexFormat() == VertexFormats.POSITION_COLOR) {
            return buffer(layer.getDrawMode(), layer.getVertexFormat(), 1, true);
        }
        return buffer(new Key(layer.getDrawMode(), layer.getVertexFormat(), null, 1, true, layer, false, false));
    };

    private static final Identifier GLYPHS = Identifier.of("phaze", "glyphs");

    /**
     * See-through font adapter for labels that fade: the glyphs of each font page are drawn last with
     * position_tex_color. Unlike the text shaders it discards only fully transparent pixels, so text
     * fades exactly like the frame around it. BufferBuilder skips the light element the font also writes.
     */
    public static final VertexConsumerProvider FADING_TEXT = layer -> buffer(new Key(VertexFormat.DrawMode.QUADS,
            VertexFormats.POSITION_TEXTURE_COLOR, GLYPHS, 1, false, layer, false, false));

    public static VertexConsumerProvider consumers(VertexConsumerProvider vanilla) { return isRecording() ? CONSUMERS : vanilla; }

    public static void draw() {
        if (!recording || IrisCompat.isShadowPass()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        try (HudPassState ignored = HudPassState.capture()) {
            RenderSystem.outputColorTextureOverride = client.getFramebuffer().getColorAttachmentView();
            RenderSystem.outputDepthTextureOverride = client.getFramebuffer().getDepthAttachmentView();
            RenderSystem.getModelViewStack().set(VIEW);
            RenderSystem.setShaderFog(((vorga.phazeclient.mixins.GameRendererGuiAccessor) client.gameRenderer)
                    .phaze$getFogRenderer().getFogBuffer(net.minecraft.client.render.fog.FogRenderer.FogType.NONE));
            if (projectionBuffer == null) projectionBuffer = new RawProjectionMatrix("phaze/world overlay");
            RenderSystem.setProjectionMatrix(projectionBuffer.set(PROJECTION), ProjectionType.PERSPECTIVE);
            MatrixStack matrices = new MatrixStack();
            FTHelperRenderer.renderHighlight(matrices, camera, tickCounter);
            FTHelperRenderer.renderSnowballs(matrices, camera, tickCounter);
            HolyWorldHelperRenderer.render(matrices, camera, tickCounter);
            PredictionsRenderer.render(matrices, camera, tickCounter);
            vorga.phazeclient.implement.waypoints.WaypointRenderer.render(matrices, camera);
            drawSelf(client, matrices);
            flush();
        } finally {
            recording = false;
            for (Batch batch : BATCHES.values()) batch.discard();
        }
    }

    private static void drawSelf(MinecraftClient client, MatrixStack matrices) {
        HitRange module = HitRange.getInstance();
        if (client.player == null || module == null || !module.isEnabled() || !module.showSelf.isValue()) return;
        var player = client.player;
        float delta = tickCounter.getTickProgress(false);
        SELF_STATE.x = MathHelper.lerp(delta, player.lastRenderX, player.getX());
        SELF_STATE.y = MathHelper.lerp(delta, player.lastRenderY, player.getY());
        SELF_STATE.z = MathHelper.lerp(delta, player.lastRenderZ, player.getZ());
        SELF_STATE.id = player.getId();
        SELF_STATE.playerName = player.getName();
        SELF_STATE.displayName = player.getDisplayName();
        SELF_STATE.sneaking = player.isInSneakingPose();
        matrices.push();
        try {
            matrices.translate(SELF_STATE.x - camera.x, SELF_STATE.y - camera.y, SELF_STATE.z - camera.z);
            HitRangeCircleRenderer.drawCircle(matrices, CONSUMERS, SELF_STATE);
        } finally { matrices.pop(); }
    }

    /** Entity extraction only queues CPU geometry; it never draws in vanilla/Iris entity phases. */
    public static void queuePlayerCircle(PlayerEntityRenderState state) {
        if (!isRecording() || camera == null) return;
        String name = state.playerName != null ? state.playerName.getString()
                : state.displayName != null ? state.displayName.getString() : Integer.toString(state.id);
        PLAYER_MATRICES.push();
        try {
            PLAYER_MATRICES.translate(state.x - camera.x, state.y - camera.y, state.z - camera.z);
            // Raw hitbox feet do not include vanilla's sneaking model offset.
            HitRangeCircleRenderer.drawCircle(PLAYER_MATRICES, state.x, state.y, state.z, state.id, name, 0.0F);
        } finally { PLAYER_MATRICES.pop(); }
    }

    /** Surfaces, then lines, then label text over both. */
    public static void flush() {
        flush(false);
        flush(true);
        flushGlyphs();
    }

    private static void flush(boolean lines) {
        for (var entry : BATCHES.entrySet()) {
            Key key = entry.getKey();
            Batch batch = entry.getValue();
            if (key.isLines() != lines || GLYPHS.equals(key.texture) || batch.builder == null) continue;
            try (BuiltBuffer built = batch.end(key)) {
                if (built != null) drawBuffer(key, built);
            }
        }
    }

    private static void flushGlyphs() {
        for (var entry : BATCHES.entrySet()) {
            Key key = entry.getKey();
            Batch batch = entry.getValue();
            if (!GLYPHS.equals(key.texture) || batch.builder == null) continue;
            try (BuiltBuffer built = batch.end(key)) {
                if (built != null) drawBuffer(key, built);
            }
        }
    }

    public static void drawBuffer(BuiltBuffer built, float width, boolean depth, Identifier texture) {
        drawBuffer(built, width, depth, texture, false, false);
    }

    public static void drawBuffer(BuiltBuffer built, float width, boolean depth, Identifier texture, boolean additive, boolean cull) {
        if (built == null) return;
        var parameters = built.getDrawParameters();
        drawBuffer(new Key(parameters.mode(), parameters.format(), texture, Math.max(1, width), depth, null, additive, cull), built);
    }

    private static void drawBuffer(Key key, BuiltBuffer built) {
        if (key.layer != null && !GLYPHS.equals(key.texture)) {
            GpuDraw.drawWithTextures(key.layer.getRenderPipeline(), built,
                    ((RenderLayerSetupAccessor) key.layer).phaze$getRenderSetup().resolveTextures(), RenderSystem.getModelViewMatrix());
        } else {
            // Width and texture are per-vertex/per-pass data, not raster state. Do not retain a pipeline per slider value.
            Key pipelineKey = new Key(key.mode, key.format, key.texture == null ? null : Identifier.of("phaze", "textured"),
                    1, key.depth, null, key.additive, key.cull);
            RenderPipeline pipeline = PIPELINES.computeIfAbsent(pipelineKey, WorldOverlayRenderer::pipeline);
            if (key.layer != null) {
                RenderSetup.Texture page = ((RenderLayerSetupAccessor) key.layer).phaze$getRenderSetup()
                        .resolveTextures().get("Sampler0");
                if (page != null) {
                    GpuDraw.drawWithTextures(pipeline, built, Map.of("Sampler0", page), RenderSystem.getModelViewMatrix());
                }
                return;
            }
            GpuDraw.draw(pipeline, built, "Sampler0", key.texture == null ? null
                    : MinecraftClient.getInstance().getTextureManager().getTexture(key.texture).getGlTextureView(),
                    FilterMode.LINEAR, RenderSystem.getModelViewMatrix());
        }
    }

    private static RenderPipeline pipeline(Key key) {
        String shader = key.format == VertexFormats.POSITION_TEXTURE_COLOR ? "position_tex_color"
                : key.format == VertexFormats.POSITION_COLOR_NORMAL_LINE_WIDTH ? "rendertype_lines" : "position_color";
        RenderPipeline.Builder builder = RenderPipeline.builder()
                .withLocation(Identifier.of("phaze", "pipeline/world_overlay_" + PIPELINES.size()))
                .withVertexShader(Identifier.of("minecraft", "core/" + shader))
                .withFragmentShader(Identifier.of("minecraft", "core/" + shader))
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withVertexFormat(key.format, key.mode)
                .withBlend(key.additive ? new BlendFunction(com.mojang.blaze3d.platform.SourceFactor.SRC_ALPHA,
                        com.mojang.blaze3d.platform.DestFactor.ONE) : BlendFunction.TRANSLUCENT)
                .withDepthTestFunction(key.depth ? DepthTestFunction.LEQUAL_DEPTH_TEST : DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(false).withCull(key.cull);
        if (key.texture != null) builder.withSampler("Sampler0");
        if (key.format == VertexFormats.POSITION_COLOR_NORMAL_LINE_WIDTH) {
            builder.withUniform("Fog", UniformType.UNIFORM_BUFFER).withUniform("Globals", UniformType.UNIFORM_BUFFER);
        } else if (key.depth && !key.isLines() && key.format == VertexFormats.POSITION_COLOR) {
            builder.withDepthBias(-1, -1);
        }
        return builder.build();
    }

    public static void clear() {
        recording = false;
        camera = null;
        tickCounter = null;
        Throwable failure = null;
        try {
            for (Batch batch : BATCHES.values()) {
                try {
                    try { batch.discard(); } finally { batch.allocator.close(); }
                } catch (RuntimeException | Error problem) {
                    if (failure == null) failure = problem; else failure.addSuppressed(problem);
                }
            }
        } finally {
            BATCHES.clear();
            PIPELINES.clear();
            RawProjectionMatrix owned = projectionBuffer;
            projectionBuffer = null;
            if (owned != null) {
                try { owned.close(); }
                catch (RuntimeException | Error problem) {
                    if (failure == null) failure = problem; else failure.addSuppressed(problem);
                }
            }
        }
        if (failure instanceof Error error) throw error;
        if (failure instanceof RuntimeException exception) throw exception;
    }

    private record Key(VertexFormat.DrawMode mode, VertexFormat format, Identifier texture, float width, boolean depth,
                       RenderLayer layer, boolean additive, boolean cull) {
        boolean isLines() {
            return mode == VertexFormat.DrawMode.LINES
                    || mode == VertexFormat.DrawMode.DEBUG_LINES || mode == VertexFormat.DrawMode.DEBUG_LINE_STRIP;
        }
    }

    private static final class Batch {
        final BufferAllocator allocator = new BufferAllocator(4096);
        BufferBuilder builder;
        long lastUsed;
        BuiltBuffer end(Key key) {
            BufferBuilder started = builder;
            builder = null;
            BuiltBuffer built = started.endNullable();
            try {
                if (built != null && key.mode == VertexFormat.DrawMode.QUADS && key.format == VertexFormats.POSITION_COLOR) {
                    built.sortQuads(allocator, VertexSorter.BY_DISTANCE);
                }
                return built;
            } catch (RuntimeException | Error failure) {
                if (built != null) built.close();
                throw failure;
            }
        }
        void discard() {
            builder = null;
            // Do not validate an incomplete vertex while handling the original rendering failure.
            try (var pending = allocator.getAllocated()) {} finally { allocator.clear(); }
        }
    }
}
