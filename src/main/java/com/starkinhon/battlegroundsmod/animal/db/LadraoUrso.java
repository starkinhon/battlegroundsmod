package com.starkinhon.battlegroundsmod.animal.db;

import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Defense;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// The Bear Thief of Goku's mountains (Stark 2026-11-11, "praticamente perfeito"): hostile, heavy and slow. One big slash
// from above with its sword: it rises and roars for 0.8 s (time to get out), then the blow comes down hard enough to
// break a guard. If its target runs, it roars, and the roar slows whoever is near for 2 s.
public class LadraoUrso extends DbCreature {
    private static final int SLASH = 0;
    private static final int ROAR = 1;
    private static final double REACH = 3.2;

    private int roarCooldown = 100;
    private double lastGap = -1;

    public LadraoUrso(EntityType<? extends LadraoUrso> type, Level level) {
        super(type, level);
        this.xpReward = 18;
    }

    public static AttributeSupplier.Builder attributes() {
        return createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 140)
                .add(Attributes.ATTACK_DAMAGE, 6)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.FOLLOW_RANGE, 28)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.7)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, false));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 14f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public boolean isWithinMeleeAttackRange(LivingEntity target) {
        return inReach(target, REACH - 0.4);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (startAttack(SLASH, 38, 18)) {
            SubCommon.sound((ServerLevel) level(), position(), SoundEvents.POLAR_BEAR_WARNING, 1.4f, 0.75f);
            return true;
        }
        return false;
    }

    @Override
    protected void impact(int kind) {
        ServerLevel server = (ServerLevel) level();
        if (kind == ROAR) {
            roarHits(server);
            return;
        }
        LivingEntity target = getTarget();
        float yaw = getYRot() * net.minecraft.util.Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-net.minecraft.util.Mth.sin(yaw), 0, net.minecraft.util.Mth.cos(yaw));
        Vec3 at = position().add(fwd.scale(2.0)).add(0, 1.2, 0);
        // the cut, drawn from above down across the front
        Vec3 side = fwd.cross(new Vec3(0, 1, 0));
        SubNet.send(server, SubNet.SLASH, 0, at, side.scale(0.25).add(0, -1, 0).normalize(), 1.6f, 0.22f, SubNet.STYLE_CRIMSON);
        SubCommon.sound(server, at, SoundEvents.PLAYER_ATTACK_SWEEP, 1.4f, 0.6f);
        if (!inReach(target, REACH)) {
            return;
        }
        Defense.breakGuard(target, this);
        SubCommon.sound(server, at, SoundEvents.PLAYER_ATTACK_CRIT, 1.4f, 0.7f);
        Combat.hurt(this, target, 70);
        Stun.apply(target, 25);
        Combat.push(target, fwd, 0.7, 0.3);
    }

    // The roar: everyone close enough to hear it well slows down for 2 s.
    private void roarHits(ServerLevel server) {
        for (LivingEntity t : Combat.inRadius(server, position(), 12, this)) {
            if (t instanceof LadraoUrso) {
                continue;
            }
            t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2), this);
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        roarCooldown = Math.max(0, roarCooldown - 1);
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive() || attacking() || Stun.real(this)) {
            lastGap = -1;
            return;
        }
        double gap = distanceTo(target);
        // running away: farther than 6 and getting farther
        if (roarCooldown <= 0 && lastGap >= 0 && gap > 6 && gap < 14 && gap > lastGap + 0.05 && hasLineOfSight(target)) {
            if (startAttack(ROAR, 24, 5)) {
                SubCommon.sound((ServerLevel) level(), position(), SoundEvents.RAVAGER_ROAR, 1.8f, 0.9f);
                SubCommon.sound((ServerLevel) level(), position(), SoundEvents.POLAR_BEAR_WARNING, 1.8f, 0.6f);
                roarCooldown = 200 + random.nextInt(80);
            }
        }
        lastGap = gap;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.POLAR_BEAR_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.POLAR_BEAR_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.POLAR_BEAR_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.8f + (random.nextFloat() - 0.5f) * 0.1f;
    }
}
