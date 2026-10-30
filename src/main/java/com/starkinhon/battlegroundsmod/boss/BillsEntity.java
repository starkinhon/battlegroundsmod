package com.starkinhon.battlegroundsmod.boss;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

// Bills, the God of Destruction boss. Fights with the destruction_god soul, calm and brutal; tuning in
// souls_bosses/bills.json.
public class BillsEntity extends SoulBoss {
    public BillsEntity(EntityType<? extends BillsEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public String bossId() {
        return "bills";
    }
}
