package com.starkinhon.battlegroundsmod.client.cutscene;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.skill.SkillSpec;

import net.neoforged.fml.loading.FMLPaths;

// A cutscene made in the Editor de Cutscenes (format "souls-cutscene/1"). Read fresh every time the skill's cutscene
// starts, so a file dropped in config/souls_cutscenes/<skill>.json shows up in game on the next use, without a restart.
// A file shipped in the jar (data/battlegroundsmod/souls_cutscenes/<skill>.json) is used when there is none in config.
// With no file, or "ativa": false, the skill keeps its hand-made cutscene.
//
// Space of the scene: x to the left, y up, z forward, in blocks, from the feet of "origem" (the caster, or the target)
// where they stood when it started; forward is from the caster towards the target (or where the caster faced).
// Times are ticks (20 per second) from when the mod starts the skill's cutscene.
public record CutsceneData(String formato, String skill, Boolean ativa, int duracaoTicks, String origem, Boolean travarMovimento,
        Boolean esconderHud, Boolean barrasPretas, List<Trecho> trechos, List<CamKey> camera, Map<String, Actor> atores,
        List<Legenda> legendas) {

    private static final Gson GSON = new Gson();

    // A stretch of the timeline where the camera belongs to the cutscene; outside it the player sees their own view.
    // entradaTicks/saidaTicks: glide in from, and back out to, the player's own view (0 = straight cut).
    public record Trecho(String nome, double inicio, double fim, double entradaTicks, double saidaTicks) {
    }

    // rot: [yaw, pitch, roll] in degrees, in the scene's space: yaw 0 looks forward, positive turns left; pitch positive
    // looks up; roll positive tilts the picture clockwise. fov: vertical, in degrees (0 = the player's own).
    // seguir: "usuario"/"alvo" carries the point along with that character as they move ("" or "cena" = fixed).
    // mirar: "usuario"/"alvo" points the camera at that character, mirarAltura blocks above their feet (rot's yaw and
    // pitch are then ignored). suave: how it travels to the next key, "suave", "linear", "entrada", "saida" or "corte".
    public record CamKey(double t, double[] pos, double[] rot, double fov, String seguir, String mirar, Double mirarAltura,
            String suave) {
    }

    // modo "somar": the pose is added on top of the skill's own; "substituir": the skill's pose is replaced while the
    // keys last. Offsets and poses only apply between the first and last key.
    public record Actor(String modo, List<ActorKey> chaves) {
    }

    // pos: offset from where the character really is, in the scene's space; giro: turn, degrees, positive to the left.
    public record ActorKey(double t, double[] pos, double giro, SkillSpec.Pose pose, String suave) {
    }

    public record Legenda(String texto, double inicio, double fim, String cor, double tamanho) {
    }

    public boolean on() {
        return ativa == null || ativa;
    }

    public boolean lock() {
        return travarMovimento == null || travarMovimento;
    }

    public boolean hideHud() {
        return esconderHud == null || esconderHud;
    }

    public boolean bars() {
        return barrasPretas == null || barrasPretas;
    }

    public boolean fromTarget() {
        return "alvo".equals(origem);
    }

    public List<Trecho> segments() {
        return trechos == null ? List.of() : trechos;
    }

    public List<CamKey> cameraKeys() {
        return camera == null ? List.of() : camera;
    }

    public Actor actor(String name) {
        return atores == null ? null : atores.get(name);
    }

    // The time the cutscene is over: the end of its last stretch, or duracaoTicks, whichever is later.
    public double end() {
        double end = duracaoTicks;
        for (Trecho trecho : segments()) {
            end = Math.max(end, trecho.fim() + Math.max(0, trecho.saidaTicks()));
        }
        return end;
    }

    public static Path configFile(String skill) {
        return FMLPaths.CONFIGDIR.get().resolve("souls_cutscenes").resolve(skill + ".json");
    }

    // The skill's cutscene, or null when it has none. Throws with a readable message when the file is broken.
    public static CutsceneData load(String skill) throws Exception {
        CutsceneData data = null;
        Path file = configFile(skill);
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                data = GSON.fromJson(reader, CutsceneData.class);
            }
        } else {
            String path = "/data/" + BattlegroundsMod.MODID + "/souls_cutscenes/" + skill + ".json";
            try (InputStream in = CutsceneData.class.getResourceAsStream(path)) {
                if (in != null) {
                    data = GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), CutsceneData.class);
                }
            }
        }
        return data == null ? null : data.sorted();
    }

    // Keys in time order, whatever order the file has them in.
    private CutsceneData sorted() {
        List<CamKey> cam = new ArrayList<>(cameraKeys());
        cam.removeIf(k -> k == null || k.pos() == null || k.pos().length < 3);
        cam.sort(Comparator.comparingDouble(CamKey::t));
        Map<String, Actor> actors = atores == null ? Map.of() : new java.util.HashMap<>();
        if (atores != null) {
            atores.forEach((name, actor) -> {
                if (actor != null && actor.chaves() != null) {
                    List<ActorKey> keys = new ArrayList<>(actor.chaves());
                    keys.removeIf(k -> k == null);
                    keys.sort(Comparator.comparingDouble(ActorKey::t));
                    actors.put(name, new Actor(actor.modo(), keys));
                }
            });
        }
        return new CutsceneData(formato, skill, ativa, duracaoTicks, origem, travarMovimento, esconderHud, barrasPretas, trechos, cam,
                actors, legendas);
    }

    static void warn(String skill, Exception e) {
        BattlegroundsMod.LOGGER.error("Could not read the cutscene of {}", skill, e);
    }
}
