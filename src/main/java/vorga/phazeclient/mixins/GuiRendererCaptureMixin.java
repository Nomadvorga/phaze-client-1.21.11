package vorga.phazeclient.mixins;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import vorga.phazeclient.api.system.shape.batched.GuiBatch;

/** Only redirect the final GUI pass, not the item/model atlas preparation passes. */
@Mixin(GuiRenderer.class)
public abstract class GuiRendererCaptureMixin {
    @ModifyArgs(method = "renderPreparedDraws", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/ProjectionMatrix2;set(FF)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"), require = 1)
    private void phaze$captureProjection(Args args) {
        if (!GuiBatch.isFlushing()) return;
        args.set(0, vorga.phazeclient.api.system.draw.GuiProjection.width(args.get(0)));
        args.set(1, vorga.phazeclient.api.system.draw.GuiProjection.height(args.get(1)));
    }

    @ModifyArgs(method = "enableScissor", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderPass;enableScissor(IIII)V"), require = 1)
    private void phaze$captureScissor(Args args,
                                      @com.llamalad7.mixinextras.sugar.Local(argsOnly = true) net.minecraft.client.gui.ScreenRect rect) {
        if (!GuiBatch.isFlushing() || RenderSystem.outputColorTextureOverride == null) return;
        var texture = RenderSystem.outputColorTextureOverride.texture();
        var window = net.minecraft.client.MinecraftClient.getInstance().getWindow();
        float sx = texture.getWidth(0) / vorga.phazeclient.api.system.draw.GuiProjection.width(
                (float) window.getFramebufferWidth() / window.getScaleFactor());
        float sy = texture.getHeight(0) / vorga.phazeclient.api.system.draw.GuiProjection.height(
                (float) window.getFramebufferHeight() / window.getScaleFactor());
        int left = Math.max(0, (int) Math.floor(rect.getLeft() * sx));
        int right = Math.min(texture.getWidth(0), (int) Math.ceil(rect.getRight() * sx));
        int top = Math.max(0, (int) Math.floor(rect.getTop() * sy));
        int bottom = Math.min(texture.getHeight(0), (int) Math.ceil(rect.getBottom() * sy));
        args.set(0, left);
        args.set(1, texture.getHeight(0) - bottom);
        args.set(2, Math.max(0, right - left));
        args.set(3, Math.max(0, bottom - top));
    }
    @ModifyArgs(method = "render(Ljava/util/function/Supplier;Lnet/minecraft/client/gl/Framebuffer;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lcom/mojang/blaze3d/buffers/GpuBuffer;Lcom/mojang/blaze3d/vertex/VertexFormat$IndexType;II)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/CommandEncoder;createRenderPass(Ljava/util/function/Supplier;Lcom/mojang/blaze3d/textures/GpuTextureView;Ljava/util/OptionalInt;Lcom/mojang/blaze3d/textures/GpuTextureView;Ljava/util/OptionalDouble;)Lcom/mojang/blaze3d/systems/RenderPass;"),
            require = 1)
    private void phaze$captureGuiOutput(Args args) {
        if (GuiBatch.isFlushing() && RenderSystem.outputColorTextureOverride != null) {
            args.set(1, RenderSystem.outputColorTextureOverride);
            args.set(3, RenderSystem.outputDepthTextureOverride);
        }
    }
}
