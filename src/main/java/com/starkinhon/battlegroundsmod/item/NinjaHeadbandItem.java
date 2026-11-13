package com.starkinhon.battlegroundsmod.item;

import java.util.List;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

// Bandana Ninja (Naruto, the village's hitai-ate): worn, ninja speed and jumps (universe/NarutoWorld refreshes them).
// Stark 2026-11-12 (Itens de Naruto): an Equipable head item like the Scouter, so the head shows the bandana's own cube
// model (models/item/bandana_ninja_3d.json, "head" display, vanilla CustomHeadLayer) instead of an iron helmet.
public class NinjaHeadbandItem extends Item implements Equipable {
    public NinjaHeadbandItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return EquipmentSlot.HEAD;
    }

    @Override
    public Holder<SoundEvent> getEquipSound() {
        return SoundEvents.ARMOR_EQUIP_IRON;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return swapWithEquipmentSlot(this, level, player, hand);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.battlegroundsmod.bandana_ninja.desc").withStyle(s -> s.withColor(0xB8C4D0)));
    }
}
