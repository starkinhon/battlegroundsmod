package com.starkinhon.battlegroundsmod.power.powers.sub.nr;

import java.util.Optional;
import java.util.UUID;

import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// Naruto's shadow clone (Kage Bunshin): a copy of whoever made it (their skin, NarutoCloneRenderer) with 1 point of
// life. It runs at its owner's enemy and punches; any hit makes it vanish in a puff of smoke. A puppet clone (the Rendan's)
// has no mind of its own: the move moves it. Never saved.
public class NarutoClone extends PathfinderMob {
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(NarutoClone.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(NarutoClone.class, EntityDataSerializers.INT);
    private int life = 400;
    private int punchCooldown;

    public NarutoClone(EntityType<? extends NarutoClone> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 1)
                .add(Attributes.MOVEMENT_SPEED, 0.34)
                .add(Attributes.ATTACK_DAMAGE, 3)
                .add(Attributes.FOLLOW_RANGE, 32);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER, Optional.empty());
        builder.define(OWNER_ID, -1);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.35, true));
    }

    public void copy(LivingEntity owner, int ticks, boolean puppet) {
        entityData.set(OWNER, Optional.of(owner.getUUID()));
        entityData.set(OWNER_ID, owner.getId());
        setCustomName(owner.getName());
        setCustomNameVisible(false);
        setYHeadRot(owner.getYHeadRot());
        yBodyRot = owner.yBodyRot;
        for (var slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            setItemSlot(slot, owner.getItemBySlot(slot).copy());
            setDropChance(slot, 0);
        }
        life = ticks;
        if (puppet) {
            setNoAi(true);
        }
    }

    public Optional<UUID> owner() {
        return entityData.get(OWNER);
    }

    public int ownerId() {
        return entityData.get(OWNER_ID);
    }

    public LivingEntity ownerEntity() {
        if (level() instanceof ServerLevel server) {
            Entity e = owner().map(server::getEntity).orElse(null);
            return e instanceof LivingEntity living ? living : null;
        }
        return level().getEntity(ownerId()) instanceof LivingEntity living ? living : null;
    }

    // Gone in a puff of white smoke.
    public void poof() {
        if (level() instanceof ServerLevel server && !isRemoved()) {
            SubNet.send(server, SubNet.POOF, 0, position().add(0, 0.9, 0), Vec3.ZERO, 1, 0, 0);
            SubCommon.sound(server, position(), SoundEvents.FIRE_EXTINGUISH, 0.7f, 1.6f);
            discard();
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isInvulnerableTo(source)) {
            return false;
        }
        Entity by = source.getEntity();
        if (by != null && owner().map(by.getUUID()::equals).orElse(false)) {
            return false;
        }
        poof();
        return true;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        LivingEntity owner = ownerEntity();
        if (punchCooldown > 0 || !(target instanceof LivingEntity living)) {
            return false;
        }
        punchCooldown = 12;
        swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        // The clone's punch counts as its owner's (their tier, their kill).
        boolean hit = Combat.hurt(owner != null ? owner : this, living, 3);
        if (hit) {
            Combat.push(living, living.position().subtract(position()), 0.25, 0.1);
            if (level() instanceof ServerLevel server) {
                SubNet.send(server, SubNet.HIT, 0, Combat.center(living), Vec3.ZERO, 0.5f, 10, random.nextInt(50));
            }
        }
        return hit;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        if (punchCooldown > 0) {
            punchCooldown--;
        }
        LivingEntity owner = ownerEntity();
        if (--life <= 0 || owner == null || !owner.isAlive() || distanceTo(owner) > 64) {
            poof();
            return;
        }
        if (isNoAi()) {
            return;
        }
        // Fight the owner's fight: whoever they last hit or who last hit them, or a mob owner's own target.
        LivingEntity want = null;
        if (owner instanceof Mob mob) {
            want = mob.getTarget();
        } else {
            LivingEntity hitByOwner = owner.getLastHurtMob();
            LivingEntity hitOwner = owner.getLastHurtByMob();
            if (hitByOwner != null && owner.tickCount - owner.getLastHurtMobTimestamp() < 200) {
                want = hitByOwner;
            } else if (hitOwner != null) {
                want = hitOwner;
            }
        }
        if (want instanceof NarutoClone || want == owner || (want instanceof Player p && (p.isCreative() || p.isSpectator()))) {
            want = null;
        }
        if (want != null && want.isAlive()) {
            setTarget(want);
        } else if (getTarget() == null || !getTarget().isAlive()) {
            setTarget(null);
            // Nothing to fight: stay close to the owner.
            if (distanceTo(owner) > 4) {
                getNavigation().moveTo(owner, 1.2);
            }
        }
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
