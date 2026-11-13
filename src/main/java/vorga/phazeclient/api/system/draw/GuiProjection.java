package vorga.phazeclient.api.system.draw;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.ProjectionMatrix2;
import net.minecraft.client.util.Window;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Matrix4fc;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class GuiProjection {

    private static final List<ProjectionMatrix2> projections = new ArrayList<>();
    private static final Deque<ProjectionState> scopes = new ArrayDeque<>();
    private static float targetWidth, targetHeight;
    private static TargetSize activeTargetSize;

    public static float width(float fallback) { return targetWidth > 0 ? targetWidth : fallback; }
    public static float height(float fallback) { return targetHeight > 0 ? targetHeight : fallback; }
    public static boolean hasTargetSize() { return targetWidth > 0 && targetHeight > 0; }

    public static TargetSize targetSize(float width, float height) { return new TargetSize(width, height); }

    public static final class TargetSize implements AutoCloseable {
        private final float previousWidth = targetWidth, previousHeight = targetHeight;
        private final TargetSize previous = activeTargetSize;
        private boolean closed;
        private TargetSize(float width, float height) {
            if (!Float.isFinite(width) || !Float.isFinite(height) || width <= 0 || height <= 0)
                throw new IllegalArgumentException("Invalid GUI render-target dimensions");
            targetWidth = width;
            targetHeight = height;
            activeTargetSize = this;
        }
        /** GUI elements queued under this size are deferred; draw them before the projection reverts. */
        @Override public void close() {
            if (closed) return;
            if (activeTargetSize != this) throw new IllegalStateException("GUI target sizes must close in reverse order");
            vorga.phazeclient.api.system.shape.batched.GuiBatch.flush();
            closed = true;
            targetWidth = previousWidth;
            targetHeight = previousHeight;
            activeTargetSize = previous;
        }
    }

    private GuiProjection() {
    }

    public static void begin() {
        RenderSystem.assertOnRenderThread();
        MinecraftClient client = MinecraftClient.getInstance();
        Window window = client == null ? null : client.getWindow();
        if (window == null) {
            throw new IllegalStateException("GUI projection requires a client window");
        }
        int level = scopes.size();
        if (projections.size() == level)
            projections.add(new ProjectionMatrix2("phaze_gui_" + level, 1000.0F, 11000.0F, true));
        ProjectionMatrix2 projection = projections.get(level);
        ProjectionState previous = new ProjectionState(RenderSystem.getProjectionMatrixBuffer(), RenderSystem.getProjectionType());
        int scale = window.getScaleFactor();
        if (scale <= 0) {
            scale = 1;
        }

        GpuBufferSlice slice = projection.set(
                width((float) window.getFramebufferWidth() / scale),
                height((float) window.getFramebufferHeight() / scale));
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        try {
            RenderSystem.setProjectionMatrix(slice, ProjectionType.ORTHOGRAPHIC);
            modelView.identity().translate(0.0F, 0.0F, -11000.0F);
            scopes.push(previous);
        } catch (RuntimeException | Error failure) {
            try { modelView.popMatrix(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            try { previous.restore(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    public static Matrix4f guiModelView(Matrix4f dest) {
        return dest.translation(0.0F, 0.0F, -11000.0F);
    }

    public static Matrix4f guiModelView(Matrix4fc pose, Matrix4f dest) {
        return dest.translation(0.0F, 0.0F, -11000.0F).mul(pose);
    }

    public static void end() {
        RenderSystem.assertOnRenderThread();
        if (scopes.isEmpty()) return;
        ProjectionState previous = scopes.pop();
        try { RenderSystem.getModelViewStack().popMatrix(); }
        finally { previous.restore(); }
    }

    public static void close() {
        RenderSystem.assertOnRenderThread();
        Throwable failure = null;
        while (!scopes.isEmpty()) {
            try { end(); }
            catch (RuntimeException | Error problem) {
                if (failure == null) failure = problem; else failure.addSuppressed(problem);
            }
        }
        while (activeTargetSize != null) activeTargetSize.close();
        targetWidth = targetHeight = 0;
        for (ProjectionMatrix2 projection : projections) {
            try { projection.close(); }
            catch (RuntimeException | Error problem) {
                if (failure == null) failure = problem; else failure.addSuppressed(problem);
            }
        }
        projections.clear();
        if (failure instanceof Error error) throw error;
        if (failure instanceof RuntimeException exception) throw exception;
    }

    private record ProjectionState(GpuBufferSlice buffer, ProjectionType type) {
        void restore() { if (buffer != null) RenderSystem.setProjectionMatrix(buffer, type); }
    }
}
