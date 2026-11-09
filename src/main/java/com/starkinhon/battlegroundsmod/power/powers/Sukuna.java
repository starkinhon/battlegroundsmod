package com.starkinhon.battlegroundsmod.power.powers;

import java.util.Arrays;
import java.util.List;

import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sukuna.Clivar;
import com.starkinhon.battlegroundsmod.power.powers.sukuna.Cravada;
import com.starkinhon.battlegroundsmod.power.powers.sukuna.Desmantelar;
import com.starkinhon.battlegroundsmod.power.powers.sukuna.DesmantelarShift;
import com.starkinhon.battlegroundsmod.power.powers.sukuna.Fuga;
import com.starkinhon.battlegroundsmod.power.powers.sukuna.ShrineDomain;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

// Ryomen Sukuna, the King of Curses (Divine Soul). Ported technique by technique from the 3D previews Stark approves:
// Z Desmantelar (解: combo, chant, the World Cutting Slash with the Modelo bar full), Shift+Z the cuts born on the
// opponent, X Clivar (捌: sprint, grab by the neck, the grid), C the Fuga (■開), V the Expansão de Domínio: Santuário
// Malevolente (ShrineDomain), B the cravada (the old Shift+X: uppercut on the run, caught by the neck up high, slammed
// down, the web of cuts that blows the ground up).
public final class Sukuna {
    public static final String ID = "sukuna";

    private Sukuna() {
    }

    public static Power create() {
        return new Power(ID, 0xC8321E, 1.2f, List.of(
                new Ability(0, 0, Desmantelar::use),
                new Ability(0, 0, Clivar::use),
                new Ability(900, 0, Fuga::use),
                new Ability(900, 0, ShrineDomain::use),
                new Ability(0, 0, Cravada::use)),
                Arrays.asList(new Ability(240, 0, DesmantelarShift::use), null, null, null, null));
    }

    private static boolean soon(LivingEntity caster, ServerLevel level) {
        SubCommon.warn(caster, "soon");
        return false;
    }
}
