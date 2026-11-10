package com.starkinhon.battlegroundsmod.item;

import java.util.List;

import com.starkinhon.battlegroundsmod.combat.Fx;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

// Bastão Mágico (Nyoi-bo, Dragon Ball): a staff. Right click: "Cresça, Nyoi-bo!" It shoots out up to 16 blocks and hits
// the first one in its way hard, pushing them away. If it hits a wall instead, it vaults the player back. 2 s recharge.
public class PowerPoleItem extends SwordItem {
    private static final double REACH = 16;
    private static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(0.85f, 0.15f, 0.1f), 1.4f);

    public PowerPoleItem(Properties properties) {
        super(Tiers.IRON, properties.attributes(SwordItem.createAttributes(Tiers.IRON, 2, -2.2f)));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel server) {
            Vec3 eye = player.getEyePosition();
            Vec3 look = player.getLookAngle();
            Vec3 far = eye.add(look.scale(REACH));
            HitResult block = level.clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            Vec3 end = block.getType() == HitResult.Type.MISS ? far : block.getLocation();
            EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, player, eye, end,
                    new AABB(eye, end).inflate(1), e -> e instanceof LivingEntity && e != player && e.isAlive() && !e.isSpectator(), 0.4f);
            if (hit != null) {
                end = hit.getLocation();
                Entity target = hit.getEntity();
                target.hurt(player.damageSources().playerAttack(player), 9f);
                target.push(look.x * 1.6, 0.35, look.z * 1.6);
                target.hurtMarked = true;
                Fx.sound(server, end, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1f, 0.7f);
            } else if (block.getType() == HitResult.Type.BLOCK) {
                // Pushes off the wall: the staff throws the player the other way.
                player.push(-look.x * 1.4, Math.max(0.4, -look.y * 1.4), -look.z * 1.4);
                player.hurtMarked = true;
                Fx.sound(server, end, SoundEvents.WOOD_HIT, 1f, 0.8f);
            }
            double length = end.distanceTo(eye);
            for (double d = 0.8; d < length; d += 0.35) {
                Vec3 p = eye.add(look.scale(d)).add(0, -0.25, 0);
                server.sendParticles(RED, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
            Fx.sound(server, player.position(), SoundEvents.TRIDENT_THROW.value(), 0.8f, 1.5f);
            player.getCooldowns().addCooldown(this, 40);
            stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.battlegroundsmod.bastao_magico.desc").withStyle(s -> s.withColor(0xFF7A4A)));
    }
}
