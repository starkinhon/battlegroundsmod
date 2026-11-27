// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.power.powers.sub.jj;

import java.util.UUID;

import com.starkinhon.battlegroundsmod.ModEntities;
import com.starkinhon.battlegroundsmod.combat.Combat;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// One of the stones Todo's X (Quebra-Chao) tears out of the ground (preview forja/todo-kit/shinjuku-todo.js v9, Stark
// 2026-11-27: "do lixo pro bglh foda"). Real physics: it flies, falls, bounces two or three times losing strength and
// settles on a face; then it stays on the ground as one of Todo's stones, and every skill of his can use it: the Z swaps
// with it like with a person, the V throws it up for the Black Flash, the B kicks it like a bullet. A body thrown hard
// through a stone on the ground breaks it on the way. Not saved: after a reload (or 40 s) it crumbles.
public class TodoStone extends Entity {
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(TodoStone.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(TodoStone.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> RESTING = SynchedEntityData.defineId(TodoStone.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> BULLET = SynchedEntityData.defineId(TodoStone.class, EntityDataSerializers.BOOLEAN);
    // The preview's gravity: 22 blocks/s^2 (0.055 a tick); the kicked stone (the B) only 3 (it flies straight).
    public static final double GRAVITY = 0.055;
    private static final double BULLET_GRAVITY = 0.0075;
    private static final int LIFE = 800;

    private UUID owner;
    private int life;
    // Client only: the tumble while it flies, snapping to a face when it rests.
    public float rotX, rotZ, rotXO, rotZO;
    private float spinX, spinZ;

    public TodoStone(EntityType<? extends TodoStone> type, Level level) {
        super(type, level);
        float seed = (getId() * 0.6180339f) % 1f;
        spinX = (seed - 0.5f) * 0.9f;
        spinZ = (((getId() * 0.381966f) % 1f) - 0.5f) * 0.9f;
    }

    public static TodoStone create(ServerLevel level, LivingEntity owner, Vec3 pos, float size, BlockState state) {
        TodoStone s = new TodoStone(ModEntities.TODO_STONE.get(), level);
        s.owner = owner.getUUID();
        s.entityData.set(SIZE, size);
        s.entityData.set(STATE, Block.getId(state));
        s.refreshDimensions();
        s.moveTo(pos.x, pos.y, pos.z, owner.getRandom().nextFloat() * 360f, 0);
        return s;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(SIZE, 1f);
        builder.define(STATE, Block.getId(Blocks.DIRT.defaultBlockState()));
        builder.define(RESTING, false);
        builder.define(BULLET, false);
    }

    public float size() {
        return entityData.get(SIZE);
    }

    public BlockState blockState() {
        return Block.stateById(entityData.get(STATE));
    }

    public boolean resting() {
        return entityData.get(RESTING);
    }

    public boolean ownedBy(Entity e) {
        return owner != null && e != null && owner.equals(e.getUUID());
    }

    public UUID owner() {
        return owner;
    }

    // Launched (or kicked): off the ground and flying.
    public void launch(Vec3 velocity, boolean bullet) {
        entityData.set(RESTING, false);
        entityData.set(BULLET, bullet);
        setDeltaMovement(velocity);
        hurtMarked = true;
    }

    // Put somewhere (a swap): still, falling from there if there is nothing under it.
    public void placeAt(Vec3 pos) {
        entityData.set(BULLET, false);
        entityData.set(RESTING, false);
        setDeltaMovement(Vec3.ZERO);
        teleportTo(pos.x, pos.y, pos.z);
        hurtMarked = true;
    }

    public Vec3 center() {
        return position().add(0, size() * 0.45, 0);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        float s = Math.max(0.2f, size() * 0.9f);
        return EntityDimensions.fixed(s, s);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (SIZE.equals(key)) {
            refreshDimensions();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            life++;
            if (owner == null || life > LIFE) {
                crumble();
                return;
            }
        }
        rotXO = rotX;
        rotZO = rotZ;
        boolean bullet = entityData.get(BULLET);
        if (!resting()) {
            Vec3 v = getDeltaMovement().add(0, bullet ? -BULLET_GRAVITY : -GRAVITY, 0);
            double vy = v.y;
            move(MoverType.SELF, v);
            Vec3 after = getDeltaMovement();
            if (horizontalCollision) {
                after = new Vec3(-v.x * 0.3, after.y, -v.z * 0.3);
                if (bullet) {
                    entityData.set(BULLET, false);
                }
            } else {
                after = new Vec3(v.x, after.y, v.z);
            }
            if (onGround() && vy < 0) {
                double hard = -vy * 20;
                if (!level().isClientSide && hard > 6) {
                    land(hard);
                }
                if (hard > 3.5) {
                    after = new Vec3(after.x * 0.5, hard * 0.3 / 20, after.z * 0.5);
                    spinX *= 0.45f;
                    spinZ *= 0.45f;
                } else {
                    after = Vec3.ZERO;
                    if (!level().isClientSide) {
                        entityData.set(RESTING, true);
                        entityData.set(BULLET, false);
                    }
                }
            }
            setDeltaMovement(after);
            if (level().isClientSide && !resting()) {
                rotX += spinX;
                rotZ += spinZ;
            }
        } else {
            setDeltaMovement(Vec3.ZERO);
            if (!level().isClientSide && level().noCollision(this, getBoundingBox().move(0, -0.06, 0))) {
                // the ground went away under it: it falls again
                entityData.set(RESTING, false);
            }
        }
        if (level().isClientSide && resting()) {
            float q = (float) (Math.PI / 2);
            rotX += (Math.round(rotX / q) * q - rotX) * 0.5f;
            rotZ += (Math.round(rotZ / q) * q - rotZ) * 0.5f;
        }
        if (!level().isClientSide && resting()) {
            smashedThrough();
        }
    }

    // A hard landing: dust of its own block and a thud.
    private void land(double hard) {
        if (level() instanceof ServerLevel level) {
            int n = (int) Math.min(18, 4 + hard * 0.6);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, blockState()), getX(), getY() + 0.05, getZ(), n,
                    size() * 0.4, 0.05, size() * 0.4, 0.15);
            float vol = (float) Math.min(1.2, 0.2 + hard * 0.05) * Math.max(0.4f, size());
            level.playSound(null, getX(), getY(), getZ(), blockState().getSoundType().getBreakSound(), SoundSource.PLAYERS, vol, 0.6f);
        }
    }

    // Someone thrown hard into it (the swapped target sent flying back, a Black Flash): it breaks on the way and they
    // lose some of the push.
    private void smashedThrough() {
        AABB box = getBoundingBox().inflate(0.15);
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !ownedBy(e))) {
            Vec3 v = e.getDeltaMovement();
            if (v.length() > 0.7) {
                e.setDeltaMovement(v.x * 0.35, Math.max(v.y, 0.18), v.z * 0.35);
                e.hurtMarked = true;
                crumble();
                return;
            }
        }
    }

    // It breaks into dust of its own block.
    public void crumble() {
        if (isRemoved()) {
            return;
        }
        if (level() instanceof ServerLevel level) {
            Vec3 c = center();
            int n = (int) (10 + 22 * size());
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, blockState()), c.x, c.y, c.z, n,
                    size() * 0.35, size() * 0.35, size() * 0.35, 0.2);
            level.playSound(null, c.x, c.y, c.z, blockState().getSoundType().getBreakSound(), SoundSource.PLAYERS,
                    0.6f + 0.6f * Math.min(1.5f, size()), 0.8f);
        }
        discard();
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 96 * 96;
    }

    // Not kept across a reload: no owner then, and it crumbles on its first tick.
    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    // How far a living body is from a stone, for the aim helpers.
    static double hitDistance(TodoStone s, Vec3 from, Vec3 to) {
        return s.getBoundingBox().inflate(0.3).clip(from, to).map(p -> p.distanceTo(from)).orElse(Double.MAX_VALUE);
    }

    static Vec3 centerOf(Entity e) {
        return e instanceof TodoStone s ? s.center() : Combat.center((LivingEntity) e);
    }
}
