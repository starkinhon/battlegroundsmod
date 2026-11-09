package com.starkinhon.battlegroundsmod.power.powers.sukuna;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.BlockRules;
import com.starkinhon.battlegroundsmod.combat.Capture;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.movement.Movement;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.network.ModPayloads.SukunaClivar;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.PowerLogic;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// X: Clivar (捌), as in the 3D preview Stark approved (forja/sukuna-desmantelar, "X: Clivar"; drawn by
// client/fx/ClivarRenderer). He sprints (~30 blocks: Stark 2026-11-09, 3x the preview's). If he reaches the target he grabs it by the neck with one hand and
// lifts it; fine white lines start measuring the body until the grid closes (slowly and smoothly, 2.4 s), then it
// snaps: the body turns into a black silhouette under the white grid for a moment and hundreds of white zigzag bits burst
// off it. The target takes the hit (scaled by its max health); he keeps holding, winds the arm back and throws it far.
//   - Against a wall: if the sprint brings the target to a wall, he pins it there and the Clivar cuts the wall too: its
//     middle breaks and the target flies through the hole (gamerule soulsBlockDestruction).
//   - Lethal: when the blow already kills, the target isn't thrown: after the silhouette the body comes apart into the
//     grid's own cells, which part, crumble to the ground and vanish over a pool of blood.
//   - A sprint that reaches no one brakes and says so.
public final class Clivar {
    // Ticks (the preview's CL_T): the crouch before the sprint, the sprint's longest, carrying to the wall, the measuring,
    // the silhouette, holding after the snap, winding the arm back.
    public static final int PREP = 3, DASH = 16, PIN = 3, MEASURE = 48, SIL = 5, HOLD = 12, WIND = 9;
    private static final double SPEED = 1.9, DISTANCE = 30, GRAB_REACH = 1.2, GRAB_RADIUS = 0.8, LOCK_RANGE = 34;
    // Where his gripping hand is in the hold (sukuna.clivar_pega: right arm up at 132 degrees, tools/sukuna/bake_clivar.js),
    // blocks on a 1.8-block body: ahead, up from his feet, to his right. The target hangs from it by the neck.
    private static final double HAND_FWD = 0.557, HAND_UP = 1.877, HAND_SIDE = 0.234;
    // How far ahead of him the held body is, how high a body pinned to a wall is lifted, how far a wall may be past it.
    private static final double REACH = HAND_FWD, LIFT = 0.45, WALL_REACH = 4.2;
    // The hit: a flat part plus a share of the target's max health.
    private static final float DAMAGE = 30, MAX_HEALTH_SHARE = 0.25f, GRAB_DAMAGE = 4;
    private static final int COOLDOWN = 260, MISS_COOLDOWN = 50, BROKEN_COOLDOWN = 80;
    private static final float MODEL_WEIGHT = 1.5f;

    private static final Map<LivingEntity, Boolean> BUSY = new WeakHashMap<>();

    private Clivar() {
    }

    public static boolean use(LivingEntity caster, ServerLevel level) {
        if (!Movement.free(caster) || BUSY.containsKey(caster)) {
            return false;
        }
        PowerData data = caster.getData(ModAttachments.POWER);
        int interrupts = data.interrupts;
        data.busyTicks = Math.max(data.busyTicks, PREP + DASH + 3);
        BUSY.put(caster, true);
        LivingEntity locked = lock(caster);
        anim(caster, "sukuna.clivar_corre");
        Fx.sound(level, caster.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.9f, 0.6f);
        Vec3[] dir = {locked != null ? flatTo(caster, locked) : Combat.flatLook(caster)};
        double[] traveled = {0};
        Vec3[] last = {caster.position()};
        Tasks.add(age -> {
            if (!caster.isAlive() || data.interrupts != interrupts || Freeze.isFrozen(caster)) {
                BUSY.remove(caster);
                return true;
            }
            if (age < PREP) {
                Vec3 v = caster.getDeltaMovement();
                caster.setDeltaMovement(v.x * 0.3, v.y, v.z * 0.3);
                caster.hurtMarked = true;
                return false;
            }
            int t = age - PREP;
            if (t == 0) {
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.SukunaClivar(caster.getId(), SukunaClivar.TRAIL, DASH,
                        Vec3.ZERO, Vec3.ZERO, caster.getRandom().nextInt(0xFFFF)));
                Fx.sound(level, caster.position(), SoundEvents.PLAYER_ATTACK_STRONG, 1f, 0.6f);
            } else {
                Vec3 moved = caster.position().subtract(last[0]);
                traveled[0] += Math.sqrt(moved.x * moved.x + moved.z * moved.z);
            }
            last[0] = caster.position();
            if (locked != null && Combat.isTarget(locked, caster)) {
                dir[0] = flatTo(caster, locked);
            }
            double step = Math.min(SPEED, DISTANCE - traveled[0]);
            Vec3 from = Combat.center(caster);
            LivingEntity caught = caught(level, caster, from, from.add(dir[0].scale(Math.max(0, step) + GRAB_REACH)), locked);
            if (caught != null) {
                grab(caster, caught, level, data);
                return true;
            }
            if (step <= 0.05 || t > DASH + 2 || t > 1 && caster.horizontalCollision) {
                miss(caster, data);
                return true;
            }
            caster.setDeltaMovement(dir[0].x * step, Math.min(caster.getDeltaMovement().y, 0.1), dir[0].z * step);
            caster.hurtMarked = true;
            caster.resetFallDistance();
            return false;
        });
        return true;
    }

    // Whoever he is fighting, if close enough and in front; else whoever he aims at.
    private static LivingEntity lock(LivingEntity caster) {
        PowerData data = caster.getData(ModAttachments.POWER);
        if (data.foughtId >= 0 && caster.level().getEntity(data.foughtId) instanceof LivingEntity fought && Combat.isTarget(fought, caster)
                && fought.distanceTo(caster) <= LOCK_RANGE && caster.hasLineOfSight(fought) && flatTo(caster, fought).dot(Combat.flatLook(caster)) > 0.5) {
            return fought;
        }
        return Combat.raycastTarget(caster, LOCK_RANGE);
    }

    private static LivingEntity caught(ServerLevel level, LivingEntity caster, Vec3 from, Vec3 to, LivingEntity locked) {
        List<LivingEntity> hits = new ArrayList<>(Combat.alongLine(level, from, to, GRAB_RADIUS, caster));
        hits.removeIf(e -> !Capture.canGrab(caster, e));
        if (locked != null && hits.contains(locked)) {
            return locked;
        }
        return hits.stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(caster))).orElse(null);
    }

    // Nobody reached: he brakes and says so.
    private static void miss(LivingEntity caster, PowerData data) {
        BUSY.remove(caster);
        data.busyTicks = 0;
        Vec3 v = caster.getDeltaMovement();
        caster.setDeltaMovement(v.x * 0.2, v.y, v.z * 0.2);
        caster.hurtMarked = true;
        anim(caster, "");
        SubCommon.warn(caster, "sukuna.clivar_miss");
        cooldown(caster, MISS_COOLDOWN);
    }

    private static void grab(LivingEntity caster, LivingEntity target, ServerLevel level, PowerData data) {
        Capture.take(target);
        Vec3 fwd = flatTo(caster, target);
        float yRot = (float) (Mth.atan2(-fwd.x, fwd.z) * Mth.RAD_TO_DEG);
        double body = caster.getBbHeight() / 1.8;
        Vec3 startFeet = target.position();
        // a wall right past the target, at its chest: pin it there
        Vec3 chest = target.position().add(0, target.getBbHeight() * 0.6, 0);
        double half = target.getBbWidth() / 2;
        BlockHitResult hit = level.clip(new ClipContext(chest, chest.add(fwd.scale(WALL_REACH + half)), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, target));
        boolean wall = hit.getType() == HitResult.Type.BLOCK && hit.getDirection().getAxis().isHorizontal();
        Vec3 normal = wall ? Vec3.atLowerCornerOf(hit.getDirection().getNormal()) : Vec3.ZERO;
        Vec3 pinFeet = wall ? new Vec3(hit.getLocation().x, startFeet.y, hit.getLocation().z).add(normal.scale(half + 0.05)) : startFeet;
        Vec3 face = wall ? wallFace(hit.getBlockPos(), hit.getDirection(), startFeet.y) : Vec3.ZERO;
        int pin = wall ? PIN : 0, m0 = pin + 2, snap = m0 + MEASURE, wind = snap + HOLD, thr = wind + WIND;
        Vec3 stand = standSpot(level, caster, startFeet.subtract(fwd.scale(REACH * body)));
        Vec3 pinStand = wall ? standSpot(level, caster, pinFeet.subtract(fwd.scale(REACH * body))) : stand;
        caster.setDeltaMovement(Vec3.ZERO);
        if (caster instanceof ServerPlayer player) {
            player.connection.teleport(stand.x, stand.y, stand.z, yRot, 0);
        } else {
            caster.moveTo(stand.x, stand.y, stand.z, yRot, 0);
            caster.setYHeadRot(yRot);
        }
        caster.hurtMarked = true;
        data.busyTicks = Math.max(data.busyTicks, thr + 6);
        Runnable freeCaster = Freeze.hold(caster, thr + 4, age -> age < pin ? stand.lerp(pinStand, smooth((age + 1) / (double) pin)) : pinStand);
        // held by the neck: lifted over 3 ticks; against a wall carried to it and pressed there
        Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
        double neck = 1.5 * target.getBbHeight() / 1.8;
        Vec3 held = stand.add(fwd.scale(HAND_FWD * body)).add(right.scale(HAND_SIDE * body)).add(0, Math.max(0.1, HAND_UP * body - neck), 0);
        Vec3 pressed = pinFeet.add(0, LIFT * 0.6 * body, 0);
        float targetYaw = yRot + 180;
        target.setYRot(targetYaw);
        target.setYHeadRot(targetYaw);
        target.setYBodyRot(targetYaw);
        Runnable freeTarget = Freeze.hold(target, thr + 2, age -> {
            if (wall) {
                return age < pin ? startFeet.lerp(pressed, smooth((age + 1) / (double) pin)) : pressed;
            }
            return startFeet.lerp(held, smooth((age + 1) / 3.0));
        });
        Stun.apply(target, thr + 12);
        PowerLogic.seal(target, thr + 2);
        Capture.grabbed(target, caster, thr + 2);
        anim(caster, "sukuna.clivar_pega");
        anim(target, "sukuna.clivar_preso");
        Combat.hurt(caster, target, GRAB_DAMAGE);
        Fx.sound(level, chest, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.1f, 0.6f);
        int interrupts = data.interrupts;
        int seed = caster.getRandom().nextInt(0xFFFF);
        boolean[] lethal = {false};
        Tasks.add(age -> {
            boolean broken = !caster.isAlive() || data.interrupts != interrupts;
            if (broken || age < snap && (!target.isAlive() || target.isRemoved())) {
                freeCaster.run();
                freeTarget.run();
                BUSY.remove(caster);
                send(caster, target, SukunaClivar.CANCEL, 0, Vec3.ZERO, Vec3.ZERO, seed);
                anim(caster, "");
                anim(target, "");
                data.busyTicks = 0;
                cooldown(caster, BROKEN_COOLDOWN);
                return true;
            }
            if (wall && age == pin) {
                anim(caster, "sukuna.clivar_prensa");
                send(caster, target, SukunaClivar.PIN, 0, pressed.add(0, target.getBbHeight() * 0.7, 0).subtract(normal.scale(half)), normal, seed);
                Fx.sound(level, pressed, SoundEvents.PLAYER_ATTACK_STRONG, 1.2f, 0.5f);
                Fx.sound(level, pressed, SoundEvents.STONE_HIT, 1.2f, 0.6f);
            }
            if (age == m0) {
                send(caster, target, SukunaClivar.MEASURE, MEASURE, face, normal, seed);
            }
            if (age > m0 && age < snap && (age - m0) % 8 == 0) {
                // the lines running over the body: a thin, rising whisper
                Fx.sound(level, Combat.center(target), SoundEvents.AMETHYST_BLOCK_CHIME, 0.5f, 1.2f + 0.6f * (age - m0) / MEASURE);
            }
            if (age == snap) {
                ModelBar.landed(caster, target, MODEL_WEIGHT);
                float damage = DAMAGE + MAX_HEALTH_SHARE * target.getMaxHealth();
                Combat.hurt(caster, target, damage);
                lethal[0] = !target.isAlive() || target.getHealth() <= 0;
                send(caster, target, lethal[0] ? SukunaClivar.SNAP_LETHAL : SukunaClivar.SNAP, 0, Vec3.ZERO, fwd, seed);
                Vec3 c = Combat.center(target);
                Fx.sound(level, c, SoundEvents.PLAYER_ATTACK_CRIT, 1.6f, 0.5f);
                Fx.sound(level, c, SoundEvents.PLAYER_ATTACK_SWEEP, 1.4f, 0.4f);
                Fx.sound(level, c, SoundEvents.AMETHYST_CLUSTER_BREAK, 1.2f, 0.7f);
                String suffix = wall ? "_parede" : "";
                anim(target, "sukuna.clivar_mole");
                if (lethal[0]) {
                    anim(caster, "sukuna.clivar_solta" + suffix);
                    freeTarget.run();
                    freeCaster.run();
                    Freeze.hold(caster, SIL + 22);
                    data.busyTicks = Math.max(0, SIL + 22);
                    BUSY.remove(caster);
                    cooldown(caster, COOLDOWN);
                    return true;
                }
                anim(caster, "sukuna.clivar_joga" + suffix);
            }
            if (age == thr && wall) {
                breakWall(caster, target, level, face, normal, seed);
            }
            if (age == thr + 2) {
                freeTarget.run();
                Combat.push(target, fwd, 1.5, 0.45);
                Stun.apply(target, 22);
                anim(target, "sukuna.clivar_voa");
                Fx.sound(level, Combat.center(target), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.3f, 0.5f);
            }
            if (age >= thr + 4) {
                freeCaster.run();
                BUSY.remove(caster);
                cooldown(caster, COOLDOWN);
                return true;
            }
            return false;
        });
    }

    // The centre of the wall's face (its middle 3x3 from the target's feet up), in front of the block hit.
    private static Vec3 wallFace(BlockPos block, Direction side, double feetY) {
        Vec3 n = Vec3.atLowerCornerOf(side.getNormal());
        Vec3 c = Vec3.atCenterOf(block).add(n.scale(0.5));
        return new Vec3(c.x, Math.floor(feetY) + 1.5, c.z);
    }

    // The wall's middle 3x3, as client/fx/ClivarRenderer.pieces lays it out: centre minus half the normal, then a block
    // either way across and up.
    private static List<BlockPos> wallBlocks(Vec3 face, Vec3 normal) {
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 right = up.cross(normal);
        right = right.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : right.normalize();
        List<BlockPos> out = new ArrayList<>();
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                out.add(BlockPos.containing(face.subtract(normal.scale(0.5)).add(right.scale(i)).add(up.scale(j))));
            }
        }
        return out;
    }

    // The grid cut the wall too: its middle comes apart (the client throws the quarter blocks through).
    private static void breakWall(LivingEntity caster, LivingEntity target, ServerLevel level, Vec3 face, Vec3 normal, int seed) {
        if (!BlockRules.canBreak(level)) {
            return;
        }
        send(caster, target, SukunaClivar.WALL_BREAK, 0, face, normal, seed);
        for (BlockPos pos : wallBlocks(face, normal)) {
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0) {
                continue;
            }
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        Fx.sound(level, face, SoundEvents.STONE_BREAK, 1.6f, 0.6f);
        Fx.sound(level, face, SoundEvents.GENERIC_EXPLODE.value(), 0.6f, 1.4f);
    }

    // Where he can stand at spot (same height, else a step up or down), or where he already is.
    private static Vec3 standSpot(ServerLevel level, LivingEntity caster, Vec3 spot) {
        for (double dy : new double[] {0, 1, -1}) {
            Vec3 at = spot.add(0, dy, 0);
            if (level.noCollision(caster, caster.getBoundingBox().move(at.subtract(caster.position())))) {
                return at;
            }
        }
        return caster.position();
    }

    // To everyone who sees the target (Sukuna, holding it, always does).
    private static void send(LivingEntity caster, LivingEntity target, int kind, int ticks, Vec3 a, Vec3 b, int seed) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(target, new ModPayloads.SukunaClivar(target.getId(), kind, ticks, a, b, seed));
    }

    private static Vec3 flatTo(LivingEntity from, LivingEntity to) {
        Vec3 d = to.position().subtract(from.position());
        Vec3 flat = new Vec3(d.x, 0, d.z);
        return flat.lengthSqr() < 1.0E-4 ? Combat.flatLook(from) : flat.normalize();
    }

    private static double smooth(double x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    private static void cooldown(LivingEntity caster, int ticks) {
        PowerData data = caster.getData(ModAttachments.POWER);
        if (data.noCooldown) {
            return;
        }
        data.cooldowns[1] = Math.max(data.cooldowns[1], ticks);
        if (caster instanceof ServerPlayer player) {
            PowerLogic.sync(player);
        }
    }

    private static void anim(LivingEntity entity, String move) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, new ModPayloads.PlaySkillAnimation(entity.getId(), move, false));
    }
}
