package com.starkinhon.battlegroundsmod.boss;

import com.starkinhon.battlegroundsmod.power.PowerLogic;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

// Tomura Shigaraki, heir of All For One (Stark, 2026-11-12): tier 3. Toque, Onda and the Mãos do Chão at first; from half
// life Absorver (he steals a power and uses it on you), the Tentáculos and one Regeneração; the Fim do Mundo only at the
// end, like the Madara's meteor: he kneels about 2 s first and a strong blow cuts it off (ShigarakiTactics).
public class ShigarakiEntity extends SoulBoss {
    // A blow at least this strong (before armor) while he kneels for the Fim do Mundo breaks it.
    private static final float BREAKS_KNEEL = 9f;

    private ShigarakiTactics tactics;
    // Until this game tick he is kneeling, the five fingers not yet on the ground.
    long kneelUntil;
    // Armor (half damage) over his long casts, the Tentáculos and the Fim do Mundo after the kneel: rooted 9-10 s, the
    // Madara hit him for free the whole time (test 2026-11-13).
    long armorFrom, armorUntil;
    private static final float ARMOR = 0.5f;

    public ShigarakiEntity(EntityType<? extends ShigarakiEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        long now = level().getGameTime();
        boolean kneeling = !level().isClientSide() && now < kneelUntil;
        boolean armored = !level().isClientSide() && now >= armorFrom && now < armorUntil;
        boolean hurt = super.hurt(source, armored ? amount * ARMOR : amount);
        if (kneeling && amount >= BREAKS_KNEEL && source.getEntity() != null && source.getEntity() != this) {
            // Cut off: the hand never touches the ground (FimDoMundo watches his interrupts).
            kneelUntil = 0;
            armorUntil = 0;
            PowerLogic.interrupt(this);
            if (tactics != null) {
                tactics.endCut();
            }
        }
        return hurt;
    }

    @Override
    public BossTactics tactics() {
        if (tactics == null) {
            tactics = new ShigarakiTactics(this);
        }
        return tactics;
    }

    @Override
    public String bossId() {
        return "shigaraki";
    }
}
