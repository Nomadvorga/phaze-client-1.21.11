package vorga.phazeclient.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.TextureTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import vorga.phazeclient.base.util.render.GlintTextureClock;
import vorga.phazeclient.implement.features.modules.other.GlintColorizer;

@Mixin(TextureTransform.class)
public abstract class GlintTextureTransformMixin {
    @Unique private static final GlintTextureClock phaze$clock = new GlintTextureClock();

    @ModifyExpressionValue(method = "getGlintTransformation(F)Lorg/joml/Matrix4f;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Util;getMeasuringTimeMs()J"),
            require = 1, allow = 1)
    private static long phaze$integrateGlintTime(long original) {
        RenderSystem.assertOnRenderThread();
        GlintColorizer module = GlintColorizer.getInstance();
        boolean enabled = module != null && module.isEnabled();
        return phaze$clock.sample(original, enabled, enabled ? module.speed.getValue() : 1.0);
    }
}
