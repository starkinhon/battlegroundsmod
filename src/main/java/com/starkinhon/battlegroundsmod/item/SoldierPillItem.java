package com.starkinhon.battlegroundsmod.item;

import java.util.List;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.Stamina;
import com.starkinhon.battlegroundsmod.power.PowerData;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

// Pílula do Soldado (Naruto): stamina full at once and 30 s of strength and speed, but the body pays after: hunger
// for 20 s. 1 min recharge.
public class SoldierPillItem extends Item {
    public SoldierPillItem(Properties properties) {
        super(properties.stacksTo(8).food(new FoodProperties.Builder().nutrition(2).saturationModifier(0.2f).alwaysEdible().fast().build()));
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        ItemStack left = super.finishUsingItem(stack, level, entity);
        if (entity instanceof ServerPlayer player) {
            PowerData data = player.getData(ModAttachments.POWER);
            data.exhaustedUntil = 0;
            data.stamina = (float) Stamina.max(player);
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 600, 0));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600, 0));
            com.starkinhon.battlegroundsmod.combat.Tasks.later(600, () -> {
                if (player.isAlive()) {
                    player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 400, 1));
                }
            });
            player.getCooldowns().addCooldown(this, 1200);
        }
        return left;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.battlegroundsmod.pilula_soldado.desc").withStyle(s -> s.withColor(0xFF9A4A)));
    }
}
