package vorga.phazeclient.implement.hud.vanilla;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import vorga.phazeclient.helpers.ColorHelper;
import vorga.phazeclient.helpers.TextureHelper;
import vorga.phazeclient.implement.features.modules.other.Saturation;

/** Saturation pips and the air-bubble lift for the second hunger bar. */
public final class SaturationOverlay {
    private static final int BUBBLE_LIFT_PX = 10, ICON_SIZE = 9;
    private static boolean bubblesLifted;
    private SaturationOverlay() { }
    public static void renderStatusBars(DrawContext context) {
        Saturation module = Saturation.getInstance();
        if (module == null || !module.isEnabled()) return;
        PlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return;
        float saturation = player.getHungerManager().getSaturationLevel();
        if (saturation <= 0) return;
        drawSaturation(context, saturation, 0, 1, context.getScaledWindowWidth() / 2 + 91,
                context.getScaledWindowHeight() - 39);
    }
    public static void beforeAirBubbles(DrawContext context) {
        if (!bubblesLifted && shouldLiftBubbles()) {
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(0, -BUBBLE_LIFT_PX);
            bubblesLifted = true;
        }
    }
    public static void afterAirBubbles(DrawContext context) {
        if (bubblesLifted) { context.getMatrices().popMatrix(); bubblesLifted = false; }
    }
    private static boolean shouldLiftBubbles() {
        Saturation module = Saturation.getInstance();
        return module != null && module.isEnabled() && "Second Hunger Bar".equals(module.style.getSelected());
    }
    private static void drawSaturation(DrawContext context, float level, float gained, float alpha, int right, int top) {
        if (level + gained < 0) return;
        int color = ColorHelper.argbFromRGBA(1, 1, 1, alpha);
        float modified = Math.max(0, Math.min(level + gained, 20));
        int start = gained == 0 ? 0 : (int) Math.max(level / 2, 0);
        int end = (int) Math.ceil(modified / 2);
        boolean yellow = "Yellow Bar".equals(Saturation.getInstance().style.getSelected());
        for (int i = start; i < end; i++) {
            int x = right - i * 8 - 9;
            float fill = modified / 2 - i;
            if (yellow) {
                int u = fill >= 1 ? 3 * ICON_SIZE : fill > 0.5F ? 2 * ICON_SIZE : fill > 0.25F ? ICON_SIZE : 0;
                context.drawTexture(RenderPipelines.GUI_TEXTURED, TextureHelper.MOD_ICONS, x, top, u, 0,
                        ICON_SIZE, ICON_SIZE, 256, 256, color);
            } else {
                Identifier texture = fill >= 1 ? TextureHelper.FOOD_FULL_TEXTURE : fill >= 0.5F ? TextureHelper.FOOD_HALF_TEXTURE : null;
                if (texture == null) continue;
                context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, TextureHelper.FOOD_EMPTY_TEXTURE, x, top - 10, ICON_SIZE, ICON_SIZE);
                context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, texture, x, top - 10, ICON_SIZE, ICON_SIZE);
            }
        }
    }
}
