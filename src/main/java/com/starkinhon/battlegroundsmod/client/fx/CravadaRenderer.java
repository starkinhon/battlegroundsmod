package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.starkinhon.battlegroundsmod.network.ModPayloads.SukunaCravada;
import com.starkinhon.battlegroundsmod.power.powers.sukuna.BTrack;
import com.starkinhon.battlegroundsmod.power.powers.sukuna.CravadaWeb;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// Sukuna's B, the cravada (the old Shift+X), as in the 3D preview Stark approved (forja/sukuna-desmantelar, "Shift+X";
// server side in power/powers/sukuna/Cravada). Everything is the preview's, laid out in his frame at the moment of the
// punch (x to his left, z ahead):
//   - the sprint: wind needles born at his back that stretch backwards, curved wind off a shoulder or a foot, a flat
//     stroke and chips kicked back at each footfall, and the launch burst (strokes, a star, dashes flung off);
//   - vertical speed lines around the target on its way up and on its way down; stars at the uppercut, the throw, the
//     impact and the stomp; chips off the uppercut and out of the ground;
//   - rings of Shift+Z strokes running out over the ground at the impact and at the landing;
//   - the stomp's web: Shift+Z strokes laid flat on the ground (each on the ground under it), in three stages ~0.7 s
//     apart, each a wider layer around the last, with stars at the first spokes' tips; then it flashes, four huge straight
//     cuts cross it all and the strokes burn away; the server then cuts the web's shape out of the ground (CravadaWeb.cuts;
//     Stark 2026-11-09: no blocks thrown around, the world's own blocks cut in the web's shape).
//   - the flight, smooth and with the camera free (Stark 2026-11-09): the one who casts it is moved along BTrack here, on
//     his own client, every tick (the server only puts him back when far off), so his view glides instead of jumping 20
//     times a second; both bodies stay turned the way the move goes whatever their cameras do, and the target's whole
//     body tumbles, hangs, is thrown head first and lies on its back as in the preview (bones alone can't lay it down).
public final class CravadaRenderer {
    // The preview's timings (ticks): the web's stages, the flash (SX_BOOM), the cut before the burst (SX_CUT).
    private static final double SX_BOOM = BTrack.BOOM - BTrack.LAND, SX_CUT = BTrack.BURST - BTrack.BOOM, FORM = 1.0;
    private static final double GLOW = 2;

    private static final class Run {
        final int id, seed;
        // The way he runs (RUN), then the way the flight goes (TRACK: the server's, toward where the target was when the
        // uppercut landed, which is not the run's when the target moved round him during the sprint).
        Vec3 fwd, left;
        double runAt = -1, hitAt = -1, relAt = -1, impactAt = -1, landAt = -1, burstAt = -1;
        int targetId = -1;
        Vec3 start = Vec3.ZERO, hit = Vec3.ZERO, rel = Vec3.ZERO, impact = Vec3.ZERO, chest = Vec3.ZERO;
        double impactGround, hitGround;
        final Vec3[] windAt = new Vec3[16], curveAt = new Vec3[6], stepAt = new Vec3[3];
        double[] webY;
        // Which web strokes already kicked their dust up as they drew.
        boolean[] kicked;
        // The flight (TRACK): where BTrack's frame sits, when it started (now() and ticks of this client).
        Vec3 origin;
        double trackAt = -1;
        int trackTick = -1;

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

    private static final Map<Integer, Run> RUNS = new HashMap<>();

    private CravadaRenderer() {
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
                SukunaCutsRenderer.star(a.add(0, 0.5, 0), 3.2f, seed + 2);
                slamDust(new Vec3(a.x, r.impactGround, a.z), 1.0);
            }
            case SukunaCravada.LAND -> {
                r.landAt = now;
                r.chest = a;
                r.webY = null;
                r.kicked = null;
                SukunaCutsRenderer.star(a.add(0, 0.3, 0), 2.8f, seed + 3);
                slamDust(new Vec3(a.x, groundUnder(a, 2), a.z), 0.8);
            }
            case SukunaCravada.TRACK -> {
                r.origin = a;
                Vec3 flat = new Vec3(b.x, 0, b.z);
                if (flat.lengthSqr() > 1.0E-4) {
                    // fly the way the server flies him (Stark 2026-11-09: Madara stepped round behind him during the
                    // sprint, the server flew one way and this client the other, 50 blocks apart, teleported back and forth)
                    r.fwd = flat.normalize();
                    r.left = new Vec3(r.fwd.z, 0, -r.fwd.x);
                }
                r.trackAt = now;
                r.targetId = ticks;
                Minecraft minecraft = Minecraft.getInstance();
                r.trackTick = minecraft.player != null ? minecraft.player.tickCount : 0;
            }
            case SukunaCravada.BURST -> {
                r.burstAt = now;
                if (r.landAt < 0) {
                    r.landAt = now - SX_BOOM - SX_CUT;
                    r.chest = a;
                }
                SukunaCutsRenderer.star(a.add(0, 0.6, 0), 6f, seed + 4);
                burstSmoke(r);
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
            r.webY = new double[CravadaWeb.WEB.size()];
            for (int i = 0; i < CravadaWeb.WEB.size(); i++) {
                Vec3 m = middle(r, CravadaWeb.WEB.get(i));
                r.webY[i] = groundUnder(new Vec3(m.x, c.y, m.z), 2.5);
            }
        }
        if (r.kicked == null) {
            r.kicked = new boolean[CravadaWeb.WEB.size()];
        }
        if (s < SX_BOOM + SX_CUT + 2) {
            for (int i = 0; i < CravadaWeb.WEB.size(); i++) {
                CravadaWeb.Stroke w = CravadaWeb.WEB.get(i);
                double lt = s - w.t0();
                if (lt < 0) {
                    continue;
                }
                Vec3 m = middle(r, w);
                if (!r.kicked[i]) {
                    // the cut bites into the ground as it draws: dust and grit kicked up along it
                    r.kicked[i] = true;
                    strokeDust(r, w, new Vec3(m.x, r.webY[i], m.z));
                }
                Vec3 p = new Vec3(m.x, r.webY[i] + (w.spoke() ? 0.04 : 0.035), m.z);
                Vec3 d = r.dir(w.dirX(), 0, w.dirZ());
                double flash = bs >= 0 ? bs < 1.5 ? 1.6 : Math.exp(-(bs - 1.5) / 2) : Math.exp(-lt / 1.5);
                double tear = bs > 2 ? cl((bs - 2) / (SX_CUT - 1), 0, 1) * 1.05 : 0;
                flat(stroke(w.seed(), w.skew(), w.rough(), snap(cl(lt / FORM, 0, 1)), flash, tear), p, d, w.len(),
                        w.thick() * GLOW * (bs >= 0 && bs < 2 ? 1.35 : 1));
            }
        }
        // stars where the first spokes finish drawing
        for (int i = 0; i < 6; i++) {
            CravadaWeb.Stroke w = CravadaWeb.WEB.get(i * 2);
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
            double a = CravadaWeb.cutAngle(i), off = CravadaWeb.cutOffset(i);
            Vec3 d = r.dir(Math.cos(a), 0, Math.sin(a));
            Vec3 n = new Vec3(-d.z, 0, d.x);
            Vec3 p = c.add(0, 0.3, 0).add(n.scale(off));
            flat(stroke((float) (900 + i * 3.3), (float) (0.7 + 0.4 * hash(i * 2.1)), (float) (0.6 + 0.3 * hash(i * 3.7)), snap(cl(lt, 0, 1)),
                    lt < 1.5 ? 1.8 : Math.exp(-(lt - 1.5) / 2), lt > SX_CUT - 1 ? cl((lt - SX_CUT + 1) / 6, 0, 1) * 1.05 : 0), p, d, CravadaWeb.CUT_LEN, CravadaWeb.CUT_THICK * GLOW);
        }
    }

    private static Vec3 middle(Run r, CravadaWeb.Stroke w) {
        return r.at(r.chest, w.midX(), 0, w.midZ());
    }

    // ---------- the flight: his own client moves him (smooth), both bodies keep their facing, the target tumbles ----------

    // The run this client's player is flying in, if any.
    private static Run driving() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return null;
        }
        Run r = RUNS.get(player.getId());
        return r != null && r.origin != null && r.trackTick >= 0 && player.tickCount - r.trackTick <= BTrack.BURST + 12 ? r : null;
    }

    private static Vec3 track(Run r, double[][] path, double age) {
        int i = Mth.clamp((int) Math.floor(age), 0, BTrack.LENGTH), j = Math.min(i + 1, BTrack.LENGTH);
        double u = Mth.clamp(age - Math.floor(age), 0, 1);
        double[] a = path[i], b = path[j];
        return r.at(r.origin, a[0] + (b[0] - a[0]) * u, a[1] + (b[1] - a[1]) * u, a[2] + (b[2] - a[2]) * u);
    }

    // Before the player moves this tick: his motion is set to land exactly on the track (as the server's Cravada.launch
    // anchors him), on the floor from just before the landing.
    public static void onPlayerTick(PlayerTickEvent.Pre event) {
        if (!(event.getEntity() instanceof LocalPlayer player)) {
            return;
        }
        Run r = driving();
        if (r == null) {
            return;
        }
        int age = Math.max(0, player.tickCount - r.trackTick);
        Vec3 at = track(r, BTrack.SUKUNA, age);
        if (age >= BTrack.LAND - 2) {
            at = new Vec3(at.x, Math.max(at.y, groundUnder(at, 3)), at.z);
        }
        // put on the track, not pushed toward it: a push collides with whatever stands in the way (a step, a tree, the
        // target's own ground a block higher) and left him behind until the server threw him back, in jumps (Stark
        // 2026-11-09). The tick still starts from where he was, so the view glides between the two.
        player.setPos(at.x, at.y, at.z);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0;
    }

    // No walking, jumping or sneaking of his own while he flies.
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (driving() == null) {
            return;
        }
        var input = event.getInput();
        input.leftImpulse = 0;
        input.forwardImpulse = 0;
        input.up = input.down = input.left = input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }

    // The target's whole-body turn about its middle (radians, about its own side: negative tips its head back toward
    // where he faces), at this age of the flight: the preview's tilt (thrown up tumbling, hanging from his hand, swung over
    // his head, thrown head first, bounced over onto its back).
    private static double tilt(double a) {
        double launch = BTrack.LAUNCH, meet = BTrack.CATCH, wind = BTrack.WIND, rel = BTrack.RELEASE, imp = BTrack.IMPACT;
        if (a < launch) {
            return -0.25 * out(cl((a - BTrack.HIT) / 1.5, 0, 1));
        }
        if (a < meet) {
            return -0.25 - 1.3 * cl((a - launch) / (meet - launch), 0, 1.2);
        }
        double caught = -0.25 - 1.3 * cl((meet - launch) / (meet - launch), 0, 1.2);
        if (a < wind) {
            return Mth.lerp(out(cl((a - meet) / 2, 0, 1)), caught, -0.3);
        }
        if (a < rel) {
            return Mth.lerp(cl((a - wind) / (rel - wind), 0, 1), -0.3, -Math.PI * 0.95);
        }
        if (a < imp) {
            return -Math.PI * (0.95 + 0.55 * cl((a - rel) / (imp - rel), 0, 1));
        }
        // the bounce rolls it the rest of the way over onto its back (-1.5 pi to -2.5 pi: head away from him, face up)
        return -Math.PI * (1.5 + out(cl((a - imp) / 5, 0, 1)));
    }

    private static final java.util.Set<Integer> TURNED = new java.util.HashSet<>();

    public static void onRenderPre(RenderLivingEvent.Pre<?, ?> event) {
        if (RUNS.isEmpty() || event.isCanceled()) {
            return;
        }
        LivingEntity e = event.getEntity();
        float partial = event.getPartialTick();
        for (Run r : RUNS.values()) {
            if (r.origin == null || r.trackAt < 0) {
                continue;
            }
            double age = now() - r.trackAt;
            if (age < 0 || age > BTrack.BURST + 14) {
                continue;
            }
            float yaw = (float) (Mth.atan2(-r.fwd.x, r.fwd.z) * Mth.RAD_TO_DEG);
            if (e.getId() == r.id) {
                // he faces the way he flies, whatever his camera does
                e.yBodyRot = e.yBodyRotO = yaw;
                return;
            }
            if (e.getId() != r.targetId) {
                continue;
            }
            e.yBodyRot = e.yBodyRotO = yaw + 180;
            double t = tilt(Math.min(age, BTrack.BURST));
            // after the burst throws it, it tumbles over once and a half in the air and comes out upright (-2.5 pi to -4 pi)
            if (age > BTrack.BURST) {
                t = -Math.PI * (2.5 + 1.5 * out(cl((age - BTrack.BURST) / 16, 0, 1)));
            }
            // the blows land in the body: flattened for a moment at the slam, the stomp and each web that cuts under it
            double squash = Math.max(Math.max(hitPulse(age - BTrack.IMPACT, 5) * 0.35, hitPulse(age - BTrack.LAND, 6) * 0.3),
                    Math.max(Math.max(hitPulse(age - BTrack.WEB2, 4), hitPulse(age - BTrack.WEB3, 4)) * 0.12, hitPulse(age - BTrack.BOOM, 4) * 0.18));
            double h = e.getBbHeight() / 2;
            double feet = Mth.lerp(partial, e.yo, e.getY());
            // the body's middle where the track has it (lying, it is lower than standing feet allow), never in the floor
            double centre = track(r, BTrack.TARGET, Math.min(age, BTrack.BURST)).y;
            double want = Math.max(centre, feet + 0.15);
            double dy = age < BTrack.IMPACT - 1 || age > BTrack.BURST ? 0 : Math.min(0, want - (feet + h));
            var pose = event.getPoseStack();
            pose.pushPose();
            pose.translate(0, h + dy - squash * 0.25, 0);
            if (squash > 0) {
                pose.scale((float) (1 + squash * 0.35), (float) (1 - squash), (float) (1 + squash * 0.35));
            }
            pose.mulPose(new Quaternionf().rotateAxis((float) -t, (float) r.left.x, 0, (float) r.left.z));
            pose.translate(0, -h, 0);
            TURNED.add(e.getId());
            return;
        }
    }

    public static void onRenderPost(RenderLivingEvent.Post<?, ?> event) {
        if (TURNED.remove(event.getEntity().getId())) {
            event.getPoseStack().popPose();
        }
    }

    // A hit's pulse: up at once, back down over len ticks (0 outside).
    private static double hitPulse(double since, double len) {
        double u = since / len;
        return since < 0 || u >= 1 ? 0 : (1 - u) * (1 - u);
    }

    // ---------- the procedural smoke (Fumaça Nova, SmokeParticle), spawned here on the client: dust, grit, columns ----------

    private static final net.minecraft.util.RandomSource RANDOM = net.minecraft.util.RandomSource.createThreadSafe();

    private static void smoke(int style, int color, double alpha, double size, double grow, int life, double friction, double gravity, double flat,
            Vec3 at, Vec3 v) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        level.addAlwaysVisibleParticle(com.starkinhon.battlegroundsmod.fx.ModParticles.fx(style, color, (float) alpha, (float) size, (float) grow, life,
                (float) friction, (float) gravity, (float) flat, 0), true, at.x, at.y, at.z, v.x, v.y, v.z);
    }

    private static double rr() {
        return RANDOM.nextDouble();
    }

    private static double gg() {
        return RANDOM.nextGaussian();
    }

    private static int ground(Vec3 at) {
        var level = Minecraft.getInstance().level;
        return level == null ? 0xB9AA90 : com.starkinhon.battlegroundsmod.fx.Smoke.groundColor(level, at.add(0, 0.25, 0));
    }

    // A body slammed into the ground (scale 1: the cravada): a wave of dust rolling out low, a lighter layer over it,
    // a column thrown up, grit flying and a shadow on the ground.
    private static void slamDust(Vec3 at, double scale) {
        int color = ground(at), dark = com.starkinhon.battlegroundsmod.fx.Smoke.mix(color, 0x2A2622, 0.35);
        double TAU = Math.PI * 2;
        for (int i = 0; i < 46; i++) {
            double a = i / 46.0 * TAU + rr() * 0.12, sp = 0.42 * scale * (0.8 + rr() * 0.4);
            Vec3 o = new Vec3(Math.cos(a), 0, Math.sin(a));
            smoke(1, color, 0.95, (0.7 + rr() * 0.4) * scale, 1.6, 22 + RANDOM.nextInt(10), 0.89, 0, 0.5, at.add(o.scale(0.7)).add(0, 0.25, 0),
                    o.scale(sp).add(0, 0.015, 0));
        }
        for (int i = 0; i < 20; i++) {
            double a = rr() * TAU, sp = 0.22 * scale * (0.6 + rr() * 0.5);
            Vec3 o = new Vec3(Math.cos(a), 0, Math.sin(a));
            smoke(1, com.starkinhon.battlegroundsmod.fx.Smoke.mix(color, 0xFFFFFF, 0.15), 0.65, (0.6 + rr() * 0.4) * scale, 1.4, 18 + RANDOM.nextInt(8),
                    0.9, 0, 1, at.add(o.scale(0.5)).add(0, 0.7, 0), o.scale(sp).add(0, 0.06, 0));
        }
        for (int i = 0; i < 14; i++) {
            smoke(0, dark, 0.85, (0.55 + rr() * 0.35) * scale, 1.8, 30 + RANDOM.nextInt(12), 0.92, 0, 1, at.add(gg() * 0.4, 0.3 + rr() * 0.4, gg() * 0.4),
                    new Vec3(gg() * 0.04, (0.14 + rr() * 0.12) * scale, gg() * 0.04));
        }
        for (int i = 0; i < 30; i++) {
            Vec3 v = new Vec3(gg(), 1.4 + rr(), gg()).normalize();
            smoke(5, dark, 1, 0.07 + rr() * 0.08, 0, 18 + RANDOM.nextInt(10), 0.97, 1, 1, at.add(0, 0.25, 0), v.scale((0.3 + rr() * 0.25) * scale));
        }
        smoke(6, 0xFFFFFF, 1.1, 0.6, 9 * scale, 12, 1, 0, 1, at.add(0, 0.05, 0), Vec3.ZERO);
        smoke(7, 0, 0.55, 2.2 * scale, 1.2, 60, 1, 0, 1, at.add(0, 0.03, 0), Vec3.ZERO);
    }

    // One web stroke biting into the ground as it draws: a little dust thrown sideways off it and grit popping up.
    private static void strokeDust(Run r, CravadaWeb.Stroke w, Vec3 mid) {
        Vec3 d = r.dir(w.dirX(), 0, w.dirZ()), n = new Vec3(-d.z, 0, d.x);
        int color = ground(mid);
        int pts = Math.max(1, (int) (w.len() / 2.2));
        for (int i = 0; i < pts; i++) {
            Vec3 p = mid.add(d.scale(((i + 0.5) / pts - 0.5) * w.len() * 0.8)).add(0, 0.15, 0);
            int side = RANDOM.nextBoolean() ? 1 : -1;
            smoke(1, color, 0.8, 0.35 + rr() * 0.2, 1.5, 14 + RANDOM.nextInt(6), 0.88, 0, 0.6, p, n.scale(side * (0.06 + rr() * 0.05)).add(0, 0.02, 0));
            smoke(5, com.starkinhon.battlegroundsmod.fx.Smoke.mix(color, 0, 0.35), 1, 0.06 + rr() * 0.04, 0, 12 + RANDOM.nextInt(6), 0.97, 1, 1, p,
                    new Vec3(gg() * 0.06, 0.2 + rr() * 0.15, gg() * 0.06));
        }
    }

    // The ground blowing up along the whole web and the four cuts: thick dust columns out of every line, a dark cloud
    // rolling up from the middle, grit and clods (the procedural specks, no block copies) thrown high, a ring of air.
    private static void burstSmoke(Run r) {
        Vec3 c = r.chest;
        Vec3 base = new Vec3(c.x, groundUnder(c, 2), c.z);
        int color = ground(base), dark = com.starkinhon.battlegroundsmod.fx.Smoke.mix(color, 0x1E1A16, 0.4);
        int budget = 900;
        java.util.List<Vec3[]> lines = new java.util.ArrayList<>();
        for (CravadaWeb.Stroke w : CravadaWeb.WEB) {
            Vec3 m = middle(r, w);
            lines.add(new Vec3[] {m, r.dir(w.dirX(), 0, w.dirZ()), new Vec3(w.len(), w.spoke() ? 1 : 0.6, 0)});
        }
        for (int i = 0; i < 4; i++) {
            double a = CravadaWeb.cutAngle(i), off = CravadaWeb.cutOffset(i);
            Vec3 d = r.dir(Math.cos(a), 0, Math.sin(a));
            lines.add(new Vec3[] {c.add(new Vec3(-d.z, 0, d.x).scale(off)), d, new Vec3(CravadaWeb.CUT_LEN * 0.8, 1.6, 0)});
        }
        for (Vec3[] line : lines) {
            double len = line[2].x, k = line[2].y;
            int pts = Math.max(1, (int) (len / 2.4));
            for (int i = 0; i < pts && budget > 0; i++, budget -= 3) {
                Vec3 p = line[0].add(line[1].scale(((i + 0.5) / pts - 0.5) * len));
                double y = groundUnder(new Vec3(p.x, base.y, p.z), 2.5);
                p = new Vec3(p.x, y + 0.2, p.z);
                double dist = Math.sqrt(p.distanceToSqr(base.x, p.y, base.z));
                int delay = (int) (dist * 0.1);
                smoke(0, rr() < 0.5 ? color : dark, 0.9, (0.6 + rr() * 0.5) * k, 1.9, 34 + RANDOM.nextInt(16) + delay, 0.93, 0, 1, p.add(0, rr() * 0.4, 0),
                        new Vec3(gg() * 0.03, (0.16 + rr() * 0.16) * k, gg() * 0.03));
                smoke(1, color, 0.9, (0.5 + rr() * 0.3) * k, 1.6, 24 + RANDOM.nextInt(10), 0.9, 0, 0.55, p,
                        new Vec3(gg() * 0.08, 0.02, gg() * 0.08));
                Vec3 v = new Vec3(gg() * 0.4, 1.2 + rr(), gg() * 0.4).normalize();
                smoke(5, dark, 1, (0.09 + rr() * 0.12) * Math.min(1.3, k), 0, 26 + RANDOM.nextInt(14), 0.98, 1, 1, p, v.scale(0.45 + rr() * 0.4));
            }
        }
        for (int i = 0; i < 24; i++) {
            smoke(0, dark, 0.95, 1.2 + rr() * 0.8, 2.2, 50 + RANDOM.nextInt(20), 0.94, 0, 1, base.add(gg() * 1.2, 0.5 + rr() * 1.5, gg() * 1.2),
                    new Vec3(gg() * 0.05, 0.22 + rr() * 0.18, gg() * 0.05));
        }
        smoke(6, 0xFFFFFF, 1.3, 0.8, 17, 16, 1, 0, 1, base.add(0, 0.06, 0), Vec3.ZERO);
        smoke(7, 0, 0.6, 9, 1.3, 90, 1, 0, 1, base.add(0, 0.03, 0), Vec3.ZERO);
    }
}
