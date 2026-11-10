package com.starkinhon.soulsjjk.power.powers.aizen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import com.starkinhon.soulsjjk.ModAttachments;
import com.starkinhon.soulsjjk.combat.Combat;
import com.starkinhon.soulsjjk.combat.Freeze;
import com.starkinhon.soulsjjk.combat.Fx;
import com.starkinhon.soulsjjk.combat.Intangible;
import com.starkinhon.soulsjjk.combat.SkillSounds;
import com.starkinhon.soulsjjk.combat.Stun;
import com.starkinhon.soulsjjk.combat.Tasks;
import com.starkinhon.soulsjjk.network.ModPayloads;
import com.starkinhon.soulsjjk.power.PowerData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// Stub: this part of the Souls mod is not in the JujutsuCraft addon (only Gojo and Sukuna are).
public final class Hogyoku {
    private Hogyoku() {
    }

    public static void clear() {
        
    }

    public static boolean dash(LivingEntity caster) {
        return false;
    }
}
