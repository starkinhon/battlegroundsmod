package com.starkinhon.battlegroundsmod.combat;

import java.util.Comparator;
import java.util.List;

import com.starkinhon.battlegroundsmod.power.Tiers;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// Targeting, damage and knockback shared by every power.
public final class Combat {

    private Combat() {
    }

    public static boolean isTarget(Entity entity, Entity owner) {
        return entity instanceof LivingEntity living
                && entity != owner
                && living.isAlive()
                && !entity.isSpectator()
                && !(entity instanceof ArmorStand)
                && !Intangible.is(entity);
    }

    // Someone the aim may lock onto. Shibuya's pedestrians (npc/Pedestre) can be hit and killed by any blow, but the aim
    // never picks them: a technique aimed at an enemy behind a crowd goes to the enemy.
    public static boolean lockable(Entity entity) {
        return !(entity instanceof com.starkinhon.battlegroundsmod.npc.Pedestre);
    }

    // The server level an entity lives in. Only call this for server-side entities.
    public static ServerLevel level(Entity entity) {
        return (ServerLevel) entity.level();
    }

    public static Vec3 center(Entity entity) {
        return entity.position().add(0, entity.getBbHeight() / 2, 0);
    }

    // Horizontal look direction; safe when the player looks straight up or down.
    public static Vec3 flatLook(Entity entity) {
        return Vec3.directionFromRotation(0, entity.getYRot());
    }

    public static Vec3 handPos(LivingEntity player) {
        return player.getEyePosition().add(player.getLookAngle().scale(0.6)).add(0, -0.35, 0);
    }

    public static List<LivingEntity> inRadius(ServerLevel level, Vec3 center, double radius, Entity owner) {
        // Freeza Poder Total's island in the air breaks under any blow of his enemies.
        com.starkinhon.battlegroundsmod.power.powers.freezaboss.FreezaTotal.probeBall(center, radius, owner);
        return level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(center, radius * 2, radius * 2, radius * 2),
                e -> isTarget(e, owner) && center(e).distanceToSqr(center) <= radius * radius);
    }

    public static List<LivingEntity> inCone(LivingEntity player, double range, double minDot) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        return inRadius(level(player), eye, range + 1, player).stream()
                .filter(e -> {
                    Vec3 to = center(e).subtract(eye);
                    return to.length() <= range + e.getBbWidth() && (to.lengthSqr() < 1 || to.normalize().dot(look) >= minDot);
                })
                .toList();
    }

    public static List<LivingEntity> alongLine(ServerLevel level, Vec3 start, Vec3 end, double radius, Entity owner) {
        com.starkinhon.battlegroundsmod.power.powers.freezaboss.FreezaTotal.probeLine(start, end, radius, owner);
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(start, end).inflate(radius),
                e -> {
                    if (!isTarget(e, owner)) {
                        return false;
                    }
                    AABB box = e.getBoundingBox().inflate(radius);
                    return box.contains(start) || box.clip(start, end).isPresent();
                });
    }

    // In the combat flight the fight keeps 9-12 blocks off the target (the page's orbit), so a skill made for the ground
    // (the Kaioken rush's 8, a grab's 7) never found it: locked on, every skill reaches it from the whole orbit.
    private static final double LOCK_REACH = 32;

    private static double lockReach(double range) {
        return Math.max(range + 1, LOCK_REACH);
    }

    // Where the player's view hits a block, or the point at max range.
    public static Vec3 raycastPoint(LivingEntity player, double range) {
        LivingEntity locked = AirFight.target(player);
        if (locked != null && (player instanceof net.minecraft.server.level.ServerPlayer || player instanceof com.starkinhon.battlegroundsmod.boss.SoulBoss)
                && locked.distanceTo(player) <= lockReach(range)) {
            // the combat flight aims at whoever it is locked on
            return center(locked);
        }
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(range));
        var hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
    }

    // The closest target in the player's view, stopped by walls. Slightly generous so moves feel good to land.
    public static LivingEntity raycastTarget(LivingEntity player, double range) {
        LivingEntity locked = AirFight.target(player);
        if (locked != null && (player instanceof net.minecraft.server.level.ServerPlayer || player instanceof com.starkinhon.battlegroundsmod.boss.SoulBoss)
                && locked.distanceTo(player) <= lockReach(range)) {
            return locked;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 end = raycastPoint(player, range);
        LivingEntity hit = alongLine(level(player), eye, end, 0.6, player).stream()
                .filter(Combat::lockable)
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(eye)))
                .orElse(null);
        return hit != null ? hit : airAim(player, range);
    }

    // Someone flying (a Dragon Ball soul, a player on the flight) near where a player aims, or null. Without a Dragon Ball
    // power there is no combat flight (Stark 2026-11-13): a launched technique still has to land on a flier, so the
    // player's aim is taken onto it (raycastTarget, and PowerLogic.cast turns the look there with the lead).
    public static LivingEntity airAim(LivingEntity player, double range) {
        if (!(player instanceof ServerPlayer) || AirFight.target(player) != null) {
            return null;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double reach = Math.max(range, 24);
        LivingEntity best = null;
        double bestDot = 0.94;
        for (LivingEntity e : level(player).getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(reach))) {
            if (e == player || !e.isAlive() || !isTarget(e, player) || !airborne(e)) {
                continue;
            }
            Vec3 to = center(e).subtract(eye);
            double d = to.length();
            if (d > reach || d < 1.0E-3) {
                continue;
            }
            double dot = to.scale(1 / d).dot(look);
            if (dot > bestDot && player.hasLineOfSight(e)) {
                bestDot = dot;
                best = e;
            }
        }
        return best;
    }

    // Flying: a player on the Dragon Ball flight or a flying soul (boss/AirCombat).
    public static boolean airborne(LivingEntity entity) {
        return entity instanceof Player p ? com.starkinhon.battlegroundsmod.universe.Flight.flying(p)
                : com.starkinhon.battlegroundsmod.boss.AirCombat.flying(entity);
    }

    // Turns a player's look onto a flier near the aim (airAim), a little ahead of where it flies so a launched technique
    // meets it (shots fly about 3 blocks a tick).
    public static void aimAtFlier(LivingEntity player, double range) {
        LivingEntity flier = airAim(player, range);
        if (flier == null) {
            return;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 c = center(flier);
        Vec3 v = new Vec3(flier.getX() - flier.xo, flier.getY() - flier.yo, flier.getZ() - flier.zo);
        if (v.lengthSqr() > 4) {
            v = Vec3.ZERO; // a teleport, not a flight
        }
        Vec3 aim = c.add(v.scale(Math.min(20, c.distanceTo(eye) / 3)));
        Vec3 d = aim.subtract(eye);
        float yaw = (float) (Mth.atan2(-d.x, d.z) * Mth.RAD_TO_DEG);
        float pitch = (float) Mth.clamp(-Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z))), -89, 89);
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        player.setXRot(pitch);
    }

    // Every move of the mod hurts through here or hurtMagic, so the attacker's tier scales them all (see Tiers).
    public static boolean hurt(LivingEntity attacker, LivingEntity target, float damage) {
        return hurtBase(attacker, target, damage * subScale(attacker));
    }

    // The Strength effect counts for the mod's moves too (Stark, 2026-11-10: Strength 255 and the punches did nothing):
    // +30% per level, like vanilla's +3 on a 10 damage hit.
    public static float strength(LivingEntity attacker) {
        var effect = attacker.getEffect(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST);
        return effect == null ? 1f : 1f + 0.3f * (effect.getAmplifier() + 1);
    }

    // The fists (combo punches, Dash Attack, Forte, Barragem): at least 1 damage a hit for a human (Stark 2026-11-10: a
    // Tier 10's punches did 0.3-0.4), the floor growing with the tier's health up to MAX_FLOOR.
    private static final float MAX_FLOOR = 6f;
    public static boolean hurtBasic(LivingEntity attacker, LivingEntity target, float damage) {
        float mult = Tiers.damageMultiplier(attacker) * com.starkinhon.battlegroundsmod.power.powers.sub.nr.NrCommon.damageMultiplier(attacker)
                * com.starkinhon.battlegroundsmod.power.powers.sub.jj.JjCommon.damageMultiplier(attacker)
                * com.starkinhon.battlegroundsmod.power.powers.kokushibo.OniBody.damageMultiplier(attacker);
        // 1 at Tier 10, growing like the tiers grow (their health): Tier 9 1.35, Tier 7 ~2.5, Tier 5 4.5, at most 6 (from
        // Tier 4 up the tier's own scale is about there already). Stark's test 2026-11-10: at Tier 7 a zombie still
        // took 1 per punch.
        float floor = (float) Math.min(MAX_FLOOR, Tiers.health(Tiers.of(attacker)) / Tiers.health(Tiers.HUMANO));
        if (mult > 0 && damage > 0 && damage * mult < floor) {
            damage = floor / mult;
        }
        boolean landed = hurtBase(attacker, target, damage);
        if (landed) {
            // Shigaraki's fists leave the Decadência on the body, piling up (Esfarelar)
            com.starkinhon.battlegroundsmod.power.powers.shigaraki.Esfarelar.onFist(attacker, target);
        }
        return landed;
    }

    // The base attacks (combo, Forte, Barragem, sword arts): already on the new scale for everyone, SubAlmas too.
    public static boolean hurtBase(LivingEntity attacker, LivingEntity target, float damage) {
        damage *= Tiers.damageMultiplier(attacker) * com.starkinhon.battlegroundsmod.power.powers.sub.nr.NrCommon.damageMultiplier(attacker)
                * com.starkinhon.battlegroundsmod.power.powers.sub.jj.JjCommon.damageMultiplier(attacker)
                * com.starkinhon.battlegroundsmod.power.powers.kokushibo.OniBody.damageMultiplier(attacker)
                * strength(attacker);
        // Clear hit immunity so fast multi-hit moves land every hit.
        target.invulnerableTime = 0;
        DamageSource source = attacker instanceof Player player
                ? attacker.damageSources().playerAttack(player)
                : attacker.damageSources().mobAttack(attacker);
        return DamageDebug.hurt(attacker, target, source, damage);
    }

    // A blow that ignores every defense (Sukuna's World Cutting Slash): no guard, parry, Escudo, intangibility or other
    // shield of the mod stops or lessens it (PowerEvents.onIncomingDamage lets it through untouched).
    private static boolean piercing;

    public static boolean piercing() {
        return piercing;
    }

    public static boolean hurtPiercing(LivingEntity attacker, LivingEntity target, float damage) {
        piercing = true;
        try {
            return hurt(attacker, target, damage);
        } finally {
            piercing = false;
        }
    }

    // The SubAlmas' damages are written in their code at the old scale (before the 2026-11-08 rebalance made every
    // value a Tier 0 maximum): this brings them to the new one.
    public static float subScale(LivingEntity attacker) {
        String power = attacker.getData(com.starkinhon.battlegroundsmod.ModAttachments.POWER).power;
        return power != null && com.starkinhon.battlegroundsmod.power.Powers.isSub(power)
                ? (float) com.starkinhon.battlegroundsmod.skill.TierSpec.get().multSubAlmas() : 1f;
    }

    // Magic damage ignores armor. The same multipliers as hurtBase (Nanami's Overtime and the Strength effect were left out
    // here, so they did nothing for the moves that hurt through this one).
    public static boolean hurtMagic(LivingEntity player, LivingEntity target, float damage) {
        damage *= Tiers.damageMultiplier(player) * com.starkinhon.battlegroundsmod.power.powers.sub.nr.NrCommon.damageMultiplier(player)
                * com.starkinhon.battlegroundsmod.power.powers.sub.jj.JjCommon.damageMultiplier(player)
                * com.starkinhon.battlegroundsmod.power.powers.kokushibo.OniBody.damageMultiplier(player) * subScale(player)
                * strength(player);
        target.invulnerableTime = 0;
        return DamageDebug.hurt(player, target, player.damageSources().indirectMagic(player, player), damage);
    }

    public static void push(Entity target, Vec3 direction, double strength, double up) {
        Vec3 flat = new Vec3(direction.x, 0, direction.z);
        flat = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize().scale(strength);
        if (target instanceof net.minecraft.server.level.ServerPlayer player && com.starkinhon.battlegroundsmod.universe.Flight.flying(player)) {
            // A flying player's own client moves them: told to fly off this way (in the air the push is the full 3D one).
            Vec3 dir = direction.lengthSqr() < 1.0E-4 ? Vec3.ZERO : direction.normalize().scale(strength);
            com.starkinhon.battlegroundsmod.boss.AirCombat.knock(player, new Vec3(dir.x, dir.y + up, dir.z).scale(20), 6, false);
            return;
        }
        target.setDeltaMovement(flat.x, up, flat.z);
        target.hurtMarked = true;
    }

    // Pushes away from a point, used for shockwaves.
    public static void pushFrom(Entity target, Vec3 origin, double strength, double up) {
        push(target, target.position().subtract(origin), strength, up);
    }

    public static void launchSelf(ServerPlayer player, Vec3 velocity) {
        player.setDeltaMovement(velocity);
        player.hurtMarked = true;
        com.starkinhon.battlegroundsmod.power.powers.opm.Opm.own(player);
    }

    // Breaks blocks only under mobGriefing and soulsBlockDestruction (BlockRules). The caster is never hurt by their
    // own explosion.
    public static void explode(ServerPlayer player, Vec3 pos, float power) {
        Level.ExplosionInteraction blocks = BlockRules.canBreak(player.level()) ? Level.ExplosionInteraction.MOB : Level.ExplosionInteraction.NONE;
        player.serverLevel().explode(player, pos.x, pos.y, pos.z, power, blocks);
    }

    public static boolean canStandAt(ServerPlayer player, Vec3 feet) {
        return player.level().noCollision(player, player.getBoundingBox().move(feet.subtract(player.position())));
    }
}
