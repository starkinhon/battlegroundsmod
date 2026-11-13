package com.starkinhon.battlegroundsmod.universe;

import java.util.ArrayList;
import java.util.List;

import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.Tiers;
import com.starkinhon.battlegroundsmod.world.NarutoSites;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// The Diário de Missões (approved in the preview, Stark 2026-11-13: the pixel map of Dragon Ball and Naruto with a pin on
// each structure). The player discovers a structure by walking into it (StructureMissions shows its name then): from then
// on its pin shows on the map with the state of its mission (NOVA, EM ANDAMENTO, FEITA HOJE, CONCLUÍDA), the progress of
// each step and how far it is; before that it is "???" under the fog, with a hint. The player can track one: the corner
// HUD shows it with an arrow. The texts and rewards of each mission are on the client (assets/.../ui/missoes.json); here
// is only what changes per player, sent as "id|state|steps|found|x|z|dimension".
public final class MissionJournal {
    static final String FOUND = "souls_descobertas";
    static final String TRACKED = "souls_rastreio";

    public static final List<String> DB = List.of("kame", "capsula", "goku", "karin", "nave", "cell", "raditz", "babidi", "buu", "guru", "vila");
    public static final List<String> NARUTO = List.of("folha", "morte", "nevoa", "chuva", "vale", "myoboku");

    private MissionJournal() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(MissionJournal::onTick);
    }

    // Every 10 s the journal goes to the client again (the tracked pin's distance and states that change on their own,
    // like a new day).
    private static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % 40 == 13) {
            discover(player);
        }
        if (player.tickCount % 200 == 37) {
            send(player);
        }
    }

    // Walking into a structure finds it (also the ones whose mission is already done, so old worlds fill the map too).
    private static void discover(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition();
        if (level.dimension() == com.starkinhon.battlegroundsmod.world.NamekWorld.LEVEL) {
            if (StructureMissions.near(player, StructureMissions.GURU, 24)) {
                found(player, "guru", new BlockPos(StructureMissions.GURU[0], 64, StructureMissions.GURU[1]));
            }
            if (StructureMissions.near(player, StructureMissions.VILLAGE, 32)) {
                found(player, "vila", new BlockPos(StructureMissions.VILLAGE[0], 64, StructureMissions.VILLAGE[1]));
            }
            return;
        }
        if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD) {
            return;
        }
        for (String kind : List.of("capsula", "goku", "karin", "cell", "nave", "raditz", "babidi", "buu", "kame")) {
            com.starkinhon.battlegroundsmod.world.WorldSites.Site s = com.starkinhon.battlegroundsmod.world.WorldSites.near(level, kind, pos, 40);
            if (s != null) {
                found(player, kind, new BlockPos(s.x(), s.y(), s.z()));
            }
        }
        NarutoSites.Site site = NarutoSites.near(level, null, pos, 44);
        if (site != null) {
            found(player, missionOf("est_" + site.kind()), new BlockPos(site.x(), site.y(), site.z()));
        }
    }

    // The mission of the place a quest id belongs to (StructureMissions' ids: est_<place>[_state], est_<naruto kind>).
    static String missionOf(String questId) {
        if (questId == null || !questId.startsWith("est_")) {
            return null;
        }
        String rest = questId.substring(4);
        for (String id : DB) {
            if (rest.equals(id) || rest.startsWith(id + "_")) {
                return id;
            }
        }
        return switch (rest) {
            case "folha", "morte", "nevoa", "chuva", "vale" -> rest;
            case "pico" -> "myoboku";
            default -> null;
        };
    }

    // The player walked into a structure: its pin shows on the map from now on.
    public static void found(ServerPlayer player, String mission, BlockPos at) {
        if (mission == null) {
            return;
        }
        CompoundTag all = player.getPersistentData().getCompound(FOUND);
        if (all.contains(mission)) {
            return;
        }
        CompoundTag one = new CompoundTag();
        one.putInt("x", at.getX());
        one.putInt("z", at.getZ());
        one.putString("d", player.level().dimension().location().toString());
        all.put(mission, one);
        player.getPersistentData().put(FOUND, all);
        send(player);
    }

    // A mission was done: MISSÃO CONCLUÍDA on screen with what it gave ("__poder", "item_id count"), in place of the
    // vanilla title when clearTitle (DbActivities.reward: not when a tier-up title took the screen).
    static void done(ServerPlayer player, String mission, List<String> rewards, boolean clearTitle) {
        if (clearTitle) {
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundClearTitlesPacket(true));
        }
        PacketDistributor.sendToPlayer(player, new ModPayloads.MissionEvent(mission, "", 0, true, List.copyOf(rewards)));
        send(player);
    }

    public static void track(ServerPlayer player, String mission) {
        player.getPersistentData().putString(TRACKED, mission == null ? "" : mission);
        send(player);
    }

    public static void send(ServerPlayer player) {
        CompoundTag all = player.getPersistentData().getCompound(FOUND);
        List<String> out = new ArrayList<>();
        for (List<String> list : List.of(DB, NARUTO)) {
            for (String id : list) {
                CompoundTag one = all.getCompound(id);
                boolean found = all.contains(id);
                int[] steps;
                String state;
                try {
                    Object[] st = state(player, id, found ? new BlockPos(one.getInt("x"), 64, one.getInt("z")) : null);
                    state = (String) st[0];
                    steps = (int[]) st[1];
                } catch (RuntimeException e) {
                    state = "pronta";
                    steps = new int[0];
                }
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < steps.length; i++) {
                    sb.append(i > 0 ? "," : "").append(steps[i]);
                }
                out.add(id + "|" + (found ? state : "oculta") + "|" + sb + "|" + (found ? 1 : 0) + "|" + one.getInt("x") + "|" + one.getInt("z") + "|"
                        + one.getString("d"));
            }
        }
        PacketDistributor.sendToPlayer(player, new ModPayloads.SyncJournal(out, player.getPersistentData().getString(TRACKED)));
    }

    private static Object[] of(String state, int... steps) {
        return new Object[] {state, steps};
    }

    // {state, progress of each step}: pronta (NOVA), andamento, feita (FEITA HOJE, comes back at dawn), concluida (once).
    private static Object[] state(ServerPlayer player, String id, BlockPos foundAt) {
        var tag = player.getPersistentData();
        ServerLevel level = player.serverLevel();
        switch (id) {
            case "kame" -> {
                int stage = DbActivities.kameStage(player);
                if (!DbActivities.knowsKamehameha(player)) {
                    return of(stage > 0 ? "andamento" : "pronta", stage >= 1 ? 1 : 0, stage >= 2 ? 1 : 0, stage >= 3 ? 1 : 0, 0);
                }
                if (stage > 0) {
                    return of("andamento", 1, stage >= 2 ? 1 : 0, 0);
                }
                return DbActivities.dailyFree(player, "souls_kame_dia") ? of("pronta", 0, 0, 0) : of("feita", 1, 1, 1);
            }
            case "capsula" -> {
                return DbActivities.dailyFree(player, DbActivities.CAPSULE_DAY) ? of("pronta", 0) : of("feita", 1);
            }
            case "goku" -> {
                if (Flight.knows(player)) {
                    return of("concluida", 1, 1);
                }
                return of("pronta", Tiers.of(player) <= Tiers.HUMANO - 1 ? 1 : 0, 0);
            }
            case "karin" -> {
                if (StructureMissions.korinDone(player)) {
                    return of("concluida", 161, 1);
                }
                int climb = StructureMissions.climbing(player);
                return climb >= 0 ? of("andamento", Math.min(161, climb), 0) : of("pronta", 0, 0);
            }
            case "nave" -> {
                int used = tag.getLong("souls_gravidade_dia") == level.getGameTime() / 24000L ? tag.getInt("souls_gravidade_s") : 0;
                return used >= DbActivities.GRAVITY_SECONDS ? of("feita", DbActivities.GRAVITY_SECONDS) : used > 0 ? of("andamento", used) : of("pronta", 0);
            }
            case "cell", "babidi", "vila" -> {
                String kind = id.equals("vila") ? "namek" : id;
                String day = id.equals("vila") ? "souls_namek_dia" : "souls_" + id + "_dia";
                int stage = DbActivities.wavesStage(player, kind);
                int n = 3;
                if (stage >= 0) {
                    int[] steps = new int[id.equals("babidi") ? 3 : 1];
                    if (id.equals("babidi")) {
                        for (int i = 0; i < 3; i++) {
                            steps[i] = stage > i ? 1 : 0;
                        }
                    } else {
                        steps[0] = Math.min(n, stage);
                    }
                    return new Object[] {"andamento", steps};
                }
                if (DbActivities.dailyFree(player, day)) {
                    return id.equals("babidi") ? of("pronta", 0, 0, 0) : of("pronta", 0);
                }
                return id.equals("babidi") ? of("feita", 1, 1, 1) : of("feita", n);
            }
            case "raditz" -> {
                if (StructureMissions.anyPodDone(player)) {
                    return of("concluida", 3, 1);
                }
                int fight = StructureMissions.podFight(player);
                return fight >= 0 ? of("andamento", fight, 0) : of("pronta", 0, 0);
            }
            case "buu" -> {
                return DbActivities.dailyFree(player, "souls_boo_dia") ? of("pronta", 0) : of("feita", 1);
            }
            case "guru" -> {
                return StructureMissions.guruDone(player) ? of("concluida", 1) : of("pronta", 0);
            }
            case "folha" -> {
                if ("guizo".equals(NarutoActivities.runType(player))) {
                    return of("andamento", 0);
                }
                return NarutoActivities.journalFree(player, "souls_guizo_dia") ? of("pronta", 0) : of("feita", 60);
            }
            case "morte" -> {
                NarutoSites.Site site = foundAt == null ? null : NarutoSites.near(level, com.starkinhon.battlegroundsmod.world.NarutoStructGen.MORTE, foundAt, 96);
                if (site != null && NarutoActivities.siteState(level, site).getBoolean("aberta")) {
                    return of("feita", 1, 1, 1);
                }
                return "altar".equals(NarutoActivities.runType(player)) ? of("andamento", 0, 0, 0) : of("pronta", 0, 0, 0);
            }
            case "nevoa" -> {
                NarutoSites.Site site = foundAt == null ? null : NarutoSites.near(level, com.starkinhon.battlegroundsmod.world.NarutoStructGen.NEVOA, foundAt, 96);
                if (site != null && NarutoActivities.siteState(level, site).getBoolean("tirada")) {
                    return of("concluida", 1, 3);
                }
                if ("espada".equals(NarutoActivities.runType(player))) {
                    return of("andamento", 1, Math.max(0, NarutoActivities.runWave(player) - 1));
                }
                return of("pronta", 0, 0);
            }
            case "chuva" -> {
                if ("flor".equals(NarutoActivities.runType(player))) {
                    return of("andamento", 1, 0);
                }
                return NarutoActivities.journalFree(player, "souls_flor_dia") ? of("pronta", 0, 0) : of("feita", 1, 90);
            }
            case "vale" -> {
                if ("vale".equals(NarutoActivities.runType(player))) {
                    return of("andamento", 1);
                }
                return NarutoActivities.journalFree(player, "souls_vale_dia") ? of("pronta", 0) : of("feita", 1);
            }
            case "myoboku" -> {
                return tag.getBoolean("souls_pergaminho_sapo") ? of("concluida", 1, 1) : of("pronta", 0, 0);
            }
            default -> {
                return of("pronta");
            }
        }
    }
}
