package com.starkinhon.battlegroundsmod.animal.naruto;

import com.starkinhon.battlegroundsmod.ModItems;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

// What each Naruto creature drops (the preview's "Solta"), with the items the mod already has: the ninja's ramen,
// soldier pills and headbands, the Ninken summoning scroll, and the closest vanilla material for the rest (leather for
// the tiger's striped hide, paper for the paper crane's chakra paper, a prismarine shard for the shark's tooth...).
// The structures' own items (NarutoLootItems) stay in their chests.
final class NarutoDrops {
    private NarutoDrops() {
    }

    static void drop(NarutoMob m, ServerLevel level, boolean byPlayer) {
        RandomSource r = level.getRandom();
        switch (m.id()) {
            case "ninja_renegado" -> {
                some(m, Items.IRON_NUGGET, 1, 3, 0.6, r);
                some(m, ModItems.RAMEN.get(), 1, 1, 0.12, r);
                rare(m, ModItems.NINJA_HEADBAND.get(), 0.03, byPlayer, r);
            }
            case "ninken" -> {
                some(m, Items.BONE, 0, 2, 1, r);
                rare(m, ModItems.SUMMON_SCROLL.get(), 0.02, byPlayer, r);
            }
            case "cobra_gigante" -> {
                some(m, Items.LEATHER, 3, 6, 1, r);
                some(m, Items.TURTLE_SCUTE, 1, 2, 0.35, r);
                some(m, Items.SPIDER_EYE, 1, 2, 0.5, r);
            }
            case "tigre" -> {
                some(m, Items.LEATHER, 1, 3, 1, r);
                some(m, Items.BEEF, 1, 3, 1, r);
            }
            case "ninja_som" -> {
                some(m, Items.IRON_NUGGET, 2, 4, 0.7, r);
                some(m, ModItems.SOLDIER_PILL.get(), 1, 1, 0.08, r);
                rare(m, Items.PAPER, 0.15, byPlayer, r);
            }
            case "centopeia" -> {
                some(m, Items.SPIDER_EYE, 1, 2, 0.6, r);
                some(m, Items.STRING, 0, 2, 0.5, r);
            }
            case "ninja_leque" -> {
                some(m, Items.STICK, 1, 3, 0.7, r);
                some(m, Items.WHITE_WOOL, 1, 1, 0.3, r);
                rare(m, ModItems.NINJA_HEADBAND.get(), 0.03, byPlayer, r);
            }
            case "escorpiao_areia" -> {
                some(m, Items.SPIDER_EYE, 1, 1, 0.5, r);
                some(m, Items.SAND, 1, 3, 0.6, r);
            }
            case "marionete" -> {
                some(m, Items.STICK, 2, 5, 1, r);
                some(m, Items.IRON_NUGGET, 1, 3, 0.5, r);
            }
            case "marionetista" -> {
                some(m, Items.STRING, 2, 5, 1, r);
                rare(m, ModItems.NINJA_HEADBAND.get(), 0.05, byPlayer, r);
            }
            case "espadachim_nevoa" -> {
                some(m, Items.COD, 1, 2, 0.6, r);
                some(m, Items.IRON_INGOT, 1, 1, 0.15, r);
                rare(m, ModItems.NINJA_HEADBAND.get(), 0.05, byPlayer, r);
            }
            case "tubarao_agua" -> {
                some(m, Items.COD, 1, 3, 1, r);
                rare(m, Items.PRISMARINE_SHARD, 0.2, byPlayer, r);
            }
            case "caranguejo_gigante" -> {
                some(m, Items.COD, 1, 2, 1, r);
                some(m, Items.ARMADILLO_SCUTE, 0, 1, 0.4, r);
            }
            case "cacador_nevoa" -> {
                some(m, Items.IRON_NUGGET, 2, 4, 0.8, r);
                rare(m, ModItems.NINJA_HEADBAND.get(), 0.04, byPlayer, r);
            }
            case "ninja_chuva" -> {
                some(m, Items.IRON_NUGGET, 2, 4, 0.8, r);
                some(m, Items.CHAIN, 1, 1, 0.1, r);
                rare(m, ModItems.NINJA_HEADBAND.get(), 0.03, byPlayer, r);
            }
            case "garca_papel" -> some(m, Items.PAPER, 1, 3, 1, r);
            case "salamandra_venenosa" -> {
                some(m, Items.SPIDER_EYE, 2, 4, 1, r);
                some(m, Items.LEATHER, 2, 4, 1, r);
                some(m, Items.SLIME_BALL, 1, 3, 0.6, r);
            }
            case "zetsu_branco" -> {
                some(m, Items.VINE, 1, 2, 0.6, r);
                some(m, Items.SPORE_BLOSSOM, 1, 1, 0.04, r);
            }
            case "corvo_uchiha" -> some(m, Items.FEATHER, 1, 2, 1, r);
            case "clone_madeira" -> {
                some(m, Items.OAK_LOG, 1, 3, 1, r);
                some(m, Items.OAK_SAPLING, 1, 1, 0.3, r);
            }
            case "sapo_ninja" -> {
                some(m, Items.SLIME_BALL, 0, 2, 0.6, r);
                some(m, Items.LEATHER, 0, 1, 0.4, r);
            }
            case "sapo_gigante" -> {
                some(m, Items.SLIME_BALL, 4, 8, 1, r);
                some(m, Items.LEATHER, 2, 4, 1, r);
            }
            case "inseto_gigante" -> {
                some(m, Items.ARMADILLO_SCUTE, 0, 1, 0.5, r);
                some(m, Items.STRING, 0, 1, 0.3, r);
            }
            default -> {
            }
        }
    }

    private static void some(NarutoMob m, ItemLike item, int min, int max, double chance, RandomSource r) {
        if (r.nextDouble() >= chance) {
            return;
        }
        int n = min + r.nextInt(max - min + 1);
        if (n > 0) {
            m.spawnAtLocation(new ItemStack(item, n));
        }
    }

    private static void rare(NarutoMob m, Item item, double chance, boolean byPlayer, RandomSource r) {
        if (byPlayer && r.nextDouble() < chance) {
            m.spawnAtLocation(new ItemStack(item));
        }
    }
}
