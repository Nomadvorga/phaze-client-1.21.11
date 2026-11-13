package vorga.phazeclient.api.system.font.msdf;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gl.ScissorState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.text.Text;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;
import vorga.phazeclient.api.system.font.Fonts;
import vorga.phazeclient.api.system.shape.batched.GuiBatch;
import vorga.phazeclient.base.util.render.GuiMatrix;
import vorga.phazeclient.implement.menu.font.GuiFonts;
import vorga.phazeclient.implement.menu.font.GuiTextRenderer;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/** Opt-in render-thread regression checks. Root owns Fabric client-test entrypoint registration. */
public final class MsdfTextClientTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        context.waitFor(client -> client.getOverlay() == null);
        Screen saved = context.computeOnClient(client -> client.currentScreen);
        Fixture fixture = context.computeOnClient(client -> new Fixture());
        try {
            context.runOnClient(client -> client.setScreen(fixture));
            context.waitFor(client -> fixture.finished);
            context.runOnClient(client -> {
                if (fixture.failure != null) throw new AssertionError("MSDF text client regression", fixture.failure);
            });
        } finally {
            context.runOnClient(client -> client.setScreen(saved));
        }
    }

    private static final class Fixture extends Screen {
        private boolean finished;
        private Throwable failure;

        private Fixture() { super(Text.literal("MSDF text state and recovery fixture")); }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            if (finished) return;
            GuiBatch.flush();
            try (TextRenderState outer = new TextRenderState(true)) {
                MsdfFont font = MsdfFonts.medium();
                Matrix4f matrix = GuiMatrix.mat4(context.getMatrices());
                check(!MsdfRenderer.isUsingFallback(), "fixture requires the real shader initially");
                for (boolean blend : new boolean[]{false, true}) {
                    for (boolean cull : new boolean[]{false, true}) {
                        seed(blend, cull);
                        State before = State.capture();
                        MsdfRenderer.renderText(font, "AV Цвет HUD", 7, 0xAD12ABEF, matrix, 20, 20, 0);
                        check(!MsdfRenderer.isUsingFallback(), "normal rendering uses MSDF");
                        State after = State.capture();
                        check(before.equals(after), "successful draw restores incoming GL/cache state: before="
                                + before.gl() + " after=" + after.gl());
                    }
                }
                earlyFailure(font, matrix);
                partialGlyphFailure(font, matrix);
                GuiTextRenderer explicit = GuiFonts.atSize(7, Fonts.Type.INTER_DEFAULT);
                var legacy = GuiFonts.getSize(14, Fonts.Type.INTER_DEFAULT);
                check(explicit == legacy, "explicit and legacy sizes share their cached adapter");
                near(explicit.measure("Цвет HUD").width(), legacy.getStringWidth("Цвет HUD"), "width compatibility");
                near(explicit.measure("A\nB").height() * 2, legacy.getStringHeight("A\nB"), "doubled legacy height is isolated");
                State before = State.capture();
                MsdfRenderer.renderText(font, "ignored", Float.NaN, -1, matrix, 0, 0, 0);
                check(before.equals(State.capture()), "invalid size is a safe no-op");
                System.out.println("MSDF text state, partial-buffer cleanup and reload recovery PASSED");
            } catch (Throwable error) {
                failure = error;
            } finally {
                MsdfFonts.onResourcesReloaded();
                finished = true;
            }
        }
    }

    /**
     * 1.21.11 feeds the MSDF effects to the shader as the MsdfEffects uniform block. An undersized block
     * buffer makes that upload fail the way a missing uniform failed before, on the deferred and the
     * immediate path alike.
     */
    private static void earlyFailure(MsdfFont font, Matrix4f matrix) throws Exception {
        Field effects = field(MsdfRenderer.class, "effectsBuffer");
        GpuBuffer undersized = RenderSystem.getDevice().createBuffer(() -> "phaze/test undersized MSDF effects",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, 16);
        GpuBuffer previous = (GpuBuffer) effects.get(null);
        effects.set(null, undersized);
        try {
            seed(false, false);
            Global global = Global.capture();
            MsdfRenderer.renderText(font, "AV Цвет", 14, 0xCCFFFFFF, matrix, 20, 40, 0);
            GuiBatch.flush();
            check(MsdfRenderer.isUsingFallback(), "rejected uniform block selects fallback");
            Throwable diagnostic = MsdfRenderer.getLastFailure();
            check(diagnostic.getMessage().contains("MsdfEffects"), "failure diagnostic names the missing uniform block");
            // The fallback is drawn by vanilla's GuiRenderer, which owns raster state like any GUI pass.
            check(global.equals(Global.capture()), "failure and fallback restore projection, model-view, targets and scissor");
            MsdfRenderer.renderText(font, "AV Цвет", 7, -1, matrix, 20, 60, 0);
            GuiBatch.flush();
            check(MsdfRenderer.getLastFailure() == diagnostic, "subsequent frames retain one diagnostic without retry/log spam");
            check(global.equals(Global.capture()), "latched fallback restores global render state too");
        } finally {
            if (previous != null && !previous.isClosed() && effects.get(null) == undersized) effects.set(null, previous);
            MsdfFonts.onResourcesReloaded();
            if (!undersized.isClosed()) undersized.close();
        }
        check(!MsdfRenderer.isUsingFallback() && MsdfRenderer.getLastFailure() == null, "reload re-arms failed renderer");
        MsdfRenderer.renderText(font, "Recovered", 7, -1, matrix, 20, 80, 0);
        GuiBatch.flush();
        check(!MsdfRenderer.isUsingFallback(), "MSDF succeeds after recovery");
    }

    @SuppressWarnings("unchecked")
    private static void partialGlyphFailure(MsdfFont font, Matrix4f matrix) throws Exception {
        Field glyphField = field(MsdfFont.class, "glyphs");
        Int2ObjectMap<MsdfGlyph> original = (Int2ObjectMap<MsdfGlyph>) glyphField.get(font);
        Int2ObjectOpenHashMap<MsdfGlyph> injected = new Int2ObjectOpenHashMap<>(original) {
            private int lookups;
            @Override
            public MsdfGlyph get(int codepoint) {
                if (++lookups == 2) throw new IllegalStateException("Expected fixture failure after first glyph");
                return super.get(codepoint);
            }
        };
        glyphField.set(font, injected);
        // Latin and Cyrillic glyphs are served from a direct array; empty it so lookups reach the map.
        MsdfGlyph[] direct = (MsdfGlyph[]) field(MsdfFont.class, "directGlyphs").get(font);
        MsdfGlyph[] directOriginal = direct.clone();
        java.util.Arrays.fill(direct, null);
        try {
            seed(true, true);
            Global global = Global.capture();
            MsdfRenderer.renderText(font, "AB", 9, -1, matrix, 20, 100, 0);
            check(MsdfRenderer.isUsingFallback(), "partial glyph failure selects fallback");
            check(global.equals(Global.capture()), "partial glyph failure/fallback restores global render state");
            BufferAllocator allocator = (BufferAllocator) field(MsdfRenderer.class, "glyphAllocator").get(null);
            check(field(BufferAllocator.class, "offset").getLong(allocator) == 0, "unfinished glyph allocation was fully returned");
            check(field(BufferAllocator.class, "refCount").getInt(allocator) == 0, "no BuiltBuffer references leaked");
        } finally {
            glyphField.set(font, original);
            System.arraycopy(directOriginal, 0, direct, 0, direct.length);
            MsdfFonts.onResourcesReloaded();
        }
        check(field(MsdfRenderer.class, "glyphAllocator").get(null) == null, "reload releases owned allocator");
        check(!MsdfRenderer.isUsingFallback(), "reload re-arms atlas filtering through per-draw FilterMode.LINEAR");
        MsdfRenderer.renderText(font, "AB", 9, -1, matrix, 20, 120, 0);
        check(!MsdfRenderer.isUsingFallback(), "subsequent MSDF draw is not poisoned by the unfinished builder");
    }

    private static void seed(boolean blend, boolean cull) {
        if (blend) GL11.glEnable(GL11.GL_BLEND); else GL11.glDisable(GL11.GL_BLEND);
        if (cull) GL11.glEnable(GL11.GL_CULL_FACE); else GL11.glDisable(GL11.GL_CULL_FACE);
        GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_DST_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glDepthFunc(GL11.GL_GEQUAL);
        GL11.glColorMask(true, false, true, false);
    }

    private record State(List<Integer> gl) {
        private static State capture() {
            List<Integer> values = new ArrayList<>();
            for (int flag : new int[]{GL11.GL_BLEND, GL11.GL_CULL_FACE, GL11.GL_DEPTH_TEST, GL11.GL_SCISSOR_TEST}) {
                values.add(GL11.glIsEnabled(flag) ? 1 : 0);
            }
            // Program, VAO, buffer and texture bindings belong to the 1.21.11 render pass, which sets them
            // through GlStateManager's cache and, like vanilla GUI draws, does not roll them back.
            for (int parameter : new int[]{GL11.GL_DEPTH_WRITEMASK, GL11.GL_DEPTH_FUNC,
                    GL14.GL_BLEND_SRC_RGB, GL14.GL_BLEND_DST_RGB, GL14.GL_BLEND_SRC_ALPHA, GL14.GL_BLEND_DST_ALPHA,
                    GL13.GL_ACTIVE_TEXTURE}) {
                values.add(GL11.glGetInteger(parameter));
            }
            try (MemoryStack stack = MemoryStack.stackPush()) {
                var mask = stack.malloc(4);
                GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, mask);
                for (int i = 0; i < 4; i++) values.add((int) mask.get(i));
            }
            return new State(values);
        }
    }

    /** The global state 1.21.11 keeps instead of a shader colour; per-draw tints live in DynamicTransforms. */
    private record Global(Matrix4f modelView, GpuBufferSlice projection, ProjectionType projectionType,
                          GpuTextureView colorTarget, GpuTextureView depthTarget, List<Integer> scissor) {
        private static Global capture() {
            ScissorState box = RenderSystem.getScissorStateForRenderTypeDraws();
            return new Global(new Matrix4f(RenderSystem.getModelViewMatrix()), RenderSystem.getProjectionMatrixBuffer(),
                    RenderSystem.getProjectionType(), RenderSystem.outputColorTextureOverride,
                    RenderSystem.outputDepthTextureOverride,
                    List.of(box.isEnabled() ? 1 : 0, box.getX(), box.getY(), box.getWidth(), box.getHeight()));
        }
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void near(float actual, float expected, String message) {
        check(Math.abs(actual - expected) < 0.001F, message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
