package com.starkinhon.battlegroundsmod.client.fx;

import java.util.function.DoubleFunction;

import net.minecraft.world.phys.Vec3;

// The age of an attack the clients draw on their own from where and when it started (the giant fireball, the meteor,
// the Susanoo's slash), stopped while Gojo's shockwave holds it in the air (DomainScene.heldAt), the same way the server
// stops moving it. Each attack drawn keeps one.
final class AttackClock {
    private double frozenAt = -1;
    private double shift;

    // raw: the age without holds. at: where the attack is at a given age.
    double age(double raw, DoubleFunction<Vec3> at) {
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
        }
        return age;
    }

    // The age as last worked out, without looking at the holds again (for dropping finished attacks).
    double peek(double raw) {
        return frozenAt >= 0 ? frozenAt : raw - shift;
    }
}
