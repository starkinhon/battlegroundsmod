package com.starkinhon.battlegroundsmod.animal.db;

import com.starkinhon.battlegroundsmod.ModEntities;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.Db;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.DbCommon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

// Android 8 of the frozen north (Stark 2026-11-11, "MUITO BALA"): the gentle giant. Never starts a fight with a person;
// hit him and he answers with both fists brought down together, a slam that cracks the ground and stuns everyone within
// 3 blocks for 1.5 s. The yetis he fights on sight (he looks after whoever walks the snow).
public class Androide8 extends DbCreature {
    private static final double RADIUS = 3;

    public Androide8(EntityType<? extends Androide8> type, Level level) {
        super(type, level);
        this.xpReward = 30;
    }

    public static AttributeSupplier.Builder attributes() {
        return createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 300)
                .add(Attributes.ATTACK_DAMAGE, 8)
                .add(Attributes.MOVEMENT_SPEED, 0.24)
                .add(Attributes.FOLLOW_RANGE, 24)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.STEP_HEIGHT, 1.5);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, false));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.5));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false,
                e -> e.getType() == ModEntities.YETI.get()));
    }

    private Vec3 slamPoint() {
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        return position().add(-Mth.sin(yaw) * 1.6, 0, Mth.cos(yaw) * 1.6);
    }

    @Override
    public boolean isWithinMeleeAttackRange(LivingEntity target) {
        return inReach(target, 2.6);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (startAttack(0, 40, 21)) {
            SubCommon.sound((ServerLevel) level(), position(), SoundEvents.IRON_GOLEM_REPAIR, 1.2f, 0.5f);
            return true;
        }
        return false;
    }

    @Override
    protected void impact(int kind) {
        ServerLevel server = (ServerLevel) level();
        Vec3 g = slamPoint();
        SubCommon.sound(server, g, SoundEvents.GENERIC_EXPLODE.value(), 1.4f, 0.6f);
        SubCommon.sound(server, g, SoundEvents.IRON_GOLEM_ATTACK, 1.6f, 0.5f);
        DbCommon.boom(server, g.add(0, 0.1, 0), 2.4f, Db.WHITE, 2000 + SubCommon.RANDOM.nextInt(100));
        float turn = SubCommon.RANDOM.nextFloat() * Mth.TWO_PI;
        for (int i = 0; i < 4; i++) {
            double a = turn + i * Mth.TWO_PI / 4 + SubCommon.RANDOM.nextGaussian() * 0.35;
            Vec3 dir = new Vec3(Math.cos(a), 0, Math.sin(a));
            SubNet.send(server, SubNet.SCAR, 0, g.add(0, 0.05, 0), dir.scale(3.2 + SubCommon.RANDOM.nextDouble() * 1.8), 0.5f, 100, 0);
        }
        DbCommon.screen(server, g, 10);
        for (LivingEntity t : Combat.inRadius(server, g, RADIUS, this)) {
            if (t instanceof Androide8) {
                continue;
            }
            double k = 1 - 0.4 * Math.min(1, t.position().distanceTo(g) / RADIUS);
            Combat.hurt(this, t, (float) (60 * k));
            Stun.apply(t, 30);
            Combat.pushFrom(t, g, 0.4 * k, 0.35);
        }
    }

    // He lives in the snow, alone: never wanders off for good.
    @Override
    public boolean removeWhenFarAway(double distance) {
        return distance > 128 * 128;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.IRON_GOLEM_DEATH;
    }

    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, BlockState state) {
        playSound(SoundEvents.IRON_GOLEM_STEP, 0.8f, 0.7f);
    }
}
