// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.power.powers.sub.jj;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.ModEffects;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Destructible;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Stamina;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.PowerLogic;
import com.starkinhon.battlegroundsmod.power.powers.DomainEscape;
import com.starkinhon.battlegroundsmod.power.powers.DomainSpace;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;
import com.starkinhon.battlegroundsmod.skill.DominioSpec;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// Itadori's Shift+V: Expansão de Domínio (chapter 264, it has no name), ported from the preview Stark approved
// (forja/itadori-kit/shinjuku.js, SCENES.dominio, 2026-11-27 "ficou perfeito"), with his rules:
// - the sign: both hands mirrored in front of his chin (SIGN ticks), then a black dome closes round him out to the raio
//   of 45 (WAVE ticks): everyone it reaches is caught, their move cut off, held.
// - the escape is the Santuário's, to the tick (Stark: "pra ele n ter vantagem"): those resisting with stamina run free
//   while it waits (Gojo's window and drain, DominioSpec), past the raio they got away, at zero they are held; the bots
//   and bosses run the same way (DomainEscape).
// - then everyone goes into its own space above the world (DominioSpec.arena) and the station shows from the inside out
//   (client/fx/ItadoriDomainScene): the platform, its pillars and roof, the trains on both sides, all round it black.
//   Inside FREE_TICKS (30 s, Gojo's void). The sure hit is the Desmantelar on the soul: every SOUL_CUT_EVERY ticks a cut
//   falls straight on the soul of each one caught, and every blow of his lands on the soul (it is pushed out of the body
//   and springs back); the SOUL_BLOWS-th tears it out: down, no power for a while.
// - Duelo de Expansões: his is far weaker. If Gojo or Sukuna opens theirs on him (or he opens his on them) his breaks
//   at once and he is left held for theirs (giveWay, called from InfiniteVoid.use and ShrineDomain.use). With the three
//   at once his goes first and the duel between Gojo and Sukuna goes on as always.
// - when it closes everyone is put back where they were in the world, and his technique is burnt out.
public final class ItadoriDomain {
    public static final double RADIUS = 45;
    public static final int SIGN = 18;
    // The dome closes slower (Stark 2026-11-28: time to run out of it).
    public static final int WAVE = 40;
    // From the open: the station reveals (client), he lowers his hands and everyone moves.
    public static final int REVEAL = 30;
    public static final int PLAY = 20;
    public static final int FREE_TICKS = 600;
    public static final int CLOSE_TICKS = 24;
    // The sure hit.
    public static final int SOUL_CUT_EVERY = 100;
    private static final float SOUL_CUT_DAMAGE = 6;
    private static final int SOUL_CUT_STUN = 25;
    public static final int SOUL_BLOWS = 4;
    private static final float SOUL_BLOW_DAMAGE = 3;
    private static final int BLOW_GAP = 6;
    private static final int SOUL_OUT_SEAL = 60;
    private static final int SOUL_OUT_STUN = 40;
    // The platform: half its width (the floor is there at the arena's height) and how much lower the tracks lie.
    public static final double PLATFORM_HALF = 5;
    public static final double TRACK_DROP = 1.25;
    private static final double RESIST_DRAIN = 8;
    private static final double ESCAPE_SLACK = 1.0;
    private static final int MIN_OPEN = com.starkinhon.battlegroundsmod.power.powers.duel.DomainDuel.WINDOW + 4;
    private static final int BURNOUT_TICKS = 200;
    private static final int BROKEN_HOLD = 30;
    private static final double VIEW_DISTANCE = 192;
    private static final int HOLD_STEP = 10;
    private static final double FLOOR_SLACK = 1.5;
    private static final String BACK_TAG = "battlegroundsmod_itadori_domain_back";
    public static final String SKILL = "jj.dominio";
    // Payload modes (Jj2Net.ITA_DOMAIN, dir.y).
    public static final int M_SIGN = 0, M_OPEN = 1, M_CLOSE = 2, M_GONE = 3, M_BREAK = 4, M_FAIL = 5;

    private static int escapeTicks() {
        return Math.max(MIN_OPEN, DominioSpec.get(false).tunnelTick()) - SIGN - WAVE;
    }

    private static double escapeDrain() {
        return DominioSpec.get(false).acerto().drain();
    }

    private static final class Active {
        final LivingEntity caster;
        final ServerLevel level;
        final Vec3 center;
        final Vec3 arena;
        final float yaw;
        final Set<LivingEntity> caught = new HashSet<>();
        final Set<LivingEntity> free = new HashSet<>();
        final Map<LivingEntity, Vec3> back = new LinkedHashMap<>();
        final Set<ServerPlayer> granted = new HashSet<>();
        final Map<LivingEntity, Runnable> holds = new HashMap<>();
        final Map<LivingEntity, Integer> blows = new HashMap<>();
        // When each one last took a blow on the soul (a long technique hitting every tick counts once per BLOW_GAP).
        final Map<LivingEntity, Integer> lastBlow = new HashMap<>();
        boolean decided;
        boolean inside;
        boolean cancelled;
        boolean closing;
        int age;
        int waited = -1;
        int openAt = -1;
        int closeAt = -1;
        int deadAt = -1;

        Active(LivingEntity caster, ServerLevel level, Vec3 center) {
            this.caster = caster;
            this.level = level;
            this.center = center;
            this.arena = DominioSpec.arena(level, center);
            this.yaw = caster.getYRot();
        }

        // Ticks since everyone could move inside, -1 before.
        int play() {
            return openAt < 0 ? -1 : age - openAt - PLAY;
        }
    }

    private static final List<Active> ACTIVE = new ArrayList<>();

    private ItadoriDomain() {
    }

    public static double waveRadius(double age) {
        double k = Mth.clamp((age - SIGN) / (double) WAVE, 0, 1);
        // eased in and out: it slows down near its edge, so those near it can still get out
        return RADIUS * (0.5 - 0.5 * Math.cos(Math.PI * k));
    }

    public static boolean use(LivingEntity caster, ServerLevel level) {
        for (Active a : ACTIVE) {
            if (a.caster == caster) {
                return false;
            }
        }
        if (underStronger(caster)) {
            // Opened on Gojo's or Sukuna's (in it, or caught by it): his breaks in his hands.
            SubNet.send(level, Jj2Net.ITA_DOMAIN, caster.getId(), caster.position(), new Vec3(caster.getYRot(), M_FAIL, 0), 0, 0, 0);
            Stun.apply(caster, BROKEN_HOLD);
            SubCommon.sound(level, caster.position(), SoundEvents.GLASS_BREAK, 1.2f, 0.6f);
            SubCommon.warn(caster, "itadori.dominio_quebra");
            return true;
        }
        Active active = new Active(caster, level, caster.position());
        ACTIVE.add(active);
        int still = SIGN + WAVE;
        caster.getData(ModAttachments.POWER).busyTicks = still;
        holdCasterFor(active, still);
        SubCommon.pose(caster, SKILL);
        send(active, M_SIGN, 0);
        level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.4f, 0.7f);
        run(active);
        return true;
    }

    private static void holdCasterFor(Active active, int ticks) {
        LivingEntity caster = active.caster;
        Freeze.holdLookFree(caster, ticks);
        caster.addEffect(new MobEffectInstance(ModEffects.ENRAIZADO, ticks, 0, false, false, false));
    }

    // Already in (or caught by) Gojo's void or Sukuna's Santuário, or in their duel.
    private static boolean underStronger(LivingEntity caster) {
        return com.starkinhon.battlegroundsmod.power.powers.gojo.InfiniteVoid.insideAny(caster)
                || com.starkinhon.battlegroundsmod.power.powers.gojo.InfiniteVoid.blocks(caster)
                || com.starkinhon.battlegroundsmod.power.powers.sukuna.ShrineDomain.insideAny(caster)
                || com.starkinhon.battlegroundsmod.power.powers.sukuna.ShrineDomain.blocks(caster)
                || com.starkinhon.battlegroundsmod.power.powers.sukuna.ShrineDomain.around(caster)
                || com.starkinhon.battlegroundsmod.power.powers.duel.DomainDuel.fighting(caster);
    }

    private static void run(Active active) {
        LivingEntity caster = active.caster;
        ServerLevel level = active.level;
        Tasks.add(raw -> {
            if (!ACTIVE.contains(active)) {
                return true;
            }
            int age = raw;
            active.age = age;
            boolean gone = !caster.isAlive() || caster.isRemoved() || caster.level() != level;
            boolean allDead = active.deadAt >= 0 && age >= active.deadAt + 40;
            boolean over = active.openAt >= 0 && age >= active.openAt + PLAY + FREE_TICKS;
            if (!active.closing && (gone || active.cancelled || allDead || over)) {
                close(active);
            }
            if (active.closing) {
                if (age >= active.closeAt + CLOSE_TICKS) {
                    finish(active, true);
                    return true;
                }
                if (active.inside) {
                    floor(active);
                }
                return false;
            }
            if (age < SIGN) {
                keepHeld(active);
                return false;
            }
            if (age < SIGN + WAVE) {
                wave(active, age);
            } else if (!active.decided && !escape(active)) {
                if (caster instanceof Player || active.caught.stream().anyMatch(LivingEntity::isAlive)) {
                    open(active);
                } else {
                    // A bot whose every target ran: no empty station standing in the world.
                    close(active);
                    return false;
                }
            }
            if (active.decided) {
                inside(active);
            }
            return false;
        });
    }

    // The dome closing: catches everyone it reaches.
    private static void wave(Active active, int age) {
        double front = waveRadius(age + 1);
        Destructible.holdAround(active.level, active.center, front, SIGN + WAVE - age + 4, active.caster);
        for (LivingEntity victim : Combat.inRadius(active.level, active.center, front, active.caster)) {
            catchOne(active, victim);
        }
        keepHeld(active);
    }

    private static void keepHeld(Active active) {
        for (LivingEntity v : active.caught) {
            if (v.isAlive() && !v.isRemoved() && (active.decided || !Stamina.resists(v)) && (active.age % HOLD_STEP == 0 || !Freeze.isFrozen(v))) {
                hold(active, v);
            }
        }
    }

    private static void catchOne(Active active, LivingEntity victim) {
        if (victim == active.caster) {
            return;
        }
        if (spared(victim)) {
            if (active.free.add(victim) && active.inside) {
                takeInside(active, victim);
            }
            return;
        }
        if (!active.caught.add(victim)) {
            return;
        }
        if (victim.hasData(ModAttachments.POWER)) {
            PowerLogic.interrupt(victim);
        }
        DomainEscape.caught(victim, active, active.center, RADIUS, () -> ACTIVE.contains(active) && !active.closing);
        if (Stamina.resists(victim) && victim instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("message.battlegroundsmod.itadori.dominio_resiste").withStyle(st -> st.withColor(0x8FB8FF)), true);
        }
        if (active.decided || !Stamina.resists(victim)) {
            hold(active, victim);
        }
        if (active.inside) {
            takeInside(active, victim);
        }
    }

    private static void hold(Active active, LivingEntity victim) {
        Runnable old = active.holds.put(victim, Freeze.holdLoose(victim, HOLD_STEP + 1, spot -> within(active, spot)));
        if (old != null) {
            old.run();
        }
    }

    private static void release(Active active) {
        for (Runnable r : active.holds.values()) {
            r.run();
        }
        active.holds.clear();
    }

    // The Santuário's escape, the same numbers: true while it still waits on someone resisting with stamina.
    private static boolean escape(Active active) {
        LivingEntity caster = active.caster;
        boolean early = !active.caught.isEmpty() && SIGN + WAVE + Math.max(0, active.waited) < MIN_OPEN;
        if (active.waited < 0) {
            if (!early && active.caught.stream().noneMatch(v -> v.isAlive() && Stamina.resists(v))) {
                return false;
            }
            active.waited = 0;
            // He keeps his sign while it waits.
            int wait = escapeTicks() + 2;
            caster.getData(ModAttachments.POWER).busyTicks = wait;
            holdCasterFor(active, wait);
        }
        if (active.waited >= escapeTicks()) {
            return false;
        }
        active.waited++;
        for (LivingEntity victim : Combat.inRadius(active.level, active.center, RADIUS, caster)) {
            catchOne(active, victim);
        }
        double far = (RADIUS + ESCAPE_SLACK) * (RADIUS + ESCAPE_SLACK);
        boolean waiting = false;
        for (LivingEntity v : List.copyOf(active.caught)) {
            if (!v.isAlive() || v.isRemoved()) {
                continue;
            }
            if (!Stamina.resists(v)) {
                if (active.waited % HOLD_STEP == 0 || !Freeze.isFrozen(v)) {
                    hold(active, v);
                }
                continue;
            }
            Runnable held = active.holds.remove(v);
            if (held != null) {
                held.run();
            }
            if (v.level() != active.level || Combat.center(v).distanceToSqr(active.center) > far) {
                active.caught.remove(v);
                if (v instanceof ServerPlayer player) {
                    player.displayClientMessage(Component.translatable("message.battlegroundsmod.domain_escaped").withStyle(st -> st.withColor(0x8FB8FF)), true);
                }
                continue;
            }
            Stamina.drain(v, escapeDrain() / 20.0);
            if (Stamina.resists(v)) {
                waiting = true;
            } else {
                hold(active, v);
            }
        }
        return waiting || early;
    }

    // It opens: everyone into its space, the station shows.
    private static void open(Active active) {
        active.decided = true;
        active.openAt = active.age;
        Destructible.eraseAlong(active.level, active.center, active.center, RADIUS, active.caster);
        LivingEntity caster = active.caster;
        caster.getData(ModAttachments.POWER).busyTicks = PLAY;
        holdCasterFor(active, PLAY);
        goInside(active);
        send(active, M_OPEN, 0);
        active.level.playSound(null, active.arena.x, active.arena.y, active.arena.z, SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.2f, 0.6f);
    }

    private static void inside(Active active) {
        if (active.inside) {
            floor(active);
            DomainSpace.wall(active.level, active.center, RADIUS, active.caster);
        }
        int p = active.play();
        if (p < 0) {
            keepHeld(active);
            return;
        }
        LivingEntity caster = active.caster;
        if (p == 0) {
            release(active);
            Freeze.free(caster);
            caster.removeEffect(ModEffects.ENRAIZADO);
            caster.getData(ModAttachments.POWER).busyTicks = 0;
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), "", false));
        }
        boolean cut = p > 0 && p % SOUL_CUT_EVERY == 0;
        for (LivingEntity v : active.caught) {
            if (!v.isAlive() || v.isRemoved() || v.level() != active.level) {
                continue;
            }
            boolean resisting = Stamina.resists(v);
            if (resisting) {
                Stamina.drain(v, RESIST_DRAIN / 20.0);
            }
            if (cut) {
                soulCut(active, v, resisting);
            }
        }
        if (active.deadAt < 0 && !active.caught.isEmpty() && active.caught.stream().noneMatch(LivingEntity::isAlive)) {
            active.deadAt = active.age;
        }
    }

    // The sure hit: the Desmantelar falls straight on the soul (no knockback: it is the soul it cuts).
    private static void soulCut(Active active, LivingEntity v, boolean resisting) {
        LivingEntity caster = active.caster;
        Vec3 c = Combat.center(v);
        Vec3 motion = v.getDeltaMovement();
        Itadori.soulDamage(caster, v, SOUL_CUT_DAMAGE);
        v.setDeltaMovement(motion);
        v.hurtMarked = true;
        if (!resisting) {
            Stun.apply(v, SOUL_CUT_STUN);
            if (com.starkinhon.battlegroundsmod.combat.Charge.charging(v)) {
                PowerLogic.interrupt(v);
            }
        }
        Vec3 dir = SubCommon.flat(v.position().subtract(caster.position()));
        // Both cuts of the X at once, on the chest (the Corte de Tesoura's own, a = 2).
        SubNet.send(active.level, Jj2Net.ITA_CUT, 0, c, caster.position().subtract(c), 2, 0, 0);
        SubNet.send(active.level, Jj2Net.ITA_RIP, v.getId(), v.position(), dir, 1, 0, 0);
        SubCommon.sound(active.level, c, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 0.7f);
        SubCommon.sound(active.level, c, SoundEvents.SOUL_ESCAPE.value(), 1.4f, 0.9f);
    }

    // A blow of his on someone caught, inside: it lands on the soul. Called from Itadori.onHit.
    static void blow(LivingEntity caster, ServerLevel level, LivingEntity target) {
        Active active = ownOpen(caster);
        if (active == null || active.play() < 0 || !active.caught.contains(target) || !target.isAlive()) {
            return;
        }
        Integer last = active.lastBlow.get(target);
        if (last != null && active.age - last < BLOW_GAP) {
            return;
        }
        active.lastBlow.put(target, active.age);
        int n = active.blows.merge(target, 1, Integer::sum);
        Vec3 dir = SubCommon.flat(target.position().subtract(caster.position()));
        Vec3 c = Combat.center(target);
        Itadori.soulDamage(caster, target, SOUL_BLOW_DAMAGE);
        if (n < SOUL_BLOWS) {
            SubNet.send(level, Jj2Net.ITA_RIP, target.getId(), target.position(), dir, 1, 0, 0);
            SubCommon.sound(level, c, SoundEvents.SOUL_ESCAPE.value(), 1.6f, 0.7f);
            return;
        }
        // The last one: the soul comes out of the body and it drops.
        active.blows.remove(target);
        SubNet.send(level, Jj2Net.ITA_RIP, target.getId(), target.position(), dir, 2, 0, 0);
        int ticks = JjCommon.control(caster, target, SOUL_OUT_SEAL);
        PowerLogic.seal(target, ticks);
        Stun.apply(target, SOUL_OUT_STUN);
        if (target.hasData(ModAttachments.POWER)) {
            PowerLogic.interrupt(target);
        }
        Combat.push(target, dir, 0.5, 0.15);
        SubCommon.sound(level, c, SoundEvents.PLAYER_ATTACK_CRIT, 1.4f, 0.5f);
        SubCommon.sound(level, c, SoundEvents.SOUL_ESCAPE.value(), 2f, 0.6f);
        SubCommon.sound(level, c, SoundEvents.WARDEN_HEARTBEAT, 1.6f, 0.9f);
        if (caster instanceof Player player) {
            SubCommon.warn(player, "itadori.alma", Math.round(ticks / 20f));
        }
    }

    private static void goInside(Active active) {
        active.inside = true;
        LivingEntity caster = active.caster;
        if (caster.isAlive()) {
            takeInside(active, caster);
        }
        for (LivingEntity v : List.copyOf(active.caught)) {
            if (v.isAlive() && !v.isRemoved() && v.level() == active.level) {
                takeInside(active, v);
            }
        }
        for (LivingEntity v : List.copyOf(active.free)) {
            if (v.isAlive() && !v.isRemoved() && v.level() == active.level) {
                takeInside(active, v);
            }
        }
    }

    private static void takeInside(Active active, LivingEntity entity) {
        if (active.back.containsKey(entity)) {
            return;
        }
        Vec3 from = entity.position();
        active.back.put(entity, from);
        if (entity instanceof ServerPlayer player) {
            CompoundTag tag = new CompoundTag();
            tag.putDouble("x", from.x);
            tag.putDouble("y", from.y);
            tag.putDouble("z", from.z);
            player.getPersistentData().put(BACK_TAG, tag);
            if (!player.getAbilities().mayfly) {
                player.getAbilities().mayfly = true;
                player.onUpdateAbilities();
                active.granted.add(player);
            }
        }
        Vec3 rel = new Vec3(from.x - active.center.x, 0, from.z - active.center.z);
        if (rel.length() > RADIUS - 2) {
            rel = rel.normalize().scale(RADIUS - 2);
        }
        Vec3 spot = new Vec3(active.center.x + rel.x, 0, active.center.z + rel.z);
        put(entity, new Vec3(spot.x, floorAt(active, spot.x, spot.z), spot.z));
    }

    // The floor's height at (x, z): the platform along his facing, the tracks lower on both sides.
    private static double floorAt(Active active, double x, double z) {
        return floorAt(active.arena, active.yaw, x, z);
    }

    public static double floorAt(Vec3 arena, float yaw, double x, double z) {
        double r = yaw * Mth.DEG_TO_RAD;
        double side = (x - arena.x) * Math.cos(r) + (z - arena.z) * Math.sin(r);
        return Math.abs(side) < PLATFORM_HALF ? arena.y : arena.y - TRACK_DROP;
    }

    private static Vec3 within(Active active, Vec3 spot) {
        double dx = spot.x - active.center.x;
        double dz = spot.z - active.center.z;
        double r = Math.sqrt(dx * dx + dz * dz);
        double max = RADIUS - 1.5;
        if (r <= max) {
            return spot;
        }
        return new Vec3(active.center.x + dx * max / r, spot.y, active.center.z + dz * max / r);
    }

    // The floor holds everyone up (the platform and, lower, the tracks), the barrier keeps them within its raio.
    private static void floor(Active active) {
        for (LivingEntity entity : active.back.keySet()) {
            if (!entity.isAlive() || entity.isRemoved() || entity.level() != active.level) {
                continue;
            }
            entity.resetFallDistance();
            double y = floorAt(active, entity.getX(), entity.getZ());
            double dx = entity.getX() - active.center.x;
            double dz = entity.getZ() - active.center.z;
            double r = Math.sqrt(dx * dx + dz * dz);
            boolean out = r > RADIUS - 0.5;
            if (entity instanceof ServerPlayer player) {
                if (player.getAbilities().flying && active.granted.contains(player)) {
                    player.getAbilities().flying = false;
                    player.onUpdateAbilities();
                }
                if (player.getY() < y - FLOOR_SLACK || out) {
                    double k = out ? (RADIUS - 1.5) / r : 1;
                    double nx = active.center.x + dx * k, nz = active.center.z + dz * k;
                    put(player, new Vec3(nx, Math.max(player.getY(), floorAt(active, nx, nz)), nz));
                }
                continue;
            }
            if (Freeze.isFrozen(entity)) {
                continue;
            }
            if (out) {
                double k = (RADIUS - 1.5) / r;
                entity.setPos(active.center.x + dx * k, entity.getY(), active.center.z + dz * k);
                y = floorAt(active, entity.getX(), entity.getZ());
            }
            DomainSpace.steer(entity, y);
            if (entity.getY() < y) {
                entity.setPos(entity.getX(), y, entity.getZ());
            }
            if (entity.getY() <= y + 1.0E-3) {
                Vec3 v = entity.getDeltaMovement();
                entity.setDeltaMovement(v.x, Math.max(0, v.y), v.z);
                entity.setOnGround(true);
            }
        }
    }

    private static void close(Active active) {
        active.closing = true;
        active.closeAt = active.age;
        release(active);
        LivingEntity caster = active.caster;
        if (caster.isAlive()) {
            caster.getData(ModAttachments.POWER).busyTicks = 0;
            Freeze.free(caster);
            caster.removeEffect(ModEffects.ENRAIZADO);
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), "", false));
        }
        com.starkinhon.battlegroundsmod.combat.SkillStop.stop(caster);
        send(active, M_CLOSE, 0);
    }

    // Gone: everyone back in the world, his technique burnt out.
    private static void finish(Active active, boolean burn) {
        comeBack(active);
        if (burn) {
            burnout(active);
        }
        send(active, M_GONE, 0);
        ACTIVE.remove(active);
    }

    private static void comeBack(Active active) {
        if (!active.inside) {
            return;
        }
        active.inside = false;
        for (Map.Entry<LivingEntity, Vec3> entry : active.back.entrySet()) {
            LivingEntity entity = entry.getKey();
            if (entity instanceof ServerPlayer player) {
                player.getPersistentData().remove(BACK_TAG);
                if (active.granted.contains(player)) {
                    player.getAbilities().mayfly = player.isCreative() || player.isSpectator();
                    player.getAbilities().flying = false;
                    player.onUpdateAbilities();
                }
            }
            if (entity.isAlive() && !entity.isRemoved() && entity.level() == active.level) {
                entity.resetFallDistance();
                put(entity, entry.getValue());
            }
        }
        active.back.clear();
        active.granted.clear();
    }

    private static void burnout(Active active) {
        LivingEntity caster = active.caster;
        if (!active.decided || !caster.isAlive() || caster.isRemoved() || !caster.hasData(ModAttachments.POWER)) {
            return;
        }
        PowerLogic.seal(caster, BURNOUT_TICKS);
        if (caster instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("message.battlegroundsmod.technique_burnout", BURNOUT_TICKS / 20)
                    .withStyle(st -> st.withColor(0xFF5555)), true);
        }
    }

    private static void put(LivingEntity entity, Vec3 pos) {
        if (Freeze.isFrozen(entity)) {
            Freeze.place(entity, pos, entity.getYRot(), entity.getXRot());
        } else if (entity instanceof ServerPlayer player) {
            player.connection.teleport(pos.x, pos.y, pos.z, player.getYRot(), player.getXRot());
        } else {
            entity.teleportTo(pos.x, pos.y, pos.z);
        }
        entity.setDeltaMovement(Vec3.ZERO);
        entity.hurtMarked = true;
    }

    private static boolean spared(LivingEntity entity) {
        return (entity instanceof Player player && player.isCreative()) || JjCommon.inSimpleDomain(entity);
    }

    // Gojo and Sukuna can always answer his with theirs (and win): caught, they keep their powers.
    private static boolean stronger(LivingEntity entity) {
        if (!entity.hasData(ModAttachments.POWER)) {
            return false;
        }
        String power = entity.getData(ModAttachments.POWER).power;
        return "strongest".equals(power) || com.starkinhon.battlegroundsmod.power.powers.Sukuna.ID.equals(power);
    }

    // ---- the duel: his breaks ----

    // Gojo or Sukuna (opener) is opening theirs: if opener is in, or caught by, one of his, it breaks right now (everyone
    // back in the world first, so theirs opens from where they really stand) and he is left held for theirs. Call before
    // their domain reads where it opens.
    public static void giveWay(LivingEntity opener) {
        for (Active active : List.copyOf(ACTIVE)) {
            if (active.closing || active.caster == opener) {
                continue;
            }
            boolean in = active.caught.contains(opener) || active.free.contains(opener) || active.back.containsKey(opener)
                    || (!active.inside && opener.level() == active.level && opener.position().distanceTo(active.center) <= RADIUS);
            if (in) {
                shatter(active);
            }
        }
    }

    private static void shatter(Active active) {
        active.closing = true;
        release(active);
        LivingEntity caster = active.caster;
        send(active, M_BREAK, 0);
        comeBack(active);
        if (caster.isAlive()) {
            Freeze.free(caster);
            caster.removeEffect(ModEffects.ENRAIZADO);
            caster.getData(ModAttachments.POWER).busyTicks = 0;
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), "", false));
            Stun.apply(caster, BROKEN_HOLD);
            active.level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.6f, 0.5f);
        }
        burnout(active);
        com.starkinhon.battlegroundsmod.combat.SkillStop.stop(caster);
        ACTIVE.remove(active);
    }

    // ---- hooks ----

    // Shift+V again with it open: he closes it.
    public static boolean press(LivingEntity caster) {
        for (Active active : ACTIVE) {
            if (active.caster == caster && !active.closing && !active.cancelled) {
                active.cancelled = true;
                return true;
            }
        }
        return false;
    }

    // Caught before it opens: no skills (Gojo and Sukuna keep theirs, to answer with their domain).
    public static boolean blocks(LivingEntity entity) {
        for (Active active : ACTIVE) {
            if (active.caster != entity && active.caught.contains(entity) && !active.closing && (!active.decided || active.play() < 0)
                    && !stronger(entity)) {
                return true;
            }
        }
        return false;
    }

    // Through the sign and the dome nothing touches him, and nobody but him touches those caught.
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        LivingEntity hurt = event.getEntity();
        for (Active active : ACTIVE) {
            if (active.closing || active.age >= SIGN + WAVE) {
                continue;
            }
            if (active.caster == hurt) {
                event.setCanceled(true);
                return;
            }
            if (active.caught.contains(hurt) && event.getSource().getEntity() != active.caster) {
                event.setCanceled(true);
                return;
            }
        }
    }

    public static boolean apart(LivingEntity a, LivingEntity b) {
        return insideOf(a) != insideOf(b);
    }

    public static boolean insideAny(LivingEntity entity) {
        return insideOf(entity) != null;
    }

    // Up in the station of his own domain, open.
    private static Active ownOpen(LivingEntity caster) {
        for (Active active : ACTIVE) {
            if (active.caster == caster && active.inside && !active.closing) {
                return active;
            }
        }
        return null;
    }

    public static boolean release(LivingEntity entity, boolean putBack) {
        boolean any = false;
        for (Active active : ACTIVE) {
            if (active.caster == entity && !active.closing) {
                active.cancelled = true;
                any = true;
            }
        }
        for (Active active : ACTIVE) {
            boolean caught = active.caught.remove(entity) | active.free.remove(entity);
            Runnable hold = active.holds.remove(entity);
            if (hold != null) {
                hold.run();
            }
            Vec3 from = active.back.remove(entity);
            if (from == null && !caught) {
                continue;
            }
            any = true;
            if (entity instanceof ServerPlayer player) {
                player.getPersistentData().remove(BACK_TAG);
                if (active.granted.remove(player)) {
                    player.getAbilities().mayfly = player.isCreative() || player.isSpectator();
                    player.getAbilities().flying = false;
                    player.onUpdateAbilities();
                }
                if (player != active.caster) {
                    SubNet.sendTo(player, Jj2Net.ITA_DOMAIN, active.caster.getId(), active.center, new Vec3(active.yaw, M_GONE, 0), 0, 0, 0);
                }
            }
            if (from != null && putBack && entity.isAlive() && !entity.isRemoved() && entity.level() == active.level) {
                entity.resetFallDistance();
                put(entity, from);
            }
        }
        return any;
    }

    public static double floorY(LivingEntity entity) {
        Active active = insideOf(entity);
        return active != null ? floorAt(active, entity.getX(), entity.getZ()) : Double.NaN;
    }

    public static boolean keepsInside(LivingEntity entity, Vec3 spot) {
        Active active = insideOf(entity);
        if (active == null) {
            return true;
        }
        double dx = spot.x - active.center.x;
        double dz = spot.z - active.center.z;
        double r = RADIUS - 1.5;
        return dx * dx + dz * dz <= r * r;
    }

    private static Active insideOf(LivingEntity entity) {
        for (Active active : ACTIVE) {
            if (active.inside && active.back.containsKey(entity)) {
                return active;
            }
        }
        return null;
    }

    public static void onLogin(ServerPlayer player) {
        if (!player.getPersistentData().contains(BACK_TAG)) {
            return;
        }
        CompoundTag tag = player.getPersistentData().getCompound(BACK_TAG);
        player.getPersistentData().remove(BACK_TAG);
        player.getAbilities().mayfly = player.isCreative() || player.isSpectator();
        player.getAbilities().flying = player.isSpectator();
        player.onUpdateAbilities();
        player.resetFallDistance();
        player.connection.teleport(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"), player.getYRot(), player.getXRot());
    }

    public static void clear() {
        ACTIVE.clear();
    }

    // To everyone near where it opened or near its space, and always to those in it (dir: yaw, mode, part).
    private static void send(Active active, int mode, float a) {
        Vec3 center = active.center;
        for (ServerPlayer player : active.level.players()) {
            boolean part = player == active.caster || active.caught.contains(player) || active.free.contains(player);
            boolean near = player.position().distanceToSqr(center) <= VIEW_DISTANCE * VIEW_DISTANCE
                    || player.position().distanceToSqr(active.arena) <= VIEW_DISTANCE * VIEW_DISTANCE;
            if (!near && !part) {
                continue;
            }
            SubNet.sendTo(player, Jj2Net.ITA_DOMAIN, active.caster.getId(), center, new Vec3(active.yaw, mode, part ? 1 : 0), a, active.age, 0);
        }
    }
}
