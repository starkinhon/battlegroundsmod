// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.world;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

// Rare weapons in chests (Stark 2026-11-20: "colocar as armas de jujutsu como loot de baús, loots raros, pode ser
// qualquer lugar; a lâmina invertida, especificamente"; then "só a lâmina invertida mesmo, a playful fica com a
// Maki"; then "bota a espada da Miwa, bota a Nichirin": the Lâmina Celestial, Miwa's sword and the Nichirin; the
// Nichirin is Demon Slayer, so no Jujutsu biome bonus). A global loot modifier, so no vanilla table is rewritten:
// when a chest table rolls (vanilla minecraft:chests/*, the mod's estruturas/*), at most ONE weapon of the list may
// join the loot, with its own small chance; inside a Jujutsu biome (tag universe_jujutsu) the bigger chance counts.
// Left out: the spawn bonus chest, the barrels' "suprimentos", the Saiyan pod (guaranteed Scouter), anything that is
// not a chest table (mobs, blocks, fishing). The list and the numbers are in loot_modifiers/armas_raras.json.
public final class RareChestWeapons extends LootModifier {
    public record Entry(Item item, float chance, float jujutsuChance) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(Entry::item),
                Codec.floatRange(0f, 1f).fieldOf("chance").forGetter(Entry::chance),
                Codec.floatRange(0f, 1f).fieldOf("chance_jujutsu").forGetter(Entry::jujutsuChance))
                .apply(i, Entry::new));
    }

    public static final MapCodec<RareChestWeapons> CODEC = RecordCodecBuilder.mapCodec(i -> codecStart(i)
            .and(Entry.CODEC.listOf().fieldOf("entries").forGetter(m -> m.entries))
            .apply(i, RareChestWeapons::new));

    private static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, BattlegroundsMod.MODID);

    static {
        SERIALIZERS.register("armas_raras", () -> CODEC);
    }

    public static void register(IEventBus bus) {
        SERIALIZERS.register(bus);
    }

    private final List<Entry> entries;

    public RareChestWeapons(LootItemCondition[] conditions, List<Entry> entries) {
        super(conditions);
        this.entries = entries;
    }

    static boolean isChestTable(ResourceLocation id) {
        if (id == null) {
            return false;
        }
        String path = id.getPath();
        if (id.getNamespace().equals("minecraft")) {
            return path.startsWith("chests/") && !path.equals("chests/spawn_bonus_chest");
        }
        if (id.getNamespace().equals(BattlegroundsMod.MODID)) {
            return path.startsWith("estruturas/") && !path.startsWith("estruturas/suprimentos");
        }
        return false;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        if (!isChestTable(context.getQueriedLootTableId())) {
            return generatedLoot;
        }
        Vec3 origin = context.getParamOrNull(LootContextParams.ORIGIN);
        boolean jujutsu = origin != null
                && context.getLevel().getBiome(BlockPos.containing(origin)).is(ModWorldgen.JUJUTSU_BIOMES);
        // one roll over the whole list: never two weapons in the same chest
        float roll = context.getRandom().nextFloat();
        float acc = 0f;
        for (Entry e : entries) {
            acc += jujutsu ? e.jujutsuChance() : e.chance();
            if (roll < acc) {
                for (ItemStack s : generatedLoot) {
                    if (s.is(e.item())) {
                        return generatedLoot;
                    }
                }
                generatedLoot.add(new ItemStack(e.item()));
                return generatedLoot;
            }
        }
        return generatedLoot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
