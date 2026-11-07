package com.starkinhon.battlegroundsmod.power.powers.sub;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

// Ichigo (SubAlma of Bleach, weak for now): an aggressive tank, the Visualizador's Ichigo tab.
// Z Getsuga Tensho: tap, a quick white-blue crescent; held, the reiatsu grows on the blade and the crescent comes out
//   huge, scraping the ground. In Bankai it is the Kuroi Getsuga: black with a red rim, curving toward the target.
// Shift + Z: Getsuga into the ground, the energy bursts out in rifts and throws whoever is close.
// X Guard with the Zangetsu: for a moment he takes hits on the blade (most of the damage gone) and each one blocked makes
//   his next Getsuga stronger.
// C Hollow Mask: less damage taken and more strength for a short while.
// V Bankai, Tensa Zangetsu: much faster, and the Getsuga turns into the Kuroi Getsuga.
public final class Ichigo {
    public static final String BANKAI = "ichigo_bankai";
    public static final String MASK = "ichigo_mask";
    public static final String GUARD = "ichigo_guard";
    public static final String POWERED = "ichigo_powered";

    private Ichigo() {
    }

    public static Power create() {
        return new Power("ichigo", 0x7FC8FF, 1.1f, List.of(
                new Ability(70, 0, Ichigo::getsuga),
                new Ability(110, 0, Ichigo::guard).asCounter(),
                new Ability(600, 0, Ichigo::mask),
                new Ability(100, 0, Ichigo::bankai)),
                Arrays.asList(new Ability(120, 0, Ichigo::groundGetsuga), null, null, null));
    }

    // ---------- Z: Getsuga Tensho ----------

    private static boolean getsuga(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        boolean kuroi = SubCommon.hasState(caster, BANKAI);
        int decide = 6;
        int chargeTicks = 40;
        int bossHold = SubCommon.RANDOM.nextBoolean() ? 0 : 20 + SubCommon.RANDOM.nextInt(30);
        SubCommon.pose(caster, "sub.erguer");
        float[] charge = {0};
        boolean[] charging = {false};
        Tasks.add(age -> {
            if (guard.broken()) {
                return true;
            }
            SubCommon.busy(caster, 2);
            boolean held = SubCommon.held(caster, age, bossHold);
            if (!charging[0]) {
                if (age + 1 < decide) {
                    return false;
                }
                if (!held) {
                    fire(caster, level, 0, kuroi);
                    return true;
                }
                charging[0] = true;
                SubCommon.sound(level, caster.position(), SoundEvents.BEACON_POWER_SELECT, 1f, 1.4f);
            }
            charge[0] = Math.min(1, (age - decide) / (float) chargeTicks);
            SubNet.send(level, SubNet.CHARGE, caster.getId(), caster.position(), Vec3.ZERO, charge[0], kuroi ? 1 : 0, 0);
            if (age % 8 == 0) {
                SubCommon.sound(level, caster.position(), SoundEvents.BEACON_AMBIENT, 0.8f, 1 + charge[0]);
            }
            caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 3, 1, false, false));
            if (held && age < decide + chargeTicks + 30) {
                return false;
            }
            fire(caster, level, charge[0], kuroi);
            return true;
        });
        return true;
    }

    private static void fire(LivingEntity caster, ServerLevel level, float charge, boolean kuroi) {
        boolean powered = SubCommon.hasState(caster, POWERED);
        if (powered) {
            SubCommon.clearState(caster, POWERED);
        }
        float boost = powered ? 1.5f : 1;
        float size = (3.2f + 2.8f * charge) * (powered ? 1.3f : 1);
        double speed = 1.6 - 0.2 * charge;
        double range = 32 + 8 * charge;
        float damage = (8 + 10 * charge) * boost * (kuroi ? 1.3f : 1);
        boolean ground = charge > 0.6f;
        SubCommon.pose(caster, "sub.corte");
        SubCommon.sound(level, caster.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.6f, 0.7f);
        SubCommon.sound(level, caster.position(), kuroi ? SoundEvents.WITHER_SHOOT : SoundEvents.WARDEN_SONIC_BOOM, 0.8f, kuroi ? 0.8f : 1.6f);
        int id = SubNet.newId();
        Vec3 dir0 = caster.getLookAngle();
        Vec3[] pos = {caster.getEyePosition().add(dir0.scale(1.2)).add(0, ground ? -0.5 : -0.2, 0)};
        Vec3[] dir = {dir0};
        Set<Integer> hit = new HashSet<>();
        LivingEntity homing = kuroi ? SubCommon.aimed(caster, 30) : null;
        Tasks.add(age -> {
            if (kuroi && homing != null && homing.isAlive()) {
                // The Kuroi curves toward the target it was aimed near.
                Vec3 want = Combat.center(homing).subtract(pos[0]).normalize();
                dir[0] = dir[0].add(want.subtract(dir[0]).scale(0.18)).normalize();
            }
            Vec3 next = pos[0].add(dir[0].scale(speed));
            boolean wall = !level.noCollision(new net.minecraft.world.phys.AABB(next, next).inflate(0.3)) && !ground;
            for (LivingEntity t : Combat.alongLine(level, pos[0], next, size * 0.45, caster)) {
                if (hit.add(t.getId())) {
                    Combat.hurt(caster, t, damage);
                    Combat.push(t, dir[0], 1.2 + charge, 0.35);
                    Stun.apply(t, 10);
                    SubNet.send(level, SubNet.RIP, 0, Combat.center(t), dir[0], size * 0.8f, kuroi ? 1 : 0, 0);
                    SubCommon.sound(level, t.position(), SoundEvents.PLAYER_ATTACK_CRIT, 1.2f, 0.8f);
                }
            }
            if (ground && age % 2 == 0) {
                Vec3 g = SubCommon.ground(level, next);
                BlockState below = level.getBlockState(net.minecraft.core.BlockPos.containing(g.subtract(0, 0.2, 0)));
                if (!below.isAir()) {
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, below), g.x, g.y + 0.1, g.z, 10, 0.6, 0.1, 0.6, 0.2);
                }
            }
            pos[0] = next;
            boolean end = wall || age * speed >= range;
            SubNet.send(level, SubNet.GETSUGA, id, pos[0], dir[0], size, kuroi ? 1 : 0, end ? 1 : 0);
            if (end) {
                SubCommon.sound(level, pos[0], SoundEvents.GENERIC_EXPLODE.value(), 0.8f, 1.4f);
            }
            return end;
        });
    }

    // ---------- Shift + Z: Getsuga into the ground ----------

    private static boolean groundGetsuga(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        boolean kuroi = SubCommon.hasState(caster, BANKAI);
        SubCommon.pose(caster, "sub.cravar");
        SubCommon.busy(caster, 14);
        Vec3 dir = SubCommon.flat(caster.getLookAngle());
        Tasks.later(8, () -> {
            if (guard.broken()) {
                return;
            }
            Vec3 center = SubCommon.ground(level, caster.position().add(dir.scale(1.5)));
            SubNet.send(level, SubNet.RIFT, 0, center, dir, 6.5f, 8, kuroi ? 1 : 0);
            SubCommon.sound(level, center, SoundEvents.GENERIC_EXPLODE.value(), 1.4f, 0.9f);
            SubCommon.sound(level, center, SoundEvents.WARDEN_SONIC_BOOM, 0.6f, 1.5f);
            BlockState below = level.getBlockState(net.minecraft.core.BlockPos.containing(center.subtract(0, 0.2, 0)));
            if (!below.isAir()) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, below), center.x, center.y + 0.3, center.z, 120, 3, 0.4, 3, 0.4);
            }
            for (LivingEntity t : Combat.inRadius(level, center, 6.5, caster)) {
                Combat.hurt(caster, t, kuroi ? 11 : 8);
                Combat.pushFrom(t, center, 0.8, 0.8);
                Stun.apply(t, 16);
            }
        });
        return true;
    }

    // ---------- X: Guard ----------

    private static boolean guard(LivingEntity caster, ServerLevel level) {
        SubCommon.setState(caster, GUARD, 30);
        SubCommon.pose(caster, "sub.defender");
        SubCommon.busy(caster, 10);
        SubCommon.sound(level, caster.position(), SoundEvents.ARMOR_EQUIP_IRON.value(), 1f, 0.8f);
        return true;
    }

    // Hits on the blade lose most of their damage; each one makes the next Getsuga stronger.
    static void onIncomingDamage(LivingEntity target, LivingIncomingDamageEvent event, Entity attacker) {
        if (!SubCommon.hasState(target, GUARD) || !(target.level() instanceof ServerLevel level)) {
            return;
        }
        event.setAmount(event.getAmount() * 0.2f);
        SubCommon.setState(target, POWERED, 140);
        SubCommon.sound(level, target.position(), SoundEvents.ANVIL_LAND, 0.7f, 1.6f);
        Vec3 at = attacker != null ? Combat.center(target).add(attacker.position().subtract(target.position()).normalize().scale(0.6))
                : Combat.center(target);
        SubNet.send(level, SubNet.IMPACT, 0, at, 1.4f, SubNet.COLOR_BLUE);
        SubNet.send(level, SubNet.CHARGE, target.getId(), target.position(), Vec3.ZERO, 0.8f, 0, 0);
    }

    // ---------- C: Hollow Mask ----------

    private static boolean mask(LivingEntity caster, ServerLevel level) {
        int ticks = 200;
        SubCommon.setState(caster, MASK, ticks);
        caster.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, ticks, 1, false, false));
        caster.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, ticks, 1, false, false));
        SubNet.send(level, SubNet.AURA, caster.getId(), caster.position(), Vec3.ZERO, ticks, 0, 0);
        SubNet.send(level, SubNet.IMPACT, 0, Combat.center(caster), 2.5f, SubNet.COLOR_RED);
        SubCommon.pose(caster, "sub.mascara");
        SubCommon.busy(caster, 12);
        SubCommon.sound(level, caster.position(), SoundEvents.WITHER_AMBIENT, 1.2f, 0.7f);
        return true;
    }

    // ---------- V: Bankai ----------

    private static boolean bankai(LivingEntity caster, ServerLevel level) {
        int ticks = 600;
        SubCommon.setState(caster, BANKAI, ticks);
        caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, 2, false, false));
        caster.addEffect(new MobEffectInstance(MobEffects.JUMP, ticks, 1, false, false));
        SubNet.send(level, SubNet.AURA, caster.getId(), caster.position(), Vec3.ZERO, ticks, 1, 0);
        SubNet.send(level, SubNet.IMPACT, 0, Combat.center(caster), 4f, SubNet.COLOR_RED);
        SubCommon.pose(caster, "sub.erguer");
        SubCommon.busy(caster, 16);
        SubCommon.sound(level, caster.position(), SoundEvents.WITHER_SPAWN, 0.8f, 1.3f);
        level.sendParticles(ParticleTypes.SMOKE, caster.getX(), caster.getY() + 1, caster.getZ(), 60, 1, 1, 1, 0.1);
        return true;
    }
}
