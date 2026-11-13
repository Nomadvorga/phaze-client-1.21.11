package vorga.phazeclient.implement.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.render.SpecialGuiElementRenderer;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.command.RenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Quaternionf;
import vorga.phazeclient.api.system.hud.HudPassState;

/** Registered with Fabric's SpecialGuiElementRegistry in ClientMain. */
public final class PaperDollGuiElementRenderer extends SpecialGuiElementRenderer<PaperDollGuiElementState> {
    public PaperDollGuiElementRenderer(VertexConsumerProvider.Immediate consumers) { super(consumers); }
    @Override public Class<PaperDollGuiElementState> getElementClass() { return PaperDollGuiElementState.class; }
    @Override public void render(PaperDollGuiElementState state, GuiRenderState guiState, int guiScale) {
        if (state.x2() <= state.x1() || state.y2() <= state.y1() || state.scale() <= 0) return;
        try (HudPassState saved = HudPassState.capture()) { super.render(state, guiState, guiScale); }
    }
    @Override protected void render(PaperDollGuiElementState state, MatrixStack matrices) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.gameRenderer.getDiffuseLighting().setShaderLights(DiffuseLighting.Type.ENTITY_IN_UI);
        EntityRenderManager entities = client.getEntityRenderDispatcher();
        RenderDispatcher dispatcher = client.gameRenderer.getEntityRenderDispatcher();
        CameraRenderState camera = new CameraRenderState();
        camera.orientation = state.cameraAngle().conjugate(new Quaternionf()).rotateY((float) Math.PI);
        matrices.translate(state.translation().x, state.translation().y, state.translation().z);
        matrices.multiply(state.rotation());
        PaperDollRenderer.setDrawing(true);
        try {
            for (PaperDollGuiElementState.Part part : state.parts()) {
                entities.render(part.state(), camera, part.x(), part.y(), part.z(), matrices, dispatcher.getQueue());
            }
            dispatcher.render();
        } finally { PaperDollRenderer.setDrawing(false); }
    }
    @Override protected float getYOffset(int height, int guiScale) { return height / 2.0F; }
    @Override protected String getName() { return "phaze paper doll"; }
}
