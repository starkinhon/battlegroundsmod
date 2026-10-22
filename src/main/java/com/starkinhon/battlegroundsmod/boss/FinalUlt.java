package com.starkinhon.battlegroundsmod.boss;

import com.starkinhon.battlegroundsmod.ModEffects;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.combat.Intangible;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.powers.uchiha.Meteoro;
import com.starkinhon.battlegroundsmod.skill.MeteoroSpec;
import com.starkinhon.battlegroundsmod.skill.SkillSpecs;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// The boss-only ult near death (madara.json "ultFinal"): everything stops, the camera zooms on its face and turns to
// the sky as the Meteoro appears, falling many times slower than the player's version so there is time to run, and
// hitting much harder, while everyone near is struck with fear (MEDO). The boss itself waits, still, until it lands.
final class FinalUlt {
    private FinalUlt() {
    }

    // Starts it and returns how long the boss must stay still.
    static int start(ServerLevel level, SoulBoss boss, BossSpec.UltFinal cfg) {
        LivingEntity target = boss.getTarget();
        Vec3 aim = target != null && target.isAlive() ? target.position() : boss.position().add(Combat.flatLook(boss).scale(15));
        // Face the target, so the meteor comes over its shoulder towards them.
        Vec3 to = aim.subtract(boss.position());
        float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90;
        boss.setYRot(yaw);
        boss.setYHeadRot(yaw);
        boss.setYBodyRot(yaw);
        boss.setXRot(0);
        boss.getNavigation().stop();

        MeteoroSpec spec = SkillSpecs.get(Meteoro.SKILL, MeteoroSpec.class, boss.powerData().awakened);
        int fallTicks = Math.max(1, (int) Math.round(spec.tempo().quedaTicks() * cfg.multQueda()));
        Meteoro.Fall plan = Meteoro.plan(boss, aim, fallTicks);
        Vec3 sky = plan.start().lerp(plan.end(), 0.1);
        int frozen = cfg.zoomTicks() + cfg.ceuTicks();

        Freeze.hold(boss, frozen + cfg.voltarTicks());
        Intangible.hold(boss, frozen);
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && player.distanceTo(boss) <= cfg.raio()) {
                Freeze.hold(player, frozen);
                Intangible.hold(player, frozen);
                PacketDistributor.sendToPlayer(player, new ModPayloads.BossCutscene(boss.getId(), cfg.zoomTicks(), cfg.ceuTicks(), cfg.voltarTicks(), sky));
            }
        }
        Fx.sound(level, boss.position(), SoundEvents.WITHER_SPAWN, 2f, 0.4f);
        Fx.sound(level, boss.position(), SoundEvents.BEACON_DEACTIVATE, 2f, 0.5f);
        Tasks.later(cfg.zoomTicks(), () -> {
            if (boss.isAlive()) {
                Meteoro.launch(boss, level, aim, fallTicks, cfg.multDano());
                // Fear until it lands: slow, heavy jumps, shaking screen and a racing heart.
                int fear = spec.tempo().preparoTicks() + fallTicks + 10;
                for (ServerPlayer player : level.players()) {
                    if (!player.isSpectator() && player.distanceTo(boss) <= cfg.raio()) {
                        player.addEffect(new MobEffectInstance(ModEffects.MEDO, fear, 0, false, false, true));
                    }
                }
            }
        });
        // The title shows once the HUD comes back.
        Tasks.later(frozen + cfg.voltarTicks(), () -> BossFx.title(level, boss, cfg.titulo(), cfg.subtitulo(), BossFx.color(boss.spec().intro().cor())));
        return cfg.zoomTicks() + spec.tempo().preparoTicks() + fallTicks + cfg.esperaDepoisTicks();
    }
}
