package vorga.phazeclient.mixins.iris;

import com.mojang.blaze3d.textures.GpuTextureView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.api.system.colorcorrection.WorldColorShaderHelper;

/** 1.21.11 gbuffer binds go through ExtendedShader.iris$setupState, which calls glUseProgram. */
@Mixin(targets = "net.irisshaders.iris.pipeline.programs.ExtendedShader", remap = false)
public abstract class IrisExtendedShaderWorldColorMixin {
    @Unique
    private boolean phaze$worldColorProgramRegistered;

    @Inject(method = "iris$setupState", at = @At("TAIL"), require = 1, remap = false)
    private void phaze$uploadWorldColorUniforms(GpuTextureView view, CallbackInfo ci) {
        if (!phaze$worldColorProgramRegistered) {
            WorldColorShaderHelper.registerCurrentProgramInstance();
            phaze$worldColorProgramRegistered = true;
        }
        WorldColorShaderHelper.uploadTerrainUniformsToCurrentGlProgram();
    }
}
