package com.starkinhon.battlegroundsmod.item;

import java.util.List;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.ModItems;
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

// Dedo do Sukuna (Jujutsu): eaten, the King of Curses wakes up inside for 40 s: strength II, resistance and speed, and
// the head spins for 8 s. 1 min recharge.
// Stark 2026-11-19: every finger eaten counts (kept on the player, ModAttachments.SUKUNA_FINGERS); the 20th, like in
// the manga, wakes his soul: the player gets Sukuna's soul crystal, the same one beating him gives. Eating no longer
// costs 3 hearts.
public class SukunaFingerItem extends Item {
    public static final int ALL = 20;
    private static final int DURATION = 800;

    public SukunaFingerItem(Properties properties) {
        super(properties.stacksTo(20).food(new FoodProperties.Builder().nutrition(1).saturationModifier(0f).alwaysEdible().build()));
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        ItemStack left = super.finishUsingItem(stack, level, entity);
        if (entity instanceof ServerPlayer player) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, DURATION, 1));
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, DURATION, 0));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, DURATION, 0));
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 160, 0));
            Fx.burst(player.serverLevel(), ParticleTypes.CRIMSON_SPORE, player.position().add(0, 1, 0), 60, 0.6, 0.02);
            Fx.sound(player.serverLevel(), player.position(), SoundEvents.WARDEN_HEARTBEAT, 1.2f, 0.7f);
            player.getCooldowns().addCooldown(this, 1200);
            // counts up to 20 and stops there: the soul comes once, when the count reaches 20
            int eaten = player.getData(ModAttachments.SUKUNA_FINGERS);
            boolean counted = eaten < ALL;
            if (counted) {
                eaten++;
                player.setData(ModAttachments.SUKUNA_FINGERS, eaten);
            }
            if (counted && eaten == ALL) {
                awaken(player);
            } else {
                player.displayClientMessage(Component.translatable("item.battlegroundsmod.dedo_sukuna.eaten", eaten, ALL)
                        .withStyle(s -> s.withColor(0xC8202A).withBold(true)), true);
            }
        }
        return left;
    }

    // The 20th finger: Sukuna's soul crystal, once per player.
    private static void awaken(ServerPlayer player) {
        var soul = ModItems.SOULS.get("sukuna");
        if (soul != null) {
            ItemStack crystal = new ItemStack(soul.get());
            if (!player.addItem(crystal)) {
                player.drop(crystal, false);
            }
        }
        player.displayClientMessage(Component.translatable("item.battlegroundsmod.dedo_sukuna.awaken")
                .withStyle(s -> s.withColor(0xC8202A).withBold(true)), false);
        Fx.burst(player.serverLevel(), ParticleTypes.CRIMSON_SPORE, player.position().add(0, 1, 0), 200, 1.2, 0.05);
        Fx.sound(player.serverLevel(), player.position(), SoundEvents.WITHER_SPAWN, 0.8f, 0.6f);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.battlegroundsmod.dedo_sukuna.desc").withStyle(s -> s.withColor(0xC8202A)));
    }
}
