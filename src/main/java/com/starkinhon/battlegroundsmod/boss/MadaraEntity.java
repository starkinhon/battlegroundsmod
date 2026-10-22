package com.starkinhon.battlegroundsmod.boss;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

// Madara, the Uchiha Ghost boss. Fights with the uchiha_ghost soul; tuning in souls_bosses/madara.json.
public class MadaraEntity extends SoulBoss {
    public MadaraEntity(EntityType<? extends MadaraEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public String bossId() {
        return "madara";
    }
}
