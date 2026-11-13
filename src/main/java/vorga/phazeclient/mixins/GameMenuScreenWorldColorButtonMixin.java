package vorga.phazeclient.mixins;

import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.ColorCorrection;

@Mixin(GameMenuScreen.class)
public abstract class GameMenuScreenWorldColorButtonMixin extends Screen {
    protected GameMenuScreenWorldColorButtonMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"), require = 1)
    private void phaze$addWorldColorButton(CallbackInfo ci) {
        if (!((GameMenuScreen) (Object) this).shouldShowMenu()) return;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Цвет мира"),
                        button -> ColorCorrection.openWorldColorMenu())
                .dimensions(this.width - 86, 10, 76, 20)
                .build());
    }
}
