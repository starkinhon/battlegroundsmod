package com.starkinhon.battlegroundsmod.power.powers;

import java.util.List;

import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.powers.gojo.Azul;
import com.starkinhon.battlegroundsmod.power.powers.gojo.InfiniteVoid;
import com.starkinhon.battlegroundsmod.power.powers.gojo.Vermelho;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

// O Mais Forte (Gojo). Z is Azul (tap: a small orb that locks where he aims and pulls; held: a big one that follows his
// aim, pulls hard and tears blocks out; see gojo/Azul; drawn with the realism look; the mix look is kept in the shader). V is the ult, Expansão de Domínio: Vazio Infinito. X is Vermelho
// (for now only its look: the Red forms at his fingertip, see gojo/Vermelho). C (Roxo) is still to come: for now it only
// says so and costs nothing.
public final class Strongest {
    private Strongest() {
    }

    public static Power create() {
        return new Power("strongest", 0x4FC3FF, 1.25f, List.of(
                new Ability(160, 0, Azul::use),
                new Ability(Vermelho.COOLDOWN, 0, Vermelho::use),
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
