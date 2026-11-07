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
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.PowerLogic;
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
// when it snapped, is stunned for STUN_CHARGING with no stamina left (fragile). The others only for STUN_OTHERS, so the
// key is never dead, but the big reward is reading the enemy. With nobody in reach it does not go off (a warning shows).
public final class VazioRelampago {
    public static final String SKILL = "gojo.dominio_relampago";
    public static final int COOLDOWN = 800;
    // The client's guess of the reach (the server reads the Expansão's own radius, 40 by default).
    public static final double RADIUS = 40;
    // The tick the void snaps open (the hand sign before it), and how long the X takes to close after.
    public static final int SNAP = 7;
    public static final int LENGTH = 50;
    public static final int STUN_CHARGING = 140;
    public static final int STUN_OTHERS = 30;
    // Gojo stands for the sign and the snap (his camera stays free).
    private static final int ROOT = 12;
    // Players this close see the void on their screen.
    private static final double VIEW_DISTANCE = 64;

    private VazioRelampago() {
    }

    public static boolean use(LivingEntity caster, ServerLevel level) {
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
            if (!caster.isAlive() || caster.level() != level) {
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
            if (caught) {
                Stun.apply(target, STUN_CHARGING);
                if (target.hasData(ModAttachments.POWER)) {
                    Stamina.exhaust(target);
                }
            } else {
                Stun.apply(target, STUN_OTHERS);
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
        boolean domain = InfiniteVoid.cancel(target);
        if (!domain) {
            Tasks.cancelSince(0, owner -> owner == target);
        }
        if (target.hasData(ModAttachments.POWER)) {
            Katon.cancel(target);
            PowerLogic.interrupt(target);
            target.getData(ModAttachments.POWER).chargeReportedAt = -10;
        } else {
            SkillStop.stop(target);
        }
        Defense.stop(target);
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

    // Same reach as the normal Expansão (its radius in gojo.dominio, awakened or not).
    public static double radius(LivingEntity caster) {
        boolean awakened = caster.hasData(ModAttachments.POWER) && caster.getData(ModAttachments.POWER).awakened;
        return DominioSpec.get(awakened).radius();
    }

    // Everyone within reach but players in creative (they see it, but take none of it).
    private static List<LivingEntity> targets(ServerLevel level, LivingEntity caster) {
        return Combat.inRadius(level, caster.position().add(0, 1, 0), radius(caster), caster).stream()
                .filter(e -> !(e instanceof Player player && (player.isCreative() || player.isSpectator())))
                .toList();
    }

    private static void sound(ServerLevel level, LivingEntity caster, SoundEvent sound, float volume, float pitch) {
        SkillStop.sound(level, caster, caster.position().add(0, 1, 0), sound, volume, pitch);
    }
}
