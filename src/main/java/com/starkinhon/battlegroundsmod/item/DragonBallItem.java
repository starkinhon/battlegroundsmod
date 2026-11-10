package com.starkinhon.battlegroundsmod.item;

import java.util.List;

import com.starkinhon.battlegroundsmod.universe.DragonBallWorld;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

// One of the seven Esferas do Dragão (stars = 1..7). They drop from enemies beaten in the Dragon Ball universe (more
// with the Radar). With all seven, right click calls Shenlong for one wish (universe/DragonBallWorld).
public class DragonBallItem extends Item {
    private final int stars;

    public DragonBallItem(int stars, Properties properties) {
        super(properties.stacksTo(1));
        this.stars = stars;
    }

    public int stars() {
        return stars;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer server) {
            DragonBallWorld.summon(server);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.battlegroundsmod.esfera_dragao.desc").withStyle(s -> s.withColor(0xFFB347)));
    }
}
