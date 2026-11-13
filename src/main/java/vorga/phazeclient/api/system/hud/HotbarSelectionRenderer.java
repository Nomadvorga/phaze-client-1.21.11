package vorga.phazeclient.api.system.hud;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.resource.metadata.GuiResourceMetadata;
import net.minecraft.client.texture.Scaling;
import net.minecraft.util.Atlases;
import net.minecraft.util.Identifier;

/** Default/stretch sprites crop UVs without splitting the GUI queue with a GPU scissor. */
public final class HotbarSelectionRenderer {
    private HotbarSelectionRenderer() {}

    public static void drawClipped(DrawContext context, RenderPipeline pipeline, Identifier texture,
                                   int x, int y, int width, int height, int left, int top, int right, int bottom) {
        int x1 = Math.max(x, left), y1 = Math.max(y, top);
        int x2 = Math.min(x + width, right), y2 = Math.min(y + height, bottom);
        if (x2 <= x1 || y2 <= y1) return;
        if (x1 == x && y1 == y && x2 == x + width && y2 == y + height) {
            context.drawGuiTexture(pipeline, texture, x, y, width, height);
            return;
        }
        var sprite = MinecraftClient.getInstance().getAtlasManager().getAtlasTexture(Atlases.GUI).getSprite(texture);
        var scaling = sprite.getContents().getAdditionalMetadataValue(GuiResourceMetadata.SERIALIZER)
                .orElse(GuiResourceMetadata.DEFAULT).scaling();
        if (scaling instanceof Scaling.Stretch) {
            context.drawGuiTexture(pipeline, texture, width, height,
                    x1 - x, y1 - y, x1, y1, x2 - x1, y2 - y1);
        } else {
            // Tile/nine-slice resource packs retain vanilla semantics and nested clip restoration.
            context.enableScissor(left, top, right, bottom);
            try { context.drawGuiTexture(pipeline, texture, x, y, width, height); }
            finally { context.disableScissor(); }
        }
    }
}
