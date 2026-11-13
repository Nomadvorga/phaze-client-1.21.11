package vorga.phazeclient.api.system.hud;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import vorga.phazeclient.api.system.draw.GpuDraw;
import vorga.phazeclient.api.system.draw.GuiProjection;
import vorga.phazeclient.api.system.draw.ScreenBlit;

/** Premultiplied cache composition onto the caller target. Does not rebind the main framebuffer. */
final class HudBlit {
    private static final RenderPipeline CROPPED = RenderPipeline.builder()
            .withLocation(Identifier.of("phaze", "pipeline/hud_blit"))
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

    private HudBlit() {}

    static boolean draw(Framebuffer source, FilterMode filter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (source == null || mc == null || mc.getWindow() == null) return false;
        int width = mc.getWindow().getFramebufferWidth();
        int height = mc.getWindow().getFramebufferHeight();
        if (width <= 0 || height <= 0) return false;
        try (Render2DState state = Render2DState.capture()) {
            try {
                compose(source, filter);
                return true;
            } catch (RuntimeException | Error failure) {
                Render2DBuffers.discardImmediate();
                throw failure;
            }
        }
    }

    /** A cropped quad: mouse motion changes only vertices, never the cached pixels or blur source. */
    static boolean drawTransformed(Framebuffer source, HudCachedTransform captured, float x, float y, float scale, FilterMode filter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (source == null || captured == null || mc == null || mc.getWindow() == null
                || !Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(scale) || scale <= 0) return false;
        int width = mc.getWindow().getFramebufferWidth(), height = mc.getWindow().getFramebufferHeight();
        if (width <= 0 || height <= 0 || source.textureWidth <= 0 || source.textureHeight <= 0) return false;
        try (Render2DState state = Render2DState.capture()) {
            try (GuiProjection.TargetSize ignored = GuiProjection.targetSize(width, height)) {
                GuiProjection.begin();
                try {
                    float left = captured.mapX(captured.left(), x, scale);
                    float right = captured.mapX(captured.right(), x, scale);
                    float top = captured.mapY(captured.top(), y, scale);
                    float bottom = captured.mapY(captured.bottom(), y, scale);
                    float u0 = captured.left() / (float) source.textureWidth;
                    float u1 = captured.right() / (float) source.textureWidth;
                    float v0 = 1.0F - captured.top() / (float) source.textureHeight;
                    float v1 = 1.0F - captured.bottom() / (float) source.textureHeight;
                    BufferBuilder builder = Render2DBuffers.begin(VertexFormats.POSITION_TEXTURE_COLOR);
                    try {
                        builder.vertex(left, top, 0).texture(u0, v0).color(-1);
                        builder.vertex(left, bottom, 0).texture(u0, v1).color(-1);
                        builder.vertex(right, bottom, 0).texture(u1, v1).color(-1);
                        builder.vertex(right, top, 0).texture(u1, v0).color(-1);
                        try (BuiltBuffer built = builder.end()) {
                            GpuDraw.draw(CROPPED, built, "Sampler0", source.getColorAttachmentView(),
                                    filter == null ? FilterMode.NEAREST : filter, GuiProjection.guiModelView(new Matrix4f()));
                        }
                    } catch (RuntimeException | Error failure) {
                        Render2DBuffers.discardImmediate();
                        throw failure;
                    }
                    return true;
                } finally {
                    GuiProjection.end();
                }
            }
        }
    }

    private static void compose(Framebuffer source, FilterMode filter) {
        ScreenBlit.blitOverMain(source, filter);
    }
}
