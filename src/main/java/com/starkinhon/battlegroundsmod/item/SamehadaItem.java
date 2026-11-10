package com.starkinhon.battlegroundsmod.item;

import java.util.List;

import com.starkinhon.battlegroundsmod.combat.Stamina;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;

// Samehada (Naruto, Kisame's living sword): each hit eats from the target, 15% of its stamina (a player's hunger too),
// and feeds the wielder 2 health. Only from beating Madara.
public class SamehadaItem extends SwordItem {
    public SamehadaItem(Properties properties) {
        super(Tiers.NETHERITE, properties.attributes(SwordItem.createAttributes(Tiers.NETHERITE, 4, -2.8f)));
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        Stamina.drain(target, 15);
        if (target instanceof Player player) {
            player.causeFoodExhaustion(3f);
        }
        attacker.heal(2f);
        return super.hurtEnemy(stack, target, attacker);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.battlegroundsmod.samehada.desc").withStyle(s -> s.withColor(0x7AA8FF)));
    }
}
