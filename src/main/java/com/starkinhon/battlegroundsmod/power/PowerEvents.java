package com.starkinhon.battlegroundsmod.power;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.ModItems;
import com.starkinhon.battlegroundsmod.boss.SoulBoss;
import com.starkinhon.battlegroundsmod.command.BossCommand;
import com.starkinhon.battlegroundsmod.combat.CombatMode;
import com.starkinhon.battlegroundsmod.combat.Defense;
import com.starkinhon.battlegroundsmod.combat.Destructible;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Intangible;
import com.starkinhon.battlegroundsmod.combat.Melee;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.movement.Movement;
import com.starkinhon.battlegroundsmod.power.powers.uchiha.Izanami;
import com.starkinhon.battlegroundsmod.power.powers.uchiha.Susanoo;
import com.starkinhon.battlegroundsmod.skill.ComboSpec;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// Game events that drive the power system.
public final class PowerEvents {

    private PowerEvents() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(PowerEvents::onServerTick);
        NeoForge.EVENT_BUS.addListener(PowerEvents::onServerStopped);
        NeoForge.EVENT_BUS.addListener(PowerEvents::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(PowerEvents::onLogin);
        NeoForge.EVENT_BUS.addListener(PowerEvents::onRespawn);
        NeoForge.EVENT_BUS.addListener(PowerEvents::onAttack);
        NeoForge.EVENT_BUS.addListener(PowerEvents::onDamage);
        NeoForge.EVENT_BUS.addListener(PowerEvents::onFall);
        NeoForge.EVENT_BUS.addListener(PowerEvents::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(PowerEvents::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(PowerEvents::onKnockBack);
        NeoForge.EVENT_BUS.addListener(PowerEvents::onProjectileImpact);
        NeoForge.EVENT_BUS.addListener(PowerEvents::onRightClickItem);
        NeoForge.EVENT_BUS.addListener(PowerEvents::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(PowerEvents::onLeftClickBlock);
        NeoForge.EVENT_BUS.addListener(PowerEvents::onStartTracking);
        NeoForge.EVENT_BUS.addListener(Izanami::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(Izanami::onDeath);
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        Tasks.tick();
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        Tasks.clear();
        Freeze.clear();
        Intangible.clear();
        Destructible.clear();
        Defense.clear();
        Susanoo.clear();
        Izanami.clear();
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PowerLogic.tick(player);
            Movement.tick(player);
            Defense.tick(player);
        }
    }

    // First time a player joins, they get the power book.
    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PowerData data = player.getData(ModAttachments.POWER);
            if (!data.receivedBook) {
                data.receivedBook = true;
                player.getInventory().add(new ItemStack(ModItems.POWER_BOOK.get()));
                player.displayClientMessage(Component.translatable("message.battlegroundsmod.welcome"), false);
            }
            PowerLogic.sync(player);
            BossCommand.syncBossBar(player);
        }
    }

    private static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer watcher) {
            PowerLogic.sendAura(target, watcher);
        }
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.getData(ModAttachments.POWER).resetCombat();
            PowerLogic.sync(player);
        }
    }

    // In combat mode, left click with an empty hand runs the punch combo instead of the vanilla hit.
    private static void onAttack(AttackEntityEvent event) {
        if (Freeze.isFrozen(event.getEntity()) || !Stun.canAttack(event.getEntity())) {
            event.setCanceled(true);
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getTarget() instanceof LivingEntity target)) {
            return;
        }
        if (!player.getMainHandItem().isEmpty() || !player.getData(ModAttachments.POWER).hasPower() || !CombatMode.in(player)) {
            return;
        }
        event.setCanceled(true);
        Melee.punch(player, target);
    }

    // Dealing damage charges the ultimate, fighting keeps combat mode going for both sides, and whoever took the hit
    // gets the Cooldown effect (unless they blocked it).
    private static void onDamage(LivingDamageEvent.Post event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player && player != event.getEntity()) {
            // Measured at human strength, so higher tiers don't fill the ult in one combo.
            PowerLogic.addUltCharge(player, event.getNewDamage() / Tiers.damageMultiplier(player));
        }
        if (event.getSource().getEntity() instanceof LivingEntity attacker && attacker != event.getEntity()) {
            CombatMode.refresh(attacker);
            CombatMode.refresh(event.getEntity());
            long now = attacker.level().getGameTime();
            PowerData attackerData = attacker.getData(ModAttachments.POWER);
            attackerData.foughtId = event.getEntity().getId();
            attackerData.foughtAt = now;
            PowerData victimData = event.getEntity().getData(ModAttachments.POWER);
            victimData.foughtId = attacker.getId();
            victimData.foughtAt = now;
            if (!Defense.blockedThisTick(event.getEntity())) {
                Stun.cooldown(event.getEntity(), ComboSpec.get().cooldownAoApanharTicks());
            }
        }
    }

    // The Meteoro used to make clouds out of block displays; any left in a world are thrown away when they load.
    private static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getEntity().getTags().contains("battlegroundsmod_meteor_cloud")) {
            event.setCanceled(true);
        }
    }

    private static void onFall(LivingFallEvent event) {
        if (Movement.ignoresFall(event.getEntity())) {
            event.setCanceled(true);
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player && player.getData(ModAttachments.POWER).noFallTicks > 0) {
            event.setCanceled(true);
        }
    }

    // Intangible or shielded entities take no damage, knockback or projectile hits (arrows fly through them).
    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (Intangible.untouchable(event.getEntity()) && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            event.setCanceled(true);
            return;
        }
        // A stunned mob's swing (or one with the Cooldown) does nothing (players are stopped in onAttack, bosses in Melee).
        if (event.getSource().is(DamageTypes.MOB_ATTACK) && !Stun.canAttack(event.getSource().getDirectEntity())) {
            event.setCanceled(true);
            return;
        }
        Defense.onIncomingDamage(event.getEntity(), event);
        if (event.isCanceled()) {
            return;
        }
        Susanoo.onIncomingDamage(event.getEntity(), event);
        // Bosses hit as hard as their file says.
        if (!event.isCanceled() && event.getSource().getEntity() instanceof SoulBoss boss) {
            event.setAmount(event.getAmount() * boss.atributos().multDano());
        }
    }

    private static void onKnockBack(LivingKnockBackEvent event) {
        if (Intangible.untouchable(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    private static void onProjectileImpact(ProjectileImpactEvent event) {
        if (event.getRayTraceResult() instanceof EntityHitResult hit && Intangible.untouchable(hit.getEntity())) {
            event.setCanceled(true);
        }
    }

    // Frozen players can't use or break anything.
    private static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (Freeze.isFrozen(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (Freeze.isFrozen(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    private static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (Freeze.isFrozen(event.getEntity())) {
            event.setCanceled(true);
        }
    }
}
