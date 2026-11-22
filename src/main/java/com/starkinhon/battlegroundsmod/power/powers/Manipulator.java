// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.power.powers;

import java.util.Arrays;
import java.util.List;

import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.powers.aizen.BlackBox;
import com.starkinhon.battlegroundsmod.power.powers.aizen.Hogyoku;
import com.starkinhon.battlegroundsmod.power.powers.aizen.KyokaSuigetsu;
import com.starkinhon.battlegroundsmod.power.powers.aizen.Raikoho;
import com.starkinhon.battlegroundsmod.power.powers.aizen.Reiatsu;
import com.starkinhon.battlegroundsmod.power.powers.aizen.Sokatsui;
import com.starkinhon.battlegroundsmod.power.powers.aizen.UmDedo;

// O Manipulador (Aizen), the kit remade from the approved page (forja/aizen-kit, Stark 2026-11-22). Z the Black Box
// (Hado #90, held to grow the box round the target, let go to shut it; aizen/BlackBox), X Kyoka Suigetsu (a clone stays,
// he walks unseen; X again breaks it), C Hado 63 Raikoho (a golden bolt that blows up where it lands), B Um Dedo + Danku
// (one counter: a blow from up close stops on a finger and the cut that already happened shows up behind; a technique
// hits a pane of glass), V the Evolucao do Hogyoku (cocoon, six wings; inside it V again is the Fragor and Shift+V the
// Hado 99, once). Shift+X is Hado 33 Sokatsui (blue fire), Shift+B the Reiatsu (held pressure that crushes and explodes).
// Cooldowns on the divine souls' scale (catalogo-skills-divinas): the big power paid by long recharges.
public final class Manipulator {
    private Manipulator() {
    }

    public static Power create() {
        return new Power("manipulator", 0x8E44FF, 1.0f, List.of(
                new Ability(600, 0, BlackBox::use),
                // The real cooldown starts when the illusion breaks (KyokaSuigetsu).
                new Ability(20, 0, KyokaSuigetsu::use),
                new Ability(700, 0, Raikoho::use),
                new Ability(2400, 0, Hogyoku::use),
                new Ability(600, 0, UmDedo::use).asCounter()),
                // Shift+V: only inside the evolved form (Hogyoku.press runs it there; outside it only says so), so the
                // key itself spends nothing.
                Arrays.asList(null, new Ability(240, 0, Sokatsui::use), null, new Ability(0, 0, (caster, level) -> false),
                        new Ability(1000, 0, Reiatsu::use)));
    }
}
