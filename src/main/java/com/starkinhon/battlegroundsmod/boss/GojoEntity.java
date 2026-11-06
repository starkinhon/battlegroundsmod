package com.starkinhon.battlegroundsmod.boss;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

// Gojo, The Strongest boss. Fights with the strongest soul: walks calmly, blinks behind the target, uses the Blue in all
// its forms (tap, charged chasing the target, shot, parked, circling him; see gojo/Azul BossPlan) and opens the
// Domain Expansion (Vazio Infinito); tuning in souls_bosses/gojo.json.
public class GojoEntity extends SoulBoss {
    public GojoEntity(EntityType<? extends GojoEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public String bossId() {
        return "gojo";
    }
}
