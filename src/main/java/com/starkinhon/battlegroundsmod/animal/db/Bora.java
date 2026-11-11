package com.starkinhon.battlegroundsmod.animal.db;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.starkinhon.battlegroundsmod.ModEntities;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.Db;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.DbCommon;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
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
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// Bora, the guardian of Karin's holy land (Stark 2026-11-11, "negão pica"): neutral. Whoever walks up to him with a
// weapon in hand gets one warning; still armed and close 5 s later, he attacks. He hates the Red Ribbon and fights its
// soldiers on sight. His blow is the spear thrust: pulls it back, then all at once, 4 blocks of reach.
public class Bora extends DbCreature {
    private static final double REACH = 4;
    private static final double WARN_RADIUS = 9;
    private static final int WARN_TICKS = 100;

    // who was warned, and when (game time)
    private final Map<UUID, Long> warned = new HashMap<>();

    public Bora(EntityType<? extends Bora> type, Level level) {
        super(type, level);
        this.xpReward = 25;
    }

    public static AttributeSupplier.Builder attributes() {
        return createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 180)
                .add(Attributes.ATTACK_DAMAGE, 6)
                .add(Attributes.MOVEMENT_SPEED, 0.31)
                .add(Attributes.FOLLOW_RANGE, 28)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    private static boolean redRibbon(LivingEntity e) {
        return e.getType() == ModEntities.SOLDADO_RED_RIBBON.get();
    }

    static boolean armed(Player p) {
        ItemStack s = p.getMainHandItem();
        return s.getItem() instanceof SwordItem || s.getItem() instanceof AxeItem || s.getItem() instanceof TridentItem
                || s.getItem() instanceof MaceItem || s.getItem() instanceof BowItem || s.getItem() instanceof CrossbowItem;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.25, false));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false, Bora::redRibbon));
    }

    @Override
    public boolean isWithinMeleeAttackRange(LivingEntity target) {
        return inReach(target, REACH - 0.5);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        return startAttack(0, 30, 13);
    }

    @Override
    protected void impact(int kind) {
        ServerLevel server = (ServerLevel) level();
        LivingEntity target = getTarget();
        float yaw = getYRot() * net.minecraft.util.Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-net.minecraft.util.Mth.sin(yaw), 0, net.minecraft.util.Mth.cos(yaw));
        SubCommon.sound(server, position(), SoundEvents.TRIDENT_THROW.value(), 1.3f, 0.7f);
        if (!inReach(target, REACH)) {
            return;
        }
        Vec3 at = Combat.center(target);
        DbCommon.hit(server, at, 0.5f, Db.WHITE);
        SubCommon.sound(server, at, SoundEvents.TRIDENT_HIT, 1.3f, 0.8f);
        Combat.hurt(this, target, 35);
        Stun.apply(target, 10);
        Combat.push(target, fwd, 0.9, 0.2);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (tickCount % 10 != 0 || getTarget() != null) {
            return;
        }
        long now = level().getGameTime();
        for (Player p : level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(WARN_RADIUS))) {
            if (!(p instanceof ServerPlayer sp) || sp.isCreative() || sp.isSpectator()) {
                continue;
            }
            Long when = warned.get(p.getUUID());
            if (!armed(p)) {
                // put it away and the warning's forgotten after a while
                if (when != null && now - when > 600) {
                    warned.remove(p.getUUID());
                }
                continue;
            }
            if (when == null) {
                warned.put(p.getUUID(), now);
                getLookControl().setLookAt(p);
                sp.displayClientMessage(Component.translatable("message.battlegroundsmod.bora_aviso"), true);
                SubCommon.sound((ServerLevel) level(), position(), SoundEvents.VINDICATOR_AMBIENT, 1.2f, 0.6f);
            } else if (now - when >= WARN_TICKS && distanceTo(p) < WARN_RADIUS - 2) {
                setTarget(p);
                sp.displayClientMessage(Component.translatable("message.battlegroundsmod.bora_ataque"), true);
                SubCommon.sound((ServerLevel) level(), position(), SoundEvents.VINDICATOR_CELEBRATE, 1.4f, 0.6f);
                return;
            }
        }
    }

    // The holy land's guardian stays where he guards.
    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source) {
        return SoundEvents.VINDICATOR_HURT;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getDeathSound() {
        return SoundEvents.VINDICATOR_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.65f;
    }
}
