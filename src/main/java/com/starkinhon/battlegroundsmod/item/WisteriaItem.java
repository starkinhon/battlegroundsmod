package com.starkinhon.battlegroundsmod.item;

import java.util.List;

import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.universe.DemonSlayerWorld;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

// Flor de Glicínia (Demon Slayer): demons hate wisteria. Right click scatters the petals: every demon within 8 blocks
// is poisoned, weakened and slowed for 8 s. Used up, 10 s recharge.
public class WisteriaItem extends Item {
    private static final double RANGE = 8;
    private static final DustParticleOptions PETAL = new DustParticleOptions(new org.joml.Vector3f(0.75f, 0.55f, 1f), 1.2f);

    public WisteriaItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel server) {
            Fx.burst(server, PETAL, player.position().add(0, 1, 0), 120, RANGE / 2, 0.02);
            Fx.sound(server, player.position(), SoundEvents.AZALEA_LEAVES_BREAK, 1.2f, 1.3f);
            for (LivingEntity oni : server.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(RANGE),
                    e -> e != player && e.isAlive() && DemonSlayerWorld.isOni(e))) {
                oni.addEffect(new MobEffectInstance(MobEffects.POISON, 160, 1));
                oni.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 160, 1));
                oni.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 160, 1));
            }
            player.getCooldowns().addCooldown(this, 200);
            if (!player.isCreative()) {
                stack.shrink(1);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.battlegroundsmod.flor_glicinia.desc").withStyle(s -> s.withColor(0xC8A0FF)));
    }
}
