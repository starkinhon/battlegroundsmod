package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
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
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
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

    // the current body's skin tone and its light rim (set per body in render(); default = the page's tan)
    private static int TONE = 0xF0C09A, TONE_RIM = 0xFFE7CF;
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
        // Boundman's steam (forja/luffy-g4): where each column leaves his body this frame (set while his body is drawn),
        // his size and the page's light turned with him
        final Vec3[] steamSrc = new Vec3[COLS.length];
        int steamFrame = -1;
        double steamScale = 1;
        float[] light = {0, 1, 0};
        final List<Col> cols = new ArrayList<>();
        // Gear 5 (forja/luffy-g5): the cloud's strength this frame; standing still he sits down and bounces on his
        // backside (sitK 0 walking .. 1 bouncing), the sash's loose end
        double collarA, sitK, bounceT, stillFor, lastPh = -1;
        Vec3 lastPos;
        double[][] rib;
        long ribNs;

        Body(int id) {
            this.id = id;
        }
    }

    // One column of steam: the points it let go, the oldest first (x, y, z, vx, vy, vz, age).
    private static final class Col {
        final List<double[]> pts = new ArrayList<>();
        double acc;
    }

    // the page's columns: thickness (px), life (s), points a second, seed; from the right shoulder, the left shoulder,
    // three on the back, the elbows and the knees
    private static final double[][] COLS = {{1.5, 1.7, 18, 1.3}, {1.7, 1.9, 17, 2.9}, {1.9, 2.0, 16, 4.1}, {1.6, 1.8, 17, 5.7}, {1.3, 1.5, 19, 6.6},
            {1.5, 1.5, 18, 7.4}, {1.6, 1.6, 18, 8.8}, {1.1, 1.2, 20, 9.9}, {1.2, 1.3, 20, 11.2}};
    private static int FRAME;

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

    private record Rubber(Vec3 c, float w, double born, float size) {
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
    // The Giant: GS = his size. While his moves draw, CAM/C/TP are taken into his "small world" (shrunk about his feet),
    // the moves run at normal size there, and what they make is put back at his size (LuffyMesh.worldScale, toReal).
    private static double GS = 1;
    // the body being drawn is the local player seen in first person: what sits round his head stays off his screen
    private static boolean SELF_FP;

    private static Vec3 toSmall(Vec3 p) {
        return GS == 1 || p == null ? p : FEET.add(p.subtract(FEET).scale(1 / GS));
    }

    private static Vec3 toReal(Vec3 p) {
        return GS == 1 || p == null ? p : FEET.add(p.subtract(FEET).scale(GS));
    }
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

    // the page's shock(): a ring on the screen that pushes the picture out, each color its own amount (the rainbow edge),
    // with a soft glow; the glow follows the page's values for each power
    private static void shock(Vec3 at, float pow, int color, float dur) {
        at = toReal(at);
        pow *= (float) Math.sqrt(GS);
        if (OpmFx.near(at, 140)) {
            float glow = pow < 1.3f ? 0.06f : pow < 2.8f ? 0.1f : pow < 3.3f ? 0.12f : pow < 5 ? 0.15f : 0.2f;
            ScreenFx.shockRing(at, pow, color, dur, glow);
        }
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
        pos = toReal(pos);
        size *= GS;
        drift *= GS;
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
        Vec3 p = toReal(base.add(D(lx, 0, lz)).add(0, ly, 0));
        double vs = Math.sqrt(GS);
        OpmBlocks.Deb d = OpmBlocks.spawn(OpmBlocks.stateAt(p.x, p.z, deep), p, D(vx * vs, vy * vs, vz * vs), size * Math.min(3, GS), false, 0);
        if (d != null) {
            d.bouncy = bouncy;
        }
    }

    private static void ring(Vec3 at, double r, float a) {
        OpmFx.ring(toReal(at), (float) (r * GS), a);
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
                // the Giant's eye is 10x higher and he reaches 10x as far: without a target the move goes where he looks
                Entity self = mc.level.getEntity(id);
                double size = self instanceof LivingEntity giant && giant.getScale() > 1.5f ? giant.getScale() : 1;
                Vec3 eye = pos.add(0, 1.62 * size, 0);
                Vec3 fallback = eye.add(dir.normalize().scale(REACH[Math.min(3, key)][gear] * size));
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
                if (a > 3.5f) {
                    // back to his size
                    say(id, "...", false);
                } else if (a > 2.5f) {
                    // Gomu Gomu no Giant!
                    say(id, "Gomu Gomu no... GIANT!", true);
                    OpmFx.whiteCut(pos, now);
                    OpmFx.lines(now);
                    OpmFx.shake(pos, 2);
                } else if (a > 1.5f) {
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
                RUBBERS.add(new Rubber(pos, a, now, Math.max(1, b)));
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
        shock(feet.add(0, 1.2, 0), 0.6f + 0.2f * lv, lv == 4 ? 0xFFFFFF : 0xFFE6D0, 0.4f);
    }

    // ---------- the look of each Gear (the page's look()) ----------

    private static final class Arm {
        Vec3 from, aim;
        double L = 0.75, fs = 1, infl = 1, amp, ph, n = 2, bx = -1, ba, toon, tw, hx, blur, haki, spin, lit, litOff;
        int col = SKIN, rim = RIM;
        boolean fist = true, thumb = true, kkg;
        double fx = 1, fy = 1, fz = 1, off = 0.12;
        LuffyMesh.Mesh mesh = LuffyMesh.Mesh.ARM;
    }

    private static Arm look(int lv, double k) {
        Arm a = new Arm();
        int c = TONE;
        if (lv == 1) {
            c = mix(TONE, PINK, k);
        }
        a.col = c;
        a.rim = TONE_RIM;
        if (lv == 3) {
            a.col = mix(TONE, HAKI, k);
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
        p.p3 = new float[] {(float) a.haki, (float) a.lit, (float) a.litOff, 0};
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
        if (SELF_FP) {
            return;
        }
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

    // Boundman's steam is his own now (the columns, the collar and the elbow tails, drawn with his body)
    private static void steamG4(double alpha, double rise, double bob) {
    }

    // G5's cloud is drawn with his body now (drawNika: at the base of his neck, with its flames); this sets how strong
    private static void collar(double alpha) {
        B.collarA = Math.max(B.collarA, alpha);
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
            bd.collarA = 0;
            SELF_FP = e == mc.player && mc.options.getCameraType().isFirstPerson();
            sitIdle(bd, e, dt);
            // his rubber is his own skin: the hand's color from whatever skin the player uses
            TONE = SkinTone.of(e, SKIN);
            TONE_RIM = mix(TONE, 0xFFFFFF, 0.3);
            Vec3 feet = e.getPosition(partial);
            FEET = feet;
            GS = e instanceof LivingEntity giant && giant.getScale() > 1.5f ? giant.getScale() : 1;
            CAM = GS == 1 ? cam : feet.add(cam.subtract(feet).scale(1 / GS));
            LuffyMesh.worldScale = (float) GS;
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
        GS = 1;
        CAM = cam;
        SELF_FP = false;
        LuffyMesh.worldScale = 1;
        for (Body bd : BODIES.values()) {
            Entity e = level.getEntity(bd.id);
            columns(bd, cam, dt, e == mc.player && mc.options.getCameraType().isFirstPerson());
        }
        FRAME++;
        bursts();
        rubberGround(cam, now);
        SQUASH.values().removeIf(s -> (now - s.born()) / 20 > 2.4);
        KNOCK.values().removeIf(k -> (now - k.born) / 20 > 6 || k.landed >= 0 && (now - k.landed) / 20 > 2.6);
    }

    private static void setLook(Body bd, int lv, double k) {
        bd.lookLv = lv;
        bd.lookK = k;
        SX = lv == 3 ? 1 + 0.35 * k : 1;
        SY = lv == 3 ? 1 + 0.12 * k : 1;
        SZ = lv == 3 ? 1 + 0.3 * k : 1;
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
        if (t != null && t.isAlive() && (m.lastC == null || t.position().distanceTo(FEET) < (REACH[m.key][lv] * 1.7 + 6) * GS)) {
            TGT = t;
            TP = t.getPosition(partial);
            C = TP.add(0, t.getBbHeight() * 0.69, 0);
            m.lastC = C;
        } else {
            TGT = t != null && t.isAlive() ? t : null;
            C = m.lastC != null ? m.lastC : m.fallback;
            TP = C.subtract(0, 1.25, 0);
        }
        if (GS != 1) {
            C = toSmall(C);
            TP = toSmall(TP);
        }
        TG = new Vec3(TP.x, GS == 1 ? groundY(TP.x, TP.z) : TP.y, TP.z);
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
                        shock(C, 0.9f, 0xFFF2D6, 0.4f);
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
                    shock(C, 1.6f, 0xFFD8D8, 0.45f);
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
                        ring(TG, 14, 1.0f);
                        shock(TP.add(0, 1, 0), 3, 0xFFE8C8, 0.7f);
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
                shock(c, 4.5f, 0xFFE2E8, 0.9f);
                OpmFx.lines(NOW);
                Vec3 g0 = new Vec3(FEET.x, groundY(FEET.x, FEET.z), FEET.z);
                ring(g0, 10, 0.8f);
                for (int i = 0; i < 260; i++) {
                    double an = (rnd() - 0.5) * 1.1, sp = 14 + rnd() * 30;
                    deb(g0, (rnd() - 0.5) * 2, 0.2, 2 + rnd() * 8, Math.sin(an) * sp, 3 + rnd() * 9, Math.cos(an) * sp, 0.08 + Math.pow(rnd(), 2.4) * 0.6, false,
                            false);
                }
                if (king) {
                    shock(c, 6, 0xFFD0D0, 1.1f);
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
                shock(TP.add(0, 1, 0), 3.2f, 0xFFFFFF, 0.7f);
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
                    shock(c, 1.6f, 0xFFD8D8, 0.45f);
                    OpmFx.lines(NOW);
                }
                case 2 -> {
                    OpmFx.flash(c, 2.4f);
                    impact(c, I5, 0xFFFFFF);
                    OpmFx.shake(c, 2.4);
                    ring(TG, 13, 1);
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
                    shock(c, 4, 0xFFE2E8, 0.8f);
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
                    shock(p, 1.1f, 0xFFE2E8, 0.25f);
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
            shock(c, 1.1f, 0xFFF2D6, 0.4f);
            OpmFx.lines(NOW);
        } else if (lv == 2) {
            OpmFx.flash(c, 2.6f);
            impact(c, I5, 0xFFFFFF);
            OpmFx.shake(c, 2.6);
            shock(c, 3.2f, 0xFFE8C8, 0.7f);
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
            shock(c, 4.5f, 0xFFE2E8, 0.9f);
            OpmFx.lines(NOW);
            Vec3 g0 = new Vec3(FEET.x, groundY(FEET.x, FEET.z), FEET.z);
            ring(g0, 10, 0.8f);
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
            shock(c, 1.9f, 0xFFD8D8, 0.45f);
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
            shock(c, 4, 0xFFFFFF, 1.0f);
            OpmFx.lines(NOW);
            say(m.id, "CLAP!!", true);
            ring(TG, 24, 1.3f);
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
                        shock(c, 1.2f, 0xFFF2D6, 0.45f);
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
            shock(c.add(0, 1, 0), 2.6f, 0xFFD8D8, 0.6f);
            OpmFx.lines(NOW);
            burst(c.add(0, 2.3, 0), down, 3.4, 0.3, 0, 18, 0, 0, 0xC8DEFA, 0);
            burst(g, down, 7, 0.4, 0, 30, 0.6, 0.03, 0xC8DEFA, 0);
            burst(g, down, 4.6, 0.32, 0, 20, 0, 0.015, 0xC8DEFA, 0);
            for (int s = -1; s <= 1; s += 2) {
                burst(g.add(D(s * 1.5, 0.4, 0)), D(s, 0, 0), 2.6, 0.34, 1, 0, 0.35, 0.02, 0xC8DEFA, 5);
            }
            ring(TG, 15, 1);
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
        skin.col = rgb(TONE);
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
            a.lit = 1.6;
            a.litOff = 6;
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
            a.lit = 0.45;
            a.litOff = 1;
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
            a.lit = 0.4;
            a.litOff = 0.5;
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
                shock(c, 3.4f, 0xFFF2A0, 0.8f);
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
            // slower and narrower than the page (26 blocks/s): at Minecraft's scale the page's ring went past any spot
            // in a fifth of a second and read as nothing; this one rolls past Luffy's feet in waves you can follow
            // (the Giant's ring runs bigger: rb.size up to 3x)
            double age = (now - rb.born()) / 20, R = age * 13 * rb.size(), wid = (3 + age * 2.5) * rb.size(), fade = Math.exp(-age * 0.7) * rb.w() * Math.sqrt(rb.size());
            double r0 = Math.max(0, R - wid * 1.4), r1 = Math.min(56, R + wid * 1.4);
            if (fade < 0.04 || r0 >= r1) {
                continue;
            }
            int cx = Mth.floor(rb.c().x), cz = Mth.floor(rb.c().z), n = (int) Math.ceil(r1);
            for (int x = -n; x <= n && drawn < 12000; x++) {
                for (int z = -n; z <= n && drawn < 12000; z++) {
                    double r = Math.sqrt(x * x + z * z);
                    if (r < r0 || r > r1) {
                        continue;
                    }
                    int wx = cx + x, wz = cz + z;
                    double h = swell(cx, cz, wx, wz, age, R, wid, fade);
                    if (h < 0.1) {
                        continue;
                    }
                    // MOTION_BLOCKING: the client only keeps WORLD_SURFACE and MOTION_BLOCKING; NO_LEAVES reads the
                    // world's bottom there, so every column came out as void air and nothing was drawn
                    int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, wx, wz) - 1;
                    at.set(wx, top, wz);
                    BlockState st = level.getBlockState(at);
                    if (st.isAir() || !st.getFluidState().isEmpty()) {
                        continue;
                    }
                    int light = LevelRenderer.getLightColor(level, at.above());
                    BlockState under = st.is(Blocks.GRASS_BLOCK) ? Blocks.DIRT.defaultBlockState() : st;
                    pose.pushPose();
                    pose.translate(wx - cam.x, top + h - cam.y, wz - cam.z);
                    blocks.renderSingleBlock(st, pose, source, light, OverlayTexture.NO_OVERLAY);
                    drawn++;
                    // the column's side down to the real ground, so the swell reads as earth and not a floating slab
                    // (only as deep as the lowest neighbour column leaves showing)
                    double low = Math.min(Math.min(swell(cx, cz, wx + 1, wz, age, R, wid, fade), swell(cx, cz, wx - 1, wz, age, R, wid, fade)),
                            Math.min(swell(cx, cz, wx, wz + 1, age, R, wid, fade), swell(cx, cz, wx, wz - 1, age, R, wid, fade)));
                    int sides = Math.min(4, (int) Math.ceil(h - Math.max(0, low)));
                    for (int k = 1; k <= sides; k++) {
                        pose.translate(0, -1, 0);
                        blocks.renderSingleBlock(under, pose, source, light, OverlayTexture.NO_OVERLAY);
                        drawn++;
                    }
                    pose.popPose();
                }
            }
        }
        source.endBatch();
    }

    // The real ground can't sink, so the wave rides on a swell: the band lifts with the envelope and the page's ring
    // rolls on top of it (never below the real ground, so no hole shows through).
    private static double swell(int cx, int cz, int wx, int wz, double age, double R, double wid, double fade) {
        double dx = wx - cx, dz = wz - cz, r = Math.sqrt(dx * dx + dz * dz);
        double env = Math.exp(-Math.pow((r - R) / wid, 2)) * fade;
        double ring = Math.sin((r - R) * 0.9 - age * 9);
        return env * (1.6 + 1.3 * ring * (0.8 + 0.4 * hash(wx, wz)));
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
        // Boundman: his whole body is drawn by the Layer (his skin on the page's boxes, the haki, the steam)
        if (boundman(bd)) {
            for (ModelPart p : new ModelPart[] {m.head, m.hat, m.body, m.jacket, m.rightArm, m.rightSleeve, m.leftArm, m.leftSleeve, m.rightLeg,
                    m.rightPants, m.leftLeg, m.leftPants}) {
                hide(p);
            }
        }
        // the rubber arm is drawn instead of his right arm; both arms out for the barrages
        for (ModelPart p : arms(m, bd.armOn || bd.hideArms, bd.hideArms)) {
            hide(p);
        }
        // Gear 5 sitting: his arms and legs are drawn by the Layer in the sitting pose (setupAnim would undo ours)
        if (sitting(bd)) {
            for (ModelPart p : new ModelPart[] {m.rightArm, m.rightSleeve, m.leftArm, m.leftSleeve, m.rightLeg, m.rightPants, m.leftLeg, m.leftPants}) {
                hide(p);
            }
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
        // Gear 5 sitting (forja/luffy-g5): his hip drops to the ground, he leans back and bounces on his backside;
        // the body turns about the hip (12 px up), as the page's root
        if (sitting(bd)) {
            double K = smooth(bd.sitK), sc = e.getScale();
            double[] b = bounce(bd);
            double y = b[0], squash = b[1], flight = b[2];
            float lean = (float) (-0.55 * K + 0.12 * flight * K);
            double hip0 = 12 / 16.0 * 0.9375;
            double hipH = lerp(hip0, 2.2 / 16.0, K) + y;
            pose.translate(0, hipH * sc, 0);
            pose.mulPose(Axis.YP.rotation(-yaw));
            pose.mulPose(Axis.XP.rotation(lean));
            float sx = (float) (1 + 0.14 * squash), sy = (float) (1 - 0.2 * squash);
            pose.scale(sx, sy, sx);
            pose.mulPose(Axis.YP.rotation(yaw));
            pose.translate(0, -hip0 * sc, 0);
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
            // only the inner parts: the skin's outer layer (hat, jacket, sleeves, pants) is a bigger box that is mostly
            // see-through, and painted with a plain texture it became a solid shell over the whole body (the white cape of G5)
            ModelPart[] limbs = {m.rightArm, m.leftArm, m.rightLeg, m.leftLeg};
            if (bd.lookLv == 1) {
                int c = FastColor.ARGB32.color(Math.round(0.42f * k * 255), 0xF3, 0x9A, 0x92);
                for (ModelPart p : new ModelPart[] {m.head, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg}) {
                    p.render(pose, buf, light, overlay, c);
                }
            } else if (bd.lookLv == 3) {
                if (boundman(bd)) {
                    drawBoundman(pose, buffer, light, player, m, bd, partialTick);
                }
            } else if (bd.lookLv == 4 && RenderSystem.getVertexSorting() != VertexSorting.ORTHOGRAPHIC_Z) {
                drawNika(pose, buffer, light, player, m, bd, partialTick);
            }
        }
    }

    // What he holds is drawn at the vanilla hand, which neither Boundman's fists nor the sitting G5 arms are: there it
    // floated by his knee or hip (the Power Book, seen in the scripted test). Those poses hold nothing.
    public static boolean hidesHeld(AbstractClientPlayer player) {
        Body bd = BODIES.get(player.getId());
        return bd != null && (boundman(bd) || sitting(bd));
    }

    // The player renderer's item-in-hand layer, wrapped so it skips the frames where hidesHeld says so.
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void wrapHeldLayer(net.minecraft.client.renderer.entity.LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> renderer) {
        try {
            java.lang.reflect.Field f = net.minecraft.client.renderer.entity.LivingEntityRenderer.class.getDeclaredField("layers");
            f.setAccessible(true);
            java.util.List<RenderLayer> layers = (java.util.List<RenderLayer>) f.get(renderer);
            for (int i = 0; i < layers.size(); i++) {
                RenderLayer inner = layers.get(i);
                if (inner instanceof net.minecraft.client.renderer.entity.layers.ItemInHandLayer) {
                    layers.set(i, new HeldLayer(renderer, inner));
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ex) {
            com.starkinhon.battlegroundsmod.BattlegroundsMod.LOGGER.warn("Luffy: could not wrap the held-item layer; items show in G4/G5 poses", ex);
        }
    }

    private static final class HeldLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        private final RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> inner;

        HeldLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> inner) {
            super(parent);
            this.inner = inner;
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffer, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (!hidesHeld(player)) {
                inner.render(pose, buffer, light, player, limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch);
            }
        }
    }

    // ---------- Boundman (forja/luffy-g4, approved: "haki quadrado") ----------

    // his body is ours to draw: in the world only (the inventory's little player keeps his plain skin)
    private static boolean boundman(Body bd) {
        return bd.lookLv == 3 && bd.lookK > 0.01 && LuffyMesh.ready() && RenderSystem.getVertexSorting() != VertexSorting.ORTHOGRAPHIC_Z;
    }

    // the page's light (0.55, 0.85, 0.65), in the page's body frame
    private static final Vector3f PAGE_LIGHT = new Vector3f(0.55f, 0.85f, 0.65f).normalize();

    // The page's build() and step(): the skin on the page's boxes (barrel chest, sunk head, short thighs), haki forearms,
    // fists and shins, bouncing every 1.1 s; the steam collar and the elbow tails go with him, the columns are let go here
    // and rise in the world (columns()).
    private static void drawBoundman(PoseStack pose, MultiBufferSource buffer, int light, AbstractClientPlayer player, PlayerModel<AbstractClientPlayer> m,
            Body bd, float partial) {
        BoundmanSkin.Skin sk = BoundmanSkin.of(player);
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(sk.tex()));
        int ov = LivingEntityRenderer.getOverlayCoords(player, 0);
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double t = (player.tickCount + partial) / 20.0 + bd.id * 0.37;
        double ph = (t % 1.1) / 1.1;
        double air = Math.pow(Math.sin(ph * Math.PI), 0.75);
        double contact = Math.max(0, 1 - ph / 0.1) + Math.max(0, (ph - 0.93) / 0.07);
        float k = (float) smooth(bd.lookK);
        pose.pushPose();
        // the model's frame (y down, his front at -z, at his neck) -> the page's (y up from his feet, front at +z, in pixels)
        pose.translate(0, 1.501, 0);
        pose.mulPose(Axis.XP.rotation((float) Math.PI));
        pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
        pose.scale(0.75f + 0.25f * k, 0.75f + 0.25f * k, 0.75f + 0.25f * k);
        pose.translate(0, air * 8, 0);
        pose.scale((float) (1 + 0.06 * contact), (float) (1 - 0.08 * contact + 0.03 * air), (float) (1 + 0.06 * contact));
        Vector3f L = pose.last().pose().transformDirection(new Vector3f(PAGE_LIGHT)).normalize();
        bd.light = new float[] {L.x, L.y, L.z};
        bd.steamScale = player.getScale();
        bd.steamFrame = FRAME;
        java.util.Arrays.fill(bd.steamSrc, null);
        BoxDraw d = new BoxDraw(pose, vc, light, ov, bd.light, cam);
        // short legs: the thigh with the skin's trousers, haki shin and foot
        for (int s = -1; s <= 1; s += 2) {
            pose.pushPose();
            pose.translate(s * 4.2f, 14, 0);
            pose.mulPose(Axis.XP.rotation((s < 0 ? m.rightLeg.xRot : m.leftLeg.xRot) * 0.6f));
            pose.mulPose(Axis.ZP.rotation(s * 0.16f));
            d.skin(s < 0 ? BoundmanSkin.RLEG : sk.lleg(), 0, -2.6f, 0, 6.4f, 5.5f, 6.4f, 0, 6);
            pose.translate(0, -5.2f, 0.4f);
            pose.mulPose(Axis.XP.rotation((float) (0.12 + 0.35 * contact)));
            bd.steamSrc[s < 0 ? 7 : 8] = d.at(0, 0, -1);
            d.haki(0, -4, 0, 6, 8.5f, 6.2f, s * 3.1f);
            d.haki(0, -8.4f, 1.6f, 6.3f, 2.6f, 9.4f, s * 5.7f);
            pose.popPose();
        }
        // barrel trunk: the belly (the skin's lower rows) and a huge chest (the upper rows)
        d.skin(BoundmanSkin.BODY, 0, 15.6f, 0.3f, 15, 5.5f, 10.5f, 7, 12);
        d.skin(BoundmanSkin.BODY, 0, 24.5f, 0, 20, 13, 12.5f, 0, 9);
        // trapezius and deltoid: the chest's corner into the arm (no gap to the sky)
        for (int s = -1; s <= 1; s += 2) {
            pose.pushPose();
            pose.translate(s * 9.6f, 29.4f, 0);
            pose.mulPose(Axis.ZP.rotation(-s * 0.42f));
            d.skin(BoundmanSkin.BODY, 0, 0, 0, 9, 6, 11, 0, 4);
            pose.popPose();
            pose.pushPose();
            pose.translate(s * 12.2f, 27.6f, 0);
            pose.mulPose(Axis.ZP.rotation(s * 0.5f));
            d.skin(s < 0 ? sk.rarm() : sk.larm(), 0, 0, 0, 8, 6, 8.5f, 0, 3);
            pose.popPose();
        }
        bd.steamSrc[2] = d.at(-4.5f, 31, -6.5f);
        bd.steamSrc[3] = d.at(3.5f, 30.5f, -6.2f);
        bd.steamSrc[4] = d.at(9.5f, 27, -4);
        // the head sunk between the shoulders, a bit forward, looking where he looks
        pose.pushPose();
        pose.translate(0, 28.4f, 1.6f);
        pose.mulPose(Axis.YP.rotation(-m.head.yRot));
        pose.mulPose(Axis.XP.rotation(m.head.xRot));
        d.skin(BoundmanSkin.HEAD, 0, 4, 0, 8, 8, 8, 0, 8);
        pose.popPose();
        // arms on guard: the shoulder with the skin, haki forearm and fist (the rubber arm takes the right one's place in a move)
        for (int s = -1; s <= 1; s += 2) {
            boolean gone = s < 0 ? bd.armOn || bd.hideArms : bd.hideArms;
            if (gone) {
                continue;
            }
            pose.pushPose();
            pose.translate(s * 11.5f, 28, 0);
            pose.mulPose(Axis.XP.rotation((float) (-0.15 + (s < 0 ? -0.35 : 0.1)) + (s < 0 ? m.rightArm.xRot : m.leftArm.xRot) * 0.5f));
            pose.mulPose(Axis.ZP.rotation(s * 0.95f));
            bd.steamSrc[s < 0 ? 0 : 1] = d.at(s * 1.5f, 1, s < 0 ? -1.5f : -0.5f);
            d.skin(s < 0 ? sk.rarm() : sk.larm(), 0, -3.2f, 0, 7.2f, 7, 7.2f, 0, 6);
            pose.translate(0, -6.8f, 0);
            pose.mulPose(Axis.XP.rotation((float) (s < 0 ? -1.4 - 0.12 * air : -1.85 + 0.1 * air)));
            pose.mulPose(Axis.ZP.rotation(-s * 0.55f));
            bd.steamSrc[s < 0 ? 5 : 6] = d.at(0, -1, 0);
            d.haki(0, -4.2f, 0, 8, 9, 8.4f, s * 1.7f + 9);
            pose.translate(0, -12.5f, 0.3f);
            d.haki(0, 0, 0, 11.5f, 10.5f, 11, s * 2.3f + 17);
            // the knuckles: four bumps of their own sizes on the fist's front
            for (int q = 0; q < 4; q++) {
                float w = (float) (2.4 + 0.5 * BoundmanSkin.hash(q, s + 3)), h = (float) (2.6 + 0.6 * BoundmanSkin.hash(s + 5, q));
                pose.pushPose();
                pose.translate((q - 1.5f) * 2.75f, -5.6f, (float) (2.4 + 0.5 * BoundmanSkin.hash(q + 9, s)));
                pose.mulPose(Axis.XP.rotation((float) (0.2 + 0.15 * BoundmanSkin.hash(q, s * 7))));
                d.haki(0, 0, 0, w, h, 3.2f, q * 4.1f + s);
                pose.popPose();
            }
            pose.popPose();
        }
        // the steam collar (low at his chest, over the shoulders, up behind his head) and a tail from each elbow
        for (double[] c : RING) {
            d.cloud(c, 1.3f);
        }
        for (double[] c : TAILS) {
            d.cloud(c, 2.1f);
        }
        pose.popPose();
    }

    // one Boundman draw: the skin's boxes in the game's buffer, the haki and the steam on LuffyMesh at the same place
    private record BoxDraw(PoseStack pose, VertexConsumer vc, int light, int ov, float[] L, Vec3 cam) {
        Vec3 at(float x, float y, float z) {
            Vector3f v = pose.last().pose().transformPosition(x, y, z, new Vector3f());
            return cam.add(v.x, v.y, v.z);
        }

        // the page's mcBox: w, h, d pixels with the skin's box on it (sides: only rows r0..r1)
        void skin(int[] box, float x, float y, float z, float w, float h, float dd, int r0, int r1) {
            int[][] f = BoundmanSkin.faces(box);
            float X = w / 2, Y = h / 2, Z = dd / 2;
            PoseStack.Pose p = pose.last();
            // top, bottom, right, front, left, back -> the page's corners
            float[][][] q = {{{-X, Y, -Z}, {X, Y, -Z}, {X, Y, Z}, {-X, Y, Z}}, {{-X, -Y, Z}, {X, -Y, Z}, {X, -Y, -Z}, {-X, -Y, -Z}},
                    {{-X, Y, -Z}, {-X, Y, Z}, {-X, -Y, Z}, {-X, -Y, -Z}}, {{-X, Y, Z}, {X, Y, Z}, {X, -Y, Z}, {-X, -Y, Z}},
                    {{X, Y, Z}, {X, Y, -Z}, {X, -Y, -Z}, {X, -Y, Z}}, {{X, Y, -Z}, {-X, Y, -Z}, {-X, -Y, -Z}, {X, -Y, -Z}}};
            float[][] n = {{0, 1, 0}, {0, -1, 0}, {-1, 0, 0}, {0, 0, 1}, {1, 0, 0}, {0, 0, -1}};
            for (int i = 0; i < 6; i++) {
                int[] r = f[i];
                float v0 = r[1], v1 = r[1] + r[3];
                if (i >= 2) {
                    v0 = r[1] + r0;
                    v1 = r[1] + Math.min(r1, r[3]);
                }
                float u0 = r[0] / 64f, u1 = (r[0] + r[2]) / 64f;
                float[][] uv = {{u0, v0 / 64f}, {u1, v0 / 64f}, {u1, v1 / 64f}, {u0, v1 / 64f}};
                for (int c = 0; c < 4; c++) {
                    vc.addVertex(p, x + q[i][c][0], y + q[i][c][1], z + q[i][c][2]).setColor(-1).setUv(uv[c][0], uv[c][1]).setOverlay(ov).setLight(light)
                            .setNormal(p, n[i][0], n[i][1], n[i][2]);
                }
            }
        }

        // the page's hakiBox (square: corners rounded 0.35 px) with the page's haki shader
        void haki(float x, float y, float z, float w, float h, float dd, float seed) {
            LuffyMesh.P p = LuffyMesh.p(LuffyMesh.HAKI);
            p.glow = false;
            p.seed = seed;
            p.p0 = new float[] {w, h, dd, 0.35f};
            p.p1 = new float[] {L[0], L[1], L[2], 0};
            LuffyMesh.queue(LuffyMesh.Mesh.RBOX, new Matrix4f(pose.last().pose()).translate(x, y, z), p, LuffyMesh.FRONT, 9);
        }

        // a lump of the collar or a tail: c = (x, y, z, radius, u) in pixels on his body
        void cloud(double[] c, float seed) {
            float r = (float) c[3];
            Matrix4f M = new Matrix4f(pose.last().pose()).translate((float) c[0], (float) c[1], (float) c[2]).scale(r);
            float[] anchor = {(float) (c[0] / 16 + seed * 7), (float) (c[1] / 16), (float) (c[2] / 16), r / 16};
            float[] p3 = {(float) (1.12 / r), (float) c[4], 0, 0};
            LuffyMesh.P p = cloudP(0, 0, (float) (1.4 / r), seed, anchor, L, p3);
            LuffyMesh.queue(LuffyMesh.Mesh.BLOB, M, p, LuffyMesh.FRONT, 9);
            LuffyMesh.P o = cloudP(0, (float) (0.24 / r), (float) (1.4 / r), seed, anchor, L, p3);
            o.p0[2] = 1;
            LuffyMesh.queue(LuffyMesh.Mesh.BLOB, M, o, LuffyMesh.BACK, 9);
        }
    }

    private static LuffyMesh.P cloudP(float age, float grow, float lump, float seed, float[] anchor, float[] L, float[] p3) {
        LuffyMesh.P p = LuffyMesh.p(LuffyMesh.CLOUD);
        p.glow = false;
        p.t = (float) TIME;
        p.seed = seed;
        p.p0 = new float[] {age, grow, 0, lump};
        p.p1 = new float[] {L[0], L[1], L[2], 0};
        p.p2 = anchor;
        p.p3 = p3;
        return p;
    }

    // the page's collar ring and elbow tails, sampled along their curves into lumps: (x, y, z, radius, u) in pixels
    private static final double[][] RING, TAILS;

    static {
        double[][] ring = new double[16][];
        for (int i = 0; i < 16; i++) {
            double a = i / 16.0 * Math.PI * 2, c = Math.cos(a), back = Math.max(0, -c), front = Math.max(0, c);
            ring[i] = new double[] {Math.sin(a) * (15.5 + 2 * BoundmanSkin.hash(i, 2)) * (1 - 0.25 * back), 30.5 - front * 6.5 + back * back * 12 + 1.5 * BoundmanSkin.hash(i, 5),
                    c * (8.4 + 1.5 * BoundmanSkin.hash(i, 9)) - back * 2};
        }
        List<double[]> r = new ArrayList<>();
        // the ring thins in front of the chest (u near 0 and 1), so the face and the tattoo show
        sample(ring, true, 2.7, 1.4, r, u -> 0.5 + 0.5 * smooth(Math.min(u, 1 - u) / 0.22));
        RING = r.toArray(new double[0][]);
        List<double[]> tl = new ArrayList<>();
        for (int s = -1; s <= 1; s += 2) {
            int k = s < 0 ? 0 : 1;
            double[][] pts = {{s * 16, 22, -2}, {s * (21 + k), 22.5, -7}, {s * (25.5 - k), 26 + k, -7.5}, {s * (27 + k), 31, -3.5}, {s * (24.5 + k), 34.5 - k, 0.5},
                    {s * 21.5, 33, 2}};
            sample(pts, false, 2.1 - 0.2 * k, 1.1, tl, u -> Math.max(0.12, Math.pow(clamp((1 - u) * 1.15, 0, 1), 0.55)));
        }
        TAILS = tl.toArray(new double[0][]);
    }

    // a Catmull-Rom curve through the points, a lump every `step` pixels, each as thick as the curve is there
    private static void sample(double[][] P, boolean closed, double rad, double step, List<double[]> out, java.util.function.DoubleUnaryOperator thick) {
        int n = P.length, segs = closed ? n : n - 1;
        List<double[]> fine = new ArrayList<>();
        for (int i = 0; i < segs; i++) {
            double[] p0 = P[closed ? (i - 1 + n) % n : Math.max(0, i - 1)], p1 = P[i % n], p2 = P[(i + 1) % n], p3 = P[closed ? (i + 2) % n : Math.min(n - 1, i + 2)];
            for (int j = 0; j < 40; j++) {
                double t = j / 40.0, t2 = t * t, t3 = t2 * t;
                double[] q = new double[3];
                for (int a = 0; a < 3; a++) {
                    q[a] = 0.5 * (2 * p1[a] + (-p0[a] + p2[a]) * t + (2 * p0[a] - 5 * p1[a] + 4 * p2[a] - p3[a]) * t2 + (-p0[a] + 3 * p1[a] - 3 * p2[a] + p3[a]) * t3);
                }
                fine.add(q);
            }
        }
        if (!closed) {
            fine.add(P[n - 1].clone());
        }
        double total = 0;
        double[] len = new double[fine.size()];
        for (int i = 1; i < fine.size(); i++) {
            double[] a = fine.get(i - 1), b = fine.get(i);
            total += Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2]));
            len[i] = total;
        }
        double next = 0;
        for (int i = 0; i < fine.size(); i++) {
            if (len[i] + 1e-9 >= next) {
                double u = len[i] / total, r = rad * thick.applyAsDouble(u);
                double[] q = fine.get(i);
                out.add(new double[] {q[0], q[1], q[2], r, u});
                next += Math.max(0.6, step * r / rad);
            }
        }
    }

    // The page's tickColumn: each column lets points go from his skin; they rise, sway, thicken and tear away up high.
    // Each point is a lump of steam; the lumps sit so close and share one noise, so a column reads as one tube.
    private static void columns(Body bd, Vec3 cam, double dt, boolean selfFp) {
        boolean on = bd.steamFrame == FRAME && bd.lookLv == 3;
        if (bd.cols.isEmpty()) {
            for (int i = 0; i < COLS.length; i++) {
                bd.cols.add(new Col());
            }
        }
        double K = 0.9375 * bd.steamScale, time = TIME;
        boolean any = false;
        for (int i = 0; i < COLS.length; i++) {
            Col c = bd.cols.get(i);
            double[] spec = COLS[i];
            double life = spec[1], seed = spec[3];
            Vec3 head = on ? bd.steamSrc[i] : null;
            if (head != null) {
                c.acc += dt * spec[2];
                while (c.acc >= 1) {
                    c.acc -= 1;
                    c.pts.add(new double[] {head.x, head.y, head.z, (rnd() - 0.5) * 0.06 * K, (0.28 + 0.14 * rnd()) * K, (-0.05 - 0.06 * rnd()) * K, 0});
                }
            }
            for (double[] q : c.pts) {
                q[6] += dt / life;
                q[4] += 0.18 * K * dt;
                q[3] += Math.sin(q[1] / K * 6 + time * 1.3 + seed) * 0.25 * K * dt;
                q[5] += Math.cos(q[1] / K * 5 + time * 1.1 + seed * 2) * 0.2 * K * dt;
                q[0] += q[3] * dt;
                q[1] += q[4] * dt;
                q[2] += q[5] * dt;
                // a column stays above where it leaves him: when he drops from a bounce it is pushed along
                if (head != null) {
                    q[1] = Math.max(q[1], head.y + q[6] * life * 0.26 * K);
                }
            }
            c.pts.removeIf(q -> q[6] >= 1);
            while (c.pts.size() > 47) {
                c.pts.remove(0);
            }
            any |= !c.pts.isEmpty();
            // seen from inside his own head the steam would fill his screen
            if (selfFp) {
                continue;
            }
            double base = spec[0] / 16 * K;
            int cnt = c.pts.size();
            for (int j = 0; j <= cnt; j++) {
                double[] q = j < cnt ? c.pts.get(j) : head == null ? null : new double[] {head.x, head.y, head.z, 0, 0, 0, 0};
                if (q == null) {
                    break;
                }
                double age = q[6], r = base * (0.45 + 2.0 * Math.pow(age, 0.75)) * (j == cnt ? 0.6 : 1);
                Matrix4f M = new Matrix4f().translation((float) (q[0] - cam.x), (float) (q[1] - cam.y), (float) (q[2] - cam.z)).scale((float) r);
                float[] anchor = {(float) (q[0] % 1024), (float) (q[1] % 1024), (float) (q[2] % 1024), (float) r};
                float lump = 0.85f;
                LuffyMesh.queue(LuffyMesh.Mesh.BLOB, M, cloudP((float) age, 0, lump, (float) seed, anchor, bd.light, new float[4]), LuffyMesh.FRONT, 9);
                LuffyMesh.P o = cloudP((float) age, (float) ((0.005 * K + r * 0.1) / r), lump, (float) seed, anchor, bd.light, new float[4]);
                o.p0[2] = 1;
                LuffyMesh.queue(LuffyMesh.Mesh.BLOB, M, o, LuffyMesh.BACK, 9);
            }
        }
        if (!any && !on) {
            bd.cols.clear();
        }
    }

    // ---------- Gear 5 · Nika (forja/luffy-g5, approved: "ÓTIMO PRA CARALHO") ----------

    private static boolean sitting(Body bd) {
        return bd.lookLv == 4 && bd.sitK > 0.001 && RenderSystem.getVertexSorting() != VertexSorting.ORTHOGRAPHIC_Z;
    }

    // Standing still in G5 he sits down (0.8 s) and bounces on his backside; moving, he gets up in 0.5 s.
    private static void sitIdle(Body bd, Entity e, double dt) {
        Vec3 p = e.position();
        double moved = bd.lastPos == null ? 0 : Math.hypot(p.x - bd.lastPos.x, p.z - bd.lastPos.z) + Math.abs(p.y - bd.lastPos.y) * 0.5;
        bd.lastPos = p;
        boolean g5 = bd.g5Until > NOW && bd.move == null && !bd.holding;
        boolean still = g5 && moved < 0.02 * Math.max(dt, 1e-3) * 20 && e.onGround();
        bd.stillFor = still ? bd.stillFor + dt : 0;
        boolean target = still && bd.stillFor > 0.35;
        bd.sitK = target ? Math.min(1, bd.sitK + dt / 0.8) : Math.max(0, bd.sitK - dt / 0.5);
        if (!g5) {
            bd.sitK = 0;
        }
        if (bd.sitK <= 0) {
            bd.bounceT = 0;
            bd.lastPh = -1;
            return;
        }
        bd.bounceT += dt * smooth(Math.min(1, bd.sitK * 1.5));
        double ph = bd.bounceT % 1.0;
        // each landing on his backside shakes the rubber ground
        if (bd.lastPh > 0.5 && ph < 0.5 && bd.sitK > 0.6) {
            double s = e instanceof LivingEntity l ? l.getScale() : 1;
            RUBBERS.add(new Rubber(p, (float) (0.35 * smooth(bd.sitK)), NOW, (float) (0.4 * s)));
        }
        bd.lastPh = ph;
    }

    // the page's bounce: 0.22 s sinking into the rubber ground, 0.78 s in the air -> (height, squash, flight)
    private static double[] bounce(Body bd) {
        double K = smooth(bd.sitK), ph = bd.bounceT % 1.0;
        if (ph < 0.22) {
            double q = ph / 0.22, s = Math.sin(q * Math.PI);
            return new double[] {-0.32 * K * s, s * K, 0};
        }
        double q = (ph - 0.22) / 0.78, fl = Math.sin(q * Math.PI);
        return new double[] {1.0 * K * fl, -0.12 * K * (1 - q) * Math.sin(q * Math.PI), fl};
    }

    private static final int CLOTH = 0xFFF6F5F2, SASH = 0xFF8A45B8;

    // The page's G5 over his own skin: the open white shirt, the wide sleeves and the shorts with their fur hems, the
    // purple sash with its loose end, the cloud at his neck with its flames; sitting, his arms and legs in the page's pose.
    private static void drawNika(PoseStack pose, MultiBufferSource buffer, int light, AbstractClientPlayer player, PlayerModel<AbstractClientPlayer> m,
            Body bd, float partial) {
        int ov = LivingEntityRenderer.getOverlayCoords(player, 0);
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        boolean sit = sitting(bd);
        ModelPart[] limbs = {m.rightArm, m.leftArm, m.rightLeg, m.leftLeg};
        float[][] saved = new float[4][];
        for (int i = 0; i < 4; i++) {
            saved[i] = new float[] {limbs[i].xRot, limbs[i].yRot, limbs[i].zRot};
        }
        if (sit) {
            // the page's sitting pose (the page's z turns the other way in the model's y-down frame)
            float K = (float) smooth(bd.sitK);
            double[] b = bounce(bd);
            double fl = b[2] * K, sq = Math.max(0, b[1]) * K, t = (player.tickCount + partial) / 20.0, wig = Math.sin(t * 6.5);
            float[][] sitPose = {{(float) (-2.75 + 0.1 * wig), -0.35f}, {(float) (-0.9 + 0.3 * wig - 0.5 * fl), (float) -(1.3 + 0.5 * fl + 0.3 * sq)},
                    {(float) (-1.95 - 0.25 * fl + 0.12 * wig), 0.22f}, {(float) (-1.35 - 0.2 * fl - 0.12 * wig), -0.3f}};
            ModelPart[] over = {m.rightSleeve, m.leftSleeve, m.rightPants, m.leftPants};
            VertexConsumer skin = buffer.getBuffer(RenderType.entityCutoutNoCull(player.getSkin().texture()));
            for (int i = 0; i < 4; i++) {
                ModelPart p = limbs[i];
                p.xRot = Mth.lerp(K, saved[i][0], sitPose[i][0]);
                p.yRot = Mth.lerp(K, saved[i][1], 0);
                p.zRot = Mth.lerp(K, saved[i][2], sitPose[i][1]);
                over[i].copyFrom(p);
                for (ModelPart q : new ModelPart[] {p, over[i]}) {
                    boolean was = q.visible;
                    q.visible = true;
                    q.render(pose, skin, light, ov);
                    q.visible = was;
                }
            }
        }
        // taken after the sitting limbs: asking the buffer for the skin's type ends any builder taken before it
        VertexConsumer cloth = buffer.getBuffer(RenderType.entityCutoutNoCull(WHITE_TEX));
        boolean slim = player.getSkin().model() == net.minecraft.client.resources.PlayerSkin.Model.SLIM;
        // the shirt, the waist and the sash on his trunk (page pixels, y from his feet; the trunk turns at 24)
        pose.pushPose();
        m.body.translateAndRotate(pose);
        pageFrame(pose, 24);
        Cloth c = new Cloth(pose, cloth, light, ov);
        c.box(0, 17.9f, -2.3f, 8.9f, 12.2f, 0.6f, 0, 0, CLOTH);
        for (int s = -1; s <= 1; s += 2) {
            c.box(s * 4.3f, 17.9f, 0, 0.6f, 12.2f, 4.6f, 0, 0, CLOTH);
            c.box(s * 3.25f, 17.9f, 2.35f, 2.3f, 12.2f, 0.6f, s * 0.28f, 0, CLOTH);
            c.box(s * 2.35f, 21.2f, 2.55f, 1.4f, 6, 0.7f, -s * 0.2f, -s * 0.32f, CLOTH);
            c.box(s * 3.2f, 25, -0.4f, 2.4f, 2.6f, 4.2f, 0, s * 0.35f, CLOTH);
        }
        c.box(0, 24.2f, 0, 8.9f, 0.6f, 4.6f, 0, 0, CLOTH);
        c.box(0, 11, 0, 8.8f, 3.2f, 4.8f, 0, 0, CLOTH);
        c.box(0, 12.3f, 0, 9.3f, 2.6f, 5.3f, 0, 0, SASH);
        pose.pushPose();
        pose.translate(3.3f, 12.2f, 2.7f);
        pose.mulPose(Axis.ZP.rotation(0.5f));
        c.box(0, 0, 0, 2.6f, 2.4f, 1.6f, 0, 0, SASH);
        pose.popPose();
        Vec3 knot = c.at(3.3f, 11.2f, 3.1f, cam), side = c.dir(1, 0, 0), back = c.dir(0, 0, -1);
        // the cloud at the base of his neck: upright (it does not lean back with him), facing where he faces
        Vec3 neck = c.at(0, 22.4f, -0.8f, cam);
        pose.popPose();
        // sleeves and trouser legs, with the fur hems
        for (int s = -1; s <= 1; s += 2) {
            ModelPart arm = s < 0 ? m.rightArm : m.leftArm, leg = s < 0 ? m.rightLeg : m.leftLeg;
            float ax = s * (slim ? 0.5f : 1);
            pose.pushPose();
            arm.translateAndRotate(pose);
            pageFrame(pose, 0);
            Cloth a = new Cloth(pose, cloth, light, ov);
            a.sleeve(ax, -2.3f, 0, 4.8f, 6.6f, 8.5f);
            a.fur(ax, -6.6f, 0, 3.9f, s * 3.7f + 11, bd);
            pose.popPose();
            pose.pushPose();
            leg.translateAndRotate(pose);
            pageFrame(pose, 0);
            Cloth l = new Cloth(pose, cloth, light, ov);
            l.sleeve(0, -3.6f, 0, 5, 6.2f, 7.6f);
            l.fur(0, -7.4f, 0, 3.7f, s * 5.1f + 3, bd);
            pose.popPose();
        }
        for (int i = 0; i < 4; i++) {
            limbs[i].xRot = saved[i][0];
            limbs[i].yRot = saved[i][1];
            limbs[i].zRot = saved[i][2];
        }
        float sc = player.getScale();
        double A = Math.max(bd.collarA, bd.lookK);
        if (A > 0.01) {
            Vec3 fwd = new Vec3(-back.x, 0, -back.z), left = new Vec3(side.x, 0, side.z);
            if (fwd.lengthSqr() < 1e-6 || left.lengthSqr() < 1e-6) {
                fwd = new Vec3(0, 0, 1);
                left = new Vec3(1, 0, 0);
            }
            fwd = fwd.normalize();
            left = left.normalize();
            Matrix4f M = new Matrix4f((float) left.x, 0, (float) left.z, 0, 0, 1, 0, 0, (float) fwd.x, 0, (float) fwd.z, 0, (float) (neck.x - cam.x),
                    (float) (neck.y - cam.y), (float) (neck.z - cam.z), 1).scale((float) (MS * 0.85 * sc)).rotateX((float) (Math.PI / 2));
            LuffyMesh.P p = LuffyMesh.p(LuffyMesh.COLLAR);
            p.t = (float) TIME;
            p.alpha = (float) Math.min(1, A);
            p.glow = false;
            p.seed = 1.7f;
            p.p0[0] = 0.55f;
            LuffyMesh.queue(LuffyMesh.Mesh.TORUS_F, M, p, LuffyMesh.FRONT, 13);
        }
        ribbon(bd, knot, side, back, player, sc, cloth, light, ov, cam);
    }

    // the model's part frame (y down, front at -z, in blocks) -> the page's (y up, front at +z, in pixels), with
    // y0 = the page height of the part's pivot
    private static void pageFrame(PoseStack pose, float y0) {
        pose.mulPose(Axis.XP.rotation((float) Math.PI));
        pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
        pose.translate(0, -y0, 0);
    }

    private record Cloth(PoseStack pose, VertexConsumer vc, int light, int ov) {
        Vec3 at(float x, float y, float z, Vec3 cam) {
            Vector3f v = pose.last().pose().transformPosition(x, y, z, new Vector3f());
            return cam.add(v.x, v.y, v.z);
        }

        Vec3 dir(float x, float y, float z) {
            Vector3f v = pose.last().pose().transformDirection(x, y, z, new Vector3f()).normalize();
            return new Vec3(v.x, v.y, v.z);
        }

        void quad(PoseStack.Pose p, float[] a, float[] b, float[] c, float[] d, int col) {
            float nx = (b[1] - a[1]) * (d[2] - a[2]) - (b[2] - a[2]) * (d[1] - a[1]);
            float ny = (b[2] - a[2]) * (d[0] - a[0]) - (b[0] - a[0]) * (d[2] - a[2]);
            float nz = (b[0] - a[0]) * (d[1] - a[1]) - (b[1] - a[1]) * (d[0] - a[0]);
            // (b - a) x (d - a) points inward for these corner orders: turned outward
            float l = -((float) Math.sqrt(nx * nx + ny * ny + nz * nz) + 1e-6f);
            float[][] q = {a, b, c, d};
            float[][] uv = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
            for (int i = 0; i < 4; i++) {
                vc.addVertex(p, q[i][0], q[i][1], q[i][2]).setColor(col).setUv(uv[i][0], uv[i][1]).setOverlay(ov).setLight(light).setNormal(p, nx / l, ny / l, nz / l);
            }
        }

        // a box of w x h x d pixels at (x, y, z), turned ry then rz (three's order)
        void box(float x, float y, float z, float w, float h, float d, float ry, float rz, int col) {
            pose.pushPose();
            pose.translate(x, y, z);
            pose.mulPose(Axis.YP.rotation(ry));
            pose.mulPose(Axis.ZP.rotation(rz));
            frustum(w, w, d, d, h, col, true);
            pose.popPose();
        }

        // a 4-sided tube, top w0 x d0, bottom w1 x d1, h tall, centred (caps only for a box)
        void frustum(float w0, float w1, float d0, float d1, float h, int col, boolean caps) {
            PoseStack.Pose p = pose.last();
            float Y = h / 2, a = w0 / 2, b = w1 / 2, c = d0 / 2, e = d1 / 2;
            float[] t0 = {-a, Y, -c}, t1 = {a, Y, -c}, t2 = {a, Y, c}, t3 = {-a, Y, c};
            float[] b0 = {-b, -Y, -e}, b1 = {b, -Y, -e}, b2 = {b, -Y, e}, b3 = {-b, -Y, e};
            quad(p, t3, t2, b2, b3, col);
            quad(p, t1, t0, b0, b1, col);
            quad(p, t0, t3, b3, b0, col);
            quad(p, t2, t1, b1, b2, col);
            if (caps) {
                quad(p, t0, t1, t2, t3, col);
                quad(p, b3, b2, b1, b0, col);
            }
        }

        void sleeve(float x, float y, float z, float top, float bottom, float len) {
            pose.pushPose();
            pose.translate(x, y, z);
            frustum(top, bottom, top, bottom, len, CLOTH, false);
            pose.popPose();
        }

        // the fur hem: the cloud, small and with no flames, lying round the limb
        void fur(float x, float y, float z, float R, float seed, Body bd) {
            LuffyMesh.P p = LuffyMesh.p(LuffyMesh.COLLAR);
            p.t = (float) TIME;
            p.glow = false;
            p.seed = seed;
            Matrix4f M = new Matrix4f(pose.last().pose()).translate(x, y, z).rotateX((float) (Math.PI / 2)).scale(R / 0.7f);
            LuffyMesh.queue(LuffyMesh.Mesh.FUR, M, p, LuffyMesh.FRONT, 13);
        }
    }

    // The sash's loose end: a chain of points hung from the knot, pulled by gravity and a soft wind behind him, never
    // through the ground, drawn as one purple strip.
    private static void ribbon(Body bd, Vec3 knot, Vec3 side, Vec3 back, AbstractClientPlayer player, float sc, VertexConsumer vc, int light, int ov, Vec3 cam) {
        int N = 13;
        double seg = 1.25 / 16 * 0.9375 * sc;
        long ns = System.nanoTime();
        double dt = bd.rib == null ? 0 : Math.min(0.05, (ns - bd.ribNs) / 1e9);
        bd.ribNs = ns;
        if (bd.rib == null || bd.rib[0][0] == 0 && bd.rib[0][1] == 0 || knot.distanceToSqr(bd.rib[0][0], bd.rib[0][1], bd.rib[0][2]) > 9 * sc * sc) {
            bd.rib = new double[N][6];
            for (int i = 0; i < N; i++) {
                bd.rib[i] = new double[] {knot.x, knot.y - i * seg, knot.z, knot.x, knot.y - i * seg, knot.z};
            }
        }
        double t = (player.tickCount) / 20.0, floor = player.getY() + 0.02;
        int sub = 3;
        double h = dt / sub;
        for (int s = 0; s < sub && dt > 0; s++) {
            for (int i = 1; i < N; i++) {
                double[] q = bd.rib[i];
                double wb = 0.35 + 0.35 * Math.sin(t * 2.1 + i * 0.4), ws = 0.5 * Math.sin(t * 1.3 + i * 0.7);
                double ax = back.x * wb + side.x * ws, ay = -7 + back.y * wb + side.y * ws, az = back.z * wb + side.z * ws;
                double vx = (q[0] - q[3]) * 0.985, vy = (q[1] - q[4]) * 0.985, vz = (q[2] - q[5]) * 0.985;
                q[3] = q[0];
                q[4] = q[1];
                q[5] = q[2];
                q[0] += vx + ax * sc * h * h;
                q[1] += vy + ay * sc * h * h;
                q[2] += vz + az * sc * h * h;
            }
            bd.rib[0] = new double[] {knot.x, knot.y, knot.z, knot.x, knot.y, knot.z};
            for (int it = 0; it < 6; it++) {
                for (int i = 1; i < N; i++) {
                    double[] a = bd.rib[i - 1], b = bd.rib[i];
                    double dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2], l = Math.sqrt(dx * dx + dy * dy + dz * dz) + 1e-9, k = (l - seg) / l;
                    if (i == 1) {
                        b[0] -= dx * k;
                        b[1] -= dy * k;
                        b[2] -= dz * k;
                    } else {
                        a[0] += dx * k * 0.5;
                        a[1] += dy * k * 0.5;
                        a[2] += dz * k * 0.5;
                        b[0] -= dx * k * 0.5;
                        b[1] -= dy * k * 0.5;
                        b[2] -= dz * k * 0.5;
                    }
                }
            }
            for (int i = 1; i < N; i++) {
                bd.rib[i][1] = Math.max(bd.rib[i][1], floor);
            }
        }
        // drawn relative to the camera, in the world's axes
        PoseStack.Pose id = new PoseStack().last();
        double w = 3.2 / 16 * 0.9375 / 2 * sc;
        float[][] L = new float[N][], R = new float[N][];
        float[][] nrm = new float[N][];
        for (int i = 0; i < N; i++) {
            double[] p = bd.rib[i], a = bd.rib[Math.max(0, i - 1)], b = bd.rib[Math.min(N - 1, i + 1)];
            Vec3 along = new Vec3(b[0] - a[0], b[1] - a[1], b[2] - a[2]).normalize();
            Vec3 wd = side.subtract(along.scale(side.dot(along))).normalize();
            Vec3 n = along.cross(wd).normalize();
            double ww = w * (i == N - 1 ? 0.75 : 1);
            L[i] = new float[] {(float) (p[0] - wd.x * ww - cam.x), (float) (p[1] - wd.y * ww - cam.y), (float) (p[2] - wd.z * ww - cam.z)};
            R[i] = new float[] {(float) (p[0] + wd.x * ww - cam.x), (float) (p[1] + wd.y * ww - cam.y), (float) (p[2] + wd.z * ww - cam.z)};
            nrm[i] = new float[] {(float) n.x, (float) n.y, (float) n.z};
        }
        for (int i = 0; i < N - 1; i++) {
            float[][] q = {L[i], R[i], R[i + 1], L[i + 1]};
            float[][] nn = {nrm[i], nrm[i], nrm[i + 1], nrm[i + 1]};
            for (int k = 0; k < 4; k++) {
                vc.addVertex(id, q[k][0], q[k][1], q[k][2]).setColor(SASH).setUv(k == 1 || k == 2 ? 1 : 0, k >= 2 ? 1 : 0).setOverlay(ov).setLight(light)
                        .setNormal(id, nn[k][0], nn[k][1], nn[k][2]);
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
        // a mob's onGround is not always sent to us: no longer falling counts as down too
        boolean ground = e.onGround() || (now - k.born) / 20 > 0.25 && Math.abs(e.getY() - e.yo) < 0.004 && e.getDeltaMovement().y <= 0.01;
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
        float h = e.getBbHeight();
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
            // resting on the back of the torso (half its depth), not on the hitbox's half width
            pose.translate(0, 0.16f * flat, 0);
            pose.mulPose(new Quaternionf().rotationAxis(ang, axis));
        }
        // turned away from Luffy, so falling head-first back toward him lands on the back:
        // face up, arms and legs on the ground instead of hanging under a face-down body
        pose.mulPose(Axis.YP.rotation((float) Math.PI));
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
