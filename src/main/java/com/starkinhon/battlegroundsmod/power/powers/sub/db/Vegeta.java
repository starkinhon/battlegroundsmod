// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.power.powers.sub.db;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.starkinhon.battlegroundsmod.combat.clash.Clash;
import com.starkinhon.battlegroundsmod.combat.Destructible;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

// Vegeta (SubAlma of Dragon Ball, tier 6). Kit remade for the 1st update (Stark approved the preview 2026-11-27,
// forja/vegeta-kit/principe.js + galick.js, artifact 2mDGwhMXYRu5JgPduum8Eo: "É isso").
// Z Lucora Gun (the old Renzoku Energy Dan, only the name): held, he switches hands without stopping and fires dozens of
//   yellow ki shots; the target disappears into a cloud of blasts and smoke (it can't see).
// X Big Bang Attack: arm out, open palm: a big blue ball made of Zanka tongues of ki, with waves running over it; it
//   flies fast, homing on the target, and bursts into tongues that open and die torn (radius 6.4). Twice as fast as
//   before, charge and flight (Stark 2026-11-27).
// C Galick Gun: held, he turns sideways, the hands pulled back at the chest and the purple ki gathers between them; let
//   go and the purple beam leaves. Let go early (under 0.6 s) = a thin fast beam; charged (up to 2 s) = a thick one that
//   tears the ground, pushes the target and blows up at the end. It locks in the clash of powers.
// V Final Flash: arms open, the yellow ki gathers in both palms with the golden aura rising; hands together, a huge
//   yellow beam goes through everything and tears the ground up under it.
// B Galick Impact: the rush. Dash with afterimages, punch in the stomach, 4 quick punches, an uppercut that throws the
//   target ~10 blocks up, he vanishes and comes out above it, a two-handed hammer drives it FORWARD into the ground
//   (crater), and the palm aimed at the crater closes it with a point-blank ki ball. A guard up at the start parries the
//   first punch and breaks the rush.
// Shift+V Explosao Final (the strongest attack of the mod): he rises with the golden aura at its peak and blows up: a
//   golden dome of radius 35 with a white one inside that burns for 3 s, a deep crater, everyone round thrown at the end.
//   The price: a PLAYER dies in it, turned to stone that crumbles (Stark 2026-11-20: "pode matar o usuario", like
//   Vegeta's own in the manga); a boss is left with 1 heart and stone for 20 s (still, no skills, nothing gets through).
// Passive Zenkai: once he survives a blow that leaves him under 20% of his life, his blows deal 15% more until he dies.
public final class Vegeta {
    private Vegeta() {
    }

    // Who has the Zenkai, by entity id: a respawned player is a new entity (new id), so it ends with death on its own.
    private static final java.util.Set<Integer> ZENKAI = new java.util.HashSet<>();
    private static final float ZENKAI_BELOW = 0.2f;
    private static final float ZENKAI_BONUS = 1.15f;

    public static Power create() {
        return new Power("vegeta", 0xFFD84A, 1.1f, List.of(
                new Ability(140, 0, Vegeta::rajada),
                new Ability(200, 0, Vegeta::bigBang),
                new Ability(280, 0, Vegeta::galickGun),
                new Ability(1000, 0, Vegeta::finalFlash),
                new Ability(440, 0, Vegeta::galickImpact)),
                java.util.Arrays.asList(null, null, null, new Ability(2400, 0, Vegeta::explosaoFinal)));
    }

    // ---------- Passive: Zenkai ----------

    // Every hit (DbCommon.onIncomingDamage): a Vegeta with the Zenkai hits 15% harder; a Vegeta hit down under 20% of his
    // life who is still standing a tick later wakes it.
    public static void onIncomingDamage(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (event.getSource().getEntity() instanceof LivingEntity attacker && attacker != target && ZENKAI.contains(attacker.getId())
                && isVegeta(attacker)) {
            event.setAmount(event.getAmount() * ZENKAI_BONUS);
        }
        if (!isVegeta(target) || ZENKAI.contains(target.getId()) || !(target.level() instanceof ServerLevel level)) {
            return;
        }
        float after = target.getHealth() - event.getAmount();
        if (after > target.getMaxHealth() * ZENKAI_BELOW) {
            return;
        }
        Tasks.later(1, () -> {
            if (!target.isAlive() || !isVegeta(target) || target.getHealth() > target.getMaxHealth() * ZENKAI_BELOW
                    || ZENKAI.contains(target.getId())) {
                return;
            }
            ZENKAI.add(target.getId());
            // The Saiyan comes back stronger: a short golden flare and the word on the action bar.
            SubNet.send(level, SubNet.KI_AURA, target.getId(), target.position(), new Vec3(1, 0, 0), 30, Db.GOLD, 1.2f);
            DbCommon.hit(level, Combat.center(target), 1.2f, Db.GOLD);
            SubCommon.sound(level, target.position(), SoundEvents.BLAZE_SHOOT, 1.2f, 0.6f);
            SubCommon.warn(target, "vegeta.zenkai");
        });
    }

    // The world closed (ServerReset): entity ids start over.
    public static void resetStatics() {
        ZENKAI.clear();
    }

    private static boolean isVegeta(LivingEntity entity) {
        return "vegeta".equals(entity.getData(com.starkinhon.battlegroundsmod.ModAttachments.POWER).power);
    }

    // ---------- Z: Lucora Gun (Renzoku Energy Dan) ----------

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
    // Blocks per tick (it used to crawl: 18 ticks whatever the distance); twice the 2.4 since the 1st update (Stark
    // 2026-11-27, the preview's 32 blocks a second doubled).
    private static final double BB_SPEED = 4.8;
    private static final int BB_LIFE = 40;
    // The ball's own size (its hitbox) and the blast's radius (twice the old 3.2).
    private static final double BB_BALL = 0.9;
    private static final double BB_BOOM = 6.4;

    private static boolean bigBang(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        int id = SubNet.newId();
        // The charge, twice as fast as the old 32 ticks (Stark 2026-11-27).
        int form = 16;
        SubCommon.pose(caster, "db.palma_dir");
        SubCommon.busy(caster, form + 6);
        caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, form, 3, false, false));
        SubNet.send(level, SubNet.KI_AURA, caster.getId(), caster.position(), new Vec3(0.6, 0, 0), form + 4, Db.PURPLE, 1);
        SubCommon.sound(level, caster.position(), SoundEvents.BEACON_ACTIVATE, 1.4f, 1.2f);
        Vec3[] from = {null};
        Vec3[] dir = {null};
        Vec3[] pos = {null};
        LivingEntity[] prey = {null};
        Destructible.Attack[] ball = {null};
        Tasks.add(age -> {
            if (age < form && guard.broken()) {
                SubNet.send(level, SubNet.BIG_BANG, id, DbCommon.hand(caster), Vec3.ZERO, 0, 0, 1);
                SubNet.send(level, SubNet.KI_AURA, caster.getId(), caster.position(), Vec3.ZERO, 0, Db.GOLD, 1);
                return true;
            }
            if (age < form) {
                float k = SubCommon.smooth(age / (float) form);
                Vec3 p = DbCommon.hand(caster).add(caster.getLookAngle().scale(0.6 * k + 0.2));
                SubNet.send(level, SubNet.BIG_BANG, id, p, Vec3.ZERO, 0.1f + 0.6f * k, SubCommon.smooth(age / 2f), 0);
                if (age % 3 == 0) {
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
                // Clash de poderes: thrown, it can lock with a beam or a ball coming the other way.
                ball[0] = Destructible.trackClash(level, caster, from[0], BB_BALL,
                        () -> SubNet.send(level, SubNet.BIG_BANG, id, ball[0].pos(), Vec3.ZERO, 0.7f, 1, 1)).clash(1.1f, 0x6FA8FF, 14f)
                        .clashDraw(p -> SubNet.send(level, SubNet.BIG_BANG, id, p, Vec3.ZERO, 0.7f, 1, 0));
                SubCommon.sound(level, from[0], SoundEvents.WARDEN_SONIC_BOOM, 1.2f, 1.4f);
            }
            int t = age - form;
            if (ball[0] != null && ball[0].gone()) {
                // Taken by a clash (it made the blast): nothing more here.
                return true;
            }
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
            if (ball[0] != null) {
                ball[0].move(p);
            }
            if (t < BB_LIFE && !touch) {
                // The comet's tail (the twisted beam leaving the hand behind it) is drawn by the client.
                SubNet.send(level, SubNet.BIG_BANG, id, p, Vec3.ZERO, 0.7f, 1, 0);
                return false;
            }
            if (ball[0] != null) {
                ball[0].end();
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

    // ---------- Shift+V: Explosao Final ----------

    private static final int FINAL_CHARGE = 52;
    private static final int STONE_TICKS = 400;
    // A player's stone before it crumbles and he dies (the sacrifice).
    private static final int SACRIFICE_TICKS = 40;
    // Radius of the dome (12, then 20, 35 since the 1st update: Stark 2026-11-27), its big hit (95 before: a low-life
    // Madara walked away) and the burning after it.
    private static final double FINAL_RADIUS = 35;
    private static final float FINAL_DAMAGE = 170;
    private static final float FINAL_BURN = 9;
    private static final int FINAL_BURNS = 10;
    private static final int FINAL_BURN_EVERY = 6;

    private static boolean explosaoFinal(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        Vec3 start = caster.position();
        SubCommon.pose(caster, "db.bracos_abertos");
        // The aura at its peak (strength 1: it bursts when lit), bigger than ever.
        SubNet.send(level, SubNet.KI_AURA, caster.getId(), start, new Vec3(1, 0, 0), FINAL_CHARGE + 6, Db.PURPLE, 1.42f);
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
            // At radius 35 the bowl is thousands of blocks: the inner one now, then the full one ring by ring (DbCommon.bigCrater
            // spreads it over ticks, so the server never breaks it all at once).
            Vec3 floor = new Vec3(start.x, ground, start.z);
            JjCommon.crater(level, floor, 8, 5, 60);
            Tasks.later(4, () -> DbCommon.bigCrater(level, floor, FINAL_RADIUS * 0.72, 8, 24));
            // The price: 1 heart and stone. Nothing gets through the stone, and he can't move or use anything. A player dies
            // when the stone crumbles (2 s); a boss stays 20 s in it, then the stone bursts.
            caster.setHealth(Math.min(caster.getHealth(), 1));
            boolean sacrifice = caster instanceof Player;
            int stoneTicks = sacrifice ? SACRIFICE_TICKS : STONE_TICKS;
            // He comes down into the bottom of his own crater and stays there.
            Vec3 top = caster.position();
            Vec3 rest = new Vec3(start.x, DbCommon.groundY(level, start.add(0, 1, 0)), start.z);
            Freeze.hold(caster, stoneTicks, a -> top.lerp(rest, SubCommon.smooth(a / 12f)));
            Intangible.hold(caster, stoneTicks);
            PowerLogic.seal(caster, stoneTicks);
            SubNet.send(level, SubNet.SCREEN, caster.getId(), caster.position(), Vec3.ZERO, 48, stoneTicks, 0);
            Tasks.later(stoneTicks, () -> {
                if (caster.isAlive()) {
                    SubCommon.sound(level, caster.position(), SoundEvents.STONE_BREAK, 1.6f, 0.7f);
                    SubCommon.sound(level, caster.position(), SoundEvents.DEEPSLATE_BREAK, 1.6f, 0.8f);
                }
            });
            if (sacrifice) {
                // One tick after the stone (and its intangibility) is gone, so nothing of the mod holds the blow back.
                Tasks.later(stoneTicks + 1, () -> {
                    if (caster.isAlive()) {
                        caster.invulnerableTime = 0;
                        caster.hurt(level.damageSources().genericKill(), Float.MAX_VALUE);
                    }
                });
            }
            return true;
        });
        return true;
    }

    // ---------- C: Galick Gun ----------

    // Let go before this (0.6 s) = the quick thin beam; held to GALICK_FULL (2 s) = fully charged.
    private static final int GALICK_QUICK = 12;
    private static final int GALICK_FULL = 40;

    private static boolean galickGun(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        int ball = SubNet.newId();
        int beam = SubNet.newId();
        int bossCharge = 6 + SubCommon.RANDOM.nextInt(GALICK_FULL);
        // Sideways, the two hands pulled back at the chest (the cupped pose) while the purple ki gathers between them.
        SubCommon.pose(caster, "db.concha");
        // the charge's weak aura (0.45, AuraFx grows it in like the preview's Galick, no burst)
        SubNet.send(level, SubNet.KI_AURA, caster.getId(), caster.position(), new Vec3(0.45, 0, 0), GALICK_FULL + 10, Db.PURPLE, 1);
        SubCommon.sound(level, caster.position(), SoundEvents.BEACON_ACTIVATE, 1.2f, 1.3f);
        int[] fired = {-1};
        float[] charge = {0};
        boolean[] big = {false};
        Map<Integer, Integer> hitAt = new HashMap<>();
        // the one the beam caught first: the charged beam drags it back along itself and blows up on it (the preview)
        LivingEntity[] caught = {null};
        Tasks.add(age -> {
            if (guard.broken()) {
                SubNet.send(level, SubNet.KI_BALL, ball, DbCommon.hands(caster), Vec3.ZERO, 0, Db.PURPLE, 1);
                SubNet.send(level, SubNet.KI_BEAM, beam, DbCommon.hands(caster), Vec3.ZERO, 0, Db.PURPLE, 1);
                SubNet.send(level, SubNet.KI_AURA, caster.getId(), caster.position(), Vec3.ZERO, 0, Db.PURPLE, 1);
                return true;
            }
            SubCommon.busy(caster, 3);
            Vec3 hm = DbCommon.hands(caster);
            if (fired[0] < 0) {
                float k = Math.min(1, age / (float) GALICK_FULL);
                charge[0] = k;
                boolean held = SubCommon.held(caster, age, bossCharge);
                if (held) {
                    Charge.report(caster, k);
                }
                // He walks slowly while it charges.
                caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 4, 2, false, false));
                // the purple ki between the palms: seen even on a quick one (the preview's 0.5 s quick charge)
                SubNet.send(level, SubNet.KI_BALL, ball, hm, Vec3.ZERO, 0.26f + 0.3f * SubCommon.smooth(k) + 0.03f * (float) Math.sin(age * 1.6), Db.PURPLE + 16 * 5, 0);
                if (age % 8 == 0) {
                    SubCommon.sound(level, hm, SoundEvents.BEACON_AMBIENT, 1f, 0.9f + k);
                }
                if (age % 10 == 0 && k > 0.4f) {
                    DbCommon.screen(level, hm, 4);
                }
                if ((held || age < 9) && age < GALICK_FULL + 20) {
                    return false;
                }
                // GALICK... GUN! He turns back to the target pushing the hands forward.
                fired[0] = age;
                big[0] = age >= GALICK_QUICK + 12;
                SubNet.send(level, SubNet.KI_BALL, ball, hm, Vec3.ZERO, 0, Db.PURPLE, 1);
                SubNet.send(level, SubNet.KI_AURA, caster.getId(), caster.position(), Vec3.ZERO, 0, Db.PURPLE, 1);
                SubCommon.pose(caster, "db.empurra");
                DbCommon.screen(level, hm, big[0] ? 9 : 4);
                SubCommon.sound(level, hm, SoundEvents.WARDEN_SONIC_BOOM, big[0] ? 1.6f : 1f, big[0] ? 1.1f : 1.6f);
                SubCommon.sound(level, hm, SoundEvents.BEACON_POWER_SELECT, 1.3f, 1.2f);
            }
            int t = age - fired[0];
            // The quick one: thin and fast (0.7 s). The charged one: thick, longer (1.5 s), grows with the charge.
            float thick = big[0] ? 0.9f + 0.5f * charge[0] : 0.45f;
            int life = big[0] ? 30 : 14;
            boolean end = t >= life;
            Vec3 dir = DbCommon.aim(caster);
            double width = thick * 0.6 + 0.4;
            Vec3 reachEnd = DbCommon.beamEnd(caster, level, hm, dir, big[0] ? 40 : 34, width, false);
            if (caught[0] == null || !caught[0].isAlive() || caught[0].isRemoved()) {
                caught[0] = null;
                double best = Double.MAX_VALUE;
                for (LivingEntity e : Combat.alongLine(level, hm, reachEnd.add(dir.scale(1.5)), width, caster)) {
                    double d = Combat.center(e).distanceTo(hm);
                    if (d < best) {
                        best = d;
                        caught[0] = e;
                    }
                }
            }
            if (caught[0] != null && t >= (big[0] ? 4 : 2)) {
                // Held in the beam and pushed back along it (charged 3 blocks a second, quick 1.2), the beam ending on it.
                LivingEntity e = caught[0];
                Vec3 push = SubCommon.flat(dir).scale(big[0] ? 0.26 : 0.08);
                e.setDeltaMovement(push.x, Math.max(e.getDeltaMovement().y, big[0] ? 0.02 : -0.08), push.z);
                e.hurtMarked = true;
                reachEnd = hm.add(dir.scale(Math.max(1, Combat.center(e).distanceTo(hm))));
            }
            Vec3 to = hm.add(reachEnd.subtract(hm).scale(Math.min(1, t / (big[0] ? 4.0 : 2.0))));
            if (Clash.done(beam)) {
                SubNet.send(level, SubNet.KI_BEAM, beam, hm, Vec3.ZERO, 0, Db.PURPLE, 1);
                return true;
            }
            to = Clash.beam(caster, level, beam, hm, to, width, big[0] ? 1.2f : 0.9f, 0xB45CFF, big[0] ? 8 + 10 * charge[0] : 6);
            if (Clash.locked(beam)) {
                end = false;
            }
            SubNet.send(level, SubNet.KI_BEAM, beam, hm, to.subtract(hm), thick + (big[0] ? 0.05f * (float) Math.sin(age * 0.9) : 0), Db.PURPLE,
                    end ? 1 : 0);
            // The charged one pushes whoever it holds (it drags them back along the beam).
            float damage = big[0] ? 1.6f + 1.2f * charge[0] : 2f;
            DbCommon.hurtAlong(caster, level, hm, to, width, damage, age, 3, hitAt, dir.scale(big[0] ? 0.55 : 0.25));
            if (big[0] && t % 2 == 0 && t > 2) {
                // It tears the ground under it.
                Vec3 q = hm.add(to.subtract(hm).scale(0.3 + 0.7 * SubCommon.RANDOM.nextDouble()));
                double g = DbCommon.groundY(level, q);
                if (q.y - g < 3) {
                    JjCommon.crater(level, new Vec3(q.x, g, q.z), 1.1, 0.6, 2);
                }
            }
            if (t % 7 == 0) {
                SubCommon.sound(level, to, SoundEvents.FIREWORK_ROCKET_BLAST, 1.1f, big[0] ? 0.6f : 0.9f);
            }
            if (end) {
                Clash.forget(beam);
                // The end of the charged one blows up (radius 4.4) on the one it held; the quick one only pops.
                float blast = big[0] ? 4.4f : 1.8f;
                if (caught[0] != null && caught[0].isAlive()) {
                    to = Combat.center(caught[0]);
                }
                DbCommon.boom(level, to, blast, Db.PURPLE, 5);
                double ground = DbCommon.groundY(level, to);
                if (to.y - ground < blast) {
                    JjCommon.crater(level, new Vec3(to.x, ground, to.z), big[0] ? 3.2 : 1.4, big[0] ? 1.6 : 0.6, big[0] ? 24 : 4);
                }
                DbCommon.hurtRadius(caster, level, to, blast, big[0] ? 6 + 6 * charge[0] : 2, big[0] ? 1.4 : 0.6, big[0] ? 0.55 : 0.25);
                if (big[0]) {
                    DbCommon.screen(level, to, 6);
                }
                return true;
            }
            return false;
        });
        return true;
    }

    // ---------- B: Galick Impact ----------

    private static final double IMPACT_RANGE = 12;

    // The rush, frame by frame as in the preview (ticks): 0-3 dash, 4 stomach, 10/12/14/16 the four punches, 19 uppercut,
    // 29 he vanishes, 32 he is above it, 35 hammer, 40 it lands in the crater, 41-50 the palm aimed at it, 51-53 the ball
    // flies, 53 the point-blank blast.
    private static final int IP_STOMACH = 4;
    private static final int[] IP_PUNCHES = {10, 12, 14, 16};
    private static final int IP_UPPER = 19;
    private static final int IP_VANISH = 29;
    private static final int IP_ABOVE = 32;
    private static final int IP_HAMMER = 35;
    private static final int IP_LAND = 40;
    private static final int IP_THROW = 51;
    private static final int IP_BOOM = 54;
    private static final int IP_END = 58;

    private static boolean galickImpact(LivingEntity caster, ServerLevel level) {
        LivingEntity found = SubCommon.aimed(caster, IMPACT_RANGE);
        if (found == null) {
            found = nearestInFront(caster, level, IMPACT_RANGE);
        }
        if (found == null) {
            SubCommon.warn(caster, "vegeta.impact_no_target", (int) IMPACT_RANGE);
            return false;
        }
        LivingEntity target = found;
        SubCommon.Guard guard = SubCommon.guard(caster);
        int ballId = SubNet.newId();
        Vec3 start = caster.position();
        Vec3 t0 = target.position();
        Vec3 fwd0 = SubCommon.flat(t0.subtract(start));
        Vec3 fwd = fwd0.lengthSqr() < 1.0E-4 ? SubCommon.flat(caster.getLookAngle()) : fwd0.normalize();
        // Where everything happens, worked out as it goes: the target's spot (pushed back by the punches, thrown up by the
        // uppercut, driven into the ground by the hammer) and his own beside it.
        Vec3[] tp = {t0};
        Vec3[] cp = {start};
        Vec3[] apex = {null};
        Vec3[] land = {null};
        Vec3[] hammerFrom = {null};
        Vec3[] above = {null};
        Vec3[] ballFrom = {null};
        Runnable[] holds = new Runnable[2];
        boolean[] over = {false};
        SubCommon.busy(caster, IP_END + 2);
        SubCommon.pose(caster, "soco.dash");
        SubCommon.sound(level, start, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1f, 1.6f);
        Vec3 front = t0.subtract(fwd.scale(1.1));
        // The dash: blue copies of him left on the way (the preview's afterimages).
        SubNet.send(level, SubNet.AFTERIMAGE, caster.getId(), start, front.subtract(start), caster.getBbHeight(), 2, 0);
        holds[0] = Freeze.holdLookFree(caster, IP_END, a -> cp[0]);
        Tasks.add(age -> {
            if (over[0]) {
                return true;
            }
            if (guard.broken() || !caster.isAlive() || !target.isAlive() || target.isRemoved()) {
                for (Runnable r : holds) {
                    if (r != null) {
                        r.run();
                    }
                }
                SubNet.send(level, SubNet.KI_BALL, ballId, cp[0], Vec3.ZERO, 0, Db.GOLD, 1);
                return true;
            }
            SubCommon.busy(caster, 3);
            if (age <= 3) {
                float k = SubCommon.smooth(age / 3f);
                cp[0] = start.lerp(front, k).add(0, 0.25 * Math.sin(k * Math.PI), 0);
                face(caster, target.position());
                return false;
            }
            if (age == IP_STOMACH) {
                // A guard up from the front parries the first punch: the rush breaks (blockGrab stuns him).
                if (com.starkinhon.battlegroundsmod.combat.Defense.blockGrab(target, caster)) {
                    holds[0].run();
                    return true;
                }
                holds[1] = Freeze.hold(target, IP_END - IP_STOMACH, a -> tp[0]);
                com.starkinhon.battlegroundsmod.combat.Stun.apply(target, IP_END - IP_STOMACH + 25);
                SubCommon.pose(caster, "soco.forte_golpe");
                blow(caster, level, target, 4, 0.9f, 12);
            }
            if (age > IP_STOMACH && age <= IP_STOMACH + 3) {
                // Doubled over by the punch, pushed back (it never topples onto him).
                tp[0] = t0.add(fwd.scale(0.6 * SubCommon.smooth((age - IP_STOMACH) / 3f)));
            }
            for (int i = 0; i < IP_PUNCHES.length; i++) {
                if (age == IP_PUNCHES[i]) {
                    SubCommon.pose(caster, i % 2 == 0 ? "soco.combo1" : "soco.combo2");
                    blow(caster, level, target, 2.5f, 0.55f, i % 2 == 0 ? 15 : 8);
                }
                if (age > IP_PUNCHES[i] && age <= IP_PUNCHES[i] + 2) {
                    tp[0] = tp[0].add(fwd.scale(0.18));
                }
            }
            if (age >= IP_STOMACH && age < IP_UPPER + 2) {
                // He keeps right in front of it while the punches push it back.
                cp[0] = tp[0].subtract(fwd.scale(1.05));
                face(caster, tp[0]);
            }
            if (age == IP_UPPER) {
                SubCommon.pose(caster, "jj.uppercut");
                blow(caster, level, target, 5, 1f, 14);
                SubCommon.sound(level, tp[0], SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.6f, 0.7f);
                apex[0] = tp[0].add(fwd.scale(1.2)).add(0, 10, 0);
                hammerFrom[0] = tp[0];
            }
            if (age > IP_UPPER && age <= IP_HAMMER) {
                // Thrown ~10 blocks up, slowing near the top.
                float k = SubCommon.easeOutCubic(Math.min(1, (age - IP_UPPER) / 13f));
                Vec3 up = hammerFrom[0].lerp(apex[0], k);
                tp[0] = SubCommon.solid(level, up.add(0, 1.8, 0)) ? tp[0] : up;
            }
            if (age == IP_VANISH) {
                SubNet.send(level, SubNet.VANISH, 0, cp[0].add(0, 1, 0), Vec3.ZERO, 0, 0, 0);
                SubCommon.sound(level, cp[0], SoundEvents.ILLUSIONER_MIRROR_MOVE, 1f, 1.8f);
            }
            if (age == IP_ABOVE) {
                // Out of nowhere above it, a little behind (on his own side): the hammer goes FORWARD.
                above[0] = tp[0].subtract(fwd.scale(1.0)).add(0, 2.2, 0);
                SubNet.send(level, SubNet.AFTERIMAGE, caster.getId(), cp[0], above[0].subtract(cp[0]), caster.getBbHeight(), 2, 0);
                cp[0] = above[0];
                SubNet.send(level, SubNet.VANISH, 0, cp[0].add(0, 1, 0), Vec3.ZERO, 0, 0, 0);
                SubCommon.pose(caster, "voo.martelo");
            }
            if (age > IP_ABOVE && age < IP_HAMMER) {
                cp[0] = tp[0].subtract(fwd.scale(1.0)).add(0, 2.2, 0);
                face(caster, tp[0]);
            }
            if (age == IP_HAMMER) {
                blow(caster, level, target, 6, 1.3f, 14);
                // Driven down FORWARD on a diagonal, far from his feet.
                Vec3 goal = tp[0].add(fwd.scale(Math.max(3, (tp[0].y - t0.y) * 0.8)));
                land[0] = new Vec3(goal.x, DbCommon.groundY(level, goal.add(0, 2, 0)), goal.z);
                hammerFrom[0] = tp[0];
                above[0] = cp[0];
            }
            if (age > IP_HAMMER && age <= IP_LAND) {
                float k = (age - IP_HAMMER) / (float) (IP_LAND - IP_HAMMER);
                tp[0] = hammerFrom[0].lerp(land[0], k * k);
                // He stays where he struck, coming down to 3.4 blocks over the crater.
                cp[0] = new Vec3(above[0].x, Math.max(land[0].y + 3.4, above[0].y - 0.7 * (age - IP_HAMMER)), above[0].z);
                face(caster, tp[0]);
            }
            if (age == IP_LAND) {
                JjCommon.crater(level, land[0], 2.4, 1.4, 14);
                DbCommon.boom(level, land[0].add(0, 0.6, 0), 1.8f, Db.GOLD_W, 3);
                DbCommon.screen(level, land[0], 10);
                SubCommon.sound(level, land[0], SoundEvents.GENERIC_EXPLODE.value(), 1.6f, 0.8f);
                SubCommon.sound(level, land[0], SoundEvents.ANVIL_LAND, 1.2f, 0.6f);
                SubCommon.pose(caster, "db.palma_dir");
            }
            if (age > IP_LAND && age < IP_THROW) {
                // The palm aimed at the crater: the yellow ball swells in it.
                face(caster, tp[0]);
                float k = SubCommon.smooth((age - IP_LAND) / 9f);
                ballFrom[0] = DbCommon.hand(caster).add(tp[0].add(0, 0.4, 0).subtract(DbCommon.hand(caster)).normalize().scale(0.5));
                SubNet.send(level, SubNet.KI_BALL, ballId, ballFrom[0], Vec3.ZERO, 0.1f + 0.3f * k, Db.GOLD, 0);
                if (age == IP_LAND + 3) {
                    SubCommon.sound(level, ballFrom[0], SoundEvents.BEACON_ACTIVATE, 1.2f, 1.6f);
                }
            }
            if (age >= IP_THROW && age < IP_BOOM) {
                float k = (age - IP_THROW + 1) / (float) (IP_BOOM - IP_THROW);
                SubNet.send(level, SubNet.KI_BALL, ballId, ballFrom[0].lerp(tp[0].add(0, 0.4, 0), k), Vec3.ZERO, 0.4f, Db.GOLD, 0);
            }
            if (age == IP_BOOM) {
                Vec3 at = tp[0].add(0, 0.4, 0);
                SubNet.send(level, SubNet.KI_BALL, ballId, at, Vec3.ZERO, 0, Db.GOLD, 1);
                DbCommon.boom(level, at, 3.4f, Db.GOLD, 9);
                DbCommon.screen(level, at, 14);
                SubCommon.sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 3f, 0.7f);
                Combat.hurtMagic(caster, target, 9);
                for (LivingEntity e : Combat.inRadius(level, at, 3.4, caster)) {
                    if (e != target) {
                        Combat.hurtMagic(caster, e, 5);
                        Combat.pushFrom(e, at, 1, 0.5);
                    }
                }
                JjCommon.crater(level, land[0], 3.4, 2.2, 24);
            }
            if (age >= IP_END) {
                over[0] = true;
                for (Runnable r : holds) {
                    if (r != null) {
                        r.run();
                    }
                }
                // Down from the air without hurting himself.
                caster.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 30, 0, false, false));
                SubCommon.pose(caster, "");
                return true;
            }
            return false;
        });
        return true;
    }

    // One blow of the rush: damage, the stop-frame flash, its own little shockwave and the sound.
    private static void blow(LivingEntity caster, ServerLevel level, LivingEntity target, float damage, float size, int preset) {
        Combat.hurt(caster, target, damage);
        Vec3 c = Combat.center(target);
        DbCommon.hit(level, c, size, Db.GOLD);
        DbCommon.screen(level, c, preset);
        SubCommon.sound(level, c, size > 1 ? SoundEvents.PLAYER_ATTACK_CRIT : SoundEvents.PLAYER_ATTACK_STRONG, 1.2f, 0.8f + 0.3f * SubCommon.RANDOM.nextFloat());
    }

    private static void face(LivingEntity caster, Vec3 at) {
        Vec3 d = at.subtract(caster.position());
        float yaw = (float) (Math.atan2(-d.x, d.z) * 180 / Math.PI);
        caster.setYRot(yaw);
        caster.setYHeadRot(yaw);
        caster.yBodyRot = yaw;
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
        SubNet.send(level, SubNet.KI_AURA, caster.getId(), caster.position(), new Vec3(0.45, 0, 0), fire, Db.PURPLE, 1.3f);
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
            // Clash de poderes: a beam coming the other way (or a ball) locks it into a clash point
            if (Clash.done(beam)) {
                SubNet.send(level, SubNet.KI_BEAM, beam, from, Vec3.ZERO, 0, Db.GOLD, 1);
                return true;
            }
            to = Clash.beam(caster, level, beam, from, to, 1.6, 1.3f, 0xFFE066, 14f);
            if (Clash.locked(beam)) {
                done = false;
            }
            SubNet.send(level, SubNet.KI_BEAM, beam, from, to.subtract(from), 2.6f + 0.2f * (float) Math.sin(age), Db.GOLD, done ? 1 : 0);
            // Goes through everything: everyone in the way, 3 blocks wide.
            DbCommon.hurtAlong(caster, level, from, to, 1.6, 2.2f, age, 4, hitAt, dir.scale(0.3));
            // The furrow only when fired from the ground (flying, it was drawn floating in the air: Stark 2026-11-21).
            if (t == 2 && SubCommon.aboveGround(level, start[0], 3) < 3) {
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
                Clash.forget(beam);
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
