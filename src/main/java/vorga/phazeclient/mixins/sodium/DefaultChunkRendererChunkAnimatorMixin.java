package vorga.phazeclient.mixins.sodium;

import net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.base.util.shader.SodiumChunkAnimatorUniforms;

@Mixin(value = DefaultChunkRenderer.class, remap = false)
public abstract class DefaultChunkRendererChunkAnimatorMixin {
    @Inject(
            method = "setModelMatrixUniforms",
            at = @At("TAIL"),
            remap = false
    )
    private static void phaze$uploadChunkColumnAnimation(
            net.caffeinemc.mods.sodium.client.render.chunk.shader.ChunkShaderInterface shader,
            RenderRegion region,
            net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform camera,
            net.caffeinemc.mods.sodium.client.gl.buffer.GlBuffer chunkData,
            CallbackInfo ci) {
        SodiumChunkAnimatorUniforms.upload(region);
    }

    private static final float[] PHAZE$DIR = new float[3];
    private static final int[] PHAZE$SLOT_BUFFER = new int[256];
    private static final float[] PHAZE$SCALAR_SCRATCH = new float[256];

    private static void phaze$fillIdentity(float[] vec4) {
        for (int i = 0; i < 256; i++) {
            int offset = i * 4;
            vec4[offset] = 0.0F;
            vec4[offset + 1] = 0.0F;
            vec4[offset + 2] = 0.0F;
            vec4[offset + 3] = 1.0F;
        }
    }

    /** CPU payload shared by the Sodium uniform upload and the offline geometry checks. */
    private static boolean phaze$packAnimData(
            vorga.phazeclient.implement.features.modules.other.ChunkAnimator animator, RenderRegion region,
            int slotCount, int mode, float[] vec4) {
        phaze$fillIdentity(vec4);
        if (mode == 1) {
            boolean hasNonZero = animator.writeRegionSectionYOffsets(
                    region.getOriginX(), region.getOriginY(), region.getOriginZ(),
                    PHAZE$SLOT_BUFFER, slotCount, PHAZE$SCALAR_SCRATCH);
            if (!hasNonZero) return false;
            animator.writeAnimationDirectionPerSection(PHAZE$DIR);
            for (int i = 0; i < 256; i++) {
                float magnitude = PHAZE$SCALAR_SCRATCH[i];
                if (magnitude != 0.0F) {
                    int offset = i * 4;
                    vec4[offset] = magnitude * PHAZE$DIR[0];
                    vec4[offset + 1] = magnitude * PHAZE$DIR[1];
                    vec4[offset + 2] = magnitude * PHAZE$DIR[2];
                }
            }
            return true;
        }
        if (mode == 2) {
            if (!animator.writeRegionSectionFadeValues(
                    region.getOriginX(), region.getOriginY(), region.getOriginZ(),
                    PHAZE$SLOT_BUFFER, slotCount, PHAZE$SCALAR_SCRATCH)) return false;
            for (int i = 0; i < 256; i++) vec4[i * 4 + 3] = PHAZE$SCALAR_SCRATCH[i];
            return true;
        }
        if (mode == 3) {
            if (!animator.writeRegionSectionScaleValues(
                    region.getOriginX(), region.getOriginY(), region.getOriginZ(),
                    PHAZE$SLOT_BUFFER, slotCount, PHAZE$SCALAR_SCRATCH)) return false;
            for (int i = 0; i < 256; i++) vec4[i * 4 + 3] = PHAZE$SCALAR_SCRATCH[i];
            return true;
        }
        return false;
    }
}
