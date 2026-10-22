package com.starkinhon.battlegroundsmod.power.powers.uchiha;

import java.util.ArrayList;
import java.util.List;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.skill.MeteoroSpec;
import com.starkinhon.battlegroundsmod.skill.SkillSpecs;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// V (ult): Madara raises his arm and a giant meteor falls on the aimed spot. Everything comes from
// souls_skills/madara.meteoro.json; the look lives in MeteoroFx.
public final class Meteoro {
    public static final String SKILL = "madara.meteoro";
    // How many ticks the crater takes to open, from the middle out (spread out so the server doesn't freeze).
    private static final int CRATER_TICKS = 6;
    // Players this far from the target still see the meteor fall.
    private static final double VIEW_DISTANCE = 512;

    private Meteoro() {
    }

    // Path of the meteor's center, from the sky to the ground.
    public record Fall(Vec3 start, Vec3 end, Vec3 dir, MeteoroSpec.Corpo corpo, int ticks) {
        public Vec3 at(double age) {
            double k = Math.min(1, age / ticks);
            double a = corpo.aceleracao();
            return start.lerp(end, k * (1 - a) + k * k * a);
        }

        double speed(double age) {
            return at(age + 1).distanceTo(at(age));
        }
    }

    public static boolean use(LivingEntity player, ServerLevel level) {
        MeteoroSpec spec = SkillSpecs.get(SKILL, MeteoroSpec.class, player.getData(ModAttachments.POWER).awakened);
        launch(player, level, target(player, level, spec.mira()), spec.tempo().quedaTicks(), 1f);
        return true;
    }

    // Where the meteor would fall from and to if cast now on target over fallTicks (bosses use it to aim their camera).
    public static Fall plan(LivingEntity caster, Vec3 target, int fallTicks) {
        MeteoroSpec spec = SkillSpecs.get(SKILL, MeteoroSpec.class, caster.getData(ModAttachments.POWER).awakened);
        return fall(caster, ground(Combat.level(caster), target, caster), spec.meteoro(), Math.max(1, fallTicks));
    }

    // The meteor itself: prep with the raised arm, then the fall over fallTicks, then the impact with its damage
    // multiplied by damageMult. Players get it from use(); bosses call it with their own timing and target.
    public static Fall launch(LivingEntity player, ServerLevel level, Vec3 aimed, int quedaTicks, float damageMult) {
        PowerData data = player.getData(ModAttachments.POWER);
        MeteoroSpec spec = SkillSpecs.get(SKILL, MeteoroSpec.class, data.awakened);
        int prep = spec.tempo().preparoTicks();
        int fallTicks = Math.max(1, quedaTicks);
        Vec3 target = ground(level, aimed, player);
        Fall fall = fall(player, target, spec.meteoro(), fallTicks);

        data.busyTicks = prep;
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, prep, 2, false, false, false));
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new ModPayloads.PlaySkillAnimation(player.getId(), SKILL, data.awakened));
        MeteoroFx.call(level, player);
        // The ball itself is drawn on the clients (client/fx/MeteorRenderer), following the same path.
        for (ServerPlayer viewer : level.players()) {
            if (viewer.distanceToSqr(target) < VIEW_DISTANCE * VIEW_DISTANCE) {
                PacketDistributor.sendToPlayer(viewer, new ModPayloads.MeteorFall(SKILL, data.awakened, fall.start(), fall.end(), prep, fallTicks));
            }
        }
        Tasks.repeat(prep + fallTicks + 1, age -> {
            if (age < prep + 10) {
                MeteoroFx.prepare(level, player, spec);
            }
            if (age >= prep && age < prep + fallTicks) {
                MeteoroFx.fall(level, spec, fall, age - prep);
            }
            if (age >= prep + fallTicks) {
                // Once it is falling it lands, even if the caster died or swapped powers.
                impact(player, level, spec, target, damageMult);
            }
        });
        return fall;
    }

    private static void impact(LivingEntity player, ServerLevel level, MeteoroSpec spec, Vec3 pos, float damageMult) {
        MeteoroSpec.Impacto impacto = spec.impacto();
        double radius = Math.max(1, impacto.raio());
        if (impacto.quebrarBlocos() && level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            crater(level, pos, impacto.raioCratera(), impacto.profundidadeCratera());
        }
        for (LivingEntity target : Combat.inRadius(level, pos, radius, player)) {
            double edge = Math.min(1, Combat.center(target).distanceTo(pos) / radius);
            Combat.hurt(player, target, (float) (impacto.danoCentro() + (impacto.danoBorda() - impacto.danoCentro()) * edge) * damageMult);
            Combat.pushFrom(target, pos, impacto.empurrao(), impacto.alturaEmpurrao());
        }
        if (impacto.tremerTela() > 0) {
            for (ServerPlayer viewer : level.players()) {
                double distance = viewer.position().distanceTo(pos);
                if (distance < radius * 2) {
                    float strength = (float) (impacto.tremerTela() * (1 - distance / (radius * 2)));
                    PacketDistributor.sendToPlayer(viewer, new ModPayloads.ScreenShake(strength, 30));
                }
            }
        }
        MeteoroFx.impact(level, spec, pos);
    }

    // Digs a round bowl: radius across, depth down (and as high up, for trees and walls in the way), with a ragged
    // edge. Unbreakable blocks stay. No drops and no neighbor updates, so it stays fast even when it is huge.
    private static void crater(ServerLevel level, Vec3 pos, double radius, double depth) {
        if (radius <= 0 || depth <= 0) {
            return;
        }
        BlockPos center = BlockPos.containing(pos);
        int r = (int) Math.ceil(radius * 1.1);
        int d = (int) Math.ceil(depth * 1.1);
        List<List<BlockPos>> waves = new ArrayList<>();
        for (int i = 0; i < CRATER_TICKS; i++) {
            waves.add(new ArrayList<>());
        }
        RandomSource random = level.getRandom();
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                double across = (x * x + z * z) / (radius * radius);
                if (across > 1.21) {
                    continue;
                }
                for (int y = -d; y <= d; y++) {
                    double shape = across + y * y / (depth * depth);
                    if (shape > 0.85 + random.nextDouble() * 0.3) {
                        continue;
                    }
                    int wave = (int) Math.min(CRATER_TICKS - 1, Math.sqrt(across) * CRATER_TICKS);
                    waves.get(wave).add(center.offset(x, y, z));
                }
            }
        }
        for (int i = 0; i < CRATER_TICKS; i++) {
            List<BlockPos> wave = waves.get(i);
            Tasks.later(i, () -> {
                for (BlockPos block : wave) {
                    BlockState state = level.getBlockState(block);
                    if (!state.isAir() && state.getDestroySpeed(level, block) >= 0) {
                        level.setBlock(block, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
            });
        }
    }

    private static Vec3 target(LivingEntity player, ServerLevel level, MeteoroSpec.Mira mira) {
        return "frente".equals(mira.modo())
                ? player.position().add(Combat.flatLook(player).scale(mira.distanciaFrente())).add(0, 1, 0)
                : Combat.raycastPoint(player, mira.distanciaMax());
    }

    // First solid block under a point, so aiming at the sky still lands on the floor.
    private static Vec3 ground(ServerLevel level, Vec3 pos, Entity owner) {
        Vec3 from = pos.add(0, 0.5, 0);
        var hit = level.clip(new ClipContext(from, from.add(0, -128, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        return hit.getType() == HitResult.Type.MISS ? pos : hit.getLocation();
    }

    // The meteor comes from high up, tilted anguloQueda from vertical, from behind the caster (turned by giroOrigem).
    private static Fall fall(LivingEntity player, Vec3 target, MeteoroSpec.Corpo corpo, int ticks) {
        double height = corpo.alturaInicio();
        double side = height * Math.tan(Math.toRadians(corpo.anguloQueda()));
        Vec3 back = Combat.flatLook(player).reverse().yRot((float) Math.toRadians(-corpo.giroOrigem()));
        Vec3 end = target.add(0, corpo.raio() * (1 - corpo.afundar()), 0);
        Vec3 start = end.add(back.scale(side)).add(0, height, 0);
        return new Fall(start, end, end.subtract(start).normalize(), corpo, ticks);
    }
}
