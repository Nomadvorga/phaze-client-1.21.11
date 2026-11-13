package vorga.phazeclient.implement.menu;

import lombok.Getter;
import lombok.Setter;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.system.animation.Animation;
import vorga.phazeclient.api.system.animation.Direction;
import vorga.phazeclient.api.system.animation.implement.DecelerateAnimation;
import vorga.phazeclient.api.system.animation.implement.LinearAnimation;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.api.system.shape.implement.Blur;
import vorga.phazeclient.base.QuickImports;
import vorga.phazeclient.base.util.math.MathUtil;
import vorga.phazeclient.base.util.color.ColorUtil;
import vorga.phazeclient.core.Main;
import vorga.phazeclient.implement.features.modules.client.Theme;
import vorga.phazeclient.implement.menu.components.AbstractComponent;
import vorga.phazeclient.implement.menu.components.implement.other.BackgroundComponent;
import vorga.phazeclient.implement.menu.components.implement.other.CategoryContainerComponent;
import vorga.phazeclient.implement.menu.components.implement.other.ModuleDetailComponent;
import vorga.phazeclient.implement.menu.components.implement.other.ModuleDescriptionComponent;
import vorga.phazeclient.implement.menu.components.implement.other.SearchComponent;
import vorga.phazeclient.implement.menu.components.implement.settings.TextComponent;
import vorga.phazeclient.implement.menu.components.implement.settings.multiselect.MultiSelectComponent;
import vorga.phazeclient.implement.menu.components.implement.settings.select.SelectComponent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.Window;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static vorga.phazeclient.api.system.animation.Direction.BACKWARDS;
import static vorga.phazeclient.api.system.animation.Direction.FORWARDS;

@Setter
@Getter
public class MenuScreen extends Screen implements QuickImports {
    private static final double PREFERRED_OVERLAY_SCALE = 2.0D;
    private static final double MIN_OVERLAY_SCALE = 1.0D;
    private static final int DEFAULT_WIDTH = 462;
    private static final int DEFAULT_HEIGHT = 300;
    private static final int MIN_WIDTH = 382;
    private static final int MIN_HEIGHT = 248;
    private static final int SCREEN_MARGIN = 24;
    private static final float CATEGORY_START_X = MenuLayout.SIDEBAR_WIDTH + MenuLayout.CONTENT_INSET;
    private static final float CONTENT_EDGE_GAP = MenuLayout.CONTENT_INSET - 1.0F;
    private static final float SEARCH_WIDTH = 82.0F;
    private static final float SEARCH_RIGHT_MARGIN = 1.0F + CONTENT_EDGE_GAP;
    private static final float CATEGORY_SEARCH_GAP = MenuLayout.PANEL_GAP + 4.0F;
    private static final float CATEGORY_ROW_Y = 35.0F + CONTENT_EDGE_GAP;
    private static final int HEADER_DRAG_HEIGHT = 34;
    private static final int TOP_TAB_TRANSITION_MS = 145;

    public static MenuScreen INSTANCE = new MenuScreen();
    private final List<AbstractComponent> components = new ArrayList<>();
    private final BackgroundComponent backgroundComponent = new BackgroundComponent();
    private final SearchComponent searchComponent = new SearchComponent();
    private final CategoryContainerComponent categoryContainerComponent = new CategoryContainerComponent();
    private final ModuleDescriptionComponent moduleDescriptionComponent = new ModuleDescriptionComponent();
    private final ModuleDetailComponent moduleDetailComponent = new ModuleDetailComponent();
    private final vorga.phazeclient.implement.menu.components.implement.other.ConfigsViewComponent configsView =
            new vorga.phazeclient.implement.menu.components.implement.other.ConfigsViewComponent();
    private final vorga.phazeclient.implement.menu.components.implement.other.CosmeticsViewComponent cosmeticsView =
            new vorga.phazeclient.implement.menu.components.implement.other.CosmeticsViewComponent();
    private final vorga.phazeclient.implement.menu.components.implement.other.ConfigShareModalComponent configShareModal =
            new vorga.phazeclient.implement.menu.components.implement.other.ConfigShareModalComponent();
    private final vorga.phazeclient.implement.menu.components.implement.other.MessengerViewComponent messengerView =
            new vorga.phazeclient.implement.menu.components.implement.other.MessengerViewComponent();
    private final vorga.phazeclient.implement.menu.components.implement.other.WaypointEditorModal waypointEditor =
            new vorga.phazeclient.implement.menu.components.implement.other.WaypointEditorModal();
    @Getter(lombok.AccessLevel.NONE)
    private final MenuFrameCache frameCache = new MenuFrameCache();
    public final Animation animation = new LinearAnimation().setMs(200).setValue(1);
    private final Animation topTabTransitionAnimation =
            new DecelerateAnimation().setMs(TOP_TAB_TRANSITION_MS).setValue(1);
    private TopTab activeTopTab = TopTab.MODS;
    private TopTab pendingTopTab;
    public ModuleCategory category = ModuleCategory.ALL;
    public int x, y, width, height;

    private boolean menuDragging = false;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;
    private int customX = -1;
    private int customY = -1;
    private double overlayScaleFactor = PREFERRED_OVERLAY_SCALE;
    private float overlayRenderScale = 1.0F;
    private int overlayViewportWidth = -1;
    private int overlayViewportHeight = -1;

    private final Animation horizontalGuideAnimation = new DecelerateAnimation().setMs(150).setValue(1);
    private final Animation verticalGuideAnimation = new DecelerateAnimation().setMs(150).setValue(1);

    public MenuScreen() {
        super(Text.of("MenuScreen"));
        initialize();
    }

    public static void preload() {
        INSTANCE.updateOverlayMetrics();
        INSTANCE.categoryContainerComponent.ensureCategoryComponentsInitialized();
        INSTANCE.prewarmUiIconAtlas();
    }

    public void initialize() {
        animation.setDirection(FORWARDS);
        topTabTransitionAnimation.setDirectionAndFinish(FORWARDS);
        horizontalGuideAnimation.setDirectionAndFinish(BACKWARDS);
        verticalGuideAnimation.setDirectionAndFinish(BACKWARDS);
        categoryContainerComponent.ensureCategoryComponentsInitialized();

        backgroundComponent.setSearchComponent(searchComponent);

        components.addAll(Arrays.asList(
                backgroundComponent,
                searchComponent,
                categoryContainerComponent,
                moduleDescriptionComponent,
                moduleDetailComponent
        ));
    }

    @Override
    public void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (menuDragging && client != null && client.getWindow() != null
                && GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) {
            menuDragging = false;
        }
        close();
        components.forEach(AbstractComponent::tick);
        super.tick();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        updateOverlayMetrics();
        Theme.getInstance().applyMenuTheme();
        updateTopTabTransition();
        super.render(context, mouseX, mouseY, delta);
        int overlayMouseX = (int) Math.round(toMenuCoordinateX(mouseX));
        int overlayMouseY = (int) Math.round(toMenuCoordinateY(mouseY));
        width = Math.min(DEFAULT_WIDTH, Math.max(MIN_WIDTH, getOverlayViewportWidth() - SCREEN_MARGIN));
        height = Math.min(DEFAULT_HEIGHT, Math.max(MIN_HEIGHT, getOverlayViewportHeight() - SCREEN_MARGIN));

        if (customX != -1 && customY != -1) {
            x = customX;
            y = customY;
        } else {
            x = getOverlayViewportWidth() / 2 - width / 2;
            y = getOverlayViewportHeight() / 2 - height / 2;
        }

        backgroundComponent.position(x, y).size(width, height);

        float categoryX = x + CATEGORY_START_X;
        float searchX = Math.max(
                x + width - SEARCH_WIDTH - SEARCH_RIGHT_MARGIN,
                categoryX + categoryContainerComponent.getTotalWidth() + CATEGORY_SEARCH_GAP
        );
        categoryContainerComponent.position(categoryX, y + CATEGORY_ROW_Y);
        searchComponent.position(searchX, y + CATEGORY_ROW_Y);

        context.getMatrices().pushMatrix();
        try {
            float guiScale = MenuUiSettings.getInstance().getGuiScale();
            float menuCenterX = x + width / 2.0F;
            float menuCenterY = y + height / 2.0F;
            applyMenuTransform(context.getMatrices(), overlayRenderScale, guiScale, menuCenterX, menuCenterY);
            float scaleAnimation = getScaleAnimation();
            float alphaAnimation = getAlphaAnimation();
            vorga.phazeclient.api.system.shape.batched.BatchedRectangle.beginScope();
            try {
                MathUtil.scale(context.getMatrices(), x + (float) width / 2, y + (float) height / 2, scaleAnimation, () -> renderGuiRegionBlur(context));
                long frameKey = frameCacheKey(overlayMouseX, overlayMouseY, guiScale, scaleAnimation, alphaAnimation);
                frameCache.render(context, frameKey, isFrameCacheable(), () ->
                MathUtil.scale(context.getMatrices(), x + (float) width / 2, y + (float) height / 2, scaleAnimation, () -> {
                    boolean moduleDetailOpen = moduleDetailComponent.isOpen();
                    boolean configsViewOpen = configsView.isOpen();
                    boolean cosmeticsViewOpen = cosmeticsView.isOpen();
                    boolean messengerViewOpen = messengerView.isOpen();
                    boolean modalOpen = configShareModal.isOpen();
                    boolean mouseOverWindow = windowManager.isMouseOverAnyWindow(overlayMouseX, overlayMouseY);
                    boolean editorOpen = waypointEditor.isOpen();
                    int routedMouseX = mouseOverWindow || modalOpen || editorOpen ? Integer.MIN_VALUE / 4 : overlayMouseX;
                    int routedMouseY = mouseOverWindow || modalOpen || editorOpen ? Integer.MIN_VALUE / 4 : overlayMouseY;
                    for (AbstractComponent component : components) {
                        if ((moduleDetailOpen || configsViewOpen || cosmeticsViewOpen || messengerViewOpen)
                                && (component == searchComponent || component == categoryContainerComponent
                                || component == moduleDescriptionComponent)) {
                            continue;
                        }
                        component.globalAlpha = alphaAnimation;
                        component.render(context, routedMouseX, routedMouseY, delta);
                    }
                    if (configsViewOpen) {
                        configsView.position(x, y).size(width, height);
                        configsView.globalAlpha = alphaAnimation;
                        configsView.render(context, routedMouseX, routedMouseY, delta);
                    }
                    if (cosmeticsViewOpen) {
                        cosmeticsView.position(x, y).size(width, height);
                        cosmeticsView.globalAlpha = alphaAnimation;
                        cosmeticsView.render(context, routedMouseX, routedMouseY, delta);
                    }
                    if (messengerView.shouldRender()) {
                        messengerView.position(x, y).size(width, height);
                        messengerView.globalAlpha = alphaAnimation;
                        messengerView.render(context, routedMouseX, routedMouseY, delta);
                    }
                    waypointEditor.position(x, y).size(width, height);
                    waypointEditor.globalAlpha = alphaAnimation;
                    waypointEditor.render(context, mouseOverWindow || modalOpen ? Integer.MIN_VALUE / 4 : overlayMouseX,
                            mouseOverWindow || modalOpen ? Integer.MIN_VALUE / 4 : overlayMouseY, delta);
                    windowManager.render(context, modalOpen ? Integer.MIN_VALUE / 4 : overlayMouseX,
                            modalOpen ? Integer.MIN_VALUE / 4 : overlayMouseY, delta);

                    configShareModal.position(x, y).size(width, height);
                    configShareModal.globalAlpha = alphaAnimation;
                    configShareModal.render(context, overlayMouseX, overlayMouseY, delta);
                }));
            } finally {
                vorga.phazeclient.api.system.shape.batched.BatchedRectangle.endScope();
            }
        } finally {
            context.getMatrices().popMatrix();
        }
        renderCenterGuides(context);
    }

    private long frameCacheKey(int mouseX, int mouseY, float guiScale, float scaleAnimation, float alphaAnimation) {
        Window window = MinecraftClient.getInstance().getWindow();
        long h = 17L;
        h = h * 31L + x;
        h = h * 31L + y;
        h = h * 31L + width;
        h = h * 31L + height;
        h = h * 31L + mouseX;
        h = h * 31L + mouseY;
        h = h * 31L + Float.floatToIntBits(overlayRenderScale);
        h = h * 31L + Float.floatToIntBits(guiScale);
        h = h * 31L + Float.floatToIntBits(scaleAnimation);
        h = h * 31L + Float.floatToIntBits(alphaAnimation);
        h = h * 31L + (window != null ? window.getFramebufferWidth() : 0);
        h = h * 31L + (window != null ? window.getFramebufferHeight() : 0);
        h = h * 31L + activeTopTab.ordinal();
        h = h * 31L + category.ordinal();
        h = h * 31L + System.identityHashCode(moduleDetailComponent.isOpen() ? moduleDetailComponent.getModule() : null);
        h = h * 31L + (configsView.isOpen() ? 1 : 0) + (cosmeticsView.isOpen() ? 2 : 0)
                + (messengerView.isOpen() ? 4 : 0) + (messengerView.shouldRender() ? 8 : 0);
        return h;
    }

    /** Overlay windows and modals sample the frame under them, so they are drawn live. */
    private boolean isFrameCacheable() {
        return windowManager.getWindows().isEmpty() && !configShareModal.isOpen() && !waypointEditor.isOpen()
                && !menuDragging;
    }

    private void invalidateFrame() {
        frameCache.invalidate();
    }

    @Override
    public void removed() {
        frameCache.release();
        super.removed();
    }

    static void applyMenuTransform(org.joml.Matrix3x2fStack matrices, float overlayScale, float guiScale,
                                   float centerX, float centerY) {
        // The overlay is the outer coordinate space, including the GUI-scale pivot.
        matrices.scale(overlayScale, overlayScale);
        matrices.translate(centerX, centerY);
        matrices.scale(guiScale, guiScale);
        matrices.translate(-centerX, -centerY);
    }

    private void renderGuiRegionBlur(DrawContext context) {
        float blurRadius = Theme.getInstance().getMenuBlurRadius() * getScaleAnimation();
        if (blurRadius <= 0.0F) {
            return;
        }

        Blur.INSTANCE.renderGaussian(ShapeProperties.create(context.getMatrices(), x, y, width, height)
                .round(8.0F)
                .softness(1.2F)
                .quality(blurRadius * 2.0F)
                .color(0xFFFFFFFF)
                .build());
    }

    private void renderCenterGuides(DrawContext context) {
        Window currentWindow = MinecraftClient.getInstance().getWindow();
        int windowWidth = currentWindow != null ? currentWindow.getScaledWidth() : getOverlayViewportWidth();
        int windowHeight = currentWindow != null ? currentWindow.getScaledHeight() : getOverlayViewportHeight();
        int radius = 3;

        boolean nearHorizontalCenter = false;
        boolean nearVerticalCenter = false;

        if (menuDragging) {
            nearHorizontalCenter = Math.abs(x - (getOverlayViewportWidth() - width) / 2.0F) <= radius;
            nearVerticalCenter = Math.abs(y - (getOverlayViewportHeight() - height) / 2.0F) <= radius;
        }

        horizontalGuideAnimation.setDirection(nearHorizontalCenter ? FORWARDS : BACKWARDS);
        verticalGuideAnimation.setDirection(nearVerticalCenter ? FORWARDS : BACKWARDS);

        float horizontalAlpha = horizontalGuideAnimation.getOutputFloat() * 0.5F;
        float verticalAlpha = verticalGuideAnimation.getOutputFloat() * 0.5F;

        if (horizontalAlpha <= 0.0F && verticalAlpha <= 0.0F) return;
        // 1.21.11 uses GUI layers, not a z translation or an eager DrawContext flush.
        context.createNewRootLayer();
        if (horizontalAlpha > 0.0F) {
            drawGuideLine(context, windowWidth / 2, 0, 1, windowHeight, horizontalAlpha);
        }

        if (verticalAlpha > 0.0F) {
            drawGuideLine(context, 0, windowHeight / 2, windowWidth, 1, verticalAlpha);
        }
    }

    protected void renderDarkening(DrawContext context) {
    }

    protected void renderDarkening(DrawContext context, int x, int y, int width, int height) {
    }

    @Override
    protected void applyBlur(DrawContext context) {
    }

    private void drawGuideLine(DrawContext context, float x, float y, float width, float height, float alpha) {
        context.fill((int) Math.floor(x), (int) Math.floor(y), (int) Math.ceil(x + width), (int) Math.ceil(y + height), ColorUtil.getText(alpha));
    }

    public void openGui() {
        invalidateFrame();
        updateOverlayMetrics();
        animation.setDirection(Direction.FORWARDS);
        categoryContainerComponent.ensureCategoryComponentsInitialized();
        prewarmUiIconAtlas();
        closeModuleDetail();

        closeConfigsView();
        closeCosmeticsView();
        closeMessengerView();
        waypointEditor.close();
        activeTopTab = TopTab.MODS;
        pendingTopTab = null;
        topTabTransitionAnimation.setDirectionAndFinish(FORWARDS);
        category = ModuleCategory.ALL;
        Theme.getInstance().applyMenuTheme();

        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.setScreen(this);
            if ("1".equals(System.getenv("PHAZE_GUI_AUTOTEST_COSMETICS"))) {
                openCosmeticsView();
            }
        }
    }

    private void prewarmUiIconAtlas() {
        UiMsdfIconAtlas.registerTexture("phaze:textures/menu/options_settings.png");
        UiMsdfIconAtlas.registerTexture("textures/settings.png");
        UiMsdfIconAtlas.registerTexture("textures/cross.png");
        UiMsdfIconAtlas.registerTexture("textures/trash.png");
        UiMsdfIconAtlas.registerTexture("textures/back_arrow.png");
        UiMsdfIconAtlas.registerTexture("textures/search_lunar.png");
        UiMsdfIconAtlas.registerTexture("textures/edit.png");
        UiMsdfIconAtlas.registerTexture("textures/share.png");
        UiMsdfIconAtlas.registerTexture("textures/file.png");
        UiMsdfIconAtlas.registerTexture("textures/file_import.png");
        UiMsdfIconAtlas.registerTexture("textures/size.png");
        UiMsdfIconAtlas.registerTexture("textures/cloud.png");
        UiMsdfIconAtlas.registerTexture("textures/clock.png");
        UiMsdfIconAtlas.registerTexture("textures/reset.png");
        UiMsdfIconAtlas.registerTexture("phaze:textures/menu/phaze_brand.png");
        UiMsdfIconAtlas.registerTexture("phaze:textures/cosmetics/category_all.png");
        UiMsdfIconAtlas.registerTexture("phaze:textures/cosmetics/category_wings.png");
        UiMsdfIconAtlas.registerTexture("phaze:textures/cosmetics/category_capes.png");
        UiMsdfIconAtlas.registerTexture("phaze:textures/cosmetics/category_hats.png");
        UiMsdfIconAtlas.registerTexture("phaze:textures/cosmetics/category_pets.png");

        Main main = Main.getInstance();
        if (main != null && main.getModuleProvider() != null) {
            for (Module module : main.getModuleProvider().getModules()) {
                String texture = module.getIcon() != null
                        ? "phaze:textures/modules/" + module.getIcon()
                        : "phaze:textures/modules/hand.png";
                UiMsdfIconAtlas.registerTexture(texture);
            }
        }

        UiMsdfIconAtlas.warmup();
    }

    public void openModuleDetail(vorga.phazeclient.api.feature.module.Module module) {
        if (module == null || module.isServerLocked()) {
            return;
        }
        closeCosmeticsView();
        closeConfigsView();
        closeMessengerView();
        windowManager.closeAll();
        SelectComponent.closeAllDropdowns();
        MultiSelectComponent.closeAllDropdowns();
        moduleDescriptionComponent.hide();
        moduleDetailComponent.open(module);
    }

    public void closeModuleDetail() {
        windowManager.closeAll();
        moduleDetailComponent.closeDetail();
    }

    public void openConfigShareModal() {
        configShareModal.position(x, y).size(width, height);
        configShareModal.openShare(null);
    }

    public void openConfigShareModal(String configName) {
        configShareModal.position(x, y).size(width, height);
        configShareModal.openShare(configName);
    }

    public void openConfigRenameModal(String configName, Runnable onRenamed) {
        configShareModal.position(x, y).size(width, height);
        configShareModal.openRename(configName, onRenamed);
    }

    public void openConfigImportModal() {
        openConfigImportModal("");
    }

    public void openConfigImportModal(String code) {
        configShareModal.position(x, y).size(width, height);
        configShareModal.openImport(configsView::refreshAfterImport, code);
    }

    public boolean isConfigShareModalOpen() {
        return configShareModal.isOpen();
    }

    public void openWaypointEditor(String worldKey, vorga.phazeclient.implement.waypoints.Waypoint waypoint, boolean existing,
                                   String serverLabel) {
        prepareWaypointEditor();
        waypointEditor.open(worldKey, waypoint, existing, serverLabel);
    }

    public void openWaypointSender(vorga.phazeclient.implement.waypoints.Waypoint draft,
                                   vorga.phazeclient.implement.menu.components.implement.other.WaypointEditorModal.CardSubmit onSend) {
        prepareWaypointEditor();
        waypointEditor.openForSend(draft, onSend);
    }

    public void openWaypointCardEditor(String serverKey, String realm, vorga.phazeclient.implement.waypoints.Waypoint card,
                                       String serverLabel,
                                       vorga.phazeclient.implement.menu.components.implement.other.WaypointEditorModal.CardSubmit onSave,
                                       Runnable onDelete) {
        prepareWaypointEditor();
        waypointEditor.openForCardEdit(serverKey, realm, card, serverLabel, onSave, onDelete);
    }

    private void prepareWaypointEditor() {
        windowManager.closeAll();
        SelectComponent.closeAllDropdowns();
        MultiSelectComponent.closeAllDropdowns();
        waypointEditor.position(x, y).size(width, height);
    }

    public boolean isWaypointEditorOpen() {
        return waypointEditor.isOpen();
    }

    public void openConfigsView() {
        closeModuleDetail();
        closeCosmeticsView();
        closeMessengerView();
        configsView.position(x, y).size(width, height);
        configsView.open();
    }

    public void closeConfigsView() {
        configsView.close();
    }

    public boolean isConfigsViewOpen() {
        return configsView.isActive();
    }

    public void openCosmeticsView() {
        closeModuleDetail();
        closeConfigsView();
        closeMessengerView();
        cosmeticsView.position(x, y).size(width, height);
        cosmeticsView.open();
    }

    public void closeCosmeticsView() {
        cosmeticsView.close();
    }

    public boolean isCosmeticsViewOpen() {
        return cosmeticsView.isOpen();
    }

    public void openMessengerView() {
        closeModuleDetail();
        closeConfigsView();
        closeCosmeticsView();
        messengerView.position(x, y).size(width, height);
        messengerView.open();
    }

    public void closeMessengerView() {
        messengerView.close();
    }

    public boolean isMessengerViewOpen() {
        return messengerView.isOpen();
    }

    public void requestTopTab(String label) {
        TopTab requested = TopTab.fromLabel(label);
        if (requested == null || (requested == activeTopTab && pendingTopTab == null)) {
            return;
        }

        // Top-level pages switch immediately; their content is already ready.
        pendingTopTab = null;
        applyTopTab(requested);
        activeTopTab = requested;
        topTabTransitionAnimation.setDirectionAndFinish(FORWARDS);
    }

    public boolean isTopTabTransitioning() {
        return false;
    }

    private void updateTopTabTransition() {
        if (pendingTopTab == null || !topTabTransitionAnimation.isFinished(BACKWARDS)) {
            return;
        }

        TopTab next = pendingTopTab;
        pendingTopTab = null;
        applyTopTab(next);
        activeTopTab = next;
        topTabTransitionAnimation.setDirection(FORWARDS);
    }

    private void applyTopTab(TopTab tab) {
        switch (tab) {
            case SETTINGS -> {
                closeConfigsView();
                closeCosmeticsView();
                closeMessengerView();
                openModuleDetail(Theme.getInstance());
            }
            case COSMETICS -> openCosmeticsView();
            case CONFIGS -> openConfigsView();
            case CHAT -> openMessengerView();
            case MODS -> {
                closeModuleDetail();
                closeConfigsView();
                closeCosmeticsView();
                closeMessengerView();
                setCategory(ModuleCategory.ALL);
            }
        }
    }

    private enum TopTab {
        MODS,
        SETTINGS,
        COSMETICS,
        CONFIGS,
        CHAT;

        private static TopTab fromLabel(String label) {
            if (label == null) {
                return null;
            }
            try {
                return valueOf(label);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
    }

    public float getScaleAnimation() {
        return animation.getOutputFloat();
    }

    public float getAlphaAnimation() {
        float progress = animation.getOutputFloat();
        return progress * progress;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        invalidateFrame();
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        updateOverlayMetrics();
        double overlayMouseX = toMenuCoordinateX(mouseX);
        double overlayMouseY = toMenuCoordinateY(mouseY);
        boolean insideMenu = MathUtil.isHovered(overlayMouseX, overlayMouseY, x, y, width, height);
        boolean clickInsideWindow = windowManager.isMouseOverAnyWindow(overlayMouseX, overlayMouseY);

        if (!clickInsideWindow) {
            windowManager.closeAll();
        }

        if (waypointEditor.isOpen()) {
            if (clickInsideWindow) windowManager.mouseClicked(overlayMouseX, overlayMouseY, button);
            else waypointEditor.mouseClicked(overlayMouseX, overlayMouseY, button);
            return true;
        }

        if (configShareModal.isOpen()) {
            configShareModal.position(x, y).size(width, height);
            configShareModal.mouseClicked(overlayMouseX, overlayMouseY, button);
            return true;
        }

        // Global navigation must not be swallowed by the currently open full-page view.
        if (!clickInsideWindow && backgroundComponent.mouseClicked(overlayMouseX, overlayMouseY, button)) {
            return true;
        }

        if (shouldStartMenuDrag(overlayMouseX, overlayMouseY, button, insideMenu)) {
            startMenuDrag(mouseX, mouseY);
            return true;
        }

        if (configsView.isOpen()) {
            configsView.position(x, y).size(width, height);
            if (configsView.mouseClicked(overlayMouseX, overlayMouseY, button)) {
                return true;
            }

            if (backgroundComponent.mouseClicked(overlayMouseX, overlayMouseY, button)) {
                return true;
            }
            return true;
        }

        if (cosmeticsView.isOpen()) {
            cosmeticsView.position(x, y).size(width, height);
            if (cosmeticsView.mouseClicked(overlayMouseX, overlayMouseY, button)) return true;
            backgroundComponent.mouseClicked(overlayMouseX, overlayMouseY, button);
            return true;
        }

        if (messengerView.isOpen()) {
            messengerView.position(x, y).size(width, height);
            if (messengerView.mouseClicked(overlayMouseX, overlayMouseY, button)) {
                return true;
            }
            if (backgroundComponent.mouseClicked(overlayMouseX, overlayMouseY, button)) {
                return true;
            }
            return true;
        }

        if (moduleDetailComponent.isOpen()) {
            boolean windowHandled = clickInsideWindow && windowManager.mouseClicked(overlayMouseX, overlayMouseY, button);
            boolean detailHandled = false;
            boolean backgroundHandled = false;

            if (!windowHandled) {
                detailHandled = moduleDetailComponent.mouseClicked(overlayMouseX, overlayMouseY, button);
                if (!detailHandled) {
                    backgroundHandled = backgroundComponent.mouseClicked(overlayMouseX, overlayMouseY, button);
                }
            }

            SelectComponent.handleGlobalClick(overlayMouseX, overlayMouseY);
            MultiSelectComponent.handleGlobalClick(overlayMouseX, overlayMouseY);

            if (detailHandled || backgroundHandled || windowHandled) {
                return true;
            }
            return true;
        }

        if (!clickInsideWindow || !windowManager.mouseClicked(overlayMouseX, overlayMouseY, button)) {
            for (AbstractComponent component : components) {
                if (component.mouseClicked(overlayMouseX, overlayMouseY, button)) {
                    return true;
                }
            }

            SelectComponent.handleGlobalClick(overlayMouseX, overlayMouseY);
            MultiSelectComponent.handleGlobalClick(overlayMouseX, overlayMouseY);
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseReleased(Click click) {
        invalidateFrame();
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        if (button == 0) {
            vorga.phazeclient.api.system.cursor.CursorManager.endDrag();
        }
        updateOverlayMetrics();
        double overlayMouseX = toMenuCoordinateX(mouseX);
        double overlayMouseY = toMenuCoordinateY(mouseY);
        if (configShareModal.isOpen()) {
            configShareModal.mouseReleased(overlayMouseX, overlayMouseY, button);
            return true;
        }
        if (waypointEditor.isOpen()) {
            if (windowManager.isMouseOverAnyWindow(overlayMouseX, overlayMouseY)) {
                windowManager.mouseReleased(overlayMouseX, overlayMouseY, button);
            } else {
                waypointEditor.mouseReleased(overlayMouseX, overlayMouseY, button);
            }
            return true;
        }
        if (menuDragging && button == 0) {
            menuDragging = false;
            return true;
        }

        if (moduleDetailComponent.isOpen()) {
            windowManager.mouseReleased(overlayMouseX, overlayMouseY, button);
            moduleDetailComponent.mouseReleased(overlayMouseX, overlayMouseY, button);
            return true;
        }

        if (cosmeticsView.isOpen()) {
            cosmeticsView.mouseReleased(overlayMouseX, overlayMouseY, button);
            return true;
        }

        if (messengerView.isOpen()) {
            messengerView.mouseReleased(overlayMouseX, overlayMouseY, button);
            return true;
        }

        components.forEach(component -> component.mouseReleased(overlayMouseX, overlayMouseY, button));
        windowManager.mouseReleased(overlayMouseX, overlayMouseY, button);
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        invalidateFrame();
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        updateOverlayMetrics();
        double overlayMouseX = toMenuCoordinateX(mouseX);
        double overlayMouseY = toMenuCoordinateY(mouseY);
        double guiScale = MenuUiSettings.getInstance().getGuiScale();
        double overlayDeltaX = toOverlayCoordinate(deltaX) / guiScale;
        double overlayDeltaY = toOverlayCoordinate(deltaY) / guiScale;
        if (configShareModal.isOpen()) {
            configShareModal.mouseDragged(overlayMouseX, overlayMouseY, button, overlayDeltaX, overlayDeltaY);
            return true;
        }
        if (waypointEditor.isOpen()) {
            if (!windowManager.mouseDragged(overlayMouseX, overlayMouseY, button, overlayDeltaX, overlayDeltaY)) {
                waypointEditor.mouseDragged(overlayMouseX, overlayMouseY, button, overlayDeltaX, overlayDeltaY);
            }
            return true;
        }
        if (menuDragging) {
            float mouseDragX = (float) (overlayMouseX + dragOffsetX);
            float mouseDragY = (float) (overlayMouseY + dragOffsetY);

            int windowWidth = getOverlayViewportWidth();
            int windowHeight = getOverlayViewportHeight();
            customX = (int) Math.max(0, Math.min(mouseDragX, windowWidth - width));
            customY = (int) Math.max(0, Math.min(mouseDragY, windowHeight - height));

            int radius = 3;
            if (Math.abs(mouseDragX - (windowWidth - width) / 2.0f) <= radius) {
                customX = (windowWidth - width) / 2;
            }

            if (Math.abs(mouseDragY - (windowHeight - height) / 2.0f) <= radius) {
                customY = (windowHeight - height) / 2;
            }

            return true;
        }

        if (moduleDetailComponent.isOpen()) {
            if (windowManager.mouseDragged(overlayMouseX, overlayMouseY, button, overlayDeltaX, overlayDeltaY)) {
                return true;
            }
            moduleDetailComponent.mouseDragged(overlayMouseX, overlayMouseY, button, overlayDeltaX, overlayDeltaY);
            return true;
        }

        if (cosmeticsView.isOpen()) {
            cosmeticsView.mouseDragged(
                    overlayMouseX, overlayMouseY, button,
                    overlayDeltaX, overlayDeltaY);
            return true;
        }

        if (messengerView.isOpen()) {
            messengerView.mouseDragged(overlayMouseX, overlayMouseY, button, overlayDeltaX, overlayDeltaY);
            return true;
        }

        if (!windowManager.mouseDragged(overlayMouseX, overlayMouseY, button, overlayDeltaX, overlayDeltaY)) {
            components.forEach(component -> component.mouseDragged(overlayMouseX, overlayMouseY, button, overlayDeltaX, overlayDeltaY));
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        invalidateFrame();

        vorga.phazeclient.api.system.cursor.CursorManager.notifyScroll(horizontal, vertical);

        updateOverlayMetrics();
        double overlayMouseX = toMenuCoordinateX(mouseX);
        double overlayMouseY = toMenuCoordinateY(mouseY);
        if (configShareModal.isOpen()) {
            configShareModal.mouseScrolled(overlayMouseX, overlayMouseY, vertical);
            return true;
        }
        if (moduleDetailComponent.isOpen()) {
            if (windowManager.mouseScrolled(overlayMouseX, overlayMouseY, vertical)) {
                return true;
            }
            if (backgroundComponent.mouseScrolled(overlayMouseX, overlayMouseY, vertical)) return true;
            moduleDetailComponent.mouseScrolled(overlayMouseX, overlayMouseY, vertical);
            return true;
        }

        if (configsView.isOpen()) {
            configsView.position(x, y).size(width, height);
            configsView.mouseScrolled(overlayMouseX, overlayMouseY, vertical);
            return true;
        }
        if (cosmeticsView.isOpen()) {
            cosmeticsView.position(x, y).size(width, height);
            cosmeticsView.mouseScrolled(overlayMouseX, overlayMouseY, vertical);
            return true;
        }
        if (messengerView.isOpen()) {
            messengerView.mouseScrolled(overlayMouseX, overlayMouseY, vertical);
            return true;
        }

        if (!windowManager.mouseScrolled(overlayMouseX, overlayMouseY, vertical)) {
            components.forEach(component -> component.mouseScrolled(overlayMouseX, overlayMouseY, vertical));
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        invalidateFrame();
        int keyCode = input.key();
        int scanCode = input.scancode();
        int modifiers = input.modifiers();

        if (waypointEditor.isOpen() && waypointEditor.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (configShareModal.isOpen()) {
            configShareModal.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        if (messengerView.isOpen() && messengerView.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (backgroundComponent.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        if (moduleDetailComponent.isOpen() && moduleDetailComponent.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        if (keyCode == 256 && shouldCloseOnEsc()) {
            if (animation.isDirection(FORWARDS)) {
                windowManager.closeAll();
                animation.setDirection(BACKWARDS);
            }
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_F && input.hasCtrlOrCmd() && !SearchComponent.typing) {
            SearchComponent.typing = true;
            searchComponent.setText("");
            searchComponent.setCursorPosition(0);
            searchComponent.setPreviousCategory(category != ModuleCategory.SEARCH ? category : ModuleCategory.ALL);
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_V && input.hasCtrlOrCmd()) {
            if (!windowManager.keyPressed(keyCode, scanCode, modifiers)) {
                components.forEach(component -> component.keyPressed(keyCode, scanCode, modifiers));
            }
            return true;
        }

        if (!windowManager.keyPressed(keyCode, scanCode, modifiers)) {
            components.forEach(component -> component.keyPressed(keyCode, scanCode, modifiers));
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean keyReleased(KeyInput input) {
        invalidateFrame();
        int keyCode = input.key();
        int scanCode = input.scancode();
        int modifiers = input.modifiers();
        if (configShareModal.isOpen()) {
            configShareModal.keyReleased(keyCode, scanCode, modifiers);
            return true;
        }
        if (backgroundComponent.keyReleased(keyCode, scanCode, modifiers)) {
            return true;
        }

        if (moduleDetailComponent.isOpen() && moduleDetailComponent.keyReleased(keyCode, scanCode, modifiers)) {
            return true;
        }

        if (!windowManager.keyReleased(keyCode, scanCode, modifiers)) {
            components.forEach(component -> component.keyReleased(keyCode, scanCode, modifiers));
        }
        return super.keyReleased(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        invalidateFrame();
        char chr = (char) input.codepoint();
        int modifiers = input.modifiers();
        if (waypointEditor.isOpen() && waypointEditor.charTyped(chr, modifiers)) {
            return true;
        }
        if (configShareModal.isOpen()) {
            configShareModal.charTyped(chr, modifiers);
            return true;
        }
        if (messengerView.isOpen() && messengerView.charTyped(chr, modifiers)) {
            return true;
        }
        if (backgroundComponent.charTyped(chr, modifiers)) {
            return true;
        }

        if (moduleDetailComponent.isOpen() && moduleDetailComponent.charTyped(chr, modifiers)) {
            return true;
        }

        if (!windowManager.charTyped(chr, modifiers)) {
            components.forEach(component -> component.charTyped(chr, modifiers));
        }
        return super.charTyped(input);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        if (animation.isFinished(BACKWARDS)) {
            SelectComponent.closeAllDropdowns();
            MultiSelectComponent.closeAllDropdowns();

            searchComponent.setText("");
            searchComponent.setCursorPosition(0);
            SearchComponent.typing = false;
            if (category == ModuleCategory.SEARCH) {
                category = searchComponent.getPreviousCategory();
            }
            windowManager.clear();
            super.close();
        }
    }

    private void updateOverlayMetrics() {
        Window currentWindow = MinecraftClient.getInstance().getWindow();
        if (currentWindow == null) {
            overlayScaleFactor = PREFERRED_OVERLAY_SCALE;
            overlayRenderScale = 1.0F;
            overlayViewportWidth = DEFAULT_WIDTH;
            overlayViewportHeight = DEFAULT_HEIGHT;
            return;
        }

        int framebufferWidth = Math.max(1, currentWindow.getFramebufferWidth());
        int framebufferHeight = Math.max(1, currentWindow.getFramebufferHeight());

        double maxScaleByWidth = framebufferWidth / (double) (MIN_WIDTH + SCREEN_MARGIN);
        double maxScaleByHeight = framebufferHeight / (double) (MIN_HEIGHT + SCREEN_MARGIN);
        double safePreferredScale = Math.min(PREFERRED_OVERLAY_SCALE, Math.min(maxScaleByWidth, maxScaleByHeight));

        overlayScaleFactor = Math.max(MIN_OVERLAY_SCALE, safePreferredScale);
        overlayRenderScale = (float) (overlayScaleFactor / currentWindow.getScaleFactor());
        overlayViewportWidth = Math.max(1, (int) Math.floor(framebufferWidth / overlayScaleFactor));
        overlayViewportHeight = Math.max(1, (int) Math.floor(framebufferHeight / overlayScaleFactor));
    }

    public int getOverlayViewportWidth() {
        Window currentWindow = MinecraftClient.getInstance().getWindow();
        return overlayViewportWidth > 0 ? overlayViewportWidth : currentWindow != null ? currentWindow.getScaledWidth() : DEFAULT_WIDTH;
    }

    public int getOverlayViewportHeight() {
        Window currentWindow = MinecraftClient.getInstance().getWindow();
        return overlayViewportHeight > 0 ? overlayViewportHeight : currentWindow != null ? currentWindow.getScaledHeight() : DEFAULT_HEIGHT;
    }

    public int clampOverlayX(double value, float elementWidth) {
        return (int) Math.max(0, Math.min(value, getOverlayViewportWidth() - elementWidth));
    }

    public int clampOverlayY(double value, float elementHeight) {
        return (int) Math.max(0, Math.min(value, getOverlayViewportHeight() - elementHeight));
    }

    public float toOverlayCoordinate(double value) {
        return overlayRenderScale == 0.0F ? (float) value : (float) (value / overlayRenderScale);
    }

    private double toMenuCoordinateX(double value) {
        float overlay = toOverlayCoordinate(value);
        return x + width / 2.0F + (overlay - (x + width / 2.0F))
                / MenuUiSettings.getInstance().getGuiScale();
    }

    private double toMenuCoordinateY(double value) {
        float overlay = toOverlayCoordinate(value);
        return y + height / 2.0F + (overlay - (y + height / 2.0F))
                / MenuUiSettings.getInstance().getGuiScale();
    }

    private void startMenuDrag(double mouseX, double mouseY) {
        menuDragging = true;
        dragOffsetX = (int) (x - toMenuCoordinateX(mouseX));
        dragOffsetY = (int) (y - toMenuCoordinateY(mouseY));
    }

    private boolean shouldStartMenuDrag(double overlayMouseX, double overlayMouseY, int button, boolean insideMenu) {

        if (configShareModal.isOpen() || configsView.isPopupOpen()) {
            return false;
        }
        if (button != 0 || !insideMenu) {
            return false;
        }
        if (cosmeticsView.isOpen() && overlayMouseY >= y + HEADER_DRAG_HEIGHT) {
            return false;
        }
        if ((messengerView.isOpen() || configsView.isActive())
                && overlayMouseY >= y + HEADER_DRAG_HEIGHT) {
            return false;
        }

        boolean inHeaderDragZone = MathUtil.isHovered(
                overlayMouseX,
                overlayMouseY,
                x,
                y,
                width,
                HEADER_DRAG_HEIGHT
        );
        if (inHeaderDragZone) {
            return !backgroundComponent.isInteractiveHover(overlayMouseX, overlayMouseY)
                    && !windowManager.isMouseOverAnyWindow(overlayMouseX, overlayMouseY);
        }

        return !isPointerOverInteractiveElement(overlayMouseX, overlayMouseY);
    }

    private boolean isPointerOverInteractiveElement(double overlayMouseX, double overlayMouseY) {
        if (windowManager.isMouseOverAnyWindow(overlayMouseX, overlayMouseY)) {
            return true;
        }

        if (backgroundComponent.isInteractiveHover(overlayMouseX, overlayMouseY)) {
            return true;
        }

        if (moduleDetailComponent.isOpen()) {
            return moduleDetailComponent.isInteractiveHover(overlayMouseX, overlayMouseY);
        }

        if ((messengerView.isOpen() || configsView.isActive())
                && MathUtil.isHovered(overlayMouseX, overlayMouseY, x, y + HEADER_DRAG_HEIGHT,
                width, height - HEADER_DRAG_HEIGHT)) {
            return true;
        }

        if (searchComponent.isHover(overlayMouseX, overlayMouseY)) {
            return true;
        }

        return categoryContainerComponent.isInteractiveHover(overlayMouseX, overlayMouseY);
    }
}
