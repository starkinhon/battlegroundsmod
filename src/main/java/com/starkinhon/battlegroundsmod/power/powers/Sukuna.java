package com.starkinhon.battlegroundsmod.power.powers;

import java.util.Arrays;
import java.util.List;

import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sukuna.Desmantelar;
import com.starkinhon.battlegroundsmod.power.powers.sukuna.DesmantelarShift;
import com.starkinhon.battlegroundsmod.power.powers.sukuna.Fuga;
import com.starkinhon.battlegroundsmod.power.powers.sukuna.ShrineDomain;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

// Ryomen Sukuna, the King of Curses (Divine Soul). Ported technique by technique from the 3D previews Stark approves:
// Z Desmantelar (解: combo, chant, the World Cutting Slash with the Modelo bar full), Shift+Z the cuts born on the
// opponent, C the Fuga (■開), V the Expansão de Domínio: Santuário Malevolente (ShrineDomain). X says "soon" until its
// turn comes.
public final class Sukuna {
    public static final String ID = "sukuna";

    private Sukuna() {
    }

    public static Power create() {
        return new Power(ID, 0xC8321E, 1.2f, List.of(
                new Ability(0, 0, Desmantelar::use),
                new Ability(20, 0, Sukuna::soon),
                new Ability(900, 0, Fuga::use),
                new Ability(900, 0, ShrineDomain::use)),
                Arrays.asList(new Ability(240, 0, DesmantelarShift::use), null, null, null));
    }

    private static boolean soon(LivingEntity caster, ServerLevel level) {
        SubCommon.warn(caster, "soon");
        return false;
    }
}
