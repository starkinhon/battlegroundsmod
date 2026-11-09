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
import com.starkinhon.battlegroundsmod.network.ModPayloads.SukunaCravada;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.PowerLogic;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// B: the old Shift+X (Stark, 2026-11-09: the whole of it moved to the fifth skill), as in the 3D preview he approved
// (forja/sukuna-desmantelar, "Shift+X"; ref: his first video; drawn by client/fx/CravadaRenderer). He sprints and punches
// an uppercut on the run that throws the target up and forward; the punch carries him into a jump that passes over the
// target's arc and catches it by the neck on the way down; he pulls it over his head and slams it into the ground (it
// bounces and slides onto its back); the throw kicks him up and he comes down standing on it. The stomp spreads a web of
// cuts over the ground around the body in three stages, ~0.7 s apart, each wider; then the whole web cuts the ground
// (four huge straight cuts across it) and the ground blows up along it: the web's own shape is cut out of the world's
// blocks (CravadaWeb.cuts; gamerule soulsBlockDestruction; Stark 2026-11-09: no blocks thrown around), throwing everyone
// on it. The flight is the preview's own ballistics, replayed from BTrack (tools/sukuna/bake_b.js). Stark 2026-11-09:
// twice the sprint's reach, and the camera stays free: the caster's own client flies him along the same track
// (CravadaRenderer.onPlayerTick) so it glides, and the server only puts him back when he drifts (Freeze.holdDriven).
public final class Cravada {
    // Ticks: the crouch before the sprint, the sprint's longest.
    private static final int PREP = 3, DASH = 20;
    private static final double SPEED = 1.3, DISTANCE = 24, GRAB_REACH = 1.2, GRAB_RADIUS = 0.8, LOCK_RANGE = 28;
    // How far the caster's client may drift from the track before the server puts him back (blocks).
    private static final double SLACK = 3.5;
    // The webs' reach (blocks, the preview's S_WEB), and the ground that blows up.
    private static final double[] WEB_R = {5.0, 8.6, 13.2};
    private static final double BURST_R = 13.4;
    private static final float UPPERCUT = 12, SLAM = 25, STOMP = 15, WEB = 8, BOOM = 20, BURST = 10;
    private static final int COOLDOWN = 500, MISS_COOLDOWN = 50, BROKEN_COOLDOWN = 100;

    // Who is in the middle of one, and until when at the latest (game time): a B that never got to its end (its task
    // dropped, an error) used to leave him marked busy for good, and the key did nothing from then on (Stark
    // 2026-11-09: "aperto e não acontece nada").
    private static final Map<LivingEntity, Long> BUSY = new WeakHashMap<>();

    private Cravada() {
    }

    public static boolean use(LivingEntity caster, ServerLevel level) {
        if (!Movement.free(caster)) {
            SubCommon.warn(caster, "sukuna.b_cant");
            return false;
        }
        Long until = BUSY.get(caster);
        if (until != null && level.getGameTime() < until) {
            return false;
        }
        PowerData data = caster.getData(ModAttachments.POWER);
        int interrupts = data.interrupts;
        data.busyTicks = Math.max(data.busyTicks, PREP + DASH + 3);
        BUSY.put(caster, level.getGameTime() + PREP + DASH + 3 + BTrack.BURST + 16);
        LivingEntity locked = lock(caster);
        anim(caster, "sukuna.b_corre");
        Vec3[] dir = {locked != null ? flatTo(caster, locked) : Combat.flatLook(caster)};
        double[] traveled = {0};
        Vec3[] last = {caster.position()};
        int seed = caster.getRandom().nextInt(0xFFFF);
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
                send(caster, SukunaCravada.RUN, DASH, caster.position(), dir[0], seed);
                Fx.sound(level, caster.position(), SoundEvents.PLAYER_ATTACK_STRONG, 1f, 0.55f);
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
                launch(caster, caught, level, data, seed);
                return true;
            }
            // a step, a slab or a block up in the way: he hops it on the run (a scripted run, 2026-11-09, missed on every
            // one); only a wall two high stops him
            boolean hop = t > 1 && caster.horizontalCollision && canHop(level, caster, dir[0]);
            if (step <= 0.05 || t > DASH + 2 || t > 1 && caster.horizontalCollision && !hop) {
                BUSY.remove(caster);
                data.busyTicks = 0;
                Vec3 v = caster.getDeltaMovement();
                caster.setDeltaMovement(v.x * 0.2, v.y, v.z * 0.2);
                caster.hurtMarked = true;
                anim(caster, "");
                send(caster, SukunaCravada.CANCEL, 0, Vec3.ZERO, Vec3.ZERO, seed);
                SubCommon.warn(caster, "sukuna.b_miss");
                cooldown(caster, MISS_COOLDOWN);
                return true;
            }
            caster.setDeltaMovement(dir[0].x * step, hop ? 0.5 : Math.min(caster.getDeltaMovement().y, 0.1), dir[0].z * step);
            caster.hurtMarked = true;
            caster.resetFallDistance();
            return false;
        });
        return true;
    }

    // Room for him a block up and a little ahead.
    private static boolean canHop(ServerLevel level, LivingEntity caster, Vec3 dir) {
        return level.noCollision(caster, caster.getBoundingBox().move(dir.x * 0.6, 1.05, dir.z * 0.6));
    }

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

    // The uppercut landed: from here the two of them fly on BTrack, in his frame at this moment.
    private static void launch(LivingEntity caster, LivingEntity target, ServerLevel level, PowerData data, int seed) {
        Capture.take(target);
        // Whatever the target was doing ends here (Stark 2026-11-09: caught in the middle of Madara's attack, the attack
        // kept moving and holding him, so the two of them ended up apart while the animation played on): its moves are
        // cut off and the holds they put on it let go, so the only thing moving it is this.
        PowerLogic.interrupt(target);
        Freeze.freeOwn(target);
        Vec3 fwd = flatTo(caster, target);
        Vec3 left = new Vec3(fwd.z, 0, -fwd.x);
        float yRot = (float) (Mth.atan2(-fwd.x, fwd.z) * Mth.RAD_TO_DEG);
        double[] s0 = BTrack.SUKUNA[0];
        Vec3 origin = caster.position().subtract(frame(left, fwd, s0[0], s0[1], s0[2]));
        Vec3 targetStart = target.position();
        int length = BTrack.LENGTH;
        double tScale = target.getBbHeight() / 1.5;
        caster.setDeltaMovement(Vec3.ZERO);
        caster.setYRot(yRot);
        caster.setYHeadRot(yRot);
        caster.hurtMarked = true;
        float targetYaw = yRot + 180;
        target.setYRot(targetYaw);
        target.setYHeadRot(targetYaw);
        target.setYBodyRot(targetYaw);
        double[] groundS = {Double.NaN}, groundT = {Double.NaN};
        send(caster, SukunaCravada.TRACK, target.getId(), origin, fwd, seed);
        Runnable freeCaster = Freeze.holdDriven(caster, BTrack.BURST + 12, age -> {
            double[] p = BTrack.SUKUNA[Math.min(age, length)];
            Vec3 at = origin.add(frame(left, fwd, p[0], p[1], p[2]));
            return age >= BTrack.LAND - 2 ? onGround(level, at, groundS, caster) : at;
        }, SLACK);
        // from this tick: his client is on the track already, before the hold's first tick turns it on (a scripted run
        // on steps had him "moved wrongly" and snapped back once at the start of the flight)
        if (caster instanceof ServerPlayer) {
            caster.noPhysics = true;
        }
        Runnable freeTarget = Freeze.holdLookFree(target, BTrack.BURST, age -> {
            double[] p = BTrack.TARGET[Math.min(age, length)];
            Vec3 c = origin.add(frame(left, fwd, p[0], p[1], p[2]));
            Vec3 feet = new Vec3(c.x, c.y - 0.75 * tScale, c.z);
            if (age <= BTrack.LAUNCH) {
                feet = targetStart.lerp(feet, age / (double) (BTrack.LAUNCH + 1));
            }
            return age >= BTrack.IMPACT - 1 ? onGround(level, feet, groundT, target) : feet;
        });
        // the holds are this move's: if another one takes either of them (a counter, a cutscene, a grab), it stops
        // instead of playing on with the two of them somewhere else
        net.minecraft.world.entity.Entity holdsCaster = Freeze.holder(caster), holdsTarget = Freeze.holder(target);
        int total = BTrack.BURST + 12;
        data.busyTicks = Math.max(data.busyTicks, total);
        Stun.apply(target, BTrack.BURST + 10);
        PowerLogic.seal(target, BTrack.BURST);
        Capture.grabbed(target, caster, BTrack.BURST);
        anim(caster, "sukuna.b_golpe");
        anim(target, "sukuna.b_alvo");
        int interrupts = data.interrupts;
        Tasks.add(age -> {
            boolean alive = target.isAlive() && !target.isRemoved();
            boolean taken = age > 0 && age < total && (Freeze.holder(caster) != holdsCaster
                    || alive && age < BTrack.BURST && Freeze.holder(target) != holdsTarget);
            if (!caster.isAlive() || data.interrupts != interrupts || taken) {
                freeCaster.run();
                freeTarget.run();
                BUSY.remove(caster);
                send(caster, SukunaCravada.CANCEL, 0, Vec3.ZERO, Vec3.ZERO, seed);
                anim(caster, "");
                anim(target, "");
                data.busyTicks = 0;
                cooldown(caster, BROKEN_COOLDOWN);
                return true;
            }
            caster.resetFallDistance();
            target.resetFallDistance();
            Vec3 tc = Combat.center(target);
            if (age == BTrack.HIT) {
                if (alive) {
                    Combat.hurt(caster, target, UPPERCUT);
                }
                send(caster, SukunaCravada.HIT, target.getId(), tc.subtract(fwd.scale(0.2)), fwd, seed);
                Fx.sound(level, tc, SoundEvents.PLAYER_ATTACK_CRIT, 1.4f, 0.6f);
                Fx.sound(level, tc, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.4f, 0.5f);
                shake(caster, tc, 0.35f, 6);
            }
            if (age == BTrack.CATCH) {
                Fx.sound(level, tc, SoundEvents.PLAYER_ATTACK_STRONG, 1.2f, 0.6f);
            }
            if (age == BTrack.RELEASE) {
                send(caster, SukunaCravada.RELEASE, BTrack.IMPACT - BTrack.RELEASE, tc, fwd, seed);
                Fx.sound(level, tc, SoundEvents.PLAYER_ATTACK_SWEEP, 1.4f, 0.5f);
                shake(caster, tc, 0.3f, 5);
            }
            if (age == BTrack.IMPACT) {
                Vec3 at = target.position();
                if (alive) {
                    Combat.hurt(caster, target, SLAM);
                }
                send(caster, SukunaCravada.IMPACT, 0, at, fwd, seed);
                Fx.sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 1.4f, 0.6f);
                Fx.sound(level, at, SoundEvents.ANVIL_LAND, 1f, 0.5f);
                shake(caster, at, 1f, 16);
            }
            if (age == BTrack.LAND) {
                Vec3 chest = target.position();
                if (alive) {
                    Combat.hurt(caster, target, STOMP);
                }
                send(caster, SukunaCravada.LAND, 0, chest, fwd, seed);
                Fx.sound(level, chest, SoundEvents.PLAYER_ATTACK_CRIT, 1.6f, 0.5f);
                Fx.sound(level, chest, SoundEvents.TRIDENT_THUNDER.value(), 0.6f, 1.6f);
                shake(caster, chest, 0.8f, 14);
                web(caster, target, level, chest, WEB_R[0], WEB, false);
            }
            if (age == BTrack.WEB2 || age == BTrack.WEB3) {
                Vec3 chest = target.position();
                web(caster, target, level, chest, age == BTrack.WEB2 ? WEB_R[1] : WEB_R[2], WEB, false);
                Fx.sound(level, chest, SoundEvents.PLAYER_ATTACK_SWEEP, 1.6f, 0.4f);
                shake(caster, chest, age == BTrack.WEB2 ? 0.25f : 0.35f, 6);
            }
            if (age == BTrack.BOOM) {
                Vec3 chest = target.position();
                if (alive) {
                    Combat.hurt(caster, target, BOOM);
                }
                web(caster, target, level, chest, BURST_R, BOOM, false);
                Fx.sound(level, chest, SoundEvents.WARDEN_SONIC_BOOM, 1.6f, 0.7f);
                Fx.sound(level, chest, SoundEvents.PLAYER_ATTACK_SWEEP, 2f, 0.4f);
                shake(caster, chest, 0.8f, 8);
            }
            if (age == BTrack.BURST) {
                Vec3 chest = target.position();
                freeTarget.run();
                send(caster, SukunaCravada.BURST, BlockRules.canBreak(level) ? 1 : 0, chest, fwd, seed);
                burst(level, chest, fwd);
                if (alive) {
                    Combat.hurt(caster, target, BURST);
                    target.setDeltaMovement(fwd.x * 0.2, 0.9, fwd.z * 0.2);
                    target.hurtMarked = true;
                }
                web(caster, target, level, chest, BURST_R, BURST, true);
                Fx.sound(level, chest, SoundEvents.GENERIC_EXPLODE.value(), 2.2f, 0.5f);
                Fx.sound(level, chest, SoundEvents.STONE_BREAK, 2f, 0.6f);
                shake(caster, chest, 1.4f, 20);
                anim(target, "");
            }
            if (age >= total) {
                freeCaster.run();
                BUSY.remove(caster);
                cooldown(caster, COOLDOWN);
                return true;
            }
            return false;
        });
    }

    // Everyone else on the web, out to this reach: hurt (and thrown up when the ground blows).
    private static void web(LivingEntity caster, LivingEntity target, ServerLevel level, Vec3 at, double reach, float damage, boolean throwUp) {
        for (LivingEntity e : Combat.inRadius(level, at, reach, caster)) {
            if (e == target || Math.abs(e.getY() - at.y) > 3) {
                continue;
            }
            if (Combat.hurt(caster, e, damage)) {
                if (throwUp) {
                    Combat.pushFrom(e, at, 0.3, 0.9);
                } else {
                    Stun.apply(e, 8);
                }
            }
        }
    }

    // The ground blows up along the web: its shape cut out of the world's blocks (CravadaWeb.cuts), nothing thrown.
    private static void burst(ServerLevel level, Vec3 at, Vec3 fwd) {
        if (!BlockRules.canBreak(level)) {
            return;
        }
        for (BlockPos pos : CravadaWeb.cuts(level, at, fwd)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    // Kept on the ground from here on: the track's height, or the floor under it if that is higher (or a little lower:
    // the flight is planned on flat ground).
    private static Vec3 onGround(ServerLevel level, Vec3 at, double[] memo, LivingEntity who) {
        Vec3 from = at.add(0, 3, 0);
        var hit = level.clip(new ClipContext(from, from.add(0, -9, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, who));
        if (hit.getType() == HitResult.Type.MISS) {
            return at;
        }
        memo[0] = hit.getLocation().y;
        return new Vec3(at.x, Math.max(at.y, memo[0]), at.z);
    }

    private static Vec3 frame(Vec3 left, Vec3 fwd, double x, double y, double z) {
        return left.scale(x).add(0, y, 0).add(fwd.scale(z));
    }

    private static void send(LivingEntity caster, int kind, int ticks, Vec3 a, Vec3 b, int seed) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.SukunaCravada(caster.getId(), kind, ticks, a, b, seed));
    }

    private static void shake(LivingEntity caster, Vec3 at, float strength, int ticks) {
        for (ServerPlayer viewer : Combat.level(caster).players()) {
            double d = viewer.position().distanceTo(at);
            if (d < 48) {
                PacketDistributor.sendToPlayer(viewer, new ModPayloads.ScreenShake((float) (strength * (1 - d / 48)), ticks));
            }
        }
    }

    private static Vec3 flatTo(LivingEntity from, LivingEntity to) {
        Vec3 d = to.position().subtract(from.position());
        Vec3 flat = new Vec3(d.x, 0, d.z);
        return flat.lengthSqr() < 1.0E-4 ? Combat.flatLook(from) : flat.normalize();
    }

    private static void cooldown(LivingEntity caster, int ticks) {
        PowerData data = caster.getData(ModAttachments.POWER);
        if (data.noCooldown) {
            return;
        }
        int slot = PowerData.SLOT_EXTRA;
        data.cooldowns[slot] = Math.max(data.cooldowns[slot], ticks);
        if (caster instanceof ServerPlayer player) {
            PowerLogic.sync(player);
        }
    }

    private static void anim(LivingEntity entity, String move) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, new ModPayloads.PlaySkillAnimation(entity.getId(), move, false));
    }
}
