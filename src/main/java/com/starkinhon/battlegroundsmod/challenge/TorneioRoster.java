// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.challenge;

import java.util.List;
import java.util.Map;

// Who can be picked for the Torneio do Poder (Stark 2026-11-26: "todos os mobs do mod, de sub-almas, almas divinas e
// bosses"), by group, and the universe each one fights for in the team mode (the anime's teams are universes). Shared by
// the menu (client/TorneioScreen) and the match (TorneioMatch).
public final class TorneioRoster {
    public static final List<String> SUBS = List.of("kuririn", "piccolo", "kame", "goku", "vegeta", "freeza",
            "shikamaru", "ino", "kakashi", "gaara", "naruto", "sasuke",
            "nobara", "miwa", "nanami", "inumaki", "itadori", "choso", "todo",
            "rukia", "byakuya");
    public static final List<String> DIVINE = List.of("bills", "madara", "gojo", "sukuna", "aizen", "kokushibo", "shigaraki",
            "joy_boy", "makima", "saitama");
    public static final List<String> BOSSES = List.of("hashirama", "freeza_total", "toji");

    // the universe ids of souls_skills/padrao.desafios.json
    private static final Map<String, String> UNIVERSE = Map.ofEntries(
            Map.entry("kuririn", "dragonball"), Map.entry("piccolo", "dragonball"), Map.entry("kame", "dragonball"),
            Map.entry("goku", "dragonball"), Map.entry("vegeta", "dragonball"), Map.entry("freeza", "dragonball"),
            Map.entry("bills", "dragonball"), Map.entry("freeza_total", "dragonball"),
            Map.entry("shikamaru", "naruto"), Map.entry("ino", "naruto"), Map.entry("kakashi", "naruto"), Map.entry("gaara", "naruto"),
            Map.entry("naruto", "naruto"), Map.entry("sasuke", "naruto"), Map.entry("madara", "naruto"), Map.entry("hashirama", "naruto"),
            Map.entry("nobara", "jujutsu"), Map.entry("miwa", "jujutsu"), Map.entry("nanami", "jujutsu"), Map.entry("inumaki", "jujutsu"),
            Map.entry("itadori", "jujutsu"), Map.entry("choso", "jujutsu"), Map.entry("todo", "jujutsu"), Map.entry("gojo", "jujutsu"),
            Map.entry("sukuna", "jujutsu"), Map.entry("toji", "jujutsu"),
            Map.entry("rukia", "bleach"), Map.entry("byakuya", "bleach"), Map.entry("aizen", "bleach"),
            Map.entry("kokushibo", "demonslayer"), Map.entry("shigaraki", "mha"), Map.entry("joy_boy", "onepiece"),
            Map.entry("makima", "chainsawman"), Map.entry("saitama", "opm"));

    public static final List<String> UNIVERSES = List.of("dragonball", "naruto", "jujutsu", "bleach", "demonslayer", "mha",
            "onepiece", "chainsawman", "opm");
    private static final Map<String, String> UNIVERSE_NAME = Map.of("dragonball", "Dragon Ball", "naruto", "Naruto",
            "jujutsu", "Jujutsu Kaisen", "bleach", "Bleach", "demonslayer", "Demon Slayer", "mha", "My Hero Academia",
            "onepiece", "One Piece", "chainsawman", "Chainsaw Man", "opm", "One Punch Man");

    private TorneioRoster() {
    }

    public static boolean known(String id) {
        return SUBS.contains(id) || DIVINE.contains(id) || BOSSES.contains(id);
    }

    public static String universe(String id) {
        return UNIVERSE.getOrDefault(id, "dragonball");
    }

    public static String universeName(String uni) {
        return UNIVERSE_NAME.getOrDefault(uni, uni);
    }
}
