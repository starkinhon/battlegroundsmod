// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.power.powers.hashirama;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.starkinhon.battlegroundsmod.combat.BlockRules;
import com.starkinhon.battlegroundsmod.combat.Capture;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Defense;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Intangible;
import com.starkinhon.battlegroundsmod.combat.Ruin;
import com.starkinhon.battlegroundsmod.combat.RuinNet;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.BossOrder;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;
import com.starkinhon.battlegroundsmod.power.powers.sub.nr.NrCommon;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// The Hashirama's new moves (Stark 2026-11-22: "que foda que ficou... mistura com as skills que já tem, aí ele vai ser
// recheadão"), ported from the approved page forja/hashirama-v2 (https://claude.ai/artifact/UN7H6e9TgSSYp21kHww7UC).
// They join the old ones instead of replacing them: the boss tactics pick the giant version of a slot with an order
// (BossOrder plan "epico"); without one the slot plays its old move. Blocks are the page's units (1 = 1 block).
//   Mar de Estacas (Mokuton: Sashiki): a front runs under the ground after the target at 32 blocks/s, turning 1.5 rad/s;
//     every 3 blocks the ground swells (0.3 s, the warning) and a row of stakes 10 to 20 long bursts out tilted
//     forward, the front opening from 6 to 20 wide over 46 blocks; at the end a flower of 11 giant stakes (22) closes on
//     where it stopped. The stakes are real blocks (gone after 30 s with soulsBlockDestruction off).
//   Prisão Gigante (Mokuton: Shichūrō): 0.9 s of a square crack 16 wide round the target, then a tower of ~33 blocks
//     (4 pillars 5 thick, walls of crossed beams with 1-block gaps, a pyramid roof). Three blows on the same wall
//     (left click) burst a hole; whoever is still inside after 3 s gets the cell of stakes. The tower always comes down
//     at the end (it is a cage, not a building).
//   Golem de Madeira (Mokuton: Mokujin): rises waist-deep out of the ground between him and the target (crater 14),
//     ~36 blocks of carved wood (client/fx/hs/HsGolem). For 15 s it punches (0.8 s of shadow, crater 10, cracks to 30)
//     and sweeps the ground to grab (1 s; blockable like the tournament's grabs: blocked, the hand bounces and he is
//     stunned; caught, it lifts and slams you).
//   Substituição de Madeira (Mokuton: Kawarimi): his body turns to wood (0.3 s, intangible), bursts into splinters
//     leaving a stump, he is behind the target and a stake bursts under them 0.45 s later.
public final class HsNew {
    static final BlockState LOG = Blocks.SPRUCE_WOOD.defaultBlockState(), LOG2 = Blocks.JUNGLE_WOOD.defaultBlockState();
    static final BlockState BARK = Blocks.DARK_OAK_WOOD.defaultBlockState(), FRESH = Blocks.STRIPPED_OAK_WOOD.defaultBlockState();
    static final String EPIC = "epico";
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private HsNew() {
    }

    // The tactics asked for the giant version of this slot.
    static boolean epic(LivingEntity caster) {
        BossOrder.Order o = BossOrder.take(caster);
        return o != null && EPIC.equals(o.plan());
    }

    public static BossOrder.Order epicOrder() {
        return new BossOrder.Order(false, 0, EPIC, false);
    }

    static void resetStatics() {
        PRISONS.clear();
    }

    // ---------- a colossal stake of blocks: tilted axis, sharp point, each block born at its own tick ----------
    static void stake(Ruin.Build b, Set<Long> put, ServerLevel level, double x0, double z0, Vec3 dir, double tilt, double len, double r, int tb, double speed) {
        double g0 = Ruin.ground(level, Mth.floor(x0), Mth.floor(z0)) - 0.5;
        Vec3 ax = new Vec3(dir.x * Math.sin(tilt), Math.cos(tilt), dir.z * Math.sin(tilt)).normalize();
        for (double s = 0; s < len; s += 0.45) {
            double u = s / len, rr = r * (u < 0.15 ? 1.15 - u : 1 - Math.pow((u - 0.15) / 0.85, 1.35));
            Vec3 c = new Vec3(x0, g0, z0).add(ax.scale(s));
            int t = tb + (int) Math.round(s / speed * 20), n = Mth.ceil(rr);
            for (int dy = -n; dy <= n; dy++) {
                for (int dz = -n; dz <= n; dz++) {
                    for (int dx = -n; dx <= n; dx++) {
                        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                        if (d > rr + 0.15 || d < rr - 1.3 && u < 0.9) {
                            continue;
                        }
                        int X = (int) Math.round(c.x + dx), Y = (int) Math.round(c.y + dy), Z = (int) Math.round(c.z + dz);
                        if (!put.add(BlockPos.asLong(X, Y, Z))) {
                            continue;
                        }
                        BlockState st = u > 0.82 ? FRESH : Math.floorMod(X + Y + Z, 4) == 0 ? BARK : ((X + Z) & 1) != 0 ? LOG : LOG2;
                        b.add(X, Y, Z, st, t);
                    }
                }
            }
        }
    }

    static void quake(ServerLevel level, Vec3 at, double s, boolean impact) {
        RuinNet.quake(level, at, s, impact, 0xFFE2B8);
    }

    // ================= Mar de Estacas =================
    static final double SEA_SPEED = 32, SEA_TURN = 1.5, SEA_LEN = 46;

    static boolean seaOfStakes(LivingEntity caster, ServerLevel level) {
        LivingEntity target = NrCommon.target(caster, 46);
        Vec3 dir0 = Hashirama.aimDir(caster, target);
        SubCommon.pose(caster, "nr.selo");
        SubCommon.busy(caster, 12);
        SubCommon.sound(level, caster.position(), SoundEvents.ROOTED_DIRT_PLACE, 2f, 0.4f);
        Vec3[] F = {null};
        Vec3[] D = {dir0};
        double[] trav = {0, -99};
        Set<Integer> hit = new HashSet<>();
        Set<Long> put = new HashSet<>();
        Tasks.add(age -> {
            if (!caster.isAlive()) {
                return true;
            }
            if (age < 7) {
                if (age % 3 == 0) {
                    quake(level, caster.position(), 0.3, false);
                }
                return false;
            }
            if (age == 7) {
                SubCommon.pose(caster, "nr.mao_chao");
                Vec3 o = SubCommon.ground(level, caster.position());
                F[0] = o.add(dir0.scale(3));
                Ruin.bowl(level, F[0].x, F[0].z, 4, 1.5, 4, 0, Ruin.opts());
                quake(level, o, 1.2, true);
                SubCommon.sound(level, o, SoundEvents.WOOD_BREAK, 2.4f, 0.5f);
                return false;
            }
            if (trav[0] >= SEA_LEN) {
                if (age == (int) trav[1] + 7) {
                    flower(caster, level, F[0], D[0], put, hit);
                    return true;
                }
                return false;
            }
            // the front turns slowly after the target and runs
            if (target != null && target.isAlive()) {
                Vec3 want = SubCommon.flat(target.position().subtract(F[0]));
                if (want.lengthSqr() > 4) {
                    want = want.normalize();
                    double ang = Math.atan2(D[0].x * want.z - D[0].z * want.x, D[0].dot(want));
                    double turn = Mth.clamp(ang, -SEA_TURN * 0.05, SEA_TURN * 0.05);
                    D[0] = Hashirama.rotate(D[0], new Vec3(0, 1, 0), -turn);
                }
            }
            double step = SEA_SPEED / 20;
            F[0] = F[0].add(D[0].scale(step));
            F[0] = new Vec3(F[0].x, SubCommon.ground(level, F[0]).y, F[0].z);
            trav[0] += step;
            if (trav[0] - trav[1] >= 3) {
                trav[1] = trav[0];
                double w = 6 + 14 * Math.min(1, trav[0] / SEA_LEN);
                Vec3 P = F[0], Dd = D[0];
                // the warning: the ground swells where the row will burst
                RuinNet.debris(level, P, w * 0.4, 6, 0, 6, 0.6);
                RuinNet.tremor(level, P, 0.35, 20, w);
                SubCommon.sound(level, P, SoundEvents.ROOTED_DIRT_BREAK, 1.6f, 0.6f);
                int k = (int) (trav[0] / 3);
                Tasks.add(a2 -> {
                    if (a2 < 6) {
                        return false;
                    }
                    row(caster, level, P, Dd, w, k, put, hit);
                    return true;
                });
                if (trav[0] >= SEA_LEN) {
                    trav[1] = age;
                }
            }
            return false;
        });
        return true;
    }

    private static void row(LivingEntity caster, ServerLevel level, Vec3 at, Vec3 dir, double w, int k, Set<Long> put, Set<Integer> hit) {
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        int n = Math.max(3, (int) Math.round(w / 3.4));
        HsEpic.R.seed(900 + k * 17L + level.getGameTime());
        Ruin.Build b = new Ruin.Build();
        for (int j = 0; j < n; j++) {
            double off = (j / (double) (n - 1) - 0.5) * w + (HsEpic.R.next() - 0.5) * 1.6;
            double x = at.x + side.x * off + dir.x * (HsEpic.R.next() - 0.5) * 2, z = at.z + side.z * off + dir.z * (HsEpic.R.next() - 0.5) * 2;
            double tilt = 0.18 + HsEpic.R.next() * 0.38, len = 10 + HsEpic.R.next() * 9 * (0.5 + w / 20), r = 1.1 + HsEpic.R.next() * 0.8 + w / 26;
            Vec3 d2 = dir.add(side.scale(off / w * 0.9)).normalize();
            stake(b, put, level, x, z, d2, tilt, len, r, 0, 70 + HsEpic.R.next() * 30);
        }
        b.start(level, 600);
        Ruin.bowl(level, at.x, at.z, w * 0.4, 1.2, 3, 0, Ruin.opts().rim(0.4));
        RuinNet.debris(level, at, w * 0.5, 16, 0, 14, 1.0);
        if (k % 3 == 0) {
            RuinNet.dust(level, at, w * 0.55, 6, 48, 8, 0x7A6450);
        }
        quake(level, at, 0.5, false);
        SubCommon.sound(level, at, SoundEvents.WOOD_BREAK, 2f, 0.5f + Hashirama.RNG.nextFloat() * 0.2f);
        for (LivingEntity e : Hashirama.near(level, at, w * 0.55, 2, 6, caster)) {
            if (!hit.add(e.getId())) {
                continue;
            }
            Combat.hurt(caster, e, 26);
            Combat.push(e, dir, 0.5, 1.4);
            Hashirama.stun(caster, e, 25);
            Hashirama.splinters(level, Combat.center(e), 6, 8);
            quake(level, Combat.center(e), 0.8, true);
        }
    }

    private static void flower(LivingEntity caster, ServerLevel level, Vec3 c, Vec3 dir, Set<Long> put, Set<Integer> hit) {
        Ruin.Build b = new Ruin.Build();
        for (int i = 0; i < 11; i++) {
            double a = i / 11.0 * Math.PI * 2, rr = 7.5;
            stake(b, put, level, c.x + Math.cos(a) * rr, c.z + Math.sin(a) * rr, new Vec3(-Math.cos(a), 0, -Math.sin(a)), 0.55, 22, 2.3, i / 2, 80);
        }
        stake(b, put, level, c.x, c.z, new Vec3(0, 0, -1), 0.05, 16, 2.6, 1, 90);
        b.start(level, 600);
        Ruin.bowl(level, c.x, c.z, 8, 4, 6, 0, Ruin.opts());
        Ruin.fissures(level, c.x, c.z, 7, 24, 2, 0, 2, Ruin.opts().seed(6));
        RuinNet.debris(level, c, 8, 26, 0, 60, 1.4);
        RuinNet.dust(level, c, 16, 14, 80, 10, 0x7C6652);
        RuinNet.ring(level, c, 40, 3, false, 28, 0xB8A890);
        quake(level, c, 1.8, true);
        SubCommon.sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), 3f, 0.5f);
        SubCommon.sound(level, c, SoundEvents.WOOD_BREAK, 3f, 0.4f);
        for (LivingEntity e : Hashirama.near(level, c, 8.5, 2, 12, caster)) {
            Combat.hurt(caster, e, hit.contains(e.getId()) ? 24 : 45);
            Combat.push(e, dir, 0.3, 2.0);
            Hashirama.stun(caster, e, 35);
            Hashirama.splinters(level, Combat.center(e), 8, 12);
        }
    }

    // ================= Prisão Gigante =================
    static final int HALFW = 8, HT = 24, APEX = 33, CRACK = 18, INSIDE = 60;

    static final class Prison {
        final ServerLevel level;
        final Vec3 c;
        final Map<Long, BlockState> placed = new HashMap<>();
        final Map<Integer, Integer> wallHits = new HashMap<>();
        boolean open, down;

        Prison(ServerLevel level, Vec3 c) {
            this.level = level;
            this.c = c;
        }

        boolean inside(LivingEntity e) {
            return Math.abs(e.getX() - c.x) < HALFW - 0.6 && Math.abs(e.getZ() - c.z) < HALFW - 0.6 && e.getY() > c.y - 2 && e.getY() < c.y + APEX;
        }

        // which wall a block belongs to: 0 -z, 1 +x, 2 +z, 3 -x (-1 = pillar or roof)
        int wall(BlockPos p) {
            double dx = p.getX() + 0.5 - c.x, dz = p.getZ() + 0.5 - c.z;
            if (p.getY() > c.y + HT || Math.abs(Math.abs(dx) - HALFW) < 2.6 && Math.abs(Math.abs(dz) - HALFW) < 2.6) {
                return -1;
            }
            if (Math.abs(dz + HALFW) <= 1.6) {
                return 0;
            }
            if (Math.abs(dx - HALFW) <= 1.6) {
                return 1;
            }
            if (Math.abs(dz - HALFW) <= 1.6) {
                return 2;
            }
            return Math.abs(dx + HALFW) <= 1.6 ? 3 : -1;
        }
    }

    static final List<Prison> PRISONS = new ArrayList<>();

    // A left click on a block (PowerEvents): a blow on a prison's wall. The third on the same wall bursts a hole there.
    public static boolean hitWall(LivingEntity who, BlockPos pos) {
        for (Prison p : PRISONS) {
            if (p.down || !p.placed.containsKey(pos.asLong())) {
                continue;
            }
            int w = p.wall(pos);
            if (w < 0) {
                return true;
            }
            int n = p.wallHits.merge(w, 1, Integer::sum);
            Vec3 at = Vec3.atCenterOf(pos);
            Hashirama.splinters(p.level, at, 4, 6);
            SubCommon.sound(p.level, at, SoundEvents.WOOD_HIT, 1.8f, 0.5f + n * 0.1f);
            if (n >= 3 && !p.open) {
                p.open = true;
                burst(p, pos);
            }
            return true;
        }
        return false;
    }

    private static void burst(Prison p, BlockPos at) {
        int floor = (int) Math.floor(p.c.y);
        List<Long> gone = new ArrayList<>();
        for (Map.Entry<Long, BlockState> e : p.placed.entrySet()) {
            BlockPos q = BlockPos.of(e.getKey());
            double d = Math.hypot(q.getX() - at.getX(), q.getZ() - at.getZ());
            if (d <= 2.6 && q.getY() >= floor - 1 && q.getY() <= floor + 5 && p.wall(q) >= 0) {
                if (p.level.getBlockState(q) == e.getValue()) {
                    p.level.setBlock(q, Blocks.AIR.defaultBlockState(), FLAGS);
                }
                gone.add(e.getKey());
            }
        }
        gone.forEach(p.placed::remove);
        Vec3 c = Vec3.atCenterOf(at);
        RuinNet.debris(p.level, c, 3, 14, 0, 40, 0.9);
        RuinNet.dust(p.level, c, 5, 5, 36, 5, 0x8A7058);
        quake(p.level, c, 1.0, true);
        SubCommon.sound(p.level, c, SoundEvents.WOOD_BREAK, 3f, 0.5f);
        SubCommon.sound(p.level, c, SoundEvents.GENERIC_EXPLODE.value(), 1.4f, 0.8f);
    }

    private static void collapse(Prison p) {
        if (p.down) {
            return;
        }
        p.down = true;
        List<Map.Entry<Long, BlockState>> all = new ArrayList<>(p.placed.entrySet());
        // from the roof down, a few hundred blocks a tick
        all.sort((a, b) -> Integer.compare(BlockPos.getY(b.getKey()), BlockPos.getY(a.getKey())));
        int[] k = {0};
        RuinNet.dust(p.level, p.c, 14, 20, 70, 14, 0x86735E);
        RuinNet.debris(p.level, p.c.add(0, 12, 0), 9, 12, 0, 80, 1.3);
        SubCommon.sound(p.level, p.c, SoundEvents.WOOD_BREAK, 3f, 0.4f);
        Tasks.add(age -> {
            int n = 0;
            while (k[0] < all.size() && n++ < 600) {
                Map.Entry<Long, BlockState> e = all.get(k[0]++);
                BlockPos q = BlockPos.of(e.getKey());
                if (p.level.isLoaded(q) && p.level.getBlockState(q) == e.getValue()) {
                    p.level.setBlock(q, Blocks.AIR.defaultBlockState(), FLAGS);
                }
            }
            if (k[0] >= all.size()) {
                PRISONS.remove(p);
                return true;
            }
            return false;
        });
    }

    static boolean giantPrison(LivingEntity caster, ServerLevel level) {
        LivingEntity target = NrCommon.target(caster, 30);
        if (target == null) {
            SubCommon.warn(caster, "no_target");
            return false;
        }
        if (Hashirama.held(target)) {
            SubCommon.warn(caster, "hashirama.alvo_preso");
            return false;
        }
        Vec3 c = SubCommon.ground(level, target.position());
        Prison p = new Prison(level, c);
        SubCommon.pose(caster, "nr.selo");
        SubCommon.busy(caster, CRACK + 6);
        SubCommon.sound(level, c, SoundEvents.ROOTED_DIRT_BREAK, 2f, 0.5f);
        RuinNet.tremor(level, c, 0.5, 18, HALFW + 6);
        // the warning: the square cracks open from the corners
        double[][] cs = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
        for (int s = 0; s < 4; s++) {
            double[] u = cs[s], v = cs[(s + 1) % 4];
            RuinNet.debrisLine(level, c.add(u[0] * HALFW, 0.2, u[1] * HALFW), c.add(v[0] * HALFW, 0.2, v[1] * HALFW), 18, 5);
        }
        boolean[] caught = {false};
        Set<Integer> punished = new HashSet<>();
        Tasks.add(age -> {
            if (age < CRACK) {
                if (age % 4 == 0) {
                    quake(level, c, 0.35, false);
                }
                return false;
            }
            if (age == CRACK) {
                SubCommon.pose(caster, "nr.mao_chao");
                build(p, level);
                PRISONS.add(p);
                caught[0] = target.isAlive() && p.inside(target);
                RuinNet.ring(level, c, 30, 3, false, 24, 0xB8A890);
                quake(level, c, 1.6, true);
                SubCommon.sound(level, c, SoundEvents.WOOD_PLACE, 3f, 0.4f);
                SubCommon.sound(level, c, SoundEvents.WARDEN_EMERGE, 2f, 0.6f);
                for (int sx = -1; sx <= 1; sx += 2) {
                    for (int sz = -1; sz <= 1; sz += 2) {
                        Vec3 q = c.add(sx * HALFW, 0, sz * HALFW);
                        Ruin.bowl(level, q.x, q.z, 3.5, 1.5, 3, 0, Ruin.opts());
                        RuinNet.dust(level, q, 6, 8, 52, 7, 0x7A6450);
                    }
                }
                if (caught[0]) {
                    Combat.hurt(caster, target, 10);
                }
                return false;
            }
            // whoever is still inside after 3 s: the cell of stakes
            if (age == CRACK + INSIDE) {
                List<LivingEntity> in = new ArrayList<>();
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c.x - HALFW, c.y - 2, c.z - HALFW, c.x + HALFW, c.y + APEX, c.z + HALFW),
                        e -> Combat.isTarget(e, caster) && p.inside(e))) {
                    in.add(e);
                }
                Ruin.Build b = new Ruin.Build();
                Set<Long> put = new HashSet<>(p.placed.keySet());
                for (int i = 0; i < 9; i++) {
                    double a = i / 9.0 * Math.PI * 2;
                    stake(b, put, level, c.x + Math.cos(a) * (HALFW - 2.5), c.z + Math.sin(a) * (HALFW - 2.5), new Vec3(-Math.cos(a), 0, -Math.sin(a)), 0.6, 13, 1.6, i / 3, 90);
                }
                stake(b, put, level, c.x, c.z, new Vec3(0, 0, -1), 0, 15, 2, 0, 90);
                b.start(level, 120);
                quake(level, c, 1.4, true);
                RuinNet.dust(level, c, 9, 14, 60, 6, 0x7C6652);
                SubCommon.sound(level, c, SoundEvents.WOOD_BREAK, 3f, 0.5f);
                for (LivingEntity e : in) {
                    if (punished.add(e.getId())) {
                        Combat.hurt(caster, e, 42);
                        Combat.push(e, Vec3.ZERO, 0, 1.2);
                        Hashirama.stun(caster, e, 40);
                        Hashirama.splinters(level, Combat.center(e), 7, 12);
                    }
                }
                return false;
            }
            if (age >= CRACK + INSIDE + 26 || !caster.isAlive() && age > CRACK + 10) {
                collapse(p);
                return true;
            }
            return false;
        }, () -> collapse(p));
        return true;
    }

    // The tower: 4 thick pillars, walls of crossed beams (gaps of one block: nobody walks through), the roof closing in a
    // pyramid; born from the bottom up at 55 blocks/s. Only into air and soft blocks (and only air with destruction off).
    private static void build(Prison p, ServerLevel level) {
        Vec3 C = p.c;
        int g = (int) Math.floor(C.y) + 1;
        double V = 55;
        List<long[]> cells = new ArrayList<>();
        List<BlockState> states = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        java.util.function.BiConsumer<long[], BlockState> add = (c, s) -> {
            if (seen.add(c[0])) {
                cells.add(new long[] {c[0], c[1], states.size()});
                states.add(s);
            }
        };
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                int px = Mth.floor(C.x) + sx * HALFW, pz = Mth.floor(C.z) + sz * HALFW;
                for (int y = -2; y < HT + 2; y++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        for (int dx = -2; dx <= 2; dx++) {
                            double d = Math.hypot(dx, dz);
                            if (d > 2.4 || d < 1.2 && y > 0) {
                                continue;
                            }
                            add.accept(new long[] {BlockPos.asLong(px + dx, g + y, pz + dz), Math.max(0, y) * 20 / (long) V},
                                    Math.floorMod(y + dx, 5) == 0 ? BARK : (y & 1) != 0 ? LOG : LOG2);
                        }
                    }
                }
            }
        }
        int cx = Mth.floor(C.x), cz = Mth.floor(C.z);
        // the walls: beams every 2 blocks up, bars every 2 across, two thick
        for (int s = 0; s < 4; s++) {
            for (int a = -HALFW + 2; a <= HALFW - 2; a++) {
                for (int y = 0; y < HT; y++) {
                    boolean beam = (y & 1) == 0, bar = (a & 1) == 0;
                    if (!beam && !bar) {
                        continue;
                    }
                    for (int q = 0; q < 2; q++) {
                        int o = HALFW - q;
                        int x = s == 0 ? cx + a : s == 1 ? cx + o : s == 2 ? cx - a : cx - o;
                        int z = s == 0 ? cz - o : s == 1 ? cz + a : s == 2 ? cz + o : cz - a;
                        add.accept(new long[] {BlockPos.asLong(x, g + y, z), (long) (y * 20 / V) + 1}, beam && bar ? BARK : beam ? LOG : LOG2);
                    }
                }
            }
        }
        // the roof: rings closing in to the top
        for (int y = HT; y <= APEX; y++) {
            double k = 1 - (y - HT) / (double) (APEX - HT + 1);
            int w = (int) Math.round(HALFW * k);
            for (int dz = -w; dz <= w; dz++) {
                for (int dx = -w; dx <= w; dx++) {
                    boolean edge = Math.abs(dx) == w || Math.abs(dz) == w;
                    if (edge || (y - HT) % 3 == 0 && ((dx + dz) & 1) == 0) {
                        add.accept(new long[] {BlockPos.asLong(cx + dx, g + y, cz + dz), (long) (y * 20 / V) + 2}, Math.floorMod(dx + dz + y, 3) != 0 ? LOG : LOG2);
                    }
                }
            }
        }
        cells.sort((x, y) -> Long.compare(x[1], y[1]));
        boolean stays = BlockRules.canBreak(level);
        int[] k = {0};
        Tasks.add(age -> {
            if (p.down) {
                return true;
            }
            BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
            while (k[0] < cells.size() && cells.get(k[0])[1] <= age) {
                long[] c = cells.get(k[0]++);
                q.set(c[0]);
                if (!level.isLoaded(q)) {
                    continue;
                }
                BlockState s = level.getBlockState(q);
                if (stays ? Ruin.fragile(s) && !BlockRules.keeps(s) : s.isAir()) {
                    BlockState put = states.get((int) c[2]);
                    level.setBlock(q, put, FLAGS);
                    p.placed.put(c[0], put);
                }
            }
            return k[0] >= cells.size();
        });
    }

    // ================= Golem de Madeira =================
    // The client draws it (HsGolem) from these events; the server keeps the same points for the hits.
    // a = 0 rises (pos = its base on the ground, dir = facing), 1 punch (pos = where the fist lands), 2 grab sweep (pos =
    // start, dir = end), 3 caught, 4 blocked, 5 slam (pos), 6 goes back into the ground.
    public static final double GS = 1.4;
    static final int G_RISE = 44, G_LIFE = 320, G_PUNCH_W = 16, G_GRAB_W = 20;

    static boolean golem(LivingEntity caster, ServerLevel level) {
        LivingEntity target = NrCommon.target(caster, 48);
        if (target == null) {
            SubCommon.warn(caster, "no_target");
            return false;
        }
        Vec3 to = SubCommon.flat(target.position().subtract(caster.position()));
        double dist = to.length();
        Vec3 face = dist < 1.0E-3 ? Combat.flatLook(caster) : to.normalize();
        // between him and the target, ~17 blocks short of the target (the fist reaches there)
        Vec3 base = SubCommon.ground(level, caster.position().add(face.scale(Math.max(0, dist - 17) * 0.5 - 4)));
        int id = SubNet.newId();
        SubCommon.pose(caster, "nr.selo");
        SubCommon.busy(caster, 14);
        SubCommon.sound(level, base, SoundEvents.BEACON_ACTIVATE, 1.4f, 0.5f);
        int[] next = {G_RISE + 8};
        int[] act = {0, 0, 0};       // 0 idle, 1 punch, 2 grab; start tick; which (punch/grab count)
        Vec3[] pt = {null, null};
        Runnable[] release = {null};
        Tasks.add(age -> {
            if (age == 8) {
                SubCommon.pose(caster, "nr.mao_chao");
                SubNet.send(level, HsNet.GOLEM, id, base, face, 0, 0, 0);
                Ruin.bowl(level, base.x, base.z, 14, 6, 12, 0, Ruin.opts().rim(0.5));
                Ruin.fissures(level, base.x, base.z, 12, 34, 1.5, 0, 3, Ruin.opts().seed(2));
                RuinNet.debris(level, base, 14, 24, 0, 120, 2.0);
                RuinNet.dust(level, base, 20, 16, 90, 24, 0x86735E);
                RuinNet.ring(level, base, 50, 4, false, 36, 0xB8A890);
                RuinNet.tremor(level, base, 1.1, 24, 40);
                quake(level, base, 1.4, true);
                SubCommon.sound(level, base, SoundEvents.WARDEN_EMERGE, 3f, 0.4f);
                SubCommon.sound(level, base, SoundEvents.GENERIC_EXPLODE.value(), 3f, 0.4f);
                return false;
            }
            if (age < 8) {
                return false;
            }
            boolean over = age >= G_LIFE || !caster.isAlive();
            if (over && act[0] == 0) {
                SubNet.send(level, HsNet.GOLEM, id, base, face, 6, 0, 0);
                SubCommon.sound(level, base, SoundEvents.ROOTED_DIRT_BREAK, 3f, 0.4f);
                RuinNet.dust(level, base, 16, 10, 60, 10, 0x86735E);
                return true;
            }
            if (act[0] == 0 && age >= next[0] && target.isAlive() && !over) {
                double d = Math.hypot(target.getX() - base.x, target.getZ() - base.z);
                if (d > 40) {
                    return false;
                }
                Vec3 tp = SubCommon.ground(level, target.position());
                boolean grab = act[2] % 2 == 1 && target.onGround() && Capture.canGrab(caster, target);
                act[2]++;
                act[1] = age;
                if (grab) {
                    act[0] = 2;
                    Vec3 side = new Vec3(-face.z, 0, face.x);
                    pt[0] = SubCommon.ground(level, tp.add(side.scale(-20)).add(face.scale(-3)));
                    pt[1] = SubCommon.ground(level, tp.add(side.scale(8)));
                    SubNet.send(level, HsNet.GOLEM, id, pt[0], pt[1], 2, 0, 0);
                    SubCommon.sound(level, pt[0], SoundEvents.ROOTED_DIRT_BREAK, 2.4f, 0.4f);
                } else {
                    act[0] = 1;
                    pt[0] = tp;
                    SubNet.send(level, HsNet.GOLEM, id, tp, face, 1, 0, 0);
                    SubCommon.sound(level, base, SoundEvents.WOOD_HIT, 3f, 0.3f);
                }
                return false;
            }
            int t = age - act[1];
            if (act[0] == 1 && t == G_PUNCH_W + 4) {
                Vec3 c = pt[0];
                Ruin.bowl(level, c.x, c.z, 10, 5, 6, 0, Ruin.opts());
                Ruin.fissures(level, c.x, c.z, 9, 30, 2.5, 0, 3, Ruin.opts().seed(8));
                RuinNet.debris(level, c, 10, 28, 0, 80, 1.6);
                RuinNet.dust(level, c, 16, 12, 76, 9, 0x7C6652);
                RuinNet.ring(level, c, 44, 3.5, false, 30, 0xB8A890);
                RuinNet.tremor(level, c, 1.2, 30, 36);
                quake(level, c, 2, true);
                SubCommon.sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), 4f, 0.45f);
                SubCommon.sound(level, c, SoundEvents.WOOD_BREAK, 3f, 0.4f);
                for (LivingEntity e : Hashirama.near(level, c, 10, 2, 10, caster)) {
                    double d = Math.hypot(e.getX() - c.x, e.getZ() - c.z);
                    Combat.hurt(caster, e, (float) (d < 4 ? 55 : 55 - (d - 4) * 4));
                    Combat.pushFrom(e, c, 1.2, 1.0);
                    Hashirama.stun(caster, e, 30);
                }
            }
            if (act[0] == 1 && t >= G_PUNCH_W + 40) {
                act[0] = 0;
                next[0] = age + 36;
            }
            if (act[0] == 2) {
                if (t <= G_GRAB_W && act[2] > 0 && release[0] == null) {
                    // the hand sweeping along the ground: who it meets is grabbed (or blocks it)
                    double k = Mth.clamp(t / (double) G_GRAB_W, 0, 1);
                    double e3 = k * k * (3 - 2 * k);
                    Vec3 hand = pt[0].lerp(pt[1], e3);
                    if (t % 3 == 0) {
                        RuinNet.debris(level, hand, 2.5, 8, 0, 6, 0.8);
                    }
                    if (k > 0.2 && target.isAlive() && Math.hypot(target.getX() - hand.x, target.getZ() - hand.z) < 3.6 && Math.abs(target.getY() - hand.y) < 4) {
                        if (Defense.blockGrab(target, caster)) {
                            SubNet.send(level, HsNet.GOLEM, id, hand, face, 4, 0, 0);
                            SubCommon.sound(level, hand, SoundEvents.WOOD_HIT, 3f, 0.6f);
                            quake(level, hand, 0.9, true);
                            act[0] = 0;
                            next[0] = age + 50;
                            return false;
                        }
                        Capture.take(target);
                        Capture.grabbed(target, caster, 34);
                        Combat.hurt(caster, target, 12);
                        Vec3 lift = base.add(face.scale(9 * GS)).add(0, 16 * GS, 0);
                        Vec3 from = target.position();
                        release[0] = Freeze.hold(target, 34, a -> {
                            double q = Math.min(1, a / 20.0);
                            return from.lerp(lift, q * q * (3 - 2 * q));
                        });
                        Runnable br = release[0];
                        Capture.trap(target, br);
                        act[1] = age - G_GRAB_W - 1;
                        SubNet.send(level, HsNet.GOLEM, id, target.position(), face, 3, 0, 0);
                        SubCommon.sound(level, hand, SoundEvents.WOOD_BREAK, 2.4f, 0.6f);
                        return false;
                    }
                    if (t == G_GRAB_W) {
                        act[0] = 0;
                        next[0] = age + 30;
                    }
                }
                if (release[0] != null && t >= G_GRAB_W + 1 + 34) {
                    Vec3 slam = SubCommon.ground(level, base.add(face.scale(14 * GS)));
                    Capture.untrap(target, release[0]);
                    release[0].run();
                    release[0] = null;
                    if (target.isAlive()) {
                        target.teleportTo(slam.x, slam.y + 0.2, slam.z);
                        Combat.hurt(caster, target, 48);
                        Hashirama.stun(caster, target, 30);
                    }
                    Ruin.bowl(level, slam.x, slam.z, 7, 4, 5, 0, Ruin.opts());
                    RuinNet.debris(level, slam, 7, 22, 0, 50, 1.3);
                    RuinNet.dust(level, slam, 11, 9, 60, 8, 0x7C6652);
                    quake(level, slam, 1.8, true);
                    SubCommon.sound(level, slam, SoundEvents.GENERIC_EXPLODE.value(), 3f, 0.5f);
                    SubNet.send(level, HsNet.GOLEM, id, slam, face, 5, 0, 0);
                    act[0] = 0;
                    next[0] = age + 50;
                }
            }
            return false;
        }, () -> {
            if (release[0] != null) {
                Capture.untrap(target, release[0]);
                release[0].run();
            }
            SubNet.send(level, HsNet.GOLEM, id, base, face, 6, 0, 0);
        });
        return true;
    }

    // ================= Substituição de Madeira =================
    static final int WOOD_TICKS = 7, STAKE_AFTER = 9;

    static boolean substitute(LivingEntity caster, ServerLevel level) {
        LivingEntity target = NrCommon.target(caster, 8);
        if (target == null) {
            return false;
        }
        if (caster instanceof com.starkinhon.battlegroundsmod.boss.HashiramaEntity h) {
            h.woodFor(WOOD_TICKS);
        }
        Intangible.hold(caster, WOOD_TICKS + 2);
        SubCommon.busy(caster, WOOD_TICKS + STAKE_AFTER);
        SubCommon.sound(level, caster.position(), SoundEvents.WOOD_PLACE, 2f, 0.6f);
        Vec3[] spot = {null};
        Tasks.add(age -> {
            if (!caster.isAlive()) {
                return true;
            }
            if (age == WOOD_TICKS) {
                Vec3 from = SubCommon.ground(level, caster.position());
                Hashirama.splinters(level, from.add(0, 1, 0), 7, 26);
                RuinNet.dust(level, from, 2.4, 3, 30, 4, 0x8A7058);
                SubCommon.sound(level, from, SoundEvents.WOOD_BREAK, 2.4f, 0.7f);
                // the stump that stays where he stood
                Ruin.Build b = new Ruin.Build();
                int x = Mth.floor(from.x), y = Mth.floor(from.y) + 1, z = Mth.floor(from.z);
                for (int dy = 0; dy < 2; dy++) {
                    for (int dz = -1; dz <= 0; dz++) {
                        for (int dx = -1; dx <= 0; dx++) {
                            b.add(x + dx, y + dy, z + dz, dy == 1 ? FRESH : LOG, 0);
                        }
                    }
                }
                b.start(level, 400);
                // behind the target
                Vec3 back = Combat.flatLook(target).reverse();
                Vec3 to = null;
                for (double turn : new double[] {0, 40, -40, 80, -80}) {
                    Vec3 d = Hashirama.rotate(back, new Vec3(0, 1, 0), Math.toRadians(turn));
                    Vec3 s = SubCommon.ground(level, target.position().add(d.scale(3.2))).add(0, 0.05, 0);
                    if (level.noCollision(caster, caster.getBoundingBox().move(s.subtract(caster.position())))) {
                        to = s;
                        break;
                    }
                }
                if (to != null) {
                    caster.teleportTo(to.x, to.y, to.z);
                    if (caster instanceof net.minecraft.world.entity.Mob m) {
                        m.getNavigation().stop();
                    }
                }
                SubCommon.pose(caster, "nr.mao_chao");
                spot[0] = target.position();
                return false;
            }
            if (age == WOOD_TICKS + STAKE_AFTER && spot[0] != null) {
                Vec3 p = spot[0];
                Ruin.Build b = new Ruin.Build();
                Vec3 d = Combat.flatLook(caster);
                stake(b, new HashSet<>(), level, p.x - d.x * 1.5, p.z - d.z * 1.5, d, 0.5, 9, 1.1, 0, 80);
                b.start(level, 300);
                Ruin.bowl(level, p.x, p.z, 2.5, 1, 2, 0, Ruin.opts());
                RuinNet.debris(level, p, 2.5, 12, 0, 16, 0.8);
                quake(level, p, 1.0, true);
                SubCommon.sound(level, p, SoundEvents.WOOD_BREAK, 2.4f, 0.5f);
                if (target.isAlive() && Math.hypot(target.getX() - p.x, target.getZ() - p.z) < 2.2) {
                    Combat.hurt(caster, target, 30);
                    Combat.push(target, d, 0.4, 1.5);
                    Hashirama.stun(caster, target, 25);
                    Hashirama.splinters(level, Combat.center(target), 6, 10);
                }
                return true;
            }
            return age > WOOD_TICKS + STAKE_AFTER + 2;
        });
        return true;
    }
}
