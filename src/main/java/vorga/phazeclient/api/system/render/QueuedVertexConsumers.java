package vorga.phazeclient.api.system.render;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Records vertices written during a deferred entity pass and replays them with
 * {@link OrderedRenderCommandQueue#submitCustom}. Positions are already transformed
 * by the caller's matrix, so the queued entry stays identity.
 */
public final class QueuedVertexConsumers implements VertexConsumerProvider {
    private static final MatrixStack IDENTITY = new MatrixStack();
    private static final ArrayDeque<QueuedVertexConsumers> POOL = new ArrayDeque<>();
    private final Map<RenderLayer, Batch> batches = new IdentityHashMap<>();

    private QueuedVertexConsumers() {}

    public static QueuedVertexConsumers borrow() {
        QueuedVertexConsumers next = POOL.pollFirst();
        if (next == null) next = new QueuedVertexConsumers();
        next.batches.clear();
        return next;
    }

    @Override
    public VertexConsumer getBuffer(RenderLayer layer) {
        return batches.computeIfAbsent(layer, ignored -> new Batch());
    }

    public void flush(OrderedRenderCommandQueue queue) {
        try {
            if (queue == null) return;
            for (Map.Entry<RenderLayer, Batch> entry : batches.entrySet()) {
                Batch batch = entry.getValue();
                if (batch.count == 0) continue;
                float[] vertices = Arrays.copyOf(batch.vertices, batch.count * Batch.STRIDE);
                int[] packed = Arrays.copyOf(batch.packed, batch.count * 4);
                int count = batch.count;
                queue.submitCustom(IDENTITY, entry.getKey(), (ignored, consumer) -> {
                    for (int i = 0; i < count; i++) {
                        int v = i * Batch.STRIDE;
                        int p = i * 4;
                        consumer.vertex(vertices[v], vertices[v + 1], vertices[v + 2])
                                .color(vertices[v + 3], vertices[v + 4], vertices[v + 5], vertices[v + 6])
                                .texture(vertices[v + 7], vertices[v + 8])
                                .overlay(packed[p], packed[p + 1])
                                .light(packed[p + 2], packed[p + 3])
                                .normal(vertices[v + 9], vertices[v + 10], vertices[v + 11]);
                    }
                });
            }
        } finally {
            batches.clear();
            POOL.addFirst(this);
        }
    }

    private static final class Batch implements VertexConsumer {
        private static final int STRIDE = 12;
        private float[] vertices = new float[256 * STRIDE];
        private int[] packed = new int[256 * 4];
        private int count;
        private float x, y, z, r = 1, g = 1, b = 1, a = 1, u, v;
        private int overlayU, overlayV, lightU, lightV;
        private boolean open;

        @Override
        public VertexConsumer vertex(float x, float y, float z) {
            if (open) commit(0, 1, 0);
            this.x = x;
            this.y = y;
            this.z = z;
            open = true;
            return this;
        }

        @Override
        public VertexConsumer color(float red, float green, float blue, float alpha) {
            r = red;
            g = green;
            b = blue;
            a = alpha;
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            return color(red / 255.0F, green / 255.0F, blue / 255.0F, alpha / 255.0F);
        }

        @Override
        public VertexConsumer color(int argb) {
            return color((argb >>> 16) & 255, (argb >>> 8) & 255, argb & 255, (argb >>> 24) & 255);
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            this.u = u;
            this.v = v;
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v) {
            overlayU = u;
            overlayV = v;
            return this;
        }

        @Override
        public VertexConsumer overlay(int uv) {
            return overlay(uv & 0xFFFF, uv >>> 16);
        }

        @Override
        public VertexConsumer light(int u, int v) {
            lightU = u;
            lightV = v;
            return this;
        }

        @Override
        public VertexConsumer light(int uv) {
            return light(uv & 0xFFFF, uv >>> 16);
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            if (open) commit(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer lineWidth(float width) {
            return this;
        }

        private void commit(float nx, float ny, float nz) {
            if (count * STRIDE + STRIDE > vertices.length) {
                vertices = Arrays.copyOf(vertices, vertices.length * 2);
                packed = Arrays.copyOf(packed, packed.length * 2);
            }
            int base = count * STRIDE;
            int p = count * 4;
            vertices[base] = x;
            vertices[base + 1] = y;
            vertices[base + 2] = z;
            vertices[base + 3] = r;
            vertices[base + 4] = g;
            vertices[base + 5] = b;
            vertices[base + 6] = a;
            vertices[base + 7] = u;
            vertices[base + 8] = v;
            vertices[base + 9] = nx;
            vertices[base + 10] = ny;
            vertices[base + 11] = nz;
            packed[p] = overlayU;
            packed[p + 1] = overlayV;
            packed[p + 2] = lightU;
            packed[p + 3] = lightV;
            count++;
            open = false;
        }
    }
}
