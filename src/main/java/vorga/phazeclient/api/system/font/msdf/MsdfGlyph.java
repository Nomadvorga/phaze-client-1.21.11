package vorga.phazeclient.api.system.font.msdf;

import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.render.BufferBuilder;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;
import vorga.phazeclient.api.system.shape.batched.GuiBatch;

public final class MsdfGlyph {
    private final float minU;
    private final float maxU;
    private final float minV;
    private final float maxV;
    private final float advance;
    private final float topPosition;
    private final float width;
    private final float height;

    public MsdfGlyph(FontData.GlyphData data, float atlasWidth, float atlasHeight) {
        this.advance = data.advance();

        FontData.BoundsData atlasBounds = data.atlasBounds();
        if (atlasBounds != null) {
            this.minU = atlasBounds.left() / atlasWidth;
            this.maxU = atlasBounds.right() / atlasWidth;
            this.minV = 1.0F - atlasBounds.top() / atlasHeight;
            this.maxV = 1.0F - atlasBounds.bottom() / atlasHeight;
        } else {
            this.minU = this.maxU = this.minV = this.maxV = 0.0F;
        }

        FontData.BoundsData planeBounds = data.planeBounds();
        if (planeBounds != null) {
            this.width = planeBounds.right() - planeBounds.left();
            this.height = planeBounds.top() - planeBounds.bottom();
            this.topPosition = planeBounds.top();
        } else {
            this.width = this.height = this.topPosition = 0.0F;
        }
    }

    private MsdfGlyph(MsdfGlyph source, float raise) {
        this.minU = source.minU;
        this.maxU = source.maxU;
        this.minV = source.minV;
        this.maxV = source.maxV;
        this.advance = source.advance;
        this.width = source.width;
        this.height = source.height;
        this.topPosition = source.topPosition + raise;
    }

    /** The same glyph drawn {@code raise} em higher, for characters an atlas lacks. */
    MsdfGlyph raised(float raise) {
        return new MsdfGlyph(this, raise);
    }

    public float apply(Matrix4f matrix, BufferBuilder consumer, float size, float x, float y, float z, int color,
                       VertexFormatElement paramsElement, float range, float thickness, float smoothness) {
        if (width <= 0 || height <= 0) return advance * size;
        y -= this.topPosition * size;
        float scaledWidth = this.width * size;
        float scaledHeight = this.height * size;

        consumer.vertex(matrix, x, y, z).texture(this.minU, this.minV).color(color);
        writeParams(consumer, paramsElement, range, thickness, smoothness);
        consumer.vertex(matrix, x, y + scaledHeight, z).texture(this.minU, this.maxV).color(color);
        writeParams(consumer, paramsElement, range, thickness, smoothness);
        consumer.vertex(matrix, x + scaledWidth, y + scaledHeight, z).texture(this.maxU, this.maxV).color(color);
        writeParams(consumer, paramsElement, range, thickness, smoothness);
        consumer.vertex(matrix, x + scaledWidth, y, z).texture(this.maxU, this.minV).color(color);
        writeParams(consumer, paramsElement, range, thickness, smoothness);

        return this.advance * size;
    }

    /** {@link #apply} for a batch run. Glyphs without ink emit nothing. */
    float emit(Matrix4f matrix, GuiBatch.Run run, float size, float x, float y, float z, int color) {
        if (this.width > 0.0F && this.height > 0.0F) {
            run.quad(matrix, x, y - this.topPosition * size, this.width * size, this.height * size, z,
                    this.minU, this.minV, this.maxU, this.maxV, color, 0);
        }
        return this.advance * size;
    }

    private static void writeParams(BufferBuilder buffer, VertexFormatElement element,
                                    float range, float thickness, float smoothness) {
        long ptr = buffer.beginElement(element);
        MemoryUtil.memPutFloat(ptr, range);
        MemoryUtil.memPutFloat(ptr + 4L, thickness);
        MemoryUtil.memPutFloat(ptr + 8L, smoothness);
        MemoryUtil.memPutFloat(ptr + 12L, 0.0F);
    }

    public float getWidth(float size) {
        return this.advance * size;
    }

    public float getPlaneWidth() { return width; }
}
