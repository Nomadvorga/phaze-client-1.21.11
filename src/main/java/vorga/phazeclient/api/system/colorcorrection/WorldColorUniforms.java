package vorga.phazeclient.api.system.colorcorrection;

import com.mojang.blaze3d.buffers.GpuBuffer;
import net.fabricmc.loader.api.FabricLoader;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.system.MemoryStack;
import vorga.phazeclient.base.util.shader.WorldColorShaderPatcher;
import static vorga.phazeclient.api.system.colorcorrection.WorldColorCorrectionController.*;

/** Two owned GPU buffers: world parameters and an immutable identity for GUI/preview passes. */
public final class WorldColorUniforms {
    public static final int SIZE = 208; // Six (color, params) vec4 pairs + world-active vec4.
    private static final Target[] TARGETS = {Target.BLOCKS, Target.FLUIDS, Target.SKY, Target.CLOUDS, Target.PLAYERS, Target.ENTITIES};
    /** Sodium writes the cloud colour into vertices, so the shader must not apply it again. */
    private static final boolean SODIUM_BAKES_CLOUDS = FabricLoader.getInstance().isModLoaded("sodium");
    private static GpuBuffer world, identity;
    private static long frame, uploaded = Long.MIN_VALUE;
    private WorldColorUniforms() {}

    /** Uploads outside any render pass. {@link #bind} only attaches the buffer a pass already opened. */
    public static void beginFrame() {
        RenderSystem.assertOnRenderThread();
        frame++;
        if (identity == null) identity = upload("phaze/world color identity", false);
        if (!worldActive()) return;
        if (world == null) world = allocate("phaze/world color");
        write(world, true);
        uploaded = frame;
    }

    public static void bind(RenderPass pass) {
        RenderSystem.assertOnRenderThread();
        GpuBuffer buffer = worldActive() && uploaded == frame ? world : identity;
        if (buffer != null) pass.setUniform(WorldColorShaderPatcher.UNIFORM, buffer.slice());
    }

    private static GpuBuffer upload(String label, boolean active) {
        GpuBuffer created = allocate(label);
        try {
            write(created, active);
            return created;
        } catch (RuntimeException | Error failure) {
            try { created.close(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    private static boolean worldActive() {
        for (Target target : TARGETS) if (isTargetActive(target)) return true;
        return false;
    }

    private static GpuBuffer allocate(String label) {
        return RenderSystem.getDevice().createBuffer(() -> label, GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, SIZE);
    }

    private static void write(GpuBuffer buffer, boolean active) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Std140Builder data = Std140Builder.onStack(stack, SIZE);
            for (Target target : TARGETS) {
                boolean enabled = active && isTargetActive(target);
                data.putVec4(enabled ? getRed(target) : 1, enabled ? getGreen(target) : 1,
                        enabled ? getBlue(target) : 1, enabled && needsBlend(target) ? getAlpha(target) : 1);
                float cpuBaked = target == Target.CLOUDS && SODIUM_BAKES_CLOUDS ? 1 : 0;
                data.putVec4(enabled ? getSaturation(target) : 1, enabled ? getBrightness(target) : 0, enabled ? 1 : 0, cpuBaked);
            }
            data.putVec4(active ? 1 : 0, 0, 0, 0);
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), data.get());
        }
    }

    public static void close() {
        RenderSystem.assertOnRenderThread();
        GpuBuffer[] owned = {world, identity};
        world = identity = null;
        uploaded = Long.MIN_VALUE;
        Throwable failure = null;
        for (GpuBuffer buffer : owned) {
            if (buffer == null) continue;
            try { buffer.close(); }
            catch (RuntimeException | Error problem) {
                if (failure == null) failure = problem; else failure.addSuppressed(problem);
            }
        }
        if (failure instanceof Error error) throw error;
        if (failure instanceof RuntimeException exception) throw exception;
    }
}
