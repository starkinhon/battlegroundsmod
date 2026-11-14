package com.starkinhon.battlegroundsmod.animal.jjmob;

import javax.annotation.Nullable;

import com.starkinhon.battlegroundsmod.ModItems;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

// What each Jujutsu curse leaves when exorcised (the preview's "Solta"), with the items the mod already has (the
// Boneco de Palha, the Boneco Amaldiçoado and, from the strongest, a Dedo do Sukuna) and the closest vanilla material for
// the rest (wheat for straw, paper for the Kitsune's talismans, basalt for the Golem...), and their voices.
final class JjDrops {
    private JjDrops() {
    }

    static void drop(JjMob m, ServerLevel level, boolean byPlayer) {
        RandomSource r = level.getRandom();
        switch (m.kind()) {
            case "mosca" -> some(m, Items.STRING, 0, 1, 0.3, r);
            case "cabecao" -> {
                some(m, Items.ROTTEN_FLESH, 1, 3, 1, r);
                some(m, Items.BONE, 0, 2, 0.5, r);
                rare(m, ModItems.STRAW_DOLL.get(), 0.04, byPlayer, r);
            }
            case "transfigurado" -> {
                some(m, Items.ROTTEN_FLESH, 1, 2, 0.8, r);
                rare(m, ModItems.STRAW_DOLL.get(), 0.03, byPlayer, r);
            }
            case "metro" -> {
                some(m, Items.IRON_INGOT, 2, 5, 1, r);
                some(m, Items.RAIL, 2, 6, 0.6, r);
                rare(m, ModItems.CURSED_DOLL.get(), 0.2, byPlayer, r);
            }
            case "rastejante" -> {
                some(m, Items.BONE, 3, 6, 1, r);
                some(m, ModItems.CURSED_DOLL.get(), 1, 1, 0.5, r);
                rare(m, ModItems.SUKUNA_FINGER.get(), 0.2, byPlayer, r);
            }
            case "verme" -> some(m, Items.SLIME_BALL, 0, 2, 0.7, r);
            case "olhos" -> {
                some(m, Items.SPIDER_EYE, 1, 3, 1, r);
                some(m, Items.STICK, 1, 3, 0.6, r);
            }
            case "utero" -> {
                some(m, Items.ROTTEN_FLESH, 2, 4, 1, r);
                rare(m, ModItems.CURSED_DOLL.get(), 0.2, byPlayer, r);
            }
            case "boneco" -> {
                some(m, Items.WHEAT, 1, 3, 1, r);
                rare(m, ModItems.STRAW_DOLL.get(), 0.15, byPlayer, r);
            }
            case "kitsune" -> {
                some(m, Items.PAPER, 1, 3, 0.8, r);
                rare(m, ModItems.STRAW_DOLL.get(), 0.05, byPlayer, r);
            }
            case "sino" -> {
                some(m, Items.GOLD_NUGGET, 2, 5, 1, r);
                rare(m, Items.BELL, 0.05, byPlayer, r);
            }
            case "espantalho" -> {
                some(m, Items.WHEAT, 1, 3, 1, r);
                some(m, Items.FEATHER, 1, 2, 0.6, r);
                rare(m, ModItems.STRAW_DOLL.get(), 0.1, byPlayer, r);
            }
            case "poco" -> {
                some(m, Items.STRING, 1, 2, 0.8, r);
                some(m, Items.BONE, 0, 1, 0.4, r);
                rare(m, ModItems.CURSED_DOLL.get(), 0.06, byPlayer, r);
            }
            case "kuchisake" -> {
                some(m, Items.IRON_NUGGET, 1, 3, 0.8, r);
                rare(m, Items.SHEARS, 0.06, byPlayer, r);
                rare(m, ModItems.CURSED_DOLL.get(), 0.08, byPlayer, r);
            }
            case "peixe" -> some(m, Items.COD, 0, 1, 0.6, r);
            case "caranguejo" -> {
                some(m, Items.BONE_MEAL, 1, 2, 0.8, r);
                rare(m, Items.NAUTILUS_SHELL, 0.03, byPlayer, r);
            }
            case "embriao" -> {
                some(m, Items.PRISMARINE_SHARD, 1, 2, 0.8, r);
                some(m, Items.COD, 0, 1, 0.5, r);
            }
            case "afogado" -> {
                some(m, Items.KELP, 1, 3, 1, r);
                some(m, Items.PRISMARINE_CRYSTALS, 0, 1, 0.4, r);
                rare(m, ModItems.CURSED_DOLL.get(), 0.06, byPlayer, r);
            }
            case "broto" -> {
                some(m, Items.WHEAT_SEEDS, 1, 3, 1, r);
                some(m, Items.CHERRY_SAPLING, 1, 1, 0.1, r);
            }
            case "javali" -> {
                some(m, Items.PORKCHOP, 1, 3, 1, r);
                some(m, Items.LEATHER, 0, 2, 0.7, r);
            }
            case "guardiao" -> {
                some(m, Items.CHERRY_LOG, 1, 3, 1, r);
                rare(m, ModItems.CURSED_DOLL.get(), 0.15, byPlayer, r);
            }
            case "inseto" -> some(m, Items.BLAZE_POWDER, 0, 1, 0.4, r);
            case "vulcaozinho" -> {
                some(m, Items.MAGMA_CREAM, 0, 2, 0.8, r);
                some(m, Items.BASALT, 1, 2, 0.6, r);
            }
            case "golem" -> {
                some(m, Items.BASALT, 2, 4, 1, r);
                some(m, Items.OBSIDIAN, 0, 1, 0.4, r);
                rare(m, ModItems.CURSED_DOLL.get(), 0.15, byPlayer, r);
            }
            case "lanterna" -> {
                some(m, Items.PAPER, 1, 2, 0.8, r);
                rare(m, Items.SOUL_LANTERN, 0.08, byPlayer, r);
            }
            case "mechamaru" -> {
                some(m, Items.IRON_NUGGET, 2, 4, 1, r);
                some(m, Items.REDSTONE, 1, 2, 0.6, r);
            }
            case "oni" -> {
                some(m, Items.BONE, 1, 3, 1, r);
                some(m, Items.LEATHER, 1, 2, 0.7, r);
                rare(m, ModItems.CURSED_DOLL.get(), 0.1, byPlayer, r);
            }
            case "larva" -> {
                some(m, Items.STRING, 1, 3, 1, r);
                some(m, Items.SLIME_BALL, 0, 1, 0.4, r);
            }
            case "abate" -> {
                some(m, Items.IRON_NUGGET, 1, 3, 0.8, r);
                some(m, Items.GOLD_NUGGET, 0, 2, 0.5, r);
                rare(m, ModItems.STRAW_DOLL.get(), 0.05, byPlayer, r);
            }
            case "solta" -> {
                some(m, Items.ROTTEN_FLESH, 2, 4, 1, r);
                some(m, ModItems.CURSED_DOLL.get(), 1, 1, 0.25, r);
                rare(m, ModItems.SUKUNA_FINGER.get(), 0.04, byPlayer, r);
            }
            default -> {
            }
        }
    }

    private static void some(JjMob m, ItemLike item, int min, int max, double chance, RandomSource r) {
        if (r.nextDouble() >= chance) {
            return;
        }
        int n = min + r.nextInt(max - min + 1);
        if (n > 0) {
            m.spawnAtLocation(new ItemStack(item, n));
        }
    }

    private static void rare(JjMob m, Item item, double chance, boolean byPlayer, RandomSource r) {
        if (byPlayer && r.nextDouble() < chance) {
            m.spawnAtLocation(new ItemStack(item));
        }
    }

    // ---------- voices ----------

    @Nullable
    static SoundEvent ambient(JjMob m) {
        return switch (m.kind()) {
            case "mosca", "inseto" -> SoundEvents.BEE_LOOP;
            case "cabecao", "transfigurado", "utero", "solta" -> SoundEvents.ZOMBIE_AMBIENT;
            case "rastejante" -> SoundEvents.WARDEN_AMBIENT;
            case "verme", "larva" -> SoundEvents.SILVERFISH_AMBIENT;
            case "kitsune" -> SoundEvents.FOX_AMBIENT;
            case "espantalho" -> SoundEvents.PARROT_AMBIENT;
            case "poco", "kuchisake" -> SoundEvents.WITCH_AMBIENT;
            case "peixe" -> SoundEvents.COD_FLOP;
            case "afogado", "embriao" -> SoundEvents.DROWNED_AMBIENT;
            case "javali" -> SoundEvents.HOGLIN_AMBIENT;
            case "vulcaozinho" -> SoundEvents.LAVA_AMBIENT;
            case "golem", "oni" -> SoundEvents.RAVAGER_AMBIENT;
            case "lanterna" -> SoundEvents.BLAZE_AMBIENT;
            default -> null;
        };
    }

    static SoundEvent hurtSound(JjMob m) {
        return switch (m.kind()) {
            case "mosca", "inseto" -> SoundEvents.BEE_HURT;
            case "verme", "larva" -> SoundEvents.SILVERFISH_HURT;
            case "kitsune" -> SoundEvents.FOX_HURT;
            case "boneco", "espantalho" -> SoundEvents.WOOL_HIT;
            case "sino" -> SoundEvents.BELL_BLOCK;
            case "poco", "kuchisake" -> SoundEvents.WITCH_HURT;
            case "peixe" -> SoundEvents.COD_HURT;
            case "caranguejo" -> SoundEvents.ARMADILLO_HURT;
            case "afogado", "embriao" -> SoundEvents.DROWNED_HURT;
            case "broto", "guardiao" -> SoundEvents.WOOD_HIT;
            case "javali" -> SoundEvents.HOGLIN_HURT;
            case "vulcaozinho", "golem" -> SoundEvents.STONE_HIT;
            case "lanterna" -> SoundEvents.BOOK_PUT;
            case "mechamaru" -> SoundEvents.IRON_GOLEM_HURT;
            case "abate" -> SoundEvents.PLAYER_HURT;
            default -> SoundEvents.ZOMBIE_HURT;
        };
    }

    @Nullable
    static SoundEvent deathSound(JjMob m) {
        // a curse goes quiet into ash (its own sound plays as it crumbles); the player only faints
        return m.kind().equals("abate") ? SoundEvents.PLAYER_HURT : null;
    }

    static float pitch(JjMob m) {
        return switch (m.kind()) {
            case "rastejante", "metro", "golem", "oni", "utero" -> 0.5f;
            case "cabecao", "guardiao", "afogado", "solta" -> 0.7f;
            case "mosca", "inseto", "peixe", "kitsune", "broto" -> 1.3f;
            default -> 1f;
        };
    }
}
