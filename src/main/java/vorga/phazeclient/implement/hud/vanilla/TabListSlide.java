package vorga.phazeclient.implement.hud.vanilla;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import vorga.phazeclient.api.system.hud.ExordiumAnimationBridge;
import vorga.phazeclient.implement.features.modules.other.Animations;

/** Renders the closing slide after the tab key is released. */
public final class TabListSlide {
    private static boolean tabWasOpenedThisCycle;
    private TabListSlide() { }
    public static void tick(MinecraftClient client) {
        Animations module = Animations.getInstance();
        if (module == null || !module.isTabSlideEnabled()) { tabWasOpenedThisCycle = false; return; }
        if (client == null || client.options == null || client.player == null || client.world == null
                || client.options.hudHidden) {
            module.snapTabClosed(); tabWasOpenedThisCycle = false; return;
        }
        boolean pressed = client.options.playerListKey.isPressed();
        module.tickTabSlide(pressed);
        if (pressed && vanillaTabListWouldRender(client)) tabWasOpenedThisCycle = true;
    }
    public static void renderClosing(MinecraftClient client, PlayerListHud playerListHud, DrawContext context) {
        Animations module = Animations.getInstance();
        if (module == null || !module.isTabSlideEnabled() || client == null || client.options == null
                || client.player == null || client.world == null || client.options.hudHidden) {
            tabWasOpenedThisCycle = false; return;
        }
        if (client.options.playerListKey.isPressed()) return;
        if (!module.isTabSlideRendering(false)) { tabWasOpenedThisCycle = false; return; }
        if (!tabWasOpenedThisCycle || ExordiumAnimationBridge.renderClosingPlayerListFromCache()) return;
        Scoreboard scoreboard = client.world.getScoreboard();
        ScoreboardObjective objective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.LIST);
        playerListHud.setVisible(true);
        try {
            playerListHud.render(context, context.getScaledWindowWidth(), scoreboard, objective);
        } finally {
            playerListHud.setVisible(false);
        }
    }
    private static boolean vanillaTabListWouldRender(MinecraftClient client) {
        if (client.player == null) return false;
        if (!client.isInSingleplayer()) return true;
        if (client.player.networkHandler != null && client.player.networkHandler.getListedPlayerListEntries().size() > 1) return true;
        return client.world != null && client.world.getScoreboard().getObjectiveForSlot(ScoreboardDisplaySlot.LIST) != null;
    }
}
