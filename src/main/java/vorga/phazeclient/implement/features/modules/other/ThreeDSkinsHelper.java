package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;
import net.minecraft.client.model.ModelPart;

/**
 * 3D Skins renderer that operates in pure additive/subtractive mode:
 * 1. preRender: Hides the vanilla 2D second layers on the model so vanilla does not draw them.
 * 2. postRender: Renders clean extruded 3D voxels attached directly to the model's limb transforms.
 *
 * This completely avoids mutating ModelPart.cuboids lists, guaranteeing 100% compatibility
 * with Sodium, Iris, ImmediatelyFast, and Entity Culling.
 */
public final class ThreeDSkinsHelper {

    private static boolean activeForCurrentEntity = false;
    private static ThreeDSkinsVoxelizer.BakedSkinLayers currentBaked = null;
    private static boolean currentIsSlim = false;
    private static boolean drawHat, drawJacket, drawLeftSleeve, drawRightSleeve, drawLeftPants, drawRightPants;

    private ThreeDSkinsHelper() {}

    /**
     * Must run before the model is submitted: the queued model draw calls setAngles(state) later,
     * and that copies the layer visibility from the render state onto the model parts.
     */
    public static void preRender(PlayerEntityRenderState state) {
        activeForCurrentEntity = false;
        currentBaked = null;

        if (state == null || state.invisible) return;

        ThreeDSkins module = ThreeDSkins.getInstance();
        if (module == null || !module.isEnabled()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        boolean isLocal = player != null && state.id == player.getId();
        if (!isLocal && !module.showOthers.isValue()) return;

        // Distance LOD check
        if (player != null && !isLocal) {
            double dx = player.getX() - state.x;
            double dy = player.getY() - state.y;
            double dz = player.getZ() - state.z;
            double maxDist = module.maxDistance.getValue();
            if (dx * dx + dy * dy + dz * dz > maxDist * maxDist) return;
        }

        Identifier skinId = state.skinTextures != null ? state.skinTextures.body().texturePath() : null;
        if (skinId == null) return;

        ThreeDSkinsVoxelizer.BakedSkinLayers baked = ThreeDSkinsVoxelizer.get(skinId);
        if (baked == null || !baked.hasAny()) return;

        activeForCurrentEntity = true;
        currentBaked = baked;
        currentIsSlim = state.skinTextures != null && state.skinTextures.model() == PlayerSkinType.SLIM;

        // Replace vanilla flat 2D layers with 3D voxels, keeping the player's skin-part choices.
        List<ThreeDSkinsVoxelizer.VoxelQuad> leftSleeve = currentIsSlim ? baked.leftSleeveSlim : baked.leftSleeveWide;
        List<ThreeDSkinsVoxelizer.VoxelQuad> rightSleeve = currentIsSlim ? baked.rightSleeveSlim : baked.rightSleeveWide;
        drawHat = state.hatVisible && module.hat3D.isValue() && !baked.hat.isEmpty();
        drawJacket = state.jacketVisible && module.jacket3D.isValue() && !baked.jacket.isEmpty();
        drawLeftSleeve = state.leftSleeveVisible && module.sleeves3D.isValue() && leftSleeve != null && !leftSleeve.isEmpty();
        drawRightSleeve = state.rightSleeveVisible && module.sleeves3D.isValue() && rightSleeve != null && !rightSleeve.isEmpty();
        drawLeftPants = state.leftPantsLegVisible && module.pants3D.isValue() && !baked.leftPants.isEmpty();
        drawRightPants = state.rightPantsLegVisible && module.pants3D.isValue() && !baked.rightPants.isEmpty();
        if (drawHat) state.hatVisible = false;
        if (drawJacket) state.jacketVisible = false;
        if (drawLeftSleeve) state.leftSleeveVisible = false;
        if (drawRightSleeve) state.rightSleeveVisible = false;
        if (drawLeftPants) state.leftPantsLegVisible = false;
        if (drawRightPants) state.rightPantsLegVisible = false;
    }

    public static void postRender(
            PlayerEntityModel model,
            PlayerEntityRenderState state,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            int overlay
    ) {
        if (!activeForCurrentEntity || currentBaked == null || model == null || state == null) {
            activeForCurrentEntity = false;
            currentBaked = null;
            return;
        }

        Identifier skinId = state.skinTextures != null ? state.skinTextures.body().texturePath() : null;
        if (skinId == null) {
            activeForCurrentEntity = false;
            currentBaked = null;
            return;
        }

        // Features only pose the shared model when the entity has features to draw.
        model.setAngles(state);

        // Skin pixels are binary alpha, not blended glass. Cutout keeps the
        // pass in the opaque/cutout pipeline and avoids the expensive sorted
        // translucent queue for every player in a crowd.
        VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayers.entityCutoutNoCull(skinId));

        if (drawHat) renderPart(consumer, matrices, model.head, currentBaked.hat, light, overlay);
        if (drawJacket) renderPart(consumer, matrices, model.body, currentBaked.jacket, light, overlay);
        if (drawRightSleeve) renderPart(consumer, matrices, model.rightArm,
                currentIsSlim ? currentBaked.rightSleeveSlim : currentBaked.rightSleeveWide, light, overlay);
        if (drawLeftSleeve) renderPart(consumer, matrices, model.leftArm,
                currentIsSlim ? currentBaked.leftSleeveSlim : currentBaked.leftSleeveWide, light, overlay);
        if (drawRightPants) renderPart(consumer, matrices, model.rightLeg, currentBaked.rightPants, light, overlay);
        if (drawLeftPants) renderPart(consumer, matrices, model.leftLeg, currentBaked.leftPants, light, overlay);

        activeForCurrentEntity = false;
        currentBaked = null;
    }

    private static void renderPart(VertexConsumer consumer, MatrixStack matrices, ModelPart part,
                                   List<ThreeDSkinsVoxelizer.VoxelQuad> quads, int light, int overlay) {
        matrices.push();
        transformPart(matrices, part);
        matrices.scale(1.0f / 16.0f, 1.0f / 16.0f, 1.0f / 16.0f);
        renderQuads(consumer, matrices, quads, light, overlay);
        matrices.pop();
    }
    /** Vanilla applies the part origin in block units, then its orientation and scale. */
    private static void transformPart(MatrixStack matrices, ModelPart part) {
        part.applyTransform(matrices);
    }

    private static void renderQuads(
            VertexConsumer consumer,
            MatrixStack matrices,
            List<ThreeDSkinsVoxelizer.VoxelQuad> quads,
            int light,
            int overlay
    ) {
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f posMatrix = entry.getPositionMatrix();

        for (ThreeDSkinsVoxelizer.VoxelQuad quad : quads) {
            Vector3f normal = quad.normal();
            for (ThreeDSkinsVoxelizer.VoxelVertex v : quad.vertices()) {
                consumer.vertex(posMatrix, v.x(), v.y(), v.z())
                        .color(0xFFFFFFFF)
                        .texture(v.u(), v.v())
                        .overlay(overlay)
                        .light(light)
                        .normal(entry, normal.x(), normal.y(), normal.z());
            }
        }
    }
}
