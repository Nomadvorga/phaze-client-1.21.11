package vorga.phazeclient.api.system.draw;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.ScissorState;
import net.minecraft.client.render.BuiltBuffer;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Map;
import net.minecraft.client.render.RenderSetup;

public final class GpuDraw {

    private GpuDraw() {
    }

    public static void drawWithUniforms(RenderPipeline pipeline,
                                        BuiltBuffer built,
                                        String samplerName,
                                        GpuTextureView textureView,
                                        String sampler1Name,
                                        GpuTextureView texture1View,
                                        FilterMode filter,
                                        Matrix4f modelView,
                                        Vector4f tint,
                                        String uniformName,
                                        GpuBuffer uniformData) {
        drawInternal(pipeline, built, samplerName, textureView, filter, modelView, tint,
                sampler1Name, texture1View, uniformName, uniformData, null);
    }

    public static void draw(RenderPipeline pipeline,
                            BuiltBuffer built,
                            String samplerName,
                            GpuTextureView textureView,
                            FilterMode filter,
                            Matrix4f modelView,
                            Vector4f tint) {
        drawInternal(pipeline, built, samplerName, textureView, filter, modelView, tint,
                null, null, null, null, null);
    }

    /** Metadata-only RenderLayer adapter: no entity phases or Iris wrapping. */
    public static void drawWithTextures(RenderPipeline pipeline, BuiltBuffer built,
                                        Map<String, RenderSetup.Texture> textures, Matrix4f modelView) {
        drawWithTextures(pipeline, built, textures, modelView, WHITE);
    }

    public static void drawWithTextures(RenderPipeline pipeline, BuiltBuffer built,
                                        Map<String, RenderSetup.Texture> textures, Matrix4f modelView, Vector4f tint) {
        drawInternal(pipeline, built, null, null, FilterMode.LINEAR, modelView,
                tint, null, null, null, null, textures);
    }

    private static final Vector4f WHITE = new Vector4f(1, 1, 1, 1);
    private static final Vector3f ZERO = new Vector3f();
    private static final Matrix4f IDENTITY = new Matrix4f();

    private static void drawInternal(RenderPipeline pipeline,
                                     BuiltBuffer built,
                                     String samplerName,
                                     GpuTextureView textureView,
                                     FilterMode filter,
                                     Matrix4f modelView,
                                     Vector4f tint,
                                     String sampler1Name,
                                     GpuTextureView texture1View,
                                     String uniformName,
                                      GpuBuffer uniformData,
                                      Map<String, RenderSetup.Texture> textures) {
        RenderSystem.assertOnRenderThread();
        if (!vorga.phazeclient.api.system.render.WorldOverlayRenderer.isRecording()) {
            vorga.phazeclient.api.system.shape.batched.GuiBatch.flush();
        }
        if (built == null) {
            return;
        }

        BuiltBuffer.DrawParameters params = built.getDrawParameters();
        if (params.indexCount() == 0) {
            return;
        }

        Framebuffer framebuffer = MinecraftClient.getInstance().getFramebuffer();
        GpuTextureView colorView = RenderSystem.outputColorTextureOverride != null
                ? RenderSystem.outputColorTextureOverride : framebuffer.getColorAttachmentView();
        GpuTextureView depthView = RenderSystem.outputColorTextureOverride != null
                ? RenderSystem.outputDepthTextureOverride
                : (framebuffer.useDepthAttachment ? framebuffer.getDepthAttachmentView() : null);

        VertexFormat format = pipeline.getVertexFormat();
        GpuBuffer vertexBuffer = format.uploadImmediateVertexBuffer(built.getBuffer());

        ByteBuffer sorted = built.getSortedBuffer();
        GpuBuffer indexBuffer;
        VertexFormat.IndexType indexType;
        if (sorted == null) {
            RenderSystem.ShapeIndexBuffer shape = RenderSystem.getSequentialBuffer(params.mode());

            indexBuffer = shape.getIndexBuffer(params.indexCount());
            indexType = shape.getIndexType();
        } else {
            indexBuffer = format.uploadImmediateIndexBuffer(sorted);
            indexType = params.indexType();
        }

        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .write(modelView, tint, ZERO, IDENTITY);

        GpuSampler sampler = textureView != null
                ? RenderSystem.getSamplerCache().get(filter)
                : null;

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (RenderPass pass = encoder.createRenderPass(
                () -> "phaze/draw",
                colorView,
                OptionalInt.empty(),
                depthView,
                OptionalDouble.empty())) {

            pass.setPipeline(pipeline);

            ScissorState scissor = RenderSystem.getScissorStateForRenderTypeDraws();
            if (scissor.isEnabled()) {
                pass.enableScissor(scissor.getX(), scissor.getY(), scissor.getWidth(), scissor.getHeight());
            }

            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", dynamicTransforms);
            pass.setVertexBuffer(0, vertexBuffer);
            if (uniformName != null && uniformData != null) {
                pass.setUniform(uniformName, uniformData.slice());
            }
            if (textureView != null) {

                pass.bindTexture(samplerName, textureView, sampler);
            }
            if (sampler1Name != null && texture1View != null) {
                pass.bindTexture(sampler1Name, texture1View,
                        RenderSystem.getSamplerCache().get(filter));
            }
            if (textures != null) {
                for (var entry : textures.entrySet()) {
                    RenderSetup.Texture texture = entry.getValue();
                    pass.bindTexture(entry.getKey(), texture.textureView(), texture.sampler());
                }
            }
            pass.setIndexBuffer(indexBuffer, indexType);
            pass.drawIndexed(0, 0, params.indexCount(), 1);
        }
    }

    public static void draw(RenderPipeline pipeline,
                            BuiltBuffer built,
                            String samplerName,
                            GpuTextureView textureView,
                            FilterMode filter,
                            Matrix4f modelView) {
        draw(pipeline, built, samplerName, textureView, filter, modelView, WHITE);
    }
}
