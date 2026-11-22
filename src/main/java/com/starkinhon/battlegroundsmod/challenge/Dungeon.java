// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
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
import com.starkinhon.battlegroundsmod.power.SkillSwap;
import com.starkinhon.battlegroundsmod.combat.Fx;
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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
// DungeonSpec.sorteio; in the Caos mode the divine souls are shuffled among themselves too, DungeonSpec.caos), at an ADAPTED tier (the same for the player and the boss: Tier 10 down to
// Tier 0). The player has "vidas" deaths for the whole run; a death heals everything and restarts that fight. Block destruction is
// off in the arenas while a run is on (protectsBlocks).
//
// Two modes (Stark 2026-11-19), picked by each player on arriving in the first arena (the countdown waits for everyone):
// - Normal, "no seco": no stat cards at all. A win offers the boss's soul (absorb or refuse), then always the same 3 cards:
//   Semente dos Deuses, Alma Aleatoria and Alma Escolhida (any soul already beaten in this run).
// - Caos: the same soul offer, then 3 crazy cards (CHAOS): above all "Roubar Tecnica", one of the beaten boss's Z, X or C on the same
//   key of the player's own soul (power/SkillSwap: the skills mix, the soul itself is never touched), plus the Roleta (Z/X/C/B/V drawn
//   from any soul every fight, sometimes a Shift too), the Troca de Corpos (the next boss's soul for its own fight), the Edo Tensei (the next fall gets back
//   up with a blast, no life lost), Dobro ou Nada (a clean win = two cards, a fall = two lives) and Tita (giant until the end).
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
    // The Normal mode's cards: always these three ("escolhida" turns into "vida" or "manter" while no other soul was beaten yet).
    public static final List<String> NORMAL = List.of("senzu", "acaso", "escolhida");
    // The Caos mode's cards ("roubar" comes first whenever it can, the others are drawn).
    public static final List<String> CHAOS = List.of("roubar", "roleta", "ginyu", "edo", "aposta", "tita", "acaso");
    // Montar Kit (Stark 2026-11-20): an extra Caos card that always comes with the drawn ones, whenever the beaten boss has a skill to
    // take. Any of its skills (Z, X, C, B or a Shift variant of those) goes on any key of the player's kit (Z, X, C, B, Shift+Z/X/C/B),
    // so fight after fight the player builds a kit of their own with up to five skills plus the Shift variants. Its V (and Shift+V)
    // too, if the player wants it (Stark 2026-11-20): only on V/Shift+V, over the soul's own ult. The Roleta can
    // still come and stays as it is: a Z/X/C built here is out of its draw, like a stolen one.
    public static final String KIT = "kit";
    // B skills that are the owner's own transformation with its own bar: on another soul they would do nothing.
    private static final java.util.Set<String> KIT_BLOCKED = java.util.Set.of("rubber_boy:7", "goku:7");

    // Tita: the player's size while the card lasts (transient, so it never outlives the session).
    private static final ResourceLocation TITAN = ResourceLocation.fromNamespaceAndPath("battlegroundsmod", "dungeon_tita");
    private static final double TITAN_SCALE = 0.75;

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
        Origin origin;
        // the mode: answered once, on arriving in the first arena
        boolean modeChosen;
        boolean chaos;
        // the soul of the character just beaten (Roubar Tecnica takes from it) and the souls Alma Escolhida offers now
        String boss = "";
        List<String> souls = List.of();
        // Caos: the keys holding a stolen skill (Roubar Tecnica, Montar Kit: any SkillSwap.TARGETS key); the Roleta never draws over them
        final java.util.Set<Integer> stolen = new java.util.HashSet<>();
        boolean roulette;
        boolean ginyu;
        boolean edo;
        boolean bet;
        boolean titan;
        // fell in the current fight (Dobro ou Nada), cards still to pick after this one (won with Dobro ou Nada)
        boolean fell;
        int bonus;
    }

    // One run (memory only: a restart ends it and the Backup puts the character back at the next login). Shared by its players.
    private static final class Run {
        MinecraftServer server;
        int stage;
        // the etapas of THIS run, in the order drawn at its start (DungeonSpec.sorteio)
        List<DungeonSpec.Etapa> etapas = List.of();
        String lastArena = "";
        // the souls (power ids) of the characters beaten so far, in order
        final List<String> beaten = new ArrayList<>();
        // true from a won fight until the next one starts (everyone is choosing, or waiting for the others)
        boolean between;
        // Caos: the divine souls at the end were already shuffled (once per run, when the first player picks Caos)
        boolean divineShuffled;
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
        if (ChallengeArenas.isArena(player.level().dimension()) || ChallengeArenas.inFight(player)
                || com.starkinhon.battlegroundsmod.intro.Intro.active(player)) {
            say(player, "Saia da arena antes de começar uma dungeon.");
            return;
        }
        List<ServerPlayer> team = new ArrayList<>();
        team.add(player);
        for (ServerPlayer mate : ArenaParty.others(player)) {
            if (RUNS.containsKey(mate.getUUID()) || !mate.isAlive() || mate.isSpectator()
                    || ChallengeArenas.isArena(mate.level().dimension()) || ChallengeArenas.inFight(mate)
                    || com.starkinhon.battlegroundsmod.intro.Intro.active(mate)) {
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
                    Component.literal("Do mais fraco ao mais forte."));
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

    // ChallengeArenas: the fight could not go on (the boss could not be spawned or kept vanishing). The run ends for these players
    // with their real character back, never with them left in the world as the run's character.
    static void fightBroken(List<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            if (RUNS.containsKey(player.getUUID())) {
                leave(player, "A luta da dungeon não pôde continuar.");
            }
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
                Component.literal("Você derrotou o Deus da Destruição."));
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

    // Also the playable introduction's (intro/Intro): the same snapshot of the real character.
    public static Backup capture(ServerPlayer player) {
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
        // the run has no Voo: Minecraft keeps mayfly saved with the player, so the real character's flight stayed on (Stark
        // 2026-11-19, "to conseguindo voar no modo dungeons") once the technique was cleared above
        com.starkinhon.battlegroundsmod.universe.Flight.revoke(player);
        SkillSwap.clear(player);
        titan(player, false);
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
        player.setData(ModAttachments.DUNGEON_BACKUP, Backup.EMPTY);
        putBack(player, b);
    }

    // The saved character back on the player (inventory, soul, Sincronia, tier, where it was). Also the introduction's.
    public static void putBack(ServerPlayer player, Backup b) {
        // an Izanami running in the arena must not pull the real character back into it (nor keep it hidden or held)
        com.starkinhon.battlegroundsmod.power.powers.uchiha.Izanami.release(player);
        CombatMode.leave(player);
        // nothing of a run's Caos stays on the real character
        SkillSwap.clear(player);
        titan(player, false);
        // an expansion still open in the arena must not pull the real character back into it (DomainSpace.release)
        com.starkinhon.battlegroundsmod.power.powers.DomainSpace.release(player, false);
        player.removeAllEffects();
        player.getInventory().clearContent();
        for (int i = 0; i < Math.min(b.items().size(), player.getInventory().getContainerSize()); i++) {
            player.getInventory().setItem(i, b.items().get(i).copy());
        }
        player.setData(ModAttachments.POWER, b.power());
        // the saved PowerData still remembers what the watchers were told before the run/intro (no outfit, no aura): the
        // lent soul's outfit stayed drawn on the player after skipping the intro (Stark 2026-11-20, Madara's armor with
        // no soul). Told again from scratch.
        b.power().resendState();
        player.setData(ModAttachments.STOLEN_SOULS, b.stolen());
        player.setData(ModAttachments.TECHNIQUES, b.techniques());
        player.setData(ModAttachments.SOUL_SYNC, b.sync());
        player.setData(ModAttachments.SOUL_FED, b.fed());
        // a real character without the Voo leaves with no flight; one who knows it gets it back from Flight's own tick
        com.starkinhon.battlegroundsmod.universe.Flight.revoke(player);
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
            member.fell = false;
            if (member.ginyu) {
                // Troca de Corpos: this fight with the soul of the one about to be fought
                member.ginyu = false;
                Power body = Powers.get(etapa.alma());
                if (body != null) {
                    PowerLogic.select(player, body);
                    freshKit(player, member);
                    SoulSync.completeAll(player, body);
                    title(player, Component.literal("TROCA DE CORPOS").withStyle(s -> s.withColor(0xB04CFF).withBold(true)),
                            Component.literal("Agora você é " + body.name().getString() + "."));
                }
            }
            if (member.roulette) {
                spinRoulette(player, member);
            }
            PowerData data = player.getData(ModAttachments.POWER);
            data.tier = etapa.tier();
            data.ult = 0f;
            data.stamina = -1;
            data.exhaustedUntil = 0;
            Tiers.apply(player);
            player.setHealth(player.getMaxHealth());
            player.getFoodData().setFoodLevel(20);
            player.clearFire();
            player.fallDistance = 0;
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
        boolean offered = Powers.get(etapa.alma()) != null;
        if (offered && !run.beaten.contains(etapa.alma())) {
            run.beaten.add(etapa.alma());
        }
        // The soul is an offer, not a gift: each player absorbs it or refuses it (soul()), then gets the cards (pick()).
        for (ServerPlayer player : team) {
            Member member = run.members.get(player.getUUID());
            member.pendingSoul = offered ? etapa.alma() : "";
            member.boss = offered ? etapa.alma() : "";
            member.offer = List.of();
            member.choosing = true;
            if (member.bet) {
                member.bet = false;
                if (!member.fell) {
                    member.bonus++;
                    say(player, "Dobro ou Nada: vitória limpa. Você escolhe duas cartas.");
                }
            }
            title(player, Component.literal("VITÓRIA").withStyle(s -> s.withColor(0xF2C94C).withBold(true)),
                    Component.literal(offered ? "A alma de " + bossName(etapa).getString() + " está disponível." : "Escolha."));
            if (offered) {
                send(player, run, member, false);
            } else {
                deal(player, run, member, false);
            }
        }
    }

    // The screen's state for this player: the soul to answer (offer) or the cards (bonus: another card comes after this one).
    private static void send(ServerPlayer player, Run run, Member member, boolean bonus) {
        DungeonSpec spec = DungeonSpec.get();
        PacketDistributor.sendToPlayer(player, new ModPayloads.DungeonChoices(member.chaos, member.offer, member.lives, run.stage,
                spec.etapas().size(), player.getData(ModAttachments.POWER).power, member.pendingSoul, member.boss, member.souls,
                member.bonus > 0 && member.pendingSoul.isEmpty()));
    }

    // Draws this player's cards (after the soul's answer) and shows them.
    private static void deal(ServerPlayer player, Run run, Member member, boolean bonus) {
        DungeonSpec spec = DungeonSpec.get();
        String current = player.getData(ModAttachments.POWER).power;
        List<String> souls = new ArrayList<>();
        for (String id : run.beaten) {
            if (!id.equals(current) && Powers.get(id) != null) {
                souls.add(id);
            }
        }
        member.souls = List.copyOf(souls);
        member.offer = member.chaos ? drawChaos(player, run, member, spec) : drawNormal(member, spec);
        send(player, run, member, bonus);
    }

    // Normal: Semente, Alma Aleatoria and Alma Escolhida (a life, or nothing, while no other soul was beaten yet).
    private static List<String> drawNormal(Member member, DungeonSpec spec) {
        List<String> cards = new ArrayList<>(NORMAL);
        if (member.souls.isEmpty()) {
            cards.set(2, member.lives < spec.vidasMax() ? "vida" : "manter");
        }
        return cards;
    }

    // Caos: Roubar Tecnica first whenever it can, the others drawn among the ones that still make sense.
    private static List<String> drawChaos(ServerPlayer player, Run run, Member member, DungeonSpec spec) {
        int count = Math.max(1, spec.opcoesPorEscolha());
        Power own = Powers.get(player.getData(ModAttachments.POWER).power);
        List<String> cards = new ArrayList<>();
        if (canSteal(own, member)) {
            cards.add("roubar");
        }
        List<String> pool = new ArrayList<>();
        for (String id : CHAOS) {
            boolean ok = switch (id) {
                case "roubar" -> false;
                // picking it again wipes the stolen keys and draws everything again (Stark 2026-11-20)
                case "roleta" -> own != null && !member.roulette;
                case "ginyu" -> !member.ginyu && run.stage < run.etapas.size()
                        && Powers.get(run.etapas.get(run.stage).alma()) != null
                        && !run.etapas.get(run.stage).alma().equals(own == null ? "" : own.id());
                case "edo" -> !member.edo;
                case "aposta" -> !member.bet && member.lives >= 2;
                case "tita" -> !member.titan;
                default -> true;
            };
            if (ok) {
                pool.add(id);
            }
        }
        Collections.shuffle(pool);
        for (String id : pool) {
            if (cards.size() >= count) {
                break;
            }
            cards.add(id);
        }
        while (cards.size() < count) {
            cards.add(cards.contains("senzu") ? "manter" : "senzu");
        }
        if (canKit(own, member)) {
            // the fixed extra card, always last
            cards.add(KIT);
        }
        return cards;
    }

    // A skill of the beaten boss Montar Kit can take: a TARGETS slot it has, not one of the blocked transformations.
    public static boolean kitSource(Power boss, int slot) {
        return boss != null && SkillSwap.swappable(slot) && PowerLogic.ability(boss, slot) != null
                && !KIT_BLOCKED.contains(boss.id() + ":" + slot);
    }

    // Montar Kit makes sense: the player has a soul and the one just beaten is another one with at least one skill to take.
    private static boolean canKit(Power own, Member member) {
        Power boss = Powers.get(member.boss);
        if (own == null || boss == null || boss.id().equals(own.id())) {
            return false;
        }
        for (int slot : SkillSwap.TARGETS) {
            if (kitSource(boss, slot)) {
                return true;
            }
        }
        return false;
    }

    // Roubar Tecnica makes sense: the player has a soul and the one just beaten is another one with a Z, X or C.
    private static boolean canSteal(Power own, Member member) {
        Power boss = Powers.get(member.boss);
        if (own == null || boss == null || boss.id().equals(own.id())) {
            return false;
        }
        for (int k = 0; k < SkillSwap.KEYS; k++) {
            if (PowerLogic.ability(boss, k) != null) {
                return true;
            }
        }
        return false;
    }

    // From the client, once, on arriving in the first arena: Normal or Caos. The countdown starts when every player answered.
    public static void mode(ServerPlayer player, boolean chaos) {
        Member member = member(player);
        if (member == null || member.modeChosen) {
            return;
        }
        member.modeChosen = true;
        member.chaos = chaos;
        title(player, Component.literal(chaos ? "MODO CAOS" : "MODO NORMAL").withStyle(s -> s.withColor(chaos ? 0xB04CFF : 0xF2C94C)
                .withBold(true)), Component.literal(chaos ? "Que comece a bagunça." : "No seco. Só você e a alma."));
        Fx.sound(player.serverLevel(), player.position(), chaos ? SoundEvents.WITHER_SPAWN : SoundEvents.BEACON_ACTIVATE, 0.5f,
                chaos ? 1.6f : 1.2f);
        Run run = RUNS.get(player.getUUID());
        if (run != null && chaos && !run.divineShuffled) {
            // Caos (Stark 2026-11-20): the divine souls at the end come in a random order too. Once per run, the fight already set
            // up stays as it is. A group with one Caos player gets it for everyone (the fights are shared).
            run.divineShuffled = true;
            DungeonSpec.caos(run.etapas, run.stage + 1, new java.util.Random());
        }
        if (run != null && run.members.size() > 1) {
            for (ServerPlayer other : online(run)) {
                if (other != player) {
                    say(other, player.getDisplayName().getString() + " escolheu o modo " + (chaos ? "Caos." : "Normal."));
                }
            }
        }
    }

    // ChallengeArenas: the players were just put in the arena. The ones who have not picked the mode yet get the screen for it.
    static void arrived(List<ServerPlayer> players) {
        DungeonSpec spec = DungeonSpec.get();
        for (ServerPlayer player : players) {
            Run run = RUNS.get(player.getUUID());
            Member member = member(player);
            if (run != null && member != null && !member.modeChosen) {
                PacketDistributor.sendToPlayer(player, new ModPayloads.DungeonModeAsk(member.lives, spec.etapas().size(), run.members.size()));
            }
        }
    }

    // ChallengeArenas: the countdown of a run's fight waits while one of its players is still picking the mode.
    static boolean waitingMode(List<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            Member member = member(player);
            if (member != null && !member.modeChosen) {
                return true;
            }
        }
        return false;
    }

    // From the client: absorb the boss's soul (take) or refuse it. Absorbing puts it in with the whole kit awake and replaces the
    // soul the player carried; refusing keeps the one they have (or none, if they have none yet). Then the cards.
    public static void soul(ServerPlayer player, boolean take) {
        Run run = RUNS.get(player.getUUID());
        Member member = member(player);
        if (run == null || member == null || !member.choosing || member.pendingSoul.isEmpty()) {
            return;
        }
        Power soul = Powers.get(member.pendingSoul);
        member.pendingSoul = "";
        if (take && soul != null) {
            PowerLogic.select(player, soul);
            freshKit(player, member);
            SoulSync.completeAll(player, soul);
            say(player, "Você absorveu a alma de " + soul.name().getString() + ".");
        } else if (soul != null) {
            say(player, "Você recusou a alma de " + soul.name().getString() + ".");
        }
        deal(player, run, member, false);
    }

    // From the client: the card picked (an index of the offer) and its second choice (arg: the key stolen, the soul chosen). The
    // next fight starts when every player of the run has picked.
    public static void pick(ServerPlayer player, int index, int arg) {
        Run run = RUNS.get(player.getUUID());
        Member member = member(player);
        if (run == null || member == null || !member.choosing || !member.pendingSoul.isEmpty() || index < 0
                || index >= member.offer.size()) {
            return;
        }
        DungeonSpec spec = DungeonSpec.get();
        String id = member.offer.get(index);
        if (!apply(player, run, member, spec, id, arg)) {
            // a second choice that does not hold (an old screen): the same cards again
            send(player, run, member, member.bonus > 0);
            return;
        }
        if (member.bonus > 0) {
            member.bonus--;
            deal(player, run, member, true);
            return;
        }
        member.choosing = false;
        member.offer = List.of();
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

    // False when the card's second choice is not valid (nothing happens then).
    private static boolean apply(ServerPlayer player, Run run, Member member, DungeonSpec spec, String id, int arg) {
        switch (id) {
            case "acaso" -> {
                // any soul of the game, SubAlma or divine
                List<Power> all = new ArrayList<>(Powers.sub());
                all.addAll(Powers.divine());
                trade(player, all);
                freshKit(player, member);
            }
            case "senzu" -> {
                giveSeeds(player, spec.sementesPremio());
                say(player, "+" + spec.sementesPremio() + " Sementes dos Deuses.");
            }
            case "vida" -> {
                member.lives = Math.min(spec.vidasMax(), member.lives + 1);
                say(player, "Vida extra! Vidas: " + member.lives);
            }
            case "escolhida" -> {
                if (arg < 0 || arg >= member.souls.size()) {
                    return false;
                }
                Power chosen = Powers.get(member.souls.get(arg));
                if (chosen == null) {
                    return false;
                }
                PowerLogic.select(player, chosen);
                freshKit(player, member);
                SoulSync.completeAll(player, chosen);
                say(player, "Você escolheu a alma de " + chosen.name().getString() + ".");
            }
            case "roubar" -> {
                Power boss = Powers.get(member.boss);
                if (arg < 0 || arg >= SkillSwap.KEYS || boss == null || !canSteal(Powers.get(player.getData(ModAttachments.POWER).power), member)
                        || !SkillSwap.set(player, arg, boss, arg)) {
                    return false;
                }
                member.stolen.add(arg);
                // taking a skill ends the Roleta (Stark 2026-11-20: "aleatoriza tudo até você roubar técnica")
                endRoulette(player, member);
                Fx.burst(player.serverLevel(), Fx.dust(boss.color(), 2f), player.position().add(0, 1, 0), 70, 0.7, 0.05);
                Fx.burst(player.serverLevel(), ParticleTypes.REVERSE_PORTAL, player.position().add(0, 1, 0), 50, 0.5, 0.2);
                Fx.sound(player.serverLevel(), player.position(), SoundEvents.ILLUSIONER_MIRROR_MOVE, 1f, 0.7f);
                title(player, Component.literal("ROUBADO").withStyle(s -> s.withColor(boss.color()).withBold(true)),
                        Component.literal(boss.abilityName(arg).getString() + " agora é seu " + KEY_NAMES[arg] + "."));
            }
            case KIT -> {
                // arg: the boss's slot * 16 + the player's key
                Power boss = Powers.get(member.boss);
                int from = arg >> 4, to = arg & 15;
                if (arg < 0 || !SkillSwap.swappable(to) || !kitSource(boss, from)
                        || !canKit(Powers.get(player.getData(ModAttachments.POWER).power), member) || !SkillSwap.set(player, to, boss, from)) {
                    return false;
                }
                // out of the Roleta's draw, like a stolen key (any kit key, B and Shifts too: the Roleta must skip every key in
                // member.stolen)
                member.stolen.add(to);
                // taking a skill ends the Roleta (Stark 2026-11-20: "aleatoriza tudo até você roubar técnica")
                endRoulette(player, member);
                Fx.burst(player.serverLevel(), Fx.dust(boss.color(), 2f), player.position().add(0, 1, 0), 70, 0.7, 0.05);
                Fx.burst(player.serverLevel(), ParticleTypes.REVERSE_PORTAL, player.position().add(0, 1, 0), 50, 0.5, 0.2);
                Fx.sound(player.serverLevel(), player.position(), SoundEvents.ILLUSIONER_MIRROR_MOVE, 1f, 0.7f);
                title(player, Component.literal("NO SEU KIT").withStyle(s -> s.withColor(boss.color()).withBold(true)),
                        Component.literal(boss.abilityName(from).getString() + " agora é seu " + keyLabel(to) + "."));
            }
            case "roleta" -> {
                // the Roleta takes over every key again: the stolen ones (Roubar, Montar Kit) go back into the draw (Stark
                // 2026-11-20: "se escolher roleta de novo, ela força a trocar tudo de novo")
                member.stolen.clear();
                member.roulette = true;
                say(player, "Roleta: a cada luta todas as suas técnicas mudam, até você cair ou roubar uma técnica.");
            }
            case "ginyu" -> {
                member.ginyu = true;
                say(player, "Troca de Corpos: na próxima luta você entra com a alma de quem vai enfrentar.");
            }
            case "edo" -> {
                member.edo = true;
                say(player, "Edo Tensei: na próxima vez que cair, você levanta na hora.");
            }
            case "aposta" -> {
                member.bet = true;
                say(player, "Dobro ou Nada: vença sem cair e escolha duas cartas. Se cair, perde duas vidas.");
            }
            case "tita" -> {
                member.titan = true;
                titan(player, true);
                Fx.burst(player.serverLevel(), ParticleTypes.EXPLOSION, player.position().add(0, 1, 0), 6, 0.8, 0);
                Fx.sound(player.serverLevel(), player.position(), SoundEvents.RAVAGER_ROAR, 1f, 0.6f);
                say(player, "Titã: você ficou gigante até o fim da dungeon.");
            }
            default -> say(player, "Você seguiu em frente.");
        }
        return true;
    }

    private static final String[] KEY_NAMES = {"Z", "X", "C"};

    // How many of Z, X, C hold a stolen/kit skill (B and the Shifts in member.stolen don't count for the Roleta's card).
    private static int stolenZxc(Member member) {
        int n = 0;
        for (int k = 0; k < SkillSwap.KEYS; k++) {
            if (member.stolen.contains(k)) {
                n++;
            }
        }
        return n;
    }

    // "Z", "B", "V", "Shift+X"... for a TARGETS key.
    public static String keyLabel(int slot) {
        int base = PowerData.baseSlot(slot);
        String key = base == PowerData.SLOT_EXTRA ? "B" : base == PowerData.SLOT_ULT ? "V"
                : base >= 0 && base < KEY_NAMES.length ? KEY_NAMES[base] : "?";
        return slot >= PowerData.SLOT_SHIFT ? "Shift+" + key : key;
    }

    // A new soul (Troca de Corpos, absorbing or choosing a soul, Acaso) comes with its own kit: the drawn and stolen keys of the old
    // one go (Stark 2026-11-20: after the Troca de Corpos the Freeza still had the Roleta's and the stolen skills, "fica em
    // definitivo"). A Roleta still on draws the new soul's keys again at the start of the next fight.
    private static void freshKit(ServerPlayer player, Member member) {
        member.stolen.clear();
        SkillSwap.clear(player);
    }

    // The Roleta stops (Stark 2026-11-20): a fall, or taking a skill (Roubar Tecnica, Montar Kit). A change of soul does not stop
    // it any more. The skills it drew last stay on the keys for good; picking the card again draws every key again.
    private static void endRoulette(ServerPlayer player, Member member) {
        if (!member.roulette) {
            return;
        }
        member.roulette = false;
        say(player, "A Roleta parou. As técnicas que ela sorteou ficam com você.");
    }

    // Roleta: the five keys of the kit, Z, X, C, B and V, each get a skill of a random soul (never the player's own), and never the
    // one that key held in the last fight (Stark 2026-11-20: "força ela a trocar toda rodada"). Stark 2026-11-21: "te dá 5 skills
    // aleatórias, as 5" (before, a soul with its own B kept it and the V never changed), "e também ter chance de te dar algum shift,
    // uma chance baixa": now and then (ROLETA_SHIFT) one Shift key gets a Shift skill of another soul too (the next spin takes it off again).
    // Keys holding a stolen skill (Roubar Tecnica, Montar Kit) are never drawn over. Same sources as Montar Kit (kitSource: the
    // owner-only transformations stay out); an ult only on V/Shift+V, like SkillSwap.set demands.
    private static final double ROLETA_SHIFT = 0.2;
    private static final int[] ROLETA_KEYS = {0, 1, 2, PowerData.SLOT_EXTRA, PowerData.SLOT_ULT};
    private static final int[] ROLETA_SHIFTS = {PowerData.SLOT_SHIFT, PowerData.SLOT_SHIFT + 1, PowerData.SLOT_SHIFT + 2,
            PowerData.SLOT_SHIFT_EXTRA, PowerData.SLOT_SHIFT_ULT};

    private static void spinRoulette(ServerPlayer player, Member member) {
        Power own = Powers.get(player.getData(ModAttachments.POWER).power);
        if (own == null) {
            return;
        }
        List<Power> all = new ArrayList<>(Powers.sub());
        all.addAll(Powers.divine());
        all.removeIf(p -> p.id().equals(own.id()));
        // every skill that can be drawn, shuffled: normal (Z/X/C/B), ult (V), and the Shift variants of both
        List<SkillSwap.Ref> skills = new ArrayList<>();
        List<SkillSwap.Ref> ults = new ArrayList<>();
        List<SkillSwap.Ref> shiftSkills = new ArrayList<>();
        List<SkillSwap.Ref> shiftUlts = new ArrayList<>();
        for (Power p : all) {
            for (int from : ROLETA_KEYS) {
                if (kitSource(p, from)) {
                    (PowerData.isUlt(from) ? ults : skills).add(new SkillSwap.Ref(p, from));
                }
            }
            for (int from : ROLETA_SHIFTS) {
                if (kitSource(p, from)) {
                    (PowerData.isUlt(from) ? shiftUlts : shiftSkills).add(new SkillSwap.Ref(p, from));
                }
            }
        }
        if (skills.isEmpty()) {
            return;
        }
        java.util.Random random = new java.util.Random(player.getRandom().nextLong());
        Collections.shuffle(skills, random);
        Collections.shuffle(ults, random);
        Collections.shuffle(shiftSkills, random);
        Collections.shuffle(shiftUlts, random);
        Map<Integer, SkillSwap.Ref> before = SkillSwap.all(player);
        java.util.Set<SkillSwap.Ref> taken = new java.util.HashSet<>(before.values());
        // the Shift skill the Roleta gave in the last fight goes (it lasts one fight); a stolen Shift stays
        for (int key : ROLETA_SHIFTS) {
            if (!member.stolen.contains(key) && before.containsKey(key)) {
                SkillSwap.remove(player, key);
            }
        }
        StringBuilder line = new StringBuilder("Roleta:");
        for (int key : ROLETA_KEYS) {
            if (member.stolen.contains(key)) {
                continue;
            }
            SkillSwap.Ref pickRef = pick(PowerData.isUlt(key) ? ults : skills, taken, random);
            if (pickRef != null && SkillSwap.set(player, key, pickRef.power(), pickRef.slot())) {
                taken.add(pickRef);
                line.append("  ").append(keyLabel(key)).append(" ").append(pickRef.power().abilityName(pickRef.slot()).getString());
            }
        }
        if (random.nextDouble() < ROLETA_SHIFT) {
            // one Shift key that does not hold a stolen skill; Shift+V only takes a Shift ult
            List<Integer> free = new ArrayList<>();
            for (int key : ROLETA_SHIFTS) {
                if (!member.stolen.contains(key) && !(PowerData.isUlt(key) ? shiftUlts : shiftSkills).isEmpty()) {
                    free.add(key);
                }
            }
            if (!free.isEmpty()) {
                int key = free.get(random.nextInt(free.size()));
                SkillSwap.Ref pickRef = pick(PowerData.isUlt(key) ? shiftUlts : shiftSkills, taken, random);
                if (pickRef != null && SkillSwap.set(player, key, pickRef.power(), pickRef.slot())) {
                    line.append("  ").append(keyLabel(key)).append(" ").append(pickRef.power().abilityName(pickRef.slot()).getString())
                            .append(" (sorte!)");
                }
            }
        }
        say(player, line.toString());
    }

    // The first skill of the shuffled pool no key has now (so it is new on this key and not repeated on another one); only if every
    // one of them is already on a key, any of them may come back. Null for an empty pool.
    private static SkillSwap.Ref pick(List<SkillSwap.Ref> pool, java.util.Set<SkillSwap.Ref> taken, java.util.Random random) {
        if (pool.isEmpty()) {
            return null;
        }
        for (SkillSwap.Ref ref : pool) {
            if (!taken.contains(ref)) {
                return ref;
            }
        }
        return pool.get(random.nextInt(pool.size()));
    }

    // Tita on or off (a transient size: it never outlives the session, and putBack takes it off).
    private static void titan(ServerPlayer player, boolean on) {
        AttributeInstance scale = player.getAttribute(Attributes.SCALE);
        if (scale == null) {
            return;
        }
        scale.removeModifier(TITAN);
        if (on) {
            scale.addTransientModifier(new AttributeModifier(TITAN, TITAN_SCALE, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }

    // Edo Tensei: up again on the spot, full health, and a blast that throws back everything around but the run's players.
    private static void edoTensei(ServerPlayer player, Run run) {
        ServerLevel level = player.serverLevel();
        Vec3 at = player.position();
        Fx.burst(level, ParticleTypes.EXPLOSION_EMITTER, at.add(0, 1, 0), 2, 0.4, 0);
        Fx.burst(level, ParticleTypes.SOUL, at.add(0, 1, 0), 120, 1.6, 0.12);
        Fx.burst(level, ParticleTypes.LARGE_SMOKE, at.add(0, 0.3, 0), 90, 2.4, 0.08);
        Fx.burst(level, ParticleTypes.SCULK_SOUL, at.add(0, 1.5, 0), 40, 0.6, 0.15);
        Fx.sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 1.4f, 0.6f);
        Fx.sound(level, at, SoundEvents.WARDEN_SONIC_BOOM, 1f, 0.8f);
        for (net.minecraft.world.entity.LivingEntity hit : level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                player.getBoundingBox().inflate(8), e -> e != player && e.isAlive() && !(e instanceof ServerPlayer p && run.members.containsKey(p.getUUID())))) {
            Vec3 push = hit.position().subtract(at);
            double d = Math.max(0.5, push.length());
            push = push.normalize().scale(2.4 * (1 - Math.min(1, d / 9)) + 0.6);
            hit.hurt(player.damageSources().playerAttack(player), Math.max(8f, hit.getMaxHealth() * 0.06f));
            hit.setDeltaMovement(push.x, 0.7, push.z);
            hit.hurtMarked = true;
        }
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 4, false, false, false));
        title(player, Component.literal("EDO TENSEI").withStyle(s -> s.withColor(0x8FE3FF).withBold(true)),
                Component.literal("Você voltou."));
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
        if (member.edo) {
            // Edo Tensei: no life lost, the fight goes on right there
            member.edo = false;
            endRoulette(player, member);
            edoTensei(player, run);
            return;
        }
        member.fell = true;
        endRoulette(player, member);
        member.lives--;
        if (member.bet) {
            // Dobro ou Nada lost: a second life goes with the first
            member.bet = false;
            member.lives--;
            say(player, "Dobro ou Nada: você caiu e perdeu duas vidas.");
        }
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
        if (event.getEntity() instanceof ServerPlayer player) {
            // the client's copy of the stolen keys starts from the server's (empty outside a run)
            SkillSwap.sync(player);
        }
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
            if (!player.isCreative() && !player.isSpectator() && player.getAbilities().mayfly
                    && !com.starkinhon.battlegroundsmod.power.powers.sub.nr.Gaara.flying(player)) {
                // no flight in a run, whatever left it on (Gaara's sand platform is a skill of the soul and keeps its own)
                com.starkinhon.battlegroundsmod.universe.Flight.revoke(player);
            }
            // no hunger in a run (Stark 2026-11-21: "para a fome não atrapalhar"); putBack gives the real character a full bar too
            if (player.getFoodData().getFoodLevel() < 20 || player.getFoodData().getSaturationLevel() < 5f) {
                player.getFoodData().setFoodLevel(20);
                player.getFoodData().setSaturation(5f);
            }
            if (member != null && !member.choosing && !run.between && ChallengeArenas.isArena(player.level().dimension())) {
                player.displayClientMessage(Component.literal((member.chaos ? "Caos " : "Dungeon ") + (run.stage + 1) + "/" + total + "   Vidas: "
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

    private static void say(ServerPlayer player, String text) {
        player.displayClientMessage(Component.literal(text), false);
    }

    private static void title(ServerPlayer player, Component big, Component small) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(big));
        player.connection.send(new ClientboundSetSubtitleTextPacket(small));
    }
}
