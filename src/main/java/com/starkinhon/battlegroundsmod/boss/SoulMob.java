package com.starkinhon.battlegroundsmod.boss;

import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

// A plain monster that fights with the boss AI (SoulBossAI) and a soul's skills: the SubAlma mobs (Byakuya and
// Rukia) with their SubAlma's skills, and the weak Fighters (Tier 10, 9 and 8) that only use
// the base combat (punches, strong, barrage, dashes). Tuning in souls_bosses/<id>.json, like the bosses.
// Unlike a boss: no bar, entrance, phases or final ult (even under /gamerule soulbossfight), no allbossestier0, they
// despawn like any monster (unless spawned from an egg or named), and they don't hunt other souls on sight.
public class SoulMob extends SoulBoss {
    public SoulMob(EntityType<? extends SoulMob> type, Level level) {
        super(type, level);
    }

    @Override
    public boolean isBoss() {
        return false;
    }

    @Override
    public String bossId() {
        // Its entity id (battlegroundsmod:byakuya -> byakuya). Read from the type, not a field: the constructors of
        // Mob and LivingEntity already ask for it (setHealth), before a field of this class would be set.
        return net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(getType()).getPath();
    }

    // Natural ones come and go, but not before they can be found (PR #309 test, 2026-11-12: SubAlmas spawned 40-72
    // blocks away vanished within 15-45 s, before anyone could walk there; in daylight a monster's idle time climbs
    // fast and vanilla's random despawn past 32 blocks took them). The first LINGER ticks only leaving (past 128 blocks,
    // vanilla's hard limit) removes them.
    private static final int LINGER = 20 * 150;

    @Override
    public boolean removeWhenFarAway(double distanceSqr) {
        return distanceSqr > 128 * 128 || tickCount > LINGER;
    }

    // One placed with a spawn egg stays (for testing and videos); natural ones come and go.
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType type, SpawnGroupData data) {
        if (type == MobSpawnType.SPAWN_EGG || type == MobSpawnType.COMMAND) {
            setPersistenceRequired();
        }
        return super.finalizeSpawn(level, difficulty, type, data);
    }
}
