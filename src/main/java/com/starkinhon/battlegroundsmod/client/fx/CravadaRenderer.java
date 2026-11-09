package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joml.Vector3f;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import com.starkinhon.battlegroundsmod.network.ModPayloads.SukunaCravada;
import com.starkinhon.battlegroundsmod.power.powers.sukuna.BTrack;
import com.starkinhon.battlegroundsmod.power.powers.sukuna.Cravada;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// Sukuna's B, the cravada (the old Shift+X), as in the 3D preview Stark approved (forja/sukuna-desmantelar, "Shift+X";
// server side in power/powers/sukuna/Cravada). Everything is the preview's, laid out in his frame at the moment of the
// punch (x to his left, z ahead):
//   - the sprint: wind needles born at his back that stretch backwards, curved wind off a shoulder or a foot, a flat
//     stroke and chips kicked back at each footfall, and the launch burst (strokes, a star, dashes flung off);
//   - vertical speed lines around the target on its way up and on its way down; stars at the uppercut, the throw, the
//     impact and the stomp; chips off the uppercut and out of the ground; rubble blocks (the ground's own) thrown out;
//   - rings of Shift+Z strokes running out over the ground at the impact and at the landing;
//   - the stomp's web: Shift+Z strokes laid flat on the ground (each on the ground under it), in three stages ~0.7 s
//     apart, each a wider layer around the last, with stars at the first spokes' tips; then it flashes, four huge straight
//     cuts cross it all and the strokes burn away; then the ground inside it blows up: the blocks the server breaks
//     (Cravada.burstBlocks, read before they go) tumble up and out like the preview's slabs, and clods of the ground fly.
public final class CravadaRenderer {
    // The preview's timings (ticks): the web's stages, the flash (SX_BOOM), the cut before the burst (SX_CUT).
    private static final double SX_BOOM = BTrack.BOOM - BTrack.LAND, SX_CUT = BTrack.BURST - BTrack.BOOM, FORM = 1.0;
    private static final double[][] S_WEB = {{14, 17, 5.0, 8.6, 6.0, 7.4, 8.4, 1.15}, {28, 25, 8.6, 13.2, 9.8, 11.4, 12.8, 1.3}};
    private static final double GLOW = 2;

    // One stroke of the web, in his frame around the body: a spoke (angle a, middle at r) or a ring piece (middle mx, mz).
    private record Stroke(boolean spoke, double a, double r, double mx, double mz, double dx, double dz, double len, double thick, double t0,
            float skew, float rough, float seed) {
    }

    private static final List<Stroke> WEB = web();

    private static final class Run {
        final int id, seed;
        final Vec3 fwd, left;
        double runAt = -1, hitAt = -1, relAt = -1, impactAt = -1, landAt = -1, burstAt = -1;
        int targetId = -1;
        Vec3 start = Vec3.ZERO, hit = Vec3.ZERO, rel = Vec3.ZERO, impact = Vec3.ZERO, chest = Vec3.ZERO;
        double impactGround, hitGround;
        final Vec3[] windAt = new Vec3[16], curveAt = new Vec3[6], stepAt = new Vec3[3];
        BlockState ground = Blocks.DIRT.defaultBlockState();
        double[] webY;
        boolean broken;
        List<Piece> pieces;
        List<Clod> clods;

        Run(int id, int seed, Vec3 fwd) {
            this.id = id;
            this.seed = seed;
            this.fwd = fwd;
            this.left = new Vec3(fwd.z, 0, -fwd.x);
        }

        double end() {
            if (burstAt >= 0) {
                return burstAt + 46;
            }
            if (landAt >= 0) {
                return landAt + SX_BOOM + SX_CUT + 50;
            }
            if (hitAt >= 0) {
                return hitAt + BTrack.LAND + 20;
            }
            return runAt + 40;
        }

        Vec3 at(Vec3 o, double x, double y, double z) {
            return o.add(left.scale(x)).add(0, y, 0).add(fwd.scale(z));
        }

        Vec3 dir(double x, double y, double z) {
            return left.scale(x).add(0, y, 0).add(fwd.scale(z)).normalize();
        }
    }

    // A block the burst takes, at rest where it was until its turn comes; and a clod of the ground thrown up.
    private record Piece(BlockState state, Vec3 at, double r, double ux, double uz, double h0, double h1, double h2, double h3) {
    }

    private record Clod(BlockState state, Vec3 c, double R, double an, double h0, double h2, double h3, double h4) {
    }

    private static final Map<Integer, Run> RUNS = new HashMap<>();

    private CravadaRenderer() {
    }

    // ---------- the web, as the preview builds it ----------

    private static List<Stroke> web() {
        List<Stroke> out = new ArrayList<>();
        int n = 11;
        double[] spokeA = new double[n];
        for (int k = 0; k < n; k++) {
            double h = hash(k * 3.3 + 1), a = (k + (hash(k * 1.9) - 0.5) * 0.5) / n * 6.2832, L = 2.6 + 2.8 * h;
            spokeA[k] = a;
            out.add(new Stroke(true, a, 0.3 + L / 2, 0, 0, 0, 0, L, 0.45 + 0.35 * hash(k * 5.1), 0.4 * hash(k * 7.7),
                    (float) (0.55 + 0.5 * hash(k * 8.3)), (float) (0.5 + 0.5 * hash(k * 9.9)), (float) (k * 2.7)));
        }
        double[] rings = {1.3, 2.3, 3.4};
        for (int j = 0; j < 3; j++) {
            double rj = rings[j];
            for (int k = 0; k < n; k++) {
                if (hash(k * 4.1 + j * 7.3) < 0.25) {
                    continue;
                }
                double a0 = spokeA[k], a1 = k + 1 < n ? spokeA[k + 1] : spokeA[0] + 6.2832;
                double r0 = rj * (1 + (hash(k + j * 3.1) - 0.5) * 0.12), r1 = rj * (1 + (hash(k + 1 + j * 3.1) - 0.5) * 0.12);
                out.add(piece(a0, a1, r0, r1, 0.94, 1.12, 0.22 + 0.14 * hash(k * 2.2 + j), 1 + rj * 0.5 + 0.4 * hash(k * 6.6 + j),
                        0.8 + 0.5 * hash(k * 3.9 + j), 0.6 + 0.4 * hash(k * 1.7 + j), 50 + k * 1.3 + j * 7.1));
            }
        }
        for (int si = 0; si < S_WEB.length; si++) {
            double[] st = S_WEB[si];
            int m = (int) st[1];
            double t = st[0], sr0 = st[2], sr1 = st[3], th = st[7];
            double[] ang = new double[m];
            for (int k = 0; k < m; k++) {
                ang[k] = (k + (hash(k * 2.3 + si * 11) - 0.5) * 0.5) / m * 6.2832 + si * 0.13;
            }
            for (int k = 0; k < m; k++) {
                if (hash(k * 5.7 + si * 3.3) < 0.15) {
                    continue;
                }
                double L = (sr1 - sr0) * (0.75 + 0.45 * hash(k * 1.3 + si * 7));
                out.add(new Stroke(true, ang[k], sr0 - 0.4 + L / 2, 0, 0, 0, 0, L, (0.5 + 0.35 * hash(k * 8.1 + si)) * th,
                        t + 0.4 * hash(k * 7.1 + si), (float) (0.55 + 0.5 * hash(k * 2.9 + si)), (float) (0.5 + 0.5 * hash(k * 6.3 + si)),
                        (float) (300 + si * 100 + k * 1.7)));
            }
            for (int j = 0; j < 3; j++) {
                double rj = st[4 + j];
                for (int k = 0; k < m; k++) {
                    if (hash(k * 4.7 + j * 9.1 + si * 13) < 0.2) {
                        continue;
                    }
                    double a0 = ang[k], a1 = k + 1 < m ? ang[k + 1] : ang[0] + 6.2832;
                    double r0 = rj * (1 + (hash(k + j * 3.7 + si) - 0.5) * 0.1), r1 = rj * (1 + (hash(k + 1 + j * 3.7 + si) - 0.5) * 0.1);
                    out.add(piece(a0, a1, r0, r1, 0.95, 1.1, (0.24 + 0.14 * hash(k * 2.6 + j + si)) * th,
                            t + 1 + (rj - sr0) * 0.35 + 0.4 * hash(k * 6.1 + j + si), 0.8 + 0.5 * hash(k * 3.3 + j + si),
                            0.6 + 0.4 * hash(k * 1.9 + j + si), 500 + si * 100 + k * 1.1 + j * 5.3));
                }
            }
        }
        return out;
    }

    // A ring piece between two spokes: from (a0, r0) to (a1, r1), its middle pulled in a little.
    private static Stroke piece(double a0, double a1, double r0, double r1, double in, double longer, double thick, double t0, double skew,
            double rough, double seed) {
        double ax = Math.cos(a0) * r0, az = Math.sin(a0) * r0, bx = Math.cos(a1) * r1, bz = Math.sin(a1) * r1;
        double dx = bx - ax, dz = bz - az, len = Math.sqrt(dx * dx + dz * dz);
        return new Stroke(false, 0, 0, (ax + bx) * 0.5 * in, (az + bz) * 0.5 * in, dx / len, dz / len, len * longer, thick, t0, (float) skew,
                (float) rough, (float) seed);
    }

    // ---------- from the server ----------

    public static void event(int id, int kind, int ticks, Vec3 a, Vec3 b, int seed) {
        double now = now();
        if (kind == SukunaCravada.CANCEL) {
            RUNS.remove(id);
            return;
        }
        Run r = RUNS.get(id);
        if (r == null || kind == SukunaCravada.RUN) {
            Vec3 f = new Vec3(b.x, 0, b.z);
            r = new Run(id, seed, f.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : f.normalize());
            r.runAt = now - (kind == SukunaCravada.RUN ? 0 : 8);
            r.start = a;
            RUNS.put(id, r);
            if (kind == SukunaCravada.RUN) {
                return;
            }
        }
        switch (kind) {
            case SukunaCravada.HIT -> {
                r.hitAt = now;
                r.hit = a;
                r.targetId = ticks;
                r.hitGround = groundUnder(a, 4);
                SukunaCutsRenderer.star(a, 2.2f, seed);
            }
            case SukunaCravada.RELEASE -> {
                r.relAt = now;
                r.rel = a;
                SukunaCutsRenderer.star(a, 2.0f, seed + 1);
            }
            case SukunaCravada.IMPACT -> {
                r.impactAt = now;
                r.impact = a;
                r.impactGround = groundUnder(a, 3);
                r.ground = groundState(a);
                SukunaCutsRenderer.star(a.add(0, 0.5, 0), 3.2f, seed + 2);
            }
            case SukunaCravada.LAND -> {
                r.landAt = now;
                r.chest = a;
                r.webY = null;
                SukunaCutsRenderer.star(a.add(0, 0.3, 0), 2.8f, seed + 3);
            }
            case SukunaCravada.BURST -> {
                r.burstAt = now;
                r.broken = ticks == 1;
                if (r.landAt < 0) {
                    r.landAt = now - SX_BOOM - SX_CUT;
                    r.chest = a;
                }
                if (r.pieces == null) {
                    readGround(r);
                }
            }
            default -> {
            }
        }
    }

    public static boolean active() {
        return !RUNS.isEmpty();
    }

    public static void clear() {
        RUNS.clear();
    }

    private static double now() {
        return ScreenFx.now() + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
    }

    private static double hash(double n) {
        double s = Math.sin(n * 127.1 + 311.7) * 43758.5453;
        return s - Math.floor(s);
    }

    private static double out(double u) {
        return 1 - Math.pow(1 - u, 3);
    }

    private static double snap(double u) {
        return 1 - Math.pow(1 - u, 5);
    }

    private static double cl(double x, double a, double b) {
        return Math.max(a, Math.min(b, x));
    }

    private static double fract(double x) {
        return x - Math.floor(x);
    }

    // The ground's height under a point: the first block top from above it down.
    private static double groundUnder(Vec3 at, double above) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return at.y;
        }
        Vec3 from = at.add(0, above, 0);
        var hit = minecraft.level.clip(new ClipContext(from, from.add(0, -above - 6, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                minecraft.player));
        return hit.getType() == HitResult.Type.MISS ? at.y : hit.getLocation().y;
    }

    private static BlockState groundState(Vec3 at) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            for (int dy = 0; dy <= 3; dy++) {
                BlockState s = level.getBlockState(BlockPos.containing(at.x, at.y - 0.2 - dy, at.z));
                if (!s.isAir() && s.getFluidState().isEmpty()) {
                    return s;
                }
            }
        }
        return Blocks.DIRT.defaultBlockState();
    }

    // The ground inside the web, read just before it blows: the blocks the server will break (same rules, same list) and
    // the clods thrown up off its top.
    private static void readGround(Run r) {
        ClientLevel level = Minecraft.getInstance().level;
        r.pieces = new ArrayList<>();
        r.clods = new ArrayList<>();
        if (level == null) {
            return;
        }
        Vec3 c = r.chest;
        for (BlockPos pos : Cravada.burstBlocks(level, c)) {
            BlockState state = level.getBlockState(pos);
            Vec3 mid = Vec3.atCenterOf(pos);
            double dx = mid.x - c.x, dz = mid.z - c.z, d = Math.sqrt(dx * dx + dz * dz);
            double ux = d < 1.0E-3 ? 1 : dx / d, uz = d < 1.0E-3 ? 0 : dz / d;
            double k = pos.getX() * 1.7 + pos.getZ() * 3.1 + pos.getY() * 0.7;
            r.pieces.add(new Piece(state, mid, d, ux, uz, hash(k + 1.3), hash(k * 2.9 + 5.1), hash(k * 4.3 + 2.2), hash(k * 6.1 + 7.7)));
        }
        for (int k = 0; k < 90; k++) {
            double h0 = hash(k * 1.3 + 7), h1 = hash(k * 2.9 + 1), h2 = hash(k * 4.1 + 3), h3 = hash(k * 5.3 + 5), h4 = hash(k * 6.7 + 2);
            double R = 13 * Math.sqrt(h0), an = h1 * 6.2832;
            Vec3 at = new Vec3(c.x + Math.cos(an) * R, c.y, c.z + Math.sin(an) * R);
            double y = groundUnder(at, 3);
            BlockState state = groundState(new Vec3(at.x, y, at.z));
            r.clods.add(new Clod(state, new Vec3(c.x, y, c.z), R, an, h0, h2, h3, h4));
        }
    }

    // ---------- drawing (FxWorld, end of the level) ----------

    private static ShaderInstance shader;
    private static Vec3 cam = Vec3.ZERO;
    private static Vec3 camRight = new Vec3(1, 0, 0), camUp = new Vec3(0, 1, 0);

    static void render(Vec3 camera) {
        double now = now();
        RUNS.values().removeIf(r -> now > r.end() || now < r.runAt - 40);
        shader = FxShaders.sukunaFx;
        if (!active() || shader == null) {
            return;
        }
        cam = camera;
        Camera c = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vector3f left = c.getLeftVector(), up = c.getUpVector();
        camRight = new Vec3(-left.x(), -left.y(), -left.z());
        camUp = new Vec3(up.x(), up.y(), up.z());
        for (Run r : RUNS.values()) {
            // the ground is read the beat the web cuts it, before the server takes it
            if (r.landAt >= 0 && r.pieces == null && now - r.landAt >= SX_BOOM) {
                readGround(r);
            }
        }
        drawBlocks(now);
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.depthMask(false);
        for (Run r : RUNS.values()) {
            if (r.start.distanceToSqr(camera) > 160 * 160) {
                continue;
            }
            drawSprint(r, now);
            drawFlight(r, now);
            drawRings(r, now);
            drawWeb(r, now);
        }
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
    }

    private static void put(BufferBuilder b, Vec3 p, float u, float v) {
        Vec3 m = p.subtract(cam);
        b.addVertex((float) m.x, (float) m.y, (float) m.z).setUv(u, v);
    }

    private static void quad(SukunaCutsRenderer.U u, Vec3 c, Vec3 r, Vec3 f) {
        u.apply(shader);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        put(b, c.subtract(r).subtract(f), 0, 0);
        put(b, c.add(r).subtract(f), 1, 0);
        put(b, c.add(r).add(f), 1, 1);
        put(b, c.subtract(r).add(f), 0, 1);
        MeshData mesh = b.build();
        if (mesh != null) {
            RenderSystem.setShader(() -> shader);
            BufferUploader.drawWithShader(mesh);
        }
    }

    private static void billboard(SukunaCutsRenderer.U u, Vec3 c, double half, double spin) {
        double cs = Math.cos(spin), sn = Math.sin(spin);
        quad(u, c, camRight.scale(cs).add(camUp.scale(sn)).scale(half), camUp.scale(cs).subtract(camRight.scale(sn)).scale(half));
    }

    // The preview's "axial": a strip along d, turned to face the camera.
    private static void axial(SukunaCutsRenderer.U u, Vec3 c, Vec3 d, double len, double wid) {
        Vec3 view = cam.subtract(c).normalize();
        Vec3 wv = d.cross(view);
        wv = wv.lengthSqr() < 1.0E-6 ? new Vec3(0, 1, 0) : wv.normalize();
        quad(u, c, d.scale(len / 2), wv.scale(wid / 2));
    }

    // The preview's "flat": a strip along d lying on the ground.
    private static void flat(SukunaCutsRenderer.U u, Vec3 c, Vec3 d, double len, double wid) {
        Vec3 wv = new Vec3(0, 1, 0).cross(d);
        wv = wv.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : wv.normalize();
        quad(u, c, d.scale(len / 2), wv.scale(wid / 2));
    }

    // A Shift+Z stroke's uniforms: grow, flash, tear.
    private static SukunaCutsRenderer.U stroke(float seed, float skew, float rough, double grow, double flash, double tear) {
        SukunaCutsRenderer.U u = SukunaCutsRenderer.U.of(5);
        u.seed = seed;
        u.s0 = 0;
        u.s1 = 1;
        u.skew = skew;
        u.rough = rough;
        u.grow = (float) grow;
        u.flash = (float) flash;
        u.tear = (float) tear;
        return u;
    }

    // The preview's life of a sprint stroke: snaps across in form, holds, burns away.
    private static SukunaCutsRenderer.U life(float seed, float skew, float rough, double lt, double form, double hold, double burn) {
        return stroke(seed, skew, rough, snap(cl(lt / form, 0, 1)), Math.exp(-lt / 1.5), lt > form + hold ? cl((lt - form - hold) / burn, 0, 1) * 1.05 : 0);
    }

    private static void chip(Vec3 p, double size, double h1, double h3, double s, int k) {
        SukunaCutsRenderer.U u = SukunaCutsRenderer.U.of(2);
        u.n = 3 + (float) Math.floor(hash(k * 2.2) * 4);
        u.asp = 0.6f + (float) hash(k * 6.1) * 1.4f;
        u.rot = (float) (h1 * 6.28 + s * (h3 - 0.5));
        billboard(u, p, size / 2, 0);
    }

    // ---------- the sprint ----------

    private static void drawSprint(Run r, double now) {
        double t = now - r.runAt;
        // the sprint ends at the punch (or by its longest)
        double runEnd = r.hitAt >= 0 ? r.hitAt - r.runAt : 14;
        Entity caster = FxAnchor.entity(r.id);
        if (t < 0 || t > runEnd + 12) {
            return;
        }
        Vec3 here = caster != null ? caster.position() : r.start;
        // 1. wind needles born at his back, fixed in the world, stretching backwards
        for (int k = 0; k < 16; k++) {
            double h0 = hash(k * 1.7 + 3), h1 = hash(k * 2.9 + 5), h2 = hash(k * 4.3 + 7), h3 = hash(k * 5.9 + 9), h4 = hash(k * 7.1 + 11);
            double tb = 0.6 + (runEnd + 2 - 0.6) * (k / 16.0) + (h0 - 0.5) * 0.5, lt = t - tb;
            if (lt < 0 || lt >= 9 || tb > runEnd + 2) {
                continue;
            }
            if (r.windAt[k] == null) {
                r.windAt[k] = here;
            }
            double len = 1.2 + 2.6 * h3, tilt = (h2 - 0.5) * 0.25;
            Vec3 d = r.dir(0, tilt, -1);
            Vec3 p = r.at(r.windAt[k], (h1 - 0.5) * 1.3, 0.15 + h2 * 1.8, -0.35).add(d.scale(len * 0.5));
            axial(life((float) (700 + k * 1.9), (float) (0.6 + 0.8 * h0), (float) (0.5 + 0.5 * h1), lt, 1.0, 3, 4), p, d, len, (0.05 + 0.12 * h4) * GLOW);
        }
        // 2. curved wind: a stroke off a shoulder or a foot that bends round behind him
        for (int k = 0; k < 6; k++) {
            double h0 = hash(k * 3.1 + 21), h1 = hash(k * 4.7 + 23), h2 = hash(k * 6.3 + 25);
            int side = k % 2 == 1 ? 1 : -1;
            double tb = 1.5 + k * 1.0 + h0 * 0.4, lt = t - tb;
            if (lt < 0 || lt >= 9 || tb > runEnd + 2) {
                continue;
            }
            if (r.curveAt[k] == null) {
                r.curveAt[k] = here;
            }
            double oy = k >= 4 ? 0.15 : 1.35 + 0.15 * h1, len = 1.4 + 1.2 * h2, thick = 0.08 + 0.06 * h1, bend = 0.7 + 0.4 * h0;
            float skew = (float) (0.7 + 0.5 * h2), rough = (float) (0.6 + 0.4 * h1);
            double l1x = side * 0.55, l1z = -1, n1 = Math.sqrt(l1x * l1x + 1);
            l1x /= n1;
            l1z /= n1;
            double th = -side * bend, l2x = l1x * Math.cos(th) + l1z * Math.sin(th), l2z = -l1x * Math.sin(th) + l1z * Math.cos(th);
            Vec3 d1 = r.dir(l1x, 0, l1z), d2 = r.dir(l2x, 0, l2z);
            Vec3 p1 = r.at(r.curveAt[k], side * 0.3, oy, 0).add(d1.scale(len * 0.5));
            Vec3 p2 = p1.add(d1.scale(len * 0.45)).add(d2.scale(len * 0.35));
            axial(life((float) (760 + k * 2.3), skew, rough, lt, 1.0, 3, 4), p1, d1, len, thick * GLOW);
            if (lt >= 0.5) {
                axial(life((float) (780 + k * 2.3), skew, rough, lt - 0.5, 0.8, 3, 4), p2, d2, len * 0.7, thick * 0.85 * GLOW);
            }
        }
        // 3. footfalls: a short flat stroke and chips kicked back at each plant
        for (int k = 0; k < 3; k++) {
            double tb = 0.2 + k * Math.PI, lt = t - tb;
            if (lt < 0 || lt >= 8 || tb >= runEnd) {
                continue;
            }
            if (r.stepAt[k] == null) {
                Vec3 h = here;
                r.stepAt[k] = new Vec3(h.x, groundUnder(h, 1), h.z);
            }
            double side = k % 2 == 1 ? 0.13 : -0.13;
            Vec3 p0 = r.stepAt[k];
            flat(life((float) (800 + k * 3.7), 0.6f, 0.9f, lt, 0.6, 2.5, 4), r.at(p0, side, 0.03, -0.6), r.fwd.scale(-1), 1.3, 0.22 * GLOW);
            for (int i = 0; i < 3; i++) {
                double s2 = lt - i * 0.2;
                if (s2 < 0 || s2 >= 6) {
                    continue;
                }
                double h1 = hash(k * 7 + i * 3.1), h2 = hash(k * 5 + i * 1.7);
                Vec3 q = r.at(p0, side + (h1 - 0.5) * 0.3, Math.max(0.05, 0.1 + 0.18 * s2 - 0.03 * s2 * s2), -0.2 - 0.22 * s2);
                chip(q, (0.1 + 0.1 * h2) * (1 - Math.pow(s2 / 6, 2)), h1, h2, s2, k * 5 + i);
            }
        }
        // 4. the launch: a burst of strokes backwards, a star and dashes flung off
        double lt = t - 0.4;
        if (lt >= 0) {
            Vec3 p0 = new Vec3(r.start.x, groundUnder(r.start, 1), r.start.z);
            for (int k = 0; k < 8; k++) {
                double h0 = hash(k * 1.1 + 31), h1 = hash(k * 2.2 + 33), h2 = hash(k * 3.3 + 35);
                if (lt >= 8) {
                    break;
                }
                double an = (h0 - 0.5) * 1.4, el = 0.1 + h1 * 0.5, len = 1.2 + 1.8 * h2;
                Vec3 d = r.dir(Math.sin(an), el, -Math.cos(an));
                Vec3 p = r.at(p0, 0, 0.5 + h1 * 0.9, -0.3).add(d.scale(0.4 + len * 0.5 + lt * 0.08));
                axial(life((float) (820 + k * 1.3), (float) (0.6 + h0), 0.7f, lt, 0.8, 2.5, 4), p, d, len, (0.07 + 0.08 * h2) * GLOW);
            }
            if (lt < 5) {
                SukunaCutsRenderer.U u = SukunaCutsRenderer.U.of(4);
                u.halo = 0.5f;
                double sc = 1.5 * (lt < 0.6 ? out(lt / 0.6) * 1.25 : 1.25 - 0.25 * cl(lt - 0.6, 0, 1)) * (1 - Math.pow(lt / 5, 2));
                billboard(u, r.at(p0, 0, 0.8, -0.4), sc / 2, 0);
            }
            for (int k = 0; k < 6; k++) {
                double h1 = hash(k * 3.7 + 41), h2 = hash(k * 5.1 + 43), s2 = lt - 0.1 - h1 * 0.4;
                if (s2 < 0 || s2 >= 5) {
                    continue;
                }
                boolean black = k % 2 == 1;
                double an = (h2 - 0.5) * 1.6;
                Vec3 d = r.dir(Math.sin(an), 0.15 + 0.3 * h1, -Math.cos(an));
                Vec3 p = r.at(p0, 0, 0.6 + h1, -0.3).add(d.scale(0.6 + 1.6 * (1 - Math.exp(-s2 / 1.2))));
                SukunaCutsRenderer.U u = SukunaCutsRenderer.U.of(3);
                u.kind = black ? 1 : 0;
                u.seed = (float) hash(k * 9.1);
                u.a = (float) (1 - Math.pow(s2 / 5, 3));
                axial(u, p, d, (black ? 0.7 : 0.5) * (1 - Math.pow(s2 / 5, 2)) + 0.1, black ? 0.14 : 0.08);
            }
        }
    }

    // ---------- the flight: speed lines, chips ----------

    private static void drawFlight(Run r, double now) {
        if (r.hitAt < 0) {
            return;
        }
        double t = now - r.hitAt;
        double launch = BTrack.LAUNCH - BTrack.HIT, rel = BTrack.RELEASE - BTrack.HIT, impact = BTrack.IMPACT - BTrack.HIT;
        boolean up = t >= launch && t < launch + 8, down = t >= rel && t < impact && r.impactAt < 0;
        Entity target = r.targetId >= 0 ? FxAnchor.entity(r.targetId) : null;
        if ((up || down) && target != null) {
            float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
            Vec3 tc = target.getPosition(partial).add(0, target.getBbHeight() * 0.5, 0);
            for (int k = 0; k < 16; k++) {
                double h1 = hash(k * 1.9), h2 = hash(k * 4.4), h3 = hash(k * 7.7);
                double an = h1 * 6.28, rr = 0.45 + 0.5 * h2, ph = fract(now * 0.35 + h3);
                double off = down ? 0.6 + ph * 2.2 : -(0.4 + ph * 2.2);
                SukunaCutsRenderer.U u = SukunaCutsRenderer.U.of(3);
                u.kind = k % 4 == 0 ? 1 : 0;
                u.seed = (float) hash(k * 2.3);
                u.a = (float) ((1 - ph) * 0.95);
                axial(u, new Vec3(tc.x + Math.cos(an) * rr, tc.y + off, tc.z + Math.sin(an) * rr), new Vec3(0, 1, 0), (0.9 + 0.9 * h3) * (1 - ph * 0.6),
                        0.05 + 0.04 * h1);
            }
        }
        // chips: up off the uppercut, then out of the ground at the impact
        for (int k = 0; k < 24; k++) {
            boolean first = k < 10;
            if (!first && r.impactAt < 0) {
                break;
            }
            double s = now - (first ? r.hitAt : r.impactAt) - (k % 4) * 0.2;
            if (s < 0 || s >= 10) {
                continue;
            }
            double h1 = hash(k * 3.1 + 2), h2 = hash(k * 1.3 + 7), h3 = hash(k * 8.9);
            Vec3 p;
            double floor;
            if (first) {
                p = r.at(r.hit, (h1 - 0.5) * 0.25 * s, (0.2 + 0.15 * h2) * s - 0.014 * s * s, (h3 - 0.5) * 0.2 * s);
                floor = r.hitGround;
            } else {
                double rad = (0.15 + 0.15 * h3) * s;
                p = r.at(r.impact, Math.cos(h1 * 6.28) * rad, 0.2 + (0.12 + 0.15 * h2) * s - 0.014 * s * s, Math.sin(h1 * 6.28) * rad);
                floor = r.impactGround;
            }
            p = new Vec3(p.x, Math.max(floor + 0.05, p.y), p.z);
            chip(p, (first ? 0.14 : 0.18 + 0.12 * h2) * (1 - Math.pow(s / 10, 2)), h1, h3, s, k);
        }
    }

    // ---------- shockwave rings on the ground: the impact (two), the landing ----------

    private static void drawRings(Run r, double now) {
        for (int i = 1; i <= 3; i++) {
            double t0 = i == 3 ? r.landAt : r.impactAt;
            if (t0 < 0) {
                continue;
            }
            t0 += i == 2 ? 2 : 0;
            double R = i == 1 ? 7 : i == 2 ? 5 : 6, dur = i == 2 ? 16 : 14;
            double s = now - t0;
            if (s < 0 || s >= dur) {
                continue;
            }
            Vec3 c = i == 3 ? r.chest : r.impact;
            double y = (i == 3 ? groundUnder(c, 2) : r.impactGround) + 0.03 + i * 0.003;
            double u = s / dur, rr = 0.4 + R * out(cl(u, 0, 1));
            for (int ci = 0; ci < 12; ci++) {
                if (hash(i * 31 + ci * 3.7) < 0.2) {
                    continue;
                }
                double ph = ci / 12.0 * 6.2832 + i * 0.7;
                Vec3 p = new Vec3(c.x + Math.cos(ph) * rr, y, c.z + Math.sin(ph) * rr);
                Vec3 d = new Vec3(-Math.sin(ph), 0, Math.cos(ph));
                flat(stroke(200 + i * 20 + ci, (float) (0.6 + 0.7 * hash(i * 5 + ci)), (float) (0.5 + 0.5 * hash(i * 9 + ci * 2)), snap(cl(s / 1.2, 0, 1)),
                        Math.exp(-s / 2), cl((u - 0.55) / 0.45, 0, 1) * 1.05), p, d, 6.2832 * rr / 12 * 0.9, (0.6 * (1 - u) + 0.15) * GLOW);
            }
        }
    }

    // ---------- the web, the four cuts, the stars at the tips ----------

    private static void drawWeb(Run r, double now) {
        if (r.landAt < 0) {
            return;
        }
        double s = now - r.landAt, bs = s - SX_BOOM;
        if (s >= SX_BOOM + SX_CUT + 8) {
            return;
        }
        Vec3 c = r.chest;
        if (r.webY == null) {
            // each stroke lies on the ground under its middle
            r.webY = new double[WEB.size()];
            for (int i = 0; i < WEB.size(); i++) {
                Vec3 m = middle(r, WEB.get(i));
                r.webY[i] = groundUnder(new Vec3(m.x, c.y, m.z), 2.5);
            }
        }
        if (s < SX_BOOM + SX_CUT + 2) {
            for (int i = 0; i < WEB.size(); i++) {
                Stroke w = WEB.get(i);
                double lt = s - w.t0();
                if (lt < 0) {
                    continue;
                }
                Vec3 m = middle(r, w);
                Vec3 p = new Vec3(m.x, r.webY[i] + (w.spoke() ? 0.04 : 0.035), m.z);
                Vec3 d = w.spoke() ? r.dir(Math.cos(w.a()), 0, Math.sin(w.a())) : r.dir(w.dx(), 0, w.dz());
                double flash = bs >= 0 ? bs < 1.5 ? 1.6 : Math.exp(-(bs - 1.5) / 2) : Math.exp(-lt / 1.5);
                double tear = bs > 2 ? cl((bs - 2) / (SX_CUT - 1), 0, 1) * 1.05 : 0;
                flat(stroke(w.seed(), w.skew(), w.rough(), snap(cl(lt / FORM, 0, 1)), flash, tear), p, d, w.len(),
                        w.thick() * GLOW * (bs >= 0 && bs < 2 ? 1.35 : 1));
            }
        }
        // stars where the first spokes finish drawing
        for (int i = 0; i < 6; i++) {
            Stroke w = WEB.get(i * 2);
            double st = s - w.t0() - FORM + 0.05;
            if (st < 0 || st >= 5) {
                continue;
            }
            Vec3 tip = r.at(c, Math.cos(w.a()) * (w.r() + w.len() * 0.47), 0, Math.sin(w.a()) * (w.r() + w.len() * 0.47));
            tip = new Vec3(tip.x, r.webY[i * 2] + 0.3, tip.z);
            SukunaCutsRenderer.U u = SukunaCutsRenderer.U.of(4);
            u.halo = 0.5f;
            double sc = 1.2 * (st < 0.6 ? out(st / 0.6) * 1.25 : 1.25 - 0.25 * cl(st - 0.6, 0, 1)) * (1 - Math.pow(st / 5, 2));
            billboard(u, tip, sc / 2, 0);
        }
        // four huge straight cuts across the whole web, one right after the other
        for (int i = 0; i < 4; i++) {
            double lt = bs - i * 0.35;
            if (lt < 0 || lt >= SX_CUT + 6) {
                continue;
            }
            double a = i * 0.81 + 0.35 + hash(i * 5.5) * 0.3, off = (hash(i * 7.1) - 0.5) * 3;
            Vec3 d = r.dir(Math.cos(a), 0, Math.sin(a));
            Vec3 n = new Vec3(-d.z, 0, d.x);
            Vec3 p = c.add(0, 0.3, 0).add(n.scale(off));
            flat(stroke((float) (900 + i * 3.3), (float) (0.7 + 0.4 * hash(i * 2.1)), (float) (0.6 + 0.3 * hash(i * 3.7)), snap(cl(lt, 0, 1)),
                    lt < 1.5 ? 1.8 : Math.exp(-(lt - 1.5) / 2), lt > SX_CUT - 1 ? cl((lt - SX_CUT + 1) / 6, 0, 1) * 1.05 : 0), p, d, 28, 1.15 * GLOW);
        }
    }

    private static Vec3 middle(Run r, Stroke w) {
        return w.spoke() ? r.at(r.chest, Math.cos(w.a()) * w.r(), 0, Math.sin(w.a()) * w.r()) : r.at(r.chest, w.mx(), 0, w.mz());
    }

    // ---------- real blocks: rubble at the impact, the burst ----------

    private static void drawBlocks(double now) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        MultiBufferSource.BufferSource source = minecraft.renderBuffers().bufferSource();
        boolean any = false;
        for (Run r : RUNS.values()) {
            // rubble blocks thrown out of the ground at the impact
            if (r.impactAt >= 0) {
                for (int k = 0; k < 18; k++) {
                    double h0 = hash(k * 1.7), h1 = hash(k * 3.9), h2 = hash(k * 6.1), h3 = hash(k * 8.3);
                    double s = now - r.impactAt - h3 * 1.2;
                    if (s < 0 || s >= 32) {
                        continue;
                    }
                    double an = h0 * 6.28, sp = 0.12 + 0.14 * h1, vy = 0.22 + 0.2 * h2, ys = vy * s - 0.02 * s * s;
                    double sc = (0.22 + 0.3 * h2) * (1 - cl((s - 24) / 8, 0, 1));
                    double tl = vy / 0.01, ds = ys <= 0 && s > 2 ? tl : s;
                    double rad = 0.5 + sp * Math.min(ds, tl);
                    double y = ys <= 0 && s > 2 ? sc / 2 : Math.max(sc / 2, ys + 0.1);
                    Vec3 p = new Vec3(r.impact.x + Math.cos(an) * rad, r.impactGround + y, r.impact.z + Math.sin(an) * rad);
                    double rr = Math.min(s, tl) * 0.25 * (h1 - 0.5) * 2;
                    block(minecraft, level, source, r.ground, p, sc, rr, rr * 0.7, rr * 0.4);
                    any = true;
                }
            }
            if (r.burstAt < 0 || r.pieces == null) {
                continue;
            }
            double bs = now - r.burstAt;
            if (bs >= 46) {
                continue;
            }
            // the blocks the burst took: in place until their turn, then up and out, tumbling, shrinking away
            if (r.broken) {
                for (Piece q : r.pieces) {
                    double st = bs - q.r() * 0.07;
                    double x = q.at().x, y = q.at().y, z = q.at().z, rx = 0, ry = 0, rz = 0, sc = 1;
                    if (st > 0) {
                        double vy = (0.95 - 0.04 * q.r()) * (0.6 + 0.6 * q.h3()), vo = 0.05 + 0.1 * q.h1();
                        double tt = Math.min(st, 2 * vy / 0.08 + 2);
                        x += q.ux() * vo * tt;
                        z += q.uz() * vo * tt;
                        y += Math.max(0, vy * tt - 0.04 * tt * tt);
                        double spin = tt * 0.2;
                        rx = spin * (q.h2() - 0.5) * 2;
                        rz = spin * (q.h0() - 0.5) * 2;
                        ry = spin * (q.h3() - 0.5);
                        sc = 1 - cl((st - 26) / 10, 0, 1);
                    }
                    if (sc <= 0.01) {
                        continue;
                    }
                    block(minecraft, level, source, q.state(), new Vec3(x, y, z), sc, rx, ry, rz);
                    any = true;
                }
            }
            // clods of the ground thrown up all over the web
            for (Clod q : r.clods) {
                double st = bs - q.R() * 0.12;
                if (st < 0 || st >= 40) {
                    continue;
                }
                double vy = (0.75 - 0.035 * q.R()) * (0.6 + 0.6 * q.h2()), vo = 0.04 + 0.08 * q.h3();
                double tt = Math.min(st, 2 * vy / 0.08);
                double rr = q.R() + vo * tt, y = Math.max(0, vy * tt - 0.04 * tt * tt);
                double sc = (0.45 + 0.6 * q.h4()) * 0.6 * (1 - cl((st - 28) / 10, 0, 1));
                if (sc <= 0.01) {
                    continue;
                }
                Vec3 p = r.at(q.c(), Math.cos(q.an()) * rr, y + sc / 2, Math.sin(q.an()) * rr);
                double rot = tt * 0.22 * (q.h2() - 0.5) * 2;
                block(minecraft, level, source, q.state(), p, sc, rot, rot * 0.6 + q.h0() * 3, rot * 0.3);
                any = true;
            }
        }
        if (any) {
            source.endBatch();
        }
    }

    private static void block(Minecraft minecraft, ClientLevel level, MultiBufferSource source, BlockState state, Vec3 p, double sc, double rx,
            double ry, double rz) {
        PoseStack pose = new PoseStack();
        pose.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
        pose.mulPose(Axis.YP.rotation((float) ry));
        pose.mulPose(Axis.XP.rotation((float) rx));
        pose.mulPose(Axis.ZP.rotation((float) rz));
        pose.scale((float) sc, (float) sc, (float) sc);
        pose.translate(-0.5, -0.5, -0.5);
        int light = LevelRenderer.getLightColor(level, BlockPos.containing(p.x, p.y + 0.5, p.z));
        minecraft.getBlockRenderer().renderSingleBlock(state, pose, source, light, OverlayTexture.NO_OVERLAY);
    }
}
