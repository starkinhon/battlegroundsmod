// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.item;

import java.util.List;
import java.util.Map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

// Which universe each mod item belongs to. The creative tabs are split by use (ModCreativeTabs); the arrows above the
// items (client/CreativeSubTabs) filter a tab by universe. Items of no universe (Souls Book, Fight Rod, Inseto de Alma,
// the street fighters, the dummies, Makima) show up on the "Outros" page.
public final class UniverseItems {
    // Same ids as the universe dimensions (battlegroundsmod:<id>), in the tabs' order.
    public static final List<String> UNIVERSES = List.of("dragonball", "naruto", "jujutsu", "bleach", "demonslayer");

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
            Map.entry("freeza_total", "dragonball"), Map.entry("raditz", "dragonball"), Map.entry("pui_pui", "dragonball"),
            Map.entry("dabura", "dragonball"),
            Map.entry("npc_bulma", "dragonball"), Map.entry("npc_mestre_kame", "dragonball"), Map.entry("npc_boo", "dragonball"),
            Map.entry("npc_gohan", "dragonball"), Map.entry("npc_korin", "dragonball"), Map.entry("npc_kaioh", "dragonball"),
            Map.entry("uchiha_ghost", "naruto"), Map.entry("madara", "naruto"), Map.entry("hashirama", "naruto"), Map.entry("toji", "jujutsu"),
            Map.entry("naruto", "naruto"), Map.entry("sasuke", "naruto"), Map.entry("kakashi", "naruto"),
            Map.entry("gaara", "naruto"), Map.entry("shikamaru", "naruto"), Map.entry("ino", "naruto"),
            Map.entry("ninja_renegado", "naruto"), Map.entry("ninja_som", "naruto"), Map.entry("anbu_raiz", "naruto"),
            Map.entry("npc_jiraiya", "naruto"), Map.entry("npc_teuchi", "naruto"), Map.entry("npc_kakashi", "naruto"),
            Map.entry("strongest", "jujutsu"), Map.entry("gojo", "jujutsu"), Map.entry("sukuna", "jujutsu"),
            Map.entry("itadori", "jujutsu"), Map.entry("choso", "jujutsu"), Map.entry("nanami", "jujutsu"),
            Map.entry("nobara", "jujutsu"), Map.entry("todo", "jujutsu"), Map.entry("inumaki", "jujutsu"),
            Map.entry("miwa", "jujutsu"), Map.entry("maldicao_menor", "jujutsu"), Map.entry("maldicao_grau2", "jujutsu"),
            Map.entry("feiticeiro_renegado", "jujutsu"), Map.entry("npc_shoko", "jujutsu"), Map.entry("npc_yaga", "jujutsu"), Map.entry("npc_maki", "jujutsu"),
            Map.entry("manipulator", "bleach"), Map.entry("byakuya", "bleach"), Map.entry("rukia", "bleach"), Map.entry("hollow", "bleach"),
            Map.entry("arrancar", "bleach"), Map.entry("menos_grande", "bleach"), Map.entry("npc_yoruichi", "bleach"),
            Map.entry("npc_urahara", "bleach"),
            Map.entry("moon_demon", "demonslayer"), Map.entry("kokushibo", "demonslayer"), Map.entry("oni_comum", "demonslayer"),
            Map.entry("oni_lua_inferior", "demonslayer"), Map.entry("oni_espadachim", "demonslayer"),
            Map.entry("npc_urokodaki", "demonslayer"), Map.entry("npc_tamayo", "demonslayer"));

    // Items, by item id.
    private static final Map<String, String> ITEMS = Map.ofEntries(
            Map.entry("senzu", "dragonball"), Map.entry("scouter", "dragonball"), 
            Map.entry("radar_dragao", "dragonball"), Map.entry("capsula_hoipoi", "dragonball"),
            Map.entry("kunai_hiraishin", "naruto"), Map.entry("pilula_soldado", "naruto"), Map.entry("ramen", "naruto"), Map.entry("balao_treino", "naruto"),
            Map.entry("bandana_ninja", "naruto"),
            Map.entry("shuriken", "naruto"), Map.entry("kunai", "naruto"), Map.entry("papel_bomba", "naruto"),
            Map.entry("cabaca_gaara", "naruto"), Map.entry("leque_temari", "naruto"),
            Map.entry("pergaminho_selamento", "naruto"),
            Map.entry("gunbai", "naruto"), Map.entry("kusanagi", "naruto"),
            Map.entry("guizo", "naruto"), Map.entry("pergaminho_ceu", "naruto"), Map.entry("pergaminho_terra", "naruto"),
            Map.entry("pele_cobra", "naruto"), Map.entry("pergaminho_marionete", "naruto"), Map.entry("kubikiribocho", "naruto"),
            Map.entry("bolsa_veneno", "naruto"), Map.entry("celula_hashirama", "naruto"),
            Map.entry("pergaminho_sapo", "naruto"), Map.entry("sharingan", "naruto"), Map.entry("selo_de_papel", "naruto"),
            Map.entry("fio_de_chakra", "naruto"),
            Map.entry("dedo_sukuna", "jujutsu"), Map.entry("venda_gojo", "jujutsu"), Map.entry("boneco_palha", "jujutsu"),
            Map.entry("boneco_amaldicoado", "jujutsu"), Map.entry("nuvem_brincalhona", "jujutsu"), Map.entry("lanca_invertida", "jujutsu"),
            Map.entry("nanami_blade", "jujutsu"), Map.entry("miwa_sword", "jujutsu"), Map.entry("nobara_hammer", "jujutsu"),
            Map.entry("aizen_sword", "bleach"), Map.entry("pilula_alma", "bleach"),
            Map.entry("sode_no_shirayuki", "bleach"), Map.entry("senbonzakura", "bleach"),
            Map.entry("kokushibo_sword", "demonslayer"), Map.entry("espada_nichirin", "demonslayer"),
            Map.entry("flor_glicinia", "demonslayer"), Map.entry("remedio_tamayo", "demonslayer"));

    // Blocks from ModBlocks, by block/item id (JujutsuBlocks are matched via JujutsuBlocks.has).
    private static final Map<String, String> BLOCKS = Map.ofEntries(
            // Dragon Ball: Namek, King Kai's planet, Deserto do Diabo.
            Map.entry("namek_rock", "dragonball"), Map.entry("namek_rock_layer", "dragonball"), Map.entry("namek_sand", "dragonball"),
            Map.entry("kaioh_grama", "dragonball"), Map.entry("kaioh_lajota", "dragonball"),
            Map.entry("kaioh_casa", "dragonball"), Map.entry("kaioh_casa_clara", "dragonball"),
            Map.entry("deserto_areia", "dragonball"), Map.entry("deserto_rocha", "dragonball"), Map.entry("deserto_rocha_clara", "dragonball"),
            // Naruto: Floresta da Folha, Floresta da Morte, Deserto do Vento, Mar da Névoa, Vila da Chuva, Vale do Fim, Monte Myoboku.
            Map.entry("folhas_da_folha", "naruto"), Map.entry("folhas_da_folha_clara", "naruto"),
            Map.entry("folhas_da_folha_escura", "naruto"), Map.entry("folhas_caidas", "naruto"),
            Map.entry("folhas_da_morte", "naruto"), Map.entry("folhas_da_morte_clara", "naruto"),
            Map.entry("folhas_da_morte_escura", "naruto"), Map.entry("cipo", "naruto"), Map.entry("trepadeira", "naruto"),
            Map.entry("chapeu_cogumelo", "naruto"), Map.entry("petala_carnivora", "naruto"),
            Map.entry("petala_carnivora_pintada", "naruto"), Map.entry("boca_carnivora", "naruto"),
            Map.entry("folhas_podres", "naruto"), Map.entry("raizes_expostas", "naruto"), Map.entry("cogumelo_brilhante", "naruto"),
            Map.entry("camada_de_areia", "naruto"), Map.entry("capim_seco", "naruto"),
            Map.entry("folhas_de_pinheiro", "naruto"), Map.entry("folhas_de_pinheiro_escura", "naruto"), Map.entry("corda", "naruto"),
            Map.entry("metal_da_chuva", "naruto"), Map.entry("metal_da_chuva_escuro", "naruto"), Map.entry("anel_de_metal", "naruto"),
            Map.entry("ferrugem", "naruto"), Map.entry("ferrugem_clara", "naruto"),
            Map.entry("janela_escura", "naruto"), Map.entry("janela_acesa", "naruto"),
            Map.entry("cano_de_metal", "naruto"), Map.entry("espeto_de_metal", "naruto"),
            Map.entry("pedra_da_estatua", "naruto"), Map.entry("pedra_da_estatua_escura", "naruto"),
            Map.entry("pedra_da_estatua_musgo", "naruto"), Map.entry("pedra_da_estatua_sombra", "naruto"),
            Map.entry("pedra_do_pico", "naruto"), Map.entry("pedra_do_pico_escura", "naruto"), Map.entry("pedra_do_pico_clara", "naruto"),
            Map.entry("caule_gigante", "naruto"), Map.entry("folha_gigante", "naruto"),
            Map.entry("folha_gigante_amarela", "naruto"), Map.entry("folha_gigante_escura", "naruto"),
            Map.entry("selo_de_papel", "naruto"), Map.entry("fio_de_chakra", "naruto"),
            Map.entry("petala_gigante", "naruto"), Map.entry("petala_gigante_ponta", "naruto"), Map.entry("miolo_de_flor", "naruto"),
            // Jujutsu: Templos Antigos.
            Map.entry("madeira_vermelha", "jujutsu"), Map.entry("laca_preta", "jujutsu"), Map.entry("cascalho_branco", "jujutsu"),
            Map.entry("shoji", "jujutsu"), Map.entry("reboco", "jujutsu"), Map.entry("lanterna_de_pedra_acesa", "jujutsu"),
            Map.entry("shimenawa", "jujutsu"), Map.entry("shide", "jujutsu"),
            Map.entry("folhas_de_cedro", "jujutsu"), Map.entry("folhas_de_cedro_clara", "jujutsu"),
            Map.entry("folhas_de_sakura_escura", "jujutsu"), Map.entry("folhas_de_sakura_clara", "jujutsu"),
            Map.entry("folhas_de_sakura_branca", "jujutsu"));

    private UniverseItems() {
    }

    // The universe id, or null for general/other items.
    public static String universe(Item item) {
        String id = BuiltInRegistries.ITEM.getKey(item).getPath();
        String found = ITEMS.get(id);
        if (found != null) {
            return found;
        }
        found = BLOCKS.get(id);
        if (found != null) {
            return found;
        }
        if (com.starkinhon.battlegroundsmod.JujutsuBlocks.has(id)) {
            return "jujutsu";
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
        if (character != null && com.starkinhon.battlegroundsmod.animal.jjmob.JjMobSpec.has(character)) {
            return "jujutsu";
        }
        String universe = character == null ? null : CHARACTERS.get(character);
        return universe == null || universe.isEmpty() ? null : universe;
    }
}
