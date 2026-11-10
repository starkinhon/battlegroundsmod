package com.starkinhon.soulsjjk.challenge;

import java.util.HashMap;
import java.util.Map;
import com.mojang.datafixers.util.Pair;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.starkinhon.soulsjjk.SoulsJjk;
import com.starkinhon.soulsjjk.ModAttachments;
import com.starkinhon.soulsjjk.combat.Fx;
import com.starkinhon.soulsjjk.network.ModPayloads;
import com.starkinhon.soulsjjk.power.PowerData;
import com.starkinhon.soulsjjk.power.PowerLogic;
import com.starkinhon.soulsjjk.power.Progression;
import com.starkinhon.soulsjjk.power.Tiers;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

// Stub: this part of the Souls mod is not in the JujutsuCraft addon (only Gojo and Sukuna are).
public final class Universes {
    private Universes() {
    }

    public static void pick(ServerPlayer player, String universe) {
        
    }
}
