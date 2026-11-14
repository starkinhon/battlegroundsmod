package com.starkinhon.battlegroundsmod.npc;

import java.util.ArrayList;
import java.util.List;

import com.starkinhon.battlegroundsmod.world.ShibuyaGen;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

// A Shibuya pedestrian (Stark approved the preview 2026-11-14, forja/jujutsu-pedestres). Nine kinds of people in the
// player's size, each look drawn from one int (PedestreLook). They walk the city's sidewalks on the left, turn at the
// corners, stop for the phone, gather at the big scramble crossings for the light and cross together (straight or on
// the diagonal), go in little groups, and run away from fights. The walking is done for the whole crowd at once by
// PedestreCrowd (nobody inside anybody); this class keeps each one's state.
// Rules (Stark): no children; killable and drops nothing; no Poder, no mission counts (not an Enemy, not a SoulBoss);
// doesn't push the player; the aim never locks on them (Combat.lockable); never saved with the world (they come and
// go around the players); never on rubble (only on the original sidewalk, PedestreCrowd.intact).
public class Pedestre extends PathfinderMob {
    public static final int POSE_NONE = 0, POSE_PHONE = 1, POSE_LOOK = 2;
    private static final EntityDataAccessor<Integer> LOOK = SynchedEntityData.defineId(Pedestre.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> POSE = SynchedEntityData.defineId(Pedestre.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> FLEE = SynchedEntityData.defineId(Pedestre.class, EntityDataSerializers.BOOLEAN);

    // ---- the walk (server only; PedestreCrowd drives it)
    static final int WALK = 0, IDLE = 1, WAIT = 2, CROSS = 3, FREE = 4;
    int state = FREE;
    char axis = 'x';
    ShibuyaGen.Street street;
    int out = 1, dir = 1;
    double a, lat, latT;
    double speed = 1.4, slow = 1, stuckT, flee, timer, lookYaw, lookGoal, lookT;
    // the scramble corner (WAIT/CROSS): the x street's and z street's indices, the corner's signs; the destination
    int cxi, czi, csx, csz, dsx, dsz;
    double tx, tz;
    // the group: followers keep a place beside or behind the leader
    Pedestre leader;
    final List<Pedestre> followers = new ArrayList<>();
    double offX, offZ;
    // this tick: the planned spot, the facing, moving, on the ground
    double px, pz, fx, fz = 1;
    boolean moving, grounded;
    int freeT, airT;
    java.util.Random rand;

    public Pedestre(EntityType<? extends Pedestre> type, Level level) {
        super(type, level);
        xpReward = 0;
        rand = new java.util.Random(level.getRandom().nextLong());
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 10).add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.STEP_HEIGHT, 0.6);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(LOOK, 0);
        builder.define(POSE, (byte) 0);
        builder.define(FLEE, false);
    }

    public int look() {
        return entityData.get(LOOK);
    }

    void setLook(int look) {
        entityData.set(LOOK, look);
        speed = PedestreLook.of(look).speed;
    }

    public int pose() {
        return entityData.get(POSE);
    }

    void setPoseId(int pose) {
        if (pose() != pose) {
            entityData.set(POSE, (byte) pose);
        }
    }

    public boolean fleeing() {
        return entityData.get(FLEE);
    }

    void setFleeing(boolean on) {
        if (fleeing() != on) {
            entityData.set(FLEE, on);
        }
    }

    // the client's stride speed (the model breathes when standing)
    public float walkSpeed() {
        return walkAnimation.speed();
    }

    PedestreLook lookSheet() {
        return PedestreLook.of(look());
    }

    // ---- an ordinary person, not a fighter
    @Override
    protected void registerGoals() {
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false; // PedestreCrowd takes them away (far from every player, or out of sight when lost)
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    protected void pushEntities() {
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
    }

    @Override
    protected void dropEquipment() {
    }

    @Override
    protected boolean shouldDropLoot() {
        return false;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PLAYER_DEATH;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hit = super.hurt(source, amount);
        if (hit && level() instanceof ServerLevel server) {
            PedestreCrowd.noise(server, getX(), getZ());
        }
        return hit;
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        if (leader != null) {
            leader.followers.remove(this);
            leader = null;
        }
        for (Pedestre f : followers) {
            f.leader = null;
        }
        followers.clear();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Look", look());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setLook(tag.getInt("Look"));
    }
}
