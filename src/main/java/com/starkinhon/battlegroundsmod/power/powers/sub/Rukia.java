package com.starkinhon.battlegroundsmod.power.powers.sub;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// Rukia (SubAlma of Bleach): zones of ice, the Visualizador's Rukia tab. Frozen bodies get the ice shell (shape 28),
// growing over them pixel by pixel and shattering at the end.
// Z Some no Mai, Tsukishiro: a white circle on the ground round the aimed spot; a column of light and ice rises and
//   everything inside turns into an ice statue, which then breaks.
// X Tsugi no Mai, Hakuren: she stabs the ground and lets out a wide white avalanche that freezes all in its way.
// C San no Mai, Shirafune: the blade grows into ice and stretches at once to pierce far; the ice spreads from the wound.
// V Bankai, Hakka no Togame: she freezes over and turns white; absolute zero spreads round her and turns everyone close
//   into crystal.
public final class Rukia {
    private Rukia() {
    }

    public static Power create() {
        return new Power("rukia", 0xBFE8FF, 1.0f, List.of(
                new Ability(160, 0, Rukia::tsukishiro),
                new Ability(150, 0, Rukia::hakuren),
                new Ability(100, 0, Rukia::shirafune),
                new Ability(100, 0, Rukia::bankai)));
    }

    // Frozen for ticks: held still, the shell on it, then it shatters with a little more damage.
    static void freeze(LivingEntity caster, ServerLevel level, LivingEntity target, int ticks, boolean fromFeet, float shatterDamage) {
        Freeze.hold(target, ticks);
        target.setTicksFrozen(Math.max(target.getTicksFrozen(), 160));
        SubNet.send(level, SubNet.FREEZE, target.getId(), target.position(), Vec3.ZERO, ticks, fromFeet ? 0 : 1, 0);
        SubCommon.sound(level, target.position(), SoundEvents.PLAYER_HURT_FREEZE, 1f, 0.8f);
        Tasks.later(ticks, () -> {
            SubNet.send(level, SubNet.FREEZE, target.getId(), target.position(), Vec3.ZERO, 0, 0, 1);
            if (target.isAlive()) {
                SubCommon.sound(level, target.position(), SoundEvents.GLASS_BREAK, 1.2f, 0.9f);
                if (shatterDamage > 0 && caster.isAlive()) {
                    target.invulnerableTime = 0;
                    Combat.hurtMagic(caster, target, shatterDamage);
                }
            }
        });
    }

    // ---------- Z: Tsukishiro ----------

    private static boolean tsukishiro(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        Vec3 center = SubCommon.ground(level, SubCommon.aimPoint(caster, 22));
        float radius = 3f;
        int rise = 16;
        SubCommon.pose(caster, "sub.apontar_baixo");
        SubCommon.busy(caster, 12);
        SubNet.send(level, SubNet.TSUKISHIRO, 0, center, Vec3.ZERO, radius, rise, 0);
        SubCommon.sound(level, center, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2f, 1.6f);
        Tasks.later(rise, () -> {
            if (guard.broken()) {
                return;
            }
            SubCommon.sound(level, center, SoundEvents.BEACON_ACTIVATE, 1.5f, 1.8f);
            SubCommon.sound(level, center, SoundEvents.GLASS_BREAK, 1.5f, 0.6f);
            level.sendParticles(ParticleTypes.SNOWFLAKE, center.x, center.y + 2, center.z, 80, radius * 0.6, 3, radius * 0.6, 0.05);
            // Everything inside the circle, even what stands high over it.
            net.minecraft.world.phys.AABB column = new net.minecraft.world.phys.AABB(center.x - radius, center.y - 1, center.z - radius,
                    center.x + radius, center.y + 12, center.z + radius);
            for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, column, e -> Combat.isTarget(e, caster))) {
                double dx = t.getX() - center.x;
                double dz = t.getZ() - center.z;
                if (dx * dx + dz * dz > (radius + 0.5) * (radius + 0.5)) {
                    continue;
                }
                Combat.hurtMagic(caster, t, 7);
                freeze(caster, level, t, 60, true, 5);
            }
        });
        return true;
    }

    // ---------- X: Hakuren ----------

    private static boolean hakuren(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        SubCommon.pose(caster, "sub.cravar");
        SubCommon.busy(caster, 20);
        Vec3 dir = SubCommon.flat(caster.getLookAngle());
        for (int i = 0; i < 4; i++) {
            int k = i;
            Tasks.later(i * 3, () -> {
                Vec3 p = caster.position().add(dir.scale(1.2)).add(dir.cross(new Vec3(0, 1, 0)).scale((k - 1.5) * 0.5));
                SubCommon.sound(level, p, SoundEvents.POWDER_SNOW_BREAK, 1f, 0.8f + k * 0.15f);
                level.sendParticles(ParticleTypes.SNOWFLAKE, p.x, p.y + 0.1, p.z, 10, 0.2, 0.1, 0.2, 0.05);
                SubNet.send(level, SubNet.IMPACT, 0, p.add(0, 0.1, 0), 0.8f, SubNet.COLOR_ICE);
            });
        }
        Tasks.later(13, () -> {
            if (guard.broken()) {
                return;
            }
            Vec3 start = caster.position().add(dir.scale(1.5));
            float length = 18;
            float width = 5;
            SubNet.send(level, SubNet.HAKUREN, 0, start, dir, length, width, 0);
            SubCommon.sound(level, start, SoundEvents.WARDEN_SONIC_BOOM, 0.7f, 1.8f);
            SubCommon.sound(level, start, SoundEvents.ELDER_GUARDIAN_CURSE, 0.6f, 1.8f);
            Set<Integer> hit = new HashSet<>();
            Tasks.add(age -> {
                double front = length * SubCommon.smooth(age / 14f);
                Vec3 a = start.add(dir.scale(Math.max(0, front - 2)));
                Vec3 b = start.add(dir.scale(front));
                double w = width * (0.4 + 0.6 * front / length) * 0.5 + 0.6;
                for (LivingEntity t : Combat.alongLine(level, a.add(0, 1, 0), b.add(0, 1, 0), w, caster)) {
                    if (hit.add(t.getId())) {
                        Combat.hurtMagic(caster, t, 8);
                        freeze(caster, level, t, 45, false, 3);
                    }
                }
                return age >= 14;
            });
        });
        return true;
    }

    // ---------- C: Shirafune ----------

    private static boolean shirafune(LivingEntity caster, ServerLevel level) {
        SubCommon.pose(caster, "sub.estocada");
        SubCommon.busy(caster, 8);
        Vec3 start = Byakuya.hand(caster);
        Vec3 dir = caster.getLookAngle();
        double range = 16;
        Vec3 end = Combat.raycastPoint(caster, range);
        double length = Math.min(range, end.distanceTo(start) + 0.5);
        LivingEntity first = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity t : Combat.alongLine(level, start, start.add(dir.scale(length)), 0.7, caster)) {
            double d = t.distanceTo(caster);
            if (d < best) {
                best = d;
                first = t;
            }
        }
        if (first != null) {
            length = Math.min(length, best + 0.5);
        }
        SubNet.send(level, SubNet.SHIRAFUNE, 0, start, dir, (float) length, 0, 0);
        SubCommon.sound(level, start, SoundEvents.GLASS_BREAK, 1f, 1.8f);
        SubCommon.sound(level, start, SoundEvents.TRIDENT_THROW.value(), 1.2f, 1.4f);
        if (first != null) {
            Combat.hurtMagic(caster, first, 9);
            freeze(caster, level, first, 35, false, 3);
            SubNet.send(level, SubNet.IMPACT, 0, Combat.center(first), 1.6f, SubNet.COLOR_ICE);
        }
        return true;
    }

    // ---------- V: Bankai ----------

    private static boolean bankai(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        Vec3 feet = caster.position();
        int freezeSelf = 26;
        float radius = 11;
        SubCommon.pose(caster, "sub.erguer");
        SubCommon.busy(caster, freezeSelf + 6);
        // She freezes over herself first (the Bankai's body at -18 degrees), untouched while it lasts.
        Freeze.hold(caster, freezeSelf);
        caster.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, freezeSelf + 10, 4, false, false));
        SubNet.send(level, SubNet.FREEZE, caster.getId(), feet, Vec3.ZERO, freezeSelf, 0, 0);
        SubCommon.sound(level, feet, SoundEvents.BEACON_ACTIVATE, 1.5f, 1.6f);
        SubCommon.sound(level, feet, SoundEvents.PLAYER_HURT_FREEZE, 1.5f, 0.6f);
        Tasks.later(freezeSelf, () -> {
            SubNet.send(level, SubNet.FREEZE, caster.getId(), feet, Vec3.ZERO, 0, 0, 1);
            if (guard.broken()) {
                return;
            }
            SubCommon.sound(level, feet, SoundEvents.GLASS_BREAK, 1.6f, 0.5f);
            SubCommon.sound(level, feet, SoundEvents.ELDER_GUARDIAN_CURSE, 1f, 1.5f);
            SubNet.send(level, SubNet.AURA, caster.getId(), feet, Vec3.ZERO, 200, 2, 0);
            SubNet.send(level, SubNet.FROST, 0, feet, Vec3.ZERO, radius, 140, 0);
            caster.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 1, false, false));
            Set<Integer> hit = new HashSet<>();
            Tasks.add(age -> {
                double r = radius * SubCommon.smooth(age / 16f);
                for (LivingEntity t : Combat.inRadius(level, feet, r, caster)) {
                    if (hit.add(t.getId())) {
                        Combat.hurtMagic(caster, t, 10);
                        freeze(caster, level, t, 80, false, 7);
                    }
                }
                return age >= 16;
            });
        });
        return true;
    }
}
