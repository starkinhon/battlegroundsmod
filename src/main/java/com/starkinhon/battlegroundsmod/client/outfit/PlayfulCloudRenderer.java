package com.starkinhon.battlegroundsmod.client.outfit;

import java.util.HashMap;
import java.util.Map;

import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.client.SkillAnimations;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Custom 3D renderer for Playful Cloud (Nuvem Brincalhona) with real-time dual-branch Verlet physics.
 * Section 2 (middle staff, rings on both ends) is held in hand centered at (0, 0, 0):
 *  - Front ring (+0.360) -> Chain 2 (3 links) -> Section 3 (front striking staff)
 *  - Back ring  (-0.360) -> Chain 1 (3 links) -> Section 1 (back counterweight staff)
 * Both branches react smoothly to gravity, movement, camera rotation, and melee "golpes" (combos, Forte, Barragem, Dash).
 */
public class PlayfulCloudRenderer extends BlockEntityWithoutLevelRenderer {
    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            BattlegroundsMod.MODID, "textures/models/playful_cloud.png");

    // Exact model-space geometry constants matching the baked meshes:
    //  staff_1: X in [-0.351, +0.3845] (ring at +0.360, cap at -0.351)
    //  staff_2: X in [-0.3845, +0.3845] (rings at -0.360 and +0.360)
    //  staff_3: X in [-0.3845, +0.351] (ring at -0.360, cap at +0.351)
    private static final float STAFF_RING_HALF = 0.360f;
    private static final float STAFF_LEN = STAFF_RING_HALF * 2f; // 0.720m between ends
    private static final float LINK_END_DIST = 0.048f;
    private static final float LINK_STEP_DIST = 0.052f;
    private static final float SCALE_3P = 0.85f;
    private static final float SCALE_1P = 0.85f;

    // Each branch has 6 Verlet nodes:
    //  0: Staff 2 anchor ring (fixed at +/- STAFF_RING_HALF, 0, 0)
    //  1..3: 3 oval chain links
    //  4: Outer staff ring end   -- rigid bar (STAFF_LEN) --   5: Outer staff capped tip
    private static final int BRANCH_NODES = 6;
    private static final float[] BRANCH_REST = {
            LINK_END_DIST, LINK_STEP_DIST, LINK_STEP_DIST, LINK_END_DIST, STAFF_LEN
    };
    private static final float[] BRANCH_INV_MASS = {
            0.0f, 1.0f, 1.0f, 1.0f, 0.38f, 0.32f
    };

    private static final float ATTACK_DURATION = 0.40f;
    private static final float MAX_STEP_DISP = 0.22f; // clamps velocity per substep so teleports/hitstops never glitch

    private static MeshModel staff1Mesh;
    private static MeshModel staff2Mesh;
    private static MeshModel staff3Mesh;
    private static MeshModel linkMesh;
    private static boolean broken;

    private static final Map<Integer, ChainSim> SIMS = new HashMap<>();
    private static long lastPruneNs;

    public PlayfulCloudRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    private static boolean ready() {
        if (broken) {
            return false;
        }
        if (staff1Mesh == null) {
            try {
                staff1Mesh = MeshModel.load("playful_cloud_staff_1");
                staff2Mesh = MeshModel.load("playful_cloud_staff_2");
                staff3Mesh = MeshModel.load("playful_cloud_staff_3");
                linkMesh = MeshModel.load("playful_cloud_link");
            } catch (RuntimeException e) {
                broken = true;
                BattlegroundsMod.LOGGER.error("Failed to load playful_cloud meshes", e);
                return false;
            }
        }
        return true;
    }

    public static void toThirdPerson(PoseStack poseStack) {
        poseStack.translate(0.5f, 0.5f, 0.5f);
        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90f));
        poseStack.translate(-0.23f, -0.04f, 0.03f);
        poseStack.mulPose(new Quaternionf().rotationYXZ(
                (float) Math.toRadians(164f),
                (float) Math.toRadians(8f),
                (float) Math.toRadians(-9f)));
        poseStack.scale(SCALE_3P, SCALE_3P, SCALE_3P);
    }

    /**
     * Renders Playful Cloud locked mathematically between BOTH hands of the HumanoidModel:
     * Staff 2 sits at the exact midpoint of the right and left fists and aligns along (pLeft - pRight),
     * so it never drifts off either hand during idle, walking, or any two-handed attack animation.
     */
    public static void renderBetweenHands(LivingEntity entity, HumanoidModel<?> model,
            PoseStack poseStack, MultiBufferSource buffer, int light) {
        if (!ready()) {
            return;
        }
        // Compute exact 3D fist positions in HumanoidModel space (where +Y is down, -Z is forward, -X is right, +X is left)
        PoseStack tmp = new PoseStack();
        tmp.pushPose();
        model.rightArm.translateAndRotate(tmp);
        Vector3f pR = new Vector3f(0f, 9.5f / 16f, -1.0f / 16f);
        tmp.last().pose().transformPosition(pR);
        tmp.popPose();

        tmp.pushPose();
        model.leftArm.translateAndRotate(tmp);
        Vector3f pL = new Vector3f(0f, 9.5f / 16f, -1.0f / 16f);
        tmp.last().pose().transformPosition(pL);
        tmp.popPose();

        Vector3f mid = new Vector3f(pR).add(pL).mul(0.5f);
        Vector3f axisX = new Vector3f(pL).sub(pR);
        if (axisX.lengthSquared() < 1.0e-5f) {
            axisX.set(1f, 0f, 0f);
        } else {
            axisX.normalize();
        }
        // World UP is (0, -1, 0) in HumanoidModel space (+Y is down).
        Vector3f worldUp = new Vector3f(0f, -1f, 0f);
        Vector3f axisZ = new Vector3f(axisX).cross(worldUp);
        if (axisZ.lengthSquared() < 1.0e-4f) {
            axisZ.set(0f, 0f, -1f);
        } else {
            axisZ.normalize();
        }
        Vector3f axisY = new Vector3f(axisZ).cross(axisX).normalize();
        Quaternionf rot = new Quaternionf().setFromNormalized(new Matrix3f(axisX, axisY, axisZ));

        poseStack.pushPose();
        poseStack.translate(mid.x, mid.y, mid.z);
        poseStack.mulPose(rot);
        poseStack.scale(SCALE_3P, SCALE_3P, SCALE_3P);

        VertexConsumer out = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        ChainSim sim = getAndStepSim(entity, false);
        renderArticulated(poseStack, out, light, OverlayTexture.NO_OVERLAY, sim);
        poseStack.popPose();
    }

    /**
     * Live endpoints of Section 3 (front striking staff) in local weapon space.
     */
    public static Vector3f[] strikingSection(LivingEntity entity) {
        if (entity != null) {
            ChainSim sim = SIMS.get(entity.getId());
            if (sim != null) {
                return new Vector3f[] {new Vector3f(sim.frontPos[4]), new Vector3f(sim.frontPos[5])};
            }
        }
        return new Vector3f[] {new Vector3f(0.56f, -0.20f, 0f), new Vector3f(1.28f, -0.20f, 0f)};
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack, MultiBufferSource buffer, int light, int overlay) {
        if (!ready()) {
            return;
        }
        VertexConsumer out = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        poseStack.pushPose();
        boolean thirdPerson = context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        boolean firstPerson = context.firstPerson();

        if (thirdPerson || firstPerson) {
            if (thirdPerson) {
                toThirdPerson(poseStack);
            } else {
                poseStack.translate(0.5f, 0.5f, 0.5f);
                poseStack.translate(0.45f, 0.05f, 0.20f);
                poseStack.mulPose(new Quaternionf().rotationYXZ(
                        (float) Math.toRadians(166f),
                        (float) Math.toRadians(-1f),
                        (float) Math.toRadians(92f)));
                poseStack.scale(SCALE_1P, SCALE_1P, SCALE_1P);
            }

            LivingEntity holder = SwordStretch.holder();
            if (holder == null && firstPerson) {
                holder = Minecraft.getInstance().player;
            }
            ChainSim sim = getAndStepSim(holder, firstPerson);
            renderArticulated(poseStack, out, light, overlay, sim);
        } else {
            // GUI / Ground / Item frame: iconic Z-folded reference pose
            poseStack.translate(0.5f, 0.5f, 0.5f);
            poseStack.mulPose(new Quaternionf().rotationZ((float) Math.toRadians(22f)));
            poseStack.scale(1.12f, 1.12f, 1.12f);
            poseStack.translate(0f, -0.02f, 0f);
            renderFoldedZPose(poseStack, out, light, overlay);
        }
        poseStack.popPose();
    }

    private static void renderArticulated(PoseStack poseStack, VertexConsumer out, int light, int overlay, ChainSim sim) {
        // Subtle dynamic whip-kick on Section 2 during strike
        float kickPitch = sim.handleKickPitch;
        float kickYaw = sim.handleKickYaw;
        if (Math.abs(kickPitch) > 1e-4f || Math.abs(kickYaw) > 1e-4f) {
            poseStack.mulPose(new Quaternionf().rotationYXZ(kickYaw, 0f, kickPitch));
        }

        // Section 2 (middle staff, held in hand at (0, 0, 0))
        staff2Mesh.emit(out, poseStack.last(), light, overlay);

        // Back branch: Staff 2 (-0.360) -> Chain 1 -> Staff 1 (ring at +0.360, cap at -0.351)
        emitChainLink(poseStack, out, light, overlay, sim.backPos[1], sim.backPos[0], sim.backPos[2], Mth.HALF_PI);
        emitChainLink(poseStack, out, light, overlay, sim.backPos[2], sim.backPos[1], sim.backPos[3], 0f);
        emitChainLink(poseStack, out, light, overlay, sim.backPos[3], sim.backPos[2], sim.backPos[4], Mth.HALF_PI);
        emitStaffSegment(poseStack, out, light, overlay, staff1Mesh, sim.backPos[5], sim.backPos[4]);

        // Front branch: Staff 2 (+0.360) -> Chain 2 -> Staff 3 (ring at -0.360, cap at +0.351)
        emitChainLink(poseStack, out, light, overlay, sim.frontPos[1], sim.frontPos[0], sim.frontPos[2], Mth.HALF_PI);
        emitChainLink(poseStack, out, light, overlay, sim.frontPos[2], sim.frontPos[1], sim.frontPos[3], 0f);
        emitChainLink(poseStack, out, light, overlay, sim.frontPos[3], sim.frontPos[2], sim.frontPos[4], Mth.HALF_PI);
        emitStaffSegment(poseStack, out, light, overlay, staff3Mesh, sim.frontPos[4], sim.frontPos[5]);
    }

    private static void emitStaffSegment(PoseStack poseStack, VertexConsumer out, int light, int overlay,
            MeshModel mesh, Vector3f a, Vector3f b) {
        float cx = (a.x + b.x) * 0.5f;
        float cy = (a.y + b.y) * 0.5f;
        float cz = (a.z + b.z) * 0.5f;
        Vector3f dir = new Vector3f(b.x - a.x, b.y - a.y, b.z - a.z);
        if (dir.lengthSquared() < 1.0e-7f) {
            dir.set(1f, 0f, 0f);
        } else {
            dir.normalize();
        }
        Quaternionf rot = new Quaternionf().rotationTo(new Vector3f(1f, 0f, 0f), dir);
        poseStack.pushPose();
        poseStack.translate(cx, cy, cz);
        poseStack.mulPose(rot);
        mesh.emit(out, poseStack.last(), light, overlay);
        poseStack.popPose();
    }

    private static void emitChainLink(PoseStack poseStack, VertexConsumer out, int light, int overlay,
            Vector3f center, Vector3f prev, Vector3f next, float twistRad) {
        Vector3f dir = new Vector3f(next.x - prev.x, next.y - prev.y, next.z - prev.z);
        if (dir.lengthSquared() < 1.0e-7f) {
            dir.set(0f, -1f, 0f);
        } else {
            dir.normalize();
        }
        // Link mesh is elongated along local +Y
        Quaternionf rot = new Quaternionf().rotationTo(new Vector3f(0f, 1f, 0f), dir);
        if (twistRad != 0f) {
            rot.rotateY(twistRad);
        }
        poseStack.pushPose();
        poseStack.translate(center.x, center.y, center.z);
        poseStack.mulPose(rot);
        linkMesh.emit(out, poseStack.last(), light, overlay);
        poseStack.popPose();
    }

    private static void renderFoldedZPose(PoseStack poseStack, VertexConsumer out, int light, int overlay) {
        // Staff 2 (middle) at (0, 0.02, 0)
        poseStack.pushPose();
        poseStack.translate(0f, 0.02f, 0f);
        staff2Mesh.emit(out, poseStack.last(), light, overlay);
        poseStack.popPose();

        // Chain 1 at x = +0.360, y = 0.172, 0.120, 0.068
        emitStaticLink(poseStack, out, light, overlay, 0.360f, 0.172f, Mth.HALF_PI);
        emitStaticLink(poseStack, out, light, overlay, 0.360f, 0.120f, 0f);
        emitStaticLink(poseStack, out, light, overlay, 0.360f, 0.068f, Mth.HALF_PI);

        // Staff 1 (top), right ring at (+0.360, 0.220, 0), tilt -0.11 rad
        float a1 = -0.11f;
        poseStack.pushPose();
        poseStack.translate(0.360f - Mth.cos(a1) * 0.360f, 0.220f - Mth.sin(a1) * 0.360f, 0f);
        poseStack.mulPose(new Quaternionf().rotationZ(a1));
        staff1Mesh.emit(out, poseStack.last(), light, overlay);
        poseStack.popPose();

        // Chain 2 at x = -0.360, y = -0.028, -0.080, -0.132
        emitStaticLink(poseStack, out, light, overlay, -0.360f, -0.028f, Mth.HALF_PI);
        emitStaticLink(poseStack, out, light, overlay, -0.360f, -0.080f, 0f);
        emitStaticLink(poseStack, out, light, overlay, -0.360f, -0.132f, Mth.HALF_PI);

        // Staff 3 (bottom), left ring at (-0.360, -0.180, 0), tilt -0.16 rad
        float a3 = -0.16f;
        poseStack.pushPose();
        poseStack.translate(-0.360f + Mth.cos(a3) * 0.360f, -0.180f + Mth.sin(a3) * 0.360f, 0f);
        poseStack.mulPose(new Quaternionf().rotationZ(a3));
        staff3Mesh.emit(out, poseStack.last(), light, overlay);
        poseStack.popPose();
    }

    private static void emitStaticLink(PoseStack poseStack, VertexConsumer out, int light, int overlay,
            float x, float y, float rotY) {
        poseStack.pushPose();
        poseStack.translate(x, y, 0f);
        if (rotY != 0f) {
            poseStack.mulPose(new Quaternionf().rotationY(rotY));
        }
        linkMesh.emit(out, poseStack.last(), light, overlay);
        poseStack.popPose();
    }

    // ---------- Per-entity real-time dual-branch physics management ----------

    private static ChainSim getAndStepSim(LivingEntity entity, boolean firstPerson) {
        long now = System.nanoTime();
        if (now - lastPruneNs > 5_000_000_000L) {
            lastPruneNs = now;
            SIMS.values().removeIf(s -> now - s.lastRenderNs > 10_000_000_000L);
        }
        int id = entity != null ? entity.getId() : -1;
        ChainSim sim = SIMS.computeIfAbsent(id, k -> new ChainSim(now));
        sim.step(entity, firstPerson, now);
        return sim;
    }

    private static final class ChainSim {
        final Vector3f[] backPos = new Vector3f[BRANCH_NODES];
        final Vector3f[] backPrev = new Vector3f[BRANCH_NODES];
        final Vector3f[] frontPos = new Vector3f[BRANCH_NODES];
        final Vector3f[] frontPrev = new Vector3f[BRANCH_NODES];

        long lastRenderNs;
        float timeSec;

        // Entity motion tracking
        boolean hasMotion;
        double lastX, lastY, lastZ;
        double lastVx, lastVy, lastVz;
        float lastYaw, lastPitch;
        float lastSwing;
        String lastAnimName;
        int lastAnimStart = Integer.MIN_VALUE;

        // Attack choreography state
        float attackTimer = -1f;
        float attackSide = 1f;
        float handleKickPitch;
        float handleKickYaw;

        ChainSim(long nowNs) {
            this.lastRenderNs = nowNs;
            resetHangingPose(false);
        }

        private void resetHangingPose(boolean firstPerson) {
            initBranch(backPos, backPrev, -STAFF_RING_HALF, firstPerson, -0.05f);
            initBranch(frontPos, frontPrev, STAFF_RING_HALF, firstPerson, 0.05f);
        }

        private static void initBranch(Vector3f[] pos, Vector3f[] prev, float anchorX, boolean firstPerson, float spreadX) {
            float dx = (firstPerson ? -0.35f : 0.0f) + spreadX;
            float dy = firstPerson ? -0.92f : -0.99f;
            float dz = 0.05f;
            Vector3f down = new Vector3f(dx, dy, dz).normalize();
            pos[0] = new Vector3f(anchorX, 0f, 0f);
            prev[0] = new Vector3f(pos[0]);
            for (int i = 1; i < BRANCH_NODES; i++) {
                pos[i] = new Vector3f(pos[i - 1]).add(new Vector3f(down).mul(BRANCH_REST[i - 1]));
                prev[i] = new Vector3f(pos[i]);
            }
        }

        void step(LivingEntity entity, boolean firstPerson, long nowNs) {
            float dt = (nowNs - lastRenderNs) * 1.0e-9f;
            if (dt < 0.0005f) {
                return; // already stepped this frame (shadow pass / reflection)
            }
            lastRenderNs = nowNs;
            if (dt > 0.25f) {
                hasMotion = false;
                attackTimer = -1f;
                resetHangingPose(firstPerson);
                dt = 0.016f;
            }
            dt = Math.min(dt, 0.04f);
            timeSec += dt;

            float accelFwd = 0f, accelUp = 0f, accelRight = 0f;
            float speedFwd = 0f, speedRight = 0f, speedUp = 0f;
            float dYawRate = 0f, dPitchRate = 0f;
            float pitchRad = 0f;
            float walkBob = 0f;

            boolean isCharging = false;
            boolean isBarrage = false;
            boolean isDash = false;

            if (entity != null) {
                float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
                double ex = Mth.lerp(partial, entity.xo, entity.getX());
                double ey = Mth.lerp(partial, entity.yo, entity.getY());
                double ez = Mth.lerp(partial, entity.zo, entity.getZ());
                float yaw = entity.getViewYRot(partial);
                float pitch = entity.getViewXRot(partial);
                pitchRad = (float) Math.toRadians(pitch);

                if (hasMotion) {
                    double dxW = ex - lastX;
                    double dyW = ey - lastY;
                    double dzW = ez - lastZ;
                    // Ignore teleport spikes (e.g. DashAttack snap) so chains don't explode
                    if (dxW * dxW + dyW * dyW + dzW * dzW < 9.0) {
                        double vx = dxW / dt;
                        double vy = dyW / dt;
                        double vz = dzW / dt;
                        double ax = (vx - lastVx) / dt;
                        double ay = (vy - lastVy) / dt;
                        double az = (vz - lastVz) / dt;

                        float yawRad = (float) Math.toRadians(yaw);
                        float fx = -Mth.sin(yawRad), fz = Mth.cos(yawRad);
                        float rx = -Mth.cos(yawRad), rz = -Mth.sin(yawRad);

                        speedFwd = Mth.clamp((float) (vx * fx + vz * fz), -18f, 18f);
                        speedRight = Mth.clamp((float) (vx * rx + vz * rz), -18f, 18f);
                        speedUp = Mth.clamp((float) vy, -18f, 18f);

                        accelFwd = Mth.clamp((float) (ax * fx + az * fz), -28f, 28f);
                        accelRight = Mth.clamp((float) (ax * rx + az * rz), -28f, 28f);
                        accelUp = Mth.clamp((float) ay, -28f, 28f);

                        dYawRate = Mth.clamp(Mth.wrapDegrees(yaw - lastYaw) / dt, -480f, 480f);
                        dPitchRate = Mth.clamp((pitch - lastPitch) / dt, -320f, 320f);

                        lastVx = vx;
                        lastVy = vy;
                        lastVz = vz;
                    } else {
                        lastVx = lastVy = lastVz = 0;
                    }
                }
                lastX = ex;
                lastY = ey;
                lastZ = ez;
                lastYaw = yaw;
                lastPitch = pitch;
                hasMotion = true;

                float horizSpeed = Mth.clamp((float) Math.hypot(speedFwd, speedRight), 0f, 10f);
                walkBob = horizSpeed > 0.3f ? Mth.sin(timeSec * 11.5f) * Math.min(horizSpeed, 6f) * 0.40f : 0f;

                // Detect vanilla swings AND melee "golpes" keyframe animations (soco.combo*, soco.forte_*, soco.barragem*, soco.dash)
                float swing = entity.getAttackAnim(partial);
                boolean newVanillaSwing = swing > 0.001f && (lastSwing <= 0.001f || swing < lastSwing - 0.12f);

                float ageTicks = entity.tickCount + partial;
                boolean keyframedActive = SkillAnimations.keyframed(entity, ageTicks);
                String animName = keyframedActive ? SkillAnimations.activeKeyframeName(entity) : null;
                int animStart = keyframedActive ? SkillAnimations.swordMoveStart(entity) : Integer.MIN_VALUE;

                if (animName != null) {
                    isCharging = animName.endsWith("forte_carga");
                    isBarrage = animName.endsWith("barragem");
                    isDash = animName.endsWith("dash");
                }

                boolean newKeyframeAttack = animName != null
                        && (!animName.equals(lastAnimName) || animStart != lastAnimStart)
                        && !isCharging && !isDash;

                if (newVanillaSwing || newKeyframeAttack) {
                    attackTimer = 0f;
                    attackSide = -attackSide;
                }

                lastSwing = swing;
                lastAnimName = animName;
                lastAnimStart = animStart;
            }

            // Advance whip attack timer
            float attackT = -1f;
            if (isBarrage) {
                // Continuous rapid alternating whips during Barragem
                float cycle = (timeSec * 5.2f) % 1.0f;
                attackT = 0.14f + cycle * 0.44f;
                attackSide = ((int) (timeSec * 5.2f) % 2 == 0) ? 1f : -1f;
                attackTimer = -1f;
            } else if (attackTimer >= 0f) {
                attackTimer += dt;
                if (attackTimer >= ATTACK_DURATION) {
                    attackTimer = -1f;
                } else {
                    attackT = attackTimer / ATTACK_DURATION;
                }
            }

            // Subtle handle (Staff 2) wrist kick during strikes
            if (attackT >= 0f) {
                float snap = Mth.sin(attackT * Mth.PI);
                float whipCurve = attackT < 0.22f
                        ? -Mth.sin((attackT / 0.22f) * Mth.HALF_PI) * 0.16f
                        : Mth.sin(((attackT - 0.22f) / 0.78f) * Mth.PI) * 0.22f;
                handleKickPitch = whipCurve;
                handleKickYaw = snap * attackSide * 0.12f;
            } else if (isCharging) {
                handleKickPitch = Mth.lerp(0.15f, handleKickPitch, -0.14f);
                handleKickYaw *= 0.85f;
            } else {
                handleKickPitch *= 0.85f;
                handleKickYaw *= 0.85f;
            }

            // Local gravity direction
            Vector3f gravity = new Vector3f();
            if (firstPerson) {
                gravity.set(-7.6f, -6.4f, -0.4f);
            } else {
                float g = 13.5f;
                gravity.set(-Mth.sin(pitchRad) * g, -Mth.cos(pitchRad) * g, 0f);
            }

            // Inertial forces in weapon-local space
            float yawForceZ = -dYawRate * 0.058f;
            float yawCentrifugal = Math.abs(dYawRate) * 0.016f;
            float pitchForceY = dPitchRate * 0.030f;

            float dragX, dragY, dragZ;
            if (firstPerson) {
                dragX = -speedUp * 1.3f - accelUp * 0.30f + walkBob;
                dragY = speedFwd * 1.4f + accelFwd * 0.35f + pitchForceY;
                dragZ = -speedRight * 1.4f - accelRight * 0.30f + yawForceZ;
            } else {
                dragX = -speedFwd * 1.6f - accelFwd * 0.38f;
                dragY = -speedUp * 1.4f - accelUp * 0.35f + walkBob + pitchForceY;
                dragZ = -speedRight * 1.4f - accelRight * 0.35f + yawForceZ;
            }

            float idleSwayZ = Mth.sin(timeSec * 2.5f) * 0.45f + Mth.cos(timeSec * 4.0f) * 0.20f;
            float idleSwayX = Mth.cos(timeSec * 2.0f) * 0.28f;

            // Compute attack/pose targets for both tips (Staff 3 tip on front branch, Staff 1 cap on back branch)
            Vector3f frontTarget = null;
            Vector3f backTarget = null;
            float frontStrength = 0f;
            float backStrength = 0f;

            if (isCharging) {
                frontTarget = new Vector3f();
                backTarget = new Vector3f();
                if (firstPerson) {
                    frontTarget.set(STAFF_RING_HALF + 0.35f, 0.25f, 0.55f);
                    backTarget.set(-STAFF_RING_HALF - 0.45f, -0.30f, -0.35f);
                } else {
                    frontTarget.set(STAFF_RING_HALF + 0.25f, 0.32f, 0.65f);
                    backTarget.set(-STAFF_RING_HALF - 0.55f, -0.25f, -0.45f);
                }
                frontStrength = 120f;
                backStrength = 95f;
            } else if (isDash) {
                frontTarget = new Vector3f();
                backTarget = new Vector3f();
                if (firstPerson) {
                    frontTarget.set(STAFF_RING_HALF - 0.15f, -0.65f, 0.25f);
                    backTarget.set(-STAFF_RING_HALF - 0.70f, -0.45f, -0.20f);
                } else {
                    frontTarget.set(STAFF_RING_HALF - 0.45f, -0.35f, 0.35f);
                    backTarget.set(-STAFF_RING_HALF - 0.85f, -0.15f, -0.20f);
                }
                frontStrength = 110f;
                backStrength = 110f;
            } else if (attackT >= 0f) {
                frontTarget = new Vector3f();
                backTarget = new Vector3f();
                computeDualWhipTargets(attackT, attackSide, firstPerson, frontTarget, backTarget);
                if (attackT < 0.16f) {
                    frontStrength = 115f;
                    backStrength = 95f;
                } else if (attackT < 0.56f) {
                    frontStrength = 250f;
                    backStrength = 175f;
                } else if (attackT < 0.82f) {
                    float fade = 1f - (attackT - 0.56f) / 0.26f;
                    frontStrength = 75f * fade;
                    backStrength = 60f * fade;
                }
            }

            int substeps = dt > 0.016f ? 2 : 1;
            float subDt = dt / substeps;
            float subDt2 = subDt * subDt;
            float damping = (float) Math.pow(0.055f, subDt);

            for (int step = 0; step < substeps; step++) {
                // Step Front branch (+STAFF_RING_HALF -> Chain 2 -> Staff 3)
                stepBranch(frontPos, frontPrev, STAFF_RING_HALF,
                        gravity, dragX + yawCentrifugal, dragY, dragZ,
                        idleSwayX, idleSwayZ, frontTarget, frontStrength,
                        damping, subDt2);

                // Step Back branch (-STAFF_RING_HALF -> Chain 1 -> Staff 1)
                stepBranch(backPos, backPrev, -STAFF_RING_HALF,
                        gravity, dragX - yawCentrifugal, dragY, dragZ,
                        -idleSwayX, -idleSwayZ * 0.85f, backTarget, backStrength,
                        damping, subDt2);
            }
        }

        private static void stepBranch(
                Vector3f[] pos, Vector3f[] prev, float anchorX,
                Vector3f gravity, float dragX, float dragY, float dragZ,
                float swayX, float swayZ,
                Vector3f tipTarget, float tipStrength,
                float damping, float subDt2) {

            pos[0].set(anchorX, 0f, 0f);
            prev[0].set(anchorX, 0f, 0f);

            for (int i = 1; i < BRANCH_NODES; i++) {
                float vx = (pos[i].x - prev[i].x) * damping;
                float vy = (pos[i].y - prev[i].y) * damping;
                float vz = (pos[i].z - prev[i].z) * damping;

                // Clamp per-step velocity to guarantee zero instability or glitches
                float v2 = vx * vx + vy * vy + vz * vz;
                if (v2 > MAX_STEP_DISP * MAX_STEP_DISP) {
                    float scale = MAX_STEP_DISP / (float) Math.sqrt(v2);
                    vx *= scale;
                    vy *= scale;
                    vz *= scale;
                }

                prev[i].set(pos[i]);

                float lever = 0.70f + (i / (float) (BRANCH_NODES - 1)) * 0.85f;
                float fx = gravity.x + dragX * lever + swayX;
                float fy = gravity.y + dragY * lever;
                float fz = gravity.z + dragZ * lever + swayZ * lever;

                if (tipTarget != null && tipStrength > 0f) {
                    if (i == 5) {
                        fx += (tipTarget.x - pos[i].x) * tipStrength;
                        fy += (tipTarget.y - pos[i].y) * tipStrength;
                        fz += (tipTarget.z - pos[i].z) * tipStrength;
                    } else if (i == 4) {
                        // Gently guide the ring end toward the strike line so the chain snaps taut without folding
                        float dirSign = anchorX > 0f ? 1f : -1f;
                        float ringGoalX = anchorX + dirSign * 0.20f;
                        float pull = tipStrength * 0.22f;
                        fx += (ringGoalX - pos[i].x) * pull;
                        fy += (tipTarget.y * 0.25f - pos[i].y) * pull;
                        fz += (tipTarget.z * 0.25f - pos[i].z) * pull;
                    }
                }

                pos[i].x += vx + fx * subDt2;
                pos[i].y += vy + fy * subDt2;
                pos[i].z += vz + fz * subDt2;
            }

            // 12 Gauss-Seidel distance constraint iterations -> zero stretch on chains & rigid staffs
            for (int iter = 0; iter < 12; iter++) {
                pos[0].set(anchorX, 0f, 0f);
                for (int s = 0; s < BRANCH_NODES - 1; s++) {
                    Vector3f a = pos[s];
                    Vector3f b = pos[s + 1];
                    float dx = b.x - a.x;
                    float dy = b.y - a.y;
                    float dz = b.z - a.z;
                    float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (dist < 1.0e-6f) {
                        continue;
                    }
                    float wA = BRANCH_INV_MASS[s];
                    float wB = BRANCH_INV_MASS[s + 1];
                    float wSum = wA + wB;
                    if (wSum <= 0f) {
                        continue;
                    }
                    float diff = (dist - BRANCH_REST[s]) / (dist * wSum);
                    if (wA > 0f) {
                        a.x += dx * diff * wA;
                        a.y += dy * diff * wA;
                        a.z += dz * diff * wA;
                    }
                    if (wB > 0f) {
                        b.x -= dx * diff * wB;
                        b.y -= dy * diff * wB;
                        b.z -= dz * diff * wB;
                    }
                }
                // Keep outer staff ends clear of Staff 2's held handle capsule (-0.32..+0.32)
                keepClearOfHandle(pos[4], 0.11f);
                keepClearOfHandle(pos[5], 0.14f);
            }
        }

        private static void computeDualWhipTargets(float t, float side, boolean firstPerson,
                Vector3f outFrontTip, Vector3f outBackTip) {
            // Front branch total length from +0.360 is 0.920 (so max +X is ~+1.28)
            // Back branch total length from -0.360 is 0.920 (so max -X is ~-1.28)
            if (firstPerson) {
                if (t < 0.16f) {
                    float u = smooth(t / 0.16f);
                    outFrontTip.set(STAFF_RING_HALF + 0.35f, 0.25f * u, 0.55f * side * u);
                    outBackTip.set(-STAFF_RING_HALF - 0.55f, -0.35f, -0.40f * side * u);
                } else if (t < 0.56f) {
                    float u = smooth((t - 0.16f) / 0.40f);
                    float frontSweep = Mth.lerp(u, 0.65f * side, -0.75f * side);
                    outFrontTip.set(STAFF_RING_HALF + 0.72f, -0.82f, frontSweep - 0.20f);
                    float backSweep = Mth.lerp(u, -0.45f * side, 0.55f * side);
                    outBackTip.set(-STAFF_RING_HALF - 0.78f, -0.35f, backSweep);
                } else {
                    float u = smooth((t - 0.56f) / 0.44f);
                    outFrontTip.set(STAFF_RING_HALF - 0.20f * u, -0.78f, -0.15f * side * (1f - u));
                    outBackTip.set(-STAFF_RING_HALF - 0.30f * (1f - u), -0.78f, 0.15f * side * (1f - u));
                }
            } else {
                if (t < 0.16f) {
                    float u = smooth(t / 0.16f);
                    outFrontTip.set(STAFF_RING_HALF + 0.20f, 0.26f * u, 0.68f * side * u);
                    outBackTip.set(-STAFF_RING_HALF - 0.55f, -0.22f, -0.55f * side * u);
                } else if (t < 0.56f) {
                    float u = smooth((t - 0.16f) / 0.40f);
                    float frontSweep = Mth.lerp(u, 0.65f * side, -0.55f * side);
                    outFrontTip.set(STAFF_RING_HALF + 0.92f, 0.04f - 0.18f * u, frontSweep);
                    float backSweep = Mth.lerp(u, -0.50f * side, 0.45f * side);
                    outBackTip.set(-STAFF_RING_HALF - 0.88f, -0.08f + 0.14f * u, backSweep);
                } else {
                    float u = smooth((t - 0.56f) / 0.44f);
                    outFrontTip.set(STAFF_RING_HALF + 0.22f * (1f - u), -0.85f * u, -0.15f * side * (1f - u));
                    outBackTip.set(-STAFF_RING_HALF - 0.22f * (1f - u), -0.85f * u, 0.15f * side * (1f - u));
                }
            }
        }

        private static void keepClearOfHandle(Vector3f p, float minDist) {
            // Keep outer staff ends clear of Staff 2's body (-0.32..+0.32 on X)
            float clampedX = Mth.clamp(p.x, -0.32f, 0.32f);
            float dx = p.x - clampedX;
            float dy = p.y;
            float dz = p.z;
            float d2 = dx * dx + dy * dy + dz * dz;
            if (d2 < minDist * minDist && d2 > 1.0e-7f) {
                float d = (float) Math.sqrt(d2);
                float push = (minDist - d) / d;
                p.x += dx * push;
                p.y += dy * push;
                p.z += dz * push;
            }
        }

        private static float smooth(float x) {
            float c = Mth.clamp(x, 0f, 1f);
            return c * c * (3f - 2f * c);
        }
    }
}
