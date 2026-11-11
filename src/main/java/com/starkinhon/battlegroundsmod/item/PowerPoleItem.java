package com.starkinhon.battlegroundsmod.item;

import java.util.ArrayList;
import java.util.List;

import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.Db;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.DbCommon;

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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// Bastão Mágico (Nyoi-bo, Dragon Ball): a staff. Right click: "Cresça, Nyoi-bo!" It shoots out up to 18 blocks and
// sweeps EVERYONE on its line (each knocked along it), with a hit that grows with the user's tier like any move (it was
// a flat 9, useless past the first tiers: Stark 2026-11-10 "item inútil"). If it hits a wall or the ground with nobody in
// the way, it pulls the player there instead (Goku riding the staff across). The staff itself is the ki-red streak of
// the Dragon Ball effects, no vanilla particles. 1.5 s recharge.
public class PowerPoleItem extends SwordItem {
    private static final double REACH = 18;
    // raw damage on the Tier 0 scale (Combat.hurtMagic scales it by the user's tier)
    private static final float DAMAGE = 80;

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
            Vec3 from = eye.add(look.scale(0.6)).add(0, -0.3, 0);
            List<LivingEntity> hit = new ArrayList<>();
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, end).inflate(1.2),
                    e -> e != player && e.isAlive() && !e.isSpectator())) {
                if (e.getBoundingBox().inflate(0.45).clip(eye, end).isPresent()) {
                    hit.add(e);
                }
            }
            if (!hit.isEmpty()) {
                for (LivingEntity target : hit) {
                    Combat.hurtMagic(player, target, DAMAGE);
                    Combat.push(target, look, 1.4, 0.35);
                    DbCommon.hit(server, Combat.center(target), 1.2f, Db.RED);
                }
                Fx.sound(server, hit.get(0).position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.2f, 0.7f);
            } else if (block.getType() == HitResult.Type.BLOCK) {
                // Rides the staff there.
                Vec3 to = end.subtract(player.position());
                double dist = to.length();
                Vec3 pull = to.normalize().scale(Math.min(2.6, 0.6 + dist * 0.14));
                player.setDeltaMovement(pull.x, Math.max(0.35, pull.y + 0.25), pull.z);
                player.hurtMarked = true;
                player.fallDistance = 0;
                Fx.sound(server, end, SoundEvents.WOOD_HIT, 1f, 0.8f);
            }
            SubNet.send(server, SubNet.STREAK, 0, from, end.subtract(from), 0.32f, Db.RED, 7);
            Fx.sound(server, player.position(), SoundEvents.TRIDENT_THROW.value(), 0.8f, 1.5f);
            player.getCooldowns().addCooldown(this, 30);
            stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.battlegroundsmod.bastao_magico.desc").withStyle(s -> s.withColor(0xFF7A4A)));
    }
}
