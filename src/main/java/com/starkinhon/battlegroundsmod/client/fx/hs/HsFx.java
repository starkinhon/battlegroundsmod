package com.starkinhon.battlegroundsmod.client.fx.hs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import com.starkinhon.battlegroundsmod.client.fx.hs.HsAssets.Part;
import com.starkinhon.battlegroundsmod.client.fx.hs.WoodGeo.Geo;
import com.starkinhon.battlegroundsmod.client.fx.hs.WoodGeo.Rng;
import com.starkinhon.battlegroundsmod.power.powers.hashirama.HsNet;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

// Hashirama's techniques on the client, the approved page's scenes (forja/hashirama-boss: hashirama.js, skills4.js,
// fan.js) replayed from the server's events: same wood, same seeds, same timeline (seconds since the event arrived).
// The page's frame is put where the server says (origin on the ground, page -z along the aim, 0.9 blocks a unit).
public final class HsFx {
    static final RandomSource RNG = RandomSource.createThreadSafe();
    static final float PU = HsWood.PU;
    static final String WD_WOOD = "#b48a5a", WD_DARK = "#5a3a20";
    static final String[] WOODC = {"#b48a5a", "#9c7448", "#c89d68", "#8a6440", "#d2ae7c"};

    // One technique playing.
    abstract static class Fx {
        int id;
        Vec3 pos, dir;
        double born;
        double yaw;
        float bright = 1;

        // false = finished
        abstract boolean draw(double t, Vec3 cam);

        void event(int kind, Vec3 p, Vec3 d, float a, float b, float c, double now) {
        }

        Matrix4f frame(Vec3 cam) {
            return HsWood.frame(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z, yaw);
        }

        // a world direction in this frame's page axes
        Vector3d toPage(Vec3 d) {
            double c = Math.cos(-yaw), s = Math.sin(-yaw);
            return new Vector3d(d.x * c + d.z * s, d.y, -d.x * s + d.z * c);
        }

        // a page point of this frame in the world
        Vec3 toWorld(double x, double y, double z) {
            double c = Math.cos(yaw), s = Math.sin(yaw);
            return pos.add((x * c + z * s) * PU, y * PU, (-x * s + z * c) * PU);
        }
    }

    private static final List<Fx> LIVE = new ArrayList<>();
    private static final Map<Long, Fx> BY_ID = new HashMap<>();
    // the wooden arm of the Lance, by Hashirama's entity id: when it started (seconds), when it goes (or -1)
    static final Map<Integer, double[]> ARMS = new HashMap<>();
    static float sleep, sleepShown;
    private static double nowS;

    private HsFx() {
    }

    public static boolean handles(int kind) {
        return kind >= HsNet.FIRST && kind <= HsNet.LAST;
    }

    static double yawOf(Vec3 d) {
        return Math.atan2(-d.x, -d.z);
    }

    private static long key(int kind, int id) {
        return (long) kind << 32 | (id & 0xffffffffL);
    }

    public static void receive(int kind, int id, Vec3 pos, Vec3 dir, float a, float b, float c) {
        double now = seconds();
        if (kind == HsNet.SLEEP) {
            sleep = a;
            return;
        }
        if (kind == HsNet.SPLINTERS) {
            Debris.splinters(pos, dir, (int) a);
            return;
        }
        if (kind == HsNet.LANCE && a == 0) {
            ARMS.put(id, new double[] {now, -1});
            return;
        }
        // events for something already playing
        int owner = kind == HsNet.SLAM ? HsNet.BUDDHA : kind;
        Fx live = BY_ID.get(key(owner, id));
        boolean fresh = switch (kind) {
            case HsNet.PRISON, HsNet.DOME -> a == 0;
            case HsNet.DRAGON -> a == 0;
            case HsNet.BUDDHA -> a == 0 || live == null;
            case HsNet.SLAM -> false;
            case HsNet.FLOWERS -> c == 0;
            default -> true;
        };
        if (!fresh) {
            if (live != null) {
                live.event(kind, pos, dir, a, b, c, now);
            }
            return;
        }
        Fx f = switch (kind) {
            case HsNet.FOREST -> new Forest();
            case HsNet.PRISON -> new Prison(b > 0.5f);
            case HsNet.STAKES -> new Stakes((int) c, a, b);
            case HsNet.FLOWERS -> new Flowers();
            case HsNet.DOME -> new Dome();
            case HsNet.LANCE -> new Lance(id, b > 0.5f);
            case HsNet.DRAGON -> new HsDragon();
            case HsNet.BUDDHA -> new HsBuddha();
            default -> null;
        };
        if (f == null) {
            return;
        }
        f.id = id;
        f.pos = pos;
        f.dir = dir;
        f.born = now;
        Vec3 flat = new Vec3(dir.x, 0, dir.z);
        f.yaw = kind == HsNet.LANCE || kind == HsNet.DRAGON || flat.lengthSqr() < 1.0E-6 ? 0 : yawOf(flat);
        if (live != null) {
            LIVE.remove(live);
        }
        LIVE.add(f);
        BY_ID.put(key(kind, id), f);
        f.event(kind, pos, dir, a, b, c, now);
    }

    static double seconds() {
        Minecraft mc = Minecraft.getInstance();
        float partial = mc.getTimer().getGameTimeDeltaPartialTick(false);
        return (com.starkinhon.battlegroundsmod.client.fx.ScreenFx.now() + partial) / 20.0;
    }

    public static boolean active() {
        return !LIVE.isEmpty() || Debris.active() || !ARMS.isEmpty();
    }

    public static void clear() {
        LIVE.clear();
        BY_ID.clear();
        ARMS.clear();
        Debris.clear();
        sleep = 0;
    }

    // Once a frame (FxWorld), before HsWood draws what was queued.
    public static void render(Vec3 cam) {
        nowS = seconds();
        for (Iterator<Fx> it = LIVE.iterator(); it.hasNext();) {
            Fx f = it.next();
            f.bright = HsWood.bright(f.pos.add(0, 1, 0));
            boolean on;
            try {
                on = f.draw(nowS - f.born, cam);
            } catch (RuntimeException e) {
                com.starkinhon.battlegroundsmod.BattlegroundsMod.LOGGER.error("Hashirama effect failed", e);
                on = false;
            }
            if (!on) {
                it.remove();
                BY_ID.values().remove(f);
            }
        }
        ARMS.values().removeIf(a -> a[1] > 0 && nowS > a[1] + 1);
        Debris.draw(cam, nowS);
    }

    static double now() {
        return nowS;
    }

    private static final net.minecraft.resources.ResourceLocation VEIL = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
            com.starkinhon.battlegroundsmod.BattlegroundsMod.MODID, "textures/fx/vignette.png");

    // Mundo das Árvores Floridas: the pollen's sleep closing in from the edges (the page's sleep veil), under the HUD.
    public static void renderGui(net.minecraft.client.gui.GuiGraphics g, net.minecraft.client.DeltaTracker delta) {
        sleepShown += (sleep - sleepShown) * 0.15f;
        if (sleep <= 0 && sleepShown < 0.01f) {
            sleepShown = 0;
            return;
        }
        float v = Mth.clamp(sleepShown, 0, 1), edge = Math.min(1, v * 1.5f), mid = (float) (Math.pow(v, 1.4) * 0.92 * Math.max(0, (v - 0.3) / 0.7));
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        g.setColor(0.03f, 0.02f, 0.05f, edge);
        g.blit(VEIL, 0, 0, 0, 0, g.guiWidth(), g.guiHeight(), g.guiWidth(), g.guiHeight());
        g.setColor(1, 1, 1, 1);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        if (mid > 0.004f) {
            g.fill(0, 0, g.guiWidth(), g.guiHeight(), (int) (mid * 255) << 24 | 0x060410);
        }
    }

    // ---------- shared drawing ----------

    static void part(Part p, Matrix4f m, double grow, float dis, float bright) {
        if (grow <= 0) {
            return;
        }
        HsWood.draw(p.g(), p.m(), m, HsWood.grow(p.m(), grow), dis, bright);
    }

    // The ground warning (the page's makeCracks): mode 0 cone, 1 square, 2 circle; a quad of half size `size` round (cx, cz).
    static void crack(Matrix4f m, float cx, float cz, float size, float ox, float oz, float dx, float dz, float front, float tan, float mode, float half, float alpha, float seed) {
        if (alpha <= 0.01f) {
            return;
        }
        HsWood.Tris t = new HsWood.Tris();
        t.ground(cx - size, cz - size, cx + size, cz + size, 0.035f);
        HsWood.imm(4, m, t, false, sh -> {
            sh.safeGetUniform("Crack").set(ox, oz, dx, dz);
            sh.safeGetUniform("Crack2").set(front, tan, mode, half);
            sh.safeGetUniform("Alpha").set(alpha);
            sh.safeGetUniform("Seed").set(seed);
        });
    }

    // The hand's shadow (the page's makeShadow) at page (x, z), radius r, closing k.
    static void shadow(Matrix4f m, float x, float z, float r, float k, float seed) {
        HsWood.Tris t = new HsWood.Tris();
        t.ground(x - r, z - r, x + r, z + r, 0.05f);
        HsWood.imm(5, m, t, false, sh -> {
            sh.safeGetUniform("Grow").set(k);
            sh.safeGetUniform("Seed").set(seed);
            sh.safeGetUniform("Alpha").set(1f);
        });
    }

    // Camera right and up in the world (for motes).
    static Vector3f[] camAxes() {
        Camera c = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vector3f left = new Vector3f(c.getLeftVector()), up = new Vector3f(c.getUpVector());
        return new Vector3f[] {left.negate(), up};
    }

    static double ground(Vec3 at) {
        net.minecraft.client.multiplayer.ClientLevel lv = Minecraft.getInstance().level;
        if (lv == null) {
            return at.y - 2;
        }
        int h = lv.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(at.x), Mth.floor(at.z));
        return h <= at.y + 2 ? h : at.y - 3;
    }

    // ---------- the page's stake (skills4.js stake): a sharp stake with its own bend, thickness and grain ----------

    record Built(Geo g, HsWood.Mat m, Vector3d tip) {
    }

    static Built stake(Rng r, Vector3d base, Vector3d dir, double len, double r0, double seed, Vector3d bend, String col) {
        int n = 10;
        Vector3d[] pts = WoodGeo.bentPath(base, dir, len, bend != null ? bend : new Vector3d(), n, seed, 0.22);
        double[] rad = new double[n];
        for (int i = 0; i < n; i++) {
            double u = (double) i / (n - 1);
            rad[i] = i == n - 1 ? 0 : r0 * Math.pow(1 - u, 0.85) * (1 + 0.08 * Math.sin(i * 2.1 + seed));
        }
        double dh = (r.next() - 0.5) * 0.03, ds = (r.next() - 0.5) * 0.08, dl = (r.next() - 0.5) * 0.08;
        float[] c = WoodGeo.offsetHSL(WoodGeo.hex(col != null ? col : WD_WOOD), dh, ds, dl);
        HsWood.Mat m = HsWood.Mat.tube(seed % 60, WD_WOOD, WD_DARK).tip(0.18).fine(30);
        m.wood = c;
        Geo g = WoodGeo.tubeGeo(pts, rad, 9, 6.283 * Math.max(rad[0], 0.2));
        return new Built(g, m, pts[n - 1]);
    }

    static Vector3d v(double x, double y, double z) {
        return new Vector3d(x, y, z);
    }

    // ================= Nascimento da Floresta =================
    static final class Forest extends Fx {
        static final double LIFE = 20, FADE = 1.5;
        final float seed = RNG.nextFloat() * 30;

        @Override
        boolean draw(double t, Vec3 cam) {
            if (t > LIFE + FADE) {
                return false;
            }
            Matrix4f m = frame(cam);
            float dis = (float) WoodGeo.smooth((t - LIFE) / FADE);
            for (Part p : HsAssets.get("forest")) {
                double lt = t - p.f("t0", 0);
                if (lt < 0) {
                    continue;
                }
                part(p, m, WoodGeo.easeOut3((lt - p.f("at", 0)) / p.f("dur", 0.3f)), dis, bright);
            }
            float front = (float) Math.min(26, t * 15 + 1.2);
            float alpha = (float) (1 - WoodGeo.smooth((t - 2.6) / 1.2));
            crack(m, 0, -13, 14, 0, 0, 0, -1, front, 0.3f, 0, 2, alpha, seed);
            return true;
        }
    }

    // ================= Prisão dos Quatro Pilares =================
    static final class Prison extends Fx {
        final boolean empty;
        final float seed = RNG.nextFloat() * 30;
        double breakAt = -1, goneAt = -1;

        Prison(boolean empty) {
            this.empty = empty;
        }

        @Override
        void event(int kind, Vec3 p, Vec3 d, float a, float b, float c, double now) {
            double t = now - born;
            if (a == 1 && breakAt < 0) {
                breakAt = t;
                Matrix4f none = new Matrix4f();
                for (Part q : HsAssets.get("prison")) {
                    int n = q.h().has("pillar") && q.h().get("pillar").getAsBoolean() ? 14 : 6;
                    for (int i = 0; i < n; i++) {
                        int k = RNG.nextInt(q.g().nv);
                        float y = q.g().c[k * 3 + 1];
                        if (y < 0) {
                            continue;
                        }
                        Vec3 w = toWorld(q.g().c[k * 3], y, q.g().c[k * 3 + 2]);
                        Vec3 out = new Vec3(w.x - pos.x, 0, w.z - pos.z);
                        out = out.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : out.normalize();
                        Debris.add(w, out.scale((5 + RNG.nextDouble() * 7) * PU).add(0, (3 + RNG.nextDouble() * 6) * PU, 0),
                                (0.15f + 0.45f * RNG.nextFloat() * RNG.nextFloat()) * PU, Debris.wood());
                    }
                }
            } else if (a == 2 && goneAt < 0) {
                goneAt = t;
            }
        }

        @Override
        boolean draw(double t, Vec3 cam) {
            if (breakAt >= 0 || t > 9) {
                return false;
            }
            Matrix4f m = frame(cam);
            double tb = t - 0.9;
            float dis = 0;
            if (empty) {
                dis = (float) WoodGeo.smooth((tb - 0.9) / 0.8);
            }
            if (goneAt >= 0) {
                dis = Math.max(dis, (float) WoodGeo.smooth((t - goneAt) / 0.8));
            }
            if (dis >= 1) {
                return false;
            }
            if (tb > 0) {
                for (Part p : HsAssets.get("prison")) {
                    part(p, m, WoodGeo.easeOut3((tb - p.f("at", 0)) / p.f("dur", 0.3f)), dis, bright);
                }
            }
            float alpha = tb < 0 ? 1 : (float) (1 - WoodGeo.smooth(tb / 0.6));
            crack(m, 0, 0, 4.2f, 0, 0, 0, -1, (float) (t / 0.5), 0.3f, 1, 1.9f, alpha, seed);
            return true;
        }
    }

    // ================= Estacas (cluster, finale, the sleeper's stake) =================
    static final class Stakes extends Fx {
        final int type;
        final float big;
        final long seed;
        final List<Built> list = new ArrayList<>();
        final List<double[]> times = new ArrayList<>();

        Stakes(int type, float big, float seed) {
            this.type = type;
            this.big = big;
            this.seed = (long) seed;
        }

        @Override
        void event(int kind, Vec3 p, Vec3 d, float a, float b, float c, double now) {
            if (type == 1) {
                Rng r = new Rng(777);
                for (int i = 0; i < 11; i++) {
                    double an = i / 11.0 * 6.283 + r.next() * 0.4;
                    Vector3d dd = v(Math.cos(an), 0.9 + 0.8 * r.next(), Math.sin(an)).normalize();
                    double len = 4 + 2.5 * r.next(), r0 = 0.32 + 0.16 * r.next();
                    list.add(stake(r, v(0, -0.6, 0), dd, len, r0, 500 + i * 17, v(Math.cos(an), -0.4, Math.sin(an)).mul(0.4), null));
                    times.add(new double[] {r.next() * 0.06, 0.14});
                }
            } else if (type == 2) {
                Rng r = new Rng(4);
                list.add(stake(r, v(0, -0.8, 0), v(0.15, 1, 0.1), 4.5, 0.42, 321, null, null));
                times.add(new double[] {0, 0.14});
            } else {
                Rng r = new Rng(seed);
                int n = 3 + (int) Math.floor(r.next() * 3);
                Vector3d dirP = v(0, 0, -1), side = v(1, 0, 0);
                for (int i = 0; i < n; i++) {
                    Vector3d bb = new Vector3d(side).mul((r.next() - 0.5) * 0.9 * big).add(new Vector3d(dirP).mul((r.next() - 0.5) * 0.6));
                    Vector3d dd = new Vector3d(dirP).mul(0.25 + 0.45 * r.next()).add(new Vector3d(side).mul((r.next() - 0.5) * 1.1)).add(0, 1, 0).normalize();
                    double len = (2.0 + 2.0 * r.next()) * big, r0 = (0.16 + 0.14 * r.next()) * big, sd = 100 + i * 13 + r.next() * 900;
                    list.add(stake(r, bb.setComponent(1, -0.4), dd, len, r0, sd, new Vector3d(dirP).mul(0.25), null));
                    times.add(new double[] {i * 0.018, 0.1 + 0.05 * r.next()});
                }
                Debris.splinters(pos.add(0, 0.3 * PU, 0), new Vec3(0, (4 + 2 * big) * PU, 0), 3 + Math.round(2 * big));
            }
        }

        @Override
        boolean draw(double t, Vec3 cam) {
            double hold = type == 2 ? 2.4 : 2.3;
            if (t > hold + 0.8) {
                return false;
            }
            Matrix4f m = frame(cam);
            float dis = (float) WoodGeo.smooth((t - hold) / 0.8);
            for (int i = 0; i < list.size(); i++) {
                Built s = list.get(i);
                double[] tt = times.get(i);
                double g = WoodGeo.easeOut3((t - tt[0]) / tt[1]);
                if (g > 0) {
                    HsWood.draw(s.g(), s.m(), m, HsWood.grow(s.m(), g), dis, bright);
                }
            }
            return true;
        }
    }

    // ================= Cúpula de Madeira =================
    static final class Dome extends Fx {
        double creakAt = -1, counterAt = -1, openAt = -1;
        final List<Built> spikes = new ArrayList<>();
        final List<double[]> times = new ArrayList<>();

        @Override
        void event(int kind, Vec3 p, Vec3 d, float a, float b, float c, double now) {
            double t = now - born;
            if (a == 1 && creakAt < 0) {
                creakAt = t;
            } else if (a == 2 && counterAt < 0) {
                counterAt = t;
                Vector3d n = toPage(d).setComponent(1, 0);
                if (n.lengthSquared() < 1.0E-6) {
                    n.set(0, 0, -1);
                }
                n.normalize();
                Rng r = new Rng(6262);
                double RD = 1.9;
                for (int i = 0; i < 9; i++) {
                    Vector3d sp = v((r.next() - 0.5) * 1.3, (r.next() - 0.3) * 0.9, (r.next() - 0.5) * 1.3);
                    Vector3d dd = new Vector3d(n).add(sp.mul(0.7)).add(0, 0.15, 0).normalize();
                    Vector3d bb = new Vector3d(dd).mul(RD * 0.8);
                    bb.y = Math.max(0.3, dd.y * RD + 0.4 + r.next() * 0.8);
                    double len = 3 + 2.2 * r.next(), r0 = 0.2 + 0.12 * r.next();
                    spikes.add(stake(r, bb, dd, len, r0, 900 + i * 11, v(0, -0.2, 0), null));
                    times.add(new double[] {r.next() * 0.05, 0.1});
                }
            } else if (a == 3 && openAt < 0) {
                openAt = t;
            }
        }

        @Override
        boolean draw(double t, Vec3 cam) {
            if (t > 8 || openAt >= 0 && t > openAt + 0.6) {
                return false;
            }
            Matrix4f m = frame(cam);
            double k = openAt >= 0 ? WoodGeo.smooth((t - openAt) / 0.5) : 0;
            double creak = creakAt >= 0 && counterAt < 0 ? Math.min(1, (t - creakAt) / 0.4) : 0;
            int i = 0;
            for (Part p : HsAssets.get("dome")) {
                if ("core".equals(p.s("role"))) {
                    if (t > 0.3 && k < 0.5) {
                        HsWood.draw(p.g(), p.m(), m, 2, 0, bright);
                    }
                    continue;
                }
                double g = WoodGeo.easeOut3((t - p.f("at", 0)) / p.f("dur", 0.2f));
                if (openAt >= 0) {
                    g = Math.min(g, 1 - k);
                }
                Matrix4f mm = m;
                if (creak > 0) {
                    float s = (float) (1 + 0.04 * Math.sin(now() * 40 + i) * creak);
                    mm = new Matrix4f(m).scale(s);
                }
                part(p, mm, g, 0, bright);
                i++;
            }
            if (counterAt >= 0) {
                for (int j = 0; j < spikes.size(); j++) {
                    Built s = spikes.get(j);
                    double g = WoodGeo.easeOut3((t - counterAt - times.get(j)[0]) / times.get(j)[1]);
                    if (g > 0) {
                        HsWood.draw(s.g(), s.m(), m, HsWood.grow(s.m(), g), (float) k, bright);
                    }
                }
            }
            return true;
        }
    }

    // ================= Lança Ramificada =================
    static final class Lance extends Fx {
        final int boss;
        Built main;
        final List<Built> branches = new ArrayList<>();
        final List<Double> lags = new ArrayList<>();
        final List<Built> barbs = new ArrayList<>();
        final List<double[]> barbAt = new ArrayList<>();

        Lance(int boss, boolean caught) {
            this.boss = boss;
        }

        @Override
        void event(int kind, Vec3 p, Vec3 d, float a, float b, float c, double now) {
            // page frame at the hand, not turned: T0 = the point the branches close on
            Vector3d H = v(0, 0, 0), T0 = v((d.x - p.x) / PU, (d.y - p.y) / PU, (d.z - p.z) / PU);
            Vector3d dir = new Vector3d(T0).sub(H).normalize();
            double dist = H.distance(T0);
            Vector3d S = new Vector3d(H).fma(Math.min(7.5, dist * 0.5), dir);
            Rng r = new Rng(8080);
            Vector3d[] mp = WoodGeo.bentPath(H, dir, H.distance(S) + 0.3, v(0, 0, 0), 10, 11, 0.06);
            double[] mr = WoodGeo.taper(10, 0.34, 0.26, 1);
            main = new Built(WoodGeo.tubeGeo(mp, mr, 12, 6.283 * 0.34), HsWood.Mat.tube(4, WD_WOOD, WD_DARK).tip(0.1).fine(34), null);
            Vector3d up = v(0, 1, 0), sx = new Vector3d(dir).cross(up).normalize(), sy = new Vector3d(sx).cross(dir).normalize();
            for (int i = 0; i < 6; i++) {
                double an = i / 6.0 * 6.283 + r.next() * 0.4;
                Vector3d rad = new Vector3d(sx).mul(Math.cos(an)).fma(Math.sin(an), sy);
                Vector3d off = new Vector3d(rad).mul(0.25 + 0.3 * r.next());
                Vector3d end = new Vector3d(T0).add(off);
                double spread = 2.0 + 1.2 * r.next();
                Vector3d seg = new Vector3d(end).sub(S);
                Vector3d[] ctrl = {new Vector3d(S), new Vector3d(S).fma(0.22, seg).fma(spread * 0.8, rad), new Vector3d(S).fma(0.55, seg).fma(spread, rad),
                        new Vector3d(S).fma(0.85, seg).fma(spread * 0.35, rad), new Vector3d(end), new Vector3d(end).fma(2.2, dir).fma(-0.6, rad).add(0, -0.9, 0)};
                Vector3d[] P = new WoodGeo.Catmull(ctrl, 0.5).spaced(23);
                double r0 = 0.2 + 0.06 * r.next();
                double[] rr = new double[24];
                for (int k = 0; k < 24; k++) {
                    rr[k] = k == 23 ? 0 : r0 * Math.pow(1 - k / 23.0, 0.7) * (1 + 0.07 * Math.sin(k * 1.7 + i));
                }
                Built br = new Built(WoodGeo.tubeGeo(P, rr, 9, 6.283 * Math.max(r0, 0.2)), HsWood.Mat.tube(20 + i * 5, WD_WOOD, WD_DARK).tip(0.1).fine(30), null);
                for (int j = 0; j < 2; j++) {
                    int k = 8 + (int) Math.floor(r.next() * 10);
                    Vector3d dd = new Vector3d(P[k + 1]).sub(P[k]).normalize().fma(0.8 + 0.5 * r.next(), rad).normalize();
                    double len = 0.7 + 0.6 * r.next();
                    barbs.add(stake(r, P[k], dd, len, r0 * 0.45, 60 + i * 7 + j, v(0, 0, 0), null));
                    barbAt.add(new double[] {k / 23.0});
                }
                branches.add(br);
                lags.add(r.next() * 0.05);
            }
            double[] arm = ARMS.get(boss);
            if (arm != null) {
                arm[1] = now + 2.7;
            }
        }

        @Override
        boolean draw(double t, Vec3 cam) {
            if (main == null || t > 2.8) {
                return false;
            }
            Matrix4f m = HsWood.frame(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z, 0);
            float dis = (float) WoodGeo.smooth((t - 2.0) / 0.7);
            HsWood.draw(main.g(), main.m(), m, HsWood.grow(main.m(), WoodGeo.easeOut3(t / 0.16)), dis, bright);
            int bi = 0;
            for (int i = 0; i < branches.size(); i++) {
                double k = WoodGeo.clamp((t - 0.14 - lags.get(i)) / 0.26, 0, 1);
                Built br = branches.get(i);
                double g = k * (2 - k) * 0.25 + k * 0.75;
                if (g > 0) {
                    HsWood.draw(br.g(), br.m(), m, HsWood.grow(br.m(), g), dis, bright);
                }
                for (int j = 0; j < 2; j++, bi++) {
                    Built s = barbs.get(bi);
                    double gg = WoodGeo.easeOut3((k - barbAt.get(bi)[0]) / 0.2);
                    if (gg > 0) {
                        HsWood.draw(s.g(), s.m(), m, HsWood.grow(s.m(), gg), dis, bright);
                    }
                }
            }
            return true;
        }
    }

    // The wooden arm (the Lance's warning) for the boss renderer: grow 0..1 and dissolve, or null.
    public static float[] arm(int entityId) {
        double[] a = ARMS.get(entityId);
        if (a == null) {
            return null;
        }
        double now = seconds(), t = now - a[0];
        float grow = (float) WoodGeo.smooth((t - 0.05) / 0.45);
        float dis = a[1] > 0 ? (float) WoodGeo.smooth((now - a[1] + 0.7) / 0.7) : 0;
        if (a[1] < 0 && t > 6) {
            dis = (float) WoodGeo.smooth((t - 6) / 0.7);
        }
        return dis >= 1 ? null : new float[] {grow, dis};
    }

    private static Geo armGeo;
    private static HsWood.Mat armMat;

    // Queues the wooden arm in the arm's own space (pose: model space of the right arm, camera relative).
    public static void drawArm(Matrix4f armPose, float grow, float dis, float bright) {
        if (armGeo == null) {
            armGeo = WoodGeo.tubeGeo(new Vector3d[] {v(0, 0.05, 0), v(0.01, -0.3, 0.01), v(-0.01, -0.62, 0), v(0, -0.86, -0.02)}, new double[] {0.24, 0.22, 0.21, 0.17}, 10,
                    6.283 * 0.24);
            armMat = HsWood.Mat.tube(2, "#a77d50", WD_DARK).tip(0.25).fine(26);
        }
        // the player model's arm hangs along +y (model space is upside down): page arm (down = -y) flipped
        Matrix4f m = new Matrix4f(armPose).translate(-0.0625f, -0.08f, 0).scale(PU, -PU, PU);
        HsWood.draw(armGeo, armMat, m, HsWood.grow(armMat, grow), dis, bright);
    }

    // ================= Mundo das Árvores Floridas =================
    static final class Flowers extends Fx {
        static final int NP = 1600;
        static final double RF = 7.5;
        final float[][] ps = new float[NP][5];
        double endAt = -1;

        Flowers() {
            for (float[] s : ps) {
                for (int k = 0; k < 5; k++) {
                    s[k] = RNG.nextFloat();
                }
            }
        }

        @Override
        void event(int kind, Vec3 p, Vec3 d, float a, float b, float c, double now) {
            if (c == 1 && endAt < 0) {
                endAt = now - born;
            }
        }

        @Override
        boolean draw(double t, Vec3 cam) {
            double life = 9.5;
            if (endAt >= 0) {
                life = Math.min(life, endAt + 1);
            }
            if (t > life) {
                return false;
            }
            Matrix4f m = frame(cam);
            float dis = (float) WoodGeo.smooth((t - (life - 1)) / 1.0);
            List<Part> parts = HsAssets.get("flowers");
            Map<Integer, Vector3d> heads = new HashMap<>();
            for (Part p : parts) {
                double lt = t - p.f("t0", 0);
                String role = p.s("role");
                int fl = (int) p.f("flower", 0);
                switch (role) {
                    case "stem" -> part(p, m, WoodGeo.easeOut3(lt / 0.55), dis, bright);
                    case "leaf" -> part(p, m, WoodGeo.easeOut3((lt - 0.35 - p.f("li", 0) * 0.08) / 0.4), dis, bright);
                    default -> {
                        double sc = Math.max(0.001, WoodGeo.easeOut3((lt - 0.4) / 0.35));
                        if (sc <= 0.002) {
                            continue;
                        }
                        float[] hq = p.arr("hq");
                        Matrix4f hm = new Matrix4f(m).translate(p.f("hx", 0), p.f("hy", 0), p.f("hz", 0)).rotate(new Quaternionf(hq[0], hq[1], hq[2], hq[3])).scale((float) sc);
                        heads.put(fl, new Vector3d(p.f("hx", 0), p.f("hy", 0), p.f("hz", 0)));
                        if ("petal".equals(role)) {
                            double k = WoodGeo.easeOut3((lt - 1.3 - p.f("lag", 0)) / 0.7), sway = 0.03 * Math.sin(now() * 1.1 + p.f("lag", 0) * 30);
                            double closed = p.f("closed", 0.15f), open = p.f("open", 1.2f);
                            double rx = -(Math.PI / 2 - (closed + (open - closed) * k)) + sway;
                            hm.rotateY(p.f("rotY", 0)).translate(0, p.f("py", 0), 0).rotateX((float) rx);
                        }
                        HsWood.draw(p.g(), p.m(), hm, 2, dis, bright);
                    }
                }
            }
            // the pollen (from 2.3 s): each grain with its own path out of a flower's heart
            double tp = t - 2.3;
            if (tp > 0 && !heads.isEmpty() && dis < 1) {
                Vector3f[] ax = camAxes();
                double c = Math.cos(yaw), s = Math.sin(yaw);
                HsWood.Tris tr = new HsWood.Tris();
                int nf = heads.size();
                Vector3d[] hs = heads.values().toArray(new Vector3d[0]);
                for (int i = 0; i < NP; i++) {
                    float[] q = ps[i];
                    double age = tp - q[3] * 2.2;
                    if (age < 0) {
                        continue;
                    }
                    Vector3d o = hs[i % nf];
                    double an = q[0] * 6.283, sp = 1.8 + 3.2 * q[1], e = 1 - Math.exp(-age * 0.9);
                    double x = o.x + Math.cos(an) * sp * e * 1.9 + Math.sin(age * (0.7 + q[2]) + q[4] * 9) * 0.9 + Math.sin(age * 2.3 + q[3] * 7) * 0.25;
                    double y = Math.max(0.15 + q[2] * 0.6, o.y - age * (0.35 + 0.5 * q[3]) + 0.8 * e);
                    double z = o.z + Math.sin(an) * sp * e * 1.9 + Math.cos(age * (0.6 + q[4]) + q[0] * 9) * 0.9;
                    double edge = 1 - WoodGeo.smooth((Math.hypot(x, z) - RF * 0.8) / (RF * 0.35));
                    double al = WoodGeo.smooth(age / 0.4) * (0.55 + 0.45 * q[4]) * (1 - WoodGeo.smooth((tp - 5.2) / 1.5)) * edge * (1 - dis);
                    if (al < 0.01) {
                        continue;
                    }
                    float wx = (float) ((x * c + z * s) * PU + pos.x - cam.x), wy = (float) (y * PU + pos.y - cam.y), wz = (float) ((-x * s + z * c) * PU + pos.z - cam.z);
                    float size = (0.09f + 0.28f * q[2] * q[2]) * PU * 0.5f;
                    tr.sprite(wx, wy, wz, size, ax[0].x, ax[0].y, ax[0].z, ax[1].x, ax[1].y, ax[1].z, 1f, 0.78f, 0.25f, (float) al);
                }
                HsWood.imm(6, new Matrix4f(), tr, false, sh -> {
                    sh.safeGetUniform("Tint").set(1f, 0.97f, 0.75f);
                    sh.safeGetUniform("Alpha").set(0.55f);
                });
            }
            return true;
        }
    }

    // ================= loose splinters and clods (the page's debris) =================
    static final class Debris {
        static final class P {
            Vec3 p, v;
            float s, age, life, spin;
            float[] col;
            Quaternionf q = new Quaternionf();
            Vector3f axis;
            boolean rest;
            double floor;
        }

        static final List<P> L = new ArrayList<>();
        static double last = -1;

        static float[] wood() {
            float[] c = WoodGeo.hex(WOODC[RNG.nextInt(5)]);
            float k = 0.85f + 0.3f * RNG.nextFloat();
            return new float[] {c[0] * k, c[1] * k, c[2] * k};
        }

        static void add(Vec3 at, Vec3 vel, float size, float[] col) {
            if (L.size() >= 400) {
                L.remove(0);
            }
            P p = new P();
            p.p = at;
            p.v = vel;
            p.s = size;
            p.col = col;
            p.life = 4 + RNG.nextFloat() * 2;
            p.q.rotationXYZ(RNG.nextFloat() * 6, RNG.nextFloat() * 6, RNG.nextFloat() * 6);
            p.axis = new Vector3f(RNG.nextFloat() - 0.5f, RNG.nextFloat() - 0.5f, RNG.nextFloat() - 0.5f).normalize();
            p.spin = 3 + RNG.nextFloat() * 11;
            p.floor = ground(at);
            L.add(p);
        }

        // the page's splinters(pos, n, pow, spread): dir's length = pow (blocks/s)
        static void splinters(Vec3 at, Vec3 dir, int n) {
            double pow = Math.max(1, dir.length());
            for (int i = 0; i < n; i++) {
                double a = RNG.nextDouble() * 6.283, sp = 0.5 * PU;
                Vec3 p = at.add((RNG.nextDouble() - 0.5) * sp, RNG.nextDouble() * sp, (RNG.nextDouble() - 0.5) * sp);
                Vec3 v = new Vec3(Math.cos(a) * pow * (0.3 + RNG.nextDouble()), pow * (0.4 + RNG.nextDouble()), Math.sin(a) * pow * (0.3 + RNG.nextDouble()));
                add(p, v, (0.12f + 0.4f * RNG.nextFloat() * RNG.nextFloat()) * PU, wood());
            }
        }

        static boolean active() {
            return !L.isEmpty();
        }

        static void clear() {
            L.clear();
        }

        static void draw(Vec3 cam, double now) {
            float dt = last < 0 ? 0 : (float) Math.max(0, Math.min(0.1, now - last));
            last = now;
            if (L.isEmpty()) {
                return;
            }
            HsWood.Tris t = new HsWood.Tris();
            float[] m = new float[9];
            Matrix4f none = new Matrix4f();
            for (Iterator<P> it = L.iterator(); it.hasNext();) {
                P p = it.next();
                p.age += dt;
                if (p.age > p.life) {
                    it.remove();
                    continue;
                }
                if (!p.rest) {
                    p.v = p.v.add(0, -20 * PU * dt, 0);
                    p.p = p.p.add(p.v.scale(dt));
                    p.q.premul(new Quaternionf().rotationAxis(p.spin * dt, p.axis));
                    if (p.p.y - p.s * 0.5 < p.floor && p.v.y < 0) {
                        p.p = new Vec3(p.p.x, p.floor + p.s * 0.5, p.p.z);
                        if (Math.abs(p.v.y) < 2.6 * PU) {
                            p.rest = true;
                        } else {
                            p.v = new Vec3(p.v.x * 0.5, -p.v.y * 0.3, p.v.z * 0.5);
                            p.spin *= 0.5f;
                        }
                    }
                }
                float shrink = p.age > p.life - 0.6f ? (p.life - p.age) / 0.6f : 1;
                org.joml.Matrix3f r = new org.joml.Matrix3f().rotation(p.q);
                m[0] = r.m00;
                m[1] = r.m10;
                m[2] = r.m20;
                m[3] = r.m01;
                m[4] = r.m11;
                m[5] = r.m21;
                m[6] = r.m02;
                m[7] = r.m12;
                m[8] = r.m22;
                float h = p.s * 0.5f * shrink;
                t.cube((float) (p.p.x - cam.x), (float) (p.p.y - cam.y), (float) (p.p.z - cam.z), h, h, h, m, p.col[0], p.col[1], p.col[2]);
            }
            float br = HsWood.bright(cam);
            HsWood.imm(2, none, t, true, sh -> {
                sh.safeGetUniform("Bright").set(br);
                sh.safeGetUniform("Tint").set(0f, 0f, 0f);
            });
        }
    }
}
