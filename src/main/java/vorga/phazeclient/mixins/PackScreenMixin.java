package vorga.phazeclient.mixins;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.pack.PackScreen;
import net.minecraft.client.gui.screen.pack.ResourcePackOrganizer;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.base.util.Lang;
import vorga.phazeclient.implement.features.modules.other.PackOrganizer;
import vorga.phazeclient.implement.features.modules.other.PackPreviewRenderer;
import vorga.phazeclient.implement.features.modules.other.PackSearch;

import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * 1.21.11 already draws the pack search box. This mixin keeps that box,
 * replaces its matcher with {@link PackSearch} while the module is on,
 * and drives the hover-preview cache.
 */
@Mixin(PackScreen.class)
public abstract class PackScreenMixin extends Screen {
    @Shadow @Final private ResourcePackOrganizer organizer;
    @Shadow private ButtonWidget doneButton;
    @Shadow private TextFieldWidget searchBox;

    protected PackScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"), require = 1)
    private void phaze$configurePackSearch(CallbackInfo ci) {
        PackPreviewRenderer.clear(this.client);
        if (this.searchBox == null) return;
        boolean enabled = this.phaze$isPackSearchEnabled();
        this.searchBox.setVisible(enabled);
        if (enabled) {
            this.searchBox.setPlaceholder(Text.literal(Lang.translate("Search packs...")));
        }
    }

    @Inject(method = "refresh", at = @At("HEAD"), require = 1)
    private void phaze$invalidatePackPreviews(CallbackInfo ci) {
        if (this.client != null) {
            PackPreviewRenderer.clear(this.client);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        PackOrganizer module = PackOrganizer.getInstance();
        if (module != null && module.isEnabled() && module.packPreviews.isValue()) {
            PackPreviewRenderer.beginFrame();
        } else {
            PackPreviewRenderer.clear(this.client);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void removed() {
        super.removed();
        PackPreviewRenderer.clear(this.client);
    }

    /** Presentation filter only; vanilla retains the complete pack selection and order. */
    @Redirect(method = "filter", at = @At(value = "INVOKE",
            target = "Ljava/util/stream/Stream;filter(Ljava/util/function/Predicate;)Ljava/util/stream/Stream;"),
            require = 1)
    private Stream<ResourcePackOrganizer.Pack> phaze$filterPacks(Stream<ResourcePackOrganizer.Pack> packs,
                                                                  Predicate<ResourcePackOrganizer.Pack> vanilla) {
        if (!this.phaze$isPackSearchEnabled() || this.searchBox == null || this.searchBox.getText().isEmpty()) {
            return packs.filter(vanilla);
        }
        String query = this.searchBox.getText();
        return packs.filter(pack -> PackSearch.matches(query, pack.getName(),
                pack.getDisplayName().getString(), pack.getDescription().getString()));
    }

    /** Vanilla disables Done when the filtered selected list is empty, even though packs stay enabled. */
    @Inject(method = {"filter", "updatePackLists"}, at = @At("TAIL"), require = 2)
    private void phaze$keepDoneButtonUsable(CallbackInfo ci) {
        if (this.doneButton != null && this.phaze$isPackSearchEnabled() && this.searchBox != null
                && !this.searchBox.getText().isEmpty()) {
            this.doneButton.active = this.organizer.getEnabledPacks().findAny().isPresent();
        }
    }

    @Unique
    private boolean phaze$isPackSearchEnabled() {
        PackOrganizer module = PackOrganizer.getInstance();
        return module != null && module.isEnabled() && module.searchBar.isValue();
    }
}
