package com.starkinhon.battlegroundsmod.animal.dbmob;

import java.util.LinkedHashMap;
import java.util.Map;

import com.starkinhon.battlegroundsmod.animal.naruto.NarutoSpec.Attack;
import com.starkinhon.battlegroundsmod.animal.naruto.NarutoSpec.Spec;

// The Dragon Ball creatures as the approved preview describes them (Stark 2026-11-12, tools/mobs/dbmobs: "agora sim,
// pode botar tudo no jogo"): size, health, how they behave, and their blows. Each blow's length and hit tick are the
// preview's own (tools/mobs/dbmobs/timings.txt: the "t % N" of the mode, and the frame the target reacts on), so the
// game's blow lands on the frame the baked animation shows it. Ranges in blocks from the body's edge. Health is the
// preview's "Vida". The models' size (Cell Jr. 0.68, the dragon 1.4...) comes with the model (blocky/db).
public final class DbMobSpec {
    private static final Map<String, Spec> ALL = new LinkedHashMap<>();

    private static Spec add(String id) {
        Spec s = new Spec(id);
        ALL.put(id, s);
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

    static {
        // ---------- Terras e Deserto ----------
        add("saibaman").hp(28).speed(0.3).size(0.55f, 1.45f).xp(6).variants(8).shadow(0.45f).tp("agarrao", 19).attacks(
                Attack.of("agarrao", 84, 19, 0.3, 4, 240),
                Attack.of("acido", 68, 24, 2, 8, 110));
        add("bandido_deserto").hp(60).speed(0.3).size(0.6f, 1.85f).xp(10).variants(8).attacks(
                Attack.of("lobo", 72, 15, 0.3, 4.5, 140),
                Attack.of("areia", 64, 16, 0, 4, 220));
        add("soldado_red_ribbon").hp(60).speed(0.29).size(0.6f, 1.85f).xp(10).variants(8).keep(7).attacks(
                Attack.of("metralhadora", 68, 13, 2, 14, 120),
                Attack.of("bazuca", 88, 36, 4, 18, 240));

        // ---------- Mar do Sul e Norte ----------
        add("pirata").hp(40).speed(0.29).size(0.6f, 1.85f).xp(7).variants(8).attacks(
                Attack.of("sabre", 56, 15, 0, 3, 60),
                Attack.of("bomba", 80, 61, 3, 10, 200));
        add("yeti").hp(90).speed(0.27).size(1.2f, 2.4f).xp(20).guard(10).kb(0.5).variants(5).shadow(0.9f).attacks(
                Attack.of("rugido", 78, 21, 0, 7, 220),
                Attack.of("pisao", 76, 25, 1.5, 7, 150));

        // ---------- Dinossauros (Paozu, Terras e Deserto) ----------
        add("raptor").hp(34).speed(0.36).size(0.8f, 1.3f).xp(8).variants(6).shadow(0.6f).attacks(
                Attack.of("bote", 80, 25, 2, 10, 170),
                Attack.of("garras", 64, 12, 0, 2.6, 60));
        add("dino_chifrudo").hp(80).speed(0.27).size(1.6f, 1.7f).xp(15).neutral().kb(0.7).variants(5).shadow(1.2f).attacks(
                Attack.of("chifrada", 76, 33, 2, 10, 150),
                Attack.of("clava", 64, 20, 0, 3.5, 80));
        add("tiranossauro").hp(220).speed(0.3).size(2.4f, 3.5f).xp(50).guard(12).kb(0.9).variants(5).shadow(2f).attacks(
                Attack.of("investida", 84, 35, 3, 14, 170),
                Attack.of("pisao", 80, 49, 0, 6, 160));
        add("pterodactilo").hp(30).speed(0.5).size(1.2f, 1.0f).xp(8).guard(10).flyer().variants(5).shadow(0.8f).attacks(
                Attack.of("mergulho", 88, 25, 0, 8, 190),
                Attack.of("ventania", 76, 16, 1, 8, 210));
        // the dragon: wild it only breathes fire; decolar / voar are the ridden flight's (never the AI's)
        add("dragao").hp(200).speed(0.3).size(2.8f, 3.6f).xp(60).neutral().kb(1.0).variants(6).shadow(2.4f).attacks(
                Attack.of("fogo", 76, 27, 0.5, 8, 160),
                new Attack("decolar", 92, 0, 99, 99, 0, true),
                new Attack("voar", 160, 0, 99, 99, 0, true));

        // ---------- Namek e estruturas ----------
        add("soldado_freeza").hp(34).speed(0.3).size(0.6f, 1.85f).xp(8).variants(20).keep(6).attacks(
                Attack.of("pistola", 72, 13, 3, 14, 150),
                Attack.of("rajada", 56, 17, 2, 12, 90),
                Attack.of("bomba", 72, 43, 3, 12, 230));
        add("cell_jr").hp(60).speed(0.36).size(0.45f, 1.25f).xp(15).variants(3).shadow(0.35f).tp("sumir", 13).attacks(
                Attack.of("kame", 68, 31, 2, 14, 170),
                Attack.of("sumir", 64, 20, 1, 8, 150));
        add("yakon").hp(220).speed(0.3).size(1.3f, 2.7f).xp(50).kb(0.6).variants(2).shadow(1f).exclusive().attacks(
                Attack.of("escuro", 88, 40, 0.5, 6, 240),
                new Attack("devorar", 84, 50, 0, 8, 320, false));

        // ---------- Rios e florestas ----------
        add("tigre_sabre").hp(70).speed(0.34).size(1.2f, 1.6f).xp(14).guard(7).kb(0.4).variants(6).shadow(1f).attacks(
                Attack.of("bote", 72, 27, 2, 7, 130),
                Attack.of("patada", 68, 21, 0, 3, 90));
        add("peixe_gigante").hp(90).speed(1.0).size(2.4f, 1.4f).xp(15).neutral().water().kb(0.6).variants(6).shadow(1f).attacks(
                Attack.of("salto", 84, 35, 1.5, 9, 150),
                Attack.of("abocanhar", 76, 17, 0, 5, 130));

        // ---------- Vilões do mundo ----------
        add("androide_gero").hp(80).speed(0.28).size(0.6f, 1.9f).xp(15).variants(8).keep(5).attacks(
                Attack.of("laser", 64, 32, 2, 14, 150),
                Attack.of("canhao", 76, 33, 3, 16, 210));
        add("tao_pai_pai").hp(90).speed(0.32).size(0.6f, 2.0f).xp(30).variants(2).keep(6).attacks(
                Attack.of("dodonpa", 60, 24, 3, 18, 120),
                Attack.of("pilar", 104, 58, 5, 18, 280));
        add("spopovich").hp(180).speed(0.3).size(1.0f, 2.3f).xp(35).kb(0.7).variants(3).shadow(0.8f).attacks(
                Attack.of("abraco", 80, 16, 0, 2.5, 170),
                Attack.of("investida", 64, 26, 2.5, 9, 140));
        add("yamu").hp(90).speed(0.3).size(0.6f, 1.9f).xp(20).variants(3).keep(3).attacks(
                Attack.of("sugar", 84, 11, 0, 3, 220),
                Attack.of("rajada", 60, 27, 3, 12, 100));
        add("babidi").hp(60).speed(0.28).size(0.6f, 1.6f).xp(40).variants(2).keep(8).exclusive().attacks(
                Attack.of("paparapa", 88, 46, 2, 14, 170),
                Attack.of("controle", 100, 23, 2, 16, 320));
    }

    private DbMobSpec() {
    }
}
