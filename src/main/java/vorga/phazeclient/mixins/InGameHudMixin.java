package vorga.phazeclient.mixins;

import net.minecraft.client.gl.RenderPipelines;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.Item;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.block.BlockState;
import net.minecraft.block.Block;
import net.minecraft.registry.Registry;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.joml.Matrix3x2f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.api.system.shape.implement.Blur;
import vorga.phazeclient.api.system.cursor.HudCursorRelay;
import vorga.phazeclient.implement.features.modules.client.Theme;
import vorga.phazeclient.implement.features.modules.hud.ArmorHud;
import vorga.phazeclient.implement.features.modules.hud.CoordinatesHud;
import vorga.phazeclient.implement.features.modules.hud.CpsHud;
import vorga.phazeclient.implement.features.modules.hud.DayCounterHud;
import vorga.phazeclient.implement.features.modules.hud.DirectionHud;
import vorga.phazeclient.implement.features.modules.hud.FpsHud;
import vorga.phazeclient.implement.features.modules.hud.KeystrokesHud;
import vorga.phazeclient.implement.features.modules.hud.MovementSpeedHud;
import vorga.phazeclient.implement.features.modules.hud.PingHud;
import vorga.phazeclient.implement.features.modules.hud.PotionHud;
import vorga.phazeclient.implement.features.modules.hud.ReachHud;
import vorga.phazeclient.implement.features.modules.hud.RectHudModule;
import vorga.phazeclient.implement.features.modules.hud.ScoreboardHud;
import vorga.phazeclient.implement.features.modules.hud.ScoreboardSidebarEntry;
import vorga.phazeclient.implement.features.modules.hud.SessionTimeHud;
import vorga.phazeclient.implement.features.modules.hud.SprintHud;
import vorga.phazeclient.implement.features.modules.hud.MemoryHud;
import vorga.phazeclient.implement.features.modules.hud.ComboCounterHud;
import vorga.phazeclient.implement.features.modules.hud.ServerAddressHud;
import vorga.phazeclient.implement.features.modules.hud.WailaHud;
import vorga.phazeclient.implement.features.modules.other.HealthIndicator;
import vorga.phazeclient.implement.features.modules.hud.NametagHud;
import vorga.phazeclient.implement.features.modules.hud.TimeHud;
import vorga.phazeclient.implement.features.modules.hud.TpsHud;
import vorga.phazeclient.implement.features.modules.other.Zoom;
import vorga.phazeclient.api.system.hud.HudBuffer;
import vorga.phazeclient.api.system.hud.HudScaleLimits;
import vorga.phazeclient.api.system.hud.BatchedHudBuffer;
import vorga.phazeclient.api.system.shape.implement.Rectangle;
import vorga.phazeclient.base.util.render.Render2DUtil;
import org.joml.Vector4i;
import vorga.phazeclient.api.system.hud.ChatAnimationFrameAccess;
import vorga.phazeclient.api.system.hud.ExordiumAnimationBridge;
import vorga.phazeclient.implement.features.modules.other.AutoSprint;
import vorga.phazeclient.implement.menu.MenuScreen;
import vorga.phazeclient.implement.menu.components.implement.settings.ScaleSnapOverlay;
import vorga.phazeclient.implement.features.modules.other.Animations;
import vorga.phazeclient.implement.features.modules.hud.Cooldowns;
import vorga.phazeclient.implement.features.modules.other.Crosshair;
import vorga.phazeclient.implement.features.modules.other.HealingHelper;
import vorga.phazeclient.implement.features.modules.other.HudOptimizer;
import vorga.phazeclient.implement.features.modules.other.ItemHighlighter;
import vorga.phazeclient.implement.features.modules.other.MaceIndicator;
import vorga.phazeclient.implement.features.modules.other.NickHider;
import vorga.phazeclient.implement.features.modules.other.NoRender;
import vorga.phazeclient.implement.features.modules.other.Saturation;
import vorga.phazeclient.implement.features.modules.other.TrapTimer;
import vorga.phazeclient.implement.features.modules.hud.InventoryHud;
import vorga.phazeclient.implement.hud.CustomHudRenderer;
import vorga.phazeclient.implement.messenger.MessengerToasts;
import vorga.phazeclient.implement.features.modules.other.StreamerMode;
import vorga.phazeclient.helpers.ColorHelper;
import vorga.phazeclient.helpers.IntPoint;
import vorga.phazeclient.helpers.TextureHelper;
import vorga.phazeclient.core.Main;
import vorga.phazeclient.api.feature.module.Module;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Vector;
import java.util.function.Supplier;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@Mixin(InGameHud.class)
public class InGameHudMixin {
    @Unique
    private static void phaze$resetGuiRenderState() {
    }

    @Inject(method = "render", at = @At("HEAD"), require = 1)
    private void beginHudFrame(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        CustomHudRenderer.beginFrame(this.client, context);
        phaze$advanceHotbarSlide();
    }

    @Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true)
    private void phaze$suppressVanillaStatusEffectOverlay(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (PotionHud.getInstance().isEnabled()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V", at = @At("HEAD"), cancellable = true)
    private void phaze$suppressVanillaScoreboardSidebar(DrawContext context, ScoreboardObjective objective, CallbackInfo ci) {
        NoRender noRender = NoRender.getInstance();
        boolean hideByNoRender = noRender != null && noRender.isEnabled() && noRender.scoreboard.isValue();
        if (ScoreboardHud.getInstance().isEnabled() || hideByNoRender) {
            ci.cancel();
        }
    }

    @Inject(method = "renderBossBarHud", at = @At("HEAD"), cancellable = true, require = 1)
    private void phaze$suppressVanillaBossBar(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        NoRender noRender = NoRender.getInstance();
        if (noRender != null && noRender.isEnabled() && noRender.bossBar.isValue()) {
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At("TAIL"), require = 1)
    private void renderCustomHudFallback(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        CustomHudRenderer.renderFrame(context);
        MessengerToasts.render(context);
    }

    @Shadow @Final private MinecraftClient client;
    @Shadow @Final private PlayerListHud playerListHud;

    @Unique private float phaze$satUnclampedFlashAlpha = 0f;
    @Unique private float phaze$satFlashAlpha = 0f;
    @Unique private byte phaze$satAlphaDir = 1;
    @Unique private final SaturationOffsetsCache phaze$satBarOffsets = new SaturationOffsetsCache();
    @Unique private final SaturationHeldFoodCache phaze$satHeldFood = new SaturationHeldFoodCache();
    @Unique private boolean phaze$bubblesLifted = false;
    @Unique private static final int PHAZE_BUBBLE_LIFT_PX = 10;


    @Unique private boolean phaze$tabWasOpenedThisCycle = false;


    @Inject(method = "renderStatusBars", at = @At("TAIL"))
    private void phaze$onSaturationRenderStatusBars(DrawContext context, CallbackInfo ci) {
        if (!Saturation.getInstance().isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        PlayerEntity player = mc.player;
        HungerManager stats = player.getHungerManager();
        float saturationLevel = stats.getSaturationLevel();
        if (saturationLevel <= 0) {
            phaze$resetSaturationFlash();
            return;
        }

        int right = context.getScaledWindowWidth() / 2 + 91;
        int top = context.getScaledWindowHeight() - 39;
        phaze$drawSaturationOverlay(context, saturationLevel, 0, 1.0F, mc.inGameHud.getTicks(), right, top);

        ItemStack heldItem = player.getMainHandStack();
        if (heldItem.isEmpty()) heldItem = player.getOffHandStack();
        if (heldItem.isEmpty() || !heldItem.contains(net.minecraft.component.DataComponentTypes.FOOD)) {
            phaze$resetSaturationFlash();
            return;
        }
        phaze$satOnClientTick();
    }

    @Inject(method = "renderAirBubbles", at = @At("HEAD"))
    private void phaze$liftAirBubblesHead(DrawContext context, PlayerEntity player,
                                          int heartCount, int maxAirBubbles, int top,
                                          CallbackInfo ci) {
        if (phaze$shouldLiftBubbles()) {
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(0.0F, -PHAZE_BUBBLE_LIFT_PX);
            phaze$bubblesLifted = true;
        }
    }

    @Inject(method = "renderAirBubbles", at = @At("RETURN"))
    private void phaze$liftAirBubblesReturn(DrawContext context, PlayerEntity player,
                                            int heartCount, int maxAirBubbles, int top,
                                            CallbackInfo ci) {
        if (phaze$bubblesLifted) {
            context.getMatrices().popMatrix();
            phaze$bubblesLifted = false;
        }
    }

    @Unique
    private static boolean phaze$shouldLiftBubbles() {
        Saturation sat = Saturation.getInstance();
        if (sat == null || !sat.isEnabled()) return false;
        return "Second Hunger Bar".equals(sat.style.getSelected());
    }

    @Unique
    private void phaze$drawSaturationOverlay(DrawContext context, float saturationLevel, float saturationGained, float alpha, int guiTicks, int right, int top) {
        if (saturationLevel + saturationGained < 0) return;
        int alphaColor = ColorHelper.argbFromRGBA(1.0F, 1.0F, 1.0F, alpha);
        float modifiedSaturation = Math.max(0, Math.min(saturationLevel + saturationGained, 20));
        int startSaturationBar = 0;
        int endSaturationBar = (int) Math.ceil(modifiedSaturation / 2.0F);
        if (saturationGained != 0) startSaturationBar = (int) Math.max(saturationLevel / 2.0F, 0);

        int iconSize = 9;
        int saturationTop = top - 10;
        String styleValue = Saturation.getInstance().style.getSelected();
        boolean isYellowBar = styleValue.equals("Yellow Bar");

        for (int i = startSaturationBar; i < endSaturationBar; ++i) {
            int x = right - i * 8 - 9;
            int y = saturationTop;
            float effectiveSaturationOfBar = (modifiedSaturation / 2.0F) - i;

            if (!isYellowBar) {
                if (effectiveSaturationOfBar <= 0) continue;
                Identifier foodTexture = null;
                if (effectiveSaturationOfBar >= 1) {
                    foodTexture = TextureHelper.FOOD_FULL_TEXTURE;
                } else if (effectiveSaturationOfBar >= .5) {
                    foodTexture = TextureHelper.FOOD_HALF_TEXTURE;
                }
                if (foodTexture == null) continue;
                context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, TextureHelper.FOOD_EMPTY_TEXTURE, x, y, iconSize, iconSize);
                context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, foodTexture, x, y, iconSize, iconSize);
            } else {
                int hungerY = top;
                int v = 0;
                int u = 0;
                if (effectiveSaturationOfBar >= 1) u = 3 * iconSize;
                else if (effectiveSaturationOfBar > .5) u = 2 * iconSize;
                else if (effectiveSaturationOfBar > .25) u = 1 * iconSize;
                context.drawTexture(RenderPipelines.GUI_TEXTURED, TextureHelper.MOD_ICONS, x, hungerY, u, v, iconSize, iconSize, 256, 256, alphaColor);
            }
        }
    }

    @Unique
    private void phaze$satOnClientTick() {
        phaze$satUnclampedFlashAlpha += phaze$satAlphaDir * 0.125F;
        if (phaze$satUnclampedFlashAlpha >= 1.5F) phaze$satAlphaDir = -1;
        else if (phaze$satUnclampedFlashAlpha <= -0.5F) phaze$satAlphaDir = 1;
        phaze$satFlashAlpha = Math.max(0F, Math.min(1F, phaze$satUnclampedFlashAlpha));
    }

    @Unique
    private void phaze$resetSaturationFlash() {
        phaze$satUnclampedFlashAlpha = phaze$satFlashAlpha = 0;
        phaze$satAlphaDir = 1;
    }

    @Unique
    private static class SaturationOffsetsCache {
        private final Vector<IntPoint> foodBarOffsets = new Vector<>();
        private int lastGuiTick = 0;
        private final Random random = new Random();

        private void generate(int guiTicks, PlayerEntity player) {
            final int preferFoodBars = 10;
            if (foodBarOffsets.size() != preferFoodBars) foodBarOffsets.setSize(preferFoodBars);
            random.setSeed((long) (guiTicks * 312871));
            for (int i = 0; i < preferFoodBars; ++i) {
                int x = -(i * 8) - 9;
                int y = 0;
                IntPoint point = foodBarOffsets.get(i);
                if (point == null) {
                    point = new IntPoint();
                    foodBarOffsets.set(i, point);
                }
                point.x = x;
                point.y = y;
            }
            lastGuiTick = guiTicks;
        }

        public Vector<IntPoint> foodBarOffsets(int guiTicks, PlayerEntity player) {
            if (guiTicks != lastGuiTick) generate(guiTicks, player);
            return this.foodBarOffsets;
        }
    }

    @Unique
    private static class SaturationHeldFoodCache {
        private ItemStack lastHeldItem;
        private int lastGuiTick = 0;

        public ItemStack result(int guiTick, PlayerEntity player) {
            if (guiTick != lastGuiTick) {
                ItemStack heldItem = player.getMainHandStack();
                if (heldItem.isEmpty()) heldItem = player.getOffHandStack();
                lastHeldItem = heldItem;
                lastGuiTick = guiTick;
            }
            return lastHeldItem;
        }
    }

    @Inject(
            method = "renderHotbarItem(Lnet/minecraft/client/gui/DrawContext;IILnet/minecraft/client/render/RenderTickCounter;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/item/ItemStack;I)V",
            at = @At("TAIL")
    )
    private void phaze$drawCooldownNumber(
            DrawContext context, int x, int y, RenderTickCounter tickCounter,
            PlayerEntity player, ItemStack stack, int seed, CallbackInfo ci
    ) {
        Cooldowns module = Cooldowns.getInstance();
        if (module == null || !module.isEnabled()) return;
        if (stack == null || stack.isEmpty() || player == null) return;
        ItemCooldownManager manager = player.getItemCooldownManager();
        if (manager == null) return;
        float tickDelta = tickCounter.getTickProgress(false);
        float progress = manager.getCooldownProgress(stack, tickDelta);
        if (progress <= 0.0F) return;

        Identifier groupId = manager.getGroup(stack);
        ItemCooldownManager.Entry entry = manager.entries.get(groupId);
        if (entry == null) return;
        float remainingTicks = entry.endTick - (manager.tick + tickDelta);
        if (remainingTicks <= 0.0F) return;
        float remainingSeconds = remainingTicks / 20.0F;

        String text = module.formatSeconds(remainingSeconds);
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.textRenderer == null) return;
        int color = module.colorForProgress(progress);
        int textWidth = mc.textRenderer.getWidth(text);
        int drawX = x + 8 - textWidth / 2;

        int drawY = y + 1;

        context.drawText(mc.textRenderer, text, drawX, drawY, color, module.textShadow.isValue());
    }

    @Unique
    private void phaze$advanceHotbarSlide() {
        vorga.phazeclient.implement.hud.vanilla.HotbarTweaks.advanceSlide();
    }

    @WrapOperation(
            method = "renderHotbar",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIII)V",
                    ordinal = 1), require = 1
    )
    private void phaze$slideSelection(DrawContext context, RenderPipeline pipeline, Identifier texture,
                                      int x, int y, int width, int height, Operation<Void> original) {
        vorga.phazeclient.implement.hud.vanilla.HotbarTweaks.renderSelection(
                context, pipeline, texture, x, y, width, height,
                nx -> original.call(context, pipeline, texture, nx, y, width, height));
    }

    @Inject(method = "renderHotbar", at = @At("TAIL"))
    private void phaze$drawHotbarUtilityHighlights(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        MaceIndicator mace = MaceIndicator.getInstance();
        ItemHighlighter highlighter = ItemHighlighter.getInstance();
        HealingHelper healing = HealingHelper.getInstance();
        boolean maceEnabled = mace != null && mace.isEnabled();
        boolean highlighterEnabled = highlighter != null && highlighter.isEnabled();
        boolean healingEnabled = healing != null && healing.isEnabled();
        if (!maceEnabled && !highlighterEnabled && !healingEnabled) return;
        if (highlighterEnabled) highlighter.beginRenderPass();
        if (healingEnabled) healing.beginRenderPass();
        phaze$paintHotbarUtilityFills(
                context,
                maceEnabled ? mace : null,
                highlighterEnabled ? highlighter : null,
                healingEnabled ? healing : null
        );
    }

    @ModifyArg(
            method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
            at = @At(value = "INVOKE",

                    target = "Lnet/minecraft/client/gui/DrawContext;drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;IIIZ)V")
    )
    private Text phaze$hideOwnNickInSidebar(Text original) {
        NickHider hider = NickHider.getInstance();
        if (hider == null || !hider.isEnabled()) return original;
        return hider.rewrite(original);
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void phaze$tabSlideTick(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        Animations module = Animations.getInstance();
        if (module == null || !module.isTabSlideEnabled()) {
            phaze$tabWasOpenedThisCycle = false;
            return;
        }
        if (this.client == null || this.client.options == null) {
            phaze$tabWasOpenedThisCycle = false;
            return;
        }
        if (this.client.options.hudHidden) {
            module.snapTabClosed();
            phaze$tabWasOpenedThisCycle = false;
            return;
        }

        boolean keyPressed = this.client.options.playerListKey.isPressed();
        module.tickTabSlide(keyPressed);

        if (keyPressed) {
            if (phaze$vanillaTabListWouldRender()) phaze$tabWasOpenedThisCycle = true;
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void phaze$tabSlideRenderTail(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (this.client != null && this.client.currentScreen instanceof MenuScreen) {
            phaze$tabWasOpenedThisCycle = false;
            return;
        }
        Animations module = Animations.getInstance();
        if (module == null || !module.isTabSlideEnabled()) {
            phaze$tabWasOpenedThisCycle = false;
            return;
        }
        if (this.client == null || this.client.options == null || this.client.options.hudHidden) {
            phaze$tabWasOpenedThisCycle = false;
            return;
        }

        boolean keyPressed = this.client.options.playerListKey.isPressed();
        if (keyPressed) {
            return;
        }
        if (!module.isTabSlideRendering(false)) {
            phaze$tabWasOpenedThisCycle = false;
            return;
        }
        if (!phaze$tabWasOpenedThisCycle) return;

        if (ExordiumAnimationBridge.renderClosingPlayerListFromCache()) {
            return;
        }

        this.playerListHud.setVisible(true);
        Scoreboard scoreboard = this.client.world == null ? null : this.client.world.getScoreboard();
        ScoreboardObjective objective = scoreboard == null ? null
                : scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.LIST);
        this.playerListHud.render(context, context.getScaledWindowWidth(), scoreboard, objective);
        this.playerListHud.setVisible(false);
    }

    @Unique
    private boolean phaze$vanillaTabListWouldRender() {
        if (this.client == null || this.client.player == null) return false;
        if (!this.client.isInSingleplayer()) return true;
        if (this.client.player.networkHandler != null
                && this.client.player.networkHandler.getListedPlayerListEntries().size() > 1) return true;
        if (this.client.world != null) {
            Scoreboard scoreboard = this.client.world.getScoreboard();
            if (scoreboard != null
                    && scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.LIST) != null) return true;
        }
        return false;
    }

    @Unique
    private void phaze$paintHotbarUtilityFills(
            DrawContext context,
            MaceIndicator mace,
            ItemHighlighter highlighter,
            HealingHelper healing
    ) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return;
        PlayerEntity player = mc.player;

        int screenW = context.getScaledWindowWidth();
        int screenH = context.getScaledWindowHeight();
        int centerX = screenW / 2;
        int itemY = screenH - 16 - 3;

        for (int n = 0; n < 9; n++) {

            ItemStack stack = player.getInventory().getMainStacks().get(n);
            int itemX = centerX - 90 + n * 20 + 2;
            if (mace != null) {
                phaze$fillHotbarOverlay(context, itemX, itemY, mace.colorForStack(stack));
            }
            if (highlighter != null) {
                phaze$fillHotbarOverlay(context, itemX, itemY, highlighter.colorForPreparedStack(stack));
            }
            if (healing != null) {
                phaze$fillHotbarOverlay(context, itemX, itemY, healing.colorForPreparedStack(stack));
            }
        }
        phaze$resetGuiRenderState();
    }

    @Unique
    private static void phaze$fillHotbarOverlay(DrawContext context, int x, int y, int color) {
        if ((color & 0xFF000000) != 0) {
            context.fill(x, y, x + 16, y + 16, color);
        }
    }

    @Inject(method = "renderHotbar", at = @At("TAIL"))
    private void phaze$flushHotbarBatch(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        phaze$resetGuiRenderState();
    }


    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void phaze$hideCrosshairInPhazeGui(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.currentScreen instanceof MenuScreen) {
            ci.cancel();
        }
    }

    @Redirect(
            method = "renderCrosshair",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/option/Perspective;isFirstPerson()Z")
    )
    private boolean phaze$forceFirstPersonForCrosshair(Perspective perspective) {
        Crosshair module = Crosshair.getInstance();
        if (module != null && module.isEnabled() && module.showInThirdPerson.isValue()) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null && mc.options != null && mc.options.hudHidden) {
                return perspective.isFirstPerson();
            }
            return true;
        }
        return perspective.isFirstPerson();
    }
}
