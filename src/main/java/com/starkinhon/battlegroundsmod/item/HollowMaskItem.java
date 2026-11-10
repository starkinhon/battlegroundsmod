package com.starkinhon.battlegroundsmod.item;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

// Máscara Hollow (Bleach, the Visored's mask): worn, the inner Hollow lends its power (strength and speed, refreshed
// by universe/BleachWorld) but eats at the body: hunger drains fast while it is on.
public class HollowMaskItem extends ArmorItem {
    public HollowMaskItem(Properties properties) {
        super(ArmorMaterials.IRON, ArmorItem.Type.HELMET, properties.durability(ArmorItem.Type.HELMET.getDurability(20)));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.battlegroundsmod.mascara_hollow.desc").withStyle(s -> s.withColor(0xE8E8F0)));
    }
}
