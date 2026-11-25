// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.power.powers.aizen;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

// Kyoka Suigetsu's clone: a copy of Aizen standing where he was, with his skin, his name, his gear, his health and his
// stance. It doesn't walk or fight, but it is not a statue (Stark 2026-11-25: "o clone fica lá travado... fique
// exatamente como você"): it lands if he was in the air, breathes, and turns its head to whoever is near, never to him.
// Mobs that were after him go after it. Any hit on it breaks the illusion (KyokaSuigetsu). Never saved.
public class AizenIllusion extends PathfinderMob {
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(AizenIllusion.class, EntityDataSerializers.OPTIONAL_UUID);
    // A boss's own skin (the Aizen boss: textures/entity/aizen.png), empty for a player (their skin comes from the server list).
    private static final EntityDataAccessor<String> SKIN = SynchedEntityData.defineId(AizenIllusion.class, EntityDataSerializers.STRING);

    public AizenIllusion(EntityType<? extends AizenIllusion> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 1024)
                .add(Attributes.MOVEMENT_SPEED, 0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER, Optional.empty());
        builder.define(SKIN, "");
    }

    public void copy(LivingEntity owner) {
        entityData.set(OWNER, Optional.of(owner.getUUID()));
        if (owner instanceof com.starkinhon.battlegroundsmod.boss.SoulBoss boss && boss.bossId() != null) {
            entityData.set(SKIN, boss.bossId());
        }
        setCustomName(owner.getName());
        setCustomNameVisible(false);
        moveTo(owner.getX(), owner.getY(), owner.getZ(), owner.getYRot(), owner.getXRot());
        setYHeadRot(owner.getYHeadRot());
        yHeadRotO = owner.getYHeadRot();
        yBodyRot = owner.yBodyRot;
        yBodyRotO = owner.yBodyRot;
        // crouched stays crouched; swimming, gliding or a spin is not a stance to stand frozen in
        boolean crouch = owner.isShiftKeyDown() || owner.getPose() == net.minecraft.world.entity.Pose.CROUCHING;
        setShiftKeyDown(crouch);
        setPose(crouch ? net.minecraft.world.entity.Pose.CROUCHING : net.minecraft.world.entity.Pose.STANDING);
        if (owner instanceof net.minecraft.world.entity.player.Player player) {
            setLeftHanded(player.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT);
        }
        for (var slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            setItemSlot(slot, owner.getItemBySlot(slot).copy());
            setDropChance(slot, 0);
        }
        // the same health (health bars over heads show it): a full bar over a hurt Aizen would give it away
        var max = getAttribute(Attributes.MAX_HEALTH);
        if (max != null) {
            max.setBaseValue(Math.max(1, Math.min(1024, owner.getMaxHealth())));
        }
        setHealth(Math.max(1, owner.getHealth()));
        setDeltaMovement(owner.getDeltaMovement().multiply(0, 1, 0));
        lookAt = null;
    }

    // ---------- alive, not a statue ----------
    private net.minecraft.world.entity.player.Player lookAt;
    private int lookFor;

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        // now and then it picks whoever is closest (never its owner, who is unseen) and turns its head there calmly
        if (--lookFor <= 0 || lookAt != null && (!lookAt.isAlive() || lookAt.distanceToSqr(this) > 20 * 20)) {
            lookFor = 40 + random.nextInt(60);
            java.util.UUID mine = owner().orElse(null);
            lookAt = level().getNearestPlayer(getX(), getY(), getZ(), 16,
                    e -> e instanceof net.minecraft.world.entity.player.Player p && !p.isSpectator() && !p.getUUID().equals(mine));
        }
        if (lookAt != null) {
            getLookControl().setLookAt(lookAt, 8, 30);
        }
    }

    // The boss whose skin it wears ("" for a player).
    public String bossSkin() {
        return entityData.get(SKIN);
    }

    public Optional<UUID> owner() {
        return entityData.get(OWNER);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && !isInvulnerableTo(source) && source.getEntity() != null && !owner().map(source.getEntity().getUUID()::equals).orElse(false)) {
            KyokaSuigetsu.cloneHit(this);
        }
        return super.hurt(source, amount);
    }

    @Override
    protected void actuallyHurt(DamageSource source, float amount) {
        // Only a reflection: nothing really gets hurt.
    }

    @Override
    public void tick() {
        super.tick();
        // Left behind (the server restarted mid-illusion, say): it fades on its own.
        if (!level().isClientSide && tickCount > 400) {
            discard();
        }
    }

    // landing where he jumped from is no hit (no red flash, no sound of a fall)
    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPushable() {
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
