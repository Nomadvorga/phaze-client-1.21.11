package vorga.phazeclient.mixins;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.pack.PackListWidget;
import net.minecraft.client.gui.screen.pack.ResourcePackOrganizer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.PackOrganizer;
import vorga.phazeclient.implement.features.modules.other.PackPreviewRenderer;

@Mixin(PackListWidget.ResourcePackEntry.class)
public abstract class ResourcePackEntryPreviewMixin {
    // Vanilla draws the icon and its controls first, then the name widget.
    // Replace only that text section, preserving all pack actions.
    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;IIZF)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/widget/TextWidget;render(Lnet/minecraft/client/gui/DrawContext;IIF)V"),
            cancellable = true, require = 1)
    private void phaze$renderPackPreview(DrawContext context, int mouseX, int mouseY, boolean hovered,
                                          float tickDelta, CallbackInfo ci) {
        PackOrganizer module = PackOrganizer.getInstance();
        if (!hovered || module == null || !module.isEnabled() || !module.packPreviews.isValue()) {
            return;
        }

        ResourcePackOrganizer.Pack pack = ((ResourcePackEntryAccessor) (Object) this).phaze$getPack();
        PackListWidget.Entry entry = (PackListWidget.Entry) (Object) this;
        int x = entry.getContentX();
        int y = entry.getContentY();
        int width = Math.max(1, entry.getContentWidth() - 40);
        int height = Math.min(32, entry.getContentHeight());
        if (pack != null && PackPreviewRenderer.render(context, pack.getName(), x + 32, y, width, height)) {
            ci.cancel();
        }
    }
}
