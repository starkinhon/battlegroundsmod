package com.starkinhon.battlegroundsmod.boss;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

// Gojo, The Strongest boss. Fights with the strongest soul: walks calmly, blinks behind the target, and uses all of
// it: the Blue in all its forms, the Red (tap, charged, behind the target), the Purple, and, only when it is time to
// end it, the Domain Expansion (Vazio Infinito) and the Unlimited Purple. How he picks them is GojoTactics; tuning in
// souls_bosses/gojo.json.
public class GojoEntity extends SoulBoss {
    public GojoEntity(EntityType<? extends GojoEntity> type, Level level) {
        super(type, level);
    }

    private GojoTactics tactics;

    @Override
    public BossTactics tactics() {
        if (tactics == null) {
            tactics = new GojoTactics(this);
        }
        return tactics;
    }

    @Override
    public String bossId() {
        return "gojo";
    }
}
