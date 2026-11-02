package com.starkinhon.battlegroundsmod.power.powers.gojo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Destructible;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.SkillSounds;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.PowerLogic;
import com.starkinhon.battlegroundsmod.skill.DominioSpec;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// V (ult): Expansão de Domínio: Vazio Infinito (gojo.dominio.json, see DominioSpec for the phases). The server side:
// Gojo held still in his pose and untouchable (Infinity) until the tunnel is over; the shockwave stopping every attack
// in flight around him, then the white erasing them; everyone the white reaches paralyzed (and their moves cut off) until
// the domain shatters, also anyone who walks in later. Inside, only Gojo acts: any other attack is erased, nobody else can
// use a skill (blocks), and nobody but Gojo can hurt those caught. When the tunnel starts, Gojo and his target are turned
// face to face, the target 20 blocks straight in front of Gojo, and the clients are told who the target is. No cutscene.
// The inside is a space of its own (DominioSpec.arena): when the tunnel starts, Gojo and everyone caught are taken
// straight up above the build height, the same places relative to each other, onto a floor of their own that holds them
// up (no blocks there, so it works the same opened in the air, on a hill or in a forest), and are brought back where they
// were when it shatters. Players up there may fly for the server (so it never kicks them for floating) but are kept on
// the floor; if the server stops while someone is up there, they are put back on their next login.
// A kill inside is told to the clients, whose screens go to red and white ink, and
// when the target dies the domain shatters. Everything seen is on the clients (client/fx/DomainScene). If Gojo falls, it
// shatters early too.
public final class InfiniteVoid {
    public static final String SKILL = DominioSpec.SKILL;
    // Players this far away get the domain, so they see the ball from outside.
    private static final double VIEW_DISTANCE = 192;
    // Those caught are held in short stretches, so they go free the moment it ends (or Gojo falls).
    private static final int HOLD_STEP = 10;
    // The domain shatters this long after its target dies, once the red and white ink has played.
    private static final int KILL_CLOSE_DELAY = 30;
    // In the tunnel the target stands this far straight in front of Gojo.
    private static final double TARGET_GAP = 20;
    // How long the clients wait to hear of the tunnel's places, so the target has been seen where it was put (the
    // tunnel's direction is measured from it).
    private static final int PLACE_SYNC_TICKS = 4;
    // Someone below the floor by more than this (lag, a knockback) is put back on it.
    private static final double FLOOR_SLACK = 1.5;
    // Saved on players taken inside, so a server stop never leaves them in the sky (see onLogin).
    private static final String BACK_TAG = "battlegroundsmod_void_back";

    // A domain open right now: for the kills inside it and Gojo's Infinity.
    private static final class Active {
        final LivingEntity caster;
        final ServerLevel level;
        final Vec3 center;
        final DominioSpec spec;
        final boolean awakened;
        final Set<LivingEntity> caught = new HashSet<>();
        // The floor of the inside, straight above center.
        final Vec3 arena;
        // Everyone taken inside, and where each was in the world.
        final Map<LivingEntity, Vec3> back = new LinkedHashMap<>();
        // Players who were allowed to fly only for this (so the server never kicks them for floating).
        final Set<ServerPlayer> granted = new HashSet<>();
        boolean inside;
        // Gojo pressed V again: it shatters now.
        boolean cancelled;
        LivingEntity target;
        int age;
        // When the target (or the last one caught) died, -1 while they live.
        int killedAt = -1;

        Active(LivingEntity caster, ServerLevel level, Vec3 center, DominioSpec spec, boolean awakened) {
            this.caster = caster;
            this.level = level;
            this.center = center;
            this.spec = spec;
            this.awakened = awakened;
            this.arena = DominioSpec.arena(level, center);
        }
    }

    private static final List<Active> ACTIVE = new ArrayList<>();

    private InfiniteVoid() {
    }

    public static boolean use(LivingEntity caster, ServerLevel level) {
        PowerData data = caster.getData(ModAttachments.POWER);
        boolean awakened = data.awakened;
        DominioSpec spec = DominioSpec.get(awakened);
        Vec3 center = caster.position();
        double radius = spec.radius();
        int wave = spec.ondaTick();
        int white = spec.whiteTick();
        int tunnel = spec.tunnelTick();
        int voidTick = spec.voidTick();
        int close = spec.closeTick();
        int release = close + Math.min(spec.closeTicks(), 16);

        // Gojo stands still through the sign, the white and the tunnel; he moves again when the void opens.
        data.busyTicks = voidTick;
        if (spec.dominio().pararUsuario()) {
            Freeze.hold(caster, voidTick);
        }
        Active active = new Active(caster, level, center, spec, awakened);
        ACTIVE.add(active);
        send(level, center, new ModPayloads.GojoDomain(caster.getId(), center, awakened, 0, 0));
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), SKILL, awakened));

        List<ServerPlayer> screen = new ArrayList<>();
        if (caster instanceof ServerPlayer player) {
            screen.add(player);
        }
        Function<String, Vec3> where = onde -> caster.isAlive() ? caster.position().add(0, 1, 0) : center.add(0, 1, 0);
        SkillSounds.play(level, spec.sons(), "uso", 0, wave, where, screen);
        SkillSounds.play(level, spec.sons(), "onda", wave, white - wave, where, screen);
        SkillSounds.play(level, spec.sons(), "branco", white, tunnel - white, where, screen);
        SkillSounds.play(level, spec.sons(), "tunel", tunnel, voidTick - tunnel, where, screen);

        Set<LivingEntity> caught = active.caught;
        boolean[] closing = {false};
        Tasks.add(age -> {
            active.age = age;
            boolean gone = !caster.isAlive() || caster.isRemoved() || caster.level() != level;
            // The target died (the ink plays first), or everyone caught is dead.
            boolean done = active.killedAt >= 0 && age >= active.killedAt + KILL_CLOSE_DELAY;
            if ((gone || done || active.cancelled) && !closing[0] && age < close) {
                // It shatters now.
                closing[0] = true;
                send(level, center, new ModPayloads.GojoDomain(caster.getId(), center, awakened, 1, 0));
                SkillSounds.play(level, spec.sons(), "quebra", 0, spec.closeTicks(), onde -> center.add(0, 1, 0), screen);
                // Everyone inside is back in the world when it shatters.
                Tasks.later(spec.shatterDelay(), () -> {
                    comeBack(active);
                    ACTIVE.remove(active);
                });
                return true;
            }
            if (age >= wave && age < white) {
                // The shockwave stops everything in flight it has reached, until the white swallows it.
                Destructible.holdAround(level, center, spec.waveRadius(age), white - age + 3, caster);
            }
            if (age >= white && age < close) {
                // The white light swallows what was stopped (and anything else inside), without its impact; after that
                // nothing but Gojo's own attacks lasts inside.
                Destructible.eraseAlong(level, center, center, age == white ? radius : spec.whiteRadius(age), caster);
            }
            if (age == tunnel) {
                faceOff(active);
            }
            if (active.inside) {
                if (age >= close + spec.shatterDelay()) {
                    comeBack(active);
                } else {
                    floor(active);
                }
            }
            if (age == voidTick) {
                SkillSounds.play(level, spec.sons(), "vazio", 0, close - voidTick, where, screen);
            }
            if (age == close) {
                closing[0] = true;
                SkillSounds.play(level, spec.sons(), "quebra", 0, spec.closeTicks(), onde -> center.add(0, 1, 0), screen);
            }
            if (age >= white && age < release) {
                double reach = spec.whiteRadius(age);
                DominioSpec.Acerto acerto = spec.acerto();
                for (LivingEntity victim : Combat.inRadius(level, center, reach, caster)) {
                    if (!caught.add(victim)) {
                        continue;
                    }
                    if (victim.hasData(ModAttachments.POWER)) {
                        // Swallowed by the white: whatever they were doing is cut off.
                        PowerLogic.interrupt(victim);
                    }
                    if (acerto.paralisar()) {
                        Freeze.hold(victim, Math.min(HOLD_STEP + 1, release - age));
                    }
                    if (active.inside) {
                        // Walked into the black ball later: taken inside too.
                        takeInside(active, victim);
                    }
                }
                for (LivingEntity victim : caught) {
                    if (!victim.isAlive() || victim.isRemoved() || victim.level() != level) {
                        continue;
                    }
                    if (acerto.paralisar() && (age - white) % HOLD_STEP == 0) {
                        // A tick of overlap so they never slip free in between.
                        Freeze.hold(victim, Math.min(HOLD_STEP + 1, release - age));
                    }
                    if (age >= voidTick && age < close && acerto.dano() > 0 && acerto.aCadaTicks() > 0
                            && (age - voidTick) % acerto.aCadaTicks() == 0) {
                        Combat.hurt(caster, victim, (float) acerto.dano());
                    }
                }
                if (active.killedAt < 0 && age >= tunnel && !caught.isEmpty() && caught.stream().noneMatch(LivingEntity::isAlive)) {
                    active.killedAt = age;
                }
            }
            if (age >= spec.goneTick()) {
                ACTIVE.remove(active);
                return true;
            }
            return false;
        });
        return true;
    }

    // V again with the domain open: Gojo closes it himself, it shatters right away (everyone back in the world) and he can
    // move again. Returns true when there was one to close.
    public static boolean cancel(LivingEntity caster) {
        for (Active active : ACTIVE) {
            if (active.caster == caster && !active.cancelled && active.age < active.spec.closeTick()) {
                active.cancelled = true;
                caster.getData(ModAttachments.POWER).busyTicks = 0;
                Freeze.free(caster);
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), "", false));
                return true;
            }
        }
        return false;
    }

    // Infinity: nothing touches Gojo while he makes the sign and the white closes. And nobody but Gojo hurts those caught.
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity hurt = event.getEntity();
        for (Active active : ACTIVE) {
            if (active.caster == hurt && active.age < active.spec.tunnelTick()) {
                event.setCanceled(true);
                return;
            }
            if (active.caster != hurt && active.caught.contains(hurt) && active.age < active.spec.closeTick()
                    && event.getSource().getEntity() != active.caster) {
                event.setCanceled(true);
                return;
            }
        }
    }

    // Caught in someone else's domain: no skills, no boss moves, nothing of their own until it shatters.
    public static boolean blocks(LivingEntity entity) {
        if (ACTIVE.isEmpty()) {
            return false;
        }
        for (Active active : ACTIVE) {
            if (active.caster != entity && active.caught.contains(entity) && active.age < active.spec.closeTick()) {
                return true;
            }
        }
        return false;
    }

    // The tunnel starts: Gojo stands straight where he is and his target is put TARGET_GAP blocks in front of him, the two
    // face to face in a straight line, held so. The clients hear of it a few ticks later, once the target's new place
    // has reached them.
    private static void faceOff(Active active) {
        LivingEntity caster = active.caster;
        // Into the space of its own, everyone where they were relative to each other.
        active.inside = true;
        if (caster.isAlive()) {
            takeInside(active, caster);
        }
        for (LivingEntity victim : active.caught) {
            if (victim.isAlive() && !victim.isRemoved() && victim.level() == active.level) {
                takeInside(active, victim);
            }
        }
        LivingEntity target = null;
        if (caster instanceof Mob mob && mob.getTarget() != null && active.caught.contains(mob.getTarget()) && mob.getTarget().isAlive()) {
            target = mob.getTarget();
        }
        if (target == null) {
            double best = Double.MAX_VALUE;
            for (LivingEntity victim : active.caught) {
                double d = victim.distanceToSqr(caster);
                if (victim.isAlive() && d < best) {
                    best = d;
                    target = victim;
                }
            }
        }
        active.target = target;
        if (target != null && caster.isAlive()) {
            Vec3 to = target.position().subtract(caster.position());
            Vec3 dir = to.horizontalDistanceSqr() > 1.0E-4 ? new Vec3(to.x, 0, to.z).normalize() : Vec3.directionFromRotation(0, caster.getYRot());
            float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90;
            Freeze.face(caster, yaw, 0);
            Vec3 spot = caster.position().add(dir.scale(TARGET_GAP));
            Freeze.place(target, new Vec3(spot.x, active.arena.y, spot.z), Mth.wrapDegrees(yaw + 180), 0);
        }
        int targetId = target != null ? target.getId() : -1;
        Tasks.later(PLACE_SYNC_TICKS, () -> {
            if (ACTIVE.contains(active)) {
                send(active.level, active.center, new ModPayloads.GojoDomain(caster.getId(), active.center, active.awakened, 3, targetId));
            }
        });
    }

    // Takes someone up into the inside: straight above where they stand, onto its floor.
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
                // Standing on nothing for the server: allowed to fly so it never kicks them, kept on the floor below.
                player.getAbilities().mayfly = true;
                player.onUpdateAbilities();
                active.granted.add(player);
            }
        }
        put(entity, new Vec3(from.x, active.arena.y, from.z));
    }

    // The floor of the inside holds up everyone in it (no blocks there). Players move themselves (their client keeps
    // them on it, DomainScene.floor); here they never take fall damage and never fly, and fall back up if they sink.
    private static void floor(Active active) {
        double y = active.arena.y;
        for (LivingEntity entity : active.back.keySet()) {
            if (!entity.isAlive() || entity.isRemoved() || entity.level() != active.level) {
                continue;
            }
            entity.resetFallDistance();
            if (entity instanceof ServerPlayer player) {
                if (player.getAbilities().flying && active.granted.contains(player)) {
                    player.getAbilities().flying = false;
                    player.onUpdateAbilities();
                }
                if (player.getY() < y - FLOOR_SLACK) {
                    put(player, new Vec3(player.getX(), y, player.getZ()));
                }
                continue;
            }
            if (Freeze.isFrozen(entity)) {
                continue;
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

    // It shattered: everyone taken inside is put back where they were in the world.
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

    // Moves someone, held still or not, keeping how they look.
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

    // A player who logs in still up in a domain's inside (the server stopped while it was open) is put back where they
    // were, and can no longer fly unless they could before.
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

    // Someone dies inside an open domain (the tunnel or the void): everyone inside sees the red and white ink.
    public static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled() || ACTIVE.isEmpty()) {
            return;
        }
        LivingEntity dead = event.getEntity();
        for (Active active : List.copyOf(ACTIVE)) {
            if (dead.level() != active.level || dead == active.caster || active.age < active.spec.tunnelTick()
                    || active.age >= active.spec.closeTick() || !near(active, dead.position(), active.spec.radius() + 1)) {
                continue;
            }
            if (dead == active.target && active.killedAt < 0) {
                active.killedAt = active.age;
            }
            ModPayloads.GojoDomain payload = new ModPayloads.GojoDomain(active.caster.getId(), active.center, active.awakened, 2, dead.getId());
            for (ServerPlayer player : active.level.players()) {
                if (near(active, player.position(), active.spec.radius() + 4) || player == active.caster) {
                    PacketDistributor.sendToPlayer(player, payload);
                }
            }
            return;
        }
    }

    // pos is within range of the domain, out in the world or inside.
    private static boolean near(Active active, Vec3 pos, double range) {
        return pos.distanceTo(active.center) <= range || pos.distanceTo(active.arena) <= range;
    }

    // The server stopped: anyone still inside is put back on their next login (onLogin).
    public static void clear() {
        ACTIVE.clear();
    }

    private static void send(ServerLevel level, Vec3 center, ModPayloads.GojoDomain payload) {
        Vec3 arena = DominioSpec.arena(level, center);
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceToSqr(center) <= VIEW_DISTANCE * VIEW_DISTANCE
                    || player.position().distanceToSqr(arena) <= VIEW_DISTANCE * VIEW_DISTANCE) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }
}
