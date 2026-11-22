// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.boss;

import java.util.ArrayDeque;
import java.util.Deque;

import com.starkinhon.battlegroundsmod.ModItems;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

// Toji Fushiguro (Stark, 2026-11-22: "agora sim ficou de respeito"): the Jujutsu end-game boss, the third one "above"
// the divine souls, out in Shibuya. His power never becomes a soul (dropAlma false). No cursed energy: the Restrição
// Celestial keeps him off every sensor (nothing makes him glow), and he fights with the mod's own weapons, the Lança
// Invertida in his hand and the Nuvem Brincalhona for its combo (power/powers/toji). Fast and strong: half the damage
// while he runs a move, and the Sumiço answers whoever stays glued to him hitting.
public class TojiEntity extends SoulBoss {
    private TojiTactics tactics;
    private long armorUntil;
    private final Deque<Long> hits = new ArrayDeque<>();

    public TojiEntity(EntityType<? extends TojiEntity> type, Level level) {
        super(type, level);
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.INVERTED_SPEAR.get()));
        setDropChance(EquipmentSlot.MAINHAND, 0);
    }

    // Half the damage while he runs a move.
    public void armor(int ticks) {
        armorUntil = Math.max(armorUntil, level().getGameTime() + ticks);
    }

    // How many blows he took in the last 1.2 s.
    public int recentHits() {
        long now = level().getGameTime();
        while (!hits.isEmpty() && now - hits.peekFirst() > 24) {
            hits.pollFirst();
        }
        return hits.size();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide()) {
            if (level().getGameTime() < armorUntil && !source.is(DamageTypes.FELL_OUT_OF_WORLD) && !source.is(DamageTypes.GENERIC_KILL)) {
                amount *= 0.5f;
            }
            if (source.getEntity() != null && source.getEntity() != this) {
                hits.addLast(level().getGameTime());
                if (hits.size() > 16) {
                    hits.pollFirst();
                }
            }
        }
        return super.hurt(source, amount);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide()) {
            // Restrição Celestial: no cursed energy, nothing finds him (the Six Eyes, the Scouter's glow)
            if (hasEffect(MobEffects.GLOWING)) {
                removeEffect(MobEffects.GLOWING);
            }
            // his falls are part of the moves (the ult's leap, the hops over the roofs)
            fallDistance = 0;
        }
    }

    @Override
    public BossTactics tactics() {
        if (tactics == null) {
            tactics = new TojiTactics(this);
        }
        return tactics;
    }

    // The rare loot, only for the world fight (EndBossLoot).
    @Override
    protected void dropCustomDeathLoot(net.minecraft.server.level.ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        EndBossLoot.drop(this, level, source, 1500,
                new ItemStack(ModItems.INVERTED_SPEAR.get()),
                new ItemStack(net.minecraft.world.item.Items.TOTEM_OF_UNDYING),
                new ItemStack(net.minecraft.world.item.Items.ENCHANTED_GOLDEN_APPLE, 2));
    }

    // No flying in his fight: he hunts on the ground.
    @Override
    public String flightLock() {
        return "message.battlegroundsmod.flight_lock.toji";
    }

    @Override
    public String bossId() {
        return "toji";
    }
}
