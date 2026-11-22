// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.power.powers.kokushibo;

import java.util.List;

// The Moon forms' clock: maps real seconds to the form's time. Built with freezes (hitstop) and slow motion in mind,
// after which the form runs faster until it has caught up; today every form plays in real time (plain): the awakened
// forms' freezes and slow motion went out with their impact frames (Stark 2026-11-22). The server (hits, carving) and
// every client (the drawing) run the form through this same map, so the blows stay on the cuts that are seen.
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
}
