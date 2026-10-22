package com.starkinhon.battlegroundsmod.power.powers.uchiha;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.skill.MeteoroSpec;
import com.starkinhon.battlegroundsmod.skill.SkillSpecs;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// V (ult): Madara raises his arm and a giant meteor falls on the aimed spot. Everything comes from
// souls_skills/madara.meteoro.json; the look lives in MeteoroFx so it can move to a model or Photon later.
public final class Meteoro {
    public static final String SKILL = "madara.meteoro";
    // The vanilla explosion only digs the crater; damage and knockback are ours.
    private static final ExplosionDamageCalculator BLOCKS_ONLY = new ExplosionDamageCalculator() {
        @Override
        public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
            return false;
        }

        @Override
        public float getKnockbackMultiplier(Entity entity) {
            return 0;
        }
    };
    // Bigger vanilla explosions get very slow and the crater stops looking round.
    private static final float MAX_CRATER_POWER = 12;

    private Meteoro() {
    }

    // Path of the meteor's center, from the sky to the ground.
    record Fall(Vec3 start, Vec3 end, Vec3 dir, MeteoroSpec.Corpo corpo, int ticks) {
        Vec3 at(double age) {
            double k = Math.min(1, age / ticks);
            double a = corpo.aceleracao();
            return start.lerp(end, k * (1 - a) + k * k * a);
        }

        double speed(double age) {
            return at(age + 1).distanceTo(at(age));
        }

        // Where the (straight) path passes a given height.
        Vec3 crossing(double y) {
            double drop = start.y - end.y;
            double t = drop <= 0 ? 0 : Math.max(0, Math.min(1, (start.y - y) / drop));
            return start.lerp(end, t);
        }
    }

    public static boolean use(ServerPlayer player, ServerLevel level) {
        PowerData data = player.getData(ModAttachments.POWER);
        MeteoroSpec spec = SkillSpecs.get(SKILL, MeteoroSpec.class, data.awakened);
        int prep = spec.tempo().preparoTicks();
        int fallTicks = Math.max(1, spec.tempo().quedaTicks());
        Vec3 target = target(player, level, spec.mira());
        Fall fall = fall(player, target, spec.meteoro(), fallTicks);

        data.busyTicks = prep;
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, prep, 2, false, false, false));
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new ModPayloads.PlaySkillAnimation(player.getId(), SKILL, data.awakened));
        MeteoroFx.call(level, player);
        MeteoroClouds clouds = MeteoroClouds.form(level, spec.nuvens(), fall, target);
        Tasks.repeat(prep + fallTicks + 1, age -> {
            if (age < prep) {
                MeteoroFx.prepare(level, player, spec, age);
            } else if (age < prep + fallTicks) {
                MeteoroFx.fall(level, spec, fall, age - prep);
                if (clouds != null) {
                    clouds.pierce(fall.at(age - prep), spec.meteoro().raio());
                }
            }
            if (age < prep + fallTicks) {
                MeteoroFx.warning(level, spec, target, (age + 1) / (double) (prep + fallTicks), age);
            } else {
                // Once it is falling it lands, even if the caster died or swapped powers.
                impact(player, level, spec, target);
                if (clouds != null) {
                    clouds.fade();
                }
            }
        });
        return true;
    }

    private static void impact(ServerPlayer player, ServerLevel level, MeteoroSpec spec, Vec3 pos) {
        MeteoroSpec.Impacto impacto = spec.impacto();
        double radius = Math.max(1, impacto.raio());
        if (impacto.quebrarBlocos()) {
            float power = (float) Math.min(MAX_CRATER_POWER, spec.meteoro().raio() * 0.8);
            level.explode(player, null, BLOCKS_ONLY, pos.x, pos.y, pos.z, power, false, Level.ExplosionInteraction.MOB);
        }
        for (LivingEntity target : Combat.inRadius(level, pos, radius, player)) {
            double edge = Math.min(1, Combat.center(target).distanceTo(pos) / radius);
            Combat.hurt(player, target, (float) (impacto.danoCentro() + (impacto.danoBorda() - impacto.danoCentro()) * edge));
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

    private static Vec3 target(ServerPlayer player, ServerLevel level, MeteoroSpec.Mira mira) {
        Vec3 aimed = "frente".equals(mira.modo())
                ? player.position().add(Combat.flatLook(player).scale(mira.distanciaFrente())).add(0, 1, 0)
                : Combat.raycastPoint(player, mira.distanciaMax());
        return ground(level, aimed, player);
    }

    // First solid block under a point, so aiming at the sky still lands on the floor.
    private static Vec3 ground(ServerLevel level, Vec3 pos, Entity owner) {
        Vec3 from = pos.add(0, 0.5, 0);
        var hit = level.clip(new ClipContext(from, from.add(0, -128, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        return hit.getType() == HitResult.Type.MISS ? pos : hit.getLocation();
    }

    // The meteor comes from high up, tilted anguloQueda from vertical, from behind the caster (turned by giroOrigem).
    private static Fall fall(ServerPlayer player, Vec3 target, MeteoroSpec.Corpo corpo, int ticks) {
        double height = corpo.alturaInicio();
        double side = height * Math.tan(Math.toRadians(corpo.anguloQueda()));
        Vec3 back = Combat.flatLook(player).reverse().yRot((float) Math.toRadians(-corpo.giroOrigem()));
        Vec3 end = target.add(0, corpo.raio() * (1 - corpo.afundar()), 0);
        Vec3 start = end.add(back.scale(side)).add(0, height, 0);
        return new Fall(start, end, end.subtract(start).normalize(), corpo, ticks);
    }
}
