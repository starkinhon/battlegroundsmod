package com.starkinhon.battlegroundsmod.dummy;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Melee;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.skill.ComboSpec;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

// Test dummy that throws the standard punch (the first hit of the combo) at the closest player in reach every
// ATTACK_INTERVAL ticks, so counters, guards and shields can be tried on a steady rhythm. Never walks, never dies.
public class AttackerDummy extends TrainingDummy {
    private static final int ATTACK_INTERVAL = 40;
    private int nextAttack = ATTACK_INTERVAL;

    public AttackerDummy(EntityType<? extends AttackerDummy> type, Level level) {
        super(type, level);
    }

    @Override
    protected void serverTick() {
        if (!isAlive()) {
            return;
        }
        Player target = level().getNearestPlayer(this, ComboSpec.get().alcance() + 0.4);
        if (target == null || target.isSpectator()) {
            nextAttack = Math.max(nextAttack, 10);
            return;
        }
        getLookControl().setLookAt(target, 60f, 60f);
        lookAt(target, 60f, 60f);
        setYHeadRot(getYRot());
        yBodyRot = getYRot();
        if (--nextAttack > 0) {
            return;
        }
        nextAttack = ATTACK_INTERVAL;
        if (!Combat.isTarget(target, this)) {
            return;
        }
        // Always the same standard hit, never the combo finisher.
        PowerData data = getData(ModAttachments.POWER);
        data.comboStep = 0;
        Melee.punch(this, (LivingEntity) target);
    }
}
