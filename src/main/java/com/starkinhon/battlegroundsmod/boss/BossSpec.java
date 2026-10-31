package com.starkinhon.battlegroundsmod.boss;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;

// How a boss fights, read from data/battlegroundsmod/souls_bosses/<boss>.json ("souls-boss/1"). The moves themselves
// are the power's skills (souls_skills/), so a boss and a player with the same soul hit exactly the same.
public record BossSpec(String boss, String poder, Integer tier, boolean dropAlma, Atributos atributos, Intro intro, Percepcao percepcao,
        Barra barra, Distancias distancias, Movimento movimento, Ritmo ritmo, Investida investida, PassoSombra passoSombra, PuloDuplo puloDuplo,
        Teleporte teleporte, Reacoes reacoes, UltFinal ultFinal,
        Map<String, Skill> skills, Map<String, Combo> combos, List<Fase> fases, String estilo) {

    // estilo: how it moves between moves. Missing or "ninja" = dashes, back dodges and double jumps (Madara);
    // "deus" = walks calmly at the target, never dodges or dashes, only blinks behind it (Bills).
    public boolean god() {
        return "deus".equals(estilo);
    }

    // tier: the power tier the boss is made for (see Tiers); its moves hit like that tier. Missing = 10 (human).
    // vida, armadura and velocidade become the entity's attributes. multDano scales every hit the boss lands.
    public record Atributos(double vida, double armadura, double velocidade, double resistenciaEmpurrao, double alcanceVisao, float multDano) {
    }

    // The entrance when it spawns: untouchable and still for duracaoTicks, with a title on nearby screens.
    public record Intro(int duracaoTicks, String titulo, String subtitulo, String cor) {
    }

    // Custom boss bar: textures/gui/bossbar/<textura>/0.png (full life) to <frames>.png (empty), drawn largura x altura
    // GUI pixels. Players can switch it off with /soul bossbar.
    // brilho: color of the light that circles the bar ("#9b30ff"), or none.
    public record Barra(String textura, int frames, int largura, int altura, String brilho) {
        public int glowColor() {
            if (brilho == null || brilho.isBlank()) {
                return -1;
            }
            try {
                return Integer.parseInt(brilho.startsWith("#") ? brilho.substring(1) : brilho, 16) & 0xFFFFFF;
            } catch (NumberFormatException e) {
                return -1;
            }
        }
    }

    // intervaloDecisao: how often a new move is picked. aleatoriedade 0 always takes the best move, 1 picks by weight.
    // trocarHumor: how often the boss rethinks whether it wants to fight up close or from range.
    // intervaloTecnicasTicks: the least time between two special techniques (anything but strong and barrage); in between
    // it fights with punches, dashes, strong and barrage. Missing = 100 (5 s).
    public record Percepcao(int intervaloDecisaoTicks, double aleatoriedade, int trocarHumorTicks, Integer intervaloTecnicasTicks) {
        public int techniqueGap() {
            return intervaloTecnicasTicks != null ? Math.max(0, intervaloTecnicasTicks) : 100;
        }
    }

    // soco: punch reach. ideal: the distance it keeps when fighting from range.
    public record Distancias(double soco, double ideal, int intervaloSocoTicks) {
    }

    public record Movimento(double velocidadeAndar, double velocidadeCorrer, int trocarLadoTicks) {
    }

    // The boss switches between walking calmly and moving insanely fast. Each mode lasts a random time between its min
    // and max; velocidade multiplies its speed in that mode. In the fast mode dashes recharge recargaInvestidaRapido.
    public record Ritmo(int calmoMinTicks, int calmoMaxTicks, int rapidoMinTicks, int rapidoMaxTicks, double velocidadeCalmo,
            double velocidadeRapido, int recargaInvestidaRapido) {
    }

    // Shadow step: a blink to atras blocks behind the target, from up to distanciaMax away. chance is checked every
    // 10 ticks (chanceCalmo in the calm mode); chanceNaEsquiva is the chance a dodge becomes a step behind them.
    public record PassoSombra(int recargaTicks, double distanciaMin, double distanciaMax, double atras, double chance, double chanceCalmo,
            double chanceNaEsquiva) {
    }

    // A forward dash to close the gap, used when the target is farther than distanciaMin.
    public record Investida(int recargaTicks, double distanciaMin, double forca, double altura) {
    }

    // Jump + air jump to reach targets standing at least alturaMin higher, up to distanciaMax away.
    public record PuloDuplo(double alturaMin, double distanciaMax) {
    }

    // If it can't reach or see its target for presoTicks, it reappears distancia blocks behind them.
    public record Teleporte(boolean ativo, int presoTicks, double distancia) {
    }

    // Chances (0 to 1) of reacting to what the target does. golpesParaContra: hits or swings within janelaPressaoTicks
    // that make it answer with the counter. After the target uses an ult, punirTicks of punishing, with combos and
    // controle moves multPunir times more likely. golpesParaFugir hits in a row make it try to escape (chanceFugirAoApanhar).
    public record Reacoes(double chanceEsquivarSkill, double chanceEsquivarProjetil, double chanceContraAtaque, double chanceEsquivarUlt,
            int golpesParaContra, int janelaPressaoTicks, int punirTicks, double multPunir, int golpesParaFugir, double chanceFugirAoApanhar) {
    }

    // The boss-only ult when it is about to die (vida = life fraction, once per fight): everyone within raio freezes,
    // the camera zooms on its face (zoomTicks) then turns to the sky (ceuTicks) as the Meteoro appears, falling
    // multQueda times slower and hitting multDano times harder. It waits still until the impact plus esperaDepoisTicks.
    public record UltFinal(boolean ativa, double vida, int zoomTicks, int ceuTicks, int voltarTicks, double multQueda, float multDano,
            double raio, int esperaDepoisTicks, String titulo, String subtitulo) {
    }

    // slot: which move of the power (0-3 = Z X C V, 4 = strong attack, 5 = barrage, 7 = the fifth skill on B).
    // tipo tells the AI what the move is for: alcance (ranged), controle (stun), contra (counter), ult, golpe (heavy hit),
    // barragem (multi-hit), hakai (Bills' V: erases a target low on life, else throws the hakai ball), anti (Bills'
// hakaizinho: shot at attacks flying at it, or to finish someone low and far), livre (used almost at random whenever it is ready and
// in range). avisoTicks: the warning before it is used. giroGrausPorTick: how fast it turns to follow the
    // target while the move plays (low = easier to dodge).
    // recargaTicks..recargaMaxTicks: the cooldown is a random value in between (no max = always recargaTicks).
    // fala: what the boss says in the chat of whoever the move catches (the neck grab), empty = nothing.
    public record Skill(int slot, String tipo, double peso, int recargaTicks, int recargaMaxTicks, int avisoTicks, double distanciaMin, double distanciaMax,
            float giroGrausPorTick, String avisoCor, String avisoSom, String fala) {
    }

    // The skill entry that uses this slot, or null.
    public Skill skillForSlot(int slot) {
        for (Skill skill : skills().values()) {
            if (skill.slot() == slot) {
                return skill;
            }
        }
        return null;
    }

    // Steps: a skill name from "skills", or soco, investida, esquiva, pulo, aproximar.
    public record Combo(double peso, List<String> passos) {
    }

    // comecaEm: the life fraction where the phase starts (1 = full life). Phases only go forward.
    // Cooldowns: x multRecarga, minus menosRecargaTicks (e.g. 80 = 4 s shorter). multVelocidade speeds up both paces.
    public record Fase(double comecaEm, List<String> skills, List<String> combos, double agressividade, double multRecarga,
            int menosRecargaTicks, double multVelocidade, double multAviso,
            int reacaoTicks, double chanceReacao, int pausaTicks, boolean despertado, String corBarra, int transicaoTicks, String titulo,
            String subtitulo) {
    }

    private static final Gson GSON = new Gson();
    private static final Map<String, BossSpec> CACHE = new HashMap<>();

    public static synchronized BossSpec get(String id) {
        return CACHE.computeIfAbsent(id, BossSpec::read);
    }

    private static BossSpec read(String id) {
        String path = "/data/" + BattlegroundsMod.MODID + "/souls_bosses/" + id + ".json";
        try (InputStream in = BossSpec.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing boss file " + path);
            }
            return GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), BossSpec.class);
        } catch (Exception e) {
            BattlegroundsMod.LOGGER.error("Could not load boss {}", id, e);
            throw new IllegalStateException("Could not load boss " + id, e);
        }
    }

    public Fase fase(int index) {
        return fases.get(Math.max(0, Math.min(index, fases.size() - 1)));
    }

    // The phase for a life fraction: the last one whose comecaEm is at or above it.
    public int faseFor(double life) {
        int index = 0;
        for (int i = 0; i < fases.size(); i++) {
            if (life <= fases.get(i).comecaEm()) {
                index = i;
            }
        }
        return index;
    }
}
