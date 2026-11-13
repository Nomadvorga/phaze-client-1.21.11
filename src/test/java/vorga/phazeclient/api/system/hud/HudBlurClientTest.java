package vorga.phazeclient.api.system.hud;

import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormats;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL33C;
import vorga.phazeclient.api.system.draw.GuiProjection;
import vorga.phazeclient.api.system.shape.implement.Blur;
import vorga.phazeclient.testsupport.ClientGpu;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Real shader/FBO checks on deterministic images, without a world, network actions or user configs. */
public final class HudBlurClientTest implements FabricClientGameTest {
    private int checks;
    private int largestDifference;

    @Override
    public void runTest(ClientGameTestContext context) {
        context.waitFor(client -> client.getOverlay() == null);
        try {
            context.runOnClient(client -> {
                try (Render2DState state = Render2DState.capture()) {
                    for (int[] size : new int[][]{{320, 180}, {321, 181}, {127, 73}}) testSize(size[0], size[1]);
                    roundedEdges(client);
                    if (Boolean.getBoolean("phaze.hudBlur.benchmark")) benchmarkSubmissions();
                }
            });
        } catch (Exception failure) {
            throw new AssertionError("HUD blur GL regression", failure);
        }
        System.out.println("HUD blur GL checks passed: " + checks + "; max channel difference: " + largestDifference);
    }

    private void testSize(int width, int height) throws Exception {
        Blur blur = new Blur();
        SimpleFramebuffer input = framebuffer(width, height);
        SimpleFramebuffer half = framebuffer(width / 2, height / 2);
        SimpleFramebuffer halfB = framebuffer(width / 2, height / 2);
        SimpleFramebuffer quarterA = framebuffer(width / 4, height / 4);
        SimpleFramebuffer quarterB = framebuffer(width / 4, height / 4);
        SimpleFramebuffer reference = framebuffer(width / 2, height / 2);
        SimpleFramebuffer result = framebuffer(width / 2, height / 2);
        SimpleFramebuffer fine = framebuffer(width, height);
        SimpleFramebuffer maskReference = framebuffer(width, height);
        SimpleFramebuffer maskResult = framebuffer(width, height);
        set(blur, "input", input); set(blur, "hudHalfInput", half); set(blur, "halfB", halfB);
        set(blur, "quarterA", quarterA); set(blur, "quarterB", quarterB);
        Class<?> rectType = Class.forName(Blur.class.getName() + "$BlurRegion");
        Constructor<?> rect = rectType.getDeclaredConstructor(int.class, int.class, int.class, int.class);
        rect.setAccessible(true);
        Method passes = method("runHudKawasePasses", Framebuffer.class, float.class, List.class);
        Method finePass = method("runDualKawasePass", ShaderProgram.class, Framebuffer.class, Framebuffer.class,
                float.class, boolean.class, rectType);
        Object whole = rect.newInstance(0, 0, width, height);
        List<HudBlurPlanner.Rect> regions = List.of(new HudBlurPlanner.Rect(0, 0, 11, 9),
                new HudBlurPlanner.Rect(width - 13, height - 11, 13, 11),
                new HudBlurPlanner.Rect(width / 2, height / 2, 7, 5),
                new HudBlurPlanner.Rect(width / 2 + 3, height / 2 + 1, 12, 7));
        List<Object> requested = new ArrayList<>();
        for (HudBlurPlanner.Rect region : regions) requested.add(rect.newInstance(region.x(), region.y(), region.width(), region.height()));
        try {
            for (int revision = 1; revision <= 2; revision++) {
                pattern(input, revision);
                set(blur, "hudInputRevision", (long) revision);
                method("ensureHudHalfInput").invoke(blur);
                for (float radius : new float[]{8.01F, 12.0F, 16.0F, 16.01F, 24.0F}) {
                    check(pipeline("HUD_KAWASE_PIPELINE") != null, "Kawase shader loaded");
                    passes.invoke(blur, reference, radius, List.of(whole));
                    byte[] expected = read(reference);
                    poison(quarterA); poison(quarterB); poison(result);
                    passes.invoke(blur, result, radius, requested);
                    byte[] actual = read(result);
                    for (HudBlurPlanner.Rect region : regions) {
                        compare(expected, actual, result.textureWidth,
                                HudBlurPlanner.scale(region, width, height, result.textureWidth, result.textureHeight), 1,
                                "grouped blur equals full-image kernel at radius " + radius);
                    }
                    // Screen UVs must work for both even and odd sizes with direct half-resolution sampling.
                    mask(result, maskResult, width, height, 0, 0.0F);
                    mask(reference, maskReference, width, height, 0, 0.0F);
                    byte[] fullExpected = read(maskReference), fullActual = read(maskResult);
                    for (HudBlurPlanner.Rect region : regions) compare(fullExpected, fullActual, width, region, 1, "half-mask UV alignment");
                }
                for (float radius : new float[]{0.001F, 0.5F, 2.0F, 4.0F, 8.0F}) {
                    finePass.invoke(blur, null, input, fine, radius / 8.0F, true, whole);
                    mask(fine, maskReference, width, height, 0, 0.0F);
                    mask(input, maskResult, width, height, 4, radius / 8.0F);
                    compare(read(maskReference), read(maskResult), width, new HudBlurPlanner.Rect(0, 0, width, height), 1,
                            "bounded fine filter equals its five-tap reference");
                }
                // Both algorithm boundaries must converge to the same image from either side.
                passes.invoke(blur, reference, 8.001F, List.of(whole));
                mask(input, maskReference, width, height, 4, 1.0F);
                mask(reference, input, maskResult, width, height, 5, 8.001F);
                compare(read(maskReference), read(maskResult), width, new HudBlurPlanner.Rect(0, 0, width, height), 1,
                        "fine/strong transition starts continuously");
                passes.invoke(blur, reference, 11.999F, List.of(whole));
                mask(reference, maskReference, width, height, 0, 0.0F);
                mask(reference, input, maskResult, width, height, 5, 11.999F);
                compare(read(maskReference), read(maskResult), width, new HudBlurPlanner.Rect(0, 0, width, height), 1,
                        "fine/strong transition ends continuously");
                passes.invoke(blur, reference, 16.0F, List.of(whole));
                passes.invoke(blur, result, 16.001F, List.of(whole));
                compare(read(reference), read(result), half.textureWidth,
                        new HudBlurPlanner.Rect(0, 0, half.textureWidth, half.textureHeight), 1,
                        "additional quarter pass starts continuously");
            }
            HudBlurPlanner planner = (HudBlurPlanner) field("hudBlurPlanner").get(blur);
            planner.beginFrame();
            for (HudBlurPlanner.Rect region : regions) planner.remember(24.0F, region);
            planner.endFrame();
            set(blur, "hudGroupsPrepared", false);
            method("prepareHudBlurGroups").invoke(blur);
            Object[] slots = (Object[]) field("hudBlurSlots").get(blur);
            int allocated = 0;
            Framebuffer groupTarget = null;
            for (Object slot : slots) if (slot != null) {
                allocated++;
                Field target = slot.getClass().getDeclaredField("framebuffer"); target.setAccessible(true);
                Framebuffer framebuffer = (Framebuffer) target.get(slot);
                groupTarget = framebuffer;
                check(framebuffer.textureWidth == half.textureWidth && framebuffer.textureHeight == half.textureHeight,
                        "HUD cache slots are half resolution");
            }
            check(allocated == 1, "same-radius overlapping HUD group shares one slot");
            passes.invoke(blur, reference, 24.0F, List.of(whole));
            byte[] expectedGroup = read(reference), actualGroup = read(groupTarget);
            Method prepared = method("applyOptimizedHudKawaseBlur", net.minecraft.client.MinecraftClient.class, float.class, rectType);
            for (int i = 0; i < regions.size(); i++) {
                check(prepared.invoke(blur, net.minecraft.client.MinecraftClient.getInstance(), 24.0F, requested.get(i)) == groupTarget,
                        "live masks hit the preprepared group rather than repeating filters");
                compare(expectedGroup, actualGroup, half.textureWidth,
                        HudBlurPlanner.scale(regions.get(i), width, height, half.textureWidth, half.textureHeight), 1,
                        "history preparation equals current world image");
            }
            HudBlurPlanner.Rect moved = new HudBlurPlanner.Rect(width / 2 - 5, height / 2 + 6, 15, 8);
            Object movedRegion = rect.newInstance(moved.x(), moved.y(), moved.width(), moved.height());
            Framebuffer movedTarget = (Framebuffer) prepared.invoke(blur, net.minecraft.client.MinecraftClient.getInstance(), 24.0F, movedRegion);
            compare(expectedGroup, read(movedTarget), half.textureWidth,
                    HudBlurPlanner.scale(moved, width, height, half.textureWidth, half.textureHeight), 1,
                    "moving HUD gets a freshly computed fallback, not historical pixels");
            check(GL11C.glGetError() == GL11C.GL_NO_ERROR, "blur stages leave no GL error");
        } finally {
            // Blur owns the injected input/half/quarter buffers and any lazily allocated slots.
            blur.cleanup();
            for (Framebuffer framebuffer : new Framebuffer[]{reference, result, fine, maskReference, maskResult}) framebuffer.delete();
        }
    }

    private void roundedEdges(net.minecraft.client.MinecraftClient client) throws Exception {
        SimpleFramebuffer target = framebuffer(256, 192), source = framebuffer(256, 192);
        String[] directions = {"Left to Right", "Right to Left", "Top to Bottom", "Bottom to Top", "Diagonal Down", "Diagonal Up", "Pulse"};
        try {
            ClientGpu.clear(source, 0xFFFFFFFF);
            for (float scale : new float[]{1, 1.5F, 3, 4}) {
                float x = 14.25F, y = 12.375F, w = 40 * scale, h = 28 * scale, r = 8 * scale;
                byte[] geometry = null;
                for (boolean animated : new boolean[]{false, true}) for (String direction : directions) {
                    clearRoundedTarget(target);
                    float guiScale = (float) client.getWindow().getScaleFactor();
                    DrawContext draw = ClientGpu.gameContext(client);
                    try (HudCapture capture = HudCapture.begin(target);
                         GuiProjection.TargetSize ignored = GuiProjection.targetSize(256 / guiScale, 192 / guiScale)) {
                        draw.getMatrices().pushMatrix();
                        try {
                            draw.getMatrices().scale(scale / guiScale, scale / guiScale);
                            HudBackgroundGeometryRenderer.roundedGradient(draw, x / scale, y / scale, (x + w) / scale, (y + h) / scale,
                                    0xFFFFFFFF, 0xFF2060A0, direction, 0.37F, animated, 8);
                        } finally {
                            draw.getMatrices().popMatrix();
                        }
                        capture.commit();
                    }
                    draw.state.clear();
                    byte[] pixels = read(target);
                    int covered = 0, minX = 256, maxX = -1, minY = 192, maxY = -1;
                    for (int py = 0; py < 192; py++) for (int px = 0; px < 256; px++) {
                        if ((pixels[(py * 256 + px) * 4 + 3] & 255) == 0) continue;
                        covered++;
                        minX = Math.min(minX, px); maxX = Math.max(maxX, px); minY = Math.min(minY, py); maxY = Math.max(maxY, py);
                    }
                    String extent = " covered=" + covered + " x=" + minX + ".." + maxX + " glY=" + minY + ".." + maxY;
                    for (int py = 0; py < 192; py++) for (int px = 0; px < 256; px++) {
                        float qx = Math.abs(px + 0.5F - x - w / 2) - w / 2 + r;
                        float qy = Math.abs((192 - py - 0.5F) - y - h / 2) - h / 2 + r;
                        float d = Math.min(Math.max(qx, qy), 0) + (float) Math.hypot(Math.max(qx, 0), Math.max(qy, 0)) - r;
                        float expected = Math.max(0, Math.min(1, 0.5F - d));
                        int alpha = pixels[(py * 256 + px) * 4 + 3] & 255;
                        check(Math.abs(alpha / 255.0F - expected) <= 0.17F,
                                "rounded contour coverage " + scale + " " + direction + " at " + px + "," + py + " alpha=" + alpha + " expected=" + expected + extent);
                        if (geometry != null) check(alpha == (geometry[(py * 256 + px) * 4 + 3] & 255), "gradient/animation preserves contour alpha");
                    }
                    if (geometry == null) geometry = pixels;
                }
                byte[] previous = null;
                clearRoundedTarget(target);
                float guiScale = (float) client.getWindow().getScaleFactor();
                DrawContext solid = ClientGpu.gameContext(client);
                try (HudCapture capture = HudCapture.begin(target);
                     GuiProjection.TargetSize ignored = GuiProjection.targetSize(256 / guiScale, 192 / guiScale)) {
                    solid.getMatrices().pushMatrix();
                    try {
                        solid.getMatrices().scale(scale / guiScale, scale / guiScale);
                        HudBackgroundGeometryRenderer.solidRounded(solid, x / scale, y / scale, 40, 28, -1, 8);
                    } finally {
                        solid.getMatrices().popMatrix();
                    }
                    capture.commit();
                }
                solid.state.clear();
                byte[] solidPixels = read(target);
                for (int i = 3; i < solidPixels.length; i += 4) check(solidPixels[i] == geometry[i], "solid and gradient have identical rounding");
                for (int mode : new int[]{0, 4, 5}) for (float strength : new float[]{0.001F, 1, 24}) {
                    clearRoundedTarget(target);
                    contour(source, target, x, y, w, h, r, mode, strength);
                    byte[] pixels = read(target);
                    for (int i = 3; i < pixels.length; i += 4) {
                        check(Math.abs((pixels[i] & 255) - (geometry[i] & 255)) <= 48, "blur/gradient contour alignment");
                        if (previous != null) check(pixels[i] == previous[i], "blur strength/mode cannot change rounding");
                    }
                    previous = pixels;
                }
            }
            System.out.println("Rounded HUD GL coverage passed: solid/7 gradients/animation, scales 1/1.5/3/4, 3 blur modes and strengths");
        } finally { target.delete(); source.delete(); }
    }

    private static void clearRoundedTarget(Framebuffer target) {
        ClientGpu.clear(target, 0x00000000);
        RenderSystem.disableScissorForRenderTypeDraws();
        RenderSystem.getModelViewStack().identity();
        RenderSystem.setProjectionMatrix(ClientGpu.orthoYDown(256, 192), ProjectionType.ORTHOGRAPHIC);
    }

    private static void mask(Framebuffer source, Framebuffer target, int width, int height, int mode, float radius) {
        mask(source, source, target, width, height, mode, radius);
    }

    private static void mask(Framebuffer source, Framebuffer original, Framebuffer target, int width, int height, int mode, float radius) {
        GpuTextureView color = RenderSystem.outputColorTextureOverride;
        GpuTextureView depth = RenderSystem.outputDepthTextureOverride;
        var savedProjection = RenderSystem.getProjectionMatrixBuffer();
        var savedType = RenderSystem.getProjectionType();
        try {
            RenderSystem.outputColorTextureOverride = target.getColorAttachmentView();
            RenderSystem.outputDepthTextureOverride = null;
            RenderSystem.disableScissorForRenderTypeDraws();
            RenderSystem.setProjectionMatrix(ClientGpu.orthoYUp(width, height), ProjectionType.ORTHOGRAPHIC);
            RenderSystem.getModelViewStack().pushMatrix();
            RenderSystem.getModelViewStack().identity();
            BufferBuilder builder = Render2DBuffers.begin(VertexFormats.POSITION_COLOR);
            builder.vertex(0, 0, 0).color(-1); builder.vertex(width, 0, 0).color(-1);
            builder.vertex(width, height, 0).color(-1); builder.vertex(0, height, 0).color(-1);
            try (BuiltBuffer built = builder.end()) {
                ClientGpu.drawBlurComposite(built, source.getColorAttachmentView(), original.getColorAttachmentView(),
                        width, height, new Vector4f(), 0.0F, radius, mode, new Vector4f(), 1.0F, true, 0.0F, 0.0F, width, height);
            }
        } catch (AssertionError missing) {
            throw new AssertionError("mask shader missing", missing);
        } finally {
            RenderSystem.getModelViewStack().popMatrix();
            RenderSystem.outputColorTextureOverride = color;
            RenderSystem.outputDepthTextureOverride = depth;
            if (savedProjection != null) RenderSystem.setProjectionMatrix(savedProjection, savedType);
        }
    }

    /** Same uniform block the rounded contour used to push into the mask shader. */
    private static void contour(Framebuffer source, Framebuffer target, float x, float y, float w, float h, float r, int mode, float strength) {
        GpuTextureView color = RenderSystem.outputColorTextureOverride;
        GpuTextureView depth = RenderSystem.outputDepthTextureOverride;
        try {
            RenderSystem.outputColorTextureOverride = target.getColorAttachmentView();
            RenderSystem.outputDepthTextureOverride = null;
            RenderSystem.disableScissorForRenderTypeDraws();
            RenderSystem.setProjectionMatrix(ClientGpu.orthoYDown(256, 192), ProjectionType.ORTHOGRAPHIC);
            RenderSystem.getModelViewStack().pushMatrix();
            RenderSystem.getModelViewStack().identity();
            BufferBuilder builder = Render2DBuffers.begin(VertexFormats.POSITION_COLOR);
            builder.vertex(x - 0.5F, y - 0.5F, 0).color(-1); builder.vertex(x - 0.5F, y + h + 0.5F, 0).color(-1);
            builder.vertex(x + w + 0.5F, y + h + 0.5F, 0).color(-1); builder.vertex(x + w + 0.5F, y - 0.5F, 0).color(-1);
            try (BuiltBuffer built = builder.end()) {
                ClientGpu.drawBlurComposite(built, source.getColorAttachmentView(), source.getColorAttachmentView(),
                        w, h, new Vector4f(r, r, r, r), 0.001F, strength, mode, new Vector4f(), 1.0F, false, 0.5F, 0.5F, 256, 192);
            }
        } finally {
            RenderSystem.getModelViewStack().popMatrix();
            RenderSystem.outputColorTextureOverride = color;
            RenderSystem.outputDepthTextureOverride = depth;
        }
    }

    /** Synthetic 17-widget prepass A/B, not game FPS. Query readback is outside CPU timing. */
    private void benchmarkSubmissions() throws Exception {
        Blur blur = new Blur();
        SimpleFramebuffer input = framebuffer(1920, 1080), half = framebuffer(960, 540);
        SimpleFramebuffer quarterA = framebuffer(480, 270), quarterB = framebuffer(480, 270);
        SimpleFramebuffer halfB = framebuffer(960, 540), oldOutput = framebuffer(1920, 1080), newOutput = framebuffer(960, 540);
        set(blur, "input", input); set(blur, "hudHalfInput", half);
        set(blur, "quarterA", quarterA); set(blur, "quarterB", quarterB); set(blur, "halfB", halfB);
        Class<?> rectType = Class.forName(Blur.class.getName() + "$BlurRegion");
        Constructor<?> rect = rectType.getDeclaredConstructor(int.class, int.class, int.class, int.class); rect.setAccessible(true);
        List<Object> regions = new ArrayList<>();
        for (int i = 0; i < 17; i++) regions.add(rect.newInstance(60 + i % 5 * 350, 60 + i / 5 * 230, 250, 150));
        Method pass = method("runDualKawasePass", ShaderProgram.class, Framebuffer.class, Framebuffer.class, float.class, boolean.class, rectType);
        Method blit = method("blitColorRegion", Framebuffer.class, Framebuffer.class, rectType, FilterMode.class);
        Method grouped = method("runHudKawasePasses", Framebuffer.class, float.class, List.class);
        int samples = 24, repeats = 8;
        int[] queries = new int[samples * 2];
        double[][] cpu = new double[2][samples], gpu = new double[2][samples];
        try {
            pattern(input, 1); set(blur, "hudInputRevision", 1L); method("ensureHudHalfInput").invoke(blur);
            for (int warm = 0; warm < 40; warm++) {
                submitBaseline(blur, pass, blit, half, quarterA, quarterB, halfB, oldOutput, regions);
                try (Render2DState state = Render2DState.capture()) {
                    grouped.invoke(blur, newOutput, 24.0F, regions);
                }
            }
            for (int sample = 0; sample < samples; sample++) {
                for (int order = 0; order < 2; order++) {
                    int variant = (sample + order) % 2;
                    int query = GL15C.glGenQueries(); queries[sample * 2 + variant] = query;
                    GL15C.glBeginQuery(GL33C.GL_TIME_ELAPSED, query);
                    long start = System.nanoTime();
                    for (int repeat = 0; repeat < repeats; repeat++) {
                        if (variant == 0) submitBaseline(blur, pass, blit, half, quarterA, quarterB, halfB, oldOutput, regions);
                        else try (Render2DState state = Render2DState.capture()) {
                            grouped.invoke(blur, newOutput, 24.0F, regions);
                        }
                    }
                    cpu[variant][sample] = (System.nanoTime() - start) / (double) repeats / 1_000_000;
                    GL15C.glEndQuery(GL33C.GL_TIME_ELAPSED);
                }
            }
            for (int sample = 0; sample < samples; sample++) for (int variant = 0; variant < 2; variant++) {
                gpu[variant][sample] = GL33C.glGetQueryObjectui64(queries[sample * 2 + variant], GL15C.GL_QUERY_RESULT)
                        / (double) repeats / 1_000_000;
            }
            System.out.printf(Locale.ROOT, "HUD blur synthetic prepass A/B (1920x1080, 17 regions, radius 24, %s): "
                            + "CPU median %.4f -> %.4f ms; GL elapsed median %.4f -> %.4f ms; filter draws 68 -> 4%n",
                    GL11C.glGetString(GL11C.GL_RENDERER), median(cpu[0]), median(cpu[1]), median(gpu[0]), median(gpu[1]));
        } finally {
            for (int query : queries) if (query != 0) GL15C.glDeleteQueries(query);
            blur.cleanup(); oldOutput.delete(); newOutput.delete();
        }
    }

    private static void submitBaseline(Blur blur, Method pass, Method blit, Framebuffer half, Framebuffer quarterA,
            Framebuffer quarterB, Framebuffer halfB, Framebuffer output, List<Object> regions) throws Exception {
        for (Object region : regions) try (Render2DState state = Render2DState.capture()) {
            pass.invoke(blur, null, half, quarterA, 24.0F * 0.14F, true, region);
            pass.invoke(blur, null, quarterA, quarterB, 24.0F * 0.18F, true, region);
            pass.invoke(blur, null, quarterB, quarterA, 24.0F * (0.18F + 0.025F + 0.015F), true, region);
            pass.invoke(blur, null, quarterA, halfB, 24.0F * 0.10F, false, region);
            blit.invoke(blur, halfB, output, region, FilterMode.LINEAR);
        }
    }

    private static double median(double[] values) {
        double[] sorted = values.clone(); Arrays.sort(sorted); return (sorted[sorted.length / 2 - 1] + sorted[sorted.length / 2]) / 2;
    }

    private static SimpleFramebuffer framebuffer(int width, int height) {
        return ClientGpu.framebuffer("phaze-hud-blur-test", width, height, false);
    }

    private static void pattern(Framebuffer target, int revision) {
        byte[] pixels = new byte[target.textureWidth * target.textureHeight * 4];
        for (int y = 0; y < target.textureHeight; y++) for (int x = 0; x < target.textureWidth; x++) {
            int index = (y * target.textureWidth + x) * 4;
            pixels[index] = (byte) ((x * 13 + y * 7 + revision * 47) & 255);
            pixels[index + 1] = (byte) (((x / 3 + y / 5 + revision) & 1) * 255);
            pixels[index + 2] = (byte) ((y * 23 + revision * 71) & 255);
            pixels[index + 3] = (byte) 255;
        }
        ClientGpu.uploadRgba(target, pixels);
    }

    private static void poison(Framebuffer target) {
        RenderSystem.disableScissorForRenderTypeDraws();
        ClientGpu.clear(target, 0xFFFF00FF);
    }

    private static byte[] read(Framebuffer target) {
        return ClientGpu.readRgba(target);
    }

    private static Object pipeline(String name) throws Exception {
        Field field = Blur.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    private void compare(byte[] expected, byte[] actual, int width, HudBlurPlanner.Rect rect, int tolerance, String label) {
        for (int y = rect.y(); y < rect.y() + rect.height(); y++) for (int x = rect.x(); x < rect.x() + rect.width(); x++) {
            for (int channel = 0; channel < 4; channel++) {
                int index = (y * width + x) * 4 + channel;
                int difference = Math.abs((expected[index] & 255) - (actual[index] & 255));
                largestDifference = Math.max(largestDifference, difference);
                check(difference <= tolerance, label + " at " + x + "," + y + ": " + difference);
            }
        }
    }

    private void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
    private static Field field(String name) throws Exception { Field field = Blur.class.getDeclaredField(name); field.setAccessible(true); return field; }
    private static void set(Blur blur, String name, Object value) throws Exception { field(name).set(blur, value); }
    private static Method method(String name, Class<?>... parameters) throws Exception {
        Method method = Blur.class.getDeclaredMethod(name, parameters); method.setAccessible(true); return method;
    }
}
