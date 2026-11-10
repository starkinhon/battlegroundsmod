package com.starkinhon.soulsjjk.power.powers.sukuna;

import com.starkinhon.soulsjjk.compat.Att;

import java.util.HashSet;
import java.util.Set;

import com.starkinhon.soulsjjk.ModAttachments;
import com.starkinhon.soulsjjk.combat.Combat;
import com.starkinhon.soulsjjk.combat.Freeze;
import com.starkinhon.soulsjjk.combat.Fx;
import com.starkinhon.soulsjjk.combat.Stun;
import com.starkinhon.soulsjjk.combat.SwordArts;
import com.starkinhon.soulsjjk.combat.Tasks;
import com.starkinhon.soulsjjk.network.ModPayloads;
import com.starkinhon.soulsjjk.power.PowerData;
import com.starkinhon.soulsjjk.power.powers.Sukuna;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.starkinhon.soulsjjk.compat.PacketDistributor;

// Sukuna's own base moves (Stark's version, approved on 2026-11-09 in the 3D preview forja/sukuna-combo): before each
// punch an open hand slashes the air and a Dismantle leaves it halfway through the slash, flies through whoever is in
// front, a little past them, and dissolves like Kokushibo's moons; then the punch lands. The punches themselves are
// the base moves', untouched (combat/Melee, combat/DashAttack call in here).
//   - Combo: each hit gets RAISE ticks more for the slash (left hand from above, right hand flat, left hand rising, right
//     backhand out, right hand straight down from above), poses sukuna.combo1..5.
//   - Forte: the right fist stays cocked while the left hand throws a big cut, then the fist lands (sukuna.forte_golpe).
//   - Barragem: after the finisher, with the right fist still out, the left hand throws one more big cut that catches the
//     one thrown away in the air (sukuna.barragem_final).
//   - Dash Attack: the left hand slashes flat on the way out; the cut gets there first (sukuna.dash).
// The cuts do damage only: no stun and no knockback of their own, so they never lock anyone (the punches still do what
// they always did). Poses and the hand's place and slash below come from tools/sukuna/bake_combo.js.
public final class SukunaMelee {
    // Ticks added to each combo hit for the slash before the punch.
    public static final int RAISE = 3;
    public static final String FORTE_POSE = "sukuna.forte_golpe";
    public static final String BARRAGE_END_POSE = "sukuna.barragem_final";
    public static final String DASH_POSE = "sukuna.dash";
    // Damage of a cut, against the punch it goes with (combo), or flat at Tier 0 scale (the others).
    private static final float COMBO_CUT_SHARE = 0.5f;
    private static final float FORTE_CUT_SHARE = 0.3f;
    private static final float BARRAGE_CUT_SHARE = 0.4f;
    private static final float DASH_CUT_SHARE = 0.5f;
    // The hand when the cut leaves it and the way it slashes, in Sukuna's frame (feet at 0, facing +z, +x his left),
    // and the tick of the move the cut leaves on.
    private record Slash(int tick, double hx, double hy, double hz, double sx, double sy, double sz) {
        Vec3 hand(float yaw) {
            return new Vec3(hx, hy, hz).yRot(-yaw * Mth.DEG_TO_RAD);
        }

        Vec3 slash(float yaw) {
            return new Vec3(sx, sy, sz).yRot(-yaw * Mth.DEG_TO_RAD);
        }
    }

    private static final Slash[] COMBO = {
            new Slash(1, 0.029, 1.053, 0.655, -0.582, -0.783, 0.218),
            new Slash(1, 0.005, 1.189, 0.416, 0.983, -0.033, 0.180),
            new Slash(1, 0.055, 1.372, 0.700, -0.590, 0.794, 0.145),
            new Slash(1, -0.753, 1.207, 0.318, -0.979, -0.004, -0.201),
            new Slash(1, -0.165, 1.023, 0.405, 0.150, -0.933, 0.327)};
    private static final Slash FORTE = new Slash(1, -0.254, 0.921, 0.855, -0.333, -0.901, 0.276);
    private static final Slash BARRAGE = new Slash(3, 0.881, 0.996, 0.587, 0.982, -0.016, -0.189);
    private static final Slash DASH = new Slash(1, -0.154, 0.989, 1.016, -0.984, 0.040, 0.173);

    // One cut's flight: size (1 = a combo cut, the preview's), speed (blocks a tick), how far past the target it goes,
    // and how much of the aim's height it follows (the combo's cuts fly nearly level, as in the preview).
    private record Flight(double size, double speed, double pass, double rise) {
    }

    private static final Flight COMBO_FLIGHT = new Flight(1, 1.6, 2.2, 0.25);
    private static final Flight FORTE_FLIGHT = new Flight(2.6, 2.0, 3, 0.5);
    private static final Flight BARRAGE_FLIGHT = new Flight(2.3, 2.0, 4, 1);
    private static final Flight DASH_FLIGHT = new Flight(1.1, 2.5, 2.5, 0.25);
    // Where the cut aims with no one to aim at, and how far it flies at least.
    private static final double FREE_RANGE = 4;

    private SukunaMelee() {
    }

    // Sukuna fighting bare-handed (with a sword in hand the sword moves win, as for everyone).
    public static boolean active(LivingEntity entity) {
        return Sukuna.ID.equals(Att.get(entity, ModAttachments.POWER).power) && !SwordArts.held(entity);
    }

    // Combo hit step: plays its pose and throws its cut. Returns how many ticks later the punch lands than usual.
    public static int comboSwing(LivingEntity attacker, LivingEntity target, int step, float punchDamage) {
        int i = Math.floorMod(step, COMBO.length);
        anim(attacker, "sukuna.combo" + (i + 1));
        later(attacker, COMBO[i], target, COMBO_FLIGHT, punchDamage * COMBO_CUT_SHARE, 0);
        return RAISE;
    }

    // The Forte's release (its pose is FORTE_POSE): the left hand's big cut.
    public static void forteCut(LivingEntity attacker, LivingEntity target, float punchDamage) {
        later(attacker, FORTE, target, FORTE_FLIGHT, punchDamage * FORTE_CUT_SHARE, 0);
    }

    // The Barragem's finisher (its pose is BARRAGE_END_POSE): one more cut at whoever it threw, where they will be.
    public static void barrageCut(LivingEntity attacker, LivingEntity target, float finisherDamage) {
        later(attacker, BARRAGE, target, BARRAGE_FLIGHT, finisherDamage * BARRAGE_CUT_SHARE, 1);
    }

    // The Dash Attack's start (its pose is DASH_POSE): the flat cut on the way out.
    public static void dashCut(LivingEntity attacker, LivingEntity target, float punchDamage) {
        later(attacker, DASH, target, DASH_FLIGHT, punchDamage * DASH_CUT_SHARE, 0);
    }

    private static void later(LivingEntity attacker, Slash slash, LivingEntity target, Flight flight, float damage, double lead) {
        PowerData data = Att.get(attacker, ModAttachments.POWER);
        int interrupts = data.interrupts;
        Tasks.later(slash.tick(), () -> {
            if (!attacker.isAlive() || data.interrupts != interrupts || Freeze.isFrozen(attacker) || Stun.real(attacker)) {
                return;
            }
            LivingEntity aim = target != null && target.isAlive() && Combat.isTarget(target, attacker) ? target : null;
            throwCut(attacker, slash, aim, flight, damage, lead);
        });
    }

    // lead: how far ahead (in ticks of its flight) to aim at a target that is moving (the Barragem's, flying away).
    private static void throwCut(LivingEntity attacker, Slash slash, LivingEntity target, Flight flight, float damage, double lead) {
        ServerLevel level = Combat.level(attacker);
        float yaw = attacker.getYHeadRot();
        Vec3 origin = attacker.position().add(slash.hand(yaw));
        Vec3 aim;
        if (target != null) {
            Vec3 c = Combat.center(target);
            double ticks = c.distanceTo(origin) / flight.speed();
            aim = c.add(target.getDeltaMovement().scale(ticks * lead));
        } else {
            aim = origin.add(attacker.getLookAngle().scale(FREE_RANGE));
        }
        Vec3 to = aim.subtract(origin);
        double dist = Math.max(FREE_RANGE * 0.5, to.length());
        Vec3 dir = new Vec3(to.x, to.y * flight.rise(), to.z);
        if (dir.lengthSqr() < 1.0E-4) {
            dir = Combat.flatLook(attacker);
        }
        dir = dir.normalize();
        double reach = dist + flight.pass();
        int seed = attacker.getRandom().nextInt(0xFFFF);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(attacker, new ModPayloads.SukunaCut(origin, dir.scale(flight.speed()),
                slash.slash(yaw).scale(flight.size()), (float) reach, seed));
        Fx.sound(level, origin, SoundEvents.PLAYER_ATTACK_SWEEP, 0.9f, 1.5f + attacker.getRandom().nextFloat() * 0.3f);
        // The blade's reach about its path: the preview's crescent spans about 0.8 of its size each side.
        double radius = 0.8 * 0.8 * flight.size();
        Set<Integer> hit = new HashSet<>();
        Vec3 way = dir;
        // the blade cuts the world it flies through, its own width (SukunaCarve): across it is the slash's line
        Vec3 slashLine = slash.slash(yaw);
        Vec3 side = slashLine.subtract(way.scale(slashLine.dot(way)));
        side = side.lengthSqr() < 1.0E-4 ? new Vec3(-way.z, 0, way.x).normalize() : side.normalize();
        Vec3 bladeSide = side;
        double blade = 0.75 * flight.size(), bow = 0.3 * flight.size();
        it.unimi.dsi.fastutil.longs.LongOpenHashSet carved = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        Tasks.add(age -> {
            double from = flight.speed() * age, upTo = Math.min(reach, flight.speed() * (age + 1));
            if (!attacker.isAlive() || from >= reach) {
                return true;
            }
            SukunaCarve.sweep(level, attacker, origin, way, bladeSide, blade, bow, from, upTo, carved);
            Vec3 a = origin.add(way.scale(from)), b = origin.add(way.scale(upTo));
            AABB around = new AABB(a, b).inflate(radius + 1);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, around, e -> Combat.isTarget(e, attacker))) {
                if (hit.contains(e.getId())) {
                    continue;
                }
                AABB box = e.getBoundingBox().inflate(radius);
                if (!box.contains(a) && box.clip(a, b).isEmpty()) {
                    continue;
                }
                hit.add(e.getId());
                // No knockback of its own: whatever the hit pushes is put back.
                Vec3 keep = e.getDeltaMovement();
                if (Combat.hurtBase(attacker, e, damage)) {
                    e.setDeltaMovement(keep);
                    e.hurtMarked = true;
                    Fx.sound(level, Combat.center(e), SoundEvents.PLAYER_ATTACK_CRIT, 0.8f, 1.3f);
                }
            }
            return upTo >= reach;
        });
    }

    private static void anim(LivingEntity entity, String move) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, new ModPayloads.PlaySkillAnimation(entity.getId(), move, false));
    }
}
