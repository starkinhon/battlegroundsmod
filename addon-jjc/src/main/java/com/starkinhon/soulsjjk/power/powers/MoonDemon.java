package com.starkinhon.soulsjjk.power.powers;

import java.util.Arrays;
import java.util.List;
import com.starkinhon.soulsjjk.SoulsJjk;
import com.starkinhon.soulsjjk.ModAttachments;
import com.starkinhon.soulsjjk.power.Ability;
import com.starkinhon.soulsjjk.power.Power;
import com.starkinhon.soulsjjk.power.PowerData;
import com.starkinhon.soulsjjk.power.PowerLogic;
import com.starkinhon.soulsjjk.power.powers.sub.SubCommon;
import com.starkinhon.soulsjjk.network.ModPayloads;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

// Stub: this part of the Souls mod is not in the JujutsuCraft addon (only Gojo and Sukuna are).
public final class MoonDemon {
    private MoonDemon() {
    }

    public static void lockOthers(LivingEntity caster, Power power, int used) {
        
    }

    public static void stretch(ServerPlayer player) {
        
    }

    public static boolean stretched(LivingEntity who) {
        return false;
    }

    public static Ability swap(LivingEntity caster, Power power, int slot, Ability normal) {
        return normal;
    }
}
