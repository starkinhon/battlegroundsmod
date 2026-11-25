// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.power.powers.aizen;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.PowerData;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// Hado 33, Sokatsui: Aizen's Shift+X (forja/aizen-kit, the soka scene), the poke in place of the old white lightning.
// The hand opens and a short burst of blue fire runs up to REACH blocks ahead, stopping on the first body it meets.
// While it pours (STREAM ticks) it follows the caster's aim and he can walk, like Madara's Katon (Stark 2026-11-22:
// "poderia ser mais móvel"): every tick the clients get the new aim (AizenNet.SOKA_AIM), and whoever the fire licks is
// burned and pushed, once. Where it first lands the blue burst stays in the world, and at the end the flame licks a
// furrow and leaves a small blue crater. Then it comes loose from the hand and dies out slowly (the client draws that).
public final class Sokatsui {
    public static final String SKILL = "aizen.sokatsui";
    static final double REACH = 14;
    static final float DAMAGE = 36;
    // the flame reaches out from tick 3 and stays on the hand until tick 11 (the page: 0.12 s to 0.55 s)
    static final int REACHES = 3, STREAM = 11;

    private Sokatsui() {
    }

    // Where the fire goes right now: from the hand toward the aim, stopping on the first body in the way.
    private static Vec3[] aim(LivingEntity caster, ServerLevel level) {
        Vec3 hand = Combat.handPos(caster);
        Vec3 end = Combat.raycastPoint(caster, REACH);
        Vec3 dir = end.subtract(hand);
        dir = dir.lengthSqr() < 1.0E-4 ? caster.getLookAngle() : dir.normalize();
        double reach = Math.max(2, end.distanceTo(hand));
        double first = reach;
        for (LivingEntity e : Combat.alongLine(level, hand, hand.add(dir.scale(reach)), 1.8, caster)) {
            first = Math.min(first, Combat.center(e).distanceTo(hand) + 0.6);
        }
        // x: where it stops (the first body), y: how far it could go (the first block); the client lets the flame splash a
        // little past the body, never through the wall
        return new Vec3[] {hand, dir, new Vec3(Math.max(2, first), reach, 0)};
    }

    public static boolean use(LivingEntity caster, ServerLevel level) {
        PowerData data = caster.getData(ModAttachments.POWER);
        data.busyTicks = 10;
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), SKILL, data.awakened));
        Vec3[] a0 = aim(caster, level);
        AizenNet.send(level, AizenNet.SOKA, caster.getId(), a0[0], a0[1], (float) a0[2].x, (float) a0[2].y);
        Fx.sound(level, a0[0], SoundEvents.BLAZE_SHOOT, 1.6f, 0.6f);
        Fx.sound(level, a0[0], SoundEvents.FIRECHARGE_USE, 1.4f, 0.7f);
        java.util.Set<LivingEntity> burned = new java.util.HashSet<>();
        Tasks.repeat(STREAM + 1, age -> {
            if (age < 1 || !caster.isAlive() || caster.level() != level) {
                return;
            }
            Vec3[] now = aim(caster, level);
            Vec3 hand = now[0], d = now[1];
            double reach = now[2].x;
            if (age <= STREAM) {
                AizenNet.send(level, AizenNet.SOKA_AIM, caster.getId(), hand, d, (float) reach, (float) now[2].y);
            }
            if (age < REACHES + 2) {
                return;
            }
            boolean hitNow = false;
            for (LivingEntity target : Combat.alongLine(level, hand, hand.add(d.scale(reach)), 1.8, caster)) {
                if (burned.add(target)) {
                    Combat.hurtMagic(caster, target, DAMAGE);
                    target.igniteForSeconds(2);
                    Combat.push(target, d, 1.1, 0.35);
                    Stun.apply(target, 25);
                    hitNow = true;
                }
            }
            if (hitNow) {
                Fx.sound(level, hand.add(d.scale(reach)), SoundEvents.GENERIC_EXPLODE.value(), 1.2f, 1.5f);
            }
            if (age == STREAM) {
                Vec3 flat = new Vec3(d.x, 0, d.z);
                if (flat.lengthSqr() > 1.0E-4) {
                    flat = flat.normalize();
                    Vec3 a = AizenHit.ground(level, caster.position().add(flat.scale(Math.min(5, reach * 0.4))));
                    Vec3 b = AizenHit.ground(level, caster.position().add(flat.scale(Math.max(3, reach - 1.5))));
                    AizenHit.furrow(level, a, b, 1.1, 1.4, 4);
                    com.starkinhon.battlegroundsmod.combat.Ruin.bowl(level, b.x, b.z, 3.5, 2.2, 4, 0,
                            com.starkinhon.battlegroundsmod.combat.Ruin.opts().rim(0.2).scorch(0.5));
                    com.starkinhon.battlegroundsmod.combat.RuinNet.debris(level, b, 2.5, 10, 0.6, 18, 0.7);
                }
                if (burned.isEmpty()) {
                    Fx.sound(level, hand.add(d.scale(reach)), SoundEvents.GENERIC_EXPLODE.value(), 1.2f, 1.5f);
                }
            }
        });
        return true;
    }
}
