package com.starkinhon.battlegroundsmod.power.powers.uchiha;

import java.util.List;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.Cone;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.PowerLogic;
import com.starkinhon.battlegroundsmod.skill.MangekyoSpec;
import com.starkinhon.battlegroundsmod.skill.SkillSpecs;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

// Mangekyo: everyone in front of Madara's eyes is caught in a genjutsu. While a short cutscene shows his face,
// the targets are picked; when it ends they get fatigue, blindness and their skills locked.
// Values come from souls_skills/madara.mangekyo.json (with a "despertado" block for awakened mode).
public final class Mangekyo {
    public static final String SKILL = "madara.mangekyo";

    private Mangekyo() {
    }

    public static boolean use(ServerPlayer player, ServerLevel level) {
        PowerData data = player.getData(ModAttachments.POWER);
        MangekyoSpec spec = SkillSpecs.get(SKILL, MangekyoSpec.class, data.awakened);
        MangekyoSpec.Tempo tempo = spec.tempo();
        MangekyoSpec.Efeitos efeitos = spec.efeitos();
        Cone cone = new Cone(level, player, player.getEyePosition(), player.getLookAngle(),
                spec.forma().comprimento(), spec.forma().comprimento(), spec.forma().raioInicio(), spec.forma().raioFim());
        List<LivingEntity> targets = cone.targets();

        data.busyTicks = tempo.atrasoTicks();
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, tempo.atrasoTicks(), 2, false, false, false));
        UchihaFx.mangekyoStart(level, player);
        if (Cone.debug) {
            cone.draw(Fx.dust(0xFFFFFF, 0.6f));
        }
        if (spec.cutscene().ativa()) {
            startCutscene(player, spec.cutscene());
            for (LivingEntity target : targets) {
                if (target instanceof ServerPlayer hitPlayer) {
                    startCutscene(player, spec.cutscene(), hitPlayer);
                }
            }
        }
        Tasks.later(tempo.atrasoTicks(), () -> {
            for (LivingEntity target : targets) {
                if (target.isAlive() && !target.isRemoved()) {
                    stun(target, efeitos);
                    UchihaFx.mangekyoHit(level, target);
                }
            }
        });
        return true;
    }

    private static void stun(LivingEntity target, MangekyoSpec.Efeitos efeitos) {
        int duration = efeitos.duracaoTicks();
        effect(target, MobEffects.DIG_SLOWDOWN, duration, efeitos.fadiga());
        effect(target, MobEffects.BLINDNESS, duration, efeitos.cegueira());
        effect(target, MobEffects.MOVEMENT_SLOWDOWN, duration, efeitos.lentidao());
        // Slowness X is a full stop for walking.
        effect(target, MobEffects.MOVEMENT_SLOWDOWN, efeitos.atordoarTicks(), efeitos.atordoarTicks() > 0 ? 10 : 0);
        if (efeitos.travarSkills() && target instanceof ServerPlayer hitPlayer) {
            PowerData data = hitPlayer.getData(ModAttachments.POWER);
            for (int i = 0; i < data.cooldowns.length; i++) {
                data.cooldowns[i] = Math.max(data.cooldowns[i], duration);
            }
            PowerLogic.sync(hitPlayer);
        }
    }

    // level 1 = effect level I. 0 or no duration skips the effect.
    private static void effect(LivingEntity target, Holder<MobEffect> effect, int duration, int level) {
        if (level > 0 && duration > 0) {
            target.addEffect(new MobEffectInstance(effect, duration, level - 1));
        }
    }

    private static void startCutscene(ServerPlayer caster, MangekyoSpec.Cutscene cutscene) {
        startCutscene(caster, cutscene, caster);
    }

    private static void startCutscene(ServerPlayer caster, MangekyoSpec.Cutscene cutscene, ServerPlayer viewer) {
        PacketDistributor.sendToPlayer(viewer, new ModPayloads.FaceCutscene(caster.getId(), cutscene.duracaoTicks(),
                (float) cutscene.distanciaInicio(), (float) cutscene.distanciaFim(), (float) cutscene.altura(), cutscene.esconderHud()));
    }
}
