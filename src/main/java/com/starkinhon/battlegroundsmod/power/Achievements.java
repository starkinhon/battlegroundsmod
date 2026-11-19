package com.starkinhon.battlegroundsmod.power;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.boss.SoulBoss;
import com.starkinhon.battlegroundsmod.challenge.ChallengeArenas;
import com.starkinhon.battlegroundsmod.universe.DbQuests;
import com.starkinhon.battlegroundsmod.world.TimeChamber;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// The Souls advancement tabs (data/battlegroundsmod/advancement/{almas,alvos,tecnicas,exploracao}, made by
// tools/advancements/souls_advancements.py, which also writes souls_skills/conquistas.json: what this class reads, so
// the two never drift apart). Stark 2026-11-12 (preview https://claude.ai/artifact/VjRj6PGgwMkdqUcu9iJyPJ): only power
// and who you beat, each kind in its own tab; 2026-11-19: they had all gone into one tab.
// The weapons, the seven balls, the biomes and Namek are vanilla criteria in the json; the rest is given here: once in
// a while from the player's state (tier, techniques, Sincronia, arena wins, missions), and at the moment for a
// character beaten, a soul absorbed and Shenlong. Only in the universes mode (Progression.on): the flat fight/test
// world, where any soul is a click away, gives nothing.
public final class Achievements {
    private static List<String> souls = List.of();
    private static Map<String, List<Integer>> skills = Map.of();
    // universe id (and "bestiario_<universe>") -> the characters (or common mobs) of its row
    private static Map<String, List<String>> targets = Map.of();
    private static List<String> divine = List.of();
    private static List<String> techniques = List.of();

    private Achievements() {
    }

    public static void register() {
        load();
        NeoForge.EVENT_BUS.addListener(Achievements::onTick);
        NeoForge.EVENT_BUS.addListener(Achievements::onDeath);
    }

    private static void load() {
        try (InputStream in = Achievements.class.getResourceAsStream("/data/" + BattlegroundsMod.MODID + "/souls_skills/conquistas.json")) {
            if (in == null) {
                return;
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            souls = strings(root.get("souls"));
            Map<String, List<Integer>> sk = new LinkedHashMap<>();
            root.getAsJsonObject("skills").entrySet().forEach(e -> {
                List<Integer> list = new ArrayList<>();
                e.getValue().getAsJsonArray().forEach(i -> list.add(i.getAsInt()));
                sk.put(e.getKey(), List.copyOf(list));
            });
            skills = sk;
            Map<String, List<String>> tg = new LinkedHashMap<>();
            root.getAsJsonObject("alvos").entrySet().forEach(e -> tg.put(e.getKey(), strings(e.getValue())));
            targets = tg;
            divine = strings(root.get("divinas"));
            techniques = strings(root.get("tecnicas"));
        } catch (Exception e) {
            BattlegroundsMod.LOGGER.error("conquistas.json", e);
        }
    }

    private static List<String> strings(JsonElement array) {
        List<String> out = new ArrayList<>();
        array.getAsJsonArray().forEach(s -> out.add(s.getAsString()));
        return List.copyOf(out);
    }

    // Gives <tab>/<id> (all its criteria) if the player doesn't have it yet.
    public static void grant(ServerPlayer player, String id) {
        AdvancementHolder holder = holder(player, id);
        if (holder == null || !Progression.on(player)) {
            return;
        }
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
        if (progress.isDone()) {
            return;
        }
        List<String> left = new ArrayList<>();
        progress.getRemainingCriteria().forEach(left::add);
        for (String criterion : left) {
            player.getAdvancements().award(holder, criterion);
        }
    }

    // One criterion of a many-part advancement (the bar Better Advancements draws: "Universo Naruto 4/7").
    private static void award(ServerPlayer player, String id, String criterion) {
        AdvancementHolder holder = holder(player, id);
        if (holder == null || !Progression.on(player)) {
            return;
        }
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
        var c = progress.getCriterion(criterion);
        if (c != null && !c.isDone()) {
            player.getAdvancements().award(holder, criterion);
        }
    }

    public static boolean has(ServerPlayer player, String id) {
        AdvancementHolder holder = holder(player, id);
        return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    private static AdvancementHolder holder(ServerPlayer player, String id) {
        return player.server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, id));
    }

    // A soul crystal absorbed (item/SoulItem): its branch opens right away.
    public static void soul(ServerPlayer player) {
        if (Progression.on(player)) {
            souls(player);
        }
    }

    // A character or a common mob beaten by a player (or by its projectile, summon...).
    private static void onDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player) || !Progression.on(player)) {
            return;
        }
        LivingEntity dead = event.getEntity();
        String id = dead instanceof SoulBoss boss ? boss.bossId() : BuiltInRegistries.ENTITY_TYPE.getKey(dead.getType()).getPath();
        beat(player, id);
    }

    private static void beat(ServerPlayer player, String id) {
        if (id == null) {
            return;
        }
        for (var row : targets.entrySet()) {
            if (!row.getValue().contains(id)) {
                continue;
            }
            if (!row.getKey().startsWith("bestiario_")) {
                grant(player, "alvos/" + id);
            }
            award(player, "alvos/" + row.getKey(), id);
        }
        if (divine.contains(id)) {
            award(player, "alvos/devorador_de_deuses", id);
        }
    }

    private static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 40 != 13 || !Progression.on(player)) {
            return;
        }
        int tier = player.getData(ModAttachments.POWER).tier;
        for (int t = 9; t >= tier && t >= 0; t--) {
            grant(player, "tecnicas/tier_" + t);
        }
        List<String> known = player.getData(ModAttachments.TECHNIQUES);
        for (String technique : techniques) {
            if (known.contains(technique)) {
                grant(player, "tecnicas/tecnica_" + technique);
                award(player, "tecnicas/todas_tecnicas", technique);
            }
        }
        souls(player);
        for (String win : ChallengeArenas.wins(player)) {
            beat(player, win);       // arena wins from before (and the ones the death event did not see)
        }
        for (String universe : targets.keySet()) {
            if (!universe.startsWith("bestiario_") && has(player, "alvos/" + universe)) {
                award(player, "alvos/multiverso", universe);
            }
        }
        if (DbQuests.done(player) >= DbQuests.total() || com.starkinhon.battlegroundsmod.universe.GuidedPath.finished(player, "dragonball")) {
            grant(player, "exploracao/missoes_bulma");
        }
        if (TimeChamber.yearDone(player)) {
            grant(player, "exploracao/sala_tempo");
        }
    }

    // Each soul worn or absorbed: its crystal, every skill already awake (Sincronia) and the whole kit.
    private static void souls(ServerPlayer player) {
        for (String id : souls) {
            if (!SoulSync.known(player, id)) {
                continue;
            }
            grant(player, "almas/" + id);
            award(player, "almas/colecionador", id);
            Power power = Powers.get(id);
            if (power == null) {
                continue;
            }
            for (int index : skills.getOrDefault(id, List.of())) {
                if (SoulSync.awake(player, power, slot(index))) {
                    grant(player, "almas/" + id + "_" + index);
                }
            }
            boolean all = true;
            for (int slot : SoulSync.skills(power)) {
                all &= SoulSync.awake(player, power, slot);
            }
            if (all) {
                grant(player, "almas/" + id + "_total");
            }
        }
    }

    // The language files number a soul's skills 0-9 (Z X C V B, then the Shift ones): the slot each one uses.
    private static int slot(int index) {
        if (index == 4) {
            return PowerData.SLOT_EXTRA;
        }
        if (index == 9) {
            return PowerData.SLOT_SHIFT_EXTRA;
        }
        return index >= 5 ? PowerData.SLOT_SHIFT + index - 5 : index;
    }
}
