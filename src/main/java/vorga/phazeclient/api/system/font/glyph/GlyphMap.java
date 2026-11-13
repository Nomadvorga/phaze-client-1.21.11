package vorga.phazeclient.api.system.font.glyph;

import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.chars.Char2ObjectArrayMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;
import org.lwjgl.system.MemoryUtil;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.WritableRaster;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import static java.awt.RenderingHints.*;
import static net.minecraft.client.texture.NativeImage.Format.*;

public class GlyphMap implements AutoCloseable {
    private static final java.util.Set<GlyphMap> LIVE_ATLASES = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private final Char2ObjectArrayMap<Glyph> glyphs = new Char2ObjectArrayMap<>();
    private final int fromIncl, toExcl;
    private final FontGlyphMetrics metrics;
    public final Identifier bindToTexture;
    private final int pixelPadding;
    public int width, height;
    private boolean generated = false;

    public GlyphMap(char from, char to, Font font, Identifier identifier, int padding) {
        this(from, to == 0 && from != 0 ? 65536 : to, new FontGlyphMetrics(font), identifier, padding);
    }

    public GlyphMap(int from, int to, FontGlyphMetrics metrics, Identifier identifier, int padding) {
        this.fromIncl = from;
        this.toExcl = to;
        this.metrics = metrics;
        this.bindToTexture = identifier;
        this.pixelPadding = padding;
    }

    public Glyph getGlyph(char c) {
        if (!generated) generate();
        return glyphs.get(c);
    }

    public boolean contains(char c) {
        return c >= fromIncl && c < toExcl;
    }

    public void generate() {
        RenderSystem.assertOnRenderThread();
        if (generated) return;

        int range = toExcl - fromIncl - 1;
        int charsVert = (int) (Math.ceil(Math.sqrt(range)) * 1.5);
        glyphs.clear();

        int generatedChars = 0;
        int charNX = 0;
        int maxX = 0, maxY = 0;
        int currentX = 0, currentY = 0;
        int currentRowMaxY = 0;

        List<Glyph> glyphs1 = new ArrayList<>();

        while (generatedChars <= range) {
            char currentChar = (char) (fromIncl + generatedChars);
            FontGlyphMetrics.Metric metric = metrics.get(currentChar);
            int width = metric.width();
            int height = metric.height();
            generatedChars++;

            if (charNX >= charsVert) {
                currentX = 0;
                currentY += currentRowMaxY + pixelPadding;
                charNX = 0;
                currentRowMaxY = 0;
            }

            maxX = Math.max(maxX, currentX + width);
            maxY = Math.max(maxY, currentY + height);

            currentRowMaxY = Math.max(currentRowMaxY, height);
            glyphs1.add(new Glyph(currentX, currentY, width, height, currentChar, this));

            currentX += width + pixelPadding;
            charNX++;
        }

        BufferedImage bufferedImage = new BufferedImage(
                Math.max(maxX + pixelPadding, 1),
                Math.max(maxY + pixelPadding, 1),
                BufferedImage.TYPE_INT_ARGB
        );

        width = bufferedImage.getWidth();
        height = bufferedImage.getHeight();

        Graphics2D g2d = bufferedImage.createGraphics();
        try {
        g2d.setColor(new Color(255, 255, 255, 0));
        g2d.fillRect(0, 0, width, height);
        g2d.setColor(Color.WHITE);

        g2d.setRenderingHint(KEY_FRACTIONALMETRICS, VALUE_FRACTIONALMETRICS_OFF);
        g2d.setRenderingHint(KEY_ANTIALIASING, VALUE_ANTIALIAS_OFF);
        g2d.setRenderingHint(KEY_TEXT_ANTIALIASING, VALUE_TEXT_ANTIALIAS_ON);

        for (Glyph glyph : glyphs1) {
            g2d.setFont(metrics.get(glyph.value()).font());
            FontMetrics fontMetrics = g2d.getFontMetrics();
            g2d.drawString(String.valueOf(glyph.value()), glyph.u(), glyph.v() + fontMetrics.getAscent());
            glyphs.put(glyph.value(), glyph);
        }
        } finally { g2d.dispose(); }
        try {
            registerBufferedImageTexture(bindToTexture, bufferedImage);
            generated = true;
            LIVE_ATLASES.add(this);
        } finally { bufferedImage.flush(); }
    }

    public static void registerBufferedImageTexture(Identifier textureIdentifier, BufferedImage inputImage) {
        RenderSystem.assertOnRenderThread();
        int imageWidth = inputImage.getWidth();
        int imageHeight = inputImage.getHeight();

        NativeImage nativeImage = new NativeImage(RGBA, imageWidth, imageHeight, false);
        boolean transferred = false;
        try {
        IntBuffer buffer = MemoryUtil.memIntBuffer(nativeImage.pointer, nativeImage.getWidth() * nativeImage.getHeight());

        WritableRaster raster = inputImage.getRaster();
        ColorModel colorModel = inputImage.getColorModel();

        Object data = createDataArrayBasedOnRaster(raster);

        for (int y = 0; y < imageHeight; y++) {
            for (int x = 0; x < imageWidth; x++) {
                raster.getDataElements(x, y, data);
                int alpha = colorModel.getAlpha(data);
                int red = colorModel.getRed(data);
                int green = colorModel.getGreen(data);
                int blue = colorModel.getBlue(data);
                buffer.put(ColorHelper.getArgb(alpha, blue, green, red));
            }
        }

            transferred = true;
            uploadAndRegister(textureIdentifier, nativeImage);
        } finally { if (!transferred) nativeImage.close(); }
    }

    private static void uploadAndRegister(Identifier textureIdentifier, NativeImage nativeImage) {
        NativeImageBackedTexture texture;
        try { texture = new NativeImageBackedTexture(textureIdentifier::toString, nativeImage); }
        catch (RuntimeException | Error failure) { nativeImage.close(); throw failure; }
        try {
            texture.upload();
            MinecraftClient.getInstance().getTextureManager().registerTexture(textureIdentifier, texture);
        } catch (RuntimeException | Error failure) {
            try { texture.close(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    @Override public void close() {
        RenderSystem.assertOnRenderThread();
        if (!generated) return;
        generated = false;
        LIVE_ATLASES.remove(this);
        MinecraftClient.getInstance().getTextureManager().destroyTexture(bindToTexture);
    }

    public static void clearAll() {
        RenderSystem.assertOnRenderThread();
        Throwable failure = null;
        for (GlyphMap map : List.copyOf(LIVE_ATLASES)) {
            try { map.close(); }
            catch (RuntimeException | Error problem) {
                if (failure == null) failure = problem; else failure.addSuppressed(problem);
            }
        }
        if (failure instanceof Error error) throw error;
        if (failure instanceof RuntimeException exception) throw exception;
    }

    private static Object createDataArrayBasedOnRaster(WritableRaster raster) {
        return switch (raster.getDataBuffer().getDataType()) {
            case DataBuffer.TYPE_BYTE -> new byte[raster.getNumDataElements()];
            case DataBuffer.TYPE_USHORT -> new short[raster.getNumDataElements()];
            case DataBuffer.TYPE_INT -> new int[raster.getNumDataElements()];
            case DataBuffer.TYPE_FLOAT -> new float[raster.getNumDataElements()];
            case DataBuffer.TYPE_DOUBLE -> new double[raster.getNumDataElements()];
            default -> throw new IllegalArgumentException("Unsupported data buffer type: " + raster.getDataBuffer().getDataType());
        };
    }
}
