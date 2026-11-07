package com.starkinhon.battlegroundsmod.power.powers.sub;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// Byakuya (SubAlma of Bleach): blades he controls, the Visualizador's Byakuya tab.
// Z Senbonzakura: the blade scatters into a river of petal-blades that follows the aim while held and comes back when
//   let go. Right next to him it doesn't cut (allies stuck to him are safe).
// X Gokei: held, the petals close into a sphere round the aimed target and squeeze; let go, they crush it from every side.
// C Senka: he flash-steps behind the target and locks it with two cuts.
// V Bankai, Senbonzakura Kageyoshi: two rows of giant blades rise round him and come apart into a storm of petals that
//   follows him: Z and X grow stronger and the storm cuts whoever comes close.
public final class Byakuya {
    public static final String BANKAI = "byakuya_bankai";
    private static final int BANKAI_TICKS = 400;

    private Byakuya() {
    }

    public static Power create() {
        return new Power("byakuya", 0xF4A6C6, 1.0f, List.of(
                new Ability(90, 0, Byakuya::senbonzakura),
                new Ability(220, 0, Byakuya::gokei),
                new Ability(160, 0, Byakuya::senka),
                new Ability(100, 0, Byakuya::bankai)));
    }

    static boolean inBankai(LivingEntity caster) {
        return SubCommon.hasState(caster, BANKAI);
    }

    // ---------- Z: Senbonzakura ----------

    private static boolean senbonzakura(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        boolean big = inBankai(caster);
        double range = big ? 28 : 18;
        double speed = big ? 1.7 : 1.2;
        float width = big ? 1.3f : 0.8f;
        float damage = big ? 3f : 2f;
        int least = 20;
        int most = big ? 80 : 60;
        int bossHold = least + SubCommon.RANDOM.nextInt(30);
        SubCommon.pose(caster, "sub.apontar");
        SubCommon.sound(level, caster.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2f, 1.4f);
        List<Vec3> path = new ArrayList<>();
        Vec3[] head = {hand(caster)};
        path.add(head[0]);
        int[] back = {-1};
        Map<Integer, Integer> lastHit = new HashMap<>();
        Tasks.add(age -> {
            if (guard.broken()) {
                SubNet.send(level, SubNet.PETALS, caster.getId(), head[0], Vec3.ZERO, width, 0, 1);
                return true;
            }
            SubCommon.busy(caster, 2);
            boolean held = SubCommon.held(caster, age, bossHold);
            if (back[0] < 0 && (age >= most || (age >= least && !held))) {
                back[0] = age;
            }
            if (back[0] < 0 && held) {
                com.starkinhon.battlegroundsmod.combat.Charge.report(caster, age / (double) most);
            }
            Vec3 goal;
            if (back[0] < 0) {
                goal = Combat.raycastPoint(caster, range);
                LivingEntity t = SubCommon.aimed(caster, range);
                if (t != null) {
                    goal = Combat.center(t);
                }
            } else {
                goal = hand(caster);
            }
            Vec3 to = goal.subtract(head[0]);
            double step = back[0] < 0 ? speed : speed * 2;
            head[0] = to.length() <= step ? goal : head[0].add(to.normalize().scale(step));
            path.add(head[0]);
            if (path.size() > 16) {
                path.remove(0);
            }
            float density = back[0] < 0 ? Math.min(1, age / 10f) : Math.max(0.1f, 1 - (age - back[0]) / 12f);
            boolean done = back[0] >= 0 && (head[0].distanceTo(hand(caster)) < 1.2 || age - back[0] > 24);
            SubNet.send(level, SubNet.PETALS, caster.getId(), head[0], Vec3.ZERO, width, density, done ? 1 : 0);
            if (age % 3 == 0) {
                SubCommon.sound(level, head[0], SoundEvents.AMETHYST_BLOCK_CHIME, 0.6f, 1.6f + SubCommon.RANDOM.nextFloat() * 0.4f);
                cutAlong(caster, level, path, width + 0.6, damage, age, lastHit);
            }
            return done;
        });
        return true;
    }

    // Everyone along the river takes a cut (each at most every 6 ticks); right next to Byakuya nobody does.
    private static void cutAlong(LivingEntity caster, ServerLevel level, List<Vec3> path, double radius, float damage, int age,
            Map<Integer, Integer> lastHit) {
        for (int i = Math.max(1, path.size() - 8); i < path.size(); i++) {
            for (LivingEntity t : Combat.alongLine(level, path.get(i - 1), path.get(i), radius, caster)) {
                if (t.distanceTo(caster) < 2.5) {
                    continue;
                }
                Integer last = lastHit.get(t.getId());
                if (last != null && age - last < 6) {
                    continue;
                }
                lastHit.put(t.getId(), age);
                Combat.hurt(caster, t, damage);
                t.invulnerableTime = 0;
                Vec3 c = Combat.center(t);
                SubNet.send(level, SubNet.SLASH, 0, c, randomDir(), 0.6f, 0.08f, SubNet.STYLE_PINK);
            }
        }
    }

    static Vec3 hand(LivingEntity caster) {
        Vec3 look = caster.getLookAngle();
        Vec3 right = SubCommon.flat(look).cross(new Vec3(0, 1, 0));
        return caster.position().add(0, caster.getBbHeight() * 0.62, 0).add(right.scale(0.38)).add(look.scale(0.45));
    }

    static Vec3 randomDir() {
        return new Vec3(SubCommon.RANDOM.nextDouble() - 0.5, SubCommon.RANDOM.nextDouble() - 0.5, SubCommon.RANDOM.nextDouble() - 0.5).normalize();
    }

    // ---------- X: Gokei ----------

    private static boolean gokei(LivingEntity caster, ServerLevel level) {
        LivingEntity target = SubCommon.aimed(caster, 26);
        if (target == null) {
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        boolean big = inBankai(caster);
        int id = SubNet.newId();
        int closeTicks = 30;
        int most = 60;
        int bossHold = 20 + SubCommon.RANDOM.nextInt(30);
        float radius = Math.max(1.2f, target.getBbWidth() * 0.6f + target.getBbHeight() * 0.45f);
        SubCommon.pose(caster, "sub.palma");
        SubCommon.sound(level, target.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.5f, 0.8f);
        Tasks.add(age -> {
            Vec3 center = Combat.center(target);
            if (guard.broken() || !target.isAlive()) {
                SubNet.send(level, SubNet.GOKEI, id, center, Vec3.ZERO, radius, 0, 2);
                return true;
            }
            SubCommon.busy(caster, 2);
            float closed = Math.min(1, age / (float) closeTicks);
            boolean held = SubCommon.held(caster, age, bossHold);
            if (held) {
                com.starkinhon.battlegroundsmod.combat.Charge.report(caster, closed);
            }
            // Caught inside: slowed hard while it closes.
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 4, closed > 0.6f ? 5 : 2, false, false));
            if (age % 6 == 0) {
                SubCommon.sound(level, center, SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 1.2f + closed);
            }
            if ((held || age < 8) && age < most) {
                SubNet.send(level, SubNet.GOKEI, id, center, Vec3.ZERO, radius, closed, 0);
                return false;
            }
            SubNet.send(level, SubNet.GOKEI, id, center, Vec3.ZERO, radius, closed, 1);
            SubCommon.sound(level, center, SoundEvents.PLAYER_ATTACK_SWEEP, 1.5f, 0.6f);
            SubCommon.sound(level, center, SoundEvents.GLASS_BREAK, 1.2f, 1.5f);
            float damage = (5 + 13 * closed) * (big ? 1.35f : 1);
            Tasks.later(5, () -> {
                if (target.isAlive()) {
                    Combat.hurt(caster, target, damage);
                    Stun.apply(target, 20);
                    for (int i = 0; i < 5; i++) {
                        SubNet.send(level, SubNet.SLASH, 0, Combat.center(target), randomDir(), 1f, 0.1f, SubNet.STYLE_PINK);
                    }
                }
                SubNet.send(level, SubNet.GOKEI, id, Combat.center(target), Vec3.ZERO, radius, closed, 2);
            });
            return true;
        });
        return true;
    }

    // ---------- C: Senka ----------

    private static boolean senka(LivingEntity caster, ServerLevel level) {
        LivingEntity target = SubCommon.aimed(caster, 18);
        if (target == null) {
            return false;
        }
        Vec3 behind = target.position().subtract(SubCommon.flat(target.getLookAngle()).scale(1.3));
        if (SubCommon.solid(level, behind.add(0, 0.5, 0)) || SubCommon.solid(level, behind.add(0, 1.5, 0))) {
            behind = target.position().subtract(SubCommon.flat(caster.getLookAngle()).scale(-1.3));
        }
        Vec3 from = caster.position();
        SubNet.send(level, SubNet.SLASH, 0, from.add(behind).scale(0.5).add(0, 1, 0), behind.subtract(from), (float) (behind.distanceTo(from) / 2),
                0.15f, SubNet.STYLE_PINK);
        Vec3 face = target.position().subtract(behind);
        float yaw = (float) (Mth.atan2(-face.x, face.z) * Mth.RAD_TO_DEG);
        if (caster instanceof ServerPlayer player) {
            player.teleportTo(level, behind.x, behind.y, behind.z, yaw, player.getXRot());
        } else {
            caster.teleportTo(behind.x, behind.y, behind.z);
            caster.setYRot(yaw);
        }
        SubCommon.sound(level, behind, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1f, 1.4f);
        SubCommon.pose(caster, "sub.corte");
        SubCommon.busy(caster, 14);
        Stun.apply(target, 14);
        for (int hit = 0; hit < 2; hit++) {
            int k = hit;
            Tasks.later(3 + hit * 6, () -> {
                if (!target.isAlive() || !caster.isAlive()) {
                    return;
                }
                target.invulnerableTime = 0;
                Combat.hurt(caster, target, 4f);
                Vec3 d = k == 0 ? new Vec3(1, 0.7, 0) : new Vec3(-1, 0.6, 0.3);
                SubNet.send(level, SubNet.SLASH, 0, Combat.center(target), d, 1.1f, 0.15f, SubNet.STYLE_PINK);
                SubCommon.sound(level, target.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 1.3f);
                if (k == 1) {
                    // The lock: the two cuts at the neck and chest leave it stunned.
                    Stun.apply(target, 34);
                    SubNet.send(level, SubNet.IMPACT, 0, Combat.center(target), 1.6f, SubNet.COLOR_PINK);
                }
            });
        }
        return true;
    }

    // ---------- V: Bankai ----------

    private static boolean bankai(LivingEntity caster, ServerLevel level) {
        if (inBankai(caster)) {
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        Vec3 face = SubCommon.flat(caster.getLookAngle());
        Vec3 feet = caster.position();
        SubCommon.setState(caster, BANKAI, BANKAI_TICKS);
        SubCommon.pose(caster, "sub.soltar");
        SubCommon.busy(caster, 30);
        SubCommon.sound(level, feet, SoundEvents.BEACON_ACTIVATE, 2f, 0.6f);
        SubNet.send(level, SubNet.BANKAI_BYAKUYA, caster.getId(), feet, face, 0, BANKAI_TICKS, 0);
        Map<Integer, Integer> lastHit = new HashMap<>();
        Tasks.add(age -> {
            if (guard.broken() || !inBankai(caster)) {
                SubCommon.clearState(caster, BANKAI);
                SubNet.send(level, SubNet.BANKAI_BYAKUYA, caster.getId(), caster.position(), face, age, BANKAI_TICKS, 1);
                SubCommon.sound(level, caster.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2f, 0.7f);
                return true;
            }
            if (age > 0 && age % 20 == 0) {
                SubNet.send(level, SubNet.BANKAI_BYAKUYA, caster.getId(), caster.position(), face, age, BANKAI_TICKS, 0);
            }
            if (age == 10 || age == 24 || age == 36) {
                SubCommon.sound(level, feet, SoundEvents.ANVIL_LAND, 1f, 0.5f + age / 60f);
            }
            if (age == 44) {
                SubCommon.sound(level, feet, SoundEvents.AMETHYST_BLOCK_RESONATE, 2f, 0.6f);
            }
            // The storm cuts whoever comes into it (not whoever is stuck right by him).
            if (age > 60 && age % 10 == 0) {
                for (LivingEntity t : Combat.inRadius(level, caster.position(), 6, caster)) {
                    if (t.distanceTo(caster) < 1.8) {
                        continue;
                    }
                    Integer last = lastHit.get(t.getId());
                    if (last != null && age - last < 10) {
                        continue;
                    }
                    lastHit.put(t.getId(), age);
                    t.invulnerableTime = 0;
                    Combat.hurt(caster, t, 1.5f);
                    SubNet.send(level, SubNet.SLASH, 0, Combat.center(t), randomDir(), 0.6f, 0.08f, SubNet.STYLE_PINK);
                }
            }
            return false;
        });
        return true;
    }
}
