package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.starkinhon.battlegroundsmod.client.FalaRender;
import com.starkinhon.battlegroundsmod.power.powers.opm.OpmShape.Frame;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

// Luffy on the screen: the approved page (forja/luffy-gears: luffy.js, ults.js, lib.js) played from the server's messages.
// Holding a key climbs the Gears (each level changes his body at once and puts its rubber arm, steam or cloud on him);
// letting go sends that Gear's move, and from then on every piece is a function of the move's own clock, like the page:
// the rubber arm (one mesh: thins as it stretches, swells with air, wobbles like a spring), the arms of the Gatling, the
// two open hands of the Bazooka, the Leo Bazooka's arms folding in the air, the Nika's clap, the G2 air bursts (rings of
// points and half moons, each its own outline), the steam of G2/G4, the cloud round his neck in G5, the Fusen balloon,
// the King Kong Gun's black fist with red lines and the hole it opens in the clouds, the Kaminari's lightning that turns
// to rubber in his hand; the ground's real blocks flying (bouncing like balls in G5) and the ground itself waving like
// rubber; the target squashed flat; on the screen the impact frames, shake, shock, speed lines, captions and his words.
// The page's world: Luffy at the origin looking at +Z; its +X is his right here (the arm comes from his right shoulder).
public final class LuffyFx {
    private LuffyFx() {
    }

    // ---------- the page's tables ----------

    private static final double[] TH = {0, 0.25, 0.5, 0.9, 1.4};
    private static final double[][] REACH = {{12, 16, 14, 18, 22}, {9, 10, 12, 12, 15}, {11, 13, 13, 14, 17}, {12, 14, 4, 18, 22}};
    private static final String[] LVNAME = {"", "Gear Second!", "Gear Third!", "Gear Fourth! Boundman!", "Gear Fifth!"};
    private static final String[][] JP = {
            {"ゴムゴムの銃", "ゴムゴムのJET銃", "ゴムゴムの巨人の銃", "ゴムゴムの大猿王銃", "ゴムゴムの猿神銃"},
            {"ゴムゴムの銃乱打", "ゴムゴムのJET銃乱打", "ゴムゴムの象銃乱打", "ゴムゴムの大猿王群鴉砲", "ゴムゴムの銃乱打"},
            {"ゴムゴムのバズーカ", "ゴムゴムのJETバズーカ", "ゴムゴムの灰熊銃", "ゴムゴムの獅子・バズーカ", "ゴムゴムのバズーカ"},
            {"ゴムゴムのライフル", "ゴムゴムのJET", "ゴムゴムの風船", "ゴムゴムの猿王銃", "ゴムゴムの雷"}};
    private static final String[][] PT = {
            {"GOMU GOMU NO... PISTOL!", "GEAR SECOND · JET PISTOL!", "GEAR THIRD · GIGANT PISTOL!", "GEAR FOURTH · KONG GUN!", "GEAR FIFTH · BAJRANG GUN!"},
            {"GOMU GOMU NO... GATLING!", "GEAR SECOND · JET GATLING!", "GEAR THIRD · ELEPHANT GATLING!", "GEAR FOURTH · KONG ORGAN!",
                    "GEAR FIFTH · GATLING DO NIKA!"},
            {"GOMU GOMU NO... BAZOOKA!", "GEAR SECOND · JET BAZOOKA!", "GEAR THIRD · GRIZZLY MAGNUM!", "GEAR FOURTH · LEO BAZOOKA!",
                    "GEAR FIFTH · BAZOOKA DO NIKA!"},
            {"GOMU GOMU NO... RIFLE!", "GEAR SECOND · JET MERGULHO!", "GEAR THIRD · GOMU GOMU NO FUSEN!", "GEAR FOURTH · KING KONG GUN!",
                    "GEAR FIFTH · GOMU GOMU NO KAMINARI!"}};
    // How long each move plays (seconds), per key and Gear.
    private static final double[][] END = {{1.0, 0.6, 1.5, 1.2, 1.7}, {1.6, 1.5, 1.9, 1.7, 2.1}, {1.0, 0.6, 1.4, 1.3, 1.6}, {1.0, 1.35, 1.55, 6.0, 2.1}};
    // Gatling: arms a second, life of each arm, thickness, fist, spread of the aim, length of the barrage.
    private static final double[][] GP = {{48, 0.22, 1, 1, 0.8, 1.0}, {130, 0.09, 1, 1, 0.9, 0.9}, {12, 0.38, 4.5, 5.5, 2.2, 1.3}, {26, 0.2, 2.2, 3.4, 1.6, 1.1},
            {26, 0.24, 2.4, 6, 2.6, 1.2}};
    // Bazooka: how far back, time pulling, time coming, thickness, hand.
    private static final double[][] BP = {{3.2, 0.22, 0.1, 1, 1.1}, {2.2, 0.08, 0.03, 1, 1.1}, {5, 0.45, 0.22, 5.5, 7}, {0.3, 0.16, 0.34, 2.4, 3.8}, {7, 0.34, 0.12, 3, 11}};

    private static final int SKIN = 0xF0C09A, PINK = 0xF39A92, HAKI = 0x1C171C, WHITE = 0xF4F1EA, RIM = 0xFFE7CF, VEST = 0xC8262B, BAND = 0xC4202A;
    private static final int BOLT_N = 5;

    // ---------- state ----------

    // One Luffy: his charge, his Gear, his states, the move he is doing.
    private static final class Body {
        final int id;
        boolean holding;
        int key = -1, lv;
        double chargeStart, lvAt;
        double g5Until = -1, fomeUntil = -1, tinyStart = -1, tinyEnd = -1;
        int keepGear = -1;
        double keepUntil = -1, fusenHit = -100;
        Move move;
        // what the last frame drew on him (for his body, drawn by the game before the effects)
        boolean armOn, hideArms, hidden;
        double flip = -1, spin, lift, balloon;
        int lookLv;
        double lookK;

        Body(int id) {
            this.id = id;
        }
    }

    private static final class Move {
        final int id, key, gear, target;
        final double start;
        final Vec3 look, from0, fallback;
        double prevU = -1, L, holeT = -1, blastT = -1;
        boolean hit, fin, vanish, said1, said2;
        int spawned;
        Vec3 blastAt, lastC;
        final List<Gat> gats = new ArrayList<>();

        Move(int id, int key, int gear, int target, double start, Vec3 from0, Vec3 look, Vec3 fallback) {
            this.id = id;
            this.key = key;
            this.gear = gear;
            this.target = target;
            this.start = start;
            this.from0 = from0;
            this.look = look;
            this.fallback = fallback;
        }
    }

    // One arm of the barrage: its shoulder point, its own aim, life, fist and wobble.
    private static final class Gat {
        double born;
        float life, fs, infl, ph, n;
        int side;
        Vec3 sh, aim;
        boolean fist, hit;
    }

    private record Burst(Vec3 pos, Vec3 dir, float size, float life, double born, float arc, float sp, float thin, float seed, float[] tint, float drift) {
    }

    private record Rubber(Vec3 c, float w, double born) {
    }

    private record Squash(int type, double born, Vec3 fwd) {
    }

    private static final int BURY = 0, BURY2 = 1, PANCAKE = 2, PANCAKE_K = 3, GAT5 = 4, CLAP = 5;

    private static final Map<Integer, Body> BODIES = new HashMap<>();
    private static final List<Burst> BURSTS = new ArrayList<>();
    private static final List<Rubber> RUBBERS = new ArrayList<>();
    private static final Map<Integer, Squash> SQUASH = new HashMap<>();
    // whoever he sent flying: tumbles in the air and lands lying flat (the page's stepFig: spin while flying, then flat)
    private static final Map<Integer, Knock> KNOCK = new HashMap<>();

    private static final class Knock {
        final double born, spin;
        final Vec3 fwd;
        double landed = -1, airAngle;
        boolean flew;

        Knock(double born, Vec3 fwd, double spin) {
            this.born = born;
            this.fwd = fwd;
            this.spin = spin;
        }
    }

    // the page's launch spins (rad/s) by Gear: toque, G2, G3, G4, G5
    private static final double[] SPIN = {-2.5, -9, -2, -14, -6};

    public static boolean active() {
        if (!BURSTS.isEmpty() || !RUBBERS.isEmpty() || !SQUASH.isEmpty() || !KNOCK.isEmpty()) {
            return true;
        }
        double now = ScreenFx.now();
        for (Body b : BODIES.values()) {
            if (b.holding || b.move != null || b.g5Until > now || b.keepUntil > now || b.tinyEnd > now || b.fomeUntil > now) {
                return true;
            }
        }
        return false;
    }

    public static void clear() {
        BODIES.clear();
        BURSTS.clear();
        RUBBERS.clear();
        SQUASH.clear();
        KNOCK.clear();
    }

    // ---------- small math ----------

    private static double rnd() {
        return OpmBlocks.rnd();
    }

    private static double clamp(double x, double a, double b) {
        return Math.max(a, Math.min(b, x));
    }

    private static double smooth(double x) {
        x = clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    private static double lerp(double a, double b, double k) {
        return a + (b - a) * k;
    }

    private static Vec3 lerp(Vec3 a, Vec3 b, double k) {
        return a.add(b.subtract(a).scale(k));
    }

    private static float[] rgb(int c) {
        return new float[] {((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f};
    }

    private static int mix(int a, int b, double k) {
        int r = (int) Math.round(lerp((a >> 16) & 255, (b >> 16) & 255, k)), g = (int) Math.round(lerp((a >> 8) & 255, (b >> 8) & 255, k)),
                bl = (int) Math.round(lerp(a & 255, b & 255, k));
        return (r << 16) | (g << 8) | bl;
    }

    private static double groundY(double x, double z) {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? 0 : level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
    }

    // ---------- the frame of the move being drawn ----------

    private static Frame F;
    private static Vec3 CAM, C, TP, TG, FEET;
    private static double U, PU, DT, NOW, TIME, BODYY;
    private static final double MS = 0.9375;
    private static double SX = 1, SY = 1, SZ = 1;
    private static Body B;
    private static Entity TGT;

    // page point (x his right, y up from his feet, z ahead) -> world
    private static Vec3 W(double x, double y, double z) {
        return F.world(-x, y, z);
    }

    private static Vec3 D(double x, double y, double z) {
        return F.dir(-x, y, z);
    }

    // a point of his upper body (the page's luf.up: 0.75 up, Boundman's scale)
    private static Vec3 UP(double x, double y, double z) {
        // the page's body is 2 units tall; the game's player model is drawn at 0.9375 (1.875 blocks), so his upper body is too
        return W(x * SX * MS, (0.75 + y * SY) * MS + BODYY, z * SZ * MS);
    }

    private static Vec3 shoulder() {
        return UP(0.375, 0.68, 0);
    }

    private static Vec3 tAim() {
        return C.add(0, 0.05, 0);
    }

    // world -> camera-relative, with the page's Object3D.lookAt (+Z towards the point, Y up)
    private static Matrix4f lookAt(Vec3 from, Vec3 to, Vec3 cam) {
        Vector3f z = new Vector3f((float) (to.x - from.x), (float) (to.y - from.y), (float) (to.z - from.z));
        if (z.lengthSquared() < 1.0E-8f) {
            z.set(0, 0, 1);
        }
        z.normalize();
        Vector3f x = new Vector3f(0, 1, 0).cross(z);
        if (x.lengthSquared() < 1.0E-8f) {
            z.z += 1.0E-4f;
            z.normalize();
            x = new Vector3f(0, 1, 0).cross(z);
            if (x.lengthSquared() < 1.0E-8f) {
                x.set(1, 0, 0);
            }
        }
        x.normalize();
        Vector3f y = new Vector3f(z).cross(x);
        return new Matrix4f(x.x, x.y, x.z, 0, y.x, y.y, y.z, 0, z.x, z.y, z.z, 0, (float) (from.x - cam.x), (float) (from.y - cam.y), (float) (from.z - cam.z), 1);
    }

    // his own axes (right-handed: left, up, ahead) at p, for the round pieces (steam, cloud, balloon)
    private static Matrix4f basis(Vec3 p, Vec3 cam) {
        Vec3 l = F.left(), w = F.forward();
        return new Matrix4f((float) l.x, 0, (float) l.z, 0, 0, 1, 0, 0, (float) w.x, 0, (float) w.z, 0, (float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z), 1);
    }

    // ---------- the screen, as the page's helpers ----------

    private static void impact(Vec3 at, int[] neg, int tint) {
        if (!OpmFx.near(at, 120)) {
            return;
        }
        int[] seq = new int[neg.length];
        for (int i = 0; i < neg.length; i++) {
            seq[i] = neg[i] == 1 ? 4 : 3;
        }
        ScreenFx.impactFrame(seq, 1, tint, at, 0);
    }

    private static final int[] I3 = {1, 0, 1}, I5 = {1, 0, 1, 0, 1}, I7 = {1, 0, 1, 0, 1, 0, 1}, I9 = {1, 0, 1, 0, 1, 0, 1, 0, 1};

    private static void say(int id, String text, boolean big) {
        FalaRender.add(id, text, big ? 0xFFFFFF : 0xF2C94C, big ? 0xFFE08A : 0xFFF6D6, big ? 14 : 22);
    }

    // burst(position, direction of the blow, size, life, ...): one anime air wave, its own outline
    private static void burst(Vec3 pos, Vec3 dir, double size, double life, double arc, int sp, double thin, double delay, int tint, double drift) {
        if (BURSTS.size() > 160) {
            BURSTS.remove(0);
        }
        Vec3 d = dir.lengthSqr() < 1.0E-8 ? F.forward() : dir.normalize();
        BURSTS.add(new Burst(pos, d, (float) size, (float) life, NOW + delay * 20, (float) arc, sp > 0 ? sp : 12 + (int) (rnd() * 9), (float) thin,
                (float) (rnd() * 10), rgb(tint), (float) drift));
    }

    private static void burst(Vec3 pos, Vec3 dir, double size, double life) {
        burst(pos, dir, size, life, 0, 0, 0, 0, 0xC8DEFA, 0);
    }

    // one G2 blow: the air bursts at the shoulder, half moons run along the way, a burst of points at the target, a thin ring
    private static void jetLine(Vec3 from, Vec3 to, double pw, double d0, boolean noStart) {
        Vec3 dir = to.subtract(from);
        double d = dir.length();
        dir = d < 1.0E-6 ? F.forward() : dir.scale(1 / d);
        if (!noStart) {
            burst(from.add(dir.scale(0.4)), dir, 0.9 * pw, 0.16, 0, 10, 0, d0, 0xC8DEFA, 0);
        }
        int n = Math.max(2, (int) Math.round(d / 2.2));
        for (int i = 1; i <= n; i++) {
            double k = i / (double) (n + 1);
            burst(from.add(dir.scale(d * k)), dir, (1.2 + 1.8 * k) * pw, 0.2, 0.95 + rnd() * 0.3, 0, 0.12, d0 + 0.006 * i, 0xC8DEFA, 0);
        }
        burst(to, dir, 3.2 * pw, 0.3, 0, 16 + (int) (rnd() * 8), 0, d0 + 0.02, 0xC8DEFA, 0);
        burst(to, dir, 5.2 * pw, 0.38, 0, 26, 0.6, d0 + 0.045, 0xC8DEFA, 0);
        burst(to.add(dir.scale(0.5)), dir, 2.2 * pw, 0.3, 1.0, 0, 0.35, d0 + 0.03, 0xC8DEFA, 5 * pw);
    }

    // a piece of the real ground (the page's spawnDeb): at base + (lx, ly, lz) of his frame, flying (vx, vy, vz)
    private static void deb(Vec3 base, double lx, double ly, double lz, double vx, double vy, double vz, double size, boolean deep, boolean bouncy) {
        Vec3 p = base.add(D(lx, 0, lz)).add(0, ly, 0);
        OpmBlocks.Deb d = OpmBlocks.spawn(OpmBlocks.stateAt(p.x, p.z, deep), p, D(vx, vy, vz), size, false, 0);
        if (d != null) {
            d.bouncy = bouncy;
        }
    }

    private static void squash(int type) {
        if (TGT != null) {
            SQUASH.put(TGT.getId(), new Squash(type, NOW, F.forward()));
        }
    }

    // ---------- the messages ----------

    private static Body body(int id) {
        return BODIES.computeIfAbsent(id, Body::new);
    }

    static void receive(int kind, int id, Vec3 pos, Vec3 dir, float a, float b, float c) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        double now = ScreenFx.now();
        switch (kind) {
            case SubNet.LUFFY_CHARGE -> {
                Body bd = body(id);
                int gear = (int) b;
                if (c < 0.5f) {
                    bd.holding = true;
                    bd.key = (int) a;
                    bd.lv = gear;
                    bd.chargeStart = now - TH[gear] * 20;
                    bd.lvAt = now - 3;
                    bd.move = null;
                } else if (c < 1.5f) {
                    bd.lv = gear;
                    bd.lvAt = now;
                    levelUp(id, pos, gear);
                } else {
                    bd.holding = false;
                }
            }
            case SubNet.LUFFY_FIRE -> {
                Body bd = body(id);
                int key = (int) a, gear = (int) b;
                bd.holding = false;
                Frame f = Frame.of(pos, dir);
                Vec3 eye = pos.add(0, 1.62, 0);
                Vec3 fallback = eye.add(dir.normalize().scale(REACH[Math.min(3, key)][gear]));
                Move m = new Move(id, key, gear, (int) c, now, pos, dir, fallback);
                bd.move = m;
                if (gear == 1 || gear == 3) {
                    bd.keepGear = gear;
                    bd.keepUntil = now + END[key][gear] * 20 + 80;
                }
                // the ground he will tear up, as it is now (before the server takes it out)
                OpmBlocks.remember(f, -6, 6, -2, 26, (x, z) -> true);
                Entity t = c >= 0 ? mc.level.getEntity((int) c) : null;
                Vec3 at = t != null ? t.position() : fallback;
                for (int x = -7; x <= 7; x++) {
                    for (int z = -7; z <= 7; z++) {
                        if (x * x + z * z <= 49) {
                            OpmBlocks.remember(Mth.floor(at.x) + x, Mth.floor(at.z) + z);
                        }
                    }
                }
                if (key == 3 && gear == 3) {
                    OpmBlocks.remember(f, -3, 3, 0, 46, (x, z) -> true);
                }
            }
            case SubNet.LUFFY_G5 -> {
                Body bd = body(id);
                if (a > 1.5f) {
                    bd.g5Until = -1;
                    bd.fomeUntil = now + b;
                } else if (a > 0.5f) {
                    bd.g5Until = now + b;
                    bd.fomeUntil = -1;
                    bd.tinyEnd = -1;
                    levelUp(id, pos, 4);
                    OpmFx.whiteCut(pos, now);
                    OpmFx.lines(now);
                } else {
                    bd.g5Until = -1;
                }
            }
            case SubNet.LUFFY_AFTER -> {
                Body bd = body(id);
                if (a < 1.5f) {
                    bd.tinyStart = now;
                    bd.tinyEnd = now + b;
                }
            }
            case SubNet.LUFFY_DENT -> {
                if (c > 1.5f) {
                    // the Fusen sent something back: BOING, the air going out the way it came
                    Frame keep = F;
                    F = Frame.of(pos, dir);
                    NOW = now;
                    burst(pos, dir, 1.8, 0.22, 0, 10, 0, 0, 0xE8D8C0, 0);
                    jetLine(pos, pos.add(dir.scale(6)), 0.9, 0, true);
                    F = keep;
                    OpmFx.shake(pos, 1.2);
                    OpmFx.lines(now);
                    for (Body bd : BODIES.values()) {
                        if (bd.move != null && bd.move.key == 3 && bd.move.gear == 2) {
                            bd.fusenHit = now;
                            say(bd.id, "BOING!", true);
                        }
                    }
                } else {
                    int r = (int) Math.ceil(a) + 2;
                    for (int x = -r; x <= r; x++) {
                        for (int z = -r; z <= r; z++) {
                            OpmBlocks.remember(Mth.floor(pos.x) + x, Mth.floor(pos.z) + z);
                        }
                    }
                }
            }
            case SubNet.LUFFY_RUBBER -> {
                if (RUBBERS.size() > 6) {
                    RUBBERS.remove(0);
                }
                RUBBERS.add(new Rubber(pos, a, now));
            }
            default -> {
            }
        }
    }

    // A new Gear on his body: his words, a ring of dust at his feet, the air bending round him.
    private static void levelUp(int id, Vec3 feet, int lv) {
        if (lv <= 0) {
            return;
        }
        say(id, LVNAME[lv], false);
        OpmFx.ring(feet, 3 + lv, 0.5f);
        OpmFx.shake(feet, 0.35 + lv * 0.2);
        OpmFx.shock(feet.add(0, 1.2, 0), 0.6f + 0.2f * lv, lv == 4 ? 0xFFFFFF : 0xFFE6D0, 0.4f);
    }

    // ---------- the look of each Gear (the page's look()) ----------

    private static final class Arm {
        Vec3 from, aim;
        double L = 0.75, fs = 1, infl = 1, amp, ph, n = 2, bx = -1, ba, toon, tw, hx, blur, haki, spin;
        int col = SKIN, rim = RIM;
        boolean fist = true, thumb = true, kkg;
        double fx = 1, fy = 1, fz = 1, off = 0.12;
        LuffyMesh.Mesh mesh = LuffyMesh.Mesh.ARM;
    }

    private static Arm look(int lv, double k) {
        Arm a = new Arm();
        int c = SKIN;
        if (lv == 1) {
            c = mix(SKIN, PINK, k);
        }
        a.col = c;
        if (lv == 3) {
            a.col = mix(SKIN, HAKI, k);
            a.rim = 0xB04A6A;
            a.haki = k;
        }
        if (lv == 4) {
            a.toon = k;
        }
        return a;
    }

    private static void solidBox(Matrix4f m, double x, double y, double z, double w, double h, double d, int col, boolean kkg) {
        LuffyMesh.P p = LuffyMesh.p(LuffyMesh.SOLID);
        p.col = rgb(col);
        p.glow = false;
        p.p0[0] = kkg ? 1 : 0;
        LuffyMesh.queue(LuffyMesh.Mesh.BOX, new Matrix4f(m).translate((float) x, (float) y, (float) z).scale((float) w, (float) h, (float) d), p, LuffyMesh.FRONT, 8);
    }

    private static void drawArm(Arm a) {
        if (a.from == null || a.aim == null) {
            return;
        }
        Matrix4f M = lookAt(a.from, a.aim, CAM);
        LuffyMesh.P p = LuffyMesh.p(LuffyMesh.ARM);
        p.p0 = new float[] {(float) a.L, 0.115f, (float) a.infl, (float) a.amp};
        p.p1 = new float[] {(float) a.ph, (float) a.n, (float) a.bx, (float) a.ba};
        p.p2 = new float[] {(float) a.toon, (float) a.tw, (float) a.hx, (float) a.blur};
        p.p3 = new float[] {(float) a.haki, 0, 0, 0};
        p.col = rgb(a.col);
        p.col2 = rgb(a.rim);
        p.glow = false;
        if (a.blur > 0) {
            p.solid = false;
        }
        LuffyMesh.queue(a.mesh, M, p, LuffyMesh.BOTH, 10);
        if (!a.fist) {
            return;
        }
        Matrix4f fm = new Matrix4f(M).translate(0, 0, (float) (a.L + a.off * a.fs)).rotateZ((float) a.spin)
                .scale((float) (a.fs * a.fx), (float) (a.fs * a.fy), (float) (a.fs * a.fz));
        solidBox(fm, 0, 0, 0, 0.3, 0.28, 0.3, a.col, a.kkg);
        solidBox(fm, 0, 0.07, 0.15, 0.31, 0.08, 0.06, a.col, a.kkg);
        if (a.thumb) {
            solidBox(fm, 0.15, -0.03, 0.08, 0.08, 0.16, 0.12, a.col, a.kkg);
        }
    }

    // the page's extendTo: the arm out to the target (k of the way)
    private static void extendTo(Arm a, double k, double extra) {
        a.from = shoulder();
        a.aim = tAim();
        a.L = Math.max(0.75, (a.from.distanceTo(a.aim) - 0.15 * a.fs + extra) * k);
    }

    // the page's snapBack: comes back whipping and wobbling; false when it is home
    private static boolean snapBack(Arm a, double u, double d, double L0) {
        double k = clamp(u / d, 0, 1);
        a.L = lerp(L0, 0.9, 1 - Math.pow(1 - k, 2));
        a.amp = 0.6 * Math.exp(-k * 3) * Math.sin(k * 16);
        a.ph = k * 20;
        return k < 1;
    }

    // ---------- steam and cloud ----------

    private static void steam(Vec3 pos, double sx, double sy, double sz, double alpha, double rise, float r, float g, float b) {
        LuffyMesh.P p = LuffyMesh.p(LuffyMesh.STEAM);
        p.t = (float) TIME;
        p.alpha = (float) alpha;
        p.p0[0] = (float) rise;
        p.col = new float[] {r, g, b};
        p.glow = false;
        LuffyMesh.queue(LuffyMesh.Mesh.SPHERE, basis(pos, CAM).scale((float) sx, (float) sy, (float) sz), p, LuffyMesh.BOTH, 12);
    }

    private static void steamG2(double alpha, double rise) {
        steam(W(0, 1.0, 0), 0.62, 1.05, 0.45, alpha, rise, 1, 0.84f, 0.84f);
    }

    private static void steamG4(double alpha, double rise, double bob) {
        steam(W(0, 1.9 + bob, -0.1), 0.9, 0.55, 0.6, alpha, rise, 0.95f, 0.95f, 1);
    }

    // the page puts it at 0.72; in the game it sat over half his head (his head is smaller and lower), so it goes lower
    private static void collar(double alpha) {
        LuffyMesh.P p = LuffyMesh.p(LuffyMesh.COLLAR);
        p.t = (float) TIME;
        p.alpha = (float) alpha;
        p.glow = false;
        LuffyMesh.queue(LuffyMesh.Mesh.TORUS, basis(UP(0, 0.56, 0), CAM).scale((float) MS).rotateX((float) (Math.PI / 2)), p, LuffyMesh.FRONT, 13);
    }

    // ---------- every frame ----------

    static void render(Vec3 cam, double now, float partial, double dt, double time) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            clear();
            return;
        }
        CAM = cam;
        NOW = now;
        TIME = time;
        DT = dt;
        for (Iterator<Map.Entry<Integer, Body>> it = BODIES.entrySet().iterator(); it.hasNext(); ) {
            Body bd = it.next().getValue();
            Entity e = level.getEntity(bd.id);
            bd.armOn = false;
            bd.hideArms = false;
            bd.hidden = false;
            bd.flip = -1;
            bd.lift = 0;
            bd.balloon = 0;
            if (e == null || e.isRemoved()) {
                if (!bd.holding && bd.move == null && bd.g5Until < now) {
                    it.remove();
                }
                continue;
            }
            B = bd;
            Vec3 feet = e.getPosition(partial);
            FEET = feet;
            Vec3 look = bd.move != null ? bd.move.look : e.getViewVector(partial);
            F = Frame.of(feet, look);
            if (bd.move != null) {
                Move m = bd.move;
                double u = (now - m.start) / 20;
                if (u > END[m.key][m.gear]) {
                    bd.move = null;
                } else {
                    playMove(level, bd, m, u, partial);
                    continue;
                }
            }
            if (bd.holding) {
                charge(bd, (now - bd.chargeStart) / 20);
                continue;
            }
            // no move: what stays on him (G5's cloud, G2's steam, Boundman)
            int lv = bd.g5Until > now ? 4 : bd.keepUntil > now ? bd.keepGear : 0;
            setLook(bd, lv, lv > 0 ? 1 : 0);
            if (lv == 1) {
                steamG2(0.5, 1.3);
            } else if (lv == 3) {
                steamG4(0.85, 1.8, 0.15);
            } else if (lv == 4) {
                collar(1);
            }
        }
        bursts();
        rubberGround(cam, now);
        SQUASH.values().removeIf(s -> (now - s.born()) / 20 > 2.4);
        KNOCK.values().removeIf(k -> (now - k.born) / 20 > 6 || k.landed >= 0 && (now - k.landed) / 20 > 2.6);
    }

    private static void setLook(Body bd, int lv, double k) {
        bd.lookLv = lv;
        bd.lookK = k;
        SX = lv == 3 ? 1 + 0.55 * k : 1;
        SY = lv == 3 ? 1 + 0.12 * k : 1;
        SZ = lv == 3 ? 1 + 0.75 * k : 1;
        BODYY = lv == 1 ? -0.2 : lv == 3 ? 0.15 : 0;
    }

    // ---------- holding: the Gear climbs (the page's chargePose and ultCharge) ----------

    private static void charge(Body bd, double t) {
        int lv = bd.lv;
        double lvAge = (NOW - bd.lvAt) / 20;
        double k = smooth(lvAge / 0.14);
        if (bd.g5Until > NOW) {
            lv = 4;
            k = 1;
        }
        setLook(bd, lv, k);
        double s = smooth(t / 0.12);
        Arm a = look(lv, k);
        a.from = shoulder();
        a.aim = UP(0.6, 0.55, -2.5);
        a.L = 0.75 + 0.5 * s;
        a.amp = 0.04;
        a.ph = TIME * 6;
        boolean armOn = true;
        if (lv == 1) {
            steamG2(k, 1.3);
        }
        if (lv == 2) {
            double blow = smooth((lvAge - 0.08) / 0.32);
            a.aim = UP(1.8, 4.5, -3);
            a.L = 1.2 + 3.2 * blow;
            a.infl = 1 + 6.5 * blow;
            a.fs = 1 + 8 * blow;
            a.bx = blow < 1 ? blow * 1.2 : -1;
            a.ba = 0.8 * (1 - blow);
            a.amp = 0.25 * (1 - blow);
        }
        if (lv == 3) {
            double b = Math.abs(Math.sin(TIME * 7.5));
            a.aim = UP(0.4, 0.6, -3);
            a.L = lerp(0.9, 0.25, k);
            a.infl = lerp(3, 2.2, k);
            a.fs = 3.6 * (0.7 + 0.3 * k);
            steamG4(0.85 * k, 1.8, 0.55 * b);
        }
        if (lv == 4) {
            double w = smooth(lvAge / 0.35);
            if (bd.g5Until > NOW) {
                w = 1;
            }
            collar(w);
            a.aim = UP(1.5, 2.2 + 0.6 * Math.sin(TIME * 3), -6);
            a.L = 1.2 + 5.5 * w;
            a.infl = 1 + 2.2 * w;
            a.fs = 1 + 9 * w;
            a.amp = 0.35 * w;
            a.n = 3;
            a.ph = TIME * 7;
        }
        if (bd.key == 3) {
            // the V: the body already shows which ult is coming
            if (lv == 0) {
                double w = smooth(t / 0.2);
                a.tw = 16 * w;
                a.hx = 0.07 * w;
                a.spin = TIME * 30 * w;
                a.L += 0.4 * w;
            }
            if (lv == 2) {
                double blow = smooth((lvAge - 0.08) / 0.3);
                armOn = false;
                bd.balloon = 0.22 * blow + 0.02 * Math.sin(TIME * 20) * blow;
                bd.lift = 0.3 * blow;
                balloon(bd.balloon, 0);
            }
            if (lv == 3) {
                a.fs *= 1.9;
                a.infl *= 1.3;
                a.kkg = true;
                a.rim = 0xFF3A2A;
                a.haki = 1;
                a.col = HAKI;
            }
            if (lv == 4) {
                double w = smooth((lvAge) / 0.45);
                a.aim = UP(0.9, 60, 1.5);
                a.L = 1.2 + 76 * w;
                a.fs = 1 + 3 * w;
                a.infl = 1 + 1.2 * w;
                a.amp = 0.9 * w;
                a.n = 4;
                a.ph = TIME * 5;
            }
        }
        if (armOn) {
            drawArm(a);
            bd.armOn = true;
        }
    }

    // ---------- letting go: the move ----------

    private static void playMove(ClientLevel level, Body bd, Move m, double u, float partial) {
        U = u;
        PU = m.prevU < 0 ? u - DT : m.prevU;
        m.prevU = u;
        int lv = m.gear;
        setLook(bd, lv, 1);
        Entity t = m.target >= 0 ? level.getEntity(m.target) : null;
        if (t != null && t.isAlive() && (m.lastC == null || t.position().distanceTo(FEET) < REACH[m.key][lv] * 1.7 + 6)) {
            TGT = t;
            TP = t.getPosition(partial);
            C = TP.add(0, t.getBbHeight() * 0.69, 0);
            m.lastC = C;
        } else {
            TGT = t != null && t.isAlive() ? t : null;
            C = m.lastC != null ? m.lastC : m.fallback;
            TP = C.subtract(0, 1.25, 0);
        }
        TG = new Vec3(TP.x, groundY(TP.x, TP.z), TP.z);
        // the blow sent it flying (the server's launch): from here it tumbles and lands flat, as on the page
        if (TGT instanceof LivingEntity le && !SQUASH.containsKey(le.getId()) && !KNOCK.containsKey(le.getId()) && !le.onGround()
                && le.getDeltaMovement().y > 0.08) {
            KNOCK.put(le.getId(), new Knock(NOW, F.forward(), SPIN[lv]));
        }
        // the caption (Japanese over Portuguese)
        double a = u < 0.08 ? u / 0.08 : u < 0.75 ? 1 : Math.max(0, 1 - (u - 0.75) / 0.25);
        OpmFx.caption(FEET, JP[m.key][lv], PT[m.key][lv], (float) a);
        switch (m.key) {
            case 0 -> pistol(m, lv);
            case 1 -> gatling(m, lv);
            case 2 -> {
                switch (lv) {
                    case 1 -> jetBazooka(m);
                    case 3 -> leo(m);
                    case 4 -> clap(m);
                    default -> bazooka(m, lv);
                }
            }
            default -> ult(m, lv);
        }
    }

    private static boolean at(double t) {
        return PU < t && U >= t;
    }

    // ---------- Z: Pistol ----------

    private static void pistol(Move m, int lv) {
        Body bd = B;
        double u = U;
        Arm a = look(lv, 1);
        switch (lv) {
            case 0 -> {
                if (u < 0.12) {
                    a.amp = 0.05;
                    extendTo(a, smooth(u / 0.12) * 0.98 + 0.02, 0);
                } else {
                    if (at(0.12)) {
                        OpmFx.flash(C.add(D(0, 0, -0.4)), 0.5f);
                        OpmFx.shake(C, 0.6);
                        OpmFx.shock(C, 0.9f, 0xFFF2D6, 0.4f);
                        OpmFx.lines(NOW);
                    }
                    extendTo(a, 1, 0);
                    if (u >= 0.2) {
                        m.L = m.L == 0 ? a.L : m.L;
                        if (!snapBack(a, u - 0.2, 0.45, m.L)) {
                            return;
                        }
                    } else {
                        m.L = a.L;
                    }
                }
                drawArm(a);
                bd.armOn = true;
            }
            case 1 -> {
                steamG2(u < 0.16 ? 1 : Math.max(0.35, 1 - (u - 0.2) * 0.5), 1.3 + 0.8 * Math.exp(-u * 3));
                if (!m.hit) {
                    m.hit = true;
                    jetLine(shoulder(), C, 1, 0, false);
                    impact(C, I3, 0xFFD0D0);
                    OpmFx.shake(C, 1.4);
                    OpmFx.shock(C, 1.6f, 0xFFD8D8, 0.45f);
                    OpmFx.lines(NOW);
                }
            }
            case 2 -> {
                double T1 = 0.42;
                a.infl = 7.5;
                a.fs = 9;
                a.from = shoulder();
                Vec3 to = C.add(0, 0.4, 0);
                if (u < T1) {
                    double k = Math.pow(smooth(u / T1), 1.6);
                    Vec3 from = UP(1.8, 4.5, -3);
                    a.aim = lerp(from, to, k).add(0, Math.sin(k * Math.PI) * 4, 0);
                    a.L = lerp(4.4, a.from.distanceTo(to) - 1.4, k);
                    m.L = a.L;
                } else {
                    if (at(T1)) {
                        OpmFx.flash(C, 2.4f);
                        impact(C, I5, 0xFFFFFF);
                        OpmFx.shake(C, 2.6);
                        OpmFx.ring(TG, 14, 1.0f);
                        OpmFx.shock(TP.add(0, 1, 0), 3, 0xFFE8C8, 0.7f);
                        OpmFx.lines(NOW);
                        squash(BURY);
                        for (int i = 0; i < 380; i++) {
                            double g = rnd() * 6.283, r = 0.6 + rnd() * 3.6, sp = 4 + rnd() * 12;
                            deb(TG, Math.cos(g) * r, 0.2, Math.sin(g) * r, Math.cos(g) * sp * 0.7, 5 + rnd() * 14, Math.sin(g) * sp * 0.7,
                                    0.1 + Math.pow(rnd(), 2.2) * 0.75, rnd() < 0.5, false);
                        }
                    }
                    a.aim = to;
                    a.L = m.L;
                    if (u >= T1 + 0.5) {
                        double d = smooth((u - T1 - 0.5) / 0.45);
                        if (d >= 1) {
                            return;
                        }
                        a.infl = lerp(7.5, 1, d);
                        a.fs = lerp(9, 1, d);
                        a.L = lerp(m.L, 0.9, d);
                    }
                }
                drawArm(a);
                bd.armOn = true;
            }
            case 3 -> kongGun(m, a, false);
            default -> bajrang(m, a);
        }
    }

    // G4: Kong Gun (and the King Kong Gun on the V, its fist almost twice as big, black with red lines)
    private static void kongGun(Move m, Arm a, boolean king) {
        double u = U;
        steamG4(0.85, 2.2, 0.15);
        a.infl = 2.2 * (king ? 1.3 : 1);
        a.fs = 3.6 * (king ? 1.9 : 1);
        if (king) {
            a.kkg = true;
            a.col = HAKI;
            a.rim = 0xFF3A2A;
            a.haki = 1;
        }
        if (u < 0.07) {
            double k = smooth(u / 0.07);
            extendTo(a, k * 0.98 + 0.02, 0);
        } else {
            if (at(0.07)) {
                Vec3 c = C;
                OpmFx.flash(c, 2.2f);
                impact(c, king ? I9 : I7, 0xFFFFFF);
                OpmFx.shake(c, 3.2);
                OpmFx.shock(c, 4.5f, 0xFFE2E8, 0.9f);
                OpmFx.lines(NOW);
                Vec3 g0 = new Vec3(FEET.x, groundY(FEET.x, FEET.z), FEET.z);
                OpmFx.ring(g0, 10, 0.8f);
                for (int i = 0; i < 260; i++) {
                    double an = (rnd() - 0.5) * 1.1, sp = 14 + rnd() * 30;
                    deb(g0, (rnd() - 0.5) * 2, 0.2, 2 + rnd() * 8, Math.sin(an) * sp, 3 + rnd() * 9, Math.cos(an) * sp, 0.08 + Math.pow(rnd(), 2.4) * 0.6, false,
                            false);
                }
                if (king) {
                    OpmFx.shock(c, 6, 0xFFD0D0, 1.1f);
                    for (int i = 0; i < 420; i++) {
                        double an = (rnd() - 0.5) * 1.3, sp = 18 + rnd() * 40;
                        deb(g0, (rnd() - 0.5) * 3, 0.2, 2 + rnd() * 14, Math.sin(an) * sp, 4 + rnd() * 12, Math.cos(an) * sp, 0.1 + Math.pow(rnd(), 2.4) * 0.8,
                                rnd() < 0.3, false);
                    }
                    m.holeT = NOW;
                }
            }
            if (u < 0.3) {
                extendTo(a, 1, Math.min(4, (u - 0.07) * 30));
                m.L = a.L;
            } else {
                double k = smooth((u - 0.3) / 0.35);
                a.from = shoulder();
                a.aim = m.lastC != null ? m.lastC.add(0, 0.05, 0) : tAim();
                a.L = lerp(m.L, 0.3, k);
            }
        }
        if (king && m.holeT >= 0) {
            double ht = (NOW - m.holeT) / 20, fade = 1 - smooth((U - (END[3][3] - 1)) / 1);
            Frame f = Frame.of(m.from0, m.look);
            OpmFx.clouds(f, 0, 240, 260 * (1 - Math.exp(-ht * 1.4)) * fade, TIME, CAM);
        }
        if (u < 1.2) {
            drawArm(a);
            B.armOn = true;
        }
    }

    // G5: Bajrang Gun (a cartoon)
    private static void bajrang(Move m, Arm a) {
        double u = U, T1 = 0.3;
        collar(1);
        a.toon = 1;
        a.n = 3;
        a.infl = 3.2;
        a.from = shoulder();
        Vec3 to = C.add(0, 2.2, 0);
        if (u < T1) {
            double k = smooth(u / T1);
            a.fs = lerp(10, 15, k);
            a.amp = lerp(0.35, 1.2, Math.sin(k * Math.PI));
            a.ph = k * 14;
            a.aim = lerp(UP(1.5, 2.2, -6), to, k).add(0, Math.sin(k * Math.PI) * 6, 0);
            a.L = lerp(6.7, a.from.distanceTo(to) - 2.6, k);
            m.L = a.L;
        } else {
            if (at(T1)) {
                OpmFx.flash(C, 3);
                impact(C, I5, 0xFFFFFF);
                OpmFx.shake(C, 2.4);
                squash(PANCAKE);
                OpmFx.shock(TP.add(0, 1, 0), 3.2f, 0xFFFFFF, 0.7f);
                OpmFx.lines(NOW);
                say(m.id, "BOING!", true);
                for (int i = 0; i < 300; i++) {
                    double g = rnd() * 6.283, r = 1 + rnd() * 6, sp = 3 + rnd() * 8;
                    deb(TG, Math.cos(g) * r, 0.3, Math.sin(g) * r, Math.cos(g) * sp, 9 + rnd() * 16, Math.sin(g) * sp, 0.12 + Math.pow(rnd(), 2) * 0.55, false, true);
                }
            }
            a.aim = to;
            a.L = m.L;
            a.fs = 15;
            if (u < T1 + 0.55) {
                a.amp = 0.5 * Math.exp(-(u - T1) * 5) * Math.sin((u - T1) * 40);
            } else {
                double k = smooth((u - T1 - 0.55) / 0.5);
                if (k >= 1) {
                    shishishi(m, u > T1 + 0.9);
                    return;
                }
                a.fs = lerp(15, 1, k);
                a.infl = lerp(3.2, 1, k);
                a.L = lerp(m.L, 0.9, k);
                a.amp = 0.4 * (1 - k) * Math.sin(u * 25);
            }
        }
        shishishi(m, u > T1 + 0.9);
        drawArm(a);
        B.armOn = true;
    }

    private static void shishishi(Move m, boolean now) {
        if (now && !m.said2) {
            m.said2 = true;
            say(m.id, "Shishishi!", false);
        }
    }

    // ---------- X: Gatling ----------

    private static void gatling(Move m, int lv) {
        double u = U;
        double[] P = GP[lv];
        double dur = P[5];
        B.hideArms = u < dur + 0.3;
        if (lv == 1) {
            steamG2(1, 1.6);
        }
        if (lv == 3) {
            steamG4(0.85, 2, 0.25 * Math.abs(Math.sin(u * 7.5)));
        }
        if (lv == 4) {
            collar(1);
        }
        // the arms that the rate says are out by now
        if (u < dur) {
            int want = (int) Math.floor(u * P[0]);
            while (m.spawned < want) {
                m.spawned++;
                Gat g = new Gat();
                g.born = m.start + m.spawned / P[0] * 20;
                g.life = (float) (P[1] * (0.8 + rnd() * 0.45));
                g.side = rnd() < 0.5 ? -1 : 1;
                g.sh = new Vec3(g.side * (0.3 + rnd() * 0.15), 0.55 + rnd() * 0.3, 0.05 + rnd() * 0.15);
                g.aim = C.add(D((rnd() - 0.5) * P[4], (rnd() - 0.35) * P[4] * 0.9, -0.3));
                g.fs = (float) (P[3] * (0.85 + rnd() * 0.3));
                g.infl = (float) (lv == 1 ? 1.5 + rnd() * 0.6 : P[2] * (0.9 + rnd() * 0.2));
                g.ph = (float) (rnd() * 6);
                g.n = (float) (1.5 + rnd() * 2);
                g.fist = lv != 1;
                m.gats.add(g);
            }
        }
        for (Iterator<Gat> it = m.gats.iterator(); it.hasNext(); ) {
            Gat g = it.next();
            double age = (NOW - g.born) / 20, k = age / g.life;
            if (k >= 1) {
                if (!g.hit) {
                    gatHit(m, lv, g.aim.add(D(0, 0, -0.1 * g.fs)));
                }
                it.remove();
                continue;
            }
            if (k < 0) {
                continue;
            }
            Arm a = look(lv, 1);
            a.mesh = LuffyMesh.Mesh.ARM48;
            a.thumb = false;
            a.from = UP(g.sh.x, g.sh.y, g.sh.z);
            a.aim = g.aim;
            double full = Math.max(0.6, a.from.distanceTo(g.aim) - 0.15 * g.fs), ext = k < 0.42 ? smooth(k / 0.42) : 1 - smooth((k - 0.42) / 0.58);
            a.L = Math.max(0.35, full * ext);
            a.fs = g.fs;
            a.infl = g.infl;
            a.n = g.n;
            a.amp = (lv == 4 ? 0.5 : lv == 1 ? 0.3 : 0.12) * Math.sin(k * Math.PI);
            a.ph = g.ph + age * (lv == 4 ? 30 : 12);
            a.fist = g.fist;
            a.blur = lv == 1 ? 1 : 0;
            drawArm(a);
            if (!g.hit && k >= 0.42) {
                g.hit = true;
                gatHit(m, lv, g.aim.add(D(0, 0, -0.1 * g.fs)));
            }
        }
        if (u >= dur && !m.fin) {
            m.fin = true;
            Vec3 c = C;
            switch (lv) {
                case 0 -> {
                    OpmFx.flash(c, 0.6f);
                    OpmFx.shake(c, 0.7);
                    OpmFx.lines(NOW);
                }
                case 1 -> {
                    jetLine(shoulder(), c, 1.3, 0, false);
                    impact(c, I3, 0xFFD0D0);
                    OpmFx.shake(c, 1.3);
                    OpmFx.shock(c, 1.6f, 0xFFD8D8, 0.45f);
                    OpmFx.lines(NOW);
                }
                case 2 -> {
                    OpmFx.flash(c, 2.4f);
                    impact(c, I5, 0xFFFFFF);
                    OpmFx.shake(c, 2.4);
                    OpmFx.ring(TG, 13, 1);
                    squash(BURY2);
                    for (int i = 0; i < 300; i++) {
                        double g = rnd() * 6.283, r = 0.6 + rnd() * 3.4, sp = 4 + rnd() * 11;
                        deb(TG, Math.cos(g) * r, 0.2, Math.sin(g) * r, Math.cos(g) * sp * 0.7, 5 + rnd() * 13, Math.sin(g) * sp * 0.7,
                                0.1 + Math.pow(rnd(), 2.2) * 0.7, rnd() < 0.5, false);
                    }
                }
                case 3 -> {
                    OpmFx.flash(c, 2.2f);
                    impact(c, I5, 0xFFFFFF);
                    OpmFx.shake(c, 3);
                    OpmFx.shock(c, 4, 0xFFE2E8, 0.8f);
                    OpmFx.lines(NOW);
                }
                default -> {
                    OpmFx.flash(c, 3);
                    impact(c, I3, 0xFFFFFF);
                    OpmFx.shake(c, 2.2);
                    say(m.id, "BOING!", true);
                    if (TGT != null) {
                        SQUASH.remove(TGT.getId());
                    }
                }
            }
        }
        if (lv == 4) {
            shishishi(m, u > dur + 0.6);
        }
    }

    private static void gatHit(Move m, int lv, Vec3 p) {
        switch (lv) {
            case 0 -> {
                OpmFx.flash(p, 0.28f);
                OpmFx.shake(p, 0.2);
            }
            case 1 -> {
                Vec3 dir = p.subtract(shoulder()).normalize();
                burst(p, dir, 0.8 + rnd() * 0.8, 0.15, 0, 9 + (int) (rnd() * 6), 0, 0, 0xC8DEFA, 0);
                if (rnd() < 0.3) {
                    burst(p.add(dir.scale(-2 - rnd() * 3)), dir, 1 + rnd() * 0.6, 0.14, 1, 0, 0.3, 0, 0xC8DEFA, 0);
                }
                OpmFx.shake(p, 0.25);
            }
            case 2 -> {
                OpmFx.flash(p, 1.3f);
                OpmFx.shake(p, 0.9);
                for (int i = 0; i < 26; i++) {
                    double g = rnd() * 6.283, sp = 3 + rnd() * 7;
                    deb(TG, Math.cos(g) * 1.2, 0.2, Math.sin(g) * 1.2, Math.cos(g) * sp, 4 + rnd() * 8, Math.sin(g) * sp, 0.1 + Math.pow(rnd(), 2) * 0.5, true, false);
                }
            }
            case 3 -> {
                OpmFx.flash(p, 1.1f);
                OpmFx.shake(p, 0.8);
                if (rnd() < 0.35) {
                    OpmFx.shock(p, 1.1f, 0xFFE2E8, 0.25f);
                }
                for (int i = 0; i < 12; i++) {
                    double g = rnd() * 6.283, sp = 4 + rnd() * 10;
                    deb(p.add(0, -0.5, 0), 0, 0, 0, Math.cos(g) * sp, 2 + rnd() * 6, Math.abs(Math.sin(g)) * sp, 0.06 + Math.pow(rnd(), 2) * 0.3, false, false);
                }
            }
            default -> {
                OpmFx.flash(p, 1.5f);
                OpmFx.shake(p, 0.6);
                squash(GAT5);
                for (int i = 0; i < 14; i++) {
                    double g = rnd() * 6.283, r = 1 + rnd() * 4, sp = 2 + rnd() * 5;
                    deb(TG, Math.cos(g) * r, 0.3, Math.sin(g) * r, Math.cos(g) * sp, 7 + rnd() * 10, Math.sin(g) * sp, 0.1 + Math.pow(rnd(), 2) * 0.4, false, true);
                }
            }
        }
    }

    // ---------- C: Bazooka ----------

    // one open hand of the Bazooka (flat)
    private static Arm hand(int lv, Vec3 sh, Vec3 aim, double L, double fs, double infl, double amp, double ph) {
        Arm a = look(lv, 1);
        a.mesh = LuffyMesh.Mesh.ARM48;
        a.from = sh;
        a.aim = aim;
        a.L = L;
        a.infl = infl;
        a.amp = amp;
        a.ph = ph;
        a.fs = fs;
        a.off = 0.06;
        a.fx = 1.15;
        a.fy = 1.25;
        a.fz = 0.45;
        a.thumb = false;
        return a;
    }

    private static void bazooka(Move m, int lv) {
        double u = U;
        double[] P = BP[lv];
        double T1 = P[1], T2 = P[1] + P[2];
        B.hideArms = true;
        Vec3 c = C;
        boolean any = false;
        for (int side = -1; side <= 1; side += 2) {
            Vec3 sh = UP(side * 0.37, 0.66, 0.05);
            Vec3 backAim = UP(side * (0.9 + 0.3 * lv), 0.7, -6), hitAim = c.add(D(side * 0.22 * Math.max(1, P[4] * 0.35), 0, 0));
            double full = sh.distanceTo(hitAim) - 0.06 * P[4], wob = 0.15;
            Arm a;
            if (u < T1) {
                double k = smooth(u / T1);
                a = hand(lv, sh, backAim, 0.75 + (P[0] - 0.75) * k, lerp(1, P[4], k), lerp(1, P[3], k), wob * k, u * 18);
            } else if (u < T2) {
                double k = Math.pow((u - T1) / P[2], 1.5);
                a = hand(lv, sh, lerp(backAim, hitAim, k), lerp(P[0], full, k), P[4], P[3], wob * (1 - k), u * 18);
            } else if (u < T2 + 0.16) {
                a = hand(lv, sh, hitAim, full, P[4], P[3], 0, 0);
            } else {
                double k = smooth((u - T2 - 0.16) / 0.4);
                if (k >= 1) {
                    continue;
                }
                a = hand(lv, sh, hitAim, lerp(full, 0.75, 1 - Math.pow(1 - k, 2)), lerp(P[4], 1, k), lerp(P[3], 1, k), 0.5 * Math.exp(-k * 3) * Math.sin(k * 16),
                        k * 20);
            }
            drawArm(a);
            any = true;
        }
        B.hideArms = any;
        if (at(T2)) {
            bazImpact(lv, c);
        }
    }

    private static void bazImpact(int lv, Vec3 c) {
        if (lv == 0) {
            OpmFx.flash(c.add(D(0, 0, -0.4)), 0.7f);
            OpmFx.shake(c, 0.9);
            OpmFx.shock(c, 1.1f, 0xFFF2D6, 0.4f);
            OpmFx.lines(NOW);
        } else if (lv == 2) {
            OpmFx.flash(c, 2.6f);
            impact(c, I5, 0xFFFFFF);
            OpmFx.shake(c, 2.6);
            OpmFx.shock(c, 3.2f, 0xFFE8C8, 0.7f);
            OpmFx.lines(NOW);
            for (int i = 0; i < 220; i++) {
                double g = (rnd() - 0.5) * 1.6, sp = 6 + rnd() * 16;
                deb(TG, (rnd() - 0.5) * 3, 0.2, rnd() * 3, Math.sin(g) * sp * 0.5, 4 + rnd() * 10, Math.cos(g) * sp, 0.1 + Math.pow(rnd(), 2.2) * 0.6, rnd() < 0.4,
                        false);
            }
        } else if (lv == 3) {
            OpmFx.flash(c, 2.4f);
            impact(c, I7, 0xFFFFFF);
            OpmFx.shake(c, 3.2);
            OpmFx.shock(c, 4.5f, 0xFFE2E8, 0.9f);
            OpmFx.lines(NOW);
            Vec3 g0 = new Vec3(FEET.x, groundY(FEET.x, FEET.z), FEET.z);
            OpmFx.ring(g0, 10, 0.8f);
            for (int i = 0; i < 240; i++) {
                double g = (rnd() - 0.5) * 1.2, sp = 14 + rnd() * 30;
                deb(g0, (rnd() - 0.5) * 2, 0.2, 2 + rnd() * 8, Math.sin(g) * sp, 3 + rnd() * 9, Math.cos(g) * sp, 0.08 + Math.pow(rnd(), 2.4) * 0.6, false, false);
            }
        }
    }

    // G2: Jet Bazooka: the hands go and come back too fast to see; what comes out is the air
    private static void jetBazooka(Move m) {
        double HT = 0.1;
        steamG2(1, 1.5);
        Vec3 c = C;
        if (at(HT)) {
            for (int s = -1; s <= 1; s += 2) {
                jetLine(UP(s * 0.37, 0.6, 0.8), c.add(D(s * 0.25, 0, 0)), 0.85, 0, false);
            }
            burst(c, D(0, 0, 1), 4.4, 0.34, 0, 20, 0, 0.035, 0xC8DEFA, 0);
        }
        if (at(HT + 0.04)) {
            impact(c, I3, 0xFFD0D0);
            OpmFx.shake(c, 1.6);
            OpmFx.shock(c, 1.9f, 0xFFD8D8, 0.45f);
            OpmFx.lines(NOW);
        }
    }

    // G4: Leo Bazooka: each arm folds in the air in three joints, opens to the sides and comes diving at the target
    private static void leo(Move m) {
        double u = U, T0 = 0.16, TE = 0.34, HT = T0 + TE;
        double[] P = BP[3];
        steamG4(0.85, 2, 0.2 * Math.abs(Math.sin(u * 7.5)));
        Vec3 c = C;
        double s = u < T0 ? 0.03 : u < HT ? 0.03 + 0.97 * Math.pow((u - T0) / TE, 1.25) : u < HT + 0.22 ? 1 : 1 - smooth((u - HT - 0.22) / 0.4);
        if (u < HT + 0.62) {
            B.hideArms = true;
            for (int ai = 0; ai < 2; ai++) {
                int side = ai == 0 ? -1 : 1;
                Vec3 sh = UP(side * 0.37, 0.66, 0.05);
                double sw = Math.sin(u * 9 + ai * 2) * 0.6;
                Vec3[] pts = {sh, sh.add(D(side * 3.0, 1.4 + sw * 0.3, -1.6)), sh.add(D(side * (6.2 + sw), 5.2, 1.4)), sh.add(D(side * (3.6 - sw), 7.6, 6.4)),
                        c.add(D(side * 0.5, 0.25, -0.65))};
                double[] len = new double[4], tot = new double[5];
                for (int i = 0; i < 4; i++) {
                    len[i] = pts[i].distanceTo(pts[i + 1]);
                    tot[i + 1] = tot[i] + len[i];
                }
                double d = Math.max(0.5, s * tot[4]);
                for (int i = 0; i < 4; i++) {
                    if (d <= tot[i]) {
                        break;
                    }
                    double L = Math.min(len[i], d - tot[i]);
                    boolean tip = d <= tot[i + 1] || i == 3;
                    Arm a = hand(3, pts[i], pts[i + 1], L, P[4], P[3], 0.08, u * 10 + i);
                    a.n = 1.5;
                    a.fist = tip;
                    drawArm(a);
                    if (i > 0) {
                        double r = 2 * 0.115 * P[3] * 1.3;
                        solidBox(lookAt(pts[i], pts[i + 1], CAM), 0, 0, 0, r, r, r, a.col, false);
                    }
                    if (tip) {
                        break;
                    }
                }
            }
        }
        if (at(HT)) {
            bazImpact(3, c);
        }
    }

    // G5: the Nika's clap: the arms open to the sides of the target and clap on it; the ground rings
    private static void clap(Move m) {
        double u = U, T1 = 0.34, T2 = 0.46, fs = 8;
        collar(1);
        Vec3 c = C;
        double sx, lift, amp;
        if (u < T1) {
            double k = smooth(u / T1);
            sx = lerp(0.8, 9, k);
            lift = 3 * Math.sin(k * Math.PI * 0.5);
            amp = 0.7 * k;
        } else if (u < T2) {
            double k = Math.pow((u - T1) / (T2 - T1), 2.2);
            sx = lerp(9, 0.75, k);
            lift = lerp(3, 0.2, k);
            amp = 0.7 * (1 - k);
        } else if (u < T2 + 0.5) {
            double w = u - T2;
            sx = 0.75 + 0.25 * Math.sin(w * 34) * Math.exp(-w * 7);
            lift = 0.2;
            amp = 0.15 * Math.exp(-w * 4);
        } else {
            double k = smooth((u - T2 - 0.5) / 0.45);
            sx = lerp(0.75, 6, k);
            lift = 0.2 + 1.5 * k;
            amp = 0.5 * (1 - k);
        }
        if (u < T2 + 0.95) {
            B.hideArms = true;
            for (int side = -1; side <= 1; side += 2) {
                Vec3 sh = UP(side * 0.37, 0.66, 0.05);
                double back = u >= T2 + 0.5 ? smooth((u - T2 - 0.5) / 0.45) : 0;
                Vec3 aim = lerp(c.add(D(side * sx, lift, 0)), sh.add(D(side * 0.6, 0, 0.6)), back * back);
                double L = Math.max(0.6, sh.distanceTo(aim) - 0.3), pf = fs * (1 - 0.8 * back);
                Arm a = hand(4, sh, aim, L, pf, 2.6, amp, u * 16 + side);
                a.n = 2.5;
                a.off = 0.15;
                a.fx = 0.4;
                a.fy = 1.25;
                a.fz = 1.05;
                drawArm(a);
            }
        }
        if (at(T2)) {
            impact(c, I5, 0xFFFFFF);
            OpmFx.shake(c, 3.2);
            OpmFx.shock(c, 4, 0xFFFFFF, 1.0f);
            OpmFx.lines(NOW);
            say(m.id, "CLAP!!", true);
            OpmFx.ring(TG, 24, 1.3f);
            squash(CLAP);
            Vec3 up = new Vec3(0, 1, 0);
            burst(c, up, 7.5, 0.4, 0, 24, 0, 0, 0xC8DEFA, 0);
            burst(c, up, 12, 0.5, 0, 34, 0.65, 0.04, 0xC8DEFA, 0);
            for (int s = -1; s <= 1; s += 2) {
                for (int i = 0; i < 3; i++) {
                    burst(c.add(0, -0.4 + i * 0.4, 0), new Vec3(0, s, 0), 3 + i, 0.4, 1.1, 0, 0.3, 0.02 + i * 0.03, 0xC8DEFA, 4 + 2 * i);
                }
            }
            for (int i = 0; i < 420; i++) {
                double g = rnd() * 6.283, r = 1.5 + rnd() * 16, sp = 1.5 + rnd() * 4;
                deb(TG, Math.cos(g) * r, 0.3, Math.sin(g) * r, Math.cos(g) * sp, 7 + rnd() * 13 - r * 0.25, Math.sin(g) * sp, 0.12 + Math.pow(rnd(), 2) * 0.55, false,
                        true);
            }
        }
        shishishi(m, u > T2 + 0.5);
    }

    // ---------- V: one ult a Gear ----------

    private static void ult(Move m, int lv) {
        double u = U;
        switch (lv) {
            case 0 -> {
                // Rifle: the twisted arm unwinds spinning like a drill
                Arm a = look(0, 0);
                a.infl = 1.5;
                double T1 = 0.13;
                if (u < T1) {
                    double k = smooth(u / T1);
                    extendTo(a, k * 0.98 + 0.02, 0);
                    a.tw = 16 * (1 - 0.5 * k);
                    a.hx = 0.07 * (1 - k);
                    a.spin = TIME * 60;
                } else {
                    if (at(T1)) {
                        Vec3 c = C, dir = c.subtract(shoulder()).normalize();
                        OpmFx.flash(c.add(D(0, 0, -0.4)), 0.6f);
                        OpmFx.shake(c, 1.1);
                        OpmFx.shock(c, 1.2f, 0xFFF2D6, 0.45f);
                        OpmFx.lines(NOW);
                        for (int i = 0; i < 7; i++) {
                            double an = i / 7.0 * 6.283;
                            burst(c, D(Math.cos(an), Math.sin(an), 0.4), 1.6 + rnd() * 0.6, 0.26, 0.8, 0, 0.35, i * 0.012, 0xEAD8C0, 1.6);
                        }
                        burst(c, dir, 2.4, 0.3, 0, 9, 0, 0, 0xEAD8C0, 0);
                    }
                    extendTo(a, 1, 0);
                    if (u < T1 + 0.22) {
                        a.tw = 8 * (1 - smooth((u - T1) / 0.22));
                        a.spin = TIME * 60;
                        m.L = a.L;
                    } else if (!snapBack(a, u - T1 - 0.22, 0.45, m.L)) {
                        return;
                    }
                }
                drawArm(a);
                B.armOn = true;
            }
            case 1 -> mergulho(m);
            case 2 -> fusen(m);
            case 3 -> kongGun(m, look(3, 1), true);
            default -> kaminari(m);
        }
    }

    // G2: Jet Mergulho: gone in a burst of air, upside down over the target, a punch straight down into the ground
    private static void mergulho(Move m) {
        double u = U, T1 = 0.05, T2 = 0.24, T3 = 0.3;
        Vec3 c = TP;
        if (!m.vanish) {
            m.vanish = true;
            Vec3 o = m.from0.add(0, 1, 0), up = c.add(0, 7, 0).add(D(0, 0, -0.3)).subtract(o);
            burst(o, up, 2.4, 0.24, 0, 11, 0, 0, 0xC8DEFA, 0);
            for (int i = 1; i <= 4; i++) {
                burst(o.add(up.scale(i / 5.0)), up, 1.6 + 0.3 * i, 0.2, 1, 0, 0.3, 0.008 * i, 0xC8DEFA, 0);
            }
            Vec3 g0 = new Vec3(m.from0.x, groundY(m.from0.x, m.from0.z), m.from0.z);
            for (int i = 0; i < 40; i++) {
                double g = rnd() * 6.283, sp = 2 + rnd() * 5;
                deb(g0, Math.cos(g) * 0.5, 0.1, Math.sin(g) * 0.5, Math.cos(g) * sp, 2 + rnd() * 4, Math.sin(g) * sp, 0.06 + rnd() * 0.12, false, false);
            }
        }
        Vec3 down = new Vec3(0, -1, 0);
        double topY = 8.2;
        if (at(T1)) {
            burst(c.add(0, topY - 1.4, 0), down, 2.2, 0.2, 0, 13, 0, 0, 0xC8DEFA, 0);
        }
        if (at(T2)) {
            for (int i = 0; i < 4; i++) {
                burst(c.add(0, topY - 2 - i * 0.9, 0), down, 1.2 + 0.4 * i, 0.16, 1.05, 0, 0.3, 0.01 * i, 0xC8DEFA, 0);
            }
        }
        if (at(T3)) {
            Vec3 g = new Vec3(c.x, TG.y + 0.2, c.z);
            impact(c, I5, 0xFFD0D0);
            OpmFx.shake(c, 2.6);
            OpmFx.shock(c.add(0, 1, 0), 2.6f, 0xFFD8D8, 0.6f);
            OpmFx.lines(NOW);
            burst(c.add(0, 2.3, 0), down, 3.4, 0.3, 0, 18, 0, 0, 0xC8DEFA, 0);
            burst(g, down, 7, 0.4, 0, 30, 0.6, 0.03, 0xC8DEFA, 0);
            burst(g, down, 4.6, 0.32, 0, 20, 0, 0.015, 0xC8DEFA, 0);
            for (int s = -1; s <= 1; s += 2) {
                burst(g.add(D(s * 1.5, 0.4, 0)), D(s, 0, 0), 2.6, 0.34, 1, 0, 0.35, 0.02, 0xC8DEFA, 5);
            }
            OpmFx.ring(TG, 15, 1);
            squash(BURY);
            for (int i = 0; i < 340; i++) {
                double an = rnd() * 6.283, r = 0.6 + rnd() * 3.4, sp = 4 + rnd() * 12;
                deb(TG, Math.cos(an) * r, 0.2, Math.sin(an) * r, Math.cos(an) * sp * 0.7, 5 + rnd() * 14, Math.sin(an) * sp * 0.7, 0.1 + Math.pow(rnd(), 2.2) * 0.7,
                        rnd() < 0.5, false);
            }
        }
        // the steam goes with him; his body: gone for a moment, then upside down, then a flip back
        Entity e = Minecraft.getInstance().level.getEntity(m.id);
        Vec3 at = e != null ? e.position().add(0, 1, 0) : FEET.add(0, 1, 0);
        double flipK = u < T3 + 0.45 ? 0 : smooth((u - T3 - 0.45) / 0.5);
        steam(at, 0.62, 1.05, 0.45, u < T3 + 0.45 ? 1 : 1 - 0.6 * flipK, 1.5, 1, 0.84f, 0.84f);
        if (u >= 0.02 && u < T1) {
            B.hidden = true;
        } else if (u >= T1) {
            B.flip = flipK;
        }
    }

    // G3: Fusen: a giant balloon; whatever is thrown at him sinks into the rubber and goes back
    private static void fusen(Move m) {
        double u = U, T0 = 0.24;
        double inf = u < T0 ? 0.22 + 0.78 * smooth(u / T0) + 0.12 * Math.sin(smooth(u / T0) * Math.PI) : u < 1.25 ? 1 : 1 - smooth((u - 1.25) / 0.25);
        double sq = 0, h = (NOW - B.fusenHit) / 20;
        if (h >= 0 && h < 0.22) {
            sq = Math.sin(h / 0.22 * Math.PI);
        } else if (h >= 0.22 && h < 0.34) {
            sq = -0.35 * Math.sin((h - 0.22) / 0.12 * Math.PI);
        } else if (h >= 0.34 && h < 1.24) {
            double w = h - 0.34;
            sq = -0.18 * Math.exp(-w * 5) * Math.sin(w * 30);
        }
        B.lift = 1.15 * Math.min(1, inf);
        B.balloon = inf;
        balloon(inf, sq);
        if (at(1.25)) {
            Vec3 bc = W(0, 0.75 + B.lift + 0.42, 0);
            for (int i = 0; i < 3; i++) {
                burst(bc.add(D(0, 0, -1)), D(0, 0, -1), 1.6 + i * 0.5, 0.22, 1, 0, 0.3, i * 0.03, 0xE8E2D8, 3);
            }
        }
    }

    // the balloon on his belly (and the vest's two halves over it); sq squashes it where something sank in
    private static void balloon(double inf, double sq) {
        if (inf <= 0.02) {
            return;
        }
        double Rb = 1.75 * inf;
        Vec3 bc = W(0, 0.75 + B.lift + 0.42, 0);
        Matrix4f m = basis(bc, CAM).scale((float) (Rb * (1 + 0.2 * sq)), (float) (Rb * (1 + 0.12 * sq)), (float) (Rb * (1 - 0.35 * sq)));
        LuffyMesh.P skin = LuffyMesh.p(LuffyMesh.SOLID);
        skin.col = rgb(SKIN);
        skin.glow = false;
        LuffyMesh.queue(LuffyMesh.Mesh.SPHERE, m, skin, LuffyMesh.FRONT, 8);
        LuffyMesh.P vest = LuffyMesh.p(LuffyMesh.SOLID);
        vest.col = rgb(VEST);
        vest.glow = false;
        LuffyMesh.queue(LuffyMesh.Mesh.VEST_L, m, vest, LuffyMesh.BOTH, 8);
        LuffyMesh.queue(LuffyMesh.Mesh.VEST_R, m, vest, LuffyMesh.BOTH, 8);
        solidBox(m, 0, -0.05, 1.0, 0.3, 0.3, 0.04, BAND, false);
    }

    // G5: Kaminari: he pulls the lightning from the clouds, it turns to rubber in his hand and he throws it
    private static void kaminari(Move m) {
        double u = U, T1 = 0.16, T2 = 0.62, T3 = 1.0, T4 = 1.13;
        collar(1);
        Vec3 sh = shoulder(), c = C;
        double storm = u < T2 ? 1 : 1 - smooth((u - T2) / 0.4);
        OpmFx.storm(FEET, (float) storm);
        Arm a = look(4, 1);
        a.n = 4;
        a.from = sh;
        boolean armOn = true;
        if (u < T1) {
            a.aim = UP(0.9, 60, 1.5);
            a.L = 77.2;
            a.fs = 4;
            a.infl = 2.2;
            a.amp = 0.9;
            a.ph = TIME * 5;
            Vec3 hand = sh.add(a.aim.subtract(sh).normalize().scale(a.L + 0.5 * a.fs));
            bolt(0, hand.add(D(4, 60, -3)), hand, 0.9, 5, 0, 0, 18, false, 14, true);
            bolt(1, hand, sh.add(0, 1.2, 0), 0.55, 2.2, 0, 0, 40, false, 14, true);
            bolt(2, hand.add(D(-14, 40, 6)), hand.add(D(-2, 8, 1)), 0.4, 3, 0, 0, 12, false, 14, true);
            if (!m.hit) {
                m.hit = true;
                impact(FEET, I3, 0xFFF4A0);
                OpmFx.shake(FEET, 1.6);
                OpmFx.whiteCut(FEET, NOW);
            }
        } else if (u < T2) {
            double k = smooth((u - T1) / (T2 - T1));
            a.aim = UP(0.9, 60, 1.5 - 1.5 * k);
            a.L = lerp(77.2, 1.6, Math.pow(k, 0.7));
            a.fs = lerp(4, 1.6, k);
            a.infl = lerp(2.2, 1.4, k);
            a.amp = 0.9 * (1 - k) + 0.2;
            a.ph = TIME * 8;
            Vec3 hand = sh.add(a.aim.subtract(sh).normalize().scale(a.L + 0.5 * a.fs));
            double len = lerp(30, 7, k);
            bolt(0, hand, hand.add(0, len, 0), lerp(0.9, 0.55, k), lerp(4, 0.35, k), 1.4 * k, 0, lerp(18, 6, k), k > 0.3, 18, false);
            if (u > 0.4 && !m.said1) {
                m.said1 = true;
                say(m.id, "boing boing...", false);
            }
        } else if (u < T3) {
            double k = smooth((u - T2) / (T3 - T2));
            a.aim = UP(0.6, 3, -1.2 * k);
            a.L = 1.4;
            a.fs = 1.6;
            a.amp = 0.15;
            Vec3 hand = sh.add(a.aim.subtract(sh).normalize().scale(a.L + 0.5 * a.fs)), dc = c.subtract(hand).normalize();
            Vec3 tip = hand.add(dc.scale(7)).add(0, 1.6 * (1 - k), 0);
            bolt(0, hand.add(dc.scale(-2.5)), tip, 0.5, 0.35, 0.9 + 0.4 * Math.sin(u * 22), 0.8 * Math.sin(u * 17), 6, true, 26, false);
            if (!m.said2) {
                m.said2 = true;
                say(m.id, "Shishishi!", false);
            }
        } else if (u < T4) {
            double k = (u - T3) / (T4 - T3);
            a.aim = c;
            a.L = lerp(1.4, 4, smooth(k));
            a.fs = 1.6;
            Vec3 hand = sh.add(c.subtract(sh).normalize().scale(a.L)), dir = c.subtract(hand).normalize(), head = lerp(hand, c, Math.pow(k, 0.8));
            bolt(0, head.add(dir.scale(-7)), head, 0.5, 0.35, 1.1 * Math.sin(u * 30), 0.9 * Math.sin(u * 25), 6, true, 30, false);
        } else {
            double w = u - T4;
            if (at(T4)) {
                impact(c, I7, 0xFFF4A0);
                OpmFx.shake(c, 3);
                OpmFx.shock(c, 3.4f, 0xFFF2A0, 0.8f);
                OpmFx.lines(NOW);
                OpmFx.flash(c, 3);
                squash(PANCAKE_K);
                say(m.id, "BZZZT!", true);
                for (int i = 0; i < 6; i++) {
                    burst(c, D(Math.cos(i), Math.sin(i * 1.7), 0.3), 2.4 + rnd(), 0.3, 0.9, 0, 0.3, i * 0.01, 0xFFF0A0, 3);
                }
                burst(c, D(0, 0, 1), 6, 0.36, 0, 22, 0, 0, 0xFFF0A0, 0);
                for (int i = 0; i < 260; i++) {
                    double g = rnd() * 6.283, r = 1 + rnd() * 7, sp = 2 + rnd() * 7;
                    deb(TG, Math.cos(g) * r, 0.3, Math.sin(g) * r, Math.cos(g) * sp, 9 + rnd() * 16, Math.sin(g) * sp, 0.12 + Math.pow(rnd(), 2) * 0.5, false, true);
                }
            }
            if (w < 0.55) {
                // the lightning breaks in branches running over the ground (a new drawing every frame)
                double fl = Math.max(0, 1 - w / 0.55);
                for (int i = 0; i < 4; i++) {
                    double an = i * 1.57 + w * 3 + rnd() * 0.6;
                    if (rnd() < 0.8 * fl) {
                        bolt(1 + i, c, TG.add(D(Math.cos(an) * (3 + 5 * rnd()), 0.1, Math.sin(an) * (3 + 5 * rnd()))), 0.18 * fl + 0.05, 0.9, 0, 0, 10, false, 14, true);
                    }
                }
            }
            armOn = w < 0.5;
            a.aim = c;
            a.L = lerp(4, 0.9, smooth(w / 0.5));
        }
        if (armOn) {
            drawArm(a);
            B.armOn = true;
        }
    }

    private static final float[] BOLT_SEED = new float[BOLT_N];

    // the page's boltAt(i, from, to, r, zig, wob, {bend, k, toon, spd, reseed})
    private static void bolt(int i, Vec3 from, Vec3 to, double r, double zig, double wob, double bend, double k, boolean toon, double spd, boolean reseed) {
        if (reseed || BOLT_SEED[i] == 0) {
            BOLT_SEED[i] = (float) (rnd() * 10 + 0.01);
        }
        LuffyMesh.P p = LuffyMesh.p(LuffyMesh.BOLT);
        p.p0 = new float[] {(float) Math.max(0.2, from.distanceTo(to)), (float) r, (float) zig, (float) (TIME * spd)};
        p.p1 = new float[] {(float) bend, (float) wob, (float) k, toon ? 1 : 0};
        p.seed = BOLT_SEED[i];
        LuffyMesh.queue(LuffyMesh.Mesh.BOLT, lookAt(from, to, CAM), p, LuffyMesh.BOTH, 19);
    }

    // ---------- the air bursts ----------

    private static void bursts() {
        if (BURSTS.isEmpty()) {
            return;
        }
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Quaternionf rot = camera.rotation(), inv = new Quaternionf(rot).conjugate();
        for (Iterator<Burst> it = BURSTS.iterator(); it.hasNext(); ) {
            Burst b = it.next();
            double t = (NOW - b.born()) / 20;
            if (t < 0) {
                continue;
            }
            double k = t / b.life();
            if (k >= 1) {
                it.remove();
                continue;
            }
            Vec3 bp = b.pos().add(b.dir().scale(b.drift() * (1 - Math.pow(1 - k, 2))));
            float s = (float) (b.size() * (0.3 + 0.7 * (1 - Math.pow(1 - k, 2.4))));
            // the half moon points the way of the blow on the screen
            Vector3f d = inv.transform(new Vector3f((float) b.dir().x, (float) b.dir().y, (float) b.dir().z));
            float ang = (float) Math.atan2(d.y, d.x);
            Matrix4f mm = new Matrix4f().translation((float) (bp.x - CAM.x), (float) (bp.y - CAM.y), (float) (bp.z - CAM.z)).rotate(rot).rotateZ(ang).scale(s);
            LuffyMesh.P p = LuffyMesh.p(LuffyMesh.BURST);
            p.p0 = new float[] {(float) k, b.arc(), b.sp(), b.thin()};
            p.col = b.tint();
            p.seed = b.seed();
            LuffyMesh.queue(LuffyMesh.Mesh.QUAD, mm, p, LuffyMesh.BOTH, 21);
        }
    }

    // ---------- the rubber ground (G5): rings run out and the blocks ride them ----------

    private static void rubberGround(Vec3 cam, double now) {
        RUBBERS.removeIf(r -> (now - r.born()) / 20 > 3.4);
        if (RUBBERS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        BlockRenderDispatcher blocks = mc.getBlockRenderer();
        MultiBufferSource.BufferSource source = mc.renderBuffers().bufferSource();
        PoseStack pose = new PoseStack();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        int drawn = 0;
        for (Rubber rb : RUBBERS) {
            double age = (now - rb.born()) / 20, R = age * 26, wid = 5 + age * 6, fade = Math.exp(-age * 0.9) * rb.w();
            double r0 = Math.max(0, R - wid * 1.8), r1 = Math.min(48, R + wid * 1.8);
            if (fade < 0.04 || r0 >= r1) {
                continue;
            }
            int cx = Mth.floor(rb.c().x), cz = Mth.floor(rb.c().z), n = (int) Math.ceil(r1);
            for (int x = -n; x <= n && drawn < 1400; x++) {
                for (int z = -n; z <= n && drawn < 1400; z++) {
                    double r = Math.sqrt(x * x + z * z);
                    if (r < r0 || r > r1) {
                        continue;
                    }
                    int wx = cx + x, wz = cz + z;
                    double s = hash(wx, wz);
                    double env = Math.exp(-Math.pow((r - R) / wid, 2)) * fade;
                    double dy = Math.sin((r - R) * 0.55 - age * 9) * env * (1.1 + 0.6 * s);
                    if (dy < 0.12) {
                        continue;
                    }
                    int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, wx, wz) - 1;
                    at.set(wx, top, wz);
                    BlockState st = level.getBlockState(at);
                    if (st.isAir() || !st.getFluidState().isEmpty()) {
                        continue;
                    }
                    int light = LevelRenderer.getLightColor(level, at.above());
                    pose.pushPose();
                    pose.translate(wx - cam.x, top + dy - cam.y, wz - cam.z);
                    blocks.renderSingleBlock(st, pose, source, light, OverlayTexture.NO_OVERLAY);
                    if (dy > 0.55) {
                        pose.translate(0, -1, 0);
                        blocks.renderSingleBlock(st.is(Blocks.GRASS_BLOCK) ? Blocks.DIRT.defaultBlockState() : st, pose, source, light, OverlayTexture.NO_OVERLAY);
                    }
                    pose.popPose();
                    drawn++;
                }
            }
        }
        source.endBatch();
    }

    private static double hash(int x, int z) {
        long h = x * 374761393L + z * 668265263L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535.0;
    }

    // ---------- his body (drawn by the game) ----------

    private static final ResourceLocation WHITE_TEX = ResourceLocation.fromNamespaceAndPath("battlegroundsmod", "textures/outfit/white.png");
    private static final java.util.Set<Integer> PUSHED = new java.util.HashSet<>();

    private static ModelPart[] arms(PlayerModel<?> m, boolean right, boolean left) {
        List<ModelPart> l = new ArrayList<>();
        if (right) {
            l.add(m.rightArm);
            l.add(m.rightSleeve);
        }
        if (left) {
            l.add(m.leftArm);
            l.add(m.leftSleeve);
        }
        return l.toArray(new ModelPart[0]);
    }

    private static void scale(ModelPart p, float x, float y, float z) {
        p.xScale = x;
        p.yScale = y;
        p.zScale = z;
    }

    // parts hidden for this draw, with whether they were shown: hidden outright, not scaled to 0 (the animation library
    // draws the parts its own way and a 0 scale does not reach it, so his own arm stayed beside the rubber one)
    private static final Map<ModelPart, Boolean> HIDDEN = new java.util.IdentityHashMap<>();

    private static void hide(ModelPart p) {
        HIDDEN.putIfAbsent(p, p.visible);
        p.visible = false;
    }

    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        Body bd = BODIES.get(event.getEntity().getId());
        if (bd == null || event.isCanceled()) {
            return;
        }
        if (bd.hidden) {
            event.setCanceled(true);
            return;
        }
        PlayerModel<AbstractClientPlayer> m = event.getRenderer().getModel();
        HIDDEN.clear();
        double now = ScreenFx.now();
        // Boundman: the trunk blown up, short thick legs, the arm into the fist
        float b = bd.lookLv == 3 ? (float) bd.lookK : 0;
        for (ModelPart p : new ModelPart[] {m.body, m.jacket}) {
            scale(p, 1 + 0.55f * b, 1 + 0.12f * b, 1 + 0.75f * b);
        }
        scale(m.head, 1 + 0.25f * b, 1, 1 + 0.25f * b);
        scale(m.hat, 1 + 0.25f * b, 1, 1 + 0.25f * b);
        for (ModelPart p : new ModelPart[] {m.rightLeg, m.leftLeg, m.rightPants, m.leftPants}) {
            scale(p, 1 + 0.5f * b, 1 - 0.18f * b, 1 + 0.5f * b);
        }
        for (ModelPart p : arms(m, true, true)) {
            scale(p, 1 + 0.6f * b, 1, 1 + 0.6f * b);
        }
        // the rubber arm is drawn instead of his right arm; both arms out for the barrages
        for (ModelPart p : arms(m, bd.armOn || bd.hideArms, bd.hideArms)) {
            hide(p);
        }
        // the Fusen: head, arms and legs are drawn pushed out round the balloon (onRenderPlayerPost)
        if (bd.balloon > 0.02) {
            for (ModelPart p : new ModelPart[] {m.head, m.hat, m.rightArm, m.rightSleeve, m.leftArm, m.leftSleeve, m.rightLeg, m.rightPants, m.leftLeg,
                    m.leftPants}) {
                hide(p);
            }
        }
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        PUSHED.add(bd.id);
        LivingEntity e = event.getEntity();
        float yaw = Mth.rotLerp(event.getPartialTick(), e.yBodyRotO, e.yBodyRot) * Mth.DEG_TO_RAD;
        // small for a while after the G3 (the manga's cost)
        if (bd.tinyEnd > now) {
            double age = (now - bd.tinyStart) / 20, len = (bd.tinyEnd - bd.tinyStart) / 20;
            double c = smooth(age / 0.18) * (1 - smooth((age - (len - 0.3)) / 0.3));
            float s = (float) (1 - 0.58 * c);
            pose.scale(s, s, s);
        }
        if (bd.lift > 0) {
            pose.translate(0, bd.lift, 0);
        }
        // Jet Mergulho: upside down over the target, then a flip back onto his feet
        if (bd.flip >= 0) {
            double k = bd.flip;
            pose.translate(0, 0.9, 0);
            pose.mulPose(Axis.YP.rotation(-yaw));
            pose.mulPose(new Quaternionf().rotationX((float) (Math.PI + Math.PI * k)).rotateY((float) (-Math.PI * (1 - k))));
            pose.mulPose(Axis.YP.rotation(yaw));
            pose.translate(0, -0.9, 0);
        }
    }

    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        int id = event.getEntity().getId();
        if (!PUSHED.remove(id)) {
            return;
        }
        PlayerModel<AbstractClientPlayer> m = event.getRenderer().getModel();
        Body bd = BODIES.get(id);
        PoseStack pose = event.getPoseStack();
        for (ModelPart p : new ModelPart[] {m.body, m.jacket, m.head, m.hat, m.rightLeg, m.leftLeg, m.rightPants, m.leftPants, m.rightArm, m.leftArm,
                m.rightSleeve, m.leftSleeve}) {
            scale(p, 1, 1, 1);
        }
        HIDDEN.forEach((p, was) -> p.visible = was);
        HIDDEN.clear();
        if (bd != null && bd.balloon > 0.02) {
            // the Fusen: his head, arms and legs pushed out round the balloon (the page's balloonSet)
            // (the head goes 1.3 up, not the page's 1.2: the game's head is smaller and lower, so this shows the same share of it)
            float k = (float) Math.min(1, bd.balloon);
            AbstractClientPlayer player = (AbstractClientPlayer) event.getEntity();
            float yaw = Mth.rotLerp(event.getPartialTick(), player.yBodyRotO, player.yBodyRot);
            var buf = event.getMultiBufferSource().getBuffer(RenderType.entityCutoutNoCull(event.getRenderer().getTextureLocation(player)));
            int light = event.getPackedLight();
            Object[][] moved = {{m.head, 0f, 1.3f}, {m.hat, 0f, 1.3f}, {m.rightArm, 1.35f, 0.25f}, {m.rightSleeve, 1.35f, 0.25f}, {m.leftArm, -1.35f, 0.25f},
                    {m.leftSleeve, -1.35f, 0.25f}, {m.rightLeg, 0.45f, 0f}, {m.rightPants, 0.45f, 0f}, {m.leftLeg, -0.45f, 0f}, {m.leftPants, -0.45f, 0f}};
            for (Object[] o : moved) {
                ModelPart p = (ModelPart) o[0];
                pose.pushPose();
                pose.mulPose(Axis.YP.rotationDegrees(180 - yaw));
                // his right is -x here, before the model's own flip
                pose.translate(-(float) o[1] * k, (float) o[2] * k, 0);
                pose.scale(-1, -1, 1);
                pose.scale(0.9375f, 0.9375f, 0.9375f);
                pose.translate(0, -1.501, 0);
                p.render(pose, buf, light, OverlayTexture.NO_OVERLAY);
                pose.popPose();
            }
        }
        pose.popPose();
    }

    // The color of each Gear over his own skin: pink in G2, black Haki limbs in G4, all white in G5.
    public static final class Layer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        public Layer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffer, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            Body bd = BODIES.get(player.getId());
            if (bd == null || bd.lookLv == 0 || bd.lookLv == 2 || bd.lookK <= 0.01) {
                return;
            }
            PlayerModel<AbstractClientPlayer> m = getParentModel();
            var buf = buffer.getBuffer(RenderType.entityTranslucent(WHITE_TEX));
            int overlay = OverlayTexture.NO_OVERLAY;
            float k = (float) bd.lookK;
            if (bd.lookLv == 1) {
                int c = FastColor.ARGB32.color(Math.round(0.42f * k * 255), 0xF3, 0x9A, 0x92);
                m.renderToBuffer(pose, buf, light, overlay, c);
            } else if (bd.lookLv == 3) {
                int c = FastColor.ARGB32.color(Math.round(0.93f * k * 255), 0x1C, 0x17, 0x1C);
                for (ModelPart p : new ModelPart[] {m.rightArm, m.leftArm, m.rightSleeve, m.leftSleeve, m.rightLeg, m.leftLeg, m.rightPants, m.leftPants}) {
                    p.render(pose, buf, light, overlay, c);
                }
            } else if (bd.lookLv == 4) {
                int c = FastColor.ARGB32.color(Math.round(0.6f * k * 255), 0xF4, 0xF1, 0xEA);
                m.renderToBuffer(pose, buf, light, overlay, c);
            }
        }
    }

    // ---------- whoever he squashed ----------

    private static final java.util.Set<Integer> SQUASHED = new java.util.HashSet<>();

    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        if (SQUASH.isEmpty() && KNOCK.isEmpty() || event.isCanceled()) {
            return;
        }
        Squash s = SQUASH.get(event.getEntity().getId());
        if (s == null) {
            knock(event);
            return;
        }
        double q = (ScreenFx.now() - s.born()) / 20;
        double x = 1, y = 1, z = 1;
        switch (s.type()) {
            case BURY, BURY2 -> {
                double k = smooth(q / 0.06) * (1 - smooth((q - 1.6) / 0.2));
                double w = s.type() == BURY ? 1.15 : 1.2;
                x = z = lerp(1, w, k);
                y = lerp(1, s.type() == BURY ? 0.45 : 0.35, k);
            }
            case PANCAKE, PANCAKE_K -> {
                double end = s.type() == PANCAKE ? 0.9 : 0.8, grow = s.type() == PANCAKE ? 1.6 : 1.4, flat = s.type() == PANCAKE ? 0.1 : 0.12;
                if (q < end) {
                    double w = 1 + grow * smooth(q / 0.05) + 0.25 * Math.sin(q * 30) * Math.exp(-q * 4);
                    x = z = w;
                    y = lerp(1, flat, smooth(q / 0.05));
                }
            }
            case GAT5 -> {
                double w = 1 + 0.5 * Math.exp(-q * 9);
                x = z = w;
                y = 1 / (w * w) * (1 + 0.1 * Math.sin(q * 30) * Math.exp(-q * 5));
            }
            default -> {
                if (q < 0.5) {
                    x = lerp(1, 0.22, smooth(q / 0.04));
                    y = 1 + 0.2 * smooth(q / 0.04);
                    z = 1.25;
                } else {
                    double w = q - 0.5, sc = 1 + 0.6 * Math.exp(-w * 6) * Math.sin(w * 28);
                    x = sc;
                    y = 1 / sc;
                }
            }
        }
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        SQUASHED.add(event.getEntity().getId());
        // in the frame of the blow (x across it, z along it)
        float yaw = (float) Math.atan2(-s.fwd().x, s.fwd().z);
        pose.mulPose(Axis.YP.rotation(-yaw));
        pose.scale((float) x, (float) y, (float) z);
        pose.mulPose(Axis.YP.rotation(yaw));
    }

    private static void knock(RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity e = event.getEntity();
        Knock k = KNOCK.get(e.getId());
        if (k == null) {
            return;
        }
        double now = ScreenFx.now();
        boolean ground = e.onGround();
        if (!ground) {
            k.flew = true;
        } else if (k.flew && k.landed < 0) {
            k.landed = now;
        }
        if (!k.flew) {
            return;
        }
        // the axis that turns his head back toward Luffy (the page's rotation.x going negative)
        Vector3f axis = new Vector3f(0, 1, 0).cross(new Vector3f((float) -k.fwd.x, 0, (float) -k.fwd.z)).normalize();
        float h = e.getBbHeight(), w = e.getBbWidth();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        SQUASHED.add(e.getId());
        if (k.landed < 0) {
            // in the air: tumbling about its middle
            k.airAngle = Math.abs(k.spin) * (now - k.born) / 20;
            pose.translate(0, h / 2, 0);
            pose.mulPose(new Quaternionf().rotationAxis((float) k.airAngle, axis));
            pose.translate(0, -h / 2, 0);
        } else {
            // down flat, then back up at the end
            double q = (now - k.landed) / 20;
            double flat = q < 0.12 ? smooth(q / 0.12) : 1 - smooth((q - 2.3) / 0.3);
            float ang = (float) (Math.PI / 2 * flat);
            pose.translate(0, w / 2 * flat, 0);
            pose.mulPose(new Quaternionf().rotationAxis(ang, axis));
        }
    }

    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> event) {
        if (SQUASHED.remove(event.getEntity().getId())) {
            event.getPoseStack().popPose();
        }
    }

    // ---------- his screen: the G5's time and the hunger after it ----------

    public static void renderGui(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        Body bd = BODIES.get(mc.player.getId());
        if (bd == null) {
            return;
        }
        double now = ScreenFx.now();
        Font font = mc.font;
        int w = graphics.guiWidth(), h = graphics.guiHeight();
        if (bd.g5Until > now) {
            double left = (bd.g5Until - now) / 20;
            Component text = Component.translatable("hud.battlegroundsmod.luffy.g5", (int) Math.ceil(left));
            boolean blink = left < 5 && ((int) (left * 4)) % 2 == 0;
            graphics.drawString(font, text, w / 2 - font.width(text) / 2, h / 2 + 30, blink ? 0xFFFFFFFF : 0xFFF4F1EA, true);
        } else if (bd.fomeUntil > now) {
            double left = (bd.fomeUntil - now) / 20;
            Component text = Component.translatable("hud.battlegroundsmod.luffy.fome", (int) Math.ceil(left));
            graphics.drawString(font, text, w / 2 - font.width(text) / 2, h / 2 + 30, 0xFFE0A050, true);
        }
    }
}
