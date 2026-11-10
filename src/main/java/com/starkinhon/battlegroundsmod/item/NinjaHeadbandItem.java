package com.starkinhon.battlegroundsmod.item;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

// Bandana Ninja (Naruto, the village's hitai-ate): worn, ninja speed and jumps (universe/NarutoWorld refreshes them).
public class NinjaHeadbandItem extends ArmorItem {
    public NinjaHeadbandItem(Properties properties) {
        super(ArmorMaterials.IRON, ArmorItem.Type.HELMET, properties.durability(ArmorItem.Type.HELMET.getDurability(15)));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.battlegroundsmod.bandana_ninja.desc").withStyle(s -> s.withColor(0xB8C4D0)));
    }
}
