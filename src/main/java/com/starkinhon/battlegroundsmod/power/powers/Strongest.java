package com.starkinhon.battlegroundsmod.power.powers;

import java.util.List;

import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.powers.gojo.InfiniteVoid;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

// O Mais Forte (Gojo). V is the ult, Expansão de Domínio: Vazio Infinito. Z (Azul), X (Vermelho) and C (Roxo) are
// still to come: for now they only say so and cost nothing.
public final class Strongest {
    private Strongest() {
    }

    public static Power create() {
        return new Power("strongest", 0x4FC3FF, 1.25f, List.of(
                new Ability(0, 0, Strongest::soon),
                new Ability(0, 0, Strongest::soon),
                new Ability(0, 0, Strongest::soon),
                new Ability(900, 0, InfiniteVoid::use)));
    }

    private static boolean soon(LivingEntity caster, net.minecraft.server.level.ServerLevel level) {
        if (caster instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("message.battlegroundsmod.soon"), true);
        }
        return false;
    }
}
