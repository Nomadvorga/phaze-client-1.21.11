package vorga.phazeclient.api.system.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.util.BufferAllocator;

/** Owned allocators: failed Phaze geometry cannot poison Minecraft's shared tessellator. */
public final class Render2DBuffers {
    private static BufferAllocator immediate, batch;
    private Render2DBuffers() {}
    public static BufferBuilder begin(VertexFormat format) {
        RenderSystem.assertOnRenderThread();
        if (immediate == null) immediate = new BufferAllocator(4096);
        return new BufferBuilder(immediate, VertexFormat.DrawMode.QUADS, format);
    }
    public static BufferBuilder beginBatch(VertexFormat format) {
        RenderSystem.assertOnRenderThread();
        if (batch == null) batch = new BufferAllocator(16 * 1024);
        return new BufferBuilder(batch, VertexFormat.DrawMode.QUADS, format);
    }
    public static void discardImmediate() { discard(immediate); }
    public static void discardBatch() { discard(batch); }
    private static void discard(BufferAllocator allocator) {
        if (allocator == null) return;
        try (var allocated = allocator.getAllocated()) {} finally { allocator.clear(); }
    }
    public static void cleanup() {
        RenderSystem.assertOnRenderThread();
        BufferAllocator a = immediate, b = batch;
        immediate = batch = null;
        try { if (a != null) a.close(); } finally { if (b != null) b.close(); }
    }
}
