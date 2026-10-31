package com.starkinhon.battlegroundsmod.fight;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;

// Melee for mobs that never attack on their own (cows, villagers, pigs...): while in a Fight Rod duel they walk up to
// their enemy and hit it. Priority 0 so panic and wandering don't pull them away mid-fight.
final class FightGoal extends Goal {
    private static final Set<PathfinderMob> HAS_GOAL = Collections.newSetFromMap(new WeakHashMap<>());
    private static final float FALLBACK_DAMAGE = 3;

    private final PathfinderMob mob;
    private int cooldown;
    private int repath;

    private FightGoal(PathfinderMob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    static void addTo(PathfinderMob mob) {
        if (HAS_GOAL.add(mob)) {
            mob.goalSelector.addGoal(0, new FightGoal(mob));
        }
    }

    @Override
    public boolean canUse() {
        return FightRod.enemyOf(mob) != null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        repath = 0;
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        var enemy = FightRod.enemyOf(mob);
        if (enemy == null) {
            return;
        }
        mob.getLookControl().setLookAt(enemy, 30, 30);
        if (--repath <= 0) {
            repath = 10;
            mob.getNavigation().moveTo(enemy, 1.25);
        }
        if (cooldown > 0) {
            cooldown--;
        }
        double reach = mob.getBbWidth() * 0.5 + enemy.getBbWidth() * 0.5 + 1.2;
        if (cooldown == 0 && mob.distanceTo(enemy) <= reach && mob.hasLineOfSight(enemy)) {
            cooldown = 20;
            mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            if (mob.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
                mob.doHurtTarget(enemy);
            } else {
                enemy.hurt(mob.damageSources().mobAttack(mob), FALLBACK_DAMAGE);
            }
        }
    }
}
