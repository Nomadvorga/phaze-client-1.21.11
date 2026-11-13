package vorga.phazeclient.mixins;

import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.debug.ChunkBorderDebugRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.world.debug.DebugDataStore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.api.system.render.WorldOverlayRenderer;
import vorga.phazeclient.implement.features.modules.other.ChunkBorders;

@Mixin(ChunkBorderDebugRenderer.class)
public abstract class ChunkBorderDebugRendererMixin {

    @Inject(
            method = "render(DDDLnet/minecraft/world/debug/DebugDataStore;Lnet/minecraft/client/render/Frustum;F)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 1
    )
    private void phaze$overrideChunkBorderRenderer(
            double cameraX,
            double cameraY,
            double cameraZ,
            DebugDataStore store,
            Frustum frustum,
            float tickProgress,
            CallbackInfo ci
    ) {
        ChunkBorders module = ChunkBorders.getInstance();
        if (module != null && module.isEnabled()) {
            ci.cancel();
            module.renderCustomChunkBorders(new MatrixStack(), WorldOverlayRenderer.CONSUMERS, cameraX, cameraY, cameraZ);
        }
    }
}
