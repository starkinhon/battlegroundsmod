package com.starkinhon.battlegroundsmod.item;

import java.util.List;

import com.starkinhon.battlegroundsmod.universe.DemonSlayerWorld;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;

// Espada Nichirin (Demon Slayer): forged from ore that soaks up the sun. Against demons (DemonSlayerWorld.isOni) each
// hit adds 6 damage and the cut burns like daylight.
public class NichirinSwordItem extends SwordItem {
    public NichirinSwordItem(Properties properties) {
        super(Tiers.DIAMOND, properties.attributes(SwordItem.createAttributes(Tiers.DIAMOND, 3, -2.2f)));
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (attacker instanceof Player player && target.isAlive() && DemonSlayerWorld.isOni(target)) {
            target.invulnerableTime = 0;
            target.hurt(player.damageSources().playerAttack(player), 6f);
            target.igniteForSeconds(3);
            if (target.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.FLAME, target.getX(), target.getY(0.6), target.getZ(), 14, 0.3, 0.4, 0.3, 0.02);
            }
        }
        return super.hurtEnemy(stack, target, attacker);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.battlegroundsmod.espada_nichirin.desc").withStyle(s -> s.withColor(0x6AA8FF)));
    }
}
