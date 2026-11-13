package vorga.phazeclient.mixins;

import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.ArmedEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.OldAnimations;

@Mixin(HeldItemFeatureRenderer.class)
public abstract class HeldItemFeatureRendererPreviewAlphaMixin {
    @Inject(
            method = "renderItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/item/ItemRenderState;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;III)V"
            ),
            require = 1
    )
    private void phaze$alignSwordBlockingThirdPerson(
            ArmedEntityRenderState entityState,
            ItemRenderState itemState,
            ItemStack stack,
            Arm arm,
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            int light,
            CallbackInfo ci
    ) {
        if (entityState instanceof PlayerEntityRenderState playerState) {
            OldAnimations mod = OldAnimations.getInstance();
            if (mod != null && mod.isEnabled() && mod.swordBlocking.isValue()) {
                boolean isMainArm = arm == playerState.mainArm;
                boolean isBlockingArm = isMainArm && (playerState.rightArmPose == BipedEntityModel.ArmPose.BLOCK
                        || playerState.leftArmPose == BipedEntityModel.ArmPose.BLOCK);
                if (isBlockingArm) {
                    float sign = arm == Arm.LEFT ? -1.0F : 1.0F;
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sign * 5.0F));
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(sign * 30.0F));
                    matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(sign * 25.0F));
                }
            }
        }
    }
}
