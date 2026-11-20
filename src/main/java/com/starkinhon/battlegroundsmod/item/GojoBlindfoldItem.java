// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.item;

import java.util.List;

import com.starkinhon.battlegroundsmod.ModItems;
import com.starkinhon.battlegroundsmod.power.Power;

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

// Venda do Gojo (Jujutsu): worn, the Six Eyes (universe/JujutsuWorld): night vision and everything alive within 24
// blocks glows through walls. Only from beating Gojo.
// Stark 2026-11-19: also a small cut on every skill's recharge (5%, 10% with Gojo's own soul). Now an Equipable head
// item like the Bandana, so the head shows the blindfold's own cube model (venda_gojo_3d, "head" display) instead of a
// leather helmet.
public class GojoBlindfoldItem extends Item implements Equipable {
    public GojoBlindfoldItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    // The recharge a skill gets after this player used it.
    public static int cooldown(Player player, Power power, int ticks) {
        if (ticks <= 0 || !player.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.GOJO_BLINDFOLD.get())) {
            return ticks;
        }
        boolean gojo = power != null && ("strongest".equals(power.id()) || "gojo".equals(power.id()));
        return Math.max(1, Math.round(ticks * (gojo ? 0.90f : 0.95f)));
    }

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return EquipmentSlot.HEAD;
    }

    @Override
    public Holder<SoundEvent> getEquipSound() {
        return SoundEvents.ARMOR_EQUIP_LEATHER;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return swapWithEquipmentSlot(this, level, player, hand);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.battlegroundsmod.venda_gojo.desc").withStyle(s -> s.withColor(0x7AD8FF)));
    }
}
