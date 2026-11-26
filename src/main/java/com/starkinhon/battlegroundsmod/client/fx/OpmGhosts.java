// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

// The page's afterimages (snapshot): Saitama's body held still at one instant, see-through and fading. Each is taken from
// the frame the game really drew of him (every part's pose as the animation had it), so it is his own skin and pose.
public final class OpmGhosts {
    // profile: whose Customizable Player Models model to draw instead of the skin, if they have one (null for a boss).
    private record Ghost(PlayerModel<AbstractClientPlayer> model, ResourceLocation skin, GameProfile profile, PartPose[] parts, Vec3 pos, float yaw,
            double born, float peak, float life) {
    }

    // A ghost posed by hand (the Omni's: crouched on a rock, or in Stark's dive, Pose.bbmodel): which body, where its feet
    // are, how it is turned (entity space: +Z forward, +X left, y up), each limb's rotation (radians, ModelPart's).
    record Pose(float[] head, float[] rightArm, float[] leftArm, float[] rightLeg, float[] leftLeg) {
    }

    private record Posed(PlayerModel<?> model, ResourceLocation skin, GameProfile profile, boolean slim, Pose pose, Vec3 pos, Quaternionf turn,
            double born, float peak, float life) {
    }

    private static final List<Ghost> GHOSTS = new ArrayList<>();
    private static final List<Posed> POSED = new ArrayList<>();
    // Drawn this frame only (the ring of 50 closing in: placed again every frame).
    private static final List<Posed> FRAME = new ArrayList<>();
    // Who wants a snapshot on their next drawn frame: id -> {peak, life}.
    private static final Map<Integer, float[]> WANT = new HashMap<>();

    private OpmGhosts() {
    }

    static boolean active() {
        return !GHOSTS.isEmpty() || !WANT.isEmpty() || !POSED.isEmpty() || !FRAME.isEmpty();
    }

    static void clear() {
        GHOSTS.clear();
        WANT.clear();
        POSED.clear();
        FRAME.clear();
    }

    // A snapshot of entity id the next time it is drawn: peak opacity, gone after life seconds.
    static void want(int id, float peak, float life) {
        WANT.put(id, new float[] {peak, life});
    }

    private static ModelPart[] parts(PlayerModel<?> m) {
        return new ModelPart[] {m.head, m.hat, m.body, m.jacket, m.rightArm, m.leftArm, m.rightSleeve, m.leftSleeve, m.rightLeg, m.leftLeg, m.rightPants,
                m.leftPants};
    }

    public static void onRenderPlayer(RenderPlayerEvent.Post event) {
        if (WANT.isEmpty()) {
            return;
        }
        AbstractClientPlayer player = (AbstractClientPlayer) event.getEntity();
        float[] w = WANT.remove(player.getId());
        if (w == null) {
            return;
        }
        PlayerModel<AbstractClientPlayer> model = event.getRenderer().getModel();
        ModelPart[] ps = parts(model);
        PartPose[] poses = new PartPose[ps.length];
        for (int i = 0; i < ps.length; i++) {
            poses[i] = ps[i].storePose();
        }
        float partial = event.getPartialTick();
        GHOSTS.add(new Ghost(model, event.getRenderer().getTextureLocation(player), player.getGameProfile(), poses, player.getPosition(partial),
                Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot), ScreenFx.now() + partial, w[0], w[1]));
        if (GHOSTS.size() > 80) {
            GHOSTS.remove(0);
        }
    }

    // ---------- posed by hand ----------

    private static final float D2R = Mth.DEG_TO_RAD;
    private static float[] deg(double x, double y, double z) {
        return new float[] {(float) x * D2R, (float) y * D2R, (float) z * D2R};
    }

    // Stark's dive (Pose.bbmodel, 2026-11-25): the Blockbench angles carried over (x and y change sign, the model faces the
    // other way), the waist 60 degrees forward, the head up, both arms out in front.
    static final Pose DIVE = new Pose(deg(35, 0, 0), deg(-141.602, 21.28713, 13.55952), deg(-151.93319, -11.06861, -5.84485),
            deg(32.40109, 2.68407, -4.22005), deg(0.32902, -7.49282, -2.52154));
    // Crouched on a rock, ready for the next hop.
    static final Pose CROUCH = new Pose(new float[3], new float[] {-0.6f, 0, 0.5f}, new float[] {-0.6f, 0, -0.5f}, new float[] {-0.3f, 0, 0},
            new float[] {-0.9f, 0, 0});
    private static final Quaternionf WAIST = new Quaternionf().rotationX(60 * D2R);

    // The body of whoever this is (a player's own skin, or the boss's), or null.
    private static Object[] body(int id) {
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.world.entity.Entity who = mc.level == null ? null : mc.level.getEntity(id);
        if (who instanceof AbstractClientPlayer pl && mc.getEntityRenderDispatcher().getRenderer(pl) instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer pr) {
            return new Object[] {pr.getModel(), pl.getSkin().texture(), pl.getSkin().model() == net.minecraft.client.resources.PlayerSkin.Model.SLIM,
                    pl.getGameProfile()};
        }
        if (who instanceof com.starkinhon.battlegroundsmod.boss.SoulBoss boss
                && mc.getEntityRenderDispatcher().getRenderer(boss) instanceof com.starkinhon.battlegroundsmod.client.SoulBossRenderer<?> br) {
            return new Object[] {br.getModel(), texture(br, boss), false, null};
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ResourceLocation texture(com.starkinhon.battlegroundsmod.client.SoulBossRenderer r, com.starkinhon.battlegroundsmod.boss.SoulBoss boss) {
        return r.getTextureLocation(boss);
    }

    // Model space (pixels, y down, facing -z) to entity space (blocks, y up, facing +z), as the renderer places it.
    private static Vector3f toEntity(Vector3f m) {
        return new Vector3f(m.x / 16f * 0.9375f, (1.501f - m.y / 16f) * 0.9375f, -m.z / 16f * 0.9375f);
    }

    // The tip of the right arm's fist and its shoulder, in entity space, with the dive's angles.
    private static Vector3f[] rightArm(boolean slim) {
        float[] r = DIVE.rightArm();
        Quaternionf q = new Quaternionf().rotationZYX(r[2], r[1], r[0]);
        Vector3f pivot = new Vector3f(-5, slim ? 2.5f : 2, 0);
        Vector3f tip = new Vector3f(slim ? -0.5f : -1, 10, 0).rotate(q).add(pivot);
        return new Vector3f[] {toEntity(pivot), toEntity(tip)};
    }

    // Stark's dive with the fist at fistAt, the arms along dir (the page's poseDive); life 0: this frame only, at peak.
    static void dive(int id, Vec3 fistAt, Vec3 dir, float peak, float life) {
        Object[] b = body(id);
        if (b == null || dir.lengthSqr() < 1.0E-6) {
            return;
        }
        Vector3f[] arm = rightArm((boolean) b[2]);
        Vector3f d0 = new Vector3f(arm[1]).sub(arm[0]).rotate(WAIST).normalize();
        Vector3f want = new Vector3f((float) dir.x, (float) dir.y, (float) dir.z).normalize();
        Quaternionf turn = new Quaternionf().rotationTo(d0, want).mul(WAIST);
        Vector3f f0 = new Vector3f(arm[1]).rotate(turn);
        add((PlayerModel<?>) b[0], (ResourceLocation) b[1], (GameProfile) b[3], (boolean) b[2], DIVE, fistAt.subtract(f0.x, f0.y, f0.z), turn, peak, life);
    }

    // Crouched at p (where his chest is), turned to face aim, leaning toward it (the page's poseAt).
    static void crouch(int id, Vec3 p, Vec3 aim, float peak, float life) {
        Object[] b = body(id);
        if (b == null) {
            return;
        }
        Vec3 d = aim.subtract(p);
        float yaw = (float) Math.atan2(d.x, d.z), pitch = (float) -Math.atan2(d.y, Math.hypot(d.x, d.z));
        Quaternionf turn = new Quaternionf().rotationY(yaw).rotateX(pitch * 0.6f);
        add((PlayerModel<?>) b[0], (ResourceLocation) b[1], (GameProfile) b[3], (boolean) b[2], CROUCH, p.add(0, -1.2, 0), turn, peak, life);
    }

    private static void add(PlayerModel<?> model, ResourceLocation skin, GameProfile profile, boolean slim, Pose pose, Vec3 pos, Quaternionf turn,
            float peak, float life) {
        Posed g = new Posed(model, skin, profile, slim, pose, pos, turn, ScreenFx.now(), peak, life);
        if (life <= 0) {
            FRAME.add(g);
            return;
        }
        POSED.add(g);
        if (POSED.size() > 220) {
            POSED.remove(0);
        }
    }

    // The posed ghosts fade out over the next life seconds (the flash: all of them go at once).
    static void fadePosed(float life) {
        double now = ScreenFx.now();
        for (int i = 0; i < POSED.size(); i++) {
            Posed g = POSED.get(i);
            float a = alpha(g, now);
            POSED.set(i, new Posed(g.model(), g.skin(), g.profile(), g.slim(), g.pose(), g.pos(), g.turn(), now, a, life));
        }
    }

    private static float alpha(Posed g, double now) {
        if (g.life() <= 0) {
            return g.peak();
        }
        double k = (now - g.born()) / 20 / g.life();
        return (float) (g.peak() * Math.pow(Math.max(0, 1 - k), 1.5));
    }

    private static void setPart(ModelPart part, float x, float y, float z, float[] r) {
        part.setPos(x, y, z);
        part.setRotation(r[0], r[1], r[2]);
        part.xScale = part.yScale = part.zScale = 1;
        part.visible = true;
    }

    private static void renderPosed(Vec3 cam, double now, MultiBufferSource.BufferSource source) {
        POSED.removeIf(g -> (now - g.born()) / 20 > g.life());
        PoseStack pose = new PoseStack();
        float[] zero = new float[3];
        List<Posed> all = new ArrayList<>(POSED);
        all.addAll(FRAME);
        FRAME.clear();
        for (Posed g : all) {
            float a = alpha(g, now);
            if (a < 0.01f) {
                continue;
            }
            PlayerModel<?> m = g.model();
            ModelPart[] ps = parts(m);
            PartPose[] keep = new PartPose[ps.length];
            boolean[] vis = new boolean[ps.length];
            for (int i = 0; i < ps.length; i++) {
                keep[i] = ps[i].storePose();
                vis[i] = ps[i].visible;
            }
            float ay = g.slim() ? 2.5f : 2;
            Pose p = g.pose();
            setPart(m.head, 0, 0, 0, p.head());
            setPart(m.hat, 0, 0, 0, p.head());
            setPart(m.body, 0, 0, 0, zero);
            setPart(m.jacket, 0, 0, 0, zero);
            setPart(m.rightArm, -5, ay, 0, p.rightArm());
            setPart(m.rightSleeve, -5, ay, 0, p.rightArm());
            setPart(m.leftArm, 5, ay, 0, p.leftArm());
            setPart(m.leftSleeve, 5, ay, 0, p.leftArm());
            setPart(m.rightLeg, -1.9f, 12, 0, p.rightLeg());
            setPart(m.rightPants, -1.9f, 12, 0, p.rightLeg());
            setPart(m.leftLeg, 1.9f, 12, 0, p.leftLeg());
            setPart(m.leftPants, 1.9f, 12, 0, p.leftLeg());
            pose.pushPose();
            pose.translate(g.pos().x - cam.x, g.pos().y - cam.y, g.pos().z - cam.z);
            pose.mulPose(g.turn());
            pose.mulPose(Axis.YP.rotationDegrees(180));
            pose.scale(-1, -1, 1);
            pose.scale(0.9375f, 0.9375f, 0.9375f);
            pose.translate(0, -1.501, 0);
            int color = FastColor.ARGB32.color(Math.round(Math.min(1, a) * 255), 228, 240, 255);
            draw(m, g.skin(), g.profile(), pose, source, color);
            pose.popPose();
            // (the vertices are already written: the parts go back to how the game had them)
            for (int i = 0; i < ps.length; i++) {
                ps[i].loadPose(keep[i]);
                ps[i].visible = vis[i];
            }
        }
        source.endBatch();
    }

    // The body see-through and lit full; with Customizable Player Models, the player's CPM model and skin (Stark 2026-11-26).
    private static void draw(PlayerModel<?> m, ResourceLocation skin, GameProfile profile, PoseStack pose, MultiBufferSource source, int color) {
        com.starkinhon.battlegroundsmod.client.compat.CpmCompat.Bound cpm = com.starkinhon.battlegroundsmod.client.compat.CpmCompat.bind(m, profile,
                skin, source, RenderType::entityTranslucent);
        try {
            m.renderToBuffer(pose, source.getBuffer(cpm != null ? cpm.renderType() : RenderType.entityTranslucent(skin)), 0xF000F0,
                    OverlayTexture.NO_OVERLAY, color);
        } finally {
            if (cpm != null) {
                cpm.end();
            }
        }
    }

    static void render(Vec3 cam, double now) {
        if (!POSED.isEmpty() || !FRAME.isEmpty()) {
            renderPosed(cam, now, Minecraft.getInstance().renderBuffers().bufferSource());
        }
        if (GHOSTS.isEmpty()) {
            return;
        }
        MultiBufferSource.BufferSource source = Minecraft.getInstance().renderBuffers().bufferSource();
        // (the view is already in the shader's ModelViewMat: positions only relative to the camera)
        PoseStack pose = new PoseStack();
        GHOSTS.removeIf(g -> (now - g.born()) / 20 > g.life());
        for (Ghost g : GHOSTS) {
            double k = (now - g.born()) / 20 / g.life();
            float a = (float) (g.peak() * Math.pow(Math.max(0, 1 - k), 1.5));
            if (a < 0.01f) {
                continue;
            }
            ModelPart[] ps = parts(g.model());
            for (int i = 0; i < ps.length; i++) {
                ps[i].loadPose(g.parts()[i]);
            }
            pose.pushPose();
            pose.translate(g.pos().x - cam.x, g.pos().y - cam.y, g.pos().z - cam.z);
            pose.mulPose(Axis.YP.rotationDegrees(180 - g.yaw()));
            pose.scale(-1, -1, 1);
            pose.scale(0.9375f, 0.9375f, 0.9375f);
            pose.translate(0, -1.501, 0);
            int color = FastColor.ARGB32.color(Math.round(a * 255), 228, 240, 255);
            draw(g.model(), g.skin(), g.profile(), pose, source, color);
            pose.popPose();
        }
        source.endBatch();
    }
}
