package vorga.phazeclient.implement.menu.components.implement.other;

import net.minecraft.client.MinecraftClient;
import org.joml.Matrix3x2fc;
import org.lwjgl.glfw.GLFW;
import vorga.phazeclient.api.system.animation.Animation;
import vorga.phazeclient.api.system.animation.Direction;
import vorga.phazeclient.api.system.animation.implement.DecelerateAnimation;
import vorga.phazeclient.api.system.font.msdf.MsdfFonts;
import vorga.phazeclient.api.system.font.msdf.MsdfRenderer;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.base.QuickImports;
import vorga.phazeclient.base.util.render.GuiMatrix;
import vorga.phazeclient.implement.menu.MenuStyle;

import java.util.function.Predicate;

/** Shared one-line messenger/waypoint input, with code-point-safe editing and caret scrolling. */
final class MessengerTextField implements QuickImports {
    static final float HEIGHT = 18.0F;
    private static final float TEXT_SIZE = 6.6F;
    private static final float PAD = 7.0F;
    private static final float RADIUS = 4.0F;
    private static final int INVALID_COLOR = 0xFFE05050;

    private final int maxCodePoints;
    private final Predicate<Integer> accepts;
    private final Animation focusAnimation = new DecelerateAnimation().setMs(170).setValue(1);
    private String value = "";
    private int cursor;
    private boolean allSelected;
    private boolean focused;
    private boolean invalid;
    private float boxX, boxY, boxW;

    MessengerTextField(int maxCodePoints, Predicate<Integer> accepts) {
        this.maxCodePoints = maxCodePoints;
        this.accepts = accepts;
        focusAnimation.setDirectionAndFinish(Direction.BACKWARDS);
    }

    String value() { return value; }

    void setValue(String next) {
        value = next == null ? "" : next;
        cursor = value.length();
        allSelected = false;
    }

    boolean focused() { return focused; }

    void setFocused(boolean next) {
        focused = next;
        if (!next) allSelected = false;
    }

    void setInvalid(boolean next) { invalid = next; }

    void render(Matrix3x2fc matrices, float x, float y, float w, String placeholder, float alpha) {
        render(matrices, x, y, w, placeholder, null, alpha);
    }

    void render(Matrix3x2fc matrices, float x, float y, float w, String placeholder, String prefix, float alpha) {
        boxX = x;
        boxY = y;
        boxW = w;
        focusAnimation.setDirection(focused ? Direction.FORWARDS : Direction.BACKWARDS);
        float focus = focusAnimation.getOutputFloat();
        int border = invalid ? MenuStyle.mix(MenuStyle.BORDER, INVALID_COLOR, 0.8F)
                : MenuStyle.mix(MenuStyle.BORDER, MenuStyle.CHIP_ACTIVE, focus * 0.65F);
        rectangle.render(ShapeProperties.create(matrices, x, y, w, HEIGHT)
                .round(RADIUS).thickness(1.5F)
                .outlineColor(MenuStyle.withAlpha(border, alpha))
                .color(MenuStyle.withAlpha(MenuStyle.CARD_INNER, alpha)).build());

        var matrix = GuiMatrix.mat4(matrices);
        float textY = MenuStyle.centerMsdfTextY(TEXT_SIZE, y, HEIGHT);
        float textX = x + PAD;
        if (prefix != null && !prefix.isEmpty()) {
            MsdfRenderer.renderText(MsdfFonts.medium(), prefix, TEXT_SIZE,
                    MenuStyle.withAlpha(MenuStyle.TEXT_MUTED, alpha), matrix, textX, textY, 0.0F);
            textX += width(prefix) + 3.0F;
        }
        float available = Math.max(0.0F, x + w - PAD - textX);
        if (value.isEmpty()) {
            if (!focused && placeholder != null) {
                MsdfRenderer.renderText(MsdfFonts.medium(), placeholder, TEXT_SIZE,
                        MenuStyle.withAlpha(MenuStyle.TEXT_MUTED, alpha), matrix, textX, textY, 0.0F);
            }
        } else {
            int start = visibleStart(available);
            String visible = value.substring(start);
            while (!visible.isEmpty() && width(visible) > available) {
                visible = visible.substring(0, visible.offsetByCodePoints(visible.length(), -1));
            }
            if (allSelected && focused) {
                rectangle.render(ShapeProperties.create(matrices, textX, textY - 1.0F, width(visible), TEXT_SIZE + 2.0F)
                        .color(MenuStyle.withAlpha(MenuStyle.mix(MenuStyle.CHIP_ACTIVE, MenuStyle.TEXT_PRIMARY, 0.35F), alpha)).build());
            }
            MsdfRenderer.renderText(MsdfFonts.medium(), visible, TEXT_SIZE,
                    MenuStyle.withAlpha(MenuStyle.TEXT_PRIMARY, alpha), matrix, textX, textY, 0.0F);
        }
        if (focused && (System.currentTimeMillis() / 500L) % 2L == 0L) {
            int start = value.isEmpty() ? 0 : visibleStart(available);
            float caretX = textX + width(value.substring(start, Math.max(start, cursor)));
            rectangle.render(ShapeProperties.create(matrices, caretX, textY - 1.0F, 0.7F, TEXT_SIZE + 2.0F)
                    .color(MenuStyle.withAlpha(MenuStyle.TEXT_PRIMARY, alpha)).build());
        }
    }

    private int visibleStart(float available) {
        int start = 0;
        while (start < cursor && width(value.substring(start, cursor)) > available) {
            start = value.offsetByCodePoints(start, 1);
        }
        return start;
    }

    boolean contains(double mouseX, double mouseY) {
        return mouseX >= boxX && mouseX <= boxX + boxW && mouseY >= boxY && mouseY <= boxY + HEIGHT;
    }

    boolean mouseClicked(double mouseX, double mouseY) {
        boolean inside = contains(mouseX, mouseY);
        setFocused(inside);
        if (inside) cursor = value.length();
        return inside;
    }

    boolean keyPressed(int keyCode, int modifiers) {
        if (!focused) return false;
        boolean control = new net.minecraft.client.input.KeyInput(keyCode, 0, modifiers).hasCtrlOrCmd();
        if (control && keyCode == GLFW.GLFW_KEY_A) {
            allSelected = !value.isEmpty();
            cursor = value.length();
            return true;
        }
        if (control && keyCode == GLFW.GLFW_KEY_C) {
            if (allSelected) MinecraftClient.getInstance().keyboard.setClipboard(value);
            return true;
        }
        if (control && keyCode == GLFW.GLFW_KEY_V) {
            String clipboard = MinecraftClient.getInstance().keyboard.getClipboard();
            if (clipboard != null) insert(clipboard.replace('\r', ' ').replace('\n', ' '));
            return true;
        }
        switch (keyCode) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                invalid = false;
                if (allSelected) clear();
                else if (cursor > 0) {
                    int start = value.offsetByCodePoints(cursor, -1);
                    value = value.substring(0, start) + value.substring(cursor);
                    cursor = start;
                }
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                invalid = false;
                if (allSelected) clear();
                else if (cursor < value.length()) {
                    value = value.substring(0, cursor) + value.substring(value.offsetByCodePoints(cursor, 1));
                }
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                allSelected = false;
                if (cursor > 0) cursor = value.offsetByCodePoints(cursor, -1);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                allSelected = false;
                if (cursor < value.length()) cursor = value.offsetByCodePoints(cursor, 1);
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> { allSelected = false; cursor = 0; return true; }
            case GLFW.GLFW_KEY_END -> { allSelected = false; cursor = value.length(); return true; }
            default -> { return false; }
        }
    }

    boolean charTyped(char chr) { return charTyped((int) chr); }

    boolean charTyped(int codePoint) {
        if (!focused || !acceptsInputCodePoint(codePoint)) return false;
        insert(new String(Character.toChars(codePoint)));
        return true;
    }

    private void clear() { value = ""; cursor = 0; allSelected = false; }

    private void insert(String text) {
        invalid = false;
        if (allSelected) clear();
        StringBuilder filtered = new StringBuilder();
        text.codePoints().filter(this::acceptsInputCodePoint).forEach(filtered::appendCodePoint);
        String candidate = value.substring(0, cursor) + filtered + value.substring(cursor);
        if (candidate.codePointCount(0, candidate.length()) > maxCodePoints) return;
        value = candidate;
        cursor += filtered.length();
    }

    private boolean acceptsInputCodePoint(int codePoint) {
        return Character.isValidCodePoint(codePoint) && (codePoint < 0xD800 || codePoint > 0xDFFF)
                && !Character.isISOControl(codePoint) && accepts.test(codePoint);
    }

    private static float width(String text) {
        return MsdfFonts.medium().getWidth(text, TEXT_SIZE) + Math.max(0, text.length() - 1) * 0.025F * TEXT_SIZE;
    }
}
