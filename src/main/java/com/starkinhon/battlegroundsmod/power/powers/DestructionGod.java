package com.starkinhon.battlegroundsmod.power.powers;

import java.util.Arrays;
import java.util.List;

import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.powers.bills.DestructionBop;
import com.starkinhon.battlegroundsmod.power.powers.bills.DestructionEnergy;
import com.starkinhon.battlegroundsmod.power.powers.bills.DestructionTouch;
import com.starkinhon.battlegroundsmod.power.powers.bills.Esfera;
import com.starkinhon.battlegroundsmod.power.powers.bills.Hakai;
import com.starkinhon.battlegroundsmod.power.powers.bills.Hakaizinho;
import com.starkinhon.battlegroundsmod.power.powers.bills.DestructionSphere;

// Deus da Destruição (Bills). Z is the Destruction Energy (held to charge, let go to throw) and Shift + Z its rain of
// balls. X is the Destruction Bop (flick up, slap down). C is the Esfera da Destruição (a tiny sphere held at his
// finger, let go to fly up and chase the target, that implodes into a giant sun and blows up twice, rewinding in
// between); Shift + C is its space version (the trip to outer space and the sun falling from 300 blocks up). V is the
// Hakai (the hakai ball when the target still has too much life), Shift + V the hakaizinho. B is the Planetary
// Destruction (a tiny ball dropped from up high that blows up the ground in a giant plasma explosion).
public final class DestructionGod {
    private DestructionGod() {
    }

    public static Power create() {
        return new Power("destruction_god", 0x9B30FF, 1.3f, List.of(
                new Ability(80, 0, DestructionEnergy::use),
                new Ability(300, 0, DestructionBop::use),
                new Ability(400, 0, DestructionSphere::use),
                new Ability(600, 0, Hakai::use),
                new Ability(500, 0, DestructionTouch::use)),
                // Z, C and the ult have a Shift variant; Shift + X or B still use the normal skill.
                Arrays.asList(new Ability(500, 0, DestructionEnergy::rain), null, new Ability(400, 0, Esfera::use),
                        new Ability(600, 0, Hakaizinho::use)));
    }
}
