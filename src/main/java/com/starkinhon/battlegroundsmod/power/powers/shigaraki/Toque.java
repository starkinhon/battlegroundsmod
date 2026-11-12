package com.starkinhon.battlegroundsmod.power.powers.shigaraki;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.movement.Movement;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// Z, Toque da Decadência (preview v2, APROVADÍSSIMO 2026-11-11). He crouches (0.26 s), dashes up to 18 blocks in 0.3 s
// (ease out) and at 0.55 s:
// - grab: impact frame (white/black, negative and positive) and the body dries from the hand, cracks and crumbles; the
//   ground under it goes too 0.7 s later.
// - miss (nobody in reach): the palm slams the ground 3 blocks ahead and a living crack runs 15 blocks where he aimed,
//   winding, and decays whoever stands on it.
public final class Toque {
    static final double RANGE = 18;
    // Preview: wind 0.26 s, dash until 0.55 s, all of it 2.4 s.
    private static final int WIND = 5, CONTACT = 11, LENGTH = 48;
    private static final float BODY_SPEED = 1.25f, DAMAGE = 70, SLAM_DAMAGE = 45;

    private static final Map<LivingEntity, Boolean> BUSY = new WeakHashMap<>();

    private Toque() {
    }

    public static boolean use(LivingEntity caster, ServerLevel level) {
        if (!Movement.free(caster) || BUSY.containsKey(caster)) {
            return false;
        }
        PowerData data = Shigaraki.data(caster);
        int interrupts = data.interrupts;
        LivingEntity target = lock(caster);
        Vec3 from = caster.position();
        Vec3 dir0 = target != null ? Shigaraki.flatTo(caster, target.position()) : Combat.flatLook(caster);
        // grab: stop 0.72 short of the target's middle; miss: 3 blocks ahead. Both follow the target while he dashes (a
        // boss never stands still for 0.55 s: the spot fixed at the press left him 3+ blocks short, the grab became the
        // miss's crack and the boss never decayed).
        Vec3[] way = {dir0, target != null ? new Vec3(target.getX(), from.y, target.getZ()).subtract(dir0.scale(0.72)) : from.add(dir0.scale(3))};
        Vec3 to = way[1];
        boolean hit = target != null;
        BUSY.put(caster, true);
        SubCommon.busy(caster, CONTACT + 8);
        SubCommon.pose(caster, hit ? "shig.toque" : "shig.toque_erra");
        SubNet.send(level, Shigaraki.TOUCH, caster.getId(), from, to, hit ? 0 : 1, 0, 0);
        SubCommon.sound(level, from, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 0.5f);
        Tasks.add(age -> {
            if (!caster.isAlive() || data.interrupts != interrupts || Freeze.isFrozen(caster)) {
                BUSY.remove(caster);
                return true;
            }
            if (age < WIND) {
                Vec3 v = caster.getDeltaMovement();
                caster.setDeltaMovement(v.x * 0.2, v.y, v.z * 0.2);
                caster.hurtMarked = true;
                return false;
            }
            if (age < CONTACT) {
                if (hit && target.isAlive() && target.position().distanceTo(from) < RANGE + 4) {
                    Vec3 d = new Vec3(target.getX() - from.x, 0, target.getZ() - from.z);
                    if (d.lengthSqr() > 1e-4) {
                        way[0] = d.normalize();
                        way[1] = new Vec3(target.getX(), from.y, target.getZ()).subtract(way[0].scale(0.72));
                    }
                }
                Vec3 to1 = way[1];
                // the dash: ease out over 0.3 s (preview easeOut3), as a velocity so walls still stop him
                float k0 = ease((age - WIND) / (float) (CONTACT - WIND)), k1 = ease((age + 1 - WIND) / (float) (CONTACT - WIND));
                // the curve's own step to the spot (the preview's; it follows the target, landOn finishes it at the contact), not want - position: a player's position on
                // the server is a tick behind their client, so that overshot past the target, shoved it and missed the grab
                Vec3 step = to1.subtract(from).scale(k1 - k0);
                caster.setDeltaMovement(step.x, Math.min(caster.getDeltaMovement().y, 0.05), step.z);
                caster.hurtMarked = true;
                caster.resetFallDistance();
                if (age == WIND) {
                    SubCommon.sound(level, caster.position(), SoundEvents.PLAYER_ATTACK_STRONG, 1f, 0.5f);
                }
                return false;
            }
            if (age == CONTACT) {
                caster.setDeltaMovement(Vec3.ZERO);
                caster.hurtMarked = true;
                Shigaraki.landOn(caster, way[1]);
                // the reach to the body's box, not its middle: a wide boss counts from its skin
                boolean reach = target != null && (target.distanceTo(caster) < 3.2
                        || target.getBoundingBox().distanceToSqr(caster.getBoundingBox().getCenter()) < 2.4 * 2.4);
                if (hit && Combat.isTarget(target, caster) && reach) {
                    grab(caster, target, level, way[0]);
                } else {
                    slam(caster, level, way[0]);
                }
            }
            if (age >= LENGTH - 10) {
                BUSY.remove(caster);
                return true;
            }
            return false;
        });
        return true;
    }

    private static float ease(float x) {
        x = Math.max(0, Math.min(1, x));
        return 1 - (1 - x) * (1 - x) * (1 - x);
    }

    // Whoever is in front within reach, the closest to the aim (the preview takes the closest one).
    static LivingEntity lock(LivingEntity caster) {
        LivingEntity aimed = Combat.raycastTarget(caster, RANGE);
        if (aimed != null) {
            return aimed;
        }
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        Vec3 look = Combat.flatLook(caster);
        for (LivingEntity e : Combat.inRadius((ServerLevel) caster.level(), caster.position(), RANGE, caster)) {
            Vec3 d = Shigaraki.flatTo(caster, e.position());
            double dot = d.dot(look);
            if (dot < 0.82 || !caster.hasLineOfSight(e)) {
                continue;
            }
            double score = e.distanceTo(caster) * (2 - dot);
            if (score < bd) {
                bd = score;
                best = e;
            }
        }
        return best;
    }

    // Grabbed: the hand on the chest; the Decadência climbs the body from it, and the ground under it goes after 0.7 s.
    private static void grab(LivingEntity caster, LivingEntity target, ServerLevel level, Vec3 dir) {
        Vec3 hand = caster.position().add(dir.scale(0.72 + 0.55)).add(0, 1.25, 0);
        Vec3 touch = target.getBoundingBox().getCenter().add(hand.subtract(target.getBoundingBox().getCenter()).scale(0.4));
        SubNet.send(level, Shigaraki.TOUCH, caster.getId(), caster.position(), touch, 0, 1, target.getId());
        SubCommon.sound(level, touch, SoundEvents.PLAYER_ATTACK_CRIT, 1.2f, 0.5f);
        SubCommon.sound(level, touch, SoundEvents.GRAVEL_BREAK, 1.2f, 0.5f);
        Decay.body(caster, target, touch, BODY_SPEED, DAMAGE, 40);
        Vec3 feet = target.position();
        Tasks.later(14, () -> Decay.seed(level, caster, Shigaraki.groundSeed(feet), 2.2f, 3.2f, Decay.GROUND, feet.y, 1.1));
    }

    // Missed: the palm hits the ground and the crack runs 15 blocks where he aimed, winding (28 points over 1.15 s).
    private static void slam(LivingEntity caster, ServerLevel level, Vec3 dir) {
        Vec3 feet = caster.position();
        Vec3 p0 = feet.add(dir.scale(0.9));
        Vec3 side = new Vec3(dir.z, 0, -dir.x);
        double ph = SubCommon.RANDOM.nextDouble() * 6;
        SubNet.send(level, Shigaraki.TOUCH, caster.getId(), feet, p0, 1, 1, 0);
        SubCommon.sound(level, p0, SoundEvents.GRAVEL_BREAK, 1.4f, 0.4f);
        Decay.seed(level, caster, Shigaraki.groundSeed(p0), 4f, 2.2f, Decay.GROUND, feet.y, 1.1);
        Set<Integer> hit = new HashSet<>();
        int n = 28;
        double len = 15;
        for (int i = 1; i <= n; i++) {
            double s = i / (double) n;
            boolean end = i == n;
            Vec3 p = p0.add(dir.scale(s * len)).add(side.scale(Math.sin(s * 8.5 + ph) * 0.9 + (SubCommon.RANDOM.nextDouble() - 0.5) * 0.5));
            int delay = (int) Math.round((0.06 + s * 1.15) * 20);
            float reach = end ? 3.6f : 1.25f + SubCommon.RANDOM.nextFloat() * 0.7f;
            Tasks.later(delay, () -> {
                Vec3 ground = SubCommon.ground(level, new Vec3(p.x, feet.y + 1, p.z));
                Decay.seed(level, caster, Shigaraki.groundSeed(ground), end ? 3.2f : 3.8f, reach, Decay.GROUND, ground.y, 1.1);
                for (LivingEntity e : Combat.inRadius(level, ground, end ? 3.2 : 1.5, caster)) {
                    if (hit.add(e.getId())) {
                        Decay.body(caster, e, e.position().add(0, 0.1, 0), BODY_SPEED, SLAM_DAMAGE, 30);
                    }
                }
            });
        }
    }
}
