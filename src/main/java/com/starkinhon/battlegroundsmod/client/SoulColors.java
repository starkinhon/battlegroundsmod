// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client;

import java.util.HashMap;
import java.util.Map;

import com.starkinhon.battlegroundsmod.power.Power;

// The interface color of each soul (Stark 2026-11-21, approved in the web preview "Interface sem cristais"): one hand
// picked hue and saturation per soul that fits the character, used by the inventory, hotbar, ult bar, skill HUD and the
// Soul Book. Power effects keep Power.color. A soul missing here falls back to the hue of its icon (inventory) and its
// Power.color (HUD and book).
public final class SoulColors {
    private record Spec(float hue, float sat) {
    }

    private static final Map<String, Spec> TABLE = new HashMap<>();

    static {
        put("strongest", 195, .85f); // Gojo: Azul dos Seis Olhos
        put("rukia", 188, .30f); // Rukia: Gelo da Sode no Shirayuki
        put("miwa", 205, .42f); // Miwa: Azul da katana
        put("kakashi", 212, .12f); // Kakashi: Prata do Raikiri
        put("vegeta", 226, .72f); // Vegeta: Azul real saiyajin
        put("uchiha_ghost", 358, .85f); // Madara: Vermelho da armadura
        put("inumaki", 258, .20f); // Inumaki: Pérola lilás
        put("sasuke", 268, .62f); // Sasuke: Roxo do Susanoo
        put("manipulator", 278, .28f); // Aizen: Violeta do Hōgyoku
        put("moon_demon", 288, .58f); // Kokushibo: Roxo da Lua
        put("freeza", 300, .35f); // Freeza: Lavanda do imperador
        put("destruction_god", 306, .70f); // Bills: Magenta do Hakai
        put("ino", 318, .62f); // Ino: Rosa da floricultura
        put("byakuya", 330, .42f); // Byakuya: Sakura do Senbonzakura
        put("sukuna", 349, .72f); // Sukuna: Carmesim do Santuário
        put("choso", 4, .36f); // Choso: Sangue
        put("itadori", 12, .72f); // Itadori: Coral do Black Flash
        put("nobara", 18, .55f); // Nobara: Cobre dos pregos
        put("shigaraki", 20, .12f); // Shigaraki: Cinzas do Decay
        put("todo", 24, .38f); // Todo: Marrom
        put("goku", 27, .82f); // Goku: Laranja do gi
        put("gaara", 34, .42f); // Gaara: Areia
        put("naruto", 38, .90f); // Naruto: Chakra da Kurama
        put("makima", 341, .52f); // Makima: Rosa avermelhado do cabelo
        put("rubber_boy", 42, .20f); // Luffy: Branco do Nika
        put("one_punch", 50, .06f); // Saitama: Careca (branco)
        put("kuririn", 52, .78f); // Kuririn: Amarelo do Kienzan
        put("nanami", 60, .28f); // Nanami: Cáqui do terno
        put("shikamaru", 80, .30f); // Shikamaru: Oliva das sombras
        put("piccolo", 98, .60f); // Piccolo: Verde namekuseijin
        put("kame", 168, .50f); // Mestre Kame: Verde-mar do casco
    }

    private SoulColors() {
    }

    private static void put(String id, float hue, float sat) {
        TABLE.put(id, new Spec(hue, sat));
    }

    public static boolean has(String id) {
        return id != null && TABLE.containsKey(id);
    }

    public static float hue(String id) {
        return TABLE.get(id).hue();
    }

    // The seven shades, dark to light, with the saturation of the soul (the base ramp is tuned for 0.55).
    public static int[] ramp(String id) {
        Spec s = TABLE.get(id);
        float h = s.hue();
        float k = s.sat() / 0.55f;
        float toward = (h > 200 && h < 300) ? 1 : -1;
        return new int[] { CrystalInventory.hsl(h + 14 * toward, sat(0.45f, k), 0.07f), CrystalInventory.hsl(h + 10 * toward, sat(0.5f, k), 0.16f),
                CrystalInventory.hsl(h + 6 * toward, sat(0.5f, k), 0.27f), CrystalInventory.hsl(h, sat(0.5f, k), 0.40f),
                CrystalInventory.hsl(h - 6 * toward, sat(0.55f, k), 0.56f), CrystalInventory.hsl(h - 12 * toward, sat(0.65f, k), 0.73f),
                CrystalInventory.hsl(h - 18 * toward, sat(0.7f, k), 0.89f) };
    }

    private static float sat(float base, float k) {
        return Math.min(1, base * k);
    }

    // The soul's color at full strength, for glows that must pop out of a busy background (the halo behind each soul
    // in the Soul Book, Stark 2026-11-22). Pale souls glow near white.
    public static int vivid(Power power) {
        if (power == null) {
            return 0xFFFFFF;
        }
        Spec s = TABLE.get(power.id());
        if (s == null) {
            return power.color() & 0xFFFFFF;
        }
        float sat = Math.min(1, s.sat() * 1.45f + 0.1f);
        float light = 0.55f + (1 - s.sat()) * 0.25f;
        return CrystalInventory.hsl(s.hue(), sat, light) & 0xFFFFFF;
    }

    // The Soul Book's nebula color (Stark 2026-11-22: the sky behind the souls as lively as the palette). Always
    // saturated, also for the pale souls: Saitama's sky goes yellow, Kakashi's blue, Luffy's gold.
    public static int sky(Power power) {
        if (power == null) {
            return 0x9B30FF;
        }
        Spec s = TABLE.get(power.id());
        if (s == null) {
            return power.color() & 0xFFFFFF;
        }
        float sat = Math.max(0.68f, Math.min(1, s.sat() * 1.45f + 0.1f));
        return CrystalInventory.hsl(s.hue(), sat, 0.5f) & 0xFFFFFF;
    }

    // One bright RGB color for the skill HUD and the book: vivid souls stay vivid, pale ones (Saitama, Kakashi) go
    // toward white instead of gray.
    public static int of(Power power) {
        if (power == null) {
            return 0xFFFFFF;
        }
        Spec s = TABLE.get(power.id());
        if (s == null) {
            return power.color() & 0xFFFFFF;
        }
        float sat = Math.min(1, s.sat() * 1.2f + 0.05f);
        float light = 0.6f + (1 - s.sat()) * 0.22f;
        return CrystalInventory.hsl(s.hue(), sat, light) & 0xFFFFFF;
    }
}
