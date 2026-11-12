package com.starkinhon.battlegroundsmod.item;

import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.TieredItem;

// Which universe each mod item belongs to (its creative tab, Stark 2026-11-10: "separar universo por universo"), and
// its kind inside the tab (the arrows above the items flip between them). Items of no universe (Souls Book, Fight Rod,
// Inseto de Alma, Cristal Multiversal, the street fighters, the dummies, Makima) stay in the Essentials tab.
public final class UniverseItems {
    // Same ids as the universe dimensions (battlegroundsmod:<id>), in the tabs' order.
    public static final List<String> UNIVERSES = List.of("dragonball", "naruto", "jujutsu", "bleach", "demonslayer");

    // The arrows' pages. ALL is every item of the tab.
    public enum Kind { ALL, SOUL, WEAPON, ITEM, SPAWN_EGG }

    // Characters, mobs and NPCs (souls by power id, spawn eggs by entity id).
    private static final Map<String, String> CHARACTERS = Map.ofEntries(
            Map.entry("destruction_god", "dragonball"), Map.entry("bills", "dragonball"),
            Map.entry("goku", "dragonball"), Map.entry("vegeta", "dragonball"), Map.entry("freeza", "dragonball"),
            Map.entry("piccolo", "dragonball"), Map.entry("kame", "dragonball"), Map.entry("kuririn", "dragonball"),
            Map.entry("soldado_red_ribbon", "dragonball"), Map.entry("saibaman", "dragonball"),
            Map.entry("soldado_freeza", "dragonball"), Map.entry("bandido_deserto", "dragonball"), Map.entry("pirata", "dragonball"),
            Map.entry("yeti", "dragonball"), Map.entry("raptor", "dragonball"), Map.entry("tiranossauro", "dragonball"),
            Map.entry("namekiano", "dragonball"), Map.entry("peixe_gigante", "dragonball"), Map.entry("androide_8", "dragonball"),
            Map.entry("ladrao_urso", "dragonball"), Map.entry("bora", "dragonball"),
            Map.entry("pterodactilo", "dragonball"), Map.entry("cell_jr", "dragonball"), Map.entry("yakon", "dragonball"),
            Map.entry("dino_chifrudo", "dragonball"), Map.entry("dragao", "dragonball"), Map.entry("tigre_sabre", "dragonball"),
            Map.entry("androide_gero", "dragonball"), Map.entry("tao_pai_pai", "dragonball"), Map.entry("spopovich", "dragonball"),
            Map.entry("yamu", "dragonball"), Map.entry("babidi", "dragonball"),
            Map.entry("npc_gohan", "dragonball"), Map.entry("npc_korin", "dragonball"),
            Map.entry("uchiha_ghost", "naruto"), Map.entry("madara", "naruto"),
            Map.entry("naruto", "naruto"), Map.entry("sasuke", "naruto"), Map.entry("kakashi", "naruto"),
            Map.entry("gaara", "naruto"), Map.entry("shikamaru", "naruto"), Map.entry("ino", "naruto"),
            Map.entry("ninja_renegado", "naruto"), Map.entry("ninja_som", "naruto"), Map.entry("anbu_raiz", "naruto"),
            Map.entry("npc_jiraiya", "naruto"), Map.entry("npc_teuchi", "naruto"),
            Map.entry("strongest", "jujutsu"), Map.entry("gojo", "jujutsu"), Map.entry("sukuna", "jujutsu"),
            Map.entry("itadori", "jujutsu"), Map.entry("choso", "jujutsu"), Map.entry("nanami", "jujutsu"),
            Map.entry("nobara", "jujutsu"), Map.entry("todo", "jujutsu"), Map.entry("inumaki", "jujutsu"),
            Map.entry("miwa", "jujutsu"), Map.entry("maldicao_menor", "jujutsu"), Map.entry("maldicao_grau2", "jujutsu"),
            Map.entry("feiticeiro_renegado", "jujutsu"), Map.entry("npc_shoko", "jujutsu"), Map.entry("npc_yaga", "jujutsu"),
            Map.entry("byakuya", "bleach"), Map.entry("rukia", "bleach"), Map.entry("hollow", "bleach"),
            Map.entry("arrancar", "bleach"), Map.entry("menos_grande", "bleach"), Map.entry("npc_yoruichi", "bleach"),
            Map.entry("npc_urahara", "bleach"),
            Map.entry("moon_demon", "demonslayer"), Map.entry("kokushibo", "demonslayer"), Map.entry("oni_comum", "demonslayer"),
            Map.entry("oni_lua_inferior", "demonslayer"), Map.entry("oni_espadachim", "demonslayer"),
            Map.entry("npc_urokodaki", "demonslayer"), Map.entry("npc_tamayo", "demonslayer"));

    // Items, by item id.
    private static final Map<String, String> ITEMS = Map.ofEntries(
            Map.entry("senzu", "dragonball"), Map.entry("scouter", "dragonball"), 
            Map.entry("radar_dragao", "dragonball"), 
            Map.entry("kunai_hiraishin", "naruto"), Map.entry("pilula_soldado", "naruto"), Map.entry("ramen", "naruto"),
            Map.entry("pergaminho_ninken", "naruto"), Map.entry("bandana_ninja", "naruto"),
            Map.entry("samehada", "naruto"), Map.entry("kusanagi", "naruto"),
            Map.entry("guizo", "naruto"), Map.entry("pergaminho_ceu", "naruto"), Map.entry("pergaminho_terra", "naruto"),
            Map.entry("pele_cobra", "naruto"), Map.entry("pergaminho_marionete", "naruto"), Map.entry("kubikiribocho", "naruto"),
            Map.entry("asas_de_papel", "naruto"), Map.entry("bolsa_veneno", "naruto"), Map.entry("celula_hashirama", "naruto"),
            Map.entry("pergaminho_sapo", "naruto"), Map.entry("sharingan", "naruto"), Map.entry("selo_de_papel", "naruto"),
            Map.entry("fio_de_chakra", "naruto"),
            Map.entry("dedo_sukuna", "jujutsu"), Map.entry("venda_gojo", "jujutsu"), Map.entry("boneco_palha", "jujutsu"),
            Map.entry("boneco_amaldicoado", "jujutsu"), Map.entry("nuvem_brincalhona", "jujutsu"), Map.entry("lanca_invertida", "jujutsu"),
            Map.entry("aizen_sword", "bleach"), Map.entry("pilula_alma", "bleach"), Map.entry("mascara_hollow", "bleach"),
            Map.entry("zanpakuto", "bleach"),
            Map.entry("kokushibo_sword", "demonslayer"), Map.entry("espada_nichirin", "demonslayer"),
            Map.entry("flor_glicinia", "demonslayer"), Map.entry("remedio_tamayo", "demonslayer"));

    // Weapons that are not TieredItem (thrown or special).
    private static final Set<String> WEAPONS = Set.of("kunai_hiraishin", "samehada", "kusanagi");

    private UniverseItems() {
    }

    // The universe id, or null for the Essentials tab.
    public static String universe(Item item) {
        String id = BuiltInRegistries.ITEM.getKey(item).getPath();
        String found = ITEMS.get(id);
        if (found != null) {
            return found;
        }
        if (id.startsWith("esfera_dragao_")) {
            return "dragonball";
        }
        if (id.startsWith("livro_desafios_")) {
            String universe = id.substring("livro_desafios_".length());
            return UNIVERSES.contains(universe) ? universe : null;
        }
        String character = null;
        if (id.startsWith("soul_")) {
            character = id.substring("soul_".length());
        } else if (id.endsWith("_spawn_egg")) {
            character = id.substring(0, id.length() - "_spawn_egg".length());
        }
        if (character != null && com.starkinhon.battlegroundsmod.animal.naruto.NarutoSpec.get(character) != null) {
            return "naruto";
        }
        String universe = character == null ? null : CHARACTERS.get(character);
        return universe == null || universe.isEmpty() ? null : universe;
    }

    public static Kind kind(Item item) {
        if (item instanceof SoulItem) {
            return Kind.SOUL;
        }
        if (item instanceof SpawnEggItem) {
            return Kind.SPAWN_EGG;
        }
        if (item instanceof TieredItem || WEAPONS.contains(BuiltInRegistries.ITEM.getKey(item).getPath())) {
            return Kind.WEAPON;
        }
        return Kind.ITEM;
    }
}
