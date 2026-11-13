package com.starkinhon.battlegroundsmod.client.ui;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.network.ModPayloads;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

// The client side of the Diário de Missões: the missions of each universe (assets/battlegroundsmod/ui/missoes.json, the
// approved preview's texts: the place, who is there, the kind, the name, the line, the steps, the rewards, what it asks for
// and the hint for the undiscovered) and what the server says of each for this player (universe/MissionJournal).
public final class Journal {
    // [text, goal]
    public record Step(String text, int goal) {
    }

    // id (UiIcons special or an item id), count, name, line under it
    public record Reward(String id, int n, String name, String desc) {
        public ItemStack stack() {
            if (UiIcons.special(id)) {
                return ItemStack.EMPTY;
            }
            return Journal.stack(id, Math.max(1, n));
        }
    }

    public record Mission(String uni, String id, String lugar, String skin, String quem, String tipo, String nome, String fala, List<Step> passos,
                          List<Step> passos2, List<Reward> rec, List<Reward> entrega, boolean escolha, String dica, String mais) {
    }

    // What the server says: state (pronta, andamento, feita, concluida, oculta), progress of each step, where it was found.
    public record Entry(String state, int[] steps, boolean found, int x, int z, String dim) {
    }

    private static Map<String, List<Mission>> catalog;
    private static final Map<String, Mission> BY_ID = new HashMap<>();
    private static final Map<String, Entry> ENTRIES = new HashMap<>();
    public static String tracked = "";

    private Journal() {
    }

    public static ItemStack stack(String id, int n) {
        ResourceLocation key = ResourceLocation.tryParse(id.contains(":") ? id : BattlegroundsMod.MODID + ":" + id);
        if (key != null && !BuiltInRegistries.ITEM.containsKey(key)) {
            key = ResourceLocation.withDefaultNamespace(id);
        }
        if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.get(key);
        return new ItemStack(item, n);
    }

    public static Map<String, List<Mission>> catalog() {
        if (catalog == null) {
            catalog = new LinkedHashMap<>();
            try (var in = Minecraft.getInstance().getResourceManager().open(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "ui/missoes.json"))) {
                JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                for (var e : root.entrySet()) {
                    List<Mission> list = new ArrayList<>();
                    for (JsonElement el : e.getValue().getAsJsonArray()) {
                        JsonObject o = el.getAsJsonObject();
                        Mission m = new Mission(e.getKey(), str(o, "id"), str(o, "lugar"), o.has("npc") && !o.get("npc").isJsonNull() ? str(o, "npc") : str(o, "mob"),
                                str(o, "quem"), str(o, "tipo"), str(o, "nome"), str(o, "fala"), steps(o, "passos"), steps(o, "passos2"), rewards(o, "rec"),
                                rewards(o, "entrega"), o.has("escolha") && o.get("escolha").getAsBoolean(), str(o, "dica"), str(o, "mais"));
                        list.add(m);
                        BY_ID.put(m.id(), m);
                    }
                    catalog.put(e.getKey(), list);
                }
            } catch (Exception ex) {
                BattlegroundsMod.LOGGER.error("Diário: não li ui/missoes.json", ex);
            }
        }
        return catalog;
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }

    private static List<Step> steps(JsonObject o, String k) {
        List<Step> out = new ArrayList<>();
        if (o.has(k)) {
            for (JsonElement el : o.getAsJsonArray(k)) {
                JsonArray a = el.getAsJsonArray();
                out.add(new Step(a.get(0).getAsString(), a.size() > 2 ? a.get(2).getAsInt() : 1));
            }
        }
        return out;
    }

    // rec: [id, n, name?, desc?]; entrega: [id, n]
    private static List<Reward> rewards(JsonObject o, String k) {
        List<Reward> out = new ArrayList<>();
        if (o.has(k)) {
            for (JsonElement el : o.getAsJsonArray(k)) {
                JsonArray a = el.getAsJsonArray();
                out.add(new Reward(a.get(0).getAsString(), a.size() > 1 ? a.get(1).getAsInt() : 1, a.size() > 2 ? a.get(2).getAsString() : "",
                        a.size() > 3 ? a.get(3).getAsString() : ""));
            }
        }
        return out;
    }

    public static Mission mission(String id) {
        catalog();
        return BY_ID.get(id);
    }

    public static Entry entry(String id) {
        Entry e = ENTRIES.get(id);
        return e != null ? e : new Entry("oculta", new int[0], false, 0, 0, "");
    }

    public static boolean has() {
        return !ENTRIES.isEmpty();
    }

    public static void apply(List<String> entries, String track) {
        for (String line : entries) {
            String[] p = line.split("\\|", -1);
            if (p.length < 7) {
                continue;
            }
            int[] steps;
            if (p[2].isEmpty()) {
                steps = new int[0];
            } else {
                String[] ss = p[2].split(",");
                steps = new int[ss.length];
                for (int i = 0; i < ss.length; i++) {
                    try {
                        steps[i] = Integer.parseInt(ss[i]);
                    } catch (NumberFormatException e) {
                        steps[i] = 0;
                    }
                }
            }
            int x = 0, z = 0;
            try {
                x = Integer.parseInt(p[4]);
                z = Integer.parseInt(p[5]);
            } catch (NumberFormatException ignored) {
            }
            ENTRIES.put(p[0], new Entry(p[1], steps, p[3].equals("1"), x, z, p[6]));
        }
        tracked = track == null ? "" : track;
    }

    public static void ask() {
        PacketDistributor.sendToServer(new ModPayloads.TrackMission("", true));
    }

    public static void track(String id) {
        tracked = id == null ? "" : id;
        PacketDistributor.sendToServer(new ModPayloads.TrackMission(tracked, false));
    }

    // The steps to show (the Kame House's daily milk after the training), and the progress of each.
    public static List<Step> steps(Mission m) {
        if (!m.passos2().isEmpty() && entry(m.id()).steps().length == m.passos2().size() && entry(m.id()).steps().length != m.passos().size()) {
            return m.passos2();
        }
        return m.passos();
    }

    public static int progress(Mission m, int step) {
        int[] s = entry(m.id()).steps();
        return step < s.length ? s[step] : 0;
    }

    // The first step not done yet (for the corner tracker), or the last.
    public static String currentStep(Mission m) {
        List<Step> st = steps(m);
        for (int i = 0; i < st.size(); i++) {
            if (progress(m, i) < st.get(i).goal()) {
                return st.get(i).text();
            }
        }
        return st.isEmpty() ? m.nome() : st.get(st.size() - 1).text();
    }

    // The distance and the direction (0 = ahead, clockwise in eighths) to where it was found, or null (other world).
    public static int[] way(String id) {
        Entry e = entry(id);
        Minecraft mc = Minecraft.getInstance();
        if (!e.found() || mc.player == null || !mc.player.level().dimension().location().toString().equals(e.dim())) {
            return null;
        }
        double dx = e.x() + 0.5 - mc.player.getX(), dz = e.z() + 0.5 - mc.player.getZ();
        int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        float target = (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90f;
        float rel = net.minecraft.util.Mth.wrapDegrees(target - mc.player.getYRot());
        int dir = Math.floorMod(Math.round(rel / 45f), 8);
        return new int[] {dist, dir};
    }
}
