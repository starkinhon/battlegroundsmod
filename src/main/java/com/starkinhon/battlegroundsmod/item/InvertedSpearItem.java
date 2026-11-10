package com.starkinhon.battlegroundsmod.item;

import java.util.ArrayList;
import java.util.List;

import com.starkinhon.battlegroundsmod.power.PowerLogic;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;

// Lança Invertida do Céu (Jujutsu, Toji's cursed tool that cut through Gojo's Infinity): every hit strips the target's
// good effects; and once every 10 s (the spear's recharge) the hit cuts the technique itself: the target's skills are
// sealed for 1 s and the move it was doing stops.
public class InvertedSpearItem extends SwordItem {
    private static final int SEAL = 20;
    private static final int RECHARGE = 200;

    public InvertedSpearItem(Properties properties) {
        super(Tiers.NETHERITE, properties.attributes(SwordItem.createAttributes(Tiers.NETHERITE, 3, -2.4f)));
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!target.level().isClientSide) {
            List<MobEffectInstance> good = new ArrayList<>();
            for (MobEffectInstance effect : target.getActiveEffects()) {
                if (effect.getEffect().value().getCategory() == MobEffectCategory.BENEFICIAL) {
                    good.add(effect);
                }
            }
            for (MobEffectInstance effect : good) {
                target.removeEffect(effect.getEffect());
            }
            if (attacker instanceof Player player && !player.getCooldowns().isOnCooldown(this)) {
                PowerLogic.seal(target, SEAL);
                player.getCooldowns().addCooldown(this, RECHARGE);
                if (target.level() instanceof ServerLevel level) {
                    level.sendParticles(ParticleTypes.ENCHANTED_HIT, target.getX(), target.getY(0.6), target.getZ(), 24, 0.3, 0.4, 0.3, 0.3);
                }
            }
        }
        return super.hurtEnemy(stack, target, attacker);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.battlegroundsmod.lanca_invertida.desc").withStyle(s -> s.withColor(0xB0A0FF)));
    }
}
