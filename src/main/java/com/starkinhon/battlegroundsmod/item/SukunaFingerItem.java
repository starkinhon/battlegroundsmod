package com.starkinhon.battlegroundsmod.item;

import java.util.List;

import com.starkinhon.battlegroundsmod.combat.Fx;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

// Dedo do Sukuna (Jujutsu): eaten, the King of Curses wakes up inside for 40 s: strength II, resistance and speed. But
// he tears his way in: 3 hearts gone (never below half a heart), and the head spins for 8 s. 3 min recharge.
public class SukunaFingerItem extends Item {
    private static final int DURATION = 800;

    public SukunaFingerItem(Properties properties) {
        super(properties.stacksTo(4).food(new FoodProperties.Builder().nutrition(1).saturationModifier(0f).alwaysEdible().build()));
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        ItemStack left = super.finishUsingItem(stack, level, entity);
        if (entity instanceof ServerPlayer player) {
            player.setHealth(Math.max(1f, player.getHealth() - 6f));
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, DURATION, 1));
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, DURATION, 0));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, DURATION, 0));
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 160, 0));
            player.displayClientMessage(Component.translatable("item.battlegroundsmod.dedo_sukuna.eaten")
                    .withStyle(s -> s.withColor(0xC8202A).withBold(true)), true);
            Fx.burst(player.serverLevel(), ParticleTypes.CRIMSON_SPORE, player.position().add(0, 1, 0), 60, 0.6, 0.02);
            Fx.sound(player.serverLevel(), player.position(), SoundEvents.WARDEN_HEARTBEAT, 1.2f, 0.7f);
            player.getCooldowns().addCooldown(this, 3600);
        }
        return left;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.battlegroundsmod.dedo_sukuna.desc").withStyle(s -> s.withColor(0xC8202A)));
    }
}
