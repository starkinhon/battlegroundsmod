// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.power.powers.aizen;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.PowerData;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// Fragor, V again while evolved (forja/aizen-kit, the fragor scene). A star of thick spikes forms on his raised hand
// (FORM ticks, 0.95 s like the page) and flies in an arc to where he looks (FLIGHT ticks); there it detonates into a smooth sphere of gold
// and red light of radius R that carves a deep bowl into the ground (radius 22, 14 deep). Damage falls off from the
// middle to the sphere's edge. Close in, the sphere shrinks so it never swallows him.
public final class Fragor {
    public static final String SKILL = "aizen.fragor";
    static final int FORM = 19;
    static final int FLIGHT = 16;
    static final double RANGE = 48;
    static final double R = 24;
    static final float CENTER = 160, EDGE = 40;

    private Fragor() {
    }

    public static boolean use(LivingEntity caster, ServerLevel level) {
        PowerData data = caster.getData(ModAttachments.POWER);
        Vec3 hand = Combat.handPos(caster);
        Vec3 end = Combat.raycastPoint(caster, RANGE);
        double dist = end.distanceTo(caster.position());
        // never round himself: a near wall gets a smaller sphere
        double r = Math.min(R, Math.max(6, dist * 0.85));
        data.busyTicks = FORM + 4;
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), SKILL, data.awakened));
        AizenNet.send(level, AizenNet.FRAGOR, caster.getId(), hand, end, (float) r, 0);
        Fx.sound(level, hand, SoundEvents.BEACON_ACTIVATE, 1.4f, 1.6f);
        Fx.sound(level, hand, SoundEvents.AMETHYST_CLUSTER_BREAK, 1.6f, 0.6f);
        Tasks.later(FORM, () -> Fx.sound(level, hand, SoundEvents.WITHER_SHOOT, 1.6f, 0.7f));
        Tasks.later(FORM + FLIGHT, () -> {
            if (!caster.isAlive()) {
                return;
            }
            Fx.sound(level, end, SoundEvents.GENERIC_EXPLODE.value(), 4f, 0.5f);
            Fx.sound(level, end, SoundEvents.WARDEN_SONIC_BOOM, 3f, 0.6f);
            AizenHit.blast(caster, level, end, r, CENTER, EDGE, 2.2, 1.0, 0);
            Vec3 floor = AizenHit.ground(level, end);
            AizenHit.crater(level, floor, r * 0.92, r * 0.58, 14, 0.35, true, 0xFFD38A);
        });
        return true;
    }
}
