package com.starkinhon.battlegroundsmod.item;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Items;

// Ramen do Ichiraku (Naruto): a full meal that keeps healing for a while. Teuchi gives one a day.
public class RamenItem extends Item {
    public RamenItem(Properties properties) {
        super(properties.stacksTo(16).food(new FoodProperties.Builder().nutrition(12).saturationModifier(1.2f)
                .effect(new MobEffectInstance(MobEffects.REGENERATION, 300, 1), 1f).usingConvertsTo(Items.BOWL).build()));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.battlegroundsmod.ramen.desc").withStyle(s -> s.withColor(0xFFC27A)));
    }
}
