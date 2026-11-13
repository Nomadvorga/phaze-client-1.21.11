package vorga.phazeclient.api.system.hud;

import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11C;
import vorga.phazeclient.testsupport.ClientGpu;

import java.lang.reflect.Field;

/** Real GPU pixel reference for cropped, fractional, premultiplied editor-cache composition. */
public final class HudCachedReplayClientTest implements FabricClientGameTest {
    private static int checks;

    @Override
    public void runTest(ClientGameTestContext context) {
        checks = 0;
        context.waitFor(client -> client.getOverlay() == null);
        context.runOnClient(client -> {
            int width = client.getWindow().getFramebufferWidth(), height = client.getWindow().getFramebufferHeight();
            HudBuffer source = new HudBuffer(GL11C.GL_LINEAR), neighbour = new HudBuffer();
            try (Render2DState original = Render2DState.capture()) {
                boolean dither = GL11C.glIsEnabled(GL11C.GL_DITHER);
                GL11C.glDisable(GL11C.GL_DITHER);
                SimpleFramebuffer target = ClientGpu.framebuffer("hud-replay-target", width, height, true);
                GpuTexture sentinel = RenderSystem.getDevice().createTexture("phaze-replay-sentinel",
                        GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST, TextureFormat.RGBA8, 4, 4, 1, 1);
                int sentinelWidth = sentinel.getWidth(0);
                try {
                    Framebuffer pixels;
                    try (HudCapture capture = source.openCapture()) {
                        check(capture != null, "isolated cache capture ready");
                        pixels = client.getFramebuffer();
                        check(pixels == HudBuffer.getActiveCaptureFramebuffer(), "entity/item render layers see the child target");
                        clearRect(pixels, 32, 48, 16, 12, 1, 0, 0, 1);
                        clearRect(pixels, 48, 48, 16, 12, 0, 0, 1, 1);
                        clearRect(pixels, 32, 60, 16, 12, 0, 1, 0, 1);
                        clearRect(pixels, 48, 60, 16, 12, 0.5F, 0.25F, 0, 0.5F);
                        // This unrelated image must not appear in the cropped replay.
                        clearRect(pixels, 220, 24, 12, 12, 1, 0, 1, 1);
                        capture.commit();
                    }
                    try (HudCapture capture = neighbour.openCapture()) {
                        check(capture != null, "ordinary neighbour capture ready");
                        check(policy(neighbour) == FilterMode.NEAREST, "ordinary HUD textures remain nearest");
                        capture.commit();
                    }
                    byte[] reference = read(pixels);
                    HudCachedTransform transform = HudCachedTransform.capture(32, 48, 32, 24, 2, 4, width, height);
                    check(policy(source) == FilterMode.LINEAR, "only the isolated editor texture is linear");
                    var savedColor = RenderSystem.outputColorTextureOverride;
                    var savedDepth = RenderSystem.outputDepthTextureOverride;
                    try {
                        for (float[] live : new float[][]{{143, 91, 2}, {153.25F, 93.25F, 3}, {210.125F, 141.375F, 1.5F}}) {
                            RenderSystem.disableScissorForRenderTypeDraws();
                            ClientGpu.clear(target, 0x00000000);
                            RenderSystem.outputColorTextureOverride = target.getColorAttachmentView();
                            RenderSystem.outputDepthTextureOverride = target.getDepthAttachmentView();
                            RenderSystem.setProjectionMatrix(ClientGpu.orthoYDown(17, 23), ProjectionType.ORTHOGRAPHIC);
                            RenderSystem.getModelViewStack().identity().translate(3, 5, 7);
                            var projection = RenderSystem.getProjectionMatrixBuffer();
                            Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix());
                            var sampler = RenderSystem.getSamplerCache().get(FilterMode.NEAREST);
                            check(source.drawCached(transform, live[0], live[1], live[2]), "transformed replay submitted");
                            check(projection == RenderSystem.getProjectionMatrixBuffer()
                                            && modelView.equals(new Matrix4f(RenderSystem.getModelViewMatrix())),
                                    "replay restores projection and model-view matrices");
                            check(sampler == RenderSystem.getSamplerCache().get(FilterMode.NEAREST),
                                    "replay restores the caller's sampler selection");
                            check(!sentinel.isClosed() && sentinel.getWidth(0) == sentinelWidth,
                                    "replay never changes a foreign texture's filtering");
                            compare(read(target), reference, width, height, transform, live);
                        }
                    } finally {
                        RenderSystem.outputColorTextureOverride = savedColor;
                        RenderSystem.outputDepthTextureOverride = savedDepth;
                    }
                    check(policy(source) == FilterMode.LINEAR, "replay does not mutate its own allocation policy");
                    check(GL11C.glGetError() == GL11C.GL_NO_ERROR, "cropped replay introduces no GL errors");
                } finally {
                    source.cleanup();
                    neighbour.cleanup();
                    target.delete();
                    sentinel.close();
                    if (dither) GL11C.glEnable(GL11C.GL_DITHER);
                }
            }
        });
        System.out.println("HUD transformed replay GL checks passed: " + checks);
    }

    private static void clearRect(Framebuffer framebuffer, int x, int y, int width, int height, float r, float g, float b, float a) {
        ClientGpu.fillRect(framebuffer, x, y, width, height, r, g, b, a);
    }

    private static FilterMode policy(HudBuffer buffer) {
        try {
            Field field = HudBuffer.class.getDeclaredField("textureFilter");
            field.setAccessible(true);
            int filter = field.getInt(buffer);
            return filter == 9729 || filter == 1 ? FilterMode.LINEAR : FilterMode.NEAREST;
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("editor filter policy is not readable", failure);
        }
    }

    private static byte[] read(Framebuffer framebuffer) {
        return ClientGpu.readRgba(framebuffer);
    }

    private static void compare(byte[] actual, byte[] source, int width, int height, HudCachedTransform captured, float[] live) {
        float ratio = live[2] / captured.scale();
        float left = captured.mapX(captured.left(), live[0], live[2]);
        float right = captured.mapX(captured.right(), live[0], live[2]);
        float top = captured.mapY(captured.top(), live[1], live[2]);
        float bottom = captured.mapY(captured.bottom(), live[1], live[2]);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            boolean inside = x + 0.5F >= left && x + 0.5F < right && y + 0.5F >= top && y + 0.5F < bottom;
            float sx = captured.x() + (x + 0.5F - live[0]) / ratio - 0.5F;
            float sy = captured.y() + (y + 0.5F - live[1]) / ratio - 0.5F;
            int ix = (int) Math.floor(sx), iy = (int) Math.floor(sy);
            float fx = sx - ix, fy = sy - iy;
            for (int channel = 0; channel < 4; channel++) {
                float expected = 0;
                if (inside) {
                    expected = sample(source, width, height, ix, iy, channel) * (1 - fx) * (1 - fy)
                            + sample(source, width, height, ix + 1, iy, channel) * fx * (1 - fy)
                            + sample(source, width, height, ix, iy + 1, channel) * (1 - fx) * fy
                            + sample(source, width, height, ix + 1, iy + 1, channel) * fx * fy;
                }
                int value = sample(actual, width, height, x, y, channel);
                checks++;
                if (Math.abs(value - expected) > 2) {
                    StringBuilder around = new StringBuilder();
                    for (int dx = -2; dx <= 2; dx++) {
                        around.append(" x").append(x + dx).append('=');
                        for (int c = 0; c < 4; c++) around.append(sample(actual, width, height, x + dx, y, c)).append(c < 3 ? "," : "");
                    }
                    throw new AssertionError("bilinear/crop/premultiplied mismatch at " + x + "," + y + "/" + channel
                            + ": " + value + " vs " + expected + " live=" + java.util.Arrays.toString(live)
                            + " crop=" + left + ".." + right + "," + top + ".." + bottom + around);
                }
            }
        }
    }

    private static int sample(byte[] pixels, int width, int height, int x, int y, int channel) {
        x = Math.clamp(x, 0, width - 1);
        y = Math.clamp(y, 0, height - 1);
        return pixels[((height - 1 - y) * width + x) * 4 + channel] & 255;
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
