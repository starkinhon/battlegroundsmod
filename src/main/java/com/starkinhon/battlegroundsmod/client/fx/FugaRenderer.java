package com.starkinhon.battlegroundsmod.client.fx;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3d;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.power.powers.sukuna.Fuga;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

// Sukuna's Fuga (power/powers/sukuna/Fuga), drawn as the 3D preview Stark approved (forja/sukuna-fuga, formation v25,
// shot and explosion v26): this class runs the preview's fuga(t) every frame for each caster and draws its pieces with
// the preview's own shaders (shaders/core/souls_fuga_*):
//   - 8 ribbons of flame (two crossed strips fixed on a 3D curve) that whip out of the hands, circle him and are sucked one
//     by one into the ball, then grow back out;
//   - the ball between the palms that stretches into the arrow: one raymarched volume of fire (souls_fuga_vol);
//   - the painted arrow on two crossed planes over it, the glow at the fist, embers rising and swirling into the ball,
//     a glow on the ground under him;
//   - the flight, the explosion volume (dome -> column -> burning out) and the shock ring on the ground.
// Everything is worked out in the caster's own frame (the preview's: feet at 0, facing +z), from the hands' path baked
// out of the preview with the pose (fx/sukuna_fuga_track.json), and turned into the world with where he faces.
// The darkness (Stark: "a atmosfera e ambiente ao redor fica exatamente escura", 70 blocks): the sky and the fog go to
// the preview's dark red-black and the whole picture is darkened before the fire is drawn on top, for everyone within
// Fuga.DARK_RADIUS blocks (fading out over the last 10).
public final class FugaRenderer {
    // ---------- the preview's timeline (pose ticks) ----------
    private static final double T_AURA = 12, T_GATHER = 34, T_BALL = 44, T_DRAW = 54, T_SET = 66, T_FIRE = 96, T_HIT = 100, T_LEN = 168;
    private static final double SLOW = 2, DRAW_X = 2.5;
    private static final int NE = 320;
    // Darkness: what the picture is multiplied by at full strength (the preview's night: hemisphere 0.28 of day, floor
    // 0x2a1512 against 0x6f7f60), and the sky / fog color before that, so after it the sky is the preview's 0x140605.
    private static final float[] MULT = {0.34f, 0.2f, 0.19f};
    private static final float[] SKY = {0x14 / 255f / 0.34f, 0x06 / 255f / 0.2f, 0x05 / 255f / 0.19f};
    private static final double DARK_FADE = 10;

    private static final class Cast {
        final int id;
        final double start;
        Vec3 feet;
        float yaw;
        float pitch;
        double shotAt = -1, hitAt = -1, stopAt = -1;
        Vec3 shotPos, shotDir, hitPos;
        // the caster's frame frozen at the release: the flight and the burst stay in the world when he turns
        Vec3 shotFeet;
        float shotYaw;
        // the integrated flow of the volume's noise (FLOW in the preview)
        double flowRt = -1, flowX, flowY;
        int flowQ = -1;
        float fx, fy;
        boolean shook;

        Cast(int id, double start, Vec3 feet) {
            this.id = id;
            this.start = start;
            this.feet = feet;
        }

        double end() {
            if (stopAt >= 0) {
                return stopAt;
            }
            return hitAt >= 0 ? hitAt + (T_LEN - T_HIT) * SLOW : start + Fuga.LENGTH + 60;
        }
    }

    private static final Map<Integer, Cast> CASTS = new HashMap<>();
    private static float[][] track;
    private static double[] hold;
    private static double frameNow = -1;
    private static float frameDark;

    private FugaRenderer() {
    }

    // ---------- from the server ----------

    public static void receive(int casterId, int phase, Vec3 pos, Vec3 dir) {
        double now = now();
        Cast c = CASTS.get(casterId);
        switch (phase) {
            case Fuga.START -> CASTS.put(casterId, new Cast(casterId, now, pos));
            case Fuga.SHOT -> {
                if (c != null) {
                    c.shotAt = now;
                    c.shotPos = pos;
                    c.shotDir = dir.normalize();
                    c.shotFeet = c.feet;
                    c.shotYaw = c.yaw;
                }
            }
            case Fuga.HIT -> {
                if (c != null) {
                    c.hitAt = now;
                    c.hitPos = pos;
                    if (c.shotAt < 0) {
                        c.shotAt = now;
                        c.shotPos = pos;
                        c.shotDir = dir.normalize();
                        c.shotFeet = c.feet;
                        c.shotYaw = c.yaw;
                    }
                }
            }
            default -> {
                if (c != null && c.shotAt < 0) {
                    c.stopAt = now;
                }
            }
        }
    }

    public static boolean active() {
        return !CASTS.isEmpty();
    }

    public static void clear() {
        CASTS.clear();
    }

    private static double now() {
        return ScreenFx.now() + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
    }

    // ---------- the darkness ----------

    // 0..1 for the camera this frame: the strongest Fuga close enough.
    public static float darkness() {
        double now = now();
        if (now == frameNow) {
            return frameDark;
        }
        frameNow = now;
        float best = 0;
        Minecraft mc = Minecraft.getInstance();
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
        Iterator<Cast> it = CASTS.values().iterator();
        while (it.hasNext()) {
            Cast c = it.next();
            double end = c.end();
            if (now > end + 40) {
                it.remove();
                continue;
            }
            double in = smooth(0, 20, now - c.start);
            double out = 1 - smooth(end - (c.stopAt >= 0 ? 0 : 10), end + (c.stopAt >= 0 ? 20 : 30), now);
            Entity e = FxAnchor.entity(c.id);
            Vec3 at = e != null ? e.position() : c.feet;
            double d = at.distanceTo(eye);
            if (c.hitPos != null) {
                d = Math.min(d, c.hitPos.distanceTo(eye));
            }
            double near = 1 - smooth(Fuga.DARK_RADIUS - DARK_FADE, Fuga.DARK_RADIUS, d);
            best = (float) Math.max(best, in * out * near);
        }
        frameDark = best;
        return best;
    }

    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        float a = CASTS.isEmpty() ? 0 : darkness();
        if (a <= 0) {
            return;
        }
        event.setRed(Mth.lerp(a, event.getRed(), SKY[0]));
        event.setGreen(Mth.lerp(a, event.getGreen(), SKY[1]));
        event.setBlue(Mth.lerp(a, event.getBlue(), SKY[2]));
    }

    public static void onFog(ViewportEvent.RenderFog event) {
        float a = CASTS.isEmpty() ? 0 : darkness();
        if (a <= 0) {
            return;
        }
        if (event.getMode() == FogRenderer.FogMode.FOG_SKY) {
            // the sky itself becomes the fog color
            event.setNearPlaneDistance(Mth.lerp(a, event.getNearPlaneDistance(), 0));
            event.setFarPlaneDistance(Mth.lerp(a, event.getFarPlaneDistance(), 0.01f));
        } else {
            // the land goes dark into the distance (the preview has no horizon: dark red-black all round)
            event.setNearPlaneDistance(Mth.lerp(a, event.getNearPlaneDistance(), 6));
            event.setFarPlaneDistance(Mth.lerp(a, event.getFarPlaneDistance(), 56));
        }
        event.setCanceled(true);
    }

    // After the sky: covers the sun, moon and stars with the dark color (only the sky is drawn yet).
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY || CASTS.isEmpty()) {
            return;
        }
        float a = darkness();
        if (a <= 0.001f) {
            return;
        }
        fullScreen(SKY[0], SKY[1], SKY[2], a, false);
    }

    // At the end of the level, before the effects: the whole picture darkened (FxWorld calls it).
    static void darken() {
        float a = CASTS.isEmpty() ? 0 : darkness();
        if (a <= 0.001f) {
            return;
        }
        fullScreen(1 + (MULT[0] - 1) * a, 1 + (MULT[1] - 1) * a, 1 + (MULT[2] - 1) * a, 1, true);
    }

    private static void fullScreen(float r, float g, float b, float a, boolean multiply) {
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.identity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f(), VertexSorting.ORTHOGRAPHIC_Z);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        if (multiply) {
            RenderSystem.blendFunc(GlStateManager.SourceFactor.DST_COLOR, GlStateManager.DestFactor.ZERO);
        } else {
            RenderSystem.defaultBlendFunc();
        }
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        buf.addVertex(-1, -1, 0).setColor(r, g, b, a);
        buf.addVertex(1, -1, 0).setColor(r, g, b, a);
        buf.addVertex(1, 1, 0).setColor(r, g, b, a);
        buf.addVertex(-1, 1, 0).setColor(r, g, b, a);
        BufferUploader.drawWithShader(buf.buildOrThrow());
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.restoreProjectionMatrix();
        modelView.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

    // ---------- the preview's helpers ----------

    private static double cl(double x, double a, double b) {
        return Math.max(a, Math.min(b, x));
    }

    private static double smooth(double a, double b, double x) {
        double u = cl((x - a) / (b - a), 0, 1);
        return u * u * (3 - 2 * u);
    }

    private static double hash(double n) {
        double s = Math.sin(n * 127.1 + 311.7) * 43758.5453;
        return s - Math.floor(s);
    }

    private static double easeOut(double u) {
        return 1 - Math.pow(1 - u, 3);
    }

    private static double easeIo(double u) {
        return u < 0.5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2;
    }

    private static double mix(double a, double b, double u) {
        return a + (b - a) * u;
    }

    // Pose ticks from game ticks since the start: the preview plays at half speed (SLOW) and the bow draw DRAW_X slower.
    private static double warp(double t) {
        double d = (T_SET - T_DRAW) * DRAW_X;
        return t < T_DRAW ? t : t < T_DRAW + d ? T_DRAW + (t - T_DRAW) / DRAW_X : t - (d - (T_SET - T_DRAW));
    }

    private static Vector3d v(double x, double y, double z) {
        return new Vector3d(x, y, z);
    }

    private static Vector3d lerp(Vector3d a, Vector3d b, double u) {
        return new Vector3d(a).lerp(b, u);
    }

    private static Vector3d add(Vector3d a, Vector3d b, double s) {
        return new Vector3d(a).fma(s, b);
    }

    // ---------- the hands' path ----------

    private static boolean loadTrack() {
        if (track != null) {
            return true;
        }
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "fx/sukuna_fuga_track.json");
        var res = Minecraft.getInstance().getResourceManager().getResource(id);
        if (res.isEmpty()) {
            return false;
        }
        try (Reader reader = res.get().openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray t = root.getAsJsonArray("t");
            float[][] out = new float[t.size()][];
            for (int i = 0; i < t.size(); i++) {
                JsonArray row = t.get(i).getAsJsonArray();
                out[i] = new float[row.size()];
                for (int j = 0; j < row.size(); j++) {
                    out[i][j] = row.get(j).getAsFloat();
                }
            }
            JsonArray h = root.getAsJsonArray("hold");
            hold = new double[h.size()];
            for (int j = 0; j < h.size(); j++) {
                hold[j] = h.get(j).getAsDouble();
            }
            track = out;
            return true;
        } catch (Exception e) {
            BattlegroundsMod.LOGGER.error("Bad Fuga track {}", id, e);
            track = new float[0][];
            return false;
        }
    }

    // Point k (0 right hand, 1 left hand, 2 the hips) of the baked path at game tick g.
    private static Vector3d trackAt(double g, int k) {
        int n = track.length - 1;
        double c = cl(g, 0, n);
        int i = (int) Math.floor(c);
        int j = Math.min(n, i + 1);
        double u = c - i;
        float[] a = track[i], b = track[j];
        return v(mix(a[k * 3], b[k * 3], u), mix(a[k * 3 + 1], b[k * 3 + 1], u), mix(a[k * 3 + 2], b[k * 3 + 2], u));
    }

    // ---------- one frame of the preview's fuga(t), in the caster's frame ----------

    private record Strip(Vector3d[] pts, double[] widths, float seed, float grow, float alpha, float len) {
    }

    private record Sprite(Vector3d at, double size, float r, float g, float b, float a, boolean ground) {
    }

    private static final class Frame {
        final List<Strip> ribbons = new ArrayList<>();
        // the ball / arrow volume: its frame (local), box and uniforms
        boolean vol;
        Vector3d tail, ax, yv, zv;
        double len, ballX, ballY, ballZ, br, m, fist;
        boolean ballFrame;
        Vector3d ballOrigin, ballA, ballY0, ballZ0;
        // painted arrow: the arrow's own frame
        boolean planes;
        double grow;
        Vector3d planeTail, planeAx, planeYv, planeZv;
        final List<Sprite> sprites = new ArrayList<>();
        final List<double[]> embers = new ArrayList<>();
        // the burst (world)
        boolean exp;
        double expR, expRise, expBurn, expHot, ringR, ringA, expLight;
    }

    private static Frame compute(Cast c, double e, double tPose, Vector3d aim) {
        Frame f = new Frame();
        double t = tPose;
        // e: game ticks into the move (held at the release until the server says it flew); after the release the body
        // follows the track again while the arrow keeps the frame it had in the bow
        double g = e;
        Vector3d HR = trackAt(g, 0), HL = trackAt(g, 1), C = trackAt(g, 2);
        Vector3d HRf = null, HLf = null;
        if (t > T_FIRE) {
            HRf = v(hold[0], hold[1], hold[2]);
            HLf = v(hold[3], hold[4], hold[5]);
        }
        Vector3d mid = lerp(HR, HL, 0.5);
        double drawU = smooth(T_DRAW, T_DRAW + 5, t);
        Vector3d fwd = new Vector3d(HR).sub(HL);
        fwd.y *= 0.3;
        fwd.normalize();
        fwd.lerp(aim, smooth(T_DRAW, T_DRAW + 4, t)).normalize();
        Vector3d up0 = v(0, 1, 0);
        Vector3d side0 = new Vector3d(fwd).cross(up0).normalize();
        Vector3d up1 = new Vector3d(side0).cross(fwd).normalize();
        Vector3d ballAt = lerp(mid, add(HR, fwd, 0.12), drawU);
        double auraOn = smooth(T_AURA, T_AURA + 6, t), inward = smooth(T_GATHER, T_BALL + 4, t);

        // --- aura ribbons
        double absorbSum = 0;
        int[] order = {0, 4, 1, 6, 2, 7, 3, 5};
        for (int k = 0; k < 8; k++) {
            double abs0 = T_GATHER - 2 + order[k] * 1.5;
            double ph = k / 8.0 * 6.2832 + hash(k * 7.3) * 0.6;
            double rad0 = 1.0 + 0.55 * hash(k * 2.9), rise = 0.5 + 1.1 * hash(k * 5.7), y0 = 0.1 + 0.7 * hash(k * 4.3);
            double span = 3.0 + 1.6 * hash(k * 8.1), tilt = 0.25 + 0.5 * hash(k * 6.6), w = 0.17 + 0.12 * hash(k * 9.9);
            double born = T_AURA + 2 + k * 1.3;
            Vector3d H = k % 2 == 1 ? HL : HR;
            double dir = k % 3 == 2 ? -1 : 1;
            double s = t - born;
            double grow = easeIo(cl(s / 12, 0, 1));
            double back = smooth(T_SET - 2, T_SET + 12, t);
            double ab = smooth(abs0, abs0 + 9, t);
            double reg = smooth(T_SET + k * 1.3, T_SET + 12 + k * 1.3, t);
            absorbSum += ab * (1 - reg);
            double ee = smooth(0, 0.4, ab) * (1 - reg);
            double rot = dir * (t * 0.085 + 2.2 * ab * ab) + ph;
            double rad = rad0 * (1 + 0.35 * back) * (1 + 0.1 * Math.sin(t * 0.2 + k));
            double pour = smooth(0.25, 1, ab);
            double len = Math.min(grow, Math.max(1 - pour, easeIo(reg)));
            double alpha = (1 - 0.3 * back) * smooth(0, 0.35, len);
            if (!(s > 0) || len <= 0.01) {
                continue;
            }
            int n = 48;
            Vector3d[] pts = new Vector3d[n + 1];
            double[] wid = new double[n + 1];
            final int kk = k;
            for (int i = 0; i <= n; i++) {
                double u = i / (double) n;
                double an = rot - u * span * dir;
                double y = C.y - 0.25 + y0 + u * rise + Math.sin(u * 5 + t * 0.25 + kk) * 0.12 + Math.sin(an - ph * 1.7) * tilt;
                Vector3d P = v(C.x + Math.cos(an) * rad, y, C.z + Math.sin(an) * rad);
                double toHand = (1 - smooth(0, 0.45, u)) * (1 - smooth(T_GATHER - 6, T_GATHER, t)) * (1 - back);
                P.lerp(H, toHand);
                if (ee > 0) {
                    double rr = u * mix(rad0 * 0.9, 0.42, ee) + 0.02;
                    double as = rot * 1.6 - u * (4.2 + 2 * ab) * dir;
                    Vector3d Q = v(ballAt.x + Math.cos(as) * rr, ballAt.y + u * mix(0.6, 0.12, ee) * (kk % 2 == 1 ? 1 : -1) + Math.sin(as * 0.5) * 0.06 * u,
                            ballAt.z + Math.sin(as) * rr);
                    P.lerp(Q, ee);
                }
                pts[i] = P;
                wid[i] = w * (0.35 + 0.65 * Math.pow(Math.sin(Math.PI * Math.min(1, u * 1.15)), 0.8)) * (1 - 0.55 * back) * (1 - 0.35 * ee)
                        * (0.55 + 0.45 * smooth(0, 0.3, u + 0.2 * (1 - ee)));
            }
            double L = 0;
            for (int i = 1; i <= n; i++) {
                L += pts[i].distance(pts[i - 1]);
            }
            f.ribbons.add(new Strip(pts, wid, (float) hash(k * 3.1 + 1), (float) len, (float) alpha, (float) Math.max(0.6, L)));
        }

        // --- the arrow (and the ball before it)
        boolean drawn = t > T_DRAW;
        Vector3d fx = add(HR, fwd, 0.08);
        Vector3d fxA = HRf != null ? add(HRf, fwd, 0.08) : fx;
        Vector3d tail = add(HLf != null ? HLf : HL, fwd, -0.25);
        double Lr = 1.35;
        Vector3d tip = add(fxA, fwd, Lr);
        Vector3d fxF = fxA;
        Vector3d ax;
        if (c.shotAt >= 0) {
            // the flight, in the frame frozen at the release
            double L0 = tip.distance(tail);
            double fly = Fuga.flown(now() - c.shotAt);
            Vector3d sp = toLocal(c, c.shotPos), sd = toLocalDir(c, c.shotDir);
            if (c.hitPos != null) {
                Vector3d hp = toLocal(c, c.hitPos);
                fly = Math.min(fly, Math.max(0, new Vector3d(hp).sub(sp).dot(sd)));
            }
            tip = add(sp, sd, fly);
            tail = add(tip, sd, -L0);
            fxF = add(tip, sd, -Lr);
            ax = sd;
        } else {
            ax = new Vector3d(tip).sub(tail).normalize();
        }
        double L = tip.distance(tail);
        Vector3d yv = new Vector3d(ax).cross(up0).normalize();
        Vector3d zv = new Vector3d(yv).cross(ax).normalize();
        double absd = absorbSum / 8;
        double bg = t > T_DRAW ? 1 : Math.min(1, Math.sqrt(smooth(0, 1, (t - (T_GATHER - 1)) / 3) * absd * 1.15));
        f.vol = bg > 0.02 && c.hitAt < 0;
        f.tail = tail;
        f.ax = ax;
        f.yv = yv;
        f.zv = zv;
        f.len = L;
        f.ballFrame = !drawn;
        if (!drawn) {
            Vector3d a = new Vector3d(aim);
            Vector3d yb = new Vector3d(a).cross(up0).normalize();
            Vector3d zb = new Vector3d(yb).cross(a).normalize();
            f.ballA = a;
            f.ballY0 = yb;
            f.ballZ0 = zb;
            f.ballOrigin = add(ballAt, a, -tail.distance(fxF));
        }
        f.br = 0.28 * bg * (1 + 0.05 * Math.sin(t * 0.9));
        f.m = easeIo(smooth(T_DRAW + 0.5, T_SET - 0.5, t));
        f.fist = tail.distance(fxF);
        // ball centre in the volume's frame
        Vector3d o = drawn ? tail : f.ballOrigin;
        Vector3d X = drawn ? ax : f.ballA, Y = drawn ? zv : f.ballZ0, Z = drawn ? new Vector3d(yv).negate() : new Vector3d(f.ballY0).negate();
        Vector3d rel = new Vector3d(ballAt).sub(o);
        f.ballX = rel.dot(X);
        f.ballY = rel.dot(Y);
        f.ballZ = rel.dot(Z);
        f.tail = o;
        f.ax = X;
        f.zv = Y;
        f.yv = Z;
        f.grow = easeOut(cl((t - T_DRAW - 7) / 6, 0, 1));
        f.planes = drawn && f.grow > 0 && c.hitAt < 0;
        // the plane frames need the arrow's own (not the ball's) axes
        f.planeTail = tail;
        f.planeAx = ax;
        f.planeYv = yv;
        f.planeZv = zv;

        // --- glows
        if (drawn && t < T_FIRE + 2) {
            double sc = 3.4 * smooth(T_DRAW, T_SET, t) * (1 + 0.08 * Math.sin(t * 1.3));
            f.sprites.add(new Sprite(add(fx, fwd, 0.4), sc, 1f * 0.75f, 0xa0 / 255f * 0.75f, 0x30 / 255f * 0.75f, 0, false));
        }
        double fireAmt = 0.25 * smooth(0, 8, t) + 0.9 * auraOn * (1 - 0.4 * inward) + 0.9 * smooth(T_BALL, T_DRAW, t) + 0.6 * smooth(T_DRAW, T_SET, t);
        double ga = 0.38 * Math.min(1.3, fireAmt);
        f.sprites.add(new Sprite(v(C.x, 0.02, C.z), 5.5 + 1.5 * auraOn, (float) ga, (float) (ga * 0x5a / 255.0), (float) (ga * 0x14 / 255.0), 0, true));

        // --- embers: rise from the ground around him, swirl, get sucked into the ball while it forms
        for (int i = 0; i < NE; i++) {
            double h1 = hash(i * 1.13), h2 = hash(i * 2.71), h3 = hash(i * 3.37), h4 = hash(i * 4.91);
            double life = 26 + 30 * h2, st = 2 + h1 * life;
            double age = t - st;
            if (age < 0) {
                continue;
            }
            double a = (age % life) / life;
            double cyc = Math.floor(age / life);
            double r = 0.4 + 3.0 * Math.pow(hash(i * 5.5 + cyc * 1.7), 0.7), an0 = hash(i * 6.1 + cyc * 2.3) * 6.2832;
            double an = an0 + a * (1.2 + h3) * (h4 < 0.5 ? 1 : -1) + t * 0.01;
            double y = a * (2.2 + 2.8 * h3) * (0.6 + 0.4 * auraOn);
            Vector3d P0 = v(Math.cos(an) * r * (1 - 0.3 * a), y + 0.05, Math.sin(an) * r * (1 - 0.3 * a));
            double suck = inward * (1 - smooth(T_DRAW, T_SET, t)) * (h4 < 0.55 ? 1 : 0) * smooth(0, 1, (t - T_GATHER) / 14 - h1 * 0.3);
            P0.lerp(ballAt, cl(suck, 0, 0.95));
            double fl = 0.6 + 0.4 * Math.sin(t * 1.7 + i);
            double size = (0.035 + 0.05 * h2) * (1 - a * 0.7) * fl * (0.5 + 0.5 * smooth(0, 10, t)) * 0.655;
            f.embers.add(new double[] {P0.x, P0.y, P0.z, size, (1 - a) * fl});
        }

        // --- the burst
        if (c.hitAt >= 0) {
            double eh = (now() - c.hitAt) / SLOW;
            f.exp = eh < T_LEN - T_HIT;
            f.expR = 4.2 * easeOut(cl(eh / 6, 0, 1)) + 0.6 * smooth(6, 40, eh);
            f.expRise = 5.5 * smooth(1, 34, eh) * easeOut(cl(eh / 30, 0, 1));
            f.expBurn = smooth(12, 48, eh);
            f.expHot = 1 - smooth(0, 10, eh);
            f.ringR = eh < 22 ? 2 + 16 * easeOut(cl(eh / 16, 0, 1)) : 0;
            f.ringA = 1 - smooth(8, 22, eh);
            f.expLight = (5 * (1 - smooth(0, 6, eh)) + 2.2) * (1 - f.expBurn);
        }
        return f;
    }

    // ---------- frames: the caster's (current, or frozen at the release) ----------

    private static Vec3 frameFeet(Cast c) {
        return c.shotAt >= 0 && c.shotFeet != null ? c.shotFeet : c.feet;
    }

    private static float frameYaw(Cast c) {
        return c.shotAt >= 0 && c.shotFeet != null ? c.shotYaw : c.yaw;
    }

    private static Vector3d toLocal(Cast c, Vec3 world) {
        Vec3 d = world.subtract(frameFeet(c)).yRot(frameYaw(c) * Mth.DEG_TO_RAD);
        return v(d.x, d.y, d.z);
    }

    private static Vector3d toLocalDir(Cast c, Vec3 dir) {
        Vec3 d = dir.yRot(frameYaw(c) * Mth.DEG_TO_RAD);
        return v(d.x, d.y, d.z).normalize();
    }

    // local -> camera-relative world
    private static Matrix4f localToRel(Cast c, Vec3 camera) {
        Vec3 o = frameFeet(c).subtract(camera);
        return new Matrix4f().translation((float) o.x, (float) o.y, (float) o.z).rotateY(-frameYaw(c) * Mth.DEG_TO_RAD);
    }

    // ---------- drawing (FxWorld calls this at the end of the level, matrices set to the camera) ----------

    static void render(Vec3 camera, float partial) {
        if (CASTS.isEmpty() || !loadTrack() || track.length == 0 || FxShaders.fugaFlame == null || FxShaders.fugaVol == null
                || FxShaders.fugaArrow == null || FxShaders.fugaExp == null || FxShaders.fugaRing == null || FxShaders.fugaGlow == null) {
            return;
        }
        double now = now();
        for (Cast c : CASTS.values()) {
            double e = now - c.start;
            if (c.stopAt >= 0 || now > c.end() + 10) {
                continue;
            }
            Entity ent = FxAnchor.entity(c.id);
            if (c.shotAt < 0 && ent != null) {
                c.feet = ent.getPosition(partial);
                // the body is kept square with the head while the move plays (SkillAnimations), so this is where it faces
                c.yaw = ent instanceof net.minecraft.world.entity.LivingEntity living ? living.getYHeadRot() : ent.getYRot();
                c.pitch = ent.getViewXRot(partial);
            } else if (c.shotAt < 0 && ent == null && e < Fuga.FIRE_TICK) {
                // out of sight: only its darkness reaches here
                continue;
            }
            double tGame = e;
            if (c.shotAt < 0 && tGame > Fuga.FIRE_TICK) {
                // waiting for the server's release: hold the drawn bow
                tGame = Fuga.FIRE_TICK;
            }
            double t = warp(tGame / SLOW);
            if (c.shotAt >= 0) {
                t = Math.max(t, T_FIRE + Math.min(T_HIT - T_FIRE, (now - c.shotAt) / SLOW) + 1e-3);
            }
            float pitch = Mth.clamp(c.pitch, -60, 60) * Mth.DEG_TO_RAD;
            Vector3d aim = v(0, -Math.sin(pitch), Math.cos(pitch));
            Frame f = compute(c, tGame, t, aim);
            Matrix4f toRel = localToRel(c, camera);
            float time = (float) (e / 20.0);
            flow(c, f.m, e);
            drawFrame(c, f, toRel, camera, time);
            if (c.hitAt >= 0 && !c.shook) {
                c.shook = true;
                if (c.hitPos.distanceTo(camera) < 48) {
                    com.starkinhon.battlegroundsmod.client.ScreenShake.start(0.9f, 16);
                }
            }
        }
    }

    // The preview's FLOW: the volume's noise phases integrated over real time, stepped at 12 fps.
    private static void flow(Cast c, double m, double e) {
        double rt = e / 20 * 4;
        if (c.flowRt < 0 || rt < c.flowRt || rt - c.flowRt > 2) {
            c.flowX = rt * 0.45 * m;
            c.flowY = rt * (0.25 + 0.2 * (1 - m));
        } else {
            double d = rt - c.flowRt;
            c.flowX += d * 0.45 * m;
            c.flowY += d * (0.25 + 0.2 * (1 - m));
        }
        c.flowRt = rt;
        int q = (int) Math.floor(e / 20 * 12);
        if (q != c.flowQ) {
            c.flowQ = q;
            c.fx = (float) c.flowX;
            c.fy = (float) c.flowY;
        }
    }

    private static void drawFrame(Cast c, Frame f, Matrix4f toRel, Vec3 camera, float time) {
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515);
        RenderSystem.disableCull();
        // 1. ribbon bodies (solid, hard-edged), 2. their halos (added)
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        for (Strip s : f.ribbons) {
            ribbon(s, toRel, time, false);
        }
        additive();
        for (Strip s : f.ribbons) {
            ribbon(s, toRel, time, true);
        }
        // 3. the ball / arrow volume
        if (f.vol) {
            RenderSystem.disableBlend();
            RenderSystem.depthMask(false);
            volume(c, f, toRel, camera, time);
        }
        // 4. the glow at the fist
        additive();
        sprites(f, toRel, camera, false);
        // 5. the painted arrow, then its halo
        if (f.planes) {
            RenderSystem.disableBlend();
            RenderSystem.depthMask(true);
            planes(f, toRel, time, false);
            additive();
            planes(f, toRel, time, true);
        }
        // 6. embers and the ground glow
        additive();
        embers(f, toRel, camera);
        sprites(f, toRel, camera, true);
        // 7. the burst
        if (f.exp && c.hitPos != null) {
            explosion(c, f, camera, time);
        }
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
    }

    private static void additive() {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false);
    }

    private static void put(BufferBuilder b, Matrix4f m, Vector3d p, float u, float v) {
        b.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setUv(u, v);
    }

    private static void draw(ShaderInstance shader, BufferBuilder b) {
        MeshData mesh = b.build();
        if (mesh == null) {
            return;
        }
        RenderSystem.setShader(() -> shader);
        BufferUploader.drawWithShader(mesh);
    }

    // Two crossed strips fixed on the curve (a real 3D flame, never turned to the camera), as the preview's Ribbon.
    private static void ribbon(Strip s, Matrix4f toRel, float time, boolean halo) {
        ShaderInstance sh = FxShaders.fugaFlame;
        sh.safeGetUniform("FTime").set(time);
        sh.safeGetUniform("Seed").set(s.seed());
        sh.safeGetUniform("Alpha").set(s.alpha());
        sh.safeGetUniform("Grow").set(s.grow());
        sh.safeGetUniform("Heat").set(0f);
        sh.safeGetUniform("Len").set(s.len());
        sh.safeGetUniform("Halo").set(halo ? 1f : 0f);
        sh.safeGetUniform("Tail").set(0.45f);
        sh.safeGetUniform("Flow").set(3.6f);
        sh.safeGetUniform("Root").set(0f);
        Vector3d[] P = s.pts();
        int n = P.length - 1;
        Vector3d[] a1 = new Vector3d[n + 1], b1 = new Vector3d[n + 1], a2 = new Vector3d[n + 1], b2 = new Vector3d[n + 1];
        for (int i = 0; i <= n; i++) {
            Vector3d tg = new Vector3d(P[Math.min(n, i + 1)]).sub(P[Math.max(0, i - 1)]).normalize();
            Vector3d ref = Math.abs(tg.y) > 0.9 ? v(1, 0, 0) : v(0, 1, 0);
            Vector3d s1 = new Vector3d(tg).cross(ref).normalize();
            Vector3d s2 = new Vector3d(tg).cross(s1).normalize();
            double hw = s.widths()[i] * 0.5 * 1.9;
            a1[i] = add(P[i], s1, -hw);
            b1[i] = add(P[i], s1, hw);
            a2[i] = add(P[i], s2, -hw);
            b2[i] = add(P[i], s2, hw);
        }
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        for (Vector3d[][] strip : new Vector3d[][][] {{a1, b1}, {a2, b2}}) {
            for (int i = 0; i < n; i++) {
                float u0 = i / (float) n, u1 = (i + 1) / (float) n;
                put(b, toRel, strip[0][i], u0, 0);
                put(b, toRel, strip[0][i + 1], u1, 0);
                put(b, toRel, strip[1][i + 1], u1, 1);
                put(b, toRel, strip[1][i], u0, 1);
            }
        }
        draw(sh, b);
    }

    // frame (columns x, y, z at origin o) in local space
    private static Matrix4f frame(Vector3d o, Vector3d x, Vector3d y, Vector3d z) {
        return new Matrix4f((float) x.x, (float) x.y, (float) x.z, 0, (float) y.x, (float) y.y, (float) y.z, 0, (float) z.x, (float) z.y, (float) z.z, 0,
                (float) o.x, (float) o.y, (float) o.z, 1);
    }

    private static void volume(Cast c, Frame f, Matrix4f toRel, Vec3 camera, float time) {
        ShaderInstance sh = FxShaders.fugaVol;
        Matrix4f local = frame(f.tail, f.ax, f.zv, f.yv);
        Matrix4f fromFrame = new Matrix4f(toRel).mul(local);
        Matrix4f toFrame = new Matrix4f(fromFrame).invert();
        float L = (float) f.len;
        float[] mn = {-2.75f, -1.15f, -1.15f}, mx = {L + 0.85f, 1.25f, 1.15f};
        sh.safeGetUniform("ToFrame").set(toFrame);
        sh.safeGetUniform("FromFrame").set(fromFrame);
        sh.safeGetUniform("ToNoise").set(local);
        sh.safeGetUniform("Ball").set((float) f.ballX, (float) f.ballY, (float) f.ballZ);
        sh.safeGetUniform("BR").set((float) f.br);
        sh.safeGetUniform("M").set((float) f.m);
        sh.safeGetUniform("Fx").set(c.fx);
        sh.safeGetUniform("Fy").set(c.fy);
        sh.safeGetUniform("L").set(L);
        sh.safeGetUniform("FTime").set(time);
        sh.safeGetUniform("Bmin").set(mn[0], mn[1], mn[2]);
        sh.safeGetUniform("Bmax").set(mx[0], mx[1], mx[2]);
        box(sh, fromFrame, mn, mx);
    }

    // The faces of a box (min, max in its own frame m -> camera-relative world) that face away from the camera: the ray
    // through each of their pixels crosses the whole box in front of it.
    private static void box(ShaderInstance sh, Matrix4f m, float[] mn, float[] mx) {
        org.joml.Vector3f[] v = new org.joml.Vector3f[8];
        for (int i = 0; i < 8; i++) {
            v[i] = m.transformPosition(new org.joml.Vector3f((i & 1) == 0 ? mn[0] : mx[0], (i & 2) == 0 ? mn[1] : mx[1], (i & 4) == 0 ? mn[2] : mx[2]));
        }
        org.joml.Vector3f centre = new org.joml.Vector3f();
        for (org.joml.Vector3f p : v) {
            centre.add(p);
        }
        centre.div(8);
        int[][] faces = {{0, 2, 6, 4}, {1, 5, 7, 3}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 1, 3, 2}, {4, 6, 7, 5}};
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        int count = 0;
        for (int[] face : faces) {
            org.joml.Vector3f fc = new org.joml.Vector3f(v[face[0]]).add(v[face[1]]).add(v[face[2]]).add(v[face[3]]).div(4);
            org.joml.Vector3f out = new org.joml.Vector3f(fc).sub(centre);
            // the camera is at 0: a back face is one whose outward side looks away from it
            if (out.dot(fc) <= 0) {
                continue;
            }
            for (int k : face) {
                b.addVertex(v[k].x, v[k].y, v[k].z);
            }
            count++;
        }
        if (count == 0) {
            b.build();
            return;
        }
        draw(sh, b);
    }

    private static void planes(Frame f, Matrix4f toRel, float time, boolean halo) {
        ShaderInstance sh = FxShaders.fugaArrow;
        double X0 = -0.7, W = 0.62, len = f.len - X0 + 0.15;
        sh.safeGetUniform("FTime").set(time);
        sh.safeGetUniform("L").set((float) f.len);
        sh.safeGetUniform("Grow").set((float) f.grow);
        sh.safeGetUniform("Fist").set((float) f.fist);
        sh.safeGetUniform("Alpha").set(1f);
        sh.safeGetUniform("Halo").set(halo ? 1f : 0f);
        sh.safeGetUniform("W").set((float) W);
        sh.safeGetUniform("X0").set((float) X0);
        Vector3d cen = add(f.planeTail, f.planeAx, X0 + len / 2);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        for (Vector3d across : new Vector3d[] {f.planeZv, f.planeYv}) {
            Vector3d xs = new Vector3d(f.planeAx).mul(len / 2), ys = new Vector3d(across).mul(W);
            put(b, toRel, new Vector3d(cen).sub(xs).sub(ys), 0, 0);
            put(b, toRel, new Vector3d(cen).add(xs).sub(ys), 1, 0);
            put(b, toRel, new Vector3d(cen).add(xs).add(ys), 1, 1);
            put(b, toRel, new Vector3d(cen).sub(xs).add(ys), 0, 1);
        }
        draw(sh, b);
    }

    // Soft glows: facing the camera (the fist), or lying on the ground (under him).
    private static void sprites(Frame f, Matrix4f toRel, Vec3 camera, boolean ground) {
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        int count = 0;
        for (Sprite s : f.sprites) {
            if (s.ground() != ground) {
                continue;
            }
            org.joml.Vector3f c = toRel.transformPosition(new org.joml.Vector3f((float) s.at().x, (float) s.at().y, (float) s.at().z));
            org.joml.Vector3f r, u;
            if (ground) {
                r = new org.joml.Vector3f((float) s.size() / 2, 0, 0);
                u = new org.joml.Vector3f(0, 0, (float) s.size() / 2);
            } else {
                org.joml.Vector3f[] ax = billboard(c);
                r = ax[0].mul((float) s.size() / 2);
                u = ax[1].mul((float) s.size() / 2);
            }
            quad(b, c, r, u, s.r(), s.g(), s.b(), s.a());
            count++;
        }
        if (count == 0) {
            b.build();
            return;
        }
        draw(FxShaders.fugaGlow, b);
    }

    private static void embers(Frame f, Matrix4f toRel, Vec3 camera) {
        if (f.embers.isEmpty()) {
            return;
        }
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        org.joml.Vector3f[] ax = null;
        for (double[] e : f.embers) {
            org.joml.Vector3f c = toRel.transformPosition(new org.joml.Vector3f((float) e[0], (float) e[1], (float) e[2]));
            if (ax == null) {
                ax = billboard(c);
            }
            float h = (float) e[3] / 2;
            quad(b, c, new org.joml.Vector3f(ax[0]).mul(h), new org.joml.Vector3f(ax[1]).mul(h), (float) cl(e[4], 0, 1), 0, 0, 1);
        }
        draw(FxShaders.fugaGlow, b);
    }

    // The camera's right and up (sprites face it).
    private static org.joml.Vector3f[] billboard(org.joml.Vector3f at) {
        var cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        org.joml.Vector3f left = cam.getLeftVector();
        org.joml.Vector3f up = cam.getUpVector();
        return new org.joml.Vector3f[] {new org.joml.Vector3f(left).negate(), new org.joml.Vector3f(up)};
    }

    private static void quad(BufferBuilder b, org.joml.Vector3f c, org.joml.Vector3f r, org.joml.Vector3f u, float cr, float cg, float cb, float ca) {
        b.addVertex(c.x - r.x - u.x, c.y - r.y - u.y, c.z - r.z - u.z).setUv(0, 0).setColor(cr, cg, cb, ca);
        b.addVertex(c.x + r.x - u.x, c.y + r.y - u.y, c.z + r.z - u.z).setUv(1, 0).setColor(cr, cg, cb, ca);
        b.addVertex(c.x + r.x + u.x, c.y + r.y + u.y, c.z + r.z + u.z).setUv(1, 1).setColor(cr, cg, cb, ca);
        b.addVertex(c.x - r.x + u.x, c.y - r.y + u.y, c.z - r.z + u.z).setUv(0, 1).setColor(cr, cg, cb, ca);
    }

    private static void explosion(Cast c, Frame f, Vec3 camera, float time) {
        Vec3 ctr = c.hitPos;
        float R = (float) Math.max(0.05, f.expR), rise = (float) f.expRise;
        // the floor's light under the fire (the preview's point light on the ground), then the fire itself
        additive();
        if (f.expLight > 0.01) {
            float k = (float) Math.min(1, f.expLight / 7.2) * 0.35f;
            BufferBuilder g = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            Vec3 o = ctr.subtract(camera).add(0, -0.17, 0);
            quad(g, new org.joml.Vector3f((float) o.x, (float) o.y, (float) o.z), new org.joml.Vector3f(14, 0, 0), new org.joml.Vector3f(0, 0, 14), k,
                    k * 0x8a / 255f, k * 0x30 / 255f, 0);
            draw(FxShaders.fugaGlow, g);
        }
        if (f.ringR > 0 && f.ringA > 0) {
            ShaderInstance rs = FxShaders.fugaRing;
            rs.safeGetUniform("R").set((float) f.ringR);
            rs.safeGetUniform("A").set((float) f.ringA);
            rs.safeGetUniform("FTime").set(time);
            float h = (float) f.ringR + 1;
            Vec3 o = ctr.subtract(camera).add(0, -0.17, 0);
            BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            b.addVertex((float) o.x - h, (float) o.y, (float) o.z - h).setUv(-h, -h);
            b.addVertex((float) o.x + h, (float) o.y, (float) o.z - h).setUv(h, -h);
            b.addVertex((float) o.x + h, (float) o.y, (float) o.z + h).setUv(h, h);
            b.addVertex((float) o.x - h, (float) o.y, (float) o.z + h).setUv(-h, h);
            draw(rs, b);
        }
        RenderSystem.disableBlend();
        RenderSystem.depthMask(false);
        ShaderInstance sh = FxShaders.fugaExp;
        Vec3 cam = camera.subtract(ctr);
        float[] mn = {-R * 1.9f, -0.2f, -R * 1.9f}, mx = {R * 1.9f, rise + R * 1.8f, R * 1.9f};
        sh.safeGetUniform("Cam").set((float) cam.x, (float) cam.y, (float) cam.z);
        sh.safeGetUniform("R").set(R);
        sh.safeGetUniform("Rise").set(rise);
        sh.safeGetUniform("Burn").set((float) f.expBurn);
        sh.safeGetUniform("Hot").set((float) f.expHot);
        sh.safeGetUniform("Fy").set(time * 4 * 0.35f);
        sh.safeGetUniform("Ground").set(-0.2f);
        sh.safeGetUniform("Bmin").set(mn[0], mn[1], mn[2]);
        sh.safeGetUniform("Bmax").set(mx[0], mx[1], mx[2]);
        Vec3 o = ctr.subtract(camera);
        box(sh, new Matrix4f().translation((float) o.x, (float) o.y, (float) o.z), mn, mx);
    }
}
