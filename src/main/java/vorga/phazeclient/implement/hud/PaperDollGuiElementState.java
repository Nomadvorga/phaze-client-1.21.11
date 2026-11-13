package vorga.phazeclient.implement.hud;

import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.special.SpecialGuiElementRenderState;
import net.minecraft.client.render.entity.state.EntityRenderState;
import org.joml.Matrix3x2f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.List;

/** One immutable GUI submission for the entire vehicle/rider group, sharing depth. */
public record PaperDollGuiElementState(List<Part> parts, Quaternionf rotation, Quaternionf cameraAngle,
                                       Vector3f translation, int x1, int y1, int x2, int y2,
                                       float scale, Matrix3x2f pose, ScreenRect scissorArea,
                                       ScreenRect bounds) implements SpecialGuiElementRenderState {
    public record Part(EntityRenderState state, double x, double y, double z) { }
    public PaperDollGuiElementState(List<Part> parts, Quaternionf rotation, Quaternionf cameraAngle,
                                    Vector3f translation, int x1, int y1, int x2, int y2,
                                    float scale, Matrix3x2f pose, ScreenRect scissor) {
        this(List.copyOf(parts), new Quaternionf(rotation), new Quaternionf(cameraAngle), new Vector3f(translation),
                x1, y1, x2, y2, scale, new Matrix3x2f(pose), scissor, bounds(x1, y1, x2, y2, pose, scissor));
    }
    private static ScreenRect bounds(int x1, int y1, int x2, int y2, Matrix3x2f pose, ScreenRect scissor) {
        ScreenRect bounds = new ScreenRect(x1, y1, x2 - x1, y2 - y1).transform(pose);
        return scissor == null ? bounds : scissor.intersection(bounds);
    }
}
