package com.starkinhon.battlegroundsmod.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

// Inseto de Alma: anywhere in the inventory it feeds on the soul so its carrier never goes hungry, but never fills them
// up. The hunger bar is held at FOOD_LEVEL (enough to sprint, below the 18 that regenerates life) with no saturation,
// so it never heals anyone: fights stay about the fight, nobody stops to farm food.
public class SoulInsectItem extends Item {
    // 8.5 drumsticks: sprinting needs more than 6, natural regeneration starts at 18.
    public static final int FOOD_LEVEL = 17;

    public SoulInsectItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !(entity instanceof Player player) || player.getAbilities().invulnerable) {
            return;
        }
        FoodData food = player.getFoodData();
        if (food.getFoodLevel() < FOOD_LEVEL) {
            food.setFoodLevel(FOOD_LEVEL);
            food.setSaturation(0);
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.battlegroundsmod.soul_insect.tip1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.battlegroundsmod.soul_insect.tip2").withStyle(ChatFormatting.DARK_GRAY));
    }
}
