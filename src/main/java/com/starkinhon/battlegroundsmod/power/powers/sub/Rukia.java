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
    // Tsukishiro: ticks the ice statue stands before it breaks.
    public static final int TSUKI_HOLD = 60;
    // Bankai timeline (ticks from the cast; the client draws the same, see client/fx/IceShell): the ice covers her
    // until X, then she is white and the ice thaws down to the neck (BANKAI_ARMOR of her height stays as armor); the
    // ground starts cracking at X + FROST; the wave of absolute zero goes out at W; at M she freezes whole again, and
    // after that thaws slowly.
    public static final int BANKAI_X = 40;
    public static final int BANKAI_FROST = 20;
    public static final int BANKAI_W = 80;
    public static final int BANKAI_M = 190;
    public static final float BANKAI_ARMOR = 0.76f;

    private Rukia() {
    }

    public static Power create() {
        return new Power("rukia", 0xBFE8FF, 1.0f, List.of(
                new Ability(160, 0, Rukia::tsukishiro),
                new Ability(150, 0, Rukia::hakuren),
                new Ability(100, 0, Rukia::shirafune),
                new Ability(100, 0, Rukia::bankai)));
    }

    // Frozen for ticks: held still with the ice over the body (spreading from the feet up, or out from `from`, over
    // `grow` ticks, reaching `reach` blocks), then it shatters with a little more damage, or (shatterDamage < 0) thaws.
    static void freeze(LivingEntity caster, ServerLevel level, LivingEntity target, int ticks, Vec3 from, boolean fromFeet, int grow,
            float reach, float shatterDamage) {
        Freeze.hold(target, ticks);
        target.setTicksFrozen(Math.max(target.getTicksFrozen(), 160));
        SubNet.send(level, SubNet.FREEZE, target.getId(), fromFeet ? target.position() : from, new Vec3(grow, reach, 0), ticks,
                fromFeet ? 0 : 1, 0);
        SubCommon.sound(level, target.position(), SoundEvents.PLAYER_HURT_FREEZE, 1f, 0.8f);
        boolean shatter = shatterDamage >= 0;
        Tasks.later(ticks, () -> {
            SubNet.send(level, SubNet.FREEZE, target.getId(), target.position(), Vec3.ZERO, 0, 0, shatter ? 1 : 2);
            if (target.isAlive()) {
                if (shatter) {
                    SubCommon.sound(level, target.position(), SoundEvents.GLASS_BREAK, 1.2f, 0.9f);
                } else {
                    SubCommon.sound(level, target.position(), SoundEvents.POWDER_SNOW_BREAK, 1f, 1.2f);
                }
                if (shatterDamage > 0 && caster.isAlive()) {
                    target.invulnerableTime = 0;
                    Combat.hurtMagic(caster, target, shatterDamage);
                }
            }
        });
    }

    // The point of target's body facing the caster, at chest height (where the ice starts spreading from).
    static Vec3 facing(LivingEntity caster, LivingEntity target) {
        Vec3 to = SubCommon.flat(caster.position().subtract(target.position()));
        return target.position().add(0, target.getBbHeight() * 0.5, 0).add(to.scale(target.getBbWidth() * 0.6));
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
                freeze(caster, level, t, TSUKI_HOLD, null, true, 7, 1.9f, 5);
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
                Vec3 lean = dir.cross(new Vec3(0, 1, 0)).scale((k - 1.5) * 0.15).add(0, 1, 0).add(dir.scale(0.2));
                SubNet.send(level, SubNet.ICE_SPIKE, k, p, lean, 0.6f, 13 - k * 3 + 16, 0);
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
                        Combat.hurtMagic(caster, t, 11);
                        freeze(caster, level, t, 60, facing(caster, t), false, 9, 1.6f, -1);
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
        SubNet.send(level, SubNet.SHIRAFUNE, 0, start, dir, (float) length, 0, first != null ? 1 : 0);
        SubCommon.sound(level, start, SoundEvents.GLASS_BREAK, 1f, 1.8f);
        SubCommon.sound(level, start, SoundEvents.TRIDENT_THROW.value(), 1.2f, 1.4f);
        if (first != null) {
            Combat.hurtMagic(caster, first, 12);
            freeze(caster, level, first, 40, start.add(dir.scale(length)), false, 26, 1.25f, -1);
            SubNet.send(level, SubNet.IMPACT, 0, Combat.center(first), 1.6f, SubNet.COLOR_ICE);
        }
        return true;
    }

    // ---------- V: Bankai ----------

    private static boolean bankai(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        Vec3 feet = caster.position();
        float radius = 11;
        int armorEnd = BANKAI_M + 16;
        boolean[] cancelled = {false};
        SubCommon.pose(caster, "sub.erguer");
        SubCommon.busy(caster, BANKAI_X + 6);
        // The white mist rises and the ice climbs over her whole body (the Bankai's body at -18 degrees); she stands
        // still and is hard to hurt while it does.
        Freeze.hold(caster, BANKAI_X);
        caster.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, BANKAI_X + 10, 4, false, false));
        SubNet.send(level, SubNet.ICE_ARMOR, caster.getId(), feet, Vec3.ZERO, armorEnd, 0, 0);
        SubCommon.sound(level, feet, SoundEvents.BEACON_ACTIVATE, 1.5f, 1.6f);
        SubCommon.sound(level, feet, SoundEvents.PLAYER_HURT_FREEZE, 1.5f, 0.6f);
        Tasks.later(BANKAI_X, () -> {
            if (guard.broken()) {
                cancelled[0] = true;
                SubNet.send(level, SubNet.ICE_ARMOR, caster.getId(), feet, Vec3.ZERO, 0, 0, 1);
                return;
            }
            // White under the ice, which thaws down to the neck and stays on her as armor.
            SubCommon.sound(level, feet, SoundEvents.GLASS_BREAK, 1.6f, 0.5f);
            SubCommon.sound(level, feet, SoundEvents.ELDER_GUARDIAN_CURSE, 1f, 1.5f);
            SubNet.send(level, SubNet.AURA, caster.getId(), feet, Vec3.ZERO, armorEnd - BANKAI_X, 2, 0);
            caster.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, armorEnd - BANKAI_X, 1, false, false));
        });
        Tasks.later(BANKAI_X + BANKAI_FROST, () -> {
            if (cancelled[0] || !caster.isAlive()) {
                return;
            }
            // The ground cracks and freezes out from her.
            SubNet.send(level, SubNet.FROST, 0, feet, Vec3.ZERO, radius, BANKAI_M - BANKAI_X - BANKAI_FROST + 50, 0);
            SubCommon.sound(level, feet, SoundEvents.GLASS_BREAK, 1.4f, 0.4f);
        });
        Tasks.later(BANKAI_W, () -> {
            if (cancelled[0] || !caster.isAlive()) {
                return;
            }
            // Absolute zero: everyone close turns into crystal.
            SubCommon.sound(level, feet, SoundEvents.WARDEN_SONIC_BOOM, 0.8f, 1.7f);
            SubCommon.sound(level, feet, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.5f, 0.6f);
            Set<Integer> hit = new HashSet<>();
            Tasks.add(age -> {
                double r = radius * SubCommon.smooth(age / 16f);
                for (LivingEntity t : Combat.inRadius(level, feet, r, caster)) {
                    if (hit.add(t.getId())) {
                        Combat.hurtMagic(caster, t, 17);
                        freeze(caster, level, t, 100, facing(caster, t), false, 10, 2.2f, -1);
                    }
                }
                return age >= 16;
            });
        });
        Tasks.later(BANKAI_M, () -> {
            if (!cancelled[0] && caster.isAlive()) {
                // Only a few seconds at absolute zero: she freezes whole again and goes back to normal, thawing.
                SubCommon.sound(level, caster.position(), SoundEvents.PLAYER_HURT_FREEZE, 1.2f, 0.7f);
            }
        });
        return true;
    }
}
