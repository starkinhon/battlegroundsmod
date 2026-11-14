package com.starkinhon.battlegroundsmod.npc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.starkinhon.battlegroundsmod.ModEntities;
import com.starkinhon.battlegroundsmod.world.ModWorldgen;
import com.starkinhon.battlegroundsmod.world.ShibuyaFeature;
import com.starkinhon.battlegroundsmod.world.ShibuyaGen;
import com.starkinhon.battlegroundsmod.world.ShibuyaGen.Street;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

// Shibuya's crowd: the approved preview's walking (forja/jujutsu-pedestres/peds.js) on the world's real streets.
// - Each one walks a sidewalk line of a street (keeping left, Japan), at a lane 0..2.4 blocks in from the curb's next
//   row; at a corner it may turn; at the big scramble crossings it waits for the light with the others (13 s green,
//   the last 4 blinking, 21 s red, the same clock for the whole world) and crosses straight or on the diagonal.
// - Groups (school friends, couples, tourists) keep their places by the leader.
// - Nobody inside anybody: the preview's spread on a 1-block grid every tick, the people standing (phone, waiting)
//   weigh more and are walked around; the players are walls they step away from (and never get pushed).
// - Only on the original ground (intact): the block under the feet is the one the city put there (a crater, rubble,
//   a hole or a broken sidewalk is not), feet and head free. A walk that meets broken ground or a post turns back.
// - A fight (any blow or explosion) sends everyone within 34 blocks running away for 6-10 s.
// - Around each player in the Shibuya biome: ~30 by day, ~20 at night, coming in 24-44 blocks away out of sight;
//   taken away 64 blocks from every player (they are never saved).
public final class PedestreCrowd {
    private static final double DT = 0.05;
    private static final int CAP_DAY = 30, CAP_NIGHT = 20;
    private static final double NEAR = 48, GONE = 64, FIGHT = 34;
    private static final double MIN = 0.66;
    // The lanes: the body (0.56 wide for the checks) stays on the sidewalk rows 1+, never on the curb (railings, posts
    // and trees stand there) nor touching a building.
    private static final double LAT_MIN = -0.2, HALF = 0.28;
    private static final int GREEN = 13, RED = 21;
    private static final Map<ServerLevel, List<Pedestre>> CROWDS = new HashMap<>();
    private static final Map<ServerLevel, List<double[]>> NOISES = new HashMap<>();

    private PedestreCrowd() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent e) -> {
            CROWDS.clear();
            NOISES.clear();
        });
        NeoForge.EVENT_BUS.addListener(PedestreCrowd::onLevelTick);
        NeoForge.EVENT_BUS.addListener(PedestreCrowd::onJoin);
        NeoForge.EVENT_BUS.addListener(PedestreCrowd::onDamage);
        NeoForge.EVENT_BUS.addListener(PedestreCrowd::onExplosion);
    }

    private static void onJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof Pedestre p && event.getLevel() instanceof ServerLevel level) {
            List<Pedestre> list = CROWDS.computeIfAbsent(level, k -> new ArrayList<>());
            if (!list.contains(p)) {
                list.add(p);
            }
        }
    }

    // ---------------------------------------------------------------- fights
    private static void onDamage(LivingDamageEvent.Post event) {
        if (event.getEntity().level() instanceof ServerLevel level && !(event.getEntity() instanceof Pedestre)
                && event.getSource().getEntity() != null) {
            noise(level, event.getEntity().getX(), event.getEntity().getZ());
        }
    }

    private static void onExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel level) {
            Vec3 c = event.getExplosion().center();
            noise(level, c.x, c.z);
        }
    }

    static void noise(ServerLevel level, double x, double z) {
        if (!CROWDS.containsKey(level)) {
            return;
        }
        List<double[]> list = NOISES.computeIfAbsent(level, k -> new ArrayList<>());
        for (double[] n : list) {
            if (Math.abs(n[0] - x) < 4 && Math.abs(n[1] - z) < 4) {
                return; // the same fight this tick
            }
        }
        list.add(new double[] {x, z});
    }

    // ---------------------------------------------------------------- the world's streets
    static int seed(ServerLevel level) {
        return ShibuyaFeature.seed(level.getSeed());
    }

    static int cityY(ServerLevel level) {
        return ShibuyaFeature.cityY(level.getChunkSource().getGenerator());
    }

    static int lineB(Street s, int out) {
        return out > 0 ? s.c() + s.r1() + 2 : s.c() + s.r0() - 2;
    }

    static double latMax(Street s, int out) {
        int rows = (out > 0 ? s.s1() - s.r1() : s.r0() - s.s0()) - 1;
        return Math.min(rows, 3) - 0.8;
    }

    // the left side of someone walking dir along the axis (Japan walks on the left)
    static double laneFor(char axis, Street s, int out, int dir, Random r) {
        int leftAcross = axis == 'x' ? -dir : dir;
        boolean towardOut = leftAcross * out > 0;
        double max = latMax(s, out);
        double mid = (max - 0.35) / 2;
        return Mth.clamp(mid + (towardOut ? 0.55 : -0.55) + (r.nextDouble() - 0.5) * 0.5, LAT_MIN, max);
    }

    static double across(Pedestre p) {
        return lineB(p.street, p.out) + 0.5 + p.out * p.lat;
    }

    static void walkPos(Pedestre p) {
        double c = across(p);
        if (p.axis == 'x') {
            p.px = p.a;
            p.pz = c;
        } else {
            p.px = c;
            p.pz = p.a;
        }
    }

    // Puts the person on the sidewalk line under (x, z), if there is one (rows 1+ of a street's sidewalk).
    static boolean adopt(Pedestre p, double x, double z, int seed) {
        for (char axis : p.rand.nextBoolean() ? new char[] {'x', 'z'} : new char[] {'z', 'x'}) {
            double acr = axis == 'x' ? z : x, along = axis == 'x' ? x : z;
            ShibuyaGen.At at = ShibuyaGen.streetAt(axis, Mth.floor(acr), seed);
            if (at == null) {
                continue;
            }
            Street s = at.s();
            int d = at.d();
            if (d >= s.r0() - 1 && d <= s.r1() + 1) {
                continue; // road or curb
            }
            int out = d > s.r1() ? 1 : -1;
            double lat = (acr - lineB(s, out) - 0.5) * out;
            double max = latMax(s, out);
            if (lat < -0.6 || lat > max + 0.6) {
                continue;
            }
            p.axis = axis;
            p.street = s;
            p.out = out;
            p.a = along;
            p.lat = Mth.clamp(lat, LAT_MIN, max);
            if (p.state != Pedestre.WALK) {
                p.dir = p.rand.nextBoolean() ? 1 : -1;
            }
            p.latT = laneFor(axis, s, out, p.dir, p.rand);
            p.state = Pedestre.WALK;
            return true;
        }
        return false;
    }

    // The original city ground: the block the city put at the street's level (any bueiro the city adds is ground too),
    // feet and head free.
    static boolean intact(ServerLevel level, int x, int z, int seed, int cityY) {
        BlockPos g = new BlockPos(x, cityY, z);
        if (!level.isLoaded(g)) {
            return false;
        }
        int t = ShibuyaGen.groundAt(x, z, seed);
        if (t < 0) {
            return false;
        }
        BlockState ground = level.getBlockState(g);
        if (ground != ShibuyaFeature.block(t) && !BuiltInRegistries.BLOCK.getKey(ground.getBlock()).getPath().contains("bueiro")) {
            return false;
        }
        return free(level, g.above()) && free(level, g.above(2));
    }

    private static boolean free(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return s.getCollisionShape(level, pos).isEmpty() && s.getFluidState().isEmpty();
    }

    // Can the body (0.6 wide) stand at (x, z)?
    static boolean room(ServerLevel level, double x, double z, int seed, int cityY) {
        int x0 = Mth.floor(x - HALF), x1 = Mth.floor(x + HALF), z0 = Mth.floor(z - HALF), z1 = Mth.floor(z + HALF);
        for (int bx = x0; bx <= x1; bx++) {
            for (int bz = z0; bz <= z1; bz++) {
                if (!intact(level, bx, bz, seed, cityY)) {
                    return false;
                }
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- the light
    private static double signalLeft(ServerLevel level, boolean[] green, boolean[] blink) {
        double t = (level.getGameTime() % ((GREEN + RED) * 20L)) / 20.0;
        green[0] = t < GREEN;
        blink[0] = green[0] && GREEN - t < 4;
        return green[0] ? GREEN - t : GREEN + RED - t;
    }

    // ---------------------------------------------------------------- the tick
    private static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (level.getGameTime() % 10 == 0) {
            spawnAround(level);
        }
        List<Pedestre> all = CROWDS.get(level);
        if (all == null) {
            return;
        }
        all.removeIf(p -> p.isRemoved());
        if (all.isEmpty()) {
            CROWDS.remove(level);
            NOISES.remove(level);
            return;
        }
        int seed = seed(level), cityY = cityY(level);
        List<Pedestre> peds = new ArrayList<>(all.size());
        for (Pedestre p : all) {
            if (p.isAlive() && p.deathTime == 0 && level.isPositionEntityTicking(p.blockPosition())) {
                peds.add(p);
            }
        }
        // far from every player: gone
        if (level.getGameTime() % 20 == 0) {
            for (Pedestre p : peds) {
                Entity near = level.getNearestPlayer(p, GONE);
                boolean lostAndUnseen = p.state == Pedestre.FREE && p.freeT > 200 && !seen(level, p);
                boolean ranOff = p.flee > 0 && level.getNearestPlayer(p, 40) == null;
                if (near == null || lostAndUnseen || ranOff) {
                    p.discard();
                }
            }
            peds.removeIf(Entity::isRemoved);
        }
        boolean[] green = new boolean[1], blink = new boolean[1];
        double left = signalLeft(level, green, blink);

        // fights heard this tick
        List<double[]> noises = NOISES.remove(level);
        if (noises != null) {
            for (double[] n : noises) {
                scare(peds, n[0], n[1], seed);
            }
        }

        for (Pedestre p : peds) {
            p.px = p.getX();
            p.pz = p.getZ();
            p.grounded = p.onGround();
            p.airT = p.grounded ? 0 : p.airT + 1;
            if (p.leader != null && (p.leader.isRemoved() || !p.leader.isAlive() || p.leader.distanceToSqr(p) > 9)) {
                p.leader.followers.remove(p);
                p.leader = null;
                p.state = Pedestre.FREE;
            }
            if (p.state == Pedestre.WALK && p.leader == null) {
                // back from where the body really is (a blocked step, a hit)
                double acr = p.axis == 'x' ? p.pz : p.px;
                p.a = p.axis == 'x' ? p.px : p.pz;
                double lat = (acr - lineB(p.street, p.out) - 0.5) * p.out;
                if (lat < -1.2 || lat > latMax(p.street, p.out) + 1.2) {
                    p.state = Pedestre.FREE;
                } else {
                    p.lat = Mth.clamp(lat, LAT_MIN, latMax(p.street, p.out));
                }
            }
            if (p.airT > 40) {
                p.state = Pedestre.FREE;
            }
        }
        for (Pedestre p : peds) {
            if (p.leader == null && p.grounded) {
                stepLeader(level, p, seed, cityY, green[0], blink[0], left);
            }
        }
        separate(peds);
        for (Pedestre p : peds) {
            if (p.leader != null && p.grounded) {
                stepFollower(p);
            }
        }
        List<ServerPlayer> players = level.players();
        for (int it = 0; it < 3; it++) {
            spread(peds, players);
        }
        for (Pedestre p : peds) {
            apply(level, p, seed, cityY);
        }
    }

    // ---------------------------------------------------------------- one leader's step
    private static void stepLeader(ServerLevel level, Pedestre p, int seed, int cityY, boolean green, boolean blink, double left) {
        Random r = p.rand;
        if (p.flee > 0) {
            p.flee -= DT;
        }
        double fast = p.flee > 0 ? 4.2 + (p.lookSheet().elder ? -2 : 0) : 1;
        p.moving = false;
        switch (p.state) {
            case Pedestre.FREE -> {
                // lost (thrown off the sidewalk, or the leader gone): back to the nearest sidewalk line, else stand
                p.freeT++;
                if (adopt(p, p.px, p.pz, seed) && room(level, p.px, p.pz, seed, cityY)) {
                    p.freeT = 0;
                    return;
                }
                p.state = Pedestre.FREE;
                if (p.freeT % 20 == 1) {
                    findWay(level, p, seed, cityY);
                }
                double dx = p.tx - p.px, dz = p.tz - p.pz, d = Math.sqrt(dx * dx + dz * dz);
                if (p.tx != 0 && d > 0.05 && d < 8) {
                    double sp = Math.min(d, p.speed * fast * DT);
                    p.px += dx / d * sp;
                    p.pz += dz / d * sp;
                    face(p, dx, dz);
                    p.moving = true;
                }
            }
            case Pedestre.IDLE -> {
                p.timer -= DT;
                if (p.timer <= 0 || p.flee > 0) {
                    p.state = Pedestre.WALK;
                    p.setPoseId(Pedestre.POSE_NONE);
                }
            }
            case Pedestre.WAIT -> {
                double dx = p.tx - p.px, dz = p.tz - p.pz, d = Math.sqrt(dx * dx + dz * dz);
                if (d > 0.05) {
                    double sp = Math.min(d, p.speed * DT);
                    p.px += dx / d * sp;
                    p.pz += dz / d * sp;
                    p.moving = true;
                }
                face(p, -p.csx, -p.csz); // facing the crossing
                if (p.lookSheet().phone()) {
                    p.setPoseId(Pedestre.POSE_PHONE);
                }
                if (p.flee > 0) {
                    leaveCorner(p, seed, true);
                } else if (green && left > 3 + r.nextDouble() * 2) {
                    startCross(level, p, seed, cityY, 'n');
                }
            }
            case Pedestre.CROSS -> {
                if (p.stuckT > 2) {
                    p.state = Pedestre.FREE; // something fell in the way: find the sidewalk
                    return;
                }
                double dx = p.tx - p.px, dz = p.tz - p.pz, d = Math.sqrt(dx * dx + dz * dz);
                double sp = p.speed * (p.flee > 0 ? fast : blink ? 1.35 : 1.08) * DT; // on the blinking green everyone hurries
                p.moving = true;
                face(p, dx, dz);
                p.setPoseId(Pedestre.POSE_NONE);
                if (d <= sp) {
                    p.px = p.tx;
                    p.pz = p.tz;
                    p.csx = p.dsx;
                    p.csz = p.dsz;
                    leaveCorner(p, seed, false);
                    return;
                }
                p.px += dx / d * sp;
                p.pz += dz / d * sp;
            }
            default -> walk(level, p, seed, cityY, green, left, fast);
        }
    }

    private static void walk(ServerLevel level, Pedestre p, int seed, int cityY, boolean green, double left, double fast) {
        Random r = p.rand;
        PedestreLook S = p.lookSheet();
        double sp = p.speed * fast * p.slow;
        double a0 = p.a;
        p.a += p.dir * sp * DT;
        p.moving = true;
        double max = latMax(p.street, p.out);
        p.latT = Mth.clamp(p.latT, LAT_MIN, max); // never off the sidewalk, not even dodging
        p.lat += (p.latT - p.lat) * Math.min(1, DT * 2.5);
        p.setPoseId(S.phone() && S.type == PedestreLook.GYARU ? Pedestre.POSE_PHONE : Pedestre.POSE_NONE);
        // a corner: the sidewalk lines of the crossing streets
        char other = p.axis == 'x' ? 'z' : 'x';
        int k = (int) Math.round(p.a / ShibuyaGen.CELL);
        for (int j = k - 1; j <= k + 1; j++) {
            Street s2 = ShibuyaGen.street(other, j, seed);
            for (int o2 = -1; o2 <= 1; o2 += 2) {
                double at = lineB(s2, o2) + 0.5;
                if ((a0 - at) * (p.a - at) > 0 || a0 == at) {
                    continue;
                }
                int xi = p.axis == 'x' ? p.street.i() : s2.i(), zi = p.axis == 'x' ? s2.i() : p.street.i();
                int sx = p.axis == 'x' ? o2 : p.out, sz = p.axis == 'x' ? p.out : o2;
                boolean into = p.dir == -o2;
                if (ShibuyaGen.scramble(xi, zi, seed) && into) {
                    // a big scramble crossing: turn onto the other avenue, or wait for the light
                    if (p.flee > 0 || r.nextDouble() < 0.22) {
                        turnOnto(p, other, s2, o2, p.out);
                        return;
                    }
                    p.cxi = xi;
                    p.czi = zi;
                    p.csx = sx;
                    p.csz = sz;
                    if (green && left > 5) {
                        startCross(level, p, seed, cityY, p.axis);
                    } else {
                        toWait(p, seed);
                    }
                    return;
                }
                if (!ShibuyaGen.scramble(xi, zi, seed) && p.flee <= 0 && r.nextDouble() < 0.18) {
                    turnOnto(p, other, s2, o2, r.nextBoolean() ? 1 : -1);
                    return;
                }
            }
        }
        // a stop for the phone or a shop window
        if (p.flee <= 0 && r.nextDouble() < DT * 0.012) {
            p.state = Pedestre.IDLE;
            p.timer = 2 + r.nextDouble() * 4;
            p.setPoseId(S.phone() ? Pedestre.POSE_PHONE : r.nextInt(3) == 0 ? Pedestre.POSE_PHONE : Pedestre.POSE_LOOK);
            if (p.pose() == Pedestre.POSE_PHONE && !S.phone()) {
                p.setPoseId(Pedestre.POSE_LOOK);
            }
        }
        walkPos(p);
        if (p.axis == 'x') {
            face(p, p.dir, 0);
        } else {
            face(p, 0, p.dir);
        }
        // broken ground, a post, a tree ahead: try the other side of the sidewalk; then turn back
        double ax = p.px + (p.axis == 'x' ? p.dir * 0.5 : 0), az = p.pz + (p.axis == 'z' ? p.dir * 0.5 : 0);
        if (!room(level, ax, az, seed, cityY)) {
            p.a = a0;
            walkPos(p);
            p.slow = 0.1;
            p.stuckT += DT * 3;
            if (p.stuckT < 1.5) {
                p.latT = p.lat > max / 2 ? LAT_MIN : max;
            } else {
                p.dir = -p.dir;
                p.latT = laneFor(p.axis, p.street, p.out, p.dir, r);
                p.stuckT = 0;
            }
        }
    }

    private static void face(Pedestre p, double dx, double dz) {
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d > 1.0E-4) {
            p.fx = dx / d;
            p.fz = dz / d;
        }
    }

    private static void turnOnto(Pedestre p, char axis, Street s2, int o2, int dir) {
        // keeps the body where it is: the new line's "along" is the present spot, the lane eases in
        double x = p.px, z = p.pz;
        p.axis = axis;
        p.street = s2;
        p.out = o2;
        p.dir = dir;
        p.a = axis == 'x' ? x : z;
        double acr = axis == 'x' ? z : x;
        p.lat = Mth.clamp((acr - lineB(s2, o2) - 0.5) * o2, LAT_MIN, latMax(s2, o2));
        p.latT = laneFor(axis, s2, o2, dir, p.rand);
        p.state = Pedestre.WALK;
    }

    private static double[] corner(int xi, int zi, int sx, int sz, int seed) {
        Street X = ShibuyaGen.street('x', xi, seed), Z = ShibuyaGen.street('z', zi, seed);
        return new double[] {lineB(Z, sx) + 0.5, lineB(X, sz) + 0.5};
    }

    private static void toWait(Pedestre p, int seed) {
        p.state = Pedestre.WAIT;
        // a place at the corner, on the inner side of the sidewalk, facing the crossing
        double[] c = corner(p.cxi, p.czi, p.csx, p.csz, seed);
        p.tx = c[0] + p.csx * (0.1 + p.rand.nextDouble() * 2.7);
        p.tz = c[1] + p.csz * (0.1 + p.rand.nextDouble() * 2.7);
    }

    private static void startCross(ServerLevel level, Pedestre p, int seed, int cityY, char cameAxis) {
        // straight on, straight across the other way, or on the diagonal: Shibuya's scramble
        int[][] opts = {{-p.csx, p.csz}, {p.csx, -p.csz}, {-p.csx, -p.csz}};
        double[] wts = cameAxis == 'z' ? new double[] {0.2, 0.45, 0.35} : new double[] {0.45, 0.2, 0.35};
        double x = p.rand.nextDouble();
        int first = 0;
        while (first < 2 && (x -= wts[first]) > 0) {
            first++;
        }
        // the drawn way first; if a post or broken ground is in the way (the light's pole at the corner, a crater),
        // another of the three
        for (int n = 0; n < 3; n++) {
            int i = (first + n) % 3;
            double[] d = corner(p.cxi, p.czi, opts[i][0], opts[i][1], seed);
            double tx = d[0] + opts[i][0] * (0.1 + p.rand.nextDouble() * 1.5), tz = d[1] + opts[i][1] * (0.1 + p.rand.nextDouble() * 1.5);
            if (room(level, tx, tz, seed, cityY) && lineClear(level, p.px, p.pz, tx, tz, seed, cityY)) {
                p.dsx = opts[i][0];
                p.dsz = opts[i][1];
                p.tx = tx;
                p.tz = tz;
                p.stuckT = 0;
                p.state = Pedestre.CROSS;
                return;
            }
        }
        // no way across: back along the sidewalk
        leaveCorner(p, seed, false);
    }

    private static boolean lineClear(ServerLevel level, double x0, double z0, double x1, double z1, int seed, int cityY) {
        double d = Math.hypot(x1 - x0, z1 - z0);
        int n = (int) Math.ceil(d * 2);
        for (int i = 1; i < n; i++) {
            double t = i / (double) n;
            if (!room(level, x0 + (x1 - x0) * t, z0 + (z1 - z0) * t, seed, cityY)) {
                return false;
            }
        }
        return true;
    }

    // out of the corner by one of the two sidewalks going away from the crossing
    private static void leaveCorner(Pedestre p, int seed, boolean flee) {
        boolean useX = p.rand.nextBoolean();
        Street X = ShibuyaGen.street('x', p.cxi, seed), Z = ShibuyaGen.street('z', p.czi, seed);
        char axis = useX ? 'x' : 'z';
        Street s = useX ? X : Z;
        int out = useX ? p.csz : p.csx;
        p.axis = axis;
        p.street = s;
        p.out = out;
        p.dir = useX ? p.csx : p.csz;
        p.a = useX ? p.px : p.pz;
        double acr = useX ? p.pz : p.px;
        p.lat = Mth.clamp((acr - lineB(s, out) - 0.5) * out, LAT_MIN, latMax(s, out));
        p.latT = laneFor(axis, s, out, p.dir, p.rand);
        p.state = Pedestre.WALK;
        p.setPoseId(Pedestre.POSE_NONE);
    }

    // a lost one looks for the nearest sidewalk line within 5 blocks
    private static void findWay(ServerLevel level, Pedestre p, int seed, int cityY) {
        int bx = Mth.floor(p.px), bz = Mth.floor(p.pz);
        double best = 1e9;
        p.tx = 0;
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                int x = bx + dx, z = bz + dz;
                double d = dx * dx + dz * dz;
                if (d >= best || !intact(level, x, z, seed, cityY)) {
                    continue;
                }
                ShibuyaGen.At X = ShibuyaGen.streetAt('x', z, seed), Z = ShibuyaGen.streetAt('z', x, seed);
                boolean side = X != null && !(X.d() >= X.s().r0() - 1 && X.d() <= X.s().r1() + 1)
                        || Z != null && !(Z.d() >= Z.s().r0() - 1 && Z.d() <= Z.s().r1() + 1);
                if (side) {
                    best = d;
                    p.tx = x + 0.5;
                    p.tz = z + 0.5;
                }
            }
        }
    }

    // ---------------------------------------------------------------- followers
    private static void stepFollower(Pedestre f) {
        Pedestre L = f.leader;
        double fx = L.fx, fz = L.fz, rx = -fz, rz = fx;
        double tx = L.px + rx * f.offX + fx * f.offZ, tz = L.pz + rz * f.offX + fz * f.offZ;
        f.px += (tx - f.px) * Math.min(1, DT * 6);
        f.pz += (tz - f.pz) * Math.min(1, DT * 6);
        keepOnWalk(f);
        f.fx = fx;
        f.fz = fz;
        f.moving = L.moving;
        f.flee = L.flee;
        f.state = L.state == Pedestre.IDLE ? Pedestre.IDLE : Pedestre.WALK;
        f.street = L.street;
        f.axis = L.axis;
        f.out = L.out;
        if (L.state == Pedestre.IDLE) {
            if (f.pose() == Pedestre.POSE_NONE) {
                f.setPoseId(f.lookSheet().phone() ? Pedestre.POSE_PHONE : Pedestre.POSE_LOOK);
            }
        } else {
            f.setPoseId(L.state == Pedestre.WAIT && f.lookSheet().phone() ? Pedestre.POSE_PHONE : Pedestre.POSE_NONE);
        }
    }

    // walking with the leader on the same sidewalk: on a narrow street in single file instead of side by side
    private static void keepOnWalk(Pedestre f) {
        Pedestre L = f.leader;
        if (L.state != Pedestre.WALK || L.street == null) {
            return;
        }
        double max = latMax(L.street, L.out), b = lineB(L.street, L.out) + 0.5;
        if (L.axis == 'x') {
            f.pz = b + L.out * Mth.clamp((f.pz - b) * L.out, LAT_MIN, max);
        } else {
            f.px = b + L.out * Mth.clamp((f.px - b) * L.out, LAT_MIN, max);
        }
    }

    // ---------------------------------------------------------------- bodies
    // nobody walks through anybody: the one behind and faster overtakes on the side or slows down
    private static void separate(List<Pedestre> peds) {
        Map<Long, List<Pedestre>> byWalk = new HashMap<>();
        for (Pedestre p : peds) {
            if (p.leader == null && p.state == Pedestre.WALK && p.grounded) {
                long key = ((long) p.street.i() << 3) ^ (p.axis == 'x' ? 0 : 4) ^ (p.out > 0 ? 1 : 0);
                byWalk.computeIfAbsent(key, k -> new ArrayList<>()).add(p);
            }
        }
        for (List<Pedestre> list : byWalk.values()) {
            for (Pedestre p : list) {
                p.slow = 1;
                double max = latMax(p.street, p.out);
                for (Pedestre q : list) {
                    if (q == p) {
                        continue;
                    }
                    double ahead = (q.a - p.a) * p.dir;
                    if (ahead <= 0 || ahead > 1.6 || Math.abs(q.lat - p.lat) > 0.6) {
                        continue;
                    }
                    if (q.dir == p.dir) {
                        if (p.speed > q.speed + 0.1 && max > 0.8) {
                            p.latT = q.lat > max / 2 ? Math.max(LAT_MIN, q.lat - 0.8) : Math.min(max, q.lat + 0.8);
                        } else {
                            p.slow = Math.min(p.slow, Math.max(0.15, (ahead - 0.7) / 0.9));
                        }
                    } else {
                        p.latT += (p.latT >= q.lat ? 1 : -1) * DT * 2; // head-on: both step aside
                    }
                }
            }
            // stuck behind someone standing: change lanes; still stuck, turn around
            for (Pedestre p : list) {
                double max = latMax(p.street, p.out);
                double before = p.stuckT;
                p.stuckT = p.slow < 0.4 ? p.stuckT + DT : Math.max(0, p.stuckT - DT);
                if (p.stuckT > 1.5 && before <= 1.5) {
                    p.latT = p.lat > max / 2 ? LAT_MIN : max;
                }
                if (p.stuckT > 4) {
                    p.dir = -p.dir;
                    p.latT = laneFor(p.axis, p.street, p.out, p.dir, p.rand);
                    p.stuckT = 0;
                }
            }
        }
        // crossing and waiting: a little push so nobody overlaps
        List<Pedestre> crowd = new ArrayList<>();
        for (Pedestre p : peds) {
            if (p.leader == null && (p.state == Pedestre.CROSS || p.state == Pedestre.WAIT)) {
                crowd.add(p);
            }
        }
        for (int i = 0; i < crowd.size(); i++) {
            for (int j = i + 1; j < crowd.size(); j++) {
                Pedestre a = crowd.get(i), b = crowd.get(j);
                double dx = b.px - a.px, dz = b.pz - a.pz, d2 = dx * dx + dz * dz;
                if (d2 > 0.5 || d2 < 1e-6) {
                    continue;
                }
                double d = Math.sqrt(d2), push = (0.71 - d) * 0.5;
                if (a.state == Pedestre.WAIT) {
                    a.tx -= dx / d * push;
                    a.tz -= dz / d * push;
                } else {
                    a.px -= dx / d * push;
                    a.pz -= dz / d * push;
                }
                if (b.state == Pedestre.WAIT) {
                    b.tx += dx / d * push;
                    b.tz += dz / d * push;
                } else {
                    b.px += dx / d * push;
                    b.pz += dz / d * push;
                }
            }
        }
    }

    // A real body: nobody stays inside anybody, in any state. Each pair too close moves apart, half each; the one
    // standing weighs more and is walked around; one walking a sidewalk is pushed only sideways within it. Players are
    // walls: the pedestrian steps away, the player is never moved.
    private static void spread(List<Pedestre> peds, List<ServerPlayer> players) {
        Map<Long, List<Pedestre>> grid = new HashMap<>();
        for (Pedestre p : peds) {
            grid.computeIfAbsent(key(Mth.floor(p.px), Mth.floor(p.pz)), k -> new ArrayList<>()).add(p);
        }
        for (Pedestre p : peds) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    List<Pedestre> cell = grid.get(key(Mth.floor(p.px) + dx, Mth.floor(p.pz) + dz));
                    if (cell == null) {
                        continue;
                    }
                    for (Pedestre q : cell) {
                        if (q.getId() <= p.getId()) {
                            continue;
                        }
                        double ddx = q.px - p.px, ddz = q.pz - p.pz, d = Math.sqrt(ddx * ddx + ddz * ddz);
                        if (d >= MIN || Math.abs(q.getY() - p.getY()) > 1.5) {
                            continue;
                        }
                        if (d < 1e-4) {
                            ddx = Math.cos(p.getId());
                            ddz = Math.sin(p.getId());
                            d = 1;
                        }
                        double over = MIN - (d < 1 ? d : 0), wp = heavy(p), wq = heavy(q);
                        if (wp + wq <= 0) {
                            continue;
                        }
                        double k = over / (wp + wq);
                        nudge(p, -ddx / d * k * wp, -ddz / d * k * wp);
                        nudge(q, ddx / d * k * wq, ddz / d * k * wq);
                    }
                }
            }
            for (ServerPlayer pl : players) {
                double ddx = p.px - pl.getX(), ddz = p.pz - pl.getZ(), d = Math.sqrt(ddx * ddx + ddz * ddz);
                if (d >= MIN || d < 1e-4 || Math.abs(pl.getY() - p.getY()) > 1.8 || pl.isSpectator() || heavy(p) <= 0) {
                    continue;
                }
                nudge(p, ddx / d * (MIN - d) * 0.5, ddz / d * (MIN - d) * 0.5);
            }
        }
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private static double heavy(Pedestre p) {
        if (!p.grounded) {
            return 0;
        }
        return p.state == Pedestre.IDLE || p.state == Pedestre.WAIT ? 0.25 : 1;
    }

    private static void nudge(Pedestre p, double mx, double mz) {
        if (p.state == Pedestre.WALK && p.leader == null && p.street != null) {
            double acr = p.axis == 'x' ? mz : mx, along = p.axis == 'x' ? mx : mz;
            p.lat = Mth.clamp(p.lat + acr * p.out, LAT_MIN, latMax(p.street, p.out));
            p.a += along;
            walkPos(p);
        } else if (p.state == Pedestre.WAIT && p.leader == null) {
            p.tx += mx;
            p.tz += mz;
            p.px += mx;
            p.pz += mz;
        } else {
            p.px += mx;
            p.pz += mz;
            if (p.leader != null) {
                keepOnWalk(p);
            }
        }
    }

    // ---------------------------------------------------------------- into the world
    private static void apply(ServerLevel level, Pedestre p, int seed, int cityY) {
        p.setFleeing(p.flee > 0);
        double yaw = Math.toDegrees(Math.atan2(-p.fx, p.fz));
        // looking around now and then (the head only)
        p.lookT -= DT;
        if (p.lookT <= 0) {
            p.lookT = 1.5 + p.rand.nextDouble() * 3.5;
            p.lookGoal = p.rand.nextDouble() < 0.6 ? 0 : (p.rand.nextDouble() - 0.5) * 90;
        }
        p.lookYaw += (p.lookGoal - p.lookYaw) * Math.min(1, DT * 3);
        p.setYRot((float) yaw);
        p.yBodyRot = (float) yaw;
        p.setYHeadRot((float) (yaw + (p.flee > 0 || p.pose() != Pedestre.POSE_NONE ? 0 : p.lookYaw)));
        if (!p.grounded) {
            return; // in the air (thrown by a blow): only gravity moves it
        }
        double dx = p.px - p.getX(), dz = p.pz - p.getZ();
        if (dx * dx + dz * dz < 1.0E-8) {
            return;
        }
        if (dx * dx + dz * dz > 1.0) {
            double d = Math.sqrt(dx * dx + dz * dz);
            dx /= d;
            dz /= d;
        }
        if (p.state != Pedestre.FREE && !room(level, p.getX() + dx, p.getZ() + dz, seed, cityY)) {
            if (p.state == Pedestre.CROSS) {
                p.stuckT += DT;
            }
            return; // never onto broken ground or into a post (the walk turns back next tick)
        }
        p.move(MoverType.SELF, new Vec3(dx, 0, dz));
    }

    // ---------------------------------------------------------------- fights
    private static void scare(List<Pedestre> peds, double x, double z, int seed) {
        for (Pedestre p : peds) {
            double d = Math.hypot(p.getX() - x, p.getZ() - z);
            if (d > FIGHT) {
                continue;
            }
            p.flee = Math.max(p.flee, 6 + p.rand.nextDouble() * 4 - d * 0.04);
            if (p.leader == null && p.state == Pedestre.WALK) {
                double along = p.axis == 'x' ? p.getX() - x : p.getZ() - z;
                p.dir = along >= 0 ? 1 : -1;
                p.latT = laneFor(p.axis, p.street, p.out, p.dir, p.rand);
            }
            if (p.leader == null && p.state == Pedestre.IDLE) {
                p.state = Pedestre.WALK;
            }
            if (p.leader == null && p.state == Pedestre.WAIT) {
                leaveCorner(p, seed, true);
                // the sidewalk going away from the fight
                double along = p.axis == 'x' ? p.getX() - x : p.getZ() - z;
                p.dir = along >= 0 ? 1 : -1;
            }
            p.setPoseId(Pedestre.POSE_NONE);
        }
    }

    // ---------------------------------------------------------------- coming in
    private static boolean seen(ServerLevel level, Entity e) {
        for (ServerPlayer pl : level.players()) {
            if (pl.distanceToSqr(e) < 24 * 24 && inView(level, pl, e.getX(), e.getY() + 1.6, e.getZ())) {
                return true;
            }
        }
        return false;
    }

    private static boolean inView(ServerLevel level, ServerPlayer pl, double x, double y, double z) {
        Vec3 eye = pl.getEyePosition(), to = new Vec3(x, y, z).subtract(eye);
        if (to.normalize().dot(pl.getLookAngle()) < 0.3) {
            return false;
        }
        var hit = level.clip(new ClipContext(eye, new Vec3(x, y, z), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, pl));
        return hit.getType() == HitResult.Type.MISS;
    }

    private static void spawnAround(ServerLevel level) {
        if (!level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) {
            return;
        }
        int seed = -1, cityY = 0;
        for (ServerPlayer pl : level.players()) {
            if (pl.isSpectator() || !pl.isAlive()) {
                continue;
            }
            BlockPos at = pl.blockPosition();
            if (!level.getBiome(at).is(ModWorldgen.SHIBUYA)) {
                continue;
            }
            if (seed == -1) {
                seed = seed(level);
                cityY = cityY(level);
            }
            if (Math.abs(pl.getY() - cityY) > 40) {
                continue; // deep in the metro or high on a roof: the street isn't around
            }
            boolean night = level.isNight();
            int cap = night ? CAP_NIGHT : CAP_DAY;
            int near = level.getEntitiesOfClass(Pedestre.class, pl.getBoundingBox().inflate(NEAR, 24, NEAR)).size();
            if (near >= cap) {
                continue;
            }
            for (int tries = 0; tries < 6; tries++) {
                if (spawnGroup(level, pl, seed, cityY, night)) {
                    break;
                }
            }
        }
    }

    private static boolean spawnGroup(ServerLevel level, ServerPlayer pl, int seed, int cityY, boolean night) {
        Random r = new Random(level.getRandom().nextLong());
        char axis = r.nextBoolean() ? 'x' : 'z';
        double plAcross = axis == 'x' ? pl.getZ() : pl.getX(), plAlong = axis == 'x' ? pl.getX() : pl.getZ();
        int k = (int) Math.round(plAcross / ShibuyaGen.CELL) + r.nextInt(3) - 1;
        Street s = ShibuyaGen.street(axis, k, seed);
        int out = r.nextBoolean() ? 1 : -1;
        double a = plAlong + (r.nextDouble() * 2 - 1) * 44;
        Pedestre p = ModEntities.PEDESTRE.get().create(level);
        if (p == null) {
            return false;
        }
        int type = PedestreLook.pickType(r, night);
        p.setLook(PedestreLook.make(type, r.nextInt()));
        p.axis = axis;
        p.street = s;
        p.out = out;
        p.dir = r.nextBoolean() ? 1 : -1;
        p.a = a;
        p.lat = p.latT = laneFor(axis, s, out, p.dir, r);
        p.state = Pedestre.WALK;
        walkPos(p);
        double dist = Math.hypot(p.px - pl.getX(), p.pz - pl.getZ());
        if (dist < 24 || dist > 44) {
            return false;
        }
        // only on intact city: the spot and the sidewalk 10 blocks each way along it, sky above (not under the
        // station's roof, never in the metro), out of the player's sight
        for (int d = -10; d <= 10; d += 2) {
            double x = axis == 'x' ? p.px + d : p.px, z = axis == 'x' ? p.pz : p.pz + d;
            if (!intact(level, Mth.floor(x), Mth.floor(z), seed, cityY)) {
                return false;
            }
        }
        BlockPos feet = BlockPos.containing(p.px, cityY + 1, p.pz);
        if (!room(level, p.px, p.pz, seed, cityY) || !level.canSeeSky(feet) || inView(level, pl, p.px, cityY + 2.6, p.pz)
                || !level.isPositionEntityTicking(feet)) {
            return false;
        }
        p.moveTo(p.px, cityY + 1, p.pz, (float) Math.toDegrees(Math.atan2(-(axis == 'x' ? p.dir : 0), axis == 'z' ? p.dir : 0)), 0);
        if (axis == 'x') {
            face(p, p.dir, 0);
        } else {
            face(p, 0, p.dir);
        }
        level.addFreshEntity(p);
        // little groups: school friends, gakuran classmates, tourists, couples, two salarymen
        int more = 0;
        int[] kinds = null;
        switch (type) {
            case PedestreLook.SEIFUKU -> {
                if (r.nextDouble() < 0.6) {
                    more = 1 + r.nextInt(2);
                    kinds = new int[] {PedestreLook.SEIFUKU};
                }
            }
            case PedestreLook.GAKURAN -> {
                if (r.nextDouble() < 0.5) {
                    more = 1 + r.nextInt(2);
                    kinds = new int[] {PedestreLook.GAKURAN};
                }
            }
            case PedestreLook.TURISTA -> {
                if (r.nextDouble() < 0.6) {
                    more = 1 + r.nextInt(2);
                    kinds = new int[] {PedestreLook.TURISTA};
                }
            }
            case PedestreLook.STREET, PedestreLook.GYARU -> {
                if (r.nextDouble() < 0.3) {
                    more = 1;
                    kinds = new int[] {PedestreLook.STREET, PedestreLook.GYARU};
                }
            }
            case PedestreLook.SALARYMAN -> {
                if (r.nextDouble() < 0.2) {
                    more = 1;
                    kinds = new int[] {PedestreLook.SALARYMAN};
                }
            }
            default -> {
            }
        }
        for (int i = 0; i < more; i++) {
            Pedestre f = ModEntities.PEDESTRE.get().create(level);
            if (f == null) {
                break;
            }
            f.setLook(PedestreLook.make(kinds[r.nextInt(kinds.length)], r.nextInt()));
            f.offX = (i % 2 != 0 ? -1 : 1) * (0.7 + r.nextDouble() * 0.25);
            f.offZ = -r.nextDouble() * 0.6 - (i > 1 ? 0.9 : 0);
            double rx = -p.fz, rz = p.fx;
            double x = p.px + rx * f.offX + p.fx * f.offZ, z = p.pz + rz * f.offX + p.fz * f.offZ;
            f.leader = p;
            f.state = Pedestre.WALK;
            f.street = s;
            f.axis = axis;
            f.out = out;
            f.px = x;
            f.pz = z;
            keepOnWalk(f);
            if (!room(level, f.px, f.pz, seed, cityY)) {
                f.discard();
                continue;
            }
            p.speed = Math.min(p.speed, f.speed);
            f.moveTo(f.px, cityY + 1, f.pz, p.getYRot(), 0);
            p.followers.add(f);
            level.addFreshEntity(f);
        }
        return true;
    }
}
