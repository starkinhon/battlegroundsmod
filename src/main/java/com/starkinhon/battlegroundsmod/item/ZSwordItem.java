package com.starkinhon.battlegroundsmod.item;

import java.util.List;

import com.starkinhon.battlegroundsmod.boss.SoulBoss;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;

// Espada Z (Dragon Ball): heavy and slow, the strongest blade of the universe. Each hit on a boss or a SubAlma deals a
// second cut of 6 more damage. Only from beating Bills in his arena.
public class ZSwordItem extends SwordItem {
    public ZSwordItem(Properties properties) {
        super(Tiers.NETHERITE, properties.attributes(SwordItem.createAttributes(Tiers.NETHERITE, 7, -3.0f)));
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (target instanceof SoulBoss && attacker instanceof Player player && target.isAlive()) {
            target.invulnerableTime = 0;
            target.hurt(player.damageSources().playerAttack(player), 6f);
        }
        return super.hurtEnemy(stack, target, attacker);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.battlegroundsmod.espada_z.desc").withStyle(s -> s.withColor(0xB0C8FF)));
    }
}
