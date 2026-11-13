package vorga.phazeclient.test;

import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.lwjgl.glfw.GLFW;
import vorga.phazeclient.core.Main;
import vorga.phazeclient.implement.features.modules.client.Theme;
import vorga.phazeclient.implement.menu.MenuScreen;

import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;

/** Real client startup, GUI, singleplayer, HUD, entity rendering and reload smoke test. */
public final class PortSmokeClientTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        context.waitFor(client -> client.getOverlay() == null);
        context.runOnClient(client -> {
            org.spongepowered.asm.mixin.MixinEnvironment.getCurrentEnvironment().audit();
            verifyCatalog();
            PortGpuCaptureChecks.verify(client);
            client.options.pauseOnLostFocus = false;
            client.options.getViewDistance().setValue(2);
            MenuScreen.INSTANCE.openGui();
            MenuScreen.INSTANCE.openModuleDetail(Theme.getInstance());
        });
        context.waitTicks(20);
        System.out.println("Port smoke GUI screenshot: " + context.takeScreenshot("port-menu"));
        for (int scale : new int[]{2, 3, 4}) {
            context.runOnClient(client -> {
                client.options.getGuiScale().setValue(scale);
                client.onResolutionChanged();
            });
            context.waitTicks(5);
            System.out.println("Port smoke scaled GUI screenshot: " + context.takeScreenshot("port-menu-scale-" + scale));
        }
        context.runOnClient(client -> {
            MenuScreen.INSTANCE.closeModuleDetail();
            client.setScreen(null);
        });
        try (var world = context.worldBuilder().create()) {
            context.waitFor(client -> client.player != null && client.world != null && client.getOverlay() == null);
            context.runOnClient(client -> {
                Set<String> visuals = Set.of("glint_colorizer", "low_health_warning", "predictions",
                        "oldanimations", "hit_range", "hitboxcustomizer", "bossbar_tweaker", "paper_doll",
                        "nametag_hud", "hudoptimizer", "animations", "chunk_animator", "block_overlay",
                        "three_d_skins", "color_correction", "motion_blur");
                for (var module : Main.getInstance().getModuleProvider().getModules()) {
                    if (visuals.contains(module.getIdentifier())) module.setStateSilent(true);
                }
            });
            world.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:diamond_sword");
            world.getServer().runCommand("enchant @a minecraft:sharpness 1");
            world.getServer().runCommand("item replace entity @a armor.chest with minecraft:diamond_chestplate");
            world.getServer().runCommand("execute at @a run summon minecraft:villager ~3 ~ ~ {NoAI:1b}");
            world.getServer().runCommand("scoreboard objectives add phaze_port dummy");
            world.getServer().runCommand("scoreboard players set RenderSmoke phaze_port 7");
            world.getServer().runCommand("scoreboard objectives setdisplay sidebar phaze_port");
            world.getServer().runCommand("bossbar add phaze:port_smoke \"Phaze render smoke\"");
            world.getServer().runCommand("bossbar set phaze:port_smoke players @a");
            world.getServer().runCommand("effect give @a minecraft:speed 60 0 true");
            context.waitTicks(80);
            System.out.println("Port smoke world screenshot: " + context.takeScreenshot("port-world"));
            context.runOnClient(client -> {
                var overlay = vorga.phazeclient.implement.features.modules.other.BlockOverlay.getInstance();
                overlay.outlineColor.setColor(0xFFFF0000);
                overlay.style.setSelected("Filled");
                overlay.fillColor.setColor(0x6000FF00);
            });
            world.getServer().runCommand("execute at @a run setblock ~ ~1 ~2 minecraft:stone");
            world.getServer().runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 5");
            context.waitTicks(20);
            System.out.println("Port smoke block overlay screenshot: " + context.takeScreenshot("port-block-overlay"));
            context.getInput().pressKey(GLFW.GLFW_KEY_F5);
            context.getInput().pressKey(GLFW.GLFW_KEY_F5);
            world.getServer().runCommand("execute as @a at @s run tp @s ~ ~ ~ 180 0");
            context.waitTicks(20);
            System.out.println("Port smoke 3d skins screenshot: " + context.takeScreenshot("port-3d-skins"));
            context.getInput().pressKey(GLFW.GLFW_KEY_F5);
            context.waitTicks(5);
            context.getInput().pressKey(options -> options.inventoryKey);
            context.waitTicks(10);
            System.out.println("Port smoke inventory screenshot: " + context.takeScreenshot("port-inventory"));
            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            world.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:ender_pearl");
            context.waitTicks(20);
            System.out.println("Port smoke prediction screenshot: " + context.takeScreenshot("port-predictions"));
            context.getInput().pressKey(options -> options.chatKey);
            context.waitTicks(10);
            System.out.println("Port smoke HUD editor screenshot: " + context.takeScreenshot("port-hud-editor"));
            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            context.getInput().pressKey(GLFW.GLFW_KEY_TAB);
            context.waitTicks(5);
            System.out.println("Port smoke animated tab screenshot: " + context.takeScreenshot("port-tab"));
            context.getInput().releaseKey(GLFW.GLFW_KEY_TAB);
            context.waitTicks(5);
            context.getInput().pressKey(GLFW.GLFW_KEY_F5);
            context.waitTicks(20);
            context.runOnClient(client -> {
                MenuScreen.INSTANCE.openGui();
                MenuScreen.INSTANCE.openCosmeticsView();
            });
            context.waitTicks(20);
            System.out.println("Port smoke cosmetics screenshot: " + context.takeScreenshot("port-cosmetics"));
            context.runOnClient(client -> {
                MenuScreen.INSTANCE.closeCosmeticsView();
                client.setScreen(null);
                client.reloadResources();
            });
            context.waitTicks(5);
            context.waitFor(client -> client.getOverlay() == null, 1200);
            context.waitTicks(30);
            context.runOnClient(PortGpuCaptureChecks::verify);
        }
        System.out.println("Phaze 1.21.11 port smoke PASSED (not a visual equivalence or FPS benchmark)");
    }

    private static void verifyCatalog() {
        var modules = Main.getInstance().getModuleProvider().getModules();
        Set<String> registered = modules.stream()
                .map(module -> module.getIdentifier()).collect(Collectors.toSet());
        if (registered.size() != modules.size()) throw new AssertionError("Duplicate runtime module identifiers");
        try (var input = PortSmokeClientTest.class.getResourceAsStream("/phaze/module-manifest.json")) {
            if (input == null) throw new AssertionError("Missing signed module catalog");
            var catalog = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            Set<String> expected = new HashSet<>();
            for (var entry : catalog.getAsJsonArray("modules")) {
                String id = entry.getAsJsonObject().get("id").getAsString();
                if (!expected.add(id)) throw new AssertionError("Duplicate signed module: " + id);
                if (!registered.contains(id)) throw new AssertionError("Signed module is not registered: " + id);
            }
            if (expected.size() != 103) {
                throw new AssertionError("Expected the complete 103-module source catalog");
            }
            if (!registered.equals(expected)) throw new AssertionError("Runtime module catalog differs from the signed catalog");
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read signed module catalog", exception);
        }
    }
}
