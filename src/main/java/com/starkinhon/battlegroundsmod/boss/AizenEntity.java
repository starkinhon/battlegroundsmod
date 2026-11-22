// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.boss;

import com.starkinhon.battlegroundsmod.ModItems;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

// Aizen, O Manipulador (Stark, 2026-11-22): a divine soul, tier 4, the whole v2.3 kit (AizenTactics). Calm and from the
// middle distance, every move on purpose: the Kyōka Suigetsu to vanish and show up behind, the Um Dedo against whoever
// comes to punch, the Dankū against techniques, and from half life the Hōgyoku (the Fragor, then the Hadō 99 once).
// Skin: textures/entity/aizen.png (S-suke, Planet Minecraft).
public class AizenEntity extends SoulBoss {
    private AizenTactics tactics;

    public AizenEntity(EntityType<? extends AizenEntity> type, Level level) {
        super(type, level);
        // His zanpakutō in hand, like the soul gives it (PowerLogic.SOUL_WEAPONS); never dropped.
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.AIZEN_SWORD.get()));
        setDropChance(EquipmentSlot.MAINHAND, 0);
    }

    @Override
    public void tick() {
        if (getMainHandItem().isEmpty()) {
            setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.AIZEN_SWORD.get()));
        }
        super.tick();
    }

    @Override
    public BossTactics tactics() {
        if (tactics == null) {
            tactics = new AizenTactics(this);
        }
        return tactics;
    }

    @Override
    public String bossId() {
        return "aizen";
    }
}
