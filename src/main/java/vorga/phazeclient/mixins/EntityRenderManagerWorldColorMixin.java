package vorga.phazeclient.mixins;

import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.api.system.colorcorrection.WorldColorCorrectionController;

@Mixin(EntityRenderManager.class)
public abstract class EntityRenderManagerWorldColorMixin {

    @Inject(
            method = "render(Lnet/minecraft/client/render/entity/state/EntityRenderState;Lnet/minecraft/client/render/state/CameraRenderState;DDDLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;)V",
            at = @At("HEAD"),
            require = 1
    )
    private void phaze$pushEntityColor(EntityRenderState state, CameraRenderState camera, double x, double y, double z, MatrixStack matrices, OrderedRenderCommandQueue queue, CallbackInfo ci) {
        WorldColorCorrectionController.push(state instanceof PlayerEntityRenderState
                ? WorldColorCorrectionController.Target.PLAYERS
                : WorldColorCorrectionController.Target.ENTITIES);
    }

    @Inject(
            method = "render(Lnet/minecraft/client/render/entity/state/EntityRenderState;Lnet/minecraft/client/render/state/CameraRenderState;DDDLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;)V",
            at = @At("RETURN"),
            require = 1
    )
    private void phaze$popEntityColor(EntityRenderState state, CameraRenderState camera, double x, double y, double z, MatrixStack matrices, OrderedRenderCommandQueue queue, CallbackInfo ci) {
        WorldColorCorrectionController.pop();
    }
}
