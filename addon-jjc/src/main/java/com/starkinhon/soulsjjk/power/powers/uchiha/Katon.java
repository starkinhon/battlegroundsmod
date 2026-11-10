package com.starkinhon.soulsjjk.power.powers.uchiha;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import com.starkinhon.soulsjjk.ModAttachments;
import com.starkinhon.soulsjjk.ModEffects;
import com.starkinhon.soulsjjk.combat.AshKill;
import com.starkinhon.soulsjjk.combat.Combat;
import com.starkinhon.soulsjjk.combat.Cone;
import com.starkinhon.soulsjjk.combat.Fx;
import com.starkinhon.soulsjjk.combat.SkillSounds;
import com.starkinhon.soulsjjk.combat.Tasks;
import com.starkinhon.soulsjjk.network.ModPayloads;
import com.starkinhon.soulsjjk.power.PowerData;
import com.starkinhon.soulsjjk.power.PowerLogic;
import com.starkinhon.soulsjjk.skill.SkillSpec;
import com.starkinhon.soulsjjk.skill.SkillSpecs;
import com.starkinhon.soulsjjk.skill.Som;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// Stub: this part of the Souls mod is not in the JujutsuCraft addon (only Gojo and Sukuna are).
public final class Katon {
    private Katon() {
    }

    public static boolean cancel(LivingEntity player) {
        return false;
    }
}
