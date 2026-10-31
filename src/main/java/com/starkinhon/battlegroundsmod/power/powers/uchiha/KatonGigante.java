package com.starkinhon.battlegroundsmod.power.powers.uchiha;

import java.util.ArrayList;
import java.util.List;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.AshKill;
import com.starkinhon.battlegroundsmod.combat.BlockRules;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Destructible;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.skill.KatonGiganteSpec;
import com.starkinhon.battlegroundsmod.skill.SkillSpecs;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// Shift + Z: Madara jumps into the sky, stops there and charges a fireball over his head that grows until he lets go of
// the key (or it is full), then throws it where he aims. It blows up on the first thing it touches. The ball itself is
// drawn on the clients (client/fx/KatonBallRenderer); values come from souls_skills/madara.katon_gigante.json.
public final class KatonGigante {
    public static final String SKILL = "madara.katon_gigante";
    private static final RandomSource RANDOM = RandomSource.create();
    private static int nextId;

    private KatonGigante() {
    }

    public static boolean use(LivingEntity caster, ServerLevel level) {
        PowerData data = caster.getData(ModAttachments.POWER);
        boolean awakened = data.awakened;
        KatonGiganteSpec spec = SkillSpecs.get(SKILL, KatonGiganteSpec.class, awakened);
        KatonGiganteSpec.Carga carga = spec.carga();
        int rise = Math.max(0, spec.pulo().subirTicks());
        int full = Math.max(1, carga.maxTicks());
        // Bosses don't hold keys: they let go somewhere between 60% and a full charge.
        int bossCharge = Math.max(carga.minTicks(), (int) (full * (0.6 + RANDOM.nextDouble() * 0.4)));
        int id = nextId++;
        String power = data.power;
        int interrupts = data.interrupts;

        data.busyTicks = rise + full + 10;
        data.noFallTicks = Math.max(data.noFallTicks, rise + full + spec.planarTicks() + 100);
        Vec3 motion = caster.getDeltaMovement();
        caster.setDeltaMovement(motion.x * 0.3, spec.pulo().forca(), motion.z * 0.3);
        caster.hurtMarked = true;
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), SKILL, awakened));
        Fx.sound(level, caster.position(), SoundEvents.FIRECHARGE_USE, 2f, 0.5f);
        Fx.sound(level, caster.position(), SoundEvents.ENDER_DRAGON_FLAP, 1.5f, 0.7f);
        Fx.effects(level, SKILL, awakened, "pulo", caster.position(), new Vec3(0, 1, 0));

        Tasks.add(age -> {
            boolean cut = !caster.isAlive() || caster.isRemoved() || !power.equals(caster.getData(ModAttachments.POWER).power)
                    || caster.getData(ModAttachments.POWER).interrupts != interrupts;
            if (cut) {
                caster.setNoGravity(false);
                send(level, new ModPayloads.KatonBall(id, caster.getId(), 2, Vec3.ZERO, Vec3.ZERO, 0, 0, 0));
                return true;
            }
            if (age < rise) {
                return false;
            }
            int charge = age - rise;
            if (charge == 0) {
                // Stops up there and the ball starts to form over the head.
                caster.setNoGravity(true);
                send(level, new ModPayloads.KatonBall(id, caster.getId(), 0, new Vec3(0, carga.acima(), 0), Vec3.ZERO, (float) carga.raioMin(),
                        (float) carga.raioMax(), full));
                Fx.effects(level, SKILL, awakened, "carga", caster, full);
            }
            // Hangs in the air: barely drifts, can still turn to aim.
            Vec3 drift = caster.getDeltaMovement();
            caster.setDeltaMovement(drift.x * 0.5, 0, drift.z * 0.5);
            caster.hurtMarked = true;
            caster.fallDistance = 0;
            if (charge % 8 == 0) {
                float k = charge / (float) full;
                Fx.sound(level, caster.position(), SoundEvents.BLAZE_SHOOT, 1.5f + k, 0.4f + k * 0.4f);
                Fx.sound(level, caster.position(), SoundEvents.FIRE_AMBIENT, 2f, 0.5f + k * 0.5f);
            }
            boolean letGo = caster instanceof Player ? !caster.getData(ModAttachments.POWER).abilityHeld : charge >= bossCharge;
            if (charge >= full || letGo && charge >= carga.minTicks()) {
                caster.setNoGravity(false);
                caster.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, spec.planarTicks(), 0, false, false, false));
                caster.getData(ModAttachments.POWER).busyTicks = 6;
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), "", false));
                caster.swing(InteractionHand.MAIN_HAND, true);
                double k = Math.min(1, charge / (double) full);
                double radius = Mth.lerp(smooth(k), carga.raioMin(), carga.raioMax());
                Vec3 center = caster.getEyePosition().add(0, carga.acima() + radius, 0);
                fly(caster, level, spec, id, center, aim(caster), radius, k, awakened);
                return true;
            }
            return false;
        });
        return true;
    }

    private static Vec3 aim(LivingEntity caster) {
        return Vec3.directionFromRotation(caster.getXRot(), caster.getYRot());
    }

    // From over the head straight along the aim, until it touches something.
    private static void fly(LivingEntity caster, ServerLevel level, KatonGiganteSpec spec, int id, Vec3 start, Vec3 dir, double radius, double charge,
            boolean awakened) {
        double speed = Math.max(0.2, spec.voo().velocidade());
        int ticks = (int) Math.ceil(spec.voo().alcance() / speed);
        Vec3 velocity = dir.scale(speed);
        send(level, new ModPayloads.KatonBall(id, caster.getId(), 1, start, velocity, (float) radius, (float) radius, ticks));
        Fx.sound(level, start, SoundEvents.GHAST_SHOOT, 3f, 0.4f);
        Fx.sound(level, start, SoundEvents.WARDEN_SONIC_CHARGE, 2f, 0.6f);
        Fx.effects(level, SKILL, awakened, "lancar", start, dir);
        // Touches ground a bit inside its radius, so it looks like it sinks in before it blows.
        double touch = radius * 0.6;
        boolean[] done = {false};
        // Erased in flight (the hakaizinho): the ball is gone with no explosion.
        Destructible.Attack attack = Destructible.track(level, caster, start, radius,
                () -> send(level, new ModPayloads.KatonBall(id, caster.getId(), 2, start, Vec3.ZERO, 0, 0, 0)));
        Tasks.repeat(ticks + 1, age -> {
            if (done[0] || attack.gone()) {
                return;
            }
            Vec3 from = start.add(velocity.scale(age));
            Vec3 to = start.add(velocity.scale(age + 1));
            attack.move(from);
            boolean hit = age == ticks;
            Vec3 at = to;
            var clip = level.clip(new ClipContext(from, to.add(dir.scale(touch)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
            if (clip.getType() != HitResult.Type.MISS) {
                hit = true;
                at = clip.getLocation().subtract(dir.scale(touch));
            } else if (!level.noCollision(null, AABB.ofSize(to, touch, touch, touch))) {
                hit = true;
            } else if (!Combat.inRadius(level, to, radius * 0.9, caster).isEmpty()) {
                hit = true;
            }
            if (age % 6 == 0) {
                Fx.sound(level, to, SoundEvents.FIRE_AMBIENT, 3f, 0.5f);
            }
            if (hit) {
                done[0] = true;
                attack.end();
                send(level, new ModPayloads.KatonBall(id, caster.getId(), 2, at, Vec3.ZERO, 0, 0, 0));
                explode(caster, level, spec, at, radius, charge, awakened);
                return;
            }
        });
    }

    private static void explode(LivingEntity caster, ServerLevel level, KatonGiganteSpec spec, Vec3 pos, double ball, double charge, boolean awakened) {
        KatonGiganteSpec.Explosao explosao = spec.explosao();
        double radius = Math.max(1, ball * explosao.raioMult());
        float center = Mth.lerp((float) charge, explosao.danoMin(), explosao.danoMax());
        for (LivingEntity target : Combat.inRadius(level, pos, radius, caster)) {
            double edge = Math.min(1, Combat.center(target).distanceTo(pos) / radius);
            AshKill.hurt(caster, target, center * (float) (1 - 0.5 * edge));
            Combat.pushFrom(target, pos, explosao.empurrao() * (1 - 0.5 * edge), 0.6);
            target.igniteForSeconds(5);
        }
        Fx.sound(level, pos, SoundEvents.GENERIC_EXPLODE.value(), 5f, 0.5f);
        Fx.sound(level, pos, SoundEvents.BLAZE_SHOOT, 4f, 0.4f);
        Fx.sound(level, pos, SoundEvents.FIRECHARGE_USE, 4f, 0.3f);
        Fx.effects(level, SKILL, awakened, charge >= 0.5 ? "explosao" : "explosaoPequena", pos, new Vec3(0, 1, 0));
        for (ServerPlayer viewer : level.players()) {
            double distance = viewer.position().distanceTo(pos);
            if (distance < radius * 3) {
                PacketDistributor.sendToPlayer(viewer, new ModPayloads.ScreenShake((float) ((0.4 + charge * 0.6) * (1 - distance / (radius * 3))), 20));
            }
        }
        if (explosao.quebrarBlocos() && BlockRules.canBreak(level)) {
            hole(level, pos, ball * explosao.crateraMult());
            if (explosao.fogo()) {
                int crater = (int) Math.ceil(ball * explosao.crateraMult());
                Tasks.later(4, () -> burnGround(level, pos, radius, crater));
            }
        }
    }

    // A round hole, opened from the middle out over 3 ticks. Unbreakable blocks stay; no drops, no neighbor updates.
    private static void hole(ServerLevel level, Vec3 pos, double radius) {
        if (radius <= 0) {
            return;
        }
        BlockPos center = BlockPos.containing(pos);
        int r = (int) Math.ceil(radius * 1.1);
        List<List<BlockPos>> waves = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    double d = (x * x + y * y + z * z) / (radius * radius);
                    if (d > 0.85 + RANDOM.nextDouble() * 0.3) {
                        continue;
                    }
                    waves.get((int) Math.min(2, Math.sqrt(d) * 3)).add(center.offset(x, y, z));
                }
            }
        }
        for (int i = 0; i < waves.size(); i++) {
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

    // Fire on the ground around the blast, thicker near the middle.
    private static void burnGround(ServerLevel level, Vec3 pos, double radius, int depth) {
        int count = (int) (radius * radius * 0.8);
        for (int i = 0; i < count; i++) {
            double a = RANDOM.nextDouble() * Mth.TWO_PI;
            double r = Math.sqrt(RANDOM.nextDouble()) * radius;
            int x = Mth.floor(pos.x + Math.cos(a) * r);
            int z = Mth.floor(pos.z + Math.sin(a) * r);
            for (int y = Mth.floor(pos.y) + 4; y >= Mth.floor(pos.y) - depth - 2; y--) {
                BlockPos block = new BlockPos(x, y, z);
                if (level.getBlockState(block).isAir() && level.getBlockState(block.below()).isFaceSturdy(level, block.below(), net.minecraft.core.Direction.UP)) {
                    level.setBlock(block, Blocks.FIRE.defaultBlockState(), Block.UPDATE_ALL);
                    break;
                }
            }
        }
    }

    private static void send(ServerLevel level, ModPayloads.KatonBall payload) {
        for (ServerPlayer viewer : level.players()) {
            PacketDistributor.sendToPlayer(viewer, payload);
        }
    }

    private static double smooth(double x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }
}
