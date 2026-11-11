package com.starkinhon.battlegroundsmod;

import java.util.LinkedHashMap;
import java.util.Map;

import com.starkinhon.battlegroundsmod.item.AizenSwordItem;
import com.starkinhon.battlegroundsmod.item.KokushiboSwordItem;
import com.starkinhon.battlegroundsmod.item.FightRodItem;
import com.starkinhon.battlegroundsmod.item.PowerBookItem;
import com.starkinhon.battlegroundsmod.item.SoulInsectItem;
import com.starkinhon.battlegroundsmod.item.SoulItem;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.Powers;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.Unbreakable;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;

public final class ModItems {
    public static final DeferredItem<PowerBookItem> POWER_BOOK = BattlegroundsMod.ITEMS.registerItem("power_book",
            PowerBookItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));

    // Pick two mobs and make them fight (fight/FightRod).
    public static final DeferredItem<FightRodItem> FIGHT_ROD = BattlegroundsMod.ITEMS.registerItem("fight_rod",
            FightRodItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));

    // Aizen's sword: the model from the Armario Souls viewer, as a normal (unbreakable) sword.
    public static final DeferredItem<AizenSwordItem> AIZEN_SWORD = BattlegroundsMod.ITEMS.registerItem("aizen_sword",
            AizenSwordItem::new, new Item.Properties().rarity(Rarity.EPIC).fireResistant().component(DataComponents.UNBREAKABLE, new Unbreakable(true)));

    // Kokushibo's sword (Kyokokukamusari): the 3D model Stark sent, with its sheath on the hip.
    public static final DeferredItem<KokushiboSwordItem> KOKUSHIBO_SWORD = BattlegroundsMod.ITEMS.registerItem("kokushibo_sword",
            KokushiboSwordItem::new, new Item.Properties().rarity(Rarity.EPIC).fireResistant().component(DataComponents.UNBREAKABLE, new Unbreakable(true)));

    // Eaten once: hunger bar always full from then on (item/SoulInsectItem).
    public static final DeferredItem<SoulInsectItem> SOUL_INSECT = BattlegroundsMod.ITEMS.registerItem("soul_insect",
            SoulInsectItem::new, new Item.Properties().stacksTo(16).rarity(Rarity.RARE)
                    .food(new FoodProperties.Builder().nutrition(0).saturationModifier(0).alwaysEdible().build()));

    public static final DeferredItem<DeferredSpawnEggItem> MADARA_SPAWN_EGG = BattlegroundsMod.ITEMS.register("madara_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.MADARA, 0x1A1A1F, 0xD12F2F, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> BILLS_SPAWN_EGG = BattlegroundsMod.ITEMS.register("bills_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.BILLS, 0x9B30FF, 0xF2C94C, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> GOJO_SPAWN_EGG = BattlegroundsMod.ITEMS.register("gojo_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.GOJO, 0x14161C, 0x4FC3FF, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> SUKUNA_SPAWN_EGG = BattlegroundsMod.ITEMS.register("sukuna_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.SUKUNA, 0x16090B, 0xC8321E, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> KOKUSHIBO_SPAWN_EGG = BattlegroundsMod.ITEMS.register("kokushibo_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.KOKUSHIBO, 0x2D1531, 0xC9A2FF, new Item.Properties()));

    // SubAlma and Fighter mobs.
    public static final DeferredItem<DeferredSpawnEggItem> BYAKUYA_SPAWN_EGG = BattlegroundsMod.ITEMS.register("byakuya_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.BYAKUYA, 0x1B1B22, 0xF2B6C8, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> RUKIA_SPAWN_EGG = BattlegroundsMod.ITEMS.register("rukia_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.RUKIA, 0x15151C, 0xCFEFFF, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> GOKU_SPAWN_EGG = BattlegroundsMod.ITEMS.register("goku_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.GOKU, 0xF26A1B, 0x2A4AA8, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> VEGETA_SPAWN_EGG = BattlegroundsMod.ITEMS.register("vegeta_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.VEGETA, 0x2A3A8A, 0xF4F4F4, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> FREEZA_SPAWN_EGG = BattlegroundsMod.ITEMS.register("freeza_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.FREEZA, 0xF4F0F4, 0x8A3AB8, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> PICCOLO_SPAWN_EGG = BattlegroundsMod.ITEMS.register("piccolo_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.PICCOLO, 0x62B04A, 0x6A3AA0, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> KAME_SPAWN_EGG = BattlegroundsMod.ITEMS.register("kame_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.KAME, 0xFF9A3A, 0xF0C8A0, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> KURIRIN_SPAWN_EGG = BattlegroundsMod.ITEMS.register("kuririn_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.KURIRIN, 0xFF7A1A, 0x2A4AA8, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> NARUTO_SPAWN_EGG = BattlegroundsMod.ITEMS.register("naruto_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.NARUTO, 0xFF7A12, 0x1C3A8A, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> SASUKE_SPAWN_EGG = BattlegroundsMod.ITEMS.register("sasuke_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.SASUKE, 0xECEFF4, 0x5A3A8A, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> KAKASHI_SPAWN_EGG = BattlegroundsMod.ITEMS.register("kakashi_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.KAKASHI, 0x56653A, 0xD0D4DC, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> GAARA_SPAWN_EGG = BattlegroundsMod.ITEMS.register("gaara_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.GAARA, 0x7A2020, 0xC9A06A, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> SHIKAMARU_SPAWN_EGG = BattlegroundsMod.ITEMS.register("shikamaru_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.SHIKAMARU, 0x5F6B3A, 0x2A2620, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> INO_SPAWN_EGG = BattlegroundsMod.ITEMS.register("ino_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.INO, 0x7A3AA8, 0xF3DC7A, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> ITADORI_SPAWN_EGG = BattlegroundsMod.ITEMS.register("itadori_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.ITADORI, 0x2A2A3A, 0xE89A8A, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> CHOSO_SPAWN_EGG = BattlegroundsMod.ITEMS.register("choso_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.CHOSO, 0x6B4A3A, 0x2A1A1A, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> NANAMI_SPAWN_EGG = BattlegroundsMod.ITEMS.register("nanami_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.NANAMI, 0xE8D9A8, 0x8A6A3A, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> NOBARA_SPAWN_EGG = BattlegroundsMod.ITEMS.register("nobara_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.NOBARA, 0x2A2A3A, 0xB0582E, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> TODO_SPAWN_EGG = BattlegroundsMod.ITEMS.register("todo_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.TODO, 0x8A8A8A, 0x3A2A1A, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> INUMAKI_SPAWN_EGG = BattlegroundsMod.ITEMS.register("inumaki_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.INUMAKI, 0x2A2A3A, 0xE8E8F0, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> MIWA_SPAWN_EGG = BattlegroundsMod.ITEMS.register("miwa_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.MIWA, 0x2A3A5A, 0x6FB8E8, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> SOLDADO_RED_RIBBON_SPAWN_EGG = BattlegroundsMod.ITEMS.register("soldado_red_ribbon_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.SOLDADO_RED_RIBBON, 0x6B6B3A, 0xC8102E, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> SAIBAMAN_SPAWN_EGG = BattlegroundsMod.ITEMS.register("saibaman_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.SAIBAMAN, 0x3E8E2E, 0xA8D86A, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> BANDIDO_DESERTO_SPAWN_EGG = BattlegroundsMod.ITEMS.register("bandido_deserto_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.BANDIDO_DESERTO, 0xC9A46A, 0x7A3E1C, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> PIRATA_SPAWN_EGG = BattlegroundsMod.ITEMS.register("pirata_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.PIRATA, 0x2F3E8C, 0xD23A2A, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> YETI_SPAWN_EGG = BattlegroundsMod.ITEMS.register("yeti_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.YETI, 0xEDEFF2, 0x7FA6C2, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> TIRANOSSAURO_SPAWN_EGG = BattlegroundsMod.ITEMS.register("tiranossauro_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.TIRANOSSAURO, 0x5E7A3A, 0xD8C79A, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> RAPTOR_SPAWN_EGG = BattlegroundsMod.ITEMS.register("raptor_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.RAPTOR, 0x8C6A3E, 0x3C2C1C, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> SOLDADO_FREEZA_SPAWN_EGG = BattlegroundsMod.ITEMS.register("soldado_freeza_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.SOLDADO_FREEZA, 0xE8E8E0, 0x6A3C8E, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> RADITZ_SPAWN_EGG = BattlegroundsMod.ITEMS.register("raditz_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.RADITZ, 0x2A2A3A, 0xE8E4D8, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> CELL_JR_SPAWN_EGG = BattlegroundsMod.ITEMS.register("cell_jr_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.CELL_JR, 0x82BEAA, 0x2E6A3C, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> PUI_PUI_SPAWN_EGG = BattlegroundsMod.ITEMS.register("pui_pui_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.PUI_PUI, 0x965AAA, 0xE8E4D8, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> YAKON_SPAWN_EGG = BattlegroundsMod.ITEMS.register("yakon_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.YAKON, 0x1C1A1E, 0xE6C828, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> DABURA_SPAWN_EGG = BattlegroundsMod.ITEMS.register("dabura_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.DABURA, 0xBE322D, 0x1E1C20, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> NPC_BULMA_SPAWN_EGG = BattlegroundsMod.ITEMS.register("npc_bulma_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.NPC_BULMA, 0xE678A0, 0x3CBEBE, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> NPC_MESTRE_KAME_SPAWN_EGG = BattlegroundsMod.ITEMS.register("npc_mestre_kame_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.NPC_MESTRE_KAME, 0xE8A040, 0x4A8A3A, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> NPC_BOO_SPAWN_EGG = BattlegroundsMod.ITEMS.register("npc_boo_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.NPC_BOO, 0xF096BE, 0x7838A0, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> NPC_GOHAN_SPAWN_EGG = BattlegroundsMod.ITEMS.register("npc_gohan_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.NPC_GOHAN, 0x5A2D82, 0xE8C27A, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> NPC_KORIN_SPAWN_EGG = BattlegroundsMod.ITEMS.register("npc_korin_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.NPC_KORIN, 0xF2F2EE, 0x8A6A4F, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> NINJA_RENEGADO_SPAWN_EGG = BattlegroundsMod.ITEMS.register("ninja_renegado_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.NINJA_RENEGADO, 0x5A5A66, 0x9A9AA8, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> NINJA_SOM_SPAWN_EGG = BattlegroundsMod.ITEMS.register("ninja_som_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.NINJA_SOM, 0x6A5A8E, 0xD8C8A0, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> ANBU_RAIZ_SPAWN_EGG = BattlegroundsMod.ITEMS.register("anbu_raiz_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.ANBU_RAIZ, 0x2A2A30, 0xE8E8E8, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> NPC_JIRAIYA_SPAWN_EGG = BattlegroundsMod.ITEMS.register("npc_jiraiya_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.NPC_JIRAIYA, 0xB03030, 0xEDEDED, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> NPC_TEUCHI_SPAWN_EGG = BattlegroundsMod.ITEMS.register("npc_teuchi_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.NPC_TEUCHI, 0xF2F2EE, 0x3E6FB0, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> MALDICAO_MENOR_SPAWN_EGG = BattlegroundsMod.ITEMS.register("maldicao_menor_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.MALDICAO_MENOR, 0x3A2A4A, 0x8AE05A, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> MALDICAO_GRAU2_SPAWN_EGG = BattlegroundsMod.ITEMS.register("maldicao_grau2_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.MALDICAO_GRAU2, 0x4A1A2A, 0xD8D0C0, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> FEITICEIRO_RENEGADO_SPAWN_EGG = BattlegroundsMod.ITEMS.register("feiticeiro_renegado_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.FEITICEIRO_RENEGADO, 0x1A1A22, 0x8A2AA8, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> NPC_SHOKO_SPAWN_EGG = BattlegroundsMod.ITEMS.register("npc_shoko_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.NPC_SHOKO, 0x2A2A2A, 0x6A4A3A, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> NPC_YAGA_SPAWN_EGG = BattlegroundsMod.ITEMS.register("npc_yaga_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.NPC_YAGA, 0x1A1A1A, 0xC8A070, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> HOLLOW_SPAWN_EGG = BattlegroundsMod.ITEMS.register("hollow_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.HOLLOW, 0xF0F0F0, 0x1A1A1A, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> ARRANCAR_SPAWN_EGG = BattlegroundsMod.ITEMS.register("arrancar_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.ARRANCAR, 0xE8E8E0, 0x3A3A4A, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> MENOS_GRANDE_SPAWN_EGG = BattlegroundsMod.ITEMS.register("menos_grande_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.MENOS_GRANDE, 0x111111, 0xF0F0F0, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> ONI_COMUM_SPAWN_EGG = BattlegroundsMod.ITEMS.register("oni_comum_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.ONI_COMUM, 0x6A2A2A, 0xD8C8B0, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> ONI_LUA_INFERIOR_SPAWN_EGG = BattlegroundsMod.ITEMS.register("oni_lua_inferior_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.ONI_LUA_INFERIOR, 0x3A1A3A, 0xC8202A, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> ONI_ESPADACHIM_SPAWN_EGG = BattlegroundsMod.ITEMS.register("oni_espadachim_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.ONI_ESPADACHIM, 0x2A1A2A, 0x8A6ACA, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> NPC_YORUICHI_SPAWN_EGG = BattlegroundsMod.ITEMS.register("npc_yoruichi_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.NPC_YORUICHI, 0x6A3A8A, 0xE8B040, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> NPC_URAHARA_SPAWN_EGG = BattlegroundsMod.ITEMS.register("npc_urahara_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.NPC_URAHARA, 0x3A6A3A, 0xF0F0E0, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> NPC_UROKODAKI_SPAWN_EGG = BattlegroundsMod.ITEMS.register("npc_urokodaki_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.NPC_UROKODAKI, 0x3A5AA8, 0xC83A3A, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> NPC_TAMAYO_SPAWN_EGG = BattlegroundsMod.ITEMS.register("npc_tamayo_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.NPC_TAMAYO, 0x8A3A6A, 0xF0D8E0, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> FIGHTER_HUMANO_SPAWN_EGG = BattlegroundsMod.ITEMS.register("fighter_humano_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.FIGHTER_HUMANO, 0x8A6A4F, 0x3E6FB0, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> FIGHTER_RUA_SPAWN_EGG = BattlegroundsMod.ITEMS.register("fighter_rua_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.FIGHTER_RUA, 0x8A6A4F, 0xC0392B, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> FIGHTER_QUARTEIRAO_SPAWN_EGG = BattlegroundsMod.ITEMS.register("fighter_quarteirao_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.FIGHTER_QUARTEIRAO, 0x8A6A4F, 0x2C2C2C, new Item.Properties()));

    public static final DeferredItem<DeferredSpawnEggItem> DUMMY_SPAWN_EGG = BattlegroundsMod.ITEMS.register("dummy_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.DUMMY, 0xC8A165, 0x6B4A2B, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> DUMMY_ATTACKER_SPAWN_EGG = BattlegroundsMod.ITEMS.register("dummy_attacker_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.DUMMY_ATTACKER, 0xC8A165, 0xC0392B, new Item.Properties()));

    // One soul crystal per power, registered as soul_<power id>.
    public static final Map<String, DeferredItem<SoulItem>> SOULS = new LinkedHashMap<>();

    // One Livro dos Desafios per universe (challenge/ChallengeArenas, souls_skills/padrao.desafios.json).
    public static final Map<String, DeferredItem<com.starkinhon.battlegroundsmod.item.ChallengeBookItem>> CHALLENGE_BOOKS = new LinkedHashMap<>();

    static {
        for (String universe : java.util.List.of("dragonball", "naruto", "jujutsu", "bleach", "demonslayer")) {
            CHALLENGE_BOOKS.put(universe, BattlegroundsMod.ITEMS.registerItem("livro_desafios_" + universe,
                    properties -> new com.starkinhon.battlegroundsmod.item.ChallengeBookItem(universe, properties),
                    new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
        }
    }

    // Cristal Multiversal: takes the player to another universe (challenge/Universes). Simple for now; the Fenda with
    // its tunnel comes later (Stark 2026-11-10).
    public static final DeferredItem<com.starkinhon.battlegroundsmod.item.MultiversalCrystalItem> MULTIVERSAL_CRYSTAL = BattlegroundsMod.ITEMS.registerItem(
            "cristal_multiversal", com.starkinhon.battlegroundsmod.item.MultiversalCrystalItem::new,
            new Item.Properties().stacksTo(16).rarity(Rarity.RARE));

    // Dragon Ball items and weapons (universe/DragonBallWorld). Placeholder looks; what they do is what matters for now.
    public static final DeferredItem<com.starkinhon.battlegroundsmod.item.SenzuItem> SENZU = BattlegroundsMod.ITEMS.registerItem("senzu",
            com.starkinhon.battlegroundsmod.item.SenzuItem::new, new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<com.starkinhon.battlegroundsmod.item.ScouterItem> SCOUTER = BattlegroundsMod.ITEMS.registerItem("scouter",
            com.starkinhon.battlegroundsmod.item.ScouterItem::new, new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<com.starkinhon.battlegroundsmod.item.DragonRadarItem> DRAGON_RADAR = BattlegroundsMod.ITEMS.registerItem("radar_dragao",
            com.starkinhon.battlegroundsmod.item.DragonRadarItem::new, new Item.Properties().rarity(Rarity.RARE));
    public static final DeferredItem<com.starkinhon.battlegroundsmod.item.PowerPoleItem> POWER_POLE = BattlegroundsMod.ITEMS.registerItem("bastao_magico",
            com.starkinhon.battlegroundsmod.item.PowerPoleItem::new, new Item.Properties().rarity(Rarity.RARE));
    public static final DeferredItem<com.starkinhon.battlegroundsmod.item.ZSwordItem> Z_SWORD = BattlegroundsMod.ITEMS.registerItem("espada_z",
            com.starkinhon.battlegroundsmod.item.ZSwordItem::new, new Item.Properties().rarity(Rarity.EPIC).fireResistant());
    // Naruto items and weapons (universe/NarutoWorld, universe/SageMode).
    public static final DeferredItem<com.starkinhon.battlegroundsmod.item.HiraishinKunaiItem> HIRAISHIN_KUNAI = BattlegroundsMod.ITEMS.registerItem("kunai_hiraishin",
            com.starkinhon.battlegroundsmod.item.HiraishinKunaiItem::new, new Item.Properties().rarity(Rarity.RARE));
    public static final DeferredItem<com.starkinhon.battlegroundsmod.item.SoldierPillItem> SOLDIER_PILL = BattlegroundsMod.ITEMS.registerItem("pilula_soldado",
            com.starkinhon.battlegroundsmod.item.SoldierPillItem::new, new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<com.starkinhon.battlegroundsmod.item.RamenItem> RAMEN = BattlegroundsMod.ITEMS.registerItem("ramen",
            com.starkinhon.battlegroundsmod.item.RamenItem::new, new Item.Properties().rarity(Rarity.COMMON));
    public static final DeferredItem<com.starkinhon.battlegroundsmod.item.SummonScrollItem> SUMMON_SCROLL = BattlegroundsMod.ITEMS.registerItem("pergaminho_ninken",
            com.starkinhon.battlegroundsmod.item.SummonScrollItem::new, new Item.Properties().rarity(Rarity.RARE));
    public static final DeferredItem<com.starkinhon.battlegroundsmod.item.PaperBombItem> PAPER_BOMB = BattlegroundsMod.ITEMS.registerItem("papel_bomba",
            com.starkinhon.battlegroundsmod.item.PaperBombItem::new, new Item.Properties().rarity(Rarity.COMMON));
    public static final DeferredItem<com.starkinhon.battlegroundsmod.item.NinjaHeadbandItem> NINJA_HEADBAND = BattlegroundsMod.ITEMS.registerItem("bandana_ninja",
            com.starkinhon.battlegroundsmod.item.NinjaHeadbandItem::new, new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<com.starkinhon.battlegroundsmod.item.SamehadaItem> SAMEHADA = BattlegroundsMod.ITEMS.registerItem("samehada",
            com.starkinhon.battlegroundsmod.item.SamehadaItem::new, new Item.Properties().rarity(Rarity.EPIC));
    public static final DeferredItem<com.starkinhon.battlegroundsmod.item.KusanagiItem> KUSANAGI = BattlegroundsMod.ITEMS.registerItem("kusanagi",
            com.starkinhon.battlegroundsmod.item.KusanagiItem::new, new Item.Properties().rarity(Rarity.EPIC));
    // Jujutsu items and weapons (universe/JujutsuWorld, universe/ReverseCurse).
    public static final DeferredItem<com.starkinhon.battlegroundsmod.item.SukunaFingerItem> SUKUNA_FINGER = BattlegroundsMod.ITEMS.registerItem("dedo_sukuna",
            com.starkinhon.battlegroundsmod.item.SukunaFingerItem::new, new Item.Properties().rarity(Rarity.EPIC));
    public static final DeferredItem<com.starkinhon.battlegroundsmod.item.GojoBlindfoldItem> GOJO_BLINDFOLD = BattlegroundsMod.ITEMS.registerItem("venda_gojo",
            com.starkinhon.battlegroundsmod.item.GojoBlindfoldItem::new, new Item.Properties().rarity(Rarity.EPIC));
    public static final DeferredItem<com.starkinhon.battlegroundsmod.item.StrawDollItem> STRAW_DOLL = BattlegroundsMod.ITEMS.registerItem("boneco_palha",
            com.starkinhon.battlegroundsmod.item.StrawDollItem::new, new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<com.starkinhon.battlegroundsmod.item.CursedDollItem> CURSED_DOLL = BattlegroundsMod.ITEMS.registerItem("boneco_amaldicoado",
            com.starkinhon.battlegroundsmod.item.CursedDollItem::new, new Item.Properties().rarity(Rarity.RARE));
    public static final DeferredItem<com.starkinhon.battlegroundsmod.item.PlayfulCloudItem> PLAYFUL_CLOUD = BattlegroundsMod.ITEMS.registerItem("nuvem_brincalhona",
            com.starkinhon.battlegroundsmod.item.PlayfulCloudItem::new, new Item.Properties().rarity(Rarity.EPIC));
    public static final DeferredItem<com.starkinhon.battlegroundsmod.item.InvertedSpearItem> INVERTED_SPEAR = BattlegroundsMod.ITEMS.registerItem("lanca_invertida",
            com.starkinhon.battlegroundsmod.item.InvertedSpearItem::new, new Item.Properties().rarity(Rarity.EPIC));
    // Bleach and Demon Slayer items and weapons (universe/BleachWorld, universe/DemonSlayerWorld).
    public static final DeferredItem<com.starkinhon.battlegroundsmod.item.SoulCandyItem> SOUL_CANDY = BattlegroundsMod.ITEMS.registerItem("pilula_alma",
            com.starkinhon.battlegroundsmod.item.SoulCandyItem::new, new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<com.starkinhon.battlegroundsmod.item.HollowMaskItem> HOLLOW_MASK = BattlegroundsMod.ITEMS.registerItem("mascara_hollow",
            com.starkinhon.battlegroundsmod.item.HollowMaskItem::new, new Item.Properties().rarity(Rarity.RARE));
    public static final DeferredItem<com.starkinhon.battlegroundsmod.item.ZanpakutoItem> ZANPAKUTO = BattlegroundsMod.ITEMS.registerItem("zanpakuto",
            com.starkinhon.battlegroundsmod.item.ZanpakutoItem::new, new Item.Properties().rarity(Rarity.EPIC));
    public static final DeferredItem<com.starkinhon.battlegroundsmod.item.NichirinSwordItem> NICHIRIN_SWORD = BattlegroundsMod.ITEMS.registerItem("espada_nichirin",
            com.starkinhon.battlegroundsmod.item.NichirinSwordItem::new, new Item.Properties().rarity(Rarity.EPIC));
    public static final DeferredItem<com.starkinhon.battlegroundsmod.item.WisteriaItem> WISTERIA = BattlegroundsMod.ITEMS.registerItem("flor_glicinia",
            com.starkinhon.battlegroundsmod.item.WisteriaItem::new, new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<com.starkinhon.battlegroundsmod.item.TamayoMedicineItem> TAMAYO_MEDICINE = BattlegroundsMod.ITEMS.registerItem("remedio_tamayo",
            com.starkinhon.battlegroundsmod.item.TamayoMedicineItem::new, new Item.Properties().rarity(Rarity.RARE));
    public static final java.util.List<DeferredItem<com.starkinhon.battlegroundsmod.item.DragonBallItem>> DRAGON_BALLS = new java.util.ArrayList<>();

    static {
        for (int stars = 1; stars <= 7; stars++) {
            int n = stars;
            DRAGON_BALLS.add(BattlegroundsMod.ITEMS.registerItem("esfera_dragao_" + n,
                    properties -> new com.starkinhon.battlegroundsmod.item.DragonBallItem(n, properties), new Item.Properties().rarity(Rarity.EPIC)));
        }
    }

    static {
        for (Power power : Powers.all()) {
            SOULS.put(power.id(), BattlegroundsMod.ITEMS.registerItem("soul_" + power.id(),
                    properties -> new SoulItem(power.id(), properties), new Item.Properties().stacksTo(16).rarity(Rarity.EPIC)));
        }
    }

    private ModItems() {
    }

    // Forces the class to load so the items above are added to the register before it fires.
    static void init() {
    }
}
