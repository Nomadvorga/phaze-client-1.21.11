package vorga.phazeclient.implement.menu.font;

import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3x2fc;
import vorga.phazeclient.base.util.render.GuiMatrix;

/** Size, position and measurements are screen-space GUI units. */
public interface GuiTextRenderer {
    record Metrics(float width, float height) { }
    Metrics measure(String text);
    void draw(MatrixStack matrices, String text, double x, double top, int color);
    default void draw(Matrix3x2fc matrices, String text, double x, double top, int color) {
        MatrixStack pose = new MatrixStack();
        pose.peek().getPositionMatrix().set(GuiMatrix.mat4(matrices));
        draw(pose, text, x, top, color);
    }
}
