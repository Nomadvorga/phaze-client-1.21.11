package vorga.phazeclient.api.system.hud;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

/** Source contour/color caches, submitted to the 1.21.11 vanilla GUI batch without extra passes. */
public final class HudBackgroundGeometryRenderer {
    private HudBackgroundGeometryRenderer() {}
    public static void solidRounded(DrawContext c, float x, float y, float w, float h, int color, float radius) {
        roundedGradient(c, x, y, x + w, y + h, color, color, "Left to Right", 0, false, radius);
    }
    public static void roundedGradient(DrawContext c, float l, float t, float r, float b, int first, int second,
                                       String direction, float offset, boolean animated, float radius) {
        if (r <= l || b <= t) return;
        if (radius <= 0.01F) { staticGradient(c, l, t, r, b, first, second, direction); return; }
        Matrix3x2fc m = c.getMatrices();
        float scale = windowScale();
        HudRoundedContour mesh = HudRoundedContour.get(r - l, b - t, radius,
                (float) Math.hypot(m.m00(), m.m01()) * scale, (float) Math.hypot(m.m10(), m.m11()) * scale);
        submit(c, new GeometryState(new Matrix3x2f(m), c.scissorStack.peekLast(), l, t, r, b,
                first, second, HudBackgroundGeometryGradients.direction(direction), offset, animated, mesh, false));
    }
    public static void staticGradient(DrawContext c, float l, float t, float r, float b, int first, int second, String selected) {
        int d = HudBackgroundGeometryGradients.direction(selected);
        quad(c, l, t, r, b, sample(first, second, d, 0, 0), sample(first, second, d, 0, 1),
                sample(first, second, d, 1, 1), sample(first, second, d, 1, 0));
    }
    private static int sample(int a, int b, int d, float x, float y) { return HudBackgroundGeometryGradients.sample(a, b, d, x, y, 0, false); }
    public static void movingDiagonal(DrawContext c, float l, float t, float r, float b, int first, int second, String direction, float offset) {
        if (r <= l || b <= t) return;
        submit(c, new GeometryState(new Matrix3x2f(c.getMatrices()), c.scissorStack.peekLast(), l, t, r, b,
                first, second, HudBackgroundGeometryGradients.direction(direction), offset, true, null, true));
    }
    public static void quad(DrawContext c, float l, float t, float r, float b, int tl, int bl, int br, int tr) {
        if (r <= l || b <= t) return;
        submit(c, new QuadState(new Matrix3x2f(c.getMatrices()), c.scissorStack.peekLast(), l, t, r, b, tl, bl, br, tr));
    }
    private static void submit(DrawContext c, SimpleGuiElementRenderState state) {
        vorga.phazeclient.api.system.shape.batched.GuiBatch.use(c);
        c.state.addSimpleElement(state);
    }
    private interface State extends SimpleGuiElementRenderState {
        Matrix3x2fc pose();
        float left(); float top(); float right(); float bottom();
        default float margin() { return 1; }
        @Override default RenderPipeline pipeline() { return RenderPipelines.GUI; }
        @Override default TextureSetup textureSetup() { return TextureSetup.empty(); }
        @Override default ScreenRect bounds() {
            int x = (int) Math.floor(left() - margin()), y = (int) Math.floor(top() - margin());
            ScreenRect rect = new ScreenRect(x, y, (int) Math.ceil(right() + margin()) - x,
                    (int) Math.ceil(bottom() + margin()) - y).transformEachVertex(pose());
            return scissorArea() == null ? rect : rect.intersection(scissorArea());
        }
    }
    private record QuadState(Matrix3x2fc pose, ScreenRect scissorArea, float left, float top, float right, float bottom,
                             int tl, int bl, int br, int tr) implements State {
        @Override public void setupVertices(VertexConsumer v) { quadVertices(v, pose, left, top, right, bottom, tl, bl, br, tr); }
    }
    private record GeometryState(Matrix3x2fc pose, ScreenRect scissorArea, float left, float top, float right, float bottom,
                                 int first, int second, int direction, float offset, boolean animated,
                                 HudRoundedContour mesh, boolean diagonal) implements State {
        @Override public float margin() { return mesh == null ? 1 : 1 / Math.min(mesh.scaleX, mesh.scaleY); }
        private int color(float x, float y) {
            return HudBackgroundGeometryGradients.sample(first, second, direction, x / mesh.width, y / mesh.height, offset, animated);
        }
        @Override public void setupVertices(VertexConsumer v) {
            if (diagonal) {
                for (int i = 0; i < 96; i++) {
                    float p = (i + 0.5F) / 96;
                    int a = HudBackgroundGeometryGradients.sample(first, second, direction, p, 0, offset, true);
                    int b = HudBackgroundGeometryGradients.sample(first, second, direction, p, 1, offset, true);
                    quadVertices(v, pose, left + (right - left) * (i / 96.0F), top,
                            left + (right - left) * ((i + 1) / 96.0F), bottom, a, b, b, a);
                }
                return;
            }
            boolean uniform = first == second || direction == HudBackgroundGeometryGradients.PULSE;
            int pulse = direction == HudBackgroundGeometryGradients.PULSE
                    ? HudBackgroundGeometryGradients.sample(first, second, direction, 0, 0, offset, animated) : first;
            int[] palette = uniform || animated ? null : mesh.colors(first, second, direction);
            for (int i = 0; i < mesh.points() - 1; i++) {
                float x0 = mesh.innerX(i), x1 = mesh.innerX(i + 1), y0 = mesh.innerY(i), y1 = mesh.innerY(i + 1);
                int tl = uniform ? pulse : palette != null ? palette[i * 2] : color(x0, y0);
                int bl = uniform ? pulse : palette != null ? palette[i * 2 + 1] : color(x0, mesh.height - y0);
                int tr = uniform ? pulse : palette != null ? palette[(i + 1) * 2] : color(x1, y1);
                int br = uniform ? pulse : palette != null ? palette[(i + 1) * 2 + 1] : color(x1, mesh.height - y1);
                if (i == mesh.capSegments && animated && !uniform
                        && (HudBackgroundGeometryGradients.horizontal(direction) || HudBackgroundGeometryGradients.diagonal(direction))) {
                    float from = x0;
                    int fromTop = tl, fromBottom = bl;
                    for (int s = 1; s <= 64; s++) {
                        float to = x0 + (x1 - x0) * (s / 64.0F);
                        int toTop = color(to, y0), toBottom = color(to, mesh.height - y0);
                        column(v, from, to, y0, y0, fromTop, fromBottom, toBottom, toTop);
                        edge(v, from, y0, to, y0, from, mesh.outerY(i), to, mesh.outerY(i), fromTop, toTop);
                        edge(v, to, mesh.height - y0, from, mesh.height - y0,
                                to, mesh.height - mesh.outerY(i), from, mesh.height - mesh.outerY(i), toBottom, fromBottom);
                        from = to; fromTop = toTop; fromBottom = toBottom;
                    }
                } else {
                    column(v, x0, x1, y0, y1, tl, bl, br, tr);
                    edge(v, x0, y0, x1, y1, mesh.outerX(i), mesh.outerY(i), mesh.outerX(i + 1), mesh.outerY(i + 1), tl, tr);
                    edge(v, x1, mesh.height - y1, x0, mesh.height - y0,
                            mesh.outerX(i + 1), mesh.height - mesh.outerY(i + 1), mesh.outerX(i), mesh.height - mesh.outerY(i), br, bl);
                }
                if (i == 0) edge(v, x0, mesh.height - y0, x0, y0, mesh.outerX(i), mesh.height - y0, mesh.outerX(i), y0, bl, tl);
                if (i == mesh.points() - 2) edge(v, x1, y1, x1, mesh.height - y1,
                        mesh.outerX(i + 1), y1, mesh.outerX(i + 1), mesh.height - y1, tr, br);
            }
        }
        private void column(VertexConsumer v, float x0, float x1, float y0, float y1, int tl, int bl, int br, int tr) {
            vertex(v, pose, left + x0, top + y0, tl); vertex(v, pose, left + x0, top + mesh.height - y0, bl);
            vertex(v, pose, left + x1, top + mesh.height - y1, br); vertex(v, pose, left + x1, top + y1, tr);
        }
        private void edge(VertexConsumer v, float ax, float ay, float bx, float by, float ox, float oy, float px, float py, int a, int b) {
            vertex(v, pose, left + ax, top + ay, a); vertex(v, pose, left + bx, top + by, b);
            vertex(v, pose, left + px, top + py, b & 0x00FFFFFF); vertex(v, pose, left + ox, top + oy, a & 0x00FFFFFF);
        }
    }
    private static void quadVertices(VertexConsumer v, Matrix3x2fc m, float l, float t, float r, float b, int tl, int bl, int br, int tr) {
        vertex(v, m, l, t, tl); vertex(v, m, l, b, bl); vertex(v, m, r, b, br); vertex(v, m, r, t, tr);
    }
    private static void vertex(VertexConsumer v, Matrix3x2fc m, float x, float y, int color) { v.vertex(m, x, y).color(color); }
    private static float windowScale() {
        MinecraftClient c = MinecraftClient.getInstance();
        return c == null || c.getWindow() == null ? 1 : Math.max(1, c.getWindow().getScaleFactor());
    }
}
