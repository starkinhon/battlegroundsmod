package com.starkinhon.battlegroundsmod.animal;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

// The pterodactyls over the Dragon World (Stark 2026-11-11: "dinossauros pássaro voando que nem no universo de Dragon
// Ball"): scenery, they never attack. Each one circles its own patch of sky high over the ground, gliding most of
// the time and flapping to climb back, its circle drifting slowly away so they cross the sky. Spawned around the player
// in the Dragon Ball biomes by universe/UniverseSpawns.
public class Pterodactyl extends FlyingMob {
    private double centerX, centerZ, altitude, angle, radius, speed, driftX, driftZ;
    private boolean started;

    public Pterodactyl(EntityType<? extends Pterodactyl> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 14.0).add(Attributes.FLYING_SPEED, 0.6);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!started) {
            started = true;
            centerX = getX();
            centerZ = getZ();
            altitude = getY();
            angle = random.nextDouble() * Math.PI * 2;
            radius = 14 + random.nextDouble() * 22;
            speed = (0.012 + random.nextDouble() * 0.01) * (random.nextBoolean() ? 1 : -1);
            double drift = random.nextDouble() * Math.PI * 2;
            driftX = Math.cos(drift) * 0.04;
            driftZ = Math.sin(drift) * 0.04;
        }
        angle += speed;
        centerX += driftX;
        centerZ += driftZ;
        // keep well above the ground under it (mountains, pillars)
        int ground = level().getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(getX()), Mth.floor(getZ()));
        if (altitude < ground + 18) {
            altitude += 0.4;
        }
        double ty = altitude + Math.sin(tickCount * 0.02 + radius) * 5;
        Vec3 target = new Vec3(centerX + Math.cos(angle) * radius, ty, centerZ + Math.sin(angle) * radius);
        Vec3 to = target.subtract(position());
        Vec3 velocity = getDeltaMovement().scale(0.85).add(to.scale(0.012));
        double max = 0.55;
        if (velocity.length() > max) {
            velocity = velocity.normalize().scale(max);
        }
        setDeltaMovement(velocity);
        if (velocity.horizontalDistanceSqr() > 1.0E-4) {
            float yaw = (float) (Mth.atan2(velocity.z, velocity.x) * Mth.RAD_TO_DEG) - 90f;
            setYRot(yaw);
            yBodyRot = yaw;
            yHeadRot = yaw;
        }
        setXRot((float) (-Mth.atan2(velocity.y, Math.sqrt(velocity.horizontalDistanceSqr())) * Mth.RAD_TO_DEG) * 0.6f);
    }

    // Climbing flaps, level flight glides (the model reads it).
    public boolean flapping() {
        return getDeltaMovement().y > 0.03 || (tickCount / 60) % 4 == 0;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.PHANTOM_AMBIENT;
    }

    @Override
    public float getVoicePitch() {
        return 1.6f + random.nextFloat() * 0.3f;
    }

    @Override
    protected float getSoundVolume() {
        return 0.6f;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 240;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return distance > 96 * 96;
    }
}
