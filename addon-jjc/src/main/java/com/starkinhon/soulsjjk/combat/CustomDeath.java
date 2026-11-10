package com.starkinhon.soulsjjk.combat;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.world.entity.LivingEntity;

// Deaths that have their own look (burning to ash under the Katon or the Meteoro, the Hakai's purple sparks). They are
// marked here when they start, so the soul ceremony (boss/SoulCeremony) leaves them alone: the soul just drops.
public final class CustomDeath {
    // A mark is only trusted for this long: a target that got away from a Hakai and died later dies normally.
    private static final long VALID_TICKS = 200;
    private static final Map<LivingEntity, Long> MARKED = Collections.synchronizedMap(new WeakHashMap<>());

    private CustomDeath() {
    }

    public static void mark(LivingEntity entity) {
        MARKED.put(entity, entity.level().getGameTime());
    }

    public static boolean marked(LivingEntity entity) {
        Long when = MARKED.get(entity);
        return when != null && entity.level().getGameTime() - when <= VALID_TICKS;
    }
}
