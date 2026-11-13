package vorga.phazeclient.api.system.hud;

public interface ChatHudBadgeRenderAccess {
    void phaze$drawBadge(int y, float opacity, boolean codeBadge);

    default void phaze$recordElement(float x1, float y1, float x2, float y2,
                                     net.minecraft.client.gui.hud.ChatHudLine.Visible line) {}
}
