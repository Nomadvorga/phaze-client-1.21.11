package vorga.phazeclient.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.command.BatchingRenderCommandQueue;
import net.minecraft.client.render.command.OrderedRenderCommandQueueImpl;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import vorga.phazeclient.api.system.colorcorrection.WorldColorCorrectionController;
import vorga.phazeclient.api.system.colorcorrection.WorldColorCorrectionController.Target;

/**
 * Feature renderers, held items and block entities submit their own colours.
 * The living-entity body sentinel only covers core/entity. Everything else is baked here.
 */
@Mixin(BatchingRenderCommandQueue.class)
public abstract class RenderCommandQueueWorldColorMixin {

    @ModifyVariable(
            method = "submitModel",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 2,
            require = 1
    )
    private int phaze$tintModel(int color, @Local(argsOnly = true) RenderLayer layer) {
        return tintModelColor(color, layer);
    }

    @ModifyVariable(
            method = "submitModelPart",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 2,
            require = 1
    )
    private int phaze$tintModelPart(int color, @Local(argsOnly = true) RenderLayer layer) {
        return tintModelColor(color, layer);
    }

    @ModifyVariable(
            method = "submitItem",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 1
    )
    private int[] phaze$tintItemLayers(int[] tints) {
        Target target = tintTarget();
        if (target == null || tints == null || tints.length == 0) return tints;
        int[] copy = null;
        for (int i = 0; i < tints.length; i++) {
            int tinted = WorldColorCorrectionController.applyToArgb(target, tints[i]);
            if (tinted == tints[i]) continue;
            if (copy == null) copy = tints.clone();
            copy[i] = tinted;
        }
        return copy == null ? tints : copy;
    }

    @WrapOperation(
            method = "submitBlockStateModel",
            at = @At(
                    value = "NEW",
                    target = "net/minecraft/client/render/command/OrderedRenderCommandQueueImpl$BlockStateModelCommand"
            ),
            require = 1
    )
    private OrderedRenderCommandQueueImpl.BlockStateModelCommand phaze$tintBlockStateModel(
            MatrixStack.Entry entry,
            RenderLayer layer,
            BlockStateModel model,
            float red,
            float green,
            float blue,
            int light,
            int overlay,
            int outline,
            Operation<OrderedRenderCommandQueueImpl.BlockStateModelCommand> original
    ) {
        Target target = tintTarget();
        if (target != null) {
            int rgb = WorldColorCorrectionController.applyRgb(
                    Math.round(red * 255.0F), Math.round(green * 255.0F), Math.round(blue * 255.0F), target);
            red = ((rgb >>> 16) & 255) / 255.0F;
            green = ((rgb >>> 8) & 255) / 255.0F;
            blue = (rgb & 255) / 255.0F;
        }
        return original.call(entry, layer, model, red, green, blue, light, overlay, outline);
    }

    private static int tintModelColor(int color, RenderLayer layer) {
        Target target = tintTarget();
        if (target == null) return color;
        if ((target == Target.PLAYERS || target == Target.ENTITIES) && usesEntityShader(layer)) {
            if ((color >>> 24) < 250) return color;
            int sentinel = target == Target.PLAYERS ? 254 : 253;
            return (color & 0x00FFFFFF) | (sentinel << 24);
        }
        return WorldColorCorrectionController.applyToArgb(target, color);
    }

    private static boolean usesEntityShader(RenderLayer layer) {
        if (layer == null) return false;
        var pipeline = layer.getRenderPipeline();
        if (pipeline == null || pipeline.getFragmentShader() == null) return false;
        return "minecraft".equals(pipeline.getFragmentShader().getNamespace())
                && "core/entity".equals(pipeline.getFragmentShader().getPath());
    }

    private static Target tintTarget() {
        Target target = WorldColorCorrectionController.current();
        if (target != Target.BLOCKS && target != Target.PLAYERS && target != Target.ENTITIES) return null;
        return WorldColorCorrectionController.isTargetActive(target) ? target : null;
    }
}
