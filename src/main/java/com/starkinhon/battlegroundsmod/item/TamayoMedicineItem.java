package com.starkinhon.battlegroundsmod.item;

import java.util.List;

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

// Remédio da Tamayo (Demon Slayer): heals 3 hearts at once, cleans poison, wither, weakness and hunger, then 10 s of
// regeneration II. 45 s recharge.
public class TamayoMedicineItem extends Item {
    public TamayoMedicineItem(Properties properties) {
        super(properties.stacksTo(8).food(new FoodProperties.Builder().nutrition(2).saturationModifier(0.3f).alwaysEdible().fast().build()));
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        ItemStack left = super.finishUsingItem(stack, level, entity);
        if (entity instanceof ServerPlayer player) {
            player.heal(6f);
            player.removeEffect(MobEffects.POISON);
            player.removeEffect(MobEffects.WITHER);
            player.removeEffect(MobEffects.WEAKNESS);
            player.removeEffect(MobEffects.HUNGER);
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1));
            player.getCooldowns().addCooldown(this, 900);
        }
        return left;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.battlegroundsmod.remedio_tamayo.desc").withStyle(s -> s.withColor(0xFF8AB0)));
    }
}
