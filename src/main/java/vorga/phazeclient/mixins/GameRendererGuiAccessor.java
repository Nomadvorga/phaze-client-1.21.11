package vorga.phazeclient.mixins;

import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameRenderer.class)
public interface GameRendererGuiAccessor {
    @Accessor("guiRenderer") GuiRenderer phaze$getGuiRenderer();
    @Accessor("guiState") GuiRenderState phaze$getGuiState();
    @Accessor("fogRenderer") FogRenderer phaze$getFogRenderer();
}
