package com.starkinhon.battlegroundsmod.item;

import java.util.List;

import com.starkinhon.battlegroundsmod.combat.Fx;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

// Boneco Amaldiçoado (Jujutsu, Principal Yaga's cursed corpses): right click and the plush stands up beside the player
// (CursedPlush), fights for 60 s with dash, barrage and shove, and falls apart. Used up.
public class CursedDollItem extends Item {
    public CursedDollItem(Properties properties) {
        super(properties.stacksTo(4));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel server) {
            CursedPlush doll = com.starkinhon.battlegroundsmod.ModEntities.CURSED_PLUSH.get().create(server);
            if (doll == null) {
                return InteractionResultHolder.fail(stack);
            }
            var look = player.getLookAngle();
            doll.moveTo(player.getX() + look.x * 1.5, player.getY(), player.getZ() + look.z * 1.5, player.getYRot(), 0);
            doll.yBodyRot = player.getYRot();
            doll.yHeadRot = player.getYRot();
            doll.setOwner(player);
            server.addFreshEntity(doll);
            Fx.burst(server, ParticleTypes.SQUID_INK, doll.position().add(0, 0.6, 0), 40, 0.5, 0.05);
            Fx.sound(server, doll.position(), SoundEvents.WOOL_PLACE, 1f, 0.6f);
            player.getCooldowns().addCooldown(this, 200);
            if (!player.isCreative()) {
                stack.shrink(1);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.battlegroundsmod.boneco_amaldicoado.desc").withStyle(s -> s.withColor(0xA88AD8)));
    }
}
