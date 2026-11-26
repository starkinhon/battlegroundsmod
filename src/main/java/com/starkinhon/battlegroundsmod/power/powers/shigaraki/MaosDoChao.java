// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.power.powers.shigaraki;

import java.util.Map;
import java.util.WeakHashMap;

import com.starkinhon.battlegroundsmod.ModSounds;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.Vec3;

// C, Mãos do Chão (remake 2026-11-26, preview maos-do-chao-v2, Stark: "ta perfeito dmais"). The palm slaps the ground
// (0.3 s) and a run of hands bursts out of it, one after another, along the ground after his aim: 16 blocks a second, up
// to 24 blocks. It turns toward the target he locks (or the point of his crosshair) at most 0.9 rad a second, and only
// while it is in front of it (46 degrees), so a dash to the side at the right time gets away. Where it touches someone
// (1.5 blocks), 8 hands burst out around them and hold them 2.6 s; then they crumble to dust (0.8 s). Combo: held, then Z.
// Damage and cooldown are the old trap's. It stops at a wall (more than 1.5 blocks up) or a drop with no ground under.
public final class MaosDoChao {
    static final int HOLD = 52;
    static final int START = 7, MAX_RUN = 30;
    static final double SPEED = 0.8, RANGE = 24, TURN = 0.045, CONE = 0.8, HIT = 1.5, STEP_UP = 1.5;
    private static final float DAMAGE = 18;

    private static final Map<LivingEntity, int[]> TRAP = new WeakHashMap<>();

    private MaosDoChao() {
    }

    public static boolean use(LivingEntity caster, ServerLevel level) {
        int id = SubNet.newId();
        int[] mine = {id};
        TRAP.put(caster, mine);
        LivingEntity aim = Toque.lock(caster);
        Vec3 feet = caster.position();
        Vec3 dir0 = aim != null && aim.distanceTo(caster) < RANGE + 4 ? Shigaraki.flatTo(caster, aim.position()) : Combat.flatLook(caster);
        Vec3 start = SubCommon.ground(level, feet.add(dir0.scale(0.9)).add(0, 1, 0));
        SubCommon.busy(caster, START + 2);
        SubCommon.pose(caster, "shig.tapa");
        SubNet.send(level, Shigaraki.TRAP, id, start, dir0, 0, 0, caster.getId());
        // the run: [0] front, [1] its way, [2] how far it went
        Vec3[] run = {start, dir0};
        double[] gone = {0};
        Tasks.add(age -> {
            if (age == 6) {
                SubCommon.sound(level, feet, SoundEvents.GRAVEL_BREAK, 1.3f, 0.5f);
                SubCommon.sound(level, feet, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1f, 0.5f);
                SubCommon.sound(level, feet, ModSounds.SHIGARAKI_C_PLANTAR.get(), 1.3f, 1.0f);
            }
            if (TRAP.get(caster) != mine || !caster.isAlive() || caster.level() != level) {
                SubNet.send(level, Shigaraki.TRAP, id, run[0], run[1], 3, 0, 0);
                if (TRAP.get(caster) == mine) {
                    TRAP.remove(caster);
                }
                return true;
            }
            if (age < START) {
                return false;
            }
            SubCommon.busy(caster, 3);
            Vec3 front = run[0], dir = steer(caster, front, run[1]);
            double step = Math.min(SPEED, RANGE - gone[0]);
            Vec3 next = new Vec3(front.x + dir.x * step, front.y, front.z + dir.z * step);
            Vec3 floor = floor(level, next);
            boolean blocked = floor == null || floor.y - front.y > STEP_UP;
            if (!blocked) {
                next = floor;
                gone[0] += step;
                run[0] = next;
                run[1] = dir;
                SubNet.send(level, Shigaraki.TRAP, id, next, dir, 4, 0, 0);
                if ((age & 1) == 0) {
                    SubCommon.sound(level, next, SoundEvents.ROOTED_DIRT_BREAK, 0.7f, 0.55f + (age % 5) * 0.06f);
                }
                LivingEntity hit = touched(caster, level, front, next);
                if (hit != null) {
                    caught(caster, hit, level, id, dir);
                    SubCommon.pose(caster, "shig.tapa_sai");
                    TRAP.remove(caster);
                    return true;
                }
            }
            if (blocked || gone[0] >= RANGE - 1e-6 || age > START + MAX_RUN) {
                SubNet.send(level, Shigaraki.TRAP, id, run[0], run[1], 3, 0, 0);
                SubCommon.pose(caster, "shig.tapa_sai");
                TRAP.remove(caster);
                return true;
            }
            return false;
        });
        return true;
    }

    // The way the run goes this tick: toward the target he locks now, else the point of his crosshair, turning at most
    // TURN, and only when that is within CONE of where it goes (behind it, it runs straight on).
    private static Vec3 steer(LivingEntity caster, Vec3 front, Vec3 dir) {
        LivingEntity aim = Toque.lock(caster);
        Vec3 to = aim != null ? aim.position() : SubCommon.aimPoint(caster, 40);
        Vec3 want = new Vec3(to.x - front.x, 0, to.z - front.z);
        if (want.lengthSqr() < 1.0) {
            return dir;
        }
        want = want.normalize();
        double ang = Math.atan2(dir.x * want.z - dir.z * want.x, dir.x * want.x + dir.z * want.z);
        if (Math.abs(ang) > CONE) {
            return dir;
        }
        double turn = Math.signum(ang) * Math.min(Math.abs(ang), TURN), c = Math.cos(turn), sn = Math.sin(turn);
        return new Vec3(dir.x * c - dir.z * sn, 0, dir.x * sn + dir.z * c).normalize();
    }

    // The ground at a point of the run: from 2 blocks over it down to 6 under; null when there is none (a drop, the void).
    private static Vec3 floor(ServerLevel level, Vec3 at) {
        Vec3 from = at.add(0, STEP_UP + 0.5, 0);
        HitResult hit = level.clip(new ClipContext(from, at.add(0, -6, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS ? null : hit.getLocation();
    }

    // Who the front touched on its way from a to b this tick: within HIT of the segment (flat), on the ground or as
    // high over it as the ground moves reach (SubCommon.SHADOW_REACH_UP: the hands pull them down).
    private static LivingEntity touched(LivingEntity caster, ServerLevel level, Vec3 a, Vec3 b) {
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        Vec3 mid = a.add(b).scale(0.5);
        for (LivingEntity e : Combat.inRadius(level, mid, HIT + SPEED + 2, caster)) {
            double abx = b.x - a.x, abz = b.z - a.z, len2 = abx * abx + abz * abz;
            double k = len2 < 1e-6 ? 0 : Math.max(0, Math.min(1, ((e.getX() - a.x) * abx + (e.getZ() - a.z) * abz) / len2));
            double dx = e.getX() - (a.x + abx * k), dz = e.getZ() - (a.z + abz * k), d2 = dx * dx + dz * dz;
            if (d2 > HIT * HIT || e.getY() < b.y - 1.5 || SubCommon.aboveGround(level, e.position(), 64) > SubCommon.SHADOW_REACH_UP) {
                continue;
            }
            if (d2 < bd) {
                bd = d2;
                best = e;
            }
        }
        return best;
    }

    // Held where the run touched it: pulled down to the ground if it was over it, the hands grip in 0.3-0.7 s and hold
    // 2.6 s (it shakes), then crumble.
    private static void caught(LivingEntity caster, LivingEntity target, ServerLevel level, int id, Vec3 dir) {
        Vec3 at = SubCommon.ground(level, target.position().add(0, 0.5, 0));
        if (SubCommon.aboveGround(level, target.position(), 64) > 0.6) {
            Vec3 down = floorUnder(level, target.position());
            if (down != null) {
                at = down;
                target.teleportTo(at.x, at.y, at.z);
            }
        }
        Vec3 pin = new Vec3(target.getX(), at.y, target.getZ());
        SubNet.send(level, Shigaraki.TRAP, id, pin, dir, 2, target.getId(), 0);
        SubCommon.sound(level, pin, SoundEvents.ROOTED_DIRT_BREAK, 1.5f, 0.5f);
        SubCommon.sound(level, pin, SoundEvents.GRAVEL_BREAK, 1.5f, 0.6f);
        SubCommon.sound(level, pin, ModSounds.SHIGARAKI_C_PEGAR.get(), 1.5f, 1.0f);
        Combat.hurt(caster, target, DAMAGE * Shigaraki.DAMAGE_MULT);
        Stun.apply(target, HOLD + 4);
        Tasks.addSystem(age -> {
            if (!target.isAlive() || age >= HOLD) {
                return true;
            }
            // pinned: the hands don't let it move
            target.setDeltaMovement(0, Math.min(0, target.getDeltaMovement().y), 0);
            if (target.distanceToSqr(pin.x, target.getY(), pin.z) > 0.04) {
                target.teleportTo(pin.x, target.getY(), pin.z);
            }
            target.hurtMarked = true;
            return false;
        });
        Tasks.later(HOLD, () -> SubCommon.sound(level, pin, SoundEvents.SAND_BREAK, 1.2f, 0.5f));
    }

    // The ground straight under a body (up to SHADOW_REACH_UP + 1 down), or null.
    private static Vec3 floorUnder(ServerLevel level, Vec3 at) {
        HitResult hit = level.clip(new ClipContext(at.add(0, 0.2, 0), at.add(0, -SubCommon.SHADOW_REACH_UP - 1, 0), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS ? null : hit.getLocation();
    }
}
