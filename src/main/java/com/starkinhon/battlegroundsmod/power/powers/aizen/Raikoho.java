// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.power.powers.aizen;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Destructible;
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

// Hado 63, Raikoho: Aizen's C (forja/aizen-kit, the raiko scene). Three golden arcs gather on his hand for CHARGE ticks,
// then a thick crooked bolt runs from the hand to where he looks (up to RANGE) in a breath: whoever is on the line is
// hit, and where it lands it blows up (a golden dome, a crater of radius 10, depth 6) and tears a furrow back along the
// bolt from 12 blocks out. Divine scale, paid by the cooldown (35 s).
public final class Raikoho {
    public static final String SKILL = "aizen.raikoho";
    static final int CHARGE = 14;
    static final double RANGE = 44;
    static final float BEAM_DAMAGE = 60, BLAST_CENTER = 120, BLAST_EDGE = 50;
    static final double BLAST_R = 10;

    private Raikoho() {
    }

    public static boolean use(LivingEntity caster, ServerLevel level) {
        PowerData data = caster.getData(ModAttachments.POWER);
        String power = data.power;
        int interrupts = data.interrupts;
        data.busyTicks = CHARGE + 6;
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), SKILL, data.awakened));
        AizenNet.send(level, AizenNet.RAIKO, caster.getId(), Combat.handPos(caster), Combat.raycastPoint(caster, RANGE), 0, 0);
        Fx.sound(level, Combat.handPos(caster), SoundEvents.BEACON_POWER_SELECT, 1.2f, 1.8f);
        Fx.sound(level, Combat.handPos(caster), SoundEvents.TRIDENT_THUNDER.value(), 0.5f, 2f);
        Tasks.add(age -> {
            PowerData now = caster.getData(ModAttachments.POWER);
            if (!caster.isAlive() || caster.isRemoved() || !power.equals(now.power) || now.interrupts != interrupts) {
                return true;
            }
            if (age < CHARGE) {
                return false;
            }
            fire(caster, level);
            return true;
        });
        return true;
    }

    private static void fire(LivingEntity caster, ServerLevel level) {
        Vec3 hand = Combat.handPos(caster);
        Vec3 end = Combat.raycastPoint(caster, RANGE);
        AizenNet.send(level, AizenNet.RAIKO, caster.getId(), hand, end, 1, 0);
        Fx.sound(level, hand, SoundEvents.LIGHTNING_BOLT_THUNDER, 2.2f, 1.4f);
        Fx.sound(level, end, SoundEvents.GENERIC_EXPLODE.value(), 3f, 0.7f);
        Fx.sound(level, end, SoundEvents.LIGHTNING_BOLT_IMPACT, 3f, 0.8f);
        // The bolt wipes out lesser attacks on its way (the clash keeps the big ones).
        Destructible.eraseAlong(level, hand, end, 1.5, caster);
        for (LivingEntity target : Combat.alongLine(level, hand, end, 1.6, caster)) {
            Combat.hurtMagic(caster, target, BEAM_DAMAGE);
            Stun.apply(target, 25);
        }
        Vec3 floor = AizenHit.ground(level, end);
        AizenHit.blast(caster, level, end, BLAST_R, BLAST_CENTER, BLAST_EDGE, 1.6, 0.7, 0);
        AizenHit.crater(level, floor, BLAST_R, 6, 8, 0.25, true, 0xFFE6A0);
        Vec3 flat = new Vec3(end.x - hand.x, 0, end.z - hand.z);
        double len = flat.length();
        if (len > 16) {
            Vec3 dir = flat.scale(1 / len);
            Vec3 from = AizenHit.ground(level, caster.position().add(dir.scale(12)));
            AizenHit.furrow(level, from, floor, 2.4, 2.2, 6);
        }
    }
}
