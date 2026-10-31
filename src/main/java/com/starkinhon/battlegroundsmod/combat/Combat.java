package com.starkinhon.battlegroundsmod.combat;

import java.util.Comparator;
import java.util.List;

import com.starkinhon.battlegroundsmod.power.Tiers;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(start, end).inflate(radius),
                e -> {
                    if (!isTarget(e, owner)) {
                        return false;
                    }
                    AABB box = e.getBoundingBox().inflate(radius);
                    return box.contains(start) || box.clip(start, end).isPresent();
                });
    }

    // Where the player's view hits a block, or the point at max range.
    public static Vec3 raycastPoint(LivingEntity player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(range));
        var hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
    }

    // The closest target in the player's view, stopped by walls. Slightly generous so moves feel good to land.
    public static LivingEntity raycastTarget(LivingEntity player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = raycastPoint(player, range);
        return alongLine(level(player), eye, end, 0.6, player).stream()
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(eye)))
                .orElse(null);
    }

    // Every move of the mod hurts through here or hurtMagic, so the attacker's tier scales them all (see Tiers).
    public static boolean hurt(LivingEntity attacker, LivingEntity target, float damage) {
        damage *= Tiers.damageMultiplier(attacker);
        // Clear hit immunity so fast multi-hit moves land every hit.
        target.invulnerableTime = 0;
        DamageSource source = attacker instanceof Player player
                ? attacker.damageSources().playerAttack(player)
                : attacker.damageSources().mobAttack(attacker);
        return target.hurt(source, damage);
    }

    // Magic damage ignores armor.
    public static boolean hurtMagic(LivingEntity player, LivingEntity target, float damage) {
        damage *= Tiers.damageMultiplier(player);
        target.invulnerableTime = 0;
        return target.hurt(player.damageSources().indirectMagic(player, player), damage);
    }

    public static void push(Entity target, Vec3 direction, double strength, double up) {
        Vec3 flat = new Vec3(direction.x, 0, direction.z);
        flat = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize().scale(strength);
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
