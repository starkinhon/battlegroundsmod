package com.starkinhon.soulsjjk.combat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.starkinhon.soulsjjk.ModAttachments;
import com.starkinhon.soulsjjk.fx.ModParticles;
import com.starkinhon.soulsjjk.network.ModPayloads;
import com.starkinhon.soulsjjk.power.PowerData;
import com.starkinhon.soulsjjk.skill.ComboSpec;
import com.starkinhon.soulsjjk.skill.DashAtaqueSpec;
import com.starkinhon.soulsjjk.skill.StaminaSpec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// Stub: this part of the Souls mod is not in the JujutsuCraft addon (only Gojo and Sukuna are).
public final class SwordArts {
    private SwordArts() {
    }

    public static final String DASH_RUN = "espada.dash";

    public static boolean barrage(net.minecraft.world.entity.LivingEntity p, net.minecraft.server.level.ServerLevel l) {
        return false;
    }

    public static int color(net.minecraft.world.entity.LivingEntity p) {
        return 0;
    }

    public static boolean comboCut(net.minecraft.world.entity.LivingEntity a, net.minecraft.world.entity.LivingEntity t, int step, Object golpe, Object spec) {
        return false;
    }

    public static int comboSwing(net.minecraft.world.entity.LivingEntity p, int step) {
        return 0;
    }

    public static void dashCut(net.minecraft.world.entity.LivingEntity a, net.minecraft.world.entity.LivingEntity t, Object spec) {
    }

    public static boolean held(LivingEntity entity) {
        return false;
    }

    public static boolean strong(net.minecraft.world.entity.LivingEntity p, net.minecraft.server.level.ServerLevel l) {
        return false;
    }
}
