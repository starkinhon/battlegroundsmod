package com.starkinhon.battlegroundsmod.boss;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

// Bills, the God of Destruction boss. Fights with the destruction_god soul, calm and brutal, and with all of it:
// how he picks his moves is BillsTactics; tuning in souls_bosses/bills.json.
public class BillsEntity extends SoulBoss {
    public BillsEntity(EntityType<? extends BillsEntity> type, Level level) {
        super(type, level);
    }

    private BillsTactics tactics;

    @Override
    public BossTactics tactics() {
        if (tactics == null) {
            tactics = new BillsTactics(this);
        }
        return tactics;
    }

    @Override
    public String bossId() {
        return "bills";
    }
}
