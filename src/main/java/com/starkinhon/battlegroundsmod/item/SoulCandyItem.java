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

// Pílula de Alma (Bleach, Soul Candy from Urahara's shop): the soul leaves the body for 20 s: invisible and fast, a
// spirit nobody sees. 1 min recharge.
public class SoulCandyItem extends Item {
    private static final int DURATION = 400;

    public SoulCandyItem(Properties properties) {
        super(properties.stacksTo(8).food(new FoodProperties.Builder().nutrition(1).saturationModifier(0.1f).alwaysEdible().fast().build()));
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        ItemStack left = super.finishUsingItem(stack, level, entity);
        if (entity instanceof ServerPlayer player) {
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, DURATION, 0));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, DURATION, 1));
            Fx.burst(player.serverLevel(), ParticleTypes.SOUL, player.position().add(0, 1, 0), 30, 0.4, 0.03);
            Fx.sound(player.serverLevel(), player.position(), SoundEvents.ENDERMAN_TELEPORT, 1f, 1.2f);
            player.getCooldowns().addCooldown(this, 1200);
        }
        return left;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.battlegroundsmod.pilula_alma.desc").withStyle(s -> s.withColor(0x9ADFFF)));
    }
}
