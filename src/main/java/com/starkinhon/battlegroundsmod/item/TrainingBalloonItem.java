package com.starkinhon.battlegroundsmod.item;

import com.starkinhon.battlegroundsmod.universe.MasterTraining;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

// Jiraiya's training balloon (universe/MasterTraining): hold right click to spin the energy inside it. What it takes
// (fill it until it bursts, or stop in time) depends on the step of the Rasengan training the player is on.
public class TrainingBalloonItem extends Item {
    public TrainingBalloonItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BLOCK;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (!level.isClientSide() && entity instanceof ServerPlayer player
                && MasterTraining.balloonHeld(player, getUseDuration(stack, entity) - remaining)) {
            player.stopUsingItem();
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remaining) {
        if (!level.isClientSide() && entity instanceof ServerPlayer player) {
            MasterTraining.balloonReleased(player, getUseDuration(stack, entity) - remaining);
        }
    }
}
