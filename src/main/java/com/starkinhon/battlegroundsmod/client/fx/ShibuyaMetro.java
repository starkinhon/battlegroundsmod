package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.starkinhon.battlegroundsmod.ModSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

// Shibuya's metro (Stark 2026-11-14: "consegue fzr um metro que passe, vá embora, volte?"; the preview's metro.js, the
// source of truth): a train of 3 cars comes out of the east tunnel with its headlights on the rails, brakes throwing
// sparks, stops at the platform, opens its doors, plays the departure melody, closes, rolls off into the cursed west
// tunnel; then the next one comes back on the other track and leaves east. It is drawn by the client from the game's
// clock (every player sees the same train at the same moment, nothing to send), at the station ShibuyaScan found
// (the rails and the platform stairs). The generation no longer builds the parked train.
// The tunnel is carved only 110 blocks each way (ShibuyaFeature.STATION_HALF): past the platform it goes dark, a few
// thin black veils across it, each taking a little light (only the ones ahead of the player), and a black cap at its end;
// the train is cut at the cap, so it comes out of the dark and goes into it. Its lights still shine in the dark.
// (The passengers getting off and on wait for the pedestrians' model, the "Biomas de Jujutsu" thread's port.)
final class ShibuyaMetro {
    private static final RandomSource RANDOM = RandomSource.createThreadSafe();
    // ShibuyaGen.metro: BED = -17 (the train's floor on the rail at -16), the platform PX1 = 52, the tunnel 110 each way
    private static final int Y0 = -16, PX1 = 52, TUN = 110;
    private static final double LEN = 62, STOP = 31, START = 211, GAP = 9, DOOR_T = 12.6;
    private static final double OUT_END = TUN + LEN + 4 - STOP;
    private static final double IN_T = (START - 100) / 20 + 10, OUT_T = Math.sqrt(OUT_END / 0.625), RUN = IN_T + DOOR_T + OUT_T + GAP;
    private static final int[] DOORS = {4, 10, 16};

    // the train now: which track, where its nose is (f), its speed, the doors (0 shut .. 1 open), its phase
    static boolean on;
    static int track, phase; // 0 in, 1 stop, 2 out, 3 gap
    static double f, lastF, vel, door, pt;
    private static double lastRunT = -1;
    private static int lastPhase = -1, lastTrack = -1;
    private static boolean announced, chimed, melody, pinpon, shut;

    private static final class Spark {
        double x, y, z, vx, vy, vz, age, life;
    }

    private static final List<Spark> SPARKS = new ArrayList<>();
    private static ShibuyaAirFx.Loop rumble, motor, squeal;

    private ShibuyaMetro() {
    }

    static void reset() {
        on = false;
        SPARKS.clear();
        for (ShibuyaAirFx.Loop l : new ShibuyaAirFx.Loop[] {rumble, motor, squeal}) {
            if (l != null) {
                l.end();
            }
        }
        rumble = motor = squeal = null;
        lastPhase = -1;
    }

    // north track (0): z from c - 7 to c - 4, runs toward -x; south (1): z from c + 7 to c + 4, runs toward +x
    private static int dir(int k) {
        return k == 0 ? -1 : 1;
    }

    private static double z0(int k) {
        return k == 0 ? ShibuyaScan.stC - 7 : ShibuyaScan.stC + 7;
    }

    private static double edge(int k) {
        return k == 0 ? ShibuyaScan.stC - 4 : ShibuyaScan.stC + 4;
    }

    private static int side(int k) {
        return k == 0 ? 1 : -1;
    }

    private static double wx(double lx) {
        return track == 0 ? f + lx : f - lx;
    }

    private static double wz(double lz) {
        return track == 0 ? z0(0) + lz : z0(1) - lz;
    }

    // ---------------------------------------------------------------- tick

    static void tick(ClientLevel level, Player player) {
        if (!ShibuyaScan.station || Math.abs(player.getZ() - ShibuyaScan.stC) > 140 || Math.abs(player.getX() - ShibuyaScan.stOx) > 220) {
            if (on) {
                reset();
            }
            return;
        }
        on = true;
        int ox = ShibuyaScan.stOx;
        double t = level.getGameTime() / 20.0 + ShibuyaScan.ih(ox, ShibuyaScan.stC, 71) * RUN * 2;
        double cyc = t % (RUN * 2);
        int k = cyc < RUN ? 0 : 1;
        double rt = cyc - k * RUN;
        int d = dir(k);
        double stop = ox - d * STOP, start = ox - d * START;
        lastF = f;
        if (k != lastTrack) {
            lastF = Double.NaN;
        }
        track = k;
        if (rt < IN_T) {
            phase = 0;
            pt = rt;
            double tc = (START - 100) / 20;
            if (rt < tc) {
                f = start + d * 20 * rt;
                vel = 20;
            } else {
                double tb = rt - tc;
                f = stop - d * (10 - tb) * (10 - tb);
                vel = 2 * (10 - tb);
            }
            door = 0;
        } else if (rt < IN_T + DOOR_T) {
            phase = 1;
            pt = rt - IN_T;
            f = stop;
            vel = 0;
            door = pt < 0.8 ? 0 : pt < 2.0 ? (pt - 0.8) / 1.2 : pt < 10.6 ? 1 : pt < 11.8 ? 1 - (pt - 10.6) / 1.2 : 0;
        } else if (rt < IN_T + DOOR_T + OUT_T) {
            phase = 2;
            pt = rt - IN_T - DOOR_T;
            f = stop + d * 0.625 * pt * pt;
            vel = 1.25 * pt;
            door = 0;
        } else {
            phase = 3;
            pt = rt - IN_T - DOOR_T - OUT_T;
            door = 0;
        }
        if (phase != lastPhase) {
            announced = chimed = melody = pinpon = shut = false;
        }
        lastPhase = phase;
        lastTrack = k;
        lastRunT = rt;
        // the station's sounds (heard on the platform and in the concourse)
        boolean near = ShibuyaAirFx.underground;
        double sx = ox, sy = ShibuyaScan.stCity - 13, sz = ShibuyaScan.stC;
        if (phase == 0 && Math.abs(f - stop) < 150 && !announced) {
            announced = true;
            if (near) {
                play(level, ModSounds.METRO_AVISO.get(), sx, sy, sz, 2.5f);
            }
        }
        if (phase == 1) {
            if (pt > 0.8 && !chimed) {
                chimed = true;
                if (near) {
                    play(level, ModSounds.METRO_CHEGADA.get(), sx, sy, sz, 2.5f);
                    play(level, ModSounds.METRO_PORTA.get(), wx(LEN / 2), ShibuyaScan.stCity + Y0 + 2, edge(k), 1.6f);
                }
            }
            if (pt > 6.2 && !melody) {
                melody = true;
                if (near) {
                    play(level, ModSounds.METRO_MELODIA.get(), sx, sy, sz, 2.5f);
                }
            }
            if (pt > 10.0 && !pinpon) {
                pinpon = true;
                if (near) {
                    play(level, ModSounds.METRO_PINPON.get(), sx, sy, sz, 2.5f);
                }
            }
            if (pt > 10.6 && !shut) {
                shut = true;
                if (near) {
                    play(level, ModSounds.METRO_PORTA.get(), wx(LEN / 2), ShibuyaScan.stCity + Y0 + 2, edge(k), 1.6f);
                }
            }
        }
        // brake sparks (the wheels on the rail) while slowing
        double yb = ShibuyaScan.stCity + Y0;
        if (phase == 0 && vel > 1.2 && vel < 13 && near) {
            for (int car = 0; car < 3; car++) {
                for (int bx : new int[] {3, 17}) {
                    if (RANDOM.nextDouble() < 0.05 * 9) {
                        double lx = car * 21 + bx + (RANDOM.nextDouble() - 0.5) * 2, lz = RANDOM.nextDouble() < 0.5 ? 0.35 : 2.65;
                        double x = wx(lx);
                        if (Math.abs(x - ox) > TUN) {
                            continue;
                        }
                        for (int i = 0; i < 3; i++) {
                            Spark s = new Spark();
                            s.x = x;
                            s.y = yb + 0.1;
                            s.z = wz(lz);
                            s.vx = -d * (2 + RANDOM.nextDouble() * 4) + (RANDOM.nextDouble() - 0.5);
                            s.vy = 1 + RANDOM.nextDouble() * 2.5;
                            s.vz = (RANDOM.nextDouble() - 0.5) * 2;
                            s.life = 0.25 + RANDOM.nextDouble() * 0.35;
                            SPARKS.add(s);
                        }
                    }
                }
            }
        }
        for (Spark s : SPARKS) {
            s.age += 0.05;
            s.x += s.vx * 0.05;
            s.y += s.vy * 0.05;
            s.z += s.vz * 0.05;
            s.vy -= 14 * 0.05;
            if (s.y < yb) {
                s.y = yb;
                s.vy *= -0.3;
            }
        }
        SPARKS.removeIf(s -> s.age >= s.life);
        sounds(level, player, ox, d);
    }

    private static void play(ClientLevel level, SoundEvent ev, double x, double y, double z, float vol) {
        level.playLocalSound(x, y, z, ev, SoundSource.AMBIENT, vol, 1f, false);
    }

    private static void sounds(ClientLevel level, Player player, int ox, int d) {
        Minecraft mc = Minecraft.getInstance();
        boolean moving = phase == 0 || phase == 2;
        // the train's point nearest the ear, inside the tunnel
        double xa = wx(0), xb = wx(LEN), cx = Mth.clamp(player.getX(), Math.min(xa, xb), Math.max(xa, xb));
        cx = Mth.clamp(cx, ox - TUN, ox + TUN);
        double y = ShibuyaScan.stCity + Y0 + 1, z = z0(track) + (track == 0 ? 1.5 : -1.5);
        boolean under = ShibuyaAirFx.underground;
        double sp = vel / 22;
        rumble = keep(mc, rumble, ModSounds.METRO_RONCO.get());
        // on the street only the deep rumble comes up, faint
        rumble.follow(cx, y, z, moving ? (float) ((0.6 + 2.4 * sp) * (under ? 1 : 0.35)) : 0);
        motor = keep(mc, motor, ModSounds.METRO_MOTOR.get());
        // the VVVF motor: steps up the scale when it leaves (do-re-mi...), then smooth; down while braking
        double[] sc = {349, 392, 440, 466, 523, 587, 622, 698};
        double mf = 0;
        if (phase == 2) {
            mf = vel < 12 ? sc[Math.min(7, (int) (vel / 1.5))] : 698 + (vel - 12) * 40;
        } else if (phase == 0 && vel < 14) {
            mf = 180 + vel * 45;
        }
        motor.follow(cx, y, z, mf > 0 && under ? 1.2f : 0);
        if (mf > 0) {
            // (Doppler by the speed toward the ear, like the preview)
            double dop = 343 / (343 + d * Math.signum(player.getX() - cx == 0 ? 1 : player.getX() - cx) * -vel * 0.6);
            motor.pitchTo = (float) Mth.clamp(mf * dop / 523, 0.5, 2);
        }
        squeal = keep(mc, squeal, ModSounds.METRO_FREIO.get());
        squeal.follow(cx, y, z, phase == 0 && vel < 7 && vel > 0.4 && under ? 1.0f : 0);
        // the wheels' clack at each rail joint (every 25 blocks), each bogie near the ear
        if (moving && vel > 2 && !Double.isNaN(lastF) && under) {
            for (int car = 0; car < 3; car++) {
                for (int bx : new int[] {3, 17}) {
                    double a = track == 0 ? lastF + car * 21 + bx : lastF - car * 21 - bx, b = track == 0 ? f + car * 21 + bx : f - car * 21 - bx;
                    if (Math.floorDiv((long) Math.floor(a), 25) != Math.floorDiv((long) Math.floor(b), 25) && Math.abs(b - ox) <= TUN
                            && Math.abs(b - player.getX()) < 40) {
                        level.playLocalSound(b, y, z, ModSounds.METRO_TRILHO.get(), SoundSource.AMBIENT, 1.2f, 0.95f + RANDOM.nextFloat() * 0.1f, false);
                    }
                }
            }
        }
    }

    private static ShibuyaAirFx.Loop keep(Minecraft mc, ShibuyaAirFx.Loop l, SoundEvent ev) {
        if (l == null || !mc.getSoundManager().isActive(l)) {
            l = new ShibuyaAirFx.Loop(ev, false);
            mc.getSoundManager().play(l);
        }
        return l;
    }

    // ---------------------------------------------------------------- render

    private static VertexConsumer cur;
    private static PoseStack.Pose curLast;
    private static Vec3 curCam;
    private static double clipLo, clipHi, oxW, ozW, cosR, sinR, baseY;
    private static int mode; // 0 the train (local z 0..3), 1 the police car (rotated)

    static void render(MultiBufferSource.BufferSource buffers, PoseStack.Pose last, Vec3 cam, float fade, float partial) {
        // (from the street nothing of the station shows: it's under the ground)
        if (!on || !ShibuyaAirFx.underground) {
            return;
        }
        int ox = ShibuyaScan.stOx;
        double yb = ShibuyaScan.stCity + Y0;
        boolean visible = phase != 3;
        double fNow = f;
        if (visible && !Double.isNaN(lastF) && phase != 1) {
            fNow = Mth.lerp(partial, lastF, f);
        }
        double fKeep = f;
        f = fNow;
        if (visible) {
            VertexConsumer solid = buffers.getBuffer(FxRenderTypes.SOLID);
            drawTrain(solid, last, cam, ox, yb);
            buffers.endBatch(FxRenderTypes.SOLID);
        }
        // the dark of the tunnels (after the train: it darkens it too), far to near
        VertexConsumer area = buffers.getBuffer(FxRenderTypes.AREA_FLAT);
        drawDark(area, last, cam, ox, yb, fade);
        buffers.endBatch(FxRenderTypes.AREA_FLAT);
        if (visible) {
            VertexConsumer glow = buffers.getBuffer(FxRenderTypes.GLOW);
            drawLights(glow, last, cam, ox, yb, fade);
            buffers.endBatch(FxRenderTypes.GLOW);
        }
        f = fKeep;
    }

    // thin black veils across the tunnel past the platform (each takes a little light: together a dark that swallows rail,
    // wall and train), only the ones ahead of the player, and an opaque cap at the tunnel's end
    private static void drawDark(VertexConsumer out, PoseStack.Pose last, Vec3 cam, int ox, double yb, float fade) {
        double c = ShibuyaScan.stC, y0 = yb, y1 = ShibuyaScan.stCity - 9, z0 = c - 7, z1 = c + 7;
        double x0 = PX1 + 6, x1 = TUN + 0.98;
        int n = 36;
        double pr = Math.abs(cam.x - ox);
        for (int sgn = -1; sgn <= 1; sgn += 2) {
            // far to near: from the cap inward
            double prev;
            for (int i = n; i >= 1; i--) {
                double u = i / (double) n, t = i == n ? 0 : tAt(u);
                prev = i == 1 ? 1 : tAt((i - 1) / (double) n);
                double a = i == n ? 1 : 1 - t / prev;
                double x = ox + sgn * (x0 + (x1 - x0) * u);
                // only ahead of the player (a veil the player stands in or has passed would darken the screen in steps)
                boolean ahead = pr <= PX1 + 4 || Math.signum(x - ox) == Math.signum(cam.x - ox) && Math.abs(x - ox) > pr + 4;
                if (!ahead && i != n) {
                    continue;
                }
                float al = (float) a * (i == n ? 1 : fade);
                ShibuyaAirFx.v(out, last, cam, x, y0, z0, 0, 0, 0, al);
                ShibuyaAirFx.v(out, last, cam, x, y0, z1, 0, 0, 0, al);
                ShibuyaAirFx.v(out, last, cam, x, y1, z1, 0, 0, 0, al);
                ShibuyaAirFx.v(out, last, cam, x, y1, z0, 0, 0, 0, al);
            }
        }
    }

    private static double tAt(double u) {
        return Math.exp(-3.2 * Math.pow(Math.max(0, u), 1.6));
    }

    // a box in the train's own coordinates (lx along it from the nose, ly up from its floor, lz 0..3 across, 3 = the
    // platform's side), cut at the tunnel's ends, each face shaded like the preview's boxInto
    private static void box(double x0, double y0, double z0, double x1, double y1, double z1, int rgb, double k) {
        float r = (rgb >> 16 & 255) / 255f, g = (rgb >> 8 & 255) / 255f, b = (rgb & 255) / 255f;
        double ax, bx, az, bz;
        if (mode == 0) {
            double wa = wx(x0), wb = wx(x1);
            ax = Math.min(wa, wb);
            bx = Math.max(wa, wb);
            ax = Math.max(ax, clipLo);
            bx = Math.min(bx, clipHi);
            if (bx <= ax) {
                return;
            }
            double za = wz(z0), zb = wz(z1);
            az = Math.min(za, zb);
            bz = Math.max(za, zb);
            boxW(ax, baseY + y0, az, bx, baseY + y1, bz, r, g, b, k, 0.68, 0.8);
        } else {
            // the car: local x forward, z across, rotated by its direction (only 4 directions: axis-aligned)
            double[] p0 = rot(x0, z0), p1 = rot(x1, z1);
            boolean alongX = Math.abs(cosR) > 0.5;
            boxW(Math.min(p0[0], p1[0]), baseY + y0, Math.min(p0[1], p1[1]), Math.max(p0[0], p1[0]), baseY + y1, Math.max(p0[1], p1[1]), r, g, b, k,
                    alongX ? 0.68 : 0.8, alongX ? 0.8 : 0.68);
        }
    }

    private static double[] rot(double x, double z) {
        return new double[] {oxW + x * cosR - z * sinR, ozW + x * sinR + z * cosR};
    }

    private static void boxW(double x0, double y0, double z0, double x1, double y1, double z1, float r, float g, float b, double k, double zk, double xk) {
        face(x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, r, g, b, k * xk);
        face(x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0, r, g, b, k * xk);
        face(x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, r, g, b, k);
        face(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r, g, b, k * 0.5);
        face(x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1, r, g, b, k * zk);
        face(x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, r, g, b, k * zk);
    }

    private static void face(double ax, double ay, double az, double bx, double by, double bz, double cx, double cy, double cz, double dx, double dy, double dz,
            float r, float g, float b, double k) {
        float kr = (float) Math.min(1, r * k), kg = (float) Math.min(1, g * k), kb = (float) Math.min(1, b * k);
        ShibuyaAirFx.v(cur, curLast, curCam, ax, ay, az, kr, kg, kb, 1);
        ShibuyaAirFx.v(cur, curLast, curCam, bx, by, bz, kr, kg, kb, 1);
        ShibuyaAirFx.v(cur, curLast, curCam, cx, cy, cz, kr, kg, kb, 1);
        ShibuyaAirFx.v(cur, curLast, curCam, dx, dy, dz, kr, kg, kb, 1);
    }

    // a flat rectangle on the side lz (0 or 3) of the train, out from it by off (no two faces in the same plane: the
    // window is 0.03 out of the body, the ads and the seated 0.05, the standing 0.07, the frames 0.1, the doors 0.12)
    private static void flat(double x0, double y0, double x1, double y1, int sideZ, double off, int rgb, double k) {
        float r = (rgb >> 16 & 255) / 255f, g = (rgb >> 8 & 255) / 255f, b = (rgb & 255) / 255f;
        double wa = Math.max(Math.min(wx(x0), wx(x1)), clipLo), wb = Math.min(Math.max(wx(x0), wx(x1)), clipHi);
        if (wb <= wa) {
            return;
        }
        double z = wz(sideZ == 0 ? -off : 3 + off);
        float kr = (float) Math.min(1, r * k), kg = (float) Math.min(1, g * k), kb = (float) Math.min(1, b * k);
        ShibuyaAirFx.v(cur, curLast, curCam, wa, baseY + y0, z, kr, kg, kb, 1);
        ShibuyaAirFx.v(cur, curLast, curCam, wb, baseY + y0, z, kr, kg, kb, 1);
        ShibuyaAirFx.v(cur, curLast, curCam, wb, baseY + y1, z, kr, kg, kb, 1);
        ShibuyaAirFx.v(cur, curLast, curCam, wa, baseY + y1, z, kr, kg, kb, 1);
    }

    private static void drawTrain(VertexConsumer out, PoseStack.Pose last, Vec3 cam, int ox, double yb) {
        cur = out;
        curLast = last;
        curCam = cam;
        mode = 0;
        baseY = yb;
        clipLo = ox - TUN;
        clipHi = ox + TUN + 1;
        int seed = ShibuyaScan.stOx * 31 + track * 17 + 5;
        for (int car = 0; car < 3; car++) {
            double x0 = car * 21;
            // in the station the light of the platform; in the tunnel darker
            double carMid = Math.abs(wx(x0 + 10) - ox);
            double k = carMid <= PX1 + 4 ? 1 : 0.42;
            box(x0 + 0.6, 0, 0.35, x0 + 19.4, 0.75, 2.65, 0x2a2c2e, k); // chassis
            for (int bx : new int[] {3, 17}) {
                box(x0 + bx - 1.4, -0.02, 0.15, x0 + bx + 1.4, 0.6, 2.85, 0x1a1b1d, k); // bogies
            }
            box(x0, 0.75, 0, x0 + 20, 5, 3, 0xc4c8cc, k); // body
            box(x0 + 0.3, 5, 0.25, x0 + 19.7, 5.35, 2.75, 0x9a9ea2, k); // roof
            for (int ax : new int[] {5, 15}) {
                box(x0 + ax - 1.6, 5.35, 0.7, x0 + ax + 1.6, 5.8, 2.3, 0x7e8286, k); // air conditioning
            }
            for (int s = 0; s <= 1; s++) {
                int sz = s * 3;
                // the orange band under and over the windows
                flat(x0, 1.0, x0 + 20, 1.6, sz, 0.03, 0xf39a1e, k);
                flat(x0, 4.05, x0 + 20, 4.25, sz, 0.03, 0xf39a1e, k);
                // the lit windows: warm light from the ceiling, people standing (head and shoulders) and sitting (heads)
                double kw = Math.max(k, 0.85);
                flat(x0 + 0.5, 1.7, x0 + 19.5, 2.9, sz, 0.03, 0xe2d6bc, kw);
                flat(x0 + 0.5, 2.9, x0 + 19.5, 4.0, sz, 0.03, 0xfff6e2, kw);
                int h = seed * 7 + car * 131 + s * 977;
                for (double px = x0 + 0.8; px < x0 + 19; px += 0.9 + ShibuyaScan.ih(h, (int) (px * 10), 1) * 1.4) {
                    double q = ShibuyaScan.ih(h, (int) (px * 10), 2);
                    if (q < 0.45) {
                        continue;
                    }
                    int skin = new int[] {0xe6c0a0, 0xd8ae88, 0xc89670, 0xf0d0b4}[(int) (q * 40) % 4];
                    int hair = new int[] {0x14110f, 0x1e1814, 0x2a2018, 0x3a2a1e, 0x5a4030}[(int) (q * 70) % 5];
                    flat(px, 1.7, px + 0.55, 2.2, sz, 0.05, skin, kw);
                    flat(px, 2.2, px + 0.55, 2.35, sz, 0.05, hair, kw);
                }
                int stand = 3 + (int) (ShibuyaScan.ih(h, 3, 3) * 6);
                for (int i = 0; i < stand; i++) {
                    double px = x0 + 1 + ShibuyaScan.ih(h, i, 4) * 17;
                    int coat = new int[] {0x2a2c36, 0x3a3440, 0x1e2430, 0x4a4a52, 0x5a4030, 0xe8e4dc, 0x2f4a6a, 0x6a2a30}[(int) (ShibuyaScan.ih(h, i, 5) * 8)];
                    flat(px - 0.45, 1.7, px + 0.45, 3.05, sz, 0.07, coat, kw);
                    flat(px - 0.28, 3.05, px + 0.28, 3.55, sz, 0.07, 0xe6c0a0, kw);
                    flat(px - 0.3, 3.55, px + 0.3, 3.72, sz, 0.07, 0x1e1814, kw);
                }
                // the hanging ads and the frames between the panes
                for (double ax = x0 + 1; ax < x0 + 19; ax += 1.6 + ShibuyaScan.ih(h, (int) (ax * 10), 6) * 1.3) {
                    int ad = new int[] {0xe8423c, 0x2f6db3, 0xf3c23a, 0x38a86a, 0xff6fb0, 0xf4f1ea}[(int) (ShibuyaScan.ih(h, (int) (ax * 10), 7) * 6)];
                    flat(ax, 3.62, ax + 0.7, 3.92, sz, 0.05, ad, kw);
                }
                for (double mx = x0 + 0.5; mx <= x0 + 19.6; mx += 4.75) {
                    if (s == 1 && (Math.abs(mx - x0 - 4) < 1.2 || Math.abs(mx - x0 - 10) < 1.2 || Math.abs(mx - x0 - 16) < 1.2)) {
                        continue;
                    }
                    flat(mx - 0.06, 1.7, mx + 0.06, 4.0, sz, 0.1, 0x505458, k);
                }
                // the doors: on the outer side shut; on the platform side two leaves sliding apart, the light inside
                for (int d : DOORS) {
                    double dx = x0 + d;
                    if (s == 0) {
                        flat(dx - 1, 0.8, dx + 1, 4.0, 0, 0.12, 0xb4b8bc, k);
                    } else {
                        flat(dx - 1, 0.8, dx + 1, 4.0, 3, 0.04, 0xfff0d4, Math.max(k, 0.9));
                        for (int sgn = -1; sgn <= 1; sgn += 2) {
                            double lb = dx + sgn * 0.5 + sgn * 0.95 * door;
                            flat(lb - 0.5, 0.8, lb + 0.5, 2.1, 3, 0.12, 0xb4b8bc, k);
                            flat(lb - 0.5, 3.6, lb + 0.5, 4.0, 3, 0.12, 0xb4b8bc, k);
                            flat(lb - 0.5, 2.1, lb - 0.38, 3.6, 3, 0.12, 0xb4b8bc, k);
                            flat(lb + 0.38, 2.1, lb + 0.5, 3.6, 3, 0.12, 0xb4b8bc, k);
                            flat(lb - 0.38, 2.1, lb + 0.38, 3.6, 3, 0.09, 0xfff0d4, Math.max(k, 0.9));
                        }
                    }
                }
            }
            if (car == 0) {
                // the cab: dark windscreen, the destination sign, the headlights
                box(-0.06, 2.2, 0.25, 0, 3.9, 2.75, 0x1a2228, 1);
                box(-0.07, 4.35, 0.6, 0, 4.85, 2.4, 0x2a1a0a, 1);
                box(-0.12, 4.45, 0.75, -0.09, 4.75, 2.25, 0xffae3a, 1.2);
                for (double z : new double[] {0.6, 2.4}) {
                    box(-0.08, 1.09, z - 0.25, -0.02, 1.41, z + 0.25, 0xfff6d0, 1.3);
                }
            }
            if (car == 2) {
                for (double z : new double[] {0.6, 2.4}) {
                    box(LEN + 0.02, 1.11, z - 0.2, LEN + 0.08, 1.39, z + 0.2, 0xff2a1a, 1.3);
                }
            }
        }
    }

    // the headlights' fan on the rails, the head and tail discs, the windows' light on the platform floor and on the
    // tunnel wall, the pools of light at the open doors, the brake sparks (small hot dots, no lines)
    private static void drawLights(VertexConsumer out, PoseStack.Pose last, Vec3 cam, int ox, double yb, float fade) {
        int d = dir(track);
        double c = ShibuyaScan.stC, ybb = yb + 0.02, platY = ShibuyaScan.stCity - 15 + 0.02;
        double fx = wx(0);
        double zl = track == 0 ? z0(0) : z0(1) - 3, zr = zl + 3;
        if (Math.abs(fx - ox) <= TUN + 1) {
            for (int s = 0; s < 4; s++) {
                double a0 = s * 7, a1 = (s + 1) * 7;
                float al0 = 0.5f * (1 - s / 4f) * fade, al1 = 0.5f * (1 - (s + 1) / 4f) * fade;
                double xa = fx + d * a0, xb = fx + d * a1;
                if (Math.abs(xb - ox) > TUN + 1) {
                    break;
                }
                ShibuyaAirFx.v(out, last, cam, xa, ybb, zl + 0.3, 1, 0.95f, 0.8f, al0);
                ShibuyaAirFx.v(out, last, cam, xb, ybb, zl - 0.2 * s, 1, 0.95f, 0.8f, al1);
                ShibuyaAirFx.v(out, last, cam, xb, ybb, zr + 0.2 * s, 1, 0.95f, 0.8f, al1);
                ShibuyaAirFx.v(out, last, cam, xa, ybb, zr - 0.3, 1, 0.95f, 0.8f, al0);
            }
            for (double lz : new double[] {0.6, 2.4}) {
                ShibuyaAirFx.disc(out, last, cam, wx(-0.15), yb + 1.25, wz(lz), 0.7, new float[] {1, 0.96f, 0.82f}, 0.9f * fade);
                ShibuyaAirFx.disc(out, last, cam, wx(-0.15), yb + 1.25, wz(lz), 2.6, new float[] {1, 0.9f, 0.7f}, 0.18f * fade);
            }
        }
        double tx = wx(LEN + 0.15);
        if (Math.abs(tx - ox) <= TUN) {
            for (double lz : new double[] {0.6, 2.4}) {
                ShibuyaAirFx.disc(out, last, cam, tx, yb + 1.25, wz(lz), 0.6, new float[] {1, 0.15f, 0.1f}, 0.8f * fade);
            }
        }
        double ze = edge(track);
        int sd = side(track);
        for (int car = 0; car < 3; car++) {
            double xa = wx(car * 21 + 0.5), xb = wx(car * 21 + 19.5);
            double x0 = Math.max(Math.min(xa, xb), ox - TUN), x1 = Math.min(Math.max(xa, xb), ox + TUN);
            if (x1 <= x0) {
                continue;
            }
            double zin = ze + sd * 2.6;
            double[][] segs = {{Math.max(x0, ox - PX1), Math.min(x1, ox + PX1), platY}, {x0, Math.min(x1, ox - PX1), ybb}, {Math.max(x0, ox + PX1), x1, ybb}};
            for (double[] sg : segs) {
                if (sg[1] <= sg[0]) {
                    continue;
                }
                float a = 0.2f * fade;
                ShibuyaAirFx.v(out, last, cam, sg[0], sg[2], ze, 1, 0.9f, 0.7f, a);
                ShibuyaAirFx.v(out, last, cam, sg[1], sg[2], ze, 1, 0.9f, 0.7f, a);
                ShibuyaAirFx.v(out, last, cam, sg[1], sg[2], zin, 1, 0.9f, 0.7f, 0);
                ShibuyaAirFx.v(out, last, cam, sg[0], sg[2], zin, 1, 0.9f, 0.7f, 0);
            }
            // the tunnel wall on the outer side
            double zw = track == 0 ? c - 7 + 0.03 : c + 7 - 0.03;
            float aw = 0.1f * fade;
            ShibuyaAirFx.v(out, last, cam, x0, yb + 1.6, zw, 1, 0.9f, 0.7f, aw);
            ShibuyaAirFx.v(out, last, cam, x1, yb + 1.6, zw, 1, 0.9f, 0.7f, aw);
            ShibuyaAirFx.v(out, last, cam, x1, yb + 4.6, zw, 1, 0.9f, 0.7f, aw);
            ShibuyaAirFx.v(out, last, cam, x0, yb + 4.6, zw, 1, 0.9f, 0.7f, aw);
            if (door > 0.05) {
                for (int dd : DOORS) {
                    double dx = wx(car * 21 + dd);
                    if (Math.abs(dx - ox) > PX1) {
                        continue;
                    }
                    float a = (float) (0.35 * door) * fade;
                    double y = platY + 0.005;
                    ShibuyaAirFx.v(out, last, cam, dx - 1.1, y, ze, 1, 0.93f, 0.78f, a);
                    ShibuyaAirFx.v(out, last, cam, dx + 1.1, y, ze, 1, 0.93f, 0.78f, a);
                    ShibuyaAirFx.v(out, last, cam, dx + 1.6, y, ze + sd * 3.2, 1, 0.93f, 0.78f, 0);
                    ShibuyaAirFx.v(out, last, cam, dx - 1.6, y, ze + sd * 3.2, 1, 0.93f, 0.78f, 0);
                }
            }
        }
        float[] hot = {1, 0.75f, 0.4f};
        for (Spark s : SPARKS) {
            float kk = (float) (1 - s.age / s.life) * fade;
            ShibuyaAirFx.disc(out, last, cam, s.x, s.y, s.z, 0.06, hot, kk);
        }
        if (!SPARKS.isEmpty()) {
            Spark s = SPARKS.get(0);
            ShibuyaAirFx.disc(out, last, cam, s.x, yb + 0.3, s.z, 1.2, new float[] {1, 0.6f, 0.25f}, 0.25f * fade);
        }
    }

    // The police car of ShibuyaAirFx (low black body, white doors and hood, glass cab, the light bar), {x, z, dirX, dirZ,
    // lights on} on the street at gy.
    static void renderCarBody(MultiBufferSource.BufferSource buffers, PoseStack.Pose last, Vec3 cam, double[] c, int gy) {
        if (c == null || gy == Integer.MIN_VALUE) {
            return;
        }
        VertexConsumer out = buffers.getBuffer(FxRenderTypes.SOLID);
        cur = out;
        curLast = last;
        curCam = cam;
        mode = 1;
        baseY = gy;
        oxW = c[0];
        ozW = c[1];
        cosR = c[2];
        sinR = c[3];
        double k = 1 - 0.55 * ShibuyaAirFx.night;
        box(-2.2, 0.28, -0.95, 2.2, 0.83, 0.95, 0x1a1a1c, k);
        box(-0.9, 0.83, -0.96, 1.3, 1.33, 0.96, 0xf2f2f0, k);
        box(1.3, 0.83, -0.96, 2.2, 1.33, 0.96, 0x1a1a1c, k);
        box(-2.1, 0.83, -0.96, -0.9, 1.33, 0.96, 0x1a1a1c, k);
        box(-1.2, 1.33, -0.875, 1.0, 1.85, 0.875, 0x2a3a48, k);
        box(-1.2, 1.85, -0.9, 1.0, 1.91, 0.9, 0xf2f2f0, k);
        boolean onL = c[4] > 0.5;
        box(-0.3, 1.91, -0.77, 0.1, 2.07, -0.07, onL ? 0xff3030 : 0x401010, 1.2);
        box(-0.3, 1.91, 0.07, 0.1, 2.07, 0.77, onL ? 0x101840 : 0x3050ff, 1.2);
        box(2.2, 0.65, -0.85, 2.26, 0.85, -0.35, 0xfff6d0, 1.3);
        box(2.2, 0.65, 0.35, 2.26, 0.85, 0.85, 0xfff6d0, 1.3);
        for (double[] w : new double[][] {{1.4, 0.95}, {1.4, -0.95}, {-1.4, 0.95}, {-1.4, -0.95}}) {
            box(w[0] - 0.35, 0, w[1] - 0.15, w[0] + 0.35, 0.65, w[1] + 0.15, 0x111111, k);
        }
        buffers.endBatch(FxRenderTypes.SOLID);
    }
}
