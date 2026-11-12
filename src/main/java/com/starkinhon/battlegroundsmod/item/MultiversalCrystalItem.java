package com.starkinhon.battlegroundsmod.item;

import java.util.List;

import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.Progression;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

// Cristal Multiversal: right click opens the universe screen (client/UniverseScreen); choosing one takes the player
// there and spends the crystal (challenge/Universes.pick). Crafted, or given by some challenges the first time.
// A simple item for now (Stark 2026-11-10); the Fenda, a wormhole with its own scene, comes later.
public class MultiversalCrystalItem extends Item {
    public MultiversalCrystalItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer server) {
            if (!Progression.on(server)) {
                server.displayClientMessage(Component.translatable("message.battlegroundsmod.universe_off"), true);
                return InteractionResultHolder.fail(stack);
            }
            // The universes are biomes of the player's own world now (Stark 2026-11-11): nothing to travel to.
            server.displayClientMessage(Component.translatable("message.battlegroundsmod.universe_no_mundo"), true);
            return InteractionResultHolder.fail(stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.battlegroundsmod.cristal_multiversal.desc").withStyle(s -> s.withColor(0xB89CFF)));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
