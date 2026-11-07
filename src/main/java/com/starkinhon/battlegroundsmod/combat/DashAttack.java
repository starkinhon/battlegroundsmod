package com.starkinhon.battlegroundsmod.combat;

import java.util.Comparator;
import java.util.List;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.movement.Movement;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.skill.ComboSpec;
import com.starkinhon.battlegroundsmod.skill.DashAtaqueSpec;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// The Dash Attack, the first base attack: a fast slide towards the aim that follows the mouse, sticks to whoever the
// fighter is fighting when it gets close and lands a combo hit on them, so the combo goes on from there.
// Values in souls_skills/padrao.dash_ataque.json (DashAtaqueSpec).
public final class DashAttack {
    public static final String SKILL = "padrao.dash_ataque";

    private DashAttack() {
    }

    // Built on each use so the cooldown follows the file.
    public static Ability ability() {
        return new Ability(DashAtaqueSpec.get().recargaTicks(), 0, DashAttack::use);
    }

    private static boolean use(LivingEntity attacker, ServerLevel level) {
        if (!Movement.free(attacker)) {
            return false;
        }
        DashAtaqueSpec spec = DashAtaqueSpec.get();
        PowerData data = attacker.getData(ModAttachments.POWER);
        int interrupts = data.interrupts;
        int ticks = Math.max(1, spec.duracaoMaxTicks());
        data.busyTicks = ticks + 1;
        Vec3[] dir = {Combat.flatLook(attacker)};
        boolean sword = SwordArts.held(attacker);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(attacker, new ModPayloads.PlaySkillAnimation(attacker.getId(),
                sword ? SwordArts.DASH_RUN : SKILL, false));
        if (sword) {
            SwordArts.color(attacker);
        }
        SkillSounds.play(level, spec.sons(), "uso", 0, ticks, onde -> attacker.position(), List.of());
        Fx.effects(level, SKILL, false, "uso", attacker.position().add(0, 1, 0), dir[0]);
        Tasks.add(age -> {
            if (!attacker.isAlive() || data.interrupts != interrupts || Freeze.isFrozen(attacker) || !Stun.canAttack(attacker)) {
                stop(attacker, data, false);
                return true;
            }
            LivingEntity target = latchTarget(attacker, data, spec);
            if (target != null) {
                stick(attacker, target, spec);
                return true;
            }
            if (age >= ticks) {
                stop(attacker, data, true);
                return true;
            }
            // Steers towards the aim a bit each tick, so the slide bends smoothly.
            Vec3 want = Combat.flatLook(attacker);
            Vec3 turned = dir[0].lerp(want, Mth.clamp(spec.curva(), 0, 1));
            if (turned.lengthSqr() > 1.0E-4) {
                dir[0] = turned.normalize();
            }
            Vec3 velocity = dir[0].scale(spec.velocidade());
            attacker.setDeltaMovement(velocity.x, Math.min(attacker.getDeltaMovement().y, 0.1), velocity.z);
            attacker.hurtMarked = true;
            attacker.resetFallDistance();
            trail(level, attacker, dir[0], spec, age);
            return false;
        });
        return true;
    }

    // Whoever the fighter fights, from a bit further away; else the closest one right in front.
    private static LivingEntity latchTarget(LivingEntity attacker, PowerData data, DashAtaqueSpec spec) {
        if (data.foughtId >= 0 && attacker.level().getEntity(data.foughtId) instanceof LivingEntity fought && Combat.isTarget(fought, attacker)
                && fought.distanceTo(attacker) <= spec.raioGrudarAlvo() && attacker.hasLineOfSight(fought)) {
            return fought;
        }
        Vec3 look = Combat.flatLook(attacker);
        return Combat.inRadius(Combat.level(attacker), Combat.center(attacker), spec.raioGrudar() + 1, attacker).stream()
                .filter(e -> e.distanceTo(attacker) <= spec.raioGrudar())
                .filter(e -> {
                    Vec3 to = e.position().subtract(attacker.position());
                    Vec3 flat = new Vec3(to.x, 0, to.z);
                    return flat.lengthSqr() < 0.25 || flat.normalize().dot(look) > 0.2;
                })
                .filter(attacker::hasLineOfSight)
                .min(Comparator.comparingDouble(e -> e.distanceTo(attacker)))
                .orElse(null);
    }

    // Snaps in front of the target, facing it, and lands the hit.
    private static void stick(LivingEntity attacker, LivingEntity target, DashAtaqueSpec spec) {
        if (SwordArts.held(attacker)) {
            SwordArts.dashCut(attacker, target, spec);
            return;
        }
        PowerData data = attacker.getData(ModAttachments.POWER);
        data.busyTicks = 0;
        Vec3 away = attacker.position().subtract(target.position());
        Vec3 flat = new Vec3(away.x, 0, away.z);
        flat = flat.lengthSqr() < 1.0E-4 ? Combat.flatLook(target) : flat.normalize();
        Vec3 spot = target.position().add(flat.scale(target.getBbWidth() * 0.5 + spec.distanciaGrudar()));
        Vec3 look = Combat.center(target).subtract(spot.add(0, attacker.getEyeHeight(), 0));
        float yRot = (float) (Mth.atan2(-look.x, look.z) * Mth.RAD_TO_DEG);
        float xRot = (float) (-Mth.atan2(look.y, Math.sqrt(look.x * look.x + look.z * look.z)) * Mth.RAD_TO_DEG);
        boolean room = attacker.level().noCollision(attacker, attacker.getBoundingBox().move(spot.subtract(attacker.position())));
        if (attacker instanceof ServerPlayer player) {
            if (room) {
                player.connection.teleport(spot.x, spot.y, spot.z, yRot, xRot);
            }
        } else {
            if (room) {
                attacker.moveTo(spot.x, spot.y, spot.z, yRot, xRot);
            }
            attacker.setYHeadRot(yRot);
        }
        attacker.setDeltaMovement(Vec3.ZERO);
        attacker.hurtMarked = true;

        ComboSpec combo = ComboSpec.get();
        int step = Math.floorMod(spec.golpeDoCombo(), combo.golpes().size());
        ComboSpec.Golpe base = combo.golpe(step);
        DashAtaqueSpec.Golpe hit = spec.golpe();
        ComboSpec.Golpe golpe = new ComboSpec.Golpe(base.nome(), base.dano() * hit.multiplicadorDano(), hit.empurrao(), hit.subida(), 0,
                hit.stunTicks(), hit.hitstopTicks(), hit.tremor(), base.recuperacaoTicks(), "dash", base.tempo(), base.animacao());
        Melee.strike(attacker, target, step, golpe, () -> {
            ServerLevel level = Combat.level(attacker);
            Vec3 at = Combat.center(target);
            SkillSounds.play(level, spec.sons(), "golpe", 0, 0, onde -> "alvo".equals(onde) ? at : attacker.position(), List.of());
            Fx.effects(level, SKILL, false, "golpe", at, Combat.flatLook(attacker));
        });
    }

    private static void stop(LivingEntity attacker, PowerData data, boolean slowDown) {
        data.busyTicks = 0;
        if (slowDown && attacker.isAlive()) {
            Vec3 now = attacker.getDeltaMovement();
            attacker.setDeltaMovement(now.x * 0.3, now.y, now.z * 0.3);
            attacker.hurtMarked = true;
        }
    }

    // The slide's look (combat/PunchFx TRAIL, every tick): a vapor cone tinted with the soul in front of the body, soul
    // flames behind it and dust at the feet; plus the file's "rastro" vfx every few ticks.
    private static void trail(ServerLevel level, LivingEntity attacker, Vec3 dir, DashAtaqueSpec spec, int age) {
        PunchFx.follow(level, PunchFx.TRAIL, attacker, dir, Melee.fistColor(attacker), 0);
        DashAtaqueSpec.Rastro rastro = spec.rastro();
        if (rastro.rastroACadaTicks() > 0 && age % rastro.rastroACadaTicks() == 0) {
            Fx.effects(level, SKILL, false, "rastro", Combat.center(attacker), dir);
        }
    }
}
