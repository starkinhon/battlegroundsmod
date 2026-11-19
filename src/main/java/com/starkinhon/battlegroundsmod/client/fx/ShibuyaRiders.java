package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.starkinhon.battlegroundsmod.ModEntities;
import com.starkinhon.battlegroundsmod.npc.Pedestre;
import com.starkinhon.battlegroundsmod.npc.PedestreLook;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

// The metro's passengers (Stark 2026-11-19: "os pedestres não aparecem no metrô"; the approved preview's metro.js people,
// the source of truth). When the train's doors open a few get off at each door, walk to the platform stairs and up, cross
// the concourse through the gates and climb the entrance stairs to the street; the ones waiting board after them (the
// front row first, the two columns alternating). Those for the next train come down from the street the same way, and
// wait at its doors in the Japanese queue: two columns, one on each side of the door, the middle free for who gets off.
// They are the pedestrians' bodies and looks (Pedestre + PedestreLook, PedestreRenderer draws them) kept by the client only,
// like the train (every player sees people, not the same people; nothing to send). Nobody walks through anybody (the
// preview's rules: slow down behind the one in front, step aside from the one coming or standing, then a minimum distance
// of 0.62 pushing both, the one standing weighs more), nor through a player. They appear and vanish out of sight only:
// on the street past the entrance, or inside the train behind the doors. The ones near the stairs' doors (2.5 to 14 from
// the middle) don't use those doors (the stairs are right there), like in the preview.
final class ShibuyaRiders {
    // (Stark 2026-11-19: "coloca bastante gente dentro do metrô": 96 bodies, fuller queues and more getting off)
    private static final int POOL = 96, LOOKS = 160;
    private static final double RAD = 0.62, DT = 0.05;
    private static final int AFTER_FREE = 0, AFTER_WAIT = 1;
    private static final Random RANDOM = new Random();

    private static final class Rider {
        Pedestre body;
        boolean busy, shown, street;
        double x, y, z, yaw, speed, delay, boardAt, stuck, moving;
        final List<double[]> path = new ArrayList<>();
        int after, waitFor = -1, slotTrack = -1, slot, fromStair, streetLeft;
        double door;
        double[] spawn; // the street spot it appears at (out of sight)
    }

    private static final List<Rider> RIDERS = new ArrayList<>();
    private static ClientLevel poolLevel;
    private static boolean active, opened;
    private static int lookBase;

    private ShibuyaRiders() {
    }

    static void reset() {
        active = false;
        opened = false;
        for (Rider r : RIDERS) {
            free(r);
        }
    }

    // ---------------------------------------------------------------- the station's places
    // (the station is ShibuyaGen.metro's: the platform stairs at x 4..11 out of the middle on z c-1..c, the concourse
    // stairs' railings at z c-2 and c+1, the gates at x 16, the entrances at x 22..29 on z c+8..c+10 east and c-11..c-9
    // west; the west side is not the mirror of the east one block by block, so each side has its own points)

    private static double X(int sg, double lx) {
        return ShibuyaScan.stOx + (sg > 0 ? lx : 1 - lx);
    }

    private static double[] p(double lx, double lz) {
        return new double[] {ShibuyaScan.stOx + lx, ShibuyaScan.stC + lz};
    }

    // from the street down to the top of the platform stairs (the lane c - 0.45)
    private static List<double[]> concourseDown(int sg) {
        List<double[]> out = new ArrayList<>();
        if (sg > 0) {
            for (double[] q : new double[][] {{29.5, 8.9}, {21.5, 8.9}, {18.6, 8.9}, {18.6, 5.5}, {13.5, 5.5}, {4.0, 2.5}, {2.0, 2.5}, {2.0, -0.45}, {4.6, -0.45}}) {
                out.add(p(q[0], q[1]));
            }
        } else {
            for (double[] q : new double[][] {{-28.5, -8.9}, {-20.6, -8.9}, {-20.6, -5.5}, {-8.5, -5.5}, {-3.0, -2.5}, {-1.0, -2.5}, {-1.0, -0.45}, {-3.6, -0.45}}) {
                out.add(p(q[0], q[1]));
            }
        }
        return out;
    }

    // from the top of the platform stairs (the lane c + 0.45) up to the street
    private static List<double[]> concourseUp(int sg) {
        List<double[]> out = new ArrayList<>();
        if (sg > 0) {
            for (double[] q : new double[][] {{3.2, 0.45}, {3.2, 3.5}, {16.5, 3.5}, {19.6, 3.5}, {19.6, 10.1}, {21.5, 10.1}, {29.5, 10.1}, {31.5, 10.1}}) {
                out.add(p(q[0], q[1]));
            }
        } else {
            for (double[] q : new double[][] {{-2.2, 0.45}, {-2.2, -3.5}, {-15.5, -3.5}, {-19.6, -3.5}, {-19.6, -10.1}, {-28.5, -10.1}, {-30.5, -10.1}}) {
                out.add(p(q[0], q[1]));
            }
        }
        return out;
    }

    // the street spot where the ones going down appear (on the sidewalk past the entrance, walking to it)
    private static double[] streetStart(int sg, double extra) {
        return sg > 0 ? p(31.5 + extra, 8.9) : p(-30.5 - extra, -8.9);
    }

    private static double lane(int k) {
        // (between the stairs and the queues; the wider island of 2026-11-19 leaves room for a third row in the queue)
        double o = ShibuyaScan.stIsland >= 5 ? 1.6 : 1.5;
        return ShibuyaMetro.sideOf(k) > 0 ? ShibuyaScan.stC - o : ShibuyaScan.stC + o;
    }

    private static double platY() {
        return ShibuyaScan.stCity - 15;
    }

    private static double concY() {
        return ShibuyaScan.stCity - 7;
    }

    private static boolean usable(double xd) {
        double a = Math.abs(xd - ShibuyaScan.stOx);
        return !(a > 2.5 && a < 14);
    }

    // ---------------------------------------------------------------- the tick (from ShibuyaMetro.tick)
    static void tick(ClientLevel level, Player player) {
        if (!ShibuyaMetro.on || !ShibuyaScan.station) {
            if (active) {
                reset();
            }
            return;
        }
        if (poolLevel != level || RIDERS.isEmpty()) {
            RIDERS.clear();
            poolLevel = level;
            lookBase = RANDOM.nextInt(1 << 20);
            for (int i = 0; i < POOL; i++) {
                Rider r = new Rider();
                r.body = new Pedestre(ModEntities.PEDESTRE.get(), level);
                RIDERS.add(r);
            }
        }
        int ph = ShibuyaMetro.phase, k = ShibuyaMetro.track;
        double pt = ShibuyaMetro.pt;
        if (!active) {
            active = true;
            // just came near: the ones for the next train are already at its doors (the platform can't be seen from
            // where the player comes); a train standing with its doors open now lets nobody off
            opened = ph == 1;
            callWaiters(ph == 0 ? k : 1 - k, true);
        }
        if (ph == 1 && pt > 0.8 && !opened) {
            opened = true;
            doorsOpen(k);
            // the ones for the next train (the other track) come down now: from the street it takes a while
            callWaiters(1 - k, false);
        }
        if (ph != 1) {
            opened = false;
        }
        // boarding, after the ones getting off
        for (Rider r : RIDERS) {
            if (r.busy && r.boardAt > 0 && r.waitFor == k && ph == 1 && pt > r.boardAt) {
                board(r, k);
            }
        }
        step(level);
    }

    private static Rider take() {
        for (Rider r : RIDERS) {
            if (!r.busy) {
                Random rr = new Random(lookBase + RANDOM.nextInt(LOOKS) * 7919L);
                int look = PedestreLook.make(PedestreLook.pickType(rr, ShibuyaAirFx.night > 0.5f), rr.nextInt(1 << 24));
                r.body.clientLook(look);
                r.body.clientPose(Pedestre.POSE_NONE);
                r.busy = true;
                r.shown = false;
                r.street = false;
                r.path.clear();
                r.after = AFTER_FREE;
                r.waitFor = r.slotTrack = -1;
                r.boardAt = r.stuck = r.delay = r.moving = 0;
                r.fromStair = 0;
                r.streetLeft = 0;
                r.spawn = null;
                r.speed = PedestreLook.of(look).speed;
                if (r.speed <= 0.2) {
                    r.speed = 1.3;
                }
                return r;
            }
        }
        return null;
    }

    private static void free(Rider r) {
        r.busy = false;
        r.shown = false;
        r.path.clear();
        r.waitFor = r.slotTrack = -1;
        r.boardAt = 0;
        r.spawn = null;
    }

    private static int slotsAt(int k, double xd) {
        int n = 0;
        for (Rider r : RIDERS) {
            if (r.busy && r.slotTrack == k && Math.abs(r.door - xd) < 0.01) {
                n++;
            }
        }
        return n;
    }

    // the ones who'll take the next train of track k (3 to 6 at each door: three rows of the two columns on the wide
    // island; 2 to 4 on an old chunk's narrow one, where a third row would stand in the walking lane)
    private static void callWaiters(int k, boolean already) {
        for (int i = 0; i < ShibuyaMetro.DOOR_COUNT; i++) {
            double xd = ShibuyaMetro.doorX(k, i);
            if (!usable(xd)) {
                continue;
            }
            boolean wide = ShibuyaScan.stIsland >= 5;
            int cap = wide ? 6 : 4;
            int m = (wide ? 3 : 2) + (RANDOM.nextDouble() < 0.6 ? 1 : 0) + (RANDOM.nextDouble() < 0.45 ? 1 : 0)
                    + (wide && RANDOM.nextDouble() < 0.3 ? 1 : 0);
            for (int j = 0; j < m && slotsAt(k, xd) < cap; j++) {
                spawnWaiter(k, xd, already);
            }
        }
    }

    private static void spawnWaiter(int k, double xd, boolean already) {
        Rider r = take();
        if (r == null) {
            return;
        }
        int sg = xd >= ShibuyaScan.stOx ? 1 : -1, side = ShibuyaMetro.sideOf(k);
        double edge = ShibuyaMetro.edgeZ(k);
        int slot = slotsAt(k, xd), col = slot % 2 == 1 ? 1 : -1, row = slot / 2;
        double wx = xd + col * 0.78, wz = edge + side * (1.25 + row * 0.68);
        r.slotTrack = k;
        r.door = xd;
        r.slot = slot;
        if (already) {
            r.x = wx;
            r.z = wz;
            r.y = platY();
            r.shown = true;
            r.body.setPos(r.x, r.y, r.z);
            arrive(r, k);
            return;
        }
        // from the street, one after the other at each entrance (not all at once)
        int queued = 0;
        for (Rider q : RIDERS) {
            if (q.busy && !q.shown && q.fromStair == sg) {
                queued++;
            }
        }
        r.fromStair = sg;
        r.delay = queued * 0.9 + RANDOM.nextDouble() * 0.6;
        r.spawn = streetStart(sg, 6);
        r.path.add(streetStart(sg, 0));
        r.path.addAll(concourseDown(sg));
        double c = ShibuyaScan.stC, L = lane(k);
        r.path.add(new double[] {X(sg, 12.6), c - 0.45});
        r.path.add(new double[] {X(sg, 13.3), c - 0.45});
        r.path.add(new double[] {X(sg, 13.3), L});
        r.path.add(new double[] {wx, L});
        r.path.add(new double[] {wx, wz});
        r.after = AFTER_WAIT;
        r.waitFor = -1;
    }

    // at its place in the queue: faces the track, maybe the phone; if its train is there with the doors open it gets in
    private static void arrive(Rider r, int k) {
        r.waitFor = k;
        r.yaw = ShibuyaMetro.sideOf(k) > 0 ? 180 : 0;
        PedestreLook S = PedestreLook.of(r.body.look());
        double q = RANDOM.nextDouble();
        r.body.clientPose(q < 0.45 && S.phone() ? Pedestre.POSE_PHONE : q > 0.7 ? Pedestre.POSE_LOOK : Pedestre.POSE_NONE);
        if (ShibuyaMetro.phase == 1 && ShibuyaMetro.track == k && ShibuyaMetro.pt > 0.8 && ShibuyaMetro.pt < 9.0) {
            r.boardAt = ShibuyaMetro.pt + 0.4 + RANDOM.nextDouble() * 0.4;
        }
    }

    private static void doorsOpen(int k) {
        int side = ShibuyaMetro.sideOf(k);
        double edge = ShibuyaMetro.edgeZ(k), c = ShibuyaScan.stC, L = lane(k);
        for (int i = 0; i < ShibuyaMetro.DOOR_COUNT; i++) {
            double xd = ShibuyaMetro.doorX(k, i);
            if (!usable(xd)) {
                continue;
            }
            int sg = xd >= ShibuyaScan.stOx ? 1 : -1;
            int m = 1 + (RANDOM.nextDouble() < 0.7 ? 1 : 0) + (RANDOM.nextDouble() < 0.4 ? 1 : 0) + (RANDOM.nextDouble() < 0.15 ? 1 : 0);
            for (int j = 0; j < m; j++) {
                Rider r = take();
                if (r == null) {
                    break;
                }
                r.x = xd + (RANDOM.nextDouble() - 0.5) * 0.8;
                r.z = edge - side * 1.0;
                r.y = platY();
                r.body.setPos(r.x, r.y, r.z);
                r.delay = 1.1 + j * 0.6 + RANDOM.nextDouble() * 0.3;
                r.speed = r.speed * (1.0 + RANDOM.nextDouble() * 0.15);
                r.path.add(new double[] {r.x, edge + side * 1.3});
                r.path.add(new double[] {r.x + sg * 2, L});
                r.path.add(new double[] {X(sg, 14.6), L});
                r.path.add(new double[] {X(sg, 14.6), c + 0.45});
                r.path.add(new double[] {X(sg, 12.6), c + 0.45});
                r.path.add(new double[] {X(sg, 4.6), c + 0.45});
                r.path.addAll(concourseUp(sg));
                double[] end = sg > 0 ? p(51.5, 10.1) : p(-50.5, -10.1);
                r.path.add(end);
                r.streetLeft = 1; // past the entrance's top: on the street
                r.after = AFTER_FREE;
                r.yaw = side > 0 ? 0 : 180;
            }
        }
        // the ones waiting get in after them: the front row first, the two columns alternating
        for (Rider r : RIDERS) {
            if (r.busy && r.waitFor == k && r.path.isEmpty() && r.shown) {
                r.boardAt = 3.9 + (r.slot / 2) * 0.75 + (r.slot % 2) * 0.35 + RANDOM.nextDouble() * 0.15;
            }
        }
    }

    private static void board(Rider r, int k) {
        int side = ShibuyaMetro.sideOf(k);
        double edge = ShibuyaMetro.edgeZ(k);
        r.boardAt = 0;
        r.waitFor = -1;
        r.slotTrack = -1;
        r.body.clientPose(Pedestre.POSE_NONE);
        r.path.clear();
        r.path.add(new double[] {r.door + (r.x - r.door) * 0.3, edge + side * 0.4});
        r.path.add(new double[] {r.door, edge - side * 1.2});
        r.after = AFTER_FREE;
    }

    // ---------------------------------------------------------------- the walk
    private static void step(ClientLevel level) {
        Minecraft mc = Minecraft.getInstance();
        List<Rider> live = new ArrayList<>();
        for (Rider r : RIDERS) {
            if (r.busy && r.shown) {
                live.add(r);
            }
        }
        List<Player> players = new ArrayList<>();
        for (Player pl : level.players()) {
            if (!pl.isSpectator() && Math.abs(pl.getZ() - ShibuyaScan.stC) < 40 && Math.abs(pl.getX() - ShibuyaScan.stOx) < 80) {
                players.add(pl);
            }
        }
        for (Rider r : RIDERS) {
            if (!r.busy) {
                continue;
            }
            Pedestre b = r.body;
            if (!r.shown) {
                r.delay -= DT;
                if (r.delay > 0) {
                    continue;
                }
                if (r.spawn != null) {
                    // appears on the street only out of the player's sight; else a bit farther along the sidewalk
                    double sy = ShibuyaScan.stCity + 1;
                    double[] s = r.spawn;
                    if (seen(mc, level, s[0], sy, s[1])) {
                        int sg = r.fromStair;
                        s = streetStart(sg, 14);
                        if (seen(mc, level, s[0], sy, s[1])) {
                            r.delay = 0.6;
                            continue;
                        }
                    }
                    r.x = s[0];
                    r.z = s[1];
                    r.y = sy;
                    r.spawn = null;
                }
                // only if the place is free (else a little later)
                boolean taken = false;
                for (Rider q : live) {
                    if (q != r && Math.hypot(q.x - r.x, q.z - r.z) < RAD + 0.1 && Math.abs(q.y - r.y) < 1.5) {
                        taken = true;
                        break;
                    }
                }
                if (taken) {
                    r.delay = 0.15;
                    continue;
                }
                r.shown = true;
                r.fromStair = 0;
                b.setPos(r.x, r.y, r.z);
                b.xo = b.xOld = r.x;
                b.yo = b.yOld = r.y;
                b.zo = b.zOld = r.z;
                live.add(r);
            }
            if (!r.path.isEmpty()) {
                double[] t = r.path.get(0);
                double dx = t[0] - r.x, dz = t[1] - r.z, d = Math.hypot(dx, dz);
                double ux = d > 1e-6 ? dx / d : 0, uz = d > 1e-6 ? dz / d : 0;
                double kk = 1, side = 0;
                if (r.stuck < 1.4) {
                    for (Rider q : live) {
                        if (q == r || Math.abs(q.y - r.y) > 1.5) {
                            continue;
                        }
                        double rx = q.x - r.x, rz = q.z - r.z, ahead = rx * ux + rz * uz, lat = rx * uz - rz * ux;
                        if (ahead <= 0 || ahead > 1.1 || Math.abs(lat) > 0.58) {
                            continue;
                        }
                        boolean qm = !q.path.isEmpty();
                        double qux = 0, quz = 0;
                        if (qm) {
                            double[] qt = q.path.get(0);
                            double qd = Math.max(1e-6, Math.hypot(qt[0] - q.x, qt[1] - q.z));
                            qux = (qt[0] - q.x) / qd;
                            quz = (qt[1] - q.z) / qd;
                        }
                        if (qm && qux * ux + quz * uz > 0.3) {
                            kk = Math.min(kk, Math.max(0, (ahead - RAD) / 0.45)); // same way: goes behind
                        } else {
                            side += (lat >= 0 ? -1 : 1) * (1.1 - ahead); // coming or standing: steps aside
                        }
                    }
                }
                // a player in the way: steps around (and never walks into one)
                for (Player pl : players) {
                    if (Math.abs(pl.getY() - r.y) > 1.8) {
                        continue;
                    }
                    double rx = pl.getX() - r.x, rz = pl.getZ() - r.z, ahead = rx * ux + rz * uz, lat = rx * uz - rz * ux;
                    if (ahead <= 0 || ahead > 1.4 || Math.abs(lat) > 0.75) {
                        continue;
                    }
                    side += (lat >= 0 ? -1 : 1) * (1.5 - ahead);
                    if (ahead < 0.75 && Math.abs(lat) < 0.5) {
                        kk = Math.min(kk, 0.2);
                    }
                }
                r.stuck = kk < 0.15 ? r.stuck + DT : Math.max(0, r.stuck - DT * 2);
                double s = r.speed * DT * Math.max(kk, 0.12);
                if (d <= s) {
                    r.x = t[0];
                    r.z = t[1];
                    r.path.remove(0);
                    if (r.streetLeft > 0 && r.path.size() <= r.streetLeft) {
                        r.street = true;
                    }
                    if (r.path.isEmpty()) {
                        done(r);
                        if (!r.busy) {
                            continue;
                        }
                    }
                } else {
                    double sd = Mth.clamp(side, -1, 1) * r.speed * 0.8 * DT;
                    r.x += ux * s + uz * sd;
                    r.z += uz * s - ux * sd;
                    double want = Math.toDegrees(Math.atan2(-dx, dz));
                    r.yaw += Mth.wrapDegrees(want - r.yaw) * 0.5;
                }
                r.moving = r.path.isEmpty() ? 0 : kk > 0.15 ? 1 : 0;
            } else {
                r.moving = 0;
            }
            // out on the street: gone as soon as nobody sees it
            if (r.street && !seen(mc, level, r.x, r.y, r.z)) {
                free(r);
                continue;
            }
            r.y = ground(level, r.x, r.z, r.y);
        }
        separate(live, players);
        for (Rider r : live) {
            if (!r.busy) {
                continue;
            }
            Pedestre b = r.body;
            double px = b.getX(), py = b.getY(), pz = b.getZ();
            b.xo = b.xOld = px;
            b.yo = b.yOld = py;
            b.zo = b.zOld = pz;
            b.yRotO = b.getYRot();
            b.yBodyRotO = b.yBodyRot;
            b.yHeadRotO = b.yHeadRot;
            b.setPos(r.x, r.y, r.z);
            float yaw = (float) r.yaw;
            b.setYRot(yaw);
            b.yBodyRot = yaw;
            b.yHeadRot = yaw;
            double moved = Math.hypot(r.x - px, r.z - pz);
            b.walkAnimation.update((float) Math.min(moved * 4, 1), 0.4f);
            b.tickCount++;
        }
    }

    private static void done(Rider r) {
        if (r.after == AFTER_WAIT) {
            arrive(r, r.slotTrack);
        } else {
            free(r);
        }
    }

    // the minimum distance between two (the one standing weighs more); a player pushes only the rider. The push never
    // takes anybody off the platform's island nor off the stairs.
    private static void separate(List<Rider> live, List<Player> players) {
        double c = ShibuyaScan.stC;
        for (int it = 0; it < 2; it++) {
            for (int i = 0; i < live.size(); i++) {
                for (int j = i + 1; j < live.size(); j++) {
                    Rider a = live.get(i), b = live.get(j);
                    if (!a.busy || !b.busy || Math.abs(a.y - b.y) > 1.5) {
                        continue;
                    }
                    double rx = b.x - a.x, rz = b.z - a.z, d = Math.hypot(rx, rz);
                    if (d >= RAD) {
                        continue;
                    }
                    if (d < 1e-4) {
                        rx = Math.cos(i * 2.4);
                        rz = Math.sin(i * 2.4);
                    } else {
                        rx /= d;
                        rz /= d;
                    }
                    double wa = a.path.isEmpty() ? 0.2 : 1, wb = b.path.isEmpty() ? 0.2 : 1, over = RAD - Math.max(d, 1e-4), sum = wa + wb;
                    push(a, -rx * over * wa / sum, -rz * over * wa / sum, c);
                    push(b, rx * over * wb / sum, rz * over * wb / sum, c);
                }
            }
            for (Rider a : live) {
                for (Player pl : players) {
                    if (!a.busy || Math.abs(pl.getY() - a.y) > 1.8) {
                        continue;
                    }
                    double rx = a.x - pl.getX(), rz = a.z - pl.getZ(), d = Math.hypot(rx, rz), min = 0.7;
                    if (d >= min || d < 1e-4) {
                        continue;
                    }
                    push(a, rx / d * (min - d), rz / d * (min - d), c);
                }
            }
        }
    }

    private static void push(Rider p, double mx, double mz, double c) {
        double half = ShibuyaScan.stIsland - 0.45;
        boolean island = p.y < concY() - 2 && Math.abs(p.z - c) <= half;
        boolean stair = Math.abs(p.x - ShibuyaScan.stOx - 0.5) <= 12 && p.z >= c - 1.0 && p.z <= c + 1.0 && p.y < concY() - 0.5;
        p.x += mx;
        p.z += mz;
        if (island) {
            p.z = Mth.clamp(p.z, c - half, c + half);
        }
        if (stair) {
            p.z = Mth.clamp(p.z, c - 0.7, c + 0.7);
        }
    }

    // the floor under (x, z) near y: the top of the first block with a body below the knees (stairs one step at a
    // time, a little smoothed); inside the train (past the platform's edge, down at the platform) its floor
    private static double ground(ClientLevel level, double x, double z, double y) {
        double c = ShibuyaScan.stC;
        if (Math.abs(z - c) > ShibuyaScan.stIsland && y < concY() - 2) {
            return platY();
        }
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int bx = Mth.floor(x), bz = Mth.floor(z);
        double top = Double.NaN;
        for (int by = Mth.floor(y + 1.05); by >= Mth.floor(y) - 3; by--) {
            m.set(bx, by, bz);
            BlockState st = level.getBlockState(m);
            VoxelShape sh = st.getCollisionShape(level, m);
            if (sh.isEmpty()) {
                continue;
            }
            double t = by + sh.max(Direction.Axis.Y);
            if (t <= y + 1.05) {
                top = t;
                break;
            }
        }
        if (top != top) {
            return y;
        }
        // up a step quickly, down a little slower (no teleport between the steps)
        return top > y ? Math.min(top, y + 0.34) : Math.max(top, y - 0.45);
    }

    // seen by the camera: in front of it (a cone of about 60 degrees), not too far, with nothing solid in between
    private static boolean seen(Minecraft mc, ClientLevel level, double x, double y, double z) {
        if (mc.player == null) {
            return false;
        }
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        double dx = x - cam.x, dy = y + 1.4 - cam.y, dz = z - cam.z, d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d > 72) {
            return false;
        }
        if (d < 4) {
            return true;
        }
        Vec3 look = mc.player.getViewVector(1f);
        if ((dx * look.x + dy * look.y + dz * look.z) / d < 0.45) {
            return false;
        }
        BlockHitResult hit = level.clip(new ClipContext(cam, new Vec3(x, y + 1.4, z), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        return hit.getType() == HitResult.Type.MISS;
    }

    // ---------------------------------------------------------------- the drawing (AFTER_ENTITIES)
    static void render(RenderLevelStageEvent event, float partial) {
        if (!active) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        org.joml.Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.set(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(event.getProjectionMatrix(), VertexSorting.DISTANCE_TO_ORIGIN);
        try {
            PoseStack ps = new PoseStack();
            for (Rider r : RIDERS) {
                if (!r.busy || !r.shown) {
                    continue;
                }
                Pedestre b = r.body;
                double x = Mth.lerp(partial, b.xo, b.getX()), y = Mth.lerp(partial, b.yo, b.getY()), z = Mth.lerp(partial, b.zo, b.getZ());
                if ((x - cam.x) * (x - cam.x) + (y - cam.y) * (y - cam.y) + (z - cam.z) * (z - cam.z) > 96 * 96) {
                    continue;
                }
                int light = dispatcher.getPackedLightCoords(b, partial);
                dispatcher.render(b, x - cam.x, y - cam.y, z - cam.z, b.getYRot(), partial, ps, buffers, light);
            }
            buffers.endBatch();
        } finally {
            RenderSystem.restoreProjectionMatrix();
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }
}
