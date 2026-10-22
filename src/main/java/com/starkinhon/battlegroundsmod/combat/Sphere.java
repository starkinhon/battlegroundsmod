package com.starkinhon.battlegroundsmod.combat;

import java.util.List;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// A ball-shaped area. A target counts when any part of its hitbox is inside.
public final class Sphere implements Area {
    private final ServerLevel level;
    private final Entity owner;
    private final Vec3 center;
    private final double radius;

    public Sphere(ServerLevel level, Entity owner, Vec3 center, double radius) {
        this.level = level;
        this.owner = owner;
        this.center = center;
        this.radius = radius;
    }

    @Override
    public List<LivingEntity> targets() {
        return level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(center, radius * 2, radius * 2, radius * 2),
                e -> Combat.isTarget(e, owner) && touches(e.getBoundingBox()));
    }

    private boolean touches(AABB box) {
        double x = Math.max(box.minX, Math.min(center.x, box.maxX)) - center.x;
        double y = Math.max(box.minY, Math.min(center.y, box.maxY)) - center.y;
        double z = Math.max(box.minZ, Math.min(center.z, box.maxZ)) - center.z;
        return x * x + y * y + z * z <= radius * radius;
    }

    @Override
    public void draw(ParticleOptions particle) {
        Fx.sphere(level, particle, center, radius, Math.max(40, (int) (radius * radius * 6)));
    }
}
