package vorga.phazeclient.implement.features.modules.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.InputSupplier;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.resource.ResourcePackSource;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;
import vorga.phazeclient.api.system.hud.BatchedHudBuffer;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Cached resource-pack metadata and one owned, bounded, asynchronously decoded icon. */
public class PackDisplayHud extends RectHudModule {
    private static final PackDisplayHud INSTANCE = new PackDisplayHud();
    public static final int ICON_SIZE = 28;
    private static final int MAX_ICON_SIZE = 256, UNKNOWN_PACK_ICON_SIZE = 128;
    private static final float TEXT_PADDING = 4;
    private static final Identifier UNKNOWN_PACK_ICON = Identifier.ofVanilla("textures/misc/unknown_pack.png");
    private static final Identifier PACK_ICON_TEXTURE = Identifier.of("phaze", "dynamic/pack_display_icon");
    private static final ResourcePackSource FABRIC_MOD_SOURCE = resolveFabricModSource();
    private String cachedPackName = "Default", cachedText = "Default", cachedDescription = "", cachedTextWithDescription = "Default";
    private String cachedProfileId;
    private int cachedTextWidth, cachedDescriptionWidth;
    private Text cachedNameText = Text.literal("Default"), cachedDescriptionText = Text.empty();
    private List<Text> cachedDescriptionLines = List.of();
    private Identifier cachedIcon, ownedIcon;
    private int cachedIconWidth = 16, cachedIconHeight = 16, cachedDrawIconWidth = ICON_SIZE, cachedDrawIconHeight = ICON_SIZE;
    private Collection<ResourcePackProfile> observedProfiles;
    private boolean observedFirst, observedIgnoreServer, textStale = true, iconStale = true;
    private int iconGeneration;
    public final BooleanSetting displayPackIcon = new BooleanSetting("Показывать иконку пака", "Показывать иконку resource pack слева").setValue(true);
    public final BooleanSetting displayDescription = new BooleanSetting("Показывать описание", "Показывать описание resource pack").setValue(true);
    public final BooleanSetting ignoreServerPack = new BooleanSetting("Игнорировать серверный пак", "Не учитывать серверный resource pack").setValue(false);
    public final SelectSetting packOrder = new SelectSetting("Порядок паков", "Выбирать первый или последний пак")
            .value("Первый", "Последний").selected("Последний");
    private PackDisplayHud() {
        super("pack_display_hud", "Pack Display", 20, 140, 1);
        cornerRounding.setValue(4);
        setup(displayPackIcon, displayDescription, ignoreServerPack, packOrder);
    }
    public static PackDisplayHud getInstance() { return INSTANCE; }
    @Override public String getDescription() { return "HUD widget showing name of current active resource pack"; }
    @Override public String getIcon() { return "item_scroller.png"; }
    private void updatePackName(MinecraftClient client) {
        if (client == null || client.getResourcePackManager() == null) return;
        Collection<ResourcePackProfile> profiles = client.getResourcePackManager().getEnabledProfiles();
        boolean first = "Первый".equals(packOrder.getSelected()), ignoreServer = ignoreServerPack.isValue();
        if (profiles == observedProfiles && first == observedFirst && ignoreServer == observedIgnoreServer) return;
        observedProfiles = profiles; observedFirst = first; observedIgnoreServer = ignoreServer;
        Text name = Text.literal("Default"), description = Text.empty();
        ResourcePackProfile selected = null, defaultProfile = null;
        for (ResourcePackProfile profile : profiles) {
            String id = profile.getId();
            if (id.equalsIgnoreCase("vanilla")) { defaultProfile = profile; continue; }
            if (isModPack(profile) || ignoreServer && (profile.getSource() == ResourcePackSource.SERVER
                    || id.toLowerCase(Locale.ROOT).contains("server"))) continue;
            name = profile.getDisplayName(); description = profile.getDescription(); selected = profile;
            if (first) break;
        }
        if (selected == null) selected = defaultProfile;
        String id = selected == null ? null : selected.getId();
        if (iconStale || !Objects.equals(cachedProfileId, id)) {
            cachedProfileId = id; iconStale = false; requestIcon(client, selected);
        }
        if (textStale || !cachedNameText.equals(name) || !cachedDescriptionText.equals(description)) {
            textStale = false;
            cachedPackName = name.getString(); cachedText = cachedPackName; cachedDescription = description.getString();
            cachedTextWithDescription = cachedText + "\n" + cachedDescription;
            cachedNameText = name; cachedDescriptionText = description; cachedDescriptionLines = splitDescription(description);
            cachedTextWidth = client.textRenderer.getWidth(name); cachedDescriptionWidth = 0;
            for (Text line : cachedDescriptionLines) cachedDescriptionWidth = Math.max(cachedDescriptionWidth, client.textRenderer.getWidth(line));
            invalidateHud();
        }
    }
    private static boolean isModPack(ResourcePackProfile profile) {
        if ("fabric".equals(profile.getId())) return true;
        ResourcePackSource source = profile.getSource();
        return source != null && (source == FABRIC_MOD_SOURCE || source.getClass().getName().startsWith("net.fabricmc."));
    }
    private static ResourcePackSource resolveFabricModSource() {
        try {
            Object source = Class.forName("net.fabricmc.fabric.impl.resource.loader.ModResourcePackCreator").getField("RESOURCE_PACK_SOURCE").get(null);
            return source instanceof ResourcePackSource value ? value : null;
        } catch (ReflectiveOperationException | LinkageError ignored) { return null; }
    }
    private void invalidateHud() { getHudBuffer().invalidate(); BatchedHudBuffer.INSTANCE.invalidate(); }
    public String getDisplayText(MinecraftClient client) {
        updatePackName(client);
        return displayDescription.isValue() && !cachedDescription.isBlank() ? cachedTextWithDescription : cachedText;
    }
    public int getDisplayTextWidth() { return cachedTextWidth; }
    public Text getNameText(MinecraftClient client) { updatePackName(client); return cachedNameText; }
    public Text getDescriptionText(MinecraftClient client) { updatePackName(client); return cachedDescriptionText; }
    public boolean hasDescription(MinecraftClient client) { updatePackName(client); return displayDescription.isValue() && !cachedDescription.isBlank(); }
    public List<Text> getDescriptionLines(MinecraftClient client) { updatePackName(client); return cachedDescriptionLines; }
    public float getTextStartX() { return (displayPackIcon.isValue() ? ICON_SIZE : 0) + TEXT_PADDING; }
    public float getPanelBaseWidth(MinecraftClient client) {
        updatePackName(client);
        return getTextStartX() + (hasDescription(client) ? Math.max(cachedTextWidth, cachedDescriptionWidth) : cachedTextWidth) + TEXT_PADDING;
    }
    public Identifier getPackIcon(MinecraftClient client) { updatePackName(client); return displayPackIcon.isValue() ? cachedIcon : null; }
    public int getPackIconWidth() { return cachedIconWidth; }
    public int getPackIconHeight() { return cachedIconHeight; }
    public void drawPackIcon(DrawContext context) {
        if (cachedIcon == null || !displayPackIcon.isValue()) return;
        context.drawTexture(RenderPipelines.GUI_TEXTURED, cachedIcon,
                (ICON_SIZE - cachedDrawIconWidth) / 2, (ICON_SIZE - cachedDrawIconHeight) / 2, 0, 0,
                cachedDrawIconWidth, cachedDrawIconHeight, cachedIconWidth, cachedIconHeight, cachedIconWidth, cachedIconHeight);
    }
    private void setIconSize(int width, int height) {
        cachedIconWidth = Math.max(1, width); cachedIconHeight = Math.max(1, height);
        int longest = Math.max(cachedIconWidth, cachedIconHeight);
        cachedDrawIconWidth = Math.max(1, Math.round((float) ICON_SIZE * cachedIconWidth / longest));
        cachedDrawIconHeight = Math.max(1, Math.round((float) ICON_SIZE * cachedIconHeight / longest));
    }
    public void onResourcesReloaded(MinecraftClient client) {
        // Invalidate a decode already in flight even before the next visible HUD frame.
        iconGeneration++; observedProfiles = null; textStale = true; iconStale = true; invalidateHud();
    }
    public void releaseIcon(MinecraftClient client) {
        iconGeneration++; releaseOwnedIcon(client); cachedIcon = null; observedProfiles = null; iconStale = true;
        invalidateHud();
    }
    private void releaseOwnedIcon(MinecraftClient client) {
        if (ownedIcon != null) { client.getTextureManager().destroyTexture(ownedIcon); ownedIcon = null; }
    }
    private void requestIcon(MinecraftClient client, ResourcePackProfile profile) {
        int generation = ++iconGeneration;
        if (profile == null) { applyIcon(client, generation, null); return; }
        CompletableFuture.supplyAsync(() -> loadIcon(profile), Util.getIoWorkerExecutor())
                .thenAcceptAsync(image -> applyIcon(client, generation, image), client);
    }
    private void applyIcon(MinecraftClient client, int generation, NativeImage image) {
        if (generation != iconGeneration) { if (image != null) image.close(); return; }
        if (image == null) {
            releaseOwnedIcon(client); setIconSize(UNKNOWN_PACK_ICON_SIZE, UNKNOWN_PACK_ICON_SIZE); cachedIcon = UNKNOWN_PACK_ICON;
        } else {
            NativeImageBackedTexture texture = null;
            try {
                texture = new NativeImageBackedTexture(() -> "Phaze pack display icon", image);
                client.getTextureManager().registerTexture(PACK_ICON_TEXTURE, texture);
            } catch (RuntimeException | Error failure) {
                if (texture != null) texture.close(); else image.close();
                throw failure;
            }
            setIconSize(image.getWidth(), image.getHeight()); ownedIcon = PACK_ICON_TEXTURE; cachedIcon = PACK_ICON_TEXTURE;
        }
        invalidateHud();
    }
    private static NativeImage loadIcon(ResourcePackProfile profile) {
        try (ResourcePack pack = profile.createResourcePack()) {
            InputSupplier<InputStream> supplier = pack.openRoot("pack.png");
            if (supplier == null) return null;
            NativeImage image;
            try (InputStream stream = supplier.get()) { image = NativeImage.read(NativeImage.Format.RGBA, stream); }
            try {
                if (isMissingTextureImage(image)) { image.close(); return null; }
            } catch (RuntimeException | Error failure) { image.close(); throw failure; }
            return fitIcon(image);
        } catch (IOException | RuntimeException ignored) { return null; }
    }
    private static NativeImage fitIcon(NativeImage image) {
        int longest = Math.max(image.getWidth(), image.getHeight());
        if (longest <= MAX_ICON_SIZE) return image;
        NativeImage scaled = null;
        try {
            int width = Math.max(1, Math.round((float) image.getWidth() * MAX_ICON_SIZE / longest));
            int height = Math.max(1, Math.round((float) image.getHeight() * MAX_ICON_SIZE / longest));
            scaled = new NativeImage(image.getFormat(), width, height, false);
            image.resizeSubRectTo(0, 0, image.getWidth(), image.getHeight(), scaled);
            return scaled;
        } catch (RuntimeException | Error failure) { if (scaled != null) scaled.close(); throw failure; }
        finally { image.close(); }
    }
    private static boolean isMissingTextureImage(NativeImage image) {
        if (image.getWidth() <= 0 || image.getHeight() <= 0) return true;
        int first = image.getColorArgb(0, 0) & 0xFFFFFF;
        if (first != 0xFF00FF) return false;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
            if ((image.getColorArgb(x, y) & 0xFFFFFF) != first) return false;
        return true;
    }
    private static List<Text> splitDescription(Text description) {
        List<Text> lines = new ArrayList<>();
        MutableText[] current = {Text.empty()};
        description.visit((style, fragment) -> {
            Style activeStyle = style;
            StringBuilder run = new StringBuilder();
            for (int i = 0; i < fragment.length(); i++) {
                char character = fragment.charAt(i);
                if (character == '§' && i + 1 < fragment.length()) {
                    appendRun(current[0], run, activeStyle);
                    Formatting formatting = Formatting.byCode(fragment.charAt(++i));
                    if (formatting == Formatting.RESET) activeStyle = Style.EMPTY;
                    else if (formatting != null) activeStyle = formatting.isColor() ? Style.EMPTY.withColor(formatting) : activeStyle.withFormatting(formatting);
                } else if (character == '\n' || character == '\r') {
                    appendRun(current[0], run, activeStyle);
                    if (character == '\r' && i + 1 < fragment.length() && fragment.charAt(i + 1) == '\n') i++;
                    lines.add(current[0]); current[0] = Text.empty();
                } else run.append(character);
            }
            appendRun(current[0], run, activeStyle);
            return java.util.Optional.empty();
        }, description.getStyle());
        lines.add(current[0]); return List.copyOf(lines);
    }
    private static void appendRun(MutableText line, StringBuilder run, Style style) {
        if (!run.isEmpty()) { line.append(Text.literal(run.toString()).setStyle(style)); run.setLength(0); }
    }
}
