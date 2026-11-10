package com.starkinhon.battlegroundsmod.item;

import java.util.List;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.combat.Stamina;
import com.starkinhon.battlegroundsmod.power.PowerData;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

// Senzu (Dragon Ball): eaten in a blink, it heals everything, fills the hunger and the stamina and ends the exhaustion.
// 30 s before the next one works (Stark 2026-11-10: items that give a real advantage). Korin gives one a day.
public class SenzuItem extends Item {
    private static final int COOLDOWN = 600;

    public SenzuItem(Properties properties) {
        super(properties.stacksTo(16).food(new FoodProperties.Builder().nutrition(20).saturationModifier(1f).alwaysEdible().fast().build()));
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        ItemStack left = super.finishUsingItem(stack, level, entity);
        if (entity instanceof ServerPlayer player) {
            player.setHealth(player.getMaxHealth());
            player.removeAllEffects();
            PowerData data = player.getData(ModAttachments.POWER);
            data.exhaustedUntil = 0;
            data.stamina = (float) Stamina.max(player);
            player.getCooldowns().addCooldown(this, COOLDOWN);
            Fx.burst(player.serverLevel(), ParticleTypes.HAPPY_VILLAGER, player.position().add(0, 1, 0), 30, 0.5, 0.1);
            Fx.sound(player.serverLevel(), player.position(), SoundEvents.PLAYER_LEVELUP, 0.6f, 1.6f);
        }
        return left;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.battlegroundsmod.senzu.desc").withStyle(s -> s.withColor(0x8EE07A)));
    }
}
