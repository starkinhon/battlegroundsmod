package com.starkinhon.battlegroundsmod.power.powers.sukuna;

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
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.PowerLogic;
import com.starkinhon.battlegroundsmod.skill.DominioSpec;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// V (ult): Expansão de Domínio: Santuário Malevolente, ported from the 3D preview Stark approved (forja/sukuna-dominio,
// v35, 2026-11-09 05:36) with his game rules (05:36 and 05:39):
// - the press: Sukuna is rooted and untouchable, and the same shockwave as Gojo's runs out of him (WAVE ticks to the
//   raio of 40): every attack in flight it reaches stops in the air, and is swallowed when the wave is done. Everyone it
//   reaches is caught: their move is cut off and they are paralyzed; a player with stamina left is told they resist.
// - if it caught someone ("se a expansão pegar mesmo"), the cutscene plays (the approved cut, CUT_LEN ticks, see the
//   client's SukunaDomainScene): everyone caught stays truly paralyzed, nobody can touch Sukuna and nobody but him can
//   touch those caught. At CUT_INSIDE (the screen black under the water) everyone is taken into a space of its own above
//   the world (DominioSpec.arena, the same as Gojo's), the nearest one 20 blocks in front of him.
// - from CUT_PLAY (the camera back over his shoulder, second 13 of the cutscene) everyone moves freely until it closes,
//   FREE_TICKS later. Inside: the Desmantelar cuts all over cut everyone but Sukuna a little and stun them a little
//   (CUT_EVERY / CUT_STUN; a player still resisting with stamina is not stunned, and their stamina drains), and Clivar,
//   homing, never misses: it cuts everyone but him every CLIVAR_EVERY ticks until the end. Techniques are allowed inside.
// - V again closes it (as Gojo's). V twice (the second press within DOUBLE_TAP ticks) opens it fast: no cutscene, the
//   screen goes from the black aura and the six eyes (cut tick QUICK_FROM) straight to the domain cutting. If the wave
//   caught nobody it opens fast too.
// - when it closes everyone is put back where they were in the world, and Sukuna's technique is burnt out (as Gojo's).
public final class ShrineDomain {
    public static final String SKILL = "sukuna.dominio";
    public static final double RADIUS = 40;
    // The shockwave: from the press to the raio.
    public static final int WAVE = 16;
    public static final int DOUBLE_TAP = 8;
    // The cutscene's own clock (Stark's cut in the preview's editor), in ticks after the wave.
    public static final int CUT_INSIDE = 128;
    public static final int CUT_PLAY = 282;
    public static final int CUT_LEN = 428;
    // The fast version shows the cut from here (the black aura and the six eyes) to CUT_RED, then jumps to CUT_PLAY.
    public static final int QUICK_FROM = 124;
    public static final int CUT_RED = 154;
    // How long it lasts once everyone moves (Gojo's void: 30 s).
    public static final int FREE_TICKS = 600;
    public static final int CLOSE_TICKS = 12;
    // The sure hits (Tier 0 values, the attacker's tier scales them like every move, Combat.hurt).
    public static final int CUT_EVERY = 16;
    public static final int CUT_STUN = 8;
    private static final float CUT_DAMAGE = 3;
    // Clivar snaps CLIVAR_FIRST ticks after the play starts (the grid measures the body first), then every CLIVAR_EVERY.
    public static final int CLIVAR_FIRST = 1;
    public static final int CLIVAR_EVERY = 7;
    private static final float CLIVAR_DAMAGE = 4;
    // Stamina a resisting player loses per second inside (percent of the max).
    private static final double RESIST_DRAIN = 8;
    private static final int BURNOUT_TICKS = 200;
    private static final double VIEW_DISTANCE = 192;
    private static final int HOLD_STEP = 10;
    private static final double TARGET_GAP = 20;
    private static final double FLOOR_SLACK = 1.5;
    private static final String BACK_TAG = "battlegroundsmod_shrine_back";
    // Payload modes.
    public static final int M_WAVE = 0, M_CUTSCENE = 1, M_QUICK = 2, M_CLOSE = 3, M_GONE = 4;

    private static final class Active {
        final LivingEntity caster;
        final ServerLevel level;
        final Vec3 center;
        final Vec3 arena;
        final Set<LivingEntity> caught = new HashSet<>();
        final Set<LivingEntity> free = new HashSet<>();
        final Map<LivingEntity, Vec3> back = new LinkedHashMap<>();
        final Set<ServerPlayer> granted = new HashSet<>();
        final Map<LivingEntity, Runnable> holds = new HashMap<>();
        float forward;
        boolean quick;
        boolean decided;
        boolean inside;
        boolean cancelled;
        boolean closing;
        // Gave way to a Duelo de Expansões (duel/DomainDuel): gone without a trace, the duel goes on from here.
        boolean yielded;
        // Ticks already gone by when it started (the domain the duel's winner gets starts after the wave).
        int skip;
        int age;
        int closeAt = -1;
        int deadAt = -1;

        Active(LivingEntity caster, ServerLevel level, Vec3 center) {
            this.caster = caster;
            this.level = level;
            this.center = center;
            this.arena = DominioSpec.arena(level, center);
            this.forward = caster.getYRot();
        }

        double cut() {
            return ShrineDomain.cut(quick, age);
        }

        // The cutscene is playing: everyone caught held still, Sukuna untouchable.
        boolean scene() {
            return decided && !quick && age - WAVE < CUT_PLAY;
        }
    }

    private static final List<Active> ACTIVE = new ArrayList<>();

    private ShrineDomain() {
    }

    // The cut tick shown at age ticks after the press: -1 during the wave; the fast version runs QUICK_FROM..CUT_RED,
    // then jumps to CUT_PLAY. The client runs the same clock.
    public static double cut(boolean quick, double age) {
        if (age < WAVE) {
            return -1;
        }
        double e = age - WAVE;
        if (!quick) {
            return e;
        }
        double transition = CUT_RED - QUICK_FROM;
        return e < transition ? QUICK_FROM + e : CUT_PLAY + (e - transition);
    }

    // The shockwave's front, age ticks after the press.
    public static double waveRadius(double age) {
        double k = Mth.clamp(age / (WAVE - 2.0), 0, 1);
        return RADIUS * (1 - Math.pow(1 - k, 3));
    }

    public static boolean use(LivingEntity caster, ServerLevel level) {
        for (Active a : ACTIVE) {
            if (a.caster == caster) {
                return false;
            }
        }
        if (com.starkinhon.battlegroundsmod.power.powers.duel.DomainDuel.sukunaOpened(caster, level)) {
            // Gojo's domain opened just before, with Sukuna inside its reach: the two fight for it (duel/DomainDuel).
            return true;
        }
        PowerData data = caster.getData(ModAttachments.POWER);
        Active active = new Active(caster, level, caster.position());
        ACTIVE.add(active);
        int still = WAVE + CUT_PLAY;
        data.busyTicks = still;
        holdCaster(active, still);
        send(active, M_WAVE, Math.round(caster.getYRot() * 10), -1);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), SKILL, false));
        level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 1.4f, 0.6f);
        run(active, level);
        return true;
    }

    // Sukuna won the Duelo de Expansões: his Santuário takes over right away, the fast version (no wave, no cutscene:
    // the black aura and the six eyes, then the cutting), with Gojo and everyone who was in Gojo's domain caught in it.
    public static void startWon(LivingEntity caster, ServerLevel level, java.util.Collection<LivingEntity> victims) {
        Active active = new Active(caster, level, caster.position());
        active.quick = true;
        active.skip = WAVE;
        ACTIVE.add(active);
        for (LivingEntity v : victims) {
            if (v != caster && v.isAlive() && !v.isRemoved() && v.level() == level) {
                catchOne(active, v, false);
            }
        }
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), SKILL, false));
        level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.6f, 0.5f);
        run(active, level);
    }

    // The Sukuna whose Santuário opened at most window ticks ago with other inside its raio (a Gojo who can still
    // answer with his own), or null.
    public static LivingEntity openedNear(LivingEntity other, int window) {
        for (Active active : ACTIVE) {
            if (active.caster != other && !active.closing && !active.cancelled && !active.yielded && active.caster.isAlive()
                    && active.age <= window && active.level == other.level() && other.position().distanceTo(active.center) <= RADIUS) {
                return active.caster;
            }
        }
        return null;
    }

    // Gives way to the duel: everything it started stops (its holds, the clients' scene) and it is gone, no burnout.
    public static void yieldTo(LivingEntity caster) {
        for (Active active : List.copyOf(ACTIVE)) {
            if (active.caster != caster || active.closing || active.yielded) {
                continue;
            }
            active.yielded = true;
            release(active);
            // Sukuna himself, held in his sign by it: let go of (the duel holds him its own way).
            Freeze.freeAll(caster);
            caster.removeEffect(ModEffects.ENRAIZADO);
            caster.getData(ModAttachments.POWER).busyTicks = 0;
            comeBack(active);
            send(active, M_GONE, 0, -1);
            ACTIVE.remove(active);
        }
    }

    private static void run(Active active, ServerLevel level) {
        LivingEntity caster = active.caster;
        Tasks.add(raw -> {
            if (active.yielded) {
                return true;
            }
            int age = raw + active.skip;
            active.age = age;
            boolean gone = !caster.isAlive() || caster.isRemoved() || caster.level() != level;
            int end = WAVE + (active.quick ? (CUT_RED - QUICK_FROM) : CUT_PLAY) + FREE_TICKS;
            boolean allDead = active.deadAt >= 0 && age >= active.deadAt + 40;
            if (!active.closing && (gone || active.cancelled || allDead || (active.decided && age >= end))) {
                close(active);
            }
            if (active.closing) {
                if (age >= active.closeAt + CLOSE_TICKS) {
                    comeBack(active);
                    burnout(active);
                    send(active, M_GONE, 0, -1);
                    ACTIVE.remove(active);
                    return true;
                }
                if (active.inside) {
                    floor(active);
                }
                return false;
            }
            if (age < WAVE) {
                wave(active, age);
            } else if (!active.decided) {
                decide(active);
            }
            if (active.decided) {
                inside(active);
            }
            return false;
        });
    }

    private static void holdCaster(Active active, int ticks) {
        LivingEntity caster = active.caster;
        Freeze.holdLookFree(caster, ticks);
        caster.addEffect(new MobEffectInstance(ModEffects.ENRAIZADO, ticks, 0, false, false, false));
    }

    // The shockwave: holds the attacks it reaches, catches everyone in it.
    private static void wave(Active active, int age) {
        double front = waveRadius(age);
        Destructible.holdAround(active.level, active.center, front, WAVE - age + 4, active.caster);
        for (LivingEntity victim : Combat.inRadius(active.level, active.center, front, active.caster)) {
            catchOne(active, victim, true);
        }
        keepHeld(active, age);
    }

    // Everyone caught stays truly still (a tick of overlap so nobody slips free in between).
    private static void keepHeld(Active active, int age) {
        for (LivingEntity v : active.caught) {
            if (v.isAlive() && !v.isRemoved() && (age % HOLD_STEP == 0 || !Freeze.isFrozen(v))) {
                hold(active, v);
            }
        }
    }

    private static void catchOne(Active active, LivingEntity victim, boolean paralyze) {
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
        if (Stamina.resists(victim) && victim instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("message.battlegroundsmod.shrine_resist").withStyle(st -> st.withColor(0xFF6070)), true);
        }
        if (paralyze) {
            hold(active, victim);
        }
        if (active.inside) {
            takeInside(active, victim);
        }
    }

    private static void hold(Active active, LivingEntity victim) {
        Runnable old = active.holds.put(victim, Freeze.hold(victim, HOLD_STEP + 1));
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

    // The wave is done: what it stopped is swallowed; the cutscene plays if it caught someone, else it opens fast.
    private static void decide(Active active) {
        active.decided = true;
        Destructible.eraseAlong(active.level, active.center, active.center, RADIUS, active.caster);
        boolean anyone = active.caught.stream().anyMatch(LivingEntity::isAlive);
        if (!anyone) {
            active.quick = true;
        }
        LivingEntity target = nearest(active);
        if (target != null && active.caster.isAlive()) {
            Vec3 to = target.position().subtract(active.caster.position());
            if (to.horizontalDistanceSqr() > 1.0E-4) {
                active.forward = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90;
            }
        }
        if (!active.quick) {
            Freeze.face(active.caster, active.forward, 0);
        }
        int yaw10 = Math.round(active.forward * 10);
        send(active, active.quick ? M_QUICK : M_CUTSCENE, yaw10, target != null ? target.getId() : -1);
        // Everyone goes into the domain's own space right now, before the opening scenes (Stark 07:27): the ground turning
        // to water, the drop and the drowning play on its clean flat stage, so no block of the world gets in the way.
        goInside(active);
        if (active.quick) {
            freeCaster(active);
            release(active);
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(active.caster, new ModPayloads.PlaySkillAnimation(active.caster.getId(), "", false));
        }
    }

    private static LivingEntity nearest(Active active) {
        LivingEntity caster = active.caster;
        if (caster instanceof Mob mob && mob.getTarget() != null && active.caught.contains(mob.getTarget()) && mob.getTarget().isAlive()) {
            return mob.getTarget();
        }
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity v : active.caught) {
            double d = v.distanceToSqr(caster);
            if (v.isAlive() && d < bd) {
                bd = d;
                best = v;
            }
        }
        return best;
    }

    private static void freeCaster(Active active) {
        LivingEntity caster = active.caster;
        caster.getData(ModAttachments.POWER).busyTicks = 0;
        Freeze.free(caster);
        caster.removeEffect(ModEffects.ENRAIZADO);
    }

    // After the wave: the cutscene (everyone held), the trip inside, then the sure hits until it closes.
    private static void inside(Active active) {
        double c = active.cut();
        int age = active.age;
        if (!active.inside && c >= CUT_INSIDE) {
            goInside(active);
        }
        if (active.inside) {
            floor(active);
        }
        // Anyone who walks into where it opened, out in the world, is caught and taken in.
        for (LivingEntity victim : Combat.inRadius(active.level, active.center, RADIUS, active.caster)) {
            if (!active.back.containsKey(victim)) {
                catchOne(active, victim, active.scene());
            }
        }
        if (active.scene()) {
            keepHeld(active, age);
            return;
        }
        if (c >= CUT_PLAY && c - 1 < CUT_PLAY && !active.quick) {
            // Second 13: the camera is back over his shoulder, everyone moves.
            freeCaster(active);
            release(active);
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(active.caster, new ModPayloads.PlaySkillAnimation(active.caster.getId(), "", false));
        }
        if (c < CUT_PLAY) {
            return;
        }
        int p = (int) Math.round(c - CUT_PLAY);
        boolean clivar = p >= CLIVAR_FIRST && (p - CLIVAR_FIRST) % CLIVAR_EVERY == 0;
        LivingEntity caster = active.caster;
        for (LivingEntity v : active.caught) {
            if (!v.isAlive() || v.isRemoved() || v.level() != active.level) {
                continue;
            }
            boolean resisting = Stamina.resists(v);
            if (resisting) {
                Stamina.drain(v, RESIST_DRAIN / 20.0);
            }
            if (Math.floorMod(p + cutPhase(v.getId()), CUT_EVERY) == 0) {
                domainCut(caster, v, CUT_DAMAGE);
                if (!resisting) {
                    Stun.apply(v, CUT_STUN);
                    // Techniques still work inside, but a charged one gets cut off by each cut that lands (Stark,
                    // 2026-11-09): quick moves come out, holding one is a gamble unless you resist with stamina.
                    if (com.starkinhon.battlegroundsmod.combat.Charge.charging(v)) {
                        com.starkinhon.battlegroundsmod.power.PowerLogic.interrupt(v);
                    }
                }
            }
            if (clivar) {
                domainCut(caster, v, CLIVAR_DAMAGE);
                active.level.playSound(null, v.getX(), v.getY() + 1, v.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.7f, 0.6f + 0.3f * (p % 3));
            }
        }
        if (active.deadAt < 0 && !active.caught.isEmpty() && active.caught.stream().noneMatch(LivingEntity::isAlive)) {
            active.deadAt = age;
        }
    }

    // A cut of the domain: its damage only, the hurt's own knockback taken back, like Kokushibo's moons (Stark,
    // 2026-11-09: Madara kept flying across the domain from the cuts). Only the domain's cuts: every other blow inside
    // still throws as usual.
    private static void domainCut(LivingEntity caster, LivingEntity v, float damage) {
        Vec3 motion = v.getDeltaMovement();
        Combat.hurt(caster, v, damage);
        v.setDeltaMovement(motion);
        v.hurtMarked = true;
    }

    // When each one's little cut lands, spread so they don't all land on the same tick (the client draws it the same).
    public static int cutPhase(int id) {
        return Math.floorMod(id * 7 + 3, CUT_EVERY);
    }

    // Into the space of its own: everyone where they were relative to Sukuna, the nearest one TARGET_GAP in front of him.
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
        LivingEntity target = nearest(active);
        if (target != null && caster.isAlive()) {
            Vec3 dir = Vec3.directionFromRotation(0, active.forward);
            dir = new Vec3(dir.x, 0, dir.z).normalize();
            Vec3 spot = caster.position().add(dir.scale(TARGET_GAP));
            Freeze.face(caster, active.forward, 0);
            if (Freeze.isFrozen(target)) {
                Freeze.place(target, new Vec3(spot.x, active.arena.y, spot.z), Mth.wrapDegrees(active.forward + 180), 0);
            } else {
                put(target, new Vec3(spot.x, active.arena.y, spot.z));
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
        // Kept within the barrier's raio around where it opened.
        Vec3 rel = new Vec3(from.x - active.center.x, 0, from.z - active.center.z);
        if (rel.length() > RADIUS - 2) {
            rel = rel.normalize().scale(RADIUS - 2);
        }
        put(entity, new Vec3(active.center.x + rel.x, active.arena.y, active.center.z + rel.z));
    }

    // The floor holds everyone up, and the barrier keeps everyone within its raio.
    private static void floor(Active active) {
        double y = active.arena.y;
        for (LivingEntity entity : active.back.keySet()) {
            if (!entity.isAlive() || entity.isRemoved() || entity.level() != active.level) {
                continue;
            }
            entity.resetFallDistance();
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
                    put(player, new Vec3(active.center.x + dx * k, Math.max(player.getY(), y), active.center.z + dz * k));
                }
                continue;
            }
            if (Freeze.isFrozen(entity)) {
                continue;
            }
            if (out) {
                double k = (RADIUS - 1.5) / r;
                entity.setPos(active.center.x + dx * k, entity.getY(), active.center.z + dz * k);
            }
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
        if (active.caster.isAlive()) {
            freeCaster(active);
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(active.caster, new ModPayloads.PlaySkillAnimation(active.caster.getId(), "", false));
        }
        com.starkinhon.battlegroundsmod.combat.SkillStop.stop(active.caster);
        send(active, M_CLOSE, 0, -1);
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
        return (entity instanceof Player player && player.isCreative())
                || com.starkinhon.battlegroundsmod.power.powers.sub.jj.JjCommon.inSimpleDomain(entity);
    }

    // ---- hooks ----

    // V again: the second press within DOUBLE_TAP ticks of the first makes it the fast version; any later press closes it.
    public static boolean press(LivingEntity caster) {
        for (Active active : ACTIVE) {
            if (active.caster != caster || active.closing || active.cancelled) {
                continue;
            }
            if (!active.decided && active.age <= DOUBLE_TAP) {
                if (!active.quick) {
                    active.quick = true;
                }
                return true;
            }
            active.cancelled = true;
            return true;
        }
        return false;
    }

    // Caught while the cutscene plays: no skills, no boss moves.
    public static boolean blocks(LivingEntity entity) {
        for (Active active : ACTIVE) {
            if (active.caster != entity && active.caught.contains(entity) && (!active.decided || active.scene()) && !active.closing) {
                return true;
            }
        }
        return false;
    }

    // While the wave runs and the cutscene plays nothing touches Sukuna, and nobody but him touches those caught.
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        LivingEntity hurt = event.getEntity();
        for (Active active : ACTIVE) {
            if (active.closing || !(active.age < WAVE || active.scene())) {
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

    // a and b are not in the same space (one inside a Santuário, the other out in the world or in another one).
    public static boolean apart(LivingEntity a, LivingEntity b) {
        return insideOf(a) != insideOf(b);
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

    private static void send(Active active, int mode, int a, int b) {
        Vec3 center = active.center;
        for (ServerPlayer player : active.level.players()) {
            boolean near = player.position().distanceToSqr(center) <= VIEW_DISTANCE * VIEW_DISTANCE
                    || player.position().distanceToSqr(active.arena) <= VIEW_DISTANCE * VIEW_DISTANCE;
            if (!near && player != active.caster) {
                continue;
            }
            // Those in it see the cutscene: Sukuna, everyone caught, the creative players inside with him.
            boolean part = player == active.caster || active.caught.contains(player) || active.free.contains(player);
            PacketDistributor.sendToPlayer(player, new ModPayloads.SukunaDomain(active.caster.getId(), center, mode, a, b, part));
        }
    }
}
