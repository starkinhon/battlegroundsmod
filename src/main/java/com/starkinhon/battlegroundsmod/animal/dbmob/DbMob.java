package com.starkinhon.battlegroundsmod.animal.dbmob;

import java.util.UUID;

import javax.annotation.Nullable;

import com.starkinhon.battlegroundsmod.animal.naruto.NarutoMob;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// One of the Dragon Ball creatures (Stark 2026-11-12, preview tools/mobs/dbmobs approved: "agora sim, pode botar tudo
// no jogo"). The body, AI, blow paths and baked animation are the Naruto creatures' (NarutoMob); the blows are
// DbMobMoves', the numbers DbMobSpec's. What only these have: the dragon you tame with roast meat and fly on, the
// Gero android's self-destruct, the Majin mark (Babidi's control), and the ones that feel no pain (stun halved).
public class DbMob extends NarutoMob {
    // the dragon: flying with someone on its back (the client that drives it says so through the position)
    private static final EntityDataAccessor<Boolean> TAMED = SynchedEntityData.defineId(DbMob.class, EntityDataSerializers.BOOLEAN);
    // Babidi's M on the forehead: whose side it fights on now (the entity id of its master, -1 = none)
    private static final EntityDataAccessor<Integer> MASTER = SynchedEntityData.defineId(DbMob.class, EntityDataSerializers.INT);

    // the dragon: how many roast meats it took, how many it wants, who tamed it, how long it has flown
    private int fed;
    private int wants = -1;
    @Nullable
    private UUID owner;
    int airTicks;
    int restTicks;
    // the android's countdown (ticks left, -1 = not counting)
    int selfDestruct = -1;
    // how long the Majin mark lasts on a creature Babidi took
    int markLeft;

    public DbMob(EntityType<? extends DbMob> type, Level level) {
        super(type, level);
    }

    @Override
    public Kit kit() {
        return DbMobMoves.KIT;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(TAMED, false);
        builder.define(MASTER, -1);
    }

    public boolean isDragon() {
        return "dragao".equals(id());
    }

    public boolean tamed() {
        return entityData.get(TAMED);
    }

    public int master() {
        return entityData.get(MASTER);
    }

    void setMaster(int id, int ticks) {
        entityData.set(MASTER, id);
        markLeft = ticks;
    }

    // The flight animation plays instead of standing and walking while the dragon carries someone off the ground.
    @Override
    @Nullable
    public String loopMode() {
        if (isDragon() && isVehicle() && !onGroundish()) {
            return "voar";
        }
        return null;
    }

    private boolean onGroundish() {
        if (onGround()) {
            return true;
        }
        // the clients that do not drive it see it by its position only: on the ground when there is ground right under
        for (int dy = 1; dy <= 2; dy++) {
            if (!level().getBlockState(blockPosition().below(dy)).getCollisionShape(level(), blockPosition().below(dy)).isEmpty()) {
                return dy == 1 || getY() - Math.floor(getY()) < 0.3;
            }
        }
        return false;
    }

    // ---------- the dragon: taming and riding ----------

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!isDragon()) {
            return super.mobInteract(player, hand);
        }
        ItemStack stack = player.getItemInHand(hand);
        if (!tamed()) {
            if (!roast(stack) || getTarget() == player) {
                return super.mobInteract(player, hand);
            }
            if (!level().isClientSide) {
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                if (wants < 0) {
                    wants = 3 + random.nextInt(3);
                }
                fed++;
                heal(20);
                ServerLevel level = (ServerLevel) level();
                Vec3 head = position().add(forward().scale(3.2)).add(0, 3.6, 0);
                level.sendParticles(ParticleTypes.HEART, head.x, head.y, head.z, 3 + fed, 0.6, 0.4, 0.6, 0);
                playSound(SoundEvents.GENERIC_EAT, 1.6f, 0.6f);
                if (fed >= wants) {
                    entityData.set(TAMED, true);
                    owner = player.getUUID();
                    setTarget(null);
                    setPersistenceRequired();
                    level.sendParticles(ParticleTypes.HEART, head.x, head.y, head.z, 16, 1.2, 0.8, 1.2, 0);
                    playSound(SoundEvents.ENDER_DRAGON_AMBIENT, 1.2f, 1.6f);
                    com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon.warn(player, "dragao_domado");
                }
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (roast(stack) && getHealth() < getMaxHealth()) {
            if (!level().isClientSide) {
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                heal(40);
                playSound(SoundEvents.GENERIC_EAT, 1.6f, 0.7f);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (!isVehicle() && !player.isSecondaryUseActive()) {
            if (!level().isClientSide) {
                player.setYRot(getYRot());
                player.startRiding(this);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    // the dinosaurs' meat, roasted (the dragon only eats it cooked)
    private static boolean roast(ItemStack stack) {
        return stack.is(Items.COOKED_BEEF) || stack.is(Items.COOKED_MUTTON) || stack.is(Items.COOKED_PORKCHOP);
    }

    @Override
    @Nullable
    public LivingEntity getControllingPassenger() {
        return isDragon() && tamed() && getFirstPassenger() instanceof Player p ? p : super.getControllingPassenger();
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, net.minecraft.world.entity.EntityDimensions dimensions, float scale) {
        if (!isDragon()) {
            return super.getPassengerAttachmentPoint(passenger, dimensions, scale);
        }
        // on the neck's base, where the preview sits its rider
        return new Vec3(0, dimensions.height() * 0.62, 0.6);
    }

    @Override
    protected void tickRidden(Player player, Vec3 input) {
        super.tickRidden(player, input);
        // turns with whoever rides it
        setRot(player.getYRot(), player.getXRot() * 0.5f);
        yRotO = yBodyRot = yHeadRot = getYRot();
    }

    @Override
    protected Vec3 getRiddenInput(Player player, Vec3 input) {
        float strafe = player.xxa * 0.5f;
        float forward = player.zza;
        if (forward <= 0) {
            forward *= 0.35f;
        }
        return new Vec3(strafe, 0, forward);
    }

    @Override
    protected float getRiddenSpeed(Player player) {
        float base = (float) getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        return onGroundish() ? base * 0.9f : base * 2.6f;
    }

    // Flying: Space climbs (and takes off from the ground), Shift dives, it glides along where the rider looks. After 3
    // minutes in the air it tires and has to land and rest a little before it flies again.
    @Override
    public void travel(Vec3 input) {
        if (!isDragon() || !isVehicle() || !(getControllingPassenger() instanceof Player p) || !isControlledByLocalInstance()) {
            super.travel(input);
            return;
        }
        boolean up = p.jumping;
        boolean down = p.isShiftKeyDown();
        boolean tired = restTicks > 0;
        boolean ground = onGround();
        if (ground) {
            airTicks = 0;
            if (restTicks > 0) {
                restTicks--;
            }
        }
        if (ground && !(up && !tired)) {
            setNoGravity(false);
            super.travel(input);
            return;
        }
        // in the air (or taking off now)
        setNoGravity(true);
        if (++airTicks > 3600 && !tired) {
            restTicks = 200;
        }
        float speed = getRiddenSpeed(p);
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 side = new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw));
        double pitch = -p.getXRot() * Mth.DEG_TO_RAD;
        Vec3 want = fwd.scale(input.z * speed).add(side.scale(input.x * speed));
        double climb = input.z > 0 ? Math.sin(pitch) * speed * input.z * 0.8 : 0;
        if (up && !tired) {
            climb += ground ? 0.9 : 0.35;
        }
        if (down || tired) {
            climb -= tired ? 0.25 : 0.4;
        }
        Vec3 v = getDeltaMovement().scale(0.86).add(want.x * 0.18, climb * 0.25 - 0.012, want.z * 0.18);
        setDeltaMovement(v);
        move(MoverType.SELF, getDeltaMovement());
        calculateEntityAnimation(false);
        resetFallDistance();
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction move) {
        super.positionRider(passenger, move);
        if (passenger instanceof LivingEntity living) {
            living.yBodyRot = yBodyRot;
        }
    }

    private int rideCool;
    // a click that came during the breath or the pace waits for it, instead of being lost (test 2026-11-13: one click in two did nothing)
    private int rideQueued;

    @Override
    public void tick() {
        super.tick();
        if (rideCool > 0) {
            rideCool--;
        }
        if (rideQueued > 0 && !level().isClientSide) {
            rideQueued--;
            if (getControllingPassenger() instanceof Player rider && !attacking() && rideCool <= 0) {
                rideQueued = 0;
                breathe(rider);
            }
        }
    }

    // Ridden: left click breathes fire where the rider looks (PunchInput / the Punch payload).
    public boolean rideFire(Player player) {
        if (!isDragon() || getControllingPassenger() != player) {
            return false;
        }
        // its own pace when ridden (2.5 s), not the wild cooldown of 8 s (test 2026-11-13: the rider's clicks fell on it)
        if (attacking() || rideCool > 0) {
            // kept until the breath and the pace are over (at most 2.5 s), so a click never falls between them
            rideQueued = 60;
            return true;
        }
        breathe(player);
        return true;
    }

    private void breathe(Player player) {
        LivingEntity aim = com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon.aimed(player, 12);
        DbMobMoves.rideFire(this, player, aim);
        rideCool = 50;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isDragon() && tamed() && source.getEntity() != null && source.getEntity() == getControllingPassenger()) {
            // its rider's own blows never burn it
            return false;
        }
        return super.hurt(source, amount);
    }

    // ---------- the save ----------

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("DbTamed", tamed());
        tag.putInt("DbFed", fed);
        tag.putInt("DbWants", wants);
        if (owner != null) {
            tag.putUUID("DbOwner", owner);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(TAMED, tag.getBoolean("DbTamed"));
        fed = tag.getInt("DbFed");
        wants = tag.contains("DbWants") ? tag.getInt("DbWants") : -1;
        owner = tag.hasUUID("DbOwner") ? tag.getUUID("DbOwner") : null;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return !tamed() && master() < 0 && super.removeWhenFarAway(distance);
    }

    @Override
    protected boolean shouldDropLoot() {
        // a tamed dragon leaves nothing
        return !tamed() && super.shouldDropLoot();
    }

    @Override
    protected void customServerAiStep() {
        if (isVehicle() && isDragon()) {
            // the rider drives: no hunting of its own
            setTarget(null);
            getNavigation().stop();
        }
        if (markLeft > 0 && --markLeft == 0) {
            DbMobMoves.unmark(this);
        }
        super.customServerAiStep();
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        int m = master();
        if (m >= 0 && other instanceof DbMob d && (d.getId() == m || d.master() == m)) {
            return true;
        }
        return super.isAlliedTo(other);
    }

    // Gero's androids and Spopovich feel no pain: a light stun does nothing, a strong one is halved (Stun.apply)
    public boolean painless() {
        return "androide_gero".equals(id()) || "spopovich".equals(id());
    }

    public static net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder attributes(com.starkinhon.battlegroundsmod.animal.naruto.NarutoSpec.Spec s) {
        return NarutoMob.attributes(s);
    }
}
