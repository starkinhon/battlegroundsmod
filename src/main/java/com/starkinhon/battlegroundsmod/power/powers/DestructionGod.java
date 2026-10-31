package com.starkinhon.battlegroundsmod.power.powers;

import java.util.Arrays;
import java.util.List;

import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.powers.bills.DestructionEnergy;
import com.starkinhon.battlegroundsmod.power.powers.bills.Esfera;
import com.starkinhon.battlegroundsmod.power.powers.bills.Hakai;
import com.starkinhon.battlegroundsmod.power.powers.bills.Hakaizinho;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// Deus da Destruição (Bills). Being remade move by move, starting with the ult: C is the Esfera da Destruição and V the
// Hakai (the hakai ball when the target still has too much life), Shift + V the hakaizinho. Z is the Destruction
// Energy (held to charge, let go to throw) and Shift + Z its rain of balls. X is still the first prototype.
public final class DestructionGod {
    private static final int PURPLE = 0x9B30FF;

    private DestructionGod() {
    }

    public static Power create() {
        return new Power("destruction_god", 0x9B30FF, 1.3f, List.of(
                new Ability(80, 0, DestructionEnergy::use),
                Ability.player(240, 35, DestructionGod::destructionTouch),
                new Ability(400, 0, Esfera::use),
                new Ability(600, 0, Hakai::use)),
                // Z and V have a Shift variant; Shift + X or C still use the normal skill.
                Arrays.asList(new Ability(500, 0, DestructionEnergy::rain), null, null, new Ability(600, 0, Hakaizinho::use)));
    }

    // X: marks a close target, holds them for a moment, then erases them. Ignores armor.
    private static boolean destructionTouch(ServerPlayer player, ServerLevel level) {
        LivingEntity target = Combat.raycastTarget(player, 7);
        if (target == null) {
            player.displayClientMessage(Component.translatable("message.battlegroundsmod.no_target"), true);
            return false;
        }
        int delay = 15;
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, delay + 5, 9, false, false));
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, delay + 5, 0, false, false));
        Fx.sound(level, target.position(), SoundEvents.ELDER_GUARDIAN_CURSE, 1f, 1.2f);
        Tasks.add(age -> {
            if (!target.isAlive()) {
                return true;
            }
            Vec3 base = target.position();
            double height = target.getBbHeight() * age / delay;
            for (int i = 0; i < 3; i++) {
                double a = age * 0.6 + i * Math.PI * 2 / 3;
                Fx.burst(level, Fx.dust(PURPLE, 1.4f), base.add(Math.cos(a) * 0.8, height, Math.sin(a) * 0.8), 1, 0, 0);
            }
            if (age < delay) {
                return false;
            }
            Vec3 pos = Combat.center(target);
            Combat.hurtMagic(player, target, 16f);
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 1));
            Fx.burst(level, ParticleTypes.ASH, pos, 120, 0.6, 0.05);
            Fx.burst(level, Fx.dust(PURPLE, 2f), pos, 50, 0.6, 0.05);
            Fx.burst(level, ParticleTypes.REVERSE_PORTAL, pos, 60, 0.4, 0.4);
            Fx.sound(level, pos, SoundEvents.WITHER_SHOOT, 1f, 0.5f);
            return true;
        });
        return true;
    }
}
