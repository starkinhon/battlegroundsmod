// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.power.powers.aizen;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Destructible;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.combat.Intangible;
import com.starkinhon.battlegroundsmod.combat.Ruin;
import com.starkinhon.battlegroundsmod.combat.SkillSounds;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.PowerLogic;
import com.starkinhon.battlegroundsmod.skill.AizenSpecs;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// Um Dedo + Danku, Aizen's B (aizen.um_dedo.json), one counter that reads what came (forja/aizen-kit, the danku scene).
// He holds up a finger for janelaTicks:
// - A blow from up close (a punch, a sword, anyone within alcance hitting with their own body): it stops dead on the
//   finger. The pressure bursts from there and cracks the ground, both are held still a moment; then he is gone and
//   standing behind the attacker with his back turned, putting the sword away. A breath of silence, the click of the
//   scabbard, and the cut that already happened shows up: the attacker takes dano, flies off, stunned atordoarTicks, and
//   the ground is torn (a crater where he stood, a furrow of 35 blocks the way he flies, another 22 the other way).
// - Anything else (a technique, a projectile, a blow from afar): Bakudo 81, Danku, rises between them, a pane of glass
//   that takes it and every other technique from that side for WALL_TICKS, cracks from where it was hit and shatters.
// Nothing hits: nothing happens. Works while stunned, like every counter.
public final class UmDedo {
    public static final String SKILL = AizenSpecs.UM_DEDO;
    // The pose once he shows up behind the attacker (the sword going back in).
    public static final String BEHIND = "aizen.dedo_atras";
    public static final String DANKU = "aizen.danku";

    // The page's timeline, in ticks after the stop (its hitstop of 0.55 s is real time here).
    private static final int STOP = 11;
    private static final int VANISH = STOP + 4;
    private static final int APPEAR = STOP + 6;
    private static final int REVEAL = STOP + 21;
    private static final double BEHIND_DIST = 4.85;
    private static final int WALL_TICKS = 32;
    private static final double WALL_AHEAD = 2.3;

    private record Ready(int interrupts, long until) {
    }

    private record Wall(Vec3 foot, Vec3 face, long until) {
    }

    private static final Map<LivingEntity, Ready> READY = new HashMap<>();
    private static final Map<LivingEntity, Wall> WALLS = new HashMap<>();

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
        Ready mine = READY.get(caster);
        Tasks.later(window + 1, () -> READY.remove(caster, mine));
        return true;
    }

    // From PowerEvents: a hit on someone with the finger up, or behind the Danku.
    public static void onIncomingDamage(LivingEntity victim, LivingIncomingDamageEvent event) {
        ServerLevel level = Combat.level(victim);
        Wall wall = WALLS.get(victim);
        if (wall != null) {
            if (level.getGameTime() > wall.until()) {
                WALLS.remove(victim);
            } else if (fromFront(victim, wall, event)) {
                event.setCanceled(true);
                return;
            }
        }
        Ready ready = READY.get(victim);
        if (ready == null) {
            return;
        }
        PowerData data = victim.getData(ModAttachments.POWER);
        if (data.interrupts != ready.interrupts() || level.getGameTime() > ready.until()) {
            return;
        }
        AizenSpecs.UmDedo spec = AizenSpecs.UmDedo.get(data.awakened);
        LivingEntity attacker = event.getSource().getEntity() instanceof LivingEntity l && l != victim ? l : null;
        boolean melee = attacker != null && event.getSource().getDirectEntity() == attacker && attacker.distanceTo(victim) <= spec.alcance()
                && !Combat.technique();
        // Nobody behind it (fire, a fall, the void) is not a blow: it goes through.
        if (!melee && event.getSource().getEntity() == null && event.getSource().getDirectEntity() == null) {
            return;
        }
        event.setCanceled(true);
        READY.remove(victim);
        if (melee) {
            counter(victim, attacker, level, spec, data.awakened);
        } else {
            Vec3 from = event.getSource().getSourcePosition();
            if (from == null) {
                from = attacker != null ? Combat.center(attacker) : Combat.center(victim).add(Combat.flatLook(victim).scale(4));
            }
            danku(victim, level, from);
        }
    }

    // A blow coming at him from the wall's side (its source in front of the pane), or with no place at all.
    private static boolean fromFront(LivingEntity victim, Wall wall, LivingIncomingDamageEvent event) {
        Vec3 from = event.getSource().getSourcePosition();
        if (from == null) {
            return event.getSource().getEntity() != null;
        }
        return from.subtract(victim.position()).dot(wall.face()) > 0;
    }

    // ---------- Danku ----------

    private static void danku(LivingEntity caster, ServerLevel level, Vec3 from) {
        Vec3 toward = from.subtract(Combat.center(caster));
        toward = new Vec3(toward.x, 0, toward.z);
        toward = toward.lengthSqr() < 1.0E-4 ? Combat.flatLook(caster) : toward.normalize();
        Vec3 foot = caster.position().add(toward.scale(WALL_AHEAD));
        long until = level.getGameTime() + WALL_TICKS;
        WALLS.put(caster, new Wall(foot, toward, until));
        caster.getData(ModAttachments.POWER).busyTicks = 8;
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), DANKU, false));
        AizenNet.send(level, AizenNet.DANKU, caster.getId(), foot, toward, 0, 0);
        // The blow lands on the glass a moment later (where it came from, at the pane).
        Vec3 hit = foot.add(0, Math.max(0.6, Math.min(3.6, from.y - foot.y)), 0);
        AizenNet.send(level, AizenNet.DANKU, caster.getId(), hit, toward, 1, 0);
        Fx.sound(level, foot, SoundEvents.AMETHYST_BLOCK_CHIME, 2f, 0.6f);
        Fx.sound(level, hit, SoundEvents.GLASS_BREAK, 0.8f, 1.6f);
        // Whatever is still flying at him from that side stops on the pane.
        Destructible.holdAround(level, foot.add(0, 2, 0), 4.5, WALL_TICKS, caster);
        Vec3 face = toward;
        Tasks.later(WALL_TICKS - 5, () -> {
            AizenNet.send(level, AizenNet.DANKU, caster.getId(), foot, face, 2, 0);
            Fx.sound(level, foot, SoundEvents.GLASS_BREAK, 2f, 0.8f);
        });
        Tasks.later(WALL_TICKS, () -> WALLS.remove(caster));
    }

    // ---------- the finger ----------

    private static void counter(LivingEntity caster, LivingEntity attacker, ServerLevel level, AizenSpecs.UmDedo spec, boolean awakened) {
        Vec3 finger = Combat.handPos(caster).lerp(Combat.center(attacker), 0.35);
        Vec3 toAttacker = Combat.center(attacker).subtract(Combat.center(caster));
        Vec3 dir = new Vec3(toAttacker.x, 0, toAttacker.z);
        dir = dir.lengthSqr() < 1.0E-4 ? Combat.flatLook(caster) : dir.normalize();
        // He lands past the attacker, back turned; the cut throws the attacker back the way it came.
        Vec3 toward = dir;
        Vec3 flyDir = dir.reverse();
        int total = REVEAL + 6;
        // Whatever the attacker was doing stops; both freeze on the finger.
        PowerLogic.interrupt(attacker);
        Freeze.hold(attacker, REVEAL);
        Freeze.hold(caster, APPEAR);
        caster.getData(ModAttachments.POWER).busyTicks = total;
        com.starkinhon.battlegroundsmod.boss.BossFalas.say(caster, "fala.battlegroundsmod.aizen.dedo", 400);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.Hitstop(caster.getId(), STOP));
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(attacker, new ModPayloads.Hitstop(attacker.getId(), STOP));
        SkillSounds.play(level, spec.sons(), "bloqueio", 0, 0, onde -> finger, List.of());
        Fx.sound(level, finger, SoundEvents.WARDEN_SONIC_BOOM, 0.8f, 1.6f);
        AizenNet.send(level, AizenNet.DEDO_STOP, caster.getId(), finger, dir, attacker.getId(), 0);
        // The pressure cracks the ground round them.
        Vec3 mid = AizenHit.ground(level, caster.position().add(dir.scale(0.9)));
        Ruin.bowl(level, mid.x, mid.z, 3.2, 0.8, 3, 0, Ruin.opts().rim(0.15));
        com.starkinhon.battlegroundsmod.combat.RuinNet.ring(level, mid, 24, 1.6, false, 40, AizenHit.DUST_LIGHT);

        Tasks.later(VANISH, () -> {
            if (!caster.isAlive()) {
                Freeze.free(attacker);
                return;
            }
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.HideEntity(caster.getId(), APPEAR - VANISH));
            Intangible.hold(caster, APPEAR - VANISH);
            Fx.sound(level, Combat.center(caster), SoundEvents.ENDERMAN_TELEPORT, 0.6f, 1.6f);
        });
        Tasks.later(APPEAR, () -> {
            if (!caster.isAlive()) {
                return;
            }
            Vec3 start = caster.position();
            Vec3 behind = attacker.position().add(toward.scale(BEHIND_DIST));
            if (caster instanceof ServerPlayer player && !Combat.canStandAt(player, behind)) {
                behind = start;
            }
            // His back to the attacker.
            float yaw = (float) Math.toDegrees(Math.atan2(-toward.x, toward.z));
            Freeze.free(caster);
            Freeze.place(caster, behind, yaw, 0);
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.HideEntity(caster.getId(), 0));
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), BEHIND, awakened));
            AizenNet.send(level, AizenNet.DEDO_BEHIND, caster.getId(), start, behind, attacker.getId(), 0);
            Freeze.hold(caster, REVEAL - APPEAR);
        });
        Tasks.later(REVEAL - 2, () -> Fx.sound(level, Combat.center(caster), SoundEvents.ARMOR_EQUIP_IRON.value(), 1.4f, 1.8f));
        Tasks.later(REVEAL, () -> {
            Freeze.free(attacker);
            if (!caster.isAlive()) {
                return;
            }
            Vec3 at = Combat.center(attacker);
            AizenNet.send(level, AizenNet.DEDO_CUT, caster.getId(), at, flyDir, attacker.getId(), 0);
            SkillSounds.play(level, spec.sons(), "corte", 0, 0, onde -> at, List.of());
            Fx.sound(level, at, SoundEvents.PLAYER_ATTACK_SWEEP, 2.4f, 0.5f);
            Fx.sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 2.5f, 0.8f);
            Vec3 floor = AizenHit.ground(level, attacker.position());
            AizenHit.crater(level, floor, 6.5, 3.6, 5, 0, true, 0xE8DCFF);
            AizenHit.furrow(level, floor.add(flyDir), floor.add(flyDir.scale(35)), 2.3, 5, 10);
            // the other way, a little to the side of where he stands
            Vec3 side = new Vec3(-toward.z, 0, toward.x).scale(2.4);
            AizenHit.furrow(level, floor.add(toward).add(side), floor.add(toward.scale(22)).add(side.scale(1.6)), 1.6, 3.2, 8);
            if (attacker.isAlive() && Combat.isTarget(attacker, caster)) {
                Combat.hurtMagic(caster, attacker, spec.dano());
                Stun.apply(attacker, spec.atordoarTicks());
                Combat.push(attacker, flyDir, spec.empurrao(), spec.subida());
                SkillSounds.play(level, spec.sons(), "acerto", 0, 0, onde -> at, List.of());
            }
        });
    }

    public static void clear() {
        READY.clear();
        WALLS.clear();
    }
}
