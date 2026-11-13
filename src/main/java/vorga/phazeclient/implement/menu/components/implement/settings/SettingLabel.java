package vorga.phazeclient.implement.menu.components.implement.settings;

import org.joml.Matrix3x2fc;
import org.joml.Matrix4f;
import vorga.phazeclient.api.system.font.FontRenderer;
import vorga.phazeclient.api.system.font.msdf.MsdfFont;
import vorga.phazeclient.api.system.font.msdf.MsdfFonts;
import vorga.phazeclient.api.system.font.msdf.MsdfRenderer;
import vorga.phazeclient.base.util.render.GuiMatrix;
import vorga.phazeclient.base.util.render.ScissorManager;
import vorga.phazeclient.core.Main;
import vorga.phazeclient.implement.menu.font.MsdfGuiFontRenderer;

/** Single-pass, shader-faded one-line label with a parent-intersected viewport. */
public final class SettingLabel {
    private static final float LEFT_CLIP_BLEED = 2.0F;
    public interface Clip {
        void push(Matrix4f matrix, float x, float y, float width, float height);
        void pop();
    }
    private final SettingLabelState state = new SettingLabelState();
    private String source;
    private String line = "";
    private float animatedTextInset;
    private Clip clip;
    public void setAnimatedTextInset(float inset) { animatedTextInset = inset; }
    public SettingLabel clip(Clip clip) { this.clip = clip; return this; }
    public String singleLine(String text) {
        if (text == null) text = "";
        if (!text.equals(source)) {
            source = text;
            line = text.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ')
                    .replace('\u2028', ' ').replace('\u2029', ' ');
        }
        return line;
    }
    public void render(Matrix3x2fc matrices, FontRenderer font, String text, float textX, float textY,
                       float right, float top, float height, int color) {
        text = singleLine(text);
        if (font instanceof MsdfGuiFontRenderer gui) {
            render(matrices, gui.msdfFont(), text, gui.msdfSize(), font.getStringWidth(text),
                    textX, gui.msdfY(textY), right, top, height, color);
        } else {
            float size = font.getFont().getSize2D() / 2.0F;
            render(matrices, MsdfFonts.bold(), text, size, renderedWidth(text, size),
                    textX, textY, right, top, height, color);
        }
    }
    public void renderMsdf(Matrix3x2fc matrices, String text, float size, float textX, float textY,
                           float right, float top, float height, int color) {
        text = singleLine(text);
        render(matrices, MsdfFonts.bold(), text, size, renderedWidth(text, size), textX, textY, right, top, height, color);
    }
    private static float renderedWidth(String text, float size) {
        return MsdfFonts.bold().getWidth(text, size) + Math.max(0, text.length() - 1) * 0.025F * size;
    }
    private void render(Matrix3x2fc matrices, MsdfFont font, String text, float size, float textWidth,
                        float textX, float textY, float right, float top, float height, int color) {
        long now = System.nanoTime();
        state.layout(text, textWidth, right - textX, height, now, animatedTextInset);
        float width = state.getWidth();
        if (width <= 0.0F || height <= 0.0F) return;
        Matrix4f matrix = GuiMatrix.mat4(matrices);
        if (!state.isOverflowing()) {
            MsdfRenderer.renderText(font, text, size, color, matrix, textX, textY, 0);
            return;
        }
        float offset = state.offset(now), fade = state.fadeWidth();
        float leftStrength = state.edgeStrength(offset), rightStrength = state.edgeStrength(state.getOverflow() - offset);
        Clip activeClip = clip;
        ScissorManager scissor = activeClip == null ? Main.getInstance().getScissorManager() : null;
        float clipX = textX - LEFT_CLIP_BLEED, clipWidth = width + LEFT_CLIP_BLEED;
        if (activeClip != null) activeClip.push(matrix, clipX, top, clipWidth, height);
        else scissor.push(matrix, clipX, top, clipWidth, height);
        try {
            float scale = Math.abs(matrix.m00()), left = matrix.m00() * textX + matrix.m30();
            MsdfRenderer.renderTextFaded(font, text, size, color, matrix, textX - offset, textY, 0,
                    left, left + width * scale, fade * scale, leftStrength, rightStrength);
        } finally {
            if (activeClip != null) activeClip.pop(); else scissor.pop();
        }
    }
    public void updateHover(boolean cardHovered) { state.updateHover(cardHovered, System.nanoTime()); }
    public SettingLabelState state() { return state; }
}
