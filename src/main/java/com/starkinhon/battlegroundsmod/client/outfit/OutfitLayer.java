package com.starkinhon.battlegroundsmod.client.outfit;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.util.Mth;

// Draws the power's 3D outfit on players who have it on (see Outfits), over their own skin. Pieces ride the model's
// bones; the cloth parts are simulated here every frame, in the player's own space, pushed by the legs and dragged
// by how the player moves.
public class OutfitLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final float GRAVITY = -157f;
    private static final float STEP = 1 / 120f;

    // Cloth state per player.
    private static final class State {
        final String power;
        final Cloth[] cloths;
        final Rig rig = new Rig();
        long lastNanos = System.nanoTime();
        float acc;
        long seen;

        State(String power, OutfitDefs.Def def, PlayerModel<?> model) {
            this.power = power;
            rig.update(model);
            int n = def.sheets().size() + def.plates().size();
            cloths = new Cloth[n];
            int i = 0;
            for (OutfitDefs.Sheet s : def.sheets()) {
                cloths[i++] = new Cloth(s.spec(), rig);
            }
            for (OutfitDefs.Plates p : def.plates()) {
                cloths[i++] = new Cloth(p.spec(), rig);
            }
        }
    }

    private static final Map<Integer, State> STATES = new HashMap<>();
    private static long lastPrune;

    public OutfitLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    // Set when drawing an outfit failed once: outfits stop drawing instead of taking the game down every frame.
    private static boolean broken;

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int light, AbstractClientPlayer player, float limbSwing,
            float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (broken) {
            return;
        }
        try {
            draw(poseStack, buffer, light, player, partialTick);
        } catch (RuntimeException e) {
            broken = true;
            BattlegroundsMod.LOGGER.error("Outfit drawing failed; outfits are off until the game restarts", e);
        }
    }

    private void draw(PoseStack poseStack, MultiBufferSource buffer, int light, AbstractClientPlayer player, float partialTick) {
        if (player.isInvisible() || player.isSpectator()) {
            return;
        }
        String power = Outfits.worn(player.getId());
        OutfitDefs.Def def = power.isEmpty() ? null : OutfitDefs.get(power);
        if (def == null) {
            return;
        }
        PlayerModel<AbstractClientPlayer> model = getParentModel();
        int overlay = LivingEntityRenderer.getOverlayCoords(player, 0);

        for (OutfitDefs.Piece piece : def.pieces()) {
            ModelPart part = part(model, piece.bone());
            if (!part.visible || piece.bone() == Rig.Bone.HAND_R && !player.getMainHandItem().isEmpty()) {
                continue;
            }
            poseStack.pushPose();
            part.translateAndRotate(poseStack);
            VertexConsumer out = buffer.getBuffer(RenderType.entityCutoutNoCull(piece.texture()));
            emitStatic(out, poseStack.last(), piece.bone(), piece.tris(), light, overlay);
            poseStack.popPose();
        }

        if (!model.body.visible) {
            return;
        }
        State state = simulate(player, def, power, model, partialTick);
        PoseStack.Pose pose = poseStack.last();
        int i = 0;
        for (OutfitDefs.Sheet sheet : def.sheets()) {
            emitSheet(buffer.getBuffer(RenderType.entityCutoutNoCull(sheet.texture())), pose, state.cloths[i++], sheet, light, overlay);
        }
        for (OutfitDefs.Plates plates : def.plates()) {
            emitPlates(buffer, pose, state.cloths[i++], plates, light, overlay);
        }
    }

    static ModelPart part(PlayerModel<?> model, Rig.Bone bone) {
        return switch (bone) {
            case HEAD -> model.head;
            case BODY -> model.body;
            case ARM_R, HAND_R -> model.rightArm;
            case ARM_L -> model.leftArm;
            case LEG_R -> model.rightLeg;
            case LEG_L -> model.leftLeg;
        };
    }

    // ---- Physics ---------------------------------------------------------------------------------------------------

    private static State simulate(AbstractClientPlayer player, OutfitDefs.Def def, String power, PlayerModel<?> model, float partialTick) {
        long now = System.nanoTime();
        if (now - lastPrune > 5_000_000_000L) {
            lastPrune = now;
            Iterator<State> it = STATES.values().iterator();
            while (it.hasNext()) {
                if (now - it.next().seen > 10_000_000_000L) {
                    it.remove();
                }
            }
        }
        State state = STATES.get(player.getId());
        if (state == null || !state.power.equals(power)) {
            state = new State(power, def, model);
            STATES.put(player.getId(), state);
        }
        state.seen = now;
        float dt = Math.min(0.05f, (now - state.lastNanos) / 1e9f);
        state.lastNanos = now;
        state.rig.update(model);

        // Air drag from how the player moves, turned into the player's own space (x to their left, z forward).
        float yaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
        float vx = (float) (player.getX() - player.xo) * 320, vy = (float) (player.getY() - player.yo) * 320, vz = (float) (player.getZ() - player.zo) * 320;
        float c = Mth.cos(yaw), s = Mth.sin(yaw);
        float lx = vx * c + vz * s, lz = -vx * s + vz * c;
        float k = 0.7f;
        Vector3f force = new Vector3f(-lx * k, GRAVITY - vy * k, -lz * k);

        state.acc += dt;
        int steps = 0;
        while (state.acc >= STEP && steps < 8) {
            state.acc -= STEP;
            steps++;
            for (Cloth cloth : state.cloths) {
                cloth.step(STEP, force, state.rig, def.colliders());
            }
        }
        if (state.acc > STEP) {
            state.acc = 0;
        }
        return state;
    }

    // ---- Drawing ---------------------------------------------------------------------------------------------------

    // Rig pixels from a bone's pivot to the model part's own space (blocks, y down, facing -z). The viewer's arm and
    // leg pivots sit a little off the vanilla ones, so those get nudged sideways.
    private static float offX(Rig.Bone bone) {
        return switch (bone) {
            case ARM_R, HAND_R -> -1;
            case ARM_L -> 1;
            case LEG_R -> -0.1f;
            case LEG_L -> 0.1f;
            default -> 0;
        };
    }

    static void emitStatic(VertexConsumer out, PoseStack.Pose pose, Rig.Bone bone, float[] t, int light, int overlay) {
        float dx = offX(bone), y0 = bone == Rig.Bone.BODY ? 24 : 0;
        for (int i = 0; i < t.length; i += 33) {
            for (int v = 0; v < 4; v++) {
                int o = i + Math.min(v, 2) * 11;
                out.addVertex(pose, (t[o] + dx) / 16f, (y0 - t[o + 1]) / 16f, -t[o + 2] / 16f)
                        .setColor(t[o + 5], t[o + 6], t[o + 7], 1f).setUv(t[o + 3], t[o + 4]).setOverlay(overlay).setLight(light)
                        .setNormal(pose, t[o + 8], -t[o + 9], -t[o + 10]);
            }
        }
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, Vector3f p, Vector3f n, float r, float g, float b, float u, float v,
            int light, int overlay) {
        out.addVertex(pose, p.x / 16f, (24 - p.y) / 16f, -p.z / 16f).setColor(r, g, b, 1f).setUv(u, v).setOverlay(overlay).setLight(light)
                .setNormal(pose, n.x, -n.y, -n.z);
    }

    private static void emitSheet(VertexConsumer out, PoseStack.Pose pose, Cloth cloth, OutfitDefs.Sheet sheet, int light, int overlay) {
        int n = cloth.n, rows = cloth.spec.rows(), cols = cloth.spec.wrap() ? n + 1 : n;
        float r = ((sheet.color() >> 16) & 255) / 255f, g = ((sheet.color() >> 8) & 255) / 255f, b = (sheet.color() & 255) / 255f;
        Vector3f[][] nrm = new Vector3f[cols][rows];
        Vector3f du = new Vector3f(), dv = new Vector3f();
        for (int c = 0; c < cols; c++) {
            for (int row = 0; row < rows; row++) {
                Vector3f p = cloth.p[c % n][row];
                Vector3f left = cloth.p[sheet.spec().wrap() ? Math.floorMod(c - 1, n) : Math.max(0, c - 1)][row];
                Vector3f right = cloth.p[sheet.spec().wrap() ? (c + 1) % n : Math.min(n - 1, c + 1)][row];
                Vector3f up = cloth.p[c % n][Math.max(0, row - 1)], down = cloth.p[c % n][Math.min(rows - 1, row + 1)];
                du.set(right).sub(left);
                dv.set(down).sub(up);
                Vector3f nn = new Vector3f(dv).cross(du).normalize();
                if (!Float.isFinite(nn.x)) {
                    nn.set(0, 0, 1);
                }
                float hint = sheet.radial() ? nn.x * p.x + nn.z * p.z : nn.z;
                if (hint < 0) {
                    nn.negate();
                }
                nrm[c][row] = nn;
            }
        }
        for (int c = 0; c < cols - 1; c++) {
            for (int row = 0; row < rows - 1; row++) {
                int[][] quad = {{c, row}, {c, row + 1}, {c + 1, row + 1}, {c + 1, row}};
                for (int[] q : quad) {
                    vertex(out, pose, cloth.p[q[0] % n][q[1]], nrm[q[0]][q[1]], r, g, b, q[0] / (float) (cols - 1), q[1] / (float) (rows - 1),
                            light, overlay);
                }
            }
        }
    }

    private static void emitPlates(MultiBufferSource buffer, PoseStack.Pose pose, Cloth cloth, OutfitDefs.Plates plates, int light, int overlay) {
        // One buffer at a time: asking the buffer source for another render type closes the one before, so the plates
        // and their laces are drawn in two passes (writing to a closed buffer crashed the game).
        Vector3f[] L = cloth.p[0], R = cloth.p[1];
        Vector3f a = new Vector3f(), b = new Vector3f(), up = new Vector3f(), right = new Vector3f(), n = new Vector3f(), c = new Vector3f();
        int lames = cloth.spec.rows() - 1;
        Matrix4f[] ms = new Matrix4f[lames];
        Matrix3f[] nms = new Matrix3f[lames];
        for (int i = 0; i < lames; i++) {
            Matrix4f m = ms[i] = new Matrix4f();
            Matrix3f nm = nms[i] = new Matrix3f();
            a.set(L[i]).add(R[i]).mul(0.5f);
            b.set(L[i + 1]).add(R[i + 1]).mul(0.5f);
            up.set(a).sub(b).normalize();
            right.set(R[i]).sub(L[i]).add(R[i + 1]).sub(L[i + 1]).normalize();
            n.set(right).cross(up).normalize();
            right.set(up).cross(n).normalize();
            c.set(a).add(b).mul(0.5f).add(n.x * 0.16f * i, n.y * 0.16f * i, n.z * 0.16f * i);
            m.set(right.x, right.y, right.z, 0, up.x, up.y, up.z, 0, n.x, n.y, n.z, 0, c.x, c.y, c.z, 1).rotateX(-0.13f);
            m.normal(nm);
        }
        VertexConsumer lacq = buffer.getBuffer(RenderType.entityCutoutNoCull(OutfitDefs.LACQUER));
        for (int i = 0; i < lames; i++) {
            emitMoved(lacq, pose, plates.lame(), ms[i], nms[i], light, overlay);
        }
        VertexConsumer gold = buffer.getBuffer(RenderType.entityCutoutNoCull(OutfitDefs.WHITE));
        for (int i = 0; i < lames; i++) {
            emitMoved(gold, pose, plates.laces(), ms[i], nms[i], light, overlay);
        }
    }

    private static void emitMoved(VertexConsumer out, PoseStack.Pose pose, float[] t, Matrix4f m, Matrix3f nm, int light, int overlay) {
        Vector3f p = new Vector3f(), nn = new Vector3f();
        for (int i = 0; i < t.length; i += 33) {
            for (int v = 0; v < 4; v++) {
                int o = i + Math.min(v, 2) * 11;
                m.transformPosition(p.set(t[o], t[o + 1], t[o + 2]));
                nm.transform(nn.set(t[o + 8], t[o + 9], t[o + 10]));
                vertex(out, pose, p, nn, t[o + 5], t[o + 6], t[o + 7], t[o + 3], t[o + 4], light, overlay);
            }
        }
    }

    // Forgets a player's cloth (they changed outfits or left).
    static void forget(int entityId) {
        STATES.remove(entityId);
    }
}
