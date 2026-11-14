package com.starkinhon.battlegroundsmod.animal.jjmob;

import java.util.LinkedHashMap;
import java.util.Map;

import com.starkinhon.battlegroundsmod.animal.naruto.NarutoSpec.Attack;
import com.starkinhon.battlegroundsmod.animal.naruto.NarutoSpec.Spec;

// The Jujutsu biomes' curses (and the Colônia's people) as the approved preview describes them (Stark 2026-11-14,
// tools/mobs/jjmobs: "porta"): size, health, how they behave, and their blows. Each blow's length is the preview's
// "t % N" of the mode (tools/mobs/jjmobs/timings.txt); the hit tick is where the preview shows it land (its shake,
// flash or claw), so the game's blow lands on the frame the baked animation shows it. Ranges in blocks from the body's
// edge; the preview's target stood at refStart, so the bands hold that distance for the blows that walk a fixed path.
// Health is the preview's "Vida". The model's drawn size comes with the model (blocky/jj).
public final class JjMobSpec {
    private static final Map<String, Spec> ALL = new LinkedHashMap<>();

    private static Spec add(String id) {
        Spec s = new Spec("jj_" + id);
        ALL.put(s.id, s);
        return s;
    }

    public static Spec get(String id) {
        return ALL.get(id);
    }

    public static Iterable<Spec> all() {
        return ALL.values();
    }

    public static boolean has(String id) {
        return ALL.containsKey(id);
    }

    // the curses' tiers, from the preview's labels (Grau 4 = tier 9-10 ... Grau especial = 4); the Kogane never fights
    private static final Map<String, Integer> TIER = Map.ofEntries(
            Map.entry("mosca", 10), Map.entry("cabecao", 8), Map.entry("transfigurado", 8), Map.entry("metro", 5),
            Map.entry("rastejante", 4), Map.entry("verme", 9), Map.entry("olhos", 7), Map.entry("utero", 5),
            Map.entry("boneco", 9), Map.entry("kitsune", 8), Map.entry("sino", 7),
            Map.entry("espantalho", 9), Map.entry("poco", 6), Map.entry("kuchisake", 6),
            Map.entry("peixe", 10), Map.entry("caranguejo", 8), Map.entry("embriao", 7), Map.entry("afogado", 6),
            Map.entry("broto", 9), Map.entry("javali", 7), Map.entry("guardiao", 5),
            Map.entry("inseto", 10), Map.entry("vulcaozinho", 7), Map.entry("golem", 5),
            Map.entry("lanterna", 9), Map.entry("mechamaru", 9), Map.entry("oni", 6),
            Map.entry("larva", 8), Map.entry("abate", 7), Map.entry("solta", 5));
    // the tiers' health (power/Tiers' defaults; read here before the tier file loads)
    private static final double[] TIER_HEALTH = {400, 300, 220, 165, 120, 90, 66, 49, 36, 27, 20};

    static {
        // ---------- Shibuya ----------
        add("mosca").hp(6).speed(0.32).size(0.5f, 0.45f).xp(2).flyer().variants(6).shadow(0.25f).attacks(
                Attack.of("mordida", 56, 20, 0, 2.2, 50),
                Attack.of("cara", 88, 17, 0, 1.6, 220));
        add("cabecao").hp(60).speed(0.28).size(1.2f, 2.5f).xp(14).kb(0.5).variants(6).shadow(0.9f).attacks(
                Attack.of("agarrao", 92, 22, 2, 5.5, 220),
                Attack.of("bote", 76, 29, 1.2, 4.5, 140));
        add("transfigurado").hp(40).speed(0.3).size(0.6f, 1.9f).xp(8).variants(8).attacks(
                Attack.of("investida", 68, 34, 1.5, 5, 120),
                Attack.of("abraco", 80, 14, 0, 1.4, 200));
        add("metro").hp(220).speed(0.3).size(2.4f, 1.8f).xp(50).kb(1.0).variants(3).shadow(2.4f).exclusive().attacks(
                Attack.of("atropelar", 84, 31, 2, 9, 160),
                Attack.of("portas", 104, 22, 0, 4, 300));

        // ---------- Floresta Negra ----------
        add("rastejante").hp(900).speed(0.26).size(3.5f, 4.0f).xp(150).kb(1.0).variants(2).shadow(3f).exclusive().attacks(
                Attack.of("maos", 112, 19, 0, 10, 200),
                Attack.of("carrossel", 100, 48, 0, 9, 220));
        add("verme").hp(14).speed(0.34).size(0.6f, 0.5f).xp(4).variants(5).shadow(0.4f).attacks(
                Attack.of("engolir", 88, 18, 0, 2.2, 300),
                Attack.of("enrolar", 100, 18, 0, 1.6, 240));
        add("olhos").hp(110).speed(0.2).size(1.0f, 3.0f).xp(20).kb(0.8).variants(5).shadow(0.8f).attacks(
                Attack.of("raizes", 92, 25, 2, 9, 140),
                Attack.of("olhar", 96, 20, 0, 10, 260));
        add("utero").hp(260).speed(0.2).size(2.0f, 2.3f).xp(40).kb(0.8).variants(3).shadow(1.6f).exclusive().keep(7).attacks(
                Attack.of("parir", 100, 42, 0, 16, 240),
                Attack.of("cordao", 100, 14, 1, 6, 180));

        // ---------- Templos Antigos ----------
        add("boneco").hp(16).speed(0.3).size(0.6f, 1.1f).xp(4).neutral().variants(3).shadow(0.4f).attacks(
                Attack.of("combo", 60, 7, 0, 1.5, 50),
                Attack.of("despertador", 100, 68, 0, 1.8, 300));
        add("kitsune").hp(24).speed(0.34).size(0.7f, 0.8f).xp(6).variants(5).keep(6).shadow(0.45f).attacks(
                Attack.of("fogo", 88, 41, 2, 10, 140),
                Attack.of("ilusao", 100, 12, 0, 5, 300));
        add("sino").hp(60).speed(0.22).size(1.0f, 2.0f).xp(12).guard(6).kb(0.6).variants(3).shadow(0.7f).attacks(
                Attack.of("badalada", 80, 28, 0, 6, 120),
                Attack.of("prender", 112, 20, 0, 3.2, 240));

        // ---------- Aldeia Amaldiçoada ----------
        add("espantalho").hp(18).speed(0.26).size(0.8f, 2.0f).xp(5).variants(5).shadow(0.5f).attacks(
                Attack.of("revoada", 92, 30, 0, 6, 200),
                Attack.of("abraco", 80, 16, 0, 1.4, 160));
        add("poco").hp(45).speed(0.26).size(0.6f, 2.0f).xp(12).variants(4).shadow(0.5f).attacks(
                Attack.of("pratos", 128, 18, 2, 10, 160),
                Attack.of("puxar", 112, 24, 1, 7, 260));
        add("kuchisake").hp(50).speed(0.3).size(0.6f, 2.2f).xp(12).variants(4).attacks(
                Attack.of("pergunta", 124, 33, 0, 14, 300),
                Attack.of("tesouradas", 72, 6, 0, 2.2, 70));

        // ---------- Mar de Dagon ----------
        add("peixe").hp(6).speed(0.3).size(0.5f, 0.4f).xp(2).flyer().variants(9).shadow(0.25f).attacks(
                Attack.of("mordida", 64, 16, 0, 2, 50),
                Attack.of("cardume", 128, 31, 0, 3, 200));
        add("caranguejo").hp(40).speed(0.3).size(1.3f, 0.9f).xp(8).kb(0.4).variants(5).shadow(0.8f).attacks(
                Attack.of("pincar", 92, 35, 0, 1.6, 160),
                Attack.of("cuspe", 96, 33, 1.5, 7, 140));
        add("embriao").hp(55).speed(0.26).size(0.8f, 1.6f).xp(12).variants(4).shadow(0.55f).attacks(
                Attack.of("cardume", 112, 40, 0, 8, 260),
                Attack.of("mare", 92, 32, 1.5, 7, 140));
        add("afogado").hp(70).speed(0.22).size(0.9f, 2.2f).xp(14).kb(0.85).variants(5).shadow(0.6f).attacks(
                Attack.of("abraco", 120, 22, 0, 1.4, 260),
                Attack.of("vomito", 96, 28, 0.5, 5, 150));

        // ---------- Floresta de Hanami ----------
        add("broto").hp(16).speed(0.3).size(0.7f, 1.0f).xp(4).variants(6).shadow(0.4f).attacks(
                Attack.of("sementes", 96, 30, 1.5, 7, 140),
                Attack.of("enraizar", 92, 39, 1.5, 4, 180));
        add("javali").hp(65).speed(0.3).size(1.2f, 1.3f).xp(12).kb(0.5).variants(5).shadow(0.9f).attacks(
                Attack.of("investida", 88, 38, 2, 7, 140),
                Attack.of("raizes", 92, 26, 2, 9, 160));
        add("guardiao").hp(140).speed(0.22).size(1.4f, 3.4f).xp(30).kb(0.7).variants(3).shadow(1.1f).attacks(
                Attack.of("flores", 128, 42, 0, 6, 320),
                Attack.of("estacas", 96, 30, 1, 9, 160));

        // ---------- Terras do Jogo ----------
        add("inseto").hp(8).speed(0.34).size(0.6f, 0.45f).xp(3).flyer().variants(6).shadow(0.3f).attacks(
                Attack.of("picada", 64, 23, 0, 2, 60),
                Attack.of("estouro", 88, 42, 0, 5, 300));
        add("vulcaozinho").hp(60).speed(0.22).size(1.0f, 1.5f).xp(12).variants(5).shadow(0.7f).attacks(
                Attack.of("erupcao", 108, 24, 2, 9, 180),
                Attack.of("cinza", 100, 30, 0, 5, 200));
        add("golem").hp(180).speed(0.2).size(1.8f, 3.0f).xp(35).kb(0.9).variants(3).shadow(1.4f).attacks(
                Attack.of("punho", 112, 34, 0, 9, 180),
                Attack.of("colunas", 112, 26, 2, 11, 200));

        // ---------- Outono de Kyoto ----------
        add("lanterna").hp(14).speed(0.3).size(0.6f, 1.0f).xp(4).flyer().variants(3).shadow(0.35f).attacks(
                Attack.of("lingua", 88, 23, 0.5, 3.5, 160),
                Attack.of("onibi", 104, 58, 2, 9, 160));
        add("mechamaru").hp(24).speed(0.3).size(0.6f, 1.4f).xp(6).variants(3).attacks(
                Attack.of("canhao", 96, 39, 1.5, 7, 140),
                Attack.of("espada", 84, 22, 1, 4, 100));
        add("oni").hp(110).speed(0.28).size(1.2f, 2.8f).xp(25).kb(0.7).variants(3).shadow(1f).attacks(
                Attack.of("porrada", 92, 32, 0, 4, 120),
                Attack.of("agarrao", 100, 18, 0, 2, 220));

        // ---------- Colônia do Jogo do Abate ----------
        add("larva").hp(30).speed(0.32).size(0.8f, 0.5f).xp(6).variants(5).shadow(0.5f).attacks(
                Attack.of("bote", 80, 26, 1.5, 4.5, 140),
                Attack.of("casulo", 100, 30, 1, 6, 220));
        add("abate").hp(50).speed(0.3).size(0.6f, 1.95f).xp(10).variants(9).attacks(
                Attack.of("corte", 76, 19, 0, 3, 70),
                Attack.of("energia", 80, 29, 0.5, 4, 150));
        add("solta").hp(150).speed(0.32).size(1.6f, 2.4f).xp(35).kb(0.6).variants(4).shadow(1.2f).attacks(
                Attack.of("garras", 92, 20, 0, 2.6, 120),
                Attack.of("grito", 100, 28, 0, 8, 260));
        // the Kogane: never fights; her modes are played by what happens (JjKogane)
        add("kogane").hp(20).speed(0.34).size(0.45f, 0.6f).xp(0).neutral().flyer().variants(5).shadow(0.2f).attacks(
                new Attack("oi", 88, 0, 99, 99, 0, true),
                new Attack("anuncio", 100, 0, 99, 99, 0, true),
                new Attack("pontos", 84, 0, 99, 99, 0, true),
                new Attack("some", 68, 0, 99, 99, 0, true));

        // Stark 2026-11-14 (the first test): "too slow, too easy to kill, the high tiers most of all". The preview's Vida
        // and speed are the base; each curse's tier (the preview's grade label) makes it tougher and quicker, and its
        // blows hit as hard as that tier's (JjMobMoves.power).
        for (Spec s : ALL.values()) {
            int tier = tier(s.id);
            s.health = Math.round(s.health * (float) Math.pow(TIER_HEALTH[tier] / TIER_HEALTH[10], 0.6));
            s.speed *= 1.15 + 0.04 * (10 - tier);
        }
    }

    public static int tier(String id) {
        Integer t = TIER.get(id.startsWith("jj_") ? id.substring(3) : id);
        return t == null ? 10 : t;
    }

    // how much harder than a plain creature (Tier 10) its blows land: its tier's health over a human's
    public static float power(String id) {
        return (float) (TIER_HEALTH[tier(id)] / TIER_HEALTH[10]);
    }

    private JjMobSpec() {
    }
}
