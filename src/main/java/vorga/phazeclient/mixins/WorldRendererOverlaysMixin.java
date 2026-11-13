package vorga.phazeclient.mixins;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.memory.ObjectAllocator;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.api.system.render.WorldOverlayRenderer;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererOverlaysMixin {

    @Inject(method = "render", at = @At("HEAD"), require = 1)
    private void phaze$beginWorldOverlays(ObjectAllocator allocator, RenderTickCounter counter,
                                          boolean outline, Camera camera, Matrix4f view,
                                          Matrix4f projection, Matrix4f cullingProjection,
                                          GpuBufferSlice fog, Vector4f fogColor, boolean sky,
                                          CallbackInfo ci) {
        // In 1.21.11 the second matrix includes bob/nausea; the third is culling-only.
        WorldOverlayRenderer.beginFrame(view, projection, camera, counter);
    }
}
