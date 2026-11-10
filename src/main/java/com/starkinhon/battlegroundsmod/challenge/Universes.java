package com.starkinhon.battlegroundsmod.challenge;

import java.util.HashMap;
import java.util.Map;

import com.mojang.datafixers.util.Pair;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.ModItems;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.PowerLogic;
import com.starkinhon.battlegroundsmod.power.Progression;
import com.starkinhon.battlegroundsmod.power.Tiers;
import com.starkinhon.battlegroundsmod.skill.ChallengeSpec;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// The universes as worlds (plan "Souls: Multiverso e Progressão", Stark 2026-11-10). Each universe of the Livros dos
// Desafios (padrao.desafios.json) is its own dimension, battlegroundsmod:<id>, with its own biomes
// (data/battlegroundsmod/dimension/<id>.json). There is no first universe: the first time a player joins a normal world
// a screen asks where to live (client/UniverseScreen); the player is born there, human, Tier 10, with that universe's
// Livro dos Desafios. The Cristal Multiversal (item/MultiversalCrystalItem) takes them to another one; each universe
// remembers where the player was, so going back lands them there.
// A flat world is the fight/test mode: no universes, nothing asked (Progression.on).
public final class Universes {
    private static final String NONE = "";

    private Universes() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(Universes::onServerTick);
        NeoForge.EVENT_BUS.addListener(Universes::onLogin);
        NeoForge.EVENT_BUS.addListener(Universes::onRespawn);
        NeoForge.EVENT_BUS.addListener(Universes::onStopped);
    }

    public static ResourceKey<Level> key(String universe) {
        return ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, universe));
    }

    // The universe a dimension is, or null (overworld, Nether, End, arenas...).
    public static String universeOf(ResourceKey<Level> dimension) {
        if (!dimension.location().getNamespace().equals(BattlegroundsMod.MODID)) {
            return null;
        }
        String path = dimension.location().getPath();
        return ChallengeSpec.get().universe(path) != null ? path : null;
    }

    public static String home(Player player) {
        return player.getData(ModAttachments.UNIVERSE);
    }

    // Where the player lives now: their universe's world, or the overworld before choosing one.
    public static ServerLevel homeLevel(ServerPlayer player) {
        String home = home(player);
        ServerLevel level = home.isEmpty() ? null : player.server.getLevel(key(home));
        return level != null ? level : player.server.overworld();
    }

    public static Vec3 homePos(ServerPlayer player) {
        ServerLevel level = homeLevel(player);
        if (level == player.server.overworld()) {
            return Vec3.atBottomCenterOf(level.getSharedSpawnPos());
        }
        BlockPos saved = player.getData(ModAttachments.UNIVERSE_POS).get(home(player));
        return Vec3.atBottomCenterOf(saved != null ? saved : arrival(level));
    }

    // First time in a normal world: ask where to live. Repeated every few seconds until they choose (the screen can be
    // lost while the world is still loading).
    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Progression.sync(player);
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 60 != 0 || !Progression.on(event.getServer())) {
            return;
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (home(player).isEmpty() && player.tickCount > 40 && player.isAlive()) {
                PacketDistributor.sendToPlayer(player, new ModPayloads.OpenUniverses(true));
            }
        }
    }

    // From the screen: the player chose a universe (first time, or with a Cristal Multiversal in hand).
    public static void pick(ServerPlayer player, String universe) {
        if (!Progression.on(player) || ChallengeSpec.get().universe(universe) == null) {
            return;
        }
        if (home(player).isEmpty()) {
            travel(player, universe, true);
            return;
        }
        if (universe.equals(universeOf(player.level().dimension()))) {
            return;
        }
        ItemStack crystal = crystalInHand(player);
        if (crystal == null) {
            player.displayClientMessage(Component.translatable("message.battlegroundsmod.universe_need_crystal"), true);
            return;
        }
        if (travel(player, universe, false) && !player.getAbilities().instabuild) {
            crystal.shrink(1);
        }
    }

    private static ItemStack crystalInHand(ServerPlayer player) {
        Item crystal = ModItems.MULTIVERSAL_CRYSTAL.get();
        for (ItemStack stack : new ItemStack[] {player.getMainHandItem(), player.getOffhandItem()}) {
            if (stack.is(crystal)) {
                return stack;
            }
        }
        return null;
    }

    // Takes the player to that universe: where they left it, or its arrival point the first time. That becomes their
    // home (respawn point, the bar's name, where the arenas send them back).
    public static boolean travel(ServerPlayer player, String universe, boolean born) {
        ChallengeSpec.Universe spec = ChallengeSpec.get().universe(universe);
        ServerLevel level = spec == null ? null : player.server.getLevel(key(universe));
        if (level == null) {
            player.displayClientMessage(Component.translatable("message.battlegroundsmod.universe_missing", universe), false);
            return false;
        }
        Map<String, BlockPos> positions = new HashMap<>(player.getData(ModAttachments.UNIVERSE_POS));
        String from = universeOf(player.level().dimension());
        if (from != null) {
            positions.put(from, player.blockPosition());
        }
        BlockPos saved = positions.get(universe);
        BlockPos arrival = arrival(level);
        BlockPos pos = saved != null ? saved : arrival;
        player.setData(ModAttachments.UNIVERSE_POS, Map.copyOf(positions));
        player.setData(ModAttachments.UNIVERSE, universe);
        if (born) {
            bornHuman(player);
        }
        player.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYRot(), 0);
        player.setRespawnPosition(level.dimension(), arrival, 0, true, false);
        giveBook(player, universe);
        if (saved == null) {
            // Only when landing at the arrival point: elsewhere its chunks may be unloaded and the NPCs not found.
            com.starkinhon.battlegroundsmod.npc.Npcs.ensure(level, universe, arrival);
        }
        Progression.sync(player);
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(spec.nome())
                .withStyle(s -> s.withColor(spec.color()).withBold(true))));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable(born
                ? "message.battlegroundsmod.universe_born" : "message.battlegroundsmod.universe_arrived")));
        Vec3 at = player.position().add(0, 1, 0);
        Fx.burst(level, ParticleTypes.REVERSE_PORTAL, at, 120, 0.8, 0.4);
        Fx.sound(level, at, SoundEvents.END_PORTAL_SPAWN, 0.5f, 1.6f);
        return true;
    }

    // Being born: human, Tier 10, empty bar, no soul and no Livro das Almas, only the basic attacks (Stark 2026-11-10).
    private static void bornHuman(ServerPlayer player) {
        PowerData data = player.getData(ModAttachments.POWER);
        if (!data.power.isEmpty()) {
            PowerLogic.clear(player);
        }
        data.tier = Tiers.HUMANO;
        data.poder = 0;
        Tiers.apply(player);
        Item book = ModItems.POWER_BOOK.get();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(book)) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
        PowerLogic.sync(player);
    }

    private static void giveBook(ServerPlayer player, String universe) {
        var book = ModItems.CHALLENGE_BOOKS.get(universe);
        if (book == null || player.getInventory().contains(new ItemStack(book.get()))) {
            return;
        }
        ItemStack stack = new ItemStack(book.get());
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    // Arrival point of each universe, found once per server run (the search loads chunks).
    private static final Map<ResourceKey<Level>, BlockPos> ARRIVALS = new HashMap<>();

    // The universe's arrival point: dry ground near its center (the same for every player, so friends land together).
    // The biome noise finds the nearest land without generating anything; only that chunk is then loaded for real,
    // so its heightmap is the true ground (an unloaded chunk reads as the bottom of the world: the void, PR #269).
    public static BlockPos arrival(ServerLevel level) {
        BlockPos cached = ARRIVALS.get(level.dimension());
        if (cached != null) {
            return cached;
        }
        if (level.getChunkSource().getGenerator() instanceof com.starkinhon.battlegroundsmod.world.MapChunkGenerator drawn) {
            // A drawn universe: its map says where to land.
            com.starkinhon.battlegroundsmod.world.WorldMap map = drawn.map();
            BlockPos at = ground(level, map.arrivalX, map.arrivalZ);
            ARRIVALS.put(level.dimension(), at);
            return at;
        }
        BlockPos center = new BlockPos(0, 64, 0);
        Pair<BlockPos, Holder<Biome>> land = level.findClosestBiome3d(biome -> !biome.is(BiomeTags.IS_OCEAN)
                && !biome.is(BiomeTags.IS_DEEP_OCEAN) && !biome.is(BiomeTags.IS_RIVER) && !biome.is(BiomeTags.IS_BEACH),
                center, 2048, 32, 64);
        BlockPos start = land != null ? land.getFirst() : center;
        BlockPos found = null;
        // A few tries around it, in case that exact column is a lake.
        for (int i = 0; i < 9 && found == null; i++) {
            int x = start.getX() + (i % 3 - 1) * 16 * (i == 0 ? 0 : 1);
            int z = start.getZ() + (i / 3 - 1) * 16 * (i == 0 ? 0 : 1);
            BlockPos top = ground(level, x, z);
            if (level.getFluidState(top).isEmpty() && level.getFluidState(top.below()).isEmpty()) {
                found = top;
            }
        }
        if (found == null) {
            found = ground(level, start.getX(), start.getZ());
        }
        ARRIVALS.put(level.dimension(), found);
        return found;
    }

    private static BlockPos ground(ServerLevel level, int x, int z) {
        LevelChunk chunk = level.getChunk(x >> 4, z >> 4);
        int y = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15) + 1;
        if (y <= level.getMinBuildHeight() + 1) {
            // No ground at all in this column: a safe platform instead of the void.
            y = 80;
            level.setBlockAndUpdate(new BlockPos(x, y - 1, z), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        }
        return new BlockPos(x, y, z);
    }

    private static void onStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        ARRIVALS.clear();
    }

    // Safety net: a player with a universe who respawns in the overworld (bed broken, respawn point lost) goes home.
    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.isEndConquered() || !(event.getEntity() instanceof ServerPlayer player) || home(player).isEmpty()
                || !Progression.on(player) || !player.level().dimension().equals(Level.OVERWORLD)) {
            return;
        }
        ServerLevel level = homeLevel(player);
        if (level == player.server.overworld()) {
            return;
        }
        BlockPos pos = arrival(level);
        player.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYRot(), 0);
        player.setRespawnPosition(level.dimension(), pos, 0, true, false);
    }

    // /soul universo: where you live. /soul universo <id> [jogador] (ops): go there without a crystal.
    // /soul universo zerar [jogador] (ops): forget the choice, so the screen asks again.
    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("universo")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    String home = home(player);
                    ChallengeSpec.Universe spec = home.isEmpty() ? null : ChallengeSpec.get().universe(home);
                    context.getSource().sendSuccess(() -> Component.translatable("message.battlegroundsmod.universe_is",
                            spec != null ? spec.nome() : "-"), false);
                    return 1;
                })
                .then(Commands.literal("zerar").requires(source -> source.hasPermission(2))
                        .executes(context -> forget(context.getSource(), context.getSource().getPlayerOrException()))
                        .then(Commands.argument("jogador", EntityArgument.player())
                                .executes(context -> forget(context.getSource(), EntityArgument.getPlayer(context, "jogador")))))
                .then(Commands.argument("id", StringArgumentType.word()).requires(source -> source.hasPermission(2))
                        .suggests((context, builder) -> {
                            for (ChallengeSpec.Universe universe : ChallengeSpec.get().universos()) {
                                builder.suggest(universe.id());
                            }
                            return builder.buildFuture();
                        })
                        .executes(context -> travel(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "id"), false) ? 1 : 0)
                        .then(Commands.argument("jogador", EntityArgument.player())
                                .executes(context -> travel(EntityArgument.getPlayer(context, "jogador"), StringArgumentType.getString(context, "id"), false) ? 1 : 0)));
    }

    private static int forget(CommandSourceStack source, ServerPlayer player) {
        player.setData(ModAttachments.UNIVERSE, NONE);
        player.setData(ModAttachments.UNIVERSE_POS, Map.of());
        source.sendSuccess(() -> Component.translatable("message.battlegroundsmod.universe_forgot", player.getDisplayName()), true);
        return 1;
    }
}
