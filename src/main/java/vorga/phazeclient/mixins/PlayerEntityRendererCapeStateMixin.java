package vorga.phazeclient.mixins;

import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.PlayerLikeEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.OldAnimations;

/**
 * Third-person sword block pose. Custom capes are drawn by
 * {@link CapeFeatureRendererMixin}; skin textures no longer carry an Identifier cape.
 */
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererCapeStateMixin {
    @Inject(
            method = "updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V",
            at = @At("RETURN"),
            require = 1
    )
    private void phaze$applyOldAnimationsThirdPerson(
            PlayerLikeEntity player,
            PlayerEntityRenderState state,
            float tickDelta,
            CallbackInfo ci
    ) {
        if (!(player instanceof PlayerEntity entity) || !OldAnimations.isSwordBlockingActive(entity)) return;
        if (state.mainArm == Arm.RIGHT) {
            state.rightArmPose = BipedEntityModel.ArmPose.BLOCK;
            if (state.leftArmPose == BipedEntityModel.ArmPose.BLOCK) {
                state.leftArmPose = BipedEntityModel.ArmPose.ITEM;
            }
        } else {
            state.leftArmPose = BipedEntityModel.ArmPose.BLOCK;
            if (state.rightArmPose == BipedEntityModel.ArmPose.BLOCK) {
                state.rightArmPose = BipedEntityModel.ArmPose.ITEM;
            }
        }
    }
}
