package com.starkinhon.battlegroundsmod.power.powers.shigaraki;

import java.util.HashSet;
import java.util.Set;

import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// B with a stolen power (Absorver): the preview's useStolen. The closest target within 32 blocks (the aim first); the
// hand goes up at 0.12 s.
final class Usar {
    private Usar() {
    }

    static void use(LivingEntity caster, ServerLevel level, int element) {
        LivingEntity target = Combat.raycastTarget(caster, Absorver.USE_RANGE);
        if (target == null) {
            double bd = Absorver.USE_RANGE;
            for (LivingEntity e : Combat.inRadius(level, caster.position(), Absorver.USE_RANGE, caster)) {
                double d = e.distanceTo(caster);
                if (d < bd && caster.hasLineOfSight(e)) {
                    bd = d;
                    target = e;
                }
            }
        }
        Vec3 dir = target != null ? Shigaraki.flatTo(caster, target.position()) : Combat.flatLook(caster);
        Vec3 from = caster.position().add(dir.scale(0.8)).add(0, 1.35, 0);
        Vec3 to = target != null ? Combat.center(target) : from.add(dir.scale(20));
        LivingEntity t = target;
        SubCommon.pose(caster, "shig.usar");
        SubCommon.busy(caster, 10);
        SubNet.send(level, Shigaraki.USE, caster.getId(), from, to, element, t == null ? -1 : t.getId(), 0);
        double len = from.distanceTo(to);
        if (element == Shigaraki.FIRE) {
            SubCommon.sound(level, from, SoundEvents.BLAZE_SHOOT, 1.5f, 0.7f);
            int arrive = 2 + (int) Math.ceil(len / 26 * 20);
            Tasks.later(arrive, () -> {
                Vec3 at = t != null && t.isAlive() ? Combat.center(t) : to;
                SubCommon.sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 2f, 0.8f);
                for (LivingEntity e : Combat.inRadius(level, at, 4.4, caster)) {
                    Combat.hurt(caster, e, e == t ? 42 : 26);
                    Combat.pushFrom(e, caster.position(), 0.9, 0.5);
                    e.igniteForSeconds(6);
                    Stun.apply(e, 25);
                }
            });
        } else if (element == Shigaraki.LIGHTNING) {
            SubCommon.sound(level, from, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.4f, 1.6f);
            Tasks.later(2, () -> {
                if (t != null && t.isAlive()) {
                    SubCommon.sound(level, t.position(), SoundEvents.LIGHTNING_BOLT_IMPACT, 1.5f, 1.2f);
                    Combat.hurt(caster, t, 34);
                    Stun.apply(t, 48);
                }
            });
        } else {
            SubCommon.sound(level, from, SoundEvents.GLASS_BREAK, 1.2f, 0.6f);
            Set<Integer> hit = new HashSet<>();
            // the row of spikes grows to the target (0.1 to 0.55 s); whoever stands on it is cut
            for (int k = 1; k <= 9; k++) {
                double u = k / 9.0;
                Vec3 p = caster.position().add(dir.scale(1.2 + u * Math.max(0, len - 1.2)));
                Tasks.later(2 + (int) Math.round(u * 9), () -> {
                    SubCommon.sound(level, p, SoundEvents.AMETHYST_BLOCK_BREAK, 0.6f, 1.3f);
                    for (LivingEntity e : Combat.inRadius(level, p.add(0, 0.8, 0), 1.3, caster)) {
                        if (e != t && hit.add(e.getId())) {
                            Combat.hurt(caster, e, 14);
                            Stun.apply(e, 25);
                        }
                    }
                });
            }
            Tasks.later(12, () -> {
                if (t != null && t.isAlive()) {
                    SubCommon.sound(level, t.position(), SoundEvents.GLASS_BREAK, 1.5f, 0.5f);
                    Combat.hurt(caster, t, 30);
                    Stun.apply(t, 60);
                    Freeze.hold(t, 60);
                }
            });
        }
    }
}
