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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// V (ult): Expansão de Domínio: Vazio Infinito (gojo.dominio.json, see DominioSpec for the phases). The server side:
// Gojo held still in his pose and untouchable (Infinity) until the white has closed; the shockwave stopping every attack
// in flight around him, then the white erasing them; everyone the white reaches paralyzed (and their moves cut off) until
// the domain shatters, also anyone who walks in later; the damage during the void; and a kill inside it told to the
// clients, whose screens go to red and white ink. Everything seen is on the clients (client/fx/DomainScene), the
// cutscene of the sign is the editor's file gojo.dominio (data/.../souls_cutscenes). If Gojo falls, it shatters early.
public final class InfiniteVoid {
    public static final String SKILL = DominioSpec.SKILL;
    // Players this far away get the domain, so they see the ball from outside.
    private static final double VIEW_DISTANCE = 192;
    // Those caught are held in short stretches, so they go free the moment it ends (or Gojo falls).
    private static final int HOLD_STEP = 10;

    // A domain open right now: for the kills inside it and Gojo's Infinity.
    private static final class Active {
        final LivingEntity caster;
        final ServerLevel level;
        final Vec3 center;
        final DominioSpec spec;
        final boolean awakened;
        int age;

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

        data.busyTicks = tunnel;
        if (spec.dominio().pararUsuario()) {
            Freeze.hold(caster, tunnel);
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

        Set<LivingEntity> caught = new HashSet<>();
        boolean[] closing = {false};
        Tasks.add(age -> {
            active.age = age;
            boolean gone = !caster.isAlive() || caster.isRemoved() || caster.level() != level;
            if (gone && !closing[0] && age < close) {
                // Gojo fell: it shatters now.
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
            if (age == white) {
                // The white light swallows what was stopped (and anything else inside), without its impact.
                Destructible.eraseAlong(level, center, center, radius, caster);
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
            }
            if (age >= spec.goneTick()) {
                ACTIVE.remove(active);
                return true;
            }
            return false;
        });
        return true;
    }

    // Infinity: nothing touches Gojo while he makes the sign and the white closes.
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        for (Active active : ACTIVE) {
            if (active.caster == event.getEntity() && active.age < active.spec.tunnelTick()) {
                event.setCanceled(true);
                return;
            }
        }
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
