package com.starkinhon.battlegroundsmod.power.powers.freezaboss;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.starkinhon.battlegroundsmod.boss.BossFalas;
import com.starkinhon.battlegroundsmod.boss.FreezaTotalEntity;
import com.starkinhon.battlegroundsmod.combat.BlockRules;
import com.starkinhon.battlegroundsmod.combat.Capture;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.Db;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.DbCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.DbScar;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.Freeza;
import com.starkinhon.battlegroundsmod.power.powers.sub.jj.JjCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.nr.NrCommon;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// Freeza Poder Total: an end-game boss (bosses-fim-de-jogo, Stark 2026-11-13), the one "above" the Freeza soul. His
// power is boss only and never becomes a soul. Ported from the approved page (forja/freeza-boss,
// https://claude.ai/artifact/VEy7edujXynPpupxHnV1en): the server runs the page's timeline (its seconds are 20 ticks, its
// units are blocks) and every warning the page shows, the client draws them (client/fx/FzFx) from FzNet events.
// Phase 1 (50% of his power):
//   Z  Kienzan Duplo: a pink saw disc spins up over each hand (0.75 s), both home in on the target turning 2.4 rad/s in
//      the flat (rising and falling slowly). Dodged at the last instant, a disc that comes round cuts Freeza himself:
//      12% of his life and 3 s stunned.
//   X  Ilha Telecinética: the ground cracks in a circle (0.6 s), a whole piece of it (grass, earth, rock, a tree) is torn
//      out, rises spinning, a shadow grows where it will fall (0.9 s) and it is thrown: 40. Hit in the air by any blow,
//      it bursts into rocks (8).
//   C  Rajada Mortal: six aim lines in a fan on the ground (0.45 s dashed, then locked), then six Death Beams along them
//      (22 each); one always runs at the target, the gaps between them are the answer.
//   B  Blitz da Cauda: he vanishes, a purple circle closes behind the target with his shimmer (0.35 s); he comes out and
//      whips the tail (16, thrown up), vanishes and comes down with an axe kick (24, buried). A dash to the side during
//      the shimmer leaves his back open for 1.5 s.
//   Shift+Z  Onda Imperial: for whoever stays glued to him hitting: arms crossed, a ring of purple cracks of 6.4 blocks
//            (0.6 s), then a dome tears up the ground (26 and thrown).
// Phase 2 (at half his life):
//   Shift+V  Poder Total: the muscles swell, a little red ball sinks into the ground and Namek starts to fall apart: the
//            sky goes red, lava cracks run across the ground and lava geysers burst round the fight (an orange circle
//            closes 0.8 s before each). A 5:00 clock runs on the bar; at 100% his body can't take it, so his power falls
//            with time (weaker, slower) and at 0:00 he is spent.
//   V  Chuva de Ki: he rises 8 blocks and fires 30 ki balls, each with a red reticle closing where it lands (0.6 s, 12
//      each, no knockback), then the Death Ball with a shadow of 5.5 blocks growing 1.2 s (55).
public final class FreezaTotal {
    public static final String ID = "freeza_total";
    public static final int COLOR = 0xB27CFF;
    // The 5:00 of "Namek explodes in five minutes", in ticks.
    public static final int CLOCK = 6000;
    static final RandomSource RNG = RandomSource.createThreadSafe();
    private static final int QUIET = 2 | 16;
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private FreezaTotal() {
    }

    public static Power create() {
        return new Power(ID, COLOR, 1.3f, List.of(
                new Ability(160, 0, FreezaTotal::kienzan),
                new Ability(260, 0, FreezaTotal::island),
                new Ability(180, 0, FreezaTotal::rajada),
                new Ability(700, 0, FreezaTotal::chuva),
                new Ability(200, 0, FreezaTotal::blitz)),
                Arrays.asList(new Ability(240, 0, FreezaTotal::onda), null, null, new Ability(6000, 0, FreezaTotal::poderTotal), null));
    }

    // ---------- the second phase's state (power falling with the clock) ----------

    static final class Total {
        final LivingEntity owner;
        final ServerLevel level;
        final Vec3 hole;
        int age;
        boolean on = true;

        Total(LivingEntity owner, ServerLevel level, Vec3 hole) {
            this.owner = owner;
            this.level = level;
            this.hole = hole;
        }
    }

    static final Map<Integer, Total> TOTAL = new HashMap<>();

    private static Total total(LivingEntity e) {
        Total t = TOTAL.get(e.getId());
        return t != null && t.on && t.owner == e ? t : null;
    }

    public static boolean inTotal(LivingEntity e) {
        return total(e) != null;
    }

    // How spent he is: 0 when the clock starts, 1 at 0:00.
    public static float tired(LivingEntity e) {
        Total t = total(e);
        return t == null ? 0 : Math.min(1, t.age / (float) CLOCK);
    }

    public static boolean spent(LivingEntity e) {
        return tired(e) >= 1;
    }

    // His moves' strength: from 100% down to 70% at the end of the clock.
    static float dmg(LivingEntity caster, float base) {
        return base * (1 - 0.3f * tired(caster));
    }

    // The boss is gone: Namek's sky goes back.
    public static void end(LivingEntity e) {
        Total t = TOTAL.remove(e.getId());
        if (t != null && t.on) {
            t.on = false;
            SubNet.send(t.level, FzNet.DOOM, e.getId(), t.hole, Vec3.ZERO, 0, 72, 0);
        }
    }

    public static void resetStatics() {
        TOTAL.clear();
        ISLANDS.clear();
    }

    // ---------- shared bits ----------

    static Vec3 aimDir(LivingEntity caster, LivingEntity target) {
        Vec3 d = SubCommon.flat(target != null ? target.position().subtract(caster.position()) : caster.getLookAngle());
        if (d.lengthSqr() < 1.0E-6) {
            d = SubCommon.flat(caster.getLookAngle());
        }
        return d.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : d.normalize();
    }

    // Who stands in an upright cylinder at base (radius r and height h in blocks), the caster left out.
    static List<LivingEntity> near(ServerLevel level, Vec3 base, double r, double below, double h, LivingEntity caster) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(base.x - r - 1, base.y - below, base.z - r - 1, base.x + r + 1, base.y + h, base.z + r + 1),
                e -> Combat.isTarget(e, caster))) {
            double dx = e.getX() - base.x, dz = e.getZ() - base.z, hw = e.getBbWidth() * 0.5;
            if (Math.sqrt(dx * dx + dz * dz) - hw <= r) {
                out.add(e);
            }
        }
        return out;
    }

    static void hurt(LivingEntity caster, LivingEntity t, float base) {
        Combat.hurtMagic(caster, t, dmg(caster, base));
    }

    static int stun(LivingEntity caster, LivingEntity t, int ticks) {
        int n = JjCommon.control(caster, t, ticks);
        // bosses cut stuns in half: never under 25 there (PORT JÁ JOGÁVEL)
        n = Math.max(n, JjCommon.isBoss(t) ? 50 : 25);
        Stun.apply(t, n);
        return n;
    }

    static boolean held(LivingEntity e) {
        return Capture.trapped(e) || Freeze.isFrozen(e);
    }

    // Takes half the damage while he winds a move up (he is not crushed while he charges: PORT JÁ JOGÁVEL).
    static void armor(LivingEntity caster, int ticks) {
        if (caster instanceof FreezaTotalEntity f) {
            f.armor(ticks);
        }
    }

    static void dust(ServerLevel level, Vec3 at, float size) {
        SubNet.send(level, SubNet.DUST, 0, at, Vec3.ZERO, size, 0, 0);
    }

    static void mark(ServerLevel level, Vec3 at, double radius, int ticks, int style) {
        SubNet.send(level, FzNet.MARK, 0, at, Vec3.ZERO, (float) radius, ticks, style);
    }

    private static Vec3 lerp(Vec3 a, Vec3 b, double k) {
        return a.add(b.subtract(a).scale(k));
    }

    private static double easeOut3(double t) {
        double u = 1 - Mth.clamp(t, 0, 1);
        return 1 - u * u * u;
    }

    // Where the left hand is (DbCommon.hand is the right one, raised for the discs).
    private static Vec3 raisedLeft(LivingEntity caster) {
        Vec3 r = DbCommon.raised(caster), c = caster.position();
        Vec3 f = SubCommon.flat(caster.getLookAngle());
        f = f.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : f.normalize();
        Vec3 side = new Vec3(-f.z, 0, f.x);
        double off = r.subtract(c).dot(side);
        return r.subtract(side.scale(2 * off));
    }

    // ================= Z: Kienzan Duplo =================
    static boolean kienzan(LivingEntity caster, ServerLevel level) {
        LivingEntity target = NrCommon.target(caster, 44);
        SubCommon.Guard guard = SubCommon.guard(caster);
        SubCommon.pose(caster, "db.disco_alto");
        SubCommon.busy(caster, 16);
        armor(caster, 16);
        SubCommon.sound(level, caster.position(), SoundEvents.BEACON_ACTIVATE, 1.4f, 1.8f);
        int form = 15;
        int[] ids = {SubNet.newId(), SubNet.newId()};
        Vec3[] p = new Vec3[2];
        Vec3[] v = new Vec3[2];
        boolean[] dead = new boolean[2];
        int[] life = new int[2];
        boolean[] cutBoss = {false};
        Set<Integer> cut = new HashSet<>();
        Tasks.add(age -> {
            if (age < form) {
                if (guard.broken()) {
                    for (int i = 0; i < 2; i++) {
                        SubNet.send(level, SubNet.SAW, ids[i], DbCommon.raised(caster), UP, 0, Db.PINK, 1);
                    }
                    return true;
                }
                float r = 0.9f * SubCommon.smooth((age - 2) / 12f);
                SubNet.send(level, SubNet.SAW, ids[0], DbCommon.raised(caster).add(0, 0.5, 0), UP, r, Db.PINK, 0);
                SubNet.send(level, SubNet.SAW, ids[1], raisedLeft(caster).add(0, 0.5, 0), UP, r, Db.PINK, 0);
                if (age % 4 == 0) {
                    SubCommon.sound(level, caster.position(), SoundEvents.UI_STONECUTTER_TAKE_RESULT, 1f, 1.5f);
                }
                return false;
            }
            if (age == form) {
                SubCommon.pose(caster, "db.arremesso");
                Vec3 f = aimDir(caster, target), side = new Vec3(-f.z, 0, f.x);
                p[0] = DbCommon.raised(caster).add(0, 0.5, 0);
                p[1] = raisedLeft(caster).add(0, 0.5, 0);
                // one out to each side, 14 blocks a second (the page's d.v = (+-9, 0, -10) turned to his aim)
                for (int i = 0; i < 2; i++) {
                    v[i] = side.scale(i == 0 ? 9 : -9).add(f.scale(10)).normalize().scale(0.7);
                }
                SubCommon.sound(level, caster.position(), SoundEvents.TRIDENT_THROW.value(), 1.5f, 1.1f);
            }
            boolean any = false;
            Vec3 bc = Combat.center(caster);
            for (int i = 0; i < 2; i++) {
                if (dead[i]) {
                    continue;
                }
                any = true;
                life[i]++;
                // homing in the flat (a saw turns flat), at most 2.4 rad/s
                Vec3 aim = target != null && target.isAlive() ? target.position().add(0, 1.1, 0) : p[i].add(v[i].scale(10));
                double a0 = Math.atan2(v[i].x, v[i].z), a1 = Math.atan2(aim.x - p[i].x, aim.z - p[i].z);
                double da = Mth.clamp(Mth.wrapDegrees((a1 - a0) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD, -0.12, 0.12);
                double a = a0 + da;
                v[i] = new Vec3(Math.sin(a) * 0.7, 0, Math.cos(a) * 0.7);
                double gy = DbCommon.groundY(level, p[i]);
                double want = (target != null && target.isAlive() ? target.getY() : gy) + 1.15;
                double dy = Mth.clamp(want - p[i].y, -0.25, 0.075);
                Vec3 next = p[i].add(v[i].x, dy, v[i].z);
                if (next.y < gy + 0.5) {
                    next = new Vec3(next.x, gy + 0.5, next.z);
                }
                Vec3 prev = p[i];
                p[i] = next;
                // the page's answer: a disc coming round cuts Freeza himself
                if (!cutBoss[0] && life[i] > 12 && Math.hypot(next.x - bc.x, next.z - bc.z) < 1.5 && Math.abs(next.y - bc.y) < 1.1) {
                    cutBoss[0] = true;
                    cutSelf(caster, level, bc, v[i]);
                    for (int k = 0; k < 2; k++) {
                        if (!dead[k]) {
                            dead[k] = true;
                            SubNet.send(level, SubNet.SAW, ids[k], p[k], UP, 0.9f, Db.PINK, 1);
                        }
                    }
                    break;
                }
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(prev, next).inflate(0.75), e -> Combat.isTarget(e, caster))) {
                    if (!cut.add(e.getId())) {
                        continue;
                    }
                    Vec3 c = Combat.center(e);
                    hurt(caster, e, 30);
                    Combat.push(e, v[i], 0.9, 0.55);
                    stun(caster, e, 25);
                    SubNet.send(level, SubNet.SLASH, 0, c, v[i].normalize().add(0, 0.25, 0), 0.75f, 0.12f, SubNet.STYLE_PINK);
                    DbCommon.hit(level, c, 0.9f, Db.PINK);
                    DbCommon.screen(level, c, 12);
                    SubCommon.sound(level, c, SoundEvents.PLAYER_ATTACK_SWEEP, 1.6f, 1.1f);
                    dead[i] = true;
                }
                boolean wall = SubCommon.solid(level, next);
                if (wall) {
                    JjCommon.crater(level, next, 0.9, 0.8, 2);
                    DbCommon.boom(level, next, 0.9f, Db.PINK, RNG.nextInt(50));
                    dead[i] = true;
                }
                if (life[i] > 90) {
                    dead[i] = true;
                }
                SubNet.send(level, SubNet.SAW, ids[i], next, UP, 0.9f, Db.PINK, dead[i] ? 1 : 0);
                if (life[i] % 5 == 0) {
                    SubCommon.sound(level, next, SoundEvents.UI_STONECUTTER_TAKE_RESULT, 1f, 1.4f);
                }
            }
            return !any;
        });
        return true;
    }

    private static void cutSelf(LivingEntity caster, ServerLevel level, Vec3 at, Vec3 dir) {
        caster.setHealth(Math.max(1, caster.getHealth() - caster.getMaxHealth() * 0.12f));
        Stun.apply(caster, 60, true);
        if (!held(caster)) {
            Freeze.hold(caster, 60);
        }
        SubNet.send(level, SubNet.SLASH, 0, at, dir.normalize().add(0, 0.25, 0), 0.9f, 0.14f, SubNet.STYLE_PINK);
        DbCommon.hit(level, at, 1.2f, Db.PINK);
        DbCommon.screen(level, at, 13);
        SubCommon.sound(level, at, SoundEvents.PLAYER_ATTACK_SWEEP, 2f, 0.8f);
        BossFalas.sayNow(caster, "fala.battlegroundsmod.freeza_total.cortado");
    }

    // ================= X: Ilha Telecinética =================
    static final class Island {
        final LivingEntity caster;
        Vec3 pos;
        final double r;
        boolean broken;

        Island(LivingEntity caster, Vec3 pos, double r) {
            this.caster = caster;
            this.pos = pos;
            this.r = r;
        }
    }

    static final List<Island> ISLANDS = new ArrayList<>();

    // Any blow of an enemy of his that crosses the island in the air breaks it (Combat.alongLine and inRadius ask here).
    public static void probeLine(Vec3 a, Vec3 b, double radius, Entity owner) {
        if (ISLANDS.isEmpty() || owner == null) {
            return;
        }
        for (Island is : ISLANDS) {
            if (is.broken || owner == is.caster || !Combat.isTarget(is.caster, owner)) {
                continue;
            }
            Vec3 c = is.pos.add(0, -1.2, 0), ab = b.subtract(a);
            double k = ab.lengthSqr() < 1.0E-6 ? 0 : Mth.clamp(c.subtract(a).dot(ab) / ab.lengthSqr(), 0, 1);
            if (a.add(ab.scale(k)).distanceTo(c) < is.r + radius) {
                is.broken = true;
            }
        }
    }

    public static void probeBall(Vec3 center, double radius, Entity owner) {
        if (ISLANDS.isEmpty() || owner == null) {
            return;
        }
        for (Island is : ISLANDS) {
            if (!is.broken && owner != is.caster && Combat.isTarget(is.caster, owner) && center.distanceTo(is.pos.add(0, -1.2, 0)) < is.r + radius) {
                is.broken = true;
            }
        }
    }

    static boolean island(LivingEntity caster, ServerLevel level) {
        LivingEntity target = NrCommon.target(caster, 36);
        if (target == null) {
            SubCommon.warn(caster, "no_target");
            return false;
        }
        double R = 3.1, D = 3.2;
        Vec3 f = aimDir(caster, target), side = new Vec3(-f.z, 0, f.x);
        Vec3 S = SubCommon.ground(level, caster.position().add(side.scale(5.5)).add(f.scale(1.5)));
        int id = SubNet.newId();
        Island is = new Island(caster, S, R);
        SubCommon.pose(caster, "db.mao_alto");
        SubCommon.busy(caster, 22);
        armor(caster, 24);
        // 0.6 s: the ground cracks in a circle and shakes
        mark(level, S, R + 0.6, 12, 4);
        SubCommon.sound(level, S, SoundEvents.ROOTED_DIRT_BREAK, 2f, 0.4f);
        List<BlockState> torn = new ArrayList<>();
        Vec3[] TG = {null};
        Vec3[] P0 = {null};
        double[] yaw = {0};
        Tasks.add(age -> {
            if (age < 12) {
                if (age % 3 == 0) {
                    DbCommon.screen(level, S, 4);
                }
                return false;
            }
            if (age == 12) {
                SubNet.send(level, FzNet.ISLAND, id, S, Vec3.ZERO, 0, (float) R, (float) D);
                ISLANDS.add(is);
                dust(level, S, (float) (R + 3));
                DbCommon.screen(level, S, 10);
                SubCommon.sound(level, S, SoundEvents.GENERIC_EXPLODE.value(), 1.6f, 0.5f);
                SubCommon.sound(level, S, SoundEvents.ROOTED_DIRT_BREAK, 2.4f, 0.3f);
                return false;
            }
            if (age == 13) {
                // the next tick, so the client has copied the blocks before they go
                tear(level, S, R, D, torn);
            }
            int t = age - 12;
            if (!caster.isAlive() || caster.isRemoved()) {
                is.broken = true;
            }
            if (is.broken) {
                ISLANDS.remove(is);
                Vec3 c = is.pos.add(0, -1.2, 0);
                SubNet.send(level, FzNet.ISLAND, id, is.pos, Vec3.ZERO, 3, 0, 0);
                DbCommon.boom(level, c, 3.6f, Db.ORANGE, RNG.nextInt(50));
                DbCommon.screen(level, c, 8);
                SubCommon.sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), 2f, 0.7f);
                // the rocks rain down where it was: only 8
                Vec3 under = SubCommon.ground(level, new Vec3(c.x, c.y - 0.5, c.z));
                for (LivingEntity e : near(level, under, R + 1.5, 2, 12, caster)) {
                    hurt(caster, e, 8);
                }
                rain(level, c, torn, 12, 0.35);
                return true;
            }
            yaw[0] += 0.03;
            Vec3 at;
            double tilt;
            if (t <= 22) {
                // torn out: it rises spinning slowly, dropping earth
                at = S.add(0, easeOut3(t / 22.0) * 7.5, 0);
                tilt = 0.12 * Math.sin(t * 0.1);
            } else if (t <= 40) {
                if (t == 23) {
                    TG[0] = SubCommon.ground(level, target.isAlive() ? target.position() : S.add(f.scale(10)));
                    // 0.9 s: the shadow grows where it will land
                    mark(level, TG[0], R * 1.15, 18, 3);
                    SubCommon.pose(caster, "db.empurra");
                }
                at = S.add(0, 7.5 + 0.15 * Math.sin(t * 0.15), 0);
                tilt = 0.12 * Math.sin(t * 0.1);
            } else if (t <= 51) {
                if (t == 41) {
                    P0[0] = is.pos;
                    SubCommon.sound(level, is.pos, SoundEvents.WARDEN_ATTACK_IMPACT, 2f, 0.5f);
                }
                double k = (t - 40) / 11.0;
                at = lerp(P0[0], TG[0].add(0, D * 0.3, 0), k * k).add(0, Math.sin(k * Math.PI) * 2.5, 0);
                yaw[0] += 0.17;
                tilt = 0.3 * k;
            } else {
                // the slam: 40 and buried for whoever stayed in the shadow
                ISLANDS.remove(is);
                Vec3 g = TG[0];
                SubNet.send(level, FzNet.ISLAND, id, g, Vec3.ZERO, 2, 0, 0);
                JjCommon.crater(level, g, R * 1.15, 2.2, 12);
                DbCommon.boom(level, g.add(0, 0.5, 0), 3.4f, Db.ORANGE, RNG.nextInt(50));
                dust(level, g, (float) (R * 4));
                DbCommon.screen(level, g, 10);
                SubCommon.sound(level, g, SoundEvents.GENERIC_EXPLODE.value(), 3f, 0.5f);
                SubCommon.sound(level, g, SoundEvents.ROOTED_DIRT_BREAK, 3f, 0.4f);
                for (LivingEntity e : near(level, g, R * 1.15, 2, 6, caster)) {
                    hurt(caster, e, 40);
                    Combat.pushFrom(e, g, 0.5, 0.75);
                    stun(caster, e, 30);
                    DbCommon.hit(level, Combat.center(e), 1.1f, Db.ORANGE);
                }
                rain(level, g.add(0, 1.5, 0), torn, 16, 0.6);
                return true;
            }
            is.pos = at;
            SubNet.send(level, FzNet.ISLAND, id, at, new Vec3(yaw[0], tilt, 0), 1, (float) R, (float) D);
            if (t % 6 == 0) {
                SubCommon.sound(level, at, SoundEvents.GRAVEL_BREAK, 1.2f, 0.5f);
            }
            return false;
        });
        return true;
    }

    // The piece of ground goes up: its blocks leave the world (no bits: they are lifted whole, the client draws them).
    private static void tear(ServerLevel level, Vec3 S, double R, double D, List<BlockState> torn) {
        if (!BlockRules.canBreak(level)) {
            return;
        }
        int cx = Mth.floor(S.x), cz = Mth.floor(S.z), top = FzIsland.topY(S);
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (FzIsland.Column col : FzIsland.columns(S, R, D)) {
            for (int y = top + FzIsland.ABOVE; y > top - col.depth(); y--) {
                at.set(cx + col.dx(), y, cz + col.dz());
                BlockState st = level.getBlockState(at);
                if (st.isAir() || !st.getFluidState().isEmpty() || st.hasBlockEntity() || st.getDestroySpeed(level, at) < 0) {
                    continue;
                }
                if (torn.size() < 400 && RNG.nextFloat() < 0.5f) {
                    torn.add(st);
                }
                level.setBlock(at, Blocks.AIR.defaultBlockState(), QUIET);
            }
        }
    }

    // Some of the island's blocks come down as real blocks round where it hit or burst.
    private static void rain(ServerLevel level, Vec3 c, List<BlockState> torn, int n, double pow) {
        if (torn.isEmpty() || !BlockRules.canBreak(level)) {
            return;
        }
        for (int i = 0; i < n; i++) {
            BlockState st = torn.get(RNG.nextInt(torn.size()));
            if (st.is(Blocks.SHORT_GRASS) || st.canBeReplaced()) {
                continue;
            }
            double a = RNG.nextDouble() * Mth.TWO_PI, r = RNG.nextDouble() * 2.5;
            BlockPos p = BlockPos.containing(c.x + Math.cos(a) * r, c.y + 1 + RNG.nextDouble() * 2, c.z + Math.sin(a) * r);
            if (!level.getBlockState(p).isAir()) {
                continue;
            }
            FallingBlockEntity b = FallingBlockEntity.fall(level, p, st);
            b.dropItem = false;
            double k = 0.3 + RNG.nextDouble();
            b.setDeltaMovement(Math.cos(a) * pow * k, 0.3 + 0.5 * RNG.nextDouble(), Math.sin(a) * pow * k);
            b.hurtMarked = true;
        }
    }

    // ================= C: Rajada Mortal =================
    private static final int[] ORDER = {0, 5, 2, 3, 1, 4};

    static boolean rajada(LivingEntity caster, ServerLevel level) {
        LivingEntity target = NrCommon.target(caster, 30);
        SubCommon.Guard guard = SubCommon.guard(caster);
        Vec3 f = aimDir(caster, target);
        double base = Math.atan2(f.x, f.z), span = 0.42, L = 22;
        Vec3 feet = SubCommon.ground(level, caster.position());
        Vec3[] dirs = new Vec3[6];
        for (int i = 0; i < 6; i++) {
            // the fan turned half a gap, so one line runs right at the target
            double a = base + (i / 5.0 - 0.5) * 2 * span + span / 5;
            dirs[i] = new Vec3(Math.sin(a), 0, Math.cos(a));
            SubNet.send(level, FzNet.AIM, 0, feet.add(0, 0.06, 0), dirs[i].scale(L), 2.4f + i * 0.6f, 12.4f, 0);
        }
        SubCommon.pose(caster, "db.apontar");
        SubCommon.busy(caster, 22);
        armor(caster, 22);
        SubCommon.sound(level, caster.position(), SoundEvents.BEACON_POWER_SELECT, 1.4f, 1.8f);
        Set<Integer> hit = new HashSet<>();
        Tasks.add(age -> {
            if (guard.broken()) {
                return true;
            }
            if (age < 15) {
                Freeze.holdLookFree(caster, 2);
                if (age == 12) {
                    SubCommon.sound(level, caster.position(), SoundEvents.ILLUSIONER_CAST_SPELL, 1.4f, 2f);
                }
                return false;
            }
            int n = age - 15;
            if (n < 6) {
                fireBeam(caster, level, feet, dirs[ORDER[n]], L, hit);
            }
            return n >= 5;
        });
        return true;
    }

    private static void fireBeam(LivingEntity caster, ServerLevel level, Vec3 feet, Vec3 dir, double L, Set<Integer> hit) {
        Vec3 tip = DbCommon.hand(caster).add(dir.scale(0.3));
        Vec3 E = SubCommon.ground(level, feet.add(dir.scale(L)));
        Vec3 to = E.subtract(tip);
        Vec3 end = DbCommon.beamEnd(caster, level, tip, to.normalize(), to.length(), 0.4, true);
        DbScar.along(caster, level, tip, end, 0.4);
        SubNet.send(level, SubNet.DEATH_BEAM, 0, tip, end.subtract(tip), 0, 0, 0);
        SubCommon.sound(level, tip, SoundEvents.TRIDENT_THROW.value(), 1.3f, 2f);
        for (LivingEntity t : Combat.alongLine(level, tip, end, 0.55, caster)) {
            if (!hit.add(t.getId())) {
                continue;
            }
            hurt(caster, t, 22);
            Combat.push(t, dir, 0.6, 0.45);
            stun(caster, t, 25);
            DbCommon.hit(level, Combat.center(t), 0.7f, Db.PINK);
            DbCommon.screen(level, Combat.center(t), 12);
        }
        JjCommon.crater(level, end, 0.9, 1.4, 3);
        DbCommon.boom(level, end.add(0, 0.3, 0), 1.3f, Db.PINK, RNG.nextInt(50));
    }

    // ================= B: Blitz da Cauda =================
    static boolean blitz(LivingEntity caster, ServerLevel level) {
        LivingEntity target = NrCommon.target(caster, 22);
        if (target == null) {
            SubCommon.warn(caster, "no_target");
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        SubCommon.busy(caster, 30);
        armor(caster, 12);
        Vec3[] P = {null};
        Vec3[] apex = {null};
        boolean[] struck = {false};
        Tasks.add(age -> {
            if (guard.broken() || !target.isAlive()) {
                caster.setInvisible(false);
                caster.setNoGravity(false);
                return true;
            }
            if (age < 4) {
                return false;
            }
            if (age == 4) {
                // gone: a purple afterimage; he is already where he will come out, unseen
                SubNet.send(level, SubNet.VANISH, 0, caster.position().add(0, 1, 0), Vec3.ZERO, 0, 0, 0);
                SubCommon.sound(level, caster.position(), SoundEvents.ENDERMAN_TELEPORT, 1.2f, 1.6f);
                Vec3 away = SubCommon.flat(target.position().subtract(caster.position()));
                away = away.lengthSqr() < 1.0E-6 ? aimDir(caster, target) : away.normalize();
                P[0] = SubCommon.ground(level, target.position().add(away.scale(1.7)));
                caster.setInvisible(true);
                caster.teleportTo(P[0].x, P[0].y, P[0].z);
                // 0.35 s: the circle closing behind the target, his shimmer in it
                mark(level, P[0], 1.5, 7, 0);
                return false;
            }
            if (age < 11) {
                caster.setPos(P[0].x, P[0].y, P[0].z);
                caster.setDeltaMovement(Vec3.ZERO);
                return false;
            }
            if (age == 11) {
                caster.setInvisible(false);
                caster.lookAt(EntityAnchorArgument.Anchor.EYES, Combat.center(target));
                if (caster instanceof FreezaTotalEntity fe) {
                    fe.whip();
                }
                SubNet.send(level, FzNet.WHIP, caster.getId(), P[0].add(0, 0.9, 0), aimDir(caster, target), 1, 0, 0);
                SubCommon.sound(level, P[0], SoundEvents.PLAYER_ATTACK_SWEEP, 1.8f, 0.6f);
                return false;
            }
            if (age == 14) {
                double d = Math.hypot(target.getX() - P[0].x, target.getZ() - P[0].z);
                if (d < 2.3 && Math.abs(target.getY() - P[0].y) < 2.5) {
                    struck[0] = true;
                    hurt(caster, target, 16);
                    target.setDeltaMovement(target.getDeltaMovement().x * 0.2, 1.05, target.getDeltaMovement().z * 0.2);
                    target.hurtMarked = true;
                    stun(caster, target, 30);
                    DbCommon.hit(level, Combat.center(target), 0.9f, Db.PURPLE);
                    DbCommon.screen(level, Combat.center(target), 12);
                    SubCommon.sound(level, P[0], SoundEvents.PLAYER_ATTACK_KNOCKBACK, 2f, 0.7f);
                    return false;
                }
                // the tail cut the air: his back is open 1.5 s
                Stun.apply(caster, 30, true);
                DbCommon.screen(level, P[0], 4);
                BossFalas.sayNow(caster, "fala.battlegroundsmod.freeza_total.exposto");
                return true;
            }
            if (!struck[0]) {
                return age > 14;
            }
            if (age == 17) {
                SubNet.send(level, SubNet.VANISH, 0, caster.position().add(0, 1, 0), Vec3.ZERO, 0, 0, 0);
                caster.setInvisible(true);
                return false;
            }
            if (age < 23) {
                return false;
            }
            if (age == 23) {
                apex[0] = target.position().add(0, 1.8, 0);
                caster.teleportTo(apex[0].x, apex[0].y, apex[0].z);
                caster.setNoGravity(true);
                caster.setInvisible(false);
                SubCommon.pose(caster, "db.joelhada");
                SubNet.send(level, SubNet.VANISH, 0, apex[0].add(0, 1, 0), Vec3.ZERO, 0, 0, 0);
            }
            if (age < 26) {
                caster.setPos(apex[0].x, apex[0].y, apex[0].z);
                caster.setDeltaMovement(Vec3.ZERO);
                return false;
            }
            // the axe kick: 24, buried in the ground
            caster.setNoGravity(false);
            hurt(caster, target, 24);
            target.setDeltaMovement(0, -2.2, 0);
            target.hurtMarked = true;
            stun(caster, target, 30);
            Vec3 g = SubCommon.ground(level, target.position());
            JjCommon.crater(level, g, 2.2, 1.6, 8);
            DbCommon.boom(level, g.add(0, 0.4, 0), 1.8f, Db.PURPLE, RNG.nextInt(50));
            dust(level, g, 7);
            DbCommon.screen(level, g, 10);
            SubCommon.sound(level, g, SoundEvents.GENERIC_EXPLODE.value(), 2f, 0.8f);
            return true;
        });
        return true;
    }

    // ================= Shift+Z: Onda Imperial =================
    static boolean onda(LivingEntity caster, ServerLevel level) {
        Vec3 c = SubCommon.ground(level, caster.position());
        double R = 6.4;
        SubCommon.Guard guard = SubCommon.guard(caster);
        SubCommon.pose(caster, "db.bracos_abertos");
        SubCommon.busy(caster, 16);
        armor(caster, 14);
        // 0.6 s: the ring of cracks of 6.4 blocks (the exact reach) and the aura rising
        mark(level, c, R, 7, 4);
        SubNet.send(level, SubNet.KI_AURA, caster.getId(), caster.position(), new Vec3(1, 0, 0), 26, Db.PURPLE, 1.2f);
        SubCommon.sound(level, c, SoundEvents.BEACON_ACTIVATE, 2f, 0.6f);
        Tasks.add(age -> {
            if (guard.broken()) {
                caster.setNoGravity(false);
                return true;
            }
            if (age < 12) {
                caster.setNoGravity(true);
                caster.setDeltaMovement(Vec3.ZERO);
                caster.setPos(c.x, c.y + 0.3 * SubCommon.smooth(age / 12f), c.z);
                if (age % 4 == 0) {
                    DbCommon.screen(level, c, 4);
                }
                return false;
            }
            caster.setNoGravity(false);
            DbCommon.boom(level, c.add(0, 0.6, 0), (float) R, Db.PURPLE, RNG.nextInt(50));
            DbCommon.bigCrater(level, c, R * 0.92, 1.6, 6);
            dust(level, c, (float) (R * 2.2));
            DbCommon.screen(level, c, 6);
            SubCommon.sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), 3f, 0.6f);
            for (LivingEntity e : near(level, c, R, 2, 5, caster)) {
                hurt(caster, e, 26);
                Combat.pushFrom(e, c, 1.4, 0.9);
                stun(caster, e, 30);
                DbCommon.hit(level, Combat.center(e), 1f, Db.PURPLE);
            }
            return true;
        });
        return true;
    }

    // ================= V: Chuva de Ki + Bola da Morte =================
    static boolean chuva(LivingEntity caster, ServerLevel level) {
        LivingEntity target = NrCommon.target(caster, 48);
        if (target == null) {
            SubCommon.warn(caster, "no_target");
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        Vec3 start = caster.position();
        Vec3 hover = start.add(0, 8, 0);
        SubCommon.sound(level, start, SoundEvents.BEACON_ACTIVATE, 1.6f, 1.2f);
        armor(caster, 150);
        int ball = SubNet.newId();
        List<double[]> landing = new ArrayList<>();
        int[] shot = {0};
        Vec3[] BT = {null};
        Vec3[] BA = {null};
        Tasks.add(age -> {
            if (guard.broken()) {
                caster.setNoGravity(false);
                SubNet.send(level, SubNet.DEATH_BALL, ball, caster.position(), Vec3.ZERO, 0, 0, 1);
                return true;
            }
            SubCommon.busy(caster, 3);
            caster.setNoGravity(true);
            caster.setDeltaMovement(Vec3.ZERO);
            if (age <= 12) {
                Vec3 p = start.add(0, 8 * easeOut3(age / 12.0), 0);
                caster.setPos(p.x, p.y, p.z);
                return false;
            }
            caster.setPos(hover.x, hover.y, hover.z);
            if (target.isAlive()) {
                caster.lookAt(EntityAnchorArgument.Anchor.EYES, Combat.center(target));
            }
            int t = age - 12;
            // 30 balls, one every 0.1 s, the hands taking turns; each aims where the target will be
            if (t % 2 == 0 && shot[0] < 30 && target.isAlive()) {
                boolean left = shot[0] % 2 == 1;
                SubCommon.pose(caster, left ? "db.palma_torta" : "db.palma_dir");
                Vec3 A = left ? raisedLeft(caster).add(0, -0.4, 0) : DbCommon.hand(caster);
                Vec3 lead = SubCommon.flat(target.getDeltaMovement()).scale(12);
                Vec3 B = SubCommon.ground(level, target.position().add(lead).add((RNG.nextDouble() - 0.5) * 3.5, 0, (RNG.nextDouble() - 0.5) * 3.5));
                mark(level, B, 1.2, 12, 2);
                SubNet.send(level, FzNet.KI_RAIN, 0, A, B.subtract(A), 12, 0, 0);
                landing.add(new double[] {age + 12, B.x, B.y, B.z});
                shot[0]++;
                SubCommon.sound(level, A, SoundEvents.BLAZE_SHOOT, 0.9f, 1.6f + RNG.nextFloat() * 0.3f);
            }
            for (int i = 0; i < landing.size(); i++) {
                double[] l = landing.get(i);
                if (l[0] > age) {
                    continue;
                }
                landing.remove(i--);
                Vec3 B = new Vec3(l[1], l[2], l[3]);
                DbCommon.boom(level, B.add(0, 0.3, 0), 1.1f, Db.PINK, RNG.nextInt(50));
                JjCommon.crater(level, B, 0.8, 1, 1);
                // 12, no knockback: only a jolt
                for (LivingEntity e : near(level, B, 1.2, 1.5, 3, caster)) {
                    Vec3 m = e.getDeltaMovement();
                    hurt(caster, e, 12);
                    e.setDeltaMovement(m);
                    e.hurtMarked = true;
                    Stun.apply(e, 6);
                }
            }
            int tb = t - 72;
            if (tb < 0) {
                return false;
            }
            // the Death Ball
            if (tb == 0) {
                SubCommon.pose(caster, "db.dedo_ceu");
                SubCommon.sound(level, caster.position(), SoundEvents.BEACON_ACTIVATE, 2f, 0.5f);
            }
            if (tb < 26) {
                float g = SubCommon.smooth(tb / 20f);
                float r = 0.1f + 1.5f * g;
                Vec3 at = DbCommon.raised(caster).add(0, 0.15 + 1.4 * g + r, 0);
                BA[0] = at;
                SubNet.send(level, SubNet.DEATH_BALL, ball, at, Vec3.ZERO, r, 0, 0);
                if (tb == 2) {
                    BT[0] = SubCommon.ground(level, target.isAlive() ? target.position() : hover.subtract(0, 8, 0));
                    // 1.2 s: the shadow of 5.5 blocks grows where it will land
                    mark(level, BT[0], 5.5, 24, 3);
                }
                if (tb % 10 == 0) {
                    SubCommon.sound(level, at, SoundEvents.FIRE_AMBIENT, 2f, 0.5f);
                }
                return false;
            }
            if (tb == 26) {
                SubCommon.pose(caster, "db.gesto");
                SubCommon.sound(level, BA[0], SoundEvents.WARDEN_SONIC_CHARGE, 2f, 0.6f);
            }
            if (tb < 35) {
                double k = (tb - 26) / 9.0;
                SubNet.send(level, SubNet.DEATH_BALL, ball, lerp(BA[0], BT[0].add(0, 1, 0), k * k), Vec3.ZERO, 1.6f, 0, 0);
                return false;
            }
            SubNet.send(level, SubNet.DEATH_BALL, ball, BT[0].add(0, 1, 0), Vec3.ZERO, 1.6f, 0, 1);
            caster.setNoGravity(false);
            Vec3 g = BT[0];
            DbCommon.boom(level, g.add(0, 0.2, 0), 5.8f, Db.ORANGE, RNG.nextInt(50));
            DbCommon.bigCrater(level, g, 5, 3, 8);
            dust(level, g, 16);
            DbCommon.screen(level, g, 6);
            SubCommon.sound(level, g, SoundEvents.GENERIC_EXPLODE.value(), 4f, 0.5f);
            for (LivingEntity e : near(level, g, 5.5, 2, 8, caster)) {
                hurt(caster, e, 55);
                Combat.pushFrom(e, g, 1.0, 1.2);
                stun(caster, e, 30);
                DbCommon.hit(level, Combat.center(e), 1.3f, Db.ORANGE);
            }
            return true;
        });
        return true;
    }

    // ================= Shift+V: Poder Total =================
    static boolean poderTotal(LivingEntity caster, ServerLevel level) {
        if (inTotal(caster)) {
            return false;
        }
        Vec3 c = SubCommon.ground(level, caster.position());
        Vec3 look = SubCommon.flat(caster.getLookAngle());
        Vec3 f = look.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : look.normalize();
        Vec3 hole = SubCommon.ground(level, c.add(f.scale(7)));
        int ball = SubNet.newId();
        SubCommon.pose(caster, "db.flex");
        SubCommon.busy(caster, 72);
        armor(caster, 116);
        SubNet.send(level, SubNet.KI_AURA, caster.getId(), caster.position(), new Vec3(1.3, 0, 0), 240, Db.PURPLE, 1.35f);
        mark(level, c, 9, 44, 5);
        SubCommon.sound(level, c, SoundEvents.WARDEN_ROAR, 2f, 1.3f);
        BossFalas.sayNow(caster, "fala.battlegroundsmod.freeza_total.poder_total");
        List<double[]> cracks = new ArrayList<>();
        Set<Long> done = new HashSet<>();
        Tasks.add(age -> {
            if (!caster.isAlive() || caster.isRemoved()) {
                return true;
            }
            if (caster instanceof FreezaTotalEntity fe) {
                fe.setBulk(SubCommon.smooth((age - 10) / 32f));
            }
            if (age < 52) {
                Freeze.holdLookFree(caster, 2);
                if (age % 5 == 0) {
                    DbCommon.screen(level, c, 4);
                }
                if (age == 46) {
                    // the pulse at 100%
                    DbCommon.boom(level, c.add(0, 1.2, 0), 2.2f, Db.WHITE, RNG.nextInt(50));
                    JjCommon.crater(level, c, 3.5, 1, 14);
                    dust(level, c, 9);
                    DbCommon.screen(level, c, 6);
                    SubCommon.sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), 3f, 0.6f);
                    for (LivingEntity e : near(level, c, 3.5, 2, 4, caster)) {
                        Combat.pushFrom(e, c, 1.2, 0.6);
                    }
                }
                return false;
            }
            if (age == 52) {
                SubCommon.pose(caster, "db.gesto_baixo");
                SubCommon.sound(level, caster.position(), SoundEvents.BEACON_ACTIVATE, 1.5f, 1.6f);
            }
            if (age < 63) {
                Freeze.holdLookFree(caster, 2);
                SubNet.send(level, SubNet.DEATH_BALL, ball, DbCommon.hand(caster).add(f.scale(0.3)), Vec3.ZERO, 0.22f * SubCommon.smooth((age - 52) / 8f), 0, 0);
                return false;
            }
            if (age < 70) {
                double k = (age - 63) / 7.0;
                SubNet.send(level, SubNet.DEATH_BALL, ball, lerp(DbCommon.hand(caster), hole.add(0, -1.2, 0), k * k), Vec3.ZERO, 0.22f, 0, 0);
                return false;
            }
            if (age == 70) {
                // it sinks in: Namek starts to fall apart
                SubNet.send(level, SubNet.DEATH_BALL, ball, hole.add(0, -1.2, 0), Vec3.ZERO, 0.22f, 0, 1);
                JjCommon.crater(level, hole, 0.9, 2, 4);
                mark(level, hole, 34, 44, 6);
                startTotal(caster, level, hole);
                if (BlockRules.canBreak(level)) {
                    cracks.addAll(lavaCracks());
                }
                SubCommon.sound(level, hole, SoundEvents.LAVA_EXTINGUISH, 3f, 0.5f);
                return false;
            }
            if (age == 75) {
                DbCommon.boom(level, hole.add(0, 0.5, 0), 3f, Db.ORANGE, RNG.nextInt(50));
                dust(level, hole, 10);
                DbCommon.screen(level, hole, 10);
                SubCommon.sound(level, hole, SoundEvents.GENERIC_EXPLODE.value(), 3f, 0.4f);
            }
            // the lava cracks run out over 2.2 s (magma at their bottom, their lips pushed up)
            double front = 34 * easeOut3((age - 70) / 44.0);
            for (double[] q : cracks) {
                if (q[2] < front && q[6] == 0) {
                    q[6] = 1;
                    Freeza.crack(level, hole, q, done);
                }
            }
            return age >= 115;
        });
        return true;
    }

    // 9-12 crooked lines from the hole out to 30-36 blocks, each point {x, z, distance, depth, width, angle, carved}.
    private static List<double[]> lavaCracks() {
        List<double[]> out = new ArrayList<>();
        int n = 9 + RNG.nextInt(4);
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n + (RNG.nextDouble() - 0.5) * 0.5, bend = 0;
            double end = 30 + RNG.nextDouble() * 6;
            for (double r = 1.5; r < end; r += 0.45) {
                bend += (RNG.nextDouble() - 0.5) * 0.04;
                bend *= 0.96;
                a += bend;
                out.add(new double[] {Math.cos(a) * r, Math.sin(a) * r, r, 1 + RNG.nextInt(2), r < 14 ? 2 : 1, a, 0});
            }
        }
        return out;
    }

    private static void startTotal(LivingEntity caster, ServerLevel level, Vec3 hole) {
        Total tt = new Total(caster, level, hole);
        TOTAL.put(caster.getId(), tt);
        SubNet.send(level, FzNet.DOOM, caster.getId(), hole, Vec3.ZERO, 1, 72, 0);
        boolean boss = caster instanceof FreezaTotalEntity;
        int[] nextGeyser = {40};
        List<double[]> geysers = new ArrayList<>();
        Tasks.add(age -> {
            if (!tt.on || !caster.isAlive() || caster.isRemoved() || !boss && age > 1200 || TOTAL.get(caster.getId()) != tt) {
                end(caster);
                return true;
            }
            tt.age = age;
            float tired = tired(caster);
            if (age % 20 == 0) {
                SubNet.send(level, FzNet.DOOM, caster.getId(), hole, Vec3.ZERO, 1, 72, 0);
                if (caster instanceof FreezaTotalEntity fe) {
                    fe.clock(Math.max(0, (CLOCK - age) / 20));
                }
            }
            if (age % 200 == 0) {
                // the aura at 100%, failing as he tires
                SubNet.send(level, SubNet.KI_AURA, caster.getId(), caster.position(), new Vec3(1.3 - 0.7 * tired, 0, 0), 220, Db.PURPLE, 1.35f);
            }
            // the geysers: every 0.55 s (slower as he tires), 40% of them at someone
            if (age >= nextGeyser[0]) {
                nextGeyser[0] = age + 11 + Math.round(7 * tired);
                List<LivingEntity> foes = Combat.inRadius(level, caster.position(), 40, caster);
                Vec3 at;
                if (!foes.isEmpty() && RNG.nextFloat() < 0.4f) {
                    LivingEntity t = foes.get(RNG.nextInt(foes.size()));
                    at = t.position().add((RNG.nextDouble() - 0.5) * 3, 0, (RNG.nextDouble() - 0.5) * 3);
                } else {
                    double a = RNG.nextDouble() * Mth.TWO_PI, d = 3 + RNG.nextDouble() * 12;
                    at = caster.position().add(Math.cos(a) * d, 0, Math.sin(a) * d);
                }
                at = SubCommon.ground(level, at.add(0, 2, 0));
                mark(level, at, 1.6, 16, 1);
                geysers.add(new double[] {age + 16, at.x, at.y, at.z});
            }
            for (int i = 0; i < geysers.size(); i++) {
                double[] g = geysers.get(i);
                if (g[0] > age) {
                    continue;
                }
                geysers.remove(i--);
                Vec3 p = new Vec3(g[1], g[2], g[3]);
                SubNet.send(level, FzNet.GEYSER, 0, p, Vec3.ZERO, 6 + RNG.nextFloat() * 4, RNG.nextInt(1000), 0);
                JjCommon.crater(level, p, 0.9, 1.2, 2);
                DbCommon.screen(level, p, 4);
                SubCommon.sound(level, p, SoundEvents.LAVA_POP, 2.5f, 0.6f);
                SubCommon.sound(level, p, SoundEvents.FIRECHARGE_USE, 1.5f, 0.6f);
                for (LivingEntity e : near(level, p, 1.5, 1.5, 6, caster)) {
                    hurt(caster, e, 14);
                    e.setDeltaMovement(e.getDeltaMovement().x * 0.3, 0.95, e.getDeltaMovement().z * 0.3);
                    e.hurtMarked = true;
                    e.igniteForSeconds(3);
                    stun(caster, e, 25);
                }
            }
            if (age == CLOCK) {
                BossFalas.sayNow(caster, "fala.battlegroundsmod.freeza_total.esgotado");
            }
            return false;
        });
    }
}
