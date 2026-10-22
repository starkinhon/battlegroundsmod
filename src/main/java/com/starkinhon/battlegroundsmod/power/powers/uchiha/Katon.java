package com.starkinhon.battlegroundsmod.power.powers.uchiha;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.AshKill;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Cone;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.combat.SkillSounds;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.skill.SkillSpec;
import com.starkinhon.battlegroundsmod.skill.SkillSpecs;
import com.starkinhon.battlegroundsmod.skill.Som;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// Katon: a huge stream of fire from the mouth. The player can aim while breathing.
// Timing, shape and particles come from souls_skills/madara.katon.json (tuned in the Forja de Skills page),
// with its "despertado" block laid over them in awakened mode.
// Gameplay lives here, visuals in UchihaFx. Sounds come from the file's "sons" list (moments uso, fogo, fim).
public final class Katon {
    public static final String SKILL = "madara.katon";
    // Each target is burned at most once per interval, so the damage does not depend on the tick rate.
    private static final int HIT_INTERVAL = 5;
    private static final float DAMAGE = 1.5f;
    private static final int SLOW_TICKS = 30;
    // Used when the file has no "sons" list: the Forja's starting sounds.
    private static final List<Som> DEFAULT_SOUNDS = List.of(
            Som.of("Katon", "battlegroundsmod:katon", "fogo", 3, 1, "boca"),
            Som.of("Fogo crepitando", "minecraft:block.fire.ambient", "fogo", 2, 0.6f, "boca").repeating(8),
            Som.of("Blaze queimando", "minecraft:entity.blaze.burn", "fogo", 1.2f, 0.5f, "meio").repeating(8));

    private Katon() {
    }

    public static boolean use(LivingEntity player, ServerLevel level) {
        PowerData data = player.getData(ModAttachments.POWER);
        SkillSpec spec = SkillSpecs.get(SKILL, SkillSpec.class, data.awakened);
        SkillSpec.Tempo tempo = spec.tempo();
        String power = data.power;
        data.busyTicks = tempo.totalTicks();
        // The caster is rooted during the wind-up and while breathing fire, but can still turn to aim.
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, tempo.totalTicks(), 2, false, false, false));
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new ModPayloads.PlaySkillAnimation(player.getId(), SKILL, data.awakened));
        Map<LivingEntity, Integer> lastHit = new HashMap<>();
        List<Som> sons = spec.sons() != null ? spec.sons() : DEFAULT_SOUNDS;
        long start = level.getGameTime();
        Function<String, Vec3> where = onde -> {
            if ("usuario".equals(onde)) {
                return player.position().add(0, 1, 0);
            }
            Cone cone = cone(player, level, spec, (int) Math.max(0, level.getGameTime() - start - tempo.atrasoTicks()));
            double length = level.getGameTime() - start < tempo.atrasoTicks() ? 0 : cone.length();
            return "meio".equals(onde) ? cone.pointAt(length / 2) : "ponta".equals(onde) ? cone.pointAt(length) : cone.origin();
        };
        List<ServerPlayer> screen = player instanceof ServerPlayer self ? List.of(self) : List.of();
        int weak = tempo.weakTicks();
        int blast = tempo.atrasoTicks() + weak;
        SkillSounds.play(level, sons, "uso", 0, tempo.atrasoTicks(), where, screen);
        SkillSounds.play(level, sons, "fogo", tempo.atrasoTicks(), weak + tempo.duracaoTicks(), where, screen);
        SkillSounds.play(level, sons, "explosao", blast, tempo.duracaoTicks(), where, screen);
        SkillSounds.play(level, sons, "fim", tempo.totalTicks(), 0, where, screen);
        boolean awakened = data.awakened;
        Tasks.add(tick -> {
            if (!player.isAlive() || player.isRemoved() || !power.equals(player.getData(ModAttachments.POWER).power)) {
                return true;
            }
            int age = tick - tempo.atrasoTicks();
            if (age < 0) {
                return false;
            }
            // First it comes out weak, as if held back; after fracoTicks the real fire bursts out.
            boolean strong = age >= weak;
            int phaseAge = strong ? age - weak : age;
            Cone cone = cone(player, level, spec, phaseAge, strong ? 1 : weakSize(spec));
            if (age == 0) {
                UchihaFx.katonStart(level, spec, cone);
                Fx.effects(level, SKILL, awakened, "fogo", cone.origin(), cone.dir());
            }
            if (strong && phaseAge == 0) {
                Fx.effects(level, SKILL, awakened, "explosao", player, tempo.duracaoTicks());
            }
            if (strong) {
                UchihaFx.katonBlast(level, spec, cone, phaseAge);
                if (phaseAge % 3 == 0) {
                    UchihaFx.katonIgnite(level, cone);
                }
            } else {
                UchihaFx.katonWeak(level, spec, cone, phaseAge);
            }
            float damage = DAMAGE * (strong ? 1 : (float) weakDamage(spec));
            for (LivingEntity target : cone.targets()) {
                Integer last = lastHit.get(target);
                if (last == null || age - last >= HIT_INTERVAL) {
                    lastHit.put(target, age);
                    burn(player, target, cone, damage, awakened);
                }
            }
            if (Cone.debug && age % 2 == 0) {
                cone.draw(Fx.dust(0xFFFFFF, 0.6f));
            }
            return age >= weak + tempo.duracaoTicks() - 1;
        });
        return true;
    }

    private static double weakSize(SkillSpec spec) {
        return spec.fraco() == null ? 1 : spec.fraco().tamanho();
    }

    private static double weakDamage(SkillSpec spec) {
        return spec.fraco() == null ? 1 : spec.fraco().dano();
    }

    // The fire travels out over crescimentoTicks instead of appearing at full length.
    private static Cone cone(LivingEntity player, ServerLevel level, SkillSpec spec, int age) {
        return cone(player, level, spec, age, 1);
    }

    // scale shrinks the whole cone (the weak start).
    private static Cone cone(LivingEntity player, ServerLevel level, SkillSpec spec, int age, double scale) {
        SkillSpec.Forma forma = spec.forma();
        double full = forma.comprimento() * scale;
        double length = full * Math.min(1, (age + 1) / (double) Math.max(1, spec.tempo().crescimentoTicks()));
        // Minecraft pitch is negative when looking up, so tilting up subtracts.
        Vec3 dir = Vec3.directionFromRotation(player.getXRot() - (float) spec.direcao().inclinacao(),
                player.getYRot() + (float) spec.direcao().giro());
        return new Cone(level, player, mouth(player, spec.origem(), dir), dir, length, full, forma.raioInicio() * scale, forma.raioFim() * scale);
    }

    private static void burn(LivingEntity player, LivingEntity target, Cone cone, float damage, boolean awakened) {
        // Keep the target in the fire: drop the vanilla hit knockback and only nudge them along the stream.
        Vec3 motion = target.getDeltaMovement();
        AshKill.hurt(player, target, damage);
        if (target instanceof ServerPlayer burned) {
            Fx.effectsTo(burned, SKILL, awakened, "queimando", 0);
        }
        Vec3 nudge = cone.dir().scale(0.05);
        target.setDeltaMovement(motion.x + nudge.x, motion.y, motion.z + nudge.z);
        target.hurtMarked = true;
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SLOW_TICKS, 0));
        target.igniteForSeconds(2);
        UchihaFx.katonBurn(Combat.level(player), Combat.center(target));
    }

    // Measured from the eyes like in the Forja page: forward along the aim, straight up, and to the player's right.
    private static Vec3 mouth(LivingEntity player, SkillSpec.Origem origem, Vec3 dir) {
        Vec3 right = dir.cross(Math.abs(dir.y) > 0.99 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
        return player.getEyePosition().add(dir.scale(origem.frente())).add(0, origem.altura(), 0).add(right.scale(origem.lado()));
    }
}
