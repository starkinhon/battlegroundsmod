// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.power.powers.makima;

import com.starkinhon.battlegroundsmod.ModEntities;
import com.starkinhon.battlegroundsmod.boss.SoulBoss;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.CombatMode;
import com.starkinhon.battlegroundsmod.combat.Stamina;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// What Makima's kit needs from the game: the passive Primeiro-Ministro (every blow on her paid by her puppets or stamina), keeping the mobs and souls she kills, her
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
        // Primeiro-Ministro (Stark 2026-11-26, as in the manga): every blow aimed at her lands, the body takes it and
        // bleeds, then it mends in about 1 s and the price goes to someone else. Her live puppets pay first (each one loses
        // that much life and bursts when it runs out), then her own stamina (a player; once it runs dry, 60 s before it pays again); with neither, what's left of the
        // blow stays with her. That's how she is beaten: kill the puppets and wear her down until she's exhausted.
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY) || !(victim.level() instanceof ServerLevel level)) {
            return;
        }
        float damage = event.getNewDamage();
        if (damage <= 0) {
            return;
        }
        float paid = pay(level, victim, damage);
        if (paid <= 0) {
            return;
        }
        float kept = damage - paid;
        float health = victim.getHealth();
        if (kept >= health) {
            // even after the contract the blow left over kills her
            return;
        }
        // She feels the whole blow (never more than her life), and the share that was paid comes back over MEND_TICKS.
        float felt = Math.min(damage, health - 0.5f);
        event.setNewDamage(felt);
        mend(victim, felt - kept);
        Makima.eyes(victim, MEND_TICKS + 20);
        MkNet.send(level, MkNet.MEND, victim.getId(), victim.position(), MEND_TICKS, 0, 0);
        SubCommon.sound(level, victim.position(), SoundEvents.CHAIN_HIT, 0.5f, 0.6f);
    }

    // How long a paid blow takes to close (Stark: "se recupera rápido, uns 1 s").
    static final int MEND_TICKS = 20;
    // Stamina for the blows: a full bar pays for this many of her max lives in damage (Stark 2026-11-27: "gastar um
    // pouco mais", was 1.0).
    private static final double STAMINA_LIVES = 0.75;
    // The contract never takes her stamina under the bar's red (StaminaSpec barra.baixaAbaixoDePct, 25%): it stops near
    // exhaustion, never in it (Stark 2026-11-27: "quase vulnerável", then it's the player's skill).
    private static double floor() {
        var bar = com.starkinhon.battlegroundsmod.skill.StaminaSpec.get().barra();
        return bar == null ? 0.25 : bar.baixaAbaixoDePct() / 100.0;
    }

    // Whether her contract pays with stamina now (not resting after it hit the red). Her hits don't give stamina back
    // while it's on (combat/Stamina.gain).
    public static boolean contractOn(LivingEntity makima) {
        return !STAMINA_READY.containsKey(makima.getUUID());
    }
    // Once her stamina runs dry paying for blows, it can't pay again for this long (her puppets always can). Stark
    // 2026-11-26 (after the test): the bar pays blow after blow until it's empty, then the 60 s start; never per blow.
    private static final int STAMINA_COOLDOWN = 1200;
    // When her stamina can pay again, per Makima (a player) whose stamina ran dry on the contract.
    private static final java.util.Map<java.util.UUID, Long> STAMINA_READY = new java.util.HashMap<>();
    // The life still to come back, per Makima: {amount left, ticks left}.
    private static final java.util.Map<LivingEntity, float[]> MENDING = new java.util.WeakHashMap<>();

    private static void mend(LivingEntity makima, float amount) {
        if (amount <= 0) {
            return;
        }
        float[] m = MENDING.computeIfAbsent(makima, k -> new float[2]);
        m[0] += amount;
        m[1] = MEND_TICKS;
    }

    private static void tickMending() {
        var it = MENDING.entrySet().iterator();
        while (it.hasNext()) {
            var en = it.next();
            LivingEntity e = en.getKey();
            float[] m = en.getValue();
            if (!e.isAlive() || e.isRemoved() || m[1] <= 0 || m[0] <= 0) {
                it.remove();
                continue;
            }
            float step = m[0] / m[1];
            // the contract, not a heal: anti-heal effects don't stop it, and it never goes over her max life
            e.setHealth(Math.min(e.getMaxHealth(), e.getHealth() + step));
            m[0] -= step;
            m[1]--;
        }
    }

    // Pays up to damage for the blow on makima, returns how much was paid: her live puppets within 200 blocks first
    // (the nearest one first), then her stamina (players only; bosses don't have it). The puppet that runs out bursts.
    private static float pay(ServerLevel level, LivingEntity makima, float damage) {
        if (!contractOn(makima)) {
            // Resting after her stamina hit the red: the whole contract is off, puppets too (Stark 2026-11-27), so she's
            // really open for those 60 s.
            return 0;
        }
        float left = damage;
        for (LivingEntity p : Puppets.of(makima, -1)) {
            if (left <= 0) {
                break;
            }
            if (!p.isAlive() || p.distanceTo(makima) > PM_RANGE) {
                continue;
            }
            float life = p.getHealth();
            float take = Math.min(left, life);
            left -= take;
            notice(makima, take >= life ? "pm_puppet_dead" : "pm_puppet");
            MkNet.send(level, MkNet.PULSE, p.getId(), p.position(), makima.getId(), 0, 0);
            Vec3 c = Combat.center(p);
            if (take >= life) {
                burst(level, makima, p);
            } else {
                p.setHealth(life - take);
                MkNet.send(level, MkNet.MIST, p.getId(), c, new Vec3(0, 0.2, 0), 10, 0, 0);
                SubCommon.sound(level, c, SoundEvents.SLIME_SQUISH_SMALL, 0.7f, 0.7f);
            }
        }
        long now = level.getGameTime();
        if (left > 0 && makima instanceof ServerPlayer player && CombatMode.fighter(player) && !Stamina.exhausted(player)
                && now >= STAMINA_READY.getOrDefault(player.getUUID(), 0L)) {
            // a full bar pays STAMINA_LIVES of her max lives, blow after blow, down to the red (never exhausted by it);
            // what it can't pay stays with her. At the floor the stamina rests STAMINA_COOLDOWN before it pays again.
            double max = Stamina.max(player);
            double perLife = max * STAMINA_LIVES;
            double floor = max * floor();
            double spare = Stamina.get(player) - floor;
            double cover = Math.max(0, spare) / perLife * player.getMaxHealth();
            float take = (float) Math.min(left, cover);
            if (take > 0) {
                Stamina.drain(player, take / player.getMaxHealth() * STAMINA_LIVES * 100.0);
                left -= take;
            }
            if (Stamina.get(player) <= floor + 0.5) {
                STAMINA_READY.put(player.getUUID(), now + STAMINA_COOLDOWN);
                notice(player, "pm_rest", STAMINA_COOLDOWN / 20);
                Stamina.sync(player);
            } else if (take > 0) {
                notice(player, "pm_stamina", Math.round(Stamina.get(player) / max * 100));
                Stamina.sync(player);
            }
        }
        return damage - left;
    }

    // What the contract just did, on her action bar (a player).
    private static void notice(LivingEntity makima, String key, Object... args) {
        if (makima instanceof ServerPlayer player) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.battlegroundsmod.makima." + key, args)
                    .withStyle(st -> st.withColor(0xFFB020)), true);
        }
    }

    // The stamina's rest over: tells her once that it pays again.
    private static void tickStaminaRest(net.minecraft.server.MinecraftServer server) {
        var it = STAMINA_READY.entrySet().iterator();
        while (it.hasNext()) {
            var en = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(en.getKey());
            if (player == null) {
                it.remove();
            } else if (player.level().getGameTime() >= en.getValue()) {
                it.remove();
                if (Makima.is(player)) {
                    notice(player, "pm_ready");
                    SubCommon.sound((ServerLevel) player.level(), player.position(), SoundEvents.CHAIN_PLACE, 0.6f, 0.8f);
                }
            }
        }
    }

    // A puppet that paid with all its life: the blow runs down the chain and it bursts (no impact frame: with every blow
    // redirected now, a combo would strobe).
    private static void burst(ServerLevel level, LivingEntity makima, LivingEntity puppet) {
        Vec3 c = Combat.center(puppet);
        Puppets.forgetPuppet(puppet);
        MkNet.send(level, MkNet.BITS, puppet.getId(), c, Vec3.ZERO, 34, 0.9f, 1);
        MkNet.send(level, MkNet.MIST, puppet.getId(), c, new Vec3(0, 0.3, 0), 70, 0, 0);
        SubCommon.sound(level, c, SoundEvents.SLIME_SQUISH, 1.4f, 0.5f);
        puppet.kill();
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
        tickMending();
        tickStaminaRest(event.getServer());
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
        MENDING.clear();
        STAMINA_READY.clear();
        Ritual.clear();
        Ratos.clear();
        Corrente.clear();
    }
}
