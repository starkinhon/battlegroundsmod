package com.starkinhon.battlegroundsmod.combat;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// A particle-only projectile: moves a hit sphere along a path each tick. No entity or renderer needed.
public class Projectile implements Tasks.Task {
    private final ServerPlayer owner;
    private final ServerLevel level;
    private final double radius;
    private final int maxAge;
    private final Set<LivingEntity> alreadyHit = new HashSet<>();
    private Vec3 pos;
    private Vec3 velocity;
    private boolean pierce = false;
    private boolean impactOnExpire = false;
    private Consumer<Vec3> trail = p -> {};
    private Consumer<LivingEntity> onHit = t -> {};
    private Consumer<Vec3> onImpact = p -> {};
    private Destructible.Attack attack;

    public Projectile(ServerPlayer owner, Vec3 start, Vec3 velocity, double radius, int maxAge) {
        this.owner = owner;
        this.level = owner.serverLevel();
        this.pos = start;
        this.velocity = velocity;
        this.radius = radius;
        this.maxAge = maxAge;
    }

    public Projectile trail(Consumer<Vec3> trail) {
        this.trail = trail;
        return this;
    }

    public Projectile onHit(Consumer<LivingEntity> onHit) {
        this.onHit = onHit;
        return this;
    }

    public Projectile onImpact(Consumer<Vec3> onImpact) {
        this.onImpact = onImpact;
        return this;
    }

    // Keeps flying through targets instead of stopping at the first one.
    public Projectile pierce() {
        this.pierce = true;
        return this;
    }

    public Projectile impactOnExpire() {
        this.impactOnExpire = true;
        return this;
    }

    public void launch() {
        // Particles only, so erasing it (the hakaizinho) just stops it.
        attack = Destructible.track(level, owner, pos, radius, () -> {});
        Tasks.add(this);
    }

    @Override
    public boolean tick(int age) {
        if (attack != null && attack.gone()) {
            return true;
        }
        boolean done = step(age);
        if (attack != null) {
            if (done) {
                attack.end();
            } else {
                attack.move(pos);
            }
        }
        return done;
    }

    private boolean step(int age) {
        Vec3 next = pos.add(velocity);
        if (!level.isLoaded(BlockPos.containing(next))) {
            return true;
        }
        var blockHit = level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        boolean hitBlock = blockHit.getType() != HitResult.Type.MISS;
        Vec3 end = hitBlock ? blockHit.getLocation() : next;

        trail.accept(pos);
        trail.accept(pos.add(end).scale(0.5));

        for (LivingEntity target : Combat.alongLine(level, pos, end, radius, owner)) {
            if (alreadyHit.add(target)) {
                onHit.accept(target);
                if (!pierce) {
                    onImpact.accept(Combat.center(target));
                    return true;
                }
            }
        }
        if (hitBlock) {
            onImpact.accept(end);
            return true;
        }
        pos = next;
        if (age >= maxAge) {
            if (impactOnExpire) {
                onImpact.accept(pos);
            }
            return true;
        }
        return false;
    }
}
