// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.npc;

import java.util.ArrayList;
import java.util.List;

import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.universe.DbActivities;
import com.starkinhon.battlegroundsmod.universe.GuidedPath;
import com.starkinhon.battlegroundsmod.universe.MissionJournal;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.network.PacketDistributor;

// The talk scene with an NPC (approved preview, Stark 2026-11-13): right click opens it on the client (client/TalkScreen,
// the camera on the player's shoulder, the line typing under the two of them). An NPC with a mission ready (Bulma's
// capsule, Mestre Kame's training, Buu's sweet, Gohan's flight) shows its line and the mission card, and the answer
// (Aceitar / Entregar / Dar o doce) runs the NPC's own code (Npcs.talk, DbActivities) here; the other NPCs run it at once
// and the scene shows what they said. What the NPC says (their `say`) is caught here instead of going to the chat.
public final class Talks {
    private static final ThreadLocal<List<Component>> CAPTURE = new ThreadLocal<>();
    // between a question's label, its answer and whether the server answers it (OpenTalk.delivery, "perguntas")
    public static final String SEP = "\u0001";

    private Talks() {
    }

    // An NPC's line while the scene is running its code: kept for the scene (true) instead of going to the chat.
    public static boolean capture(Component line) {
        List<Component> lines = CAPTURE.get();
        if (lines == null) {
            return false;
        }
        lines.add(line);
        return true;
    }

    private static List<Component> run(Runnable talk) {
        CAPTURE.set(new ArrayList<>());
        try {
            talk.run();
            return CAPTURE.get();
        } finally {
            CAPTURE.remove();
        }
    }

    private static String json(ServerPlayer player, Component c) {
        return Component.Serializer.toJson(c, player.registryAccess());
    }

    // The mission each NPC gives: talking to it puts the pin on the Diário's map too (an NPC met away from its structure).
    private static final java.util.Map<String, String> MISSIONS = java.util.Map.of("bulma", "capsula", "mestre_kame", "kame", "boo", "buu",
            "gohan", "goku", "korin", "karin");

    public static void open(ServerPlayer player, UniverseNpc npc) {
        npc.hold(player);
        String own = MISSIONS.get(npc.npcId());
        if (own != null) {
            MissionJournal.found(player, own, npc.blockPosition());
        }
        String name = json(player, npc.getType().getDescription());
        // the Caminho's own conversation (universe/GuidedPath): the first meeting with its questions, Bulma's radar...
        GuidedPath.Talk path = GuidedPath.talk(player, npc);
        if (path != null) {
            List<String> questions = new ArrayList<>();
            for (GuidedPath.Question q : path.questions()) {
                questions.add(json(player, q.label()) + SEP + json(player, q.answer()) + SEP + (q.server() ? "1" : "0"));
            }
            PacketDistributor.sendToPlayer(player, new ModPayloads.OpenTalk(npc.getId(), npc.npcId(), name, "", json(player, path.line()), "",
                    questions.isEmpty() ? "" : "perguntas", questions, false));
            return;
        }
        String mission = DbActivities.talkMission(player, npc.npcId());
        if (mission != null) {
            String accept = switch (mission) {
                case "capsula" -> "entregar";
                case "buu" -> "doce";
                case "goku" -> "aprender";
                default -> "aceitar";
            };
            PacketDistributor.sendToPlayer(player, new ModPayloads.OpenTalk(npc.getId(), npc.npcId(), name, mission, "", "", accept, List.of(),
                    mission.equals("buu")));
            return;
        }
        List<Component> lines = new ArrayList<>(run(() -> Npcs.talk(player, npc)));
        // the Caminho's lines around the NPC's own: who you are to them, and where to go next
        Component before = GuidedPath.before(player, npc.npcId());
        Component after = GuidedPath.after(player, npc.npcId());
        if ((before != null || after != null) && lines.size() == 1 && lines.get(0).getString().equals("...")) {
            lines.clear();
        }
        if (before != null) {
            lines.add(0, before);
        }
        if (after != null) {
            lines.add(after);
        }
        if (lines.isEmpty()) {
            npc.release();
            return;
        }
        Component line = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            line = line.copy().append(i > 0 ? Component.literal(" ") : Component.empty()).append(lines.get(i));
        }
        PacketDistributor.sendToPlayer(player, new ModPayloads.OpenTalk(npc.getId(), npc.npcId(), name, "", json(player, line), "", "", List.of(), false));
    }

    // The answer in the scene: 0 accept / deliver / give the sweet (pick = the sweet's item id), 2 the scene closed.
    public static void choice(ServerPlayer player, int npcId, int choice, String pick) {
        if (!(player.level().getEntity(npcId) instanceof UniverseNpc npc) || npc.distanceToSqr(player) > 100) {
            return;
        }
        if (choice >= 10) {
            // a question of the Caminho's conversation that the server answers (GuidedPath.answer)
            Component reply = GuidedPath.answer(player, npc, choice - 10);
            boolean taken = false;
            if (npc.npcId().equals("mestre_kame")) {
                // "Me ensina a lutar": the Turtle Training starts right there (his own line is the same, not said twice)
                run(() -> Npcs.talk(player, npc));
                taken = DbActivities.talkTaken(player, "kame");
                if (taken && com.starkinhon.battlegroundsmod.world.SoulsRules.autoTrack(player)) {
                    MissionJournal.track(player, "kame");
                }
            }
            List<String> out = new ArrayList<>();
            if (reply != null) {
                out.add(json(player, reply));
            }
            PacketDistributor.sendToPlayer(player, new ModPayloads.TalkReply(out, taken));
            return;
        }
        if (choice != 0) {
            npc.release();
            return;
        }
        String mission = DbActivities.talkMission(player, npc.npcId());
        ResourceLocation key = pick == null || pick.isEmpty() ? null : ResourceLocation.tryParse(pick);
        Item sweet = key == null ? null : BuiltInRegistries.ITEM.getOptional(key).orElse(null);
        List<Component> lines = run(() -> {
            if (npc.npcId().equals("boo") && sweet != null) {
                DbActivities.boo(player, npc, sweet);
            } else {
                Npcs.talk(player, npc);
            }
        });
        boolean taken = mission != null && DbActivities.talkTaken(player, mission);
        if (taken && mission.equals("kame") && com.starkinhon.battlegroundsmod.world.SoulsRules.autoTrack(player)) {
            // the training goes on in the corner (the preview: "a missão tá no seu canto da tela")
            MissionJournal.track(player, mission);
        }
        List<String> out = new ArrayList<>();
        for (Component line : lines) {
            out.add(json(player, line));
        }
        PacketDistributor.sendToPlayer(player, new ModPayloads.TalkReply(out, taken));
    }
}
