package vorga.phazeclient.mixins;

import com.mojang.blaze3d.shaders.ShaderType;
import net.minecraft.client.gl.ShaderLoader;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.base.util.shader.WorldColorShaderPatcher;

@Mixin(ShaderLoader.class)
public abstract class ShaderLoaderWorldColorMixin {
    @Inject(method = "getSource(Lnet/minecraft/util/Identifier;Lcom/mojang/blaze3d/shaders/ShaderType;)Ljava/lang/String;",
            at = @At("RETURN"), cancellable = true, require = 1)
    private void phaze$patchWorldColor(Identifier id, ShaderType type, CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(WorldColorShaderPatcher.patch(id, type, cir.getReturnValue()));
    }
}
