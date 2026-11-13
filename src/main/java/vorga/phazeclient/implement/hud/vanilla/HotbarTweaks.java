package vorga.phazeclient.implement.hud.vanilla;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import vorga.phazeclient.api.system.hud.ExordiumAnimationBridge;
import vorga.phazeclient.api.system.hud.HotbarSelectionRenderer;
import vorga.phazeclient.api.system.hud.HotbarSlideAnimation;
import vorga.phazeclient.implement.features.modules.hud.Cooldowns;
import vorga.phazeclient.implement.features.modules.other.Animations;
import vorga.phazeclient.implement.features.modules.other.HealingHelper;
import vorga.phazeclient.implement.features.modules.other.ItemHighlighter;
import vorga.phazeclient.implement.features.modules.other.MaceIndicator;
import java.util.function.DoubleUnaryOperator;

/** Deferred GUI hotbar additions: selector, cooldown digits and utility fills. */
public final class HotbarTweaks {
    private static final int SLOT_PX = 20, BG_W = 182, BG_H = 22;
    private static final HotbarSlideAnimation SLIDE = new HotbarSlideAnimation();
    private static final DoubleUnaryOperator SMOOTHNESS = speed -> Animations.getInstance().smoothnessForSpeed((float) speed);
    @FunctionalInterface public interface SelectionDraw { void drawAt(int x); }
    private HotbarTweaks() { }
    public static void drawCooldownNumber(DrawContext context, int x, int y, float tickDelta, PlayerEntity player, ItemStack stack) {
        Cooldowns module = Cooldowns.getInstance();
        if (module == null || !module.isEnabled() || stack == null || stack.isEmpty() || player == null) return;
        ItemCooldownManager manager = player.getItemCooldownManager();
        float progress = manager.getCooldownProgress(stack, tickDelta);
        if (progress <= 0) return;
        ItemCooldownManager.Entry entry = manager.entries.get(manager.getGroup(stack));
        if (entry == null) return;
        float ticks = entry.endTick - (manager.tick + tickDelta);
        if (ticks <= 0) return;
        MinecraftClient client = MinecraftClient.getInstance();
        String text = module.formatSeconds(ticks / 20);
        context.drawText(client.textRenderer, text, x + 8 - client.textRenderer.getWidth(text) / 2, y + 1,
                module.colorForProgress(progress), module.textShadow.isValue());
    }
    public static void advanceSlide() {
        Animations module = Animations.getInstance();
        MinecraftClient client = MinecraftClient.getInstance();
        if (module == null || !module.isHotbarSlideEnabled() || client == null || client.player == null) {
            SLIDE.resetTracking(); ExordiumAnimationBridge.updateHotbarAnimation(0, 0, false, 0); return;
        }
        int selected = client.player.getInventory().getSelectedSlot();
        SLIDE.advance(selected, module.isHotbarRolloverEnabled(), module.hotbarSpeed.getValue(), System.nanoTime(), SMOOTHNESS);
        ExordiumAnimationBridge.updateHotbarAnimation(SLIDE.currentX(), selected * SLOT_PX,
                SLIDE.mirrorOffset() != 0, SLIDE.mirrorOffset());
    }
    public static void renderSelection(DrawContext context, RenderPipeline pipeline, Identifier texture,
                                       int x, int y, int width, int height, SelectionDraw original) {
        Animations module = Animations.getInstance();
        MinecraftClient client = MinecraftClient.getInstance();
        if (module == null || !module.isHotbarSlideEnabled() || client == null || client.player == null) {
            original.drawAt(x); return;
        }
        if (ExordiumAnimationBridge.isCapturingHotbar()) {
            ExordiumAnimationBridge.recordHotbarSelection(pipeline, texture, x, y, width, height); return;
        }
        int newX = x - client.player.getInventory().getSelectedSlot() * SLOT_PX + Math.round(SLIDE.currentX());
        int mirror = SLIDE.mirrorOffset();
        if (mirror == 0) { original.drawAt(newX); return; }
        int left = context.getScaledWindowWidth() / 2 - BG_W / 2, top = context.getScaledWindowHeight() - BG_H;
        int right = left + BG_W, bottom = top + BG_H;
        HotbarSelectionRenderer.drawClipped(context, pipeline, texture, newX, y, width, height, left, top, right, bottom);
        HotbarSelectionRenderer.drawClipped(context, pipeline, texture, newX + mirror, y, width, height, left, top, right, bottom);
    }
    public static void drawUtilityHighlights(DrawContext context) {
        MaceIndicator mace = MaceIndicator.getInstance();
        ItemHighlighter highlighter = ItemHighlighter.getInstance();
        HealingHelper healing = HealingHelper.getInstance();
        boolean useMace = mace != null && mace.isEnabled(), useHighlighter = highlighter != null && highlighter.isEnabled();
        boolean useHealing = healing != null && healing.isEnabled();
        if (!useMace && !useHighlighter && !useHealing) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        if (useHighlighter) highlighter.beginRenderPass();
        if (useHealing) healing.beginRenderPass();
        int center = context.getScaledWindowWidth() / 2, y = context.getScaledWindowHeight() - 19;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = client.player.getInventory().getMainStacks().get(slot);
            int x = center - 90 + slot * SLOT_PX + 2;
            if (useMace) fillSlot(context, x, y, mace.colorForStack(stack));
            if (useHighlighter) fillSlot(context, x, y, highlighter.colorForPreparedStack(stack));
            if (useHealing) fillSlot(context, x, y, healing.colorForPreparedStack(stack));
        }
    }
    /** Compatibility hook: DrawContext now owns draw ordering and render state. */
    public static void flush(DrawContext context) { }
    private static void fillSlot(DrawContext context, int x, int y, int color) {
        if ((color >>> 24) != 0) context.fill(x, y, x + 16, y + 16, color);
    }
}
