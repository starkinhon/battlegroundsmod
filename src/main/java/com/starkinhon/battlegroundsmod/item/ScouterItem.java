package com.starkinhon.battlegroundsmod.item;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

// Scouter (Dragon Ball): worn on the head it reads whoever the player looks at, up to 64 blocks: name, tier and power
// level (client/ScouterHud). Know if that one is too strong before picking the fight. Leather look for now.
public class ScouterItem extends ArmorItem {
    public ScouterItem(Properties properties) {
        super(ArmorMaterials.LEATHER, ArmorItem.Type.HELMET, properties.durability(ArmorItem.Type.HELMET.getDurability(10)));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.battlegroundsmod.scouter.desc").withStyle(s -> s.withColor(0x8EE07A)));
    }
}
