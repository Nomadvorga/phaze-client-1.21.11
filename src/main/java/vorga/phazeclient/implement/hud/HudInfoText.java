package vorga.phazeclient.implement.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import vorga.phazeclient.implement.features.modules.hud.ArmorHud;
import vorga.phazeclient.implement.features.modules.hud.DayCounterHud;
import vorga.phazeclient.implement.features.modules.hud.RectHudModule;
import vorga.phazeclient.implement.features.modules.hud.SessionTimeHud;
import vorga.phazeclient.implement.features.modules.hud.WailaHud;
import vorga.phazeclient.implement.features.modules.hud.TimeHud;
import vorga.phazeclient.implement.features.modules.other.AutoSprint;
import vorga.phazeclient.implement.features.modules.other.StreamerMode;
import java.util.Locale;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Game-state strings and version-sensitive lookups for HUD widgets. */
final class HudInfoText {
    private HudInfoText() { }
    static String formatDurabilityText(ArmorHud module, ItemStack stack) {
        return stack.isDamageable() ? module.formatDurability(Math.max(0, stack.getMaxDamage() - stack.getDamage()), stack.getMaxDamage()) : "-";
    }
    static String getSprintHudText(MinecraftClient client) {
        if (client.player == null || client.options == null) return "Not Sprinting";
        if (client.player.getAbilities().flying) return client.options.sneakKey.isPressed() ? "Flying Descending" : "Flying";
        if (client.options.sneakKey.isPressed() || client.player.isSneaking()) return "Sneaking (Key Held)";
        AutoSprint module = AutoSprint.getInstance();
        boolean auto = module.isEnabled() && module.showInSprintHud.isValue();
        if (client.options.sprintKey.isPressed()) return auto ? "Sprinting (AutoSprint)" : "Sprinting (Key Held)";
        if (client.player.isSprinting()) return auto ? "Sprinting (AutoSprint)" : "Sprinting (Vanilla)";
        return "Not Sprinting";
    }
    static String getDayCounterText(MinecraftClient client) {
        DayCounterHud module = DayCounterHud.getInstance();
        if (client.world == null) return module.reverseOrder.isValue() ? "Day: 0" : "0 Days";
        long days = Math.max(0L, client.world.getTime() / 24000L) + 1;
        return module.reverseOrder.isValue() ? "Day: " + days : days + (days == 1 ? " Day" : " Days");
    }
    static String getTimeHudText(TimeHud module, MinecraftClient client) {
        String pattern = module.hour24.isValue() ? (module.showSeconds.isValue() ? "H:mm:ss" : "H:mm")
                : module.showAmPm.isValue() ? (module.showSeconds.isValue() ? "h:mm:ss a" : "h:mm a")
                : (module.showSeconds.isValue() ? "h:mm:ss" : "h:mm");
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern(pattern, Locale.US));
        if (module.showPhase.isValue() && client != null && client.world != null) {
            TimeHud.Phase phase = module.phaseForTime(client.world.getTimeOfDay());
            return time + " " + (module.colorPhase.isValue() ? phase.colorCode + phase.label + "§r" : phase.label);
        }
        return time;
    }
    static String getSessionText(SessionTimeHud module) {
        long elapsed = Math.max(0L, System.currentTimeMillis() - module.getSessionStartMs());
        long seconds = elapsed / 1000, minutes = seconds / 60, hours = minutes / 60;
        return switch (module.displayOption.getSelected()) {
            case "12h 34m 56s" -> String.format(Locale.US, "%dh %dm %ds", hours, minutes % 60, seconds % 60);
            case "123.456s" -> String.format(Locale.US, "%.3fs", elapsed / 1000.0);
            case "12:34" -> String.format(Locale.US, "%02d:%02d", hours, minutes % 60);
            case "12:34:56.789" -> String.format(Locale.US, "%02d:%02d:%02d.%03d", hours, minutes % 60, seconds % 60, elapsed % 1000);
            default -> String.format(Locale.US, "%02d:%02d:%02d", hours, minutes % 60, seconds % 60);
        };
    }
    static String wrapTextWithBrackets(String text, RectHudModule module) {
        return module != null && !module.background.isValue() && module.showBrackets.isValue() ? "[" + text + "]" : text;
    }
    static String directionLabel(int degrees, boolean intermediate) {
        return switch (degrees) {
            case 0 -> "S";
            case 45 -> intermediate ? "SE" : "";
            case 90 -> "W";
            case 135 -> intermediate ? "SW" : "";
            case 180 -> "N";
            case 225 -> intermediate ? "NW" : "";
            case 270 -> "E";
            case 315 -> intermediate ? "NE" : "";
            default -> String.valueOf(degrees);
        };
    }
    static String getBiomeName(MinecraftClient client, BlockPos position) {
        return client.world == null ? "Unknown" : client.world.getBiome(position).getKey()
                .map(key -> toTitleCase(key.getValue().getPath())).orElse("Unknown");
    }
    static int getBiomeColor(MinecraftClient client, BlockPos position) {
        if (client.world == null) return 0xFFFF55;
        String biome = client.world.getBiome(position).getKey().map(key -> key.getValue().getPath()).orElse("");
        return switch (biome) {
            case "river", "frozen_river" -> 0x55AAFF;
            case "ocean", "deep_ocean", "cold_ocean", "deep_cold_ocean", "lukewarm_ocean", "deep_lukewarm_ocean" -> 0x3388DD;
            case "warm_ocean" -> 0x55CCEE;
            case "frozen_ocean", "deep_frozen_ocean" -> 0xAADDFF;
            case "desert" -> 0xFFDD55;
            case "beach" -> 0xFFEE88;
            case "snowy_beach" -> 0xDDEEFF;
            case "plains", "sunflower_plains" -> 0x88DD44;
            case "meadow" -> 0x77DD66;
            case "forest", "flower_forest" -> 0x44BB33;
            case "birch_forest", "old_growth_birch_forest" -> 0x88CC55;
            case "dark_forest" -> 0x336622;
            case "taiga", "old_growth_pine_taiga", "old_growth_spruce_taiga" -> 0x559944;
            case "snowy_taiga" -> 0xDDEEFF;
            case "snowy_plains", "ice_spikes", "snowy_slopes", "frozen_peaks" -> 0xFFFFFF;
            case "jungle", "bamboo_jungle", "sparse_jungle" -> 0x33BB22;
            case "savanna", "savanna_plateau", "windswept_savanna" -> 0xBBAA44;
            case "badlands", "eroded_badlands", "wooded_badlands" -> 0xDD7733;
            case "swamp", "mangrove_swamp" -> 0x668855;
            case "mushroom_fields" -> 0xCC77CC;
            case "stony_shore", "windswept_hills", "windswept_gravelly_hills", "windswept_forest", "stony_peaks" -> 0xAAAAAA;
            case "grove" -> 0xBBDDAA;
            case "jagged_peaks" -> 0xCCDDEE;
            case "cherry_grove" -> 0xFFAACC;
            case "the_nether", "nether_wastes" -> 0xFF5533;
            case "soul_sand_valley" -> 0x774433;
            case "crimson_forest" -> 0xDD3344;
            case "warped_forest" -> 0x22BBAA;
            case "basalt_deltas" -> 0x888888;
            case "the_end", "end_highlands", "end_midlands", "end_barrens", "small_end_islands" -> 0xDDDD88;
            case "the_void" -> 0x555555;
            case "deep_dark" -> 0x224455;
            case "lush_caves", "dripstone_caves" -> 0x66BB55;
            default -> 0xFFFF55;
        };
    }
    static String toTitleCase(String value) {
        StringBuilder result = new StringBuilder();
        for (String part : value.replace('_', ' ').split(" ")) {
            if (part.isEmpty()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return result.isEmpty() ? value : result.toString();
    }
    static String getFacingShort(Direction direction) {
        return switch (direction) { case NORTH -> "N"; case SOUTH -> "S"; case EAST -> "E"; case WEST -> "W"; default -> ""; };
    }
    static String getFacing8Point(float yaw) {
        float normalized = (yaw % 360 + 360) % 360;
        if (normalized >= 337.5F || normalized < 22.5F) return "S";
        if (normalized < 67.5F) return "SW";
        if (normalized < 112.5F) return "W";
        if (normalized < 157.5F) return "NW";
        if (normalized < 202.5F) return "N";
        if (normalized < 247.5F) return "NE";
        return normalized < 292.5F ? "E" : "SE";
    }
    static String getDirectionXSign(String facing) {
        return switch (facing) { case "E", "NE", "SE" -> "+"; case "W", "NW", "SW" -> "-"; default -> ""; };
    }
    static String getDirectionZSign(String facing) {
        return switch (facing) { case "S", "SE", "SW" -> "+"; case "N", "NE", "NW" -> "-"; default -> ""; };
    }
    static int getServerPing(MinecraftClient client) {
        if (client.getNetworkHandler() == null || client.player == null) return 0;
        PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
        return entry == null ? 0 : Math.max(0, entry.getLatency());
    }
    static String getKeyLabel(Text text) {
        String value = text == null ? "" : text.getString();
        if (value.isBlank()) return "?";
        return (value.length() > 3 ? value.substring(0, 3) : value).toUpperCase(Locale.ROOT);
    }
    static String getEffectName(StatusEffectInstance effect) {
        String name = effect.getEffectType().value().getName().getString();
        return effect.getAmplifier() >= 1 ? name + " " + toRoman(effect.getAmplifier() + 1) : name;
    }
    static String toRoman(int number) {
        return switch (number) { case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; case 5 -> "V";
            case 6 -> "VI"; case 7 -> "VII"; case 8 -> "VIII"; case 9 -> "IX"; case 10 -> "X"; default -> String.valueOf(number); };
    }
    static String formatEffectDuration(StatusEffectInstance effect) {
        if (effect.isInfinite()) return "\u221E";
        int seconds = Math.max(0, effect.getDuration() / 20);
        return String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60);
    }
    static String getWailaText(MinecraftClient client) {
        WailaHud module = WailaHud.getInstance();
        if (client.player == null || client.world == null) return module.alwaysShow.isValue() ? "No target" : "";
        HitResult hit = client.player.raycast(4.5, 0, false);
        if (hit.getType() == HitResult.Type.MISS) return module.alwaysShow.isValue() ? "No target" : "";
        StringBuilder result = new StringBuilder();
        if (hit instanceof BlockHitResult blockHit) {
            BlockPos position = blockHit.getBlockPos();
            BlockState state = client.world.getBlockState(position);
            result.append(state.getBlock().getName().getString());
            if (module.showCoordinates.isValue() && !StreamerMode.getInstance().isHideCoordinatesEnabled()) {
                result.append("\n(").append(position.getX()).append(", ").append(position.getY()).append(", ").append(position.getZ()).append(')');
            }
            if (module.showCorrectTool.isValue()) result.append("\nCorrect Tool: ").append(resolveCorrectTool(state));
            if (module.showBreakTime.isValue()) {
                float hardness = state.getHardness(client.world, position), speed = client.player.getBlockBreakingSpeed(state);
                if (client.player.isCreative() || hardness <= 0) result.append("\nBreak Time: Instant");
                else if (speed <= 0) result.append("\nBreak Time: Never");
                else {
                    float time = hardness / speed * (client.player.canHarvest(state) ? 30 : 100) / 20;
                    result.append(time < 0.05F ? "\nBreak Time: Instant" : String.format(Locale.US, "\nBreak Time: %.2fs", time));
                }
            }
        } else if (hit instanceof EntityHitResult entityHit && module.showEntities.isValue()) {
            Entity entity = entityHit.getEntity();
            result.append(entity.getName().getString()).append("\nType: ").append(entity.getType().getName().getString());
        }
        return result.toString();
    }
    static String resolveCorrectTool(BlockState state) {
        String tier = state.isIn(BlockTags.NEEDS_DIAMOND_TOOL) ? "Diamond " : state.isIn(BlockTags.NEEDS_IRON_TOOL) ? "Iron "
                : state.isIn(BlockTags.NEEDS_STONE_TOOL) ? "Stone " : "";
        if (state.isIn(BlockTags.PICKAXE_MINEABLE)) return tier + "Pickaxe";
        if (state.isIn(BlockTags.AXE_MINEABLE)) return tier + "Axe";
        if (state.isIn(BlockTags.SHOVEL_MINEABLE)) return tier + "Shovel";
        if (state.isIn(BlockTags.HOE_MINEABLE)) return tier + "Hoe";
        if (state.isIn(BlockTags.SWORD_EFFICIENT)) return "Sword";
        return "Hand";
    }
    static ItemStack getWailaIcon(MinecraftClient client) {
        WailaHud module = WailaHud.getInstance();
        if (!module.showIcon.isValue() || client.player == null || client.world == null) return null;
        HitResult hit = client.player.raycast(4.5, 0, false);
        if (hit.getType() == HitResult.Type.MISS) return new ItemStack(Items.BARRIER);
        if (hit instanceof BlockHitResult blockHit) {
            Item item = client.world.getBlockState(blockHit.getBlockPos()).getBlock().asItem();
            if (item != Items.AIR) return new ItemStack(item);
        } else if (hit instanceof EntityHitResult entityHit && module.showEntities.isValue()) {
            Entity entity = entityHit.getEntity();
            if (entity instanceof net.minecraft.entity.decoration.ItemFrameEntity frame && !frame.getHeldItemStack().isEmpty()) return frame.getHeldItemStack();
            Identifier typeId = Registries.ENTITY_TYPE.getId(entity.getType());
            Item egg = Registries.ITEM.get(Identifier.of(typeId.getNamespace(), typeId.getPath() + "_spawn_egg"));
            return new ItemStack(egg != null && egg != Items.AIR ? egg : Items.BARRIER);
        }
        return null;
    }
}
