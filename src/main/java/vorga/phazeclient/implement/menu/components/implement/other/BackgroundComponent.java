package vorga.phazeclient.implement.menu.components.implement.other;

import vorga.phazeclient.base.util.render.GuiMatrix;

import org.joml.Matrix3x2fStack;
import org.joml.Matrix3x2fc;

import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import vorga.phazeclient.api.system.animation.Animation;
import vorga.phazeclient.api.system.animation.Direction;
import vorga.phazeclient.api.system.animation.implement.DecelerateAnimation;
import vorga.phazeclient.api.system.font.msdf.MsdfFonts;
import vorga.phazeclient.api.system.font.msdf.MsdfRenderer;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.base.util.Lang;
import vorga.phazeclient.base.util.math.MathUtil;
import vorga.phazeclient.implement.features.modules.client.Theme;
import vorga.phazeclient.implement.menu.MenuScreen;
import vorga.phazeclient.implement.menu.MenuLayout;
import vorga.phazeclient.implement.menu.MenuStyle;
import vorga.phazeclient.implement.menu.UiMsdfIconAtlas;
import vorga.phazeclient.implement.menu.components.AbstractComponent;
import vorga.phazeclient.implement.menu.components.implement.settings.SettingLabel;
import vorga.phazeclient.implement.config.ConfigManager;
import vorga.phazeclient.core.Main;

import java.util.HashMap;
import java.util.Map;

@Setter
@Accessors(chain = true)
public class BackgroundComponent extends AbstractComponent {
    private static final float TAB_TEXT_SIZE = 6.9F;
    private static final float FOOTER_TEXT_SIZE = 5.5F;
    private static final float CONFIG_TEXT_SIZE = 6.0F;
    private static final float HEADER_HEIGHT = 34f;
    private static final float TOP_TAB_HEIGHT = 17.0F;
    private static final float TOP_TAB_GAP = 8.0F;
    private static final float TOP_TAB_HORIZONTAL_PADDING = 19.0F;
    private static final float SIDEBAR_WIDTH = MenuLayout.SIDEBAR_WIDTH;
    private static final float CONFIG_ROW_HEIGHT = 17.5F;
    private static final float CONFIG_ROW_GAP = 1.0F;
    private static final float CONFIG_DELETE_WIDTH = 19.5F;
    private static final float CONFIG_DELETE_ICON_SIZE = 5.4F;
    private static final float CONFIG_TEXT_PADDING = 6.0F;
    private static final float CONFIG_SCROLL_STEP = 22.0F;
    private static final float FOOTER_BUTTON_HEIGHT = 15.0F;
    private static final float FOOTER_BUTTON_GAP = 14.0F;

    private final ConfigManager configManager = Main.getInstance().getConfigManager();
    private final Animation saveConfigHoverAnimation = new DecelerateAnimation().setMs(180).setValue(1);
    private final Animation editHudHoverAnimation = new DecelerateAnimation().setMs(180).setValue(1);
    private final Map<String, Animation> configHoverAnimations = new HashMap<>();
    private final Map<String, Animation> configDeleteHoverAnimations = new HashMap<>();
    private final Map<String, SettingLabel> configLabels = new HashMap<>();
    private final Map<String, Animation> topTabHoverAnimations = new HashMap<>();
    private SearchComponent searchComponent;

    private String editingConfigName = null;
    private String editingText = "";
    private long lastClickTime = 0;
    private String lastClickedConfig = null;
    private double configScroll;
    private double smoothedConfigScroll;

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {

        Theme.getInstance().syncLanguage();
        Matrix3x2fStack matrix = context.getMatrices();

        boolean configsOpen = MenuScreen.INSTANCE.isConfigsViewOpen();
        boolean cosmeticsOpen = MenuScreen.INSTANCE.isCosmeticsViewOpen();
        boolean fullWidth = configsOpen || cosmeticsOpen || MenuScreen.INSTANCE.isMessengerViewOpen();

        rectangle.render(ShapeProperties.create(matrix, x, y, width, height)
                .round(8).softness(1).thickness(2)
                .outlineColor(applyGlobalAlpha(MenuStyle.BORDER_LIGHT))
                .color(applyGlobalAlpha(MenuStyle.PANEL_BG))
                .build());

        rectangle.render(ShapeProperties.create(matrix, x + 1.0F, y + 1.0F, width - 2.0F, height - 2.0F)
                .round(7).color(applyGlobalAlpha(MenuStyle.PANEL_BG_SOFT)).build());

        rectangle.render(ShapeProperties.create(matrix, x + 1.0F, y + 1.0F, width - 2.0F, HEADER_HEIGHT)
                .round(7, 0, 7, 0).color(applyGlobalAlpha(MenuStyle.PANEL_HEADER)).build());

        if (!fullWidth) {
            rectangle.render(ShapeProperties.create(matrix, x + 1.0F, y + HEADER_HEIGHT + 1.0F, SIDEBAR_WIDTH - 1.0F, height - HEADER_HEIGHT - 2.0F)
                    .round(0, 0, 0, 7).color(applyGlobalAlpha(MenuStyle.PANEL_SIDEBAR)).build());

            rectangle.render(ShapeProperties.create(matrix, x + SIDEBAR_WIDTH, y + HEADER_HEIGHT + 1.0F, width - SIDEBAR_WIDTH - 1.0F, height - HEADER_HEIGHT - 2.0F)
                    .round(0, 7, 0, 0).color(applyGlobalAlpha(MenuStyle.PANEL_CONTENT)).build());

            rectangle.render(ShapeProperties.create(matrix, x + SIDEBAR_WIDTH, y + HEADER_HEIGHT + 1.0F, 1.0F, height - HEADER_HEIGHT - 2.0F)
                    .color(applyGlobalAlpha(MenuStyle.BORDER)).build());
        } else {

            rectangle.render(ShapeProperties.create(matrix, x + 1.0F, y + HEADER_HEIGHT + 1.0F, width - 2.0F, height - HEADER_HEIGHT - 2.0F)
                    .round(0, 7, 0, 7).color(applyGlobalAlpha(MenuStyle.PANEL_CONTENT)).build());
        }
        rectangle.render(ShapeProperties.create(matrix, x + 1.0F, y + HEADER_HEIGHT, width - 2.0F, 1.0F)
                .color(applyGlobalAlpha(MenuStyle.BORDER)).build());

        renderHeader(context, mouseX, mouseY);
        if (!fullWidth) {
            renderConfigs(context, mouseX, mouseY);
            renderSidebarFooter(context, mouseX, mouseY);
        }
    }

    private void renderHeader(DrawContext context, int mouseX, int mouseY) {
        String[] labels = {"MODS", "SETTINGS", "COSMETICS", "CONFIGS", "CHAT"};
        boolean settingsActive = isSettingsTabActive();
        boolean cosmeticsActive = isCosmeticsTabActive();
        boolean configsActive = isConfigsTabActive();
        boolean messengerActive = MenuScreen.INSTANCE.isMessengerViewOpen();
        boolean modsActive = !settingsActive && !cosmeticsActive && !configsActive && !messengerActive;
        boolean[] active = {modsActive, settingsActive, cosmeticsActive, configsActive, messengerActive};
        float totalTabsWidth = 0.0F;
        for (int i = 0; i < labels.length; i++) {
            totalTabsWidth += getTopTabWidth(labels[i]);
            if (i < labels.length - 1) {
                totalTabsWidth += TOP_TAB_GAP;
            }
        }

        if (configsActive) {
            totalTabsWidth += TOP_TAB_GAP + TOP_TAB_HEIGHT;
        }

        float tabsX = x + width / 2F - totalTabsWidth / 2.0F;
        for (int i = 0; i < labels.length; i++) {
            tabsX += drawTopTab(context, mouseX, mouseY, tabsX, labels[i], active[i]) + TOP_TAB_GAP;
        }

        if (configsActive) {
            drawImportPlus(context, mouseX, mouseY, tabsX);
        }
    }

    private float getTopTabWidth(String label) {
        return MsdfFonts.bold().getWidth(topTabText(label), TAB_TEXT_SIZE) + TOP_TAB_HORIZONTAL_PADDING;
    }

    private float drawTopTab(DrawContext context, int mouseX, int mouseY, float tabX, String label, boolean active) {
        Matrix3x2fStack matrix = context.getMatrices();
        float tabWidth = getTopTabWidth(label);
        float tabY = y + (HEADER_HEIGHT - TOP_TAB_HEIGHT) / 2.0F;
        boolean hovered = MathUtil.isHovered(mouseX, mouseY, tabX, tabY, tabWidth, TOP_TAB_HEIGHT);
        if (hovered) vorga.phazeclient.api.system.cursor.CursorManager.requestHand();
        Animation hoverAnimation = topTabHoverAnimations.computeIfAbsent(label, ignored -> new DecelerateAnimation().setMs(180).setValue(1));
        hoverAnimation.setDirection(hovered ? Direction.FORWARDS : Direction.BACKWARDS);

        float hoverProgress = hoverAnimation.getOutputFloat();
        float activeProgress = active ? 1.0F : 0.0F;

        int baseTabColor = MenuStyle.PANEL_CHIP;
        int hoverTabColor = MenuStyle.mix(baseTabColor, 0xFFFFFFFF, 0.08F);
        int activeTabColor = MenuStyle.mix(baseTabColor, 0xFFFFFFFF, 0.16F);
        int tabColor = MenuStyle.mix(MenuStyle.mix(baseTabColor, hoverTabColor, hoverProgress), activeTabColor, activeProgress);

        int baseBorderColor = MenuStyle.BORDER;
        int hoverBorderColor = MenuStyle.mix(MenuStyle.BORDER, MenuStyle.BORDER_LIGHT, 0.26F);
        int activeBorderColor = MenuStyle.mix(MenuStyle.BORDER, MenuStyle.BORDER_LIGHT, 0.38F);
        int borderColor = MenuStyle.mix(MenuStyle.mix(baseBorderColor, hoverBorderColor, hoverProgress), activeBorderColor, activeProgress);

        int baseTextColor = MenuStyle.TEXT_MUTED;
        int hoverTextColor = MenuStyle.mix(MenuStyle.TEXT_MUTED, MenuStyle.TEXT_PRIMARY, 0.45F);
        int activeTextColor = MenuStyle.TEXT_PRIMARY;
        int tabText = MenuStyle.mix(MenuStyle.mix(baseTextColor, hoverTextColor, hoverProgress), activeTextColor, activeProgress);

        rectangle.render(ShapeProperties.create(matrix, tabX, tabY, tabWidth, TOP_TAB_HEIGHT)
                .round(2).thickness(3.0F).outlineColor(applyGlobalAlpha(borderColor)).color(MenuStyle.withAlpha(tabColor, 0)).build());
        String shownLabel = topTabText(label);
        MsdfRenderer.renderText(
                MsdfFonts.bold(),
                shownLabel,
                TAB_TEXT_SIZE,
                applyGlobalAlpha(tabText),
                GuiMatrix.mat4(matrix),
                MenuStyle.centerMsdfTextX(MsdfFonts.bold(), shownLabel, TAB_TEXT_SIZE, tabX, tabWidth),
                MenuStyle.centerMsdfTextY(TAB_TEXT_SIZE, tabY, TOP_TAB_HEIGHT),
                0.0F
        );
        return tabWidth;
    }

    private void renderNewConfigButton(DrawContext context, int mouseX, int mouseY) {
        Matrix3x2fStack matrix = context.getMatrices();
        float buttonX = x + 4.0F;
        float buttonY = y + HEADER_HEIGHT + 7.0F;
        float buttonWidth = SIDEBAR_WIDTH - 8.0F;
        boolean hovered = MathUtil.isHovered(mouseX, mouseY, buttonX, buttonY, buttonWidth, FOOTER_BUTTON_HEIGHT);
        saveConfigHoverAnimation.setDirection(hovered ? Direction.FORWARDS : Direction.BACKWARDS);

        float hoverProgress = saveConfigHoverAnimation.getOutputFloat();

        rectangle.render(ShapeProperties.create(matrix, buttonX, buttonY, buttonWidth, FOOTER_BUTTON_HEIGHT)
                .round(2).thickness(2.0F)
                .outlineColor(MenuStyle.BORDER)
                .color(MenuStyle.mix(MenuStyle.PANEL_CHIP, MenuStyle.TEXT_PRIMARY, 0.06F * hoverProgress)).build());
        String label = Lang.t("sidebar.new_config");
        MsdfRenderer.renderText(
                MsdfFonts.bold(),
                label,
                FOOTER_TEXT_SIZE,
                MenuStyle.mix(MenuStyle.TEXT_MUTED, MenuStyle.TEXT_PRIMARY, 0.55F + hoverProgress * 0.45F),
                GuiMatrix.mat4(matrix),
                MenuStyle.centerMsdfTextX(MsdfFonts.bold(), label, FOOTER_TEXT_SIZE, buttonX, buttonWidth),
                MenuStyle.centerMsdfTextY(FOOTER_TEXT_SIZE, buttonY, FOOTER_BUTTON_HEIGHT),
                0.0F
        );
    }

    private void renderConfigs(DrawContext context, int mouseX, int mouseY) {
        Matrix3x2fStack matrix = context.getMatrices();
        String[] configs = configManager.getConfigList();
        float rowX = configRowX();
        float rowWidth = configRowWidth();
        float viewportY = configViewportY();
        float viewportHeight = configViewportHeight();
        double maxScroll = Math.max(0.0F, configContentHeight(configs.length) - viewportHeight);
        configScroll = Math.max(-maxScroll, Math.min(0.0D, configScroll));
        if (maxScroll <= 0.01D) {
            configScroll = 0.0D;
            smoothedConfigScroll = 0.0D;
        } else {
            smoothedConfigScroll = MathUtil.interpolateSmooth(3.5D, smoothedConfigScroll, configScroll);
            smoothedConfigScroll = Math.max(-maxScroll, Math.min(0.0D, smoothedConfigScroll));
            if (Math.abs(smoothedConfigScroll - configScroll) < 0.01D) smoothedConfigScroll = configScroll;
        }

        String currentConfig = configManager.getCurrentConfigName();
        var scissor = Main.getInstance().getScissorManager();
        scissor.push(GuiMatrix.mat4(matrix), rowX, viewportY, rowWidth, viewportHeight);
        try {
        for (int i = 0; i < configs.length; i++) {
            String config = configs[i];
            float rowY = viewportY + i * (CONFIG_ROW_HEIGHT + CONFIG_ROW_GAP) + (float) smoothedConfigScroll;
            if (rowY + CONFIG_ROW_HEIGHT < viewportY - 1.0F || rowY > viewportY + viewportHeight + 1.0F) {
                continue;
            }

            boolean inViewport = mouseY >= viewportY && mouseY < viewportY + viewportHeight;
            boolean rowHovered = inViewport && MathUtil.isHovered(mouseX, mouseY, rowX, rowY, rowWidth, CONFIG_ROW_HEIGHT);
            boolean deleteHovered = inViewport && isDeleteHovered(mouseX, mouseY, rowY);
            if (rowHovered) vorga.phazeclient.api.system.cursor.CursorManager.requestHand();
            boolean active = config.equalsIgnoreCase(currentConfig);
            Animation rowHoverAnimation = configHoverAnimation(config);
            Animation deleteHoverAnimation = configDeleteHoverAnimation(config);

            rowHoverAnimation.setDirection(rowHovered ? Direction.FORWARDS : Direction.BACKWARDS);
            deleteHoverAnimation.setDirection(deleteHovered ? Direction.FORWARDS : Direction.BACKWARDS);

            float rowHoverProgress = rowHoverAnimation.getOutputFloat();
            float deleteHoverProgress = deleteHoverAnimation.getOutputFloat();

            int baseRowColor = active ? MenuStyle.PANEL_ROW_ACTIVE : MenuStyle.PANEL_ROW;
            int rowColor = MenuStyle.mix(baseRowColor, MenuStyle.TEXT_PRIMARY, 0.07F * rowHoverProgress);
            int outlineColor = active
                    ? MenuStyle.mix(MenuStyle.BORDER_LIGHT, MenuStyle.CHIP_ACTIVE, 0.35F + rowHoverProgress * 0.08F)
                    : MenuStyle.mix(MenuStyle.BORDER, MenuStyle.BORDER_LIGHT, rowHoverProgress);

            rectangle.render(ShapeProperties.create(matrix, rowX, rowY, rowWidth, CONFIG_ROW_HEIGHT)
                    .round(1.5F).thickness(2.0F).outlineColor(applyGlobalAlpha(outlineColor)).color(applyGlobalAlpha(rowColor)).build());

            float labelRight = deleteSectionX(rowX, rowWidth) + (CONFIG_DELETE_WIDTH - CONFIG_DELETE_ICON_SIZE) / 2.0F - 3.0F;
            SettingLabel configLabel = configLabels.computeIfAbsent(config, ignored -> new SettingLabel());
            configLabel.updateHover(rowHovered);
            configLabel.renderMsdf(matrix, config, CONFIG_TEXT_SIZE, rowX + CONFIG_TEXT_PADDING,
                    MenuStyle.centerMsdfTextY(CONFIG_TEXT_SIZE, rowY, CONFIG_ROW_HEIGHT),
                    labelRight, rowY, CONFIG_ROW_HEIGHT, applyGlobalAlpha(MenuStyle.TEXT_PRIMARY));

            {
                float iconSize = CONFIG_DELETE_ICON_SIZE;
                float deleteX = deleteSectionX(rowX, rowWidth) + (CONFIG_DELETE_WIDTH - iconSize) / 2.0F;
                float iconY = rowY + (CONFIG_ROW_HEIGHT - iconSize) / 2.0F;
                int deleteColor = MenuStyle.mix(MenuStyle.TEXT_MUTED, 0xFFFFFFFF, deleteHoverProgress);

                renderConfigCross(matrix, deleteX, iconY, iconSize, applyGlobalAlpha(deleteColor));
            }

            if (editingConfigName != null && editingConfigName.equals(config)) {
                float textWidth = MsdfFonts.bold().getWidth(editingText + "_", CONFIG_TEXT_SIZE);
                rectangle.render(ShapeProperties.create(matrix, rowX, rowY, Math.max(rowWidth, textWidth + CONFIG_TEXT_PADDING * 2), CONFIG_ROW_HEIGHT)
                        .round(1.5F).thickness(2.0F).outlineColor(applyGlobalAlpha(MenuStyle.CHIP_ACTIVE)).color(applyGlobalAlpha(MenuStyle.PANEL_BG)).build());
                MsdfRenderer.renderText(
                        MsdfFonts.bold(),
                        editingText + "_",
                        CONFIG_TEXT_SIZE,
                        applyGlobalAlpha(MenuStyle.TEXT_PRIMARY),
                        GuiMatrix.mat4(context.getMatrices()),
                        rowX + CONFIG_TEXT_PADDING,
                        MenuStyle.centerMsdfTextY(CONFIG_TEXT_SIZE, rowY, CONFIG_ROW_HEIGHT),
                        0.0F
                );
            }

        }
        } finally {
            scissor.pop();
        }
    }

    private void renderSidebarFooter(DrawContext context, int mouseX, int mouseY) {
        Matrix3x2fStack matrix = context.getMatrices();
        float footerX = x + 4;
        float footerWidth = SIDEBAR_WIDTH - 8;
        float buttonGap = 4.0F;

        float newConfigY = footerStartY() - 5.0F - FOOTER_BUTTON_HEIGHT - buttonGap;
        boolean newConfigHovered = MathUtil.isHovered(mouseX, mouseY, footerX, newConfigY, footerWidth, FOOTER_BUTTON_HEIGHT);
        saveConfigHoverAnimation.setDirection(newConfigHovered ? Direction.FORWARDS : Direction.BACKWARDS);
        float newConfigHoverProgress = saveConfigHoverAnimation.getOutputFloat();

        rectangle.render(ShapeProperties.create(matrix, footerX, newConfigY, footerWidth, FOOTER_BUTTON_HEIGHT)
                .round(2).thickness(2.0F)
                .outlineColor(applyGlobalAlpha(MenuStyle.BORDER))
                .color(applyGlobalAlpha(MenuStyle.mix(MenuStyle.PANEL_CHIP, MenuStyle.TEXT_PRIMARY, 0.06F * newConfigHoverProgress))).build());
        String newConfigLabel = Lang.t("sidebar.new_config");
        MsdfRenderer.renderText(
                MsdfFonts.bold(),
                newConfigLabel,
                FOOTER_TEXT_SIZE,
                applyGlobalAlpha(MenuStyle.mix(MenuStyle.TEXT_MUTED, MenuStyle.TEXT_PRIMARY, 0.55F + newConfigHoverProgress * 0.45F)),
                GuiMatrix.mat4(matrix),
                MenuStyle.centerMsdfTextX(MsdfFonts.bold(), newConfigLabel, FOOTER_TEXT_SIZE, footerX, footerWidth),
                MenuStyle.centerMsdfTextY(FOOTER_TEXT_SIZE, newConfigY, FOOTER_BUTTON_HEIGHT),
                0.0F
        );

        float editHudY = footerStartY() - 5.0F;
        boolean editHovered = MathUtil.isHovered(mouseX, mouseY, footerX, editHudY, footerWidth, FOOTER_BUTTON_HEIGHT);
        editHudHoverAnimation.setDirection(editHovered ? Direction.FORWARDS : Direction.BACKWARDS);
        float editHoverProgress = editHudHoverAnimation.getOutputFloat();

        rectangle.render(ShapeProperties.create(matrix, footerX, editHudY, footerWidth, FOOTER_BUTTON_HEIGHT)
                .round(2).thickness(2.0F)
                .outlineColor(applyGlobalAlpha(MenuStyle.BORDER))
                .color(applyGlobalAlpha(MenuStyle.mix(MenuStyle.CHIP_ACTIVE, MenuStyle.TEXT_PRIMARY, 0.08F * editHoverProgress))).build());
        String editLabel = Lang.t("sidebar.edit_hud");
        MsdfRenderer.renderText(
                MsdfFonts.bold(),
                editLabel,
                FOOTER_TEXT_SIZE,
                applyGlobalAlpha(MenuStyle.mix(MenuStyle.TEXT_PRIMARY, MenuStyle.withAlpha(MenuStyle.TEXT_PRIMARY, 1.0F), editHoverProgress)),
                GuiMatrix.mat4(matrix),
                MenuStyle.centerMsdfTextX(MsdfFonts.bold(), editLabel, FOOTER_TEXT_SIZE, footerX, footerWidth),
                MenuStyle.centerMsdfTextY(FOOTER_TEXT_SIZE, editHudY, FOOTER_BUTTON_HEIGHT),
                0.0F
        );
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (handleTopTabClick(mouseX, mouseY)) {
            return true;
        }

        if (MenuScreen.INSTANCE.isConfigsViewOpen() || MenuScreen.INSTANCE.isCosmeticsViewOpen()
                || MenuScreen.INSTANCE.isMessengerViewOpen()) {
            return false;
        }

        float footerX = x + 4.0F;
        float footerWidth = SIDEBAR_WIDTH - 8.0F;
        float buttonGap = 4.0F;

        float newConfigY = footerStartY() - 5.0F - FOOTER_BUTTON_HEIGHT - buttonGap;
        if (MathUtil.isHovered(mouseX, mouseY, footerX, newConfigY, footerWidth, FOOTER_BUTTON_HEIGHT)) {
            playButtonClickSound();
            configManager.createNewConfig();
            return true;
        }

        float editHudY = footerStartY() - 5.0F;
        if (MathUtil.isHovered(mouseX, mouseY, footerX, editHudY, footerWidth, FOOTER_BUTTON_HEIGHT)) {
            playButtonClickSound();
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.player != null && client.world != null) {

                client.setScreen(new ChatScreen("", false));
            }
            return true;
        }

        float rowX = configRowX();
        float rowWidth = configRowWidth();
        float viewportY = configViewportY();
        float viewportBottom = viewportY + configViewportHeight();
        if (mouseY < viewportY || mouseY >= viewportBottom) return false;
        String[] configs = configManager.getConfigList();
        for (int i = 0; i < configs.length; i++) {
            String config = configs[i];
            float rowY = viewportY + i * (CONFIG_ROW_HEIGHT + CONFIG_ROW_GAP) + (float) smoothedConfigScroll;
            if (rowY + CONFIG_ROW_HEIGHT < viewportY || rowY > viewportBottom) {
                continue;
            }

            if (isDeleteHovered(mouseX, mouseY, rowY)) {

                playButtonClickSound();
                configManager.deleteConfig(config);
                return true;
            }

            if (MathUtil.isHovered(mouseX, mouseY, rowX, rowY, rowWidth, CONFIG_ROW_HEIGHT)) {
                long currentTime = System.currentTimeMillis();
                if (config.equals(lastClickedConfig) && (currentTime - lastClickTime) < 500) {

                    if (!config.equalsIgnoreCase("default")) {
                        editingConfigName = config;
                        editingText = config;
                        lastClickedConfig = null;
                        lastClickTime = 0;
                    }
                } else {

                    if (editingConfigName != null) {

                        if (!editingText.isEmpty() && !editingText.equals(editingConfigName)) {
                            configManager.renameConfig(editingConfigName, editingText);
                        }
                        editingConfigName = null;
                        editingText = "";
                    }
                    playButtonClickSound();
                    configManager.loadConfig(config);
                    lastClickedConfig = config;
                    lastClickTime = currentTime;
                }
                return true;
            }

        }

        if (editingConfigName != null) {
            editingConfigName = null;
            editingText = "";
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (editingConfigName != null) {
            if (keyCode == GLFW.GLFW_KEY_ENTER) {

                if (!editingText.isEmpty() && !editingText.equals(editingConfigName)) {
                    configManager.renameConfig(editingConfigName, editingText);
                }
                editingConfigName = null;
                editingText = "";
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {

                editingConfigName = null;
                editingText = "";
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {

                if (!editingText.isEmpty()) {
                    editingText = editingText.substring(0, editingText.length() - 1);
                }
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (editingConfigName != null) {

            if (Character.isLetterOrDigit(chr) || chr == '_' || chr == '-') {
                editingText += chr;
            }
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    public boolean isInteractiveHover(double mouseX, double mouseY) {
        if (isTopTabHovered(mouseX, mouseY)) {
            return true;
        }
        if (MenuScreen.INSTANCE.isConfigsViewOpen() || MenuScreen.INSTANCE.isCosmeticsViewOpen()
                || MenuScreen.INSTANCE.isMessengerViewOpen()) return false;

        float footerX = x + 4.0F;
        float footerWidth = SIDEBAR_WIDTH - 8.0F;
        float buttonGap = 4.0F;
        float newConfigY = footerStartY() - 5.0F - FOOTER_BUTTON_HEIGHT - buttonGap;
        float editHudY = footerStartY() - 5.0F;

        if (MathUtil.isHovered(mouseX, mouseY, footerX, newConfigY, footerWidth, FOOTER_BUTTON_HEIGHT)) {
            return true;
        }

        if (MathUtil.isHovered(mouseX, mouseY, footerX, editHudY, footerWidth, FOOTER_BUTTON_HEIGHT)) {
            return true;
        }

        float rowX = configRowX();
        float rowWidth = configRowWidth();
        float viewportY = configViewportY();
        float viewportBottom = viewportY + configViewportHeight();
        if (mouseY < viewportY || mouseY >= viewportBottom) return false;
        String[] configs = configManager.getConfigList();
        for (int i = 0; i < configs.length; i++) {
            float rowY = viewportY + i * (CONFIG_ROW_HEIGHT + CONFIG_ROW_GAP) + (float) smoothedConfigScroll;
            if (rowY + CONFIG_ROW_HEIGHT < viewportY || rowY > viewportBottom) {
                continue;
            }

            if (MathUtil.isHovered(mouseX, mouseY, rowX, rowY, rowWidth, CONFIG_ROW_HEIGHT) || isDeleteHovered(mouseX, mouseY, rowY)) {
                return true;
            }

        }

        return false;
    }

    private float footerStartY() {
        return y + height - 18.0F;
    }

    private float configRowX() {
        return x + 5.0F;
    }

    private float configRowStartY() {
        return y + HEADER_HEIGHT + 8.5F;
    }

    private float configViewportY() { return configRowStartY(); }

    private float configViewportHeight() {
        float newConfigY = footerStartY() - 5.0F - FOOTER_BUTTON_HEIGHT - 4.0F;
        return Math.max(0.0F, newConfigY - 4.0F - configViewportY());
    }

    private static float configContentHeight(int configCount) {
        return configCount <= 0 ? 0.0F : configCount * CONFIG_ROW_HEIGHT + (configCount - 1) * CONFIG_ROW_GAP;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (MenuScreen.INSTANCE.isConfigsViewOpen() || MenuScreen.INSTANCE.isCosmeticsViewOpen()
                || MenuScreen.INSTANCE.isMessengerViewOpen()
                || !MathUtil.isHovered(mouseX, mouseY, x, configViewportY(), SIDEBAR_WIDTH, configViewportHeight())) return false;
        double maxScroll = Math.max(0.0F, configContentHeight(configManager.getConfigList().length) - configViewportHeight());
        if (maxScroll <= 0.01D) return false;
        configScroll = Math.max(-maxScroll, Math.min(0.0D, configScroll + amount * CONFIG_SCROLL_STEP));
        return true;
    }

    private float configRowWidth() {
        return SIDEBAR_WIDTH - 8.0F;
    }

    private float deleteSectionX(float rowX, float rowWidth) {
        return rowX + rowWidth - CONFIG_DELETE_WIDTH;
    }

    private boolean isDeleteHovered(double mouseX, double mouseY, float rowY) {
        float iconSize = CONFIG_DELETE_ICON_SIZE;
        float deleteX = deleteSectionX(configRowX(), configRowWidth()) + (CONFIG_DELETE_WIDTH - iconSize) / 2.0F;
        float deleteY = rowY + (CONFIG_ROW_HEIGHT - iconSize) / 2.0F;
        return MathUtil.isHovered(mouseX, mouseY, deleteX, deleteY, iconSize, iconSize);
    }

    private Animation configHoverAnimation(String configName) {
        return configHoverAnimations.computeIfAbsent(configName, ignored -> new DecelerateAnimation().setMs(180).setValue(1));
    }

    private Animation configDeleteHoverAnimation(String configName) {
        return configDeleteHoverAnimations.computeIfAbsent(configName, ignored -> new DecelerateAnimation().setMs(160).setValue(1));
    }

    private boolean handleTopTabClick(double mouseX, double mouseY) {
        String[] labels = {"MODS", "SETTINGS", "COSMETICS", "CONFIGS", "CHAT"};
        boolean configsActive = isConfigsTabActive();
        float totalTabsWidth = 0.0F;
        for (int i = 0; i < labels.length; i++) {
            totalTabsWidth += getTopTabWidth(labels[i]);
            if (i < labels.length - 1) {
                totalTabsWidth += TOP_TAB_GAP;
            }
        }

        if (configsActive) {
            totalTabsWidth += TOP_TAB_GAP + TOP_TAB_HEIGHT;
        }

        float tabsX = x + width / 2F - totalTabsWidth / 2.0F;
        float tabY = y + (HEADER_HEIGHT - TOP_TAB_HEIGHT) / 2.0F;
        for (String label : labels) {
            float tabWidth = getTopTabWidth(label);
            if (MathUtil.isHovered(mouseX, mouseY, tabsX, tabY, tabWidth, TOP_TAB_HEIGHT)) {
                playButtonClickSound();
                MenuScreen.INSTANCE.requestTopTab(label);
                return true;
            }
            tabsX += tabWidth + TOP_TAB_GAP;
        }

        if (configsActive
                && MathUtil.isHovered(mouseX, mouseY, tabsX, tabY, TOP_TAB_HEIGHT, TOP_TAB_HEIGHT)) {
            playButtonClickSound();
            MenuScreen.INSTANCE.openConfigImportModal();
            return true;
        }
        return false;
    }

    private boolean isTopTabHovered(double mouseX, double mouseY) {
        String[] labels = {"MODS", "SETTINGS", "COSMETICS", "CONFIGS", "CHAT"};
        boolean configsActive = isConfigsTabActive();
        float totalTabsWidth = 0.0F;
        for (int i = 0; i < labels.length; i++) {
            totalTabsWidth += getTopTabWidth(labels[i]);
            if (i < labels.length - 1) {
                totalTabsWidth += TOP_TAB_GAP;
            }
        }
        if (configsActive) {
            totalTabsWidth += TOP_TAB_GAP + TOP_TAB_HEIGHT;
        }

        float tabsX = x + width / 2F - totalTabsWidth / 2.0F;
        float tabY = y + (HEADER_HEIGHT - TOP_TAB_HEIGHT) / 2.0F;
        for (String label : labels) {
            float tabWidth = getTopTabWidth(label);
            if (MathUtil.isHovered(mouseX, mouseY, tabsX, tabY, tabWidth, TOP_TAB_HEIGHT)) {
                return true;
            }
            tabsX += tabWidth + TOP_TAB_GAP;
        }
        if (configsActive
                && MathUtil.isHovered(mouseX, mouseY, tabsX, tabY, TOP_TAB_HEIGHT, TOP_TAB_HEIGHT)) {
            return true;
        }
        return false;
    }

    private void drawImportPlus(DrawContext context, int mouseX, int mouseY, float tabX) {
        Matrix3x2fStack matrix = context.getMatrices();
        float tabY = y + (HEADER_HEIGHT - TOP_TAB_HEIGHT) / 2.0F;
        boolean hovered = MathUtil.isHovered(mouseX, mouseY, tabX, tabY, TOP_TAB_HEIGHT, TOP_TAB_HEIGHT);
        Animation hoverAnimation = topTabHoverAnimations.computeIfAbsent("__plus__",
                ignored -> new DecelerateAnimation().setMs(180).setValue(1));
        hoverAnimation.setDirection(hovered ? Direction.FORWARDS : Direction.BACKWARDS);
        float hoverProgress = hoverAnimation.getOutputFloat();

        int baseTabColor = MenuStyle.PANEL_CHIP;
        int hoverTabColor = MenuStyle.mix(baseTabColor, 0xFFFFFFFF, 0.08F);
        int tabColor = MenuStyle.mix(baseTabColor, hoverTabColor, hoverProgress);

        int baseBorderColor = MenuStyle.BORDER;
        int hoverBorderColor = MenuStyle.mix(MenuStyle.BORDER, MenuStyle.BORDER_LIGHT, 0.45F);
        int borderColor = MenuStyle.mix(baseBorderColor, hoverBorderColor, hoverProgress);

        int baseIconColor = MenuStyle.TEXT_MUTED;
        int hoverIconColor = MenuStyle.mix(MenuStyle.TEXT_MUTED, MenuStyle.TEXT_PRIMARY, 0.55F);
        int iconColor = MenuStyle.mix(baseIconColor, hoverIconColor, hoverProgress);

        rectangle.render(ShapeProperties.create(matrix, tabX, tabY, TOP_TAB_HEIGHT, TOP_TAB_HEIGHT)
                .round(2).thickness(3.0F)
                .outlineColor(applyGlobalAlpha(borderColor))
                .color(MenuStyle.withAlpha(tabColor, 0))
                .build());

        float iconSize = TOP_TAB_HEIGHT * 0.50F;
        float chipCx = tabX + TOP_TAB_HEIGHT * 0.5F;
        float chipCy = tabY + TOP_TAB_HEIGHT * 0.5F;
        matrix.pushMatrix();

        matrix.rotateAbout((float) Math.toRadians(45.0F), chipCx, chipCy);
        image.setTexture("phaze:textures/menu/cross.png")
                .render(ShapeProperties.create(matrix,
                        chipCx - iconSize * 0.5F, chipCy - iconSize * 0.5F,
                        iconSize, iconSize)
                        .color(applyGlobalAlpha(iconColor))
                        .build());
        matrix.popMatrix();
    }

    private void renderConfigCross(Matrix3x2fc matrix, float x, float y, float size, int color) {
        net.minecraft.util.Identifier phaze$tex = Identifier.of("phaze", "textures/menu/cross.png");

        Matrix4f positionMatrix = GuiMatrix.mat4(matrix);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        buffer.vertex(positionMatrix, x, y, 0.0F).texture(0.0F, 0.0F).color(color);
        buffer.vertex(positionMatrix, x, y + size, 0.0F).texture(0.0F, 1.0F).color(color);
        buffer.vertex(positionMatrix, x + size, y + size, 0.0F).texture(1.0F, 1.0F).color(color);
        buffer.vertex(positionMatrix, x + size, y, 0.0F).texture(1.0F, 0.0F).color(color);

        vorga.phazeclient.api.system.draw.GuiProjection.begin();
        try {
            vorga.phazeclient.api.system.draw.PhazeDrawLayers.positionTexColor(phaze$tex).draw(buffer.end());
        } finally {
            vorga.phazeclient.api.system.draw.GuiProjection.end();
        }
    }

    private boolean isSettingsTabActive() {
        return MenuScreen.INSTANCE.getModuleDetailComponent().isOpen()
                && MenuScreen.INSTANCE.getModuleDetailComponent().getModule() == Theme.getInstance();
    }

    private boolean isConfigsTabActive() {
        return MenuScreen.INSTANCE.isConfigsViewOpen();
    }

    private boolean isCosmeticsTabActive() {
        return MenuScreen.INSTANCE.isCosmeticsViewOpen();
    }

    private static String topTabText(String id) {
        return id;
    }
}
