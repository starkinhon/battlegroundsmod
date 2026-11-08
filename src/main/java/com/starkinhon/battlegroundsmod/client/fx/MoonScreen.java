package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.List;

import com.starkinhon.battlegroundsmod.power.powers.kokushibo.MoonForms;
import com.starkinhon.battlegroundsmod.power.powers.kokushibo.MoonTime;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

// What the screen does during the Awakened Moon forms (Kokushibo, Stark 2026-11-08: "pro cara pensar, meu Deus,
// fudeu"). A port of the approved preview's script (forja/kokushibo-formas/impacto-desperta.html, mkimpacto.py): per form
// a list of moments (impact frames one per tick, shakes, FOV punches, colour split, blur, shockwaves, the screen cut in
// two, speed lines, the night closing in, letterbox, heartbeat) anchored on the form's clock (MoonTime, the same freezes
// and slow motion the server plays). On top of the script, how close the nearest cut or moon is to you: the closer, the
// more it shakes, and one going right past you is a hit of its own. Drawn by the fx_moonscreen pass under the HUD.
public final class MoonScreen {
    private static final double TICK = 0.05;
    private static final int P = 0, I = 1, N = 2, L = 3;
    private static final int GRADE = 0, VIG = 1, LETTER = 2, CA = 3, BLUR = 4;
    private static final float[] LILAC = {.80f, .66f, 1f}, WHITE = {1, 1, 1};
    // Full effects this close to where he stands; nothing past FAR (blocks).
    private static final double NEAR = 56, FAR = 170;

    private enum K { IMP, SHAKE, FOV, CA, BLUR, FLASH, SHOCK, SPLIT, WIN, RUMBLE, LINES, HEART, BLUR_AT }

    private static final class Ev {
        K k;
        double t, t0, t1, a, d = 4, life, fin, fout, spd, ang, off;
        int ch;
        String seq;
        double[] at;
        float[] c;
        // anchored on real ticks instead (moments made on the fly, the near passes)
        double ra = Double.NaN;
    }

    public static final class Show {
        final int form;
        final MoonForms.Form shape;
        final MoonForms.Basis basis;
        final MoonTime clock;
        final double born;
        final boolean caster;
        final double weight;
        final List<Ev> evs = new ArrayList<>();
        final List<Ev> dyn = new ArrayList<>();
        double near = 1e9, prevNear = 1e9, lastPass = -99;

        Show(int form, MoonForms.Form shape, MoonForms.Basis basis, MoonTime clock, double born, boolean caster, double weight) {
            this.form = form;
            this.shape = shape;
            this.basis = basis;
            this.clock = clock;
            this.born = born;
            this.caster = caster;
            this.weight = weight;
        }

        // How close the nearest cut or moon got to the camera this frame (blocks); the caster's own are always at his
        // side, so they never count for him.
        public void near(double blocks, double now) {
            if (caster) {
                return;
            }
            near = blocks;
            double sec = (now - born) / 20.0;
            if (blocks < 4 && prevNear >= 4 && sec - lastPass > .3) {
                lastPass = sec;
                dyn.add(ev(K.IMP, b -> {
                    b.ra = sec;
                    b.seq = "I";
                    b.at = new double[] {0, 2, 0};
                }));
                dyn.add(ev(K.SHAKE, b -> {
                    b.ra = sec;
                    b.a = .75;
                    b.d = 6;
                }));
                dyn.add(ev(K.CA, b -> {
                    b.ra = sec;
                    b.a = 1.3;
                    b.life = .3;
                }));
                dyn.add(ev(K.FOV, b -> {
                    b.ra = sec;
                    b.a = 4;
                    b.life = .3;
                }));
            }
            prevNear = blocks;
        }
    }

    private static final List<Show> SHOWS = new ArrayList<>();

    private MoonScreen() {
    }

    public static Show start(int form, MoonForms.Form shape, MoonForms.Basis basis, MoonTime clock, double born, Vec3 feet, boolean caster) {
        Minecraft mc = Minecraft.getInstance();
        double d = mc.player == null ? 0 : mc.player.position().distanceTo(feet);
        double weight = Mth.clamp(1 - (d - NEAR) / (FAR - NEAR), 0, 1);
        Show s = new Show(form, shape, basis, clock, born, caster, weight);
        script(s);
        SHOWS.add(s);
        while (SHOWS.size() > 4) {
            SHOWS.remove(0);
        }
        return s;
    }

    public static void clear() {
        SHOWS.clear();
    }

    private interface Fill {
        void fill(Ev e);
    }

    private static Ev ev(K k, Fill f) {
        Ev e = new Ev();
        e.k = k;
        f.fill(e);
        return e;
    }

    // ---------------- the script, as on the preview (times in seconds of the form) ----------------
    private static void at(Show s, K k, double t, Fill f) {
        s.evs.add(ev(k, e -> {
            e.t = t;
            f.fill(e);
        }));
    }

    private static void win(Show s, int ch, double t0, double t1, double a, double fin, double fout) {
        s.evs.add(ev(K.WIN, e -> {
            e.ch = ch;
            e.t0 = t0;
            e.t1 = t1;
            e.a = a;
            e.fin = fin;
            e.fout = fout;
        }));
    }

    private static void imp(Show s, double t, String seq, double... at) {
        at(s, K.IMP, t, e -> {
            e.seq = seq;
            e.at = at;
        });
    }

    private static void shake(Show s, double t, double a, double d) {
        at(s, K.SHAKE, t, e -> {
            e.a = a;
            e.d = d;
        });
    }

    private static void timed(Show s, K k, double t, double a, double life) {
        at(s, k, t, e -> {
            e.a = a;
            e.life = life;
        });
    }

    private static void flash(Show s, double t, float[] c, double a, double life) {
        at(s, K.FLASH, t, e -> {
            e.c = c;
            e.a = a;
            e.life = life;
        });
    }

    private static void shock(Show s, double t, double spd, double a, double... at) {
        at(s, K.SHOCK, t, e -> {
            e.spd = spd;
            e.a = a;
            e.at = at;
        });
    }

    private static void lines(Show s, double t0, double t1, double a, double... at) {
        s.evs.add(ev(K.LINES, e -> {
            e.t0 = t0;
            e.t1 = t1;
            e.a = a;
            e.at = at;
        }));
    }

    private static void rumble(Show s, double t0, double t1, double a) {
        s.evs.add(ev(K.RUMBLE, e -> {
            e.t0 = t0;
            e.t1 = t1;
            e.a = a;
        }));
    }

    // a heavy hit: frames + shake + punch + colour split + blur + ring, all on the same tick
    private static void slam(Show s, double t, double power, String seq, double[] at) {
        imp(s, t, seq, at);
        shake(s, t, power, 3.2);
        timed(s, K.FOV, t, 6 + 6 * power, .55);
        timed(s, K.CA, t, 1.3 * power, .55);
        at(s, K.BLUR, t, e -> {
            e.a = .35 + .3 * power;
            e.life = .4;
            e.at = at;
        });
        shock(s, t, 1.3, power, at);
    }

    private static void script(Show s) {
        switch (s.form) {
            case 7 -> {
                // the ring closes on an ink frame and everything stops for a breath, then the floor rumbles
                win(s, GRADE, 0, 1.6, .38, .06, .6);
                win(s, VIG, 0, 1.7, .5, .06, .6);
                slam(s, .09, 1, "NNIP", new double[] {0, 2.7, 0});
                lines(s, .09, .5, .9, 0, 2.7, 0);
                rumble(s, .14, 1.4, .38);
                shock(s, .45, 1.1, .6, 0, 0, 24);
            }
            case 8 -> {
                // letterbox, slow motion, the screen itself cut in two along the huge cut, then the slam
                win(s, LETTER, 0, 1.7, 1, .12, .45);
                win(s, GRADE, 0, 1.9, .45, .08, .7);
                win(s, VIG, 0, 1.9, .5, .08, .7);
                imp(s, .10, "PP", 1.5, 2.8, 16);
                at(s, K.SPLIT, .30, e -> {
                    e.life = .7;
                    e.ang = -.12;
                    e.off = .03;
                    e.at = new double[] {1.5, 2.8, 16};
                });
                slam(s, .60, 1.1, "INIL", new double[] {1.5, 2.8, 16});
                flash(s, .60, LILAC, .45, .3);
                lines(s, .58, 1.0, .8, 1.5, 2.8, 16);
            }
            case 9 -> {
                // every claw a jolt, every 4th a strobe frame, the dark climbing; the last stops time and slams
                win(s, GRADE, .05, 1.9, .6, 1.3, .6);
                win(s, VIG, .05, 1.9, .55, 1.3, .6);
                lines(s, .08, 1.5, .55, 0, 60, 26);
                for (int i = 0; i < 26; i++) {
                    double t = .095 + i * .052;
                    shake(s, t, .22, 9);
                    timed(s, K.CA, t, .35, .12);
                    if (i % 4 == 0 && i < 24) {
                        imp(s, t, i % 8 != 0 ? "I" : "P", 0, 4, 7 + i * 1.9);
                    }
                }
                slam(s, 1.47, 1.2, "NNNIP", new double[] {0, 4, 52});
                flash(s, 1.47, WHITE, .35, .25);
            }
            case 10 -> {
                // a negative frame as the saws are born, then a tunnel of speed down the range and a grinding shake
                win(s, GRADE, 0, 3.0, .32, .1, .6);
                win(s, VIG, 0, 3.0, .45, .1, .6);
                imp(s, .10, "IN", 0, 13, 12);
                shake(s, .10, .8, 4);
                timed(s, K.FOV, .10, -8, .7);
                flash(s, .10, LILAC, .3, .2);
                rumble(s, .12, 2.9, .28);
                win(s, CA, .12, 2.8, .5, .2, .4);
                win(s, BLUR, .12, 2.6, .22, .2, .4);
                lines(s, .12, 2.6, .5, 0, 13, 190);
                s.evs.add(ev(K.BLUR_AT, e -> e.at = new double[] {0, 13, 190}));
            }
            case 14 -> {
                // six waves, six rings of bent space, each one darker; the 6th goes slow and slams
                for (int k = 0; k < 6; k++) {
                    double t = .12 + k * .30;
                    win(s, GRADE, t, 3.4, .12, .08, .8);
                    win(s, VIG, t, 3.4, .09, .08, .8);
                    shock(s, t, 1 + k * .2, .5 + k * .12, 0, 1, 0);
                    if (k < 5) {
                        shake(s, t, .32 + k * .1, 4);
                        timed(s, K.CA, t, .4 + .12 * k, .3);
                        imp(s, t, k % 2 == 1 ? "I" : "P", 0, 3, 0);
                    }
                }
                slam(s, 1.62, 1.3, "PINNL", new double[] {0, 3, 0});
                flash(s, 1.62, LILAC, .4, .3);
                lines(s, 1.6, 2.1, .9, 0, 3, 0);
            }
            case 16 -> {
                // cinema bars, the world drains, a heartbeat; the first cut in slow motion; every landing a slam; the
                // last freezes on an ink frame for a third of a second, then white
                double[][] land = MoonTime.landings(s.shape);
                if (land.length == 0) {
                    land = new double[][] {{.5, 0, 0, 26}};
                }
                double last = land[land.length - 1][0];
                win(s, LETTER, 0, last + .9, 1, .25, .6);
                win(s, GRADE, 0, last + .9, .62, .3, 1.0);
                win(s, VIG, 0, last + 1, .62, .3, 1);
                double first = land[0][0];
                s.evs.add(ev(K.HEART, e -> {
                    e.t0 = 0;
                    e.t1 = first;
                }));
                for (int i = 0; i < land.length; i++) {
                    double[] h = land[i];
                    double[] spot = {h[1], 3, h[3]};
                    if (i < land.length - 1) {
                        slam(s, h[0], .9, "NI", spot);
                        flash(s, h[0], LILAC, .25, .2);
                    } else {
                        slam(s, h[0], 1.6, "NNNNNNNIPN", spot);
                        lines(s, h[0] - .01, h[0] + .2, 1, spot);
                        flash(s, h[0] + .02, WHITE, .7, .45);
                    }
                }
            }
            default -> {
            }
        }
    }

    // ---------------- this frame ----------------
    static final class State {
        int imp = -1;
        double shake, fov, blur, ca, vig, grade, letter, lines, blurA;
        Vec3 blurAt, linesAt, impAt;
        float[] flash = {1, 1, 1, 0};
        Vec3 shockAt, splitAt;
        double shockR, shockA, splitK, splitAng, splitOff;
    }

    public static boolean active(double now) {
        SHOWS.removeIf(s -> (now - s.born) / 20.0 > s.clock.realEnd + 1.5);
        return !SHOWS.isEmpty();
    }

    private static double smooth(double a, double b, double x) {
        double k = Mth.clamp((x - a) / (b - a == 0 ? 1e-6 : b - a), 0, 1);
        return k * k * (3 - 2 * k);
    }

    private static double dec(double x, double life) {
        return x >= 0 && x < life ? (1 - x / life) * (1 - x / life) : 0;
    }

    static State state(double now) {
        State st = new State();
        for (Show s : SHOWS) {
            double w = s.weight;
            if (w <= 0) {
                continue;
            }
            double real = (now - s.born) / 20.0;
            double te = s.clock.form(real);
            double[] win = new double[5];
            List<Ev> all = new ArrayList<>(s.evs);
            all.addAll(s.dyn);
            for (Ev e : all) {
                switch (e.k) {
                    case WIN -> win[e.ch] += e.a * smooth(e.t0, e.t0 + e.fin, te) * (1 - smooth(e.t1, e.t1 + e.fout, te));
                    case RUMBLE -> st.shake += w * e.a * smooth(e.t0, e.t0 + .1, te) * (1 - smooth(e.t1 - .3, e.t1, te));
                    case LINES -> {
                        double v = w * e.a * smooth(e.t0, e.t0 + .04, te) * (1 - smooth(e.t1 - .15, e.t1, te));
                        if (v > st.lines) {
                            st.lines = v;
                            st.linesAt = s.basis.world(e.at);
                        }
                    }
                    case BLUR_AT -> st.blurAt = s.basis.world(e.at);
                    case HEART -> {
                        if (te >= e.t0 && te < e.t1) {
                            win[VIG] += .3 * Math.pow(Math.max(0, Math.sin(real * Math.PI * 2 * 1.3)), 10);
                        }
                    }
                    default -> moment(st, s, e, real, w);
                }
            }
            st.grade += w * win[GRADE];
            st.vig += w * win[VIG];
            st.letter = Math.max(st.letter, w * win[LETTER]);
            st.ca += w * win[CA];
            if (w * win[BLUR] > st.blur) {
                st.blur = w * win[BLUR];
            }
            // closer cuts: more shake and colour split, darker edges
            double danger = real > s.clock.realEnd ? 0 : Mth.clamp((24 - s.near) / 20, 0, 1);
            st.shake += .3 * danger * danger;
            st.ca += .5 * danger;
            st.vig += .25 * danger;
        }
        st.grade = Math.min(.85, st.grade);
        st.vig = Math.min(.92, st.vig);
        st.letter = Math.min(1, st.letter);
        return st;
    }

    private static void moment(State st, Show s, Ev e, double real, double w) {
        double x = real - (Double.isNaN(e.ra) ? s.clock.real(e.t) : e.ra);
        if (x < 0) {
            return;
        }
        switch (e.k) {
            case IMP -> {
                int i = (int) Math.floor(x / TICK);
                if (i < e.seq.length() && w > .35) {
                    char ch = e.seq.charAt(i);
                    st.imp = ch == 'P' ? P : ch == 'I' ? I : ch == 'N' ? N : L;
                    st.impAt = s.basis.world(e.at);
                }
            }
            case SHAKE -> st.shake += w * e.a * Math.exp(-x * e.d);
            case FOV -> st.fov += w * e.a * dec(x, e.life);
            case CA -> st.ca += w * e.a * dec(x, e.life);
            case BLUR -> {
                double v = w * e.a * dec(x, e.life);
                if (v > st.blur) {
                    st.blur = v;
                    st.blurAt = s.basis.world(e.at);
                }
            }
            case FLASH -> {
                double v = w * e.a * dec(x, e.life);
                if (v > st.flash[3]) {
                    st.flash = new float[] {e.c[0], e.c[1], e.c[2], (float) v};
                }
            }
            case SHOCK -> {
                if (x < 1.1) {
                    st.shockAt = s.basis.world(e.at);
                    st.shockR = x * e.spd;
                    st.shockA = w * e.a * (1 - x / 1.1);
                }
            }
            case SPLIT -> {
                if (x < e.life) {
                    st.splitAt = s.basis.world(e.at);
                    st.splitAng = e.ang;
                    st.splitOff = e.off;
                    st.splitK = w * Math.min(1, x / .04) * (1 - smooth(e.life * .55, e.life, x));
                }
            }
            default -> {
            }
        }
    }

    // FOV punch (degrees) right now.
    static float fov(double now) {
        return SHOWS.isEmpty() ? 0 : (float) state(now).fov;
    }

    // The pass (between FxPost.beginPasses and endPasses).
    static void pass(double now, boolean reduced) {
        var shader = FxShaders.moonScreen;
        if (shader == null) {
            return;
        }
        State st = state(now);
        if (st.imp < 0 && st.shake < .002 && st.blur < .002 && st.ca < .002 && st.vig < .002 && st.grade < .002 && st.letter < .002 && st.lines < .01
                && st.flash[3] < .004 && st.shockA <= 0 && st.splitK <= 0) {
            return;
        }
        var target = Minecraft.getInstance().getMainRenderTarget();
        double sec = now / 20.0;
        double sh = Math.min(1.6, st.shake) * FxConfig.shake();
        double n1 = Math.sin(sec * 61.3) + Math.sin(sec * 37.1 + 2) * .6, n2 = Math.cos(sec * 53.7) + Math.sin(sec * 29.3 + 1) * .6;
        double n3 = Math.sin(sec * 44.1 + 1.3) + Math.sin(sec * 23.9) * .5;
        boolean frames = FxConfig.on(FxConfig.IMPACT_FRAMES) && !reduced;
        boolean bend = FxConfig.on(FxConfig.DISTORTION);
        shader.safeGetUniform("Screen").set((float) target.width, (float) target.height);
        shader.safeGetUniform("Imp").set(frames ? (float) st.imp : -1f);
        shader.safeGetUniform("Seed").set((float) (Math.floor(now) % 97));
        shader.safeGetUniform("Blur").set(FxConfig.on(FxConfig.RADIAL_BLUR) ? (float) st.blur : 0f);
        shader.safeGetUniform("CA").set(bend ? (float) st.ca : 0f);
        shader.safeGetUniform("Vig").set((float) st.vig);
        shader.safeGetUniform("Grade").set((float) st.grade);
        shader.safeGetUniform("Letter").set((float) st.letter);
        shader.safeGetUniform("Lines").set((float) st.lines);
        shader.safeGetUniform("Roll").set((float) (n3 * .012 * sh));
        shader.safeGetUniform("Zoom").set((float) (1 + .04 * Math.min(1, sh)));
        shader.safeGetUniform("Night").set(1f);
        shader.safeGetUniform("Offset").set((float) (n1 * .004 * sh), (float) (n2 * .004 * sh));
        float[] bc = ScreenFx.screen(st.blurAt != null ? st.blurAt : center());
        shader.safeGetUniform("BlurCenter").set(bc[0], bc[1]);
        float[] lc = ScreenFx.screen(st.linesAt != null ? st.linesAt : center());
        shader.safeGetUniform("LinesCenter").set(lc[0], lc[1]);
        if (bend && st.splitK > 0) {
            float[] c = ScreenFx.screen(st.splitAt);
            shader.safeGetUniform("SplitCenter").set(c[0], c[1]);
            shader.safeGetUniform("Split").set((float) st.splitK, (float) st.splitAng, (float) st.splitOff, 0f);
        } else {
            shader.safeGetUniform("Split").set(0f, 0f, 0f, 0f);
        }
        if (bend && st.shockA > 0) {
            float[] c = ScreenFx.screen(st.shockAt);
            shader.safeGetUniform("Shock").set(c[0], c[1], (float) st.shockR, (float) st.shockA);
        } else {
            shader.safeGetUniform("Shock").set(0f, 0f, 0f, 0f);
        }
        // the flash waits for the frames to end (they are strong enough on their own)
        float fa = st.imp >= 0 && frames ? 0 : st.flash[3] * (reduced ? .35f : 1f);
        shader.safeGetUniform("Flash").set(st.flash[0], st.flash[1], st.flash[2], fa);
        FxPost.pass(shader);
    }

    private static Vec3 center() {
        Show s = SHOWS.isEmpty() ? null : SHOWS.get(SHOWS.size() - 1);
        return s == null ? Vec3.ZERO : s.basis.world(new double[] {0, 2, 0});
    }
}
