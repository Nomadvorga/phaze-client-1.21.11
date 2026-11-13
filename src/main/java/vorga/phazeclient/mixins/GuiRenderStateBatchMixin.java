package vorga.phazeclient.mixins;

import net.minecraft.client.gui.render.state.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.api.system.shape.batched.GuiBatch;

/** Includes chat's direct DrawnTextConsumer path, which bypasses DrawContext.drawText. */
@Mixin(GuiRenderState.class)
public abstract class GuiRenderStateBatchMixin {
    @Inject(method = {"addItem", "addText", "addSpecialElement", "addSimpleElement", "addSimpleElementToCurrentLayer"},
            at = @At("HEAD"), require = 1)
    private void phaze$queued(CallbackInfo ci) { GuiBatch.queuedState((GuiRenderState) (Object) this); }

    @Inject(method = "clear", at = @At("RETURN"), require = 1)
    private void phaze$cleared(CallbackInfo ci) { GuiBatch.clearedState((GuiRenderState) (Object) this); }
}
