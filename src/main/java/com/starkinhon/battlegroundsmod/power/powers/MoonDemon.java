package com.starkinhon.battlegroundsmod.power.powers;

import java.util.List;

import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.powers.kokushibo.YoruNoMiya;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;

// O Demonio da Lua (Kokushibo), Moon Breathing. Planned kit (Stark, 2026-11-07): Z, X, C and B the 1st, 2nd, 3rd and
// 5th Forms; the ult (V) is "Alongar a Espada" (the sword swells into flesh and grows three blades) and while it lasts the
// four turn into the awakened forms (6th and up). Only the 1st Form is in so far; the others say so on screen.
public final class MoonDemon {
    private MoonDemon() {
    }

    public static Power create() {
        return new Power("moon_demon", 0x9B4DCA, 1.1f, List.of(
                new Ability(80, 0, YoruNoMiya::use),
                new Ability(20, 0, MoonDemon::soon),
                new Ability(20, 0, MoonDemon::soon),
                new Ability(20, 0, MoonDemon::soon)));
    }

    // Not made yet: tell the player instead of failing quietly.
    private static boolean soon(net.minecraft.world.entity.LivingEntity caster, net.minecraft.server.level.ServerLevel level) {
        SubCommon.warn(caster, "moon_demon.soon");
        return false;
    }
}
