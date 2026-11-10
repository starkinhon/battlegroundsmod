package com.starkinhon.battlegroundsmod.item;

import java.util.List;

import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.combat.Tasks;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// Papel-Bomba (Kibaku Fuda, Naruto): right click throws it up to 24 blocks. It sticks to whoever it hits (and follows
// them) or to the wall, hisses for 1.5 s and blows up. The blast hurts but breaks no blocks.
public class PaperBombItem extends Item {
    private static final double RANGE = 24;
    private static final int FUSE = 30;

    public PaperBombItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel server) {
            Vec3 eye = player.getEyePosition();
            Vec3 far = eye.add(player.getLookAngle().scale(RANGE));
            HitResult block = level.clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            Vec3 end = block.getType() == HitResult.Type.MISS ? far : block.getLocation();
            EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, player, eye, end, new AABB(eye, end).inflate(1),
                    e -> e instanceof LivingEntity && e != player && e.isAlive(), 0.3f);
            Entity stuck = hit != null ? hit.getEntity() : null;
            Vec3 spot = stuck != null ? hit.getLocation() : end;
            Vec3 offset = stuck != null ? spot.subtract(stuck.position()) : Vec3.ZERO;
            Fx.sound(server, player.position(), SoundEvents.BOOK_PAGE_TURN, 1f, 1.4f);
            Tasks.repeat(FUSE, age -> {
                Vec3 at = stuck != null && stuck.isAlive() ? stuck.position().add(offset) : spot;
                server.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 2, 0.05, 0.05, 0.05, 0.01);
                if (age % 6 == 0) {
                    Fx.sound(server, at, SoundEvents.TNT_PRIMED, 0.5f, 1.8f);
                }
                if (age == FUSE - 1) {
                    server.explode(player, at.x, at.y, at.z, 2.6f, Level.ExplosionInteraction.NONE);
                }
            });
            player.getCooldowns().addCooldown(this, 20);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.battlegroundsmod.papel_bomba.desc").withStyle(s -> s.withColor(0xFF6A4A)));
    }
}
