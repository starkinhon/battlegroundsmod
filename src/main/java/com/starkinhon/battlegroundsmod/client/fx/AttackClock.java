// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client.fx;

import java.util.function.DoubleFunction;

import net.minecraft.world.phys.Vec3;

// The age of an attack the clients draw on their own from where and when it started (the giant fireball, the meteor,
// the Susanoo's slash), stopped while Gojo's shockwave holds it in the air (DomainScene.heldAt), the same way the server
// stops moving it. Each attack drawn keeps one.
final class AttackClock {
    private double frozenAt = -1;
    private double shift;
    // Held by a clash de poderes (ClashFx): the id of the clash, -1 for none. It stays stopped until the clash ends, and
    // then it is gone (the server erased it: the clash made the blast).
    private int clash = -1;

    // raw: the age without holds. at: where the attack is at a given age.
    double age(double raw, DoubleFunction<Vec3> at) {
        if (clash >= 0) {
            return frozenAt;
        }
        if (frozenAt >= 0) {
            if (DomainScene.heldAt(at.apply(frozenAt))) {
                return frozenAt;
            }
            shift = raw - frozenAt;
            frozenAt = -1;
        }
        double age = raw - shift;
        if (DomainScene.holding() && age > 0 && DomainScene.heldAt(at.apply(age))) {
            frozenAt = age;
        } else if (age > 0 && ClashFx.holding()) {
            int id = ClashFx.heldAt(at.apply(age));
            if (id >= 0) {
                clash = id;
                frozenAt = age;
            }
        }
        return age;
    }

    // The age as last worked out, without looking at the holds again (for dropping finished attacks).
    double peek(double raw) {
        return frozenAt >= 0 ? frozenAt : raw - shift;
    }

    // Held by a clash that is still going on: the attack stays (whatever its own time says).
    boolean clashing() {
        return clash >= 0 && !ClashFx.over(clash);
    }

    // Held by a clash that ended: the attack is gone.
    boolean dead() {
        return clash >= 0 && ClashFx.over(clash);
    }

    // How far the clash moved it since it was held (zero when not held by one).
    Vec3 moved(float partial) {
        return clash >= 0 ? ClashFx.shift(clash, partial) : Vec3.ZERO;
    }
}
