package com.starkinhon.soulsjjk.boss;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import com.starkinhon.soulsjjk.SoulsJjk;
import com.starkinhon.soulsjjk.ModAttachments;
import com.starkinhon.soulsjjk.combat.Combat;
import com.starkinhon.soulsjjk.combat.Destructible;
import com.starkinhon.soulsjjk.combat.Freeze;
import com.starkinhon.soulsjjk.combat.Intangible;
import com.starkinhon.soulsjjk.combat.Melee;
import com.starkinhon.soulsjjk.combat.Stamina;
import com.starkinhon.soulsjjk.movement.Movement;
import com.starkinhon.soulsjjk.power.BossOrder;
import com.starkinhon.soulsjjk.power.PowerData;
import com.starkinhon.soulsjjk.power.PowerLogic;
import com.starkinhon.soulsjjk.power.powers.DomainSpace;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

// Stub: this part of the Souls mod is not in the JujutsuCraft addon (only Gojo and Sukuna are).
public final class SoulBossAI {
    private SoulBossAI() {
    }

    // MISSING approach
}
