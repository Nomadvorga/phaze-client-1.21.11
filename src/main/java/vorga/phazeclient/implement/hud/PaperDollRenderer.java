package vorga.phazeclient.implement.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3x2f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import vorga.phazeclient.implement.features.modules.hud.PaperDoll;
import vorga.phazeclient.mixins.DrawContextStateAccessor;
import java.util.ArrayList;
import java.util.List;

/** Extracts posed render states, restoring entities before any deferred rendering. */
public final class PaperDollRenderer {
    private static final long AUTO_HIDE_LINGER_MS = 500;
    private static final float RADIANS = (float) (Math.PI / 180);
    private static final int MAX_ENTITIES = 8;
    private static long visibleUntilMs;
    private static boolean drawing;
    private PaperDollRenderer() { }
    public static boolean isDrawing() { return drawing; }
    static void setDrawing(boolean value) { drawing = value; }
    public static Entity subject(MinecraftClient client) {
        Entity camera = client.getCameraEntity(); return camera != null ? camera : client.player;
    }
    public static boolean isHidden(MinecraftClient client, PaperDoll doll, Entity subject) {
        if (doll.hideDuringF3.isValue() && client.getDebugHud().shouldShowDebugHud()) return true;
        if (doll.hideDuringScreens.isValue() && client.currentScreen != null) return true;
        if (doll.hideInF5.isValue() && !client.options.getPerspective().isFirstPerson()) return true;
        if (doll.autoHide.isValue() && subject instanceof LivingEntity living) {
            long now = Util.getMeasuringTimeMs();
            if (isBusy(living)) visibleUntilMs = now + AUTO_HIDE_LINGER_MS;
            else if (now > visibleUntilMs) return true;
        }
        return false;
    }
    private static boolean isBusy(LivingEntity entity) {
        return entity.isInSneakingPose() || entity.isSprinting() || entity.isGliding() || entity.isInSwimmingPose()
                || entity.hasVehicle() || entity.isBlocking() || entity.isUsingItem() || entity.handSwinging
                || entity.hurtTime > 0 || entity.isOnFire() || entity.inPowderSnow;
    }
    public static void draw(DrawContext context, PaperDoll doll, Entity subject, float feetX, float feetY, float size, float tickDelta) {
        if (subject == null || !Float.isFinite(size) || size <= 0) return;
        float turn = (float) Math.atan(-doll.lookingSides.getValue() / 40);
        float tilt = (float) Math.atan(doll.lookingUpDown.getValue() / 40);
        float yaw = 180 + turn * 40, bodyYaw = 180 + turn * 20, fixedPitch = -tilt * 20;
        float panelScale = size / PaperDoll.BASE_DOLL_SIZE;
        // Keep panel bounds stable while lifting a horizontal body within it.
        int x1 = Math.round(feetX - PaperDoll.FEET_X * panelScale), y1 = Math.round(feetY - PaperDoll.FEET_Y * panelScale);
        int x2 = Math.round(x1 + PaperDoll.BASE_WIDTH * panelScale), y2 = Math.round(y1 + PaperDoll.BASE_HEIGHT * panelScale);
        if (subject instanceof LivingEntity living && (living.isGliding() || living.isUsingRiptide())) {
            float ticks = living.getGlidingTicks() + tickDelta;
            float progress = MathHelper.clamp(ticks * ticks / 100, 0, 1);
            feetY -= size * (1 + progress / 90) - size * 0.2F;
        }
        Entity root = subject.getRootVehicle();
        List<Snapshot> snapshots = collect(subject, root);
        Vec3d rootPos = root.getLerpedPos(tickDelta), subjectPos = subject.getLerpedPos(tickDelta);
        float realFacing = root instanceof LivingEntity living ? MathHelper.lerpAngleDegrees(tickDelta, living.lastBodyYaw, living.bodyYaw)
                : root.getLerpedYaw(tickDelta);
        float turnBy = ((root instanceof LivingEntity ? bodyYaw : yaw) - realFacing) * RADIANS;
        float sin = MathHelper.sin(turnBy), cos = MathHelper.cos(turnBy);
        MinecraftClient client = MinecraftClient.getInstance();
        List<PaperDollGuiElementState.Part> parts = new ArrayList<>(snapshots.size());
        drawing = true;
        try {
            // Pose the whole group before extraction: vanilla's rider pose consults its vehicle.
            for (Snapshot snapshot : snapshots) pose(snapshot, doll, yaw, bodyYaw, fixedPitch);
            for (Snapshot snapshot : snapshots) {
                Entity entity = snapshot.entity;
                Vec3d position = entity.getLerpedPos(tickDelta);
                double dx = position.x - rootPos.x, dz = position.z - rootPos.z;
                EntityRenderState state = client.getEntityRenderDispatcher().getAndUpdateRenderState(entity, tickDelta);
                state.light = LightmapTextureManager.MAX_LIGHT_COORDINATE;
                state.shadowPieces.clear(); state.outlineColor = EntityRenderState.NO_OUTLINE;
                state.displayName = null; state.leashDatas = null; state.positionOffset = null;
                if (state instanceof PlayerEntityRenderState playerState) {
                    // These are extracted cape motion/flying fields; never modify live client velocity history.
                    playerState.field_53536 = 0; playerState.field_53537 = 0; playerState.field_53538 = 0;
                    playerState.applyFlyingRotation = false; playerState.flyingRotation = 0;
                }
                parts.add(new PaperDollGuiElementState.Part(state, dx * cos - dz * sin, position.y - subjectPos.y, dx * sin + dz * cos));
            }
        } finally {
            for (int i = snapshots.size() - 1; i >= 0; i--) snapshots.get(i).restore();
            drawing = false;
        }
        Quaternionf cameraAngle = new Quaternionf().rotationX(-tilt * 20 * RADIANS);
        Quaternionf orientation = new Quaternionf().rotationZ((float) Math.PI).mul(cameraAngle);
        Vector3f translation = new Vector3f((feetX - (x1 + x2) * 0.5F) / size, (feetY - (y1 + y2) * 0.5F) / size, 0);
        ((DrawContextStateAccessor) (Object) context).phaze$getGuiRenderState().addSpecialElement(
                new PaperDollGuiElementState(parts, orientation, cameraAngle, translation, x1, y1, x2, y2,
                        size, new Matrix3x2f(context.getMatrices()), context.scissorStack.peekLast()));
    }
    private static List<Snapshot> collect(Entity subject, Entity root) {
        List<Snapshot> result = new ArrayList<>(MAX_ENTITIES);
        if (root != subject) {
            result.add(new Snapshot(root));
            for (Entity passenger : root.getPassengerList()) {
                if (result.size() >= MAX_ENTITIES - 1) break;
                if (passenger != subject) result.add(new Snapshot(passenger));
            }
        }
        result.add(new Snapshot(subject)); return result;
    }
    private static void pose(Snapshot snapshot, PaperDoll doll, float yaw, float bodyYaw, float fixedPitch) {
        Entity entity = snapshot.entity;
        boolean pitchLocked = doll.pitchLocked();
        if (entity instanceof LivingEntity living) {
            boolean gliding = living.isGliding(), spinning = living.isUsingRiptide();
            if (gliding || spinning) entity.setVelocity(Vec3d.ZERO);
            pitchLocked |= gliding && doll.lockElytraRotation.isValue() || spinning && doll.lockSpinningRotation.isValue();
            living.bodyYaw = living.lastBodyYaw = bodyYaw;
            if (doll.headLocked()) living.headYaw = living.lastHeadYaw = yaw;
            else if (doll.headFollowsBody()) {
                living.headYaw = yaw + snapshot.headYaw - snapshot.bodyYaw;
                living.lastHeadYaw = yaw + snapshot.lastHeadYaw - snapshot.lastBodyYaw;
            } else {
                living.headYaw = yaw + snapshot.headYaw - snapshot.yaw;
                living.lastHeadYaw = yaw + snapshot.lastHeadYaw - snapshot.lastYaw;
            }
        }
        entity.setYaw(yaw); entity.lastYaw = yaw;
        if (pitchLocked) { entity.setPitch(fixedPitch); entity.lastPitch = fixedPitch; }
    }
    private static final class Snapshot {
        final Entity entity;
        final float yaw, lastYaw, pitch, lastPitch, bodyYaw, lastBodyYaw, headYaw, lastHeadYaw;
        final Vec3d velocity;
        Snapshot(Entity entity) {
            this.entity = entity; yaw = entity.getYaw(); lastYaw = entity.lastYaw;
            pitch = entity.getPitch(); lastPitch = entity.lastPitch; velocity = entity.getVelocity();
            if (entity instanceof LivingEntity living) {
                bodyYaw = living.bodyYaw; lastBodyYaw = living.lastBodyYaw; headYaw = living.headYaw; lastHeadYaw = living.lastHeadYaw;
            } else { bodyYaw = lastBodyYaw = headYaw = lastHeadYaw = 0; }
        }
        void restore() {
            entity.setYaw(yaw); entity.lastYaw = lastYaw; entity.setPitch(pitch); entity.lastPitch = lastPitch; entity.setVelocity(velocity);
            if (entity instanceof LivingEntity living) {
                living.bodyYaw = bodyYaw; living.lastBodyYaw = lastBodyYaw; living.headYaw = headYaw; living.lastHeadYaw = lastHeadYaw;
            }
        }
    }
}
