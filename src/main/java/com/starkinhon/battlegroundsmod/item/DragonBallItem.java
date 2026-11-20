// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.item;

import java.util.List;

import com.starkinhon.battlegroundsmod.universe.DragonBallWorld;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
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

    // Only one ball of each star exists in a world (DragonBallWorld.BallRegistry) and its star is freed only when Shenlong is
    // called: a ball on the ground must never be lost, or that star could never drop again and Shenlong never come. It never
    // despawns, nothing hurts it (lava, fire, explosion, cactus) and one that falls under the world comes back up on top.
    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        if (entity.level() instanceof net.minecraft.server.level.ServerLevel server && !DragonBallWorld.checkBall(server, stack, null)) {
            // a newer ball of this star exists (the old one was lost and the star came back): this copy is stone now
            // (a fresh stone item: the ball's own entity never despawned, a stone should)
            ItemEntity stone = new ItemEntity(server, entity.getX(), entity.getY(), entity.getZ(), DragonBallWorld.stone());
            server.addFreshEntity(stone);
            entity.discard();
            return true;
        }
        if (!entity.level().isClientSide()) {
            if (!entity.isInvulnerable()) {
                entity.setUnlimitedLifetime();
                entity.setInvulnerable(true);
            }
            Level level = entity.level();
            if (entity.getY() < level.getMinBuildHeight() + 1) {
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, entity.getBlockX(), entity.getBlockZ());
                entity.teleportTo(entity.getX(), Math.max(top, level.getMinBuildHeight() + 1) + 1, entity.getZ());
                entity.setDeltaMovement(0, 0, 0);
            }
        }
        return false;
    }

    // In a player's (or mob's) inventory: the same check, once a second (DragonBallWorld.checkBall).
    @Override
    public void inventoryTick(ItemStack stack, Level level, net.minecraft.world.entity.Entity entity, int slot, boolean selected) {
        if (level instanceof net.minecraft.server.level.ServerLevel server && !stack.isEmpty()
                && !DragonBallWorld.checkBall(server, stack, entity)) {
            stack.setCount(0);
            if (entity instanceof Player player) {
                ItemStack stone = DragonBallWorld.stone();
                if (!player.getInventory().add(stone)) {
                    player.drop(stone, false);
                }
                player.displayClientMessage(Component.translatable("message.battlegroundsmod.esfera_virou_pedra", stars), false);
            }
        }
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
