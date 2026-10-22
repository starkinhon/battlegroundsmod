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
public record BossSpec(String boss, String poder, boolean dropAlma, Atributos atributos, Intro intro, Percepcao percepcao,
        Distancias distancias, Movimento movimento, Investida investida, PuloDuplo puloDuplo, Teleporte teleporte, Reacoes reacoes,
        Map<String, Skill> skills, Map<String, Combo> combos, List<Fase> fases) {

    // vida, armadura and velocidade become the entity's attributes. multDano scales every hit the boss lands.
    public record Atributos(double vida, double armadura, double velocidade, double resistenciaEmpurrao, double alcanceVisao, float multDano) {
    }

    // The entrance when it spawns: untouchable and still for duracaoTicks, with a title on nearby screens.
    public record Intro(int duracaoTicks, String titulo, String subtitulo, String cor) {
    }

    // intervaloDecisao: how often a new move is picked. aleatoriedade 0 always takes the best move, 1 picks by weight.
    // trocarHumor: how often the boss rethinks whether it wants to fight up close or from range.
    public record Percepcao(int intervaloDecisaoTicks, double aleatoriedade, int trocarHumorTicks) {
    }

    // soco: punch reach. ideal: the distance it keeps when fighting from range.
    public record Distancias(double soco, double ideal, int intervaloSocoTicks) {
    }

    public record Movimento(double velocidadeAndar, double velocidadeCorrer, int trocarLadoTicks) {
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
    // controle moves multPunir times more likely.
    public record Reacoes(double chanceEsquivarSkill, double chanceEsquivarProjetil, double chanceContraAtaque, double chanceEsquivarUlt,
            int golpesParaContra, int janelaPressaoTicks, int punirTicks, double multPunir) {
    }

    // slot: which move of the power (0-3 = Z X C V, 4 = strong attack, 5 = barrage).
    // tipo tells the AI what the move is for: alcance (ranged), controle (stun), contra (counter), ult, golpe (heavy hit),
    // barragem (multi-hit). avisoTicks: the warning before it is used. giroGrausPorTick: how fast it turns to follow the
    // target while the move plays (low = easier to dodge).
    public record Skill(int slot, String tipo, double peso, int recargaTicks, int avisoTicks, double distanciaMin, double distanciaMax,
            float giroGrausPorTick, String avisoCor, String avisoSom) {
    }

    // Steps: a skill name from "skills", or soco, investida, esquiva, pulo, aproximar.
    public record Combo(double peso, List<String> passos) {
    }

    // comecaEm: the life fraction where the phase starts (1 = full life). Phases only go forward.
    public record Fase(double comecaEm, List<String> skills, List<String> combos, double agressividade, double multRecarga, double multAviso,
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
