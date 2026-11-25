// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.power.powers;

import java.util.Arrays;
import java.util.List;

import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.powers.opm.Opm;
import com.starkinhon.battlegroundsmod.power.powers.opm.OpmMoves;

// Cara de um Soco (Saitama), the approved page (forja/saitama-soco-serio), Stark 2026-11-11, remade 2026-11-25 (forja/
// saitama-jogavel: "agora sim isso e uma alma divina"). Two rows of the same keys: normal (a bored face, but blows of a divine
// soul: ~120 for the barrage, 50, 45, 80) and, with the Barra Seria full and V pressed, the Modo Serio, where each key is one
// serious move. Z Socos Consecutivos / Soco Serio, X Gancho / Socos Omnidirecionais Serios, C Chute / Virada de Mesa Seria,
// V Modo Serio / Espirro Serio, B Counter. Shift+Z Soco Normal (a fist of wind, 25 blocks), Shift+C Pulo + Soco no Chao.
// Passive: no more Pedra Viva (he is pushed like anyone); high resistance, and he can hit back out of a stun with the base
// fighting (Opm.shrugOff: stamina and 8 s between uses).
public final class OnePunch {
    private OnePunch() {
    }

    public static Power create() {
        return new Power(Opm.ID, 0xF2C94C, 1.35f, List.of(
                new Ability(120, 0, OpmMoves::z),
                new Ability(160, 0, OpmMoves::x),
                new Ability(180, 0, OpmMoves::c),
                new Ability(20, 0, OpmMoves::v),
                new Ability(300, 0, OpmMoves::b).asBeatenCounter()),
                Arrays.asList(new Ability(140, 0, OpmMoves::shiftZ), null, new Ability(240, 0, OpmMoves::shiftC), null, null));
    }
}
