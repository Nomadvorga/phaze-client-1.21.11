package vorga.phazeclient.mixins;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.api.system.cursor.CursorManager;
import vorga.phazeclient.implement.features.modules.other.Animations;

/**
 * Per-frame request lifecycle for Phaze's own screens and the chat HUD editor.
 * Vanilla screens keep 1.21.11's native widget cursors.
 */
@Mixin(Screen.class)
public abstract class ScreenCursorMixin {

    /** {@code renderWithTooltip} is final, so overriding screens cannot skip the bracket. */
    @Inject(method = "renderWithTooltip", at = @At("HEAD"))
    private void phaze$cursorBeginFrame(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (phaze$ownsCursor()) CursorManager.beginFrame();
    }

    /** Still commits when the toggle is off, so the arrow comes back on the next frame. */
    @Inject(method = "renderWithTooltip", at = @At("TAIL"))
    private void phaze$cursorEndFrame(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (phaze$ownsCursor()) CursorManager.endFrame(Animations.getInstance().isDynamicCursorEnabled());
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void phaze$cursorOnRemoved(CallbackInfo ci) {
        if (phaze$ownsCursor()) CursorManager.forceArrow();
    }

    @Unique
    private boolean phaze$ownsCursor() {
        return (Object) this instanceof ChatScreen || getClass().getName().startsWith("vorga.phazeclient.");
    }
}
