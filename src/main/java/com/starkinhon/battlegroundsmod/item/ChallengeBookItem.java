package com.starkinhon.battlegroundsmod.item;

import com.starkinhon.battlegroundsmod.client.ClientHooks;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

// The Livro dos Desafios: right click opens the board of every universe's characters, each with its own arena
// (client/ChallengeScreen, challenge/ChallengeArenas). One general book since the universes stopped being maps (Stark
// 2026-11-12); the old per-universe books still open it, at their universe's section. universe is empty for the general one.
public class ChallengeBookItem extends Item {
    private final String universe;

    public ChallengeBookItem(String universe, Properties properties) {
        super(properties);
        this.universe = universe;
    }

    public String universe() {
        return universe;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            ClientHooks.openChallenges(universe);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
