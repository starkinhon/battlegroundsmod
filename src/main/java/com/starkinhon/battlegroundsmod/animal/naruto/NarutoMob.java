package com.starkinhon.battlegroundsmod.animal.naruto;

import java.util.EnumSet;
import java.util.List;

import javax.annotation.Nullable;

import com.starkinhon.battlegroundsmod.animal.db.DbCreature;
import com.starkinhon.battlegroundsmod.animal.naruto.NarutoSpec.Attack;
import com.starkinhon.battlegroundsmod.animal.naruto.NarutoSpec.Behavior;
import com.starkinhon.battlegroundsmod.animal.naruto.NarutoSpec.Move;
import com.starkinhon.battlegroundsmod.animal.naruto.NarutoSpec.Spec;
import com.starkinhon.battlegroundsmod.combat.Stun;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingLookControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;

// One of the Naruto biomes' creatures (Stark 2026-11-12, preview tools/mobs/naruto approved: "tá perfeito"). Which one
// comes from its entity type's id (NarutoSpec), so the 24 kinds share this body: the model, skin and the baked preview
// animation are the client's (NarutoMobRenderer), the blows NarutoMoves'. A blow that moves the body in the preview
// walks it here along the same path, tick by tick (NarutoTravel), and the client leaves that part out of the animation,
// so the creature really goes where the preview showed it go (and hits there). A blow that hides the body teleports it
// while hidden (NarutoMoves.teleport).
public class NarutoMob extends DbCreature {
    private static final EntityDataAccessor<Integer> LINK = SynchedEntityData.defineId(NarutoMob.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> CLONE = SynchedEntityData.defineId(NarutoMob.class, EntityDataSerializers.BOOLEAN);

    private Spec spec;
    private final int[] cooldowns = new int[4];
    // the blow now playing: the target and the body's yaw when it started, and the preview's path for it
    private float[][] travel;
    private float travelYaw;
    public LivingEntity blowTarget;
    public Vec3 blowSpot = Vec3.ZERO;
    // a few moves keep something between ticks (a hold to release, the clones out)
    public Runnable release;
    public int heldId = -1;
    public int lifeLeft = -1;
    public final java.util.List<Integer> spawned = new java.util.ArrayList<>();
    public int sleep;
    private int alertCooldown;

    // What a kind of creature does with this body: its blows, gates and voice. The Naruto creatures' kit is NarutoMoves;
    // the Dragon Ball creatures (animal/dbmob) bring their own and share everything else (AI, travel, baked animation).
    public interface Kit {
        Spec spec(String id);

        @Nullable
        float[][] travel(String id, String mode);

        boolean canUse(NarutoMob m, String mode, LivingEntity t);

        @Nullable
        Vec3 fleeFrom(NarutoMob m);

        void start(NarutoMob m, String mode, @Nullable LivingEntity t);

        void tick(NarutoMob m, String mode, int tick, @Nullable LivingEntity t);

        void impact(NarutoMob m, String mode, @Nullable LivingEntity t);

        void end(NarutoMob m, String mode, @Nullable LivingEntity t);

        void teleport(NarutoMob m, String mode, @Nullable LivingEntity t);

        boolean answer(NarutoMob m, LivingEntity attacker, DamageSource source, float amount);

        void hurtBy(NarutoMob m, LivingEntity attacker, float amount);

        void died(NarutoMob m);

        void idle(NarutoMob m);

        void drop(NarutoMob m, ServerLevel level, boolean byPlayer);

        @Nullable
        SoundEvent ambient(NarutoMob m);

        @Nullable
        SoundEvent hurtSound(NarutoMob m);

        @Nullable
        SoundEvent deathSound(NarutoMob m);

        float pitch(NarutoMob m);
    }

    private static final Kit NARUTO = new Kit() {
        public Spec spec(String id) {
            return NarutoSpec.get(id);
        }

        public float[][] travel(String id, String mode) {
            return NarutoTravel.of(id, mode);
        }

        public boolean canUse(NarutoMob m, String mode, LivingEntity t) {
            return NarutoMoves.canUse(m, mode, t);
        }

        public Vec3 fleeFrom(NarutoMob m) {
            return NarutoMoves.fleeFrom(m);
        }

        public void start(NarutoMob m, String mode, LivingEntity t) {
            NarutoMoves.start(m, mode, t);
        }

        public void tick(NarutoMob m, String mode, int tick, LivingEntity t) {
            NarutoMoves.tick(m, mode, tick, t);
        }

        public void impact(NarutoMob m, String mode, LivingEntity t) {
            NarutoMoves.impact(m, mode, t);
        }

        public void end(NarutoMob m, String mode, LivingEntity t) {
            NarutoMoves.end(m, mode, t);
        }

        public void teleport(NarutoMob m, String mode, LivingEntity t) {
            NarutoMoves.teleport(m, mode, t);
        }

        public boolean answer(NarutoMob m, LivingEntity attacker, DamageSource source, float amount) {
            return NarutoMoves.answer(m, attacker, source, amount);
        }

        public void hurtBy(NarutoMob m, LivingEntity attacker, float amount) {
            NarutoMoves.hurtBy(m, attacker, amount);
        }

        public void died(NarutoMob m) {
            NarutoMoves.died(m);
        }

        public void idle(NarutoMob m) {
            NarutoMoves.idle(m);
        }

        public void drop(NarutoMob m, ServerLevel level, boolean byPlayer) {
            NarutoDrops.drop(m, level, byPlayer);
        }

        public SoundEvent ambient(NarutoMob m) {
            return NarutoMoves.ambient(m);
        }

        public SoundEvent hurtSound(NarutoMob m) {
            return NarutoMoves.hurtSound(m);
        }

        public SoundEvent deathSound(NarutoMob m) {
            return NarutoMoves.deathSound(m);
        }

        public float pitch(NarutoMob m) {
            return NarutoMoves.pitch(m);
        }
    };

    // the kind's kit (a subclass for another universe overrides this)
    public Kit kit() {
        return NARUTO;
    }

    public NarutoMob(EntityType<? extends NarutoMob> type, Level level) {
        super(type, level);
        Spec s = spec();
        this.xpReward = s.xp;
        if (s.move == Move.FLYER) {
            this.moveControl = new FlyingMoveControl(this, 20, true);
            setNoGravity(true);
        } else if (s.move == Move.WATER) {
            this.moveControl = new SmoothSwimmingMoveControl(this, 85, 10, 0.1f, 0.5f, false);
            this.lookControl = new SmoothSwimmingLookControl(this, 10);
            setPathfindingMalus(PathType.WATER, 0f);
        }
    }

    public Spec spec() {
        if (spec == null) {
            spec = kit().spec(BuiltInRegistries.ENTITY_TYPE.getKey(getType()).getPath());
        }
        return spec;
    }

    public String id() {
        return spec().id;
    }

    // a mode the client loops instead of standing and walking (the ridden dragon's flight), null for none
    @Nullable
    public String loopMode() {
        return null;
    }

    // the creatures' own events (the spores ticking on whoever carries them)
    public static void register() {
        NarutoMoves.register();
    }

    public static AttributeSupplier.Builder attributes(Spec s) {
        return createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, s.health)
                .add(Attributes.ATTACK_DAMAGE, 4)
                .add(Attributes.MOVEMENT_SPEED, s.speed)
                .add(Attributes.FLYING_SPEED, s.speed)
                .add(Attributes.FOLLOW_RANGE, 28)
                .add(Attributes.KNOCKBACK_RESISTANCE, s.knockback)
                .add(Attributes.STEP_HEIGHT, s.height > 1.5f ? 1.0 : 0.6);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(LINK, -1);
        builder.define(CLONE, false);
    }

    // the marionetista and its marionete (the entity id of the other one, -1 = none)
    public int link() {
        return entityData.get(LINK);
    }

    public void setLink(int id) {
        entityData.set(LINK, id);
    }

    @Nullable
    public NarutoMob partner() {
        int id = link();
        return id >= 0 && level().getEntity(id) instanceof NarutoMob m && m.isAlive() ? m : null;
    }

    // a water clone of the Espadachim (one hit bursts it)
    public boolean isClone() {
        return entityData.get(CLONE);
    }

    void makeClone(int life) {
        entityData.set(CLONE, true);
        lifeLeft = life;
        setHealth(1);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        Move m = spec().move;
        if (m == Move.FLYER) {
            FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
            nav.setCanOpenDoors(false);
            nav.setCanFloat(true);
            return nav;
        }
        if (m == Move.WATER) {
            return new WaterBoundPathNavigation(this, level);
        }
        return super.createNavigation(level);
    }

    // The pterodactyls soar over the land (Stark 2026-11-13: they hung in one spot in the sky, flapping): the vanilla
    // random flight looks for spots near the ground and finds none up there. A far point ahead every so often, 16-44
    // blocks away and high over the ground under it, flown to straight.
    private final class SoarGoal extends Goal {
        private Vec3 to;

        SoarGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return getTarget() == null && !isPassenger();
        }

        @Override
        public void start() {
            pick();
        }

        @Override
        public void tick() {
            if (to == null || position().distanceToSqr(to) < 16 || horizontalCollision || getRandom().nextInt(300) == 0) {
                pick();
            }
            // glided straight: the flying move control crawled at ~1 block/s up there (2026-11-13 test), ~5 now
            Vec3 dir = to.subtract(position()).normalize();
            // never down in the trees, the hills or the water on the way: climb first (one got stuck in a crown,
            // another floated on the sea in the 2026-11-13 test)
            int under = level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, getBlockX(), getBlockZ());
            if (getY() < under + 10 || horizontalCollision || isInWater()) {
                dir = new Vec3(dir.x * 0.5, 0.9, dir.z * 0.5).normalize();
            }
            setDeltaMovement(getDeltaMovement().lerp(dir.scale(0.55), 0.08));
            Vec3 v = getDeltaMovement();
            float yaw = (float) (Mth.atan2(v.z, v.x) * Mth.RAD_TO_DEG) - 90f;
            setYRot(Mth.rotLerp(0.15f, getYRot(), yaw));
            yBodyRot = getYRot();
            yHeadRot = getYRot();
        }

        private void pick() {
            double a = Math.toRadians(getYRot() + 90) + (getRandom().nextDouble() - 0.5) * 2.4;
            double d = 16 + getRandom().nextDouble() * 28;
            int x = Mth.floor(getX() + Math.cos(a) * d), z = Mth.floor(getZ() + Math.sin(a) * d);
            int ground = level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
            double y = Mth.clamp(ground + 18 + getRandom().nextInt(16), level().getMinBuildHeight() + 8, level().getMaxBuildHeight() - 8);
            to = new Vec3(x + 0.5, y, z + 0.5);
        }
    }

    @Override
    protected void registerGoals() {
        Spec s = spec();
        if (s.move != Move.WATER) {
            goalSelector.addGoal(0, new FloatGoal(this));
        }
        goalSelector.addGoal(1, new FleeGoal());
        goalSelector.addGoal(2, new FightGoal());
        if (s.id.equals("marionetista")) {
            goalSelector.addGoal(3, new BehindPuppetGoal());
        }
        switch (s.move) {
            case FLYER -> goalSelector.addGoal(5, s.id.equals("pterodactilo") ? new SoarGoal() : new WaterAvoidingRandomFlyingGoal(this, 0.8));
            case WATER -> goalSelector.addGoal(5, new RandomSwimmingGoal(this, 0.8, 20));
            default -> goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7));
        }
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        if (s.behavior == Behavior.HOSTILE) {
            targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, this::prey));
        } else if (s.behavior == Behavior.TERRITORIAL) {
            targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 5, true, false,
                    p -> prey(p) && p.distanceTo(this) <= spec().guard));
        }
    }

    // who it hunts: the shark only someone it can reach from the water
    private boolean prey(LivingEntity e) {
        if (spec().move != Move.WATER) {
            return true;
        }
        if (e.isInWater()) {
            return true;
        }
        BlockPos below = e.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(below.offset(-2, -3, -2), below.offset(2, 0, 2))) {
            if (!level().getFluidState(p).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        Spec s = spec();
        float k = s.scale == null ? 1 : s.scale[Math.floorMod(variant(), s.scale.length)];
        return EntityDimensions.scalable(s.width * k, s.height * k);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType type, @Nullable SpawnGroupData data) {
        setVariant(random.nextInt(Math.max(1, spec().variants)));
        SpawnGroupData out = super.finalizeSpawn(level, difficulty, type, data);
        if (id().equals("marionetista") && level instanceof ServerLevelAccessor) {
            NarutoMoves.spawnPuppet(this, level.getLevel());
        }
        return out;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("NrClone", isClone());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.getBoolean("NrClone")) {
            // a water clone never outlives the fight it was made for
            discard();
        }
    }

    // ---------- blows ----------

    public boolean ready(int kind) {
        return kind >= 0 && kind < cooldowns.length && cooldowns[kind] <= 0;
    }

    // Starts this blow against target: the cooldown runs from now, the body's path is the preview's.
    public boolean blow(int kind, @Nullable LivingEntity target) {
        Attack a = spec().attack(kind);
        if (a == null || !startAttack(kind, a.length(), Math.max(1, a.impact()))) {
            return false;
        }
        cooldowns[kind] = a.cooldown() + random.nextInt(20);
        blowTarget = target;
        blowSpot = target != null ? target.position() : position();
        travel = kit().travel(id(), a.mode());
        if (target != null) {
            double dx = target.getX() - getX(), dz = target.getZ() - getZ();
            if (dx * dx + dz * dz > 1.0E-4) {
                float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
                setYRot(yaw);
                yBodyRot = yaw;
                yHeadRot = yaw;
            }
        }
        travelYaw = getYRot();
        kit().start(this, a.mode(), target);
        return true;
    }

    @Override
    protected void impact(int kind) {
        Attack a = spec().attack(kind);
        if (a != null) {
            kit().impact(this, a.mode(), blowTarget);
        }
    }

    @Override
    protected void attackTick(int kind, int tick) {
        Attack a = spec().attack(kind);
        if (a == null) {
            return;
        }
        int frame = tick - 1;
        if (travel != null && frame >= 1 && frame < travel[0].length) {
            // walk the body along the preview's path (x right, z back, in blocks), facing where it faced at the start
            float dx = travel[0][frame] - travel[0][frame - 1];
            float dz = travel[1][frame] - travel[1][frame - 1];
            float yaw = travelYaw * Mth.DEG_TO_RAD;
            Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
            Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
            Vec3 step = right.scale(dx).add(fwd.scale(-dz));
            setYRot(travelYaw);
            yBodyRot = travelYaw;
            yHeadRot = travelYaw;
            move(MoverType.SELF, step);
        }
        if (frame == spec().teleportFrame(a.mode())) {
            kit().teleport(this, a.mode(), blowTarget);
        }
        kit().tick(this, a.mode(), tick, blowTarget);
        if (tick >= attackLength) {
            kit().end(this, a.mode(), blowTarget);
        }
    }

    public boolean reaches(LivingEntity target, double reach) {
        return inReach(target, reach);
    }

    public int blowTick() {
        return attackTimer;
    }

    // ends the blow now (the shark out of the water, a prison broken)
    public void cutBlow() {
        if (attackTimer > 0) {
            attackTimer = attackLength;
        }
    }

    // the yaw the blow started facing (travel and teleports are relative to it)
    public float blowYaw() {
        return travelYaw;
    }

    // turns the blow under way (a ridden dragon breathes where its rider looks)
    public void faceBlow(float yaw) {
        travelYaw = yaw;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    public Vec3 forward() {
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        // the blows come from FightGoal; vanilla's melee never fires for these
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) {
            return super.hurt(source, amount);
        }
        if (isClone()) {
            // a water clone bursts in one blow, in water
            if (!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                NarutoMoves.burstClone(this);
                return true;
            }
        }
        Entity attacker = source.getEntity();
        if (attacker instanceof LivingEntity living && kit().answer(this, living, source, amount)) {
            // the Substituição: the blow hits a log
            return false;
        }
        if (id().equals("clone_madeira") && source.is(DamageTypeTags.IS_FIRE)) {
            amount *= 2;
        }
        boolean hurt = super.hurt(source, amount);
        if (hurt && attacker instanceof LivingEntity living && !(attacker instanceof NarutoMob)) {
            kit().hurtBy(this, living, amount);
            alertPack(living);
        }
        return hurt;
    }

    // the pack fights together: every one of its kind near the one hurt turns on whoever did it
    private void alertPack(LivingEntity attacker) {
        if (alertCooldown > 0 || attacker instanceof Player p && (p.isCreative() || p.isSpectator())) {
            return;
        }
        alertCooldown = 20;
        double r = spec().behavior == Behavior.HOSTILE ? 12 : 20;
        for (NarutoMob m : level().getEntitiesOfClass(NarutoMob.class, getBoundingBox().inflate(r), m -> m != this && m.getType() == getType())) {
            if (m.getTarget() == null || !m.getTarget().isAlive()) {
                m.setTarget(attacker);
            }
        }
    }

    @Override
    public void die(DamageSource source) {
        kit().died(this);
        super.die(source);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (release != null) {
            release.run();
            release = null;
        }
        super.remove(reason);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        for (int i = 0; i < cooldowns.length; i++) {
            if (cooldowns[i] > 0) {
                cooldowns[i]--;
            }
        }
        if (alertCooldown > 0) {
            alertCooldown--;
        }
        if (lifeLeft > 0 && --lifeLeft == 0) {
            NarutoMoves.burstClone(this);
            return;
        }
        kit().idle(this);
    }

    // canBreatheUnderwater is final under NeoForge: the shark breathes in water through the fluid-type hook
    @Override
    public boolean canDrownInFluidType(net.neoforged.neoforge.fluids.FluidType type) {
        return spec().move != Move.WATER && super.canDrownInFluidType(type);
    }

    @Override
    public boolean isPushedByFluid() {
        return spec().move != Move.WATER && super.isPushedByFluid();
    }

    @Override
    public void travel(Vec3 input) {
        if (spec().move == Move.WATER && isControlledByLocalInstance() && isInWater() && !attacking()) {
            moveRelative(getSpeed(), input);
            move(MoverType.SELF, getDeltaMovement());
            setDeltaMovement(getDeltaMovement().scale(0.9));
            return;
        }
        super.travel(input);
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, net.minecraft.world.level.block.state.BlockState state, BlockPos pos) {
        if (spec().move != Move.FLYER) {
            super.checkFallDamage(y, onGround, state, pos);
        }
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        // the ninja land on their feet; the Sapo Gigante's leap is its own blow
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return !spec().exclusive && super.removeWhenFarAway(distance);
    }

    @Override
    protected boolean shouldDropLoot() {
        return !isClone() && super.shouldDropLoot();
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        if (!isClone()) {
            kit().drop(this, level, recentlyHit);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return kit().ambient(this);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return kit().hurtSound(this);
    }

    @Override
    protected SoundEvent getDeathSound() {
        return kit().deathSound(this);
    }

    @Override
    public float getVoicePitch() {
        return kit().pitch(this) + (random.nextFloat() - 0.5f) * 0.1f;
    }

    // ---------- AI ----------

    public double gap(LivingEntity target) {
        return Math.max(0, distanceTo(target) - (getBbWidth() + target.getBbWidth()) * 0.5);
    }

    // Picks the blow for the distance and closes in (or keeps its distance, the ones that fight from afar).
    private final class FightGoal extends Goal {
        private int repath;
        private int strafe;

        FightGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = getTarget();
            return t != null && t.isAlive() && !isClone() || isClone() && t != null;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity t = getTarget();
            if (t == null || attacking()) {
                return;
            }
            getLookControl().setLookAt(t, 30f, 30f);
            double gap = gap(t);
            Spec s = spec();
            if (!isClone() && !Stun.real(NarutoMob.this) && hasLineOfSight(t)) {
                Attack[] list = s.attacks;
                int first = random.nextInt(list.length);
                for (int k = 0; k < list.length; k++) {
                    int i = (first + k) % list.length;
                    Attack a = list[i];
                    if (a.reactive() || !ready(i) || gap < a.min() || gap > a.max() || !kit().canUse(NarutoMob.this, a.mode(), t)) {
                        continue;
                    }
                    if (blow(i, t)) {
                        return;
                    }
                }
            }
            if (s.move == Move.FLYER) {
                // hovers a little above and to the side of its target
                Vec3 want = t.position().add(0, t.getBbHeight() + 1.2, 0).add(t.position().subtract(position()).normalize().scale(-2.5));
                getMoveControl().setWantedPosition(want.x, want.y, want.z, 1.1);
                return;
            }
            if (--repath > 0) {
                return;
            }
            repath = 6;
            double keep = s.keep;
            if (keep > 0 && gap < keep - 1.5 && !isClone()) {
                // too close for its kind of fight: steps back from the target
                Vec3 away = position().subtract(t.position()).multiply(1, 0, 1).normalize().scale(4);
                Vec3 to = position().add(away);
                getNavigation().moveTo(to.x, to.y, to.z, 1.15);
            } else if (keep > 0 && gap < keep + 1.5 && !isClone()) {
                // in its band: circles slowly
                if (--strafe <= 0) {
                    strafe = 6 + random.nextInt(6);
                    Vec3 side = t.position().subtract(position()).cross(new Vec3(0, random.nextBoolean() ? 1 : -1, 0)).normalize().scale(3);
                    Vec3 to = position().add(side);
                    getNavigation().moveTo(to.x, to.y, to.z, 0.9);
                }
            } else if (gap > 0.8) {
                getNavigation().moveTo(t, 1.2);
            } else {
                getNavigation().stop();
            }
        }
    }

    // The tiger runs when it is nearly dead; the beetle runs from the frogs that hunt it.
    private final class FleeGoal extends Goal {
        private Vec3 from;

        FleeGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (attacking()) {
                return false;
            }
            from = kit().fleeFrom(NarutoMob.this);
            return from != null;
        }

        @Override
        public void start() {
            Vec3 away = position().subtract(from).multiply(1, 0, 1);
            away = away.lengthSqr() < 1.0E-3 ? forward() : away.normalize();
            Vec3 to = position().add(away.scale(10));
            getNavigation().moveTo(to.x, to.y, to.z, 1.4);
        }

        @Override
        public boolean canContinueToUse() {
            return !getNavigation().isDone();
        }
    }

    // The marionetista keeps 3-4 blocks behind its puppet, hidden from whoever it fights.
    private final class BehindPuppetGoal extends Goal {
        private int repath;

        BehindPuppetGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return partner() != null && !attacking();
        }

        @Override
        public void tick() {
            NarutoMob puppet = partner();
            if (puppet == null || --repath > 0) {
                return;
            }
            repath = 8;
            LivingEntity t = puppet.getTarget() != null ? puppet.getTarget() : getTarget();
            Vec3 back = t != null ? puppet.position().subtract(t.position()).multiply(1, 0, 1) : puppet.forward().scale(-1);
            back = back.lengthSqr() < 1.0E-3 ? puppet.forward().scale(-1) : back.normalize();
            Vec3 spot = puppet.position().add(back.scale(3.5));
            if (distanceToSqr(spot) > 1.5) {
                getNavigation().moveTo(spot.x, spot.y, spot.z, 1.1);
            }
            if (t != null) {
                getLookControl().setLookAt(t, 30f, 30f);
            }
        }
    }

    public static List<LivingEntity> around(NarutoMob mob, Vec3 at, double r) {
        return com.starkinhon.battlegroundsmod.combat.Combat.inRadius((ServerLevel) mob.level(), at, r, mob);
    }
}
