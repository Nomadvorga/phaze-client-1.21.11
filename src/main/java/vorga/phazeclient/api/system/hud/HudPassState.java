package vorga.phazeclient.api.system.hud;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.gl.ScissorState;
import org.joml.Matrix4f;

/** 1.21.11 pass snapshot: no raw GL calls or guessed main framebuffer binding. */
public final class HudPassState implements AutoCloseable {
    private final GpuTextureView color = RenderSystem.outputColorTextureOverride;
    private final GpuTextureView depth = RenderSystem.outputDepthTextureOverride;
    private final GpuBufferSlice projection = RenderSystem.getProjectionMatrixBuffer();
    private final ProjectionType projectionType = RenderSystem.getProjectionType();
    private final GpuBufferSlice fog = RenderSystem.getShaderFog();
    private final GpuBufferSlice lights = RenderSystem.getShaderLights();
    private final Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix());
    private final float framebufferHeight = vorga.phazeclient.api.system.shape.batched.BatchedRectangle.getRenderTargetFbHeightOverride();
    private final boolean scissor;
    private final int x, y, w, h;
    private boolean closed;

    private HudPassState() {
        RenderSystem.assertOnRenderThread();
        ScissorState state = RenderSystem.getScissorStateForRenderTypeDraws();
        scissor = state.isEnabled();
        x = state.getX(); y = state.getY(); w = state.getWidth(); h = state.getHeight();
    }

    public static HudPassState capture() { return new HudPassState(); }

    @Override public void close() {
        if (closed) return;
        closed = true;
        vorga.phazeclient.api.system.shape.batched.BatchedRectangle.setRenderTargetFbHeight(framebufferHeight);
        RenderSystem.outputColorTextureOverride = color;
        RenderSystem.outputDepthTextureOverride = depth;
        if (projection != null) RenderSystem.setProjectionMatrix(projection, projectionType);
        RenderSystem.getModelViewStack().set(modelView);
        RenderSystem.setShaderFog(fog);
        RenderSystem.setShaderLights(lights);
        if (scissor) RenderSystem.enableScissorForRenderTypeDraws(x, y, w, h);
        else RenderSystem.disableScissorForRenderTypeDraws();
    }
}
