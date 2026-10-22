package com.starkinhon.battlegroundsmod.combat;

import java.util.List;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// A round area standing on the ground: around the caster or on an aimed point. With an inner radius it becomes a
// ring that leaves the middle out. Reaches one block below its base so targets on a lower step still count.
public final class Cylinder implements Area {
    private final ServerLevel level;
    private final Entity owner;
    private final Vec3 base;
    private final double innerRadius;
    private final double radius;
    private final double height;

    public Cylinder(ServerLevel level, Entity owner, Vec3 base, double innerRadius, double radius, double height) {
        this.level = level;
        this.owner = owner;
        this.base = base;
        this.innerRadius = innerRadius;
        this.radius = radius;
        this.height = height;
    }

    @Override
    public List<LivingEntity> targets() {
        AABB area = new AABB(base.x - radius, base.y - 1, base.z - radius, base.x + radius, base.y + height, base.z + radius);
        return level.getEntitiesOfClass(LivingEntity.class, area, e -> Combat.isTarget(e, owner) && touches(e.getBoundingBox()));
    }

    // Closest point of the target's box to the axis must be inside the outer edge, farthest point outside the inner one.
    private boolean touches(AABB box) {
        if (box.maxY < base.y - 1 || box.minY > base.y + height) {
            return false;
        }
        double nearX = Math.max(box.minX, Math.min(base.x, box.maxX)) - base.x;
        double nearZ = Math.max(box.minZ, Math.min(base.z, box.maxZ)) - base.z;
        double farX = Math.max(Math.abs(box.minX - base.x), Math.abs(box.maxX - base.x));
        double farZ = Math.max(Math.abs(box.minZ - base.z), Math.abs(box.maxZ - base.z));
        return nearX * nearX + nearZ * nearZ <= radius * radius && farX * farX + farZ * farZ >= innerRadius * innerRadius;
    }

    @Override
    public void draw(ParticleOptions particle) {
        int points = Math.max(16, (int) (radius * 8));
        Fx.ring(level, particle, base.add(0, 0.1, 0), radius, points, 0);
        Fx.ring(level, particle, base.add(0, height, 0), radius, points, 0);
        if (innerRadius > 0) {
            Fx.ring(level, particle, base.add(0, 0.1, 0), innerRadius, points, 0);
        }
        for (int i = 0; i < 4; i++) {
            double angle = i * Math.PI / 2;
            Vec3 foot = base.add(Math.cos(angle) * radius, 0.1, Math.sin(angle) * radius);
            Fx.line(level, particle, foot, foot.add(0, height, 0), 0.5);
        }
    }
}
