package com.starkinhon.battlegroundsmod.power.powers.uchiha;

import java.util.HashSet;
import java.util.Set;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Destructible;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.skill.SkillSpecs;
import com.starkinhon.battlegroundsmod.skill.SusanooCorteSpec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// Shift + C: the Susanoo's arm swings a sword and a huge blue slash flies out where the caster aims, cutting every
// enemy and tearing a strip through the blocks on its way. The arm and the slash are drawn on the clients (bracoEspada
// and corte in the vfx list); the shape swept here is the same crescent they draw.
public final class SusanooCorte {
    public static final String SKILL = "madara.susanoo_corte";
    // How far the slash's tips trail behind its middle, as a fraction of its width (same as the client's corte).
    private static final double BOW = 0.18;
    // The crescent is tilted like a sword swing by this many radians around its flight (same as the client's corte).
    static final double ROLL = 0.35;

    private SusanooCorte() {
    }

    public static boolean use(LivingEntity caster, ServerLevel level) {
        PowerData data = caster.getData(ModAttachments.POWER);
        boolean awakened = data.awakened;
        SusanooCorteSpec spec = SkillSpecs.get(SKILL, SusanooCorteSpec.class, awakened);
        int windUp = Math.max(0, spec.tempo().atrasoTicks());
        data.busyTicks = windUp + spec.tempo().duracaoTicks();
        caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, data.busyTicks, 2, false, false, false));
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), SKILL, awakened));
        Fx.effects(level, SKILL, awakened, "uso", caster, 0);
        Fx.sound(level, caster.position(), SoundEvents.BEACON_ACTIVATE, 1.5f, 0.6f);
        Fx.sound(level, caster.position(), SoundEvents.WARDEN_SONIC_CHARGE, 1.2f, 0.8f);
        int interrupts = data.interrupts;
        Tasks.later(windUp, () -> {
            // Cut off during the swing (grabbed, sealed): no slash.
            if (!caster.isAlive() || data.interrupts != interrupts) {
                return;
            }
            Vec3 dir = Vec3.directionFromRotation(caster.getXRot(), caster.getYRot());
            SusanooCorteSpec.Corte corte = spec.corte();
            Vec3 origin = caster.position().add(0, corte.subir(), 0).add(Combat.flatLook(caster).scale(corte.frente()));
            Fx.effects(level, SKILL, awakened, "corte", origin, dir);
            Fx.sound(level, origin, SoundEvents.PLAYER_ATTACK_SWEEP, 3f, 0.5f);
            Fx.sound(level, origin, SoundEvents.WARDEN_SONIC_BOOM, 2f, 0.8f);
            fly(caster, level, corte, origin, dir, awakened);
        });
        return true;
    }

    private static void fly(LivingEntity caster, ServerLevel level, SusanooCorteSpec.Corte corte, Vec3 origin, Vec3 dir, boolean awakened) {
        double speed = Math.max(0.5, corte.velocidade());
        double reach = corte.alcance();
        double half = corte.largura() / 2;
        double bow = corte.largura() * BOW;
        Vec3 side = dir.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 flatNormal = side.cross(dir).normalize();
        Vec3 sideAxis = side.scale(Math.cos(ROLL)).add(flatNormal.scale(Math.sin(ROLL))).normalize();
        Vec3 normal = sideAxis.cross(dir).normalize();
        boolean grief = corte.quebrarBlocos() && level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        Set<LivingEntity> hit = new HashSet<>();
        int ticks = (int) Math.ceil(reach / speed);
        // Erased in flight (the hakaizinho): the crescent stops cutting and the clients stop drawing it.
        Destructible.Attack attack = Destructible.track(level, caster, origin, half,
                () -> Destructible.sendErase(level, SKILL, origin, reach));
        Tasks.repeat(ticks, age -> {
            if (attack.gone()) {
                return;
            }
            if (age == ticks - 1) {
                attack.end();
            }
            double from = age * speed;
            double to = Math.min(reach, from + speed);
            // Enemies inside the strip the crescent swept this tick.
            Vec3 middle = origin.add(dir.scale((from + to) / 2));
            attack.move(middle);
            for (LivingEntity target : Combat.inRadius(level, middle, half + speed + bow, caster)) {
                Vec3 rel = Combat.center(target).subtract(origin);
                double across = rel.dot(sideAxis);
                double u = across / half;
                double along = rel.dot(dir) + bow * u * u;
                double up = Math.abs(rel.dot(normal));
                if (Math.abs(across) > half + 0.6 || along < from - 1 || along > to + 1 || up > corte.espessura() / 2 + target.getBbHeight() / 2
                        || !hit.add(target)) {
                    continue;
                }
                Combat.hurt(caster, target, corte.dano());
                Combat.push(target, dir, corte.empurrao(), corte.altura());
                Fx.effects(level, SKILL, awakened, "acerto", Combat.center(target), dir);
                Fx.sound(level, target.position(), SoundEvents.PLAYER_ATTACK_CRIT, 2f, 0.6f);
            }
            if (age % 4 == 0) {
                Fx.sound(level, middle, SoundEvents.PLAYER_ATTACK_SWEEP, 2f, 0.4f);
            }
            if (grief) {
                cut(level, origin, dir, sideAxis, normal, from, to, half, bow, corte.espessura() / 2);
            }
        });
    }

    // Empties the blocks in the crescent strip between from and to blocks along the flight. No drops, no neighbor updates.
    private static void cut(ServerLevel level, Vec3 origin, Vec3 dir, Vec3 side, Vec3 normal, double from, double to, double half, double bow,
            double thick) {
        Set<BlockPos> done = new HashSet<>();
        int dust = 0;
        for (double s = -half; s <= half; s += 0.5) {
            double u = s / half;
            double back = bow * u * u;
            for (double h = -thick; h <= thick; h += 0.5) {
                for (double a = from; a <= to; a += 0.5) {
                    BlockPos block = BlockPos.containing(origin.add(dir.scale(a - back)).add(side.scale(s)).add(normal.scale(h)));
                    if (!done.add(block)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(block);
                    if (state.isAir() || state.getDestroySpeed(level, block) < 0) {
                        continue;
                    }
                    if (dust++ % 6 == 0) {
                        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), block.getX() + 0.5, block.getY() + 0.5,
                                block.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.15);
                    }
                    level.setBlock(block, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
    }
}
