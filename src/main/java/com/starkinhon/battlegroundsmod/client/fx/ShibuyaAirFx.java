package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.starkinhon.battlegroundsmod.ModSounds;
import com.starkinhon.battlegroundsmod.world.ModWorldgen;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

// Shibuya's air (Stark 2026-11-14: the preview forja/jujutsu-shibuya-ambiente approved, "caralho, ficou mt lindo MESMO!
// principalmente a noite"). amb.js there is the source of truth and this is its port, the same names where it helps.
// Fades in when the player walks into the biome and out when the player leaves (BiomeAura). Shibuya's mark is the NEON:
// - each lit sign (the faces of sign blocks open to the air, joined into panels by ShibuyaScan) lights the air in front of
//   it: a bloom on the facade (9 quads fading at the edge), a wider veil a little in front, a disc of lit air seen from any
//   side; each panel flickers its own way (68% steady, 14% breathing, 10% a sweep running, 8% a bad contact that goes
//   dark);
// - the light field: the street under the signs, shop windows and lamps takes their color (3 lights per 2-block cell), and
//   flickers with them; the signs' reflection on the asphalt (the panel mirrored on the street and projected through the
//   camera, so it moves with the look and stays under its facade);
// - the steam: half of the manholes (bueiro blocks) breathe steam in fans of 9 rays (white by day; at night nearly black,
//   lit by the neon under it); roof vents; the cursed buildings' purple miasma;
// - by day the yellowish smog: a band on the horizon, a low haze running with the wind, pale dust running channeled along
//   the streets (at night the haze takes the neon's color, and there are no lines of color in the air: Stark doesn't want
//   thin neon lines in any effect, 2026-11-14), flyers tumbling and lying on the sidewalk until a gust lifts them;
// - at night the city's light pollution on the horizon (magenta to the streets' orange);
// - the screen (fx_shibuyagrade): by day warm smog, a little washed; at night lively color and magenta/cyan edges added
//   on ("screen", it only brightens: the night is never darkened); in the metro a cool fluorescent tint;
// - now and then a police car down an avenue, its lights painting the street red and blue, the siren with Doppler;
// - sounds built from noise (tools/sounds/shibuya_sounds.py): the city's hum and far traffic, the near neon's buzz and its
//   crackle when it fails, the manhole's hiss, a far siren now and then.
// Underground (the metro station, ShibuyaMetro) none of the street's effects show: only the station's air.
public final class ShibuyaAirFx {
    private static final RandomSource RANDOM = RandomSource.createThreadSafe();
    private static final double WIND = 0.6;
    private static final float[][] LC = {c(0xfff1c8), c(0xff2a2a), c(0xff3a2a), c(0x3affb4), c(0x7fe6ff)};
    private static final float[][] FLY = {c(0xff4fb0), c(0xffd23a), c(0x2fd6e6), c(0xe8423c), c(0x3a6cff), c(0x38d16a)};

    private static float strength, lastStrength;
    static float night;
    private static double gust, wx = 1, wz, time;
    static boolean underground;

    private static final class Puff {
        ShibuyaScan.Vent v;
        double x, y, z, ox, oy, oz, vy, age, life, s0, grow, spin, rot, tone, dist;
        final double[] r = new double[9];
    }

    private static final class Streak {
        double x, y, z, ox, oz, h, s, ph, age, life, dx, dz, k;
    }

    private static final class Flyer {
        double x, y, z, ox, oy, oz, s, ang, spin, ax, ay, az, ph, band, rest, vy;
        float[] c;
    }

    private static final class Crow {
        double cx, cz, y, r, a, sp, ph;
    }

    private static final class Car {
        double x, z, ox, v, dirX, dirZ;
        boolean on;
    }

    private static final List<Puff> PUFFS = new ArrayList<>();
    private static final List<Streak> STREAKS = new ArrayList<>();
    private static final List<Flyer> FLYERS = new ArrayList<>();
    private static final List<Crow> CROWS = new ArrayList<>();
    private static Car car;
    private static double nextCar = 50, nextFar = 30, nextPass = 3;
    private static Loop humLoop, buzzLoop, hissLoop, sirenLoop;
    private static double buzzX, buzzY, buzzZ, hissX, hissY, hissZ;
    private static float buzzVol, hissVol;

    private ShibuyaAirFx() {
    }

    private static float[] c(int rgb) {
        return new float[] {(rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f};
    }

    private static double rnd() {
        return RANDOM.nextDouble();
    }

    private static double near01(double v, double a, double b) {
        return Mth.clamp((v - a) / (b - a), 0, 1);
    }

    private static double vnoise(double x, double z, int s) {
        int ix = Mth.floor(x), iz = Mth.floor(z);
        double fx = x - ix, fz = z - iz;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        double h00 = ShibuyaScan.ih(ix, iz, s), h10 = ShibuyaScan.ih(ix + 1, iz, s), h01 = ShibuyaScan.ih(ix, iz + 1, s), h11 = ShibuyaScan.ih(ix + 1, iz + 1, s);
        double a = h00 + (h10 - h00) * fx, b = h01 + (h11 - h01) * fx;
        return a + (b - a) * fz;
    }

    private static double gustAt(double t) {
        return 0.15 + 0.85 * Math.pow(0.5 + 0.5 * Math.sin(t * 0.37 + Math.sin(t * 0.11) * 2.2), 3);
    }

    public static boolean active() {
        return strength > 0.001f || lastStrength > 0.001f;
    }

    static float strength() {
        return strength;
    }

    // ---------------------------------------------------------------- tick

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        lastStrength = strength;
        if (mc.level == null || mc.player == null) {
            reset();
            ShibuyaScan.clear();
            ShibuyaMetro.reset();
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        boolean inside = BiomeAura.in("shibuya", b -> b.is(ModWorldgen.SHIBUYA));
        strength = Mth.clamp(strength + (inside ? 0.015f : -0.02f), 0, 1);
        if (strength <= 0) {
            reset();
            ShibuyaMetro.reset();
            return;
        }
        ClientLevel level = mc.level;
        Vec3 p = mc.player.position();
        double dt = 0.05;
        time = level.getGameTime() / 20.0;
        float daylight = level.getSkyDarken(1);
        night = Mth.clamp((0.8f - daylight) / 0.6f, 0, 1);
        gust = gustAt(time);
        double dir = WIND + Math.sin(time * 0.03) * 0.5;
        wx = Math.cos(dir);
        wz = Math.sin(dir);
        ShibuyaScan.tick(level, p.x, p.z);
        underground = ShibuyaScan.station && mc.player.getEyeY() < ShibuyaScan.stCity - 2.5
                && Math.abs(p.z - ShibuyaScan.stC) < 16 && Math.abs(p.x - ShibuyaScan.stOx) < 120;
        ShibuyaMetro.tick(level, mc.player);
        double t = time, g = gust;
        // each sign's flicker
        for (ShibuyaScan.Panel s : ShibuyaScan.panels) {
            if (s.mode == 0) {
                s.f = 0.95f + 0.05f * (float) Math.sin(t * 47 + s.ph);
            } else if (s.mode == 1) {
                s.f = 0.5f + 0.5f * (0.5f + 0.5f * (float) Math.sin(t * s.rate + s.ph));
            } else if (s.mode == 2) {
                s.f = 0.9f;
            } else {
                s.next -= dt;
                if (s.burst > 0) {
                    s.burst -= dt;
                    s.f = s.off > 0 ? 0.04f : (ShibuyaScan.ih(Mth.floor(t * 22), (int) s.cx, (int) s.cz) > 0.45 ? 1f : 0.06f);
                    if (s.off > 0) {
                        s.off -= dt;
                    }
                    if (s.burst <= 0) {
                        s.f = 1;
                    }
                } else {
                    s.f = 1;
                }
                if (s.next < 0) {
                    s.burst = 0.3f + (float) rnd() * 1.4f;
                    if (rnd() < 0.35) {
                        s.off = 0.8f + (float) rnd() * 1.8f;
                        s.burst = s.off + 0.5f;
                    }
                    s.next = 3 + (float) rnd() * 9;
                    crackle(level, p, s);
                }
            }
        }
        // steam: the manhole breathes, harder now and then and at night (colder air)
        for (ShibuyaScan.Vent v : ShibuyaScan.vents) {
            if (Math.abs(v.x - p.x) > 90 || Math.abs(v.z - p.z) > 90) {
                continue;
            }
            double br = 0.55 + 0.45 * Math.pow(0.5 + 0.5 * Math.sin(t * 0.5 + v.ph), 2);
            v.acc += dt * v.rate * v.power * br * (v.kind == 0 ? 1 + 0.35 * night : 1);
            while (v.acc > 1) {
                v.acc -= 1;
                if (PUFFS.size() < 1600) {
                    PUFFS.add(puff(v));
                }
            }
        }
        for (Puff f : PUFFS) {
            simPuff(f, dt);
        }
        PUFFS.removeIf(f -> f.age >= f.life);
        if (STREAKS.isEmpty()) {
            for (int i = 0; i < 500; i++) {
                Streak r = new Streak();
                spawnStreak(r, p, true);
                STREAKS.add(r);
            }
            for (int i = 0; i < 90; i++) {
                Flyer f = new Flyer();
                spawnFlyer(f, p, true);
                FLYERS.add(f);
            }
        }
        for (Streak r : STREAKS) {
            r.ox = r.x;
            r.oz = r.z;
            r.ph += dt * 3;
            r.age += dt;
            double sp = (6 + 12 * g) * r.s * r.k;
            r.x += (r.dx * sp - r.dz * Math.sin(r.ph) * 0.5) * dt;
            r.z += (r.dz * sp + r.dx * Math.sin(r.ph) * 0.5) * dt;
            if (r.age > r.life || Math.abs(r.x - p.x) > 48 || Math.abs(r.z - p.z) > 48) {
                spawnStreak(r, p, false);
            }
        }
        double[] wt = new double[3];
        for (Flyer f : FLYERS) {
            f.ox = f.x;
            f.oy = f.y;
            f.oz = f.z;
            windAt(f.x, f.z, f.y, wt);
            if (f.rest > 0) {
                // lying on the sidewalk until the next strong gust lifts it
                f.rest -= dt;
                if (g > 0.75 && rnd() < dt * 2) {
                    f.rest = 0;
                    f.vy = 2 + rnd() * 3;
                }
                if (f.rest > 0) {
                    continue;
                }
            }
            f.ang += dt * f.spin * (0.4 + g);
            f.ph += dt;
            f.vy *= 1 - dt * 0.8;
            f.y += dt * (-0.45 + f.vy + g * 0.9 * Math.sin(f.ph * 0.7) * wt[2]);
            double sp = (0.8 + 5 * g) * wt[2];
            f.x += dt * (wt[0] * sp + Math.cos(f.ph) * 0.6);
            f.z += dt * (wt[1] * sp + Math.sin(f.ph * 0.8) * 0.6);
            double ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(f.x), Mth.floor(f.z)) + 0.02;
            if (f.y < ground) {
                if (ground - f.y > 1.5) {
                    // into a wall: back where it came from, turned round
                    f.x = f.ox;
                    f.z = f.oz;
                    f.ph += Math.PI;
                } else {
                    f.y = ground;
                    f.rest = 2 + rnd() * 8;
                    f.ax = 0.02;
                    f.ay = 1;
                    f.az = 0.02;
                    f.ox = f.x;
                    f.oy = f.y;
                    f.oz = f.z;
                }
            }
            if (Math.abs(f.x - p.x) > 30 || Math.abs(f.z - p.z) > 30 || Math.abs(f.y - p.y) > 30) {
                spawnFlyer(f, p, false);
            }
        }
        if (CROWS.isEmpty()) {
            spawnCrows(level, p);
        }
        for (Crow cw : CROWS) {
            cw.a += cw.sp * dt;
            cw.ph += dt * 7;
        }
        stepCar(level, p, dt);
        sounds(mc, level, p, g);
    }

    private static void reset() {
        PUFFS.clear();
        STREAKS.clear();
        FLYERS.clear();
        CROWS.clear();
        car = null;
        for (Loop l : new Loop[] {humLoop, buzzLoop, hissLoop, sirenLoop}) {
            if (l != null) {
                l.end();
            }
        }
        humLoop = buzzLoop = hissLoop = sirenLoop = null;
    }

    private static Puff puff(ShibuyaScan.Vent v) {
        Puff f = new Puff();
        for (int k = 0; k < 9; k++) {
            f.r[k] = 0.62 + rnd() * 0.5;
        }
        boolean m = v.kind == 0, c = v.kind == 2;
        f.v = v;
        f.x = v.x + (rnd() - 0.5) * 0.5;
        f.y = v.y + 0.1;
        f.z = v.z + (rnd() - 0.5) * 0.5;
        f.ox = f.x;
        f.oy = f.y;
        f.oz = f.z;
        f.vy = m ? 0.9 + rnd() * 0.6 : 1 + rnd() * 0.6;
        f.life = m ? 3.5 + rnd() * 2.5 : c ? 6 + rnd() * 5 : 4 + rnd() * 3;
        f.s0 = m ? 0.6 + rnd() * 0.3 : 0.5 + rnd() * 0.3;
        f.grow = m ? 0.6 + rnd() * 0.25 : 0.5;
        f.spin = (rnd() - 0.5) * 0.6;
        f.rot = rnd() * Math.PI * 2;
        f.tone = rnd();
        return f;
    }

    private static final double[] WT = new double[3];

    private static void simPuff(Puff f, double dt) {
        f.ox = f.x;
        f.oy = f.y;
        f.oz = f.z;
        f.age += dt;
        windAt(f.x, f.z, f.y, WT);
        double lift = Math.min(1, (f.y - f.v.y) / 3), sp = (0.25 + 1.3 * gust) * WT[2] * lift;
        f.x += (WT[0] * sp + Math.sin(f.age * 0.8 + f.rot) * 0.2) * dt;
        f.z += (WT[1] * sp + Math.cos(f.age * 0.7 + f.rot) * 0.2) * dt;
        f.y += f.vy * dt;
        f.vy = Math.max(0.25, f.vy - dt * 0.12);
        f.rot += f.spin * dt;
    }

    // The wind channeled by the streets: in a street it runs along it (the buildings don't let it cross) and stronger; in
    // a crossing it swirls; over the roofs it is the free wind.
    private static void windAt(double x, double z, double y, double[] out) {
        int ax = ShibuyaScan.streetAxis(x, z), sy = ShibuyaScan.streetY(x, z);
        boolean low = sy != Integer.MIN_VALUE && y < sy + 26;
        if (!low || ax == 3) {
            out[0] = wx;
            out[1] = wz;
            out[2] = ax == 3 && low ? 1.15 : 1;
        } else if (ax == 1) {
            out[0] = wx >= 0 ? 1 : -1;
            out[1] = 0;
            out[2] = 1.1 + 0.6 * Math.abs(wx);
        } else if (ax == 2) {
            out[0] = 0;
            out[1] = wz >= 0 ? 1 : -1;
            out[2] = 1.1 + 0.6 * Math.abs(wz);
        } else {
            out[0] = wx;
            out[1] = wz;
            out[2] = 0.35;
        }
    }

    private static void spawnStreak(Streak r, Vec3 p, boolean any) {
        int sy = Integer.MIN_VALUE;
        for (int tries = 0; tries < 8; tries++) {
            r.x = p.x + (rnd() - 0.5) * 92;
            r.z = p.z + (rnd() - 0.5) * 92;
            sy = ShibuyaScan.streetY(r.x, r.z);
            if (sy != Integer.MIN_VALUE) {
                break;
            }
        }
        r.h = rnd() < 0.85 ? 0.15 + rnd() * 2.2 : 3 + rnd() * 30;
        r.y = (sy == Integer.MIN_VALUE ? -1000 : sy) + r.h;
        r.ox = r.x;
        r.oz = r.z;
        r.s = 0.7 + rnd() * 0.6;
        r.ph = rnd() * Math.PI * 2;
        r.age = any ? rnd() * 3 : 0;
        r.life = 2 + rnd() * 3;
        windAt(r.x, r.z, r.y, WT);
        r.dx = WT[0];
        r.dz = WT[1];
        r.k = WT[2];
    }

    // a flyer: an A5 sheet with a colored band printed on it (each its own), falling while it turns, lifted by the gust
    private static void spawnFlyer(Flyer f, Vec3 p, boolean any) {
        f.x = p.x + (rnd() - 0.5) * 60;
        f.z = p.z + (rnd() - 0.5) * 60;
        int sy = ShibuyaScan.streetY(f.x, f.z);
        double base = sy == Integer.MIN_VALUE ? p.y : sy;
        f.y = base + (any ? rnd() * 12 : 4 + rnd() * 10);
        f.ox = f.x;
        f.oy = f.y;
        f.oz = f.z;
        f.s = 0.16 + rnd() * 0.1;
        f.ang = rnd() * Math.PI * 2;
        f.spin = (rnd() < 0.5 ? -1 : 1) * (1 + rnd() * 3);
        double ax = rnd() - 0.5, ay = rnd() - 0.5, az = rnd() - 0.5, l = Math.max(1e-4, Math.sqrt(ax * ax + ay * ay + az * az));
        f.ax = ax / l;
        f.ay = ay / l;
        f.az = az / l;
        f.ph = rnd() * Math.PI * 2;
        f.c = FLY[RANDOM.nextInt(FLY.length)];
        f.band = 0.25 + rnd() * 0.3;
        f.rest = 0;
        f.vy = 0;
    }

    // crows circling the tallest building near (the ones the game already had: the same)
    private static void spawnCrows(ClientLevel level, Vec3 p) {
        int bx = Mth.floor(p.x), bz = Mth.floor(p.z), top = Integer.MIN_VALUE;
        for (int dx = -48; dx <= 48; dx += 8) {
            for (int dz = -48; dz <= 48; dz += 8) {
                int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(p.x) + dx, Mth.floor(p.z) + dz);
                if (h > top) {
                    top = h;
                    bx = Mth.floor(p.x) + dx;
                    bz = Mth.floor(p.z) + dz;
                }
            }
        }
        for (int i = 0; i < 14; i++) {
            Crow cw = new Crow();
            cw.cx = bx;
            cw.cz = bz;
            cw.y = top + 6 + rnd() * 14;
            cw.r = 10 + rnd() * 22;
            cw.a = rnd() * Math.PI * 2;
            cw.sp = (0.25 + rnd() * 0.2) * (rnd() < 0.5 ? 1 : -1);
            cw.ph = rnd() * 6;
            CROWS.add(cw);
        }
    }

    // A police car now and then down the nearest avenue (the longest straight run of street through the player's area),
    // keeping to the left, braking for nothing (the pedestrians wait at the light).
    private static void stepCar(ClientLevel level, Vec3 p, double dt) {
        nextCar -= dt;
        if (car == null && nextCar < 0 && !underground) {
            nextCar = 70 + rnd() * 60;
            // a straight street near the player: from a street cell 20 to 40 blocks off, along its direction
            for (int tries = 0; tries < 12 && car == null; tries++) {
                double a = rnd() * Math.PI * 2, d = 20 + rnd() * 20, x = p.x + Math.cos(a) * d, z = p.z + Math.sin(a) * d;
                int ax = ShibuyaScan.streetAxis(x, z);
                if (ax != 1 && ax != 2 || ShibuyaScan.streetY(x, z) == Integer.MIN_VALUE) {
                    continue;
                }
                Car cr = new Car();
                double s = rnd() < 0.5 ? 1 : -1;
                cr.dirX = ax == 1 ? s : 0;
                cr.dirZ = ax == 2 ? s : 0;
                // start 70 blocks back along the street
                cr.x = (ax == 1 ? p.x - s * 70 : x);
                cr.z = (ax == 2 ? p.z - s * 70 : z);
                cr.ox = 0;
                cr.v = 15;
                // the whole way (140 blocks) has to be road under the car's width, at the same height: never through a
                // building, a sidewalk or a square
                int gy = ShibuyaScan.streetY(x, z);
                boolean clear = true;
                for (int q = 0; q <= 140 && clear; q += 2) {
                    double qx = cr.x + cr.dirX * q, qz = cr.z + cr.dirZ * q;
                    for (int w = -1; w <= 1 && clear; w++) {
                        clear = ShibuyaScan.road(level, Mth.floor(qx - cr.dirZ * w * 1.2), gy, Mth.floor(qz + cr.dirX * w * 1.2));
                    }
                }
                if (!clear) {
                    continue;
                }
                car = cr;
            }
        }
        if (car == null) {
            return;
        }
        car.x += car.dirX * car.v * dt;
        car.z += car.dirZ * car.v * dt;
        car.ox += car.v * dt;
        car.on = Math.sin(time * 13) > 0;
        if (car.ox > 140) {
            car = null;
        }
    }

    // ---------------------------------------------------------------- sounds

    private static void crackle(ClientLevel level, Vec3 p, ShibuyaScan.Panel s) {
        double d = Math.sqrt((s.cx - p.x) * (s.cx - p.x) + (s.cy - p.y) * (s.cy - p.y) + (s.cz - p.z) * (s.cz - p.z));
        if (d < 30 && !underground) {
            level.playLocalSound(s.cx, s.cy, s.cz, ModSounds.SHIBUYA_ESTALO.get(), SoundSource.AMBIENT, 0.9f * strength, 0.9f + (float) rnd() * 0.3f, false);
        }
    }

    private static void sounds(Minecraft mc, ClientLevel level, Vec3 p, double g) {
        humLoop = keep(mc, humLoop, ModSounds.SHIBUYA_CIDADE.get(), true);
        humLoop.target = (float) ((underground ? 0.25 : 0.55 + 0.15 * night) * strength);
        // the nearest sign's buzz (at night), the nearest breathing manhole's hiss
        ShibuyaScan.Panel ss = null;
        double sd = 14;
        if (night > 0.3f && !underground) {
            for (ShibuyaScan.Panel s : ShibuyaScan.panels) {
                if (s.shop) {
                    continue;
                }
                double d = Math.sqrt((s.cx - p.x) * (s.cx - p.x) + (s.cy - p.y) * (s.cy - p.y) + (s.cz - p.z) * (s.cz - p.z));
                if (d < sd) {
                    sd = d;
                    ss = s;
                }
            }
        }
        if (ss != null) {
            buzzX = ss.cx + ss.nx * 0.5;
            buzzY = ss.cy;
            buzzZ = ss.cz + ss.nz * 0.5;
        }
        buzzVol = ss == null ? 0 : (float) (0.7 * ss.f * strength);
        buzzLoop = keep(mc, buzzLoop, ModSounds.SHIBUYA_NEON.get(), false);
        buzzLoop.follow(buzzX, buzzY, buzzZ, buzzVol);
        ShibuyaScan.Vent hv = null;
        double hd = 12;
        if (!underground) {
            for (ShibuyaScan.Vent v : ShibuyaScan.vents) {
                double d = Math.sqrt((v.x - p.x) * (v.x - p.x) + (v.z - p.z) * (v.z - p.z));
                if (v.kind == 0 && d < hd) {
                    hd = d;
                    hv = v;
                }
            }
        }
        if (hv != null) {
            hissX = hv.x;
            hissY = hv.y;
            hissZ = hv.z;
        }
        hissVol = hv == null ? 0 : 0.6f * strength;
        hissLoop = keep(mc, hissLoop, ModSounds.SHIBUYA_VAPOR.get(), false);
        hissLoop.follow(hissX, hissY, hissZ, hissVol);
        sirenLoop = keep(mc, sirenLoop, ModSounds.SHIBUYA_SIRENE.get(), false);
        if (car != null && !underground) {
            double dx = car.x - p.x, dz = car.z - p.z, d = Math.max(1, Math.sqrt(dx * dx + dz * dz));
            double vr = -(car.dirX * dx + car.dirZ * dz) / d * car.v;
            sirenLoop.follow(car.x, p.y, car.z, 1.6f * strength);
            sirenLoop.pitchTo = (float) Mth.clamp(343 / (343 - vr * 1.6), 0.6, 1.6);
        } else {
            sirenLoop.follow(p.x, p.y, p.z, 0);
        }
        if (!underground) {
            nextPass -= 0.05;
            if (nextPass < 0) {
                // a car passing on a near street (not seen: the sound of the city's traffic)
                double a = rnd() * Math.PI * 2, d = 12 + rnd() * 18;
                level.playLocalSound(p.x + Math.cos(a) * d, p.y, p.z + Math.sin(a) * d, ModSounds.SHIBUYA_CARRO.get(), SoundSource.AMBIENT,
                        (night > 0.5f ? 0.5f : 1f) * strength, 0.85f + (float) rnd() * 0.3f, false);
                nextPass = 2 + rnd() * (night > 0.5f ? 9 : 4);
            }
            nextFar -= 0.05;
            if (nextFar < 0) {
                double a = rnd() * Math.PI * 2;
                level.playLocalSound(p.x + Math.cos(a) * 60, p.y + 10, p.z + Math.sin(a) * 60, ModSounds.SHIBUYA_SIRENE_LONGE.get(), SoundSource.AMBIENT,
                        3f * strength, 0.95f + (float) rnd() * 0.1f, false);
                nextFar = 40 + rnd() * 40;
            }
        }
    }

    private static Loop keep(Minecraft mc, Loop l, SoundEvent ev, boolean relative) {
        if (l == null || !mc.getSoundManager().isActive(l)) {
            l = new Loop(ev, relative);
            mc.getSoundManager().play(l);
        }
        return l;
    }

    // A looping sound that follows a point (or is everywhere, relative), fading its volume toward the target.
    static final class Loop extends AbstractTickableSoundInstance {
        float target, pitchTo = 1;
        double tx, ty, tz;
        boolean placed;

        Loop(SoundEvent ev, boolean relative) {
            super(ev, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            looping = true;
            delay = 0;
            this.relative = relative;
            attenuation = relative ? SoundInstance.Attenuation.NONE : SoundInstance.Attenuation.LINEAR;
            volume = 0.001f;
        }

        void follow(double x, double y, double z, float vol) {
            if (!placed) {
                this.x = x;
                this.y = y;
                this.z = z;
                placed = true;
            }
            tx = x;
            ty = y;
            tz = z;
            target = vol;
        }

        void end() {
            stop();
        }

        @Override
        public void tick() {
            if (strength <= 0) {
                stop();
                return;
            }
            if (placed) {
                x += (tx - x) * 0.3;
                y += (ty - y) * 0.3;
                z += (tz - z) * 0.3;
            }
            volume = Math.max(0.001f, volume + (target - volume) * 0.15f);
            pitch += (pitchTo - pitch) * 0.3f;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }

    // ---------------------------------------------------------------- render

    private static final double[] BR = new double[3], BU = new double[3];
    private static double lookX, lookY, lookZ;

    // A flat piece's two axes, turned to face the camera's POSITION (not its looking direction): turning the head doesn't
    // turn it, only walking round it does, so it stays in the world instead of stuck on the screen.
    private static void face(Vec3 cam, double x, double y, double z) {
        double fx = cam.x - x, fy = cam.y - y, fz = cam.z - z, l = Math.sqrt(fx * fx + fy * fy + fz * fz);
        if (l < 1e-4) {
            fx = -lookX;
            fy = -lookY;
            fz = -lookZ;
            l = Math.max(1e-4, Math.sqrt(fx * fx + fy * fy + fz * fz));
        }
        fx /= l;
        fy /= l;
        fz /= l;
        double rx = -fz, rz = fx, rl = Math.sqrt(rx * rx + rz * rz);
        if (rl < 1e-3) {
            rx = 1;
            rz = 0;
            rl = 1;
        }
        rx /= rl;
        rz /= rl;
        BR[0] = rx;
        BR[1] = 0;
        BR[2] = rz;
        BU[0] = -rz * fy;
        BU[1] = rz * fx - rx * fz;
        BU[2] = rx * fy;
    }

    static void v(VertexConsumer out, PoseStack.Pose last, Vec3 cam, double x, double y, double z, float r, float g, float b, float a) {
        out.addVertex(last, (float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z)).setColor(r, g, b, a);
    }

    // a triangle in a quads buffer (the last corner twice)
    static void tri(VertexConsumer out, PoseStack.Pose last, Vec3 cam, double x0, double y0, double z0, float a0, double x1, double y1, double z1,
            double x2, double y2, double z2, float a12, float r, float g, float b) {
        v(out, last, cam, x0, y0, z0, r, g, b, a0);
        v(out, last, cam, x1, y1, z1, r, g, b, a12);
        v(out, last, cam, x2, y2, z2, r, g, b, a12);
        v(out, last, cam, x2, y2, z2, r, g, b, a12);
    }

    // a disc facing the camera's position, bright in the middle and gone at the rim
    static void disc(VertexConsumer out, PoseStack.Pose last, Vec3 cam, double x, double y, double z, double s, float[] c, float a) {
        face(cam, x, y, z);
        double px = 0, py = 0, pz = 0;
        for (int q = 0; q <= 10; q++) {
            double an = q / 10.0 * Math.PI * 2, ox = Math.cos(an) * s, oy = Math.sin(an) * s;
            double X = x + BR[0] * ox + BU[0] * oy, Y = y + BR[1] * ox + BU[1] * oy, Z = z + BR[2] * ox + BU[2] * oy;
            if (q > 0) {
                tri(out, last, cam, x, y, z, a, px, py, pz, X, Y, Z, 0, c[0], c[1], c[2]);
            }
            px = X;
            py = Y;
            pz = Z;
        }
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float fade = Mth.lerp(partial, lastStrength, strength);
        if (fade <= 0.001f) {
            return;
        }
        Camera cam = event.getCamera();
        Vec3 camera = cam.getPosition();
        PoseStack.Pose last = event.getPoseStack().last();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            if (!underground) {
                // after the sky the model-view isn't the camera's any more (DragonWind's lesson): set it, identity pose on top
                org.joml.Matrix4fStack view = com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();
                view.pushMatrix();
                view.set(event.getModelViewMatrix());
                com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
                try {
                    renderSky(buffers, new PoseStack().last(), camera, fade);
                } finally {
                    view.popMatrix();
                    com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
                }
            }
            return;
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        org.joml.Vector3f look = cam.getLookVector();
        lookX = look.x();
        lookY = look.y();
        lookZ = look.z();
        double t = (mc.level.getGameTime() + partial) / 20.0;
        boolean n = night > 0.5f;
        if (!underground) {
            // the ground light first (flat on the asphalt), then the signs and the air
            VertexConsumer light = buffers.getBuffer(FxRenderTypes.GLOW);
            if (n) {
                renderGround(light, last, camera, fade);
                renderReflections(light, last, camera, fade);
            }
            buffers.endBatch(FxRenderTypes.GLOW);
            VertexConsumer area = buffers.getBuffer(FxRenderTypes.AREA_FLAT);
            if (n) {
                renderSignFilm(area, last, camera, fade);
            } else {
                renderHaze(area, last, camera, fade, false, t);
                renderStreaks(area, last, camera, fade, partial);
            }
            renderSteam(area, last, camera, fade, n, partial);
            renderFlyers(area, last, camera, fade, n, partial);
            renderCrows(area, last, camera, fade, n);
            buffers.endBatch(FxRenderTypes.AREA_FLAT);
            VertexConsumer glow = buffers.getBuffer(FxRenderTypes.GLOW);
            if (n) {
                renderSigns(glow, last, camera, fade, t);
                renderLamps(glow, last, camera, fade, t);
                renderHaze(glow, last, camera, fade, true, t);
                renderCurseGlow(glow, last, camera, fade, partial);
            }
            renderCar(glow, last, camera, fade, n, t, partial);
            buffers.endBatch(FxRenderTypes.GLOW);
            ShibuyaMetro.renderCarBody(buffers, last, camera, car == null ? null : new double[] {car.x - car.dirX * car.v * (1 - partial) * 0.05,
                    car.z - car.dirZ * car.v * (1 - partial) * 0.05, car.dirX, car.dirZ, car.on ? 1 : 0}, ShibuyaScan.streetY(car == null ? 0 : car.x, car == null ? 0 : car.z));
        }
        ShibuyaMetro.render(buffers, last, camera, fade, partial);
    }

    // the city's light pollution low on the horizon at night (magenta to orange, slowly changing); by day a band of
    // yellowish smog (a ring round the camera drawn behind everything: the terrain covers it, only the sky shows it)
    private static void renderSky(MultiBufferSource.BufferSource buffers, PoseStack.Pose last, Vec3 cam, float fade) {
        boolean n = night > 0.5f;
        VertexConsumer out = buffers.getBuffer(n ? FxRenderTypes.GLOW : FxRenderTypes.AREA_FLAT);
        double r = 60;
        int segs = 48;
        double[] ys = {-0.12, -0.03, 0.02, 0.07, 0.16, 0.37};
        Vec3 o = Vec3.ZERO;
        for (int s = 0; s < segs; s++) {
            double a0 = s / (double) segs * Math.PI * 2, a1 = (s + 1) / (double) segs * Math.PI * 2;
            for (int k = 0; k < ys.length - 1; k++) {
                float[] c0 = skyCol(a0, ys[k], n), c1 = skyCol(a1, ys[k], n), c2 = skyCol(a1, ys[k + 1], n), c3 = skyCol(a0, ys[k + 1], n);
                double y0 = ys[k] * r, y1 = ys[k + 1] * r;
                out.addVertex(last, (float) (Math.cos(a0) * r), (float) y0, (float) (Math.sin(a0) * r)).setColor(c0[0], c0[1], c0[2], c0[3] * fade);
                out.addVertex(last, (float) (Math.cos(a1) * r), (float) y0, (float) (Math.sin(a1) * r)).setColor(c1[0], c1[1], c1[2], c1[3] * fade);
                out.addVertex(last, (float) (Math.cos(a1) * r), (float) y1, (float) (Math.sin(a1) * r)).setColor(c2[0], c2[1], c2[2], c2[3] * fade);
                out.addVertex(last, (float) (Math.cos(a0) * r), (float) y1, (float) (Math.sin(a0) * r)).setColor(c3[0], c3[1], c3[2], c3[3] * fade);
            }
        }
        buffers.endBatch(n ? FxRenderTypes.GLOW : FxRenderTypes.AREA_FLAT);
    }

    private static float[] skyCol(double ang, double y, boolean n) {
        double band = Math.exp(-Math.pow((y - 0.02) / 0.11, 2)), wide = Math.exp(-Math.pow((y - 0.05) / 0.32, 2));
        if (n) {
            double m = 0.5 + 0.5 * Math.sin(ang * 3 + Math.sin(ang * 7) * 1.2 + time * 0.03), k = band * 0.24 + wide * 0.05;
            return new float[] {(float) ((0.95 + 0.05 * m) * k), (float) ((0.38 + 0.2 * m) * k), (float) ((0.66 - 0.36 * m) * k), 1};
        }
        return new float[] {0.80f, 0.76f, 0.62f, (float) Mth.clamp(band * 0.75 + wide * 0.35, 0, 0.85)};
    }

    // the neon's light on the street: the street grid with each cell's color (each light flickering its own way)
    private static void renderGround(VertexConsumer out, PoseStack.Pose last, Vec3 cam, float fade) {
        if (!ShibuyaScan.field) {
            return;
        }
        int N = ShibuyaScan.FN, fx0 = ShibuyaScan.fx0, fz0 = ShibuyaScan.fz0, FS = ShibuyaScan.FS;
        int i0 = Math.max(0, Mth.floor((cam.x - 70 - fx0) / FS)), i1 = Math.min(N - 2, Mth.floor((cam.x + 70 - fx0) / FS));
        int j0 = Math.max(0, Mth.floor((cam.z - 70 - fz0) / FS)), j1 = Math.min(N - 2, Mth.floor((cam.z + 70 - fz0) / FS));
        float[] c00 = new float[3], c10 = new float[3], c11 = new float[3], c01 = new float[3];
        float K = 0.75f * fade;
        for (int i = i0; i <= i1; i++) {
            for (int j = j0; j <= j1; j++) {
                int gy = ShibuyaScan.FY[i * N + j];
                if (gy == Integer.MIN_VALUE) {
                    continue;
                }
                ShibuyaScan.fieldAt(i, j, c00);
                ShibuyaScan.fieldAt(i + 1, j, c10);
                ShibuyaScan.fieldAt(i + 1, j + 1, c11);
                ShibuyaScan.fieldAt(i, j + 1, c01);
                if (c00[0] + c00[1] + c00[2] + c10[0] + c11[0] + c01[0] + c10[2] + c11[2] + c01[2] < 0.01) {
                    continue;
                }
                // (a cell whose neighbor is at another height keeps its own: no light quad bending up a curb)
                double y = gy + 0.02, x0 = fx0 + (i + 0.5) * FS, z0 = fz0 + (j + 0.5) * FS, x1 = x0 + FS, z1 = z0 + FS;
                v(out, last, cam, x0, y, z0, c00[0], c00[1], c00[2], K);
                v(out, last, cam, x1, y, z0, c10[0], c10[1], c10[2], K);
                v(out, last, cam, x1, y, z1, c11[0], c11[1], c11[2], K);
                v(out, last, cam, x0, y, z1, c01[0], c01[1], c01[2], K);
            }
        }
    }

    // a point of the panel at (u, y), off the facade by off
    private static double spX(ShibuyaScan.Panel g, double u, double off) {
        return g.nx != 0 ? g.plane + g.nx * off : u;
    }

    private static double spZ(ShibuyaScan.Panel g, double u, double off) {
        return g.nx != 0 ? u : g.plane + g.nz * off;
    }

    private static void panelQuad(VertexConsumer out, PoseStack.Pose last, Vec3 cam, ShibuyaScan.Panel g, double ua, double ub, double ya, double yb, double off,
            float r, float gg, float b, float a00, float a10, float a11, float a01) {
        v(out, last, cam, spX(g, ua, off), ya, spZ(g, ua, off), r, gg, b, a00);
        v(out, last, cam, spX(g, ub, off), ya, spZ(g, ub, off), r, gg, b, a10);
        v(out, last, cam, spX(g, ub, off), yb, spZ(g, ub, off), r, gg, b, a11);
        v(out, last, cam, spX(g, ua, off), yb, spZ(g, ua, off), r, gg, b, a01);
    }

    private static boolean facing(ShibuyaScan.Panel s, Vec3 cam, double lim) {
        return (cam.x - s.cx) * s.nx + (cam.z - s.cz) * s.nz >= lim;
    }

    // a panel failing (bad contact): a dark film puts the block out
    private static void renderSignFilm(VertexConsumer out, PoseStack.Pose last, Vec3 cam, float fade) {
        for (ShibuyaScan.Panel s : ShibuyaScan.panels) {
            if (s.shop || s.f >= 0.5f || !facing(s, cam, -1)) {
                continue;
            }
            float a = 0.82f * (1 - s.f) * fade;
            panelQuad(out, last, cam, s, s.u0, s.u1 + 1, s.y0, s.y1 + 1, 0.02, 0.03f, 0.03f, 0.04f, a, a, a, a);
        }
    }

    // a lit sign: the panel livelier over the block, the glow leaking round it on the facade (9 quads, gone at the edge)
    // and a wider veil a little in front (the air lights up); the disc of lit air seen from any side
    private static void renderSigns(VertexConsumer out, PoseStack.Pose last, Vec3 cam, float fade, double t) {
        for (ShibuyaScan.Panel s : ShibuyaScan.panels) {
            if (s.shop) {
                continue;
            }
            double d = Math.sqrt((cam.x - s.cx) * (cam.x - s.cx) + (cam.y - s.cy) * (cam.y - s.cy) + (cam.z - s.cz) * (cam.z - s.cz));
            if (d > 260 || !facing(s, cam, -1)) {
                continue;
            }
            float[] c = s.col;
            float f = s.f * fade;
            if (s.f >= 0.5f) {
                float a = 0.22f * f;
                panelQuad(out, last, cam, s, s.u0, s.u1 + 1, s.y0, s.y1 + 1, 0.025, c[0], c[1], c[2], a, a, a, a);
            }
            double big = Math.min(3.2, 0.9 + Math.sqrt(s.n) * 0.28);
            bloom(out, last, cam, s, 0.06, big, big * 0.8, c, (float) (0.7 * f * Math.min(1.4, s.in)));
            bloom(out, last, cam, s, 0.7, big * 2.6, big * 2.0, c, 0.18f * f);
            disc(out, last, cam, s.cx + s.nx * 1.0, s.cy, s.cz + s.nz * 1.0, Math.min(9, 1.5 + Math.sqrt(s.n) * 0.7), c, 0.13f * f);
            if (s.mode == 2) {
                // the sweep: a clear band running along the sign
                double len = s.u1 + 1 - s.u0, pp = s.u0 - 2 + ((t * (4 + s.rate * 3) + s.ph * 3) % (len + 4));
                double a0 = Math.max(s.u0, pp - 1.2), b0 = Math.min(s.u1 + 1, pp + 1.2);
                if (b0 > a0) {
                    float a = 0.5f * fade;
                    panelQuad(out, last, cam, s, a0, b0, s.y0, s.y1 + 1, 0.04, c[0] * 0.5f + 0.5f, c[1] * 0.5f + 0.5f, c[2] * 0.5f + 0.5f, a, a, a, a);
                }
            }
        }
    }

    private static void bloom(VertexConsumer out, PoseStack.Pose last, Vec3 cam, ShibuyaScan.Panel s, double off, double mu, double my, float[] c, float a) {
        if (a < 0.003f) {
            return;
        }
        int gy = ShibuyaScan.streetY(s.cx + s.nx * 1.5, s.cz + s.nz * 1.5);
        double floor = gy == Integer.MIN_VALUE ? s.y0 - my : gy + 0.03;
        double[] us = {s.u0 - mu, s.u0, s.u1 + 1, s.u1 + 1 + mu}, ys = {Math.max(floor, s.y0 - my), s.y0, s.y1 + 1, s.y1 + 1 + my};
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                panelQuad(out, last, cam, s, us[i], us[i + 1], ys[j], ys[j + 1], off, c[0], c[1], c[2], al(i, j, a), al(i + 1, j, a), al(i + 1, j + 1, a), al(i, j + 1, a));
            }
        }
    }

    private static float al(int i, int j, float a) {
        return (i == 1 || i == 2) && (j == 1 || j == 2) ? a : 0;
    }

    // the reflection on the smooth asphalt: the sign's rectangle mirrored on the street (y' = 2 * street - y) and
    // projected on the street's plane through the camera; strong near the base and gone far off
    private static void renderReflections(VertexConsumer out, PoseStack.Pose last, Vec3 cam, float fade) {
        for (ShibuyaScan.Panel s : ShibuyaScan.panels) {
            if (s.shop) {
                continue;
            }
            int gy = ShibuyaScan.streetY(s.cx + s.nx * 1.5, s.cz + s.nz * 1.5);
            if (gy == Integer.MIN_VALUE || s.y0 - gy > 20 || cam.y < gy + 0.3) {
                continue;
            }
            double dx = cam.x - s.cx, dz = cam.z - s.cz;
            if (dx * s.nx + dz * s.nz < 1.5) {
                continue;
            }
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > 90) {
                continue;
            }
            float a = (float) (0.1 * s.f * Math.min(1, 5 / Math.max(1, s.y0 - gy)) * near01(90 - d, 0, 30) * near01(d, 4, 14)) * fade;
            if (a < 0.004f) {
                continue;
            }
            double y = gy + 0.025, U0 = s.u0 - 0.3, U1 = s.u1 + 1.3, Y0 = s.y0, Y1 = s.y1 + 1;
            float[] c = s.col;
            for (int k = 0; k < 3; k++) {
                double ya = Y0 + (Y1 - Y0) * k / 3, yb = Y0 + (Y1 - Y0) * (k + 1) / 3;
                float aa = a * (1 - k / 3f), ab = a * (1 - (k + 1) / 3f);
                reflV(out, last, cam, spX(s, U0, 0), ya, spZ(s, U0, 0), gy, y, c, aa);
                reflV(out, last, cam, spX(s, U1, 0), ya, spZ(s, U1, 0), gy, y, c, aa);
                reflV(out, last, cam, spX(s, U1, 0), yb, spZ(s, U1, 0), gy, y, c, ab);
                reflV(out, last, cam, spX(s, U0, 0), yb, spZ(s, U0, 0), gy, y, c, ab);
            }
        }
    }

    private static void reflV(VertexConsumer out, PoseStack.Pose last, Vec3 cam, double x, double py, double z, int gy, double y, float[] c, float a) {
        double yy = 2 * gy - py, t = (cam.y - y) / (cam.y - yy);
        v(out, last, cam, cam.x + (x - cam.x) * t, y, cam.z + (z - cam.z) * t, c[0], c[1], c[2], a);
    }

    private static void renderLamps(VertexConsumer out, PoseStack.Pose last, Vec3 cam, float fade, double t) {
        for (ShibuyaScan.Lamp l : ShibuyaScan.lamps) {
            double d = Math.sqrt((l.x - cam.x) * (l.x - cam.x) + (l.y - cam.y) * (l.y - cam.y) + (l.z - cam.z) * (l.z - cam.z));
            if (d > 220 || d < 1) {
                continue;
            }
            double s = new double[] {1.5, 2.4, 0.8, 0.8, 1.1}[l.k];
            float a = new float[] {0.5f, 0.8f, 0.45f, 0.45f, 0.35f}[l.k];
            // the aviation light on top of the towers blinks slowly
            if (l.k == 1) {
                a *= Math.sin(t * 2.4 + l.ph * 6) > 0.2 ? 1 : 0.05f;
            }
            s *= 1 + d * 0.006;
            disc(out, last, cam, l.x, l.y, l.z, s, LC[l.k], a * fade);
        }
    }

    // low haze: at night it takes the color of the neon under it (added), by day it is the beige smog running with the
    // wind; faded near the eye's height (in the eye it washes the screen)
    private static void renderHaze(VertexConsumer out, PoseStack.Pose last, Vec3 cam, float fade, boolean n, double t) {
        if (!ShibuyaScan.field) {
            return;
        }
        int N = ShibuyaScan.FN, fx0 = ShibuyaScan.fx0, fz0 = ShibuyaScan.fz0, FS = ShibuyaScan.FS;
        double R = 60;
        int i0 = Math.max(0, Mth.floor((cam.x - R - fx0) / FS)), i1 = Math.min(N - 2, Mth.floor((cam.x + R - fx0) / FS));
        int j0 = Math.max(0, Mth.floor((cam.z - R - fz0) / FS)), j1 = Math.min(N - 2, Mth.floor((cam.z + R - fz0) / FS));
        if (i1 < i0 || j1 < j0) {
            return;
        }
        double run = t * (2 + 3 * gust);
        double[][] lay = {{1.4, 1}, {3.6, 0.6}};
        int W = i1 - i0 + 2, H = j1 - j0 + 2;
        float[] vc = new float[W * H * 4];
        float[] c = new float[3];
        for (int L = 0; L < 2; L++) {
            double amp = lay[L][1];
            for (int a = 0; a < W; a++) {
                for (int b = 0; b < H; b++) {
                    int i = i0 + a, j = j0 + b, o = (a * H + b) * 4;
                    int gy = ShibuyaScan.FY[Math.min(N - 1, i) * N + Math.min(N - 1, j)];
                    if (gy == Integer.MIN_VALUE) {
                        vc[o + 3] = 0;
                        continue;
                    }
                    double y = gy + lay[L][0];
                    double x = fx0 + (i + 0.5) * FS, z = fz0 + (j + 0.5) * FS;
                    double dd = Math.sqrt((x - cam.x) * (x - cam.x) + (z - cam.z) * (z - cam.z));
                    double nearK = near01(dd, 2, 9) * near01(R, dd, dd + 1) * (1 - near01(dd, R * 0.7, R));
                    double u = x * wx + z * wz, w = -x * wz + z * wx;
                    double m = vnoise((u - run) * 0.05, w * 0.08 + L * 5, 7) * 0.65 + vnoise((u - run * 1.6) * 0.13, w * 0.17, 8) * 0.35;
                    double k = near01(m, 0.3, 0.8);
                    double pat = k * k * (3 - 2 * k) * nearK * amp * near01(Math.abs(y - cam.y), 0.6, 3);
                    if (n) {
                        ShibuyaScan.fieldAt(i, j, c);
                        vc[o] = c[0];
                        vc[o + 1] = c[1];
                        vc[o + 2] = c[2];
                        vc[o + 3] = (float) (pat * 0.14) * fade;
                    } else {
                        vc[o] = 0.86f;
                        vc[o + 1] = 0.82f;
                        vc[o + 2] = 0.68f;
                        vc[o + 3] = (float) (pat * 0.13) * fade;
                    }
                }
            }
            for (int a = 0; a < W - 1; a++) {
                for (int b = 0; b < H - 1; b++) {
                    int i = i0 + a, j = j0 + b, gy = ShibuyaScan.FY[i * N + j];
                    if (gy == Integer.MIN_VALUE) {
                        continue;
                    }
                    int o00 = (a * H + b) * 4, o10 = ((a + 1) * H + b) * 4, o11 = ((a + 1) * H + b + 1) * 4, o01 = (a * H + b + 1) * 4;
                    if (vc[o00 + 3] + vc[o10 + 3] + vc[o11 + 3] + vc[o01 + 3] < 0.004f) {
                        continue;
                    }
                    if (n && vc[o00] + vc[o00 + 1] + vc[o00 + 2] + vc[o11] + vc[o11 + 2] < 0.02f) {
                        continue;
                    }
                    double y = gy + lay[L][0], x0 = fx0 + (i + 0.5) * FS, z0 = fz0 + (j + 0.5) * FS, x1 = x0 + FS, z1 = z0 + FS;
                    v(out, last, cam, x0, y, z0, vc[o00], vc[o00 + 1], vc[o00 + 2], vc[o00 + 3]);
                    v(out, last, cam, x1, y, z0, vc[o10], vc[o10 + 1], vc[o10 + 2], vc[o10 + 3]);
                    v(out, last, cam, x1, y, z1, vc[o11], vc[o11 + 1], vc[o11 + 2], vc[o11 + 3]);
                    v(out, last, cam, x0, y, z1, vc[o01], vc[o01 + 1], vc[o01 + 2], vc[o01 + 3]);
                }
            }
        }
    }

    // steam: a fan of 9 rays with its own outline, white by day; at night nearly black, lit by the neon under it; the
    // cursed building's miasma is the same fan, dark purple. Far to near (sorted per cloud, not per quad).
    private static void renderSteam(VertexConsumer out, PoseStack.Pose last, Vec3 cam, float fade, boolean n, float partial) {
        List<Puff> list = new ArrayList<>(PUFFS.size());
        for (Puff f : PUFFS) {
            double x = Mth.lerp(partial, f.ox, f.x), y = Mth.lerp(partial, f.oy, f.y), z = Mth.lerp(partial, f.oz, f.z);
            double d2 = (x - cam.x) * (x - cam.x) + (y - cam.y) * (y - cam.y) + (z - cam.z) * (z - cam.z);
            if (d2 < 160 * 160 && (x - cam.x) * lookX + (y - cam.y) * lookY + (z - cam.z) * lookZ > -4) {
                f.dist = Math.sqrt(d2);
                list.add(f);
            }
        }
        list.sort((a, b) -> Double.compare(b.dist, a.dist));
        float[] c = new float[3];
        for (Puff f : list) {
            double dist = f.dist, tone = f.tone;
            double x = Mth.lerp(partial, f.ox, f.x), y = Mth.lerp(partial, f.oy, f.y), z = Mth.lerp(partial, f.oz, f.z);
            double k = f.age / f.life;
            boolean curse = f.v.kind == 2;
            double s = f.s0 + f.age * f.grow * (1 + gust * 0.4);
            double a = Math.sin(Math.min(1, k * 8) * Math.PI / 2) * Math.pow(1 - k, 1.3) * (curse ? 0.7 : f.v.kind == 0 ? 1.0 : 0.7);
            a *= near01(dist, 1, 4) * fade;
            if (a < 0.004) {
                continue;
            }
            float r, g, b;
            if (curse) {
                r = (float) (0.2 + 0.1 * tone);
                g = 0.07f;
                b = (float) (0.3 + 0.12 * tone);
                if (n) {
                    r *= 1.3f;
                    b *= 1.4f;
                }
            } else if (n) {
                ShibuyaScan.fieldXZ(x, z, c);
                int gy = ShibuyaScan.streetY(x, z);
                double lit = Math.max(0, 1 - (y - (gy == Integer.MIN_VALUE ? y : gy)) / 9) * 0.9;
                r = (float) (0.3 + c[0] * lit);
                g = (float) (0.28 + c[1] * lit);
                b = (float) (0.34 + c[2] * lit);
            } else {
                float tn = (float) (0.86 + tone * 0.1);
                r = tn;
                g = tn;
                b = tn * 0.98f;
            }
            face(cam, x, y, z);
            double px = 0, py = 0, pz = 0;
            for (int q = 0; q <= 9; q++) {
                double ang = f.rot + (q % 9) / 9.0 * Math.PI * 2, rr = f.r[q % 9] * (1 + 0.12 * Math.sin(f.age * 1.1 + q * 1.7)) * s;
                double ox = Math.cos(ang) * rr, oy = Math.sin(ang) * rr;
                double X = x + BR[0] * ox + BU[0] * oy, Y = y + BR[1] * ox + BU[1] * oy, Z = z + BR[2] * ox + BU[2] * oy;
                if (q > 0) {
                    tri(out, last, cam, x, y, z, (float) a, px, py, pz, X, Y, Z, 0, r, g, b);
                }
                px = X;
                py = Y;
                pz = Z;
            }
        }
    }

    // the tip of the miasma glows a little at night (cursed energy)
    private static void renderCurseGlow(VertexConsumer out, PoseStack.Pose last, Vec3 cam, float fade, float partial) {
        float[] c = {0.6f, 0.25f, 1f};
        for (Puff f : PUFFS) {
            if (f.v.kind != 2) {
                continue;
            }
            double k = f.age / f.life, a = Math.sin(Math.min(1, k * 8) * Math.PI / 2) * Math.pow(1 - k, 1.3) * 0.7 * 0.25 * fade;
            if (a < 0.004) {
                continue;
            }
            double s = f.s0 + f.age * f.grow;
            disc(out, last, cam, Mth.lerp(partial, f.ox, f.x), Mth.lerp(partial, f.oy, f.y), Mth.lerp(partial, f.oz, f.z), s * 0.6, c, (float) a);
        }
    }

    // the wind by day: pale dust running along the street, low (a thin ribbon, light, gone at its tail)
    private static void renderStreaks(VertexConsumer out, PoseStack.Pose last, Vec3 cam, float fade, float partial) {
        float[] col = {0.92f, 0.9f, 0.84f};
        for (Streak r : STREAKS) {
            double x = Mth.lerp(partial, r.ox, r.x), z = Mth.lerp(partial, r.oz, r.z);
            double cx = cam.x - x, cy = cam.y - r.y, cz = cam.z - z, dist = Math.sqrt(cx * cx + cy * cy + cz * cz);
            double life = Math.sin(Math.min(1, r.age / r.life) * Math.PI);
            float a = (float) (0.2 * near01(64 - dist, 0, 36) * near01(dist, 6, 12) * (0.2 + 0.8 * gust) * life * Math.min(1.3, r.k)) * fade;
            if (a < 0.004f) {
                continue;
            }
            double len = (6 + 12 * gust) * r.s * 0.22 * r.k;
            double sx = r.dz * cy, sy = r.dx * cz - r.dz * cx, sz = -r.dx * cy, sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (sl < 1e-6) {
                continue;
            }
            double wd = 0.03 * (1 + dist * 0.02) / sl;
            sx *= wd;
            sy *= wd;
            sz *= wd;
            double tx = x - r.dx * len, tz = z - r.dz * len;
            v(out, last, cam, tx - sx, r.y - sy, tz - sz, col[0], col[1], col[2], 0);
            v(out, last, cam, tx + sx, r.y + sy, tz + sz, col[0], col[1], col[2], 0);
            v(out, last, cam, x + sx, r.y + sy, z + sz, col[0], col[1], col[2], a);
            v(out, last, cam, x - sx, r.y - sy, z - sz, col[0], col[1], col[2], a);
        }
    }

    private static void renderFlyers(VertexConsumer out, PoseStack.Pose last, Vec3 cam, float fade, boolean n, float partial) {
        float[] c = new float[3];
        for (Flyer f : FLYERS) {
            double x = Mth.lerp(partial, f.ox, f.x), y = Mth.lerp(partial, f.oy, f.y), z = Mth.lerp(partial, f.oz, f.z);
            double d = Math.sqrt((x - cam.x) * (x - cam.x) + (y - cam.y) * (y - cam.y) + (z - cam.z) * (z - cam.z));
            float al = (float) (near01(30 - d, 0, 8) * near01(d, 2, 4)) * fade;
            if (al < 0.01f) {
                continue;
            }
            // the sheet tumbling round its own axis
            double s = f.s * 0.5, ax = f.ax, ay = f.ay, az = f.az;
            double ppx = Math.abs(ay) < 0.9 ? 0 : 1, ppy = Math.abs(ay) < 0.9 ? 1 : 0;
            double e1x = -az * ppy, e1y = az * ppx, e1z = ax * ppy - ay * ppx, l1 = Math.max(1e-6, Math.sqrt(e1x * e1x + e1y * e1y + e1z * e1z));
            e1x /= l1;
            e1y /= l1;
            e1z /= l1;
            double e2x = ay * e1z - az * e1y, e2y = az * e1x - ax * e1z, e2z = ax * e1y - ay * e1x;
            double co = Math.cos(f.ang), sn = Math.sin(f.ang);
            double ux = (e1x * co + e2x * sn) * s, uy = (e1y * co + e2y * sn) * s, uz = (e1z * co + e2z * sn) * s;
            double vx = ax * s * 0.72, vy = ay * s * 0.72, vz = az * s * 0.72;
            double fac = Math.abs((e2x * co - e1x * sn) * 0.3 + (e2y * co - e1y * sn) * 0.9 + (e2z * co - e1z * sn) * 0.3);
            float L = (float) (0.78 + 0.22 * fac);
            float tr = 1, tg = 1, tb = 1;
            if (n) {
                ShibuyaScan.fieldXZ(x, z, c);
                L *= 0.22f;
                tr = 1 + c[0] * 3.2f;
                tg = 1 + c[1] * 3.2f;
                tb = 1 + c[2] * 3.2f;
            }
            float pr = 0.93f * L * tr, pg = 0.91f * L * tg, pb = 0.86f * L * tb;
            float br = f.c[0] * L * tr, bg = f.c[1] * L * tg, bb = f.c[2] * L * tb;
            double b = 1 - 2 * f.band;
            // the sheet (from b to 1 along it) and the printed band (from -1 to b)
            fv(out, last, cam, x, y, z, ux, uy, uz, vx, vy, vz, 1, 1, pr, pg, pb, al);
            fv(out, last, cam, x, y, z, ux, uy, uz, vx, vy, vz, 1, -1, pr, pg, pb, al);
            fv(out, last, cam, x, y, z, ux, uy, uz, vx, vy, vz, b, -1, pr, pg, pb, al);
            fv(out, last, cam, x, y, z, ux, uy, uz, vx, vy, vz, b, 1, pr, pg, pb, al);
            fv(out, last, cam, x, y, z, ux, uy, uz, vx, vy, vz, b, 1, br, bg, bb, al);
            fv(out, last, cam, x, y, z, ux, uy, uz, vx, vy, vz, b, -1, br, bg, bb, al);
            fv(out, last, cam, x, y, z, ux, uy, uz, vx, vy, vz, -1, -1, br, bg, bb, al);
            fv(out, last, cam, x, y, z, ux, uy, uz, vx, vy, vz, -1, 1, br, bg, bb, al);
        }
    }

    private static void fv(VertexConsumer out, PoseStack.Pose last, Vec3 cam, double x, double y, double z, double ux, double uy, double uz, double vx, double vy, double vz,
            double su, double sv, float r, float g, float b, float a) {
        v(out, last, cam, x + ux * su + vx * sv, y + uy * su + vy * sv + 0.003, z + uz * su + vz * sv, r, g, b, a);
    }

    private static void renderCrows(VertexConsumer out, PoseStack.Pose last, Vec3 cam, float fade, boolean n) {
        float L = n ? 0.04f : 0.08f, a = 0.95f * fade;
        for (Crow cw : CROWS) {
            double x = cw.cx + Math.cos(cw.a) * cw.r, z = cw.cz + Math.sin(cw.a) * cw.r, y = cw.y + Math.sin(cw.ph * 0.1) * 1.5;
            double sg = Math.signum(cw.sp), fx = -Math.sin(cw.a) * sg, fz = Math.cos(cw.a) * sg, lx = -fz, lz = fx, flap = Math.sin(cw.ph) * 0.35, w = 0.7;
            for (int side = -1; side <= 1; side += 2) {
                v(out, last, cam, x, y, z, L, L, L * 1.1f, a);
                v(out, last, cam, x + side * lx * w, y + flap, z + side * lz * w, L, L, L * 1.1f, a);
                v(out, last, cam, x + side * lx * w * 0.6 - fx * 0.3, y + flap * 0.6, z + side * lz * w * 0.6 - fz * 0.3, L, L, L * 1.1f, a);
                v(out, last, cam, x - fx * 0.35, y, z - fz * 0.35, L, L, L * 1.1f, a);
            }
        }
    }

    // the police car's lights painting the street red and blue round it, the halo on the bar, the headlights on the
    // asphalt at night
    private static void renderCar(VertexConsumer out, PoseStack.Pose last, Vec3 cam, float fade, boolean n, double t, float partial) {
        if (car == null) {
            return;
        }
        int gy = ShibuyaScan.streetY(car.x, car.z);
        if (gy == Integer.MIN_VALUE) {
            return;
        }
        double cx0 = car.x - car.dirX * car.v * (1 - partial) * 0.05, cz0 = car.z - car.dirZ * car.v * (1 - partial) * 0.05, y = gy + 0.03;
        double sx = -car.dirZ, sz = car.dirX; // the car's side
        float[][] cols = {{1, 0.12f, 0.12f}, {0.15f, 0.3f, 1}};
        for (int k = 0; k < 2; k++) {
            float[] col = cols[k];
            double ang = t * 9 + k * Math.PI;
            boolean on = (k == 0) == car.on;
            float a = (on ? 0.55f : 0.12f) * (n ? 1 : 0.45f) * fade;
            double px = cx0 + Math.cos(ang) * 3.2, pz = cz0 + Math.sin(ang) * 3.2, R = 5.5;
            for (int q = 0; q < 12; q++) {
                double a0 = q / 12.0 * Math.PI * 2, a1 = (q + 1) / 12.0 * Math.PI * 2;
                tri(out, last, cam, px, y, pz, a, px + Math.cos(a0) * R, y, pz + Math.sin(a0) * R, px + Math.cos(a1) * R, y, pz + Math.sin(a1) * R, 0, col[0], col[1], col[2]);
            }
            double side = k == 0 ? -0.42 : 0.42;
            disc(out, last, cam, cx0 - car.dirX * 0.1 + sx * side, gy + 2.0, cz0 - car.dirZ * 0.1 + sz * side, 1.6, col, (on ? 0.9f : 0.15f) * fade);
        }
        if (n) {
            // the headlights: a clear fan ahead on the asphalt
            for (int s = -1; s <= 1; s += 2) {
                double bx = cx0 + car.dirX * 2.2 + sx * 0.6 * s, bz = cz0 + car.dirZ * 2.2 + sz * 0.6 * s;
                double fx = cx0 + car.dirX * 16, fz = cz0 + car.dirZ * 16;
                tri(out, last, cam, bx, y, bz, 0.5f * fade, fx + sx * (s < 0 ? -4 : -2.5), y, fz + sz * (s < 0 ? -4 : -2.5), fx + sx * (s < 0 ? 2.5 : 4), y,
                        fz + sz * (s < 0 ? 2.5 : 4), 0, 1, 0.95f, 0.8f);
            }
        }
    }

    // ---------------------------------------------------------------- screen and fog

    public static boolean gradeOn() {
        return active() && FxShaders.shibuyaGrade != null;
    }

    // The screen grade (between FxPost.beginPasses and endPasses): the preview's CSS filter and overlay layers.
    static void gradePass(float partial) {
        ShaderInstance sh = FxShaders.shibuyaGrade;
        float fade = Mth.lerp(partial, lastStrength, strength);
        if (sh == null || fade <= 0.001f) {
            return;
        }
        // 0 day, 1 night, 2 metro
        float mode = underground ? 2 : night > 0.5f ? 1 : 0;
        sh.safeGetUniform("Mode").set(mode);
        sh.safeGetUniform("Night").set(night);
        sh.safeGetUniform("Fade").set(fade);
        FxPost.pass(sh);
    }

    // The fog: by day a yellowish grey smog, the far end of the avenue lost in beige; at night the violet of the light
    // pollution (never black); in the station the still air of the platform.
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        float fade = Mth.lerp((float) event.getPartialTick(), lastStrength, strength);
        if (fade <= 0.001f) {
            return;
        }
        float r, g, b;
        if (underground) {
            r = 0x1c / 255f;
            g = 0x1f / 255f;
            b = 0x24 / 255f;
        } else {
            r = Mth.lerp(night, 0xc9 / 255f, 0x2c / 255f);
            g = Mth.lerp(night, 0xc3 / 255f, 0x20 / 255f);
            b = Mth.lerp(night, 0xab / 255f, 0x38 / 255f);
        }
        event.setRed(Mth.lerp(fade, event.getRed(), r));
        event.setGreen(Mth.lerp(fade, event.getGreen(), g));
        event.setBlue(Mth.lerp(fade, event.getBlue(), b));
    }

    public static void onFog(ViewportEvent.RenderFog event) {
        float fade = Mth.lerp((float) event.getPartialTick(), lastStrength, strength);
        if (fade <= 0.001f || event.getMode() != FogRenderer.FogMode.FOG_TERRAIN) {
            return;
        }
        float near = underground ? 40 : Mth.lerp(night, 40f, 34f), far = underground ? 190 : Mth.lerp(night, 260f, 240f);
        event.setNearPlaneDistance(Mth.lerp(fade, event.getNearPlaneDistance(), Math.min(event.getNearPlaneDistance(), near)));
        event.setFarPlaneDistance(Mth.lerp(fade, event.getFarPlaneDistance(), Math.min(event.getFarPlaneDistance(), far)));
        event.setCanceled(true);
    }
}
