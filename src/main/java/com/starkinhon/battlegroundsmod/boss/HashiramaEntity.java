package com.starkinhon.battlegroundsmod.boss;

import com.starkinhon.battlegroundsmod.power.powers.hashirama.Hashirama;
import com.starkinhon.battlegroundsmod.power.powers.hashirama.HsBuddha;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// Hashirama Senju, the God of Shinobi (Stark, 2026-11-13): the first end-game boss, the one "above" the divine souls in
// the Vale do Fim. His power never becomes a soul (dropAlma false). Phase 1 is the Mokuton (forest, stakes, prison,
// lance, flowers, dome, the dragon); at 60% life he takes Sage Mode (the red marks, synced here so the skin changes) and
// summons the Buddha of a Thousand Hands, standing on its head while its arms slap the ground (HsBuddha).
public class HashiramaEntity extends SoulBoss {
    private static final EntityDataAccessor<Boolean> DATA_SAGE = SynchedEntityData.defineId(HashiramaEntity.class, EntityDataSerializers.BOOLEAN);

    private HashiramaTactics tactics;

    public HashiramaEntity(EntityType<? extends HashiramaEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SAGE, false);
    }

    public boolean sage() {
        return entityData.get(DATA_SAGE);
    }

    public void setSage(boolean on) {
        entityData.set(DATA_SAGE, on);
    }

    // The Buddha fell apart or dissolved: he comes down (gravity back on in HsBuddha) and summons again later.
    public void buddhaEnded() {
        if (tactics != null) {
            tactics.buddhaEnded();
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide()) {
            // The wooden dome takes the blow (and the first one arms its counter).
            if (!source.is(DamageTypes.FELL_OUT_OF_WORLD) && !source.is(DamageTypes.GENERIC_KILL) && Hashirama.domeHit(this, source)) {
                return false;
            }
            if (source.is(DamageTypes.FALL) && HsBuddha.active(this)) {
                return false;
            }
        }
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide() && source.getEntity() != null && source.getEntity() != this) {
            HsBuddha.hitOnTop(this);
        }
        return hurt;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide()) {
            Vec3 p = HsBuddha.perch(this);
            if (p != null) {
                setPos(p.x, p.y, p.z);
                setDeltaMovement(Vec3.ZERO);
                fallDistance = 0;
                getNavigation().stop();
            }
        }
    }

    @Override
    public BossTactics tactics() {
        if (tactics == null) {
            tactics = new HashiramaTactics(this);
        }
        return tactics;
    }

    @Override
    public String bossId() {
        return "hashirama";
    }
}
