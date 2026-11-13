package vorga.phazeclient.mixins.iris;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.attribute.EnvironmentAttributes;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import vorga.phazeclient.api.system.colorcorrection.WorldColorCorrectionController;
import vorga.phazeclient.implement.features.modules.other.SkyCustomizer;

@Mixin(targets = "net.irisshaders.iris.uniforms.CommonUniforms", remap = false)
public abstract class IrisSkyColorUniformMixin {

    @ModifyReturnValue(method = "getSkyColor", at = @At("RETURN"), require = 1, remap = false)
    private static Vector3d phaze$tintIrisSkyColor(Vector3d vanilla) {
        if (vanilla == null) {
            return vanilla;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return vanilla;
        ClientWorld world = mc.world;
        if (world == null) return vanilla;

        int r = clamp255((int) Math.round(vanilla.x * 255.0));
        int g = clamp255((int) Math.round(vanilla.y * 255.0));
        int b = clamp255((int) Math.round(vanilla.z * 255.0));
        int incoming = 0xFF000000 | (r << 16) | (g << 8) | b;

        int working = incoming;
        SkyCustomizer module = SkyCustomizer.getInstance();
        if (module != null && module.isEnabled()) {
            Float skyBrightness = world.getEnvironmentAttributes().getAttributeValue(EnvironmentAttributes.SKY_LIGHT_FACTOR_VISUAL);
            working = module.applyToSky(incoming, skyBrightness == null ? 0.5F : skyBrightness);
        }

        int corrected = WorldColorCorrectionController.applyToArgb(
                WorldColorCorrectionController.Target.SKY,
                0xFF000000 | (working & 0x00FFFFFF)
        );

        return new Vector3d(
                ((corrected >> 16) & 0xFF) / 255.0,
                ((corrected >> 8) & 0xFF) / 255.0,
                (corrected & 0xFF) / 255.0
        );
    }

    private static int clamp255(int v) {
        if (v < 0) return 0;
        if (v > 255) return 255;
        return v;
    }
}
