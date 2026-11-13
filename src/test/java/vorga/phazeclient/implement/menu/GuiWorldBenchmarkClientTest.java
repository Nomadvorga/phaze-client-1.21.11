package vorga.phazeclient.implement.menu;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.world.WorldCreator;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.world.Difficulty;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL33C;
import vorga.phazeclient.api.system.animation.Direction;
import vorga.phazeclient.api.system.shape.batched.BatchedRectangle;
import vorga.phazeclient.api.system.shape.batched.GuiDrawStats;
import vorga.phazeclient.implement.features.modules.client.Theme;

import java.util.Arrays;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Whole in-world frames at 1080p: world alone versus world plus the menu, interleaved per round
 * so background load affects every scene alike. CPU is submission time, GPU a GL_TIME_ELAPSED
 * query around the same frame.
 */
public final class GuiWorldBenchmarkClientTest implements FabricClientGameTest {
    private static final int ROUNDS = 12;
    private static final int WARMUP_FRAMES = 20;
    private static final long SETTLE_NANOS = 600_000_000L;
    private static final int FRAMES_PER_ROUND = 50;

    private record Scene(String name, Consumer<MinecraftClient> apply) {}

    @Override
    public void runTest(ClientGameTestContext context) {
        context.waitFor(client -> client.getOverlay() == null);
        context.runOnClient(client -> {
            client.options.pauseOnLostFocus = false;
            client.options.getViewDistance().setValue(2);
            client.options.getGuiScale().setValue(2);
            client.getToastManager().clear();
        });
        context.getInput().resizeWindow(1920, 1080);
        TestSingleplayerContext world = context.worldBuilder().adjustSettings(creator -> {
            creator.setWorldName("Phaze GUI world benchmark");
            creator.setGameMode(WorldCreator.Mode.CREATIVE);
            creator.setDifficulty(Difficulty.PEACEFUL);
        }).create();
        float savedBlur = context.computeOnClient(client -> Theme.getInstance().blurRadius.getValue());
        Scene[] scenes = {
                new Scene("world only", client -> {
                    setBatching(true);
                    client.setScreen(null);
                }),
                new Scene("world + mods page", client -> openMenu(client, savedBlur, false)),
                new Scene("world + mods page, no frame cache", client -> {
                    openMenu(client, savedBlur, false);
                    MenuFrameCache.setEnabled(false);
                }),
                new Scene("world + mods page, unbatched", client -> {
                    openMenu(client, savedBlur, false);
                    MenuFrameCache.setEnabled(false);
                    setBatching(false);
                }),
                new Scene("world + mods page, blur 0", client -> openMenu(client, 0.0F, false)),
                new Scene("world + settings page", client -> openMenu(client, savedBlur, true)),
                new Scene("world + chat page", client -> {
                    openMenu(client, savedBlur, false);
                    MenuScreen.INSTANCE.openMessengerView();
                }),
                new Scene("world + cosmetics page, no frame cache", client -> {
                    openMenu(client, savedBlur, false);
                    MenuFrameCache.setEnabled(false);
                    MenuScreen.INSTANCE.openCosmeticsView();
                }),
                new Scene("world + cosmetics page", client -> {
                    openMenu(client, savedBlur, false);
                    MenuScreen.INSTANCE.openCosmeticsView();
                }),
        };
        try {
            world.getClientWorld().waitForChunksRender();
            context.runOnClient(client -> client.options.getViewDistance().setValue(8));
            context.waitTicks(300);
            if (Boolean.getBoolean("phaze.guiBench.profile")) {
                context.runOnClient(client -> {
                    openMenu(client, savedBlur, false);
                    if (Boolean.getBoolean("phaze.guiBench.cosmetics")) MenuScreen.INSTANCE.openCosmeticsView();
                    int query = GL15C.glGenQueries();
                    for (int i = 0; i < 15000; i++) frame(client, query);
                    GL15C.glDeleteQueries(query);
                });
                return;
            }
            compareCachedFrames(context);
            context.runOnClient(client -> bench(client, scenes));
            context.runOnClient(client -> scenes[scenes.length - 1].apply().accept(client));
            context.waitTicks(10);
            System.out.println("GUI world bench screenshot: " + context.takeScreenshot("cosmetics-cached"));
            context.runOnClient(client -> System.out.println("GUI world bench preview state: live="
                    + previewField("livePlayerPreview") + " validated=" + previewField("playerPreviewValidated")));
            context.runOnClient(client -> setLivePreview(true));
            context.waitTicks(2);
            System.out.println("GUI world bench screenshot: " + context.takeScreenshot("cosmetics-live"));
            context.runOnClient(client -> setLivePreview(false));
        } finally {
            context.runOnClient(client -> {
                setBatching(true);
                MenuFrameCache.setEnabled(true);
                Theme.getInstance().blurRadius.setValue(savedBlur);
                MenuScreen.INSTANCE.closeModuleDetail();
                client.setScreen(null);
            });
            world.close();
        }
        System.out.println("GUI world benchmark finished");
    }

    /**
     * The cached menu against the live one, inside the menu rectangle. Blur is off so the world moving under
     * the menu between the two screenshots does not count; the cosmetics preview animates on its own.
     */
    private static void compareCachedFrames(ClientGameTestContext context) {
        String[] pages = {"mods", "settings", "chat", "cosmetics"};
        for (String page : pages) {
            java.awt.image.BufferedImage[] shots = new java.awt.image.BufferedImage[2];
            for (int cached = 0; cached < 2; cached++) {
                boolean useCache = cached == 1;
                context.runOnClient(client -> {
                    openMenu(client, 0.0F, page.equals("settings"));
                    if (page.equals("chat")) MenuScreen.INSTANCE.openMessengerView();
                    if (page.equals("cosmetics")) MenuScreen.INSTANCE.openCosmeticsView();
                    MenuFrameCache.setEnabled(useCache);
                });
                context.waitTicks(20);
                java.nio.file.Path path = context.takeScreenshot("frame-cache-" + page + (useCache ? "-cached" : "-live"));
                try {
                    shots[cached] = javax.imageio.ImageIO.read(path.toFile());
                } catch (java.io.IOException e) {
                    throw new AssertionError(e);
                }
            }
            int[] rect = context.computeOnClient(client -> menuRectangle(client));
            int differing = 0, maxDelta = 0, total = 0;
            for (int py = Math.max(0, rect[1]); py < Math.min(shots[0].getHeight(), rect[3]); py++) {
                for (int px = Math.max(0, rect[0]); px < Math.min(shots[0].getWidth(), rect[2]); px++) {
                    int a = shots[0].getRGB(px, py), b = shots[1].getRGB(px, py);
                    int delta = 0;
                    for (int shift = 0; shift <= 16; shift += 8) {
                        delta = Math.max(delta, Math.abs(((a >> shift) & 0xFF) - ((b >> shift) & 0xFF)));
                    }
                    total++;
                    maxDelta = Math.max(maxDelta, delta);
                    if (delta > 2) differing++;
                }
            }
            System.out.printf(Locale.ROOT, "GUI frame cache compare [%s]: %d of %d menu pixels differ by more than 2, max delta %d%n",
                    page, differing, total, maxDelta);
        }
        context.runOnClient(client -> MenuFrameCache.setEnabled(true));
    }

    /** Framebuffer pixels {left, top, right, bottom} of the menu, with the GUI-scale pivot at its centre. */
    private static int[] menuRectangle(MinecraftClient client) {
        MenuScreen menu = MenuScreen.INSTANCE;
        double scale = menu.getOverlayScaleFactor();
        double guiScale = MenuUiSettings.getInstance().getGuiScale();
        double cx = (menu.x + menu.width / 2.0) * scale, cy = (menu.y + menu.height / 2.0) * scale;
        double hw = menu.width / 2.0 * guiScale * scale, hh = menu.height / 2.0 * guiScale * scale;
        return new int[]{(int) Math.floor(cx - hw), (int) Math.floor(cy - hh), (int) Math.ceil(cx + hw), (int) Math.ceil(cy + hh)};
    }

    private static Object previewField(String name) {
        try {
            var viewField = MenuScreen.class.getDeclaredField("cosmeticsView");
            viewField.setAccessible(true);
            Object view = viewField.get(MenuScreen.INSTANCE);
            var field = view.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(view);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void setBatching(boolean batched) {
        BatchedRectangle.setEnabled(batched);
    }

    private static void setLivePreview(boolean live) {
        setPreviewField("livePlayerPreview", live);
    }

    private static void setPreviewField(String name, boolean value) {
        try {
            var viewField = MenuScreen.class.getDeclaredField("cosmeticsView");
            viewField.setAccessible(true);
            Object view = viewField.get(MenuScreen.INSTANCE);
            var field = view.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.setBoolean(view, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void openMenu(MinecraftClient client, float blur, boolean settings) {
        setBatching(true);
        MenuFrameCache.setEnabled(true);
        MenuScreen.INSTANCE.closeMessengerView();
        Theme.getInstance().blurRadius.setValue(blur);
        if (client.currentScreen != MenuScreen.INSTANCE) MenuScreen.INSTANCE.openGui();
        MenuScreen.INSTANCE.animation.setDirectionAndFinish(Direction.FORWARDS);
        MenuScreen.INSTANCE.closeCosmeticsView();
        if (settings) MenuScreen.INSTANCE.openModuleDetail(Theme.getInstance());
        else MenuScreen.INSTANCE.closeModuleDetail();
    }

    private static void bench(MinecraftClient client, Scene[] scenes) {
        int query = GL15C.glGenQueries();
        try {
            double[][] cpu = new double[scenes.length][ROUNDS], gpu = new double[scenes.length][ROUNDS];
            double[][] total = new double[scenes.length][ROUNDS], pipelined = new double[scenes.length][ROUNDS];
            int[][] draws = new int[scenes.length][];
            for (int round = 0; round < ROUNDS; round++) {
                for (int order = 0; order < scenes.length; order++) {
                    int s = (round + order) % scenes.length;
                    scenes[s].apply().accept(client);
                    // Long enough for the page's open transitions to settle, as in an idle menu.
                    long settled = System.nanoTime() + SETTLE_NANOS;
                    for (int i = 0; i < WARMUP_FRAMES || System.nanoTime() < settled; i++) frame(client, query);
                    long cpuNanos = 0, gpuNanos = 0, totalNanos = 0;
                    int captures = 0, replays = 0;
                    for (int i = 0; i < FRAMES_PER_ROUND; i++) {
                        GuiDrawStats.reset();
                        long[] times = frame(client, query);
                        cpuNanos += times[0];
                        gpuNanos += times[1];
                        totalNanos += times[2];
                        captures += GuiDrawStats.menuCaptures;
                        replays += GuiDrawStats.menuReplays;
                    }
                    draws[s] = new int[]{GuiDrawStats.rectDraws, GuiDrawStats.bufferDraws, GuiDrawStats.scissorChanges,
                            GuiDrawStats.batchInstances, captures, replays};
                    cpu[s][round] = cpuNanos / (double) FRAMES_PER_ROUND / 1_000_000.0;
                    gpu[s][round] = gpuNanos / (double) FRAMES_PER_ROUND / 1_000_000.0;
                    total[s][round] = totalNanos / (double) FRAMES_PER_ROUND / 1_000_000.0;
                    // CPU and GPU overlap across frames here, as in the real render loop.
                    GL11C.glFinish();
                    long start = System.nanoTime();
                    for (int i = 0; i < FRAMES_PER_ROUND; i++) client.gameRenderer.render(RenderTickCounter.ZERO, true);
                    GL11C.glFinish();
                    pipelined[s][round] = (System.nanoTime() - start) / (double) FRAMES_PER_ROUND / 1_000_000.0;
                }
            }
            double base = median(total[0]), pipelinedBase = median(pipelined[0]);
            for (int s = 0; s < scenes.length; s++) {
                double frame = median(total[s]), throughput = median(pipelined[s]);
                System.out.printf(Locale.ROOT,
                        "GUI world bench [%s] %dx%d: cpu %.3f ms, gpu %.3f ms, frame %.3f ms (%.0f fps), GUI cost %.3f ms;"
                                + " pipelined %.3f ms (%.0f fps), GUI cost %.3f ms, %.0f fps lost; batch draws %d (%d instances), buffer draws %d,"
                                + " scissor changes %d; menu captures %d, replays %d of %d frames%n",
                        scenes[s].name(), client.getWindow().getFramebufferWidth(), client.getWindow().getFramebufferHeight(),
                        median(cpu[s]), median(gpu[s]), frame, 1000.0 / frame, frame - base,
                        throughput, 1000.0 / throughput, throughput - pipelinedBase,
                        1000.0 / pipelinedBase - 1000.0 / throughput,
                        draws[s][0], draws[s][3], draws[s][1], draws[s][2], draws[s][4], draws[s][5], FRAMES_PER_ROUND);
            }
        } finally {
            GL15C.glDeleteQueries(query);
        }
    }

    /** Returns {submission nanos, GPU elapsed nanos, nanos including glFinish}. */
    private static long[] frame(MinecraftClient client, int query) {
        GL11C.glFinish();
        long start = System.nanoTime();
        GL15C.glBeginQuery(GL33C.GL_TIME_ELAPSED, query);
        client.gameRenderer.render(RenderTickCounter.ZERO, true);
        GL15C.glEndQuery(GL33C.GL_TIME_ELAPSED);
        long submitted = System.nanoTime();
        GL11C.glFinish();
        long end = System.nanoTime();
        long gpu = GL33C.glGetQueryObjecti64(query, GL15C.GL_QUERY_RESULT);
        return new long[]{submitted - start, gpu, end - start};
    }

    private static double median(double[] values) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        return (sorted[sorted.length / 2 - 1] + sorted[sorted.length / 2]) / 2.0;
    }
}
