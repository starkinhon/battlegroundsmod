package com.starkinhon.battlegroundsmod.power.powers;

import java.util.List;

import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.powers.kokushibo.MoonFormSkill;
import com.starkinhon.battlegroundsmod.power.powers.kokushibo.YoruNoMiya;

// O Demonio da Lua (Kokushibo), Moon Breathing. Kit (Stark, 2026-11-07): Z, X, C and B the 1st, 2nd, 3rd and 5th Forms,
// V the 6th (the last form of the standard kit, the biggest and most destructive). All five look exactly as the pages Stark
// approved (forja/kokushibo-formas/). Later the ult "Alongar a Espada" (the sword swells into flesh and grows three
// blades) will turn the four into the awakened forms.
public final class MoonDemon {
    private MoonDemon() {
    }

    public static Power create() {
        return new Power("moon_demon", 0x9B4DCA, 1.1f, List.of(
                new Ability(80, 0, YoruNoMiya::use),
                new Ability(120, 0, MoonFormSkill::second).asBeatenCounter(),
                new Ability(160, 0, MoonFormSkill::third),
                new Ability(400, 0, MoonFormSkill::sixth),
                new Ability(240, 0, MoonFormSkill::fifth).asBeatenCounter()));
    }
}
