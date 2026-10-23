package com.starkinhon.battlegroundsmod.skill;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.Gson;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;

// A power's aura (format souls-aura/1), in data/battlegroundsmod/souls_auras/<power>.json. Players show it while in
// combat mode, bosses with that power all the time. A power without a file has no aura. Only the client draws it.
//
// contorno: the glowing outline hugging the body (espessura in blocks, chamas = how far the flame tongues reach up).
// fios: wavy lines of power rising around the body. chao: glow on the floor under the feet.
// particulas: what it breathes out each tick (tipo poeira, risco, fumaca, anel), porTick can be a fraction.
// primeiraPessoa: how much is left when it is your own aura seen in first person (nothing covers the view).
// olhos: a light trail following the eyes: bosses from phase aPartirDaFase on (Madara's sharingan), anyone for
// jogadorTicks after using ativarSkill (the Mangekyo).
public record AuraSpec(String poder, String cor, String cor2, String corEscura, int fadeEntrarTicks, int fadeSairTicks, Contorno contorno,
        Fios fios, Chao chao, List<Particula> particulas, Entrada entrada, PrimeiraPessoa primeiraPessoa, Olhos olhos) {

    public record Contorno(boolean ativo, double espessura, double chamas, double brilho, double velocidade, double borda) {
    }

    public record Fios(int quantidade, double raio, double altura, double largura, double ondulacao, double velocidade, double opacidade) {
    }

    public record Chao(double raio, double opacidade, double anel) {
    }

    public record Particula(String tipo, String cor, double porTick, double tamanho, int vidaTicks, double subida, double raio, double altura) {
    }

    // The burst when it turns on: a ring running over the floor and a puff of particles.
    public record Entrada(double raioAnel, int vidaTicks, int particulas) {
    }

    public record PrimeiraPessoa(double particulas, double bordaTela) {
    }

    // ativarSkill / jogadorTicks: anyone with this power (players too) gets the trail for that long after using that skill.
    // duranteSkills: moves that light the eyes only while they run (the neck-grab dash), then fade out over sumirTicks.
    // Only one trail ever: if the eyes already trail (Mangekyo, a boss's phase), these add nothing.
    public record Olhos(int aPartirDaFase, String ativarSkill, int jogadorTicks, List<String> duranteSkills, int sumirTicks, String cor,
            String cor2, int rastroTicks, double largura, double brilho, double x, double y, double z) {
    }

    private static final Gson GSON = new Gson();
    private static final Map<String, Optional<AuraSpec>> CACHE = new HashMap<>();

    // null when the power has no aura file.
    public static synchronized AuraSpec get(String power) {
        if (power == null || power.isEmpty()) {
            return null;
        }
        return CACHE.computeIfAbsent(power, AuraSpec::read).orElse(null);
    }

    private static Optional<AuraSpec> read(String power) {
        String path = "/data/" + BattlegroundsMod.MODID + "/souls_auras/" + power + ".json";
        try (InputStream in = AuraSpec.class.getResourceAsStream(path)) {
            if (in == null) {
                return Optional.empty();
            }
            return Optional.ofNullable(GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), AuraSpec.class));
        } catch (Exception e) {
            BattlegroundsMod.LOGGER.error("Could not load aura {}", power, e);
            return Optional.empty();
        }
    }
}
