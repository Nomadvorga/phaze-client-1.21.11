package vorga.phazeclient.test;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import org.joml.Matrix4f;
import vorga.phazeclient.api.system.hud.HudBuffer;
import vorga.phazeclient.api.system.hud.HudCapture;
import vorga.phazeclient.api.system.hud.HudPassState;
import vorga.phazeclient.api.system.shape.batched.BatchedRectangle;

/** Real GPU attachment/state checks. These deliberately use a caller target other than the main framebuffer. */
final class PortGpuCaptureChecks {
    private PortGpuCaptureChecks() {}

    static void verify(MinecraftClient client) {
        RenderSystem.assertOnRenderThread();
        check(!HudCapture.isActive(), "smoke runs between captures");
        Framebuffer previousCapture = HudBuffer.activeCaptureFramebuffer;
        int previousSentinel = HudBuffer.activeCaptureTarget;
        SimpleFramebuffer caller = new SimpleFramebuffer("phaze/port caller", 128, 96, true);
        SimpleFramebuffer parent = null, child = null;
        try (HudPassState restoreCaller = HudPassState.capture()) {
            parent = new SimpleFramebuffer("phaze/port parent", 80, 64, true);
            child = new SimpleFramebuffer("phaze/port child", 32, 24, true);
            RenderSystem.outputColorTextureOverride = caller.getColorAttachmentView();
            RenderSystem.outputDepthTextureOverride = caller.getDepthAttachmentView();
            BatchedRectangle.setRenderTargetFbHeight(96);
            Matrix4f callerView = new Matrix4f().translation(1, 2, 3);
            Matrix4f parentView = new Matrix4f().translation(4, 5, 6);
            RenderSystem.getModelViewStack().set(callerView);
            RenderSystem.enableScissorForRenderTypeDraws(5, 7, 17, 11);

            HudCapture outer = HudCapture.begin(parent);
            try {
                target(client, parent, 64);
                RenderSystem.getModelViewStack().set(parentView);
                RenderSystem.enableScissorForRenderTypeDraws(2, 3, 9, 8);
                HudCapture inner = HudCapture.begin(child);
                target(client, child, 24);
                boolean rejected = false;
                try { outer.close(false); } catch (IllegalStateException expected) { rejected = true; }
                check(rejected && !outer.isClosed() && !inner.isClosed(), "out-of-order close cannot corrupt the capture stack");
                RenderSystem.getModelViewStack().identity();
                inner.close(false);
                target(client, parent, 64);
                check(RenderSystem.getModelViewMatrix().equals(parentView), "child restores the parent view");
                scissor(2, 3, 9, 8);
                outer.close(false);
                check(!outer.wasSuccessful() && !inner.wasSuccessful(), "aborted captures never publish content");
            } finally {
                HudCapture.abortThrough(outer);
            }
            check(RenderSystem.outputColorTextureOverride == caller.getColorAttachmentView()
                            && RenderSystem.outputDepthTextureOverride == caller.getDepthAttachmentView(),
                    "parent restores the real caller attachments, not the main framebuffer");
            check(RenderSystem.getModelViewMatrix().equals(callerView), "parent restores the caller view");
            check(BatchedRectangle.getRenderTargetFbHeightOverride() == 96, "parent restores the caller framebuffer height");
            scissor(5, 7, 17, 11);
            check(!HudCapture.isActive() && HudBuffer.activeCaptureFramebuffer == previousCapture
                            && HudBuffer.activeCaptureTarget == previousSentinel, "normal abort restores capture references and sentinel");

            HudCapture owner = HudCapture.begin(parent);
            try {
                HudCapture nested = HudCapture.begin(child);
                HudCapture.abortThrough(owner);
                check(owner.isClosed() && nested.isClosed() && !HudCapture.isActive(), "owner disposal unwinds nested captures first");
                check(RenderSystem.outputColorTextureOverride == caller.getColorAttachmentView(), "nested disposal restores the caller target");
                scissor(5, 7, 17, 11);
            } finally {
                HudCapture.abortThrough(owner);
            }
        } finally {
            try { if (child != null) child.delete(); }
            finally { try { if (parent != null) parent.delete(); } finally { caller.delete(); } }
        }
        System.out.println("Port smoke nested GPU capture checks PASSED");
    }

    private static void target(MinecraftClient client, Framebuffer framebuffer, int height) {
        check(client.getFramebuffer() == framebuffer && HudBuffer.activeCaptureFramebuffer == framebuffer,
                "Minecraft/render layers resolve the innermost capture framebuffer");
        check(RenderSystem.outputColorTextureOverride == framebuffer.getColorAttachmentView()
                        && RenderSystem.outputDepthTextureOverride == framebuffer.getDepthAttachmentView(), "GPU attachments follow capture nesting");
        check(BatchedRectangle.getRenderTargetFbHeightOverride() == height, "raw framebuffer height follows capture nesting");
    }

    private static void scissor(int x, int y, int width, int height) {
        var scissor = RenderSystem.getScissorStateForRenderTypeDraws();
        check(scissor.isEnabled() && scissor.getX() == x && scissor.getY() == y
                        && scissor.getWidth() == width && scissor.getHeight() == height, "capture restores the caller scissor");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
