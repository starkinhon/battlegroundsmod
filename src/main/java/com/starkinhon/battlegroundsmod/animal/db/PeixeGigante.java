package com.starkinhon.battlegroundsmod.animal.db;

import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.Db;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.DbCommon;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.SmoothSwimmingLookControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;

// The giant fish of the southern sea (Stark 2026-11-11, "ficou bala"): the one that tried to eat Goku. It lives in deep
// water and goes for anyone in the water, in a boat, or standing at the edge over it: it rises, leaps out up to 4 blocks
// with its mouth open and bites, and the bite drags its prey off the boat or the shore into the water. On land it
// flops about and dries out.
public class PeixeGigante extends DbCreature {
    private static final double BITE_REACH = 2.6;
    private int dry;
    private int leapCooldown;
    private Vec3 leapDir = Vec3.ZERO;

    public PeixeGigante(EntityType<? extends PeixeGigante> type, Level level) {
        super(type, level);
        this.xpReward = 20;
        this.moveControl = new SmoothSwimmingMoveControl(this, 85, 10, 0.1f, 0.5f, false);
        this.lookControl = new SmoothSwimmingLookControl(this, 10);
        setPathfindingMalus(PathType.WATER, 0f);
    }

    public static AttributeSupplier.Builder attributes() {
        return createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 120)
                .add(Attributes.ATTACK_DAMAGE, 6)
                .add(Attributes.MOVEMENT_SPEED, 1.1)
                .add(Attributes.FOLLOW_RANGE, 32)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6);
    }

    // Someone it can reach: in the water, in a boat, or on the shore right over it.
    private boolean prey(LivingEntity e) {
        if (e.isInWater() || e.getVehicle() instanceof Boat) {
            return true;
        }
        if (Math.abs(e.getY() - getY()) > 6) {
            return false;
        }
        BlockPos below = e.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(below.offset(-2, -2, -2), below.offset(2, 0, 2))) {
            if (!level().getFluidState(p).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.3, true));
        goalSelector.addGoal(5, new RandomSwimmingGoal(this, 0.8, 20));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, false, false, this::prey));
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new WaterBoundPathNavigation(this, level);
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public void travel(Vec3 input) {
        if (isControlledByLocalInstance() && isInWater() && !attacking()) {
            moveRelative(getSpeed(), input);
            move(MoverType.SELF, getDeltaMovement());
            setDeltaMovement(getDeltaMovement().scale(0.9));
        } else {
            super.travel(input);
        }
    }

    @Override
    public boolean isWithinMeleeAttackRange(LivingEntity target) {
        // leaps from up to 6 blocks off, never from the land
        return isInWater() && leapCooldown <= 0 && distanceTo(target) < 6.5;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (startAttack(0, 40, 19)) {
            Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(position());
            leapDir = to;
            leapCooldown = 50;
            return true;
        }
        return false;
    }

    @Override
    protected void attackTick(int kind, int tick) {
        LivingEntity target = getTarget();
        if (target != null && tick < 6) {
            // rises toward its prey before breaking the surface
            leapDir = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(position());
            setDeltaMovement(getDeltaMovement().scale(0.5).add(0, isUnderWater() ? 0.3 : 0.05, 0));
        }
        if (tick == 6) {
            Vec3 flat = new Vec3(leapDir.x, 0, leapDir.z);
            double d = Math.min(6, flat.length());
            Vec3 h = flat.lengthSqr() < 1.0E-3 ? Vec3.ZERO : flat.normalize().scale(0.18 + d * 0.09);
            // up to 4 blocks: higher when the prey is higher
            double up = clamp(0.55 + Math.max(0, leapDir.y) * 0.13, 0.55, 1.05);
            setDeltaMovement(h.x, up, h.z);
            hasImpulse = true;
            SubCommon.sound((ServerLevel) level(), position(), SoundEvents.GENERIC_SPLASH, 1.5f, 0.6f);
        }
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    @Override
    protected void impact(int kind) {
        ServerLevel server = (ServerLevel) level();
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive() || distanceTo(target) > BITE_REACH + getBbWidth() * 0.5) {
            SubCommon.sound(server, position(), SoundEvents.EVOKER_FANGS_ATTACK, 1.2f, 0.5f);
            return;
        }
        Vec3 at = Combat.center(target);
        SubCommon.sound(server, at, SoundEvents.EVOKER_FANGS_ATTACK, 1.5f, 0.6f);
        SubCommon.sound(server, at, SoundEvents.PLAYER_ATTACK_CRIT, 1.2f, 0.6f);
        DbCommon.hit(server, at, 0.6f, Db.WHITE);
        Combat.hurt(this, target, 30);
        Stun.apply(target, 25);
        // the bite drags it off the boat or the shore, into the water after the fish
        target.stopRiding();
        Vec3 pull = position().subtract(target.position());
        Combat.push(target, pull, 0.6, -0.1);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            return;
        }
        leapCooldown = Math.max(0, leapCooldown - 1);
        if (isInWater()) {
            dry = 0;
            return;
        }
        // out of the water: flops, and dries out
        if (onGround() && random.nextInt(12) == 0) {
            setDeltaMovement(getDeltaMovement().add((random.nextFloat() - 0.5) * 0.3, 0.35, (random.nextFloat() - 0.5) * 0.3));
            playSound(SoundEvents.GUARDIAN_FLOP, 1.2f, 0.6f);
        }
        if (++dry > 100 && dry % 20 == 0) {
            hurt(damageSources().dryOut(), 6);
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        LivingEntity target = getTarget();
        // whoever got away from the water is no longer its prey
        if (target != null && !attacking() && tickCount % 20 == 0 && !prey(target) && getLastHurtByMob() != target) {
            setTarget(null);
            return;
        }
        // swims straight at prey it can't path to (on the shore, in a boat), while the water goes on that way
        if (target != null && !attacking() && isInWater() && distanceTo(target) > 4 && getNavigation().isDone()) {
            Vec3 to = target.position().subtract(position());
            Vec3 step = position().add(to.normalize().scale(1.5));
            if (!level().getFluidState(BlockPos.containing(step.x, Math.min(step.y, getY()), step.z)).isEmpty()) {
                getMoveControl().setWantedPosition(target.getX(), Math.min(target.getY() - 0.5, getY() + 1), target.getZ(), 1.3);
            }
        }
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return distance > 96 * 96;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.COD_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.COD_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.5f;
    }
}
