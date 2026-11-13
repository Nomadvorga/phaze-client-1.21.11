package vorga.phazeclient.mixins.sodium;

import net.minecraft.client.render.CloudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import vorga.phazeclient.api.system.colorcorrection.WorldColorCorrectionController;

/**
 * Sodium 0.8.14 has no separate cloud renderer. It overwrites mesh emission inside
 * vanilla CloudRenderer and bakes this colour into the vertex buffer, so the
 * rendertype_clouds shader skips its own tint while Sodium is loaded.
 */
@Mixin(CloudRenderer.class)
public abstract class SodiumCloudRendererWorldColorMixin {

    @ModifyVariable(method = "renderClouds", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 1)
    private int phaze$tintSodiumClouds(int color) {
        return WorldColorCorrectionController.applyToArgb(WorldColorCorrectionController.Target.CLOUDS, color);
    }
}
