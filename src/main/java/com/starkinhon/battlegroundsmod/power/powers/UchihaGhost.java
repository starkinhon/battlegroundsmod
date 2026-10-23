package com.starkinhon.battlegroundsmod.power.powers;

import java.util.List;

import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.powers.uchiha.Agarrao;
import com.starkinhon.battlegroundsmod.power.powers.uchiha.Katon;
import com.starkinhon.battlegroundsmod.power.powers.uchiha.Mangekyo;
import com.starkinhon.battlegroundsmod.power.powers.uchiha.Meteoro;
import com.starkinhon.battlegroundsmod.power.powers.uchiha.Susanoo;

// Fantasma dos Uchiha (Madara). Being remade move by move: Z is the new Katon, X the Mangekyo, C the Susanoo counter, V the Meteoro,
// B the neck grab.
public final class UchihaGhost {
    private UchihaGhost() {
    }

    public static Power create() {
        return new Power("uchiha_ghost", 0xD12F2F, 1.1f, List.of(
                new Ability(300, 30, Katon::use),
                new Ability(400, 35, Mangekyo::use),
                new Ability(300, 30, Susanoo::use).asCounter(),
                new Ability(100, 0, Meteoro::use),
                new Ability(240, 0, Agarrao::use)));
    }
}
