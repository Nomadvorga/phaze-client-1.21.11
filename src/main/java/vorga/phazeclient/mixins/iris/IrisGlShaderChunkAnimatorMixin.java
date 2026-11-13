package vorga.phazeclient.mixins.iris;

import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import vorga.phazeclient.base.util.shader.ChunkAnimatorShaderPatcher;
import vorga.phazeclient.base.util.shader.IrisEntityShaderPatcher;
import vorga.phazeclient.base.util.shader.IrisFluidShaderPatcher;
import vorga.phazeclient.base.util.shader.IrisSkyShaderPatcher;

@Mixin(targets = "net.irisshaders.iris.gl.shader.GlShader", remap = false)
public abstract class IrisGlShaderChunkAnimatorMixin {

    @ModifyArgs(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/irisshaders/iris/gl/shader/GlShader;createShader(Lnet/irisshaders/iris/gl/shader/ShaderType;Ljava/lang/String;Ljava/lang/String;)I",
                    remap = false
            ),
            remap = false
    )
    private static void phaze$injectIrisShaderPatches(Args args) {
        String name = args.get(1);
        String source = args.get(2);

        String patched = ChunkAnimatorShaderPatcher.patch(source);
        patched = IrisSkyShaderPatcher.patch(name, patched);
        patched = IrisEntityShaderPatcher.patch(name, patched);
        patched = IrisFluidShaderPatcher.patch(name, patched);
        args.set(2, patched);
    }
}
