package com.starkinhon.battlegroundsmod.boss;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

// Joy Boy, the Luffy boss (Stark, 2026-11-12, thread "Adicionar os 4 bosses novos"): tier 2, the Garoto Borracha soul.
// Gear 2 at first, Gear 4 Boundman from 60%, and at 25% the Tambores da Libertação: the total Gear 5 for a minute. When it
// ends he is starving and spent, and that is the window to kill him (JoyBoyTactics; tuning in souls_bosses/joy_boy.json).
public class JoyBoyEntity extends SoulBoss {
    private JoyBoyTactics tactics;

    public JoyBoyEntity(EntityType<? extends JoyBoyEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public BossTactics tactics() {
        if (tactics == null) {
            tactics = new JoyBoyTactics(this);
        }
        return tactics;
    }

    @Override
    public String bossId() {
        return "joy_boy";
    }
}
