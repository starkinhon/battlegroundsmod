package com.starkinhon.battlegroundsmod.item;

import java.util.List;

import com.starkinhon.battlegroundsmod.combat.Fx;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// Zanpakutō (Bleach): a Shinigami's sword. Right click: Getsuga Tenshō, a crescent of reiatsu that runs 14 blocks
// ahead and cuts everyone on the way (10 damage). 6 s recharge.
public class ZanpakutoItem extends SwordItem {
    private static final double RANGE = 14;
    private static final double WIDTH = 1.6;
    private static final float DAMAGE = 10f;

    public ZanpakutoItem(Properties properties) {
        super(Tiers.DIAMOND, properties.attributes(SwordItem.createAttributes(Tiers.DIAMOND, 3, -2.4f)));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel server) {
            Vec3 start = player.getEyePosition();
            Vec3 dir = player.getLookAngle();
            for (int i = 1; i <= RANGE; i++) {
                Vec3 at = start.add(dir.scale(i));
                server.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y - 0.3, at.z, 1, 0, 0, 0, 0);
                server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y - 0.3, at.z, 4, 0.4, 0.3, 0.4, 0.01);
            }
            AABB box = new AABB(start, start.add(dir.scale(RANGE))).inflate(WIDTH);
            for (LivingEntity hit : server.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive())) {
                Vec3 to = hit.position().add(0, hit.getBbHeight() / 2, 0).subtract(start);
                double along = to.dot(dir);
                if (along < 0 || along > RANGE || to.subtract(dir.scale(along)).length() > WIDTH + hit.getBbWidth() / 2) {
                    continue;
                }
                hit.invulnerableTime = 0;
                hit.hurt(player.damageSources().playerAttack(player), DAMAGE);
            }
            Fx.sound(server, player.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 0.6f);
            player.getCooldowns().addCooldown(this, 120);
            stack.hurtAndBreak(2, player, LivingEntity.getSlotForHand(hand));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.battlegroundsmod.zanpakuto.desc").withStyle(s -> s.withColor(0x6AD0FF)));
    }
}
