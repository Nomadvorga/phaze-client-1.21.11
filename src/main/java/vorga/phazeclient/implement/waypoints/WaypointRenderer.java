package vorga.phazeclient.implement.waypoints;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import vorga.phazeclient.api.system.render.Render3DUtil;
import vorga.phazeclient.api.system.render.WorldOverlayRenderer;
import vorga.phazeclient.base.util.MessengerPrefs;
import vorga.phazeclient.base.util.MessengerService;
import vorga.phazeclient.implement.features.modules.other.Waypoints;
import vorga.phazeclient.implement.messenger.ChatWorld;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws every world marker in one late overlay pass: saved waypoints, the
 * death point, shared chat points and messenger pings. All of it is see-through
 * by design. Geometry goes into the shared world-overlay batches (quads and
 * lines); label text goes to {@link WorldOverlayRenderer#FADING_TEXT}, drawn over
 * both, so it fades exactly like its frame. Marker objects are pooled and label
 * strings are rebuilt only when the shown metre count changes.
 *
 * <p>Points beyond the render distance are drawn scaled toward the camera by
 * the same factor in every axis, which leaves their projection unchanged: the
 * beam and label stay visible over unloaded chunks and past the far plane.</p>
 */
public final class WaypointRenderer {
    private static final float FILL_ALPHA = 0.5F;
    private static final float BEAM_ALPHA = 0.5F;
    /** Beam and label fade in between these camera distances, on a smoothstep curve. */
    private static final double FADE_START = 5.0D;
    private static final double FADE_END = 15.0D;
    /** TextRenderer turns alpha below 4 into fully opaque, so fainter text is skipped. */
    private static final int MIN_TEXT_ALPHA = 4;
    /** Label text unit in blocks per block of distance: a constant on-screen size. */
    private static final float LABEL_SCALE = 0.0032F;
    private static final float LABEL_NEAR_DISTANCE = 6.0F;
    private static final float LABEL_PAD_X = 3.0F;
    private static final float LABEL_PAD_Y = 2.0F;
    private static final float LABEL_BORDER = 0.8F;
    private static final float LABEL_LIFT = 4.0F;
    private static final int LABEL_BACKGROUND = 0x33000000;
    /** Beam half width in blocks per block of distance, with a floor for close range. */
    private static final double BEAM_WIDTH = 0.0016D;
    private static final double BEAM_MIN_HALF = 0.05D;
    private static final double HIGHLIGHT_GROW = 0.002D;
    private static final float LINE_WIDTH = 2.0F;
    private static final Matrix4f IDENTITY = new Matrix4f();
    private static final int DEATH_FALLBACK = 0xFFFF3333;
    /** Chat cards sent before cards carried a colour. */
    private static final int SHARED_FALLBACK = 0xFF7FD3FF;

    private static final List<Mark> POOL = new ArrayList<>();
    private static Mark[] order = new Mark[16];
    private static int count;

    private static List<Waypoint> cachedWaypoints = List.of();
    private static String cachedWorld;
    private static String cachedDimension;
    private static int cachedVersion = -1;

    private WaypointRenderer() {
    }

    private static final class Mark {
        int x, y, z;
        int color;
        boolean text, highlight, beam, distance;
        String name;
        double rx, ry, rz, meters, scale;
        float fade;
        String labelName;
        int labelMeters = Integer.MIN_VALUE;
        boolean labelText, labelDistance;
        String label;
        Text labelTextObject;
        float labelWidth;
    }

    public static void render(MatrixStack matrices, Vec3d camera) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || camera == null) return;
        String dimension = ChatWorld.dimension();
        if (dimension == null) return;
        count = 0;
        collect(client, dimension);
        if (count == 0) return;

        double limit = Math.max(64.0D, client.gameRenderer.getViewDistanceBlocks());
        double px = client.player.getX(), py = client.player.getY(), pz = client.player.getZ();
        for (int index = 0; index < count; index++) {
            Mark mark = order[index];
            double cx = mark.x + 0.5D, cy = mark.y + 0.5D, cz = mark.z + 0.5D;
            double dx = cx - px, dy = cy - py, dz = cz - pz;
            mark.meters = Math.sqrt(dx * dx + dy * dy + dz * dz);
            mark.rx = cx - camera.x;
            mark.ry = cy - camera.y;
            mark.rz = cz - camera.z;
            double fromCamera = Math.sqrt(mark.rx * mark.rx + mark.ry * mark.ry + mark.rz * mark.rz);
            mark.scale = fromCamera > limit ? limit / fromCamera : 1.0D;
            // The camera position is interpolated every frame, so this ramps without tick steps.
            double t = Math.max(0.0D, Math.min(1.0D, (fromCamera - FADE_START) / (FADE_END - FADE_START)));
            mark.fade = (float) (t * t * (3.0D - 2.0D * t));
        }
        sortFarToNear();
        drawBeamsAndFills(client, camera);
        drawOutlines(matrices);
        // F1 hides the labels with the rest of the HUD; beams and blocks belong to the world.
        if (!client.options.hudHidden) drawLabels(client, matrices);
    }

    /* ---- gathering ---------------------------------------------------- */

    private static void collect(MinecraftClient client, String dimension) {
        Waypoints module = Waypoints.getInstance();
        if (module.isEnabled()) {
            String world = WaypointStore.currentWorldKey();
            WaypointStore store = WaypointStore.get();
            if (world != null) {
                if (store.version() != cachedVersion || !world.equals(cachedWorld) || !dimension.equals(cachedDimension)) {
                    cachedWaypoints = store.snapshot(world, dimension);
                    cachedVersion = store.version();
                    cachedWorld = world;
                    cachedDimension = dimension;
                }
                double max = module.maxDistance.getValue();
                double maxSq = max * max;
                double px = client.player.getX(), py = client.player.getY(), pz = client.player.getZ();
                for (Waypoint waypoint : cachedWaypoints) {
                    double dx = waypoint.blockX() + 0.5D - px, dy = waypoint.blockY() + 0.5D - py, dz = waypoint.blockZ() + 0.5D - pz;
                    if (dx * dx + dy * dy + dz * dz > maxSq) continue;
                    add(waypoint.blockX(), waypoint.blockY(), waypoint.blockZ(), waypoint.name(), waypoint.color(),
                            waypoint.showText(), waypoint.highlight(), waypoint.showBeam(), waypoint.showDistance());
                }
            }
        }

        String server = ChatWorld.serverKey();
        if (server == null) return;
        if (module.chatWaypoints.isValue()) {
            refreshShared(server, dimension);
            double max = module.maxDistance.getValue();
            double maxSq = max * max;
            double px = client.player.getX(), py = client.player.getY(), pz = client.player.getZ();
            for (SharedView view : sharedViews) {
                double dx = view.x() + 0.5D - px, dy = view.y() + 0.5D - py, dz = view.z() + 0.5D - pz;
                if (dx * dx + dy * dy + dz * dz > maxSq) continue;
                int bits = view.display();
                add(view.x(), view.y(), view.z(), view.name(), view.color(),
                        (bits & 1) != 0, (bits & 2) != 0, (bits & 4) != 0, (bits & 8) != 0);
            }
        }
        for (MessengerService.Ping ping : MessengerService.getInstance().getActivePings()) {
            if (!server.equals(ping.serverKey()) || !dimension.equals(ping.dimension())) continue;
            String name = ping.label() == null || ping.label().isBlank() ? ping.username() : ping.username() + " · " + ping.label();
            add(ping.x(), ping.y(), ping.z(), name,
                    MessengerService.isAdmin(ping.userId()) ? ChatWorld.ADMIN_PING_COLOR : ChatWorld.PING_COLOR,
                    true, true, true, true);
        }
    }

    /** A chat point card as the world shows it, resolved once per change instead of per frame. */
    private record SharedView(int x, int y, int z, String name, int color, int display) {}

    private static List<SharedView> sharedViews = List.of();
    private static String sharedServer;
    private static String sharedRealm;
    private static String sharedDimension;
    private static int sharedMessages = -1;
    private static int sharedHidden = -1;
    private static int sharedStore = -1;

    private static void refreshShared(String server, String dimension) {
        MessengerService service = MessengerService.getInstance();
        MessengerPrefs prefs = MessengerPrefs.get();
        WaypointStore store = WaypointStore.get();
        String realm = ChatWorld.realm();
        if (service.messagesVersion() == sharedMessages && prefs.hiddenVersion() == sharedHidden
                && store.version() == sharedStore && server.equals(sharedServer) && dimension.equals(sharedDimension)
                && java.util.Objects.equals(realm, sharedRealm)) {
            return;
        }
        sharedMessages = service.messagesVersion();
        sharedHidden = prefs.hiddenVersion();
        sharedStore = store.version();
        sharedServer = server;
        sharedRealm = realm;
        sharedDimension = dimension;
        List<SharedView> views = new ArrayList<>();
        for (MessengerService.SharedPoint point : service.sharedPoints()) {
            MessengerService.Message message = point.message();
            MessengerService.Attachment card = message.attachment();
            if (!server.equals(card.serverKey()) || !dimension.equals(card.dimension())) continue;
            if (card.realm() != null && !card.realm().equals(realm)) continue;
            if (prefs.isPointHidden(message.id()) || store.findByMessage(server, message.id()) != null) continue;
            int color = card.color() >= 0 ? 0xFF000000 | card.color()
                    : "death".equals(card.type()) ? DEATH_FALLBACK : SHARED_FALLBACK;
            views.add(new SharedView(card.x(), card.y(), card.z(), WaypointStore.cleanName(message.text()), color,
                    card.display() >= 0 ? card.display() : 15));
        }
        sharedViews = List.copyOf(views);
    }

    private static void add(int x, int y, int z, String name, int color,
                            boolean text, boolean highlight, boolean beam, boolean distance) {
        if (!text && !highlight && !beam && !distance) return;
        if (count == POOL.size()) POOL.add(new Mark());
        Mark mark = POOL.get(count);
        mark.x = x;
        mark.y = y;
        mark.z = z;
        mark.name = name == null ? "" : name;
        mark.color = color;
        mark.text = text;
        mark.highlight = highlight;
        mark.beam = beam;
        mark.distance = distance;
        if (order.length <= count) order = java.util.Arrays.copyOf(order, order.length * 2);
        order[count++] = mark;
    }

    /** Insertion sort: a handful of marks, nearly sorted between frames, no allocation. */
    private static void sortFarToNear() {
        for (int index = 1; index < count; index++) {
            Mark mark = order[index];
            int slot = index - 1;
            while (slot >= 0 && order[slot].meters < mark.meters) {
                order[slot + 1] = order[slot];
                slot--;
            }
            order[slot + 1] = mark;
        }
    }

    /* ---- geometry ----------------------------------------------------- */

    private static void drawBeamsAndFills(MinecraftClient client, Vec3d camera) {
        double top = client.world.getTopYInclusive() + 1.0D;
        BufferBuilder buffer = null;
        for (int index = 0; index < count; index++) {
            Mark mark = order[index];
            if (!mark.beam || mark.fade <= 0.0F || mark.y >= top) continue;
            if (buffer == null) buffer = quads();
            double s = mark.scale;
            double bx = mark.rx * s, bz = mark.rz * s;
            double horizontal = Math.sqrt(bx * bx + bz * bz);
            double half = Math.max(BEAM_MIN_HALF, horizontal * BEAM_WIDTH);
            double sx, sz;
            if (horizontal < 1.0E-4D) {
                sx = half;
                sz = 0.0D;
            } else {
                sx = -bz / horizontal * half;
                sz = bx / horizontal * half;
            }
            float y0 = (float) ((mark.y - camera.y) * s);
            float y1 = (float) ((top - camera.y) * s);
            int argb = withAlpha(mark.color, BEAM_ALPHA * mark.fade);
            quad(buffer, IDENTITY, (float) (bx - sx), y0, (float) (bz - sz), (float) (bx + sx), y0, (float) (bz + sz),
                    (float) (bx + sx), y1, (float) (bz + sz), (float) (bx - sx), y1, (float) (bz - sz), argb);
        }
        for (int index = 0; index < count; index++) {
            Mark mark = order[index];
            if (!mark.highlight || mark.scale < 1.0D) continue;
            if (buffer == null) buffer = quads();
            box(buffer, IDENTITY, mark, withAlpha(mark.color, FILL_ALPHA));
        }
    }

    private static BufferBuilder quads() {
        return WorldOverlayRenderer.buffer(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR, 1.0F, false);
    }

    private static void box(BufferBuilder buffer, Matrix4f matrix, Mark mark, int argb) {
        float x1 = (float) (mark.rx - 0.5D - HIGHLIGHT_GROW), x2 = (float) (mark.rx + 0.5D + HIGHLIGHT_GROW);
        float y1 = (float) (mark.ry - 0.5D - HIGHLIGHT_GROW), y2 = (float) (mark.ry + 0.5D + HIGHLIGHT_GROW);
        float z1 = (float) (mark.rz - 0.5D - HIGHLIGHT_GROW), z2 = (float) (mark.rz + 0.5D + HIGHLIGHT_GROW);
        quad(buffer, matrix, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, argb);
        quad(buffer, matrix, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1, argb);
        quad(buffer, matrix, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1, argb);
        quad(buffer, matrix, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, argb);
        quad(buffer, matrix, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, argb);
        quad(buffer, matrix, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, argb);
    }

    private static void quad(BufferBuilder buffer, Matrix4f matrix,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz, int argb) {
        float r = ((argb >>> 16) & 255) / 255.0F;
        float g = ((argb >>> 8) & 255) / 255.0F;
        float b = (argb & 255) / 255.0F;
        float a = ((argb >>> 24) & 255) / 255.0F;
        buffer.vertex(matrix, ax, ay, az).color(r, g, b, a);
        buffer.vertex(matrix, bx, by, bz).color(r, g, b, a);
        buffer.vertex(matrix, cx, cy, cz).color(r, g, b, a);
        buffer.vertex(matrix, dx, dy, dz).color(r, g, b, a);
    }

    private static void drawOutlines(MatrixStack matrices) {
        BufferBuilder buffer = null;
        for (int index = 0; index < count; index++) {
            Mark mark = order[index];
            if (!mark.highlight || mark.scale < 1.0D) continue;
            if (buffer == null) {
                buffer = WorldOverlayRenderer.buffer(VertexFormat.DrawMode.LINES,
                        VertexFormats.POSITION_COLOR_NORMAL_LINE_WIDTH, LINE_WIDTH, false);
            }
            float x1 = (float) (mark.rx - 0.5D - HIGHLIGHT_GROW), x2 = (float) (mark.rx + 0.5D + HIGHLIGHT_GROW);
            float y1 = (float) (mark.ry - 0.5D - HIGHLIGHT_GROW), y2 = (float) (mark.ry + 0.5D + HIGHLIGHT_GROW);
            float z1 = (float) (mark.rz - 0.5D - HIGHLIGHT_GROW), z2 = (float) (mark.rz + 0.5D + HIGHLIGHT_GROW);
            int argb = 0xFF000000 | (mark.color & 0xFFFFFF);
            edge(matrices, buffer, x1, y1, z1, x2, y1, z1, argb);
            edge(matrices, buffer, x1, y1, z2, x2, y1, z2, argb);
            edge(matrices, buffer, x1, y2, z1, x2, y2, z1, argb);
            edge(matrices, buffer, x1, y2, z2, x2, y2, z2, argb);
            edge(matrices, buffer, x1, y1, z1, x1, y2, z1, argb);
            edge(matrices, buffer, x2, y1, z1, x2, y2, z1, argb);
            edge(matrices, buffer, x1, y1, z2, x1, y2, z2, argb);
            edge(matrices, buffer, x2, y1, z2, x2, y2, z2, argb);
            edge(matrices, buffer, x1, y1, z1, x1, y1, z2, argb);
            edge(matrices, buffer, x2, y1, z1, x2, y1, z2, argb);
            edge(matrices, buffer, x1, y2, z1, x1, y2, z2, argb);
            edge(matrices, buffer, x2, y2, z1, x2, y2, z2, argb);
        }
    }

    private static void edge(MatrixStack matrices, BufferBuilder buffer,
                             float ax, float ay, float az, float bx, float by, float bz, int argb) {
        Render3DUtil.vertexLine(matrices, buffer, ax, ay, az, bx, by, bz, argb, argb, LINE_WIDTH);
    }

    private static void drawLabels(MinecraftClient client, MatrixStack matrices) {
        TextRenderer font = client.textRenderer;
        Quaternionf rotation = client.gameRenderer.getCamera().getRotation();
        VertexConsumerProvider consumers = WorldOverlayRenderer.FADING_TEXT;
        BufferBuilder buffer = null;
        for (int index = 0; index < count; index++) {
            Mark mark = order[index];
            if (mark.fade <= 0.0F || !updateLabel(mark, font)) continue;
            if (buffer == null) buffer = quads();
            double s = mark.scale;
            double ax = mark.rx * s, ay = (mark.ry + 0.5D) * s, az = mark.rz * s;
            float distance = (float) Math.sqrt(ax * ax + ay * ay + az * az);
            float scale = LABEL_SCALE * Math.max(LABEL_NEAR_DISTANCE, distance);

            matrices.push();
            matrices.translate(ax, ay, az);
            matrices.multiply(rotation);
            matrices.scale(scale, -scale, scale);
            Matrix4f matrix = matrices.peek().getPositionMatrix();

            float textX = -mark.labelWidth / 2.0F;
            float textY = -(font.fontHeight + LABEL_LIFT);
            float left = textX - LABEL_PAD_X, right = -textX + LABEL_PAD_X - 1.0F;
            float upper = textY - LABEL_PAD_Y, lower = textY + font.fontHeight - 1.0F + LABEL_PAD_Y;
            float fade = mark.fade;
            int border = withAlpha(mark.color, fade);
            int background = withAlpha(LABEL_BACKGROUND, ((LABEL_BACKGROUND >>> 24) / 255.0F) * fade);
            rect(buffer, matrix, left, upper, right, lower, background);
            rect(buffer, matrix, left, upper, right, upper + LABEL_BORDER, border);
            rect(buffer, matrix, left, lower - LABEL_BORDER, right, lower, border);
            rect(buffer, matrix, left, upper + LABEL_BORDER, left + LABEL_BORDER, lower - LABEL_BORDER, border);
            rect(buffer, matrix, right - LABEL_BORDER, upper + LABEL_BORDER, right, lower - LABEL_BORDER, border);
            int textAlpha = Math.round(255.0F * fade);
            if (textAlpha >= MIN_TEXT_ALPHA && mark.labelTextObject != null) {
                font.draw(mark.labelTextObject, textX, textY, (textAlpha << 24) | 0xFFFFFF, false, matrix,
                        consumers, TextRenderer.TextLayerType.SEE_THROUGH, 0, 0xF000F0);
            }
            matrices.pop();
        }
    }

    /** "name [17m]", "name", "[17m]" or nothing; rebuilt only when what it shows changes. */
    private static boolean updateLabel(Mark mark, TextRenderer font) {
        boolean text = mark.text && !mark.name.isEmpty();
        if (!text && !mark.distance) return false;
        int meters = (int) mark.meters;
        if (mark.label == null || mark.labelMeters != meters || mark.labelText != text
                || mark.labelDistance != mark.distance || !mark.name.equals(mark.labelName)) {
            String distance = "[" + meters + "m]";
            mark.label = text && mark.distance ? mark.name + " " + distance : text ? mark.name : distance;
            mark.labelTextObject = Text.literal(mark.label);
            mark.labelWidth = font.getWidth(mark.labelTextObject);
            mark.labelMeters = meters;
            mark.labelText = text;
            mark.labelDistance = mark.distance;
            mark.labelName = mark.name;
        }
        return true;
    }

    private static void rect(BufferBuilder buffer, Matrix4f matrix, float x1, float y1, float x2, float y2, int argb) {
        quad(buffer, matrix, x1, y1, 0.0F, x1, y2, 0.0F, x2, y2, 0.0F, x2, y1, 0.0F, argb);
    }

    private static int withAlpha(int color, float alpha) {
        return (Math.round(alpha * 255.0F) << 24) | (color & 0x00FFFFFF);
    }
}
