package vorga.phazeclient.mixins;

import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.api.system.shape.batched.BatchedRectangle;
import vorga.phazeclient.api.system.shape.batched.GuiBatch;

@Mixin(DrawContext.class)
public abstract class DrawContextBatchMixin {
    @Inject(method = {"fill", "fillGradient", "drawText", "drawGuiTexture", "drawTexture", "drawItem", "addEntity", "addPlayerSkin"},
            at = @At("HEAD"), require = 1)
    private void phaze$beforeDeferredDraw(CallbackInfo ci) {
        vorga.phazeclient.api.system.shape.batched.GuiDrawStats.bufferDraws++;
        BatchedRectangle.flushIfBatching();
        GuiBatch.queued((DrawContext) (Object) this);
    }

    @Inject(method = {"enableScissor", "disableScissor"}, at = @At("HEAD"), require = 1)
    private void phaze$beforeScissor(CallbackInfo ci) {
        if (vorga.phazeclient.base.util.render.ScissorManager.isSyncingVanilla()) return;
        GuiBatch.use((DrawContext) (Object) this);
        GuiBatch.flush();
    }
}
