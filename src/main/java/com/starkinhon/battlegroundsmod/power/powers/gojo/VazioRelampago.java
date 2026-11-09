package com.starkinhon.battlegroundsmod.power.powers.gojo;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.ModEffects;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Defense;
import com.starkinhon.battlegroundsmod.combat.Destructible;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.SkillStop;
import com.starkinhon.battlegroundsmod.combat.Stamina;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.PowerLogic;
import com.starkinhon.battlegroundsmod.power.Tiers;
import com.starkinhon.battlegroundsmod.power.powers.uchiha.Katon;
import com.starkinhon.battlegroundsmod.skill.DominioSpec;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.monster.SpellcasterIllager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// Shift + V of Gojo: the Expansão de 0,2 segundos (Stark's reference, approved in the Visualizador, scene gojo-dom02).
// He makes the Expansão's hand sign quickly (Stark's own pose, gojo.dominio, only played faster: gojo.dominio_relampago)
// and the void snaps open for an instant: a spiky black blot swallows him, the screen goes black, the inside of our own
// Expansão lights up (the black hole and its rings, ink falling into it, the water floor, those caught in grey), and it
// closes in an X across the whole screen that thins into two lines (client/fx/VoidFlashScene).
// What it does is a counter: everyone within the normal Expansão's reach (radius()) has whatever skill they had going broken (held, halfway through,
// active, their attacks in the air, even a Domain opening). Whoever was charging or using something when he pressed, or
// when it snapped, also loses all stamina (fragile), so the big reward is reading the enemy. Everyone caught takes Brain
// Damage, held completely still (brainTicks): 3 s for bosses and mobs, 5 s for players, plus 1 s for each tier the target
// is below Gojo (Stark's rule). With nobody in reach it does not go off (a warning shows).
public final class VazioRelampago {
    public static final String SKILL = "gojo.dominio_relampago";
    public static final int COOLDOWN = 800;
    // The client's guess of the reach (the server reads the Expansão's own radius, 40 by default).
    public static final double RADIUS = 40;
    // The tick the void snaps open (the hand sign before it), and how long the X takes to close after.
    public static final int SNAP = 7;
    public static final int LENGTH = 50;
    // Brain Damage: base for bosses and mobs, base for players, and how much each tier below Gojo adds.
    public static final int BRAIN_MOBS = 60;
    public static final int BRAIN_PLAYERS = 100;
    public static final int BRAIN_PER_TIER = 20;
    // Gojo stands for the sign and the snap (his camera stays free).
    private static final int ROOT = 12;
    // Players this close see the void on their screen.
    private static final double VIEW_DISTANCE = 64;

    private VazioRelampago() {
    }

    public static boolean use(LivingEntity caster, ServerLevel level) {
        if (inDomain(caster)) {
            // Inside a Domain (his own open, someone else's, the space of one) or in a Duelo de Expansões: it does not go off
            // (Stark, 2026-11-09: the boss Gojo snapped it in the tie and inside his own Expansão, and breaking a
            // Santuário mid-way left everyone stuck in it).
            if (caster instanceof ServerPlayer player) {
                player.displayClientMessage(Component.translatable("message.battlegroundsmod.vazio_relampago_dominio"), true);
            }
            return false;
        }
        List<LivingEntity> near = targets(level, caster);
        if (near.isEmpty()) {
            if (caster instanceof ServerPlayer player) {
                player.displayClientMessage(Component.translatable("message.battlegroundsmod.vazio_relampago_ninguem"), true);
            }
            return false;
        }
        PowerData data = caster.getData(ModAttachments.POWER);
        // Who was in the middle of something the moment he pressed (they still count if they let go before the snap).
        Set<LivingEntity> busy = new HashSet<>();
        for (LivingEntity target : near) {
            if (busy(target)) {
                busy.add(target);
            }
        }
        data.busyTicks = ROOT;
        Freeze.holdLookFree(caster, ROOT);
        caster.addEffect(new MobEffectInstance(ModEffects.ENRAIZADO, ROOT, 0, false, false, false));
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), SKILL, data.awakened));
        Vec3 center = caster.position();
        ModPayloads.VoidFlash payload = new ModPayloads.VoidFlash(caster.getId(), center);
        for (ServerPlayer player : level.players()) {
            if (player == caster || player.position().distanceTo(center) < VIEW_DISTANCE) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
        sound(level, caster, SoundEvents.BEACON_ACTIVATE, 1.4f, 1.9f);
        Tasks.later(SNAP, () -> {
            if (!caster.isAlive() || caster.level() != level || inDomain(caster)) {
                return;
            }
            snap(level, caster, busy);
        });
        // The X closing.
        Tasks.later(30, () -> sound(level, caster, SoundEvents.AMETHYST_CLUSTER_BREAK, 1.8f, 0.7f));
        return true;
    }

    private static void snap(ServerLevel level, LivingEntity caster, Set<LivingEntity> busy) {
        Vec3 center = caster.position();
        sound(level, caster, SoundEvents.WARDEN_SONIC_BOOM, 2.2f, 1.7f);
        sound(level, caster, SoundEvents.BEACON_DEACTIVATE, 2.0f, 0.6f);
        // Everything in the air inside it is gone (only Gojo's own attacks stay).
        Destructible.eraseAlong(level, center, center, radius(caster), caster);
        for (LivingEntity target : targets(level, caster)) {
            boolean caught = busy.contains(target) || busy(target);
            breakAll(target);
            int ticks = brainTicks(caster, target);
            // Brain Damage, a complete stun the whole time on anyone (bosses too, no boss resistance): stopped where they
            // stand, no walking, jumping, punches, skills or AI. Mobs and bosses are held still with the AI off (Freeze);
            // players too, but their camera stays free (turning does nothing for them).
            InfiniteVoid.brainDamage(target, ticks);
            if (target instanceof Player) {
                Freeze.holdLookFree(target, ticks);
            } else {
                Freeze.hold(target, ticks);
            }
            if (caught && target.hasData(ModAttachments.POWER)) {
                Stamina.exhaust(target);
            }
            if (target instanceof ServerPlayer player) {
                player.displayClientMessage(Component.translatable(caught ? "message.battlegroundsmod.vazio_relampago_pego" : "message.battlegroundsmod.vazio_relampago_atingido")
                        .withStyle(s -> s.withColor(0x8FE6FF)), true);
            }
        }
    }

    // Breaks whatever skill target had going: a Domain of theirs shatters (it brings everyone back by itself), any other
    // move of theirs still running is dropped (like the Izanami does: their tasks, not the bookkeeping ones), the Katon
    // and the Susanoo are put out, the guard drops, and their sounds and effects stop.
    public static void breakAll(LivingEntity target) {
        // A Santuário opening closes the way Sukuna's own V closes it (everyone back in the world): dropping its task
        // left the arena, the holds and the clients' scene stuck for good.
        boolean domain = InfiniteVoid.cancel(target) | com.starkinhon.battlegroundsmod.power.powers.sukuna.ShrineDomain.cancel(target);
        if (target.hasData(ModAttachments.POWER)) {
            Katon.cancel(target);
            PowerLogic.interrupt(target);
            target.getData(ModAttachments.POWER).chargeReportedAt = -10;
        } else {
            SkillStop.stop(target);
        }
        Defense.stop(target);
        if (!domain) {
            // The interrupt first, and the target's own tasks dropped only a couple of ticks later: a move that reads the
            // interrupt closes itself on its next tick (frees its key, its flags), and only what is still running after
            // that is cut. Dropping them right away left keys dead for good (Sukuna's Z and B after the boss's Shift+V,
            // Stark 2026-11-09), and the old cancelSince(0, ...) also dropped every task with no owner in the world.
            Tasks.addSystem(age -> {
                if (age < 2) {
                    return false;
                }
                Tasks.cancelOwned(target);
                return true;
            });
        }
    }

    // Charging, holding a key, in the middle of a move, or just cast one. A fighter of the mod reads that from its power
    // data; any other mob (no power data) counts when it is in the middle of its own attack: swinging, drawing a bow or
    // crossbow (anything held in use), a creeper swelling, an illager casting, a ranged mob aiming.
    public static boolean busy(LivingEntity target) {
        if (target.hasData(ModAttachments.POWER)) {
            PowerData data = target.getData(ModAttachments.POWER);
            long now = target.level().getGameTime();
            if (data.chargeReportedAt >= now - 2 || data.busyTicks > 0 || data.lastCastTime >= now - 10
                    || data.abilityHeld && data.chargeLeft > 0) {
                return true;
            }
        }
        if (target instanceof Player) {
            return false;
        }
        return target.swinging || target.isUsingItem()
                || target instanceof Creeper creeper && creeper.getSwellDir() > 0
                || target instanceof SpellcasterIllager illager && illager.isCastingSpell()
                || target instanceof RangedAttackMob && target instanceof Mob mob && mob.isAggressive();
    }

    // 3 s for bosses and mobs, 5 s for players, plus 1 s for each tier the target is below the caster (tier 10 is the
    // weakest, so a higher number is a lower tier). Same tier or above: only the base.
    public static int brainTicks(LivingEntity caster, LivingEntity target) {
        int base = target instanceof Player ? BRAIN_PLAYERS : BRAIN_MOBS;
        int below = Math.max(0, tier(target) - tier(caster));
        return base + below * BRAIN_PER_TIER;
    }

    // Without attaching power data to a mob that has none (a plain mob is human, tier 10).
    private static int tier(LivingEntity entity) {
        return entity.hasData(ModAttachments.POWER) ? Tiers.of(entity) : Tiers.HUMANO;
    }

    // Same reach as the normal Expansão (its radius in gojo.dominio, awakened or not).
    public static double radius(LivingEntity caster) {
        boolean awakened = caster.hasData(ModAttachments.POWER) && caster.getData(ModAttachments.POWER).awakened;
        return DominioSpec.get(awakened).radius();
    }

    // In a Duelo de Expansões, with a Domain of his own open, caught in someone else's or up in the space of one.
    public static boolean inDomain(LivingEntity entity) {
        return com.starkinhon.battlegroundsmod.power.powers.duel.DomainDuel.fighting(entity)
                || InfiniteVoid.open(entity) || InfiniteVoid.blocks(entity) || InfiniteVoid.insideAny(entity)
                || com.starkinhon.battlegroundsmod.power.powers.sukuna.ShrineDomain.open(entity)
                || com.starkinhon.battlegroundsmod.power.powers.sukuna.ShrineDomain.blocks(entity)
                || com.starkinhon.battlegroundsmod.power.powers.sukuna.ShrineDomain.insideAny(entity);
    }

    // Everyone within reach but players in creative (they see it, but take none of it), the two of a Duelo de Expansões
    // and anyone up in the space of a Domain (not in the same world as him).
    private static List<LivingEntity> targets(ServerLevel level, LivingEntity caster) {
        return Combat.inRadius(level, caster.position().add(0, 1, 0), radius(caster), caster).stream()
                .filter(e -> !(e instanceof Player player && (player.isCreative() || player.isSpectator())))
                .filter(e -> !com.starkinhon.battlegroundsmod.power.powers.duel.DomainDuel.fighting(e)
                        && !InfiniteVoid.insideAny(e)
                        && !com.starkinhon.battlegroundsmod.power.powers.sukuna.ShrineDomain.insideAny(e))
                .toList();
    }

    private static void sound(ServerLevel level, LivingEntity caster, SoundEvent sound, float volume, float pitch) {
        SkillStop.sound(level, caster, caster.position().add(0, 1, 0), sound, volume, pitch);
    }
}
