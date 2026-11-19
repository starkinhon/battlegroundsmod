package com.starkinhon.battlegroundsmod.item;

import java.util.UUID;

import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Fx;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

// Principal Yaga's cursed plush (Boneco Amaldiçoado, Stark 2026-11-19): the item's own model at toy size
// (client/CursedPlushRenderer), on its owner's side for 60 s. Only three moves, like Yaga's dolls: a dash that rams the
// enemy (Investida), a quick barrage of punches (Barragem) and a heavy shove that throws it back (Empurrão). It hunts
// the monsters around and whoever its owner is fighting. Its blows count as the owner's (their tier). Never saved.
public class CursedPlush extends PathfinderMob {
    public static final int IDLE = 0, DASH = 1, BARRAGE = 2, SHOVE = 3;
    // Length of each move in ticks, and the tick of the dash's launch and the shove's blow (the renderer animates with them).
    public static final int DASH_LEN = 16, DASH_GO = 5, BARRAGE_LEN = 20, SHOVE_LEN = 14, SHOVE_HIT = 7;
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(CursedPlush.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STATE_START = SynchedEntityData.defineId(CursedPlush.class, EntityDataSerializers.INT);
    private UUID owner;
    private int life = 1200;
    private int dashCooldown = 20, barrageCooldown, shoveCooldown;
    private boolean dashHit;

    public CursedPlush(EntityType<? extends CursedPlush> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 60)
                .add(Attributes.ARMOR, 6)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
                .add(Attributes.FOLLOW_RANGE, 24);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STATE, IDLE);
        builder.define(STATE_START, 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
    }

    public void setOwner(Player player) {
        owner = player.getUUID();
    }

    public int state() {
        return entityData.get(STATE);
    }

    // Ticks since the current move started (client and server agree through the synced start tick).
    public int stateAge() {
        return tickCount - entityData.get(STATE_START);
    }

    private void start(int state) {
        entityData.set(STATE, state);
        entityData.set(STATE_START, tickCount);
        getNavigation().stop();
    }

    private Player ownerPlayer() {
        return owner != null && level() instanceof ServerLevel server && server.getEntity(owner) instanceof Player p ? p : null;
    }

    // Gone in a puff of cursed ink.
    private void poof() {
        if (level() instanceof ServerLevel server && !isRemoved()) {
            Fx.burst(server, ParticleTypes.SQUID_INK, position().add(0, 0.6, 0), 40, 0.5, 0.05);
            Fx.burst(server, ParticleTypes.POOF, position().add(0, 0.6, 0), 12, 0.4, 0.02);
            Fx.sound(server, position(), SoundEvents.WOOL_BREAK, 1f, 0.7f);
            discard();
        }
    }

    private boolean foe(Entity e, Player owner) {
        return Combat.isTarget(e, owner) && !(e instanceof CursedPlush) && e != this
                && !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        Entity by = source.getEntity();
        if (by != null && owner != null && (owner.equals(by.getUUID()) || by instanceof CursedPlush)) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        Player owner = ownerPlayer();
        if (--life <= 0 || owner == null || !owner.isAlive() || distanceTo(owner) > 48) {
            poof();
            return;
        }
        if (dashCooldown > 0) dashCooldown--;
        if (barrageCooldown > 0) barrageCooldown--;
        if (shoveCooldown > 0) shoveCooldown--;
        LivingEntity target = pickTarget(owner);
        setTarget(target);
        if (target != null) {
            face(target);
        }
        int age = stateAge();
        switch (state()) {
            case DASH -> dash(owner, target, age);
            case BARRAGE -> barrage(owner, target, age);
            case SHOVE -> shove(owner, age);
            default -> idle(owner, target);
        }
    }

    // The owner's fight first (whoever they hit or who hit them), then the nearest monster.
    private LivingEntity pickTarget(Player owner) {
        LivingEntity want = null;
        LivingEntity hitByOwner = owner.getLastHurtMob();
        LivingEntity hitOwner = owner.getLastHurtByMob();
        if (hitByOwner != null && owner.tickCount - owner.getLastHurtMobTimestamp() < 200) {
            want = hitByOwner;
        } else if (hitOwner != null && owner.tickCount - owner.getLastHurtByMobTimestamp() < 200) {
            want = hitOwner;
        }
        if (want != null && foe(want, owner) && want.distanceTo(this) < 24) {
            return want;
        }
        LivingEntity current = getTarget();
        if (current != null && current.isAlive() && foe(current, owner) && current.distanceTo(this) < 24) {
            return current;
        }
        return level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(16),
                e -> e instanceof Enemy && foe(e, owner) && hasLineOfSight(e)).stream()
                .min((a, b) -> Double.compare(a.distanceToSqr(this), b.distanceToSqr(this))).orElse(null);
    }

    private void face(Entity target) {
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * (180 / Math.PI)) - 90f;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    private void idle(Player owner, LivingEntity target) {
        if (target == null) {
            if (distanceTo(owner) > 4) {
                getNavigation().moveTo(owner, 1.2);
            }
            return;
        }
        double d = distanceTo(target);
        if (d <= 2.6) {
            if (barrageCooldown == 0) {
                start(BARRAGE);
                return;
            }
            if (shoveCooldown == 0) {
                start(SHOVE);
                return;
            }
            getNavigation().stop();
            return;
        }
        if (d > 3.5 && d < 11 && dashCooldown == 0 && onGround() && hasLineOfSight(target)) {
            dashHit = false;
            start(DASH);
            return;
        }
        getNavigation().moveTo(target, 1.25);
    }

    // Crouches, then rams forward; the first enemy in the way takes the blow and flies.
    private void dash(Player owner, LivingEntity target, int age) {
        if (age < DASH_GO) {
            setDeltaMovement(0, getDeltaMovement().y, 0);
            if (age == 1 && level() instanceof ServerLevel server) {
                Fx.sound(server, position(), SoundEvents.WOOL_STEP, 1.2f, 0.6f);
            }
        } else if (age < DASH_LEN && !dashHit) {
            Vec3 dir = Vec3.directionFromRotation(0, getYRot());
            setDeltaMovement(dir.x * 0.85, getDeltaMovement().y, dir.z * 0.85);
            hurtMarked = true;
            if (level() instanceof ServerLevel server) {
                if (age == DASH_GO) {
                    Fx.sound(server, position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 0.7f);
                }
                Fx.burst(server, ParticleTypes.CLOUD, position().add(0, 0.1, 0), 2, 0.2, 0.01);
                for (LivingEntity hit : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.5),
                        e -> foe(e, owner) && (e == target || e instanceof Enemy))) {
                    dashHit = true;
                    if (Combat.hurtBase(owner, hit, 5f)) {
                        Combat.push(hit, dir, 1.3, 0.4);
                    }
                    Fx.burst(server, ParticleTypes.EXPLOSION, Combat.center(hit), 1, 0, 0);
                    Fx.sound(server, hit.position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1f, 0.8f);
                }
            }
        } else {
            setDeltaMovement(getDeltaMovement().multiply(0.3, 1, 0.3));
            dashCooldown = 70;
            start(IDLE);
        }
    }

    // A flurry of soft fists: a hit every 2 ticks while the enemy stays close, then the shove if it is ready.
    private void barrage(Player owner, LivingEntity target, int age) {
        setDeltaMovement(0, getDeltaMovement().y, 0);
        if (age >= BARRAGE_LEN || target == null) {
            barrageCooldown = 90;
            start(shoveCooldown == 0 && target != null && distanceTo(target) < 3 ? SHOVE : IDLE);
            return;
        }
        if (age >= 2 && age % 2 == 0 && distanceTo(target) < 3.2 && level() instanceof ServerLevel server) {
            if (Combat.hurtBase(owner, target, 1.2f)) {
                Combat.push(target, target.position().subtract(position()), 0.12, 0.05);
            }
            Vec3 at = Combat.center(target).add((random.nextDouble() - 0.5) * 0.6, (random.nextDouble() - 0.5) * 0.6, (random.nextDouble() - 0.5) * 0.6);
            Fx.burst(server, ParticleTypes.CRIT, at, 4, 0.1, 0.2);
            Fx.sound(server, target.position(), SoundEvents.WOOL_HIT, 0.9f, 1.2f + random.nextFloat() * 0.4f);
        }
    }

    // Winds back, then one heavy blow throws everything in front of it.
    private void shove(Player owner, int age) {
        setDeltaMovement(0, getDeltaMovement().y, 0);
        if (age == SHOVE_HIT && level() instanceof ServerLevel server) {
            Vec3 dir = Vec3.directionFromRotation(0, getYRot());
            Vec3 front = position().add(dir.scale(1.2)).add(0, 0.6, 0);
            for (LivingEntity hit : Combat.inRadius(server, front, 1.8, owner)) {
                if (!foe(hit, owner) || !(hit == getTarget() || hit instanceof Enemy)) {
                    continue;
                }
                if (Combat.hurtBase(owner, hit, 6f)) {
                    Combat.push(hit, dir, 1.7, 0.55);
                }
            }
            Fx.burst(server, ParticleTypes.SWEEP_ATTACK, front, 1, 0, 0);
            Fx.burst(server, ParticleTypes.POOF, front, 10, 0.4, 0.08);
            Fx.sound(server, front, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.2f, 0.6f);
        }
        if (age >= SHOVE_LEN) {
            shoveCooldown = 110;
            start(IDLE);
        }
    }

    @Override
    protected void tickDeath() {
        if (!level().isClientSide) {
            poof();
        }
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WOOL_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WOOL_BREAK;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.WOOL_STEP, 0.4f, 0.8f);
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected boolean shouldDropLoot() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }
}
