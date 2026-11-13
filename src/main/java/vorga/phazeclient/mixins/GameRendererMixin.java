package vorga.phazeclient.mixins;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.implement.features.modules.other.AspectRatio;
import vorga.phazeclient.implement.features.modules.other.ColorCorrection;
import vorga.phazeclient.implement.features.modules.other.LowHealthWarning;
import vorga.phazeclient.implement.features.modules.other.MotionBlur;

@Mixin(value = GameRenderer.class, priority = 1100)
public abstract class GameRendererMixin {

    @Shadow public abstract float getFarPlaneDistance();

    @Inject(method = "getBasicProjectionMatrix", at = @At("TAIL"), cancellable = true)
    private void phaze$overrideAspect(float fovDegrees, CallbackInfoReturnable<Matrix4f> cir) {
        AspectRatio module = AspectRatio.getInstance();
        if (module == null || !module.isEnabled()) {
            return;
        }

        Matrix4f projection = new Matrix4f();

        float ratio = module.getRatio();

        projection.perspective(
                (float) (fovDegrees * (Math.PI / 180.0)),
                ratio,
                0.05F,
                getFarPlaneDistance()
        );

        cir.setReturnValue(projection);
    }

    /** Phaze clips mirror into this frame's vanilla scissor stack, so it must be known before any GUI code runs. */
    @com.llamalad7.mixinextras.injector.ModifyExpressionValue(method = "render",
            at = @At(value = "NEW", target = "net/minecraft/client/gui/DrawContext"), require = 1)
    private DrawContext phaze$trackFrameContext(DrawContext context) {
        vorga.phazeclient.api.system.shape.batched.GuiBatch.use(context);
        return context;
    }

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/render/GuiRenderer;render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V"), require = 1)
    private void phaze$renderLowHealthAboveGui(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci,
                                               @Local DrawContext context) {
        LowHealthWarning.getInstance().render(context);
    }

    @Inject(method = "renderWorld", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/WorldRenderer;render(Lnet/minecraft/client/util/memory/ObjectAllocator;Lnet/minecraft/client/render/RenderTickCounter;ZLnet/minecraft/client/render/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V",
            shift = At.Shift.AFTER), require = 1)
    private void phaze$drawWorldOverlaysBeforeDepthClear(RenderTickCounter tickCounter, CallbackInfo ci) {
        vorga.phazeclient.api.system.render.WorldOverlayRenderer.draw();
    }

    @Inject(
            method = "renderWorld",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/GameRenderer;renderHand(FZLorg/joml/Matrix4f;)V",
                    shift = At.Shift.BEFORE
            ),
            require = 1
    )
    private void phaze$applyMotionBlur(RenderTickCounter tickCounter, CallbackInfo ci) {
        MotionBlur module = MotionBlur.getInstance();
        if (module == null || !module.isEnabled()) return;
        if (module.shader == null) return;
        module.shader.applyMotionBlurBeforeHands();
    }

    @Inject(
            method = "renderWorld",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/GameRenderer;renderHand(FZLorg/joml/Matrix4f;)V",
                    shift = At.Shift.AFTER
            ),
            require = 1
    )
    private void phaze$applyColorCorrection(RenderTickCounter tickCounter, CallbackInfo ci) {
        ColorCorrection module = ColorCorrection.getInstance();
        if (module == null || !module.isEnabled()) return;
        if (module.shader == null) return;
        module.shader.apply();
    }
}
