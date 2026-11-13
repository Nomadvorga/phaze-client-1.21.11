package vorga.phazeclient.base.util.shader;

import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.util.iterator.ByteIterator;
import org.lwjgl.opengl.GL20;
import vorga.phazeclient.implement.features.modules.other.ChunkAnimator;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** Uploads the shared column clock. Sodium's native section timestamps are not the animation clock. */
public final class SodiumChunkAnimatorUniforms {
    private static final float[] DIRECTION = new float[3];
    private static final float[] REGION_OFFSET = new float[3];
    private static final float[] VALUES = new float[256];
    private static final int[] SLOTS = new int[256];
    private static final Map<Integer, Locations> PROGRAMS = new HashMap<>();

    private SodiumChunkAnimatorUniforms() { }

    public static void upload(RenderRegion region) {
        if (region == null) return;
        int program = GL20.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        if (program == 0) return;

        Locations locations = PROGRAMS.computeIfAbsent(program, SodiumChunkAnimatorUniforms::findLocations);
        ChunkAnimator animator = ChunkAnimator.getInstance();
        int mode = animator != null && animator.isEnabled() ? animator.getAnimationModeIndex() : 0;
        if (locations.mode() >= 0) GL20.glUniform1i(locations.mode(), mode);
        if (animator == null || mode == 0) {
            if (locations.section() >= 0) {
                Arrays.fill(VALUES, 0.0F);
                GL20.glUniform1fv(locations.section(), VALUES);
            }
            return;
        }

        animator.writeAnimationDirectionPerSection(DIRECTION);
        if (locations.direction() >= 0) {
            GL20.glUniform3f(locations.direction(), DIRECTION[0], DIRECTION[1], DIRECTION[2]);
        }

        int count = collectSlots(region);
        if (count <= 0) return;
        int originX = region.getOriginX();
        int originY = region.getOriginY();
        int originZ = region.getOriginZ();
        if (locations.section() < 0) {
            if (mode != 1 || locations.regionOffset() < 0) return;
            float magnitude = animator.getRegionMagnitude(originX, originY, originZ, SLOTS, count);
            if (magnitude == 0.0F) return;
            GL20.glGetUniformfv(program, locations.regionOffset(), REGION_OFFSET);
            GL20.glUniform3f(locations.regionOffset(),
                    REGION_OFFSET[0] + DIRECTION[0] * magnitude,
                    REGION_OFFSET[1] + DIRECTION[1] * magnitude,
                    REGION_OFFSET[2] + DIRECTION[2] * magnitude);
            return;
        }

        boolean active = mode == 2
                ? animator.writeRegionSectionFadeValues(originX, originY, originZ, SLOTS, count, VALUES)
                : mode == 3
                ? animator.writeRegionSectionScaleValues(originX, originY, originZ, SLOTS, count, VALUES)
                : animator.writeRegionSectionYOffsets(originX, originY, originZ, SLOTS, count, VALUES);
        if (!active && mode != 1) Arrays.fill(VALUES, 1.0F);
        GL20.glUniform1fv(locations.section(), VALUES);
    }

    private static int collectSlots(RenderRegion region) {
        ChunkRenderList list = region.getRenderList();
        if (list == null) return 0;
        ByteIterator iterator = list.sectionsWithGeometryIterator(false);
        if (iterator == null) return 0;
        int count = 0;
        while (iterator.hasNext() && count < SLOTS.length) SLOTS[count++] = iterator.nextByteAsInt();
        return count;
    }

    private static Locations findLocations(int program) {
        return new Locations(
                GL20.glGetUniformLocation(program, "u_PhazeChunkAnimMode"),
                GL20.glGetUniformLocation(program, "u_PhazeSectionValue"),
                GL20.glGetUniformLocation(program, "u_PhazeChunkAnimDirection"),
                GL20.glGetUniformLocation(program, "u_RegionOffset")
        );
    }

    private record Locations(int mode, int section, int direction, int regionOffset) { }
}
