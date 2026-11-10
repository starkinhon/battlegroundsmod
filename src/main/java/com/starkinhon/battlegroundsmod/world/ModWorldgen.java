package com.starkinhon.battlegroundsmod.world;

import java.util.function.Supplier;

import com.mojang.serialization.MapCodec;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

// The world generator for drawn universes ("type": "battlegroundsmod:map" in a dimension's json).
public final class ModWorldgen {
    public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, BattlegroundsMod.MODID);
    public static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES =
            DeferredRegister.create(Registries.BIOME_SOURCE, BattlegroundsMod.MODID);

    public static final Supplier<MapCodec<MapChunkGenerator>> MAP = CHUNK_GENERATORS.register("map", () -> MapChunkGenerator.CODEC);
    public static final Supplier<MapCodec<MapBiomeSource>> MAP_BIOMES = BIOME_SOURCES.register("map", () -> MapBiomeSource.CODEC);

    private ModWorldgen() {
    }

    public static void register(IEventBus bus) {
        CHUNK_GENERATORS.register(bus);
        BIOME_SOURCES.register(bus);
    }
}
