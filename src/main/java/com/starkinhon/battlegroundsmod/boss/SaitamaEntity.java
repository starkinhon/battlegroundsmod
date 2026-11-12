package com.starkinhon.battlegroundsmod.boss;

import com.starkinhon.battlegroundsmod.power.powers.opm.Opm;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

// Saitama, the hero for fun (Stark, 2026-11-12): tier 1, the Cara de um Soco soul. Bored at first (normal blows, the
// counter's flick, nothing pushes him: Pedra Viva), then from half life the face goes from "ok" to serious and each serious
// move comes with a long warning, dodgeable. The Soco Sério only under 20%: who dodges lives (SaitamaTactics).
public class SaitamaEntity extends SoulBoss {
    private SaitamaTactics tactics;

    public SaitamaEntity(EntityType<? extends SaitamaEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && tickCount % 20 == 1) {
            // Pedra Viva works on the mobs it knows of (Opm.MOBS): from his first tick, not only after his first V.
            Opm.track(this);
        }
    }

    @Override
    public BossTactics tactics() {
        if (tactics == null) {
            tactics = new SaitamaTactics(this);
        }
        return tactics;
    }

    @Override
    public String bossId() {
        return "saitama";
    }
}
