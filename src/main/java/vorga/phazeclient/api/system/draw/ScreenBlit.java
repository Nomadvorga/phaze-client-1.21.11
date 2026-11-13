package vorga.phazeclient.api.system.draw;

import net.minecraft.client.gl.RenderPipelines;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

import java.util.OptionalInt;

public final class ScreenBlit {

    private static final RenderPipeline PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.of("phaze", "pipeline/screen_blit"))
            .withVertexShader(Identifier.of("minecraft", "core/screenquad"))
            .withFragmentShader(Identifier.of("minecraft", "core/blit_screen"))
            .withSampler("InSampler")
            .withBlend(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withColorWrite(true, true)

            .withCull(false)
            .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
            .build();

    private ScreenBlit() {
    }

    public static void blitOverMain(Framebuffer source) {
        blitOverMain(source, FilterMode.NEAREST);
    }

    public static void blitOverMain(Framebuffer source, FilterMode filter) {
        if (source == null) {
            return;
        }
        FilterMode sampler = filter == null ? FilterMode.NEAREST : filter;
        RenderSystem.assertOnRenderThread();
        vorga.phazeclient.api.system.shape.batched.GuiBatch.flush();

        Framebuffer target = MinecraftClient.getInstance().getFramebuffer();
        if (target == null) {
            return;
        }

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "phaze/screen blit",
                RenderSystem.outputColorTextureOverride != null
                        ? RenderSystem.outputColorTextureOverride : target.getColorAttachmentView(),
                OptionalInt.empty())) {
            pass.setPipeline(PIPELINE);
            RenderSystem.bindDefaultUniforms(pass);
            pass.bindTexture(
                    "InSampler",
                    source.getColorAttachmentView(),
                    RenderSystem.getSamplerCache().get(sampler));
            pass.draw(0, 3);
        }
    }
}
