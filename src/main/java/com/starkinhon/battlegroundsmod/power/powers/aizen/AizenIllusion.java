package com.starkinhon.battlegroundsmod.power.powers.aizen;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

// Kyoka Suigetsu's clone: a copy of Aizen standing where he was, with his skin, his name and his gear. It doesn't move
// or fight; mobs that were after him go after it. Any hit on it breaks the illusion (KyokaSuigetsu). Never saved.
public class AizenIllusion extends PathfinderMob {
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(AizenIllusion.class, EntityDataSerializers.OPTIONAL_UUID);

    public AizenIllusion(EntityType<? extends AizenIllusion> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20)
                .add(Attributes.MOVEMENT_SPEED, 0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER, Optional.empty());
    }

    public void copy(LivingEntity owner) {
        entityData.set(OWNER, Optional.of(owner.getUUID()));
        setCustomName(owner.getName());
        setCustomNameVisible(false);
        moveTo(owner.getX(), owner.getY(), owner.getZ(), owner.getYRot(), owner.getXRot());
        setYHeadRot(owner.getYHeadRot());
        yBodyRot = owner.yBodyRot;
        setShiftKeyDown(owner.isShiftKeyDown());
        setPose(owner.getPose());
        for (var slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            setItemSlot(slot, owner.getItemBySlot(slot).copy());
            setDropChance(slot, 0);
        }
        setNoAi(true);
    }

    public Optional<UUID> owner() {
        return entityData.get(OWNER);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && !isInvulnerableTo(source) && source.getEntity() != null && !owner().map(source.getEntity().getUUID()::equals).orElse(false)) {
            KyokaSuigetsu.cloneHit(this);
        }
        return super.hurt(source, amount);
    }

    @Override
    protected void actuallyHurt(DamageSource source, float amount) {
        // Only a reflection: nothing really gets hurt.
    }

    @Override
    public void tick() {
        super.tick();
        // Left behind (the server restarted mid-illusion, say): it fades on its own.
        if (!level().isClientSide && tickCount > 400) {
            discard();
        }
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPushable() {
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
