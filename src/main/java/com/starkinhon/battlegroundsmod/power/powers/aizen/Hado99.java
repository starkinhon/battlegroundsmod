// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.power.powers.aizen;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.combat.Ruin;
import com.starkinhon.battlegroundsmod.combat.RuinNet;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.PowerData;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// Hado 99, Goryutenmetsu: Shift+V while evolved, once a form (forja/aizen-kit, the goryu scene). Golden fissures run
// out over the ground where he looks and rocks float up; five dragons of violet-magenta light with blue ink burst from
// the ground round the target one after another after the chant and all arrive together (ARRIVE ticks): whoever is on their way is hit, and
// where they land the ground blows into a crater of radius 32 (17 deep) under a column of light 160 blocks tall.
public final class Hado99 {
    public static final String SKILL = "aizen.hado99";
    // the chant (CHANT ticks: the cracks spread) and then the dragons' flight; all land together at ARRIVE
    static final int CHANT = 40;
    static final int ARRIVE = CHANT + 48;
    static final double RANGE = 64;
    static final double R = 32;
    static final float CENTER = 260, EDGE = 90, DRAGON = 60;

    private Hado99() {
    }

    public static boolean use(LivingEntity caster, ServerLevel level, Runnable done) {
        PowerData data = caster.getData(ModAttachments.POWER);
        String power = data.power;
        Vec3 aim = Combat.raycastPoint(caster, RANGE);
        Vec3 target = AizenHit.ground(level, aim);
        double dist = Math.hypot(target.x - caster.getX(), target.z - caster.getZ());
        // never on himself: closer in, a smaller crater
        double r = Math.min(R, Math.max(10, dist * 0.8));
        data.busyTicks = ARRIVE + 6;
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), SKILL, data.awakened));
        AizenNet.send(level, AizenNet.HADO99, caster.getId(), caster.position(), target, (float) r, 0);
        Fx.sound(level, caster.position(), SoundEvents.ENDER_DRAGON_GROWL, 3f, 0.5f);
        Fx.sound(level, target, SoundEvents.BEACON_ACTIVATE, 3f, 0.5f);
        // The golden cracks run out where they will land.
        Ruin.fissures(level, target.x, target.z, 3, r * 1.5, 1.6, 4, 3, Ruin.opts().width(0.07).seed(99));
        RuinNet.tremor(level, target, 0.6, 20, r * 1.6);
        // the page's starts: 0, 0.16, 0.3, 0.42, 0.55 s after the chant
        int[] starts = {0, 3, 6, 8, 11};
        for (int i = 0; i < 5; i++) {
            int k = i;
            Tasks.later(CHANT + starts[i], () -> Fx.sound(level, caster.position(), SoundEvents.ENDER_DRAGON_SHOOT, 2f, 0.6f + 0.1f * k));
        }
        Tasks.add(age -> {
            PowerData now = caster.getData(ModAttachments.POWER);
            if (!caster.isAlive() || caster.isRemoved() || !power.equals(now.power)) {
                done.run();
                return true;
            }
            now.busyTicks = Math.max(now.busyTicks, 2);
            if (age < ARRIVE) {
                return false;
            }
            done.run();
            // the dragons burst from the ground in a ring round the crater and sweep in: whoever stands there, past the
            // blast's edge, is hit on their way
            for (LivingEntity t : Combat.inRadius(level, target, r * 1.35, caster)) {
                if (Math.hypot(t.getX() - target.x, t.getZ() - target.z) > r) {
                    Combat.hurtMagic(caster, t, DRAGON);
                    Stun.apply(t, 25);
                }
            }
            Fx.sound(level, target, SoundEvents.GENERIC_EXPLODE.value(), 5f, 0.4f);
            Fx.sound(level, target, SoundEvents.WARDEN_SONIC_BOOM, 4f, 0.5f);
            Fx.sound(level, target, SoundEvents.LIGHTNING_BOLT_THUNDER, 4f, 0.5f);
            AizenHit.blast(caster, level, target, r, CENTER, EDGE, 2.6, 1.2, 0);
            AizenHit.crater(level, target, r, r * 0.53, 20, 0.2, true, 0xFF7AE0);
            RuinNet.ring(level, target.add(0, 6, 0), r * 3.2, 4, true, 40, 0xF0C8FF);
            return true;
        });
        return true;
    }
}
