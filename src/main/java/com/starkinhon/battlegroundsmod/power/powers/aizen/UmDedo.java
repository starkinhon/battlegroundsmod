package com.starkinhon.battlegroundsmod.power.powers.aizen;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.combat.SkillSounds;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.PowerLogic;
import com.starkinhon.battlegroundsmod.skill.AizenSpecs;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// Um Dedo, Aizen's B (aizen.um_dedo.json), a counter: he holds up one finger for janelaTicks. A hit from someone within
// alcance in that time stops dead on the finger (no damage; both held still hitstopTicks, the world flashes), then the
// sword comes out and cuts the attacker corteTicks later, throwing them back and stunning them. Nothing hits: nothing
// happens. Works while stunned, like every counter.
// Timeline: 0 "pronto", "bloqueio" when it stops a hit, "corte" when the sword swings, "acerto" when it lands.
public final class UmDedo {
    public static final String SKILL = AizenSpecs.UM_DEDO;

    private record Ready(int interrupts, long until) {
    }

    private static final Map<LivingEntity, Ready> READY = new HashMap<>();

    private UmDedo() {
    }

    public static boolean use(LivingEntity caster, ServerLevel level) {
        PowerData data = caster.getData(ModAttachments.POWER);
        boolean awakened = data.awakened;
        AizenSpecs.UmDedo spec = AizenSpecs.UmDedo.get(awakened);
        int window = Math.max(1, spec.janelaTicks());
        READY.put(caster, new Ready(data.interrupts, level.getGameTime() + window));
        data.busyTicks = window;
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), SKILL, awakened));
        SkillSounds.play(level, spec.sons(), "pronto", 0, 0, onde -> Combat.handPos(caster), List.of());
        Fx.effects(level, SKILL, awakened, "pronto", Combat.handPos(caster), new Vec3(0, 1, 0));
        Ready mine = READY.get(caster);
        Tasks.later(window + 1, () -> READY.remove(caster, mine));
        return true;
    }

    // From PowerEvents: a hit on someone with the finger up.
    public static void onIncomingDamage(LivingEntity victim, LivingIncomingDamageEvent event) {
        Ready ready = READY.get(victim);
        if (ready == null || !(event.getSource().getEntity() instanceof LivingEntity attacker) || attacker == victim
                || event.getSource().getDirectEntity() != attacker) {
            return;
        }
        PowerData data = victim.getData(ModAttachments.POWER);
        ServerLevel level = Combat.level(victim);
        AizenSpecs.UmDedo spec = AizenSpecs.UmDedo.get(data.awakened);
        if (data.interrupts != ready.interrupts() || level.getGameTime() > ready.until() || attacker.distanceTo(victim) > spec.alcance()) {
            return;
        }
        event.setCanceled(true);
        READY.remove(victim);
        counter(victim, attacker, level, spec, data.awakened);
    }

    private static void counter(LivingEntity caster, LivingEntity attacker, ServerLevel level, AizenSpecs.UmDedo spec, boolean awakened) {
        int stop = Math.max(0, spec.hitstopTicks());
        int cut = Math.max(stop + 1, spec.corteTicks());
        Vec3 contact = Combat.handPos(caster).lerp(Combat.center(attacker), 0.35);
        Vec3 toAttacker = Combat.center(attacker).subtract(Combat.center(caster));
        Vec3 dir = toAttacker.lengthSqr() < 1.0E-4 ? Combat.flatLook(caster) : toAttacker.normalize();
        // Whatever the attacker was doing stops; both freeze on the finger.
        PowerLogic.interrupt(attacker);
        Freeze.hold(attacker, cut + 2);
        Freeze.hold(caster, stop);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.Hitstop(caster.getId(), stop));
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(attacker, new ModPayloads.Hitstop(attacker.getId(), stop));
        caster.getData(ModAttachments.POWER).busyTicks = cut + 8;
        SkillSounds.play(level, spec.sons(), "bloqueio", 0, 0, onde -> contact, List.of());
        Fx.effects(level, SKILL, awakened, "bloqueio", contact, dir);
        Tasks.later(stop, () -> PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster,
                new ModPayloads.PlaySkillAnimation(caster.getId(), AizenSpecs.UM_DEDO_CORTE, awakened)));
        Tasks.later(cut, () -> {
            if (!caster.isAlive()) {
                Freeze.free(attacker);
                return;
            }
            Vec3 middle = Combat.center(caster).add(dir.scale(0.8));
            SkillSounds.play(level, spec.sons(), "corte", 0, 0, onde -> Combat.center(caster), List.of());
            Fx.effects(level, SKILL, awakened, "corte", middle, dir);
            // The cut: a diagonal slit in front of him.
            Vec3 side = dir.cross(new Vec3(0, 1, 0));
            Vec3 slash = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize().add(0, 0.8, 0).normalize();
            AizenNet.send(level, AizenNet.CUT, caster.getId(), Combat.center(attacker), slash, 1.6f, 1.2f);
            Freeze.free(attacker);
            if (!attacker.isAlive() || !Combat.isTarget(attacker, caster)) {
                return;
            }
            Combat.hurtMagic(caster, attacker, spec.dano());
            Stun.apply(attacker, spec.atordoarTicks());
            Combat.push(attacker, dir, spec.empurrao(), spec.subida());
            SkillSounds.play(level, spec.sons(), "acerto", 0, 0, onde -> Combat.center(attacker), List.of());
            Fx.effects(level, SKILL, awakened, "acerto", Combat.center(attacker), new Vec3(0, 1, 0));
        });
    }

    public static void clear() {
        READY.clear();
    }
}
