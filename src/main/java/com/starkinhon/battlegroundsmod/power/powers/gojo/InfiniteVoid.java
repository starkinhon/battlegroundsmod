package com.starkinhon.battlegroundsmod.power.powers.gojo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
// face to face where they stand, and the clients are told who the target is (the tunnel's cutscene, the editor's file
// gojo.dominio, plays on Gojo's screen). A kill inside is told to the clients, whose screens go to red and white ink, and
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

    // A domain open right now: for the kills inside it and Gojo's Infinity.
    private static final class Active {
        final LivingEntity caster;
        final ServerLevel level;
        final Vec3 center;
        final DominioSpec spec;
        final boolean awakened;
        final Set<LivingEntity> caught = new HashSet<>();
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
            if ((gone || done) && !closing[0] && age < close) {
                // It shatters now.
                closing[0] = true;
                send(level, center, new ModPayloads.GojoDomain(caster.getId(), center, awakened, 1, 0));
                SkillSounds.play(level, spec.sons(), "quebra", 0, spec.closeTicks(), onde -> center.add(0, 1, 0), screen);
                ACTIVE.remove(active);
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
                    boolean fresh = caught.add(victim);
                    if (fresh && victim.hasData(ModAttachments.POWER)) {
                        // Swallowed by the white: whatever they were doing is cut off.
                        PowerLogic.interrupt(victim);
                    }
                    if (acerto.paralisar() && (fresh || (age - white) % HOLD_STEP == 0)) {
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

    // The tunnel starts: Gojo and his target turned face to face where they stand, in a straight line, and held so.
    private static void faceOff(Active active) {
        LivingEntity caster = active.caster;
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
            if (to.horizontalDistanceSqr() > 1.0E-4) {
                float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90;
                Freeze.face(caster, yaw, 0);
                Freeze.face(target, Mth.wrapDegrees(yaw + 180), 0);
            }
        }
        send(active.level, active.center,
                new ModPayloads.GojoDomain(caster.getId(), active.center, active.awakened, 3, target != null ? target.getId() : -1));
    }

    // Someone dies inside an open domain (the tunnel or the void): everyone inside sees the red and white ink.
    public static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled() || ACTIVE.isEmpty()) {
            return;
        }
        LivingEntity dead = event.getEntity();
        for (Active active : List.copyOf(ACTIVE)) {
            if (dead.level() != active.level || dead == active.caster || active.age < active.spec.tunnelTick()
                    || active.age >= active.spec.closeTick() || dead.position().distanceTo(active.center) > active.spec.radius() + 1) {
                continue;
            }
            if (dead == active.target && active.killedAt < 0) {
                active.killedAt = active.age;
            }
            ModPayloads.GojoDomain payload = new ModPayloads.GojoDomain(active.caster.getId(), active.center, active.awakened, 2, dead.getId());
            for (ServerPlayer player : active.level.players()) {
                if (player.position().distanceTo(active.center) <= active.spec.radius() + 4 || player == active.caster) {
                    PacketDistributor.sendToPlayer(player, payload);
                }
            }
            return;
        }
    }

    public static void clear() {
        ACTIVE.clear();
    }

    private static void send(ServerLevel level, Vec3 center, ModPayloads.GojoDomain payload) {
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceToSqr(center) <= VIEW_DISTANCE * VIEW_DISTANCE) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }
}
