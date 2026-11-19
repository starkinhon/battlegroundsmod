package com.starkinhon.battlegroundsmod.challenge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.ModItems;
import com.starkinhon.battlegroundsmod.combat.CombatMode;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.PowerLogic;
import com.starkinhon.battlegroundsmod.power.Powers;
import com.starkinhon.battlegroundsmod.power.SoulSync;
import com.starkinhon.battlegroundsmod.power.Tiers;
import com.starkinhon.battlegroundsmod.skill.ChallengeSpec;
import com.starkinhon.battlegroundsmod.skill.DungeonSpec;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// The Dungeon (/souls dungeon): a roguelike apart from the player's own world (Stark, 2026-11-15).
//
// The player is sent to a random one of the 30 arenas with everything reset: human (Tier 10), no soul, no strength, bare
// fists, one Semente dos Deuses. The real character is saved first (Backup, an attachment, so a crash or a closed game
// loses nothing: the next login puts it back) and comes back at the end of the run, won or lost.
//
// A run climbs padrao.dungeon.json's etapas: one fight after another against the characters from the weakest (Kuririn) to the
// strongest (Bills), each in a new random arena (the SubAlmas in a new random order every run, the divine souls at the end in order,
// DungeonSpec.sorteio), at an ADAPTED tier (the same for the player and the boss: Tier 10 down to
// Tier 0). Winning offers the boss's soul, which the player may absorb (its whole kit awake) or refuse, and then 3 of the
// OPTIONS to pick one of (trade the soul, more Sementes, an extra life, ...). The player has "vidas" deaths for the whole run; a
// death heals everything and restarts that fight. Block destruction is off in the arenas while a run is on (protectsBlocks).
//
// A run is shared by a GROUP: the party of the challenge book (ArenaParty) comes in by itself when one of them starts it, all
// fight the same boss in the same arena, and the boss's health grows with the number of fighters like in any group fight
// (ChallengeArenas.spawnBoss: x2 with two players). Everything that is personal stays personal: each player has their own lives,
// soul, cards and buffs, and the next fight starts when everyone has chosen. A player who falls with a partner still up is put back
// at the start of the arena (a life lost) and the fight goes on; a solo player restarts the fight. A player out of lives, or who
// leaves, gets the real character back and the others go on.
//
// ChallengeArenas builds the arenas and spawns the boss; this class owns the run. The client picks with DungeonChoiceScreen.
public final class Dungeon {
    // The choices after a win. 3 of them are drawn each time (DungeonSpec.opcoesPorEscolha). The names and texts are on the
    // client (DungeonChoiceScreen).
    public static final List<String> OPTIONS = List.of("manter", "sacrificar", "acaso", "senzu", "vida", "resistencia", "forca",
            "ult", "ascensao");

    // The real character, kept while a run is on. Saved with the player (attachment), also after a death.
    public record Backup(boolean active, List<ItemStack> items, PowerData power, List<String> stolen, List<String> techniques,
            Map<String, SoulSync.Entry> sync, boolean fed, int xpLevel, float xpProgress, float health,
            String level, double x, double y, double z, float yaw) {
        public static final Codec<Backup> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.optionalFieldOf("active", false).forGetter(Backup::active),
                ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(Backup::items),
                PowerData.CODEC.optionalFieldOf("power", new PowerData()).forGetter(Backup::power),
                Codec.STRING.listOf().optionalFieldOf("stolen", List.of()).forGetter(Backup::stolen),
                Codec.STRING.listOf().optionalFieldOf("techniques", List.of()).forGetter(Backup::techniques),
                SoulSync.MAP_CODEC.optionalFieldOf("sync", Map.of()).forGetter(Backup::sync),
                Codec.BOOL.optionalFieldOf("fed", false).forGetter(Backup::fed),
                Codec.INT.optionalFieldOf("xp_level", 0).forGetter(Backup::xpLevel),
                Codec.FLOAT.optionalFieldOf("xp_progress", 0f).forGetter(Backup::xpProgress),
                Codec.FLOAT.optionalFieldOf("health", 20f).forGetter(Backup::health),
                Codec.STRING.optionalFieldOf("level", "minecraft:overworld").forGetter(Backup::level),
                Codec.DOUBLE.optionalFieldOf("x", 0d).forGetter(Backup::x),
                Codec.DOUBLE.optionalFieldOf("y", 0d).forGetter(Backup::y),
                Codec.DOUBLE.optionalFieldOf("z", 0d).forGetter(Backup::z),
                Codec.FLOAT.optionalFieldOf("yaw", 0f).forGetter(Backup::yaw)).apply(i, Backup::new));

        public static final Backup EMPTY = new Backup(false, List.of(), new PowerData(), List.of(), List.of(), Map.of(), false, 0, 0f,
                20f, "minecraft:overworld", 0, 0, 0, 0f);
    }

    // Where a player of a run came from (a fight's arena sends them there if something takes them out of it).
    record Origin(ResourceKey<Level> level, Vec3 pos, float yaw) {
    }

    // One player of a run: all of it personal.
    private static final class Member {
        int lives;
        boolean choosing;
        List<String> offer = List.of();
        // the power id of the boss's soul while the player has not answered absorb/refuse yet, "" otherwise
        String pendingSoul = "";
        int strength;
        int resistance;
        int ascend;
        boolean ult;
        Origin origin;
    }

    // One run (memory only: a restart ends it and the Backup puts the character back at the next login). Shared by its players.
    private static final class Run {
        MinecraftServer server;
        int stage;
        // the etapas of THIS run, in the order drawn at its start (DungeonSpec.sorteio)
        List<DungeonSpec.Etapa> etapas = List.of();
        String lastArena = "";
        // true from a won fight until the next one starts (everyone is choosing, or waiting for the others)
        boolean between;
        final Map<UUID, Member> members = new LinkedHashMap<>();
    }

    // every player of a run -> the run they share
    private static final Map<UUID, Run> RUNS = new HashMap<>();

    private Dungeon() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, Dungeon::onDeath);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, Dungeon::onLogin);
        NeoForge.EVENT_BUS.addListener(Dungeon::onLogout);
        NeoForge.EVENT_BUS.addListener(Dungeon::onServerTick);
    }

    // BlockRules: the arenas of a run never lose a block (the old arenas were wrecked by skills and bugged the next fight).
    public static boolean protectsBlocks(Level level) {
        return !RUNS.isEmpty() && ChallengeArenas.isArena(level.dimension());
    }

    public static boolean inRun(ServerPlayer player) {
        return RUNS.containsKey(player.getUUID());
    }

    // ---- start and end

    // The player starts a run and their party of the challenge book comes in with them, by itself (the ones who are busy are left out).
    public static void start(ServerPlayer player) {
        DungeonSpec spec = DungeonSpec.get();
        if (RUNS.containsKey(player.getUUID())) {
            say(player, "Você já está em uma dungeon. Use /souls dungeon sair para abandonar.");
            return;
        }
        if (!player.isAlive() || player.isSpectator()) {
            return;
        }
        if (ChallengeArenas.isArena(player.level().dimension()) || ChallengeArenas.inFight(player)) {
            say(player, "Saia da arena antes de começar uma dungeon.");
            return;
        }
        List<ServerPlayer> team = new ArrayList<>();
        team.add(player);
        for (ServerPlayer mate : ArenaParty.others(player)) {
            if (RUNS.containsKey(mate.getUUID()) || !mate.isAlive() || mate.isSpectator()
                    || ChallengeArenas.isArena(mate.level().dimension()) || ChallengeArenas.inFight(mate)) {
                say(player, mate.getDisplayName().getString() + " está ocupado e não entrou na dungeon.");
                continue;
            }
            team.add(mate);
        }
        Run run = new Run();
        run.server = player.server;
        run.etapas = new ArrayList<>(spec.sorteio(new java.util.Random()));
        for (ServerPlayer p : team) {
            if (p.getData(ModAttachments.DUNGEON_BACKUP).active()) {
                // left over from a game that closed in the middle of a run: the real character first
                restore(p);
            }
            CombatMode.leave(p);
            Member member = new Member();
            member.lives = spec.vidas();
            member.origin = new Origin(p.level().dimension(), p.position(), p.getYRot());
            p.setData(ModAttachments.DUNGEON_BACKUP, capture(p));
            run.members.put(p.getUUID(), member);
            RUNS.put(p.getUUID(), run);
            reset(p, spec);
            title(p, Component.literal("DUNGEON").withStyle(s -> s.withColor(0xD8352A).withBold(true)),
                    Component.literal("Do mais fraco ao mais forte. " + spec.vidas() + " vidas."));
            if (p != player) {
                say(p, player.getDisplayName().getString() + " começou uma dungeon e levou você junto (sua party do livro).");
            }
        }
        if (team.size() > 1) {
            for (ServerPlayer p : team) {
                say(p, "Dungeon em grupo de " + team.size() + ": a vida de cada boss é multiplicada por " + team.size() + ".");
            }
        }
        if (!startStage(run)) {
            endAll(run, "Não foi possível montar a arena.");
        }
    }

    // /souls dungeon sair: only this player goes, the group goes on.
    public static void quit(ServerPlayer player) {
        if (!RUNS.containsKey(player.getUUID())) {
            say(player, "Você não está em uma dungeon.");
            return;
        }
        leave(player, "Você abandonou a dungeon.");
    }

    // The last life gone, a quit, a run that could not go on: this player gets the real character back, the others go on.
    private static void leave(ServerPlayer player, String reason) {
        Run run = RUNS.get(player.getUUID());
        int total = DungeonSpec.get().etapas().size();
        int reached = run == null ? 0 : Math.min(run.stage + 1, total);
        restore(player);
        say(player, reason + " Você chegou na etapa " + reached + " de " + total + ". Seu personagem voltou ao mundo.");
        if (run != null && run.between) {
            // the others may all have chosen already and were only waiting for this player
            next(run);
        }
    }

    private static void endAll(Run run, String reason) {
        run.between = false;
        for (ServerPlayer p : online(run)) {
            leave(p, reason);
        }
    }

    private static void finishWon(ServerPlayer player) {
        restore(player);
        title(player, Component.literal("DUNGEON COMPLETA").withStyle(s -> s.withColor(0xF2C94C).withBold(true)),
                Component.literal("Você derrotou o Bills."));
        say(player, "Dungeon completa! Seu personagem voltou ao mundo.");
    }

    // The online players of a run (the ones who are gone are dropped from it).
    private static List<ServerPlayer> online(Run run) {
        List<ServerPlayer> list = new ArrayList<>();
        Iterator<UUID> it = run.members.keySet().iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            ServerPlayer p = run.server.getPlayerList().getPlayer(id);
            if (p == null) {
                it.remove();
                RUNS.remove(id);
            } else {
                list.add(p);
            }
        }
        return list;
    }

    private static void detach(ServerPlayer player) {
        Run run = RUNS.remove(player.getUUID());
        if (run != null) {
            run.members.remove(player.getUUID());
        }
    }

    // ---- the real character: save, reset, put back

    private static Backup capture(ServerPlayer player) {
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            items.add(player.getInventory().getItem(i).copy());
        }
        return new Backup(true, items, player.getData(ModAttachments.POWER), List.copyOf(player.getData(ModAttachments.STOLEN_SOULS)),
                List.copyOf(player.getData(ModAttachments.TECHNIQUES)), Map.copyOf(player.getData(ModAttachments.SOUL_SYNC)),
                player.getData(ModAttachments.SOUL_FED), player.experienceLevel, player.experienceProgress, player.getHealth(),
                player.level().dimension().location().toString(), player.getX(), player.getY(), player.getZ(), player.getYRot());
    }

    // Human, Tier 10, empty bar, no soul, no strength, bare fists, Sementes dos Deuses (like Universes.bornHuman).
    private static void reset(ServerPlayer player, DungeonSpec spec) {
        PowerData fresh = new PowerData();
        fresh.receivedBook = true;
        player.setData(ModAttachments.POWER, fresh);
        player.setData(ModAttachments.STOLEN_SOULS, List.of());
        player.setData(ModAttachments.TECHNIQUES, List.of());
        player.setData(ModAttachments.SOUL_SYNC, Map.of());
        player.setData(ModAttachments.SOUL_FED, false);
        player.getInventory().clearContent();
        player.removeAllEffects();
        player.setExperienceLevels(0);
        player.experienceProgress = 0f;
        Tiers.apply(player);
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        giveSeeds(player, spec.sementesIniciais());
        PowerLogic.sync(player);
        SoulSync.sync(player);
    }

    private static void restore(ServerPlayer player) {
        detach(player);
        ChallengeArenas.dungeonStop(player);
        Backup b = player.getData(ModAttachments.DUNGEON_BACKUP);
        if (!b.active()) {
            return;
        }
        CombatMode.leave(player);
        player.setData(ModAttachments.DUNGEON_BACKUP, Backup.EMPTY);
        player.removeAllEffects();
        player.getInventory().clearContent();
        for (int i = 0; i < Math.min(b.items().size(), player.getInventory().getContainerSize()); i++) {
            player.getInventory().setItem(i, b.items().get(i).copy());
        }
        player.setData(ModAttachments.POWER, b.power());
        player.setData(ModAttachments.STOLEN_SOULS, b.stolen());
        player.setData(ModAttachments.TECHNIQUES, b.techniques());
        player.setData(ModAttachments.SOUL_SYNC, b.sync());
        player.setData(ModAttachments.SOUL_FED, b.fed());
        player.setExperienceLevels(b.xpLevel());
        player.experienceProgress = b.xpProgress();
        Tiers.apply(player);
        player.setHealth(Math.max(1f, Math.min(b.health(), player.getMaxHealth())));
        player.getFoodData().setFoodLevel(20);
        ServerLevel level = player.server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(b.level())));
        if (level == null) {
            level = player.server.overworld();
        }
        player.teleportTo(level, b.x(), b.y(), b.z(), b.yaw(), 0f);
        player.fallDistance = 0;
        player.setDeltaMovement(Vec3.ZERO);
        PowerLogic.sync(player);
        SoulSync.sync(player);
    }

    // ---- the fights

    // Puts every player of the run at the tier of the next fight, fully healed with their buffs, and asks the arenas for one fight
    // for all of them (the boss's health grows with the team there).
    private static boolean startStage(Run run) {
        List<ServerPlayer> team = online(run);
        if (team.isEmpty()) {
            return false;
        }
        DungeonSpec spec = DungeonSpec.get();
        DungeonSpec.Etapa etapa = run.etapas.get(run.stage);
        Map<UUID, Origin> origins = new HashMap<>();
        for (ServerPlayer player : team) {
            Member member = run.members.get(player.getUUID());
            PowerData data = player.getData(ModAttachments.POWER);
            data.tier = Math.max(0, etapa.tier() - member.ascend);
            data.ult = member.ult && data.hasPower() ? PowerData.MAX_ULT : 0f;
            data.stamina = -1;
            data.exhaustedUntil = 0;
            Tiers.apply(player);
            player.setHealth(player.getMaxHealth());
            player.getFoodData().setFoodLevel(20);
            player.clearFire();
            player.fallDistance = 0;
            applyBuffs(player, member);
            origins.put(player.getUUID(), member.origin);
        }
        String arena = pickArena(team.get(0), run);
        if (!ChallengeArenas.beginDungeon(team, arena, etapa.boss(), etapa.tier(), spec.contagemSegundos() * 20, origins)) {
            return false;
        }
        run.lastArena = arena;
        run.between = false;
        for (ServerPlayer player : team) {
            PowerLogic.sync(player);
            say(player, "Etapa " + (run.stage + 1) + "/" + spec.etapas().size() + ": " + bossName(etapa).getString() + " (Tier "
                    + etapa.tier() + ")");
        }
        return true;
    }

    private static void applyBuffs(ServerPlayer player, Member member) {
        int duration = 20 * 60 * 60;
        if (member.strength > 0) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, duration, member.strength - 1, false, false, true));
        }
        if (member.resistance > 0) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, duration, member.resistance - 1, false, false, true));
        }
    }

    // A random arena of the 30 that is free and is not the last one.
    private static String pickArena(ServerPlayer player, Run run) {
        List<String> all = new ArrayList<>();
        for (ChallengeSpec.Universe universe : ChallengeSpec.get().universos()) {
            for (ChallengeSpec.Challenge challenge : universe.desafios()) {
                all.add(challenge.id());
            }
        }
        List<String> free = new ArrayList<>();
        for (String id : all) {
            if (!id.equals(run.lastArena) && !ChallengeArenas.arenaTaken(id)) {
                free.add(id);
            }
        }
        if (free.isEmpty()) {
            for (String id : all) {
                if (!ChallengeArenas.arenaTaken(id)) {
                    free.add(id);
                }
            }
        }
        List<String> from = free.isEmpty() ? all : free;
        return from.get(player.getRandom().nextInt(from.size()));
    }

    // ChallengeArenas: the character of the fight died, once for the whole group (the players who were still in it).
    static void stageWon(List<ServerPlayer> players) {
        Run run = RUNS.get(players.get(0).getUUID());
        if (run == null) {
            return;
        }
        DungeonSpec spec = DungeonSpec.get();
        DungeonSpec.Etapa etapa = run.etapas.get(run.stage);
        run.stage++;
        List<ServerPlayer> team = online(run);
        for (ServerPlayer player : team) {
            player.setHealth(player.getMaxHealth());
            player.getFoodData().setFoodLevel(20);
        }
        if (run.stage >= spec.etapas().size()) {
            for (ServerPlayer player : team) {
                finishWon(player);
            }
            return;
        }
        run.between = true;
        // The soul is an offer, not a gift: each player absorbs it or refuses it (soul()), then picks a card (pick()).
        boolean offered = Powers.get(etapa.alma()) != null;
        for (ServerPlayer player : team) {
            Member member = run.members.get(player.getUUID());
            member.pendingSoul = offered ? etapa.alma() : "";
            member.offer = draw(member, spec);
            member.choosing = true;
            title(player, Component.literal("VITÓRIA").withStyle(s -> s.withColor(0xF2C94C).withBold(true)),
                    Component.literal(offered ? "A alma de " + bossName(etapa).getString() + " está disponível." : "Escolha."));
            PacketDistributor.sendToPlayer(player, new ModPayloads.DungeonChoices(member.offer, member.lives, run.stage,
                    spec.etapas().size(), currentSoulName(player), offered ? bossName(etapa).getString() : ""));
        }
    }

    // 3 different choices that still make sense (no extra life at the limit, no more Ascensao than allowed...).
    private static List<String> draw(Member member, DungeonSpec spec) {
        List<String> pool = new ArrayList<>(OPTIONS);
        if (member.lives >= spec.vidasMax()) {
            pool.remove("vida");
        }
        if (member.ascend >= spec.ascensaoMax()) {
            pool.remove("ascensao");
        }
        if (member.strength >= spec.efeitoMax()) {
            pool.remove("forca");
        }
        if (member.resistance >= spec.efeitoMax()) {
            pool.remove("resistencia");
        }
        Collections.shuffle(pool);
        return new ArrayList<>(pool.subList(0, Math.min(spec.opcoesPorEscolha(), pool.size())));
    }

    // From the client: absorb the boss's soul (take) or refuse it. Absorbing puts it in with the whole kit awake and replaces the
    // soul the player carried; refusing keeps the one they have (or none, if they have none yet).
    public static void soul(ServerPlayer player, boolean take) {
        Member member = member(player);
        if (member == null || !member.choosing || member.pendingSoul.isEmpty()) {
            return;
        }
        Power soul = Powers.get(member.pendingSoul);
        member.pendingSoul = "";
        if (take && soul != null) {
            PowerLogic.select(player, soul);
            SoulSync.completeAll(player, soul);
            say(player, "Você absorveu a alma de " + soul.name().getString() + ".");
        } else if (soul != null) {
            say(player, "Você recusou a alma de " + soul.name().getString() + ".");
        }
    }

    // From the client: the card picked (an index of the offer). The next fight starts when every player of the run has picked.
    public static void pick(ServerPlayer player, int index) {
        Run run = RUNS.get(player.getUUID());
        Member member = member(player);
        if (run == null || member == null || !member.choosing || index < 0 || index >= member.offer.size()) {
            return;
        }
        DungeonSpec spec = DungeonSpec.get();
        String id = member.offer.get(index);
        // a card picked without answering the soul counts as refusing it (so the run can never wait forever)
        member.pendingSoul = "";
        member.choosing = false;
        member.offer = List.of();
        apply(player, member, spec, id);
        for (Member other : run.members.values()) {
            if (other.choosing) {
                say(player, "Aguardando os outros jogadores escolherem...");
                return;
            }
        }
        next(run);
    }

    // Everyone has chosen: the next fight.
    private static void next(Run run) {
        if (!run.between || run.members.isEmpty()) {
            return;
        }
        for (Member member : run.members.values()) {
            if (member.choosing) {
                return;
            }
        }
        if (!startStage(run)) {
            endAll(run, "Não foi possível montar a arena.");
        }
    }

    private static Member member(ServerPlayer player) {
        Run run = RUNS.get(player.getUUID());
        return run == null ? null : run.members.get(player.getUUID());
    }

    private static void apply(ServerPlayer player, Member member, DungeonSpec spec, String id) {
        switch (id) {
            case "sacrificar" -> {
                // the soul in hand for a random SubAlma (never the same one)
                trade(player, Powers.sub());
            }
            case "acaso" -> {
                // a gamble: any soul of the game, SubAlma or divine
                List<Power> all = new ArrayList<>(Powers.sub());
                all.addAll(Powers.divine());
                trade(player, all);
            }
            case "senzu" -> {
                giveSeeds(player, spec.sementesPremio());
                say(player, "+" + spec.sementesPremio() + " Sementes dos Deuses.");
            }
            case "vida" -> {
                member.lives = Math.min(spec.vidasMax(), member.lives + 1);
                say(player, "Vida extra! Vidas: " + member.lives);
            }
            case "resistencia" -> {
                member.resistance = Math.min(spec.efeitoMax(), member.resistance + 1);
                say(player, "Resistência nível " + member.resistance + " até o fim da dungeon.");
            }
            case "forca" -> {
                member.strength = Math.min(spec.efeitoMax(), member.strength + 1);
                say(player, "Força nível " + member.strength + " até o fim da dungeon.");
            }
            case "ult" -> {
                member.ult = true;
                say(player, "Você começa cada luta com a ult carregada.");
            }
            case "ascensao" -> {
                member.ascend = Math.min(spec.ascensaoMax(), member.ascend + 1);
                say(player, "Ascensão: você luta " + member.ascend + " tier(s) acima do tier da luta.");
            }
            default -> say(player, "Você seguiu em frente.");
        }
    }

    private static void trade(ServerPlayer player, List<Power> pool) {
        String current = player.getData(ModAttachments.POWER).power;
        List<Power> options = new ArrayList<>();
        for (Power power : pool) {
            if (!power.id().equals(current)) {
                options.add(power);
            }
        }
        if (options.isEmpty()) {
            return;
        }
        Power chosen = options.get(player.getRandom().nextInt(options.size()));
        PowerLogic.select(player, chosen);
        SoulSync.completeAll(player, chosen);
    }

    // ---- events

    // The player dies in a run: no death. Everything heals and a life goes. Alone, the same fight starts again; with a partner still
    // in the fight, the player is put back at the start of the arena and the fight goes on. Out of lives, the player leaves the run.
    private static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Run run = RUNS.get(player.getUUID());
        Member member = member(player);
        if (run == null || member == null) {
            return;
        }
        event.setCanceled(true);
        player.setHealth(player.getMaxHealth());
        player.clearFire();
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0;
        if (member.choosing || run.between) {
            // between fights there is nothing to lose
            return;
        }
        member.lives--;
        if (member.lives <= 0) {
            leave(player, run.members.size() > 1 ? "Suas vidas acabaram. O grupo segue sem você." : "Suas vidas acabaram.");
            return;
        }
        say(player, "Você caiu! Vidas restantes: " + member.lives);
        if (run.members.size() == 1) {
            com.starkinhon.battlegroundsmod.power.powers.gojo.InfiniteVoid.abortFor(player);
            ChallengeArenas.dungeonStop(player);
            if (run.stage == 0 && run.etapas.size() > 1) {
                // Dying on stage 1 swaps the killer into stage 2 so you face another opponent first.
                Collections.swap(run.etapas, 0, 1);
            }
            if (!startStage(run)) {
                endAll(run, "Não foi possível montar a arena.");
            }
            return;
        }
        com.starkinhon.battlegroundsmod.power.powers.gojo.InfiniteVoid.abortFor(player);
        ChallengeArenas.revive(player);
        // a moment of full protection at the start of the arena
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 4, false, false, false));
    }

    // A player whose game closed in the middle of a run gets the real character back.
    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !RUNS.containsKey(player.getUUID())
                && player.getData(ModAttachments.DUNGEON_BACKUP).active()) {
            restore(player);
            say(player, "A dungeon foi encerrada porque você saiu do jogo. Seu personagem voltou ao mundo.");
        }
    }

    // A player who leaves the game leaves the run (the Backup puts the character back at the next login); the group goes on.
    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Run run = RUNS.remove(player.getUUID());
            if (run != null) {
                run.members.remove(player.getUUID());
                if (run.between) {
                    next(run);
                }
            }
        }
    }

    // The run's lives and etapa on the action bar, once a second.
    private static void onServerTick(ServerTickEvent.Post event) {
        if (RUNS.isEmpty() || event.getServer().getTickCount() % 20 != 0) {
            return;
        }
        MinecraftServer server = event.getServer();
        int total = DungeonSpec.get().etapas().size();
        for (Map.Entry<UUID, Run> entry : new ArrayList<>(RUNS.entrySet())) {
            Run run = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                RUNS.remove(entry.getKey());
                run.members.remove(entry.getKey());
                continue;
            }
            Member member = run.members.get(entry.getKey());
            if (member != null && !member.choosing && !run.between && ChallengeArenas.isArena(player.level().dimension())) {
                player.displayClientMessage(Component.literal("Dungeon " + (run.stage + 1) + "/" + total + "   Vidas: "
                        + "♥".repeat(Math.max(0, member.lives)) + (run.members.size() > 1 ? "   Grupo: " + run.members.size() : "")), true);
            }
        }
    }

    // ---- helpers

    private static void giveSeeds(ServerPlayer player, int count) {
        if (count <= 0) {
            return;
        }
        ItemStack seeds = new ItemStack(ModItems.SENZU.get(), count);
        if (!player.getInventory().add(seeds)) {
            player.drop(seeds, false);
        }
    }

    private static Component bossName(DungeonSpec.Etapa etapa) {
        Power power = Powers.get(etapa.alma());
        return power != null ? power.name() : Component.literal(etapa.boss());
    }

    private static String currentSoulName(ServerPlayer player) {
        Power power = Powers.get(player.getData(ModAttachments.POWER).power);
        return power == null ? "" : power.name().getString();
    }

    private static void say(ServerPlayer player, String text) {
        player.displayClientMessage(Component.literal(text), false);
    }

    private static void title(ServerPlayer player, Component big, Component small) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(big));
        player.connection.send(new ClientboundSetSubtitleTextPacket(small));
    }
}
