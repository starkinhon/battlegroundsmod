package com.starkinhon.battlegroundsmod.power.powers.uchiha;

import java.util.HashMap;
import java.util.Map;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Cone;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.PowerData;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// Katon: a huge stream of fire from the mouth for about 5 seconds. The player can aim while breathing.
// Gameplay lives here, visuals in UchihaFx so they can be swapped for Photon later.
public final class Katon {
    public static final int DURATION = 100;
    // The fire travels out over the first ticks instead of appearing at full length.
    private static final int GROW_TICKS = 8;
    private static final double MAX_LENGTH = 14;
    private static final double START_RADIUS = 0.4;
    private static final double END_RADIUS = 6.4;
    // Each target is burned at most once per interval, so the damage does not depend on the tick rate.
    private static final int HIT_INTERVAL = 5;
    private static final float DAMAGE = 1.5f;
    private static final int SLOW_TICKS = 30;

    private Katon() {
    }

    public static boolean use(ServerPlayer player, ServerLevel level) {
        PowerData data = player.getData(ModAttachments.POWER);
        String power = data.power;
        data.busyTicks = DURATION;
        // The caster is rooted while breathing fire, but can still turn to aim.
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, DURATION, 2, false, false, false));
        UchihaFx.katonStart(level, mouth(player));
        Map<LivingEntity, Integer> lastHit = new HashMap<>();
        Tasks.add(age -> {
            if (!player.isAlive() || player.isRemoved() || !power.equals(player.getData(ModAttachments.POWER).power)) {
                return true;
            }
            double length = MAX_LENGTH * Math.min(1, (age + 1) / (double) GROW_TICKS);
            Cone cone = new Cone(level, player, mouth(player), player.getLookAngle(), length, MAX_LENGTH, START_RADIUS, END_RADIUS);
            UchihaFx.katonStream(level, cone, age);
            for (LivingEntity target : cone.targets()) {
                Integer last = lastHit.get(target);
                if (last == null || age - last >= HIT_INTERVAL) {
                    lastHit.put(target, age);
                    burn(player, target, cone);
                }
            }
            if (Cone.debug && age % 2 == 0) {
                cone.draw(Fx.dust(0xFFFFFF, 0.6f));
            }
            return age >= DURATION - 1;
        });
        return true;
    }

    private static void burn(ServerPlayer player, LivingEntity target, Cone cone) {
        // Keep the target in the fire: drop the vanilla hit knockback and only nudge them along the stream.
        Vec3 motion = target.getDeltaMovement();
        Combat.hurt(player, target, DAMAGE);
        Vec3 nudge = cone.dir().scale(0.05);
        target.setDeltaMovement(motion.x + nudge.x, motion.y, motion.z + nudge.z);
        target.hurtMarked = true;
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SLOW_TICKS, 0));
        target.igniteForSeconds(2);
        UchihaFx.katonBurn(player.serverLevel(), Combat.center(target));
    }

    // Just below the eyes and a bit in front, so the fire comes out of the mouth in third person too.
    private static Vec3 mouth(ServerPlayer player) {
        return player.getEyePosition().add(player.getLookAngle().scale(0.4)).add(0, -0.18, 0);
    }
}
