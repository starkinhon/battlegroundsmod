package com.starkinhon.battlegroundsmod.animal.db;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.Vec3;

import com.starkinhon.battlegroundsmod.combat.Stun;
import net.minecraft.world.level.Level;

// The Dragon Ball biomes' own creatures (Stark 2026-11-11, prévia mobs-db aprovada): Namekiano, Peixe gigante,
// Androide 8, Ladrão Urso and Bora. Models and skins come from tools/mobs/blocky (the same boxes and painting as the
// preview), animated in client/blocky/DbAnims. What they share lives here: a variant (the Namekians' seed), and the
// attack animations, started on the server and sent to the clients as an entity event so the swing lines up with the
// hit. Each attack is a timer here: the hit lands on its own tick, never before the wind-up shows.
public abstract class DbCreature extends Monster {
    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(DbCreature.class, EntityDataSerializers.INT);
    private static final byte EVENT_BASE = 110;
    private static final byte EVENT_CANCEL = 119;

    // client: when the attack animation of each kind started (tickCount), -1000 = never
    public int attackStart = -1000;
    public int attackKind;
    // server: ticks into the current attack, 0 = none
    protected int attackTimer;
    protected int attackLength;
    protected int attackImpact;
    protected int attackCurrentKind;

    protected DbCreature(EntityType<? extends DbCreature> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(VARIANT, 0);
    }

    public int variant() {
        return entityData.get(VARIANT);
    }

    public void setVariant(int v) {
        entityData.set(VARIANT, v);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (VARIANT.equals(key)) {
            refreshDimensions();
            variantChanged();
        }
    }

    // A Namekian's hitbox and health follow its variant.
    protected void variantChanged() {
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("DbVariant", variant());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setVariant(tag.getInt("DbVariant"));
    }

    public boolean attacking() {
        return attackTimer > 0;
    }

    // Starts an attack of this kind: length in ticks, the hit on tick impact.
    protected boolean startAttack(int kind, int length, int impact) {
        if (attackTimer > 0) {
            return false;
        }
        attackCurrentKind = kind;
        attackLength = length;
        attackImpact = impact;
        attackTimer = 1;
        level().broadcastEntityEvent(this, (byte) (EVENT_BASE + kind));
        return true;
    }

    // Called on the impact tick of the current attack.
    protected abstract void impact(int kind);

    // Every tick while an attack plays (for leaps, holds...).
    protected void attackTick(int kind, int tick) {
    }

    // Is the target still where the blow lands: within reach of the body and in front of it (a step back or aside
    // during the wind-up dodges it).
    protected boolean inReach(LivingEntity target, double reach) {
        if (target == null || !target.isAlive()) {
            return false;
        }
        Vec3 to = target.position().subtract(position());
        Vec3 flat = new Vec3(to.x, 0, to.z);
        double gap = Math.sqrt(getBoundingBox().distanceToSqr(target.getBoundingBox().getCenter())) - target.getBbWidth() * 0.5;
        if (gap > reach || Math.abs(to.y) > reach + 1.5) {
            return false;
        }
        if (flat.lengthSqr() < 1.0E-3) {
            return true;
        }
        float yaw = getYRot() * net.minecraft.util.Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-net.minecraft.util.Mth.sin(yaw), 0, net.minecraft.util.Mth.cos(yaw));
        return flat.normalize().dot(fwd) > 0.2 || gap < 0.6;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        // a stun during the wind-up cancels the blow (it never lands from a stunned body)
        if (attackTimer > 0 && attackTimer < attackImpact && Stun.real(this)) {
            attackTimer = 0;
            level().broadcastEntityEvent(this, EVENT_CANCEL);
            return;
        }
        if (attackTimer > 0) {
            getNavigation().stop();
            if (getTarget() != null && attackTimer < attackImpact) {
                getLookControl().setLookAt(getTarget(), 30f, 30f);
            }
            attackTick(attackCurrentKind, attackTimer);
            if (attackTimer == attackImpact) {
                impact(attackCurrentKind);
            }
            attackTimer = attackTimer >= attackLength ? 0 : attackTimer + 1;
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_CANCEL) {
            attackStart = -1000;
            return;
        }
        if (id >= EVENT_BASE && id < EVENT_BASE + 8) {
            attackStart = tickCount;
            attackKind = id - EVENT_BASE;
            return;
        }
        super.handleEntityEvent(id);
    }

    // 0..1 through the attack animation of this length (seconds), or -1 when none is playing.
    public float attackProgress(float age, float seconds) {
        float p = (age - attackStart) / (seconds * 20f);
        return p >= 0 && p < 1 ? p : -1;
    }
}
