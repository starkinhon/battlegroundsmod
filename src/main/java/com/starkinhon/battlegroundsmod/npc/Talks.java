package com.starkinhon.battlegroundsmod.npc;

import java.util.ArrayList;
import java.util.List;

import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.universe.DbActivities;
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
        List<Component> lines = run(() -> Npcs.talk(player, npc));
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
        if (taken && mission.equals("kame")) {
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
