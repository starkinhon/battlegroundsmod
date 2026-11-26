// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.skill;

// The Esfera da Destruição Planetária's cutscene (Bills' Shift + C), remade with Stark on 2026-11-26 in the web preview
// (forja/bills-esfera-cutscene, cena.js v18, approved after 17 rounds: "tá perfeito"). Everything here is the preview's
// math line by line, a pure function of the time in ticks since the press: the two bodies' poses, where the target and
// the ball are, and the camera of each shot. Positions are in blocks in Bills' frame: x to his right, y up, z ahead,
// from his feet where he stood (EsferaCutscene turns them into the world). The poses use SkillSpec.Pose's channels plus
// a whole-body transform (Raiz) that SpaceScene draws with the PoseStack.
public final class EsferaCena {
    // ---------- timeline (ticks from the press), from bills.esfera.json's tempo ----------
    public final double tp;       // gone in black streaks
    public final double espaco;   // in space (already looking at the target)
    public final double chegada;  // how long the arrival shot lasts
    public final double alvo;     // from behind the target: Bills high up, looking down at it
    public final double carga;    // raises the finger slowly and the sun forms
    public final double lancar;   // the throw: the arm rises a little...
    public final double solta;    // ...the ball leaves and goes STRAIGHT, diagonally down...
    public final double braco;    // ...while the arm comes down, wide and slow
    public final double implode;  // the ball flies small for a while, then shrinks to a point...
    public final double gigante;  // ...and bursts into the giant sun
    public final double recolhe;  // Bills takes the arm back
    public final double perto;    // cut to behind Bills: the sun closing in on the target (final shot)
    public final double contato;  // where the sun would touch it (after the end: the sun swallows it in the world)
    public final double fim;

    // ---------- layout (Stark 2026-11-26) ----------
    // Bills very high up; the target far below, just over the Earth, diagonally ahead of him. The Earth lies on the line D.
    public static final double DIST = 40, DESCE = 45, VAO = 16;
    private static final double[] DESVIO = {1.5, 6, 6};
    public static final double[] D = {0, -Math.sin(Math.toRadians(DESCE)), Math.cos(Math.toRadians(DESCE))};
    public final double raioTerra;
    public final double[] planeta;
    private static final double[] ALVO0 = {D[0] * DIST + DESVIO[0], D[1] * DIST + DESVIO[1], D[2] * DIST + DESVIO[2]};
    // How far down he tilts his head to look at the target (degrees below level, from his eyes).
    private static final double OLHAR_ALVO = Math.toDegrees(Math.atan2(-(ALVO0[1] - 1.62 + 1), Math.hypot(ALVO0[0], ALVO0[2])));

    // The ball: radius fully charged on his finger and as the giant sun.
    public final double raioCarga;
    public final double raioGigante;

    // Hands behind his back (his pose, calm and smug).
    private static final double COSTAS_FRENTE = -38, COSTAS_ABRIR = -14;

    public EsferaCena(EsferaSpec spec) {
        tp = spec.tpTick();
        espaco = spec.spaceTick();
        alvo = spec.aimTick();
        chegada = alvo - espaco;
        carga = spec.chargeTick();
        lancar = spec.throwTick();
        solta = spec.flyTick();
        braco = solta + 8;
        implode = spec.blastTick();
        gigante = implode + 6;
        recolhe = solta + 58;
        fim = spec.endTick();
        perto = fim - 44;
        contato = fim + 36;
        raioTerra = Math.max(10, spec.espaco().tamanho() / 2);
        double far = DIST + VAO + raioTerra;
        planeta = new double[] {D[0] * far, D[1] * far, D[2] * far};
        raioCarga = Math.max(0.2, spec.esfera().raioCarga());
        raioGigante = Math.max(raioCarga, spec.esfera().raioGigante());
    }

    // ---------- curves ----------
    public enum Ease { LIN, IO, OUT, IN, SNAP, BACK }

    public static double ease(Ease e, double u) {
        switch (e) {
            case LIN:
                return u;
            case OUT:
                return 1 - (1 - u) * (1 - u) * (1 - u);
            case IN:
                return u * u * u;
            case SNAP:
                return 1 - Math.pow(1 - u, 5);
            case BACK: {
                double c = 2.2;
                double v = u - 1;
                return 1 + (c + 1) * v * v * v + c * v * v;
            }
            default:
                return u * u * (3 - 2 * u);
        }
    }

    public static double io(double u) {
        return ease(Ease.IO, u);
    }

    public static double cl(double x, double a, double b) {
        return Math.min(b, Math.max(a, x));
    }

    // A track: keys of {tick, value} with the curve into each key (IO when left out).
    private static final class K {
        final double t;
        final double v;
        final Ease e;

        K(double t, double v, Ease e) {
            this.t = t;
            this.v = v;
            this.e = e;
        }
    }

    private static K k(double t, double v) {
        return new K(t, v, Ease.IO);
    }

    private static K k(double t, double v, Ease e) {
        return new K(t, v, e);
    }

    private static double track(double t, K... keys) {
        if (t <= keys[0].t) {
            return keys[0].v;
        }
        for (int i = 1; i < keys.length; i++) {
            K b = keys[i];
            if (t <= b.t) {
                K a = keys[i - 1];
                double u = b.t == a.t ? 1 : (t - a.t) / (b.t - a.t);
                return a.v + (b.v - a.v) * ease(b.e, cl(u, 0, 1));
            }
        }
        return keys[keys.length - 1].v;
    }

    // A damped spring set off by a jolt at t0 (amp degrees), dying out by itself.
    private static double wobble(double t, double t0, double amp, double freq, double decay) {
        return t < t0 ? 0 : amp * Math.exp(-(t - t0) / decay) * Math.sin((t - t0) * freq);
    }

    private static double hash(int a, int b) {
        int h = a * 374761393 + b * 668265263;
        h = (h ^ (h >>> 13)) * 1103515245;
        return ((h ^ (h >>> 16)) & 0xFFFF) / (double) 0xFFFF;
    }

    // Smooth noise: two sines with their own phase per channel.
    public static double drift(double t, int seed, double amp, double speed) {
        return amp * (0.6 * Math.sin(t * speed * (0.8 + 0.4 * hash(seed, 1)) + 6.28 * hash(seed, 2))
                + 0.4 * Math.sin(t * speed * (1.7 + 0.6 * hash(seed, 3)) + 6.28 * hash(seed, 4)));
    }

    // ---------- the poses ----------
    // The whole body: moved (x right, y up, z ahead, blocks) and turned around its middle, 1 block over the feet
    // (guinada around the vertical, inclinar forward, rolar to its left; degrees).
    public record Raiz(double x, double y, double z, double guinada, double inclinar, double rolar) {
        public static final Raiz ZERO = new Raiz(0, 0, 0, 0, 0, 0);
    }

    public record Corpo(SkillSpec.Pose pose, Raiz raiz, boolean visivel) {
    }

    private static SkillSpec.Pose pose(double cabeca, double tronco, double giro, double bdF, double bdA, double beF, double beA, double pdF, double pdA,
            double peF, double peA) {
        return new SkillSpec.Pose(cabeca, tronco, new SkillSpec.Braco(bdF, bdA), new SkillSpec.Braco(beF, beA), giro, new SkillSpec.Braco(pdF, pdA),
                new SkillSpec.Braco(peF, peA), 0, 0);
    }

    public Corpo bills(double t) {
        boolean space = t >= espaco;
        boolean visivel = t < tp + 1 || space;
        double look = OLHAR_ALVO - 16;
        // head: chin up (smug) -> looks down at the target -> looks up at the sun -> locked on the target again
        double cabeca = track(t, k(0, 0), k(3, 4, Ease.OUT), k(7, -10, Ease.SNAP), k(16, -8), k(espaco, look),
                k(carga + 10, look), k(carga + 30, -22), k(lancar, -20));
        // chest: breathes; fills up to charge; in the throw it only follows the arm, no jolt
        double tronco = track(t, k(0, 0), k(3, -5, Ease.OUT), k(7, 4, Ease.SNAP), k(16, 3), k(espaco, 8), k(carga, 8),
                k(carga + 10, 10), k(carga + 30, -6), k(lancar, -6), k(lancar + 10, -8), k(braco, 6),
                k(braco + 10, 8, Ease.OUT), k(recolhe + 12, 4), k(fim, 4)) + 1.2 * Math.sin(t * 0.11);
        double giro = track(t, k(0, 0), k(lancar, 0), k(lancar + 10, -5), k(braco, 6), k(recolhe, 3), k(recolhe + 12, 0));
        // right arm: behind his back -> lets go and dips -> rises slowly with the finger up -> holds -> the throw, smooth
        // (divine: up a little and down wide towards the target) -> slowly back behind his back
        double bdF = track(t, k(0, 0), k(3, 10, Ease.OUT), k(7, COSTAS_FRENTE, Ease.SNAP), k(carga, COSTAS_FRENTE), k(carga + 10, -48),
                k(carga + 30, 176), k(carga + 38, 172), k(lancar, 172), k(lancar + 10, 186), k(braco, 75),
                k(braco + 10, 62, Ease.OUT), k(recolhe, 40), k(recolhe + 8, 40), k(recolhe + 18, COSTAS_FRENTE), k(fim, COSTAS_FRENTE));
        double bdA = track(t, k(0, 0), k(3, 8, Ease.OUT), k(7, COSTAS_ABRIR, Ease.SNAP), k(carga, COSTAS_ABRIR), k(carga + 10, 20),
                k(carga + 30, 4), k(lancar, 6), k(lancar + 10, 10), k(braco, -6), k(recolhe, 0),
                k(recolhe + 8, 8), k(recolhe + 18, COSTAS_ABRIR), k(fim, COSTAS_ABRIR));
        bdF += drift(t, 11, t > carga + 12 && t < lancar ? 1.2 : 0.5, 0.09);
        // left arm: behind his back; in the throw it opens slowly for balance
        double beF = track(t, k(0, 0), k(3, 10, Ease.OUT), k(7, COSTAS_FRENTE, Ease.SNAP), k(lancar + 4, COSTAS_FRENTE), k(braco, -35),
                k(braco + 10, -40, Ease.OUT), k(recolhe + 6, -30), k(recolhe + 18, COSTAS_FRENTE));
        double beA = track(t, k(0, 0), k(3, 8, Ease.OUT), k(7, COSTAS_ABRIR, Ease.SNAP), k(lancar + 4, COSTAS_ABRIR), k(braco, 18),
                k(recolhe + 6, 10), k(recolhe + 18, COSTAS_ABRIR));
        // legs: on the ground, still; in space, loose (one ahead of the other). They never kick in the throw.
        double pdF = 0, pdA = 0, peF = 0, peA = 0;
        double rx = 0, ry = 0, rz = 0, inclinar = 0, rolar = 0;
        if (space) {
            double sa = t - espaco;
            double arrastar = wobble(t, espaco, -30, 0.32, 7);
            pdF = 10 + arrastar + drift(t, 21, 1.5, 0.05);
            pdA = 4 + drift(t, 22, 1, 0.05);
            peF = -6 + arrastar * 0.8 + drift(t, 23, 1.5, 0.05);
            peA = 7 + drift(t, 24, 1, 0.05);
            // floats (up and down, slowly)
            ry = 0.07 * Math.sin(sa * 0.1) + 0.03 * Math.sin(sa * 0.23);
            rolar = 1.2 * Math.sin(sa * 0.07);
            // arrival: comes from behind and above, tilted, and overshoots a little before stopping
            double arr = cl((t - espaco) / 10, 0, 1);
            double kb = ease(Ease.BACK, arr);
            rz = -1.8 * (1 - kb);
            ry += 0.9 * (1 - kb);
            inclinar = 18 * (1 - ease(Ease.OUT, arr)) + wobble(t, espaco + 6, 6, 0.3, 6);
            // looking down at the target: the whole body leans with the head
            inclinar += track(t, k(espaco, 8), k(carga + 10, 8), k(carga + 30, 0), k(lancar, 0), k(lancar + 10, 8));
            rz += track(t, k(braco, 0), k(braco + 8, -0.12), k(fim, 0));
        }
        // from the throw on, the head stays LOCKED on the target (taking off the chest and the lean)
        if (t >= lancar) {
            double lock = OLHAR_ALVO - tronco - inclinar;
            double w = io(cl((t - lancar) / 8, 0, 1));
            cabeca = cabeca + (lock - cabeca) * w;
        }
        return new Corpo(pose(cabeca, tronco, giro, bdF, bdA, beF, beA, pdF, pdA, peF, peA), new Raiz(rx, ry, rz, 0, inclinar, rolar), visivel);
    }

    // The target, stunned and adrift: upright, facing Bills up there, no control of its body, everything loose, floating
    // slowly and sinking very slowly towards the Earth. It does not tremble or cringe.
    public Corpo alvo(double t) {
        if (t < espaco) {
            // on the ground, stunned and swaying
            SkillSpec.Pose p = pose(18 + drift(t, 31, 4, 0.2), 12 + drift(t, 32, 3, 0.15), 0, 8 + drift(t, 33, 5, 0.2), 6,
                    4 + drift(t, 34, 5, 0.2), 8, 0, 0, 0, 0);
            return new Corpo(p, new Raiz(0, 0, 0, 0, 0, drift(t, 35, 4, 0.12)), t < tp + 1);
        }
        double s = t - espaco;
        SkillSpec.Pose p = pose(-18 + drift(s, 41, 6, 0.03), 4 + drift(s, 42, 4, 0.025), 0,
                12 + drift(s, 43, 10, 0.03), 32 + drift(s, 44, 8, 0.035),
                4 + drift(s, 45, 10, 0.03), 28 + drift(s, 46, 8, 0.035),
                6 + drift(s, 47, 6, 0.025), 8 + drift(s, 48, 4, 0.03),
                -4 + drift(s, 49, 6, 0.025), 10 + drift(s, 50, 4, 0.03));
        // the whole body: upright, turned to Bills (its back to the Earth), leaning back, turning very slowly
        return new Corpo(p, new Raiz(0, 0, 0, 180 + drift(s, 51, 12, 0.012), -16 + drift(s, 52, 6, 0.02), drift(s, 53, 7, 0.018)), true);
    }

    // Where the target's middle is (Bills' frame); it sinks very slowly towards the Earth.
    public double[] alvoPos(double t) {
        if (t < espaco) {
            return new double[] {0, 1, 4};
        }
        double s = t - espaco;
        double go = s * 0.02;
        return new double[] {ALVO0[0] + D[0] * go + Math.sin(s * 0.03) * 0.4, ALVO0[1] + D[1] * go + Math.sin(s * 0.045) * 0.25,
                ALVO0[2] + D[2] * go};
    }

    // ---------- the ball ----------
    // radius (0 = none); on his finger (mao) or flying (k from 0 to 1 until contact).
    public record Bola(double raio, boolean mao, double k) {
    }

    public Bola esfera(double t) {
        if (t < carga + 24) {
            return new Bola(0, true, 0);
        }
        if (t < solta) {
            // born over the finger and grows A LOT; it rises as it grows, so it never swallows Bills
            double kk = cl((t - carga - 24) / (lancar - carga - 24), 0, 1);
            double r = (0.06 + (raioCarga - 0.06) * io(kk)) * (1 + 0.04 * Math.sin(t * 0.9));
            return new Bola(r, true, 0);
        }
        // out of his hand at ONE steady speed to the end (only 16 ticks to pick up speed, no jolt leaving the hand); when
        // the cutscene ends it still hasn't touched the target
        double v = 1 / (contato - solta - 8);
        double kk = t < solta + 16 ? v * (t - solta) * (t - solta) / 32 : v * (t - solta - 8);
        double menor = raioCarga * 0.7;
        double r;
        if (t < implode) {
            r = raioCarga + (menor - raioCarga) * io(cl((t - solta) / 20, 0, 1));
        } else if (t < gigante) {
            r = menor * (1 - 0.75 * io((t - implode) / (gigante - implode)));
        } else {
            r = menor * 0.25 + (raioGigante - menor * 0.25) * ease(Ease.OUT, cl((t - gigante) / 6, 0, 1));
        }
        return new Bola(r, false, kk);
    }

    // How many ticks the ball trails behind the arm in the throw (heavy, not glued to the hand like a sword).
    public double atraso(double t) {
        return 7 * io(cl((t - lancar) / 10, 0, 1));
    }

    // Where the ball sits over his finger with Bills' pose at tick tt: the fingertip plus a little more along the arm.
    public double[] held(double tt, double r) {
        double[][] arm = arm(bills(tt));
        double[] tip = arm[1];
        double[] sh = arm[0];
        double[] d = norm(sub(tip, sh));
        return add(tip, d, r * 1.5 + 0.15);
    }

    // The ball's middle at tick t (Bills' frame), or null when there is none.
    public double[] bolaPos(double t, Bola b) {
        if (b.raio() <= 0) {
            return null;
        }
        if (b.mao()) {
            return held(t - atraso(t), b.raio());
        }
        double[] from = held(solta - atraso(solta), raioCarga);
        double[] aim = add(alvoPos(t), new double[] {0, 0.4, 0}, 1);
        double[] dir = norm(sub(aim, from));
        double[] contact = add(aim, dir, -raioGigante * 0.85);
        return lerp(from, contact, b.k());
    }

    // Shoulder and fingertip of his right arm (Bills' frame), the same chain the preview's model uses: feet, the body's
    // middle a block up (raiz turn), hips at 12 px, chest lean, twist, shoulder at (-5, 10) px, the fingertip 10.5 px down.
    // The model's own frame here has x to its LEFT (Minecraft's model space); turned back to Bills' frame at the end.
    public double[][] arm(Corpo c) {
        Raiz r = c.raiz();
        SkillSpec.Pose p = c.pose();
        double s = 1 / 16.0;
        // in the model's frame (x left, y up, z ahead)
        double[] tip = {-0.5, -10.5, 0};
        double[] sh = {0, 0, 0};
        double[][] pts = {sh, tip};
        for (int i = 0; i < 2; i++) {
            double[] v = pts[i];
            // arm: (x = -frente) then (z = -abrir), YXZ order: Rx then Rz applied to the point as Rx(Rz(v))
            v = rotZ(v, Math.toRadians(-SkillSpec.Braco.of(p.bracoDir()).abrir()));
            v = rotX(v, Math.toRadians(-SkillSpec.Braco.of(p.bracoDir()).frente()));
            v = add(v, new double[] {-5, 10, 0}, 1);
            v = rotY(v, Math.toRadians(p.giro()));
            v = rotX(v, Math.toRadians(p.tronco()));
            v = add(v, new double[] {0, 12, 0}, 1);
            v = new double[] {v[0] * s, v[1] * s - 1, v[2] * s};
            // tumble: Ry(guinada) Rx(inclinar) Rz(-rolar)
            v = rotZ(v, Math.toRadians(-r.rolar()));
            v = rotX(v, Math.toRadians(r.inclinar()));
            v = rotY(v, Math.toRadians(r.guinada()));
            v = add(v, new double[] {0, 1, 0}, 1);
            // back to Bills' frame (x right) and moved with the root
            pts[i] = new double[] {-v[0] + r.x(), v[1] + r.y(), v[2] + r.z()};
        }
        return pts;
    }

    // ---------- the camera ----------
    // Each shot: where the camera is and what it looks at (Bills' frame), the lens (vertical fov, constant: no zoom)
    // and a dutch tilt (rolar, degrees).
    public record Shot(double[] pos, double[] olhar, double fov, double rolar, int cena) {
    }

    public Shot camera(double t, double[] olhos, double[] peito, double[] alvoAt, double[] esf) {
        double[] pos;
        double[] olhar;
        double fov;
        double rolar = 0;
        int cena;
        if (t < tp) {
            cena = 1; // hands behind his back (low, three quarters from the front)
            double kk = io(t / tp);
            pos = add(olhos, v(1.1 - 0.2 * kk, -0.85, 2.9 - 0.6 * kk), 1);
            olhar = add(olhos, v(0, -0.15, 0), 1);
            fov = 55;
            rolar = -3;
        } else if (t < espaco) {
            cena = 2; // gone in black streaks
            pos = add(olhos, v(0.9, -0.85, 2.3), 1);
            olhar = add(olhos, v(0, -0.15, 0), 1);
            fov = 49;
            rolar = -3;
        } else if (t < espaco + chegada) {
            cena = 3; // arrival (wide, from behind and above, the Earth below)
            double kk = ease(Ease.OUT, (t - espaco) / chegada);
            pos = add(olhos, v(2.8 - 0.6 * kk, 3.2 - 0.6 * kk, -6.5 + 1.5 * kk), 1);
            olhar = add(olhos, v(0, -5, 5), 1);
            fov = 58;
        } else if (t < carga) {
            cena = 6; // from behind the target: Bills up there, looking at it
            double kk = io(cl((t - alvo) / (carga - alvo), 0, 1));
            double[] up = norm(sub(olhos, alvoAt));
            pos = add(add(alvoAt, up, -2.4 + 0.3 * kk), v(0.9, 0.5, 0), 1);
            olhar = add(olhos, v(0, -0.3, 0), 1);
            fov = 22;
            rolar = -5;
        } else if (t < carga + 32) {
            cena = 7; // from below: the finger rises and the sun is born
            double kk = io((t - carga) / 32);
            double ang = -0.35 + 0.75 * kk;
            pos = add(peito, v(Math.sin(ang) * 3.4, -1.3 + 0.1 * kk, Math.cos(ang) * 3.4), 1);
            olhar = add(peito, v(0, 0.9 + 1.1 * io(cl((t - carga - 4) / 10, 0, 1)), 0), 1);
            fov = 54;
            rolar = 5 - 4 * kk;
        } else if (t < lancar) {
            cena = 8; // from below looking up: the arm raised and the sun growing on his hand
            double kk = io((t - carga - 32) / (lancar - carga - 32));
            pos = add(peito, v(1.5 - 0.3 * kk, -1.9 + 0.3 * kk, 1.3 - 0.2 * kk), 1);
            olhar = lerp(add(peito, v(0, 2, 0), 1), esf, 0.55 + 0.25 * kk);
            fov = 66;
            rolar = 6;
        } else if (t < braco + 2) {
            cena = 9; // from the side: the smooth throw, looking fixedly at the target
            double kk = io((t - lancar) / (braco + 2 - lancar));
            pos = add(peito, v(10, 3.5 - 1.5 * kk, 3 - 0.6 * kk), 1);
            olhar = add(peito, v(0, 3.8 - 2 * kk, 1 + 1.5 * kk), 1);
            fov = 68;
            rolar = 4;
        } else if (t < perto - 54) {
            cena = 10; // over his shoulder: the ball going down at the target and the Earth
            double kk = io((t - braco - 2) / (perto - 54 - braco - 2));
            pos = add(olhos, v(7.5 + 1.5 * kk, 8.0 + 1.0 * kk, -9 - 2 * kk), 1);
            olhar = lerp(esf, alvoAt, 0.5 + 0.2 * kk);
            fov = 52;
        } else if (t < perto) {
            cena = 11; // from behind the target, well back: the target small in front and the sun HUGE coming over it
            double kk = io((t - (perto - 54)) / 54);
            pos = add(add(alvoAt, D, 9.0 - 1.0 * kk), v(2.4, -1.4, 0), 1);
            olhar = lerp(alvoAt, esf, 0.3 + 0.05 * kk);
            fov = 60;
            rolar = -6;
        } else {
            cena = 13; // behind Bills (the anime frame): on the line sun -> his head, a little to his left; his back on
                       // the right, the huge sun ahead going down to the target and the Earth (the final shot)
            double kk = io((t - perto) / (fim - perto));
            double[] u = norm(sub(olhos, esf));
            pos = add(add(olhos, u, 3.4 + 0.8 * kk), v(-1.6, -0.2 + 0.3 * kk, 0), 1);
            olhar = add(esf, v(-3.5, 0, 0), 1);
            fov = 56;
        }
        // shake: the finger, the release, the giant sun bursting; from behind the target a little more as it comes; in the
        // final shot just a little, growing as the sun nears the target
        double shake = 0.35 * tremor(t, carga + 7, 6) + 0.2 * tremor(t, solta, 10) + 0.8 * tremor(t, gigante, 10);
        if (t >= perto - 54 && t < perto) {
            shake += 0.12 + 0.3 * ease(Ease.IN, (t - (perto - 54)) / 54);
        }
        if (t >= perto) {
            shake += 0.18 * ease(Ease.IN, (t - perto) / (fim - perto));
        }
        // hand-held, very lightly (never still like a robot)
        double hand = 0.025;
        pos = add(pos, v(drift(t, 61, hand + shake * 0.12, 0.13), drift(t, 62, hand + shake * 0.12, 0.11), drift(t, 63, hand * 0.5, 0.09)), 1);
        olhar = add(olhar, v(drift(t, 64, shake * 0.18, 1.7), drift(t, 65, shake * 0.18, 1.9), 0), 1);
        return new Shot(pos, olhar, fov, rolar, cena);
    }

    // White on the screen (a smooth fade, never blinking): into the streaks and out in space, the sun bursting, the end.
    public double flash(double t) {
        double f = 0;
        if (t >= tp && t < espaco + 6) {
            f = t < espaco ? (t - tp) / (espaco - tp) : 1 - (t - espaco) / 6;
        }
        if (t >= gigante && t < gigante + 6) {
            f = Math.max(f, 0.55 * (1 - (t - gigante) / 6));
        }
        if (t >= fim - 6) {
            f = Math.max(f, (t - (fim - 6)) / 6);
        }
        return cl(f, 0, 1);
    }

    private static double tremor(double t, double t0, double dur) {
        return t < t0 || t > t0 + dur ? 0 : 1 - (t - t0) / dur;
    }

    // ---------- small vector math (double[3]) ----------
    public static double[] v(double x, double y, double z) {
        return new double[] {x, y, z};
    }

    public static double[] add(double[] a, double[] b, double k) {
        return new double[] {a[0] + b[0] * k, a[1] + b[1] * k, a[2] + b[2] * k};
    }

    public static double[] sub(double[] a, double[] b) {
        return new double[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    public static double[] lerp(double[] a, double[] b, double k) {
        return new double[] {a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k};
    }

    public static double[] norm(double[] a) {
        double n = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
        if (n < 1.0E-9) {
            return new double[] {0, 0, 0};
        }
        return new double[] {a[0] / n, a[1] / n, a[2] / n};
    }

    private static double[] rotX(double[] p, double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new double[] {p[0], p[1] * c - p[2] * s, p[1] * s + p[2] * c};
    }

    private static double[] rotY(double[] p, double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new double[] {p[0] * c + p[2] * s, p[1], -p[0] * s + p[2] * c};
    }

    private static double[] rotZ(double[] p, double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new double[] {p[0] * c - p[1] * s, p[0] * s + p[1] * c, p[2]};
    }
}
