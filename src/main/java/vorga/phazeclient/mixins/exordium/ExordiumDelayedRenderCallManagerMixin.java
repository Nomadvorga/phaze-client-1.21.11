package vorga.phazeclient.mixins.exordium;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.api.system.hud.ExordiumAnimationBridge;

@Pseudo
@Mixin(targets = "dev.tr7zw.exordium.util.DelayedRenderCallManager", remap = false)
public abstract class ExordiumDelayedRenderCallManagerMixin {
    // 1.4.3 uses CustomShaderManager, not MultiStateHolder. HEAD also handles an empty vanilla queue.
    @Inject(method = "renderComponents", at = @At("HEAD"), require = 1, remap = false)
    private void phaze$renderAnimatedCachedTextures(CallbackInfo ci) {
        ExordiumAnimationBridge.renderDeferredComponents();
    }
}
