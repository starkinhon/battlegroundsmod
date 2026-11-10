package com.starkinhon.battlegroundsmod.item;

import java.util.List;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;

// Espada Kusanagi (Naruto, Sasuke's): fast, and its blade carries the Chidori: each hit adds 3 lightning damage and
// numbs the target (heavy slowness for 1 s).
public class KusanagiItem extends SwordItem {
    public KusanagiItem(Properties properties) {
        super(Tiers.DIAMOND, properties.attributes(SwordItem.createAttributes(Tiers.DIAMOND, 2, -1.8f)));
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (attacker instanceof Player player && target.isAlive()) {
            target.invulnerableTime = 0;
            target.hurt(player.damageSources().lightningBolt(), 3f);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 3));
            if (target.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY(0.6), target.getZ(), 18, 0.3, 0.4, 0.3, 0.2);
            }
        }
        return super.hurtEnemy(stack, target, attacker);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.battlegroundsmod.kusanagi.desc").withStyle(s -> s.withColor(0x9FD8FF)));
    }
}
