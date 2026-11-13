package vorga.phazeclient.mixins;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.FishingRodItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.ShieldItem;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.joml.Quaternionfc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.implement.features.modules.other.ChangeHand;
import vorga.phazeclient.implement.features.modules.other.NoRender;
import vorga.phazeclient.implement.features.modules.other.OldAnimations;

/**
 * First-person item pose. 1.21.11 applies the equip offset before {@code swingArm},
 * so the classic swing and sword-block poses replace that default equip call and
 * then skip the vanilla swing that would stack on top.
 */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
    @Unique private boolean phaze$replacedDefaultSwing;

    @Inject(
            method = "renderFirstPersonItem(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/util/Hand;FLnet/minecraft/item/ItemStack;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;push()V", ordinal = 0, shift = At.Shift.AFTER),
            require = 1
    )
    private void phaze$applyHandOffset(
            AbstractClientPlayerEntity player,
            float tickDelta,
            float pitch,
            Hand hand,
            float swingProgress,
            ItemStack item,
            float equipProgress,
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            int light,
            CallbackInfo ci
    ) {
        ChangeHand mod = ChangeHand.getInstance();
        if (mod == null || !mod.isEnabled()) return;
        if (item.isEmpty() || item.contains(DataComponentTypes.MAP_ID)) return;
        mod.applyHandOffset(matrices, hand == Hand.MAIN_HAND, player.getMainArm());
    }

    @Inject(
            method = "renderFirstPersonItem(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/util/Hand;FLnet/minecraft/item/ItemStack;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V"
            ),
            require = 1
    )
    private void phaze$applyHandTweaks(
            AbstractClientPlayerEntity player,
            float tickDelta,
            float pitch,
            Hand hand,
            float swingProgress,
            ItemStack item,
            float equipProgress,
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            int light,
            CallbackInfo ci
    ) {
        float xSign = (player.getMainArm() == Arm.LEFT) ? -1.0f : 1.0f;
        boolean isMain = hand == Hand.MAIN_HAND;

        NoRender noRender = NoRender.getInstance();
        if (noRender != null && noRender.isEnabled() && !isMain && item.getItem() instanceof ShieldItem && noRender.lowerShield.isValue()) {
            matrices.translate(0.0F, -0.2F, 0.0F);
            matrices.scale(0.85F, 0.85F, 0.85F);
        }

        OldAnimations oldAnim = OldAnimations.getInstance();
        if (oldAnim != null && oldAnim.isEnabled()) {
            if (isMain && item.getItem() instanceof FishingRodItem && oldAnim.oldRod.isValue()) {
                matrices.translate(xSign * -0.05F, 0.05F, -0.05F);
                matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(xSign * 8.0F));
                matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(8.0F));
            }
            if (oldAnim.oldBow.isValue() && item.isOf(Items.BOW) && player.isUsingItem()) {
                matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(xSign * 25.0F));
                matrices.translate(xSign * 0.02F, 0.0F, 0.0F);
            }
        }

        ChangeHand mod = ChangeHand.getInstance();
        if (mod == null || !mod.isEnabled()) return;
        mod.applyHandScale(matrices, isMain);
    }

    @WrapOperation(
            method = "renderFirstPersonItem(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/util/Hand;FLnet/minecraft/item/ItemStack;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;isUsingItem()Z", ordinal = 0),
            require = 1
    )
    private boolean phaze$suppressShieldUsing(
            AbstractClientPlayerEntity player,
            Operation<Boolean> original,
            @Local(argsOnly = true) Hand hand,
            @Local(argsOnly = true) ItemStack item
    ) {
        return phaze$swordBlockHidesShieldUse(player, hand, item) ? false : original.call(player);
    }

    @WrapOperation(
            method = "renderFirstPersonItem(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/util/Hand;FLnet/minecraft/item/ItemStack;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;isUsingItem()Z", ordinal = 1),
            require = 1
    )
    private boolean phaze$suppressShieldUsingSecond(
            AbstractClientPlayerEntity player,
            Operation<Boolean> original,
            @Local(argsOnly = true) Hand hand,
            @Local(argsOnly = true) ItemStack item
    ) {
        return phaze$swordBlockHidesShieldUse(player, hand, item) ? false : original.call(player);
    }

    @WrapOperation(
            method = "renderFirstPersonItem(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/util/Hand;FLnet/minecraft/item/ItemStack;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/item/HeldItemRenderer;applyEquipOffset(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/util/Arm;F)V",
                    ordinal = 4
            ),
            require = 1
    )
    private void phaze$oldDefaultPose(
            HeldItemRenderer instance,
            MatrixStack matrices,
            Arm arm,
            float equipProgress,
            Operation<Void> original,
            @Local(argsOnly = true) AbstractClientPlayerEntity player,
            @Local(argsOnly = true) Hand hand,
            @Local(argsOnly = true, ordinal = 2) float swingProgress
    ) {
        OldAnimations mod = OldAnimations.getInstance();
        boolean blocking = arm == player.getMainArm() && OldAnimations.isSwordBlockingActive(player);
        boolean swingingThisHand = mod != null && mod.isEnabled() && mod.oldSwing.isValue()
                && swingProgress > 0.0F && player.handSwinging && hand == player.preferredHand;
        if (blocking || swingingThisHand) {
            int armSign = arm == Arm.RIGHT ? 1 : -1;
            if (blocking) {
                OldAnimations.renderSwordBlock(matrices, arm, armSign, equipProgress, swingProgress);
            } else {
                OldAnimations.renderOldSwing(matrices, arm, armSign, swingProgress, equipProgress);
            }
            phaze$replacedDefaultSwing = true;
            return;
        }
        original.call(instance, matrices, arm, equipProgress);
    }

    @Inject(method = "swingArm", at = @At("HEAD"), cancellable = true, require = 1)
    private void phaze$skipReplacedSwing(float swingProgress, MatrixStack matrices, int armSign, Arm arm, CallbackInfo ci) {
        if (!phaze$replacedDefaultSwing) return;
        phaze$replacedDefaultSwing = false;
        ci.cancel();
    }

    @Inject(
            method = "renderFirstPersonItem(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/util/Hand;FLnet/minecraft/item/ItemStack;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V",
            at = @At("TAIL")
    )
    private void phaze$clearReplacedSwing(CallbackInfo ci) {
        phaze$replacedDefaultSwing = false;
    }

    @WrapWithCondition(
            method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;multiply(Lorg/joml/Quaternionfc;)V"),
            require = 1
    )
    private boolean phaze$skipHandSway(MatrixStack instance, Quaternionfc rotation) {
        ChangeHand mod = ChangeHand.getInstance();
        return mod == null || !mod.isEnabled() || !mod.noHandSway.isValue();
    }

    @Inject(method = "shouldSkipHandAnimationOnSwap", at = @At("HEAD"), cancellable = true, require = 1)
    private void phaze$noReEquipOnSwap(ItemStack from, ItemStack to, CallbackInfoReturnable<Boolean> cir) {
        OldAnimations mod = OldAnimations.getInstance();
        if (mod != null && mod.isEnabled() && mod.noReEquip.isValue() && from.isOf(to.getItem())) {
            cir.setReturnValue(true);
        }
    }

    @Redirect(
            method = "updateHeldItems",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getHandEquippingProgress(F)F"),
            require = 1
    )
    private float phaze$noReEquipOnAttackCooldown(ClientPlayerEntity player, float tickDelta) {
        OldAnimations mod = OldAnimations.getInstance();
        if (mod != null && mod.isEnabled() && mod.noReEquip.isValue()) return 1.0F;
        return player.getHandEquippingProgress(tickDelta);
    }

    @Unique
    private static boolean phaze$swordBlockHidesShieldUse(AbstractClientPlayerEntity player, Hand hand, ItemStack item) {
        return hand == Hand.OFF_HAND && item.getItem() instanceof ShieldItem && OldAnimations.isSwordBlockingActive(player);
    }
}
