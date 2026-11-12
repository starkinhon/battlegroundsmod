package com.starkinhon.battlegroundsmod.power.powers.makima;

import java.util.List;

import com.starkinhon.battlegroundsmod.ModEntities;
import com.starkinhon.battlegroundsmod.boss.SoulBoss;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// What Makima's kit needs from the game: the passive Primeiro-Ministro, keeping the mobs and souls she kills, her
// puppets never turning on her, and the names she learns by seeing (the Ritual's sheet).
public final class MakimaEvents {
    private static final double PM_RANGE = 200;

    private MakimaEvents() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(MakimaEvents::onDamage);
        NeoForge.EVENT_BUS.addListener(MakimaEvents::onIncoming);
        NeoForge.EVENT_BUS.addListener(MakimaEvents::onDeath);
        NeoForge.EVENT_BUS.addListener(MakimaEvents::onChangeTarget);
        NeoForge.EVENT_BUS.addListener(MakimaEvents::onTick);
        NeoForge.EVENT_BUS.addListener(MakimaEvents::onJoin);
        NeoForge.EVENT_BUS.addListener(MakimaEvents::onStopped);
    }

    // Her puppets and she never hurt each other.
    private static void onIncoming(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker) || attacker == victim) {
            return;
        }
        LivingEntity ownerA = Makima.is(attacker) ? attacker : Puppets.ownerOf(attacker);
        LivingEntity ownerV = Makima.is(victim) ? victim : Puppets.ownerOf(victim);
        if (ownerA != null && ownerA == ownerV) {
            event.setCanceled(true);
        }
    }

    private static void onDamage(LivingDamageEvent.Pre event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) {
            return;
        }
        Entity source = event.getSource().getEntity();
        if (source instanceof LivingEntity attacker && Makima.is(attacker)) {
            Puppets.foe(attacker, victim);
            Ritual.see(attacker, victim, "golpe");
        }
        if (!Makima.is(victim)) {
            return;
        }
        if (source instanceof LivingEntity attacker) {
            Puppets.foe(victim, attacker);
            Ritual.see(victim, attacker, "golpe");
        }
        // Primeiro-Ministro: a blow that would kill her goes to a mob within 200 blocks (her puppets first).
        if (event.getNewDamage() < victim.getHealth() || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || !(victim.level() instanceof ServerLevel level)) {
            return;
        }
        LivingEntity sub = substitute(level, victim);
        if (sub == null) {
            return;
        }
        event.setNewDamage(0);
        Vec3 c = Combat.center(sub);
        if (Puppets.isPuppet(sub)) {
            MkNet.send(level, MkNet.PULSE, sub.getId(), sub.position(), victim.getId(), 0, 0);
            Puppets.forgetPuppet(sub);
        }
        MkNet.send(level, MkNet.IMPACT, victim.getId(), c, 3, 1, 0);
        MkNet.send(level, MkNet.BITS, sub.getId(), c, Vec3.ZERO, 34, 0.9f, 1);
        MkNet.send(level, MkNet.MIST, sub.getId(), c, new Vec3(0, 0.3, 0), 70, 0, 0);
        SubCommon.sound(level, c, SoundEvents.SLIME_SQUISH, 1.4f, 0.5f);
        SubCommon.sound(level, victim.position(), SoundEvents.CHAIN_HIT, 1.0f, 0.6f);
        Makima.eyes(victim, 40);
        sub.kill();
    }

    private static LivingEntity substitute(ServerLevel level, LivingEntity makima) {
        List<LivingEntity> mine = Puppets.of(makima, -1);
        for (LivingEntity p : mine) {
            if (p.isAlive() && p.distanceTo(makima) <= PM_RANGE) {
                return p;
            }
        }
        if (makima instanceof SoulBoss) {
            // The boss: only her own puppets take the blow (Stark 2026-11-12: "o jogo é matar os fantoches primeiro"); a
            // cow in the next field doesn't make her immortal.
            return null;
        }
        LivingEntity best = null;
        double bd = PM_RANGE * PM_RANGE;
        AABB box = makima.getBoundingBox().inflate(PM_RANGE);
        for (Mob m : level.getEntitiesOfClass(Mob.class, box, m -> m.isAlive() && !(m instanceof SoulBoss boss && boss.isBoss()))) {
            double d = m.distanceToSqr(makima);
            if (d < bd) {
                bd = d;
                best = m;
            }
        }
        return best;
    }

    // How long after her last blow a death is still hers (a fall after the Bang, the fire, the void): 5 s.
    private static final int CREDIT_TICKS = 100;

    // What she kills is hers: a mob goes to her stock of puppets, a soul to her souls. Her own blow, the blow of one of
    // her puppets or souls (the "Morram." too), or a death that comes up to 5 s after one of those blows with nobody
    // else's hand in it.
    private static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide() || Puppets.isPuppet(dead)) {
            return;
        }
        LivingEntity killer = killerOf(dead, event.getSource().getEntity());
        if (killer == null) {
            return;
        }
        if (dead instanceof Player) {
            Almas.take(killer, dead);
            return;
        }
        if (dead instanceof SoulBoss boss && (boss.isBoss() || isSoulMob(boss))) {
            Almas.take(killer, dead);
            return;
        }
        if (dead instanceof Mob) {
            Fantoche.store(killer, dead);
        }
    }

    // The Makima a death belongs to, or null.
    private static LivingEntity killerOf(LivingEntity dead, Entity source) {
        if (source instanceof LivingEntity living) {
            LivingEntity owner = makimaOf(living);
            if (owner != null || living != dead) {
                // Someone else's hand (another player, a wolf) keeps the kill theirs.
                return owner;
            }
        }
        LivingEntity last = dead.getLastHurtByMob();
        if (last == null || dead.tickCount - dead.getLastHurtByMobTimestamp() > CREDIT_TICKS) {
            return null;
        }
        return makimaOf(last);
    }

    private static LivingEntity makimaOf(LivingEntity e) {
        if (Makima.is(e)) {
            return e;
        }
        LivingEntity owner = Puppets.ownerOf(e);
        return owner != null && owner.isAlive() && Makima.is(owner) ? owner : null;
    }

    private static boolean isSoulMob(SoulBoss boss) {
        for (var t : ModEntities.SUB_MOBS) {
            if (t.get() == boss.getType()) {
                return true;
            }
        }
        return false;
    }

    private static void onChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity owner = Puppets.ownerOf(event.getEntity());
        LivingEntity to = event.getNewAboutToBeSetTarget();
        if (owner != null && to != null && Makima.ours(owner, to)) {
            event.setCanceled(true);
        }
    }

    private static void onTick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            Puppets.tick(level);
        }
        if (event.getServer().getTickCount() % 20 != 0) {
            return;
        }
        // Whoever she sees with her own eyes, she knows by name.
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (!Makima.is(player) || !(player.level() instanceof ServerLevel level)) {
                continue;
            }
            Vec3 eye = player.getEyePosition();
            Vec3 look = player.getLookAngle();
            for (LivingEntity e : Combat.inRadius(level, player.position(), 40, player)) {
                Vec3 to = Combat.center(e).subtract(eye);
                if (to.normalize().dot(look) > 0.35 && Corrente.seen(level, eye, e)) {
                    Ritual.see(player, e, "olhos");
                }
            }
        }
    }

    // A puppet saved with the world comes back without her chain: it goes.
    private static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof LivingEntity e)) {
            return;
        }
        if (e.getPersistentData().getBoolean(Puppets.TAG) && !Puppets.isPuppet(e)) {
            event.setCanceled(true);
        }
    }

    private static void onStopped(ServerStoppedEvent event) {
        Puppets.clear();
        Ritual.clear();
        Ratos.clear();
        Corrente.clear();
    }
}
