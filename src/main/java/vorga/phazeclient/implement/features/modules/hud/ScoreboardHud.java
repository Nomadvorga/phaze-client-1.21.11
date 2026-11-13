package vorga.phazeclient.implement.features.modules.hud;

import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;

public final class ScoreboardHud extends RectHudModule {
    private static final ScoreboardHud INSTANCE = new ScoreboardHud();

    @FunctionalInterface
    public interface BooleanLike {
        boolean isValue();
    }

    public static ScoreboardHud getInstance() {
        return INSTANCE;
    }

    private boolean positionInitialized;
    public boolean isPositionInitialized() { return positionInitialized; }
    public void setPositionInitialized(boolean value) { positionInitialized = value; }
    @Override public void setHudPosition(float x, float y, int width, int height) {
        super.setHudPosition(x, y, width, height); positionInitialized = true;
    }
    @Override public void setHudX(float x) { super.setHudX(x); positionInitialized = true; }
    @Override public void setHudY(float y) { super.setHudY(y); positionInitialized = true; }
    @Override public void setHudXRatio(float ratio) { super.setHudXRatio(ratio); positionInitialized = true; }
    @Override public void setHudYRatio(float ratio) { super.setHudYRatio(ratio); positionInitialized = true; }
    @Override public void setHudPositionRatio(float x, float y) { super.setHudPositionRatio(x, y); positionInitialized = true; }
    @Override public void resetHudTransform() { super.resetHudTransform(); positionInitialized = false; }
    public final BooleanLike showNumbers = () -> true;
    public final BooleanLike showZeros = () -> true;
    public final BooleanSetting showTitle = new BooleanSetting("Show Title", "Show scoreboard title").setValue(true);

    private ScoreboardHud() {

        super("scoreboard_hud", "Scoreboard", 0.0f, 0.0f, 1.0f);

        showTitle.setFullWidth(true);
        setup(showTitle);
    }

    @Override
    public String getDescription() {
        return "Modify vanilla scoreboard with blur, custom colors, position, and scale";
    }

    @Override
    public String getIcon() {
        return "scoreboard_hud.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    public boolean shouldUseVanillaColors() {
        return background.isValue() && isVanillaPreset();
    }
}
