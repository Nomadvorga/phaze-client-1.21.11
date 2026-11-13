package vorga.phazeclient.mixins;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.CapeFeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Items;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.cosmetics.CosmeticsRenderer;
import vorga.phazeclient.implement.cosmetics.CosmeticsState;
import vorga.phazeclient.implement.features.modules.other.WaveCapes;
import vorga.phazeclient.implement.features.modules.other.WavyCapeRenderer;

@Mixin(CapeFeatureRenderer.class)
public abstract class CapeFeatureRendererMixin
        extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {
    @Shadow @Final private BipedEntityModel<PlayerEntityRenderState> model;

    private CapeFeatureRendererMixin(
            FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
    }

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/PlayerEntityRenderState;FF)V", at = @At("HEAD"), cancellable = true, require = 1)
    private void phaze$renderCustomCape(
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            int light,
            PlayerEntityRenderState state,
            float limbAngle,
            float limbDistance,
            CallbackInfo ci
    ) {
        if (state.invisible || !state.capeVisible) return;
        WaveCapes waveCapes = WaveCapes.getInstance();
        boolean wavy = waveCapes != null && waveCapes.isEnabled();
        String selection = CosmeticsRenderer.capeSelectionFor(state);
        if (CosmeticsState.isCape(selection)) {
            ci.cancel();
            if (!CosmeticsRenderer.shouldRenderFor(state)) return;
            Identifier texture = CosmeticsRenderer.capeTexture(selection);
            if (texture == null) return;
            matrices.push();
            if (wavy) {
                getContextModel().body.applyTransform(matrices);
                WavyCapeRenderer.renderWavyCape(matrices, queue, texture, light, state,
                        CosmeticsState.PHAZE_CAPE.equalsIgnoreCase(selection));
            } else {
                queue.submitModel(model, state, matrices, RenderLayers.entitySolid(texture),
                        light, OverlayTexture.DEFAULT_UV, state.outlineColor, null);
            }
            matrices.pop();
            return;
        }

        if (!wavy || (state.equippedChestStack != null && state.equippedChestStack.isOf(Items.ELYTRA))) return;
        AssetInfo.TextureAsset cape = state.skinTextures == null ? null : state.skinTextures.cape();
        if (cape == null) return;
        ci.cancel();
        matrices.push();
        getContextModel().body.applyTransform(matrices);
        WavyCapeRenderer.renderWavyCape(matrices, queue, cape.texturePath(), light, state, false);
        matrices.pop();
    }
}
