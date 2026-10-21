package com.starkinhon.battlegroundsmod.item;

import java.util.List;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.PowerLogic;
import com.starkinhon.battlegroundsmod.power.Powers;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

// A character's soul crystal. Right click to absorb it and become one with that power.
public class SoulItem extends Item {
    private final String powerId;

    public SoulItem(String powerId, Properties properties) {
        super(properties);
        this.powerId = powerId;
    }

    public Power power() {
        return Powers.get(powerId);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getData(ModAttachments.POWER).power.equals(powerId)) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.battlegroundsmod.same_soul"), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            PowerLogic.select(serverPlayer, power());
            stack.consume(1, player);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.battlegroundsmod.soul", power().name());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(power().description().copy().withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.battlegroundsmod.soul.hint").withStyle(ChatFormatting.DARK_PURPLE));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
