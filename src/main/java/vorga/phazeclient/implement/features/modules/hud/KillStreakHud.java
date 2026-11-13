package vorga.phazeclient.implement.features.modules.hud;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;

public class KillStreakHud extends RectHudModule {
    private static final KillStreakHud INSTANCE = new KillStreakHud();
    private static boolean registered;
    public final BooleanSetting playersOnly = new BooleanSetting("Players Only", "Count only player eliminations").setValue(true);
    public final ValueSetting posX = new ValueSetting("X Offset", "X screen coordinate").range(0, 1920).step(5).setValue(20);
    public final ValueSetting posY = new ValueSetting("Y Offset", "Y screen coordinate").range(0, 1080).step(5).setValue(120);
    private int streak;
    private LivingEntity pendingTarget;
    private long lastHitTime;
    private KillStreakHud() {
        super("killstreak_hud", "Killstreak", 20, 120, 1);
        setup(playersOnly, posX, posY);
        registerEvents();
    }
    public static KillStreakHud getInstance() { return INSTANCE; }
    @Override public String getDescription() { return "HUD counter tracking current and highest consecutive kill streaks"; }
    @Override public String getIcon() { return "combo_counter_hud.png"; }
    private synchronized void registerEvents() {
        if (registered) return;
        registered = true;
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient() && isEnabled() && entity instanceof LivingEntity living) {
                pendingTarget = living; lastHitTime = System.currentTimeMillis();
            }
            return ActionResult.PASS;
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) { pendingTarget = null; streak = 0; return; }
            if (!isEnabled()) { pendingTarget = null; return; }
            if (client.player.isDead() || client.player.getHealth() <= 0) { streak = 0; pendingTarget = null; return; }
            if (pendingTarget == null) return;
            if (System.currentTimeMillis() - lastHitTime > 2500) pendingTarget = null;
            else if (pendingTarget.isDead() || pendingTarget.getHealth() <= 0) {
                if (!playersOnly.isValue() || pendingTarget instanceof PlayerEntity) streak++;
                pendingTarget = null;
            }
        });
    }
    public void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!isEnabled() || client.player == null || client.options.hudHidden) return;
        String text = "Streak: " + streak;
        int x = (int) posX.getValue(), y = (int) posY.getValue();
        if (background.isValue()) context.fill(x, y, x + client.textRenderer.getWidth(text) + 8, y + 14, getResolvedBackgroundColor(client));
        context.drawText(client.textRenderer, text, x + 4, y + 3, 0xFFFFFFFF, textShadow.isValue());
    }
}
