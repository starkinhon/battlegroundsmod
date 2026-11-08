package com.starkinhon.battlegroundsmod.power.powers.kokushibo;

import java.util.ArrayList;
import java.util.List;

// The awakened forms' clock (Stark 2026-11-08, forja/kokushibo-formas/impacto-desperta.html): some moments freeze
// (hitstop) or go in slow motion, and afterwards the form runs faster until it has caught up, so it still ends when it
// always did. The server (hits, carving) and every client (the drawing, the screen effects) run the form through this
// same map, so the blows stay on the cuts that are seen.
public final class MoonTime {
    private static final double STEP = 1 / 240.0;

    // A freeze: when the form reaches t, it stands still for hold seconds.
    public record Stop(double t, double hold) {
    }

    // Slow motion: from t, for dur seconds of the form, it runs at rate k.
    public record Slow(double t, double dur, double k) {
    }

    private final double[] form;
    public final double realEnd;

    private MoonTime(double[] form, double realEnd) {
        this.form = form;
        this.realEnd = realEnd;
    }

    // The normal forms and anything without a script: real time is the form's time.
    public static MoonTime plain(double end) {
        return build(end, List.of(), List.of());
    }

    // The clock of this form (shape: the form as made, with its hanging moons).
    public static MoonTime of(int form, MoonForms.Form shape) {
        List<Stop> stops = new ArrayList<>();
        List<Slow> slows = new ArrayList<>();
        switch (form) {
            case 7 -> stops.add(new Stop(.09, .12));
            case 8 -> slows.add(new Slow(.20, .32, .3));
            case 9 -> stops.add(new Stop(1.47, .15));
            case 14 -> slows.add(new Slow(1.52, .3, .3));
            case 16 -> {
                double[][] land = landings(shape);
                if (land.length > 0) {
                    slows.add(new Slow(Math.max(.05, land[0][0] - .3), .26, .4));
                    stops.add(new Stop(land[land.length - 1][0], .35));
                }
            }
            default -> {
            }
        }
        return build(shape.end, stops, slows);
    }

    private static MoonTime build(double end, List<Stop> stops, List<Slow> slows) {
        int max = (int) Math.ceil((end + 4) / STEP) + 2;
        double[] es = new double[max];
        int n = 1;
        double r = 0, e = 0, holdLeft = 0;
        boolean[] done = new boolean[stops.size()];
        while (e < end - 1e-6 && n < max) {
            double rate = 1;
            for (int i = 0; i < stops.size(); i++) {
                if (!done[i] && e >= stops.get(i).t()) {
                    done[i] = true;
                    holdLeft += stops.get(i).hold();
                }
            }
            Slow slow = null;
            for (Slow s : slows) {
                if (e >= s.t() && e < s.t() + s.dur()) {
                    slow = s;
                }
            }
            if (holdLeft > 0) {
                rate = 0;
                holdLeft -= STEP;
            } else if (slow != null) {
                rate = slow.k();
            } else {
                double debt = r - e;
                rate = debt > 1e-4 ? 1 + Math.min(.9, debt * 6) : 1;
            }
            r += STEP;
            e = Math.min(end, e + STEP * rate);
            es[n++] = e;
        }
        double[] trimmed = new double[n];
        System.arraycopy(es, 0, trimmed, 0, n);
        return new MoonTime(trimmed, r);
    }

    // The form's time after real seconds since it started.
    public double form(double real) {
        double x = real / STEP;
        int i = (int) Math.floor(x);
        if (i < 0) {
            return 0;
        }
        if (i >= form.length - 1) {
            return form[form.length - 1];
        }
        double f = x - i;
        return form[i] + (form[i + 1] - form[i]) * f;
    }

    // The first real second at which the form reaches t.
    public double real(double t) {
        int lo = 0, hi = form.length - 1;
        while (lo < hi) {
            int m = (lo + hi) >>> 1;
            if (form[m] < t - 1e-9) {
                lo = m + 1;
            } else {
                hi = m;
            }
        }
        return lo * STEP;
    }

    // The 16th Form's landings, in order: {time, x, y, z} (local space; where it lands right now, it follows its
    // target until it locks). Empty for any other form.
    public static double[][] landings(MoonForms.Form shape) {
        MoonForms.Form base = shape instanceof MoonForms.Linger l ? l.base() : shape;
        if (!(base instanceof MoonFormsAwakened.Form16 f)) {
            return new double[0][];
        }
        List<MoonFormsAwakened.Form16.Cut> cuts = new ArrayList<>(f.cuts());
        cuts.sort((a, b) -> Double.compare(a.hit, b.hit));
        double[][] out = new double[cuts.size()][];
        for (int i = 0; i < cuts.size(); i++) {
            MoonFormsAwakened.Form16.Cut c = cuts.get(i);
            double[] g = c.g != null ? c.g : c.g0 != null ? c.g0 : new double[] {0, 0, 26};
            out[i] = new double[] {c.hit, g[0], g[1], g[2]};
        }
        return out;
    }
}
