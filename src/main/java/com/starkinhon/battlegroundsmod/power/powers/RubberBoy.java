package com.starkinhon.battlegroundsmod.power.powers;

import java.util.List;

import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.powers.luffy.Luffy;
import com.starkinhon.battlegroundsmod.power.powers.luffy.LuffyMoves;

// Garoto Borracha (Luffy), the approved page (forja/luffy-gears), Stark 2026-11-11. Holding a key climbs the Gears (toque,
// G2, G3, G4) and letting go fires that key in the Gear reached: Z Pistol / Jet Pistol / Gigant Pistol / Kong Gun,
// X Gatling / Jet Gatling / Elephant Gatling / Kong Organ, C Bazooka / Jet Bazooka / Grizzly Magnum / Leo Bazooka,
// V Rifle / Jet Mergulho / Fusen / King Kong Gun. B with the Tambores da Libertacao full: total Gear 5 for 1 minute
// (Bajrang Gun, Gatling do Nika, Palma do Nika, Kaminari), then hunger and no stamina. See Luffy and LuffyMoves.
public final class RubberBoy {
    private RubberBoy() {
    }

    public static Power create() {
        return new Power(Luffy.ID, 0xC0392B, 1.15f, List.of(
                new Ability(20, 0, LuffyMoves::z),
                new Ability(70, 0, LuffyMoves::x),
                new Ability(60, 0, LuffyMoves::c),
                new Ability(240, 0, LuffyMoves::v),
                new Ability(20, 0, LuffyMoves::b)));
    }
}
