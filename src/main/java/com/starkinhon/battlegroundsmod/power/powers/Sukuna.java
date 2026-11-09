package com.starkinhon.battlegroundsmod.power.powers;

import java.util.List;

import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sukuna.Fuga;
import com.starkinhon.battlegroundsmod.power.powers.sukuna.ShrineDomain;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

// Ryomen Sukuna, the King of Curses (Divine Soul). Ported technique by technique from the 3D previews Stark approves:
// C, the Fuga (■開), and V, the Expansão de Domínio: Santuário Malevolente (ShrineDomain). Z and X say "soon" until
// their turn comes.
public final class Sukuna {
    public static final String ID = "sukuna";

    private Sukuna() {
    }

    public static Power create() {
        return new Power(ID, 0xC8321E, 1.2f, List.of(
                new Ability(20, 0, Sukuna::soon),
                new Ability(20, 0, Sukuna::soon),
                new Ability(900, 0, Fuga::use),
                new Ability(900, 0, ShrineDomain::use)), List.of());
    }

    private static boolean soon(LivingEntity caster, ServerLevel level) {
        SubCommon.warn(caster, "soon");
        return false;
    }
}
