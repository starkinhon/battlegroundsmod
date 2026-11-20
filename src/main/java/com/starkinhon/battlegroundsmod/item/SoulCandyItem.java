// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.item;

import java.util.List;

import com.starkinhon.battlegroundsmod.combat.Fx;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// Pílula de Alma (Bleach, Soul Candy from Urahara's shop): the soul leaves the body for 20 s, fast and unseen. 1 min
// recharge.
// Stark 2026-11-19: invisibility alone fooled nobody ("eles veem do mesmo jeito"). Now, out of the body, mobs and
// bosses really lose the player: whoever was after them lets go, nobody can pick them as a target (SoulBoss checks
// outOfBody in stillHunting) and the boss's sense of a hidden player skips them. Hitting anyone puts the soul back.
public class SoulCandyItem extends Item {
    private static final int DURATION = 400;
    private static final String UNTIL = "souls_alma_fora_ate";

    public SoulCandyItem(Properties properties) {
        super(properties.stacksTo(8).food(new FoodProperties.Builder().nutrition(1).saturationModifier(0.1f).alwaysEdible().fast().build()));
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(SoulCandyItem::onTarget);
        NeoForge.EVENT_BUS.addListener(SoulCandyItem::onTick);
        NeoForge.EVENT_BUS.addListener(SoulCandyItem::onAttack);
        NeoForge.EVENT_BUS.addListener(SoulCandyItem::onDamage);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        ItemStack left = super.finishUsingItem(stack, level, entity);
        if (entity instanceof ServerPlayer player) {
            player.getPersistentData().putLong(UNTIL, player.level().getGameTime() + DURATION);
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, DURATION, 0));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, DURATION, 1));
            dropHunters(player);
            Fx.burst(player.serverLevel(), ParticleTypes.SOUL, player.position().add(0, 1, 0), 30, 0.4, 0.03);
            Fx.sound(player.serverLevel(), player.position(), SoundEvents.ENDERMAN_TELEPORT, 1f, 1.2f);
            player.getCooldowns().addCooldown(this, 1200);
        }
        return left;
    }

    // True while the player's soul is out of the body (server side).
    public static boolean outOfBody(Entity entity) {
        return entity instanceof Player player && !player.level().isClientSide
                && player.getPersistentData().getLong(UNTIL) > player.level().getGameTime();
    }

    private static void dropHunters(ServerPlayer player) {
        for (Mob mob : player.serverLevel().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(64), m -> m.getTarget() == player)) {
            mob.setTarget(null);
        }
    }

    private static void comeBack(ServerPlayer player) {
        player.getPersistentData().remove(UNTIL);
        player.removeEffect(MobEffects.INVISIBILITY);
        Fx.burst(player.serverLevel(), ParticleTypes.SOUL, player.position().add(0, 1, 0), 20, 0.4, 0.03);
        Fx.sound(player.serverLevel(), player.position(), SoundEvents.ENDERMAN_TELEPORT, 1f, 0.8f);
    }

    private static void onTarget(LivingChangeTargetEvent event) {
        if (outOfBody(event.getNewAboutToBeSetTarget())) {
            event.setCanceled(true);
        }
    }

    private static void onTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 10 == 0 && outOfBody(player)) {
            dropHunters(player);
        }
    }

    // Hitting anyone, by hand or by a skill, puts the soul back.
    private static void onAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && outOfBody(player)) {
            comeBack(player);
        }
    }

    private static void onDamage(LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player && player != event.getEntity() && outOfBody(player)) {
            comeBack(player);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.battlegroundsmod.pilula_alma.desc").withStyle(s -> s.withColor(0x9ADFFF)));
    }
}
