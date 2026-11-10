package com.starkinhon.soulsjjk.power.powers.sub.db;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import com.starkinhon.soulsjjk.ModAttachments;
import com.starkinhon.soulsjjk.ModEffects;
import com.starkinhon.soulsjjk.combat.BlockRules;
import com.starkinhon.soulsjjk.combat.Combat;
import com.starkinhon.soulsjjk.combat.Tasks;
import com.starkinhon.soulsjjk.power.PowerData;
import com.starkinhon.soulsjjk.power.powers.sub.SubCommon;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

// Stub: this part of the Souls mod is not in the JujutsuCraft addon (only Gojo and Sukuna are).
public final class DbCommon {
    private DbCommon() {
    }

    public static void hit(ServerLevel level, Vec3 at, float size, int color) {
        
    }

    public static float meleeMultiplier(LivingEntity attacker) {
        return 0;
    }

    public static float punchSpeed(LivingEntity attacker) {
        return 0;
    }
}
