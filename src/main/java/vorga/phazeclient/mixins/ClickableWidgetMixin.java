package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.api.system.cursor.CursorManager;
import vorga.phazeclient.implement.features.modules.other.Animations;

/**
 * Hover and slider-drag cursor requests for widgets placed on Phaze's own screens.
 * Widgets on vanilla screens keep 1.21.11's native cursors.
 */
@Mixin(ClickableWidget.class)
public abstract class ClickableWidgetMixin {

    @Inject(method = "render", at = @At("TAIL"), require = 1)
    private void phaze$cursorRequestHand(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!Animations.getInstance().isDynamicCursorEnabled() || !phaze$onPhazeScreen()) {
            return;
        }
        ClickableWidget self = (ClickableWidget) (Object) this;
        if (!self.visible) {
            return;
        }
        int x = self.getX();
        int y = self.getY();
        boolean over = mouseX >= x && mouseX < x + self.getWidth() && mouseY >= y && mouseY < y + self.getHeight();
        if (!over) {
            return;
        }
        if (!self.active) {
            CursorManager.requestNotAllowed();
        } else if (self instanceof TextFieldWidget) {
            CursorManager.requestBeam();
        } else {
            CursorManager.requestHand();
        }
    }

    /** Re-arms the 150 ms scroll-override window every drag tick, so the resize cursor holds for the whole drag. */
    @Inject(method = "mouseDragged", at = @At("HEAD"), require = 1)
    private void phaze$cursorOnDrag(Click click, double deltaX, double deltaY, CallbackInfoReturnable<Boolean> cir) {
        if (!Animations.getInstance().isDynamicCursorEnabled() || !phaze$onPhazeScreen()) {
            return;
        }
        ClickableWidget self = (ClickableWidget) (Object) this;
        if (self instanceof SliderWidget || !phaze$looksLikeSlider(self)) {
            return;
        }
        CursorManager.notifyScroll(1.0, 0.0);
    }

    @Unique
    private static boolean phaze$onPhazeScreen() {
        Screen screen = MinecraftClient.getInstance().currentScreen;
        return screen instanceof ChatScreen || screen != null && screen.getClass().getName().startsWith("vorga.phazeclient.");
    }

    @Unique
    private static boolean phaze$looksLikeSlider(ClickableWidget widget) {
        String name = widget.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT);
        return name.contains("slider") || name.contains("range") || name.contains("bar");
    }
}
