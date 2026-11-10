package com.starkinhon.soulsjjk.boss;

import com.starkinhon.soulsjjk.ModAttachments;
import com.starkinhon.soulsjjk.combat.Combat;
import com.starkinhon.soulsjjk.combat.Freeze;
import com.starkinhon.soulsjjk.combat.Intangible;
import com.starkinhon.soulsjjk.combat.Stun;
import com.starkinhon.soulsjjk.combat.Tasks;
import com.starkinhon.soulsjjk.movement.Movement;
import com.starkinhon.soulsjjk.network.ModPayloads;
import com.starkinhon.soulsjjk.power.PowerData;
import com.starkinhon.soulsjjk.power.PowerLogic;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

// Stub: this part of the Souls mod is not in the JujutsuCraft addon (only Gojo and Sukuna are).
public abstract class SoulBoss extends net.minecraft.world.entity.PathfinderMob {
    protected SoulBoss(net.minecraft.world.entity.EntityType<? extends SoulBoss> t, net.minecraft.world.level.Level l) {
        super(t, l);
    }


    public boolean isBoss() {
        return false;
    }

    public String bossId() {
        return "";
    }

    public com.starkinhon.soulsjjk.power.PowerData powerData() {
        return new com.starkinhon.soulsjjk.power.PowerData();
    }
}
