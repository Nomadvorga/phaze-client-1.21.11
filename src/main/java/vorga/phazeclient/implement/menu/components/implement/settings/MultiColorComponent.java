package vorga.phazeclient.implement.menu.components.implement.settings;

import org.joml.Matrix3x2fStack;

import net.minecraft.client.gui.DrawContext;
import vorga.phazeclient.api.feature.module.setting.implement.MultiColorSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ColorSetting;
import vorga.phazeclient.implement.menu.font.GuiFonts;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.base.util.math.MathUtil;
import vorga.phazeclient.implement.menu.MenuScreen;
import vorga.phazeclient.implement.menu.MenuStyle;
import vorga.phazeclient.implement.menu.components.implement.window.AbstractWindow;
import vorga.phazeclient.implement.menu.components.implement.window.implement.settings.color.SettingColorPickerWindow;

import static vorga.phazeclient.api.system.font.Fonts.Type.*;

public class MultiColorComponent extends AbstractSettingComponent {
    private final MultiColorSetting setting;

    public MultiColorComponent(MultiColorSetting setting) {
        super(setting);
        this.setting = setting;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        updateVisibilityAnimation();
        var labelFont = GuiFonts.getSize(14, INTER_BOLD);

        boolean isModified = setting.isModified();
        float textOffset = animatedTextOffset(isModified);

        if (!setting.isVisible()) {
            return;
        }

        Matrix3x2fStack matrix = context.getMatrices();

        int colorCount = setting.getColorCount();
        height = ROW_HEIGHT;
        float hoverProgress = animatedCardHover(MathUtil.isHovered(mouseX, mouseY, x, y, width, height));

        resetIcon.position(x, y, height).alpha(currentAlpha).modified(isModified).render(context);
        renderSettingCard(context, 0.0f, hoverProgress);

        float textX = x + 10 + textOffset;
        String text = label.singleLine(setting.getLocalizedName());
        float firstColorX = x + width - 15.5F - Math.max(0, colorCount - 1) * 9.0F;
        label.render(matrix, labelFont, text, textX, centeredTextY(labelFont, text),
                firstColorX - 8.0F, y, height, primaryText());
        updateLabelHover(mouseX, mouseY);

        for (int i = 0; i < colorCount; i++) {
            ColorSetting colorSetting = setting.getColor(i);
            if (colorSetting != null) {
                float colorX = x + width - 15.5f - ((colorCount - 1 - i) * 9);
                float colorY = y + height / 2 - 3.5f;
                if (MathUtil.isHovered(mouseX, mouseY, colorX, colorY, 7, 7)) {
                    vorga.phazeclient.api.system.cursor.CursorManager.requestHand();
                }

                rectangle.render(ShapeProperties.create(matrix, colorX, colorY, 7, 7)
                        .round(3.5F)
                        .thickness(1.2F)
                        .softness(1)
                        .outlineColor(MenuStyle.BORDER_LIGHT)
                        .color(colorSetting.getColor())
                        .build());
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!setting.isVisible()) {
            return false;
        }

        if (button == 0 && resetIcon.isHovered(mouseX, mouseY)) {
            playButtonClickSound();
            setting.reset();
            return true;
        }

        int colorCount = setting.getColorCount();

        for (int i = 0; i < colorCount; i++) {
            float colorX = x + width - 15.5f - ((colorCount - 1 - i) * 9);
            float colorY = y + height / 2 - 3.5f;

            if (MathUtil.isHovered(mouseX, mouseY, colorX, colorY, 7, 7)) {
                if (button == 0) {
                    playButtonClickSound();
                    setting.setSelectedColorIndex(i);
                    ColorSetting selectedColor = setting.getColor(i);

                    if (selectedColor != null) {
                        AbstractWindow existingWindow = null;

                        for (AbstractWindow window : windowManager.getWindows()) {
                            if (window instanceof SettingColorPickerWindow) {
                                existingWindow = window;
                                break;
                            }
                        }

                        if (existingWindow != null) {
                            windowManager.delete(existingWindow);
                        } else {

                            int windowWidth = Math.round(SettingColorPickerWindow.WINDOW_WIDTH);
                            int windowHeight = Math.round(SettingColorPickerWindow.getWindowHeight(selectedColor));

                            int windowX = (int) (mouseX + 10);
                            int windowY = (int) (mouseY + 10);

                            windowX = MenuScreen.INSTANCE.clampOverlayX(windowX, windowWidth);
                            windowY = MenuScreen.INSTANCE.clampOverlayY(windowY, windowHeight);

                            AbstractWindow colorWindow = new SettingColorPickerWindow(selectedColor)
                                    .position(windowX, windowY)
                                    .size(windowWidth, windowHeight)
                                    .draggable(false);

                            windowManager.add(colorWindow);
                        }
                    }
                    return true;
                } else if (button == 1) {
                    playButtonClickSound();
                    setting.setSelectedColorIndex(i);
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isHover(double mouseX, double mouseY) {
        if (!setting.isVisible()) {
            return false;
        }

        int colorCount = setting.getColorCount();
        for (int i = 0; i < colorCount; i++) {
            float colorX = x + width - 15.5f - ((colorCount - 1 - i) * 9);
            float colorY = y + height / 2 - 3.5f;
            if (MathUtil.isHovered(mouseX, mouseY, colorX, colorY, 7, 7)) {
                return true;
            }
        }
        if (MathUtil.isHovered(mouseX, mouseY, x + 9, y + 6, width - colorCount * 9 - 18, height - 12)) {
            return true;
        }
        return false;
    }
}
