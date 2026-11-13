package vorga.phazeclient.mixins.sodium;

import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
import net.caffeinemc.mods.sodium.client.render.model.MutableQuadViewImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.api.system.colorcorrection.WorldColorCorrectionController;

/** Sodium 1.21.11 has no processQuad on the old FRAPI type; the protected emitter is the quad exit. */
@Mixin(value = BlockRenderer.class, remap = false)
public abstract class SodiumBlockRendererWorldColorMixin {
    @Inject(method = "processQuad", at = @At("TAIL"), remap = false, require = 1)
    private void phaze$tintBlockQuad(MutableQuadViewImpl quad, CallbackInfo ci) {
        for (int vertex = 0; vertex < 4; vertex++) {
            quad.setColor(vertex, WorldColorCorrectionController.applyToArgb(
                    WorldColorCorrectionController.Target.BLOCKS, quad.getColor(vertex)));
        }
    }
}
