// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client.fx;

import static com.starkinhon.battlegroundsmod.client.fx.DbFx.bb;
import static com.starkinhon.battlegroundsmod.client.fx.DbFx.rel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.client.outfit.InvertedSpearRenderer;
import com.starkinhon.battlegroundsmod.client.outfit.MeshModel;
import com.starkinhon.battlegroundsmod.power.powers.toji.TjNet;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

// Toji's own effects (power/powers/toji/TjNet, SubNet kinds 560-579), as the approved page draws them
// (forja/toji-boss):
//   FAN     the Corrente de Mil Milhas: a chain with physics (Verlet nodes with weight and inertia, paid out and reeled in
//           from his hand) dragging behind the mod's Lança Invertida as it turns, drawn link by link with Makima's forged
//           links (MkChain) at twice their size and with no red light, the spear's own mesh and texture at the end
//   TOPPLE  a building falling over whole: its blocks copied the moment the event comes (before the server takes them),
//           baked once into GPU buffers (epic.BakedBlocks) and turned over its base edge, then breaking up on the ground
//   SHARDS  an expansion shattering: glassy shards flying out and falling
//   GHOST   the Sumiço's afterimages: a dark shape of dust where he stood for an instant
// The chain, the spear, the buildings and the shards go at the end of the level (FxWorld, renderWorld); the ghosts in
// DbFx's passes. Age in ticks.
public final class TjFx {
    private static final int NODES = 140;
    private static final float SEG = 1.1f;
    private static final float CHAIN_SCALE = 2.5f;
    private static final float SPEAR_SCALE = 2.0f;
    private static final int TOPPLE_FALL = 26;
    private static final int MAX_PIECES = 6000;
    private static final int SHARD_N = 900;
    private static final float[] GHOST = FxDraw.rgb(0x2A282E);

    private static MeshModel spearMesh;
    private static boolean spearBroken;

    private static final class Fan {
        Vec3 c = Vec3.ZERO;
        double th, R, sy, om;
        double updated;
        boolean on = true;
        double offAt = -1;
        final float[] P = new float[NODES * 3];
        final float[] O = new float[NODES * 3];
        int n;
        float rest0 = SEG;
        double lastSim = -1;
    }

    private static final class Topple {
        final int x0, y0, z0, w, h, d, dir;
        final double born, delay;
        final List<Object[]> pieces = new ArrayList<>();
        com.starkinhon.battlegroundsmod.client.fx.epic.BakedBlocks mesh;
        boolean landed;

        Topple(int x0, int y0, int z0, int w, int h, int d, int dir, double born, double delay) {
            this.x0 = x0;
            this.y0 = y0;
            this.z0 = z0;
            this.w = w;
            this.h = h;
            this.d = d;
            this.dir = dir;
            this.born = born;
            this.delay = delay;
        }

        void close() {
            if (mesh != null) {
                mesh.close();
                mesh = null;
            }
        }
    }

    private record Chunk(BlockState state, Vec3 from, Vec3 vel, Vector3f axis, float spin, double born) {
    }

    private record Burst(Vec3 c, float R, int seed, double born) {
    }

    private record Ghost(Vec3 at, float yaw, int pose, double born) {
    }

    private static final Map<Integer, Fan> FANS = new HashMap<>();
    private static final List<Topple> TOPPLES = new ArrayList<>();
    private static final List<Chunk> CHUNKS = new ArrayList<>();
    private static final List<Burst> BURSTS = new ArrayList<>();
    private static final List<Ghost> GHOSTS = new ArrayList<>();

    private TjFx() {
    }

    static boolean handles(int kind) {
        return kind >= TjNet.FIRST && kind <= TjNet.LAST;
    }

    static boolean active() {
        return !FANS.isEmpty() || !TOPPLES.isEmpty() || !CHUNKS.isEmpty() || !BURSTS.isEmpty() || !GHOSTS.isEmpty();
    }

    static void clear() {
        FANS.clear();
        TOPPLES.forEach(Topple::close);
        TOPPLES.clear();
        CHUNKS.clear();
        BURSTS.clear();
        GHOSTS.clear();
    }

    static void receive(int kind, int id, Vec3 pos, Vec3 dir, float a, float b, float c) {
        double now = ScreenFx.now();
        switch (kind) {
            case TjNet.FAN -> {
                Fan f = FANS.get(id);
                if (a <= 0) {
                    if (f != null && f.offAt < 0) {
                        f.on = false;
                        f.offAt = now;
                    }
                    return;
                }
                if (f == null || !f.on) {
                    f = new Fan();
                    FANS.put(id, f);
                }
                f.c = pos;
                f.th = dir.x;
                f.R = dir.y;
                f.sy = dir.z;
                f.om = b;
                f.updated = now;
            }
            case TjNet.TOPPLE -> {
                if (TOPPLES.size() < 24) {
                    Topple t = new Topple(Mth.floor(pos.x), Mth.floor(pos.y), Mth.floor(pos.z), (int) Math.round(dir.x), (int) Math.round(dir.y),
                            (int) Math.round(dir.z), (int) a, now, b);
                    snapshot(t);
                    TOPPLES.add(t);
                }
            }
            case TjNet.SHARDS -> BURSTS.add(new Burst(pos, a, (int) b, now));
            case TjNet.GHOST -> GHOSTS.add(new Ghost(pos, a, (int) b, now));
            default -> {
            }
        }
    }

    private static void prune(double now) {
        Iterator<Fan> it = FANS.values().iterator();
        while (it.hasNext()) {
            Fan f = it.next();
            if (f.on && now - f.updated > 40) {
                f.on = false;
                f.offAt = now;
            }
            if (!f.on && now - f.offAt > 8) {
                it.remove();
            }
        }
        Iterator<Topple> tt = TOPPLES.iterator();
        while (tt.hasNext()) {
            Topple t = tt.next();
            if (t.landed || now - t.born > t.delay + TOPPLE_FALL + 200) {
                t.close();
                tt.remove();
            }
        }
        CHUNKS.removeIf(ch -> now - ch.born() > 34);
        BURSTS.removeIf(b -> now - b.born() > 60);
        GHOSTS.removeIf(g -> now - g.born() > 34);
    }

    // ---------- the ghosts (DbFx's passes) ----------

    static void draw(double now, float partial) {
        if (DbFx.pass) {
            return;
        }
        for (Ghost g : GHOSTS) {
            float age = (float) (now - g.born()) + partial;
            if (age < 0) {
                continue;
            }
            float op = 0.62f * (1 - age / 34f);
            float yaw = g.yaw() * Mth.DEG_TO_RAD;
            float fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
            float crouch = g.pose() == 0 ? 0.7f : 1f;
            Vector3f base = rel(g.at());
            // a shape of dust: legs, body, head, a little smeared the way he went
            for (int i = 0; i < 6; i++) {
                float y = (0.25f + i * 0.33f) * crouch;
                float lean = g.pose() == 1 ? (i - 2) * 0.08f : 0;
                Vector3f p = new Vector3f(base).add(fx * lean, y + age * 0.02f, fz * lean);
                float size = i == 5 ? 0.55f : 0.7f + (i == 3 ? 0.15f : 0);
                bb(p, size * (1 + age * 0.03f), i * 1.3f + age * 0.05f, FxDraw.SMOKE, 0, GHOST, op, false);
            }
            Vector3f trail = new Vector3f(base).add(-fx * 0.7f, 1.0f * crouch, -fz * 0.7f);
            bb(trail, 1.2f + age * 0.05f, age * 0.04f, FxDraw.SMOKE, 0, GHOST, op * 0.5f, false);
        }
    }

    // ---------- the chain, the spear, the buildings, the shards (end of the level) ----------

    static void renderWorld(Vec3 camera) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null || !active()) {
            return;
        }
        float partial = mc.getTimer().getGameTimeDeltaPartialTick(false);
        double now = ScreenFx.now();
        prune(now);
        double t = now + partial;
        MultiBufferSource.BufferSource source = mc.renderBuffers().bufferSource();
        PoseStack pose = new PoseStack();
        VertexConsumer solid = source.getBuffer(FxRenderTypes.SOLID);
        for (Map.Entry<Integer, Fan> e : FANS.entrySet()) {
            if (e.getValue().on) {
                fan(e.getKey(), e.getValue(), level, camera, t, partial, solid);
            }
        }
        for (Burst b : BURSTS) {
            shards(b, camera, (float) (t - b.born()), solid);
        }
        source.endBatch(FxRenderTypes.SOLID);
        for (Map.Entry<Integer, Fan> e : FANS.entrySet()) {
            Fan f = e.getValue();
            if (f.on && f.n >= 2) {
                spear(f, camera, t, pose, source, level);
            }
        }
        source.endBatch();
        BlockRenderDispatcher blocks = mc.getBlockRenderer();
        // one building baked a frame at most (each is thousands of block models): they start baking 6 ticks early
        boolean[] baked = {false};
        for (Topple tp : TOPPLES) {
            topple(tp, camera, t, now, pose, level, baked);
        }
        for (Chunk ch : CHUNKS) {
            float age = (float) (t - ch.born());
            float s = 1 - smooth((age - 22) / 12);
            if (s <= 0.02f || age < 0) {
                continue;
            }
            Vec3 p = ch.from().add(ch.vel().scale(age)).add(0, -0.045 * age * age, 0);
            int light = LevelRenderer.getLightColor(level, BlockPos.containing(p));
            pose.pushPose();
            pose.translate(p.x - camera.x, p.y - camera.y, p.z - camera.z);
            pose.mulPose(new Quaternionf().rotationAxis(ch.spin() * age, ch.axis()));
            pose.scale(1.4f * s, 1.4f * s, 1.4f * s);
            pose.translate(-0.5, -0.5, -0.5);
            blocks.renderSingleBlock(ch.state(), pose, source, light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        source.endBatch();
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    // ---- FAN ----

    private static Vec3 spearAt(Fan f, double th) {
        return new Vec3(f.c.x + Math.cos(th) * f.R, f.c.y + f.sy, f.c.z + Math.sin(th) * f.R);
    }

    private static double angle(Fan f, double t) {
        // the server's angle, run on with its speed between packets (never more than 3 ticks ahead)
        return f.th + f.om * Math.min(3, Math.max(0, t - f.updated)) / 20.0;
    }

    private static Vec3 hand(int id, Fan f, Level level, float partial, double th) {
        Entity e = level.getEntity(id);
        Vec3 base = e != null ? e.getPosition(partial) : f.c;
        double up = e != null ? e.getBbHeight() * 1.3 : 2.4;
        return base.add(Math.cos(th) * 0.35, up, Math.sin(th) * 0.35);
    }

    private static void fan(int id, Fan f, Level level, Vec3 camera, double t, float partial, VertexConsumer solid) {
        double th = angle(f, t);
        Vec3 hand = hand(id, f, level, partial, th);
        Vec3 S = spearAt(f, th);
        if (f.n < 2) {
            f.n = 2;
            f.rest0 = SEG;
            set(f.P, 0, hand);
            set(f.O, 0, hand);
            set(f.P, 1, S);
            set(f.O, 1, S);
            f.lastSim = t;
        }
        double dtTicks = Math.max(0, Math.min(1.5, t - f.lastSim));
        f.lastSim = t;
        float dt = (float) (dtTicks / 20.0);
        if (dt > 0) {
            // as much chain as the reach needs, a little slack so it curves behind the spear
            float need = (float) (hand.distanceTo(S) * 1.12);
            float cur = (f.n - 2) * SEG + f.rest0;
            if (cur < need) {
                payout(f, need - cur);
            } else if (cur > need + 2) {
                reel(f, cur - need);
            }
            for (int s = 0; s < 3; s++) {
                double a = th - f.om * dt * (2 - s) / 3.0;
                Vec3 sp = spearAt(f, a);
                step(f, dt / 3, hand, sp);
            }
        }
        Vector3f[] poly = new Vector3f[f.n];
        for (int i = 0; i < f.n; i++) {
            poly[i] = new Vector3f((float) (f.P[i * 3] - camera.x), (float) (f.P[i * 3 + 1] - camera.y), (float) (f.P[i * 3 + 2] - camera.z));
        }
        MkChain.draw(solid, poly, 9999, 0, 4242 + id, new MkChain.Light(0, -1, 0, (float) t), CHAIN_SCALE);
    }

    private static void set(float[] a, int i, Vec3 v) {
        a[i * 3] = (float) v.x;
        a[i * 3 + 1] = (float) v.y;
        a[i * 3 + 2] = (float) v.z;
    }

    // More chain out of his hand: new nodes come in next to the hand.
    private static void payout(Fan f, float len) {
        f.rest0 += len;
        while (f.rest0 > SEG && f.n < NODES) {
            System.arraycopy(f.P, 3, f.P, 6, (f.n - 1) * 3);
            System.arraycopy(f.O, 3, f.O, 6, (f.n - 1) * 3);
            System.arraycopy(f.P, 0, f.P, 3, 3);
            System.arraycopy(f.O, 0, f.O, 3, 3);
            f.n++;
            f.rest0 -= SEG;
        }
        if (f.n >= NODES) {
            f.rest0 = Math.min(f.rest0, SEG * 4);
        }
    }

    private static void reel(Fan f, float len) {
        f.rest0 -= len;
        while (f.rest0 < 0.05f && f.n > 2) {
            System.arraycopy(f.P, 6, f.P, 3, (f.n - 2) * 3);
            System.arraycopy(f.O, 6, f.O, 3, (f.n - 2) * 3);
            f.n--;
            f.rest0 += SEG;
        }
        if (f.rest0 < 0.05f) {
            f.rest0 = 0.05f;
        }
    }

    // Verlet: each node keeps its speed and falls; then the links pull them back to their length (only when stretched),
    // the hand and the spear held where they are. Nothing goes under the street.
    private static void step(Fan f, float dt, Vec3 hand, Vec3 end) {
        int n = f.n;
        float[] P = f.P, O = f.O;
        set(P, 0, hand);
        set(O, 0, hand);
        set(P, n - 1, end);
        set(O, n - 1, end);
        float g = 30 * dt * dt;
        for (int i = 1; i < n - 1; i++) {
            int o = i * 3;
            for (int k = 0; k < 3; k++) {
                float v = (P[o + k] - O[o + k]) * 0.995f;
                O[o + k] = P[o + k];
                P[o + k] += v;
            }
            P[o + 1] -= g;
        }
        float floor = (float) f.c.y + 0.15f;
        for (int it = 0; it < 14; it++) {
            for (int i = 0; i < n - 1; i++) {
                int a = i * 3, b = a + 3;
                float dx = P[b] - P[a], dy = P[b + 1] - P[a + 1], dz = P[b + 2] - P[a + 2];
                float L = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                float rest = i == 0 ? f.rest0 : SEG;
                if (L <= rest || L < 1.0E-6f) {
                    continue;
                }
                float wa = i == 0 ? 0 : 1, wb = i + 1 == n - 1 ? 0 : 1, w = wa + wb;
                if (w <= 0) {
                    continue;
                }
                float k = (L - rest) / L / w;
                P[a] += dx * k * wa;
                P[a + 1] += dy * k * wa;
                P[a + 2] += dz * k * wa;
                P[b] -= dx * k * wb;
                P[b + 1] -= dy * k * wb;
                P[b + 2] -= dz * k * wb;
            }
            for (int i = 1; i < n - 1; i++) {
                if (P[i * 3 + 1] < floor) {
                    P[i * 3 + 1] = floor;
                }
            }
        }
    }

    // The mod's own spear at the end of the chain, its tip leading the turn.
    private static void spear(Fan f, Vec3 camera, double t, PoseStack pose, MultiBufferSource source, Level level) {
        if (spearBroken) {
            return;
        }
        if (spearMesh == null) {
            try {
                spearMesh = MeshModel.load("lanca_invertida");
            } catch (RuntimeException e) {
                spearBroken = true;
                BattlegroundsMod.LOGGER.error("Failed to load lanca_invertida mesh for Toji's chain", e);
                return;
            }
        }
        int e = (f.n - 1) * 3;
        Vec3 S = new Vec3(f.P[e], f.P[e + 1], f.P[e + 2]);
        double th = angle(f, t);
        float sign = f.om >= 0 ? 1 : -1;
        Vector3f tan = new Vector3f((float) -Math.sin(th) * sign, 0, (float) Math.cos(th) * sign).normalize();
        int light = LevelRenderer.getLightColor(level, BlockPos.containing(S));
        pose.pushPose();
        pose.translate(S.x - camera.x, S.y - camera.y, S.z - camera.z);
        pose.mulPose(new Quaternionf().rotationTo(0, 1, 0, tan.x, tan.y, tan.z));
        pose.scale(SPEAR_SCALE, SPEAR_SCALE, SPEAR_SCALE);
        // the chain is tied at the butt: the mesh runs from 0 (the guard) to its tip along +y
        VertexConsumer out = source.getBuffer(RenderType.entityCutoutNoCull(InvertedSpearRenderer.TEXTURE));
        spearMesh.emit(out, pose.last(), light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    // ---- TOPPLE ----

    // The building's blocks that show (a face open to air or out of the box), copied before the server takes them.
    private static void snapshot(Topple t) {
        Level level = Minecraft.getInstance().level;
        if (level == null || t.w <= 0 || t.h <= 0 || t.d <= 0) {
            return;
        }
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        int top = t.y0 + t.h + 2;
        for (int y = t.y0; y <= top; y++) {
            for (int z = t.z0; z < t.z0 + t.d; z++) {
                for (int x = t.x0; x < t.x0 + t.w; x++) {
                    p.set(x, y, z);
                    BlockState st = level.getBlockState(p);
                    if (st.isAir() || !st.getFluidState().isEmpty() || st.hasBlockEntity() || st.getRenderShape() != RenderShape.MODEL) {
                        continue;
                    }
                    boolean shows = false;
                    for (Direction d : Direction.values()) {
                        q.setWithOffset(p, d);
                        if (q.getX() < t.x0 || q.getX() >= t.x0 + t.w || q.getZ() < t.z0 || q.getZ() >= t.z0 + t.d || q.getY() > top
                                || !level.getBlockState(q).canOcclude()) {
                            shows = true;
                            break;
                        }
                    }
                    if (shows) {
                        t.pieces.add(new Object[] {st, x, y, z});
                        if (t.pieces.size() >= MAX_PIECES) {
                            return;
                        }
                    }
                }
            }
        }
    }

    // The pivot: the base edge on the side it falls to.
    private static Vec3 pivot(Topple t) {
        return switch (t.dir) {
            case 0 -> new Vec3(t.x0 + t.w, t.y0, t.z0 + t.d * 0.5);
            case 1 -> new Vec3(t.x0, t.y0, t.z0 + t.d * 0.5);
            case 2 -> new Vec3(t.x0 + t.w * 0.5, t.y0, t.z0 + t.d);
            default -> new Vec3(t.x0 + t.w * 0.5, t.y0, t.z0);
        };
    }

    private static Quaternionf turn(Topple t, float ang) {
        return switch (t.dir) {
            case 0 -> new Quaternionf().rotationZ(-ang);
            case 1 -> new Quaternionf().rotationZ(ang);
            case 2 -> new Quaternionf().rotationX(ang);
            default -> new Quaternionf().rotationX(-ang);
        };
    }

    private static void topple(Topple t, Vec3 camera, double tt, double now, PoseStack pose, Level level, boolean[] baked) {
        double age = tt - t.born - t.delay;
        if (age < -6 || t.pieces.isEmpty()) {
            return;
        }
        Vec3 pv = pivot(t);
        if (t.mesh == null) {
            if (baked[0]) {
                return;
            }
            baked[0] = true;
            // lit once, from the sky over it: built a single time, then one matrix a frame
            int light = LevelRenderer.getLightColor(level, BlockPos.containing(t.x0 + t.w * 0.5, t.y0 + t.h + 2, t.z0 + t.d * 0.5));
            t.mesh = com.starkinhon.battlegroundsmod.client.fx.epic.BakedBlocks.bake(sink -> {
                for (Object[] pc : t.pieces) {
                    sink.add((BlockState) pc[0], (float) ((int) pc[1] - pv.x), (float) ((int) pc[2] - pv.y), (float) ((int) pc[3] - pv.z), light);
                }
            });
        }
        if (age < 0) {
            return;
        }
        float k = (float) Math.min(1, age / TOPPLE_FALL);
        // it leans slowly and then comes down fast (a falling tower), a little bounce at the end
        float ang = (float) (Math.PI / 2 * k * k);
        pose.pushPose();
        pose.translate(pv.x - camera.x, pv.y - camera.y, pv.z - camera.z);
        pose.mulPose(turn(t, ang));
        t.mesh.draw(pose.last().pose());
        pose.popPose();
        if (k >= 1 && !t.landed) {
            t.landed = true;
            land(t, pv, now);
        }
    }

    // On the ground it breaks up: its blocks fly off along where it lay.
    private static void land(Topple t, Vec3 pv, double now) {
        RandomSource rnd = RandomSource.create(t.x0 * 31L + t.z0 * 7L + t.h);
        int n = Math.min(t.pieces.size(), 34);
        Vector3f dir = switch (t.dir) {
            case 0 -> new Vector3f(1, 0, 0);
            case 1 -> new Vector3f(-1, 0, 0);
            case 2 -> new Vector3f(0, 0, 1);
            default -> new Vector3f(0, 0, -1);
        };
        for (int i = 0; i < n; i++) {
            Object[] pc = t.pieces.get(rnd.nextInt(t.pieces.size()));
            double along = rnd.nextDouble() * t.h;
            double side = (rnd.nextDouble() - 0.5) * (t.dir < 2 ? t.d : t.w);
            Vec3 from = pv.add(dir.x * along + (t.dir < 2 ? 0 : side), 1 + rnd.nextDouble() * 2, dir.z * along + (t.dir < 2 ? side : 0));
            Vec3 vel = new Vec3((rnd.nextDouble() - 0.5) * 0.5 + dir.x * 0.15, 0.25 + rnd.nextDouble() * 0.45, (rnd.nextDouble() - 0.5) * 0.5 + dir.z * 0.15);
            Vector3f axis = new Vector3f(rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f);
            if (axis.lengthSquared() < 1.0E-4f) {
                axis.set(1, 0, 0);
            }
            CHUNKS.add(new Chunk((BlockState) pc[0], from, vel, axis.normalize(), (rnd.nextFloat() - 0.5f) * 0.5f, now));
        }
    }

    // ---- SHARDS ----

    private static float hash(int a, int b, int c) {
        int h = a * 374761393 + b * 668265263 + c * 1442695041;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535f;
    }

    // The barrier's pieces: on a dome of radius R round the middle, thrown out and falling, spinning, shrinking at the end.
    private static void shards(Burst b, Vec3 camera, float age, VertexConsumer out) {
        if (age < 0) {
            return;
        }
        float s = 1 - smooth((age - 40) / 20);
        if (s <= 0.01f) {
            return;
        }
        Vector3f c = new Vector3f((float) (b.c().x - camera.x), (float) (b.c().y - camera.y), (float) (b.c().z - camera.z));
        for (int i = 0; i < SHARD_N; i++) {
            float u = hash(i, b.seed(), 1), v = hash(i, b.seed(), 2);
            float th = u * Mth.TWO_PI, ph = (float) Math.acos(1 - v * 1.1f);
            float sx = Mth.sin(ph) * Mth.cos(th), sy = Mth.cos(ph), sz = Mth.sin(ph) * Mth.sin(th);
            float speed = 0.25f + hash(i, b.seed(), 3) * 0.55f;
            float px = c.x + sx * b.R() + sx * speed * age;
            float py = c.y + sy * b.R() * 0.8f + (sy * speed + 0.1f) * age - 0.012f * age * age;
            float pz = c.z + sz * b.R() + sz * speed * age;
            float size = (0.35f + hash(i, b.seed(), 4) * 0.9f) * s;
            float spin = age * (hash(i, b.seed(), 5) - 0.5f) * 0.6f + hash(i, b.seed(), 6) * 6;
            Vector3f ax = new Vector3f(hash(i, b.seed(), 7) - 0.5f, hash(i, b.seed(), 8) - 0.5f, hash(i, b.seed(), 9) - 0.5f);
            if (ax.lengthSquared() < 1.0E-4f) {
                ax.set(0, 1, 0);
            }
            Quaternionf q = new Quaternionf().rotationAxis(spin, ax.normalize());
            Vector3f e1 = q.transform(new Vector3f(size, 0, 0)), e2 = q.transform(new Vector3f(0, size * 0.7f, 0));
            float tone = hash(i, b.seed(), 10);
            float r = 0.62f + tone * 0.35f, g = 0.7f + tone * 0.28f, bl = 0.86f + tone * 0.14f;
            if (tone < 0.18f) {
                r = 0.1f;
                g = 0.1f;
                bl = 0.16f;
            }
            // a sliver: three corners and the last one pulled in
            out.addVertex(px - e1.x, py - e1.y, pz - e1.z).setColor(r, g, bl, 1f);
            out.addVertex(px + e2.x, py + e2.y, pz + e2.z).setColor(r, g, bl, 1f);
            out.addVertex(px + e1.x, py + e1.y, pz + e1.z).setColor(r * 0.8f, g * 0.8f, bl * 0.85f, 1f);
            out.addVertex(px - e2.x * 0.3f, py - e2.y * 0.3f, pz - e2.z * 0.3f).setColor(r * 0.7f, g * 0.7f, bl * 0.8f, 1f);
        }
    }
}
