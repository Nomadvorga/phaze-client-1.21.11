package vorga.phazeclient.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.api.system.colorcorrection.WorldColorCorrectionController;
import vorga.phazeclient.api.system.render.QueuedVertexConsumers;
import vorga.phazeclient.implement.features.modules.hud.NametagHud;
import vorga.phazeclient.implement.features.modules.other.HitColor;
import vorga.phazeclient.implement.features.modules.other.HitRange;
import vorga.phazeclient.implement.hitcolor.OverlayRendered;
import vorga.phazeclient.implement.hitcolor.OverlayReloadListener;
import vorga.phazeclient.implement.features.modules.other.ThreeDSkinsHelper;
import vorga.phazeclient.implement.hitrange.HitRangeCircleRenderer;
import vorga.phazeclient.implement.cosmetics.CosmeticsRenderer;
import vorga.phazeclient.implement.cosmetics.PreviewMarker;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, S extends LivingEntityRenderState, M extends EntityModel<? super S>> {

    @Shadow
    protected abstract float getAnimationCounter(S state);

    @Inject(
            method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;pop()V", shift = At.Shift.BEFORE)
    )
    private void phaze$renderCosmeticsInBodySpace(
            S state,
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            CameraRenderState cameraState,
            CallbackInfo ci
    ) {
        if (!(state instanceof PlayerEntityRenderState playerState)) return;
        if (playerState instanceof PreviewMarker marker
                && marker.phaze$previewSelection() != null) {
            CosmeticsRenderer.renderPreview(
                    marker.phaze$previewSelection(), marker.phaze$previewAlpha(),
                    marker.phaze$previewYaw(),
                    () -> phaze$renderCosmetics(playerState, state, matrices, queue));
            return;
        }
        phaze$renderCosmetics(playerState, state, matrices, queue);
    }

    private void phaze$renderCosmetics(
            PlayerEntityRenderState playerState,
            S state,
            MatrixStack matrices,
            OrderedRenderCommandQueue queue
    ) {
        boolean preview = CosmeticsRenderer.isRenderingPreview();
        QueuedVertexConsumers queued = preview ? null : QueuedVertexConsumers.borrow();
        VertexConsumerProvider consumers = preview
                ? CosmeticsRenderer.previewVertexConsumers()
                : queued;

        EntityModel<?> model = ((LivingEntityRenderer<?, ?, ?>) (Object) this).getModel();
        try {
            if (model instanceof PlayerEntityModel playerModel) {
                MatrixStack.Entry stableLightingEntry = matrices.peek();
                matrices.push();
                playerModel.body.applyTransform(matrices);
                CosmeticsRenderer.renderLocalPlayer(
                        matrices, consumers, playerState, state.light, stableLightingEntry);
                vorga.phazeclient.implement.cosmetics.bridge.PhazeCosmeticRenderer.renderBody(
                        matrices, consumers, playerState, state.light);
                matrices.pop();

                matrices.push();
                playerModel.head.applyTransform(matrices);
                CosmeticsRenderer.renderHeadCosmetic(
                        matrices, consumers, playerState, state.light, stableLightingEntry);
                vorga.phazeclient.implement.cosmetics.bridge.PhazeCosmeticRenderer.renderHead(
                        matrices, consumers, playerState, state.light);
                matrices.pop();
            } else {
                CosmeticsRenderer.renderLocalPlayer(
                        matrices, consumers, playerState, state.light, matrices.peek());
            }
        } finally {
            if (preview) {
                CosmeticsRenderer.flushPreviewVertexConsumers();
            } else if (queued != null) {
                queued.flush(queue);
            }
        }
    }

    @WrapOperation(
            method = {"render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V"},
            at = {@At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/entity/feature/FeatureRenderer;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/EntityRenderState;FF)V"
            )},
            require = 1
    )
    private void phaze$renderFeatureWithHitColorOverlay(
            FeatureRenderer<?, ?> featureRenderer,
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            int light,
            EntityRenderState entityRenderState,
            float limbAngle,
            float limbDistance,
            Operation<Void> original
    ) {
        if (HitColor.getInstance().isEnabled() && featureRenderer instanceof OverlayRendered rendered) {
            @SuppressWarnings("unchecked")
            S livingEntityRenderState = (S) entityRenderState;
            int overlay = LivingEntityRenderer.getOverlay(livingEntityRenderState, this.getAnimationCounter(livingEntityRenderState));
            rendered.setOverlay(overlay);
            OverlayReloadListener.event();
        }

        original.call(featureRenderer, matrices, queue, light, entityRenderState, limbAngle, limbDistance);
    }

    @Inject(
            method = "hasLabel(Lnet/minecraft/entity/LivingEntity;D)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void phaze$forceOwnNametagVisibility(LivingEntity entity, double squaredDistanceToCamera, CallbackInfoReturnable<Boolean> cir) {
        NametagHud module = NametagHud.getInstance();
        if (!module.isEnabled()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.options == null) {
            return;
        }

        boolean isSelf = entity == client.player || entity == client.getCameraEntity();
        if (!isSelf) {
            return;
        }

        if (module.hideInF1.isValue() && client.options.hudHidden) {
            cir.setReturnValue(false);
            return;
        }

        if (!module.thirdPersonNametag.isValue() && !client.options.getPerspective().isFirstPerson()) {
            cir.setReturnValue(false);
            return;
        }

        cir.setReturnValue(true);
    }

    @Inject(
            method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At("TAIL"), require = 1
    )
    private void phaze$drawHitRange(
            S state,
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            CameraRenderState cameraState,
            CallbackInfo ci
    ) {
        HitRange config = HitRange.getInstance();
        if (!config.isEnabled()) {
            return;
        }

        if (!(state instanceof PlayerEntityRenderState playerState)) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null) {
            return;
        }

        if (player.getId() == playerState.id) {
            return;
        }

        if (!HitRangeCircleRenderer.isInRange(state.x, state.y, state.z,
                player.getX(), player.getY(), player.getZ(), config.maxDistance.getInt())) {
            return;
        }

        if (config.nearestOnly.isValue()) {
            if (config.getNearest() == null || config.getNearest().getId() != playerState.id) {
                return;
            }
        }

        if (playerState.deathTime > 0.0f || playerState.invisibleToPlayer || playerState.sleepingDirection != null) {
            return;
        }

        vorga.phazeclient.api.system.render.WorldOverlayRenderer.queuePlayerCircle(playerState);
    }

    @ModifyArg(
            method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/RenderLayer;IIILnet/minecraft/client/texture/Sprite;ILnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;)V"),
            index = 6,
            require = 1
    )
    private int phaze$worldColorEntityAlpha(int color, @Local(argsOnly = true) S state) {
        if ((color >>> 24) < 250) return color;
        boolean player = state instanceof PlayerEntityRenderState;
        WorldColorCorrectionController.Target target = player
                ? WorldColorCorrectionController.Target.PLAYERS
                : WorldColorCorrectionController.Target.ENTITIES;
        if (!WorldColorCorrectionController.isTargetActive(target)) return color;
        int sentinel = player ? 254 : 253;
        return (color & 0x00FFFFFF) | (sentinel << 24);
    }

    @Inject(
            method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At("HEAD"),
            require = 1
    )
    private void phaze$applyThreeDSkins(
            S state,
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            CameraRenderState cameraState,
            CallbackInfo ci
    ) {
        EntityModel<?> model = ((LivingEntityRenderer<?, ?, ?>) (Object) this).getModel();
        if (state instanceof PlayerEntityRenderState playerState && model instanceof PlayerEntityModel) {
            ThreeDSkinsHelper.preRender(playerState);
        }
    }

    @Inject(
            method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;pop()V", shift = At.Shift.BEFORE),
            require = 1
    )
    private void phaze$restoreThreeDSkins(
            S state,
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            CameraRenderState cameraState,
            CallbackInfo ci
    ) {
        EntityModel<?> model = ((LivingEntityRenderer<?, ?, ?>) (Object) this).getModel();
        if (state instanceof PlayerEntityRenderState playerState && model instanceof PlayerEntityModel playerModel) {
            QueuedVertexConsumers queued = QueuedVertexConsumers.borrow();
            try {
                ThreeDSkinsHelper.postRender(
                        playerModel, playerState, matrices, queued,
                        state.light,
                        LivingEntityRenderer.getOverlay(playerState, this.getAnimationCounter(state))
                );
            } finally {
                queued.flush(queue);
            }
        }
    }
}
