package vorga.phazeclient.mixins;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import net.minecraft.client.gl.RenderPassImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.api.system.colorcorrection.WorldColorUniforms;
import vorga.phazeclient.base.util.shader.WorldColorShaderPatcher;

@Mixin(RenderPassImpl.class)
public abstract class RenderPassWorldColorMixin {
    @Inject(method = "setPipeline(Lcom/mojang/blaze3d/pipeline/RenderPipeline;)V", at = @At("TAIL"), require = 1)
    private void phaze$bindWorldColor(RenderPipeline pipeline, CallbackInfo ci) {
        if (WorldColorShaderPatcher.supports(pipeline.getFragmentShader())) WorldColorUniforms.bind((RenderPass) this);
    }
}
