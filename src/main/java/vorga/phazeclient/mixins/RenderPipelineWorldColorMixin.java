package vorga.phazeclient.mixins;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gl.UniformType;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.base.util.shader.WorldColorShaderPatcher;
import java.util.Optional;

@Mixin(RenderPipeline.Builder.class)
public abstract class RenderPipelineWorldColorMixin {
    @Shadow private Optional<Identifier> fragmentShader;
    @Shadow private Optional<Identifier> location;

    @Inject(method = "build()Lcom/mojang/blaze3d/pipeline/RenderPipeline;", at = @At("HEAD"), require = 1)
    private void phaze$addWorldColorContract(CallbackInfoReturnable<RenderPipeline> cir) {
        Identifier shader = fragmentShader.orElse(null);
        if (!WorldColorShaderPatcher.supports(shader)) return;
        RenderPipeline.Builder builder = (RenderPipeline.Builder) (Object) this;
        builder.withUniform(WorldColorShaderPatcher.UNIFORM, UniformType.UNIFORM_BUFFER);
        if (shader.getPath().equals("core/terrain") && location.map(id -> id.getPath().contains("translucent")).orElse(false)) {
            builder.withShaderDefine("PHAZE_TERRAIN_FLUID");
        }
    }
}
