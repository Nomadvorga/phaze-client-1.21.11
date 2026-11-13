package vorga.phazeclient.api.system.font.glyph;

import it.unimi.dsi.fastutil.chars.Char2ObjectOpenHashMap;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;

/** Shared CPU metrics for measurement and atlas packing; measurement never touches textures. */
public final class FontGlyphMetrics {
    private static final FontRenderContext CONTEXT = new FontRenderContext(new AffineTransform(), true, false);
    private static Font[] fallbackFonts;
    private final Font font;
    private final Char2ObjectOpenHashMap<Metric> metrics = new Char2ObjectOpenHashMap<>();

    public FontGlyphMetrics(Font font) { this.font = font; }

    public synchronized Metric get(char value) {
        Metric cached = metrics.get(value);
        if (cached != null) return cached;
        Font selected = selectFont(value);
        var bounds = selected.getStringBounds(String.valueOf(value), CONTEXT);
        Metric result = new Metric(selected, (int) Math.ceil(bounds.getWidth()), (int) Math.ceil(bounds.getHeight()));
        metrics.put(value, result);
        return result;
    }

    private Font selectFont(char value) {
        if (font.canDisplay(value)) return font;
        for (Font fallback : fallbacks()) {
            if (fallback.canDisplay(value)) return new Font(fallback.getFontName(), Font.BOLD, font.getSize());
        }
        return new Font("SansSerif", Font.BOLD, 0);
    }

    private static synchronized Font[] fallbacks() {
        if (fallbackFonts == null) fallbackFonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts();
        return fallbackFonts;
    }

    public record Metric(Font font, int width, int height) {}
}
