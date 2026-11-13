package vorga.phazeclient.mixins;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.RenderLayer;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import vorga.phazeclient.implement.features.modules.other.GlintColorizer;

/** Per-draw DynamicTransforms tint, never a global shader color requiring a reset. */
@Mixin(RenderLayer.class)
public abstract class GlintColorizerMixin {
    @Shadow @Final protected String name;

    @ModifyArg(method = "draw(Lnet/minecraft/client/render/BuiltBuffer;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/DynamicUniforms;write(Lorg/joml/Matrix4fc;Lorg/joml/Vector4fc;Lorg/joml/Vector3fc;Lorg/joml/Matrix4fc;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"),
            index = 1, require = 1, allow = 1)
    private Vector4fc phaze$tintGlint(Vector4fc original) {
        if (!name.contains("glint")) return original;
        RenderSystem.assertOnRenderThread();
        GlintColorizer module = GlintColorizer.getInstance();
        if (module == null || !module.isEnabled()) return original;
        int color = module.getActiveGlintColor();
        return new Vector4f(original.x() * ((color >>> 16) & 255) / 255.0F,
                original.y() * ((color >>> 8) & 255) / 255.0F,
                original.z() * (color & 255) / 255.0F,
                original.w() * ((color >>> 24) & 255) / 255.0F);
    }
}
