package com.starkinhon.battlegroundsmod.power.powers.uchiha;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.Area;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Cone;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Intangible;
import com.starkinhon.battlegroundsmod.combat.SkillSounds;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.PowerLogic;
import com.starkinhon.battlegroundsmod.skill.CutsceneSpec;
import com.starkinhon.battlegroundsmod.skill.MangekyoSpec;
import com.starkinhon.battlegroundsmod.skill.SkillSpecs;
import com.starkinhon.battlegroundsmod.skill.Som;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// Mangekyo: everyone in Madara's area is caught in a genjutsu. A short cutscene shows his face while he and the
// targets are frozen; the eyes flash after the zoom lands, and when it ends the targets get fatigue, blindness
// and their skills locked. Values come from souls_skills/madara.mangekyo.json (with a "despertado" block).
public final class Mangekyo {
    public static final String SKILL = "madara.mangekyo";

    // Used when the file has no "sons" list: the Forja's starting sounds.
    private static final List<Som> DEFAULT_SOUNDS = List.of(
            Som.of("Sharingan", "battlegroundsmod:sharingan", "uso", 0.7f, 1, "tela").delayed(10),
            Som.of("Maldição", "minecraft:entity.elder_guardian.curse", "particulas", 1.2f, 0.6f, "olhos"),
            Som.of("Brilho", "minecraft:block.beacon.power_select", "particulas", 1, 0.5f, "olhos"));

    private Mangekyo() {
    }

    public static boolean use(LivingEntity player, ServerLevel level) {
        PowerData data = player.getData(ModAttachments.POWER);
        MangekyoSpec spec = SkillSpecs.get(SKILL, MangekyoSpec.class, data.awakened);
        int delay = spec.tempo().atrasoTicks();
        CutsceneSpec cutscene = spec.cutscene();
        Area area = Area.of(spec.forma(), player);
        List<LivingEntity> targets = area.targets();
        // The eyes start trailing light (client side, see Aura).
        Fx.effects(level, SKILL, data.awakened, "olhos", player, 0);

        // Everyone involved stays frozen until both the wait and the cutscene are over.
        int frozen = cutscene.ativa() ? Math.max(delay, cutscene.duracaoTicks()) : delay;
        data.busyTicks = frozen;
        Freeze.hold(player, frozen);
        for (LivingEntity target : targets) {
            Freeze.hold(target, frozen);
        }
        if (cutscene.ativa() && cutscene.intangible()) {
            Intangible.hold(player, cutscene.duracaoTicks());
            for (LivingEntity target : targets) {
                Intangible.hold(target, cutscene.duracaoTicks());
            }
        }
        if (Cone.debug) {
            area.draw(Fx.dust(0xFFFFFF, 0.6f));
        }
        if (cutscene.ativa()) {
            Fx.effects(level, SKILL, data.awakened, "cutscene", player, cutscene.duracaoTicks());
            if (player instanceof ServerPlayer self) {
                startCutscene(player, self, data.awakened);
            }
            for (LivingEntity target : targets) {
                if (target instanceof ServerPlayer hitPlayer) {
                    startCutscene(player, hitPlayer, data.awakened);
                }
            }
        }
        sounds(level, player, spec, targets);
        Tasks.later(cutscene.particlesTick(), () -> {
            if (player.isAlive()) {
                UchihaFx.mangekyoEyes(level, player);
                Fx.effects(level, SKILL, data.awakened, "particulas", player.getEyePosition(), player.getLookAngle());
            }
        });
        Tasks.later(delay, () -> {
            for (LivingEntity target : targets) {
                if (target.isAlive() && !target.isRemoved()) {
                    stun(target, spec.efeitos());
                    UchihaFx.mangekyoHit(level, target);
                    // Ring over the head and the eye under the feet stay for as long as the stun does.
                    Fx.effects(level, SKILL, data.awakened, "stun", target, spec.efeitos().duracaoTicks());
                }
            }
        });
        return true;
    }

    // Moments: uso (on press), zoom (camera goes to the face), particulas (eyes flash), stun (it lands).
    private static void sounds(ServerLevel level, LivingEntity player, MangekyoSpec spec, List<LivingEntity> targets) {
        List<Som> sons = spec.sons() != null ? spec.sons() : DEFAULT_SOUNDS;
        CutsceneSpec cutscene = spec.cutscene();
        Function<String, Vec3> where = onde -> switch (onde == null ? "" : onde) {
            case "olhos" -> player.getEyePosition().add(player.getLookAngle().scale(0.3));
            case "alvo" -> targets.isEmpty() ? null : Combat.center(targets.get(0));
            default -> player.position().add(0, 1, 0);
        };
        // "tela" is heard by everyone who watches the cutscene: Madara and the players caught.
        List<ServerPlayer> screen = new ArrayList<>();
        if (player instanceof ServerPlayer self) {
            screen.add(self);
        }
        for (LivingEntity target : targets) {
            if (target instanceof ServerPlayer hitPlayer) {
                screen.add(hitPlayer);
            }
        }
        int delay = spec.tempo().atrasoTicks();
        SkillSounds.play(level, sons, "uso", 0, delay, where, screen);
        if (cutscene.ativa()) {
            SkillSounds.play(level, sons, "zoom", 0, cutscene.duracaoTicks(), where, screen);
        }
        SkillSounds.play(level, sons, "particulas", cutscene.particlesTick(), 0, where, screen);
        SkillSounds.play(level, sons, "stun", delay, spec.efeitos().duracaoTicks(), where, screen);
    }

    private static void stun(LivingEntity target, MangekyoSpec.Efeitos efeitos) {
        int duration = efeitos.duracaoTicks();
        effect(target, MobEffects.DIG_SLOWDOWN, duration, efeitos.fadiga());
        effect(target, MobEffects.BLINDNESS, duration, efeitos.cegueira());
        effect(target, MobEffects.MOVEMENT_SLOWDOWN, duration, efeitos.lentidao());
        Freeze.hold(target, efeitos.atordoarTicks());
        // Bosses carry power data too, so their skills get sealed the same way, and what they had going is cut off.
        if (efeitos.travarSkills() && target.hasData(ModAttachments.POWER)) {
            PowerLogic.seal(target, duration);
        }
    }

    // level 1 = effect level I. 0 or no duration skips the effect.
    private static void effect(LivingEntity target, Holder<MobEffect> effect, int duration, int level) {
        if (level > 0 && duration > 0) {
            target.addEffect(new MobEffectInstance(effect, duration, level - 1));
        }
    }

    // The client reads the cutscene timing from the same skill file.
    private static void startCutscene(LivingEntity caster, ServerPlayer viewer, boolean awakened) {
        PacketDistributor.sendToPlayer(viewer, new ModPayloads.FaceCutscene(caster.getId(), SKILL, awakened));
    }
}
