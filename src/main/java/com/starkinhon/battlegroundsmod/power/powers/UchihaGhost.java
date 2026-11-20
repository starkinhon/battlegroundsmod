// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.power.powers;

import java.util.List;

import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.powers.uchiha.Agarrao;
import com.starkinhon.battlegroundsmod.power.powers.uchiha.Izanami;
import com.starkinhon.battlegroundsmod.power.powers.uchiha.Katon;
import com.starkinhon.battlegroundsmod.power.powers.uchiha.KatonGigante;
import com.starkinhon.battlegroundsmod.power.powers.uchiha.Mangekyo;
import com.starkinhon.battlegroundsmod.power.powers.uchiha.Meteoro;
import com.starkinhon.battlegroundsmod.power.powers.uchiha.Susanoo;
import com.starkinhon.battlegroundsmod.power.powers.uchiha.SusanooCorte;

// Fantasma dos Uchiha (Madara). Being remade move by move: Z is the new Katon, X the Mangekyo, C the Susanoo counter, V the Meteoro,
// B the neck grab. With Shift: Z the giant fireball, X the Izanami, C the Susanoo slash, V the double meteor.
public final class UchihaGhost {
    private UchihaGhost() {
    }

    public static Power create() {
        return new Power("uchiha_ghost", 0xD12F2F, 1.1f, List.of(
                new Ability(500, 30, Katon::use),
                new Ability(800, 35, Mangekyo::use),
                new Ability(500, 30, Susanoo::use).asCounter(),
                new Ability(1200, 0, Meteoro::use),
                new Ability(600, 0, Agarrao::use)), List.of(
                new Ability(700, 0, KatonGigante::use),
                new Ability(PowerData.SPENT, 0, Izanami::use),
                new Ability(1400, 0, SusanooCorte::use),
                new Ability(3600, 0, Meteoro::useDouble)));
    }
}
