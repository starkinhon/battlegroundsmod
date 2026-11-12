package com.starkinhon.soulsdev.client;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.client.fx.SkillEffects;
import com.starkinhon.battlegroundsmod.skill.SkillSpecs;
import com.starkinhon.battlegroundsmod.skill.VfxSpec;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec3;

// /devc forja: toca a lista "vfx" do arquivo da skill (data/battlegroundsmod/souls_skills/<skill>.json, o que a Forja
// exporta) num ponto PARADO na sua frente, sem seguir ninguém. Sem momento: toca todos os momentos da skill.
// Só o que está na lista vfx: efeitos com renderer próprio (Genkidama, Getsuga...) são pelo /dev fx.
public final class ForjaFx {
    private ForjaFx() {
    }

    public static List<String> skills() {
        List<String> r = new ArrayList<>();
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) {
            return r;
        }
        String pasta = "souls_skills/";
        server.getResourceManager().listResources("souls_skills", id -> id.getPath().endsWith(".json")).keySet().forEach(id -> {
            if (id.getNamespace().equals(BattlegroundsMod.MODID)) {
                String p = id.getPath();
                r.add(p.substring(pasta.length(), p.length() - 5));
            }
        });
        r.sort(null);
        return r;
    }

    public static Set<String> momentos(String skill) {
        Set<String> r = new LinkedHashSet<>();
        try {
            VfxSpec spec = SkillSpecs.get(skill, VfxSpec.class, false);
            if (spec.vfx() != null) {
                for (VfxSpec.Efeito e : spec.vfx()) {
                    if (e != null && e.momento() != null) {
                        r.add(e.momento());
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // skill sem arquivo
        }
        return r;
    }

    public static String tocar(String skill, String momento, double dist) {
        LocalPlayer p = Minecraft.getInstance().player;
        Set<String> todos = momentos(skill);
        if (todos.isEmpty()) {
            throw new IllegalArgumentException("sem lista vfx (ou skill não existe)");
        }
        Vec3 frente = Vec3.directionFromRotation(0, p.getYRot());
        Vec3 pos = p.position().add(frente.scale(dist));
        Set<String> tocar = momento == null ? todos : Set.of(momento);
        for (String m : tocar) {
            SkillEffects.play(skill, false, m, pos, frente, -1, 100, -1);
        }
        return "forja " + skill + " " + tocar + String.format(" em %.1f %.1f %.1f", pos.x, pos.y, pos.z);
    }
}
