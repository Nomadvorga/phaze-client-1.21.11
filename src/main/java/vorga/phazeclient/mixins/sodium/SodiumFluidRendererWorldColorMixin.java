package vorga.phazeclient.mixins.sodium;

import com.llamalad7.mixinextras.sugar.Local;
import net.caffeinemc.mods.sodium.client.model.quad.ModelQuadViewMutable;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.DefaultFluidRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.api.system.colorcorrection.WorldColorCorrectionController;

@Mixin(value = DefaultFluidRenderer.class, remap = false)
public abstract class SodiumFluidRendererWorldColorMixin {
    @Inject(method = "updateQuad", at = @At("TAIL"), remap = false, require = 1)
    private void phaze$tintFluidQuad(CallbackInfo ci, @Local(argsOnly = true) ModelQuadViewMutable quad) {
        for (int vertex = 0; vertex < 4; vertex++) {
            quad.setColor(vertex, WorldColorCorrectionController.applyToArgb(
                    WorldColorCorrectionController.Target.FLUIDS, quad.getColor(vertex)));
        }
    }
}
