// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
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
import com.starkinhon.battlegroundsmod.power.powers.opm.OpmNormal;
import com.starkinhon.battlegroundsmod.power.powers.opm.OpmOmni;
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
import net.minecraft.world.entity.LivingEntity;
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
        // the playable kit: a normal move's raw direction; the Omni's start, the target's place in the air, the rocks,
        // the hops' order, the ring of 50; the Socos' corridor (its punches, the last one's clock)
        Vec3 aim, home, oc, hopFrom;
        List<Rock> rocks, hopR;
        Vec3[] ringDir;
        double[] ringAt;
        boolean slamOnly, boomGround;
        final float[] pu = new float[32];
        int puI;
        double big = -1;
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
    // Modo Serio: id -> serious moves left.
    private static final Map<Integer, Integer> LEFT = new HashMap<>();
    // His eyes: id -> when they last flashed (the mode turning on, each serious blow); read by OpmLayers.
    static final Map<Integer, Double> EYES = new HashMap<>();
    private static double lastFrame = -1, linesAt = -100, whiteAt = -100;
    // The caption of the move nearest the camera this frame: {jp, pt}, its opacity.
    private static String[] caption;
    private static float captionA;

    public static boolean active() {
        return !MOVES.isEmpty() || !FLASHES.isEmpty() || !RINGS.isEmpty() || !LAUNCHES.isEmpty() || !STARS.isEmpty() || !ARMS.isEmpty() || !BEAMS.isEmpty()
                || OpmBlocks.active() || OpmTrees.active() || OpmGhosts.active() || LuffyFx.active();
    }

    public static void clear() {
        MOVES.clear();
        FLASHES.clear();
        RINGS.clear();
        LAUNCHES.clear();
        STARS.clear();
        ARMS.clear();
        BEAMS.clear();
        SERIO.clear();
        LEFT.clear();
        EYES.clear();
        OpmBlocks.clear();
        OpmTrees.clear();
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
                        drop(m);
                        MOVES.remove(m);
                    }
                    return;
                }
                Frame f = Frame.of(pos, dir);
                if (m == null) {
                    m = new Move(kind, id, seed, now, f);
                    MOVES.add(m);
                    EYES.put(id, now);
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
                        OpmBlocks.remember(f, -Math.sin(OpmShape.FAN) * 302, Math.sin(OpmShape.FAN) * 302, 0, 302,
                                (x, z) -> Math.abs(Math.atan2(x, z)) < OpmShape.FAN + 0.05 && Math.hypot(x, z) < 302);
                    }
                }
            }
            case SubNet.OPM_SALTOS -> {
                int seed = (int) a;
                Move m = find(kind, id, seed);
                if (c >= 1.5f) {
                    if (m != null) {
                        drop(m);
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
                // the dents, the ring sunk to 20 and the crater of 25 round the target
                OpmBlocks.remember(f, -22, 22, -22, 22, (x, z) -> x * x + z * z < 22 * 22);
                MOVES.add(m);
                EYES.put(id, now);
            }
            case SubNet.OPM_MESA -> {
                int seed = (int) a;
                Move m = find(kind, id, seed);
                if (c >= 1.5f) {
                    if (m != null) {
                        drop(m);
                        MOVES.remove(m);
                        OpmBlocks.SLAB.close();
                    }
                    return;
                }
                m = new Move(kind, id, seed, now, Frame.of(pos, dir));
                m.breaks = c > 0.5f;
                MOVES.add(m);
                EYES.put(id, now);
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
                if (a > 1.5f) {
                    // a move used, the mode goes on: only the count changes
                    LEFT.put(id, (int) c);
                } else if (a > 0.5f) {
                    SERIO.put(id, now + b);
                    LEFT.put(id, (int) c);
                    wake(id, now);
                } else {
                    SERIO.remove(id);
                    LEFT.remove(id);
                }
            }
            case SubNet.OPM_SAY -> say(id, (int) a);
            case SubNet.OPM_TREE -> OpmTrees.receive(net.minecraft.core.BlockPos.containing(pos.x, pos.y + 0.01, pos.z), dir, a > 0.5f, b, (int) c);
            case SubNet.OPM_DENT -> dent(pos, b);
            case SubNet.OPM_SHRUG -> shrug(id, now);
            case SubNet.OPM_NORMAL -> normal(id, pos, dir, (int) a, (int) b, now);
            case SubNet.OPM_OMNI -> omni(id, pos, dir, (int) a, (int) b, (int) c, now);
            default -> {
            }
        }
    }

    // A move ended or cut short: whatever of it still floats drops now (it would wait for a push that never comes).
    private static void drop(Move m) {
        for (OpmBlocks.Deb d : m.pebbles) {
            if (d.flt) {
                OpmBlocks.release(d, new Vec3((rnd() - 0.5) * 2, 0, (rnd() - 0.5) * 2), 0.2, 1);
            }
        }
        for (OpmBlocks.Deb d : m.lev) {
            if (d.flt) {
                OpmBlocks.release(d, new Vec3((rnd() - 0.5) * 2, 0, (rnd() - 0.5) * 2), 0.2, 1);
            }
        }
        m.pebbles.clear();
        m.lev.clear();
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

    // ---------- the Modo Serio turning on, Pedra Viva taking a blow ----------

    // The ground cracks round his feet (the same lines the server takes out) and throws its pieces; his eyes flash.
    private static void wake(int id, double now) {
        Minecraft mc = Minecraft.getInstance();
        Entity who = mc.level.getEntity(id);
        EYES.put(id, now);
        if (who == null) {
            return;
        }
        Vec3 at = who.position();
        int top = Mth.floor(at.y) - 1;
        double R = OpmShape.WAKE_R;
        for (int x = Mth.floor(at.x - R); x <= Mth.floor(at.x + R); x++) {
            for (int z = Mth.floor(at.z - R); z <= Mth.floor(at.z + R); z++) {
                if (!OpmShape.wakeCrack(x, z, x + 0.5 - at.x, z + 0.5 - at.z)) {
                    continue;
                }
                BlockState st = mc.level.getBlockState(new net.minecraft.core.BlockPos(x, top, z));
                if (st.isAir() || !st.getFluidState().isEmpty()) {
                    continue;
                }
                for (int j = 0; j < 2; j++) {
                    Vec3 p = new Vec3(x + 0.5 + (rnd() - 0.5) * 0.6, top + 0.9, z + 0.5 + (rnd() - 0.5) * 0.6);
                    Vec3 out = new Vec3(p.x - at.x, 0, p.z - at.z).normalize();
                    OpmBlocks.spawn(st, p, new Vec3(out.x * (1 + rnd() * 3), 4 + rnd() * 7, out.z * (1 + rnd() * 3)), 0.15 + Math.pow(rnd(), 2) * 0.4, false, 0);
                }
            }
        }
        shock(at.add(0, 1, 0), 3, 0xFFF2D6, 0.6f);
        RINGS.add(new Ring(at, 10, 1.4f, now, (float) (rnd() * 10)));
        shake(at, 1.2);
        lines(now);
    }

    // Pedra Viva: a heavy blow and his feet dig in (k 0..1 how heavy).
    private static void dent(Vec3 at, float k) {
        double now = ScreenFx.now();
        RINGS.add(new Ring(at, (float) (2.5 + 3 * k), 0.6f, now, (float) (rnd() * 10)));
        puff(at, (int) (10 + 22 * k), 0.7 + 0.6 * k);
        shake(at, 0.3 + 0.8 * k);
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
                case SubNet.OPM_NORMAL -> normalEnd(m.seed);
                case SubNet.OPM_OMNI -> m.slamOnly ? OpmOmni.OS + 2 : OpmOmni.OF + 2.2;
                default -> 2.6;
            };
            if (t > end) {
                if (m.kind == SubNet.OPM_MESA) {
                    OpmBlocks.SLAB.close();
                }
                if (m.kind == SubNet.OPM_OMNI) {
                    omniDrop(m);
                }
                drop(m);
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
                case SubNet.OPM_SOCOS -> socos(m, t, prev, dt, now, time, cam);
                case SubNet.OPM_NORMAL -> normalFx(m, t, prev, dt, now, time, cam);
                case SubNet.OPM_OMNI -> omniFx(m, t, prev, dt, now, time, cam);
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
        // the whole trees felled or blown away
        OpmTrees.render(cam, (float) (stop ? 0 : dt));
        OpmGhosts.render(cam, now);
        flashes(now, cam);
        rings(now, cam);
        beams(now, cam);
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
                    double w = OpmShape.dentW(m.hopI);
                    shake(c, 0.35 + w * 0.3);
                    Vec3 at = f.world(a[0], 0, a[1]);
                    // the dent's ground thrown up (bigger dents than before, 5 to 6 across)
                    for (int j = 0; j < 18; j++) {
                        double g = rnd() * 6.28, rr = rnd() * (1.1 + 1.2 * w);
                        OpmBlocks.spawn(OpmBlocks.stateAt(at.x + Math.cos(g) * rr, at.z + Math.sin(g) * rr, rnd() < 0.3),
                                at.add(Math.cos(g) * rr, 0.1, Math.sin(g) * rr),
                                new Vec3(Math.cos(g) * (2 + rnd() * 6), 4 + rnd() * 9, Math.sin(g) * (2 + rnd() * 6)), 0.12 + Math.pow(rnd(), 2) * 0.45, false, 0);
                    }
                    RINGS.add(new Ring(at, (float) (2.5 + 1.5 * w), 0.5f, now, (float) (rnd() * 10)));
                    // every 5th: the whole ring round the target sinks (the server's sink), its rim breaks loose
                    if (i % 5 == 0) {
                        double[] rd = OpmShape.sink(i / (double) OpmShape.SLN);
                        shake(c, 1.4);
                        RINGS.add(new Ring(c, (float) (rd[0] * 1.15), 0.9f, now, (float) (rnd() * 10)));
                        for (int j = 0; j < 140; j++) {
                            double g = rnd() * 6.28, rr = rd[0] * (0.35 + 0.65 * Math.sqrt(rnd()));
                            Vec3 q = new Vec3(c.x + Math.cos(g) * rr, c.y - rnd() * rd[1], c.z + Math.sin(g) * rr);
                            OpmBlocks.spawn(OpmBlocks.stateAt(q.x, q.z, rnd() < 0.4), q,
                                    new Vec3(Math.cos(g) * (1 + rnd() * 3), 3 + rnd() * 8, Math.sin(g) * (1 + rnd() * 3)), 0.2 + Math.pow(rnd(), 2) * 0.6, false, 0.2);
                        }
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
            EYES.put(m.id, now);
            // the crater of 25: its ground thrown out and up
            for (int j = 0; j < 560; j++) {
                double g = rnd() * 6.28, pw = 0.4 + rnd() * rnd() * 1.6, rr = Math.sqrt(rnd()) * 10;
                Vec3 p = new Vec3(h.x + Math.cos(g) * rr, h.y - rnd() * OpmShape.CRATER_D * (1 - rr / 12.5), h.z + Math.sin(g) * rr);
                OpmBlocks.spawn(OpmBlocks.stateAt(p.x, p.z, rr < 6), p,
                        new Vec3(Math.cos(g) * 14 * pw + dir.x * 34 * pw, (10 + rnd() * 20) * pw, Math.sin(g) * 14 * pw + dir.z * 34 * pw),
                        0.2 + Math.pow(rnd(), 2) * 1.1, false, 0.3);
            }
        }
        if (m.hitAt != null) {
            double b = t - hit;
            Vec3 h = m.hitAt, dir = m.hitDir;
            dustRing(h, b, 34, 2.2, m.seedDust, cam);
            // the dome of air: 62 out in 0.9 s (the trees it reaches fall away from it, OpmTrees)
            dustRing(h, b, OpmShape.DOME_R + 2, 2.0, m.seedCore, cam);
            if (b < OpmShape.DOME_T + 0.5) {
                KiMesh.P dp = KiMesh.p(KiMesh.BLAST);
                dp.life = (float) clamp(b / (OpmShape.DOME_T + 0.5), 0, 1);
                dp.seed = m.seedDust;
                dp.time = (float) time;
                dp.flat = 0.55f;
                dp.alpha = 0.35f;
                dp.color = new float[] {0.86f, 0.93f, 1f};
                KiMesh.queue(KiMesh.Mesh.BLAST, KiMesh.sphere(h.subtract(cam).toVector3f(), (float) Math.max(1, OpmShape.domeAt(b))), dp, KiMesh.BOTH, 13);
            }
            if (prev < hit + 0.05 && t >= hit + 0.05) {
                shock(h.add(0, 2, 0), 9, 0xE6F3FF, 1.2f);
            }
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
        if (tp < 3.9) {
            OpmMesh.P p = OpmMesh.p(OpmMesh.SNEEZE);
            p.len = (float) Math.max(1, F);
            p.tp = (float) tp;
            p.t = (float) time;
            p.seed = m.seedTube;
            p.alpha = (float) (1 - smooth((tp - 3.0) / 0.9));
            OpmMesh.queue(OpmMesh.Mesh.SNEEZE, M, p, OpmMesh.BOTH, 22);
        }
        dustRing(f.origin(), tp, 70, 2.6, m.seedDust, cam);
        clouds(f, 0, 230, 300 * (1 - Math.pow(1 - clamp((tp - 0.3) / 2.6, 0, 1), 2.4)) * (1 - smooth((tp - 7) / 3)), time, cam);
        // the fan of ground blown away
        if (F > m.fPrev) {
            for (double r = Math.max(3, m.fPrev); r < Math.min(F, OpmShape.SN_L); r += 0.5) {
                int n = (int) Math.floor(Math.min(r, 150) * 0.03 + rnd());
                for (int i = 0; i < n; i++) {
                    double a = (rnd() * 2 - 1) * OpmShape.FAN, x = Math.sin(a) * r, z = Math.cos(a) * r, pw = 0.4 + rnd() * rnd() * 1.3;
                    Vec3 p = f.world(x, -rnd() * 1.5, z);
                    if (OpmBlocks.wet(p.x, p.z)) {
                        // the sea parted: spray instead of ground
                        splash(p, f.dir(Math.sin(a) * 0.9 * pw, 0.6 + rnd() * 0.8, Math.cos(a) * 0.9 * pw), 3);
                        continue;
                    }
                    OpmBlocks.spawn(OpmBlocks.stateAt(p.x, p.z, rnd() < 0.25), p, f.dir(Math.sin(a) * 30 * pw, (5 + rnd() * 12) * pw, Math.cos(a) * 30 * pw),
                            0.2 + Math.pow(rnd(), 2.3) * 0.9, false, 0.4);
                }
                // the walls of water left standing at the fan's edges: spray and mist along them
                if (rnd() < 0.5) {
                    for (int side = -1; side <= 1; side += 2) {
                        double a = side * OpmShape.FAN, x = Math.sin(a) * (r + 0.6), z = Math.cos(a) * (r + 0.6);
                        Vec3 p = f.world(x, 0, z);
                        if (OpmBlocks.wet(p.x, p.z)) {
                            splash(p, f.dir(-side * 0.25, 0.8 + rnd() * 0.9, 0.3), 6);
                        }
                    }
                }
            }
            m.fPrev = F;
        }
    }

    // Spray and mist at p, thrown along v (blocks a tick).
    private static void splash(Vec3 p, Vec3 v, int n) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !near(p, 160)) {
            return;
        }
        double y = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(p.x), Mth.floor(p.z));
        for (int i = 0; i < n; i++) {
            mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.SPLASH, p.x + (rnd() - 0.5) * 2, y + 0.2, p.z + (rnd() - 0.5) * 2, v.x * (0.5 + rnd()),
                    v.y * (0.5 + rnd()), v.z * (0.5 + rnd()));
        }
        mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.CLOUD, p.x, y + 1 + rnd() * 3, p.z, v.x * 0.2, 0.05 + rnd() * 0.1, v.z * 0.2);
    }

    // ---------- Z normal: Socos Consecutivos (v2) ----------

    // 30 punches in 1.35 s, each one 3 arms that land; the pressure of each punch a bulge running down a corridor of air
    // 8 blocks out in front (who is behind the target is hit too). The last one throws the target.
    private static void socos(Move m, double t, double prev, double dt, double now, double time, Vec3 cam) {
        Minecraft mc = Minecraft.getInstance();
        Entity caster = mc.level.getEntity(m.id);
        if (caster == null) {
            return;
        }
        double ns0 = OpmNormal.NS0, nsd = OpmNormal.NSD, nsf = ns0 + OpmNormal.NSN * nsd;
        Vec3 fwd = OpmShape.Frame.of(caster.position(), caster.getLookAngle()).forward();
        // where he aims (the crosshair, pitch included), never chasing a target off it (Stark: "na mira, sem ser teleguiado")
        Vec3 look = caster.getViewVector(1).normalize();
        Vec3 chest = caster.getEyePosition().add(look.scale(2.2)).add(0, -0.2, 0);
        Frame body = Frame.of(caster.position(), fwd);
        if (t >= ns0 && t < nsf) {
            m.ghostT += dt;
            if (m.ghostT > 0.03) {
                m.ghostT = 0;
                OpmGhosts.want(m.id, 0.2f, 0.12f);
            }
        }
        for (int i = 0; i < OpmNormal.NSN; i++) {
            double hitT = ns0 + i * nsd + nsd * 0.4;
            if (prev >= hitT || t < hitT) {
                continue;
            }
            boolean last = i == OpmNormal.NSN - 1;
            // every punch: 3 arms at once, each lands where it reaches (a flash there)
            for (int q = 0; q < 3; q++) {
                double side = rnd() < 0.5 ? -1 : 1;
                Vec3 sh = body.world(side * (0.28 + rnd() * 0.3), 1.2 + rnd() * 0.55, 0.08 + rnd() * 0.22);
                Vec3 aim = chest.add(body.dir((rnd() - 0.5) * 1.1, (rnd() - 0.4) * 1.3, -0.25));
                ARMS.add(new Arm(sh, aim, (float) (rnd() * 6.283), (float) (0.85 + rnd() * 0.3), (float) ((sh.distanceTo(aim) - 0.15) / 0.88), (float) (0.25 + rnd() * 0.35),
                        (float) (0.75 + rnd() * 0.35), (float) (1 + rnd() * 0.8), (float) (0.09 + Math.pow(rnd(), 1.5) * 0.1), (float) (0.65 + rnd() * 0.3), now, m.id));
                FLASHES.add(new Flash(aim, (float) (0.16 + rnd() * 0.12), now, (float) (rnd() * 10)));
            }
            if (ARMS.size() > 160) {
                ARMS.subList(0, ARMS.size() - 160).clear();
            }
            int s = (m.puI++ % 8) * 4;
            m.pu[s] = 0;
            m.pu[s + 1] = (float) (rnd() * 6.283);
            m.pu[s + 2] = last ? 1.8f : (float) (0.5 + rnd() * 0.5);
            if (last) {
                m.big = 0;
                FLASHES.add(new Flash(chest.add(fwd.scale(-0.45)), 1.1f, now, (float) (rnd() * 10)));
            } else {
                shake(chest, 0.35);
            }
        }
        // the bulges run down to the mouth in 0.2 s
        for (int s = 0; s < 32; s += 4) {
            if (m.pu[s + 2] > 0) {
                m.pu[s] += (float) (dt / 0.2);
                if (m.pu[s] > 1.1f) {
                    m.pu[s + 2] = 0;
                }
            }
        }
        // the corridor: comes with the punches, stretches and goes after the last
        if (t >= ns0 - 0.05) {
            double fade, len, r0;
            if (m.big < 0) {
                fade = smooth((t - ns0 + 0.05) / 0.1);
                len = 8.5;
                r0 = 1.7;
            } else {
                m.big += dt;
                fade = 1 - smooth(m.big / 0.45);
                len = 8.5 + 9 * smooth(m.big / 0.3);
                r0 = 1.7 + 1.3 * smooth(m.big / 0.3);
            }
            if (fade > 0.002) {
                OpmMesh.P p = OpmMesh.p(OpmMesh.CORRIDOR);
                p.alpha = (float) fade;
                p.len = (float) len;
                p.t = (float) time;
                p.seed = m.seedTube;
                p.pu = m.pu.clone();
                p.glow = false;
                OpmMesh.queue(OpmMesh.Mesh.MTUBE, new Matrix4f(frame(body, cam)).translate(0, 1.35f, 0.35f).scale((float) r0, (float) (r0 * 0.85), (float) len), p,
                        OpmMesh.BOTH, 24);
            }
        }
    }

    // ---------- the playable kit (forja/saitama-jogavel, approved 2026-11-25) ----------

    // A beam of light of the Omni's hops: from a to b, w thick, gone after life seconds.
    private record Beam(Vec3 a, Vec3 b, float w, float life, double born) {
    }

    private static final List<Beam> BEAMS = new ArrayList<>();

    private static void beam(Vec3 a, Vec3 b, double w, double life) {
        BEAMS.add(new Beam(a, b, (float) w, (float) life, ScreenFx.now()));
        if (BEAMS.size() > 90) {
            BEAMS.remove(0);
        }
    }

    private static void beams(double now, Vec3 cam) {
        BEAMS.removeIf(b -> (now - b.born()) / 20 > b.life());
        for (Beam b : BEAMS) {
            double k = (now - b.born()) / 20 / b.life();
            Vec3 d = b.b().subtract(b.a());
            float len = (float) d.length();
            if (len < 0.05f) {
                continue;
            }
            OpmMesh.P p = OpmMesh.p(OpmMesh.BEAM);
            p.alpha = (float) ((k < 0.08 ? 1.6 : 1) * Math.pow(1 - k, 1.6));
            float w = (float) (b.w() * (1 + (k < 0.08 ? 1.5 * (1 - k / 0.08) : 0)));
            Quaternionf q = new Quaternionf().rotationTo(0, 0, 1, (float) d.x, (float) d.y, (float) d.z);
            OpmMesh.queue(OpmMesh.Mesh.BEAM, new Matrix4f().translation(b.a().subtract(cam).toVector3f()).rotate(q).scale(w, w, len), p, OpmMesh.BOTH, 27);
        }
    }

    // The ground torn up at p: n pieces, thrown up (up) and out (out), about size big, pushed along dir (the page's burst).
    private static void burst(Vec3 p, int n, double up, double out, double size, Vec3 dir, double dpow) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !near(p, 160)) {
            return;
        }
        OpmBlocks.remember(Mth.floor(p.x), Mth.floor(p.z));
        for (int j = 0; j < n; j++) {
            double g = rnd() * 6.283, rr = rnd() * 1.4, pw = 0.5 + rnd();
            double x = p.x + Math.cos(g) * rr, z = p.z + Math.sin(g) * rr;
            Vec3 v = new Vec3(Math.cos(g) * out * pw + (dir != null ? dir.x * dpow * pw : 0), up * (0.5 + rnd()),
                    Math.sin(g) * out * pw + (dir != null ? dir.z * dpow * pw : 0));
            OpmBlocks.spawn(OpmBlocks.stateAt(x, z, rnd() < 0.3), new Vec3(x, p.y + 0.05, z), v, size * (0.4 + Math.pow(rnd(), 2) * 1.6), false, 0.4);
        }
    }

    // He broke out of a stun with his fists: a small jolt round him.
    private static void shrug(int id, double now) {
        Entity who = Minecraft.getInstance().level.getEntity(id);
        if (who == null) {
            return;
        }
        Vec3 at = who.position();
        RINGS.add(new Ring(at, 3.5f, 0.5f, now, (float) (rnd() * 10)));
        FLASHES.add(new Flash(at.add(0, 1.2, 0), 0.9f, now, (float) (rnd() * 10)));
        shock(at.add(0, 1, 0), 0.7f, 0xFFF2D6, 0.3f);
        puff(at, 12, 0.8);
        EYES.put(id, now);
    }

    // A normal move of the remade kit: its air drawn from its own clock (normalFx).
    private static void normal(int id, Vec3 pos, Vec3 dir, int sub, int other, double now) {
        Move m = new Move(SubNet.OPM_NORMAL, id, sub, now, Frame.of(pos, dir));
        m.hitAt = pos;
        m.aim = dir;
        m.hitDir = dir.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : dir.normalize();
        m.target = other;
        MOVES.add(m);
        switch (sub) {
            case 1 -> {
                // Gancho: the ground round the target thrown up with it
                burst(pos, 140, 22, 4, 0.4, null, 0);
                shock(pos.add(0, 1.6, 0), 1.1f, 0xFFF2D6, 0.45f);
                lines(now);
            }
            case 2 -> {
                shock(pos.add(0, 1, 0).add(m.hitDir.scale(1.5)), 1.1f, 0xFFF2D6, 0.45f);
                RINGS.add(new Ring(pos.add(m.hitDir.scale(1.5)), 5, 0.7f, now, (float) (rnd() * 10)));
                lines(now);
            }
            case 4 -> lines(now);
            case 5 -> {
                FLASHES.add(new Flash(pos, 0.7f, now, (float) (rnd() * 10)));
                shake(pos, 1.0);
                shock(pos, 0.9f, 0xE6F3FF, 0.4f);
                lines(now);
            }
            case 7 -> {
                FLASHES.add(new Flash(pos.add(0, 0.3, 0), 1.0f, now, (float) (rnd() * 10)));
                impact(pos, 3);
                shake(pos, 2);
                burst(pos, 70, 10, 4, 0.35, null, 0);
                shock(pos.add(0, 0.6, 0), 1.2f, 0xFFF2D6, 0.4f);
                OpmBlocks.remember(Frame.of(pos, dir), -16, 16, -16, 16, (x, z) -> x * x + z * z < 16 * 16);
            }
            case 8 -> {
                // the cracked ground blows up: plates of it jump, the small stuff flies, everyone inside goes up
                impact(pos, 3);
                shake(pos, 2.8);
                lines(now);
                shock(pos.add(0, 1, 0), 2.2f, 0xFFF2D6, 0.6f);
                double R = OpmNormal.PJ_R;
                Minecraft mc = Minecraft.getInstance();
                for (int j = 0; j < 90; j++) {
                    double g = rnd() * 6.283, r = 1.5 + Math.sqrt(rnd()) * (R - 1.5), x = pos.x + Math.cos(g) * r, z = pos.z + Math.sin(g) * r;
                    double y = Math.min(pos.y, mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z))) - 0.3;
                    OpmBlocks.spawn(OpmBlocks.stateAt(x, z, rnd() < 0.4), new Vec3(x, y, z),
                            new Vec3(Math.cos(g) * (1 + rnd() * 4), 7 + rnd() * 14 * (1 - r / R * 0.5), Math.sin(g) * (1 + rnd() * 4)), 0.8 + rnd() * 1.6, false, 0);
                }
                for (int j = 0; j < 420; j++) {
                    double g = rnd() * 6.283, r = Math.sqrt(rnd()) * R, x = pos.x + Math.cos(g) * r, z = pos.z + Math.sin(g) * r;
                    OpmBlocks.spawn(OpmBlocks.stateAt(x, z, rnd() < 0.3), new Vec3(x, pos.y, z),
                            new Vec3(Math.cos(g) * (2 + rnd() * 6), 8 + rnd() * 18, Math.sin(g) * (2 + rnd() * 6)), 0.1 + Math.pow(rnd(), 2) * 0.6, false, 0.3);
                }
            }
            default -> {
            }
        }
    }

    private static double normalEnd(int sub) {
        return switch (sub) {
            case 1 -> 1.1;
            case 2, 5 -> 1.0;
            case 3 -> 0.4;
            case 4 -> 0.5;
            case 6 -> 1.3;
            case 7 -> 0.9;
            default -> 1.6;
        };
    }

    private static void normalFx(Move m, double t, double prev, double dt, double now, double time, Vec3 cam) {
        Vec3 pos = m.hitAt, dir = m.hitDir;
        Frame f = m.f;
        switch (m.seed) {
            case 1 -> {
                // the spiral column: up 26 blocks, its front up in 0.3 s
                if (t < 1.0) {
                    float sc = (float) (2.2 + 1.2 * smooth(t / 0.5));
                    OpmMesh.P p = OpmMesh.p(OpmMesh.HELIX);
                    p.fwd = (float) smooth(t / 0.3);
                    p.life = (float) clamp((t - 0.25) / 0.75, 0, 1);
                    p.t = (float) time;
                    p.seed = m.seedTube;
                    OpmMesh.queue(OpmMesh.Mesh.MTUBE, new Matrix4f().translation(pos.add(0, 0.1, 0).subtract(cam).toVector3f()).scale(sc, 26, sc), p,
                            OpmMesh.BOTH, 24);
                }
            }
            case 2 -> {
                // the crescent flying 15 blocks over the ground, and the groove it opens (16 blocks in 0.4 s)
                if (t < 0.6) {
                    double k = smooth(t / 0.45);
                    float sc = (float) (1.3 + 2.2 * k);
                    OpmMesh.P p = OpmMesh.p(OpmMesh.CRESCENT);
                    p.life = (float) (clamp((t - 0.3) / 0.3, 0, 1) * 0.9 + 0.08);
                    p.t = (float) time;
                    p.seed = m.seedTube;
                    OpmMesh.queue(OpmMesh.Mesh.GRID, new Matrix4f(frame(f, cam)).translate(0, 0.9f, (float) (0.8 + 15 * k)).scale(sc), p, OpmMesh.BOTH, 24);
                }
                double front = 16 * smooth(t / 0.4);
                for (; m.fPrev < front; m.fPrev += 1) {
                    double z = 1.5 + m.fPrev;
                    for (int j = 0; j < 8; j++) {
                        double sd = rnd() < 0.5 ? -1 : 1;
                        Vec3 at = f.world(sd * (0.6 + rnd() * 1.2), 0.1, z + rnd());
                        OpmBlocks.spawn(OpmBlocks.stateAt(at.x, at.z, false), at, f.dir(sd * (3 + rnd() * 6), 3 + rnd() * 7, 4 + rnd() * 6),
                                0.12 + Math.pow(rnd(), 2) * 0.5, false, 0.5);
                    }
                }
            }
            case 3, 4 -> {
                // the snap of air: a ball of spikes that opens and is gone in 0.3 s
                if (t < 0.32) {
                    double s = 0.2 + (m.seed == 3 ? 0.9 : 3.4) * smooth(t / 0.18);
                    OpmMesh.P p = OpmMesh.p(OpmMesh.SNAP);
                    p.life = (float) clamp(t / 0.32, 0, 1);
                    p.seed = m.seedTube;
                    OpmMesh.queue(OpmMesh.Mesh.SPHERE, new Matrix4f().translation(pos.subtract(cam).toVector3f()).scale((float) s), p, OpmMesh.BOTH, 26);
                }
            }
            case 5 -> {
                // the fist of wind: 40 blocks a second for 25, the ground under it torn up where it runs low
                double len = OpmNormal.SN_LEN, z = Math.min(len, 40 * t);
                if (t < len / 40 + 0.25) {
                    OpmMesh.P p = OpmMesh.p(OpmMesh.WIND_FIST);
                    p.life = (float) clamp((t - len / 40) / 0.25, 0, 1);
                    p.t = (float) time;
                    p.seed = m.seedTube;
                    Quaternionf q = new Quaternionf().rotationTo(0, 0, 1, (float) dir.x, (float) dir.y, (float) dir.z);
                    OpmMesh.queue(OpmMesh.Mesh.SPHERE, new Matrix4f().translation(pos.add(dir.scale(z)).subtract(cam).toVector3f()).rotate(q)
                            .scale((float) (0.7 + 0.9 * smooth(t / 0.12))), p, OpmMesh.BOTH, 25);
                }
                Minecraft mc = Minecraft.getInstance();
                for (; m.fPrev < z; m.fPrev += 1) {
                    Vec3 at = pos.add(dir.scale(m.fPrev + 1));
                    double gy = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(at.x), Mth.floor(at.z));
                    if (at.y - gy > 2.5 || at.y - gy < -1) {
                        continue;
                    }
                    for (int j = 0; j < 5; j++) {
                        double sd = rnd() < 0.5 ? -1 : 1;
                        Vec3 side = new Vec3(dir.z, 0, -dir.x).normalize();
                        Vec3 g = new Vec3(at.x, gy + 0.1, at.z).add(side.scale(sd * rnd() * 1.2));
                        OpmBlocks.spawn(OpmBlocks.stateAt(g.x, g.z, false), g,
                                side.scale(sd * (2 + rnd() * 3)).add(dir.x * (8 + rnd() * 8), 2 + rnd() * 4, dir.z * (8 + rnd() * 8)), 0.1 + Math.pow(rnd(), 2) * 0.3,
                                false, 0.8);
                    }
                }
            }
            case 6 -> {
                // the jump: the push off where he stood, his afterimages in the air
                double up = OpmNormal.PJ_UP / 20.0, land = OpmNormal.PJ_LAND / 20.0;
                Vec3 from = pos.subtract(m.aim);
                if (prev < up && t >= up) {
                    burst(from, 60, 12, 5, 0.3, null, 0);
                    shake(from, 1.2);
                    shock(from.add(0, 0.5, 0), 0.9f, 0xFFF2D6, 0.4f);
                }
                if (t >= up && t < land) {
                    m.ghostT += dt;
                    if (m.ghostT > 0.05) {
                        m.ghostT = 0;
                        OpmGhosts.want(m.id, 0.3f, 0.3f);
                    }
                }
            }
            case 7 -> {
                // the cracks running 15 out in half a second: dust jumps along their front
                double R = OpmNormal.PJ_R * smooth(t / 0.5);
                if (t < 0.6) {
                    Minecraft mc = Minecraft.getInstance();
                    int n = (int) (dt * 260);
                    for (int j = 0; j < n; j++) {
                        double g = rnd() * 6.283, r = R * (0.85 + rnd() * 0.15), x = pos.x + Math.cos(g) * r, z = pos.z + Math.sin(g) * r;
                        double y = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
                        if (Math.abs(y - pos.y) > 4) {
                            continue;
                        }
                        OpmBlocks.spawn(OpmBlocks.stateAt(x, z, false), new Vec3(x, y + 0.05, z), new Vec3(Math.cos(g) * rnd() * 2, 2 + rnd() * 4, Math.sin(g) * rnd() * 2),
                                0.08 + Math.pow(rnd(), 2) * 0.25, false, 0.4);
                    }
                    if (rnd() < dt * 12) {
                        shake(pos, 0.4);
                    }
                }
            }
            case 8 -> {
                // the dust ring and the dome of air off the blown ground
                dustRing(pos, t, OpmNormal.PJ_R + 4, 1.3, m.seedDust, cam);
                double dl = clamp(t / 0.9, 0, 1);
                if (dl < 1) {
                    double DR = Math.max(0.1, (OpmNormal.PJ_R + 3) * (1 - Math.pow(1 - clamp(t / 0.5, 0, 1), 2.2)));
                    KiMesh.P dp = KiMesh.p(KiMesh.BLAST);
                    dp.life = (float) dl;
                    dp.seed = m.seedDust;
                    dp.time = (float) time;
                    dp.flat = 0.35f;
                    dp.alpha = 0.4f;
                    dp.color = new float[] {0.9f, 0.86f, 0.78f};
                    KiMesh.queue(KiMesh.Mesh.BLAST, KiMesh.sphere(pos.subtract(cam).toVector3f(), (float) DR), dp, KiMesh.BOTH, 13);
                }
            }
            default -> {
            }
        }
    }

    // ---------- X serious: Socos Omnidirecionais Serios ----------

    // One rock of the Omni: its own size, shape, spin and place in the air.
    private static final class Rock {
        OpmBlocks.Deb d;
        Vec3 st, home;
        double sz, delay, dur, orb, jolt;
        boolean free;
    }

    private static void omni(int id, Vec3 pos, Vec3 dir, int seed, int caught, int phase, double now) {
        Minecraft mc = Minecraft.getInstance();
        Move m = find(SubNet.OPM_OMNI, id, seed);
        if (phase == 2) {
            if (m != null) {
                omniDrop(m);
                MOVES.remove(m);
            }
            return;
        }
        Frame f = Frame.of(pos, dir);
        if (m == null) {
            m = new Move(SubNet.OPM_OMNI, id, seed, now - (phase == 1 ? 20 * OpmOmni.OS : 0), f);
            Entity caster = mc.level.getEntity(id);
            m.home = caster != null ? caster.position() : pos.subtract(f.forward().scale(2.5));
            MOVES.add(m);
            EYES.put(id, now);
            OpmBlocks.remember(f, -20, 20, -20, 20, (x, z) -> x * x + z * z < 20 * 20);
        }
        if (phase == 0) {
            return;
        }
        // the slam
        m.start = now - 20 * OpmOmni.OS;
        m.prevT = OpmOmni.OS;
        impact(pos, 3);
        shake(pos, 3.2);
        lines(now);
        shock(pos.add(0, 0.5, 0), 3, 0xFFF2D6, 0.7f);
        whiteCut(pos, now);
        burst(pos, 420, 40, 10, 0.7, null, 0);
        RINGS.add(new Ring(pos, 44, 1.8f, now, (float) (rnd() * 10)));
        RINGS.add(new Ring(pos, (float) OpmOmni.CRATER_R, 0.8f, now, (float) (rnd() * 10)));
        if (caught < 0) {
            m.slamOnly = true;
            return;
        }
        OpmBlocks.remember(f, -36, 36, -36, 36, (x, z) -> x * x + z * z < 36 * 36);
        m.target = caught;
        m.oc = pos.add(0, dir.y, 0);
        m.hitAt = m.oc;
        m.hitDir = f.forward();
        m.hopFrom = pos.subtract(f.forward().scale(0.8)).add(0, 1.2, 0);
        m.rocks = new ArrayList<>();
        Vec3 oc = m.oc;
        for (int i = 0, n = 0; i < 160 && n < 48; i++) {
            boolean big = n < 30;
            double g = i * 2.39996 + rnd() * 0.4, el = (rnd() * 2 - 1) * 0.9, rr = (big ? 18 : 14) + rnd() * (big ? 30 : 34);
            n++;
            Rock r = new Rock();
            r.home = new Vec3(oc.x + Math.cos(g) * Math.cos(el) * rr, Math.max(pos.y + 4, oc.y + Math.sin(el) * rr * 0.8), oc.z + Math.sin(g) * Math.cos(el) * rr);
            r.st = new Vec3(oc.x + (rnd() - 0.5) * 18, pos.y - 3, oc.z + (rnd() - 0.5) * 18);
            r.sz = big ? 4 + Math.pow(rnd(), 1.4) * 7 : 1.2 + rnd() * 2.2;
            r.d = OpmBlocks.spawn(OpmBlocks.stateAt(r.st.x, r.st.z, rnd() < 0.6), r.st, Vec3.ZERO, r.sz, true, 0);
            if (r.d == null) {
                break;
            }
            r.d.sx = (float) (r.sz * (0.8 + rnd() * 0.6));
            r.d.sy = (float) (r.sz * (0.6 + rnd() * 0.6));
            r.d.sz = (float) (r.sz * (0.8 + rnd() * 0.5));
            r.d.wx = (float) ((rnd() - 0.5) * 13);
            r.d.wy = (float) ((rnd() - 0.5) * 13);
            r.d.wz = (float) ((rnd() - 0.5) * 13);
            r.delay = rnd() * 0.3;
            r.dur = 1.3 + rnd() * 0.6;
            r.orb = (0.12 + rnd() * 0.12) * (rnd() < 0.5 ? -1 : 1);
            m.rocks.add(r);
        }
    }

    // A move cut short: the rocks drop.
    private static void omniDrop(Move m) {
        if (m.rocks != null) {
            for (Rock r : m.rocks) {
                if (!r.free) {
                    r.free = true;
                    OpmBlocks.release(r.d, new Vec3((rnd() - 0.5) * 3, 0, (rnd() - 0.5) * 3), 0.2, 1);
                }
            }
        }
    }

    // Smooth: each rock its own delay and time; up fast, slowing to a stop, already turning round the target as it rises.
    private static Vec3 rockPos(Move m, Rock r, double t) {
        Vec3 oc = m.oc;
        double k = clamp((t - OpmOmni.OS - r.delay) / r.dur, 0, 1), e = 1 - Math.pow(1 - k, 4), side = smooth(k * 1.4);
        double x = Mth.lerp(side, r.st.x, r.home.x), y = Mth.lerp(e, r.st.y, r.home.y), z = Mth.lerp(side, r.st.z, r.home.z);
        double tt = Math.max(0, t - OpmOmni.OS - r.delay), a = r.orb * tt * tt / (tt + 1.2), dx = x - oc.x, dz = z - oc.z;
        x = oc.x + dx * Math.cos(a) - dz * Math.sin(a);
        z = oc.z + dx * Math.sin(a) + dz * Math.cos(a);
        y += Math.sin(t * 1.3 + r.sz) * 0.4 * smooth(k * 1.2 - 0.2);
        return new Vec3(x, y, z);
    }

    // Where he lands on a rock: on its face toward the target.
    private static Vec3 onRock(Move m, Rock r, double t) {
        Vec3 p = rockPos(m, r, t), toC = m.oc.subtract(p).normalize();
        return p.add(toC.scale(r.sz * 0.55 + 0.3));
    }

    private static void omniFx(Move m, double t, double prev, double dt, double now, double time, Vec3 cam) {
        Minecraft mc = Minecraft.getInstance();
        Frame f = m.f;
        Vec3 slam = f.origin();
        silence(m, t, 0.95, 0.9, "マジ全方位パンチ", "SÉRIE SÉRIA · SOCOS OMNIDIRECIONAIS SÉRIOS");
        boolean close = near(slam, 120);
        if (t < OpmOmni.OS && close) {
            focus = Math.max(focus, (float) (0.4 * smooth((t - 0.45) / 0.45)));
        }
        if (m.slamOnly || m.oc == null) {
            return;
        }
        Vec3 oc = m.oc;
        Entity target = mc.level.getEntity(m.target);
        boolean seen = near(oc, 160) || close;
        // the rocks
        for (Rock r : m.rocks) {
            if (r.free) {
                continue;
            }
            Vec3 p = rockPos(m, r, t);
            if (r.jolt > 0) {
                r.jolt = Math.max(0, r.jolt - dt * 4);
                p = p.add((rnd() - 0.5) * r.jolt * 0.5, (rnd() - 0.5) * r.jolt * 0.5, (rnd() - 0.5) * r.jolt * 0.5);
            }
            r.d.px = p.x;
            r.d.py = p.y;
            r.d.pz = p.z;
            r.d.vx = r.d.vy = r.d.vz = 0;
            r.d.floated = 0;
        }
        // the hops: rock to rock, always to the other side (the beam crosses the target)
        if (t >= OpmOmni.OH0 && t < OpmOmni.OHF) {
            int i = Math.min(OpmOmni.OHN - 1, (int) Math.floor((t - OpmOmni.OH0) / OpmOmni.OHD));
            if (m.hopR == null) {
                m.hopR = new ArrayList<>();
                List<Rock> R = new ArrayList<>();
                for (Rock r : m.rocks) {
                    if (r.sz > 3) {
                        R.add(r);
                    }
                }
                if (R.size() < 3) {
                    R = m.rocks;
                }
                int[] used = new int[R.size()];
                List<Integer> recent = new ArrayList<>();
                int cur = (int) (rnd() * R.size());
                for (int j = 0; j < OpmOmni.OHN && !R.isEmpty(); j++) {
                    Vec3 dir = oc.subtract(R.get(cur).home).normalize();
                    int best = -1;
                    double bs = -99;
                    for (int q = 0; q < R.size(); q++) {
                        if (q == cur || recent.contains(q)) {
                            continue;
                        }
                        double c = R.get(q).home.subtract(oc).normalize().dot(dir);
                        if (c < -0.1) {
                            continue;
                        }
                        double sc = c * 0.6 - used[q] * 1.5 + rnd() * 1.4;
                        if (sc > bs) {
                            bs = sc;
                            best = q;
                        }
                    }
                    if (best < 0) {
                        best = (cur + 1) % R.size();
                    }
                    used[best]++;
                    recent.add(best);
                    if (recent.size() > Math.min(8, R.size() - 2)) {
                        recent.remove(0);
                    }
                    m.hopR.add(R.get(best));
                    cur = best;
                }
            }
            // faster and faster, each afterimage lasting longer: by the end dozens of him in the air, like the 50
            while (m.hopI < i && m.hopI + 1 < m.hopR.size()) {
                int h = ++m.hopI;
                double pr = h / (double) (OpmOmni.OHN - 1);
                Rock r = m.hopR.get(h);
                Vec3 to = onRock(m, r, t), from = h == 0 ? m.hopFrom : onRock(m, m.hopR.get(h - 1), t);
                beam(from, to, 0.25 + rnd() * 0.3, 0.9 + rnd() * 0.6);
                r.jolt = 1;
                FLASHES.add(new Flash(to, (float) (1.0 + rnd() * 0.7), now, (float) (rnd() * 10)));
                shake(oc, 0.45);
                for (int j = 0; j < 4; j++) {
                    OpmBlocks.spawn(OpmBlocks.stateAt(to.x, to.z, true), to, new Vec3((rnd() - 0.5) * 12, (rnd() - 0.5) * 12, (rnd() - 0.5) * 12),
                            0.15 + rnd() * 0.4, false, 0.6);
                }
                float life = (float) Mth.lerp(pr, 0.8, 2.4);
                if (seen) {
                    // on the rock: crouched, ready for the next hop; mid-beam: Stark's dive, already aimed at the target
                    OpmGhosts.crouch(m.id, to, oc, 0.95f, life);
                    Vec3 mid = from.lerp(to, 0.35 + rnd() * 0.3), d = oc.subtract(mid).normalize();
                    OpmGhosts.dive(m.id, mid.add(d.scale(1.2)), d, 0.85f, life * 0.8f);
                }
                // the beam went through the target: a blow
                Vec3 tc = target != null ? target.position().add(0, target.getBbHeight() * 0.5, 0) : oc;
                FLASHES.add(new Flash(tc.add((rnd() - 0.5) * 1.5, (rnd() - 0.5) * 1.5, (rnd() - 0.5) * 1.5), 0.6f, now, (float) (rnd() * 10)));
            }
            if (seen) {
                double sk = smooth((t - OpmOmni.OH0) / 1.2);
                vig = Math.max(vig, (float) (0.75 * sk));
                if (0.55 * sk > sil) {
                    sil = (float) (0.55 * sk);
                    silAt = oc;
                }
                focus = Math.max(focus, 0.6f);
            }
        }
        // 50 of him, from every side, punching the target
        if (t >= OpmOmni.OHF && t < OpmOmni.OF) {
            double k = clamp((t - OpmOmni.OHF) / (OpmOmni.OR - OpmOmni.OHF), 0, 1);
            if (m.ringDir == null) {
                m.ringDir = new Vec3[50];
                m.ringAt = new double[50];
                for (int j = 0; j < 50; j++) {
                    double g = j * 2.39996, y = 1 - 2 * (j + 0.5) / 50, rr = Math.sqrt(1 - y * y);
                    m.ringDir[j] = new Vec3(Math.cos(g) * rr, y * 0.85, Math.sin(g) * rr).normalize();
                    m.ringAt[j] = rnd() * 0.35;
                }
            }
            if (seen) {
                double rad = Mth.lerp(smooth(k), 16, 2.6);
                for (int j = 0; j < 50; j++) {
                    double kk = clamp((t - OpmOmni.OHF - m.ringAt[j]) / 0.25, 0, 1);
                    if (kk > 0) {
                        Vec3 d = m.ringDir[j];
                        OpmGhosts.dive(m.id, oc.add(d.scale(Math.max(1.6, rad - 1.2))), d.reverse(), (float) (0.85 * kk), 0);
                    }
                }
                vig = Math.max(vig, 0.8f);
                if (0.55f > sil) {
                    sil = 0.55f;
                    silAt = oc;
                }
            }
        }
        if (prev < OpmOmni.OR && t >= OpmOmni.OR) {
            impact(oc, 3);
            shake(oc, 5);
            if (m.ringDir != null) {
                for (Vec3 d : m.ringDir) {
                    FLASHES.add(new Flash(oc.add(d.scale(2.0)), (float) (1.4 + rnd()), now, (float) (rnd() * 10)));
                }
            }
        }
        if (t >= OpmOmni.OR) {
            double b = t - OpmOmni.OR;
            // the pressure of the 50's punch: a shell with waves running out, torn by noise as it grows
            if (b < 1.4) {
                double e = 1 - Math.pow(1 - clamp(b / 0.7, 0, 1), 3);
                OpmMesh.P p = OpmMesh.p(OpmMesh.BOOM);
                p.life = (float) clamp(b / 1.4, 0, 1);
                p.seed = m.seedCore;
                OpmMesh.queue(OpmMesh.Mesh.SPHERE, new Matrix4f().translation(oc.subtract(cam).toVector3f()).scale((float) (2 + 70 * e)), p, OpmMesh.BOTH, 28);
            }
            // it reaches the ground below: a ring of dust, the ground thrown out (the server sinks it and fells the trees)
            if (b > 0.35) {
                double g = b - 0.35;
                dustRing(slam, g, 90, 2.2, m.seedDust, cam);
                if (!m.boomGround) {
                    m.boomGround = true;
                    shake(slam, 3);
                    for (int j = 0; j < 500; j++) {
                        double a = rnd() * 6.283, r = 10 + rnd() * 40, x = slam.x + Math.cos(a) * r, z = slam.z + Math.sin(a) * r;
                        double y = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
                        if (Math.abs(y - slam.y) > 12) {
                            continue;
                        }
                        OpmBlocks.spawn(OpmBlocks.stateAt(x, z, rnd() < 0.3), new Vec3(x, y + 0.05, z),
                                new Vec3(Math.cos(a) * (8 + rnd() * 16), 6 + rnd() * 14, Math.sin(a) * (8 + rnd() * 16)), 0.2 + Math.pow(rnd(), 2) * 1.2, false, 0.4);
                    }
                }
            }
        }
        // the flash: the rocks burst out, the target over the horizon
        if (prev < OpmOmni.OF && t >= OpmOmni.OF) {
            whiteCut(oc, now);
            lines(now);
            shock(oc, 9, 0xE6F3FF, 1.4f);
            shake(oc, 5);
            EYES.put(m.id, now);
            OpmGhosts.fadePosed(0.35f);
            for (Rock r : m.rocks) {
                if (r.free) {
                    continue;
                }
                r.free = true;
                Vec3 o = new Vec3(r.d.px, r.d.py, r.d.pz).subtract(oc).normalize();
                OpmBlocks.release(r.d, o.scale(45 + rnd() * 60), 0.15, 3);
            }
            for (int j = 0; j < 400; j++) {
                Vec3 o = new Vec3(rnd() - 0.5, rnd() - 0.5, rnd() - 0.5).normalize();
                OpmBlocks.spawn(OpmBlocks.stateAt(oc.x, oc.z, true), oc.add(o.scale(rnd() * 4)), o.scale(20 + rnd() * 50), 0.15 + Math.pow(rnd(), 2) * 0.9, false,
                        0.25);
            }
        }
        if (t >= OpmOmni.OF) {
            double b = t - OpmOmni.OF;
            if (b < 1.1) {
                KiMesh.P p = KiMesh.p(KiMesh.BLAST);
                p.life = (float) clamp(b / 1.1, 0, 1);
                p.seed = m.seedTube;
                p.time = (float) time;
                p.color = new float[] {0.749f, 0.878f, 1f};
                KiMesh.queue(KiMesh.Mesh.BLAST, KiMesh.sphere(oc.subtract(cam).toVector3f(), (float) (1 + 26 * (1 - Math.pow(1 - clamp(b / 0.4, 0, 1), 3)))), p,
                        KiMesh.BOTH, 14);
            }
            if (seen) {
                vig = Math.max(vig, (float) (0.8 * (1 - smooth(b / 1.5))));
            }
            // the way back: the same beam of light as on the rocks, from the target down to where he stood (no teleport)
            if (prev - OpmOmni.OF < OpmOmni.BACK0 && b >= OpmOmni.BACK0) {
                Vec3 to = m.home.add(0, 1.2, 0);
                beam(oc, to, 0.7, 1.4);
                beam(oc.add(0.6, 0, 0), to, 0.3, 1.1);
                for (int j = 1; j <= 5; j++) {
                    Vec3 q = oc.lerp(to, j / 6.0), d = to.subtract(q).normalize();
                    OpmGhosts.dive(m.id, q, d, (float) (0.5 + 0.08 * j), (float) (0.5 + 0.1 * j));
                }
                FLASHES.add(new Flash(to, 1.6f, now, (float) (rnd() * 10)));
                shake(to, 1.5);
                burst(m.home, 120, 12, 6, 0.4, null, 0);
                shock(to, 1.2f, 0xE6F3FF, 0.4f);
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

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static net.minecraft.resources.ResourceLocation textureOf(com.starkinhon.battlegroundsmod.client.SoulBossRenderer r,
            com.starkinhon.battlegroundsmod.boss.SoulBoss boss) {
        return r.getTextureLocation(boss);
    }

    // One ghost arm drawn as the player's own right arm (and sleeve): from the shoulder along q, 0.88 x len long, w wide.
    private static boolean skinArm(Arm a, Quaternionf q, float len, float alpha, Vec3 cam) {
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.client.model.PlayerModel<?> model;
        boolean slim;
        net.minecraft.resources.ResourceLocation tex;
        net.minecraft.world.entity.Entity who = mc.level.getEntity(a.caster());
        if (who instanceof net.minecraft.client.player.AbstractClientPlayer pl
                && mc.getEntityRenderDispatcher().getRenderer(pl) instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer pr) {
            model = pr.getModel();
            slim = pl.getSkin().model() == net.minecraft.client.resources.PlayerSkin.Model.SLIM;
            tex = pl.getSkin().texture();
        } else if (who instanceof com.starkinhon.battlegroundsmod.boss.SoulBoss boss
                && mc.getEntityRenderDispatcher().getRenderer(boss) instanceof com.starkinhon.battlegroundsmod.client.SoulBossRenderer<?> br) {
            // The Saitama boss: his own skin's arm.
            model = br.getModel();
            slim = false;
            tex = textureOf(br, boss);
        } else {
            return false;
        }
        var buf = mc.renderBuffers().bufferSource();
        var vc = buf.getBuffer(net.minecraft.client.renderer.RenderType.entityTranslucent(tex));
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
                Component text = Component.translatable("hud.battlegroundsmod.opm.serio", (int) Math.ceil(left), LEFT.getOrDefault(mc.player.getId(), 1));
                boolean blink = left < 3 && ((int) (left * 4)) % 2 == 0;
                graphics.drawString(font, text, w / 2 - font.width(text) / 2, h / 2 + 18, blink ? 0xFFFFFFFF : 0xFFF2C94C, true);
            }
        }
    }

    // ---------- the camera cuts (Stark 2026-11-20, approved on the preview: "cinema") ----------

    // A cut: from the move's second t0 to t1, the camera at local eye looking at local at (frame m.f; hit: about m.hitAt).
    private record Cut(int kind, double t0, double t1, double[] eye, double[] at, boolean hit) {
    }

    private static final Cut[] CUTS = {
            new Cut(SubNet.OPM_SOCO, OpmShape.T_FACE, OpmShape.T_P - 0.04, new double[] {0.95, 1.5, 1.9}, new double[] {0, 1.55, 0}, false),
            new Cut(SubNet.OPM_SOCO, OpmShape.T_P + OpmShape.HSTOP, OpmShape.T_P + OpmShape.HSTOP + 1.3, new double[] {-6, 3.5, -4}, new double[] {0, 1, 40}, false),
            new Cut(SubNet.OPM_SALTOS, OpmShape.SL0, OpmShape.SL0 + 1.1, new double[] {11, 5, 15}, new double[] {0, 1, 0}, false),
            new Cut(SubNet.OPM_SALTOS, OpmShape.SLF + OpmShape.SL_STOP, OpmShape.SLF + OpmShape.SL_STOP + 1.1, new double[] {-9, 3.5, -7}, new double[] {0, 1, 4}, true),
            new Cut(SubNet.OPM_OMNI, OpmOmni.OS - 0.1, OpmOmni.OS + 1.0, new double[] {-12, 3, -14}, new double[] {0, 9, 2}, false),
            new Cut(SubNet.OPM_OMNI, OpmOmni.OH0, OpmOmni.OH0 + 1.3, new double[] {-38, 8, -46}, new double[] {0, 0, 0}, true),
            new Cut(SubNet.OPM_MESA, OpmShape.MG, OpmShape.MC + 0.6, new double[] {-13, 4, -6}, new double[] {0, 4, OpmShape.HINGE}, false),
            new Cut(SubNet.OPM_ESPIRRO, OpmShape.E1, OpmShape.EX - 0.04, new double[] {0.85, 1.65, 1.8}, new double[] {0, 1.7, 0}, false),
            new Cut(SubNet.OPM_ESPIRRO, OpmShape.EX + 0.2, OpmShape.EX + 1.7, new double[] {-7, 4, -5}, new double[] {0, 2, 60}, false)};

    // Where the camera goes this frame for the local player's own serious move, or null.
    public static com.starkinhon.battlegroundsmod.client.FaceCutscene.Shot shot(float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || MOVES.isEmpty() || !FxConfig.saitamaCuts()) {
            return null;
        }
        double now = ScreenFx.now() + partial;
        for (Move m : MOVES) {
            if (m.id != mc.player.getId()) {
                continue;
            }
            double t = (now - m.start) / 20;
            for (Cut c : CUTS) {
                if (c.kind() != m.kind || t < c.t0() || t >= c.t1()) {
                    continue;
                }
                Frame f = c.hit() && m.hitAt != null ? Frame.of(m.hitAt, m.hitDir) : m.f;
                if (c.hit() && m.hitAt == null) {
                    continue;
                }
                // a slow push in over the cut
                double k = (t - c.t0()) / (c.t1() - c.t0()), push = 1 - 0.12 * smooth(k);
                Vec3 at = f.world(c.at()[0], c.at()[1], c.at()[2]);
                Vec3 want = f.world(c.eye()[0] * push, c.eye()[1], c.eye()[2] * push);
                // never inside a wall: pulled in to just before what is in the way
                net.minecraft.world.phys.BlockHitResult hit = mc.level.clip(new net.minecraft.world.level.ClipContext(at, want,
                        net.minecraft.world.level.ClipContext.Block.VISUAL, net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player));
                Vec3 eye = hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? want
                        : hit.getLocation().add(at.subtract(hit.getLocation()).normalize().scale(0.4));
                if (eye.distanceTo(at) < 1.2) {
                    return null;
                }
                Vec3 d = at.subtract(eye);
                float yaw = (float) (Mth.atan2(-d.x, d.z) * Mth.RAD_TO_DEG);
                float pitch = (float) (-Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
                return new com.starkinhon.battlegroundsmod.client.FaceCutscene.Shot(eye, yaw, pitch);
            }
        }
        return null;
    }

    public static boolean isSerious(LivingEntity entity) {
        if (entity == null) {
            return false;
        }
        Double end = SERIO.get(entity.getId());
        if (end == null) {
            return false;
        }
        if (end <= ScreenFx.now()) {
            SERIO.remove(entity.getId());
            return false;
        }
        return true;
    }
}
