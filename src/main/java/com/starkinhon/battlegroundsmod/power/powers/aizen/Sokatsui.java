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
// The hand opens and a short burst of blue fire runs REACH blocks ahead: whoever is in it is burned and pushed, the
// flame licks a furrow in the ground and leaves a small blue crater where it ends. Then it comes loose from the hand
// and dies out slowly (the client draws that).
public final class Sokatsui {
    public static final String SKILL = "aizen.sokatsui";
    static final double REACH = 14;
    static final float DAMAGE = 36;
    static final int LICK = 7;

    private Sokatsui() {
    }

    public static boolean use(LivingEntity caster, ServerLevel level) {
        PowerData data = caster.getData(ModAttachments.POWER);
        data.busyTicks = 10;
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), SKILL, data.awakened));
        Vec3 hand = Combat.handPos(caster);
        Vec3 end = Combat.raycastPoint(caster, REACH);
        Vec3 dir = end.subtract(hand);
        dir = dir.lengthSqr() < 1.0E-4 ? caster.getLookAngle() : dir.normalize();
        double reach = Math.max(2, end.distanceTo(hand));
        AizenNet.send(level, AizenNet.SOKA, caster.getId(), hand, dir, (float) reach, 0);
        Fx.sound(level, hand, SoundEvents.BLAZE_SHOOT, 1.6f, 0.6f);
        Fx.sound(level, hand, SoundEvents.FIRECHARGE_USE, 1.4f, 0.7f);
        Vec3 d = dir;
        Tasks.later(LICK, () -> {
            if (!caster.isAlive()) {
                return;
            }
            for (LivingEntity target : Combat.alongLine(level, hand, hand.add(d.scale(reach)), 1.8, caster)) {
                Combat.hurtMagic(caster, target, DAMAGE);
                target.igniteForSeconds(2);
                Combat.push(target, d, 1.1, 0.35);
                Stun.apply(target, 25);
            }
            Vec3 flat = new Vec3(d.x, 0, d.z);
            if (flat.lengthSqr() > 1.0E-4) {
                flat = flat.normalize();
                Vec3 a = AizenHit.ground(level, caster.position().add(flat.scale(5)));
                Vec3 b = AizenHit.ground(level, caster.position().add(flat.scale(Math.max(6, reach - 1.5))));
                AizenHit.furrow(level, a, b, 1.1, 1.4, 4);
                com.starkinhon.battlegroundsmod.combat.Ruin.bowl(level, b.x, b.z, 3.5, 2.2, 4, 0,
                        com.starkinhon.battlegroundsmod.combat.Ruin.opts().rim(0.2).scorch(0.5));
                com.starkinhon.battlegroundsmod.combat.RuinNet.debris(level, b, 2.5, 10, 0.6, 18, 0.7);
            }
            Fx.sound(level, hand.add(d.scale(reach)), SoundEvents.GENERIC_EXPLODE.value(), 1.2f, 1.5f);
        });
        return true;
    }
}
