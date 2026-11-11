package com.starkinhon.battlegroundsmod.item;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.DbCommon;
import com.starkinhon.battlegroundsmod.combat.Tasks;

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

// Papel-Bomba (Kibaku Fuda, Naruto): right click throws one up to 24 blocks. It sticks to whoever it hits (and follows
// them) or to the ground or wall, burns for 1.2 s and blows up like the Shikamaru's tags (the same tag and fire
// effects): the blast grows with the user's tier like any move, and every other armed tag within 4.6 blocks goes off
// with it, one after the other, so a few thrown around a spot make a chain (Stark 2026-11-10: the old one, a plain
// vanilla explosion of fixed size, was "uma bosta"). Throws every 0.4 s.
public class PaperBombItem extends Item {
    private static final double RANGE = 24;
    private static final int FUSE = 24;
    private static final double CHAIN = 4.6;
    // raw damage on the Tier 0 scale (Combat.hurtMagic scales it by the thrower's tier)
    private static final float DAMAGE = 110;

    private static final class Tag {
        final int id = SubNet.newId();
        final Player owner;
        final ServerLevel level;
        final Entity stuck;
        final Vec3 offset;
        Vec3 pos;
        int fuse = FUSE;
        boolean gone;

        Tag(Player owner, ServerLevel level, Entity stuck, Vec3 pos) {
            this.owner = owner;
            this.level = level;
            this.stuck = stuck;
            this.pos = pos;
            this.offset = stuck != null ? pos.subtract(stuck.position()) : Vec3.ZERO;
        }
    }

    private static final List<Tag> ARMED = new ArrayList<>();

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
            Tag tag = new Tag(player, server, stuck, spot);
            ARMED.add(tag);
            int fly = Math.max(2, (int) Math.round(spot.distanceTo(eye) / 3.0));
            Vec3 hand0 = eye.add(player.getLookAngle().scale(0.5)).add(0, -0.3, 0);
            SubNet.send(server, SubNet.KUNAI, 0, hand0, spot.subtract(hand0), fly, 0, 0);
            Fx.sound(server, player.position(), SoundEvents.BOOK_PAGE_TURN, 1f, 1.4f);
            Tasks.add(age -> {
                if (tag.gone) {
                    return true;
                }
                if (tag.stuck != null && tag.stuck.isAlive()) {
                    tag.pos = tag.stuck.position().add(tag.offset);
                }
                if (age < fly) {
                    return false;
                }
                SubNet.send(server, SubNet.TAG, tag.id, tag.pos, Vec3.ZERO, 1, 1 - tag.fuse / (float) FUSE, 0);
                if (age % 6 == 0) {
                    Fx.sound(server, tag.pos, SoundEvents.FIRECHARGE_USE, 0.4f, 1.8f);
                }
                if (tag.fuse-- <= 0) {
                    boom(tag);
                    return true;
                }
                return false;
            });
            player.getCooldowns().addCooldown(this, 8);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private static void boom(Tag tag) {
        if (tag.gone) {
            return;
        }
        tag.gone = true;
        ARMED.remove(tag);
        ServerLevel level = tag.level;
        Vec3 at = tag.pos;
        double r = 3.2;
        SubNet.send(level, SubNet.TAG, tag.id, at, Vec3.ZERO, 1, 0, 1);
        SubNet.send(level, SubNet.KATON_BOOM, 0, at, new Vec3(at.y, 0, 0), (float) r, 0, tag.id % 40);
        DbCommon.screen(level, at, 58);
        Fx.sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 1.6f, 0.9f + level.getRandom().nextFloat() * 0.2f);
        for (LivingEntity t : Combat.inRadius(level, at, r, tag.owner)) {
            double k = 1 - 0.5 * Math.min(1, Combat.center(t).distanceTo(at) / r);
            Combat.hurtMagic(tag.owner, t, (float) (DAMAGE * k));
            Combat.pushFrom(t, at, 0.7 * k, 0.4);
        }
        // the chain: the nearest armed tags go next, a few ticks apart
        List<Tag> next = new ArrayList<>();
        for (Tag other : ARMED) {
            if (!other.gone && other.level == level && other.pos.distanceTo(at) <= CHAIN) {
                next.add(other);
            }
        }
        next.sort(Comparator.comparingDouble(o -> o.pos.distanceToSqr(at)));
        for (int i = 0; i < next.size(); i++) {
            Tag other = next.get(i);
            other.fuse = Math.min(other.fuse, 2 + i * 2);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.battlegroundsmod.papel_bomba.desc").withStyle(s -> s.withColor(0xFF6A4A)));
    }
}
