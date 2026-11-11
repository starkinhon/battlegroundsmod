package com.starkinhon.battlegroundsmod.animal;

import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.DbMobs;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
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
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

// The dinosaurs of the Dragon World (Stark 2026-11-10: "o que mais tem no verso"): the tyrannosaur, big and alone, slow
// to turn but one bite takes a lot, and the raptors, small and fast, in packs. Each has two moves (power/powers/sub/db/
// DbMobs, Stark 2026-11-10 "faz os outros poderes dos inimigos"): the raptor pounces (Bote) and claws (Garras), the
// tyrannosaur charges (Investida) and stomps (Pisão Sísmico), picked by distance with their own cooldowns; their tier comes from their health like any monster's (padrao.progressao.json mobsVanilla): raptor 24 = Tier 10,
// tyrannosaur 160 = Tier 8 (Stark 2026-11-10: every enemy was Tier 10, nothing grew a player past Tier 7). One class for both: the
// entity type says which (rex()).
public class Dinosaur extends Monster {
    // ticks until each move is ready again, and while one is playing out
    private int zCooldown = 60;
    private int xCooldown = 40;
    private int busy;

    public Dinosaur(EntityType<? extends Dinosaur> type, Level level) {
        super(type, level);
        this.xpReward = rex() ? 20 : 6;
    }

    public boolean rex() {
        return "tiranossauro".equals(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(getType()).getPath());
    }

    public static AttributeSupplier.Builder attributes(boolean rex) {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, rex ? 160 : 24)
                .add(Attributes.ATTACK_DAMAGE, rex ? 7 : 2.5)
                .add(Attributes.MOVEMENT_SPEED, rex ? 0.27 : 0.36)
                .add(Attributes.FOLLOW_RANGE, rex ? 28 : 24)
                .add(Attributes.KNOCKBACK_RESISTANCE, rex ? 0.85 : 0.1)
                .add(Attributes.ATTACK_KNOCKBACK, rex ? 1.2 : 0.2)
                .add(Attributes.STEP_HEIGHT, rex ? 1.5 : 1.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, rex() ? 1.15 : 1.35, false));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        zCooldown = Math.max(0, zCooldown - 1);
        xCooldown = Math.max(0, xCooldown - 1);
        if (busy > 0) {
            busy--;
            return;
        }
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive() || !(level() instanceof ServerLevel server) || Stun.real(this)
                || !hasLineOfSight(target)) {
            return;
        }
        double d = distanceTo(target);
        if (rex()) {
            if (xCooldown <= 0 && d <= 6 && onGround() && DbMobs.rexStomp(this, server)) {
                xCooldown = 200 + random.nextInt(80);
                busy = 26;
            } else if (zCooldown <= 0 && d >= 7 && d <= 18 && onGround() && DbMobs.rexCharge(this, server)) {
                zCooldown = 260 + random.nextInt(100);
                busy = 45;
            }
        } else {
            if (xCooldown <= 0 && d <= 3 && DbMobs.claws(this, server)) {
                xCooldown = 110 + random.nextInt(60);
                busy = 12;
            } else if (zCooldown <= 0 && d >= 4 && d <= 10 && onGround() && DbMobs.pounce(this, server)) {
                zCooldown = 170 + random.nextInt(80);
                busy = 40;
            }
        }
    }

    // The big one bites from farther away (its head is far out in front).
    @Override
    public boolean isWithinMeleeAttackRange(LivingEntity target) {
        if (rex()) {
            return getBoundingBox().inflate(1.6, 0.5, 1.6).intersects(target.getBoundingBox());
        }
        return super.isWithinMeleeAttackRange(target);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit) {
            playSound(rex() ? SoundEvents.RAVAGER_ATTACK : SoundEvents.FOX_BITE, 1.0f, getVoicePitch());
        }
        return hit;
    }

    @Override
    public float getVoicePitch() {
        return (rex() ? 0.65f : 1.1f) + (random.nextFloat() - 0.5f) * 0.15f;
    }

    @Override
    protected float getSoundVolume() {
        return rex() ? 2.0f : 0.9f;
    }

    @Override
    public int getAmbientSoundInterval() {
        return rex() ? 240 : 160;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return rex() ? SoundEvents.RAVAGER_ROAR : SoundEvents.FOX_SCREECH;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return rex() ? SoundEvents.RAVAGER_HURT : SoundEvents.FOX_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return rex() ? SoundEvents.RAVAGER_DEATH : SoundEvents.FOX_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        if (rex()) {
            playSound(SoundEvents.RAVAGER_STEP, 0.6f, 0.7f);
        } else {
            super.playStepSound(pos, state);
        }
    }
}
