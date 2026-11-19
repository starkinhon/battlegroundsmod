package com.starkinhon.battlegroundsmod.power.powers.hashirama;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Ruin;
import com.starkinhon.battlegroundsmod.combat.RuinNet;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;
import com.starkinhon.battlegroundsmod.power.powers.sub.nr.NrCommon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

// The Hashirama's moves at the scale of the strongest boss of the mod (approved page forja/escala-fim-de-jogo,
// https://claude.ai/artifact/3W7E3frk5jNpm6AVxXzcyP, Stark 2026-11-19: "ISSO É UM BOSS FINAL"; before it his biggest
// crater was ~6 blocks, "o C do Kuririn"). Blocks are the page's units here (1 = 1 block); seconds are 20 ticks.
//   Floresta Profunda (Mokuton: Jukai Kōtan): he claps the ground and a whole forest tears out of it in a radius of 64
//     at 42 blocks/s: 34 colossal trees of real blocks (trunks 3 to 5 thick, 25 to 45 high, roots, branches, crowns),
//     the ground swelling and cracking in front of the wave. One tree bursts under the target: 0.5 s of seal with the
//     ground shaking is the warning, a dash or a jump gets out. The trees stay (with soulsBlockDestruction off they
//     only go into air and are gone after 30 s).
//   Dragões de Madeira (Mokuryū no Jutsu): three dragons three times the approved one, each digging under the ground
//     (a furrow 6 wide torn at 45 blocks/s), bursting out of a crater of 9 near the target, spiralling up ~40 blocks,
//     roaring and diving at the target (turning 2.6 rad/s: a dash at the last second makes it stick its head in the
//     ground): a crater of 12 and cracks to 30.
public final class HsEpic {
    static final RandomSourceHolder R = new RandomSourceHolder();

    private HsEpic() {
    }

    static final class RandomSourceHolder {
        long s = 1;

        void seed(long v) {
            s = (v * 2654435761L) & 0xFFFFFFFFL;
        }

        double next() {
            s = (s * 1103515245L + 12345L) & 0x7FFFFFFFL;
            return s / (double) 0x7FFFFFFFL;
        }
    }

    // A page point (x, z) of the frame at o turned by yaw (page -z = forward), in blocks.
    static Vec3 at(Vec3 o, double yaw, double x, double z) {
        double c = Math.cos(yaw), s = Math.sin(yaw);
        return new Vec3(o.x + x * c + z * s, o.y, o.z - x * s + z * c);
    }

    static void quake(ServerLevel level, Vec3 at, double strength, boolean impact) {
        RuinNet.quake(level, at, strength, impact, 0xFFE2B8);
    }

    // ================= Floresta Profunda =================
    static final int SEAL = 10, SLAP = 3;
    static final double RMAX = 64, SPEED = 42;

    private static final BlockState LOG = Blocks.SPRUCE_WOOD.defaultBlockState(), LOG2 = Blocks.JUNGLE_WOOD.defaultBlockState();
    private static final BlockState LEAF = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
    private static final BlockState LEAF2 = Blocks.JUNGLE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);

    record Tree(int x0, int z0, int g0, int ht, double r0, int tb, double vy, double lean0, double lean1, double ph, double tw) {
        double[] axis(double y) {
            return new double[] {x0 + lean0 * y + Math.sin(y * tw + ph) * 2.2, z0 + lean1 * y + Math.cos(y * tw * 0.8 + ph) * 2.2};
        }
    }

    // The page's colossalTree: every block is born at its own tick (the trunk climbs vy blocks a second from tb).
    static Tree colossal(ServerLevel level, Ruin.Build b, int x0, int z0, int tb, int seed, double hmax) {
        R.seed(seed);
        int g0 = Ruin.ground(level, x0, z0) + 1;
        int ht = (int) Math.round(hmax * (0.75 + R.next() * 0.45));
        double r0 = 1.6 + R.next() * 1.2, vy = 55 + R.next() * 20;
        Tree t = new Tree(x0, z0, g0, ht, r0, tb, vy, (R.next() - 0.5) * 0.35, (R.next() - 0.5) * 0.35, R.next() * 6.28, 0.08 + R.next() * 0.06);
        Set<Long> put = new HashSet<>();
        // the trunk: only the shell of each layer, the foot swelling into roots
        for (int y = 0; y < ht; y++) {
            double[] c = t.axis(y);
            double rr = r0 * (1 + 1.5 * Math.exp(-y / 2.6)) * (1 - 0.35 * y / ht);
            int tick = tb + (int) Math.round(y / vy * 20);
            int n = Mth.ceil(rr);
            for (int dz = -n; dz <= n; dz++) {
                for (int dx = -n; dx <= n; dx++) {
                    double d = Math.hypot(dx, dz) + noise(c[0] + dx, y, seed) * 0.8;
                    if (d <= rr && d > rr - 1.6) {
                        add(b, put, c[0] + dx, g0 + y, c[1] + dz, (y + dx + dz) % 3 != 0 ? LOG : LOG2, tick);
                    }
                }
            }
        }
        // roots opening out of the foot into the ground
        int nr = 4 + (int) (R.next() * 3);
        for (int k = 0; k < nr; k++) {
            double a = k / (double) nr * 6.283 + R.next() * 0.6, L = 7 + R.next() * 7;
            for (double s = 0; s < L; s += 0.7) {
                double x = x0 + Math.cos(a) * (r0 * 1.5 + s), z = z0 + Math.sin(a) * (r0 * 1.5 + s);
                double y = Ruin.ground(level, Mth.floor(x), Mth.floor(z)) + 1.2 - s * 0.22, rr = r0 * 0.9 * (1 - s / L);
                int tick = tb - 1 + (int) Math.round(s / 60 * 20);
                for (int q = -1; q <= 1; q++) {
                    add(b, put, x + q * rr * Math.sin(a) * 0.6, y, z - q * rr * Math.cos(a) * 0.6, LOG, tick);
                }
                if (rr > 0.8) {
                    add(b, put, x, y + 1, z, LOG2, tick);
                }
            }
        }
        // branches with their crowns, and the top crown
        int nb = 3 + (int) (R.next() * 3);
        for (int k = 0; k < nb; k++) {
            int y0 = (int) Math.round(ht * (0.5 + 0.4 * R.next()));
            double[] c = t.axis(y0);
            double a = R.next() * 6.283, L = 7 + R.next() * 8, up = 0.45 + R.next() * 0.5;
            double ex = c[0], ey = g0 + y0, ez = c[1];
            double t0 = tb + y0 / vy * 20;
            for (double s = 0; s < L; s += 0.6) {
                ex = c[0] + Math.cos(a) * s;
                ez = c[1] + Math.sin(a) * s;
                ey = g0 + y0 + s * up;
                int tick = (int) Math.round(t0 + s / 40 * 20);
                double br = 1.2 * (1 - s / L * 0.5);
                for (int q = -1; q <= 1; q++) {
                    for (int p = 0; p <= (br > 1 ? 1 : 0); p++) {
                        add(b, put, ex + q * Math.sin(a) * br * 0.7, ey + p, ez - q * Math.cos(a) * br * 0.7, LOG, tick);
                    }
                }
            }
            leafBlob(b, put, Math.round(ex), Math.round(ey + 1), Math.round(ez), 3.5 + R.next() * 2.5, (int) Math.round(t0 + L / 40 * 20 + 1), seed);
        }
        double[] c = t.axis(ht);
        leafBlob(b, put, Math.round(c[0]), g0 + ht + 1, Math.round(c[1]), 5 + R.next() * 2.5, (int) Math.round(tb + ht / vy * 20 + 1), seed);
        return t;
    }

    private static double noise(double x, double y, int seed) {
        return Ruin.noise(x * 0.5, y * 0.4, 0, seed);
    }

    private static void leafBlob(Ruin.Build b, Set<Long> put, long cx, long cy, long cz, double r, int tick, int seed) {
        int ry = Mth.ceil(r * 0.7), rh = Mth.ceil(r);
        for (int dy = -ry; dy <= ry; dy++) {
            for (int dz = -rh; dz <= rh; dz++) {
                for (int dx = -rh; dx <= rh; dx++) {
                    double d = Math.sqrt(dx * dx + dy / 0.7 * (dy / 0.7) + dz * dz)
                            + Ruin.noise((cx + dx) * 0.35, (cy + dy) * 0.35, (cz + dz) * 0.35, seed) * 2.2 - 1.1;
                    if (d <= r && d > r - 1.7) {
                        add(b, put, cx + dx, cy + dy, cz + dz, ((dx + dy + dz) & 1) != 0 ? LEAF : LEAF2, tick + (int) Math.round(d / r * 5));
                    }
                }
            }
        }
    }

    private static void add(Ruin.Build b, Set<Long> put, double x, double y, double z, BlockState s, int tick) {
        int ix = (int) Math.round(x), iy = (int) Math.round(y), iz = (int) Math.round(z);
        long k = net.minecraft.core.BlockPos.asLong(ix, iy, iz);
        if (put.add(k)) {
            b.add(ix, iy, iz, s, tick);
        }
    }

    static boolean forest(LivingEntity caster, ServerLevel level) {
        LivingEntity target = NrCommon.target(caster, 60);
        Vec3 dir = Hashirama.aimDir(caster, target);
        Vec3 o = SubCommon.ground(level, caster.position());
        double yaw = Hashirama.yawOf(dir);
        SubCommon.pose(caster, "nr.selo");
        SubCommon.busy(caster, SEAL + SLAP + 6);
        SubCommon.sound(level, o, SoundEvents.ROOTED_DIRT_BREAK, 2f, 0.4f);
        RuinNet.tremor(level, o, 0.35, 18, 14);
        // where the target stands when the seal starts: a tree will burst there
        Vec3[] spot = {target != null ? target.position() : null};
        Tasks.add(age -> {
            if (!caster.isAlive()) {
                return true;
            }
            if (age < SEAL) {
                if (age % 3 == 0) {
                    quake(level, o, 0.4 * age / SEAL, false);
                }
                return false;
            }
            if (age == SEAL) {
                SubCommon.pose(caster, "nr.mao_chao");
                return false;
            }
            if (age == SEAL + SLAP) {
                plant(caster, level, o, yaw, dir, spot[0]);
                return true;
            }
            return false;
        });
        return true;
    }

    private static void plant(LivingEntity caster, ServerLevel level, Vec3 o, double yaw, Vec3 dir, Vec3 spot) {
        quake(level, o, 1.6, true);
        SubCommon.sound(level, o, SoundEvents.GENERIC_EXPLODE.value(), 3f, 0.4f);
        SubCommon.sound(level, o, SoundEvents.WARDEN_EMERGE, 3f, 0.5f);
        // the sites: spaced (never one inside another), away from him, denser in front
        R.seed(caster.getId() * 31L + level.getGameTime());
        List<double[]> sites = new ArrayList<>();
        int tries = 0;
        while (sites.size() < 34 && tries++ < 4000) {
            double a = R.next() * 6.283, r = 9 + Math.sqrt(R.next()) * (RMAX - 9);
            double x = Math.round(Math.sin(a) * r), z = Math.round(-Math.cos(a) * r);
            double front = Math.cos(a) > -0.2 ? 1 : 0.55;
            if (R.next() > front) {
                continue;
            }
            boolean close = false;
            for (double[] s : sites) {
                close |= Math.hypot(s[0] - x, s[1] - z) < 10.5;
            }
            if (!close) {
                sites.add(new double[] {x, z, r, 0});
            }
        }
        // one bursts right under the target (the nearest site moves there)
        if (spot != null) {
            double c = Math.cos(-yaw), s = Math.sin(-yaw);
            double dx = spot.x - o.x, dz = spot.z - o.z;
            double px = dx * c + dz * s, pz = -dx * s + dz * c;
            double pr = Math.hypot(px, pz);
            if (pr >= 6 && pr <= RMAX + 4) {
                double[] best = null;
                for (double[] q : sites) {
                    if (best == null || Math.hypot(q[0] - px, q[1] - pz) < Math.hypot(best[0] - px, best[1] - pz)) {
                        best = q;
                    }
                }
                if (best != null) {
                    best[0] = Math.round(px);
                    best[1] = Math.round(pz);
                    best[2] = pr;
                    best[3] = 1;
                }
            }
        }
        Ruin.Build b = new Ruin.Build();
        List<Tree> trees = new ArrayList<>();
        int k = 0;
        for (double[] s : sites) {
            Vec3 w = at(o, yaw, s[0], s[1]);
            int tb = 2 + (int) Math.round((s[2] / SPEED + R.next() * 0.12) * 20);
            trees.add(colossal(level, b, Mth.floor(w.x), Mth.floor(w.z), tb, 31 + k++ + (int) (level.getGameTime() & 1023), s[2] < 30 ? 30 : 38));
        }
        b.start(level, 600);
        // the whole ground swelling and cracking in front of the wave
        Ruin.heave(level, o.x, o.z, 5, RMAX + 6, SPEED / 20, 0, k);
        RuinNet.tremor(level, o, 1.4, SPEED, RMAX + 8);
        RuinNet.tremor(level, o, 0.9, SPEED * 0.7, RMAX + 8);
        RuinNet.ring(level, o, RMAX + 10, 4, false, (int) ((RMAX / SPEED + 0.6) * 20), 0xB8A890);
        Set<Integer> hit = new HashSet<>();
        boolean[] born = new boolean[trees.size()];
        Tasks.add(age -> {
            boolean left = false;
            for (int i = 0; i < trees.size(); i++) {
                Tree t = trees.get(i);
                if (born[i]) {
                    continue;
                }
                if (age < t.tb()) {
                    left = true;
                    continue;
                }
                born[i] = true;
                Vec3 base = new Vec3(t.x0() + 0.5, t.g0(), t.z0() + 0.5);
                // the earth tears round the foot as the trunk comes out
                RuinNet.debris(level, base, t.r0() * 3.2, 22, 0, 10, 1.4);
                if (i % 3 == 0) {
                    RuinNet.dust(level, base, 8 + t.r0() * 2, 9 + t.r0() * 2, 64, 10, 0x7A6450);
                    SubCommon.sound(level, base, SoundEvents.WOOD_BREAK, 2.4f, 0.4f + Hashirama.RNG.nextFloat() * 0.2f);
                }
                // who stands where a trunk bursts is thrown high, away from the trunk
                for (LivingEntity e : Hashirama.near(level, base, t.r0() * 1.6 + 1.2, 2, 6, caster)) {
                    if (!hit.add(e.getId())) {
                        continue;
                    }
                    Combat.hurt(caster, e, 34);
                    Vec3 away = SubCommon.flat(e.position().subtract(base));
                    away = away.lengthSqr() < 1.0E-4 ? dir : away.normalize();
                    Combat.push(e, away, 0.9, 2.1);
                    Hashirama.stun(caster, e, 25);
                    quake(level, Combat.center(e), 1.0, true);
                    Hashirama.splinters(level, Combat.center(e), 7, 10);
                }
            }
            if (age % 6 == 0 && age < 50) {
                quake(level, at(o, yaw, 0, -20), 0.9 * (1 - age / 50.0), false);
            }
            return !left;
        });
    }

    // ================= Dragões de Madeira =================
    static final double DS = 3, DRAG_SPEED = 45;
    // the page's plans: where each digs from and where it bursts out (page frame, -z = towards the target)
    private static final double[][] PLANS = {{-6, -8, -26, -40, 0}, {6, -8, 24, -44, 11}, {0, -9, -4, -72, 22}};

    static boolean dragons(LivingEntity caster, ServerLevel level) {
        LivingEntity target = NrCommon.target(caster, 70);
        if (target == null) {
            SubCommon.warn(caster, "no_target");
            return false;
        }
        Vec3 dir = Hashirama.aimDir(caster, target);
        double yaw = Hashirama.yawOf(dir);
        Vec3 o = SubCommon.ground(level, caster.position());
        double dist = Math.max(20, Math.min(70, SubCommon.flat(target.position().subtract(o)).length()));
        SubCommon.pose(caster, "nr.selo_cruz");
        SubCommon.busy(caster, 20);
        SubCommon.sound(level, o, SoundEvents.ROOTED_DIRT_BREAK, 2.4f, 0.4f);
        for (int k = 0; k < 3; k++) {
            int kk = k;
            double[] p = PLANS[k];
            // the page's burst points are 40-72 in front for a target 46 away: scaled to where the target is
            double sc = dist / 46;
            Vec3 from = at(o, yaw, p[0], p[1]), out = at(o, yaw, p[2] * Math.min(1.2, sc), p[3] * sc);
            Tasks.later(9 + (int) p[4], () -> dragon(caster, level, target, kk, from, out));
        }
        return true;
    }

    static int dragonId(LivingEntity caster, int k) {
        return caster.getId() * 4 + k + 1;
    }

    private static void dragon(LivingEntity caster, ServerLevel level, LivingEntity target, int k, Vec3 from0, Vec3 out0) {
        if (!caster.isAlive()) {
            return;
        }
        int id = dragonId(caster, k);
        Vec3 from = SubCommon.ground(level, from0), out = SubCommon.ground(level, out0);
        double L = Math.hypot(out.x - from.x, out.z - from.z);
        int dig = Math.max(4, (int) Math.round(L / DRAG_SPEED * 20));
        Ruin.trench(level, from, out, 3, 3, dig, 0, Ruin.opts().lip(2.2).seed(k + 3));
        RuinNet.tremor(level, from, 0.6, DRAG_SPEED, L + 6);
        RuinNet.debrisLine(level, from, out, 70, 14);
        double g = out.y;
        Vec3[] hp = {new Vec3(out.x, g - 25, out.z)};
        Vec3[] vel = {new Vec3(0, 1, 0)};
        Vec3[] sA = {hp[0]};
        Vec3[] dirT = {SubCommon.flat(target.position().subtract(out))};
        int[] state = {0};
        int[] at = {0};
        Set<Integer> struck = new HashSet<>();
        Tasks.add(age -> {
            if (age < dig) {
                if (age % 4 == 0) {
                    quake(level, out, 0.35, false);
                    SubCommon.sound(level, from.lerp(out, age / (double) dig), SoundEvents.ROOTED_DIRT_BREAK, 2f, 0.5f);
                }
                return false;
            }
            int t = age - dig;
            if (state[0] == 0) {
                state[0] = 1;
                SubNet.send(level, HsNet.DRAGON, id, hp[0], new Vec3(0, 1, 0), 0, 1, (float) DS);
                Ruin.bowl(level, out.x, out.z, 9, 5, 6, 0, Ruin.opts());
                RuinNet.debris(level, out, 9, 26, 0, 60, 1.3);
                RuinNet.dust(level, out, 14, 10, 64, 10, 0x7A6450);
                RuinNet.ring(level, out, 34, 3, false, 28, 0xB8A890);
                quake(level, out, 1.3, false);
                SubCommon.sound(level, out, SoundEvents.GENERIC_EXPLODE.value(), 3f, 0.5f);
                for (LivingEntity e : Hashirama.near(level, out, 9, 2, 8, caster)) {
                    if (struck.add(e.getId())) {
                        Combat.hurt(caster, e, 28);
                        Combat.pushFrom(e, out, 1.0, 1.2);
                        Hashirama.stun(caster, e, 25);
                    }
                }
            }
            double dt = 0.05;
            Vec3 want = hp[0];
            if (state[0] == 1) {
                double D1 = 20, D2 = 18;
                if (t < D1) {
                    double e = easeOut3(t / D1), a = 0.8 + 5.2 * e + k, rr = 1 + 9 * e;
                    want = new Vec3(out.x + Math.cos(a) * rr, g - 25 + 66 * e, out.z + Math.sin(a) * rr);
                    sA[0] = want;
                    Vec3 dT = SubCommon.flat(target.position().subtract(want));
                    dirT[0] = dT.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : dT.normalize();
                } else {
                    double q = Mth.clamp((t - D1) / D2, 0, 1), e = q * q * (3 - 2 * q);
                    want = sA[0].add(dirT[0].scale(12 * e)).add(0, 6 * Math.sin(Math.min(1, q * 1.4) * Math.PI * 0.5) - 4 * q * q * q, 0);
                    if (t == (int) D1 + 5) {
                        SubCommon.sound(level, want, SoundEvents.RAVAGER_ROAR, 4f, 0.4f);
                        SubCommon.sound(level, want, SoundEvents.ENDER_DRAGON_GROWL, 2.4f, 0.5f);
                        quake(level, want, 0.5, false);
                    }
                }
                Vec3 v = want.subtract(hp[0]);
                if (v.lengthSqr() > 1.0E-6) {
                    vel[0] = v.normalize();
                }
                hp[0] = want;
                if (t >= D1 + D2) {
                    state[0] = 2;
                }
            } else if (state[0] == 2) {
                // the dive: turns slowly at the target (offset per dragon), 52 blocks/s
                Vec3 aim = target.isAlive() ? target.position() : hp[0].add(vel[0].scale(10));
                aim = aim.add((k - 1) * 3, 0, 0);
                aim = new Vec3(aim.x, SubCommon.ground(level, aim).y - 6, aim.z);
                Vec3 cur = vel[0], wd = aim.subtract(hp[0]).normalize();
                double ang = Math.acos(Mth.clamp(cur.dot(wd), -1, 1));
                if (ang > 1.0E-4) {
                    Vec3 ax = cur.cross(wd);
                    if (ax.lengthSqr() > 1.0E-10) {
                        cur = Hashirama.rotate(cur, ax.normalize(), Math.min(ang, 2.6 * dt));
                    }
                }
                vel[0] = cur;
                hp[0] = hp[0].add(cur.scale(52 * dt));
                // whoever the head runs through on the way down
                for (LivingEntity e : Hashirama.near(level, hp[0].add(0, -4, 0), 4.5, 1, 9, caster)) {
                    if (struck.add(e.getId())) {
                        Combat.hurt(caster, e, 40);
                        Combat.push(e, SubCommon.flat(cur).lengthSqr() > 1.0E-4 ? SubCommon.flat(cur).normalize() : Vec3.ZERO, 1.2, 0.8);
                        Hashirama.stun(caster, e, 30);
                        quake(level, Combat.center(e), 1.2, true);
                    }
                }
                double gg = SubCommon.ground(level, hp[0]).y;
                if (hp[0].y < gg + 2 || t > 140) {
                    state[0] = 3;
                    at[0] = t;
                    Vec3 c = new Vec3(hp[0].x, gg, hp[0].z);
                    SubNet.send(level, HsNet.DRAGON, id, hp[0], cur, 3, 1, (float) DS);
                    Ruin.bowl(level, c.x, c.z, 12, 7, 7, 0, Ruin.opts());
                    Ruin.fissures(level, c.x, c.z, 11, 30, 2.5, 4, 2, Ruin.opts().seed(k * 5 + 1));
                    RuinNet.debris(level, c, 12, 30, 0, 90, 1.4);
                    RuinNet.dust(level, c, 18, 13, 70, 9, 0x7C6652);
                    RuinNet.ring(level, c, 46, 3.5, false, 30, 0xB8A890);
                    quake(level, c, 2, k == 1);
                    SubCommon.sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), 4f, 0.45f);
                    SubCommon.sound(level, c, SoundEvents.WOOD_BREAK, 3f, 0.4f);
                    for (LivingEntity e : Hashirama.near(level, c, 12, 2, 10, caster)) {
                        if (!struck.add(e.getId())) {
                            continue;
                        }
                        double d = Math.hypot(e.getX() - c.x, e.getZ() - c.z);
                        Combat.hurt(caster, e, (float) (d < 5 ? 70 : 70 - (d - 5) * 4));
                        Combat.pushFrom(e, c, 1.3, 1.1);
                        Hashirama.stun(caster, e, 35);
                    }
                    return false;
                }
            } else {
                // stuck in the ground: slides a little further in and stays, then goes
                int t2 = t - at[0];
                hp[0] = hp[0].add(vel[0].scale(52 * dt * 0.7 * Math.max(0, 1 - t2 / 18.0)));
                if (t2 > 70) {
                    SubNet.send(level, HsNet.DRAGON, id, hp[0], vel[0], 7, 1, (float) DS);
                    return true;
                }
                return false;
            }
            SubNet.send(level, HsNet.DRAGON, id, hp[0], vel[0], 2, 1, (float) DS);
            if (t % 5 == 0) {
                SubCommon.sound(level, hp[0], SoundEvents.WOOD_STEP, 3f, 0.35f);
            }
            return false;
        });
    }

    static double easeOut3(double x) {
        x = Mth.clamp(x, 0, 1);
        return 1 - Math.pow(1 - x, 3);
    }
}
