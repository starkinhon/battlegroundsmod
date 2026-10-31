package com.starkinhon.battlegroundsmod.power.powers.bills;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.ModEffects;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Destructible;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.combat.Intangible;
import com.starkinhon.battlegroundsmod.combat.SkillSounds;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.skill.DestructionTouchSpec;
import com.starkinhon.battlegroundsmod.skill.HakaiSpec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// V: Destruction Touch (bills.destruction_touch.json, see DestructionTouchSpec). Bills rises over the ground, lets a
// tiny white ball drop from his finger, and where it lands the ground glows, cracks, shoots up pillars of light and
// then goes off in a giant pink plasma explosion that pulverizes everything around. All the light is drawn from the
// skill's vfx list (client/fx/FxWorld: bolinha, rachaduras, pilares, plasma).
public final class DestructionTouch {
    public static final String SKILL = DestructionTouchSpec.SKILL;

    private DestructionTouch() {
    }

    public static boolean use(LivingEntity caster, ServerLevel level) {
        PowerData data = caster.getData(ModAttachments.POWER);
        boolean awakened = data.awakened;
        DestructionTouchSpec spec = DestructionTouchSpec.get(awakened);
        DestructionTouchSpec.Voo voo = spec.voo();
        LivingEntity target = Combat.raycastTarget(caster, spec.alvo().alcance());
        if (target == null && data.foughtId >= 0 && level.getEntity(data.foughtId) instanceof LivingEntity fought && Combat.isTarget(fought, caster)
                && fought.distanceTo(caster) <= spec.alvo().alcance()) {
            target = fought;
        }
        LivingEntity locked = target;
        int rise = Math.max(1, voo.subirTicks());
        int drop = spec.dropTick();
        double groundY = ground(level, caster.position(), caster);
        double topY = Math.max(caster.getY(), groundY + voo.altura());
        String power = data.power;
        int interrupts = data.interrupts;

        // Busy until the ball is out; after that he only hangs there watching, and can be knocked down.
        data.busyTicks = drop + 4;
        data.noFallTicks = Math.max(data.noFallTicks, drop + 400);
        Runnable unshield = Intangible.shield(caster, drop + 400);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), SKILL, awakened));
        List<ServerPlayer> screen = new ArrayList<>();
        if (caster instanceof ServerPlayer player) {
            screen.add(player);
        }
        Function<String, Vec3> where = onde -> caster.position().add(0, 1, 0);
        SkillSounds.play(level, spec.sons(), "uso", 0, drop, where, screen);
        Fx.effects(level, SKILL, awakened, "uso", caster.position(), new Vec3(0, 1, 0));
        caster.setNoGravity(true);

        Tasks.add(age -> {
            PowerData now = caster.getData(ModAttachments.POWER);
            boolean gone = !caster.isAlive() || caster.isRemoved() || !power.equals(now.power);
            if (gone || knockedOut(caster, now, interrupts, spec.cancelarStunTicks())) {
                caster.setNoGravity(false);
                unshield.run();
                if (!gone) {
                    Fx.effects(level, SKILL, awakened, "cancelar", caster.position().add(0, 1, 0), Vec3.ZERO);
                    SkillSounds.play(level, spec.sons(), "cancelar", 0, 0, where, screen);
                    PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), "", false));
                }
                return true;
            }
            now.busyTicks = Math.max(now.busyTicks, 2);
            caster.fallDistance = 0;
            Vec3 motion = caster.getDeltaMovement();
            // Up to the height, slowing down as he gets there, then he just hangs there.
            double dy = age < rise ? Mth.clamp((topY - caster.getY()) * 0.3, 0, 1.4) : 0;
            caster.setDeltaMovement(motion.x * 0.5, dy, motion.z * 0.5);
            caster.hurtMarked = true;
            if (age < drop) {
                return false;
            }
            letGo(caster, level, spec, locked, awakened, screen, unshield);
            return true;
        });
        return true;
    }

    // The ball leaves his finger: he stays up until it is all over, then glides down.
    private static void letGo(LivingEntity caster, ServerLevel level, DestructionTouchSpec spec, LivingEntity target, boolean awakened,
            List<ServerPlayer> screen, Runnable unshield) {
        DestructionTouchSpec.Voo voo = spec.voo();
        DestructionTouchSpec.Impacto impacto = spec.impacto();
        DestructionTouchSpec.Bola bola = spec.bola();
        Vec3 start = fingertip(caster);
        Vec3 below = target != null && target.isAlive() ? target.position() : caster.position();
        Vec3 land = new Vec3(below.x, ground(level, below.add(0, 1, 0), caster), below.z);
        if (start.y - land.y > bola.alturaMaxima()) {
            land = new Vec3(land.x, start.y - bola.alturaMaxima(), land.z);
        }
        double speed = Math.max(0.05, bola.velocidade());
        Vec3 path = land.subtract(start);
        Vec3 dir = path.lengthSqr() < 1.0E-6 ? new Vec3(0, -1, 0) : path.normalize();
        int fall = Math.max(1, (int) Math.ceil(path.length() / speed));
        Vec3 landed = start.add(dir.scale(fall * speed));
        int glow = Math.max(1, impacto.brilhoTicks());
        int pillars = Math.max(1, impacto.pilaresTicks());
        int boom = fall + glow + pillars;
        int stay = boom + Math.max(0, voo.ficarTicks());

        Fx.effects(level, SKILL, awakened, "soltar", start, dir);
        Fx.effects(level, SKILL, awakened, "queda", start, dir, fall);
        SkillSounds.play(level, spec.sons(), "soltar", 0, 0, onde -> start, screen);
        Destructible.Attack attack = Destructible.track(level, caster, start, Math.max(0.1, bola.raio()),
                () -> Destructible.sendErase(level, SKILL, start, 1));
        // Everyone in the blast hears it coming: the screen is the target's too.
        List<ServerPlayer> heard = new ArrayList<>(screen);
        if (target instanceof ServerPlayer player && !heard.contains(player)) {
            heard.add(player);
        }
        Tasks.add(age -> {
            if (caster.isAlive() && !caster.isRemoved() && age <= stay) {
                caster.fallDistance = 0;
                Vec3 motion = caster.getDeltaMovement();
                caster.setDeltaMovement(motion.x * 0.5, 0, motion.z * 0.5);
                caster.hurtMarked = true;
            }
            if (age == stay) {
                caster.setNoGravity(false);
                caster.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, Math.max(1, voo.planarTicks()), 0, false, false, false));
                unshield.run();
            }
            if (age < fall) {
                if (attack.gone()) {
                    // Erased on the way down: nothing lands. He glides down now.
                    caster.setNoGravity(false);
                    caster.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, Math.max(1, voo.planarTicks()), 0, false, false, false));
                    unshield.run();
                    return true;
                }
                attack.move(start.add(dir.scale((age + 1) * speed)));
                return false;
            }
            if (age == fall) {
                attack.end();
                Fx.effects(level, SKILL, awakened, "toque", landed, new Vec3(0, 1, 0), glow);
                SkillSounds.play(level, spec.sons(), "toque", 0, glow, onde -> landed, heard);
            } else if (age == fall + glow) {
                Fx.effects(level, SKILL, awakened, "pilares", landed, new Vec3(0, 1, 0), pillars);
                SkillSounds.play(level, spec.sons(), "pilares", 0, pillars, onde -> landed, heard);
            } else if (age == boom) {
                explode(caster, level, spec.explosao(), landed, awakened);
                SkillSounds.play(level, spec.sons(), "explosao", 0, 0, onde -> landed, heard);
                Fx.effects(level, SKILL, awakened, "explosao", landed, new Vec3(0, 1, 0));
            }
            return age >= stay;
        });
    }

    // The blast: damage falling off from the middle, anyone left under the Hakai's life limit pulverized, attacks in
    // flight inside it erased, and the crater.
    private static void explode(LivingEntity caster, ServerLevel level, DestructionTouchSpec.Explosao explosao, Vec3 at, boolean awakened) {
        double radius = Math.max(1, explosao.raio());
        HakaiSpec hakai = HakaiSpec.get(awakened);
        for (LivingEntity hit : Combat.inRadius(level, at, radius, caster)) {
            double k = Math.min(1, Combat.center(hit).distanceTo(at) / radius);
            if (!hakai.canErase(hit)) {
                Combat.hurt(caster, hit, (float) (explosao.danoCentro() + (explosao.danoBorda() - explosao.danoCentro()) * k));
                Combat.pushFrom(hit, at, explosao.empurrao() * (1 - k * 0.5), 0.5 + 0.4 * (1 - k));
            }
            if (hit.isAlive() && hakai.canErase(hit)) {
                Hakai.disintegrate(caster, hit, awakened);
            }
        }
        // Attacks in flight inside it are gone too.
        for (Destructible.Attack erased : Destructible.eraseAlong(level, at, at, radius, caster)) {
            Fx.effects(level, Hakaizinho.SKILL, awakened, "pulverizar", erased.pos(), Vec3.ZERO);
        }
        if (explosao.quebrarBlocos() && level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            crater(level, at, explosao.raioCratera(), explosao.profundidadeCratera(), Math.max(1, explosao.crateraTicks()));
        }
    }

    // A bowl pulverized out of the ground, a layer of rings per tick from the middle out: no drops, no debris.
    private static void crater(ServerLevel level, Vec3 at, double radius, double depth, int ticks) {
        int r = (int) Math.ceil(radius);
        int up = (int) Math.ceil(radius * 0.6);
        BlockPos center = BlockPos.containing(at);
        Tasks.repeat(ticks, tick -> {
            double from = radius * tick / ticks;
            double to = radius * (tick + 1) / ticks;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    double flat = Math.sqrt(dx * dx + dz * dz);
                    if (flat < from || flat >= to && tick < ticks - 1 || flat > radius) {
                        continue;
                    }
                    double k = flat / radius;
                    // Deep in the middle, shallow at the rim; everything standing over it is gone as well.
                    int bottom = -(int) Math.round(depth * Math.sqrt(Math.max(0, 1 - k * k)));
                    int top = (int) Math.round(up * (1 - k * 0.5));
                    for (int dy = bottom; dy <= top; dy++) {
                        BlockPos block = center.offset(dx, dy, dz);
                        if (!level.isLoaded(block)) {
                            continue;
                        }
                        BlockState state = level.getBlockState(block);
                        if (state.isAir() || state.getDestroySpeed(level, block) < 0) {
                            continue;
                        }
                        level.setBlock(block, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
            }
        });
    }

    // Grabbed, frozen, sealed (anything that cuts his moves off) or stunned for long enough.
    private static boolean knockedOut(LivingEntity caster, PowerData data, int interrupts, int stunTicks) {
        if (data.interrupts != interrupts || Freeze.isFrozen(caster)) {
            return true;
        }
        MobEffectInstance stun = caster.getEffect(ModEffects.ATORDOADO);
        return stun != null && stun.getDuration() >= Math.max(1, stunTicks);
    }

    // The top of the ground under a point (or the point itself when there is nothing within 96 blocks below).
    private static double ground(ServerLevel level, Vec3 from, LivingEntity caster) {
        var hit = level.clip(new ClipContext(from, from.subtract(0, 96, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, caster));
        return hit.getType() == HitResult.Type.MISS ? from.y : hit.getLocation().y;
    }

    // The tip of his right index finger, pointing down in front of him.
    private static Vec3 fingertip(LivingEntity caster) {
        Vec3 flat = Combat.flatLook(caster);
        Vec3 right = new Vec3(-flat.z, 0, flat.x);
        return caster.position().add(0, 0.5, 0).add(right.scale(0.35)).add(flat.scale(0.6));
    }
}
