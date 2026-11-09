package com.starkinhon.battlegroundsmod.power.powers.sub.db;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.starkinhon.battlegroundsmod.combat.Charge;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Intangible;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.PowerLogic;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;
import com.starkinhon.battlegroundsmod.power.powers.sub.jj.JjCommon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// Vegeta (SubAlma of Dragon Ball, tier 6), the Visualizador's Vegeta tab.
// Z Renzoku Energy Dan: held, he switches hands without stopping and fires dozens of yellow ki shots; the target
//   disappears into a cloud of blasts and smoke (it can't see).
// X Big Bang Attack: arm out, open palm: a big blue ball made of Zanka tongues of ki, with waves running over it; it
//   flies fast, homing on the target, and bursts into tongues that open and die torn (radius 6.4).
// C Explosao Final (the strongest attack of the mod): he rises with the golden aura at its peak and blows up: a golden
//   dome of radius 20 with a white one inside that burns for 3 s, a deep crater, everyone round thrown at the end. The
//   price: 1 heart left and turned to stone for 20 s (still, no skills, nothing gets through the stone); then the stone bursts.
// V Final Flash: arms open, the yellow ki gathers in both palms with the golden aura rising; hands together, a huge
//   yellow beam goes through everything and tears the ground up under it.
public final class Vegeta {
    private Vegeta() {
    }

    public static Power create() {
        return new Power("vegeta", 0xFFD84A, 1.1f, List.of(
                new Ability(140, 0, Vegeta::rajada),
                new Ability(200, 0, Vegeta::bigBang),
                new Ability(1600, 0, Vegeta::explosaoFinal),
                new Ability(1000, 0, Vegeta::finalFlash)));
    }

    // ---------- Z: Renzoku Energy Dan ----------

    private static boolean rajada(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        int least = 20;
        int most = 82;
        int bossHold = 30 + SubCommon.RANDOM.nextInt(40);
        SubCommon.pose(caster, "soco.barragem");
        Map<Integer, Float> cover = new HashMap<>();
        Map<Integer, LivingEntity> covered = new HashMap<>();
        int[] stop = {-1};
        Tasks.add(age -> {
            boolean broken = guard.broken();
            if (stop[0] < 0) {
                boolean held = SubCommon.held(caster, age, bossHold);
                if (broken || age >= most || (age >= least && !held)) {
                    stop[0] = age;
                    // The punching pose loops on its own: it stops with the shots.
                    if (caster.isAlive()) {
                        SubCommon.pose(caster, "");
                    }
                } else {
                    if (held) {
                        Charge.report(caster, age / (double) most);
                    }
                    SubCommon.busy(caster, 3);
                    if (age % 8 == 0) {
                        SubCommon.pose(caster, "soco.barragem");
                    }
                    caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 4, 2, false, false));
                    LivingEntity target = SubCommon.aimed(caster, 32);
                    Vec3 aim = target != null ? Combat.center(target) : Combat.raycastPoint(caster, 32);
                    if (age % 2 == 0) {
                        // One ball every 2 ticks, the hands taking turns, each with its own aim and speed; some miss and
                        // hole the ground round the target.
                        Vec3 from = (age / 2) % 2 == 0 ? DbCommon.hand(caster) : DbCommon.handLeft(caster);
                        boolean miss = SubCommon.RANDOM.nextFloat() < 0.22f;
                        double speed = 1.6 + SubCommon.RANDOM.nextDouble() * 0.6;
                        Vec3 to;
                        if (miss) {
                            Vec3 near = aim.add((SubCommon.RANDOM.nextDouble() - 0.5) * 3, 0, (SubCommon.RANDOM.nextDouble() - 0.5) * 2);
                            to = new Vec3(near.x, DbCommon.groundY(level, near.add(0, 2, 0)), near.z);
                        } else {
                            // Aimed where the target will be when the ball gets there (someone running still gets hit).
                            Vec3 lead = target != null ? target.getDeltaMovement().multiply(1, 0, 1).scale(from.distanceTo(aim) / speed) : Vec3.ZERO;
                            to = aim.add(lead).add((SubCommon.RANDOM.nextDouble() - 0.5) * 0.6, (SubCommon.RANDOM.nextDouble() - 0.5) * 0.9,
                                    (SubCommon.RANDOM.nextDouble() - 0.5) * 0.6);
                        }
                        renzokuBall(caster, level, from, to, speed, cover, covered);
                    }
                    if (age % 2 == 0) {
                        SubCommon.sound(level, caster.position(), SoundEvents.FIRECHARGE_USE, 0.6f, 1.6f + SubCommon.RANDOM.nextFloat() * 0.3f);
                    }
                    if (age % 10 == 0) {
                        DbCommon.screen(level, caster.position(), 4);
                    }
                }
            }
            // The smoke piles up round whoever is hit (blinding players) and slowly opens after it stops.
            boolean any = false;
            for (Map.Entry<Integer, LivingEntity> e : covered.entrySet()) {
                LivingEntity t = e.getValue();
                float k = Math.min(1, cover.getOrDefault(e.getKey(), 0f));
                if (stop[0] >= 0) {
                    k = Math.max(0, k - 1 / 40f);
                    cover.put(e.getKey(), k);
                }
                if (k > 0 && t.isAlive()) {
                    any = true;
                    if (k > 0.5f) {
                        t.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 25, 0, false, false));
                    }
                }
                SubNet.send(level, SubNet.SMOKE_COVER, t.getId(), t.position(), Vec3.ZERO, k, 0, k <= 0 || !t.isAlive() ? 1 : 0);
            }
            return stop[0] >= 0 && (!any || age - stop[0] > 60);
        });
        return true;
    }

    // How far round a Renzoku ball it touches (the ball and its glow).
    private static final double BALL_RADIUS = 0.45;

    // One Renzoku ball, flown by the server tick by tick along the same line the client draws: whoever it passes
    // through is hit right there (not only whoever stands where it was aimed), and wherever it ends it chips the ground.
    private static void renzokuBall(LivingEntity caster, ServerLevel level, Vec3 from, Vec3 to, double speed, Map<Integer, Float> cover,
            Map<Integer, LivingEntity> covered) {
        // It flies on past the aim point until it meets the ground (a miss doesn't stop in mid air).
        Vec3 dir = to.subtract(from);
        if (dir.lengthSqr() < 1.0E-4) {
            dir = caster.getLookAngle();
        }
        Vec3 far = to.add(dir.normalize().scale(6));
        Vec3 path = far.subtract(from);
        int flight = Math.max(2, (int) Math.round(path.length() / speed));
        int id = SubNet.newId();
        SubNet.send(level, SubNet.SHOT, id, from, path, flight, Db.GOLD, 0);
        Tasks.add(t -> {
            if (!caster.isAlive()) {
                return true;
            }
            Vec3 a = from.add(path.scale(t / (double) flight));
            Vec3 b = from.add(path.scale(Math.min(1, (t + 1) / (double) flight)));
            var block = level.clip(new net.minecraft.world.level.ClipContext(a, b, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, caster));
            boolean wall = block.getType() != net.minecraft.world.phys.HitResult.Type.MISS;
            Vec3 end = wall ? block.getLocation() : b;
            LivingEntity victim = null;
            double best = Double.MAX_VALUE;
            for (LivingEntity e : Combat.alongLine(level, a, end, BALL_RADIUS, caster)) {
                double d = Combat.center(e).distanceToSqr(a);
                if (d < best) {
                    best = d;
                    victim = e;
                }
            }
            if (victim != null) {
                // Bursts on the body, where the line passes closest to it.
                Vec3 seg = end.subtract(a);
                double len2 = Math.max(1.0E-6, seg.lengthSqr());
                double k = Math.max(0, Math.min(1, Combat.center(victim).subtract(a).dot(seg) / len2));
                Vec3 at = a.add(seg.scale(k));
                SubNet.send(level, SubNet.SHOT, id, at, Vec3.ZERO, 0, Db.GOLD, 1);
                Combat.hurtMagic(caster, victim, 0.9f);
                cover.merge(victim.getId(), 0.08f, Float::sum);
                covered.put(victim.getId(), victim);
                // The ground round whoever is hit takes the blast too (spread round the feet, never one deep shaft).
                Vec3 near = victim.position().add((SubCommon.RANDOM.nextDouble() - 0.5) * 2.4, 0.5, (SubCommon.RANDOM.nextDouble() - 0.5) * 2.4);
                double g = DbCommon.groundY(level, near);
                if (victim.getY() - g < 2.5) {
                    chip(level, new Vec3(near.x, g, near.z), new Vec3(0, -1, 0), from);
                }
                return true;
            }
            if (wall) {
                SubNet.send(level, SubNet.SHOT, id, end, Vec3.ZERO, 0, Db.GOLD, 1);
                chip(level, end, path, from);
                return true;
            }
            if (t + 1 >= flight) {
                double g = DbCommon.groundY(level, far);
                if (far.y - g < 1.5) {
                    chip(level, new Vec3(far.x, g, far.z), new Vec3(0, -1, 0), from);
                }
                return true;
            }
            return false;
        });
    }

    // A ball landing breaks at least the block it hit, and sometimes one more beside it.
    private static void chip(ServerLevel level, Vec3 at, Vec3 dir, Vec3 from) {
        Vec3 into = at.add(dir.normalize().scale(0.3));
        net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(into);
        if (level.getBlockState(pos).isAir()) {
            pos = net.minecraft.core.BlockPos.containing(at.add(0, -0.3, 0));
        }
        JjCommon.breakAt(level, pos, from, SubCommon.RANDOM.nextInt(3) == 0);
        if (SubCommon.RANDOM.nextFloat() < 0.45f) {
            net.minecraft.core.Direction side = net.minecraft.core.Direction.Plane.HORIZONTAL.getRandomDirection(level.random);
            JjCommon.breakAt(level, pos.relative(side), from, false);
        }
    }

    // ---------- X: Big Bang Attack ----------

    private static final double BB_RANGE = 48;
    // Blocks per tick (it used to crawl: 18 ticks whatever the distance).
    private static final double BB_SPEED = 2.4;
    private static final int BB_LIFE = 40;
    // The ball's own size (its hitbox) and the blast's radius (twice the old 3.2).
    private static final double BB_BALL = 0.9;
    private static final double BB_BOOM = 6.4;

    private static boolean bigBang(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        int id = SubNet.newId();
        int form = 32;
        SubCommon.pose(caster, "db.palma_dir");
        SubCommon.busy(caster, form + 6);
        caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, form, 3, false, false));
        SubNet.send(level, SubNet.KI_AURA, caster.getId(), caster.position(), new Vec3(0.6, 0, 0), form + 4, Db.GOLD, 1);
        SubCommon.sound(level, caster.position(), SoundEvents.BEACON_ACTIVATE, 1.4f, 1.2f);
        Vec3[] from = {null};
        Vec3[] dir = {null};
        Vec3[] pos = {null};
        LivingEntity[] prey = {null};
        Tasks.add(age -> {
            if (age < form && guard.broken()) {
                SubNet.send(level, SubNet.BIG_BANG, id, DbCommon.hand(caster), Vec3.ZERO, 0, 0, 1);
                SubNet.send(level, SubNet.KI_AURA, caster.getId(), caster.position(), Vec3.ZERO, 0, Db.GOLD, 1);
                return true;
            }
            if (age < form) {
                float k = SubCommon.smooth(age / (float) form);
                Vec3 p = DbCommon.hand(caster).add(caster.getLookAngle().scale(0.6 * k + 0.2));
                SubNet.send(level, SubNet.BIG_BANG, id, p, Vec3.ZERO, 0.1f + 0.6f * k, SubCommon.smooth(age / 4f), 0);
                if (age % 6 == 0) {
                    SubCommon.sound(level, p, SoundEvents.FIRE_AMBIENT, 1.4f, 0.7f + k);
                }
                return false;
            }
            if (age == form) {
                from[0] = DbCommon.hand(caster).add(caster.getLookAngle().scale(0.8));
                pos[0] = from[0];
                LivingEntity target = SubCommon.aimed(caster, BB_RANGE);
                if (target == null) {
                    target = nearestInFront(caster, level, BB_RANGE);
                }
                prey[0] = target;
                Vec3 goal = target != null ? Combat.center(target) : SubCommon.aimPoint(caster, BB_RANGE);
                dir[0] = goal.subtract(from[0]).normalize();
                SubCommon.sound(level, from[0], SoundEvents.WARDEN_SONIC_BOOM, 1.2f, 1.4f);
            }
            int t = age - form;
            // Homing: it turns after the target every tick (a tight turn, it doesn't orbit) and flies fast.
            LivingEntity target = prey[0];
            if (target != null && target.isAlive() && !target.isRemoved()) {
                Vec3 want = Combat.center(target).subtract(pos[0]);
                if (want.lengthSqr() > 1.0E-4) {
                    dir[0] = dir[0].lerp(want.normalize(), 0.45).normalize();
                }
            }
            Vec3 prev = pos[0];
            Vec3 next = prev.add(dir[0].scale(BB_SPEED));
            var block = level.clip(new net.minecraft.world.level.ClipContext(prev, next, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, caster));
            boolean wall = block.getType() != net.minecraft.world.phys.HitResult.Type.MISS;
            Vec3 p = wall ? block.getLocation() : next;
            boolean touch = wall || !Combat.alongLine(level, prev, p, BB_BALL, caster).isEmpty();
            if (touch) {
                // Bursts where it met the body, not past it.
                for (LivingEntity e : Combat.alongLine(level, prev, p, BB_BALL, caster)) {
                    Vec3 seg = p.subtract(prev);
                    double k = Math.max(0, Math.min(1, Combat.center(e).subtract(prev).dot(seg) / Math.max(1.0E-6, seg.lengthSqr())));
                    p = prev.add(seg.scale(k));
                    break;
                }
            }
            pos[0] = p;
            if (t < BB_LIFE && !touch) {
                // The comet's tail (the twisted beam leaving the hand behind it) is drawn by the client.
                SubNet.send(level, SubNet.BIG_BANG, id, p, Vec3.ZERO, 0.7f, 1, 0);
                return false;
            }
            SubNet.send(level, SubNet.BIG_BANG, id, p, Vec3.ZERO, 0.7f, 1, 1);
            double ground = DbCommon.groundY(level, p);
            SubNet.send(level, SubNet.BIG_BANG_BOOM, 0, p, new Vec3(ground, 0, 0), (float) BB_BOOM, 0, 0);
            DbCommon.screen(level, p, 6);
            SubCommon.sound(level, p, SoundEvents.GENERIC_EXPLODE.value(), 4f, 0.6f);
            SubCommon.sound(level, p, SoundEvents.BLAZE_SHOOT, 2.5f, 0.45f);
            DbCommon.hurtRadius(caster, level, p, BB_BOOM * 1.3, 24, 1.6, 0.55);
            if (p.y - ground < BB_BOOM) {
                JjCommon.crater(level, new Vec3(p.x, ground, p.z), BB_BOOM, BB_BOOM * 0.5, 40);
            }
            return true;
        });
        return true;
    }

    // The closest one in front within range (when the aim isn't on anyone, the Big Bang still finds someone).
    private static LivingEntity nearestInFront(LivingEntity caster, ServerLevel level, double range) {
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getLookAngle();
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : Combat.inRadius(level, eye, range, caster)) {
            Vec3 to = Combat.center(e).subtract(eye);
            double d = to.length();
            if (d < 1 || to.normalize().dot(look) < 0.85 || !caster.hasLineOfSight(e)) {
                continue;
            }
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    // ---------- C: Explosao Final ----------

    private static final int FINAL_CHARGE = 52;
    private static final int STONE_TICKS = 400;
    // Radius of the dome (it was 12), its big hit (95 before: a low-life Madara walked away) and the burning after it.
    private static final double FINAL_RADIUS = 20;
    private static final float FINAL_DAMAGE = 170;
    private static final float FINAL_BURN = 9;
    private static final int FINAL_BURNS = 10;
    private static final int FINAL_BURN_EVERY = 6;

    private static boolean explosaoFinal(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        Vec3 start = caster.position();
        SubCommon.pose(caster, "db.bracos_abertos");
        // The aura at its peak (strength 1: it bursts when lit), bigger than ever.
        SubNet.send(level, SubNet.KI_AURA, caster.getId(), start, new Vec3(1, 0, 0), FINAL_CHARGE + 6, Db.GOLD, 1.42f);
        SubCommon.sound(level, start, SoundEvents.BEACON_ACTIVATE, 2.5f, 0.6f);
        Runnable[] lift = {Freeze.hold(caster, FINAL_CHARGE + 2, a -> start.add(0, 0.9 * SubCommon.smooth(a / 48f), 0))};
        Tasks.add(age -> {
            if (age < FINAL_CHARGE) {
                if (guard.broken()) {
                    lift[0].run();
                    SubNet.send(level, SubNet.KI_AURA, caster.getId(), caster.position(), Vec3.ZERO, 0, Db.GOLD, 1);
                    return true;
                }
                SubCommon.busy(caster, 3);
                if (age % 10 == 0) {
                    SubCommon.sound(level, caster.position(), SoundEvents.BEACON_AMBIENT, 2.5f, 0.5f + age / 60f);
                    DbCommon.screen(level, caster.position(), 4);
                }
                return false;
            }
            lift[0].run();
            Vec3 c = Combat.center(caster);
            double ground = DbCommon.groundY(level, start.add(0, 1, 0));
            SubNet.send(level, SubNet.KI_AURA, caster.getId(), caster.position(), Vec3.ZERO, 0, Db.GOLD, 1);
            // The biggest blast of the mod (style 2000 in the client: golden dome with a white one inside).
            SubNet.send(level, SubNet.KI_BOOM, 0, c, new Vec3(ground, 0, 0), (float) (FINAL_RADIUS / 1.4), Db.GOLD, 2000);
            DbCommon.screen(level, c, 49);
            SubCommon.sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), 5f, 0.4f);
            SubCommon.sound(level, c, SoundEvents.WARDEN_SONIC_BOOM, 4f, 0.5f);
            Tasks.later(6, () -> SubCommon.sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), 4f, 0.5f));
            // The blast: everyone in the dome takes the big hit and is held in the fire (barely pushed) while it burns.
            for (LivingEntity e : Combat.inRadius(level, c, FINAL_RADIUS, caster)) {
                double k = 1 - 0.3 * Math.min(1, Combat.center(e).distanceTo(c) / FINAL_RADIUS);
                Combat.hurtMagic(caster, e, (float) (FINAL_DAMAGE * k));
                Combat.pushFrom(e, c, 0.4, 0.3);
            }
            // The dome keeps burning for 3 s (a repeated area hit: no knockback), then throws everyone still inside out.
            for (int i = 1; i <= FINAL_BURNS; i++) {
                int n = i;
                Tasks.later(i * FINAL_BURN_EVERY, () -> {
                    for (LivingEntity e : Combat.inRadius(level, c, FINAL_RADIUS, caster)) {
                        Combat.hurtMagic(caster, e, FINAL_BURN);
                        if (n == FINAL_BURNS) {
                            double k = 1 - 0.5 * Math.min(1, Combat.center(e).distanceTo(c) / FINAL_RADIUS);
                            Combat.pushFrom(e, c, 3.4 * k, 1.2);
                        }
                    }
                    if (n % 3 == 0) {
                        SubCommon.sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), 3f, 0.35f + 0.05f * n);
                    }
                });
            }
            // The crater opens with the dome: the inner bowl now, the full one as it reaches its size.
            Vec3 floor = new Vec3(start.x, ground, start.z);
            JjCommon.crater(level, floor, FINAL_RADIUS * 0.5, 5, 60);
            Tasks.later(8, () -> JjCommon.crater(level, floor, FINAL_RADIUS * 0.72, 7, 90));
            // The price: 1 heart and stone for 20 s. Nothing gets through the stone, and he can't move or use anything.
            caster.setHealth(Math.min(caster.getHealth(), 1));
            // He comes down into the bottom of his own crater and stays there.
            Vec3 top = caster.position();
            Vec3 rest = new Vec3(start.x, DbCommon.groundY(level, start.add(0, 1, 0)), start.z);
            Freeze.hold(caster, STONE_TICKS, a -> top.lerp(rest, SubCommon.smooth(a / 12f)));
            Intangible.hold(caster, STONE_TICKS);
            PowerLogic.seal(caster, STONE_TICKS);
            SubNet.send(level, SubNet.SCREEN, caster.getId(), caster.position(), Vec3.ZERO, 48, STONE_TICKS, 0);
            Tasks.later(STONE_TICKS, () -> {
                if (caster.isAlive()) {
                    SubCommon.sound(level, caster.position(), SoundEvents.STONE_BREAK, 1.6f, 0.7f);
                    SubCommon.sound(level, caster.position(), SoundEvents.DEEPSLATE_BREAK, 1.6f, 0.8f);
                }
            });
            return true;
        });
        return true;
    }

    // ---------- V: Final Flash ----------

    private static final float FLASH_BOOM = 8.6f;

    private static boolean finalFlash(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        int join = 80;
        int fire = 90;
        int end = 150;
        int right = SubNet.newId();
        int left = SubNet.newId();
        int beam = SubNet.newId();
        SubCommon.pose(caster, "db.bracos_abertos");
        SubNet.send(level, SubNet.KI_AURA, caster.getId(), caster.position(), new Vec3(0.45, 0, 0), fire, Db.GOLD, 1.3f);
        SubCommon.sound(level, caster.position(), SoundEvents.BEACON_ACTIVATE, 2f, 0.9f);
        Map<Integer, Integer> hitAt = new HashMap<>();
        Vec3[] start = {null};
        Tasks.add(age -> {
            if (guard.broken()) {
                SubNet.send(level, SubNet.KI_BALL, right, caster.position(), Vec3.ZERO, 0, Db.GOLD, 1);
                SubNet.send(level, SubNet.KI_BALL, left, caster.position(), Vec3.ZERO, 0, Db.GOLD, 1);
                SubNet.send(level, SubNet.KI_BEAM, beam, caster.position(), Vec3.ZERO, 0, Db.GOLD, 1);
                SubNet.send(level, SubNet.KI_AURA, caster.getId(), caster.position(), Vec3.ZERO, 0, Db.GOLD, 1);
                return true;
            }
            SubCommon.busy(caster, 3);
            if (age < fire) {
                // Charged with the arms open: still and open to hits.
                Freeze.holdLookFree(caster, 2);
                float k = SubCommon.smooth((age - 6) / 60f);
                Vec3 look = caster.getLookAngle();
                Vec3 side = SubCommon.flat(look).cross(new Vec3(0, 1, 0));
                Vec3 chest = caster.position().add(0, caster.getBbHeight() * 0.72, 0);
                if (age < join + 4) {
                    SubNet.send(level, SubNet.KI_BALL, right, chest.add(side.scale(0.95)), Vec3.ZERO, 0.1f + 0.18f * k, Db.GOLD, 0);
                    SubNet.send(level, SubNet.KI_BALL, left, chest.add(side.scale(-0.95)), Vec3.ZERO, 0.1f + 0.18f * k, Db.GOLD, 0);
                } else {
                    SubNet.send(level, SubNet.KI_BALL, left, chest, Vec3.ZERO, 0, Db.GOLD, 1);
                    SubNet.send(level, SubNet.KI_BALL, right, DbCommon.hands(caster).add(look.scale(0.3)), Vec3.ZERO, 0.6f, Db.GOLD, 0);
                }
                if (age == join) {
                    SubCommon.pose(caster, "db.empurra");
                }
                if (age % 15 == 0) {
                    SubCommon.sound(level, caster.position(), SoundEvents.BEACON_AMBIENT, 2f, 0.6f + k);
                }
                if (age % 7 == 0) {
                    DbCommon.screen(level, caster.position(), 4);
                }
                return false;
            }
            if (age == fire) {
                SubNet.send(level, SubNet.KI_BALL, right, caster.position(), Vec3.ZERO, 0, Db.GOLD, 1);
                DbCommon.screen(level, Combat.center(caster), 7);
                SubCommon.sound(level, caster.position(), SoundEvents.WARDEN_SONIC_BOOM, 3f, 0.6f);
                SubCommon.sound(level, caster.position(), SoundEvents.GENERIC_EXPLODE.value(), 2f, 0.6f);
                start[0] = caster.position();
            }
            int t = age - fire;
            Vec3 from = DbCommon.hands(caster).add(caster.getLookAngle().scale(0.3));
            Vec3 dir = caster.getLookAngle();
            Vec3 far = DbCommon.beamEnd(caster, level, from, dir, 40, 1.6, true);
            Vec3 to = from.add(far.subtract(from).scale(Math.min(1, t / 6.0)));
            boolean done = age >= end;
            SubNet.send(level, SubNet.KI_BEAM, beam, from, to.subtract(from), 2.6f + 0.2f * (float) Math.sin(age), Db.GOLD, done ? 1 : 0);
            // Goes through everything: everyone in the way, 3 blocks wide.
            DbCommon.hurtAlong(caster, level, from, to, 1.6, 2.2f, age, 4, hitAt, dir.scale(0.3));
            if (t == 2) {
                Vec3 flat = SubCommon.flat(dir);
                double ground = DbCommon.groundY(level, start[0].add(flat.scale(1)));
                SubNet.send(level, SubNet.SCAR, 0, new Vec3(start[0].x, ground, start[0].z).add(flat), flat.scale(Math.min(40, far.distanceTo(from))), 3, 140, 0);
            }
            if (t % 6 == 0) {
                SubCommon.sound(level, to, SoundEvents.FIREWORK_ROCKET_BLAST, 1.5f, 0.5f);
            }
            if (t % 2 == 0 && t > 2) {
                // It tears the ground up under it as it passes.
                Vec3 q = from.add(to.subtract(from).scale(0.3 + 0.7 * SubCommon.RANDOM.nextDouble()));
                double g = DbCommon.groundY(level, q);
                if (q.y - g < 3.5) {
                    JjCommon.crater(level, new Vec3(q.x, g, q.z), 1.4, 0.8, 2);
                }
            }
            if (done) {
                // The blast where the beam ends (twice the old 4.3): it hurts round it too, not only along the beam.
                DbCommon.boom(level, to, FLASH_BOOM, Db.GOLD, 8);
                DbCommon.hurtRadius(caster, level, to, FLASH_BOOM * 1.2, 14, 1.4, 0.5);
                double g = DbCommon.groundY(level, to);
                if (to.y - g < FLASH_BOOM) {
                    JjCommon.crater(level, new Vec3(to.x, g, to.z), FLASH_BOOM, FLASH_BOOM * 0.5, 50);
                }
            }
            return done;
        });
        return true;
    }
}
