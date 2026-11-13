package vorga.phazeclient.implement.features.modules.hud;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ClientBossBar;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ColorSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;
import java.util.Arrays;
import java.util.Collection;

/** Knobs and cached layout for the vanilla boss-bar draw path. */
public final class BossBarHud extends RectHudModule {
    private static final BossBarHud INSTANCE = new BossBarHud();
    public static final Text PREVIEW_NAME = Text.literal("Boss Bar");
    private static final long RAINBOW_CACHE_INTERVAL = 1_000_000_000L / 60;
    public final BooleanSetting renderHealth = new BooleanSetting("Render Health", "Show the boss health bar").setValue(true);
    public final BooleanSetting renderText = new BooleanSetting("Render Text", "Show the boss bar name").setValue(false);
    public final SectionSetting colorOptions = new SectionSetting("Color");
    public final SelectSetting colorMode = new SelectSetting("Mode", "Boss bar color mode") {
        @Override public boolean isFullWidth() { return isSelected("Rainbow"); }
    }.value("Custom", "Rainbow").selected("Custom");
    public final ColorSetting fillColor = new ColorSetting("Fill Color", "Boss bar fill color")
            .value(0xFFAA55FF).noAlpha().popupRow().visible(() -> colorMode.isSelected("Custom"));
    public final ValueSetting rainbowSpeed = new ValueSetting("Rainbow Speed", "Speed of the boss bar rainbow cycle")
            .range(0.1F, 1).step(0.05F).setValue(0.5F).visible(() -> colorMode.isSelected("Rainbow"));
    private Text[] measuredNames = new Text[8];
    private int[] measuredWidths = new int[8];
    private int baseWidth = 182, baseHeight = 5, nativeX, nativeY = 12;
    private boolean liveBars, positionInitialized, layoutDirty = true;
    private int layoutScreenWidth = -1, layoutScreenHeight = -1, layoutRows;
    private boolean layoutHealth, layoutText;
    private long lastHueNanos = -1;
    private float rainbowHue;
    private int activeFillColor = 0xFFAA55FF;
    private boolean cacheInitialized, lastEnabled, lastHealth, lastText, lastRainbow, lastChat;
    private float lastX, lastY, lastScale, lastGuiScale, lastRainbowSpeed;
    private int lastCustomColor;
    private long lastCacheNanos;
    private BossBarHud() {
        super("bossbar_tweaker", "Boss Bar", 0, 0, 1);
        settings().clear(); background.setValue(false); rainbowSpeed.setFullWidth(true);
        setup(mainSection, renderHealth, renderText, colorOptions, colorMode, fillColor, rainbowSpeed);
    }
    public static BossBarHud getInstance() { return INSTANCE; }
    @Override public String getDescription() { return "Customizable boss bar"; }
    @Override public String getIcon() { return "scoreboard_hud.png"; }
    @Override public float getRenderHudScale() { return getHudScale() * guiScale(); }
    public int getBaseWidth() { return baseWidth; }
    public int getBaseHeight() { return baseHeight; }
    public boolean hasLiveBars() { return liveBars; }
    public int getActiveFillColor() { return activeFillColor; }
    public void updateLayout(MinecraftClient client, Collection<ClientBossBar> bars) {
        int width = client.getWindow().getScaledWidth(), height = client.getWindow().getScaledHeight();
        int rows = Math.min(Math.max(1, bars.size()), Math.max(1, (height / 3 - 12 + 18) / 19));
        liveBars = !bars.isEmpty();
        boolean health = renderHealth.isValue(), text = renderText.isValue();
        boolean namesChanged = false;
        if (text && liveBars) {
            int i = 0;
            for (ClientBossBar bar : bars) {
                if (i >= rows) break;
                if (i >= measuredNames.length || measuredNames[i] != bar.getName()) { namesChanged = true; break; }
                i++;
            }
        }
        if (!layoutDirty && !namesChanged && width == layoutScreenWidth && height == layoutScreenHeight
                && rows == layoutRows && health == layoutHealth && text == layoutText) {
            updateDefaultPosition(width); return;
        }
        layoutDirty = false; layoutScreenWidth = width; layoutScreenHeight = height;
        layoutRows = rows; layoutHealth = health; layoutText = text; baseWidth = 182;
        if (text) {
            if (rows > measuredNames.length) {
                measuredNames = Arrays.copyOf(measuredNames, rows); measuredWidths = Arrays.copyOf(measuredWidths, rows);
            }
            int index = 0;
            if (liveBars) {
                for (ClientBossBar bar : bars) { measureName(client, index++, bar.getName()); if (index == rows) break; }
            } else measureName(client, 0, PREVIEW_NAME);
        }
        nativeX = width / 2 - baseWidth / 2; nativeY = text ? 3 : 12;
        baseHeight = (rows - 1) * 19 + (text ? (health ? 14 : 9) : 5);
        updateDefaultPosition(width);
    }
    private void updateDefaultPosition(int width) {
        if (!positionInitialized) {
            super.setHudX(Math.max(0, width / 2 * guiScale() - baseWidth * getRenderHudScale() / 2));
            super.setHudY(nativeY * guiScale());
        }
    }
    private void measureName(MinecraftClient client, int index, Text name) {
        if (measuredNames[index] != name) { measuredNames[index] = name; measuredWidths[index] = client.textRenderer.getWidth(name); }
        baseWidth = Math.max(baseWidth, (measuredWidths[index] + 3) & ~1);
    }
    public int getNameWidth(TextRenderer renderer, StringVisitable name) {
        for (int i = 0; i < layoutRows; i++) if (measuredNames[i] == name) return measuredWidths[i];
        return renderer.getWidth(name);
    }
    public void invalidateNameWidths() { Arrays.fill(measuredNames, null); layoutDirty = true; }
    public void applyTransform(DrawContext context) {
        float scale = guiScale();
        context.getMatrices().translate(getHudX() / scale, getHudY() / scale);
        context.getMatrices().scale(getHudScale(), getHudScale());
        context.getMatrices().translate(-nativeX, -nativeY);
    }
    public void beginFrame(long nowNanos) {
        MinecraftClient client = MinecraftClient.getInstance();
        boolean rainbow = isEnabled() && renderHealth.isValue() && colorMode.isSelected("Rainbow")
                && (liveBars || client != null && client.currentScreen instanceof ChatScreen);
        if (rainbow) {
            if (lastHueNanos >= 0) rainbowHue = advanceRainbowHue(rainbowHue, nowNanos - lastHueNanos, rainbowSpeed.getValue());
            lastHueNanos = nowNanos; activeFillColor = 0xFF000000 | MathHelper.hsvToRgb(rainbowHue, 1, 1);
        } else { lastHueNanos = -1; activeFillColor = fillColor.getColor() | 0xFF000000; }
    }
    static float rainbowCycleSeconds(float speed) {
        float value = MathHelper.clamp(speed, 0.1F, 1);
        return value <= 0.5F ? MathHelper.lerp((value - 0.1F) / 0.4F, 20, 5) : MathHelper.lerp((value - 0.5F) / 0.5F, 5, 1);
    }
    static float advanceRainbowHue(float hue, long elapsedNanos, float speed) {
        return (float) ((hue + Math.max(0L, elapsedNanos) / (1_000_000_000.0 * rainbowCycleSeconds(speed))) % 1);
    }
    public boolean shouldRefreshExordium(long nowNanos) {
        MinecraftClient client = MinecraftClient.getInstance();
        boolean enabled = isEnabled(), health = renderHealth.isValue(), text = renderText.isValue();
        boolean rainbow = colorMode.isSelected("Rainbow"), chat = client != null && client.currentScreen instanceof ChatScreen;
        float x = getHudX(), y = getHudY(), scale = getHudScale(), guiScale = guiScale(), speed = rainbowSpeed.getValue();
        int customColor = fillColor.getColor();
        boolean changed = !cacheInitialized || enabled != lastEnabled || health != lastHealth || text != lastText
                || rainbow != lastRainbow || chat != lastChat || x != lastX || y != lastY || scale != lastScale
                || guiScale != lastGuiScale || customColor != lastCustomColor || speed != lastRainbowSpeed;
        boolean animate = enabled && health && liveBars && rainbow && nowNanos - lastCacheNanos >= RAINBOW_CACHE_INTERVAL;
        cacheInitialized = true; lastEnabled = enabled; lastHealth = health; lastText = text;
        lastRainbow = rainbow; lastChat = chat; lastX = x; lastY = y; lastScale = scale;
        lastGuiScale = guiScale; lastCustomColor = customColor; lastRainbowSpeed = speed;
        if (changed || animate) lastCacheNanos = nowNanos;
        return changed || animate;
    }
    @Override public void setHudX(float x) { if (x != super.getHudX()) positionInitialized = true; super.setHudX(x); }
    @Override public void setHudY(float y) { if (y != super.getHudY()) positionInitialized = true; super.setHudY(y); }
    @Override public void setHudPosition(float x, float y, int width, int height) { positionInitialized = true; super.setHudPosition(x, y, width, height); }
    @Override public void setHudPositionRatio(float x, float y) { positionInitialized = true; super.setHudPositionRatio(x, y); }
    @Override public void resetHudTransform() { super.resetHudTransform(); positionInitialized = false; }
    public boolean isHudPositionAutomatic() { return !positionInitialized; }
    public void resetAutomaticPosition() { positionInitialized = false; }
    public static JsonObject migrateLegacyData(JsonObject source) {
        MinecraftClient client = MinecraftClient.getInstance();
        return migrateLegacyData(source, client == null || client.getWindow() == null ? 0 : client.getWindow().getWidth(),
                client == null || client.getWindow() == null ? 0 : client.getWindow().getHeight(), guiScale());
    }
    static JsonObject migrateLegacyData(JsonObject source, int width, int height, float guiScale) {
        boolean nested = source.has("settings") && source.get("settings").isJsonObject();
        JsonObject old = nested ? source.getAsJsonObject("settings") : source;
        if (!old.has("Scale") && !old.has("Y Offset") && !old.has("Hide Bossbar")) return source;
        JsonObject data = source.deepCopy(), settings = nested ? data.getAsJsonObject("settings") : data;
        float scale = MathHelper.clamp(legacyNumber(settings.get("Scale"), 1), 0.5F, MAX_HUD_SCALE);
        if (!data.has("hud_scale")) data.addProperty("hud_scale", scale);
        else scale = MathHelper.clamp(legacyNumber(data.get("hud_scale"), scale), 0.5F, MAX_HUD_SCALE);
        boolean hasPosition = data.has("hud_x_ratio") || data.has("hud_y_ratio") || data.has("hud_x") || data.has("hud_y")
                || data.has("hud_ref_w") || data.has("hud_ref_h") || data.has("hud_auto_position");
        if (!hasPosition && width > 0 && height > 0) {
            float offset = legacyNumber(settings.get("Y Offset"), 0);
            data.addProperty("hud_x", width * 0.5F - 91 * scale * guiScale);
            data.addProperty("hud_y", (12 * scale + offset) * guiScale);
            data.addProperty("hud_ref_w", width); data.addProperty("hud_ref_h", height); data.addProperty("hud_auto_position", false);
        }
        JsonElement hide = settings.get("Hide Bossbar");
        if (hide != null && hide.isJsonPrimitive() && hide.getAsJsonPrimitive().isBoolean()) {
            if (!settings.has("Render Health")) settings.addProperty("Render Health", !hide.getAsBoolean());
            if (hide.getAsBoolean() && !settings.has("Render Text")) settings.addProperty("Render Text", false);
        }
        settings.remove("Scale"); settings.remove("Y Offset"); settings.remove("Hide Bossbar");
        return data;
    }
    private static float legacyNumber(JsonElement value, float fallback) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return fallback;
        float number = value.getAsFloat(); return Float.isFinite(number) ? number : fallback;
    }
    private static float guiScale() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client == null || client.getWindow() == null ? 1 : (float) client.getWindow().getScaleFactor();
    }
}
