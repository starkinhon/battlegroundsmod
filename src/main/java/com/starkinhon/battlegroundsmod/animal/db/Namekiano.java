package com.starkinhon.battlegroundsmod.animal.db;

import java.util.List;

import com.starkinhon.battlegroundsmod.ModEntities;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.Db;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.DbCommon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;

// The Namekians of Namek (Stark 2026-11-11: "MUITO AURA"): neutral, until someone hits one; then every Namekian within
// 16 blocks turns on them. Three kinds, from the variant (40 built in tools/mobs/blocky from seeds, each its own height,
// width, head, green, clothes, antennae, scar and moustache): the warrior (80 health) fights first, the villager (40)
// runs and never fights, the elder (60) is slow and heals the Namekians around him. The warriors and elders punch with
// the arm that stretches up to 5 blocks. They hate Freeza's soldiers and fight them on sight.
public class Namekiano extends DbCreature {
    // per variant, from the export (blocky/namekiano_index.json): g warrior, a villager, n elder
    private static final String TYPES = "gggaaannaggnaaggnaaggnaaggnaaggnaaggnaag";
    // m thin, n normal, g fat
    private static final String WIDTHS = "mgngmngmgmggmgmnggmnggmmggggngnmgmgnmggm";
    // the body's height in blocks (the model's without the antennae)
    private static final float[] HEIGHTS = {2.54f, 1.98f, 2.24f, 2.53f, 1.93f, 2.24f, 1.99f, 2.52f, 2.2f, 2.25f, 2.23f, 2.23f,
            2.54f, 1.94f, 2.2f, 2.24f, 1.92f, 2.22f, 2.56f, 2.52f, 2.54f, 2.2f, 2.25f, 2.2f, 1.96f, 2.24f, 2.19f, 2.2f, 2.0f, 2.25f,
            1.96f, 2.24f, 2.2f, 2.53f, 2.22f, 2.23f, 2.23f, 1.97f, 2.24f, 2.23f};
    public static final int VARIANTS = TYPES.length();
    private static final double ALERT = 16;
    private static final double REACH = 5;

    private int healCooldown = 40;

    public Namekiano(EntityType<? extends Namekiano> type, Level level) {
        super(type, level);
        this.xpReward = 8;
    }

    public static AttributeSupplier.Builder attributes() {
        return createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 80)
                .add(Attributes.ATTACK_DAMAGE, 4)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 24)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    private char kind() {
        int v = variant();
        return v >= 0 && v < VARIANTS ? TYPES.charAt(v) : 'g';
    }

    public boolean elder() {
        return kind() == 'n';
    }

    public boolean villager() {
        return kind() == 'a';
    }

    private static boolean freezaSoldier(LivingEntity e) {
        return e.getType() == ModEntities.SOLDADO_FREEZA.get();
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        // the villagers run: from whoever hurt them and from Freeza's soldiers (the fighters ignore these: never a target)
        goalSelector.addGoal(1, new PanicGoal(this, 1.5) {
            @Override
            public boolean canUse() {
                return villager() && super.canUse();
            }
        });
        goalSelector.addGoal(2, new AvoidEntityGoal<>(this, LivingEntity.class, 10f, 1.1, 1.5,
                e -> villager() && (freezaSoldier(e) || e == getLastHurtByMob())));
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.15, false) {
            @Override
            public boolean canUse() {
                return !villager() && super.canUse();
            }
        });
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, Namekiano.class) {
            @Override
            public boolean canUse() {
                return !villager() && super.canUse();
            }
        });
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false,
                e -> !villager() && freezaSoldier(e)));
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType,
            SpawnGroupData data) {
        setVariant(random.nextInt(VARIANTS));
        setHealth(getMaxHealth());
        return super.finalizeSpawn(level, difficulty, spawnType, data);
    }

    @Override
    protected void variantChanged() {
        if (level().isClientSide) {
            return;
        }
        float max = villager() ? 40 : elder() ? 60 : 80;
        var health = getAttribute(Attributes.MAX_HEALTH);
        if (health != null && health.getBaseValue() != max) {
            health.setBaseValue(max);
            setHealth(Math.min(getHealth(), max));
        }
        var speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(elder() ? 0.24 : villager() ? 0.3 : 0.32);
        }
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        int v = variant();
        float h = v >= 0 && v < VARIANTS ? HEIGHTS[v] : 2.2f;
        char w = v >= 0 && v < VARIANTS ? WIDTHS.charAt(v) : 'n';
        float width = w == 'g' ? 1.05f : w == 'm' ? 0.7f : 0.85f;
        return EntityDimensions.scalable(width, h).withEyeHeight(h * 0.88f);
    }

    // Hit one and the rest of the village turns on you.
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide && source.getEntity() instanceof LivingEntity attacker && !(attacker instanceof Namekiano)
                && !(attacker instanceof Player p && (p.isCreative() || p.isSpectator()))) {
            for (Namekiano other : level().getEntitiesOfClass(Namekiano.class, getBoundingBox().inflate(ALERT),
                    n -> n != this && n.isAlive() && !n.villager())) {
                if (other.getTarget() == null) {
                    other.setTarget(attacker);
                }
            }
        }
        return hurt;
    }

    @Override
    public boolean isWithinMeleeAttackRange(LivingEntity target) {
        return inReach(target, REACH - 0.5);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        // the stretch punch: wind-up, the arm shoots out on tick 11, held, back (DbAnims.namek, 1.6 s)
        return startAttack(0, 32, 11);
    }

    @Override
    protected void impact(int kind) {
        LivingEntity target = getTarget();
        ServerLevel server = (ServerLevel) level();
        if (!inReach(target, REACH)) {
            SubCommon.sound(server, position(), SoundEvents.PLAYER_ATTACK_NODAMAGE, 1f, 0.8f);
            return;
        }
        Vec3 at = Combat.center(target);
        SubCommon.sound(server, at, SoundEvents.PLAYER_ATTACK_STRONG, 1.2f, 0.8f);
        DbCommon.hit(server, at, 0.45f, Db.WHITE);
        Combat.hurt(this, target, elder() ? 14 : 18);
        Combat.pushFrom(target, position(), 0.7, 0.25);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!elder() || --healCooldown > 0) {
            return;
        }
        healCooldown = 60;
        // the elder heals the hurt Namekians around him (himself too, a little)
        ServerLevel server = (ServerLevel) level();
        List<Namekiano> hurt = server.getEntitiesOfClass(Namekiano.class, getBoundingBox().inflate(8),
                n -> n.isAlive() && n.getHealth() < n.getMaxHealth());
        for (Namekiano n : hurt) {
            n.heal(n == this ? 3 : 6);
            DbCommon.hit(server, Combat.center(n), 0.35f, Db.GREEN);
        }
        if (!hurt.isEmpty()) {
            SubCommon.sound(server, position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8f, 1.4f);
            swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        }
    }

    // They're people, not monsters: no despawning while someone's around, and Namek's village keeps its own.
    @Override
    public boolean removeWhenFarAway(double distance) {
        return distance > 128 * 128;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getDeathSound() {
        return SoundEvents.PLAYER_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return (elder() ? 0.75f : villager() ? 1.1f : 0.9f) + (random.nextFloat() - 0.5f) * 0.1f;
    }
}
