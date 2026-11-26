// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.power.powers.makima;

import java.util.Map;

// Shared by the Ritual dos Nomes (Ritual) and the creative search (client/CreativeCharacterSearch). Plain data, no
// game classes, so the client can load it without touching anything else of the ritual.
public final class RealNames {
    // The real names (Stark 2026-11-23): the souls go by their titles everywhere in the game (#530), but in the middle
    // of a fight nobody remembers "Saiyajin da Terra", so the sheet is written with the name. Keyed by the entity's id
    // (or the boss's id); what is not here (a plain mob, a nametag) keeps its own name.
    public static final Map<String, String> MAP = Map.ofEntries(
            // Divine souls and end bosses
            Map.entry("madara", "Madara Uchiha"), Map.entry("bills", "Bills"), Map.entry("gojo", "Satoru Gojo"),
            Map.entry("kokushibo", "Kokushibo"), Map.entry("sukuna", "Ryomen Sukuna"), Map.entry("aizen", "Sosuke Aizen"),
            Map.entry("joy_boy", "Luffy"), Map.entry("saitama", "Saitama"), Map.entry("shigaraki", "Tomura Shigaraki"),
            Map.entry("makima", "Makima"), Map.entry("hashirama", "Hashirama Senju"), Map.entry("freeza_total", "Freeza"),
            Map.entry("toji", "Toji Fushiguro"),
            // Sub-souls
            Map.entry("goku", "Goku"), Map.entry("vegeta", "Vegeta"), Map.entry("freeza", "Freeza"),
            Map.entry("piccolo", "Piccolo"), Map.entry("kame", "Mestre Kame"), Map.entry("kuririn", "Kuririn"),
            Map.entry("naruto", "Naruto Uzumaki"), Map.entry("sasuke", "Sasuke Uchiha"), Map.entry("kakashi", "Kakashi Hatake"),
            Map.entry("gaara", "Gaara"), Map.entry("shikamaru", "Shikamaru Nara"), Map.entry("ino", "Ino Yamanaka"),
            Map.entry("itadori", "Yuji Itadori"), Map.entry("choso", "Choso"), Map.entry("nanami", "Kento Nanami"),
            Map.entry("nobara", "Nobara Kugisaki"), Map.entry("todo", "Aoi Todo"), Map.entry("inumaki", "Toge Inumaki"),
            Map.entry("miwa", "Kasumi Miwa"), Map.entry("byakuya", "Byakuya Kuchiki"), Map.entry("rukia", "Rukia Kuchiki"),
            // Named mobs and minibosses
            Map.entry("bora", "Bora"), Map.entry("raditz", "Raditz"), Map.entry("cell_jr", "Cell Jr."),
            Map.entry("pui_pui", "Pui Pui"), Map.entry("yakon", "Yakon"), Map.entry("dabura", "Dabura"),
            Map.entry("zetsu_branco", "Zetsu Branco"), Map.entry("androide_gero", "Androide do Dr. Gero"),
            Map.entry("tao_pai_pai", "Tao Pai Pai"), Map.entry("spopovich", "Spopovich"), Map.entry("yamu", "Yamu"),
            Map.entry("babidi", "Babidi"), Map.entry("jj_boneco", "Boneco do Yaga"), Map.entry("jj_kogane", "Kogane"),
            Map.entry("jj_mechamaru", "Mini Mechamaru"),
            // NPCs
            Map.entry("npc_gohan", "Gohan"), Map.entry("npc_korin", "Mestre Karin"), Map.entry("npc_jiraiya", "Jiraiya"),
            Map.entry("npc_teuchi", "Teuchi"), Map.entry("npc_shoko", "Shoko Ieiri"), Map.entry("npc_yaga", "Diretor Yaga"),
            Map.entry("npc_yoruichi", "Yoruichi"), Map.entry("npc_urahara", "Kisuke Urahara"),
            Map.entry("npc_urokodaki", "Urokodaki"), Map.entry("npc_tamayo", "Tamayo"), Map.entry("npc_bulma", "Bulma"),
            Map.entry("npc_mestre_kame", "Mestre Kame"), Map.entry("npc_boo", "Majin Boo"),
            Map.entry("npc_kakashi", "Kakashi Hatake"), Map.entry("npc_maki", "Maki Zenin"));

    private RealNames() {
    }
}
