package com.starkinhon.battlegroundsmod.power.powers.kokushibo;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.phys.Vec3;

// Kokushibo's Moon Breathing forms 2, 3, 5 and 6, exactly as the 3D pages Stark approved
// (forja/kokushibo-formas/moon3d.html, form23.js, form6.js): this is the pages' script, line by line, with the same
// random numbers (mulberry32) for the same seed, so the client draws (client/fx/MoonFormsRenderer) and the server cuts
// (MoonFormSkill) the very same thing.
// Units are blocks. Local space as on the pages: Kokushibo's feet at the origin, y up, +z where he faces, +x on his left.
public final class MoonForms {
    private MoonForms() {
    }

    // ---------------- what a form looks like at one moment ----------------

    // A cut: points along it (s 0..1), the direction across it at each one (+ = its outer edge), half its width, how far
    // the point has gone and the tail burnt (in s), its fade, a seed for its streaks, how long it is compared to the 1st
    // form's (for the streaks) and its shape (0 a cut that grows, 1 a whole crescent blade, 2 the streak behind a blade).
    public static final class Ribbon {
        public final double[][] p, a;
        public final double hw, head, tail, bandA, seed, len;
        public final int shape;

        Ribbon(double[][] p, double[][] a, double hw, double head, double tail, double bandA, double seed, double len, int shape) {
            this.p = p;
            this.a = a;
            this.hw = hw;
            this.head = head;
            this.tail = tail;
            this.bandA = bandA;
            this.seed = seed;
            this.len = len;
            this.shape = shape;
        }

        // Whether the point s of the cut is drawn right now (between its burnt tail and its point).
        public boolean drawn(double s) {
            if (bandA <= 0.05 || shape == 2) {
                return false;
            }
            return s < head - 0.02 && s > tail + 0.02;
        }
    }

    // A crescent moon (a billboard): where, its radius, turn, thinness, alpha, how far it has dissolved, the cut of its
    // hollow and its index (seeds its texture).
    public record Moon(double[] p, double r, double rot, double thin, double a, double diss, double cut, int idx) {
    }

    public record Spark(double[] p, double z, double a) {
    }

    public record Frame(List<Ribbon> ribbons, List<Moon> moons, List<Spark> sparks, double flash, double shake) {
    }

    public abstract static class Form {
        public double end;
        // Drawn in the awakened colours (lilac cuts with a black edge, blue moons): the forms of the stretched sword.
        public boolean pal;
        // Its shape depends on the world as it plays (the 16th follows its targets): it can only be asked for its frames
        // in order, as time goes on, never ahead.
        boolean live;
        // The moons it leaves hanging after it: none (it leaves its own), how far they spread out from him (14th: 2x the
        // area, Stark) and how many at most (-1: the usual).
        boolean noLinger;
        double lingerSpread = 1;
        int lingerMax = -1;

        Form(double end) {
            this.end = end;
        }

        public abstract Frame frame(double t);
    }

    // The awakened forms of the stretched sword (MoonFormsAwakened).
    public static boolean awakened(int form) {
        return form >= 7;
    }

    public static Form create(int form, int seed) {
        return switch (form) {
            case 2 -> new Form2(seed);
            case 3 -> new Form3(seed);
            case 5 -> new Form5(seed);
            case 7 -> new MoonFormsAwakened.Form7(seed);
            case 8 -> new MoonFormsAwakened.Form8(seed);
            case 9 -> new MoonFormsAwakened.Form9(seed);
            case 10 -> new MoonFormsAwakened.Form10(seed);
            case 14 -> new MoonFormsAwakened.Form14(seed);
            case 16 -> new MoonFormsAwakened.Form16(seed, new double[0][], i -> null);
            default -> new Form6(seed);
        };
    }

    // The form with its moons left hanging in the air after it (luas-no-ar.html, Stark 2026-11-07); full: the full moon's.
    public static Linger create(int form, int seed, boolean full) {
        return new Linger(create(form, seed), full);
    }

    // The 16th Form, homing on its targets (their feet in local space when it starts, and where each one is now: null
    // once it is gone). No targets: the cuts fall in front of him.
    public static Linger create16(int seed, boolean full, double[][] targets, java.util.function.IntFunction<double[]> targetAt) {
        return new Linger(new MoonFormsAwakened.Form16(seed, targets, targetAt), full);
    }

    // ---------------- moons that stay in the air (Stark 2026-11-07, luas-no-ar.html) ----------------
    // After the form, part of its moons stop where the attack left them (slowing down from the speed they had), hang there
    // bobbing and slowly turning, and cut whoever touches them; then they break into light like any moon. So the area they
    // hold is the very shape the attack drew. At the full moon more of them stay, bigger and for longer.
    public static final class Linger extends Form {
        private static final double STEP = 1 / 20.0;

        private static final class Held {
            int idx;
            double[] p, prev, v;
            double r, rot, thin, cut, at, prevAt, spin, bob, gone;
        }

        private final Form base;

        // The form under its hanging moons.
        public Form base() {
            return base;
        }
        private final List<Held> held = new ArrayList<>();
        private final java.util.Set<Integer> ids = new java.util.HashSet<>();

        Linger(Form base, boolean full) {
            // the awakened forms' moons stay longer (Stark: 6.5 s on their pages); some forms leave none of these
            super(base.end + (base.noLinger ? 0 : base.pal ? (full ? 8.0 : 6.5) : full ? 5.5 : 4.0));
            this.base = base;
            this.pal = base.pal;
            this.live = base.live;
            this.spread = base.lingerSpread;
            double share = full ? .6 : .4, grow = full ? 1.35 : 1;
            int max = base.noLinger ? 0 : base.lingerMax > 0 ? (full ? base.lingerMax * 5 / 4 : base.lingerMax) : full ? 64 : 40;
            this.grow = grow;
            this.share = share;
            this.max = max;
            if (base.live) {
                // its moons are only known as it plays: they are picked as they show up (stream below)
                return;
            }
            java.util.LinkedHashMap<Integer, Held> seen = new java.util.LinkedHashMap<>();
            for (int i = 0; i * STEP <= base.end; i++) {
                double tt = i * STEP;
                for (Moon m : base.frame(tt).moons()) {
                    if (m.diss() > 0) {
                        continue;
                    }
                    Held was = seen.get(m.idx());
                    Held h = new Held();
                    h.idx = m.idx();
                    h.p = m.p().clone();
                    h.prev = was != null ? was.p : m.p().clone();
                    h.r = m.r();
                    h.rot = m.rot();
                    h.thin = m.thin();
                    h.cut = m.cut();
                    h.at = tt;
                    seen.put(m.idx(), h);
                }
            }
            for (Held h : seen.values()) {
                if (held.size() >= max) {
                    break;
                }
                if (hash01(h.idx * 7 + 3) < share) {
                    held.add(h);
                }
            }
            for (Held h : held) {
                h.v = mul(sub(h.p, h.prev), 1 / STEP);
                double sp = Math.hypot(Math.hypot(h.v[0], h.v[1]), h.v[2]);
                if (sp > 12) {
                    h.v = mul(h.v, 12 / sp);
                }
                h.spin = (hash01(h.idx) - .5) * 1.2;
                h.bob = hash01(h.idx + 11) * 6.28;
                h.gone = end - .7 - hash01(h.idx + 5) * 1.2;
                ids.add(h.idx);
            }
        }

        private final double grow, share, spread;
        private final int max;
        private final java.util.Map<Integer, Held> byIdx = new java.util.HashMap<>();

        // Whether this moon is one left hanging, and already hanging by t (no longer part of the attack itself).
        public boolean hanging(int idx, double t) {
            if (!ids.contains(idx)) {
                return false;
            }
            Held h = byIdx.get(idx);
            if (h != null) {
                return t > h.at;
            }
            for (Held x : held) {
                if (x.idx == idx) {
                    return t > x.at;
                }
            }
            return false;
        }

        // A live form: each moon that would be left hanging is followed while the attack still holds it (its last place,
        // the one before and when), and hangs from where it was last seen once the attack lets go of it.
        private void stream(Frame fr, double t) {
            for (Moon m : fr.moons()) {
                if (m.diss() > 0) {
                    continue;
                }
                Held h = byIdx.get(m.idx());
                if (h == null) {
                    if (held.size() >= max || hash01(m.idx() * 7 + 3) >= share) {
                        continue;
                    }
                    h = new Held();
                    h.idx = m.idx();
                    h.prev = m.p().clone();
                    h.prevAt = t;
                    h.spin = (hash01(h.idx) - .5) * 1.2;
                    h.bob = hash01(h.idx + 11) * 6.28;
                    h.gone = end - .7 - hash01(h.idx + 5) * 1.2;
                    held.add(h);
                    ids.add(h.idx);
                    byIdx.put(h.idx, h);
                } else if (t > h.at) {
                    h.prev = h.p;
                    h.prevAt = h.at;
                }
                h.p = m.p().clone();
                h.at = t;
                h.r = m.r();
                h.rot = m.rot();
                h.thin = m.thin();
                h.cut = m.cut();
                double dt = h.at - h.prevAt;
                h.v = dt > 1.0E-4 ? mul(sub(h.p, h.prev), 1 / dt) : v(0, 0, 0);
                double sp = Math.hypot(Math.hypot(h.v[0], h.v[1]), h.v[2]);
                if (sp > 12) {
                    h.v = mul(h.v, 12 / sp);
                }
            }
        }

        @Override
        public Frame frame(double t) {
            Frame fr = t <= base.end ? base.frame(t) : new Frame(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), 0, 0);
            if (live) {
                stream(fr, t);
            }
            List<Moon> ms = new ArrayList<>();
            for (Moon m : fr.moons()) {
                if (!ids.contains(m.idx()) || m.diss() <= 0) {
                    ms.add(m);
                }
            }
            for (Held h : held) {
                if (!(t > h.at && t < h.gone + .45)) {
                    continue;
                }
                double k = t - h.at, tau = .22;
                double[] drift = mul(h.v, tau * (1 - Math.exp(-k / tau)));
                // spread out from him (the 14th: over 2x the area, Stark)
                double sp = (spread - 1) * outCubic(Math.min(1, k / 1.6));
                double[] p = add(add(add(h.p, v(h.p[0] * sp, 0, h.p[2] * sp)), drift), v(0, Math.sin(k * 1.6 + h.bob) * .12 + .06 * k, 0));
                double diss = Math.max(0, Math.min(1, (t - h.gone) / .45));
                double g = 1 + (grow - 1) * Math.min(1, k / .35);
                ms.add(new Moon(p, h.r * g * (1 + diss * .12), h.rot + h.spin * k, h.thin, 1, diss, h.cut, h.idx));
            }
            return new Frame(fr.ribbons(), ms, fr.sparks(), fr.flash(), fr.shake());
        }

        // The page's hash01 (Math.imul and >>> as ints).
        static double hash01(int n) {
            int x = (n ^ 0x9e3779b9) * 0x85ebca6b;
            x ^= x >>> 13;
            x *= 0xc2b2ae35;
            x ^= x >>> 16;
            return (x & 0xFFFFFFFFL) / 4294967296.0;
        }
    }

    // Local (the pages' space) to world: feet at origin, facing fwd, left and up.
    public record Basis(Vec3 origin, Vec3 left, Vec3 up, Vec3 fwd) {
        public Vec3 world(double[] p) {
            return new Vec3(origin.x + left.x * p[0] + up.x * p[1] + fwd.x * p[2], origin.y + left.y * p[0] + up.y * p[1] + fwd.y * p[2],
                    origin.z + left.z * p[0] + up.z * p[1] + fwd.z * p[2]);
        }

        // World to local (the axes are at right angles and of length 1).
        public double[] local(Vec3 w) {
            Vec3 d = w.subtract(origin);
            return new double[] {d.dot(left), d.dot(up), d.dot(fwd)};
        }

        public Vec3 dir(double[] p) {
            return new Vec3(left.x * p[0] + up.x * p[1] + fwd.x * p[2], left.y * p[0] + up.y * p[1] + fwd.y * p[2], left.z * p[0] + up.z * p[1] + fwd.z * p[2]);
        }
    }

    // The 2nd, 3rd and 5th forms follow his turning only; the 6th (a barrage thrown at the target) his look up and down too.
    public static Basis basis(int form, Vec3 feet, Vec3 look) {
        Vec3 l = look.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : look.normalize();
        Vec3 flat = new Vec3(l.x, 0, l.z);
        flat = flat.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : flat.normalize();
        Vec3 fwd = form == 6 ? l : flat;
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 left = up.cross(fwd);
        left = left.lengthSqr() < 1.0E-6 ? new Vec3(flat.z, 0, -flat.x) : left.normalize();
        Vec3 u = fwd.cross(left).normalize();
        return new Basis(feet, left, u, fwd);
    }

    // ---------------- the pages' little maths ----------------

    static final double D = Math.PI / 180;

    static final class Rng {
        private int seed;

        Rng(int seed) {
            this.seed = seed;
        }

        // mulberry32, as on the pages
        double next() {
            seed += 0x6D2B79F5;
            int t = (seed ^ seed >>> 15) * (1 | seed);
            t = t + (t ^ t >>> 7) * (61 | t) ^ t;
            return ((t ^ t >>> 14) & 0xFFFFFFFFL) / 4294967296.0;
        }
    }

    static double[] v(double x, double y, double z) {
        return new double[] {x, y, z};
    }

    static double[] add(double[] a, double[] b) {
        return v(a[0] + b[0], a[1] + b[1], a[2] + b[2]);
    }

    static double[] sub(double[] a, double[] b) {
        return v(a[0] - b[0], a[1] - b[1], a[2] - b[2]);
    }

    static double[] mul(double[] a, double s) {
        return v(a[0] * s, a[1] * s, a[2] * s);
    }

    static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    static double[] cross(double[] a, double[] b) {
        return v(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]);
    }

    static double[] norm(double[] a) {
        double l = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
        if (l == 0) {
            l = 1;
        }
        return v(a[0] / l, a[1] / l, a[2] / l);
    }

    static double[] lerp(double[] a, double[] b, double t) {
        return v(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t);
    }

    static double clamp01(double x) {
        return Math.max(0, Math.min(1, x));
    }

    static double outCubic(double x) {
        return 1 - Math.pow(1 - x, 3);
    }

    static double inOut(double x) {
        return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
    }

    static double outBack(double x) {
        double c1 = 2.2, c3 = c1 + 1;
        return 1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2);
    }

    static final double[] UP = {0, 1, 0};

    // =====================================================================================================
    // 5th form, GEPPAKU SAIKA: he does not swing. Seven curved cuts appear stacked one over the other around him, each a
    // spiral opening outwards, banked like a hurricane, and the whole vortex keeps turning around him. A defence: anyone
    // who walks into it is hit and thrown out.
    // =====================================================================================================
    static final class Form5 extends Form {
        static final int NCUT = 7;
        static final double RIN = 1.7, ROUT = 6.2, SPIN = 4.4;

        static final class Cut {
            double y, th0, turns, r0, r1, bank, wob, hw, t0, tail, seed;
        }

        record M(Cut c, double u, double off, double lift, double r, double rot, double spin, double thin, double cut, double delay, double die, int idx) {
        }

        record S(Cut c, double u, double off, double z, double life, double delay) {
        }

        final List<Cut> cuts = new ArrayList<>();
        final List<M> moons = new ArrayList<>();
        final List<S> sparks = new ArrayList<>();

        Form5(int seed) {
            super(3.95);
            Rng r = new Rng(seed);
            for (int k = 0; k < NCUT; k++) {
                double f = k / (double) (NCUT - 1);
                Cut c = new Cut();
                c.y = .35 + f * 3.1 + (r.next() - .5) * .2;
                c.th0 = k * 2.39 + r.next() * .4;
                c.turns = 1.05 + r.next() * .25;
                c.r0 = RIN + f * .9 + r.next() * .3;
                c.r1 = ROUT - (1 - f) * .9 - r.next() * .4;
                c.bank = (18 + r.next() * 14) * D;
                c.wob = .25 + r.next() * .25;
                c.hw = .38 + f * .06;
                c.t0 = .10 + k * .045 + r.next() * .02;
                c.tail = 2.75 + k * .04 + r.next() * .05;
                c.seed = r.next() * 10;
                cuts.add(c);
            }
            for (Cut c : cuts) {
                int chains = 5;
                for (int j = 0; j < chains; j++) {
                    double u0 = (j + .2 + r.next() * .6) / chains * .94 + .03;
                    double sign = r.next() < .6 ? 1 : -1;
                    double off = sign * (c.hw * 1.6 + r.next() * .5);
                    double size = .42 + Math.pow(r.next(), 1.3) * .5, du = 0;
                    int n = 2 + (int) Math.floor(r.next() * 2.2);
                    for (int i = 0; i < n; i++) {
                        double u = Math.min(.99, u0 + du);
                        double lift = (r.next() - .5) * .5, rot = r.next() * 6.28, spin = (r.next() - .5) * 1.2;
                        double thin = .42 + r.next() * .22, cut = .97 + r.next() * .06, delay = r.next() * .04;
                        double die = c.tail + .1 + u0 * .5 + r.next() * .18;
                        moons.add(new M(c, u, off, lift, size, rot, spin, thin, cut, delay, die, moons.size()));
                        du += size * .55 / ((c.r0 + c.r1) * .5 * c.turns * Math.PI * 2) + r.next() * .01;
                        off += (r.next() - .5) * size * 1.2;
                        size *= .62 + r.next() * .18;
                    }
                }
            }
            for (int i = 0; i < 40; i++) {
                Cut c = cuts.get(i % NCUT);
                double u = r.next() * .98 + .01, off = (r.next() - .5) * 1.4, z = .07 + r.next() * .12, life = .25 + r.next() * .45, delay = r.next() * 1.8;
                sparks.add(new S(c, u, off, z, life, delay));
            }
        }

        static double spinAt(double t) {
            return SPIN * Math.max(0, t - .1) + 1.4 * outCubic(clamp01((t - .1) / .5));
        }

        static double[][] pointOn(Cut c, double u, double t, double off, double lift) {
            double th = c.th0 + u * c.turns * Math.PI * 2 + spinAt(t);
            double rad = c.r0 + (c.r1 - c.r0) * u;
            double[] radial = v(Math.cos(th), 0, Math.sin(th));
            double[] across = norm(v(radial[0] * Math.cos(c.bank), Math.sin(c.bank), radial[2] * Math.cos(c.bank)));
            double y = c.y + c.wob * Math.sin(th * 1.5 + c.seed) + lift;
            return new double[][] {add(v(radial[0] * rad, y, radial[2] * rad), mul(across, off)), across};
        }

        static double head(Cut c, double t) {
            return outCubic(clamp01((t - c.t0) / .38));
        }

        static double tail(Cut c, double t) {
            return inOut(clamp01((t - c.tail) / .8));
        }

        static double bandA(Cut c, double t) {
            return 1 - clamp01((t - c.tail - .55) / .35);
        }

        @Override
        public Frame frame(double t) {
            List<Ribbon> rs = new ArrayList<>();
            for (Cut c : cuts) {
                double h = head(c, t);
                if (h <= 0) {
                    continue;
                }
                int n = 120;
                double[][] p = new double[n + 1][], a = new double[n + 1][];
                for (int i = 0; i <= n; i++) {
                    double[][] q = pointOn(c, i / (double) n, t, 0, 0);
                    p[i] = q[0];
                    a[i] = q[1];
                }
                rs.add(new Ribbon(p, a, c.hw, h, tail(c, t), bandA(c, t), c.seed, c.turns * 2.6, 0));
            }
            List<Moon> ms = new ArrayList<>();
            for (M m : moons) {
                Cut c = m.c();
                double born = c.t0 + .38 * (1 - Math.cbrt(1 - Math.min(m.u(), .9999))) + m.delay();
                double life = t - born;
                if (life <= 0) {
                    continue;
                }
                double[][] q = pointOn(c, m.u(), t, m.off(), m.lift());
                double diss = clamp01((t - m.die()) / .45);
                ms.add(new Moon(q[0], m.r() * outBack(clamp01(life / .16)) * (1 + diss * .12), m.rot() + m.spin() * life, m.thin(), 1, diss, m.cut(), m.idx()));
            }
            List<Spark> ss = new ArrayList<>();
            for (S s : sparks) {
                double k = (t - s.c().t0 - .2 - s.delay()) / s.life();
                if (k <= 0 || k >= 1) {
                    continue;
                }
                ss.add(new Spark(pointOn(s.c(), s.u(), t, s.off(), 0)[0], s.z(), Math.sin(Math.PI * k) * (1 - k * .4)));
            }
            double flash = Math.exp(-Math.pow((t - .16) / .06, 2)) * .5;
            double shake = Math.exp(-Math.max(0, t - .14) * 7) * clamp01((t - .1) / .05) * .10;
            return new Frame(rs, ms, ss, flash, shake);
        }
    }

    // =====================================================================================================
    // 6th form, TOKOYO KOGETSU MUKEN: a storm of crescent blades thrown in front of him in one burst. Dozens of them, from
    // small to gigantic, every one on its own curved path (rising, diving, swinging left or right), crossing each other,
    // so the whole front is full and there is no safe gap. Big blades sweep wide arcs; small ones fill the space between.
    // =====================================================================================================
    static final class Form6 extends Form {
        static final double[] TGT = {0, 0, 16};

        static final class Blade {
            int kind; // 0 giant, 1 mid, 2 small
            double R, hw, t0, fly, span, a0, spin, tilt, bulge, seed, tl, die;
            double[] S, E, Cp;
        }

        static final class M {
            Blade b;
            double s, off;
            double[] S, E, Cp;
            double t0, fly, r, rot, spin, thin, cut;
            int idx;
            boolean ride;
        }

        final List<Blade> blades = new ArrayList<>();
        final List<M> moons = new ArrayList<>();

        Form6(int seed) {
            super(2.2);
            Rng r = new Rng(seed);
            int[] kinds = new int[34];
            for (int i = 0; i < 34; i++) {
                kinds[i] = i < 6 ? 0 : i < 18 ? 1 : 2;
            }
            for (int i = 0; i < kinds.length; i++) {
                Blade b = new Blade();
                b.kind = kinds[i];
                b.R = b.kind == 0 ? 3.4 + r.next() * 2.2 : b.kind == 1 ? 1.5 + r.next() * 1.1 : .6 + r.next() * .6;
                b.hw = b.kind == 0 ? .42 + r.next() * .12 : b.kind == 1 ? .26 + r.next() * .08 : .13 + r.next() * .06;
                double sx = (r.next() - .5) * 2.4, sy = 1.0 + r.next() * 2.6, sz = 1.6 + r.next() * 1.2;
                b.S = v(sx, sy, sz);
                double spread = b.kind == 0 ? 7 : 5;
                double ex = TGT[0] + (r.next() - .5) * spread * 2;
                double ey = Math.max(.6, 1.2 + (r.next() - .3) * spread);
                double ez = TGT[2] + 4 + r.next() * 8;
                b.E = v(ex, ey, ez);
                double side = r.next() * Math.PI * 2, bend = b.kind == 0 ? 5 + r.next() * 4 : 2 + r.next() * 4;
                b.Cp = add(lerp(b.S, b.E, .45), v(Math.cos(side) * bend, Math.sin(side) * bend * .7, 0));
                b.Cp[1] = Math.max(.8, b.Cp[1]);
                b.t0 = .06 + Math.pow(r.next(), 1.3) * .5;
                b.fly = b.kind == 0 ? .75 + r.next() * .2 : .55 + r.next() * .25;
                b.span = (150 + r.next() * 70) * D;
                b.a0 = r.next() * Math.PI * 2;
                b.spin = (r.next() - .5) * 2.2;
                b.tilt = (r.next() - .5) * 70 * D;
                b.bulge = .25 + r.next() * .25;
                b.seed = r.next() * 10;
                b.tl = b.t0 + .05;
                b.die = b.tl + b.fly * 1.15;
                blades.add(b);
            }
            for (Blade b : blades) {
                int n = b.kind == 0 ? 5 : b.kind == 1 ? 2 : 0;
                for (int j = 0; j < n; j++) {
                    M m = new M();
                    m.b = b;
                    m.ride = true;
                    m.s = (j + .2 + r.next() * .6) / n;
                    double sign = r.next() < .6 ? 1 : -1;
                    m.off = sign * (b.hw * 1.8 + r.next() * .6);
                    m.r = .35 + Math.pow(r.next(), 1.4) * .6;
                    m.rot = r.next() * 6.28;
                    m.spin = r.next() - .5;
                    m.spin *= 1.5;
                    m.thin = .42 + r.next() * .22;
                    m.cut = .97 + r.next() * .06;
                    m.idx = moons.size();
                    moons.add(m);
                }
            }
            for (int j = 0; j < 30; j++) {
                M m = new M();
                double sx = (r.next() - .5) * 2, sy = 1 + r.next() * 2.5, sz = 1.8 + r.next();
                m.S = v(sx, sy, sz);
                double ex = (r.next() - .5) * 14, ey = .8 + r.next() * 6, ez = TGT[2] + r.next() * 10;
                m.E = v(ex, ey, ez);
                double cx = (r.next() - .5) * 8, cy = (r.next() - .3) * 4;
                m.Cp = add(lerp(m.S, m.E, .5), v(cx, cy, 0));
                m.t0 = .08 + r.next() * .55;
                m.fly = .5 + r.next() * .3;
                m.r = .3 + Math.pow(r.next(), 1.6) * .55;
                m.rot = r.next() * 6.28;
                m.spin = (r.next() - .5) * 2;
                m.thin = .42 + r.next() * .22;
                m.cut = .97 + r.next() * .06;
                m.idx = moons.size();
                moons.add(m);
            }
        }

        static double[] bez(Blade b, double u) {
            return lerp(lerp(b.S, b.Cp, u), lerp(b.Cp, b.E, u), u);
        }

        // centre and direction of flight
        static double[][] centreAt(Blade b, double t) {
            double u = Math.max(0, (t - b.tl) / b.fly);
            if (u <= 1) {
                return new double[][] {bez(b, u), norm(sub(bez(b, Math.min(1, u + .02)), bez(b, Math.max(0, u - .02))))};
            }
            double[] tan = norm(sub(b.E, b.Cp));
            return new double[][] {add(b.E, mul(tan, (u - 1) * dot(sub(b.E, b.Cp), tan) * 2)), tan};
        }

        // N, E1, E2 and the turn in its own plane
        static double[][] frameOf(Blade b, double t, double[] tan) {
            double[] lr = norm(cross(UP, tan)), u = cross(tan, lr);
            double ct = Math.cos(b.tilt), st = Math.sin(b.tilt);
            double[] n = norm(add(mul(tan, ct), mul(u, st)));
            double[] e1 = norm(cross(UP, n)), e2 = cross(n, e1);
            double rot = b.a0 + b.spin * Math.max(0, t - b.tl);
            return new double[][] {n, e1, e2, {rot}};
        }

        static double[][] pointOn(Blade b, double s, double t, double off, double[][] c) {
            double[][] fr = frameOf(b, t, c[1]);
            double a = fr[3][0] + (s - .5) * b.span;
            double[] radial = add(mul(fr[1], Math.cos(a)), mul(fr[2], Math.sin(a)));
            double mid = Math.cos((s - .5) * b.span);
            double[] p = add(add(c[0], mul(radial, b.R)), mul(fr[0], b.R * b.bulge * (mid - .6)));
            double[] across = norm(add(radial, mul(fr[0], .25)));
            return new double[][] {add(p, mul(across, off)), across};
        }

        static double head(Blade b, double t) {
            return outCubic(clamp01((t - b.t0) / (b.kind == 0 ? .14 : .09))) * 1.08;
        }

        static double tail(Blade b, double t) {
            return inOut(clamp01((t - b.die) / .3));
        }

        static double bandA(Blade b, double t) {
            return 1 - clamp01((t - b.die - .22) / .2);
        }

        @Override
        public Frame frame(double t) {
            List<Ribbon> rs = new ArrayList<>();
            for (Blade b : blades) {
                double h = head(b, t);
                if (h <= 0 || bandA(b, t) <= 0) {
                    continue;
                }
                double[][] c = centreAt(b, t);
                int n = b.kind == 0 ? 64 : b.kind == 1 ? 36 : 20;
                double[][] p = new double[n + 1][], a = new double[n + 1][];
                for (int i = 0; i <= n; i++) {
                    double[][] q = pointOn(b, i / (double) n, t, 0, c);
                    p[i] = q[0];
                    a[i] = q[1];
                }
                rs.add(new Ribbon(p, a, b.hw, h, tail(b, t), bandA(b, t), b.seed, b.R * b.span / 4.5, 1));
                // a short streak behind the blade, along its curved path, so the trajectories read
                if (t > b.tl + .03) {
                    int mm = 14;
                    double[][] tp = new double[mm + 1][], ta = new double[mm + 1][];
                    for (int i = 0; i <= mm; i++) {
                        double tt = Math.max(b.tl, t - .16 * (1 - i / (double) mm));
                        double[][] ci = centreAt(b, tt);
                        tp[i] = pointOn(b, .5, tt, 0, ci)[0];
                        ta[i] = norm(cross(ci[1], UP));
                    }
                    rs.add(new Ribbon(tp, ta, b.hw * .9, 1.1, 0, .45 * bandA(b, t), b.seed + 3, .6, 2));
                }
            }
            List<Moon> ms = new ArrayList<>();
            for (M m : moons) {
                double[] p;
                double life, die;
                if (m.ride) {
                    life = t - m.b.t0 - .05 - m.s * .06;
                    die = m.b.die;
                    p = pointOn(m.b, m.s, t, m.off, centreAt(m.b, t))[0];
                } else {
                    life = t - m.t0;
                    die = m.t0 + m.fly * 1.1;
                    double u = Math.max(0, life / m.fly);
                    p = u <= 1 ? lerp(lerp(m.S, m.Cp, u), lerp(m.Cp, m.E, u), u) : add(m.E, mul(sub(m.E, m.Cp), (u - 1) * 2));
                }
                if (life <= 0) {
                    continue;
                }
                double diss = clamp01((t - die) / .35);
                ms.add(new Moon(p, m.r * outBack(clamp01(life / .14)) * (1 + diss * .12), m.rot + m.spin * life, m.thin, 1, diss, m.cut, m.idx));
            }
            double flash = Math.exp(-Math.pow((t - .1) / .05, 2)) * .4;
            double shake = Math.exp(-Math.max(0, t - .08) * 6) * clamp01((t - .05) / .04) * .14;
            return new Frame(rs, ms, new ArrayList<>(), flash, shake);
        }
    }

    // =====================================================================================================
    // Shared by the 2nd and 3rd forms: a cut that is an arc in a tilted plane.
    // =====================================================================================================
    static final class Arc {
        double[] c, n, e1, e2;
        double R, r1, a0, span, hw, t0, sweep, tail, bank, spinY, seed;

        Arc init() {
            double[] ref = Math.abs(n[1]) > .9 ? v(1, 0, 0) : v(0, 1, 0);
            e1 = norm(cross(ref, n));
            e2 = cross(n, e1);
            return this;
        }

        double[][] point(double u, double t, double off, double lift) {
            double a = a0 + span * u, rad = R + (r1 - R) * u;
            double[] radial = add(mul(e1, Math.cos(a)), mul(e2, Math.sin(a)));
            double[] across = norm(add(mul(radial, Math.cos(bank)), mul(n, Math.sin(bank))));
            double[] p = add(mul(radial, rad), mul(across, off));
            p = add(p, mul(n, lift));
            double sy = spinY * Math.max(0, t - t0);
            if (sy != 0) {
                double cs = Math.cos(sy), sn = Math.sin(sy);
                p = v(cs * p[0] - sn * p[2], p[1], sn * p[0] + cs * p[2]);
                across = v(cs * across[0] - sn * across[2], across[1], sn * across[0] + cs * across[2]);
            }
            return new double[][] {add(c, p), across};
        }

        double head(double t) {
            return outCubic(clamp01((t - t0) / sweep));
        }

        double headInv(double u) {
            return t0 + sweep * (1 - Math.cbrt(1 - Math.min(u, .9999)));
        }

        double tailAt(double t) {
            return inOut(clamp01((t - tail) / .8));
        }

        double bandA(double t) {
            return 1 - clamp01((t - tail - .55) / .35);
        }

        Ribbon ribbon(double t, int n, double dy) {
            double[][] p = new double[n + 1][], a = new double[n + 1][];
            for (int i = 0; i <= n; i++) {
                double[][] q = point(i / (double) n, t, 0, 0);
                p[i] = add(q[0], v(0, dy, 0));
                a[i] = q[1];
            }
            return new Ribbon(p, a, hw, head(t), tailAt(t), bandA(t), seed, Math.abs(span) * R / 4.5, 0);
        }
    }

    static final class ArcMoon {
        Arc o;
        double u, off, lift, r, rot, spin, thin, cut, delay, die, out;
        int idx;
    }

    static List<ArcMoon> standardMoons(Rng r, List<Arc> cuts, int perCut, double sizeK) {
        List<ArcMoon> moons = new ArrayList<>();
        for (Arc o : cuts) {
            for (int j = 0; j < perCut; j++) {
                double u0 = (j + .2 + r.next() * .6) / perCut * .94 + .03;
                double sign = r.next() < .62 ? 1 : -1;
                double off = sign * (o.hw * 1.6 + r.next() * .5 * sizeK);
                double size = (.4 + Math.pow(r.next(), 1.3) * .55) * sizeK, du = 0;
                int n = 2 + (int) Math.floor(r.next() * 2.2);
                for (int i = 0; i < n; i++) {
                    ArcMoon m = new ArcMoon();
                    m.o = o;
                    m.u = Math.min(.99, u0 + du);
                    m.off = off;
                    m.lift = (r.next() - .5) * .6;
                    m.r = size;
                    m.rot = r.next() * 6.28;
                    m.spin = (r.next() - .5) * 1.2;
                    m.thin = .42 + r.next() * .22;
                    m.cut = .97 + r.next() * .06;
                    m.delay = r.next() * .04;
                    m.die = o.tail + .1 + u0 * .5 + r.next() * .18;
                    m.out = (r.next() * .5 + .5) * Math.signum(off);
                    moons.add(m);
                    du += size * .55 / (o.R * Math.abs(o.span)) + r.next() * .01;
                    off += (r.next() - .5) * size * 1.2;
                    size *= .62 + r.next() * .18;
                }
            }
        }
        for (int i = 0; i < moons.size(); i++) {
            moons.get(i).idx = i;
        }
        return moons;
    }

    static Moon standardMoonFrame(ArcMoon m, double t, double dy) {
        Arc o = m.o;
        double life = t - (o.headInv(m.u) + m.delay);
        if (life <= 0) {
            return null;
        }
        double diss = clamp01((t - m.die) / .45);
        double[][] q = o.point(m.u, t, m.off + outCubic(clamp01(life / 1.3)) * .5 * m.out, m.lift);
        return new Moon(add(q[0], v(0, dy, 0)), m.r * outBack(clamp01(life / .16)) * (1 + diss * .12), m.rot + m.spin * life, m.thin, 1, diss, m.cut, m.idx);
    }

    // A moon thrown off a cut (the 2nd form's barrage, the 3rd form's storm).
    static final class Flung {
        Arc o;
        double u, speed, rise, r, rot, spin, thin, cut, delay, life, swirl;
        double[] vel;
        int idx;
    }

    // =====================================================================================================
    // 2nd form, SHUKA NO ROGETSU: he jumps and turns in the air; three cuts loop almost all the way round him, each in its
    // own tilted plane, like the petals of a flower closing around him (a cage that guards him). The loops keep turning
    // slowly, and every cut throws a barrage of crescent moons outwards.
    // =====================================================================================================
    static final class Form2 extends Form {
        static final double JUMP = 2.4;
        final List<Arc> cuts = new ArrayList<>();
        final List<ArcMoon> moons;
        final List<Flung> barrage = new ArrayList<>();

        Form2(int seed) {
            super(3.0);
            Rng r = new Rng(seed);
            for (int k = 0; k < 3; k++) {
                double tilt = (42 + r.next() * 14) * D, yaw = (k * 120 + 25 + (r.next() - .5) * 30) * D;
                Arc o = new Arc();
                o.c = v(0, JUMP + 1.1, 0);
                o.R = 3.3 + k * .35 + r.next() * .2;
                o.r1 = o.R;
                o.n = v(Math.sin(tilt) * Math.cos(yaw), Math.cos(tilt), Math.sin(tilt) * Math.sin(yaw));
                o.a0 = r.next() * Math.PI * 2;
                o.span = -(305 + r.next() * 25) * D * (k == 1 ? -1 : 1);
                o.hw = .40 + r.next() * .06;
                o.t0 = .14 + k * .13;
                o.sweep = .26;
                o.tail = 1.75 + k * .06;
                o.bank = 15 * D;
                o.spinY = .9 * (k == 1 ? -1 : 1);
                o.seed = r.next() * 10;
                cuts.add(o.init());
            }
            moons = standardMoons(r, cuts, 5, 1);
            for (Arc o : cuts) {
                for (int i = 0; i < 18; i++) {
                    Flung b = new Flung();
                    b.o = o;
                    b.u = r.next() * .97 + .015;
                    b.speed = 6 + r.next() * 5;
                    b.rise = (r.next() - .35) * 3;
                    b.r = .32 + Math.pow(r.next(), 1.5) * .45;
                    b.rot = r.next() * 6.28;
                    b.spin = (r.next() - .5) * 2;
                    b.thin = .42 + r.next() * .22;
                    b.cut = .97 + r.next() * .06;
                    b.delay = r.next() * .08;
                    b.life = .7 + r.next() * .45;
                    b.idx = 200 + barrage.size();
                    barrage.add(b);
                }
            }
        }

        static double jumpY(double t) {
            return JUMP * Math.pow(Math.sin(Math.PI * clamp01((t - .02) / 2.1)), .6);
        }

        @Override
        public Frame frame(double t) {
            double dy = jumpY(t) - JUMP;
            List<Ribbon> rs = new ArrayList<>();
            for (Arc o : cuts) {
                if (o.head(t) > 0) {
                    rs.add(o.ribbon(t, 110, dy));
                }
            }
            List<Moon> ms = new ArrayList<>();
            for (ArcMoon m : moons) {
                Moon f = standardMoonFrame(m, t, dy);
                if (f != null) {
                    ms.add(f);
                }
            }
            for (Flung b : barrage) {
                double born = b.o.headInv(b.u) + b.delay, k = t - born;
                if (k <= 0 || k > b.life + .45) {
                    continue;
                }
                double[] pp = b.o.point(b.u, born, 0, 0)[0];
                double[] out = norm(v(pp[0], 0, pp[2]));
                double[] p = add(add(pp, v(0, jumpY(born) - JUMP, 0)), add(mul(out, b.speed * k), v(0, b.rise * k, 0)));
                double diss = clamp01((k - b.life) / .45);
                ms.add(new Moon(p, b.r * outBack(clamp01(k / .14)) * (1 + diss * .12), b.rot + b.spin * k, b.thin, 1, diss, b.cut, b.idx));
            }
            double flash = 0, shake = 0;
            for (Arc o : cuts) {
                flash = Math.max(flash, Math.exp(-Math.pow((t - o.t0 - .05) / .05, 2)) * .4);
                shake += Math.exp(-Math.max(0, t - o.t0 - .1) * 8) * clamp01((t - o.t0) / .05) * .07;
            }
            return new Frame(rs, ms, new ArrayList<>(), flash, shake);
        }
    }

    // =====================================================================================================
    // 3rd form, ENKIZUKI TSUGARI: two gigantic crescent cuts swung back and forth in front of him, lying low over the
    // ground, and a whole storm of small moons thrown off them over the area. Great destruction on the ground.
    // =====================================================================================================
    static final class Form3 extends Form {
        final List<Arc> cuts = new ArrayList<>();
        final List<ArcMoon> moons;
        final List<Flung> storm = new ArrayList<>();

        Form3(int seed) {
            super(3.0);
            Rng r = new Rng(seed);
            Arc a = new Arc();
            a.c = v(-.5, 1.0, 5.6);
            a.R = 6.9;
            a.r1 = 7.6;
            a.n = norm(v(.06, 1, -.12));
            a.a0 = 205 * D;
            a.span = -235 * D;
            a.hw = .95;
            a.t0 = .12;
            a.sweep = .34;
            a.tail = 1.65;
            a.bank = 22 * D;
            a.seed = r.next() * 10;
            cuts.add(a.init());
            Arc b = new Arc();
            b.c = v(.6, 1.9, 7.8);
            b.R = 8.2;
            b.r1 = 8.8;
            b.n = norm(v(-.05, 1, .10));
            b.a0 = -25 * D;
            b.span = 235 * D;
            b.hw = 1.05;
            b.t0 = .40;
            b.sweep = .36;
            b.tail = 1.9;
            b.bank = 26 * D;
            b.seed = r.next() * 10;
            cuts.add(b.init());
            // angles measured in the ground plane
            for (Arc o : cuts) {
                o.e1 = v(1, 0, 0);
                o.e2 = mul(norm(cross(o.n, o.e1)), -1);
            }
            moons = standardMoons(r, cuts, 8, 1.15);
            for (Arc o : cuts) {
                for (int i = 0; i < 46; i++) {
                    Flung s = new Flung();
                    s.o = o;
                    s.u = r.next() * .97 + .015;
                    double vx = (r.next() - .5) * 10, vy = .8 + r.next() * 4.5, vz = (r.next() - .5) * 10;
                    s.vel = v(vx, vy, vz);
                    s.swirl = (r.next() - .5) * 2.4;
                    s.r = .32 + Math.pow(r.next(), 1.5) * .62;
                    s.rot = r.next() * 6.28;
                    s.spin = (r.next() - .5) * 2.5;
                    s.thin = .42 + r.next() * .22;
                    s.cut = .97 + r.next() * .06;
                    s.delay = r.next() * .12;
                    s.life = 1.1 + r.next() * .7;
                    s.idx = 300 + storm.size();
                    storm.add(s);
                }
            }
        }

        @Override
        public Frame frame(double t) {
            List<Ribbon> rs = new ArrayList<>();
            for (Arc o : cuts) {
                if (o.head(t) > 0) {
                    rs.add(o.ribbon(t, 140, 0));
                }
            }
            List<Moon> ms = new ArrayList<>();
            for (ArcMoon m : moons) {
                Moon f = standardMoonFrame(m, t, 0);
                if (f != null) {
                    ms.add(f);
                }
            }
            for (Flung s : storm) {
                double born = s.o.headInv(s.u) + s.delay, k = t - born;
                if (k <= 0 || k > s.life + .45) {
                    continue;
                }
                double[] pp = s.o.point(s.u, born, 0, 0)[0];
                double ang = s.swirl * k;
                double[] off = add(mul(s.vel, k * (1 - .25 * k)), v(0, -1.2 * k * k, 0));
                double rx = Math.cos(ang) * off[0] - Math.sin(ang) * off[2], rz = Math.sin(ang) * off[0] + Math.cos(ang) * off[2];
                double[] p = v(pp[0] + rx, Math.max(.3, pp[1] + off[1]), pp[2] + rz);
                double diss = clamp01((k - s.life) / .45);
                ms.add(new Moon(p, s.r * outBack(clamp01(k / .14)) * (1 + diss * .12), s.rot + s.spin * k, s.thin, 1, diss, s.cut, s.idx));
            }
            double flash = 0, shake = 0;
            for (Arc o : cuts) {
                flash = Math.max(flash, Math.exp(-Math.pow((t - o.t0 - .05) / .05, 2)) * .45);
                shake += Math.exp(-Math.max(0, t - o.t0 - .12) * 6) * clamp01((t - o.t0) / .05) * .16;
            }
            return new Frame(rs, ms, new ArrayList<>(), flash, shake);
        }
    }
}
