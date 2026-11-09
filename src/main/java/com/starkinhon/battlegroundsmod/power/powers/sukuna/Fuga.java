package com.starkinhon.battlegroundsmod.power.powers.sukuna;

import java.util.HashSet;
import java.util.Set;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.ModEffects;
import com.starkinhon.battlegroundsmod.combat.AshKill;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.powers.sub.jj.JjCommon;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// Sukuna's Fuga (■開, key C), ported from the 3D preview Stark approved (forja/sukuna-fuga, v25 formation + v26 shot).
// The caster stands rooted while the fire gathers into the ball, becomes the arrow and is drawn like a bow (the timeline
// is the preview's at its 1x speed: one preview tick = SLOW game ticks, the bow draw stretched DRAW_X times). Then the
// arrow flies at where he aims, faster and faster, and bursts into a dome of fire that climbs into a column.
// While it forms, everything within DARK_RADIUS blocks goes dark (client/fx/FugaRenderer). Visuals are all client side;
// this class only times it, flies the arrow and does the damage.
public final class Fuga {
    public static final String SKILL = "sukuna.fuga";
    // The preview's timeline in game ticks (track baked from the preview: bake.js in the preview's folder).
    public static final int FIRE_TICK = 228;
    public static final int LENGTH = 372;
    // The flight: the preview covers 12 blocks in 8 ticks along u^1.6; the same curve keeps going up to RANGE.
    private static final double FLY_BLOCKS = 12, FLY_TICKS = 8, FLY_POW = 1.6;
    private static final double RANGE = 64;
    // Where the arrow's tip sits at the release, in the caster's own frame (facing +z, feet at 0): the right fist from the
    // baked pose, plus the preview's offsets along the aim (0.08 out of the fist, the arrow 1.35 past it).
    private static final Vec3 TIP_LOCAL = new Vec3(-0.071, 1.093, 0.763 + 0.08 + 1.35);
    // The burst: full damage at the middle, EDGE at RADIUS.
    private static final double RADIUS = 7;
    private static final float DAMAGE_CENTER = 150, DAMAGE_EDGE = 45;
    private static final int BURN_SECONDS = 8;
    public static final double DARK_RADIUS = 70;

    // Phases sent to the clients (ModPayloads.FugaFx).
    public static final int START = 0, SHOT = 1, HIT = 2, STOP = 3;

    private Fuga() {
    }

    public static boolean use(LivingEntity caster, ServerLevel level) {
        PowerData data = caster.getData(ModAttachments.POWER);
        String power = data.power;
        int interrupts = data.interrupts;
        data.busyTicks = FIRE_TICK + 6;
        // Rooted while the arrow forms (he can still turn to aim), free again right after the release.
        caster.addEffect(new MobEffectInstance(ModEffects.ENRAIZADO, FIRE_TICK + 4, 0, false, false, false));
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), SKILL, false));
        send(caster, START, caster.position(), caster.getLookAngle());
        Fx.sound(level, caster.position(), SoundEvents.FIRECHARGE_USE, 1.2f, 0.6f);
        Tasks.add(tick -> {
            if (!caster.isAlive() || caster.isRemoved() || !power.equals(caster.getData(ModAttachments.POWER).power)
                    || caster.getData(ModAttachments.POWER).interrupts != interrupts) {
                caster.removeEffect(ModEffects.ENRAIZADO);
                send(caster, STOP, caster.position(), Vec3.ZERO);
                return true;
            }
            sounds(level, caster, tick);
            if (tick < FIRE_TICK) {
                return false;
            }
            shoot(caster, level);
            return true;
        });
        return true;
    }

    // The fire's voice while it forms: crackling as the ribbons whip out, a roar as the ball takes them in, the hiss of
    // the bow being drawn.
    private static void sounds(ServerLevel level, LivingEntity caster, int tick) {
        Vec3 at = caster.position().add(0, 1, 0);
        if (tick % 9 == 0 && tick < FIRE_TICK) {
            Fx.sound(level, at, SoundEvents.FIRE_AMBIENT, 1.6f, 0.7f + 0.3f * tick / FIRE_TICK);
        }
        if (tick == 24 || tick == 68) {
            Fx.sound(level, at, SoundEvents.BLAZE_SHOOT, 1.4f, 0.5f);
        }
        if (tick == 88) {
            Fx.sound(level, at, SoundEvents.FIRECHARGE_USE, 1.6f, 0.45f);
        }
        if (tick == 108) {
            Fx.sound(level, at, SoundEvents.BLAZE_AMBIENT, 1.2f, 0.4f);
        }
        if (tick == 140) {
            Fx.sound(level, at, SoundEvents.FIRECHARGE_USE, 1.3f, 0.35f);
        }
    }

    // The release: the arrow flies at the crosshair, stopped by the first body or block on its way.
    private static void shoot(LivingEntity caster, ServerLevel level) {
        float yaw = caster.getYHeadRot();
        Vec3 tip = caster.position().add(TIP_LOCAL.yRot(-yaw * Mth.DEG_TO_RAD));
        Vec3 eye = caster.getEyePosition();
        Vec3 aimEnd = eye.add(caster.getLookAngle().scale(RANGE));
        var sight = level.clip(new ClipContext(eye, aimEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        Vec3 aim = sight.getType() == HitResult.Type.MISS ? aimEnd : sight.getLocation();
        Vec3 dir = aim.subtract(tip);
        dir = dir.lengthSqr() < 1.0E-4 ? caster.getLookAngle() : dir.normalize();
        Vec3 way = dir;
        send(caster, SHOT, tip, way);
        Fx.sound(level, tip, SoundEvents.BLAZE_SHOOT, 2f, 0.4f);
        Fx.sound(level, tip, SoundEvents.FIRECHARGE_USE, 2f, 0.7f);
        Tasks.add(tick -> {
            double a = flown(tick - 1), b = flown(tick);
            boolean last = b >= RANGE;
            b = Math.min(b, RANGE);
            Vec3 from = tip.add(way.scale(a)), to = tip.add(way.scale(b));
            var wall = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
            Vec3 end = wall.getType() == HitResult.Type.MISS ? to : wall.getLocation();
            LivingEntity body = Combat.alongLine(level, from, end, 0.7, caster).stream()
                    .min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(from))).orElse(null);
            if (body != null) {
                explode(caster, level, Combat.center(body).add(0, -0.4, 0), way);
                return true;
            }
            if (wall.getType() != HitResult.Type.MISS || last) {
                explode(caster, level, end.subtract(way.scale(0.3)), way);
                return true;
            }
            return false;
        });
    }

    // Blocks flown after this many ticks of flight.
    public static double flown(double ticks) {
        return ticks <= 0 ? 0 : FLY_BLOCKS * Math.pow(ticks / FLY_TICKS, FLY_POW);
    }

    private static void explode(LivingEntity caster, ServerLevel level, Vec3 at, Vec3 way) {
        // The burst sits on the ground under where it hit when the ground is close (the dome grows out of the floor).
        var floor = level.clip(new ClipContext(at, at.add(0, -3, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        Vec3 center = floor.getType() == HitResult.Type.MISS ? at : floor.getLocation().add(0, 0.2, 0);
        send(caster, HIT, center, way);
        Fx.sound(level, center, SoundEvents.GENERIC_EXPLODE.value(), 4f, 0.55f);
        Fx.sound(level, center, SoundEvents.BLAZE_SHOOT, 3f, 0.3f);
        Set<LivingEntity> hit = new HashSet<>(Combat.inRadius(level, center, RADIUS, caster));
        for (LivingEntity target : hit) {
            double d = Math.min(1, Combat.center(target).distanceTo(center) / RADIUS);
            float damage = (float) Mth.lerp(d, DAMAGE_CENTER, DAMAGE_EDGE);
            AshKill.hurt(caster, target, damage);
            target.igniteForSeconds(BURN_SECONDS);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
            Vec3 out = target.position().subtract(center);
            out = new Vec3(out.x, 0, out.z).lengthSqr() < 0.01 ? way : out;
            Combat.push(target, out, 2.2 * (1 - 0.5 * d), 0.9 * (1 - 0.4 * d));
        }
        // The ground breaks into a bowl and catches fire around it (gamerule soulsBlockDestruction).
        JjCommon.crater(level, center, 4.5, 2.5, 24);
        Tasks.later(2, () -> scorch(level, center, RADIUS + 2));
    }

    private static void scorch(ServerLevel level, Vec3 center, double r) {
        if (!com.starkinhon.battlegroundsmod.combat.BlockRules.canBreak(level)) {
            return;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        var random = level.getRandom();
        for (int i = 0; i < 70; i++) {
            double a = random.nextDouble() * Mth.TWO_PI, d = Math.sqrt(random.nextDouble()) * r;
            int x = Mth.floor(center.x + Math.cos(a) * d), z = Mth.floor(center.z + Math.sin(a) * d);
            for (int y = Mth.floor(center.y) + 3; y >= Mth.floor(center.y) - 5; y--) {
                pos.set(x, y, z);
                if (level.getBlockState(pos).isAir() && !level.getBlockState(pos.below()).isAir()) {
                    if (BaseFireBlock.canBePlacedAt(level, pos, net.minecraft.core.Direction.UP)) {
                        level.setBlock(pos, BaseFireBlock.getState(level, pos), 11);
                    }
                    break;
                }
            }
        }
    }

    private static void send(LivingEntity caster, int phase, Vec3 pos, Vec3 dir) {
        // Everyone who can see the darkness hears about it (the tracking range is shorter than its 70 blocks).
        ModPayloads.FugaFx msg = new ModPayloads.FugaFx(caster.getId(), phase, pos, dir);
        Vec3 c = phase == START || phase == STOP ? caster.position() : pos;
        PacketDistributor.sendToPlayersNear(Combat.level(caster), null, c.x, c.y, c.z, DARK_RADIUS + 40, msg);
    }
}
