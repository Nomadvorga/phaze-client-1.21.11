package vorga.phazeclient.implement.features.modules.hud;

import net.minecraft.util.math.MathHelper;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ColorSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;
import vorga.phazeclient.api.system.hud.HudBuffer;
import vorga.phazeclient.api.system.hud.HudScaleLimits;
import vorga.phazeclient.implement.menu.MenuPalettes;

public final class ArmorHud extends Module {
    private static final ArmorHud INSTANCE = new ArmorHud();
    private static final float DEFAULT_HUD_X = 22.0f;
    private static final float DEFAULT_HUD_Y = 60.0f;
    private static final float DEFAULT_HUD_SCALE = 1.0f;

    public final SectionSetting mainSection = new SectionSetting("General");
    public final BooleanSetting textShadow = new BooleanSetting("Text Shadow", "Draw text with vanilla shadow").setValue(true);
    public final BooleanSetting background = new BooleanSetting("Background", "Draw scoreboard-style background").setValue(false);
    public final SelectSetting backgroundPreset = new SelectSetting("Background Preset", "Choose preset background style")
            .value(
                    "Vanilla",
                    "Lunar Blue",
                    "Mocha Gold",
                    "Rose Quartz",
                    "Emerald Frost",
                    "Arctic Mint",
                    "Crimson Silk",
                    "Solar Ember",
                    "Midnight Bloom",
                    "Desert Mirage",
                    "Sapphire Steel",
                    "Velvet Plum",
                    "Frosted Peach",
                    "Moss Smoke",
                    "Polar Night",
                    "Snow",
                    "Obsidian",
                    "Nebula",
                    "Coral",
                    "Jade",
                    "Sunset",
                    "Violet",
                    "Ocean",
                    "Custom Color",
                    "Gradient"
            )
            .selected("Vanilla")
            .visible(() -> background.isValue());
    public final ValueSetting colorBrightness = new ValueSetting("Color Brightness", "Adjust main color brightness")
            .range(0, 200)
            .setValue(100)
            .visible(() -> background.isValue() && !isVanillaPreset() && !isCustomColorPreset() && !isGradientPreset());
    public final ValueSetting backgroundOpacity = new ValueSetting("Background Opacity", "Custom background opacity")
            .range(0, 100)
            .setValue(30)
            .visible(() -> background.isValue() && !isVanillaPreset());
    public final ColorSetting customBackgroundColor = new ColorSetting("Custom Background Color", "Color used by the custom HUD background")
            .value(0xFF203447)
            .noAlpha()
            .popupRow()
            .visible(() -> background.isValue() && isCustomColorPreset());
    public final ColorSetting gradientStartColor = new ColorSetting("Gradient Start Color", "First color of the HUD gradient")
            .value(0xFF1B4965)
            .noAlpha()
            .popupRow()
            .visible(() -> background.isValue() && isGradientPreset());
    public final ColorSetting gradientEndColor = new ColorSetting("Gradient End Color", "Second color of the HUD gradient")
            .value(0xFF5FA8D3)
            .noAlpha()
            .popupRow()
            .visible(() -> background.isValue() && isGradientPreset());
    public final ValueSetting gradientSpeed = new ValueSetting("Gradient Speed", "How quickly the gradient flows between its colors")
            .range(0.0f, 4.0f)
            .step(0.05f)
            .setValue(0.75f)
            .visible(() -> background.isValue() && isGradientPreset());
    public final SelectSetting gradientDirection = new SelectSetting("Gradient Direction", "Direction of the animated HUD gradient")
            .value("Left to Right", "Right to Left", "Top to Bottom", "Bottom to Top", "Diagonal Down", "Diagonal Up", "Pulse")
            .selected("Left to Right")
            .visible(() -> background.isValue() && isGradientPreset());
    public final ValueSetting backgroundBlurRadius = new ValueSetting("Background Blur Radius", "Blur radius for HUD background")
            .range(0.0f, 32.0f)
            .step(0.25f)
            .setValue(0)
            .visible(() -> background.isValue());
    public final SectionSetting colorSection = new SectionSetting("Color Settings")
            .visible(() -> background.isValue());
    public final SectionSetting otherSection = new SectionSetting("Other");
    public final ValueSetting cornerRounding = new ValueSetting("Corner Rounding", "Round this HUD background's corners")
            .range(0.0F, 10.0F)
            .step(0.5F)
            .setValue(0.0F)
            .visible(() -> background.isValue());
    public final SelectSetting durabilityMode = new SelectSetting("Durability Mode", "How to display armor durability")
            .value("Units", "Percent")
            .selected("Units");

    public final BooleanSetting colorByDurability = new BooleanSetting(
            "Color By Durability",
            "Color the durability number red/yellow/green based on remaining armor percentage"
    ).setValue(false);

    private final HudBuffer hudBuffer = new HudBuffer();
    private long gradientLastUpdateNanos = -1L;
    private float gradientAnimationOffset = 0.0F;

    private float hudX = DEFAULT_HUD_X;
    private float hudY = DEFAULT_HUD_Y;
    private float hudScale = DEFAULT_HUD_SCALE;
    private int hudRefWidth = -1, hudRefHeight = -1;
    private float hudLayoutWidth, hudLayoutHeight;
    public void setHudLayoutSize(float width, float height) {
        hudLayoutWidth = Float.isFinite(width) ? Math.max(0, width) : 0;
        hudLayoutHeight = Float.isFinite(height) ? Math.max(0, height) : 0;
    }
    public float getHudLayoutWidth() { return hudLayoutWidth; }
    public float getHudLayoutHeight() { return hudLayoutHeight; }

    public static ArmorHud getInstance() {
        return INSTANCE;
    }

    private ArmorHud() {
        super("armor_hud", "Armor", ModuleCategory.HUD, true, false);
        mainSection.setFullWidth(true);
        textShadow.setFullWidth(true);
        background.setFullWidth(true);
        backgroundPreset.setFullWidth(true);
        colorBrightness.setFullWidth(true);
        backgroundOpacity.setFullWidth(true);
        customBackgroundColor.setFullWidth(true);
        gradientStartColor.setFullWidth(true);
        gradientEndColor.setFullWidth(true);
        gradientSpeed.setFullWidth(true);
        gradientDirection.setFullWidth(true);
        backgroundBlurRadius.setFullWidth(true);
        cornerRounding.setFullWidth(true);
        durabilityMode.setFullWidth(true);
        colorByDurability.setFullWidth(true);
        setup(mainSection, textShadow, background, colorSection, backgroundPreset, colorBrightness, backgroundOpacity,
                customBackgroundColor, gradientStartColor, gradientEndColor, gradientSpeed, gradientDirection, backgroundBlurRadius,
                otherSection, cornerRounding, durabilityMode, colorByDurability);
    }

    @Override
    public String getDescription() {
        return "Shows armor and durability on HUD";
    }

    @Override
    public String getIcon() {
        return "armor_hud.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    public HudBuffer getHudBuffer() {
        return hudBuffer;
    }

    public boolean hasActiveBackgroundBlur() {

        return background.isValue() && backgroundBlurRadius.getValue() > 0.0f;
    }

    public float getHudX() {
        resolveForCurrentScreen();
        return hudX;
    }

    public void setHudX(float hudX) {
        resolveForCurrentScreen();
        this.hudX = hudX;
        syncStoredHudRatios();
    }

    public float getHudY() {
        resolveForCurrentScreen();
        return hudY;
    }

    public void setHudY(float hudY) {
        resolveForCurrentScreen();
        this.hudY = hudY;
        syncStoredHudRatios();
    }

    public void resetHudTransform() {
        setHudLayoutSize(0, 0);
        this.hudX = DEFAULT_HUD_X;
        this.hudY = DEFAULT_HUD_Y;
        this.hudScale = DEFAULT_HUD_SCALE;
        syncStoredHudRatios();
    }

    public float getHudScale() {
        return hudScale;
    }

    public float getRenderHudScale() {
        return hudScale * HudScaleLimits.RENDER_MULTIPLIER;
    }

    public void setHudScale(float hudScale) {
        this.hudScale = HudScaleLimits.clamp(hudScale);
    }

    public float getMinHudScale() {
        return HudScaleLimits.MIN;
    }

    public float getMaxHudScale() {
        return HudScaleLimits.MAX;
    }

    public String formatDurability(int remaining, int max) {
        if ("Percent".equalsIgnoreCase(durabilityMode.getSelected())) {
            int percent = max <= 0 ? 0 : MathHelper.clamp(Math.round((remaining * 100.0f) / max), 0, 100);
            return percent + "%";
        }
        return String.valueOf(remaining);
    }

    public int colorForDurability(int remaining, int max) {
        if (!colorByDurability.isValue()) {
            return 0xFFFFFFFF;
        }
        if (max <= 0) {
            return 0xFFFFFFFF;
        }
        float ratio = (float) remaining / (float) max;
        if (ratio > 0.75F) {
            return 0xFF55FF55;
        }
        if (ratio > 0.25F) {
            return 0xFFFFAA00;
        }
        return 0xFFFF5555;
    }

    public int getResolvedBackgroundColor(net.minecraft.client.MinecraftClient client) {
        if (isVanillaPreset()) {
            return client.options.getTextBackgroundColor(0.5F);
        }
        if (isCustomColorPreset()) {
            return applyColorOptions(customBackgroundColor.getColor());
        }
        if (isGradientPreset()) {
            return getResolvedGradientStartColor();
        }
        var palette = MenuPalettes.byName(backgroundPreset.getSelected());
        int presetColor = adjustBrightness(palette.chipActive(), colorBrightness.getValue() / 100.0f);
        int alpha = MathHelper.clamp(Math.round((backgroundOpacity.getValue() / 100.0f) * 255.0f), 0, 255);
        return (alpha << 24) | (presetColor & 0x00FFFFFF);
    }

    private boolean isVanillaPreset() {
        return "Vanilla".equalsIgnoreCase(backgroundPreset.getSelected());
    }

    public boolean isCustomColorPreset() {
        return "Custom Color".equalsIgnoreCase(backgroundPreset.getSelected());
    }

    public boolean isGradientPreset() {
        return "Gradient".equalsIgnoreCase(backgroundPreset.getSelected());
    }

    public boolean hasActiveAnimatedBackground() {
        return background.isValue() && isGradientPreset() && gradientSpeed.getValue() > 0.0F;
    }

    public int getResolvedGradientStartColor() {
        return applyColorOptions(gradientStartColor.getColor());
    }

    public int getResolvedGradientEndColor() {
        return applyColorOptions(gradientEndColor.getColor());
    }

    public String getGradientDirection() {
        return gradientDirection.getSelected();
    }

    public float getGradientAnimationOffset(long nowMs) {
        long nowNanos = System.nanoTime();
        if (gradientLastUpdateNanos < 0L) {
            gradientLastUpdateNanos = nowNanos;
            return gradientAnimationOffset;
        }
        float deltaSeconds = MathHelper.clamp((nowNanos - gradientLastUpdateNanos) / 1_000_000_000.0F, 0.0F, 0.10F);
        gradientLastUpdateNanos = nowNanos;
        float speed = Math.max(0.0F, gradientSpeed.getValue());
        gradientAnimationOffset = (gradientAnimationOffset + deltaSeconds * speed * 0.5F) % 1.0F;
        return gradientAnimationOffset;
    }

    public void writeGradientColors(int[] colors, long nowMs) {
        if (colors == null || colors.length < 4) {
            return;
        }
        if (!isGradientPreset()) {
            int color = getResolvedBackgroundColor(net.minecraft.client.MinecraftClient.getInstance());
            colors[0] = color;
            colors[1] = color;
            colors[2] = color;
            colors[3] = color;
            return;
        }
        int first = getResolvedGradientStartColor();
        int second = getResolvedGradientEndColor();
        int midpoint = blend(first, second, 0.5F);
        switch (gradientDirection.getSelected()) {
            case "Right to Left" -> setGradientCorners(colors, second, second, first, first);
            case "Top to Bottom" -> setGradientCorners(colors, first, second, first, second);
            case "Bottom to Top" -> setGradientCorners(colors, second, first, second, first);
            case "Diagonal Down" -> setGradientCorners(colors, first, midpoint, midpoint, second);
            case "Diagonal Up" -> setGradientCorners(colors, midpoint, second, first, midpoint);
            case "Pulse" -> setGradientCorners(colors, first, first, first, first);
            default -> setGradientCorners(colors, first, first, second, second);
        }
    }

    private int applyColorOptions(int color) {
        int alpha = MathHelper.clamp(Math.round(((color >>> 24) & 0xFF) * (backgroundOpacity.getValue() / 100.0F)), 0, 255);
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private static void setGradientCorners(int[] colors, int topLeft, int bottomLeft, int topRight, int bottomRight) {
        colors[0] = topLeft;
        colors[1] = bottomLeft;
        colors[2] = topRight;
        colors[3] = bottomRight;
    }

    private static int blend(int first, int second, float progress) {
        float t = MathHelper.clamp(progress, 0.0F, 1.0F);
        int a = Math.round(((first >>> 24) & 0xFF) + (((second >>> 24) & 0xFF) - ((first >>> 24) & 0xFF)) * t);
        int r = Math.round(((first >>> 16) & 0xFF) + (((second >>> 16) & 0xFF) - ((first >>> 16) & 0xFF)) * t);
        int g = Math.round(((first >>> 8) & 0xFF) + (((second >>> 8) & 0xFF) - ((first >>> 8) & 0xFF)) * t);
        int b = Math.round((first & 0xFF) + ((second & 0xFF) - (first & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int adjustBrightness(int color, float multiplier) {
        int r = MathHelper.clamp(Math.round(((color >>> 16) & 0xFF) * multiplier), 0, 255);
        int g = MathHelper.clamp(Math.round(((color >>> 8) & 0xFF) * multiplier), 0, 255);
        int b = MathHelper.clamp(Math.round((color & 0xFF) * multiplier), 0, 255);
        return (r << 16) | (g << 8) | b;
    }

    public float getHudXRatio() {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            return 0.0f;
        }
        resolveForCurrentScreen();
        return MathHelper.clamp(hudX / Math.max(1.0f, client.getWindow().getWidth()), 0.0f, 1.0f);
    }

    public float getHudYRatio() {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            return 0.0f;
        }
        resolveForCurrentScreen();
        return MathHelper.clamp(hudY / Math.max(1.0f, client.getWindow().getHeight()), 0.0f, 1.0f);
    }

    public void setHudXRatio(float ratio) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client != null && client.getWindow() != null) {
            this.hudX = MathHelper.clamp(ratio, 0, 1) * Math.max(1, client.getWindow().getWidth());
            syncStoredHudRatios();
        }
    }

    public void setHudYRatio(float ratio) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client != null && client.getWindow() != null) {
            this.hudY = MathHelper.clamp(ratio, 0, 1) * Math.max(1, client.getWindow().getHeight());
            syncStoredHudRatios();
        }
    }

    private void syncStoredHudRatios() {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            return;
        }
        hudRefWidth = client.getWindow().getWidth();
        hudRefHeight = client.getWindow().getHeight();
    }
    private void resolveForCurrentScreen() {
        var client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) return;
        int width = client.getWindow().getWidth(), height = client.getWindow().getHeight();
        if (width <= 0 || height <= 0) return;
        if (hudRefWidth <= 0 || hudRefHeight <= 0) { syncStoredHudRatios(); return; }
        if (width == hudRefWidth && height == hudRefHeight) return;
        hudX = vorga.phazeclient.api.system.hud.HudPositionMath.rescale(hudX, hudRefWidth, width, hudLayoutWidth);
        hudY = vorga.phazeclient.api.system.hud.HudPositionMath.rescale(hudY, hudRefHeight, height, hudLayoutHeight);
        hudRefWidth = width; hudRefHeight = height;
    }
    public void setHudPosition(float x, float y, int refWidth, int refHeight) {
        hudX = x; hudY = y;
        if (refWidth > 0 && refHeight > 0) { hudRefWidth = refWidth; hudRefHeight = refHeight; }
        else syncStoredHudRatios();
    }
    public void setHudPositionRatio(float xRatio, float yRatio) {
        var client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) return;
        hudX = MathHelper.clamp(xRatio, 0, 1) * Math.max(1, client.getWindow().getWidth());
        hudY = MathHelper.clamp(yRatio, 0, 1) * Math.max(1, client.getWindow().getHeight());
        syncStoredHudRatios();
    }
    public int getHudRefWidth() { return hudRefWidth; }
    public int getHudRefHeight() { return hudRefHeight; }
    public void setHudReference(int width, int height) {
        hudRefWidth = width; hudRefHeight = height;
    }
}
