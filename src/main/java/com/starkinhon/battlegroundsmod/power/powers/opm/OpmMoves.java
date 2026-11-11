package com.starkinhon.battlegroundsmod.power.powers.opm;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

// The keys: in the Modo Serio each is its serious move, otherwise the normal one.
public final class OpmMoves {
    private OpmMoves() {
    }

    public static boolean z(LivingEntity caster, ServerLevel level) {
        return Opm.serious(caster) ? OpmSerious.soco(caster, level) : OpmNormal.socos(caster, level);
    }

    public static boolean x(LivingEntity caster, ServerLevel level) {
        return Opm.serious(caster) ? OpmSerious.saltos(caster, level) : OpmNormal.gancho(caster, level);
    }

    public static boolean c(LivingEntity caster, ServerLevel level) {
        return Opm.serious(caster) ? OpmSerious.mesa(caster, level) : OpmNormal.chute(caster, level);
    }

    // V: turns the Modo Serio on (the full bar), or in it the Espirro.
    public static boolean v(LivingEntity caster, ServerLevel level) {
        if (Opm.serious(caster)) {
            return OpmSerious.espirro(caster, level);
        }
        Opm.track(caster);
        Opm.turnSerious(caster, level);
        return true;
    }

    public static boolean b(LivingEntity caster, ServerLevel level) {
        return OpmNormal.counter(caster, level);
    }
}
