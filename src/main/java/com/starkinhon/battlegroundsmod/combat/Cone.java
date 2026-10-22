package com.starkinhon.battlegroundsmod.combat;

import java.util.List;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// A cone hitbox for breath and spray moves: starts at origin, opens along dir, cut short by walls.
// The width at a distance only depends on maxLength, so a cone stopped by a wall keeps the same shape.
public final class Cone implements Area {
    // Toggled by /poder hitbox: draws every cone so hitboxes can be checked in game.
    public static boolean debug = false;

    private final ServerLevel level;
    private final Entity owner;
    private final Vec3 origin;
    private final Vec3 dir;
    private final double length;
    private final double maxLength;
    private final double startRadius;
    private final double endRadius;

    public Cone(ServerLevel level, Entity owner, Vec3 origin, Vec3 dir, double length, double maxLength, double startRadius, double endRadius) {
        this.level = level;
        this.owner = owner;
        this.origin = origin;
        this.dir = dir.normalize();
        this.maxLength = maxLength;
        this.startRadius = startRadius;
        this.endRadius = endRadius;
        Vec3 end = origin.add(this.dir.scale(length));
        var hit = level.clip(new ClipContext(origin, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        this.length = hit.getType() == HitResult.Type.MISS ? length : hit.getLocation().distanceTo(origin);
    }

    public Vec3 origin() {
        return origin;
    }

    public Vec3 dir() {
        return dir;
    }

    public double length() {
        return length;
    }

    public double radiusAt(double distance) {
        return startRadius + (endRadius - startRadius) * Math.min(distance, maxLength) / maxLength;
    }

    public Vec3 pointAt(double distance) {
        return origin.add(dir.scale(distance));
    }

    // Uses the closest point of the target's bounding box to the cone axis, so big mobs and
    // targets standing at the edge are hit when any part of them is inside the fire.
    public boolean touches(AABB box) {
        Vec3 closest = box.getCenter();
        double t = 0;
        for (int i = 0; i < 3; i++) {
            t = clamp(closest.subtract(origin).dot(dir), 0, length);
            closest = closestInBox(box, pointAt(t));
        }
        if (closest.distanceTo(pointAt(t)) > radiusAt(t)) {
            return false;
        }
        // No hitting through walls from the side of the cone.
        var hit = level.clip(new ClipContext(origin, closest, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(closest) < 0.04;
    }

    @Override
    public List<LivingEntity> targets() {
        Vec3 end = pointAt(length);
        double r = radiusAt(length);
        AABB area = new AABB(origin, end).inflate(r + 1);
        return level.getEntitiesOfClass(LivingEntity.class, area, e -> Combat.isTarget(e, owner) && touches(e.getBoundingBox()));
    }

    // Outline of the hitbox: rings along the axis and four edge lines.
    @Override
    public void draw(ParticleOptions particle) {
        Vec3 side = dir.cross(Math.abs(dir.y) > 0.99 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
        Vec3 up = side.cross(dir).normalize();
        for (int step = 0; step <= 4; step++) {
            double t = length * step / 4;
            Vec3 center = pointAt(t);
            double r = radiusAt(t);
            for (int i = 0; i < 16; i++) {
                double angle = i * Math.PI * 2 / 16;
                Vec3 offset = side.scale(Math.cos(angle) * r).add(up.scale(Math.sin(angle) * r));
                Fx.burst(level, particle, center.add(offset), 1, 0, 0);
            }
        }
        for (Vec3 edge : new Vec3[] {side, side.reverse(), up, up.reverse()}) {
            Fx.line(level, particle, origin.add(edge.scale(radiusAt(0))), pointAt(length).add(edge.scale(radiusAt(length))), 0.5);
        }
    }

    private static Vec3 closestInBox(AABB box, Vec3 p) {
        return new Vec3(clamp(p.x, box.minX, box.maxX), clamp(p.y, box.minY, box.maxY), clamp(p.z, box.minZ, box.maxZ));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
