package com.starkinhon.battlegroundsmod.boss;

import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

// A plain monster that fights with the boss AI (SoulBossAI) and a soul's skills: the SubAlma mobs (Byakuya, Ichigo,
// Rukia, Urahara) with their SubAlma's skills, and the weak Fighters (Tier 10, 9 and 8) that only use
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

    @Override
    public boolean removeWhenFarAway(double distance) {
        return true;
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
