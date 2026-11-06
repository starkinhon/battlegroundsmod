package com.starkinhon.battlegroundsmod.power.powers.sub;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// Orihime (SubAlma of Bleach): protection and reflection, the Visualizador's Orihime tab. The Shun Shun Rikka come out
// of her hairpins as golden lights.
// Z Santen Kesshun: a golden triangle in front of her that rejects whatever hits it from the front (her and whoever
//   stands behind it).
// X Soten Kisshun: a golden dome over the ally she aims at (or herself): not healing, the harm is rejected and the body
//   goes back to how it was (health back over time, bad effects and fire gone).
// C Shiten Koshun: the triangle with Tsubaki in the middle: the first hit is gathered and bursts back at whoever struck.
// V Koten Zanshun: Tsubaki becomes a narrow golden blade that flies straight through everyone in its way.
public final class Orihime {
    private Orihime() {
    }

    public static Power create() {
        return new Power("orihime", 0xFFC94A, 0.9f, List.of(
                new Ability(120, 0, Orihime::santen).asCounter(),
                new Ability(300, 0, Orihime::soten),
                new Ability(200, 0, Orihime::shiten).asCounter(),
                new Ability(100, 0, Orihime::koten)));
    }

    private static Vec3 front(LivingEntity caster) {
        return caster.position().add(0, caster.getBbHeight() * 0.55, 0).add(SubCommon.flat(caster.getLookAngle()).scale(1.6));
    }

    private static boolean santen(LivingEntity caster, ServerLevel level) {
        SubCommon.pose(caster, "sub.palma");
        SubCommon.sound(level, caster.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2f, 1.8f);
        SubCommon.sound(level, caster.position(), SoundEvents.BEACON_POWER_SELECT, 0.8f, 1.6f);
        SubDefense.shield(caster, level, front(caster), SubCommon.flat(caster.getLookAngle()), 2.6, 70, 0, null);
        return true;
    }

    private static boolean shiten(LivingEntity caster, ServerLevel level) {
        SubCommon.pose(caster, "sub.palma");
        SubCommon.sound(level, caster.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2f, 1.4f);
        SubDefense.shield(caster, level, front(caster), SubCommon.flat(caster.getLookAngle()), 2.6, 70, 1, (shield, attacker) -> {
            // The blow, gathered, bursts back at whoever struck it.
            shield.takeDown();
            Vec3 from = shield.center;
            SubNet.send(level, SubNet.IMPACT, 0, from, 3f, SubNet.COLOR_GOLD);
            SubCommon.sound(level, from, SoundEvents.GENERIC_EXPLODE.value(), 1f, 1.5f);
            if (attacker instanceof LivingEntity back && back.isAlive() && back.distanceTo(caster) < 40) {
                Vec3 to = Combat.center(back);
                SubNet.send(level, SubNet.BEAM, 0, from, to.subtract(from), 0.5f, SubNet.COLOR_GOLD, 0);
                SubNet.send(level, SubNet.IMPACT, 0, to, 2.6f, SubNet.COLOR_GOLD);
                Combat.hurtMagic(caster, back, 12);
                Combat.pushFrom(back, from, 1.4, 0.5);
                Stun.apply(back, 20);
            }
        });
        return true;
    }

    private static boolean soten(LivingEntity caster, ServerLevel level) {
        LivingEntity aimed = SubCommon.aimed(caster, 16);
        // Her dome goes on whoever she aims at that isn't an enemy (a tamed wolf, a teammate), else on herself.
        LivingEntity target = aimed != null && (aimed.isAlliedTo(caster) || caster.isShiftKeyDown()) ? aimed : caster;
        int ticks = 80;
        float radius = Math.max(1.2f, target.getBbWidth() * 0.9f + 0.6f);
        SubCommon.pose(caster, "sub.palma");
        SubCommon.sound(level, target.position(), SoundEvents.BEACON_ACTIVATE, 1f, 1.8f);
        SubNet.send(level, SubNet.DOME, target.getId(), target.position(), Vec3.ZERO, ticks, radius, 0);
        target.clearFire();
        target.removeEffect(MobEffects.POISON);
        target.removeEffect(MobEffects.WITHER);
        target.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        target.removeEffect(MobEffects.WEAKNESS);
        target.removeEffect(MobEffects.BLINDNESS);
        target.removeEffect(MobEffects.DIG_SLOWDOWN);
        target.setTicksFrozen(0);
        Tasks.add(age -> {
            if (!target.isAlive()) {
                return true;
            }
            if (age % 5 == 0) {
                target.heal(1.2f);
            }
            if (age % 20 == 0) {
                SubCommon.sound(level, target.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.7f, 1.8f);
            }
            return age >= ticks;
        });
        return true;
    }

    private static boolean koten(LivingEntity caster, ServerLevel level) {
        SubCommon.pose(caster, "sub.apontar");
        SubCommon.sound(level, caster.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.4f, 2f);
        SubCommon.sound(level, caster.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 1.8f);
        int id = SubNet.newId();
        Vec3 dir = caster.getLookAngle();
        Vec3[] pos = {caster.getEyePosition().add(dir.scale(1))};
        double speed = 2.2;
        double range = 42;
        Set<Integer> hit = new HashSet<>();
        Tasks.add(age -> {
            Vec3 next = pos[0].add(dir.scale(speed));
            boolean wall = !level.noCollision(new net.minecraft.world.phys.AABB(next, next).inflate(0.2));
            for (LivingEntity t : Combat.alongLine(level, pos[0], next, 0.9, caster)) {
                if (hit.add(t.getId())) {
                    // "Rejects" what it cuts: a heavy cut that ignores armor.
                    Combat.hurtMagic(caster, t, 18);
                    t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 1, false, true));
                    SubNet.send(level, SubNet.SLASH, 0, Combat.center(t), dir.cross(new Vec3(0, 1, 0)), 1.2f, 0.18f, SubNet.STYLE_GOLD);
                    SubCommon.sound(level, t.position(), SoundEvents.PLAYER_ATTACK_CRIT, 1.2f, 1.4f);
                }
            }
            pos[0] = next;
            boolean end = wall || age * speed >= range;
            SubNet.send(level, SubNet.KOTEN, id, pos[0], dir, 0, 0, end ? 1 : 0);
            return end;
        });
        return true;
    }
}
