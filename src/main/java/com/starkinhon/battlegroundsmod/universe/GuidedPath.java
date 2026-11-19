package com.starkinhon.battlegroundsmod.universe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.datafixers.util.Pair;
import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.ModEntities;
import com.starkinhon.battlegroundsmod.ModItems;
import com.starkinhon.battlegroundsmod.challenge.ChallengeArenas;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.npc.UniverseNpc;
import com.starkinhon.battlegroundsmod.power.Progression;
import com.starkinhon.battlegroundsmod.power.Tiers;
import com.starkinhon.battlegroundsmod.skill.ChallengeSpec;
import com.starkinhon.battlegroundsmod.world.ModWorldgen;
import com.starkinhon.battlegroundsmod.world.NarutoSites;
import com.starkinhon.battlegroundsmod.world.NarutoStructGen;
import com.starkinhon.battlegroundsmod.world.TimeChamber;
import com.starkinhon.battlegroundsmod.world.WorldSites;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// O Caminho (Stark 2026-11-19, "o mod tá com muito conteúdo, mas não tá amarrado pro player cabação entender"): after the
// intro, a mission shows up in the corner on its own and each universe has one path of 7 steps where every master sends
// the player to the next one. Approved doc: https://claude.ai/artifact/FANa91tMDfW4tA74cvJJhP.
// - It starts with the master nearest to where the player woke up (Stark picked it): Mestre Kame (Kame House), Kakashi
//   (Vila da Folha) or Yaga (Templos Antigos). The other two paths are there from the start (Diário, aba Caminho) and the
//   player picks which one the corner shows.
// - Flying is always the 3rd step (Stark: "voo é o meta completo do mod"). Gohan keeps his Tier 9 lock: finishing the
//   2nd step gives the Poder up to Tier 9 (Stark), and the step before a Tier 8 teacher (Rasengan, Reversão) up to Tier 8.
// - Every step is checked from what the game already keeps (the Kamehameha learned, the flight, the arena wins...), so an
//   old save starts where it already is, and a step done for one path counts for the others (flying is everyone's 3rd).
// - The three paths end in their book's boss (Freeza, Madara, Sukuna); all three done open the last one: Os mais fortes,
//   Hashirama or Freeza Poder Total out in the world.
// Only with progression on, in the overworld (Souls world or a normal one; Stark: never in a flat world).
// Kept per player in its persistent data (copied on death like the other missions).
public final class GuidedPath {
    public static final List<String> UNIS = List.of("dragonball", "naruto", "jujutsu");
    private static final List<String> SHORT = List.of("db", "nr", "jj");
    public static final int STEPS = 7;
    private static final String ON = "souls_cam_on";
    private static final String ACTIVE = "souls_cam_ativo";
    private static final String STEP = "souls_cam_";
    private static final String COUNT = "souls_cam_conta_";
    private static final String FINAL = "souls_cam_fim";
    private static final String MET = "souls_cam_viu_";
    private static final String RADAR = "souls_cam_radar";
    private static final String INTERVIEW = "souls_cam_entrevista";
    private static final String YAGA_TALK = "souls_cam_yaga_perg";
    private static final String MAKI = "souls_cam_maki";
    private static final String CHOSEN = "souls_cam_escolhido";
    private static final int INTERVIEW_TICKS = 60 * 20;

    private GuidedPath() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(GuidedPath::onTick);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, false, LivingDeathEvent.class, GuidedPath::onDeath);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            WAYS.remove(e.getEntity().getUUID());
            stopInterview(e.getEntity().getUUID(), null);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.Clone e) -> {
            // the path survives death like the other missions (persistent data is not copied by itself)
            CompoundTag from = e.getOriginal().getPersistentData(), to = e.getEntity().getPersistentData();
            for (String key : from.getAllKeys()) {
                if (key.startsWith(STEP) && !to.contains(key)) {
                    to.put(key, from.get(key).copy());
                }
            }
        });
        introHook();
    }

    // ---------------------------------------------------------------- start

    // The intro (intro.Intro) posts IntroDoneEvent when it ends or is skipped: the path starts there. The tick also starts
    // it once the intro is marked done, for a save that went through the intro before this was in the game.
    private static void introHook() {
        NeoForge.EVENT_BUS.addListener((com.starkinhon.battlegroundsmod.intro.IntroDoneEvent e) -> begin(e.getPlayer()));
    }

    private static boolean here(ServerPlayer player) {
        return Progression.on(player) && player.level().dimension() == Level.OVERWORLD;
    }

    public static boolean started(ServerPlayer player) {
        return player.getPersistentData().getBoolean(ON);
    }

    // Starts the path (once): the nearest of the three masters goes in the corner.
    public static void begin(ServerPlayer player) {
        if (started(player) || !here(player)) {
            return;
        }
        CompoundTag tag = player.getPersistentData();
        tag.putBoolean(ON, true);
        String first = nearest(player);
        tag.putString(ACTIVE, first);
        // an old save: steps already done count right away (quietly)
        for (String uni : UNIS) {
            check(player, uni, false);
        }
        announce(player);
        sync(player);
    }

    // The master nearest to the player: Kame House, the Vila da Folha or the Templos Antigos.
    private static String nearest(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos from = player.blockPosition();
        double best = Double.MAX_VALUE;
        String pick = "dragonball";
        BlockPos kame = WorldSites.locate(level, WorldSites.KAME, from, 6);
        if (kame != null) {
            best = dist(from, kame);
        }
        BlockPos folha = NarutoSites.locate(level, NarutoStructGen.FOLHA, from, 5);
        if (folha != null && dist(from, folha) < best) {
            best = dist(from, folha);
            pick = "naruto";
        }
        BlockPos templo = biome(level, ModWorldgen.TEMPLOS_ANTIGOS, from, 2400);
        if (templo != null && dist(from, templo) < best) {
            pick = "jujutsu";
        }
        return pick;
    }

    private static double dist(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    private static BlockPos biome(ServerLevel level, ResourceKey<Biome> key, BlockPos from, int radius) {
        Pair<BlockPos, Holder<Biome>> found = level.findClosestBiome3d(h -> h.is(key), from, radius, 32, 64);
        return found == null ? null : found.getFirst();
    }

    // ---------------------------------------------------------------- state

    public static int step(ServerPlayer player, String uni) {
        return Math.max(0, Math.min(STEPS, player.getPersistentData().getInt(STEP + uni)));
    }

    public static boolean finished(ServerPlayer player, String uni) {
        return step(player, uni) >= STEPS;
    }

    public static String active(ServerPlayer player) {
        String a = player.getPersistentData().getString(ACTIVE);
        return a.isEmpty() ? "dragonball" : a;
    }

    // 0 closed, 1 open (Os mais fortes), 2 done
    private static int fin(ServerPlayer player) {
        return player.getPersistentData().getInt(FINAL);
    }

    private static String id(String uni, int step) {
        return "cam_" + SHORT.get(UNIS.indexOf(uni)) + (step + 1);
    }

    // The id of the step the corner shows ("cam_db3", "cam_fim"), or "" when nothing.
    private static String shownId(ServerPlayer player) {
        if (!started(player)) {
            return "";
        }
        String uni = active(player);
        if (!finished(player, uni)) {
            return id(uni, step(player, uni));
        }
        return fin(player) == 1 ? "cam_fim" : "";
    }

    private static int count(ServerPlayer player, String uni) {
        return player.getPersistentData().getInt(COUNT + uni);
    }

    private static boolean met(ServerPlayer player, String npc) {
        return player.getPersistentData().getBoolean(MET + npc);
    }

    private static void meet(ServerPlayer player, String npc) {
        player.getPersistentData().putBoolean(MET + npc, true);
    }

    private static boolean flag(ServerPlayer player, String key) {
        return player.getPersistentData().getBoolean(key);
    }

    private static int winsIn(ServerPlayer player, String uni) {
        int n = 0;
        for (String win : ChallengeArenas.wins(player)) {
            ChallengeSpec.Universe u = ChallengeSpec.get().universeOf(win);
            if (u != null && u.id().equals(uni)) {
                n++;
            }
        }
        return n;
    }

    // Whether the step is done, from what the game keeps.
    private static boolean done(ServerPlayer player, String uni, int step) {
        return switch (uni) {
            case "dragonball" -> switch (step) {
                case 0 -> met(player, "mestre_kame") || DbActivities.knowsKamehameha(player);
                case 1 -> DbActivities.knowsKamehameha(player);
                case 2 -> Flight.knows(player);
                case 3 -> DragonBallWorld.hasAnyBall(player);
                case 4 -> winsIn(player, uni) >= 2;
                case 5 -> player.level().dimension() == TimeChamber.LEVEL || TimeChamber.yearDone(player);
                default -> ChallengeArenas.wins(player).contains("freeza");
            };
            case "naruto" -> switch (step) {
                case 0 -> met(player, "kakashi") || EnergyControl.knows(player) || flag(player, MasterTraining.BELLS_DONE);
                case 1 -> EnergyControl.knows(player);
                case 2 -> Flight.knows(player);
                case 3 -> flag(player, MasterTraining.RASENGAN_DONE);
                case 4 -> winsIn(player, uni) >= 2;
                case 5 -> SageMode.knows(player);
                default -> ChallengeArenas.wins(player).contains("madara");
            };
            default -> switch (step) {
                case 0 -> met(player, "yaga") || flag(player, INTERVIEW);
                case 1 -> flag(player, INTERVIEW);
                case 2 -> Flight.knows(player);
                case 3 -> count(player, uni) >= 10;
                case 4 -> ReverseCurse.knows(player);
                case 5 -> flag(player, MAKI) && winsIn(player, uni) >= 2;
                default -> ChallengeArenas.wins(player).contains("sukuna");
            };
        };
    }

    // The bar of the step (count, goal), goal 0 = no bar.
    private static int[] bar(ServerPlayer player, String uni, int step) {
        if (step == 4 && !uni.equals("jujutsu")) {
            return new int[] {Math.min(2, winsIn(player, uni)), 2};
        }
        if (uni.equals("jujutsu")) {
            if (step == 1) {
                Interview f = FIGHTS.get(player.getUUID());
                return new int[] {f == null ? 0 : Math.min(60, f.ticks / 20), 60};
            }
            if (step == 3) {
                return new int[] {Math.min(10, count(player, uni)), 10};
            }
            if (step == 5 && flag(player, MAKI)) {
                return new int[] {Math.min(2, winsIn(player, uni)), 2};
            }
        }
        return new int[] {0, 0};
    }

    // Poder each step pays (a part of the tier, like the old Bulma chain) and the tier it makes sure of.
    private static final double[] PODER = {0.15, 0.3, 0.3, 0.3, 0.5, 0.5, 1.0};

    private static int tierAfter(String uni, int step) {
        if (step == 1) {
            return Tiers.HUMANO - 1;
        }
        if (uni.equals("naruto") && step == 2 || uni.equals("jujutsu") && step == 3) {
            return Tiers.HUMANO - 2;
        }
        return -1;
    }

    // ---------------------------------------------------------------- the tick

    private static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % 10 == 3) {
            interview(player);
        }
        if (player.tickCount % 20 != 7 || !here(player)) {
            return;
        }
        if (!started(player)) {
            if (com.starkinhon.battlegroundsmod.intro.Intro.done(player)) {
                begin(player);
            }
            return;
        }
        boolean changed = false;
        for (String uni : UNIS) {
            changed |= check(player, uni, true);
        }
        if (player.tickCount % 100 == 7) {
            homes(player);
        }
        if (changed || player.tickCount % 200 == 7) {
            sync(player);
        }
    }

    // Moves a path on while its step is done; returns whether it moved.
    private static boolean check(ServerPlayer player, String uni, boolean loud) {
        boolean moved = false;
        int guard = 0;
        while (!finished(player, uni) && done(player, uni, step(player, uni)) && guard++ < STEPS) {
            complete(player, uni, loud && uni.equals(active(player)));
            moved = true;
        }
        if (moved) {
            afterMove(player, uni, loud);
        }
        if (fin(player) == 0 && finished(player, "dragonball") && finished(player, "naruto") && finished(player, "jujutsu")) {
            player.getPersistentData().putInt(FINAL, 1);
            if (loud) {
                player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
                player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("message.battlegroundsmod.caminho.acordou")
                        .withStyle(s -> s.withColor(0xD8352A).withBold(true))));
                player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("message.battlegroundsmod.caminho.acordou_sub")));
                Fx.sound(player.serverLevel(), player.position(), SoundEvents.WITHER_SPAWN, 0.5f, 0.7f);
            }
            moved = true;
        }
        return moved;
    }

    private static void complete(ServerPlayer player, String uni, boolean shown) {
        int step = step(player, uni);
        String sid = id(uni, step);
        CompoundTag tag = player.getPersistentData();
        tag.putInt(STEP + uni, step + 1);
        tag.putInt(COUNT + uni, 0);
        double poder = PODER[Math.min(step, PODER.length - 1)];
        Progression.add(player, Progression.cost(Tiers.of(player)) * poder);
        int tier = tierAfter(uni, step);
        if (tier >= 0) {
            reach(player, tier);
        }
        List<String> rewards = new ArrayList<>();
        rewards.add("__poder");
        if (uni.equals("jujutsu") && step == 1) {
            give(player, new ItemStack(ModItems.CURSED_DOLL.get()));
            rewards.add("boneco_amaldicoado 1");
        }
        if (shown) {
            PacketDistributor.sendToPlayer(player, new ModPayloads.MissionEvent(sid, sid, color(uni), true, List.copyOf(rewards)));
        }
        push(player, uni, step);
    }

    // Makes sure the player is at least at this tier (Stark: "concluindo a missão anterior você ganha o tier 9").
    private static void reach(ServerPlayer player, int tier) {
        if (Tiers.of(player) <= tier) {
            return;
        }
        var data = player.getData(ModAttachments.POWER);
        double need = -data.poder;
        for (int t = data.tier; t > tier; t--) {
            need += Progression.cost(t);
        }
        if (need > 0) {
            Progression.add(player, need + 0.01);
        }
    }

    // A path moved: the next one is the active when it ended, and the new step shows (NOVA MISSÃO).
    private static void afterMove(ServerPlayer player, String uni, boolean loud) {
        if (finished(player, uni) && uni.equals(active(player))) {
            for (String other : UNIS) {
                if (!finished(player, other)) {
                    player.getPersistentData().putString(ACTIVE, other);
                    break;
                }
            }
        }
        if (loud && (uni.equals(active(player)) || finished(player, uni))) {
            announce(player);
        }
    }

    private static void announce(ServerPlayer player) {
        String sid = shownId(player);
        if (!sid.isEmpty()) {
            PacketDistributor.sendToPlayer(player, new ModPayloads.MissionEvent(sid, sid, color(active(player)), false, List.of()));
        }
    }

    private static int color(String uni) {
        return switch (uni) {
            case "naruto" -> 0xF28C28;
            case "jujutsu" -> 0x7A5CFF;
            default -> 0xFF9A2E;
        };
    }

    // The player picked which path the corner shows (Diário, aba Caminho).
    public static void pick(ServerPlayer player, String uni) {
        if (!started(player) || !UNIS.contains(uni) || finished(player, uni)) {
            return;
        }
        player.getPersistentData().putString(ACTIVE, uni);
        player.getPersistentData().putBoolean(CHOSEN, true);
        WAYS.remove(player.getUUID());
        announce(player);
        sync(player);
        DbQuests.sync(player);
    }

    // ---------------------------------------------------------------- what the masters say

    // One line said by an NPC (the talk scene's style in the chat: gold name), for the ones said on their own when a step
    // ends next to them.
    private static void say(ServerPlayer player, String npc, Component line) {
        Component name = Component.translatable("entity.battlegroundsmod.npc_" + npc);
        player.sendSystemMessage(Component.empty().append(name.copy().withStyle(s -> s.withColor(0xF2C94C).withBold(true)))
                .append(Component.literal(": ").withStyle(s -> s.withColor(0xAAAAAA))).append(line.copy().withStyle(s -> s.withColor(0xE6E0EE))));
    }

    private static Component line(String key) {
        return Component.translatable("caminho.battlegroundsmod." + key);
    }

    private static boolean near(ServerPlayer player, String npc, double radius) {
        return !player.serverLevel().getEntitiesOfClass(UniverseNpc.class, player.getBoundingBox().inflate(radius),
                n -> n.isAlive() && n.npcId().equals(npc)).isEmpty();
    }

    // What the master says when a step ends next to them (and the line that sends the player on).
    private static void push(ServerPlayer player, String uni, int step) {
        String key = null, npc = null;
        if (step == 2) {
            // the flight: Gohan sends you on, by the path the corner shows
            npc = "gohan";
            key = "gohan." + active(player);
        } else if (step == 1) {
            npc = switch (uni) {
                case "dragonball" -> "mestre_kame";
                case "naruto" -> "kakashi";
                default -> "yaga";
            };
            key = npc + ".passou";
        }
        if (key != null && near(player, npc, 32)) {
            say(player, npc, line(key));
        }
    }

    // A conversation the path takes over (the first meeting with questions, Bulma's radar, the Yaga's interview...).
    public record Question(Component label, Component answer, boolean server) {
    }

    public record Talk(Component line, List<Question> questions) {
    }

    // The path's own conversation with this NPC now, or null (then the NPC talks as always).
    public static Talk talk(ServerPlayer player, UniverseNpc npc) {
        if (!started(player) || !here(player)) {
            return null;
        }
        String who = npc.npcId();
        int db = step(player, "dragonball"), nr = step(player, "naruto"), jj = step(player, "jujutsu");
        switch (who) {
            case "mestre_kame" -> {
                if (db == 0 && !met(player, who)) {
                    return new Talk(line("kame.chegada"), List.of(
                            new Question(line("p.lugar"), line("kame.lugar"), false),
                            new Question(line("p.almas"), line("kame.almas"), false),
                            new Question(line("p.lutar"), line("kame.lutar"), true)));
                }
            }
            case "kakashi" -> {
                if (nr == 0 && !met(player, who)) {
                    return new Talk(line("kakashi.chegada"), List.of(
                            new Question(line("p.lugar"), line("kakashi.lugar"), false),
                            new Question(line("p.energia"), line("kakashi.energia"), false),
                            new Question(line("p.treina"), line("kakashi.treina"), true)));
                }
            }
            case "yaga" -> {
                if (jj == 0) {
                    return new Talk(line("yaga.chegada"), List.of(
                            new Question(line("p.forte"), line("yaga.hm"), true),
                            new Question(line("p.proteger"), line("yaga.hm"), true),
                            new Question(line("p.seila"), line("yaga.hm"), true)));
                }
                if (jj == 1 && !FIGHTS.containsKey(player.getUUID())) {
                    startInterview(player, npc);
                    return new Talk(line("yaga.denovo"), List.of());
                }
                if (jj >= 2 && !flag(player, YAGA_TALK)) {
                    player.getPersistentData().putBoolean(YAGA_TALK, true);
                    return new Talk(line("yaga.pergunta"), List.of(
                            new Question(line("p.lugar"), line("yaga.lugar"), false),
                            new Question(line("p.maldicoes"), line("yaga.maldicoes"), false),
                            new Question(line("p.agora"), line(jj == 2 ? "yaga.voo" : jj == 3 ? "yaga.aldeia" : "yaga.agora"), false)));
                }
            }
            case "bulma" -> {
                if (db == 3 && !flag(player, RADAR)) {
                    player.getPersistentData().putBoolean(RADAR, true);
                    give(player, new ItemStack(ModItems.DRAGON_RADAR.get()));
                    give(player, new ItemStack(ModItems.SCOUTER.get()));
                    WAYS.remove(player.getUUID());
                    sync(player);
                    DbQuests.sync(player);
                    return new Talk(line("bulma.radar"), List.of());
                }
            }
            case "jiraiya" -> {
                if (nr == 5 && !SageMode.knows(player)) {
                    List<String> techniques = new ArrayList<>(player.getData(ModAttachments.TECHNIQUES));
                    techniques.add(SageMode.TECHNIQUE);
                    player.setData(ModAttachments.TECHNIQUES, List.copyOf(techniques));
                    EnergyControl.sync(player);
                    player.connection.send(new ClientboundSetTitlesAnimationPacket(8, 60, 20));
                    player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("message.battlegroundsmod.technique_learned")
                            .withStyle(s -> s.withColor(0xF2C94C).withBold(true))));
                    player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("technique.battlegroundsmod.modo_sabio")));
                    Fx.sound(player.serverLevel(), player.position(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
                    return new Talk(line("jiraiya.sabio"), List.of());
                }
            }
            case "maki" -> {
                if (jj == 5 && !flag(player, MAKI)) {
                    player.getPersistentData().putBoolean(MAKI, true);
                    give(player, new ItemStack(ModItems.PLAYFUL_CLOUD.get()));
                    sync(player);
                    DbQuests.sync(player);
                    return new Talk(line("maki.chegada"), List.of());
                }
            }
            default -> {
            }
        }
        return null;
    }

    // A question answered on the server's side (choice = its place in the list).
    public static Component answer(ServerPlayer player, UniverseNpc npc, int choice) {
        if (!started(player)) {
            return null;
        }
        switch (npc.npcId()) {
            case "mestre_kame" -> {
                meet(player, "mestre_kame");
                check(player, "dragonball", true);
                sync(player);
                return line("kame.lutar"); // and the Turtle Training starts (npc/Talks)
            }
            case "kakashi" -> {
                meet(player, "kakashi");
                check(player, "naruto", true);
                sync(player);
                return line("kakashi.treina");
            }
            case "yaga" -> {
                if (step(player, "jujutsu") == 0) {
                    meet(player, "yaga");
                    check(player, "jujutsu", true);
                    startInterview(player, npc);
                    sync(player);
                    return line("yaga.hm");
                }
                return null;
            }
            default -> {
                return null;
            }
        }
    }

    // A line said before the NPC's own (the first time a master sees you on this step), or null.
    public static Component before(ServerPlayer player, String npc) {
        if (!started(player) || !here(player)) {
            return null;
        }
        int nr = step(player, "naruto"), jj = step(player, "jujutsu");
        if (npc.equals("jiraiya") && nr == 3 && !met(player, "jiraiya")) {
            meet(player, "jiraiya");
            return line("jiraiya.chegada");
        }
        if (npc.equals("shoko") && jj == 4 && !met(player, "shoko")) {
            meet(player, "shoko");
            return line("shoko.chegada");
        }
        return null;
    }

    // A line said after the NPC's own, that points to the next place, or null.
    public static Component after(ServerPlayer player, String npc) {
        if (!started(player) || !here(player)) {
            return null;
        }
        for (String uni : UNIS) {
            check(player, uni, true);
        }
        int db = step(player, "dragonball"), nr = step(player, "naruto"), jj = step(player, "jujutsu");
        String key = switch (npc) {
            case "mestre_kame" -> db == 2 ? "kame.voo" : null;
            case "gohan" -> db == 3 ? "gohan.dragonball" : null;
            case "bulma" -> db == 3 && flag(player, RADAR) ? "bulma.esfera" : db == 4 ? "bulma.livro" : db == 5 ? "bulma.karin" : null;
            case "korin" -> db == 5 ? "korin.sala" : db == 6 ? "korin.freeza" : null;
            case "kakashi" -> nr == 2 ? "kakashi.voo" : nr == 3 ? "kakashi.jiraiya" : null;
            case "jiraiya" -> nr == 4 ? "jiraiya.livro" : nr == 6 ? "jiraiya.madara" : null;
            case "yaga" -> jj == 2 ? "yaga.voo" : jj == 3 ? "yaga.aldeia" : jj == 4 ? "yaga.shoko" : jj == 6 ? "yaga.sukuna" : null;
            case "shoko" -> jj == 5 ? "shoko.maki" : null;
            case "maki" -> jj >= 5 && flag(player, MAKI) ? "maki.livro" : null;
            default -> null;
        };
        return key == null ? null : line(key);
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    // ---------------------------------------------------------------- the Yaga's interview

    // The manga's interview: the answer does not matter, his cursed corpses attack. Hold out 60 s near him; two straw
    // corpses (the Templos' boneco) are always on you, a new one when one falls. Dying or running off ends it: talk to
    // him again.
    private static final class Interview {
        final BlockPos at;
        final List<UUID> dolls = new ArrayList<>();
        int ticks, wait = 60;

        Interview(BlockPos at) {
            this.at = at;
        }
    }

    private static final Map<UUID, Interview> FIGHTS = new HashMap<>();

    private static void startInterview(ServerPlayer player, UniverseNpc npc) {
        stopInterview(player.getUUID(), player.serverLevel());
        FIGHTS.put(player.getUUID(), new Interview(npc.blockPosition()));
    }

    private static void stopInterview(UUID id, ServerLevel level) {
        Interview f = FIGHTS.remove(id);
        if (f == null || level == null) {
            return;
        }
        for (UUID d : f.dolls) {
            if (level.getEntity(d) instanceof Entity e) {
                e.discard();
            }
        }
    }

    // every 10 ticks
    private static void interview(ServerPlayer player) {
        Interview f = FIGHTS.get(player.getUUID());
        if (f == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (!player.isAlive() || player.level().dimension() != Level.OVERWORLD || player.blockPosition().distSqr(f.at) > 40 * 40) {
            stopInterview(player.getUUID(), level);
            say(player, "yaga", line("yaga.falhou"));
            return;
        }
        if (f.wait > 0) {
            // the scene closes before the corpses stand up
            f.wait -= 10;
            return;
        }
        f.dolls.removeIf(d -> !(level.getEntity(d) instanceof LivingEntity e) || !e.isAlive());
        var type = ModEntities.JJ_MOBS.get("jj_boneco");
        while (type != null && f.dolls.size() < 2) {
            double a = player.getRandom().nextDouble() * Math.PI * 2;
            BlockPos at = DbActivities.dryGround(level, (int) (player.getX() + Math.cos(a) * 6), (int) (player.getZ() + Math.sin(a) * 6), 4);
            if (at == null) {
                break;
            }
            Entity doll = type.get().spawn(level, at, MobSpawnType.EVENT);
            if (!(doll instanceof Mob mob)) {
                break;
            }
            mob.setTarget(player);
            mob.setLastHurtByMob(player);
            Fx.burst(level, net.minecraft.core.particles.ParticleTypes.CLOUD, mob.position().add(0, 0.6, 0), 10, 0.3, 0.02);
            f.dolls.add(mob.getUUID());
        }
        for (UUID d : f.dolls) {
            if (level.getEntity(d) instanceof Mob mob && mob.getTarget() != player) {
                mob.setTarget(player);
            }
        }
        f.ticks += 10;
        if (f.ticks % 200 == 0 && f.ticks < INTERVIEW_TICKS) {
            DbQuests.sync(player);
        }
        if (f.ticks == 400) {
            say(player, "yaga", line("yaga.meio"));
        }
        if (f.ticks >= INTERVIEW_TICKS) {
            stopInterview(player.getUUID(), level);
            player.getPersistentData().putBoolean(INTERVIEW, true);
            check(player, "jujutsu", true);
            sync(player);
            DbQuests.sync(player);
        }
    }

    // ---------------------------------------------------------------- kills

    private static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide()) {
            return;
        }
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(dead.getType()).getPath();
        if (event.getSource().getEntity() instanceof ServerPlayer player && started(player) && step(player, "jujutsu") == 3 && curse(id)) {
            CompoundTag tag = player.getPersistentData();
            tag.putInt(COUNT + "jujutsu", tag.getInt(COUNT + "jujutsu") + 1);
            DbQuests.sync(player);
        }
        if (dead.getType() == ModEntities.HASHIRAMA.get() || dead.getType() == ModEntities.FREEZA_TOTAL.get()) {
            if (dead.level() instanceof ServerLevel level) {
                for (ServerPlayer p : level.getPlayers(p -> p.distanceToSqr(dead) < 96 * 96)) {
                    if (fin(p) == 1) {
                        p.getPersistentData().putInt(FINAL, 2);
                        PacketDistributor.sendToPlayer(p, new ModPayloads.MissionEvent("cam_fim", "cam_fim", 0xD8352A, true, List.of()));
                        sync(p);
                        DbQuests.sync(p);
                    }
                }
            }
        }
    }

    // An exorcised curse: the Jujutsu biomes' (not the Colônia's people, nor Mechamaru's puppet) and the old ones.
    private static boolean curse(String id) {
        if (id.equals("maldicao_menor") || id.equals("maldicao_grau2")) {
            return true;
        }
        return id.startsWith("jj_") && !id.equals("jj_abate") && !id.equals("jj_kogane") && !id.equals("jj_mechamaru");
    }

    // ---------------------------------------------------------------- where the arrow points

    private record Way(String id, BlockPos pos, long at, BlockPos from) {
    }

    private static final Map<UUID, Way> WAYS = new HashMap<>();

    // The place the step sends the player (null: none, like the Livro dos Desafios).
    private static BlockPos target(ServerPlayer player, String sid) {
        ServerLevel level = player.serverLevel();
        BlockPos from = player.blockPosition();
        return switch (sid) {
            case "cam_db1", "cam_db2" -> WorldSites.locate(level, WorldSites.KAME, from, 6);
            case "cam_db3", "cam_nr3", "cam_jj3" -> WorldSites.locate(level, WorldSites.GOKU, from, 6);
            case "cam_db4" -> flag(player, RADAR) ? null : WorldSites.locate(level, WorldSites.CAPSULE, from, 6);
            case "cam_db6" -> WorldSites.locate(level, WorldSites.KARIN, from, 6);
            case "cam_nr1", "cam_nr2" -> NarutoSites.locate(level, NarutoStructGen.FOLHA, from, 5);
            case "cam_nr4", "cam_nr6" -> NarutoSites.locate(level, NarutoStructGen.MYOBOKU, from, 5);
            case "cam_jj1", "cam_jj2", "cam_jj5" -> {
                BlockPos npc = npcNear(player, sid.equals("cam_jj5") ? ModEntities.NPC_SHOKO.get() : ModEntities.NPC_YAGA.get());
                yield npc != null ? npc : biome(level, ModWorldgen.TEMPLOS_ANTIGOS, from, 2400);
            }
            case "cam_jj4" -> biome(level, ModWorldgen.ALDEIA_AMALDICOADA, from, 2400);
            case "cam_jj6" -> {
                if (flag(player, MAKI)) {
                    yield null;
                }
                BlockPos npc = npcNear(player, ModEntities.NPC_MAKI.get());
                yield npc != null ? npc : biome(level, ModWorldgen.OUTONO_DE_KYOTO, from, 2400);
            }
            case "cam_fim" -> Tiers.of(player) <= 2 ? NarutoSites.locate(level, NarutoSites.VALE, from, 5) : null;
            default -> null;
        };
    }

    private static BlockPos npcNear(ServerPlayer player, EntityType<UniverseNpc> type) {
        List<UniverseNpc> list = player.serverLevel().getEntities(type, player.getBoundingBox().inflate(200), Entity::isAlive);
        UniverseNpc best = null;
        for (UniverseNpc n : list) {
            if (best == null || n.distanceToSqr(player) < best.distanceToSqr(player)) {
                best = n;
            }
        }
        return best == null ? null : best.blockPosition();
    }

    private static BlockPos way(ServerPlayer player, String sid) {
        long now = player.serverLevel().getGameTime();
        Way cached = WAYS.get(player.getUUID());
        boolean far = cached != null && cached.from().distSqr(player.blockPosition()) > 150 * 150;
        // the biome search is the slow one: again only every 30 s, or after walking away
        int every = sid.startsWith("cam_jj") ? 600 : 300;
        if (cached != null && cached.id().equals(sid) && now - cached.at() < every && !far) {
            return cached.pos();
        }
        BlockPos pos = target(player, sid);
        WAYS.put(player.getUUID(), new Way(sid, pos, now, player.blockPosition()));
        return pos;
    }

    // ---------------------------------------------------------------- the masters that come to you

    // Yaga and Shoko stand at the Escola Jujutsu, 1 in 10 temples: a player on the path finds them in the first temple
    // of the Templos Antigos instead (the nearest one keeps them; never two). Maki trains alone in the Outono de Kyoto.
    private static void homes(ServerPlayer player) {
        int jj = step(player, "jujutsu");
        ServerLevel level = player.serverLevel();
        Holder<Biome> biome = level.getBiome(player.blockPosition());
        if (jj <= 4 && biome.is(ModWorldgen.TEMPLOS_ANTIGOS)) {
            standBy(player, ModEntities.NPC_YAGA.get(), 0);
            if (jj >= 3) {
                standBy(player, ModEntities.NPC_SHOKO.get(), 5);
            }
        }
        if (jj == 5 && !flag(player, MAKI) && biome.is(ModWorldgen.OUTONO_DE_KYOTO)) {
            standBy(player, ModEntities.NPC_MAKI.get(), 0);
        }
    }

    private static void standBy(ServerPlayer player, EntityType<UniverseNpc> type, int side) {
        ServerLevel level = player.serverLevel();
        if (!level.getEntities(type, player.getBoundingBox().inflate(220), Entity::isAlive).isEmpty()) {
            return;
        }
        var look = player.getLookAngle();
        double len = Math.max(0.01, Math.sqrt(look.x * look.x + look.z * look.z));
        double fx = look.x / len, fz = look.z / len;
        int x = (int) (player.getX() + fx * 18 - fz * side), z = (int) (player.getZ() + fz * 18 + fx * side);
        BlockPos at = DbActivities.dryGround(level, x, z, 10);
        if (at == null) {
            return;
        }
        UniverseNpc npc = type.spawn(level, at, MobSpawnType.EVENT);
        if (npc != null) {
            npc.restrictTo(at, 8);
            npc.setCustomName(npc.getType().getDescription());
            npc.setCustomNameVisible(true);
            WAYS.remove(player.getUUID());
        }
    }

    // ---------------------------------------------------------------- to the client

    // The corner panel's line for the path (DbQuests.sync: after a running activity and the structure's mission).
    public record Hud(String id, int count, int goal, int x, int z, boolean way) {
    }

    public static Hud hud(ServerPlayer player) {
        if (!here(player)) {
            return null;
        }
        String sid = shownId(player);
        if (sid.isEmpty()) {
            return null;
        }
        // Bulma's step tells you to come first, then to find a ball; Maki's the same
        if (sid.equals("cam_db4") && flag(player, RADAR)) {
            sid = "cam_db4b";
        } else if (sid.equals("cam_jj6") && flag(player, MAKI)) {
            sid = "cam_jj6b";
        } else if (sid.equals("cam_fim") && Tiers.of(player) > 2) {
            sid = "cam_fim_tier";
        }
        int[] b = new int[] {0, 0};
        String uni = active(player);
        if (!finished(player, uni)) {
            b = bar(player, uni, step(player, uni));
        }
        String base = sid.replace("cam_db4b", "cam_db4").replace("cam_jj6b", "cam_jj6").replace("cam_fim_tier", "cam_fim");
        BlockPos to = way(player, base);
        return new Hud(sid, b[0], b[1], to == null ? 0 : to.getX(), to == null ? 0 : to.getZ(), to != null);
    }

    // Who the corner sends the player to (the gold diamond over their head, client/NpcRenderer), "" for none.
    private static String targetNpc(ServerPlayer player) {
        String sid = shownId(player);
        return switch (sid) {
            case "cam_db1", "cam_db2" -> "mestre_kame";
            case "cam_db3", "cam_nr3", "cam_jj3" -> Flight.knows(player) ? "" : "gohan";
            case "cam_db4" -> flag(player, RADAR) ? "" : "bulma";
            case "cam_db6" -> "korin";
            case "cam_nr1", "cam_nr2" -> "kakashi";
            case "cam_nr4", "cam_nr6" -> "jiraiya";
            case "cam_jj1", "cam_jj2" -> FIGHTS.containsKey(player.getUUID()) ? "" : "yaga";
            case "cam_jj5" -> "shoko";
            case "cam_jj6" -> flag(player, MAKI) ? "" : "maki";
            default -> "";
        };
    }

    public static void sync(ServerPlayer player) {
        List<Integer> steps = new ArrayList<>();
        for (String uni : UNIS) {
            steps.add(step(player, uni));
        }
        steps.add(fin(player));
        PacketDistributor.sendToPlayer(player, new ModPayloads.PathSync(started(player) && here(player) ? active(player) : "", steps,
                started(player) && here(player) ? targetNpc(player) : ""));
    }
}
