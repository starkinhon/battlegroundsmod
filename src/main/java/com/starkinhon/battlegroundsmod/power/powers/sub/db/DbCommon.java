package com.starkinhon.battlegroundsmod.power.powers.sub.db;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.ModEffects;
import com.starkinhon.battlegroundsmod.combat.BlockRules;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

// Shared bits of the Dragon Ball SubAlmas (Goku, Vegeta, Freeza, Piccolo, Kuririn, Mestre Kame): where the hands are,
// the ki beam that hurts along its length, the buffs that change the base fighting (Kaioken, Max Power) and the
// defenses checked on every hit (Suiken's dodges, the Taiyoken's stun broken by a hit from the front).
public final class DbCommon {
    public static final String KAIOKEN = "db_kaioken";
    public static final String MAX_POWER = "db_max_power";
    public static final String SUIKEN = "db_suiken";
    public static final String BLIND = "db_blind";
    private static final Map<Integer, Long> LAST_COUNTER = new HashMap<>();

    private DbCommon() {
    }

    // Where a beam goes: at whoever the combat flight is locked on (the look of a flying player reaches the server a tick
    // late and the beam went off to the side in the 2026-11-13 test), else along the look.
    public static Vec3 aim(LivingEntity caster) {
        LivingEntity locked = com.starkinhon.battlegroundsmod.combat.AirFight.target(caster);
        if (locked != null && locked.isAlive() && locked.distanceTo(caster) <= 48) {
            Vec3 d = com.starkinhon.battlegroundsmod.combat.Combat.center(locked).subtract(caster.getEyePosition());
            if (d.lengthSqr() > 1.0E-4) {
                return d.normalize();
            }
        }
        return caster.getLookAngle();
    }

    // Both hands brought together in front of the chest (Kamehameha, Final Flash).
    public static Vec3 hands(LivingEntity caster) {
        Vec3 look = aim(caster);
        return caster.position().add(0, caster.getBbHeight() * 0.58, 0).add(look.scale(0.55));
    }

    // The right hand held out.
    public static Vec3 hand(LivingEntity caster) {
        Vec3 look = caster.getLookAngle();
        Vec3 right = SubCommon.flat(look).cross(new Vec3(0, 1, 0));
        return caster.position().add(0, caster.getBbHeight() * 0.62, 0).add(right.scale(0.38)).add(look.scale(0.45));
    }

    // The left hand held out.
    public static Vec3 handLeft(LivingEntity caster) {
        Vec3 look = caster.getLookAngle();
        Vec3 right = SubCommon.flat(look).cross(new Vec3(0, 1, 0));
        return caster.position().add(0, caster.getBbHeight() * 0.62, 0).add(right.scale(-0.38)).add(look.scale(0.45));
    }

    // A finger raised over the head (Death Ball, Kienzan, Death Saucer).
    public static Vec3 raised(LivingEntity caster) {
        Vec3 right = SubCommon.flat(caster.getLookAngle()).cross(new Vec3(0, 1, 0));
        return caster.position().add(0, caster.getBbHeight() * 1.2, 0).add(right.scale(0.35));
    }

    // Two fingers on the forehead (Instant Transmission, Makankosappo).
    public static Vec3 forehead(LivingEntity caster) {
        Vec3 look = caster.getLookAngle();
        return caster.getEyePosition().add(look.scale(0.3)).add(0, 0.12, 0);
    }

    public static double groundY(ServerLevel level, Vec3 at) {
        return SubCommon.ground(level, at).y;
    }

    // Where a beam from `from` along `dir` stops: the first block, or the first one hit (pierce = goes through everyone).
    public static Vec3 beamEnd(LivingEntity caster, ServerLevel level, Vec3 from, Vec3 dir, double range, double radius, boolean pierce) {
        Vec3 far = from.add(dir.scale(range));
        var hit = level.clip(new net.minecraft.world.level.ClipContext(from, far, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, caster));
        Vec3 end = hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? far : hit.getLocation();
        if (!pierce) {
            double best = from.distanceTo(end);
            for (LivingEntity t : Combat.alongLine(level, from, end, radius, caster)) {
                double d = Combat.center(t).distanceTo(from);
                if (d < best) {
                    best = d;
                }
            }
            end = from.add(dir.scale(best));
        }
        return end;
    }

    // Everyone along a line from `from` to `to`, hurt by damage each, once per `every` ticks (hitAt keeps track). Every
    // beam that hurts this way also scars the ground under it.
    public static void hurtAlong(LivingEntity caster, ServerLevel level, Vec3 from, Vec3 to, double radius, float damage, int age, int every,
            Map<Integer, Integer> hitAt, Vec3 push) {
        // The beam's body tears the ground it runs over, not only its blast (DbScar).
        DbScar.along(caster, level, from, to, radius);
        for (LivingEntity t : Combat.alongLine(level, from, to, radius, caster)) {
            Integer last = hitAt.get(t.getId());
            if (last != null && age - last < every) {
                continue;
            }
            hitAt.put(t.getId(), age);
            Combat.hurtMagic(caster, t, damage);
            if (push != null) {
                t.setDeltaMovement(t.getDeltaMovement().scale(0.3).add(push));
                t.hurtMarked = true;
            }
        }
    }

    public static void hurtRadius(LivingEntity caster, ServerLevel level, Vec3 at, double radius, float damage, double push, double up) {
        for (LivingEntity t : Combat.inRadius(level, at, radius, caster)) {
            double k = 1 - 0.5 * Math.min(1, Combat.center(t).distanceTo(at) / Math.max(0.1, radius));
            Combat.hurtMagic(caster, t, (float) (damage * k));
            if (push > 0) {
                Combat.pushFrom(t, at, push * k, up);
            }
        }
    }

    public static void boom(ServerLevel level, Vec3 at, float radius, int color, int seed) {
        SubNet.send(level, SubNet.KI_BOOM, 0, at, new Vec3(groundY(level, at), 0, 0), radius, color, seed);
        SubCommon.sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 1f + radius * 0.2f, 1.2f - Math.min(0.5f, radius * 0.08f));
    }

    // A big crater (the charged Genki Dama, tens of blocks wide): a ring a tick from the middle out so the server never
    // breaks it all at once (like Bills' Destruction Touch), deep in the middle and shallow at the rim, with what stands
    // over it (trees, houses) gone too. No drops, no neighbor updates; some blocks on the way are thrown out instead,
    // the ones nearer the middle higher. Only with soulsBlockDestruction (and mobGriefing).
    public static void bigCrater(ServerLevel level, Vec3 center, double radius, double depth, int ticks) {
        if (radius <= 0 || !BlockRules.canBreak(level)) {
            return;
        }
        int r = (int) Math.ceil(radius);
        int up = (int) Math.ceil(Math.min(radius * 0.5, 24));
        BlockPos mid = BlockPos.containing(center);
        int[] flown = {0};
        int flyMax = (int) Math.min(60, 8 + radius);
        Tasks.repeat(Math.max(1, ticks), tick -> {
            double from = radius * tick / ticks;
            double to = radius * (tick + 1) / ticks;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    double flat = Math.sqrt(dx * dx + dz * dz);
                    if (flat < from || flat >= to && tick < ticks - 1 || flat > radius) {
                        continue;
                    }
                    double k = flat / radius;
                    // A ragged rim: each column a little deeper or shallower than its neighbors.
                    double rough = 0.85 + 0.3 * SubCommon.RANDOM.nextDouble();
                    int bottom = -(int) Math.round(depth * Math.sqrt(Math.max(0, 1 - k * k)) * rough);
                    int top = (int) Math.round(up * (1 - k * 0.5));
                    for (int dy = top; dy >= bottom; dy--) {
                        BlockPos block = mid.offset(dx, dy, dz);
                        if (!level.isLoaded(block)) {
                            continue;
                        }
                        BlockState state = level.getBlockState(block);
                        if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity() || state.getDestroySpeed(level, block) < 0) {
                            continue;
                        }
                        if (dy <= 1 && dy >= bottom - 1 && flown[0] < flyMax && SubCommon.RANDOM.nextInt(40) == 0) {
                            flown[0]++;
                            FallingBlockEntity thrown = FallingBlockEntity.fall(level, block, state);
                            thrown.dropItem = false;
                            Vec3 out = new Vec3(dx, 0, dz);
                            out = out.lengthSqr() < 1.0E-4 ? Vec3.ZERO : out.normalize();
                            thrown.setDeltaMovement(out.scale(0.5 + 0.8 * SubCommon.RANDOM.nextDouble()).add(0, 0.6 + 1.0 * (1 - k) * SubCommon.RANDOM.nextDouble(), 0));
                            thrown.hurtMarked = true;
                        } else {
                            com.starkinhon.battlegroundsmod.combat.BlockBreakFx.erase(level, block, state, Block.UPDATE_CLIENTS);
                        }
                    }
                }
            }
        });
    }

    public static void screen(ServerLevel level, Vec3 at, int preset) {
        SubNet.send(level, SubNet.SCREEN, 0, at, Vec3.ZERO, preset, 0, 0);
    }

    public static void hit(ServerLevel level, Vec3 at, float size, int color) {
        SubNet.send(level, SubNet.HIT, 0, at, Vec3.ZERO, size, color, SubCommon.RANDOM.nextInt(50));
    }

    // A move that waits for its key to be pressed again: the cooldown is set by hand when it ends (the first press
    // returns false so the key stays free).
    public static void cooldown(LivingEntity caster, int slot, int ticks) {
        PowerData data = caster.getData(ModAttachments.POWER);
        if (!data.noCooldown && !(caster instanceof Player p && p.isCreative())) {
            data.cooldowns[slot] = Math.max(data.cooldowns[slot], ticks);
        }
        if (caster instanceof net.minecraft.server.level.ServerPlayer player) {
            com.starkinhon.battlegroundsmod.power.PowerLogic.sync(player);
        }
    }

    // ---------- the base fighting under a buff ----------

    // Damage of the base punches: Kaioken +50%, Max Power +40%.
    public static float meleeMultiplier(LivingEntity attacker) {
        float k = 1;
        if (SubCommon.hasState(attacker, KAIOKEN)) {
            k *= 1.5f;
        }
        if (SubCommon.hasState(attacker, MAX_POWER)) {
            k *= 1.4f;
        }
        return k;
    }

    // How much faster the punches come (Kaioken 1.5x).
    public static float punchSpeed(LivingEntity attacker) {
        return SubCommon.hasState(attacker, KAIOKEN) ? 1.5f : 1;
    }

    // Every hit: Suiken lets most punches slip past (and slaps back), a frontal hit wakes whoever the Taiyoken blinded.
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        Entity attacker = event.getSource().getEntity();
        Entity direct = event.getSource().getDirectEntity();
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        boolean melee = attacker != null && attacker == direct && attacker != target && attacker.distanceTo(target) < 5;
        if (melee && SubCommon.hasState(target, SUIKEN) && SubCommon.RANDOM.nextFloat() < 0.6f) {
            event.setCanceled(true);
            Vec3 side = SubCommon.flat(target.position().subtract(attacker.position())).cross(new Vec3(0, 1, 0));
            Vec3 c = Combat.center(target);
            SubNet.send(level, SubNet.STREAK, 0, c.add(side.scale(0.5)).add(target.position().subtract(attacker.position()).normalize().scale(-1)),
                    target.position().subtract(attacker.position()).normalize().scale(1.4), 0.25f, Db.WHITE, 6);
            SubCommon.sound(level, c, SoundEvents.PLAYER_ATTACK_NODAMAGE, 1f, 1.4f);
            Long last = LAST_COUNTER.get(target.getId());
            long now = level.getGameTime();
            if (attacker instanceof LivingEntity enemy && (last == null || now - last >= 10)) {
                LAST_COUNTER.put(target.getId(), now);
                Kame.palm(target, level, enemy);
            }
            return;
        }
        if (attacker != null && attacker != target && SubCommon.hasState(target, BLIND)) {
            Vec3 toAttacker = SubCommon.flat(attacker.position().subtract(target.position()));
            if (toAttacker.dot(SubCommon.flat(target.getLookAngle())) > 0.2) {
                // The fright of a hit from the front wakes them on the spot (from behind it doesn't).
                wake(level, target);
            }
        }
    }

    public static void wake(ServerLevel level, LivingEntity target) {
        SubCommon.clearState(target, BLIND);
        target.removeEffect(ModEffects.ATORDOADO);
        target.removeEffect(MobEffects.BLINDNESS);
        SubNet.send(level, SubNet.DIZZY, target.getId(), target.position(), Vec3.ZERO, 0, 0, 0);
        screen(level, Combat.center(target), 17);
    }

    // Lose the target (mobs and bosses blinded by the Taiyoken).
    public static void loseTarget(LivingEntity target) {
        if (target instanceof Mob mob) {
            mob.setTarget(null);
        }
    }

    // A one-time set of who was hit (a disc that cuts each one once on the way).
    public static Set<Integer> once() {
        return new HashSet<>();
    }

    public static void later(int ticks, Runnable r) {
        Tasks.later(ticks, r);
    }

    // The world closed (ServerReset): nothing of it is kept for the next one (old entities, old game times).
    public static void resetStatics() {
        LAST_COUNTER.clear();
    }
}
