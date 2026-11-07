package com.starkinhon.battlegroundsmod.power.powers;

import java.util.List;

import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;

// The Fighter mobs' "soul": no techniques at all, only the base combat every fighter has (punches, strong, barrage,
// dash attack, dashes and dodges). Hidden: not in the power book, no soul crystal, not in /soul power.
public final class Fighter {
    private Fighter() {
    }

    public static Power create() {
        Ability none = new Ability(20, 0, (caster, level) -> false);
        return new Power("fighter", 0xD8D8D8, 1.0f, List.of(none, none, none, none));
    }
}
