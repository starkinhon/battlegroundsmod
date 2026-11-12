package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.starkinhon.battlegroundsmod.client.FalaRender;
import com.starkinhon.battlegroundsmod.client.ScreenShake;
import com.starkinhon.battlegroundsmod.power.powers.opm.OpmShape;
import com.starkinhon.battlegroundsmod.power.powers.opm.OpmShape.Frame;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

// Saitama on the screen, the approved page (forja/saitama-soco-serio/scene.js) played from the server's messages: each
// serious move is one message at its start (and one when the blow lands with the final aim), and from then on every
// piece is a function of the move's own clock, in step with the server's ticks. What the page draws, drawn the same way:
// the air tube and its halo and core, the Mach disc, the steam cone, the dust walls along the trench, the dust ring, the
// sneeze, the clouds opened over the sky, the flying ground (the world's real blocks, OpmBlocks), the slab turning over,
// the afterimages (OpmGhosts), the arms of the barrage, the little flashes, the star where someone vanished; and on the
// screen the silence (grey, dark edges, focus lines), the caption, the impact frames, the white cut, the shake, his words.
public final class OpmFx {
    private static final float[] WHITE_HOT = {1, 0.949f, 0.839f};

    private OpmFx() {
    }

    // ---------- what is going on ----------

    private static final class Move {
        final int kind, id, seed;
        double start;
        Frame f;
        boolean breaks;
        double prevT = -1, ghostT, armT;
        int target = -1, hopI = -1, levTick = -1;
        boolean landSnap, cut;
        double[][] hops;
        double fPrev;
        Vec3 hitAt, hitDir;
        final List<OpmBlocks.Deb> pebbles = new ArrayList<>(), lev = new ArrayList<>();
        float seedTube = (float) (OpmBlocks.rnd() * 10), seedCore = (float) (OpmBlocks.rnd() * 10), seedDust = (float) (OpmBlocks.rnd() * 10);

        Move(int kind, int id, int seed, double start, Frame f) {
            this.kind = kind;
            this.id = id;
            this.seed = seed;
            this.start = start;
            this.f = f;
        }
    }

    private record Flash(Vec3 p, float size, double born, float seed) {
    }

    private record Ring(Vec3 c, float r, float dur, double born, float seed) {
    }

    private record Launch(int id, Vec3 from, double born, Vec3[] last) {
    }

    private record Star(Vec3 p, double born) {
    }

    // One ghost arm of the barrage: from the shoulder toward its own point on the target.
    private record Arm(Vec3 from, Vec3 to, float roll, float w, float len, float e0, float e1, float str, float life, float peak, double born, int caster) {
    }

    private static final List<Move> MOVES = new ArrayList<>();
    private static final List<Flash> FLASHES = new ArrayList<>();
    private static final List<Ring> RINGS = new ArrayList<>();
    private static final List<Launch> LAUNCHES = new ArrayList<>();
    private static final List<Star> STARS = new ArrayList<>();
    private static final List<Arm> ARMS = new ArrayList<>();
    // Modo Serio: id -> the tick it runs out.
    private static final Map<Integer, Double> SERIO = new HashMap<>();
    private static double lastFrame = -1, linesAt = -100, whiteAt = -100;
    // The caption of the move nearest the camera this frame: {jp, pt}, its opacity.
    private static String[] caption;
    private static float captionA;

    public static boolean active() {
        return !MOVES.isEmpty() || !FLASHES.isEmpty() || !RINGS.isEmpty() || !LAUNCHES.isEmpty() || !STARS.isEmpty() || !ARMS.isEmpty()
                || OpmBlocks.active() || OpmGhosts.active() || LuffyFx.active();
    }

    public static void clear() {
        MOVES.clear();
        FLASHES.clear();
        RINGS.clear();
        LAUNCHES.clear();
        STARS.clear();
        ARMS.clear();
        SERIO.clear();
        OpmBlocks.clear();
        OpmGhosts.clear();
        LuffyFx.clear();
        caption = null;
    }

    private static double rnd() {
        return OpmBlocks.rnd();
    }

    private static double smooth(double x) {
        return OpmShape.smooth(x);
    }

    private static double clamp(double x, double a, double b) {
        return OpmShape.clamp(x, a, b);
    }

    static boolean near(Vec3 p, double r) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.position().distanceToSqr(p) < r * r;
    }

    // ---------- the messages ----------

    public static void receive(int kind, int id, Vec3 pos, Vec3 dir, float a, float b, float c) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        double now = ScreenFx.now();
        switch (kind) {
            case SubNet.OPM_SOCO, SubNet.OPM_ESPIRRO -> {
                int seed = (int) a;
                Move m = find(kind, id, seed);
                if (b >= 1.5f) {
                    if (m != null) {
                        MOVES.remove(m);
                    }
                    return;
                }
                Frame f = Frame.of(pos, dir);
                if (m == null) {
                    m = new Move(kind, id, seed, now, f);
                    MOVES.add(m);
                    // the ground round his feet (the pebbles of the wind-up)
                    OpmBlocks.remember(f, -10, 10, -6, 12, (x, z) -> true);
                }
                m.breaks = c > 0.5f;
                if (b > 0.5f) {
                    // the blow lands: the final aim, and the move's clock set to the server's
                    m.f = f;
                    m.start = now - 20 * (kind == SubNet.OPM_SOCO ? OpmShape.T_P : OpmShape.EX);
                    if (kind == SubNet.OPM_SOCO) {
                        OpmBlocks.remember(f, -40, 40, 0.6, 185, (x, z) -> Math.abs(x) < Math.max(OpmShape.rad(z), OpmShape.groundW(z) * 1.4) + 1);
                    } else {
                        OpmBlocks.remember(f, -Math.sin(OpmShape.FAN) * 152, Math.sin(OpmShape.FAN) * 152, 0, 152,
                                (x, z) -> Math.abs(Math.atan2(x, z)) < OpmShape.FAN + 0.05 && Math.hypot(x, z) < 152);
                    }
                }
            }
            case SubNet.OPM_SALTOS -> {
                int seed = (int) a;
                Move m = find(kind, id, seed);
                if (c >= 1.5f) {
                    if (m != null) {
                        MOVES.remove(m);
                    }
                    return;
                }
                Frame f = Frame.of(pos, dir);
                m = new Move(kind, id, seed, now, f);
                m.target = (int) b;
                m.breaks = c > 0.5f;
                Entity caster = mc.level.getEntity(id);
                double[] s = f.local(caster != null ? caster.position() : f.world(0, 0, -4));
                m.hops = OpmShape.hops(s[0], s[2], seed);
                OpmBlocks.remember(f, -19, 19, -19, 19, (x, z) -> x * x + z * z < 19 * 19);
                MOVES.add(m);
            }
            case SubNet.OPM_MESA -> {
                int seed = (int) a;
                Move m = find(kind, id, seed);
                if (c >= 1.5f) {
                    if (m != null) {
                        MOVES.remove(m);
                        OpmBlocks.SLAB.close();
                    }
                    return;
                }
                m = new Move(kind, id, seed, now, Frame.of(pos, dir));
                m.breaks = c > 0.5f;
                MOVES.add(m);
                net.minecraft.core.BlockPos o = net.minecraft.core.BlockPos.containing(pos);
                OpmBlocks.SLAB.start(o, (int) Math.round(dir.x), (int) Math.round(dir.z));
            }
            case SubNet.OPM_SOCOS -> {
                Move m = new Move(kind, id, 0, now, Frame.of(pos, dir));
                m.target = (int) a;
                MOVES.add(m);
            }
            case SubNet.OPM_FLASH -> {
                FLASHES.add(new Flash(pos, a, now, (float) (rnd() * 10)));
                if (b >= 1) {
                    shock(pos, 0.8f, 0xFFF2D6, 0.4f);
                    lines(now);
                    puff(pos, 14, 1.2);
                    shake(pos, b >= 2 ? 1.6 : 1.0);
                }
                // every skill's blow gets its impact frame (Stark 2026-11-11): the normal ones (last of the Socos, Gancho,
                // Chute) three frames like Luffy's short ones (two went by unseen), the Counter's flick five
                if (b >= 1 && near(pos, 48)) {
                    ScreenFx.impactFrame(b >= 2 ? new int[] {4, 3, 4, 3, 4} : new int[] {4, 3, 4}, 1, 0xFFFFFF, pos, 0);
                }
            }
            case SubNet.OPM_RING -> RINGS.add(new Ring(pos, a, b, now, (float) (rnd() * 10)));
            case SubNet.OPM_LAUNCH -> LAUNCHES.add(new Launch(id, pos, now, new Vec3[] {pos}));
            case SubNet.OPM_SERIO -> {
                if (a > 0.5f) {
                    SERIO.put(id, now + b);
                } else {
                    SERIO.remove(id);
                }
            }
            case SubNet.OPM_SAY -> say(id, (int) a);
            default -> {
            }
        }
    }

    private static Move find(int kind, int id, int seed) {
        for (Move m : MOVES) {
            if (m.kind == kind && m.id == id && m.seed == seed) {
                return m;
            }
        }
        return null;
    }

    // His words over his head (the page's say line).
    private static void say(int id, int which) {
        String text = switch (which) {
            case 0 -> "ok.";
            case 1 -> "Ha...";
            case 2 -> "Ha... ha...!";
            case 3 -> "ATCHIM!";
            case 4 -> "... foi mal.";
            default -> "";
        };
        float hold = switch (which) {
            case 0 -> 30;
            case 3 -> 15;
            case 4 -> 36;
            default -> 12;
        };
        FalaRender.add(id, text, 0xF2C94C, 0xFFF6D6, hold);
    }

    // ---------- the screen, as the page's helpers ----------

    // shake(a): the page's camera shake (fades 1.4 a second).
    static void shake(Vec3 at, double a) {
        if (near(at, 90)) {
            ScreenShake.start((float) Math.min(2.6, a * 0.55), (int) Math.min(50, 6 + a / 1.4 * 20));
        }
    }

    private static void impact(Vec3 at, int frames) {
        if (!near(at, 120)) {
            return;
        }
        int[] seq = new int[frames];
        for (int i = 0; i < frames; i++) {
            seq[i] = i % 2 == 0 ? 4 : 3;
        }
        ScreenFx.impactFrame(seq, 1, 0xFFFFFF, at, 0);
    }

    private static void hitFlash(Vec3 at, double pow) {
        impact(at, 5);
        shake(at, pow);
    }

    // shock(at, pow, color, dur): the ring of bent air.
    static void shock(Vec3 at, float pow, int color, float dur) {
        if (near(at, 140)) {
            ScreenFx.warp(at, 2.5f + pow * 2.2f, Math.min(0.6f, 0.12f + 0.08f * pow), 30, 0, new Vec3(0, 1, 0), color, 0.12f, Math.max(4, Math.round(dur * 20)), -1,
                    null, 0);
        }
    }

    static void lines(double now) {
        linesAt = now;
    }

    static void whiteCut(Vec3 at, double now) {
        if (near(at, 140)) {
            whiteAt = now;
            ScreenFx.flash(0xFFFFFF, 0.85f, 3);
        }
    }

    // A little dust of the ground where a normal blow landed.
    private static void puff(Vec3 p, int n, double pw) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        double gy = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(p.x), Mth.floor(p.z));
        if (p.y - gy > 3) {
            return;
        }
        OpmBlocks.remember(Mth.floor(p.x), Mth.floor(p.z));
        for (int i = 0; i < n; i++) {
            double g = rnd() * 6.283;
            BlockState st = OpmBlocks.stateAt(p.x, p.z, false);
            OpmBlocks.spawn(st, new Vec3(p.x + Math.cos(g) * 0.4, gy + 0.08, p.z + Math.sin(g) * 0.4),
                    new Vec3(Math.cos(g) * (1 + rnd() * 3) * pw, (2 + rnd() * 4) * pw, Math.sin(g) * (1 + rnd() * 3) * pw), 0.06 + Math.pow(rnd(), 2) * 0.18, false,
                    0);
        }
    }

    // ---------- for Luffy (LuffyFx): the same little pieces and the same screen ----------

    static void flash(Vec3 p, float size) {
        FLASHES.add(new Flash(p, size, ScreenFx.now(), (float) (rnd() * 10)));
    }

    static void ring(Vec3 c, float r, float dur) {
        RINGS.add(new Ring(c, r, dur, ScreenFx.now(), (float) (rnd() * 10)));
    }

    // A move's caption (the strongest near the camera this frame wins).
    static void caption(Vec3 at, String jp, String pt, float a) {
        if (near(at, 96) && a > captionA) {
            captionA = a;
            caption = new String[] {jp, pt};
        }
    }

    // The storm of the Kaminari: grey and dark edges (s 0..1), like the serious moves' silence.
    static void storm(Vec3 at, float s) {
        if (near(at, 96) && s > 0.004f) {
            if (0.67f * s > sil) {
                sil = 0.67f * s;
                silAt = at;
            }
            vig = Math.max(vig, 0.9f * s);
        }
    }

    // ---------- every frame ----------

    private static Matrix4f frame(Frame f, Vec3 cam) {
        Vec3 l = f.left(), w = f.forward(), o = f.origin();
        return new Matrix4f((float) l.x, (float) l.y, (float) l.z, 0, 0, 1, 0, 0, (float) w.x, (float) w.y, (float) w.z, 0, (float) (o.x - cam.x),
                (float) (o.y - cam.y), (float) (o.z - cam.z), 1);
    }

    // the screen this frame (the strongest of the moves near the camera)
    private static float sil, focus, vig;
    private static Vec3 silAt;

    static void render(Vec3 cam, double now, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            clear();
            return;
        }
        double dt = lastFrame < 0 ? 0 : clamp((now - lastFrame) / 20, 0, 0.1);
        lastFrame = now;
        double time = (now / 20) % 1000;
        // the holes are drawn again (and handed to the next frame's cloud pass)
        HOLES.clear();
        sil = 0;
        focus = 0;
        vig = 0;
        silAt = null;
        caption = null;
        captionA = 0;
        boolean stop = false;
        for (Iterator<Move> it = MOVES.iterator(); it.hasNext(); ) {
            Move m = it.next();
            double t = (now - m.start) / 20;
            double end = switch (m.kind) {
                case SubNet.OPM_SOCO -> 16;
                case SubNet.OPM_SALTOS -> 9;
                case SubNet.OPM_MESA -> 11;
                case SubNet.OPM_ESPIRRO -> 12;
                default -> 2.6;
            };
            if (t > end) {
                if (m.kind == SubNet.OPM_MESA) {
                    OpmBlocks.SLAB.close();
                }
                it.remove();
                continue;
            }
            double prev = m.prevT < 0 ? t - dt : m.prevT;
            m.prevT = t;
            stop |= switch (m.kind) {
                case SubNet.OPM_SOCO -> t >= OpmShape.T_P && t < OpmShape.T_P + OpmShape.HSTOP;
                case SubNet.OPM_SALTOS -> t >= OpmShape.SLF && t < OpmShape.SLF + OpmShape.SL_STOP;
                case SubNet.OPM_MESA -> t >= OpmShape.MC && t < OpmShape.MC + 0.2;
                case SubNet.OPM_ESPIRRO -> t >= OpmShape.EX && t < OpmShape.EX + 0.2;
                default -> false;
            };
            switch (m.kind) {
                case SubNet.OPM_SOCO -> soco(m, t, prev, dt, now, time, cam);
                case SubNet.OPM_SALTOS -> saltos(m, t, prev, dt, now, time, cam);
                case SubNet.OPM_MESA -> mesa(m, t, prev, dt, now, time, cam);
                case SubNet.OPM_ESPIRRO -> espirro(m, t, prev, dt, now, time, cam);
                case SubNet.OPM_SOCOS -> socos(m, t, prev, dt, now, cam);
                default -> {
                }
            }
        }
        // Luffy's moves (their pieces queued on LuffyMesh, their ground pieces here)
        LuffyFx.render(cam, now, partial, dt, time);
        OpmBlocks.SLAB.bake();
        OpmBlocks.step((float) (stop ? 0 : dt), time);
        // the ground's pieces and the slab are solid: drawn first
        OpmBlocks.render(cam);
        OpmGhosts.render(cam, now);
        flashes(now, cam);
        rings(now, cam);
        arms(now, cam);
        launches(now, cam);
        screen(now);
    }

    // The serious moves' silence and focus lines, the speed lines (on the screen pass).
    private static void screen(double now) {
        double l = (now - linesAt) / 10;
        float speed = l < 1 ? (float) (1 - l * l) : 0;
        float lines = Math.max(focus, speed);
        if (sil > 0.004f || lines > 0.004f || vig > 0.004f) {
            ScreenFx.sustainGrade(14, 1, 0.82f * sil, Math.max(0.55f * sil, vig), lines, silAt == null ? Vec3.ZERO : silAt);
        }
    }

    // A serious move's silence (sil 0..1), from its clock: on after the face turns, off silEnd seconds in.
    private static void silence(Move m, double t, double silEnd, double capEnd, String jp, String pt) {
        Vec3 o = m.f.origin();
        if (!near(o, 96)) {
            return;
        }
        double s = t < silEnd ? smooth((t - OpmShape.T_FACE) / 0.5) : 1 - smooth((t - silEnd) / 0.35);
        if (s > sil) {
            sil = (float) s;
            silAt = o;
        }
        if (t > OpmShape.T_FACE + 0.1 && t < capEnd + 0.1) {
            float a = (float) (Math.min(1, (t - OpmShape.T_FACE - 0.1) / 0.15) * (1 - smooth((t - capEnd) / 0.1)));
            if (a > captionA) {
                captionA = a;
                caption = new String[] {jp, pt};
            }
        }
    }

    // ---------- Z: Soco Serio ----------

    private static void soco(Move m, double t, double prev, double dt, double now, double time, Vec3 cam) {
        Frame f = m.f;
        silence(m, t, OpmShape.T_P + OpmShape.HSTOP, OpmShape.T_P - 0.15, "マジ殴り", "SÉRIE SÉRIA · SOCO SÉRIO");
        Vec3 fist = f.world(-0.12, 1.42, 0.95);
        if (t > OpmShape.T_WIND && t < OpmShape.T_P) {
            double k = (t - OpmShape.T_WIND) / (OpmShape.T_P - OpmShape.T_WIND);
            // the ground's little stones come up and float
            if (m.pebbles.size() < 46 && rnd() < dt * 60) {
                double a = -1.2 + rnd() * 2.6, r = 1.1 + rnd() * 3.4;
                Vec3 p = f.world(Math.cos(a) * r, 0.05, Math.sin(a) * r + 0.6);
                OpmBlocks.Deb d = OpmBlocks.spawn(OpmBlocks.stateAt(p.x, p.z, false), p, new Vec3(0, 0.6 + rnd() * 1.6, 0),
                        0.06 + Math.pow(rnd(), 2.5) * 0.16, true, 0);
                if (d != null) {
                    m.pebbles.add(d);
                }
            }
            if (near(f.origin(), 96)) {
                focus = Math.max(focus, (float) (0.25 + 0.6 * smooth(k)));
            }
            if (rnd() < dt * 8) {
                shake(f.origin(), 0.12 * k);
            }
        }
        if (prev < OpmShape.T_P && t >= OpmShape.T_P) {
            hitFlash(fist, 3);
        }
        double t0 = OpmShape.T_P + OpmShape.HSTOP;
        if (t < t0) {
            return;
        }
        double tp = t - t0;
        if (prev < t0) {
            shock(f.world(-0.12, 1.42, 3.95), 6, 0xE6F3FF, 1.0f);
            lines(now);
            whiteCut(f.origin(), now);
            for (OpmBlocks.Deb d : m.pebbles) {
                OpmBlocks.release(d, f.dir((rnd() - 0.5) * 6, 2 + rnd() * 5, 30 + rnd() * 40), 0.6, 1);
            }
            m.pebbles.clear();
        }
        double F = OpmShape.frontAt(tp);
        Matrix4f M = frame(f, cam);
        // the tube, its halo, its core
        if (tp < 2.9) {
            float alpha = (float) (1 - smooth((tp - 2.2) / 0.6));
            OpmMesh.P p = OpmMesh.p(OpmMesh.TUBE);
            p.len = (float) Math.max(1, F);
            p.tp = (float) tp;
            p.t = (float) time;
            p.seed = m.seedTube;
            p.alpha = alpha;
            p.scale = 1.45f;
            p.halo = 1;
            OpmMesh.queue(OpmMesh.Mesh.TUBE, M, p, OpmMesh.BACK, 21);
            p.scale = 1;
            p.halo = 0;
            OpmMesh.queue(OpmMesh.Mesh.TUBE, M, p, OpmMesh.BOTH, 22);
            p.scale = 0.52f;
            p.core = 1;
            p.seed = m.seedCore;
            OpmMesh.queue(OpmMesh.Mesh.TUBE, M, p, OpmMesh.FRONT, 23);
        }
        // the Mach disc at its front
        if (tp < OpmShape.TFRONT + 0.35) {
            float R = (float) (OpmShape.rad(F) * 2.3);
            OpmMesh.P p = OpmMesh.p(OpmMesh.MACH);
            p.alpha = (float) (1 - smooth((tp - OpmShape.TFRONT) / 0.35));
            p.t = (float) time;
            p.seed = m.seedTube;
            Matrix4f q = new Matrix4f(M).translate(0, (float) OpmShape.yc(F), (float) (F - OpmShape.rad(F) * 0.35)).scale(R, R, 1);
            OpmMesh.queue(OpmMesh.Mesh.QUAD, q, p, OpmMesh.BOTH, 20);
        }
        // the steam cone at the fist
        cone(M, new Vector3f(-0.12f, 1.42f, 0.95f), tp, 0.5, 0.4, 3.1, 0.7, 1.6, 0.35, 0.5, m.seedTube);
        // the dust walls along the trench
        if (tp < 7.5) {
            OpmMesh.P p = OpmMesh.p(OpmMesh.DUST);
            p.len = (float) OpmShape.LMAX;
            p.tp = (float) tp;
            p.t = (float) time;
            p.seed = m.seedDust;
            p.fwd = (float) F;
            p.colA = new float[] {0.784f, 0.698f, 0.58f};
            p.glow = false;
            OpmMesh.queue(OpmMesh.Mesh.HALF, M, p, OpmMesh.BOTH, 18);
        }
        dustRing(f.origin(), tp, 42, 2.6, m.seedDust, cam);
        clouds(f, 0, 110, 170 * (1 - Math.pow(1 - clamp((tp - 0.35) / 3.2, 0, 1), 2.6)) * (1 - smooth((tp - 9) / 4)), time, cam);
        // the ground torn out of the trench as the front passes
        double to = Math.min(F, 182);
        if (F > m.fPrev) {
            for (double z = Math.max(1.5, m.fPrev); z < to; z += 0.35) {
                double w = OpmShape.groundW(z), dd = OpmShape.digD(z);
                int n = (int) Math.floor(0.55 + w * 0.12 + rnd());
                for (int i = 0; i < n; i++) {
                    double u = rnd() * 2 - 1, x = u * w * 1.1, pw = 0.4 + rnd() * rnd() * 1.4, side = Math.signum(x) == 0 ? 1 : Math.signum(x);
                    boolean deep = dd > 2.5 && Math.abs(u) < 0.6;
                    Vec3 p = f.world(x, -rnd() * dd * 0.6, z);
                    Vec3 v = f.dir(side * (2 + rnd() * 9) * (0.3 + Math.abs(u)), (6 + rnd() * 18) * pw * (0.6 + Math.min(1, dd / 4)), (14 + rnd() * 46) * pw);
                    OpmBlocks.spawn(OpmBlocks.stateAt(p.x, p.z, deep), p, v, 0.25 + Math.pow(rnd(), 2.3) * Math.min(1.1, 0.6 + w * 0.05), false, 0.35);
                }
            }
            m.fPrev = F;
        }
    }

    // The steam cone: at local point at, opening for dur seconds (the page's cone numbers).
    private static void cone(Matrix4f M, Vector3f at, double tp, double dur, double s0, double s1, double h0, double h1, double z0, double z1, float seed) {
        double cl = tp / dur;
        if (cl >= 1 || cl < 0) {
            return;
        }
        double g = clamp(cl * 1.6, 0, 1);
        float cs = (float) (s0 + s1 * (1 - Math.pow(1 - g, 3)));
        OpmMesh.P p = OpmMesh.p(OpmMesh.CONE);
        p.life = (float) cl;
        p.seed = seed;
        Matrix4f q = new Matrix4f(M).translate(at.x, at.y, (float) (at.z + z0 + z1 * g)).rotateX((float) (-Math.PI / 2)).scale(cs, (float) (h0 + h1 * g), cs);
        OpmMesh.queue(OpmMesh.Mesh.CONE, q, p, OpmMesh.BOTH, 24);
    }

    // The dust ring running over the ground at c (R wide at the end, over dur seconds).
    private static void dustRing(Vec3 c, double tp, double R, double dur, float seed, Vec3 cam) {
        double rl = clamp(tp / dur, 0, 1);
        if (rl >= 1 || tp < 0) {
            return;
        }
        OpmMesh.P p = OpmMesh.p(OpmMesh.RING);
        p.life = (float) rl;
        p.ringR = (float) (R * (1 - Math.pow(1 - rl, 2.2)));
        p.ringMax = (float) (R * 1.33);
        p.ringW = (float) (2 + tp * 3);
        p.seed = seed;
        p.colA = new float[] {0.541f, 0.478f, 0.4f};
        p.colB = new float[] {0.847f, 0.8f, 0.722f};
        p.glow = false;
        Matrix4f q = new Matrix4f().translation((float) (c.x - cam.x), (float) (c.y + 0.12 - cam.y), (float) (c.z - cam.z)).rotateX((float) (-Math.PI / 2))
                .scale(p.ringMax);
        OpmMesh.queue(OpmMesh.Mesh.QUAD, q, p, OpmMesh.BOTH, 8);
    }

    // ---------- the hole in the clouds ----------

    private record Hole(Frame f, double hx, double hz, double r, double time) {
    }

    private static final List<Hole> HOLES = new ArrayList<>();
    private static final float HOLE_HALF = 320;

    private static double cloudY() {
        Minecraft mc = Minecraft.getInstance();
        float y = mc.level == null ? Float.NaN : mc.level.effects().getCloudHeight();
        return Float.isNaN(y) ? Double.NaN : y;
    }

    private static Matrix4f holeModel(Hole h, double y, Vec3 cam) {
        Vec3 c = h.f().world(h.hx(), 0, h.hz());
        return new Matrix4f(frame(h.f(), cam)).setTranslation((float) (c.x - cam.x), (float) (y - cam.y), (float) (c.z - cam.z))
                .rotateX((float) (-Math.PI / 2)).scale(HOLE_HALF);
    }

    private static OpmMesh.P holeP(Hole h, int kind) {
        OpmMesh.P p = OpmMesh.p(kind);
        p.hole = (float) h.r();
        p.hc = new float[] {0, 0};
        p.ringMax = HOLE_HALF;
        p.t = (float) h.time();
        p.colA = new float[] {1, 1, 1};
        p.glow = false;
        return p;
    }

    // The clouds pushed out round the hole (drawn with the effects), and the hole for the cloud pass (onRenderLevel).
    static void clouds(Frame f, double hx, double hz, double r, double time, Vec3 cam) {
        double y = cloudY();
        if (r <= 0.5 || Double.isNaN(y)) {
            return;
        }
        Hole h = new Hole(f, hx, hz, r, time);
        HOLES.add(h);
        OpmMesh.queue(OpmMesh.Mesh.QUAD, holeModel(h, y + 2, cam), holeP(h, OpmMesh.CLOUD), OpmMesh.BOTH, 2);
    }

    // After the particles, before the game draws its clouds: the holes write depth where the clouds must not show.
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || HOLES.isEmpty() || !active()) {
            return;
        }
        double y = cloudY();
        Vec3 cam = event.getCamera().getPosition();
        if (!Double.isNaN(y)) {
            for (Hole h : HOLES) {
                OpmMesh.P p = holeP(h, OpmMesh.MASK);
                OpmMesh.mask(holeModel(h, y + 0.2, cam), p, event.getModelViewMatrix(), event.getProjectionMatrix());
                OpmMesh.mask(holeModel(h, y + 4.2, cam), p, event.getModelViewMatrix(), event.getProjectionMatrix());
            }
        }
    }

    // ---------- X: Saltos Laterais Serios ----------

    private static void saltos(Move m, double t, double prev, double dt, double now, double time, Vec3 cam) {
        Minecraft mc = Minecraft.getInstance();
        Frame f = m.f;
        Vec3 c = f.origin();
        Entity target = mc.level.getEntity(m.target);
        Entity caster = mc.level.getEntity(m.id);
        silence(m, t, OpmShape.SL0, 1.05, "マジ反復横跳び", "SÉRIE SÉRIA · SALTOS LATERAIS SÉRIOS");
        boolean close = near(c, 96);
        if (t < OpmShape.SL0 && close) {
            focus = Math.max(focus, (float) (0.4 * smooth((t - 0.6) / 0.4)));
        }
        if (t >= OpmShape.SL0 && t < OpmShape.SLF) {
            double[] p = OpmShape.hopAt(m.hops, t);
            int i = (int) p[4];
            if (i != m.hopI) {
                if (m.hopI >= 0) {
                    // the landing digs the ground and throws a little of it
                    double[] a = m.hops[i];
                    double w = 0.55 + 0.5 * ((double) m.hopI / OpmShape.SLN);
                    shake(c, 0.35 + w * 0.3);
                    Vec3 at = f.world(a[0], 0, a[1]);
                    for (int j = 0; j < 7; j++) {
                        double g = rnd() * 6.28;
                        OpmBlocks.spawn(OpmBlocks.stateAt(at.x, at.z, false), at.add(Math.cos(g) * 0.8, 0.1, Math.sin(g) * 0.8),
                                new Vec3(Math.cos(g) * (2 + rnd() * 4), 3 + rnd() * 6, Math.sin(g) * (2 + rnd() * 4)), 0.08 + Math.pow(rnd(), 2) * 0.3, false, 0);
                    }
                }
                m.hopI = i;
            }
            // afterimages: one held on each landing, fainter ones in the air
            boolean landed = p[3] > 0.5;
            if (landed && !m.landSnap) {
                OpmGhosts.want(m.id, 0.85f, 1.2f);
                m.landSnap = true;
            }
            if (!landed) {
                m.landSnap = false;
                m.ghostT += dt;
                if (m.ghostT > 0.03) {
                    m.ghostT = 0;
                    OpmGhosts.want(m.id, 0.45f, 0.5f);
                }
            }
            // the torn ground floats up and turns round the target (the same spots the server took out)
            double kf = i / (double) OpmShape.SLN;
            int tick = (int) Math.floor(t * 20);
            for (int k = Math.max(m.levTick + 1, (int) Math.ceil(OpmShape.SL0 * 20)); k <= tick; k++) {
                for (double[] s : OpmShape.levSpawns(m.seed, k, kf)) {
                    Vec3 w = f.world(s[0], -0.4, s[1]);
                    OpmBlocks.Deb d = OpmBlocks.spawn(OpmBlocks.stateAt(w.x, w.z, s[4] > 0.5), w, new Vec3(0, s[3], 0), s[2], true, 0);
                    if (d != null) {
                        d.orbit = true;
                        d.ox = c.x;
                        d.oz = c.z;
                        d.ow = s[5];
                        d.of = s[6];
                        d.oph = s[7];
                        d.wx *= 0.6f;
                        d.wy *= 0.6f;
                        d.wz *= 0.6f;
                        m.lev.add(d);
                    }
                }
            }
            m.levTick = Math.max(m.levTick, tick);
            if (rnd() < dt * 10) {
                shake(c, 0.08 + 0.25 * kf);
            }
            if (close) {
                vig = Math.max(vig, (float) (0.35 * kf));
                focus = Math.max(focus, 0.5f);
            }
        }
        if (prev < OpmShape.SLF && t >= OpmShape.SLF) {
            impact(c, 7);
            shake(c, 4);
        }
        double hit = OpmShape.SLF + OpmShape.SL_STOP;
        if (prev < hit && t >= hit) {
            Vec3 h = target != null ? target.position() : c;
            Vec3 from = caster != null ? caster.position() : f.world(0, 0, -1.6);
            Vec3 dir = new Vec3(h.x - from.x, 0, h.z - from.z);
            dir = dir.lengthSqr() < 1.0E-4 ? f.forward() : dir.normalize();
            m.hitAt = h;
            m.hitDir = dir;
            shock(h.add(0, 1.2, 0), 5, 0xE6F3FF, 1.0f);
            lines(now);
            whiteCut(h, now);
            shake(h, 3.5);
            // the floating blocks burst outward
            for (OpmBlocks.Deb d : m.lev) {
                Vec3 o = new Vec3(d.px - h.x, 0, d.pz - h.z);
                double r = Math.max(o.length(), 0.5);
                o = o.scale(1 / r);
                OpmBlocks.release(d, new Vec3(o.x * (24 + rnd() * 30) + dir.x * 18, 4 + rnd() * 12, o.z * (24 + rnd() * 30) + dir.z * 18), 0.22, 4);
            }
            m.lev.clear();
            for (int j = 0; j < 260; j++) {
                double g = rnd() * 6.28, pw = 0.4 + rnd() * rnd() * 1.6, rr = rnd() * 4;
                Vec3 p = new Vec3(h.x + Math.cos(g) * rr, h.y - rnd() * 2, h.z + Math.sin(g) * rr);
                OpmBlocks.spawn(OpmBlocks.stateAt(p.x, p.z, rr < 2), p,
                        new Vec3(Math.cos(g) * 14 * pw + dir.x * 34 * pw, (10 + rnd() * 20) * pw, Math.sin(g) * 14 * pw + dir.z * 34 * pw),
                        0.2 + Math.pow(rnd(), 2) * 1.1, false, 0.3);
            }
        }
        if (m.hitAt != null) {
            double b = t - hit;
            Vec3 h = m.hitAt, dir = m.hitDir;
            dustRing(h, b, 34, 2.2, m.seedDust, cam);
            // the blast at the fist and the steam cone along the blow
            Vector3f bc = h.add(0, 1.0, 0).add(dir.scale(1.2)).subtract(cam).toVector3f();
            KiMesh.P p = KiMesh.p(KiMesh.BLAST);
            p.life = (float) clamp(b / 1.1, 0, 1);
            p.seed = m.seedTube;
            p.time = (float) time;
            p.flat = 0.3f;
            p.color = new float[] {0.749f, 0.878f, 1f};
            if (p.life < 1) {
                KiMesh.queue(KiMesh.Mesh.BLAST, KiMesh.sphere(bc, (float) (1 + 10 * (1 - Math.pow(1 - clamp(b / 0.35, 0, 1), 3)))), p, KiMesh.BOTH, 14);
            }
            Matrix4f M = frame(Frame.of(h, dir), cam);
            cone(M, new Vector3f(0, 1.2f, 0), b, 0.55, 1, 8, 2, 7, 1.5, 3, m.seedCore);
        }
    }

    // ---------- C: Virada de Mesa Seria ----------

    private static void mesa(Move m, double t, double prev, double dt, double now, double time, Vec3 cam) {
        Frame f = m.f;
        silence(m, t, OpmShape.MF, 1.35, "マジちゃぶ台返し", "SÉRIE SÉRIA · VIRADA DE MESA SÉRIA");
        double L = OpmShape.HINGE, X = OpmShape.SLAB_X, Z0 = OpmShape.SLAB_Z0 - 0.5, T = OpmShape.SLAB_DEEP;
        boolean close = near(f.origin(), 120);
        if (t >= OpmShape.MG && t < OpmShape.MF) {
            double w = smooth((t - OpmShape.MG) / 0.3);
            if (rnd() < dt * 8) {
                shake(f.origin(), 0.25 * w);
            }
            if (close) {
                focus = Math.max(focus, (float) (0.6 * w));
            }
            // the ground cracks loose along the slab's edges
            if (rnd() < dt * 50) {
                boolean side = rnd() < 0.5;
                double x = side ? (rnd() < 0.5 ? -X : X) : -X + rnd() * 2 * X, z = side ? Z0 + rnd() * (L - Z0) : (rnd() < 0.5 ? Z0 : L);
                Vec3 p = f.world(x, 0.1, z);
                BlockState st = OpmBlocks.SLAB.any(false);
                OpmBlocks.spawn(st != null ? st : OpmBlocks.stateAt(p.x, p.z, false), p, new Vec3((rnd() - 0.5) * 2, 2 + rnd() * 4, (rnd() - 0.5) * 2),
                        0.08 + rnd() * 0.18, false, 0);
            }
        }
        double ang = OpmShape.slabAngle(t);
        if (prev < OpmShape.MF && t >= OpmShape.MF) {
            shake(f.origin(), 2);
            impact(f.origin(), 3);
            lines(now);
        }
        if (t >= OpmShape.MF && t < OpmShape.MC) {
            // earth falling from under the slab while it rises
            for (int q = 0; q < 4; q++) {
                if (rnd() < dt * 60) {
                    double x = -X + rnd() * 2 * X, dz = -(rnd() * (L - Z0)), y = -T, cA = Math.cos(ang), sA = Math.sin(ang);
                    Vec3 p = f.world(x, y * cA - dz * sA, L + dz * cA + y * sA);
                    BlockState st = OpmBlocks.SLAB.any(true);
                    OpmBlocks.spawn(st != null ? st : OpmBlocks.stateAt(p.x, p.z, true), p, f.dir((rnd() - 0.5) * 2, -2, 0), 0.4 + Math.pow(rnd(), 2) * 1.8,
                            false, 0);
                }
            }
        }
        if (ang > 0.01 && t < OpmShape.MC + 1.2) {
            OpmBlocks.SLAB.draw(cam, ang);
        } else if (t >= OpmShape.MC + 1.2) {
            OpmBlocks.SLAB.close();
        }
        if (prev < OpmShape.MC && t >= OpmShape.MC) {
            Vec3 land = f.world(0, 6, L + 33);
            hitFlash(land, 4);
            shock(land, 9, 0xFFE8C8, 1.3f);
            whiteCut(land, now);
            double far = 2 * L - Z0;
            for (int i = 0; i < 900; i++) {
                double x = -X - 2 + rnd() * (2 * X + 4), z = L + rnd() * (L - Z0), side = x < 0 ? -1 : 1;
                double edge = Math.min(Math.min(Math.abs(x + X), Math.abs(x - X)), Math.min(Math.abs(z - L), Math.abs(z - far)));
                double pw = (0.4 + rnd() * rnd() * 1.4) * (edge < 3 ? 1.3 : 0.6);
                Vec3 p = f.world(x, 0.5 + rnd() * 12, z);
                BlockState st = OpmBlocks.SLAB.any(rnd() < 0.5);
                OpmBlocks.spawn(st != null ? st : OpmBlocks.stateAt(p.x, p.z, true), p,
                        f.dir(side * (6 + rnd() * 22) * pw * (edge < 4 ? 1 : 0.3), (10 + rnd() * 28) * pw, (rnd() - 0.3) * 18 * pw),
                        0.3 + Math.pow(rnd(), 2.2) * 2.0, false, 0);
            }
        }
        if (t >= OpmShape.MC) {
            dustRing(f.world(0, T, L + (L - Z0) / 2), t - OpmShape.MC, 85, 2.8, m.seedDust, cam);
        }
    }

    // ---------- V: Espirro Serio ----------

    private static void espirro(Move m, double t, double prev, double dt, double now, double time, Vec3 cam) {
        Frame f = m.f;
        silence(m, t, OpmShape.EX + 0.2, 1.0, "マジくしゃみ", "SÉRIE SÉRIA · ESPIRRO SÉRIO");
        Vec3 H = f.world(0, 1.75, 0.3);
        boolean close = near(f.origin(), 96);
        if (prev < OpmShape.E1 && t >= OpmShape.E1) {
            say(m.id, 1);
        }
        if (prev < OpmShape.E2 && t >= OpmShape.E2) {
            say(m.id, 2);
        }
        if (t >= OpmShape.E1 && t < OpmShape.EX) {
            double w = 0.5 * smooth((t - OpmShape.E1) / 0.3) + 0.5 * smooth((t - OpmShape.E2) / 0.3);
            if (close) {
                focus = Math.max(focus, (float) (0.3 + 0.5 * w));
            }
            if (rnd() < dt * 8) {
                shake(f.origin(), 0.1 * w);
            }
            // the air pulled in to his face
            if (rnd() < dt * 70) {
                double a = -1.3 + rnd() * 2.6, r = 3 + rnd() * 6;
                Vec3 p = f.world(Math.sin(a) * r, 0.05, Math.cos(a) * r);
                Vec3 v = H.subtract(p).scale(0.9 + rnd() * 0.5).add(0, 6, 0);
                OpmBlocks.Deb d = OpmBlocks.spawn(OpmBlocks.stateAt(p.x, p.z, false), p, v, 0.05 + Math.pow(rnd(), 3) * 0.14, false, 1.2);
                if (d != null) {
                    m.pebbles.add(d);
                }
            }
        }
        if (prev < OpmShape.EX && t >= OpmShape.EX) {
            hitFlash(H, 3.6);
            say(m.id, 3);
        }
        if (prev < OpmShape.EX + 2.6 && t >= OpmShape.EX + 2.6) {
            say(m.id, 4);
        }
        double t0 = OpmShape.EX + 0.2;
        if (t < t0) {
            return;
        }
        double tp = t - t0;
        if (prev < t0) {
            shock(f.world(0, 1.75, 3.3), 6, 0xEEF6FF, 1.0f);
            lines(now);
            whiteCut(f.origin(), now);
            for (OpmBlocks.Deb d : m.pebbles) {
                OpmBlocks.release(d, f.dir((rnd() - 0.5) * 8, 3 + rnd() * 5, 35 + rnd() * 30), 0.5, 1);
            }
            m.pebbles.clear();
        }
        double F = OpmShape.snFront(tp);
        Matrix4f M = frame(f, cam);
        if (tp < 3.2) {
            OpmMesh.P p = OpmMesh.p(OpmMesh.SNEEZE);
            p.len = (float) Math.max(1, F);
            p.tp = (float) tp;
            p.t = (float) time;
            p.seed = m.seedTube;
            p.alpha = (float) (1 - smooth((tp - 2.4) / 0.8));
            OpmMesh.queue(OpmMesh.Mesh.SNEEZE, M, p, OpmMesh.BOTH, 22);
        }
        dustRing(f.origin(), tp, 30, 2.2, m.seedDust, cam);
        clouds(f, 0, 170, 200 * (1 - Math.pow(1 - clamp((tp - 0.3) / 2.6, 0, 1), 2.4)) * (1 - smooth((tp - 7) / 3)), time, cam);
        // the fan of ground blown away
        if (F > m.fPrev) {
            for (double r = Math.max(3, m.fPrev); r < Math.min(F, 152); r += 0.5) {
                int n = (int) Math.floor(r * 0.04 + rnd());
                for (int i = 0; i < n; i++) {
                    double a = (rnd() * 2 - 1) * OpmShape.FAN, x = Math.sin(a) * r, z = Math.cos(a) * r, pw = 0.4 + rnd() * rnd() * 1.3;
                    Vec3 p = f.world(x, -rnd() * 1.5, z);
                    OpmBlocks.spawn(OpmBlocks.stateAt(p.x, p.z, false), p, f.dir(Math.sin(a) * 30 * pw, (5 + rnd() * 12) * pw, Math.cos(a) * 30 * pw),
                            0.2 + Math.pow(rnd(), 2.3) * 0.9, false, 0.4);
                }
            }
            m.fPrev = F;
        }
    }

    // ---------- Z normal: Socos Consecutivos ----------

    private static void socos(Move m, double t, double prev, double dt, double now, Vec3 cam) {
        Minecraft mc = Minecraft.getInstance();
        Entity caster = mc.level.getEntity(m.id);
        Entity target = m.target >= 0 ? mc.level.getEntity(m.target) : null;
        if (caster == null) {
            return;
        }
        double ns0 = 0.25, nsd = 0.075, nsf = ns0 + 14 * nsd;
        Vec3 fwd = OpmShape.Frame.of(caster.position(), caster.getLookAngle()).forward();
        // where he aims (the crosshair, pitch included), never chasing a target off it (Stark: "na mira, sem ser teleguiado")
        Vec3 look = caster.getViewVector(1).normalize();
        Vec3 chest = caster.getEyePosition().add(look.scale(2.2)).add(0, -0.2, 0);
        if (t >= ns0 && t < nsf) {
            // short afterimages of each instant, and the arms the eye missed
            m.ghostT += dt;
            if (m.ghostT > 0.028) {
                m.ghostT = 0;
                OpmGhosts.want(m.id, 0.2f, 0.12f);
            }
            m.armT += dt;
            double rate = 0.0075 + 0.006 * (1 - smooth((t - ns0) / 0.25));
            Frame body = Frame.of(caster.position(), fwd);
            while (m.armT > rate) {
                m.armT -= rate;
                double side = rnd() < 0.5 ? -1 : 1;
                Vec3 sh = body.world(side * (0.28 + rnd() * 0.3), 0.75 + 0.45 + rnd() * 0.55, 0.08 + rnd() * 0.22);
                Vec3 aim = chest.add(body.dir((rnd() - 0.5) * 1.3, (rnd() - 0.4) * 1.5, -0.25));
                float w = (float) (0.85 + rnd() * 0.3);
                ARMS.add(new Arm(sh, aim, (float) (rnd() * 6.283), w, (float) ((sh.distanceTo(aim) - 0.15) / 0.88), (float) (0.25 + rnd() * 0.35),
                        (float) (0.75 + rnd() * 0.35), (float) (1 + rnd() * 0.8), (float) (0.07 + Math.pow(rnd(), 1.5) * 0.11), (float) (0.65 + rnd() * 0.3), now, m.id));
            }
            if (ARMS.size() > 160) {
                ARMS.subList(0, ARMS.size() - 160).clear();
            }
            if (rnd() < dt * 22) {
                FLASHES.add(new Flash(chest.add(fwd.scale(-0.45)).add((rnd() - 0.5) * 1.0, (rnd() - 0.4) * 1.2, 0), (float) (0.14 + rnd() * 0.12), now,
                        (float) (rnd() * 10)));
            }
            for (int i = 0; i < 13; i++) {
                double hitT = ns0 + i * nsd + nsd * 0.4;
                if (prev < hitT && t >= hitT) {
                    FLASHES.add(new Flash(chest.add(fwd.scale(-0.45)).add((rnd() - 0.5) * 0.5, (rnd() - 0.4) * 0.5, 0), (float) (0.24 + rnd() * 0.1), now,
                            (float) (rnd() * 10)));
                    shake(chest, 0.25);
                }
            }
        }
    }

    // ---------- the little pieces ----------

    private static void flashes(double now, Vec3 cam) {
        FLASHES.removeIf(fl -> (now - fl.born()) / 20 > 0.14);
        for (Flash fl : FLASHES) {
            double k = (now - fl.born()) / 20 / 0.14;
            OpmMesh.P p = OpmMesh.p(OpmMesh.FLASH);
            p.alpha = (float) ((1 - k) * 0.75);
            p.seed = fl.seed();
            p.colA = WHITE_HOT;
            float s = (float) (fl.size() * (0.35 + 0.65 * Math.sqrt(k)));
            OpmMesh.queue(OpmMesh.Mesh.SPHERE, new Matrix4f().translation(fl.p().subtract(cam).toVector3f()).scale(s), p, OpmMesh.FRONT, 30);
        }
    }

    private static void rings(double now, Vec3 cam) {
        RINGS.removeIf(r -> (now - r.born()) / 20 > r.dur());
        for (Ring r : RINGS) {
            dustRing(r.c(), (now - r.born()) / 20, r.r(), r.dur(), r.seed(), cam);
        }
    }

    private static void arms(double now, Vec3 cam) {
        ARMS.removeIf(a -> (now - a.born()) / 20 > a.life());
        for (Arm a : ARMS) {
            double k = (now - a.born()) / 20 / a.life();
            double ext = a.e0() + (a.e1() - a.e0()) * Math.min(1, k * 2.5), sm = 1 + (a.str() - 1) * Math.max(0, 1 - k * 3);
            float alpha = (float) (a.peak() * Math.pow(1 - k, 1.3) * smooth(Math.min(1, k * 6)));
            Vec3 d = a.to().subtract(a.from());
            if (d.lengthSqr() < 1.0E-4 || alpha < 0.01f) {
                continue;
            }
            Quaternionf q = new Quaternionf().rotationTo(0, 0, 1, (float) d.x, (float) d.y, (float) d.z).rotateZ(a.roll());
            // the arm of his own skin (Stark 2026-11-11: the player's arm, as Luffy's), see-through and fading
            if (skinArm(a, q, (float) (a.len() * ext * sm), alpha, cam)) {
                continue;
            }
            Matrix4f g = new Matrix4f().translation(a.from().subtract(cam).toVector3f()).rotate(q).scale(a.w(), a.w(), (float) (a.len() * ext * sm));
            OpmMesh.P p = OpmMesh.p(OpmMesh.GHOST);
            p.alpha = alpha;
            p.glow = false;
            p.colA = new float[] {0.953f, 0.776f, 0.137f};
            OpmMesh.queue(OpmMesh.Mesh.BOX, new Matrix4f(g).translate(0, 0, 0.25f).scale(0.25f, 0.25f, 0.5f), p, OpmMesh.FRONT, 16);
            p.colA = new float[] {0.831f, 0.165f, 0.165f};
            OpmMesh.queue(OpmMesh.Mesh.BOX, new Matrix4f(g).translate(0, 0, 0.69f).scale(0.27f, 0.27f, 0.38f), p, OpmMesh.FRONT, 16);
        }
        // the skin arms, all in one go
        Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
    }

    // One ghost arm drawn as the player's own right arm (and sleeve): from the shoulder along q, 0.88 x len long, w wide.
    private static boolean skinArm(Arm a, Quaternionf q, float len, float alpha, Vec3 cam) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.level.getEntity(a.caster()) instanceof net.minecraft.client.player.AbstractClientPlayer pl)
                || !(mc.getEntityRenderDispatcher().getRenderer(pl) instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer pr)) {
            return false;
        }
        var model = pr.getModel();
        boolean slim = pl.getSkin().model() == net.minecraft.client.resources.PlayerSkin.Model.SLIM;
        var buf = mc.renderBuffers().bufferSource();
        var vc = buf.getBuffer(net.minecraft.client.renderer.RenderType.entityTranslucent(pl.getSkin().texture()));
        com.mojang.blaze3d.vertex.PoseStack pose = new com.mojang.blaze3d.vertex.PoseStack();
        pose.translate(a.from().x - cam.x, a.from().y - cam.y, a.from().z - cam.z);
        pose.mulPose(q);
        // the arm part runs down its +y: turned onto +z, stretched to the ghost's length, its box centred on the line
        pose.mulPose(com.mojang.math.Axis.XP.rotation((float) (Math.PI / 2)));
        pose.scale(a.w(), 0.88f * len / 0.75f, a.w());
        pose.translate((slim ? 0.5f : 1f) / 16f, 2 / 16f, 0);
        int col = net.minecraft.util.FastColor.ARGB32.color(Math.round(Mth.clamp(alpha, 0, 1) * 255), 255, 255, 255);
        for (var part : new net.minecraft.client.model.geom.ModelPart[] {model.rightArm, model.rightSleeve}) {
            var keep = part.storePose();
            boolean vis = part.visible;
            part.setPos(0, 0, 0);
            part.setRotation(0, 0, 0);
            part.xScale = part.yScale = part.zScale = 1;
            part.visible = true;
            part.render(pose, vc, 0xF000F0, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, col);
            part.loadPose(keep);
            part.visible = vis;
        }
        return true;
    }

    // Whoever he threw: gone over the horizon (or out of sight far off), a star twinkles where they went.
    private static void launches(double now, Vec3 cam) {
        Minecraft mc = Minecraft.getInstance();
        for (Iterator<Launch> it = LAUNCHES.iterator(); it.hasNext(); ) {
            Launch l = it.next();
            Entity e = mc.level.getEntity(l.id());
            double age = (now - l.born()) / 20;
            if (e != null && !e.isRemoved()) {
                l.last()[0] = e.position();
            }
            boolean gone = e == null || e.isRemoved() || l.last()[0].distanceTo(l.from()) > 330;
            if (gone && age > 0.3 && l.last()[0].distanceTo(l.from()) > 40) {
                STARS.add(new Star(l.last()[0], now));
                it.remove();
            } else if (age > 12 || (gone && age > 0.3)) {
                it.remove();
            }
        }
        STARS.removeIf(s -> (now - s.born()) / 20 > 0.7);
        Camera camera = mc.gameRenderer.getMainCamera();
        for (Star s : STARS) {
            double k = (now - s.born()) / 20 / 0.7;
            Vec3 d = s.p().subtract(cam);
            double dist = d.length();
            // far off it is brought nearer, the same size on the screen
            double near = Math.min(dist, 180);
            Vector3f at = d.scale(near / Math.max(dist, 1.0E-3)).toVector3f();
            float sc = (float) (26 * (0.4 + Math.sin(k * Math.PI)) * near / Math.max(dist, 1.0E-3) * Math.max(1, dist / 120));
            OpmMesh.P p = OpmMesh.p(OpmMesh.STAR);
            p.alpha = (float) Math.sin(clamp(k, 0, 1) * Math.PI);
            OpmMesh.queue(OpmMesh.Mesh.QUAD, new Matrix4f().translation(at).rotate(camera.rotation()).scale(sc * 0.5f), p, OpmMesh.BOTH, 50);
        }
    }

    // ---------- the screen ----------

    // The caption of the serious move (Japanese over Portuguese), and the Modo Serio's time on his own screen.
    public static void renderGui(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        Font font = mc.font;
        int w = graphics.guiWidth(), h = graphics.guiHeight();
        if (caption != null && captionA > 0.02f) {
            int a = Math.max(5, Math.round(captionA * 255)) << 24;
            graphics.pose().pushPose();
            graphics.pose().translate(w / 2f, h * 0.70f, 0);
            graphics.pose().scale(2.6f, 2.6f, 1);
            graphics.drawString(font, caption[0], -font.width(caption[0]) / 2, 0, a | 0xFFFFFF, true);
            graphics.pose().popPose();
            graphics.pose().pushPose();
            graphics.pose().translate(w / 2f, h * 0.70f + 30, 0);
            graphics.pose().scale(1.1f, 1.1f, 1);
            graphics.drawString(font, caption[1], -font.width(caption[1]) / 2, 0, a | 0xF2C94C, true);
            graphics.pose().popPose();
        }
        Double end = SERIO.get(mc.player.getId());
        if (end != null) {
            double left = (end - ScreenFx.now()) / 20;
            if (left <= 0) {
                SERIO.remove(mc.player.getId());
            } else {
                Component text = Component.translatable("hud.battlegroundsmod.opm.serio", (int) Math.ceil(left));
                boolean blink = left < 3 && ((int) (left * 4)) % 2 == 0;
                graphics.drawString(font, text, w / 2 - font.width(text) / 2, h / 2 + 18, blink ? 0xFFFFFFFF : 0xFFF2C94C, true);
            }
        }
    }
}
