package com.starkinhon.battlegroundsmod.power;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.challenge.ChallengeArenas;
import com.starkinhon.battlegroundsmod.skill.ChallengeSpec;
import com.starkinhon.battlegroundsmod.universe.BleachWorld;
import com.starkinhon.battlegroundsmod.universe.DbQuests;
import com.starkinhon.battlegroundsmod.universe.DemonSlayerWorld;
import com.starkinhon.battlegroundsmod.universe.Flight;
import com.starkinhon.battlegroundsmod.universe.ReverseCurse;
import com.starkinhon.battlegroundsmod.universe.SageMode;
import com.starkinhon.battlegroundsmod.world.TimeChamber;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// The "Souls Mod" advancement tab (data/battlegroundsmod/advancement/souls, made by tools/advancements/souls_advancements.py).
// Stark 2026-11-11: "conquistas de roubar certas armas, conquistar certos tiers, conseguir o voo, o sage mode, a
// reversão, dominar o universo, entre outras". The weapons, the seven balls and Namek are vanilla criteria in the json;
// the rest ("minecraft:impossible") is given here: once in a while from the player's state (tier, techniques, arena
// wins, soul, missions), and at the moment for the soul absorbed and Shenlong. Only in the universes mode
// (Progression.on): the flat fight/test world, where any soul is a click away, gives nothing.
public final class Achievements {
    private static final String[][] DIVINE = {
            {"alma_gojo", "strongest"}, {"alma_sukuna", "sukuna"}, {"alma_kokushibo", "moon_demon"},
            {"alma_madara", "uchiha_ghost"}, {"alma_bills", "destruction_god"}, {"alma_aizen", "manipulator"}};
    private static final int[] TIERS = {9, 7, 5, 3, 1, 0};
    private static final Map<String, String> TECHNIQUES = Map.of(
            "tecnica_voo", Flight.TECHNIQUE, "tecnica_modo_sabio", SageMode.TECHNIQUE, "tecnica_reversao", ReverseCurse.TECHNIQUE,
            "tecnica_shunpo", BleachWorld.SHUNPO, "tecnica_marca_cacador", DemonSlayerWorld.MARK);

    private Achievements() {
    }

    // The Divine Souls beaten (Stark 2026-11-11: the main achievements, all of them = the mod beaten): power -> id.
    private static final String[][] DIVINE_BOSSES = {
            {"uchiha_ghost", "derrotar_madara"}, {"strongest", "derrotar_gojo"}, {"sukuna", "derrotar_sukuna"},
            {"moon_demon", "derrotar_kokushibo"}, {"destruction_god", "derrotar_bills"}};
    // Aizen (manipulator), Luffy (rubber_boy), Saitama (one_punch) and Shigaraki come in when they get a boss.

    public static void register() {
        NeoForge.EVENT_BUS.addListener(Achievements::onTick);
        NeoForge.EVENT_BUS.addListener(Achievements::onDeath);
    }

    private static void onDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (!(event.getEntity() instanceof com.starkinhon.battlegroundsmod.boss.SoulBoss boss) || !boss.isBoss()
                || !(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        String power;
        try {
            power = com.starkinhon.battlegroundsmod.boss.BossSpec.get(boss.bossId()).poder();
        } catch (RuntimeException e) {
            return;
        }
        boolean all = true;
        for (String[] divine : DIVINE_BOSSES) {
            if (divine[0].equals(power)) {
                grant(player, "almas_divinas");
                grant(player, divine[1]);
            }
            all &= has(player, divine[1]);
        }
        if (all) {
            grant(player, "devorador_de_deuses");
        }
    }

    // Gives souls/<id> (all its criteria) if the player doesn't have it yet.
    public static void grant(ServerPlayer player, String id) {
        if (!Progression.on(player)) {
            return;
        }
        AdvancementHolder holder = holder(player, id);
        if (holder == null) {
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

    public static boolean has(ServerPlayer player, String id) {
        AdvancementHolder holder = holder(player, id);
        return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    // A soul crystal absorbed (item/SoulItem): the first theft, and the Divine Soul ones right away.
    public static void soul(ServerPlayer player) {
        grant(player, "primeira_alma");
        souls(player);
    }

    private static AdvancementHolder holder(ServerPlayer player, String id) {
        return player.server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "souls/" + id));
    }

    private static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 40 != 13 || !Progression.on(player)) {
            return;
        }
        PowerData data = player.getData(ModAttachments.POWER);
        for (int tier : TIERS) {
            if (data.tier <= tier) {
                grant(player, "tier_" + tier);
            }
        }
        souls(player);
        techniques(player);
        universes(player);
        dragonBall(player);
    }

    private static void souls(ServerPlayer player) {
        String power = player.getData(ModAttachments.POWER).power;
        boolean all = true;
        for (String[] divine : DIVINE) {
            if (divine[1].equals(power)) {
                grant(player, divine[0]);
            }
            all &= has(player, divine[0]);
        }
        if (all) {
            grant(player, "todas_divinas");
        }
    }

    private static void techniques(ServerPlayer player) {
        List<String> known = player.getData(ModAttachments.TECHNIQUES);
        boolean all = true;
        for (var entry : TECHNIQUES.entrySet()) {
            if (known.contains(entry.getValue())) {
                grant(player, entry.getKey());
            } else {
                all = false;
            }
        }
        if (all) {
            grant(player, "todas_tecnicas");
        }
    }

    // A universe is ruled when every challenge of its Livro dos Desafios was beaten.
    private static void universes(ServerPlayer player) {
        List<String> wins = ChallengeArenas.wins(player);
        if (wins.isEmpty()) {
            return;
        }
        grant(player, "primeiro_desafio");
        ChallengeSpec spec = ChallengeSpec.get();
        if (spec == null || spec.universos() == null) {
            return;
        }
        boolean all = true;
        for (ChallengeSpec.Universe universe : spec.universos()) {
            boolean ruled = universe.desafios() != null && !universe.desafios().isEmpty();
            if (ruled) {
                for (ChallengeSpec.Challenge challenge : universe.desafios()) {
                    ruled &= wins.contains(challenge.id());
                }
            }
            if (ruled) {
                grant(player, "dominar_" + universe.id());
            }
            all &= ruled;
        }
        if (all) {
            grant(player, "multiverso");
        }
    }

    private static void dragonBall(ServerPlayer player) {
        int done = DbQuests.done(player);
        if (done >= 1) {
            grant(player, "raditz");
        }
        if (done >= DbQuests.total()) {
            grant(player, "missoes_bulma");
        }
        if (TimeChamber.yearDone(player)) {
            grant(player, "sala_tempo");
        }
    }
}
