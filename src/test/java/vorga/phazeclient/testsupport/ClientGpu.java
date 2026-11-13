package vorga.phazeclient.testsupport;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.ProjectionMatrix2;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;
import vorga.phazeclient.api.system.draw.GpuDraw;
import vorga.phazeclient.mixins.GameRendererGuiAccessor;

import java.lang.reflect.Field;

/**
 * 1.21.11 stand-in for the framebuffer, projection, and screenshot calls the
 * client fixtures used before GpuTexture. Pixel buffers stay bottom-up RGBA,
 * matching glReadPixels, so existing channel comparisons keep their indices.
 */
public final class ClientGpu {
    private static final int COMPOSITE_UBO_SIZE = 80;
    private static ProjectionMatrix2 yUp;
    private static ProjectionMatrix2 yDown;
    private static GpuBuffer compositeUbo;
    private static RenderPipeline compositePipeline;

    private ClientGpu() {}

    public static SimpleFramebuffer framebuffer(String name, int width, int height, boolean depth) {
        return new SimpleFramebuffer(name, Math.max(1, width), Math.max(1, height), depth);
    }

    public static DrawContext context(MinecraftClient client) {
        return new DrawContext(client, new GuiRenderState(), 0, 0);
    }

    public static DrawContext gameContext(MinecraftClient client) {
        GuiRenderState state = ((GameRendererGuiAccessor) client.gameRenderer).phaze$getGuiState();
        return new DrawContext(client, state, 0, 0);
    }

    public static void clear(Framebuffer framebuffer, int argb) {
        RenderSystem.getDevice().createCommandEncoder().clearColorTexture(framebuffer.getColorAttachment(), argb);
    }

    /** Orthographic projection with y = 0 at the bottom, matching the old ortho2D(0, w, 0, h). */
    public static com.mojang.blaze3d.buffers.GpuBufferSlice orthoYUp(float width, float height) {
        if (yUp == null) yUp = new ProjectionMatrix2("phaze-test-y-up", -1000.0F, 1000.0F, false);
        return yUp.set(width, height);
    }

    /** Orthographic projection with y = 0 at the top, matching GUI ortho2D(0, w, h, 0). */
    public static com.mojang.blaze3d.buffers.GpuBufferSlice orthoYDown(float width, float height) {
        if (yDown == null) yDown = new ProjectionMatrix2("phaze-test-y-down", -1000.0F, 1000.0F, true);
        return yDown.set(width, height);
    }

    public static void useOrtho(com.mojang.blaze3d.buffers.GpuBufferSlice slice, boolean yDown) {
        RenderSystem.setProjectionMatrix(slice, ProjectionType.ORTHOGRAPHIC);
    }

    /**
     * Reads the color attachment. Blocks until the fenced map runs.
     * Layout is bottom-up RGBA, the same order glReadPixels produced.
     */
    public static byte[] readRgba(Framebuffer framebuffer) {
        int width = framebuffer.textureWidth;
        int height = framebuffer.textureHeight;
        int bytes = width * height * 4;
        GpuBuffer buffer = RenderSystem.getDevice().createBuffer(
                () -> "phaze test readback",
                GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ,
                bytes);
        byte[][] pixels = new byte[1][];
        Throwable[] failure = new Throwable[1];
        try {
            RenderSystem.getDevice().createCommandEncoder().copyTextureToBuffer(
                    framebuffer.getColorAttachment(), buffer, 0L, () -> {
                        try (GpuBuffer.MappedView view = RenderSystem.getDevice().createCommandEncoder()
                                .mapBuffer(buffer, true, false)) {
                            java.nio.ByteBuffer data = view.data();
                            byte[] copy = new byte[bytes];
                            data.get(copy);
                            pixels[0] = copy;
                        } catch (Throwable error) {
                            failure[0] = error;
                        }
                    }, 0);
            long deadline = System.nanoTime() + 2_000_000_000L;
            while (pixels[0] == null && failure[0] == null && System.nanoTime() < deadline) {
                RenderSystem.executePendingTasks();
                Thread.onSpinWait();
            }
        } finally {
            buffer.close();
        }
        if (failure[0] != null) throw new AssertionError("GPU readback failed for " + framebuffer.textureWidth + "x" + framebuffer.textureHeight, failure[0]);
        if (pixels[0] == null) {
            throw new AssertionError("GPU readback fence did not complete; copyTextureToBuffer stayed queued");
        }
        return pixels[0];
    }

    public static NativeImage screenshot(Framebuffer framebuffer) {
        NativeImage[] image = new NativeImage[1];
        ScreenshotRecorder.takeScreenshot(framebuffer, shot -> image[0] = shot);
        long deadline = System.nanoTime() + 2_000_000_000L;
        while (image[0] == null && System.nanoTime() < deadline) {
            RenderSystem.executePendingTasks();
            Thread.onSpinWait();
        }
        if (image[0] == null) {
            throw new AssertionError("ScreenshotRecorder queued the read behind a GPU fence and it did not complete");
        }
        return image[0];
    }

    public static void uploadRgba(Framebuffer framebuffer, byte[] rgbaBottomUp) {
        int width = framebuffer.textureWidth;
        int height = framebuffer.textureHeight;
        if (rgbaBottomUp.length != width * height * 4) {
            throw new AssertionError("upload size " + rgbaBottomUp.length + " != " + width + "x" + height);
        }
        try (NativeImage image = new NativeImage(width, height, false)) {
            for (int glY = 0; glY < height; glY++) {
                int nativeY = height - 1 - glY;
                for (int x = 0; x < width; x++) {
                    int index = (glY * width + x) * 4;
                    int red = rgbaBottomUp[index] & 255;
                    int green = rgbaBottomUp[index + 1] & 255;
                    int blue = rgbaBottomUp[index + 2] & 255;
                    int alpha = rgbaBottomUp[index + 3] & 255;
                    image.setColorArgb(x, nativeY, (alpha << 24) | (red << 16) | (green << 8) | blue);
                }
            }
            RenderSystem.getDevice().createCommandEncoder().writeToTexture(framebuffer.getColorAttachment(), image);
        }
    }

    /** Paints a top-down rectangle into a bottom-up color attachment. */
    public static void fillRect(Framebuffer framebuffer, int x, int y, int width, int height, float red, float green, float blue, float alpha) {
        byte[] pixels = readRgba(framebuffer);
        int targetWidth = framebuffer.textureWidth;
        int targetHeight = framebuffer.textureHeight;
        int r = Math.round(red * 255), g = Math.round(green * 255), b = Math.round(blue * 255), a = Math.round(alpha * 255);
        for (int row = 0; row < height; row++) {
            int glY = targetHeight - y - height + row;
            if (glY < 0 || glY >= targetHeight) continue;
            for (int col = 0; col < width; col++) {
                int px = x + col;
                if (px < 0 || px >= targetWidth) continue;
                int index = (glY * targetWidth + px) * 4;
                pixels[index] = (byte) r;
                pixels[index + 1] = (byte) g;
                pixels[index + 2] = (byte) b;
                pixels[index + 3] = (byte) a;
            }
        }
        uploadRgba(framebuffer, pixels);
    }

    /**
     * Draws the blur composite pipeline with the same uniform values the 1.21.4 mask shader took.
     * The caller selects the projection and {@code outputColorTextureOverride}.
     */
    public static void drawBlurComposite(
            BuiltBuffer built,
            GpuTextureView blurred,
            GpuTextureView previous,
            float sizeX, float sizeY,
            Vector4f radius,
            float smoothness,
            float blurRadius,
            int blurMode,
            Vector4f tint,
            float frameMix,
            boolean rectMask,
            float maskPadX,
            float maskPadY,
            float screenWidth,
            float screenHeight
    ) {
        compositeUbo = ensureCompositeUbo();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            java.nio.ByteBuffer data = Std140Builder.onStack(stack, COMPOSITE_UBO_SIZE)
                    .putVec4(radius.x, radius.y, radius.z, radius.w)
                    .putVec4(tint.x, tint.y, tint.z, tint.w)
                    .putVec2(sizeX, sizeY)
                    .putFloat(smoothness)
                    .putFloat(blurRadius)
                    .putFloat(frameMix)
                    .putInt(rectMask ? 1 : 0)
                    .putInt(blurMode)
                    .putVec2(screenWidth, screenHeight)
                    .putVec2(maskPadX, maskPadY)
                    .get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(compositeUbo.slice(), data);
        }
        GpuTextureView previousView = previous != null ? previous : blurred;
        GpuDraw.drawWithUniforms(compositePipeline(), built, "Sampler0", blurred, "Sampler1", previousView,
                FilterMode.LINEAR, new Matrix4f(), new Vector4f(1, 1, 1, 1), "BlurCompositeConfig", compositeUbo);
    }

    private static GpuBuffer ensureCompositeUbo() {
        if (compositeUbo != null && !compositeUbo.isClosed()) return compositeUbo;
        compositeUbo = RenderSystem.getDevice().createBuffer(
                () -> "phaze test blur composite",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                COMPOSITE_UBO_SIZE);
        return compositeUbo;
    }

    private static RenderPipeline compositePipeline() {
        if (compositePipeline != null) return compositePipeline;
        try {
            Field field = Class.forName("vorga.phazeclient.api.system.shape.implement.Blur").getDeclaredField("COMPOSITE_PIPELINE");
            field.setAccessible(true);
            compositePipeline = (RenderPipeline) field.get(null);
            return compositePipeline;
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("blur composite pipeline is not available", failure);
        }
    }
}
