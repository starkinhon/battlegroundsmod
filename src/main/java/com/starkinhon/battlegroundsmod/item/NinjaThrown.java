package com.starkinhon.battlegroundsmod.item;

import com.starkinhon.battlegroundsmod.ModEntities;
import com.starkinhon.battlegroundsmod.ModItems;
import com.starkinhon.battlegroundsmod.combat.BlockRules;
import com.starkinhon.battlegroundsmod.combat.Combat;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

// What a ninja throws (Stark 2026-11-12, thread Itens de Naruto): the Shuriken, the Kunai and the Papel-Bomba fly as this
// one entity, drawn with the item's own cube model (ThrownItemRenderer). A Shuriken or Kunai that hits falls where it
// landed, to be picked up again; the Papel-Bomba sticks to the block or the body it hit and blows 2 s later.
public class NinjaThrown extends ThrowableItemProjectile {
    private static final EntityDataAccessor<Integer> STUCK = SynchedEntityData.defineId(NinjaThrown.class, EntityDataSerializers.INT);
    private Vec3 stuckOffset;
    private boolean stuckBlock;
    private int fuse = -1;

    public NinjaThrown(EntityType<? extends NinjaThrown> type, Level level) {
        super(type, level);
    }

    public NinjaThrown(Level level, LivingEntity owner, ItemStack stack) {
        super(ModEntities.NINJA_THROWN.get(), owner, level);
        setItem(stack.copyWithCount(1));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STUCK, -1);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.SHURIKEN.get();
    }

    private boolean bomb() {
        return getItem().is(ModItems.PAPER_BOMB.get());
    }

    @Override
    protected double getDefaultGravity() {
        return bomb() ? 0.03 : 0.012;
    }

    @Override
    public void tick() {
        if (fuse >= 0) {
            // stuck: follow the body it is on, or stay on the block, until the fuse ends
            setDeltaMovement(Vec3.ZERO);
            int on = entityData.get(STUCK);
            Entity host = on >= 0 ? level().getEntity(on) : null;
            if (host != null && stuckOffset != null) {
                setPos(host.position().add(stuckOffset));
            }
            if (level() instanceof ServerLevel server) {
                if (fuse % 4 == 0) {
                    server.sendParticles(ParticleTypes.SMALL_FLAME, getX(), getY() + 0.1, getZ(), 1, 0.02, 0.02, 0.02, 0.005);
                }
                if (fuse % 10 == 0) {
                    server.playSound(null, blockPosition(), SoundEvents.TNT_PRIMED, SoundSource.PLAYERS, 0.4f, 1.6f + (40 - fuse) * 0.01f);
                }
                if (--fuse <= 0) {
                    blow(server);
                }
            }
            return;
        }
        super.tick();
        if (level().isClientSide && !bomb()) {
            // a thin trail so the eye can follow the steel
            level().addParticle(ParticleTypes.CRIT, getX(), getY(), getZ(), 0, 0, 0);
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        if (!(level() instanceof ServerLevel server) || !(hit.getEntity() instanceof LivingEntity target) || target == getOwner()) {
            return;
        }
        if (bomb()) {
            stick(target, hit.getLocation());
            return;
        }
        boolean kunai = getItem().is(ModItems.KUNAI.get());
        if (getOwner() instanceof LivingEntity owner) {
            Combat.hurtBasic(owner, target, kunai ? 14f : 8f);
        } else {
            target.hurt(damageSources().thrown(this, null), kunai ? 5f : 3f);
        }
        server.playSound(null, blockPosition(), SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 0.7f, kunai ? 1.3f : 1.7f);
        drop(server);
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        super.onHitBlock(hit);
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        if (bomb()) {
            if (fuse < 0) {
                stuckBlock = true;
                setPos(hit.getLocation());
                fuse = 40;
                setNoGravity(true);
            }
            return;
        }
        server.playSound(null, blockPosition(), SoundEvents.TRIDENT_HIT_GROUND, SoundSource.PLAYERS, 0.8f, 1.5f);
        drop(server);
    }

    private void stick(LivingEntity target, Vec3 at) {
        if (fuse >= 0) {
            return;
        }
        entityData.set(STUCK, target.getId());
        stuckOffset = at.subtract(target.position());
        fuse = 40;
        setNoGravity(true);
    }

    // where it landed: back to an item to pick up (not for the creative player, who did not spend one)
    private void drop(ServerLevel server) {
        if (!(getOwner() instanceof Player p && p.getAbilities().instabuild)) {
            ItemEntity item = new ItemEntity(server, getX(), getY(), getZ(), getItem().copyWithCount(1));
            item.setDeltaMovement(getDeltaMovement().scale(-0.05));
            item.setDefaultPickUpDelay();
            server.addFreshEntity(item);
        }
        discard();
    }

    private void blow(ServerLevel server) {
        Vec3 c = position();
        Level.ExplosionInteraction blocks = BlockRules.canBreak(server) ? Level.ExplosionInteraction.MOB : Level.ExplosionInteraction.NONE;
        server.explode(this, c.x, c.y, c.z, 2.6f, blocks);
        discard();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("souls_fuse", fuse);
        tag.putBoolean("souls_on_block", stuckBlock);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        fuse = tag.contains("souls_fuse") ? tag.getInt("souls_fuse") : -1;
        stuckBlock = tag.getBoolean("souls_on_block");
    }
}
