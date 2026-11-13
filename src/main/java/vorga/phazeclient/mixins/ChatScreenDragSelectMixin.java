package vorga.phazeclient.mixins;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.ParentElement;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.implement.features.modules.other.Animations;

/** Arms screen dragging when a click lands in the chat field, which bypasses ParentElement. */
@Mixin(ChatScreen.class)
public abstract class ChatScreenDragSelectMixin {
    @Shadow protected TextFieldWidget chatField;

    @Inject(method = "mouseClicked(Lnet/minecraft/client/gui/Click;Z)Z", at = @At("RETURN"), require = 1)
    private void phaze$armChatDrag(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (click.button() != 0) return;
        if (!Animations.getInstance().isComfortableTextSelectionEnabled()) return;
        if (chatField == null) return;

        double mouseX = click.x();
        double mouseY = click.y();
        int x = chatField.getX();
        int y = chatField.getY();
        int w = chatField.getWidth();
        int h = chatField.getHeight();
        if (mouseX < x || mouseX >= x + w || mouseY < y || mouseY >= y + h) return;

        ParentElement self = (ParentElement) (Object) this;
        self.setFocused(chatField);
        self.setDragging(true);
    }
}
