package com.starkinhon.battlegroundsmod.item;

import java.util.ArrayList;
import java.util.List;

import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.combat.Tasks;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

// Pergaminho de Invocação (Naruto, Kakashi's ninken): right click and three ninja dogs come out of the smoke, fight for
// the player for 60 s and go back in. 8 uses, 90 s recharge.
public class SummonScrollItem extends Item {
    private static final int LIFE = 1200;

    public SummonScrollItem(Properties properties) {
        super(properties.durability(8));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel server && player instanceof ServerPlayer owner) {
            List<Wolf> dogs = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                Wolf dog = EntityType.WOLF.create(server);
                if (dog == null) {
                    continue;
                }
                double a = i / 3.0 * Math.PI * 2;
                dog.moveTo(player.getX() + Math.cos(a) * 1.5, player.getY(), player.getZ() + Math.sin(a) * 1.5, player.getYRot(), 0);
                dog.tame(owner);
                dog.setCustomName(Component.translatable("entity.battlegroundsmod.ninken"));
                dog.setHealth(dog.getMaxHealth());
                server.addFreshEntity(dog);
                dogs.add(dog);
                Fx.burst(server, ParticleTypes.CLOUD, dog.position().add(0, 0.5, 0), 20, 0.4, 0.05);
            }
            Fx.sound(server, player.position(), SoundEvents.WOLF_AMBIENT, 1f, 1.2f);
            Tasks.later(LIFE, () -> {
                for (Wolf dog : dogs) {
                    if (dog.isAlive()) {
                        Fx.burst((ServerLevel) dog.level(), ParticleTypes.CLOUD, dog.position().add(0, 0.5, 0), 20, 0.4, 0.05);
                        dog.discard();
                    }
                }
            });
            player.getCooldowns().addCooldown(this, 1800);
            stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.battlegroundsmod.pergaminho_ninken.desc").withStyle(s -> s.withColor(0xD8C08A)));
    }
}
