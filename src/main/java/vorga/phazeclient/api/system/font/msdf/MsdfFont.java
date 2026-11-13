package vorga.phazeclient.api.system.font.msdf;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import it.unimi.dsi.fastutil.ints.Int2FloatMap;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import vorga.phazeclient.api.system.shape.batched.GuiBatch;

import java.util.HashMap;
import java.util.Map;

public final class MsdfFont {
    private static final MinecraftClient MC = MinecraftClient.getInstance();

    private final Identifier atlasIdentifier;
    private final FontData.AtlasData atlas;

    private final Int2ObjectMap<MsdfGlyph> glyphs;
    private final Int2ObjectMap<Int2FloatMap> kernings;
    private static final int DIRECT_LIMIT = 0x500;
    private final MsdfGlyph[] directGlyphs = new MsdfGlyph[DIRECT_LIMIT];
    private final Int2FloatMap[] directKernings = new Int2FloatMap[DIRECT_LIMIT];
    private final float planeTop, planeBottom;

    private record WidthKey(String text, float size) {}
    private final Map<WidthKey, Float> widthCache = new HashMap<>(64);

    private MsdfFont(Identifier atlasIdentifier, FontData.AtlasData atlas, Int2ObjectMap<MsdfGlyph> glyphs,
                     Int2ObjectMap<Int2FloatMap> kernings, float planeTop, float planeBottom) {
        this.atlasIdentifier = atlasIdentifier;
        this.atlas = atlas;
        this.glyphs = glyphs;
        this.kernings = kernings;
        this.planeTop = planeTop;
        this.planeBottom = planeBottom;
        for (int c = 0; c < DIRECT_LIMIT; c++) {
            directGlyphs[c] = glyphs.get(c);
            directKernings[c] = kernings.get(c);
        }
    }

    private MsdfGlyph glyph(int c) {
        MsdfGlyph glyph = c >= 0 && c < DIRECT_LIMIT ? directGlyphs[c] : null;
        return glyph != null ? glyph : glyphs.get(c);
    }
    private Int2FloatMap kerning(int c) { return c >= 0 && c < DIRECT_LIMIT ? directKernings[c] : kernings.get(c); }
    float planeTop() { return planeTop; }
    float planeBottom() { return planeBottom; }
    public void onResourcesReloaded() { widthCache.clear(); }

    public FontData.AtlasData getAtlas() {
        return atlas;
    }

    public com.mojang.blaze3d.textures.GpuTextureView getTextureView() {
        RenderSystem.assertOnRenderThread();
        return MinecraftClient.getInstance().getTextureManager().getTexture(atlasIdentifier).getGlTextureView();
    }

    public void applyGlyphs(Matrix4f matrix, net.minecraft.client.render.BufferBuilder consumer, String text, float size, float thickness, float spacing, float x, float y, float z, int color,
                            com.mojang.blaze3d.vertex.VertexFormatElement paramsElement, float range, float shaderThickness, float smoothness) {
        int previousChar = -1;
        for (int i = 0; i < text.length();) {
            int c = text.codePointAt(i);
            i += Character.charCount(c);
            MsdfGlyph glyph = glyph(c);
            if (glyph == null) {
                continue;
            }

            Int2FloatMap kerning = kerning(previousChar);
            if (kerning != null) {
                x += kerning.getOrDefault(c, 0.0F) * size;
            }

            x += glyph.apply(matrix, consumer, size, x, y, z, color, paramsElement, range, shaderThickness, smoothness) + thickness + spacing;
            previousChar = c;
        }
    }

    /** Appends one quad per visible glyph to the shared {@link GuiBatch} run. */
    public void applyGlyphs(Matrix4f matrix, GuiBatch.Run run, String text, float size, float thickness, float spacing,
                            float x, float y, float z, int color) {
        int previousChar = -1;
        for (int i = 0; i < text.length();) {
            int c = text.codePointAt(i);
            i += Character.charCount(c);
            MsdfGlyph glyph = glyph(c);
            if (glyph == null) continue;
            Int2FloatMap kerning = kerning(previousChar);
            if (kerning != null) x += kerning.getOrDefault(c, 0.0F) * size;
            x += glyph.emit(matrix, run, size, x, y, z, color) + thickness + spacing;
            previousChar = c;
        }
    }

    public float getWidth(String text, float size) {

        if (text == null || text.isEmpty()) {
            return 0.0F;
        }

        WidthKey key = new WidthKey(text, size);
        Float cached = widthCache.get(key);
        if (cached != null) {
            return cached;
        }

        int previousChar = -1;
        float width = 0.0F;

        for (int i = 0; i < text.length();) {
            int c = text.codePointAt(i);
            i += Character.charCount(c);
            MsdfGlyph glyph = glyph(c);
            if (glyph == null) {
                continue;
            }

            Int2FloatMap kerning = kerning(previousChar);
            if (kerning != null) {
                width += kerning.getOrDefault(c, 0.0F) * size;
            }

            width += glyph.getWidth(size);
            previousChar = c;
        }

        if (widthCache.size() >= 1024) {
            widthCache.clear();
        }
        widthCache.put(key, width);
        return width;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** CPU-only layout, including spacing between glyphs but no trailing gap. */
    public float getRenderedWidth(String text, float size, float thickness) {
        if (text == null || text.isEmpty()) return 0;
        int count = 0;
        for (int i = 0; i < text.length();) {
            int c = text.codePointAt(i);
            i += Character.charCount(c);
            if (glyph(c) != null) count++;
        }
        return MsdfTextMetrics.renderedWidth(getWidth(text, size), count, size, thickness);
    }

    public float getRenderedWidth(String text, float size) {
        return getRenderedWidth(text, size, MsdfTextMetrics.DEFAULT_THICKNESS);
    }

    public float getInkLeft(String text, float size) {
        return text == null || text.isEmpty() ? 0 : glyphPadding() * size - MsdfTextMetrics.PEN_OFFSET_X;
    }

    public float getInkRight(String text, float size) {
        if (text == null || text.isEmpty()) return 0;
        float pen = 0, lastPen = 0;
        int previous = -1;
        MsdfGlyph last = null;
        for (int i = 0; i < text.length();) {
            int c = text.codePointAt(i);
            i += Character.charCount(c);
            MsdfGlyph glyph = glyph(c);
            if (glyph == null) continue;
            Int2FloatMap kerning = kerning(previous);
            if (kerning != null) pen += kerning.getOrDefault(c, 0) * size;
            lastPen = pen;
            last = glyph;
            pen += glyph.getWidth(size) + MsdfTextMetrics.DEFAULT_GLYPH_SPACING * size;
            previous = c;
        }
        return last == null ? 0 : lastPen + (last.getPlaneWidth() - glyphPadding()) * size - MsdfTextMetrics.PEN_OFFSET_X;
    }

    public float getInkWidth(String text, float size) { return getInkRight(text, size) - getInkLeft(text, size); }
    private float glyphPadding() { return atlas.size() > 0 ? (atlas.range() * 0.5F + 0.5F) / atlas.size() : 0; }

    public static final class Builder {
        private Identifier dataIdentifier;
        private Identifier atlasIdentifier;

        public Builder data(String fileName) {
            this.dataIdentifier = Identifier.of("minecraft", "msdf/" + fileName + ".json");
            return this;
        }

        public Builder atlas(String fileName) {
            this.atlasIdentifier = Identifier.of("minecraft", "msdf/" + fileName + ".png");
            return this;
        }

        public MsdfFont build() {
            FontData data = ResourceProvider.fromJsonToInstance(dataIdentifier, FontData.class);
            float atlasWidth = data.atlas().width();
            float atlasHeight = data.atlas().height();
            Int2ObjectMap<MsdfGlyph> glyphs = new Int2ObjectOpenHashMap<>();
            float planeTop = 0, planeBottom = 0;
            for (var glyphData : data.glyphs()) {
                glyphs.put(glyphData.unicode(), new MsdfGlyph(glyphData, atlasWidth, atlasHeight));
                if (glyphData.planeBounds() != null) {
                    planeTop = Math.max(planeTop, glyphData.planeBounds().top());
                    planeBottom = Math.min(planeBottom, glyphData.planeBounds().bottom());
                }
            }

            addMiddleDot(data, glyphs);

            Int2ObjectMap<Int2FloatMap> kernings = new Int2ObjectOpenHashMap<>();
            for (var kerning : data.kernings()) {
                Int2FloatMap map = kernings.get(kerning.leftChar());
                if (map == null) {
                    map = new Int2FloatOpenHashMap();
                    kernings.put(kerning.leftChar(), map);
                }
                map.put(kerning.rightChar(), kerning.advance());
            }

            return new MsdfFont(atlasIdentifier, data.atlas(), glyphs, kernings, planeTop, planeBottom);
        }

        /**
         * The UI separates fields with " · ", which these atlases do not contain: without this it
         * renders as a plain double space. The period is lifted to the middle of the x-height.
         */
        private static void addMiddleDot(FontData data, Int2ObjectMap<MsdfGlyph> glyphs) {
            if (glyphs.containsKey(0xB7) || !glyphs.containsKey('.')) return;
            FontData.BoundsData period = null;
            FontData.BoundsData x = null;
            for (var glyphData : data.glyphs()) {
                if (glyphData.unicode() == '.') period = glyphData.planeBounds();
                else if (glyphData.unicode() == 'x') x = glyphData.planeBounds();
            }
            if (period == null || x == null) return;
            float raise = (x.top() + x.bottom()) / 2.0F - (period.top() + period.bottom()) / 2.0F;
            glyphs.put(0xB7, glyphs.get('.').raised(raise));
        }
    }
}
