package com.starkinhon.battlegroundsmod.item;

import java.util.List;

import com.starkinhon.battlegroundsmod.ModAttachments;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

// Inseto de Alma: eaten once, it lives on in its eater's soul for good (kept on death): they never go hungry again, but
// it never fills them up. The hunger bar is held at FOOD_LEVEL (enough to sprint, below the 18 that regenerates life)
// with no saturation, so it never heals anyone: fights stay about the fight, nobody stops to farm food. Eating it gives
// the advancement data/battlegroundsmod/advancement/soul_insect.json.
public class SoulInsectItem extends Item {
    // 8.5 drumsticks: sprinting needs more than 6, natural regeneration starts at 18.
    public static final int FOOD_LEVEL = 17;

    public SoulInsectItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof ServerPlayer player) {
            boolean first = !player.getData(ModAttachments.SOUL_FED);
            player.setData(ModAttachments.SOUL_FED, true);
            player.displayClientMessage(Component.translatable(first ? "message.battlegroundsmod.soul_insect.eaten"
                    : "message.battlegroundsmod.soul_insect.already").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1, 0.6f);
        }
        return super.finishUsingItem(stack, level, entity);
    }

    // Every tick on the server: whoever ate one never drops below FOOD_LEVEL.
    public static void tick(ServerPlayer player) {
        if (player.getAbilities().invulnerable || !player.getData(ModAttachments.SOUL_FED)) {
            return;
        }
        FoodData food = player.getFoodData();
        if (food.getFoodLevel() < FOOD_LEVEL) {
            food.setFoodLevel(FOOD_LEVEL);
            food.setSaturation(0);
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.battlegroundsmod.soul_insect.tip1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.battlegroundsmod.soul_insect.tip2").withStyle(ChatFormatting.DARK_GRAY));
    }
}
