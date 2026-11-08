package com.starkinhon.battlegroundsmod.boss;

import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.combat.Intangible;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.network.ModPayloads;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// The Kokushibo boss's scene when he falls to 600 life (Stark, 2026-11-08: "uma cutscene pro boss quando ele trocar a
// lâmina"). Everyone near stops; the camera (souls_cutscenes/kokushibo.despertar.json, made to be opened and tuned in the
// Editor de Cutscenes) closes in on his six eyes, cuts away as the kimono tears, circles his hand while the flesh swells out of the sword and grows its three blades (the same scene as the
// player's H, client/outfit/SwordStretch), and pulls back for the blow: impact frame, a lilac shockwave that throws
// everyone back, the screen shaking. Then the title, and the fight goes on with the Awakened Moon. The screen effects are
// the "vfx" of souls_skills/kokushibo.despertar.json (editable in the Forja), one moment per beat. No lines inside the
// cutscene (Stark's rule): the one from the manga page he sent comes right after it.
final class KokushiboDespertar {
    static final String SKILL = "kokushibo.despertar";
    // The beats, in ticks from the start (they match the cutscene file).
    private static final int TEAR = 28;
    private static final int STRETCH = 30;
    private static final int IMPACT = 142;
    private static final int LENGTH = 172;
    private static final double RANGE = 48;
    private static final int LILAC = 0xC9A2FF;

    private KokushiboDespertar() {
    }

    // Starts it and returns how long the boss stays in it.
    static int start(ServerLevel level, KokushiboEntity boss) {
        LivingEntity target = boss.getTarget();
        if (target != null && target.isAlive()) {
            Vec3 to = target.position().subtract(boss.position());
            float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90;
            boss.setYRot(yaw);
            boss.setYHeadRot(yaw);
            boss.setYBodyRot(yaw);
        }
        boss.setXRot(0);
        boss.getNavigation().stop();
        Freeze.hold(boss, IMPACT);
        // The players near watch it: still and untouchable until the blow, each with the camera framed towards them.
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && player.distanceTo(boss) <= RANGE) {
                Freeze.hold(player, IMPACT);
                Intangible.hold(player, IMPACT);
                PacketDistributor.sendToPlayer(player, new ModPayloads.FaceCutscene(boss.getId(), SKILL, true));
            }
        }
        Fx.effects(level, SKILL, true, "inicio", Combat.center(boss), Combat.flatLook(boss));
        Fx.sound(level, boss.position(), SoundEvents.WARDEN_HEARTBEAT, 2f, 0.5f);
        Fx.sound(level, boss.position(), SoundEvents.BEACON_DEACTIVATE, 1.6f, 0.5f);

        Tasks.later(TEAR, () -> {
            if (!boss.isAlive()) {
                return;
            }
            boss.setLook(KokushiboEntity.LOOK_TORN);
            Fx.effects(level, SKILL, true, "rasgo", Combat.center(boss), Combat.flatLook(boss));
            Fx.sound(level, boss.position(), SoundEvents.WOOL_BREAK, 2f, 0.6f);
            Fx.sound(level, boss.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.6f, 0.5f);
        });
        Tasks.later(STRETCH, () -> {
            if (!boss.isAlive()) {
                return;
            }
            boss.setLook(KokushiboEntity.LOOK_AWAKENED);
            boss.stretch(level, true);
            Fx.effects(level, SKILL, true, "carne", Combat.center(boss), Combat.flatLook(boss));
            Fx.sound(level, boss.position(), SoundEvents.SLIME_BLOCK_BREAK, 1.8f, 0.5f);
            Fx.sound(level, boss.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 2f, 0.6f);
        });
        // The heart beats faster and faster while the flesh grows.
        for (int t = STRETCH + 12, gap = 22; t < IMPACT - 4; t += gap, gap = Math.max(7, gap - 3)) {
            float pitch = 0.5f + 0.5f * (t - STRETCH) / (float) (IMPACT - STRETCH);
            Tasks.later(t, () -> {
                if (boss.isAlive()) {
                    Fx.sound(level, boss.position(), SoundEvents.WARDEN_HEARTBEAT, 2.2f, pitch);
                }
            });
        }
        Tasks.later(IMPACT - 10, () -> {
            if (boss.isAlive()) {
                Fx.effects(level, SKILL, true, "carga", Combat.center(boss), Combat.flatLook(boss));
                Fx.sound(level, boss.position(), SoundEvents.WARDEN_SONIC_CHARGE, 2f, 0.7f);
            }
        });
        Tasks.later(IMPACT, () -> {
            if (!boss.isAlive()) {
                return;
            }
            Fx.effects(level, SKILL, true, "impacto", Combat.center(boss), Combat.flatLook(boss));
            Fx.sound(level, boss.position(), SoundEvents.WARDEN_SONIC_BOOM, 2.5f, 0.6f);
            Fx.sound(level, boss.position(), SoundEvents.LIGHTNING_BOLT_THUNDER, 2f, 0.6f);
            Fx.sound(level, boss.position(), SoundEvents.TRIDENT_RIPTIDE_3.value(), 2f, 0.6f);
            BossFx.shockwave(level, boss, LILAC, 14);
            for (LivingEntity near : Combat.inRadius(level, Combat.center(boss), 14, boss)) {
                Combat.pushFrom(near, boss.position(), 2.4, 0.7);
            }
        });
        Tasks.later(LENGTH - 2, () -> {
            boss.sceneOver();
            if (!boss.isAlive()) {
                return;
            }
            BossSpec.Fase fase = boss.spec().fase(1);
            BossFx.title(level, boss, fase.titulo(), fase.subtitulo(), LILAC);
            BossFalas.sayNow(boss, "fala.battlegroundsmod.kokushibo.despertar");
        });
        return LENGTH;
    }
}
