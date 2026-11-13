package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.input.MouseInput;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.api.system.cursor.CursorManager;
import vorga.phazeclient.implement.features.modules.other.Animations;

/** Scroll-cursor window and drag-pin release for {@link CursorManager}. */
@Mixin(Mouse.class)
public abstract class MouseCursorMixin {

    /** In-game wheel events (hotbar, Zoom) must not tug at the cursor while the camera owns the mouse. */
    @Inject(method = "onMouseScroll", at = @At("HEAD"), require = 1)
    private void phaze$cursorOnScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (!Animations.getInstance().isDynamicCursorEnabled()) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.currentScreen == null) {
            return;
        }
        CursorManager.notifyScroll(horizontal, vertical);
    }

    /** Safety net for slider releases swallowed by a parent component or a mid-drag screen swap. */
    @Inject(method = "onMouseButton", at = @At("HEAD"), require = 1)
    private void phaze$cursorOnMouseButton(long window, MouseInput input, int action, CallbackInfo ci) {
        if (action == GLFW.GLFW_RELEASE && input.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            CursorManager.endDrag();
        }
    }
}
