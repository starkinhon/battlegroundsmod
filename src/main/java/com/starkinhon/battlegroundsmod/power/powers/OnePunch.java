package com.starkinhon.battlegroundsmod.power.powers;

import java.util.List;

import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.powers.opm.Opm;
import com.starkinhon.battlegroundsmod.power.powers.opm.OpmMoves;

// Cara de um Soco (Saitama), the approved page (forja/saitama-soco-serio), Stark 2026-11-11. Two rows of the same keys:
// normal (bored, weak on purpose next to the serious moves, but his normal blows hit harder than most) and, with the Barra
// Seria full and V pressed, the Modo Serio, where each key is one serious move and using one ends the mode.
// Z Socos Consecutivos / Soco Serio, X Gancho / Saltos Laterais Serios, C Chute / Virada de Mesa Seria, V Modo Serio /
// Espirro Serio, B Counter. Passive Pedra Viva: no knockback from anything, high resistance (see Opm).
public final class OnePunch {
    private OnePunch() {
    }

    public static Power create() {
        return new Power(Opm.ID, 0xF2C94C, 1.35f, List.of(
                new Ability(60, 0, OpmMoves::z),
                new Ability(90, 0, OpmMoves::x),
                new Ability(110, 0, OpmMoves::c),
                new Ability(20, 0, OpmMoves::v),
                new Ability(100, 0, OpmMoves::b).asBeatenCounter()));
    }
}
