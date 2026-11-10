package com.starkinhon.battlegroundsmod.item;

import java.util.List;
import java.util.UUID;

import com.starkinhon.battlegroundsmod.combat.Fx;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

// Boneco de Palha (Jujutsu, Nobara's Ressonância): right click and the nail goes into the doll: the last one the player
// hit (within 30 s, up to 64 blocks away, walls don't matter) takes 7 damage and is pinned (heavy slowness 1,5 s).
// One doll per use, 4 s recharge. The last hit is kept on the player (JujutsuWorld.onHurt).
public class StrawDollItem extends Item {
    public static final String LAST_HIT = "souls_ressonancia_alvo";
    public static final String LAST_HIT_AT = "souls_ressonancia_quando";
    private static final int MEMORY = 600;
    private static final double RANGE = 64;

    public StrawDollItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer owner)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        var data = owner.getPersistentData();
        Entity target = data.hasUUID(LAST_HIT) && server.getGameTime() - data.getLong(LAST_HIT_AT) <= MEMORY
                ? server.getEntity(data.getUUID(LAST_HIT)) : null;
        if (!(target instanceof LivingEntity living) || !living.isAlive() || living.distanceTo(player) > RANGE) {
            owner.displayClientMessage(Component.translatable("item.battlegroundsmod.boneco_palha.none"), true);
            return InteractionResultHolder.fail(stack);
        }
        living.invulnerableTime = 0;
        living.hurt(player.damageSources().indirectMagic(player, player), 7f);
        living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 4));
        Fx.burst(server, ParticleTypes.CRIT, living.position().add(0, living.getBbHeight() * 0.6, 0), 30, 0.4, 0.3);
        Fx.sound(server, living.position(), SoundEvents.ANVIL_LAND, 0.7f, 1.6f);
        Fx.sound(server, player.position(), SoundEvents.ANVIL_LAND, 0.5f, 1.8f);
        player.getCooldowns().addCooldown(this, 80);
        if (!player.isCreative()) {
            stack.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    // Called when the player hits someone: they become the doll's target.
    public static void remember(ServerPlayer player, UUID target) {
        player.getPersistentData().putUUID(LAST_HIT, target);
        player.getPersistentData().putLong(LAST_HIT_AT, player.serverLevel().getGameTime());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.battlegroundsmod.boneco_palha.desc").withStyle(s -> s.withColor(0xD8B860)));
    }
}
