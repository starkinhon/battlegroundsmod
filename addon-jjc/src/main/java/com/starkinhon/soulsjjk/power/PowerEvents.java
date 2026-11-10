package com.starkinhon.soulsjjk.power;

import com.starkinhon.soulsjjk.compat.Att;

import com.starkinhon.soulsjjk.ModAttachments;
import com.starkinhon.soulsjjk.ModItems;
import com.starkinhon.soulsjjk.boss.SoulBoss;
import com.starkinhon.soulsjjk.command.BossCommand;
import com.starkinhon.soulsjjk.combat.CombatMode;
import com.starkinhon.soulsjjk.combat.Defense;
import com.starkinhon.soulsjjk.combat.Destructible;
import com.starkinhon.soulsjjk.combat.Freeze;
import com.starkinhon.soulsjjk.combat.Intangible;
import com.starkinhon.soulsjjk.combat.Melee;
import com.starkinhon.soulsjjk.combat.Stun;
import com.starkinhon.soulsjjk.combat.Tasks;
import com.starkinhon.soulsjjk.movement.Movement;
import com.starkinhon.soulsjjk.power.powers.uchiha.Izanami;
import com.starkinhon.soulsjjk.power.powers.uchiha.Susanoo;
import com.starkinhon.soulsjjk.skill.ComboSpec;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import com.starkinhon.soulsjjk.compat.LivingIncomingDamageEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.TickEvent;

// Game events that drive the power system.
public final class PowerEvents {

    private PowerEvents() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(PowerEvents::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(PowerEvents::onServerStopped);
        MinecraftForge.EVENT_BUS.addListener(PowerEvents::onPlayerTick);
        MinecraftForge.EVENT_BUS.addListener(PowerEvents::onLogin);
        MinecraftForge.EVENT_BUS.addListener(PowerEvents::onRespawn);
        MinecraftForge.EVENT_BUS.addListener(PowerEvents::onAttack);
        MinecraftForge.EVENT_BUS.addListener(PowerEvents::onDamage);
        MinecraftForge.EVENT_BUS.addListener(PowerEvents::onFall);
        MinecraftForge.EVENT_BUS.addListener(PowerEvents::onEntityJoin);
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.living.LivingHurtEvent e) -> onIncomingDamage(new com.starkinhon.soulsjjk.compat.LivingIncomingDamageEvent(e)));
        MinecraftForge.EVENT_BUS.addListener(PowerEvents::onKnockBack);
        MinecraftForge.EVENT_BUS.addListener(PowerEvents::onProjectileImpact);
        MinecraftForge.EVENT_BUS.addListener(PowerEvents::onRightClickItem);
        MinecraftForge.EVENT_BUS.addListener(PowerEvents::onRightClickBlock);
        MinecraftForge.EVENT_BUS.addListener(PowerEvents::onLeftClickBlock);
        MinecraftForge.EVENT_BUS.addListener(PowerEvents::onStartTracking);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOW, com.starkinhon.soulsjjk.power.powers.gojo.InfiniteVoid::onDeath);
        // Last, so a death the Izanami undoes never stops anything.
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, com.starkinhon.soulsjjk.combat.SkillStop::onDeath);
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Tasks.tick();
        com.starkinhon.soulsjjk.combat.BlockBreakFx.flush();
        if (event.getServer().getTickCount() % 200 == 0) {
            Defense.prune();
        }
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        Tasks.clear();
        com.starkinhon.soulsjjk.combat.BlockBreakFx.clear();
        Freeze.clear();
        Intangible.clear();
        com.starkinhon.soulsjjk.combat.Capture.clear();
        com.starkinhon.soulsjjk.power.powers.sub.jj.JjCommon.clear();
        Destructible.clear();
        Defense.clear();
        com.starkinhon.soulsjjk.power.powers.gojo.InfiniteVoid.clear();
        com.starkinhon.soulsjjk.power.powers.sukuna.ShrineDomain.clear();
        com.starkinhon.soulsjjk.power.powers.duel.DomainDuel.clear();
    }

    private static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) {
            Tasks.as(player, () -> {
                PowerLogic.tick(player);
                Movement.tick(player);
                Defense.tick(player);
                com.starkinhon.soulsjjk.combat.Charge.tick(player);
            });
        }
    }

    // First time a player joins, they get the power book. Not in a world with universes and progression (Stark
    // 2026-11-10): there everyone is born with only the basic attacks and gets souls from the characters they beat.
    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PowerData data = Att.get(player, ModAttachments.POWER);
            com.starkinhon.soulsjjk.power.powers.gojo.InfiniteVoid.onLogin(player);
            com.starkinhon.soulsjjk.power.powers.sukuna.ShrineDomain.onLogin(player);
            PowerLogic.sync(player);
        }
    }

    private static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer watcher) {
            PowerLogic.sendAura(target, watcher);
        }
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Att.get(player, ModAttachments.POWER).resetCombat();
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
        if (!CombatMode.freeHand(player) || !CombatMode.fighter(player) || !CombatMode.in(player)) {
            return;
        }
        event.setCanceled(true);
        Melee.punch(player, target);
    }

    // Dealing damage charges the ultimate, fighting keeps combat mode going for both sides, and whoever took the hit
    // gets the Cooldown effect (unless they blocked it).
    private static final float ULT_SCALE = 0.4f;

    private static void onDamage(LivingDamageEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player && player != event.getEntity()) {
            // Measured on the move's maximum, so higher tiers don't fill the ult in one combo; the maximums are about 2.5x
            // the old base damages (rebalance of 2026-11-08), hence ULT_SCALE.
            PowerLogic.addUltCharge(player, event.getEntity(), event.getAmount() / Tiers.damageMultiplier(player) * ULT_SCALE);
        }
        if (event.getSource().getEntity() instanceof LivingEntity attacker && attacker != event.getEntity()) {
            com.starkinhon.soulsjjk.power.powers.kokushibo.OniBody.onHurt(event.getEntity(), attacker);
            CombatMode.refresh(attacker);
            CombatMode.refresh(event.getEntity());
            long now = attacker.level().getGameTime();
            PowerData attackerData = Att.get(attacker, ModAttachments.POWER);
            attackerData.foughtId = event.getEntity().getId();
            attackerData.foughtAt = now;
            PowerData victimData = Att.get(event.getEntity(), ModAttachments.POWER);
            victimData.foughtId = attacker.getId();
            victimData.foughtAt = now;
            if (!Defense.blockedThisTick(event.getEntity())) {
                Stun.cooldown(event.getEntity(), ComboSpec.get().cooldownAoApanharTicks());
            }
        }
    }

    // The Meteoro used to make clouds out of block displays; any left in a world are thrown away when they load.
    private static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getEntity().getTags().contains("soulsjjk_meteor_cloud")) {
            event.setCanceled(true);
        }
    }

    private static void onFall(LivingFallEvent event) {
        if (Movement.ignoresFall(event.getEntity())) {
            event.setCanceled(true);
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player && Att.get(player, ModAttachments.POWER).noFallTicks > 0) {
            event.setCanceled(true);
        }
    }

    // Intangible or shielded entities take no damage, knockback or projectile hits (arrows fly through them). Only the
    // one whose cutscene holds them (Intangible.holdExcept) still lands its own blows.
    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        com.starkinhon.soulsjjk.combat.DamageDebug.incoming(event.getAmount());
        if (com.starkinhon.soulsjjk.combat.Combat.piercing()) {
            // Sukuna's World Cutting Slash: it ignores every defense.
            return;
        }
        if (Intangible.untouchableBy(event.getEntity(), event.getSource().getEntity())
                && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            event.setCanceled(true);
            com.starkinhon.soulsjjk.combat.DamageDebug.blocked("Intangible (cutscene/escudo de skill)");
            return;
        }
        // A stunned mob's swing (or one with the Cooldown) does nothing (players are stopped in onAttack, bosses in Melee).
        if (event.getSource().is(DamageTypes.MOB_ATTACK) && !Stun.canAttack(event.getSource().getDirectEntity())) {
            event.setCanceled(true);
            com.starkinhon.soulsjjk.combat.DamageDebug.blocked("atacante atordoado/Cooldown");
            return;
        }
        com.starkinhon.soulsjjk.power.powers.gojo.InfiniteVoid.onIncomingDamage(event);
        if (event.isCanceled()) {
            com.starkinhon.soulsjjk.combat.DamageDebug.blocked("Expansao do Gojo (InfiniteVoid)");
            return;
        }
        com.starkinhon.soulsjjk.power.powers.sukuna.ShrineDomain.onIncomingDamage(event);
        com.starkinhon.soulsjjk.power.powers.duel.DomainDuel.onIncomingDamage(event);
        if (event.isCanceled()) {
            com.starkinhon.soulsjjk.combat.DamageDebug.blocked("Santuario do Sukuna ou Duelo de Expansoes");
            return;
        }
        com.starkinhon.soulsjjk.power.powers.aizen.UmDedo.onIncomingDamage(event.getEntity(), event);
        if (event.isCanceled()) {
            com.starkinhon.soulsjjk.combat.DamageDebug.blocked("Um Dedo do Aizen");
            return;
        }
        com.starkinhon.soulsjjk.power.powers.sub.SubDefense.onIncomingDamage(event);
        if (event.isCanceled()) {
            com.starkinhon.soulsjjk.combat.DamageDebug.blocked("defesa de SubAlma (SubDefense)");
            return;
        }
        Defense.onIncomingDamage(event.getEntity(), event);
        if (event.isCanceled()) {
            com.starkinhon.soulsjjk.combat.DamageDebug.blocked("defesa/parry (Defense)");
            return;
        }
        Susanoo.onIncomingDamage(event.getEntity(), event);
        // Bosses hit as hard as their file says.
        if (!event.isCanceled() && event.getSource().getEntity() instanceof SoulBoss boss) {
            // (bosses of the Souls mod are not in the addon)
        }
        // The target's Escudo against a lower tier (Stark, 2026-11-08): a god takes nothing from a human. Every blow
        // with an attacker goes through here, the mod's moves, vanilla hits and mobs alike (a plain mob is a human).
        if (!event.isCanceled() && event.getSource().getEntity() instanceof LivingEntity attacker && attacker != event.getEntity()) {
            event.setAmount(Tiers.shield(attacker, event.getEntity(), event.getAmount()));
            com.starkinhon.soulsjjk.combat.DamageDebug.shielded(event.getAmount());
        }
        // The target's Resistencia (2026-11-09): every blow, the same tier's too; the Armadura comes after this, in vanilla.
        if (!event.isCanceled()) {
            event.setAmount(Tiers.resist(event.getEntity(), event.getSource(), event.getAmount()));
            com.starkinhon.soulsjjk.combat.DamageDebug.finished(event.getAmount());
        } else {
            com.starkinhon.soulsjjk.combat.DamageDebug.blocked("Susanoo");
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
