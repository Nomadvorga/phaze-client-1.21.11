package vorga.phazeclient.api.system.hud;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.ProjectionMatrix2;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import vorga.phazeclient.api.system.hud.BatchedHudBuffer;
import vorga.phazeclient.api.system.shape.batched.BatchedRectangle;
import vorga.phazeclient.testsupport.ClientGpu;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL14C;
import org.lwjgl.opengl.GL20C;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

import java.util.Arrays;
import java.util.Objects;

/** GL-context regression fixture. Root suite registers this client-gametest entrypoint. */
public final class RenderSafetyClientTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        context.waitFor(client -> client.getOverlay() == null);
        context.runOnClient(client -> {
            HudRenderLifecycle.cleanup();
            try (Render2DState original = Render2DState.capture()) {
                SimpleFramebuffer draw = ClientGpu.framebuffer("safety-draw", 48, 48, true);
                SimpleFramebuffer read = ClientGpu.framebuffer("safety-read", 32, 32, true);
                HudBuffer nested = new HudBuffer();
                HudBuffer grandchild = new HudBuffer();
                ProjectionMatrix2 seededProjection = new ProjectionMatrix2("phaze/test safety projection", -1000.0F, 1000.0F, true);
                GpuBufferSlice savedProjection = RenderSystem.getProjectionMatrixBuffer();
                ProjectionType savedProjectionType = RenderSystem.getProjectionType();
                try {
                    // 1.21.11 has no global shader colour; the global draw state is projection and model-view.
                    RenderSystem.setProjectionMatrix(seededProjection.set(48.0F, 48.0F), ProjectionType.ORTHOGRAPHIC);
                    RenderSystem.getModelViewStack().set(new Matrix4f().translation(3.0F, 4.0F, 0.0F).scale(0.5F, 0.75F, 1.0F));
                    RenderSystem.outputColorTextureOverride = draw.getColorAttachmentView();
                    RenderSystem.outputDepthTextureOverride = read.getDepthAttachmentView();
                    // Seed through GlStateManager: raw GL calls would desync the cache that render passes trust.
                    GlStateManager._viewport(3, 4, 25, 26);
                    RenderSystem.enableScissorForRenderTypeDraws(5, 6, 9, 10);
                    GlStateManager._scissorBox(5, 6, 9, 10);
                    GlStateManager._enableScissorTest();
                    GlStateManager._colorMask(false, true, false, true);
                    GlStateManager._depthMask(false);
                    GlStateManager._disableDepthTest();
                    GlStateManager._disableCull();
                    GlStateManager._enableBlend();
                    GlStateManager._blendFuncSeparate(GL11C.GL_ONE, GL11C.GL_ZERO, GL11C.GL_ZERO, GL11C.GL_ONE);
                    BatchedRectangle.restoreRenderTargetFbHeight(48);
                    Snapshot expected = Snapshot.read();

                    RuntimeException injected = new RuntimeException("injected capture render failure");
                    try (HudCapture capture = BatchedHudBuffer.INSTANCE.openCapture()) {
                        check(capture != null, "capture ready");
                        check(BatchedHudBuffer.INSTANCE.getActiveCaptureFramebuffer() != null, "batch redirect active");
                        check(client.getFramebuffer() == HudBuffer.getActiveCaptureFramebuffer(), "client getter redirects to the batch");
                        check(BatchedHudBuffer.INSTANCE.getRealMainFramebuffer() != client.getFramebuffer(), "clean blur source is separate from capture pixels");
                        check(!RenderSystem.getScissorStateForRenderTypeDraws().isEnabled(), "full clear ignores old scissor");
                        throw injected;
                    } catch (RuntimeException failure) {
                        check(failure == injected, "original failure preserved");
                    }
                    expected.assertCurrent("abort");
                    check(!BatchedHudBuffer.INSTANCE.hasContent(), "partial capture is never cached");
                    check(HudBuffer.activeCaptureTarget == -1, "abort restores capture sentinel");
                    check(BatchedRectangle.getRenderTargetFbHeightOverride() == 48, "abort restores parent height");

                    try (HudCapture batch = BatchedHudBuffer.INSTANCE.openCapture()) {
                        check(batch != null, "second capture ready after failure");
                        int parent = HudBuffer.activeCaptureTarget;
                        var parentFramebuffer = client.getFramebuffer();
                        float parentHeight = BatchedRectangle.getRenderTargetFbHeightOverride();
                        Snapshot parentState = Snapshot.read();
                        try (HudCapture child = nested.openCapture()) {
                            check(child != null, "nested capture ready");
                            // 1.21.11 keeps one "capture active" sentinel; ownership is the framebuffer itself.
                            check(HudBuffer.getActiveCaptureFramebuffer() != parentFramebuffer, "child owns its target");
                            check(BatchedHudBuffer.INSTANCE.getActiveCaptureFramebuffer() == null, "unrelated per-HUD target cannot redirect to global batch FBO");
                            var childFramebuffer = client.getFramebuffer();
                            check(childFramebuffer == HudBuffer.getActiveCaptureFramebuffer() && childFramebuffer != parentFramebuffer,
                                    "client getter follows the child instead of rebinding main or batch");
                            try (HudCapture inner = grandchild.openCapture()) {
                                check(inner != null && client.getFramebuffer() != childFramebuffer, "third level redirects to the innermost target");
                                throw injected;
                            } catch (RuntimeException failure) {
                                check(failure == injected, "nested capture preserves the original exception");
                            }
                            check(client.getFramebuffer() == childFramebuffer, "grandchild abort restores the child framebuffer reference");
                            check(!grandchild.hasContent(), "failed grandchild is not published");
                            child.commit();
                        }
                        parentState.assertCurrent("nested close");
                        check(HudBuffer.activeCaptureTarget == parent, "nested close restores parent redirect");
                        check(client.getFramebuffer() == parentFramebuffer, "nested close restores the parent client getter");
                        check(BatchedRectangle.getRenderTargetFbHeightOverride() == parentHeight, "nested close restores parent height");
                        batch.commit();
                    }
                    expected.assertCurrent("commit");
                    check(BatchedHudBuffer.INSTANCE.hasContent(), "completed capture published");
                    BatchedHudBuffer.INSTANCE.blit();
                    expected.assertCurrent("blit");

                    // An injected error after beginScope must not poison future nesting or height state.
                    BatchedRectangle.beginScope();
                    try {
                        throw injected;
                    } catch (RuntimeException failure) {
                        check(failure == injected, "batch failure propagated");
                    } finally {
                        BatchedRectangle.endScope();
                    }
                    check(!BatchedRectangle.isBatching(), "batch scope unwound");
                    expected.assertCurrent("batch scope");
                    BufferBuilder builder = Render2DBuffers.begin(VertexFormats.POSITION);
                    builder.vertex(0, 0, 0); builder.vertex(0, 1, 0); builder.vertex(1, 1, 0); builder.vertex(1, 0, 0);
                    try (BuiltBuffer built = builder.end()) { check(built.getDrawParameters().vertexCount() == 4, "tessellator usable after capture failure"); }

                    HudRenderLifecycle.cleanup();
                    // Shutdown/reload can interrupt an unfinished child. Disposal must unwind it first.
                    try (HudCapture batch = BatchedHudBuffer.INSTANCE.openCapture();
                         HudCapture child = nested.openCapture();
                         HudCapture inner = grandchild.openCapture()) {
                        check(batch != null && child != null && inner != null, "lifecycle interrupt has three live scopes");
                        HudRenderLifecycle.cleanup();
                        check(HudBuffer.getActiveCaptureFramebuffer() == null && BatchedHudBuffer.INSTANCE.getRealMainFramebuffer() == null,
                                "interrupted lifecycle clears every framebuffer reference");
                        expected.assertCurrent("interrupted lifecycle");
                    }
                    HudRenderLifecycle.cleanup();
                    check(HudBuffer.activeCaptureTarget == -1, "repeated lifecycle clears redirect");
                    check(BatchedRectangle.getRenderTargetFbHeightOverride() == -1, "lifecycle clears height override");
                    check(!BatchedHudBuffer.INSTANCE.hasContent() && !nested.hasContent(), "lifecycle invalidates all HUD caches");
                } finally {
                    nested.cleanup();
                    grandchild.cleanup();
                    BatchedHudBuffer.INSTANCE.cleanup();
                    BatchedRectangle.restoreRenderTargetFbHeight(-1);
                    if (savedProjection != null) RenderSystem.setProjectionMatrix(savedProjection, savedProjectionType);
                    seededProjection.close();
                    draw.delete();
                    read.delete();
                }
            }
        });
        System.out.println("HUD/2D GL render safety checks passed");
    }

    record Snapshot(GpuTextureView draw, GpuTextureView readTarget, int[] viewport, int[] scissorBox, boolean scissor,
                            boolean depth, boolean depthMask, boolean cull, boolean blend,
                            int srcRgb, int dstRgb, int srcAlpha, int dstAlpha, int program,
                            byte[] colorMask, GpuBufferSlice projection, ProjectionType projectionType,
                            Matrix4f modelView, GpuBufferSlice fog, GpuBufferSlice lights) {
        static Snapshot read() {
            int[] viewport = new int[4], box = new int[4];
            GL11C.glGetIntegerv(GL11C.GL_VIEWPORT, viewport);
            GL11C.glGetIntegerv(GL11C.GL_SCISSOR_BOX, box);
            byte[] mask = new byte[4];
            try (MemoryStack stack = MemoryStack.stackPush()) {
                var buffer = stack.malloc(4);
                GL11C.glGetBooleanv(GL11C.GL_COLOR_WRITEMASK, buffer);
                buffer.get(mask);
            }
            var passScissor = RenderSystem.getScissorStateForRenderTypeDraws();
            return new Snapshot(RenderSystem.outputColorTextureOverride, RenderSystem.outputDepthTextureOverride,
                    viewport, box, passScissor.isEnabled(), GL11C.glIsEnabled(GL11C.GL_DEPTH_TEST),
                    GL11C.glGetBoolean(GL11C.GL_DEPTH_WRITEMASK), GL11C.glIsEnabled(GL11C.GL_CULL_FACE),
                    GL11C.glIsEnabled(GL11C.GL_BLEND), GL11C.glGetInteger(GL14C.GL_BLEND_SRC_RGB),
                    GL11C.glGetInteger(GL14C.GL_BLEND_DST_RGB), GL11C.glGetInteger(GL14C.GL_BLEND_SRC_ALPHA),
                    GL11C.glGetInteger(GL14C.GL_BLEND_DST_ALPHA), GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM),
                    mask, RenderSystem.getProjectionMatrixBuffer(), RenderSystem.getProjectionType(),
                    new Matrix4f(RenderSystem.getModelViewMatrix()), RenderSystem.getShaderFog(), RenderSystem.getShaderLights());
        }

        /**
         * Raster state (enables, masks, blend factors, program, viewport) belongs to 1.21.11 render passes,
         * which set it through GlStateManager and, like vanilla, never roll it back. What must hold is that
         * the cache those passes trust still matches the hardware, plus the global draw state below.
         */
        void assertCurrent(String stage) {
            Snapshot actual = read();
            check(draw == actual.draw && readTarget == actual.readTarget, stage + " restores independent read/draw targets");
            var passScissor = RenderSystem.getScissorStateForRenderTypeDraws();
            check(scissor == actual.scissor
                            && (!scissor || passScissor.getX() == 5 && passScissor.getY() == 6 && passScissor.getWidth() == 9 && passScissor.getHeight() == 10),
                    stage + " restores the render-pass scissor");
            assertCacheCoherent(stage);
            check(Objects.equals(projection, actual.projection) && projectionType == actual.projectionType
                            && modelView.equals(actual.modelView),
                    stage + " restores projection and model-view");
            check(Objects.equals(fog, actual.fog) && Objects.equals(lights, actual.lights),
                    stage + " restores fog and light uniforms");
        }
    }

    private static void assertCacheCoherent(String stage) {
        Object blend = cached("BLEND"), depth = cached("DEPTH"), cull = cached("CULL"), mask = cached("COLOR_MASK");
        check(capability(blend) == GL11C.glIsEnabled(GL11C.GL_BLEND)
                        && capability(depth) == GL11C.glIsEnabled(GL11C.GL_DEPTH_TEST)
                        && capability(cull) == GL11C.glIsEnabled(GL11C.GL_CULL_FACE),
                stage + " keeps GlStateManager's enable cache equal to GL");
        check((boolean) member(depth, "mask") == GL11C.glGetBoolean(GL11C.GL_DEPTH_WRITEMASK),
                stage + " keeps the cached depth mask equal to GL");
        check((int) member(blend, "srcFactorRgb") == GL11C.glGetInteger(GL14C.GL_BLEND_SRC_RGB)
                        && (int) member(blend, "dstFactorRgb") == GL11C.glGetInteger(GL14C.GL_BLEND_DST_RGB)
                        && (int) member(blend, "srcFactorAlpha") == GL11C.glGetInteger(GL14C.GL_BLEND_SRC_ALPHA)
                        && (int) member(blend, "dstFactorAlpha") == GL11C.glGetInteger(GL14C.GL_BLEND_DST_ALPHA),
                stage + " keeps cached blend factors equal to GL");
        byte[] hardware = new byte[4];
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var buffer = stack.malloc(4);
            GL11C.glGetBooleanv(GL11C.GL_COLOR_WRITEMASK, buffer);
            buffer.get(hardware);
        }
        check((boolean) member(mask, "red") == (hardware[0] != 0) && (boolean) member(mask, "green") == (hardware[1] != 0)
                        && (boolean) member(mask, "blue") == (hardware[2] != 0) && (boolean) member(mask, "alpha") == (hardware[3] != 0),
                stage + " keeps the cached colour mask equal to GL");
    }

    private static Object cached(String name) {
        return member(null, GlStateManager.class, name);
    }

    private static boolean capability(Object state) {
        return (boolean) member(member(state, "capState"), "state");
    }

    private static Object member(Object owner, String name) {
        return member(owner, owner.getClass(), name);
    }

    private static Object member(Object owner, Class<?> type, String name) {
        try {
            var field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(owner);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cannot inspect GlStateManager." + name, failure);
        }
    }

    private static void check(boolean success, String message) {
        if (!success) throw new AssertionError(message);
    }
}
