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

// Scouter (Dragon Ball): worn on the head it reads whoever the player looks at, up to 64 blocks: name, tier and power
// level (client/ScouterHud). Know if that one is too strong before picking the fight.
// Stark 2026-11-12 (remake dos itens DB): an Equipable head item, not an ArmorItem, so the player's head shows the
// scouter's own cube model (models/item/scouter_3d.json, "head" display, drawn by vanilla CustomHeadLayer) instead of
// a leather helmet: housing on the left ear, arm along the temple, translucent green lens over the left eye.
public class ScouterItem extends Item implements Equipable {
    public ScouterItem(Properties properties) {
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
        tooltip.add(Component.translatable("item.battlegroundsmod.scouter.desc").withStyle(s -> s.withColor(0x8EE07A)));
    }
}
