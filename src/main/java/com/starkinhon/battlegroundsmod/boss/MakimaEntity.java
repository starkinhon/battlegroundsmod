package com.starkinhon.battlegroundsmod.boss;

import java.util.List;

import com.starkinhon.battlegroundsmod.power.powers.makima.Fantoche;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

// Makima, the Control Devil (Stark, 2026-11-12): tier 4. She comes with 3 puppets; by her passive (Primeiro-Ministro) a
// blow that would kill her goes to a puppet, so the fight is killing the puppets first. She keeps away with the chain,
// the orders and the Bang (which finishes whoever is at 15% or less); from half life the Ritual dos Nomes and the chains
// out of the ground, "Morram." (MakimaTactics).
public class MakimaEntity extends SoulBoss {
    // What she keeps in her shadow when she comes (the toughest come out first: Fantoche picks by type name).
    private static final List<String> PUPPETS = List.of("minecraft:vindicator", "minecraft:wither_skeleton", "minecraft:zombie");

    private MakimaTactics tactics;

    public MakimaEntity(EntityType<? extends MakimaEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && tickCount == 2 && isBoss()) {
            Fantoche.seed(this, PUPPETS);
        }
    }

    @Override
    public BossTactics tactics() {
        if (tactics == null) {
            tactics = new MakimaTactics(this);
        }
        return tactics;
    }

    @Override
    public String bossId() {
        return "makima";
    }
}
