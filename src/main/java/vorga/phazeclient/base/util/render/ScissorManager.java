package vorga.phazeclient.base.util.render;

import com.mojang.blaze3d.systems.RenderSystem;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import vorga.phazeclient.api.system.shape.batched.GuiBatch;
import vorga.phazeclient.base.QuickImports;

import java.util.ArrayDeque;
import java.util.Deque;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ScissorManager implements QuickImports {
    @lombok.experimental.NonFinal
    private static boolean syncingVanilla;

    Deque<Scissor> scissorStack = new ArrayDeque<>();
    Deque<Scissor> freeScissors = new ArrayDeque<>();
    Vector3f scratchPosition = new Vector3f();
    Vector3f scratchSize = new Vector3f();

    /** Vanilla elements record their scissor when queued, so mirroring a clip needs no batch flush. */
    public static boolean isSyncingVanilla() {
        return syncingVanilla;
    }

    public void push(Matrix4f matrix4f, float x, float y, float width, float height) {

        vorga.phazeclient.api.system.shape.batched.BatchedRectangle.flushIfBatching();

        Vector3f pos = matrix4f.transformPosition(x, y, 0, scratchPosition);
        Vector3f size = matrix4f.getScale(scratchSize).mul(width, height, 0);

        Scissor newScissor = freeScissors.pollFirst();
        if (newScissor == null) {
            newScissor = new Scissor();
        }

        if (!scissorStack.isEmpty()) {
            Scissor prevScissor = scissorStack.peek();

            double intersectX = Math.max(prevScissor.x, pos.x);
            double intersectY = Math.max(prevScissor.y, pos.y);
            double intersectWidth = Math.min(prevScissor.x + prevScissor.width, pos.x + size.x) - intersectX;
            double intersectHeight = Math.min(prevScissor.y + prevScissor.height, pos.y + size.y) - intersectY;

            newScissor.set(intersectX, intersectY, intersectWidth, intersectHeight);
        } else {
            newScissor.set(pos.x, pos.y, size.x, size.y);
        }

        scissorStack.push(newScissor);
        setScissor(newScissor);
        pushVanilla(newScissor);
    }

    public void pop() {

        vorga.phazeclient.api.system.shape.batched.BatchedRectangle.flushIfBatching();

        if (!scissorStack.isEmpty()) {
            Scissor popped = scissorStack.pop();
            popVanilla(popped);
            freeScissors.addFirst(popped);
            if (scissorStack.isEmpty()) {
                vorga.phazeclient.api.system.shape.batched.GuiClipState.onDisable();
                RenderSystem.disableScissorForRenderTypeDraws();
            } else {
                setScissor(scissorStack.peek());
            }
        }
    }

    public boolean fullyContains(Matrix4f matrix4f, float x, float y, float width, float height) {
        Scissor active = scissorStack.peek();
        if (active == null) {
            return true;
        }
        Vector3f pos = matrix4f.transformPosition(x, y, 0, scratchPosition);
        Vector3f size = matrix4f.getScale(scratchSize).mul(width, height, 0);
        return pos.x >= active.x
                && pos.y >= active.y
                && pos.x + size.x <= active.x + active.width
                && pos.y + size.y <= active.y + active.height;
    }

    private void setScissor(Scissor scissor) {
        int scaleFactor = (int) window().getScaleFactor();
        int x = scissor.x * scaleFactor;
        int y = window().getHeight() - (scissor.y * scaleFactor + scissor.height * scaleFactor);
        int width = scissor.width * scaleFactor;
        int height = scissor.height * scaleFactor;

        vorga.phazeclient.api.system.shape.batched.GuiDrawStats.scissorChanges++;
        vorga.phazeclient.api.system.shape.batched.GuiClipState.onBox(x, y, width, height);
        RenderSystem.enableScissorForRenderTypeDraws(x, y, width, height);
    }

    /** Vanilla DrawContext draws are clipped only by the context's own scissor stack, in GUI space. */
    private static void pushVanilla(Scissor scissor) {
        DrawContext context = GuiBatch.currentContext();
        scissor.context = context;
        if (context == null) return;
        Matrix3x2fStack matrices = context.getMatrices();
        syncingVanilla = true;
        matrices.pushMatrix();
        try {
            matrices.identity();
            context.enableScissor(scissor.x, scissor.y, scissor.x + scissor.width, scissor.y + scissor.height);
        } finally {
            matrices.popMatrix();
            syncingVanilla = false;
        }
    }

    private static void popVanilla(Scissor scissor) {
        DrawContext context = scissor.context;
        scissor.context = null;
        if (context == null) return;
        syncingVanilla = true;
        try {
            context.disableScissor();
        } finally {
            syncingVanilla = false;
        }
    }

    private static class Scissor {
        public int x, y;
        public int width, height;
        DrawContext context;

        public void set(double x, double y, double width, double height) {
            this.x = Math.max(0, (int) Math.round(x));
            this.y = Math.max(0, (int) Math.round(y));
            this.width = Math.max(0, (int) Math.round(width));
            this.height = Math.max(0, (int) Math.round(height));
        }
    }
}
