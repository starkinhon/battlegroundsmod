package com.starkinhon.battlegroundsmod.client;

import java.util.List;

// The Caminho on the client (universe/GuidedPath, ModPayloads.PathSync): the path the corner shows, each path's step and
// who the corner sends the player to (the gold diamond over that NPC, client/NpcRenderer).
public final class PathClient {
    public static final List<String> UNIS = List.of("dragonball", "naruto", "jujutsu");
    public static final int STEPS = 7;
    public static String active = "";
    public static List<Integer> steps = List.of(0, 0, 0, 0);
    public static String npc = "";

    private PathClient() {
    }

    static void set(String active, List<Integer> steps, String npc) {
        PathClient.active = active;
        PathClient.steps = List.copyOf(steps);
        PathClient.npc = npc;
    }

    public static boolean on() {
        return !active.isEmpty();
    }

    public static int step(int uni) {
        return uni < steps.size() ? steps.get(uni) : 0;
    }

    // the last path: 0 closed, 1 open, 2 done
    public static int last() {
        return steps.size() > 3 ? steps.get(3) : 0;
    }
}
