// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.power.powers.makima.RealNames;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// The creative search finds a character by the real name, like the Ritual dos Nomes (Stark 2026-11-26): "aizen" brings
// The Manipulator's soul, the Kyōka Suigetsu and his spawn egg, even where the item's own name only has the title.
// Called by CreativeScreenMixin after the game fills the search results; the character's items go to the front.
public final class CreativeCharacterSearch {
    // Souls are named after the power, not the character.
    private static final Map<String, String> POWER_TO_CHARACTER = Map.of(
            "uchiha_ghost", "madara", "manipulator", "aizen", "rubber_boy", "joy_boy", "destruction_god", "bills",
            "one_punch", "saitama", "strongest", "gojo", "moon_demon", "kokushibo");

    // Items of a character that carry neither the soul nor the egg in the id.
    private static final Map<String, List<String>> ITEM_TO_CHARACTERS = Map.ofEntries(
            Map.entry("aizen_sword", List.of("aizen")), Map.entry("kokushibo_sword", List.of("kokushibo")),
            Map.entry("nanami_blade", List.of("nanami")), Map.entry("sode_no_shirayuki", List.of("rukia")),
            Map.entry("senbonzakura", List.of("byakuya")), Map.entry("miwa_sword", List.of("miwa")),
            Map.entry("nobara_hammer", List.of("nobara")), Map.entry("gunbai", List.of("madara")),
            Map.entry("kusanagi", List.of("sasuke")), Map.entry("dedo_sukuna", List.of("sukuna")),
            Map.entry("venda_gojo", List.of("gojo")), Map.entry("cabaca_gaara", List.of("gaara")),
            Map.entry("celula_hashirama", List.of("hashirama")), Map.entry("lanca_invertida", List.of("toji")),
            Map.entry("nuvem_brincalhona", List.of("goku")), Map.entry("remedio_tamayo", List.of("npc_tamayo")),
            Map.entry("pergaminho_sapo", List.of("npc_jiraiya")),
            Map.entry("sharingan", List.of("sasuke", "kakashi", "madara")));

    // Other names people type for the same character.
    private static final Map<String, String> EXTRA_NAMES = Map.of(
            "joy_boy", "Monkey D. Luffy Joy Boy", "bills", "Beerus", "freeza_total", "Frieza", "freeza", "Frieza");

    private static Map<Item, Set<String>> index;

    private CreativeCharacterSearch() {
    }

    // The character's items among the tab's searchable ones, in the creative order; empty when the query names nobody.
    public static List<ItemStack> find(String query, Collection<ItemStack> searchable) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.length() < 3 || q.startsWith("#") || q.startsWith("@")) {
            return List.of();
        }
        Set<String> wanted = new HashSet<>();
        for (Map.Entry<String, String> e : RealNames.MAP.entrySet()) {
            if (names(e.getValue(), q) || names(EXTRA_NAMES.get(e.getKey()), q)) {
                wanted.add(e.getKey());
            }
        }
        if (wanted.isEmpty()) {
            return List.of();
        }
        Map<Item, Set<String>> idx = index();
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack stack : searchable) {
            Set<String> keys = idx.get(stack.getItem());
            if (keys != null && keys.stream().anyMatch(wanted::contains)) {
                out.add(stack);
            }
        }
        return out;
    }

    // The whole name or any word of it starts with what was typed ("aiz", "sosuke aizen", "uchiha").
    private static boolean names(String name, String q) {
        if (name == null) {
            return false;
        }
        String n = name.toLowerCase(Locale.ROOT);
        if (n.startsWith(q)) {
            return true;
        }
        for (String w : n.split("[ .()]+")) {
            if (w.startsWith(q)) {
                return true;
            }
        }
        return false;
    }

    // Built once: the item registry does not change after the game loads.
    private static Map<Item, Set<String>> index() {
        if (index != null) {
            return index;
        }
        Map<Item, Set<String>> idx = new HashMap<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (!BattlegroundsMod.MODID.equals(id.getNamespace())) {
                continue;
            }
            String path = id.getPath();
            Set<String> keys = new HashSet<>();
            if (path.startsWith("soul_")) {
                String power = path.substring(5);
                keys.add(POWER_TO_CHARACTER.getOrDefault(power, power));
            } else if (path.endsWith("_spawn_egg")) {
                keys.add(path.substring(0, path.length() - "_spawn_egg".length()));
            }
            List<String> extra = ITEM_TO_CHARACTERS.get(path);
            if (extra != null) {
                keys.addAll(extra);
            }
            keys.removeIf(k -> !RealNames.MAP.containsKey(k));
            if (!keys.isEmpty()) {
                idx.put(item, keys);
            }
        }
        index = idx;
        return idx;
    }
}
