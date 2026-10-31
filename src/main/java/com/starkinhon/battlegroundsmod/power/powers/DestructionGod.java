package com.starkinhon.battlegroundsmod.power.powers;

import java.util.Arrays;
import java.util.List;

import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.powers.bills.DestructionBop;
import com.starkinhon.battlegroundsmod.power.powers.bills.DestructionEnergy;
import com.starkinhon.battlegroundsmod.power.powers.bills.Esfera;
import com.starkinhon.battlegroundsmod.power.powers.bills.Hakai;
import com.starkinhon.battlegroundsmod.power.powers.bills.Hakaizinho;

// Deus da Destruição (Bills). Being remade move by move, starting with the ult: C is the Esfera da Destruição and V the
// Hakai (the hakai ball when the target still has too much life), Shift + V the hakaizinho. Z is the Destruction
// Energy (held to charge, let go to throw) and Shift + Z its rain of balls. X is the Destruction Bop (flick up, slap
// down).
public final class DestructionGod {
    private DestructionGod() {
    }

    public static Power create() {
        return new Power("destruction_god", 0x9B30FF, 1.3f, List.of(
                new Ability(80, 0, DestructionEnergy::use),
                new Ability(300, 0, DestructionBop::use),
                new Ability(400, 0, Esfera::use),
                new Ability(600, 0, Hakai::use)),
                // Z and V have a Shift variant; Shift + X or C still use the normal skill.
                Arrays.asList(new Ability(500, 0, DestructionEnergy::rain), null, null, new Ability(600, 0, Hakaizinho::use)));
    }
}
