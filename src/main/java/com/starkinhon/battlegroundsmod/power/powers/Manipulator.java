package com.starkinhon.battlegroundsmod.power.powers;

import java.util.Arrays;
import java.util.List;

import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.powers.aizen.BlackBox;
import com.starkinhon.battlegroundsmod.power.powers.aizen.Hogyoku;
import com.starkinhon.battlegroundsmod.power.powers.aizen.KyokaSuigetsu;
import com.starkinhon.battlegroundsmod.power.powers.aizen.Reiatsu;
import com.starkinhon.battlegroundsmod.power.powers.aizen.UmDedo;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// O Manipulador (Aizen). Z the Black Box (Hado #90, Kurohitsugi: held to grow the box around the target, let go to shut
// it; see aizen/BlackBox), X Kyoka Suigetsu (a clone stays, he walks unseen; X again breaks it), C the Reiatsu (tap: a
// small pulse; held: it grows and crushes everyone to their knees), B Um Dedo (a counter: one finger stops the hit, the
// sword answers), V the special, the Evolucao do Hogyoku (cocoon, six wings; V again is the dash of cuts while it lasts).
// Shift + X is the white lightning.
public final class Manipulator {
    private Manipulator() {
    }

    public static Power create() {
        return new Power("manipulator", 0x8E44FF, 1.0f, List.of(
                new Ability(160, 0, BlackBox::use),
                // The real cooldown starts when the illusion breaks (KyokaSuigetsu).
                new Ability(20, 0, KyokaSuigetsu::use),
                new Ability(120, 0, Reiatsu::use),
                new Ability(100, 0, Hogyoku::use),
                new Ability(80, 0, UmDedo::use).asCounter()),
                Arrays.asList(null, Ability.player(50, 15, Manipulator::whiteLightning), null, null));
    }

    // X: a piercing beam of white lightning.
    private static boolean whiteLightning(ServerPlayer player, ServerLevel level) {
        Vec3 start = Combat.handPos(player);
        Vec3 end = Combat.raycastPoint(player, 32);
        Fx.line(level, ParticleTypes.END_ROD, start, end, 0.3);
        Fx.line(level, ParticleTypes.ELECTRIC_SPARK, start, end, 0.5);
        Fx.line(level, Fx.dust(0xFFFFFF, 1.5f), start, end, 0.25);
        Fx.burst(level, ParticleTypes.FLASH, end, 1, 0, 0);
        Fx.sound(level, start, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.5f, 1.5f);
        for (LivingEntity target : Combat.alongLine(level, start, end, 0.6, player)) {
            Combat.hurt(player, target, 22f);
            Fx.burst(level, ParticleTypes.ELECTRIC_SPARK, Combat.center(target), 20, 0.4, 0.3);
        }
        return true;
    }
}
