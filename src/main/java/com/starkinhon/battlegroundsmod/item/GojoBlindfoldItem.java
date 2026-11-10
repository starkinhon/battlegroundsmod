package com.starkinhon.battlegroundsmod.item;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

// Venda do Gojo (Jujutsu): worn, the Six Eyes (universe/JujutsuWorld): night vision and everything alive within 24
// blocks glows through walls. Only from beating Gojo.
public class GojoBlindfoldItem extends ArmorItem {
    public GojoBlindfoldItem(Properties properties) {
        super(ArmorMaterials.LEATHER, ArmorItem.Type.HELMET, properties.durability(ArmorItem.Type.HELMET.getDurability(25)));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.battlegroundsmod.venda_gojo.desc").withStyle(s -> s.withColor(0x7AD8FF)));
    }
}
