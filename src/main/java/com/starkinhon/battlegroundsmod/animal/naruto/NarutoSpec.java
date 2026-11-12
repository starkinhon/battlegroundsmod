package com.starkinhon.battlegroundsmod.animal.naruto;

import java.util.LinkedHashMap;
import java.util.Map;

// The Naruto biomes' creatures as the approved preview describes them (Stark 2026-11-12, tools/mobs/naruto: "tá
// perfeito, só caprichar no spawn"): size, health, how they behave, and their two blows. Each blow's length and hit
// tick are the preview's own (timings.txt: the "t % N" of the mode, and the frame the target reacts on), so the game's
// blow lands on the same frame the baked animation shows it. Ranges in blocks from the body's edge.
public final class NarutoSpec {
    public enum Behavior {
        // attacks players on sight
        HOSTILE,
        // only fights back (and the pack with it)
        NEUTRAL,
        // neutral, but attacks whoever comes within guard blocks
        TERRITORIAL
    }

    public enum Move {
        GROUND,
        FLYER,
        WATER
    }

    // A blow: the preview's mode, its length and hit tick (ticks from the start, 1 = the first), the distance band it
    // is used from, and its cooldown. reactive = only as an answer to a blow taken (the renegade's Substituição).
    public record Attack(String mode, int length, int impact, double min, double max, int cooldown, boolean reactive) {
        public static Attack of(String mode, int frames, int react, double min, double max, int cooldown) {
            return new Attack(mode, frames, react + 1, min, max, cooldown, false);
        }
    }

    public static final class Spec {
        public final String id;
        public float health = 40;
        public double speed = 0.28;
        public float width = 0.6f;
        public float height = 1.8f;
        public int xp = 8;
        public double knockback;
        public Behavior behavior = Behavior.HOSTILE;
        public double guard = 4;
        public Move move = Move.GROUND;
        public Attack[] attacks = new Attack[0];
        public int variants = 1;
        // distance the creature keeps from its target when it fights from afar (0 = closes in)
        public double keep;
        // the frame the body teleports on in a blow that hides it (later frames show it where the entity really is)
        public final Map<String, Integer> teleport = new LinkedHashMap<>();
        // per variant hitbox scale (the ninken breeds)
        public float[] scale;
        public float shadow = 0.5f;
        public boolean exclusive;

        public Spec(String id) {
            this.id = id;
        }

        public Attack attack(int kind) {
            return kind >= 0 && kind < attacks.length ? attacks[kind] : null;
        }

        public int kindOf(String mode) {
            for (int i = 0; i < attacks.length; i++) {
                if (attacks[i].mode().equals(mode)) {
                    return i;
                }
            }
            return -1;
        }

        public int teleportFrame(String mode) {
            Integer f = teleport.get(mode);
            return f == null ? -1 : f;
        }

        public Spec hp(float v) {
            health = v;
            return this;
        }

        public Spec speed(double v) {
            speed = v;
            return this;
        }

        public Spec size(float w, float h) {
            width = w;
            height = h;
            return this;
        }

        public Spec xp(int v) {
            xp = v;
            return this;
        }

        public Spec kb(double v) {
            knockback = v;
            return this;
        }

        public Spec neutral() {
            behavior = Behavior.NEUTRAL;
            return this;
        }

        public Spec guard(double r) {
            behavior = Behavior.TERRITORIAL;
            guard = r;
            return this;
        }

        public Spec flyer() {
            move = Move.FLYER;
            return this;
        }

        public Spec water() {
            move = Move.WATER;
            return this;
        }

        public Spec variants(int n) {
            variants = n;
            return this;
        }

        public Spec keep(double d) {
            keep = d;
            return this;
        }

        public Spec tp(String mode, int frame) {
            teleport.put(mode, frame);
            return this;
        }

        public Spec shadow(float s) {
            shadow = s;
            return this;
        }

        public Spec exclusive() {
            exclusive = true;
            return this;
        }

        public Spec attacks(Attack... a) {
            attacks = a;
            return this;
        }
    }

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

    static {
        // ---------- Floresta da Folha ----------
        add("ninja_renegado").hp(70).speed(0.31).size(0.6f, 1.85f).xp(10).variants(9).tp("substituicao", 16).attacks(
                new Attack("substituicao", 68, 33, 0, 3.2, 240, true),
                Attack.of("papelbomba", 78, 47, 1.5, 8, 110));
        Spec ninken = add("ninken").hp(24).speed(0.36).size(0.7f, 0.95f).xp(5).neutral().variants(4).shadow(0.4f).attacks(
                Attack.of("pinca", 64, 15, 0, 2.6, 50),
                Attack.of("gatsuga", 56, 15, 2.2, 7.5, 120));
        // Pug, Shiba, Wolf, Mastiff (the preview's heights 0.89 / 1.09 / 1.32 / 1.35)
        ninken.scale = new float[] {0.75f, 0.9f, 1.1f, 1.15f};

        // ---------- Floresta da Morte ----------
        add("cobra_gigante").hp(300).speed(0.26).size(1.6f, 1.0f).xp(60).kb(0.9).shadow(1.4f).exclusive().attacks(
                Attack.of("bote", 64, 21, 2.5, 10, 70),
                Attack.of("engolir", 124, 15, 0, 7, 260));
        add("tigre").hp(110).speed(0.33).size(1.3f, 1.4f).xp(18).kb(0.5).shadow(0.9f).attacks(
                Attack.of("salto", 60, 24, 2.5, 6.5, 70),
                Attack.of("rugido", 64, 17, 0, 9, 320));
        add("ninja_som").hp(120).speed(0.3).size(0.6f, 1.85f).xp(18).variants(3).keep(4.5).attacks(
                Attack.of("onda", 60, 20, 2.5, 8, 130),
                Attack.of("rajada", 64, 16, 0, 4.5, 90));
        add("centopeia").hp(70).speed(0.34).size(1.1f, 0.45f).xp(10).shadow(0.7f).attacks(
                Attack.of("picada", 52, 18, 0, 2.6, 50),
                Attack.of("enrolar", 72, 15, 0.6, 3.5, 170));

        // ---------- Deserto do Vento ----------
        add("ninja_leque").hp(120).speed(0.3).size(0.6f, 1.85f).xp(18).variants(3).keep(5.5).attacks(
                Attack.of("kamaitachi", 72, 27, 2.5, 9, 90),
                Attack.of("fechado", 60, 17, 0, 2.6, 60));
        add("escorpiao_areia").hp(60).speed(0.3).size(1.0f, 0.5f).xp(10).shadow(0.6f).attacks(
                Attack.of("emboscada", 68, 15, 0, 2.6, 200),
                Attack.of("ferroada", 72, 17, 0, 2.2, 60));
        add("marionete").hp(100).speed(0.32).size(0.8f, 2.2f).xp(20).kb(0.4).variants(2).attacks(
                Attack.of("laminas", 72, 22, 0, 3.2, 60),
                Attack.of("jaula", 88, 20, 0, 3.2, 200),
                new Attack("agulhas", 60, 0, 99, 99, 0, true));
        add("marionetista").hp(90).speed(0.3).size(0.6f, 1.85f).xp(25).variants(2).attacks(
                new Attack("laminas", 72, 0, 99, 99, 0, true),
                new Attack("jaula", 88, 0, 99, 99, 0, true),
                Attack.of("agulhas", 60, 14, 0, 3.2, 80));

        // ---------- Mar da Névoa ----------
        add("espadachim_nevoa").hp(140).speed(0.31).size(0.6f, 1.85f).xp(25).variants(3).tp("silencioso", 24).attacks(
                Attack.of("silencioso", 88, 54, 1.5, 10, 140),
                Attack.of("clones", 92, 47, 2, 10, 300));
        add("tubarao_agua").hp(110).speed(1.1).size(1.2f, 0.9f).xp(18).water().kb(0.5).shadow(0.8f).attacks(
                Attack.of("salto", 76, 32, 1.5, 6, 70),
                Attack.of("torpedo", 68, 22, 2, 7, 120));
        add("caranguejo_gigante").hp(40).speed(0.27).size(1.3f, 0.7f).xp(6).neutral().kb(0.4).shadow(0.7f).attacks(
                Attack.of("pinca", 60, 16, 0, 2.0, 40),
                Attack.of("bolhas", 64, 24, 0.5, 4, 130));
        add("cacador_nevoa").hp(110).speed(0.33).size(0.6f, 1.8f).xp(20).variants(3).attacks(
                Attack.of("prisao", 92, 14, 0, 2.0, 300),
                Attack.of("senbon", 60, 14, 1.5, 6.5, 70));

        // ---------- Vila da Chuva ----------
        add("ninja_chuva").hp(120).speed(0.3).size(0.6f, 1.85f).xp(18).variants(8).keep(4).attacks(
                Attack.of("senbon", 84, 35, 1.5, 7, 150),
                Attack.of("corrente", 72, 26, 2.5, 6.5, 110));
        add("garca_papel").hp(20).speed(0.5).size(0.7f, 0.6f).xp(4).guard(5).flyer().shadow(0.3f).tp("mergulho", 27).attacks(
                Attack.of("mergulho", 72, 26, 0, 5, 160),
                Attack.of("corte", 60, 16, 0, 3, 50));
        add("salamandra_venenosa").hp(420).speed(0.24).size(1.6f, 1.2f).xp(70).kb(0.9).shadow(1.4f).exclusive().attacks(
                Attack.of("gas", 100, 30, 0, 7, 200),
                Attack.of("rabada", 68, 17, 0, 3.5, 60));

        // ---------- Vale do Fim ----------
        add("zetsu_branco").hp(110).speed(0.3).size(0.6f, 1.85f).xp(18).variants(2).tp("agarrao", 10).attacks(
                Attack.of("agarrao", 80, 25, 1, 6, 160),
                Attack.of("esporos", 88, 22, 0.5, 4, 200));
        add("corvo_uchiha").hp(15).speed(0.3).size(0.5f, 0.55f).xp(4).guard(4).shadow(0.25f).tp("revoada", 12).attacks(
                Attack.of("genjutsu", 88, 20, 0, 6, 160),
                Attack.of("revoada", 72, 21, 0.5, 6, 140));
        add("clone_madeira").hp(180).speed(0.29).size(0.6f, 1.95f).xp(25).guard(10).variants(3).kb(0.3).attacks(
                Attack.of("estacas", 76, 19, 1.5, 6, 100),
                Attack.of("prisao", 92, 33, 0, 5, 240));

        // ---------- Monte Myoboku ----------
        add("sapo_ninja").hp(25).speed(0.3).size(0.7f, 0.7f).xp(5).neutral().variants(10).shadow(0.4f).attacks(
                Attack.of("bala", 64, 22, 1.5, 6, 80),
                Attack.of("lingua", 60, 12, 0.5, 4, 100));
        add("sapo_gigante").hp(650).speed(0.27).size(3.4f, 3.6f).xp(120).neutral().kb(1.0).shadow(2.2f).exclusive().keep(5).attacks(
                Attack.of("salto", 88, 37, 2.5, 9, 150),
                Attack.of("canhao", 100, 38, 4, 13, 190));
        add("inseto_gigante").hp(30).speed(0.3).size(0.9f, 0.75f).xp(5).guard(3).variants(3).shadow(0.5f).attacks(
                Attack.of("chifrada", 68, 25, 0, 3.2, 70),
                Attack.of("voo", 76, 35, 1.5, 6, 140));
    }

    private NarutoSpec() {
    }
}
