package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.implement.features.modules.other.Animations;

/**
 * Drag-to-select for vanilla text fields. {@link TextFieldWidget} still does not
 * override {@code mouseDragged}, so a drag never moves the selection.
 */
@Mixin(ClickableWidget.class)
public abstract class ClickableWidgetDragSelectMixin {

    @Inject(method = "mouseDragged(Lnet/minecraft/client/gui/Click;DD)Z", at = @At("HEAD"), cancellable = true, require = 1)
    private void phaze$dragSelect(Click click, double deltaX, double deltaY, CallbackInfoReturnable<Boolean> cir) {
        if (click.button() != 0) return;
        if (!Animations.getInstance().isComfortableTextSelectionEnabled()) return;

        if (!((Object) this instanceof TextFieldWidget tf)) return;
        if (!tf.active || !tf.visible) return;
        if (!tf.isFocused()) tf.setFocused(true);

        int relX = MathHelper.floor(click.x()) - tf.getX();
        int inset = (tf.getWidth() - tf.getInnerWidth()) / 2;
        if (inset > 0) relX -= inset;
        if (relX < 0) relX = 0;

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        if (tr == null) return;

        String text = tf.getText();
        if (text == null) text = "";

        int idx = 0;
        int best = 0;
        int bestDist = Integer.MAX_VALUE;
        for (int i = 0; i <= text.length(); i++) {
            int width = tr.getWidth(text.substring(0, i));
            int dist = Math.abs(width - relX);
            if (dist < bestDist) {
                bestDist = dist;
                best = i;
            }
            if (width > relX + 4) break;
            idx = i;
        }
        tf.setSelectionStart(bestDist < Integer.MAX_VALUE ? best : idx);
        cir.setReturnValue(true);
    }
}
