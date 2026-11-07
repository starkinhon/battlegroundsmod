package com.starkinhon.battlegroundsmod.power.powers;

import java.util.Arrays;
import java.util.List;

import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.powers.gojo.Azul;
import com.starkinhon.battlegroundsmod.power.powers.gojo.AzulFlash;
import com.starkinhon.battlegroundsmod.power.powers.gojo.InfiniteVoid;
import com.starkinhon.battlegroundsmod.power.powers.gojo.Roxo;
import com.starkinhon.battlegroundsmod.power.powers.gojo.VazioRelampago;
import com.starkinhon.battlegroundsmod.power.powers.gojo.Vermelho;

// O Mais Forte (Gojo). Z is Azul (tap: a small orb that locks where he aims and pulls; held: a big one that follows his
// aim, pulls hard and tears blocks out; see gojo/Azul; drawn with the realism look; the mix look is kept in the shader). V is the ult, Expansão de Domínio: Vazio Infinito. X is Vermelho
// (tap: a blast of repulsion straight ahead carving a tunnel; held: much longer and wider, fired when let go; Shift + X:
// behind the target, point blank; see gojo/Vermelho). C is Roxo (held: the Red and the Blue come round to the front and
// mix into the Purple, held on it grows, let go it is launched and erases everything in its path; a full Red fired into
// a parked full Blue makes the Unlimited Purple; see gojo/Roxo). Shift + C is still to be rethought.
// Shift + Z: the Blue on his palm pulls the target to him and he meets it with a Black Flash (see gojo/AzulFlash).
// Shift + V: the Expansão de 0,2 s, a counter that breaks every skill around and stuns whoever was charging (gojo/VazioRelampago).
public final class Strongest {
    private Strongest() {
    }

    public static Power create() {
        return new Power("strongest", 0x4FC3FF, 1.25f, List.of(
                new Ability(160, 0, Azul::use),
                new Ability(Vermelho.COOLDOWN, 0, Vermelho::use),
                new Ability(Roxo.COOLDOWN, 0, Roxo::use),
                new Ability(900, 0, InfiniteVoid::use)),
                // Shift: Z (the Blue pulls them in for a Black Flash), X (the Red point blank, behind the target) and V (the
                // Expansão de 0,2 s).
                Arrays.asList(new Ability(AzulFlash.COOLDOWN, 0, AzulFlash::use), new Ability(Vermelho.SHIFT_COOLDOWN, 0, Vermelho::useShift), null,
                        new Ability(VazioRelampago.COOLDOWN, 0, VazioRelampago::use)));
    }
}
