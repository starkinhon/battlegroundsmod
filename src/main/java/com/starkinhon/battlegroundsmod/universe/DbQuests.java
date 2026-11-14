package com.starkinhon.battlegroundsmod.universe;

import java.util.List;
import java.util.function.Supplier;

import com.starkinhon.battlegroundsmod.ModItems;
import com.starkinhon.battlegroundsmod.challenge.ChallengeArenas;
import com.starkinhon.battlegroundsmod.challenge.Universes;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.Progression;
import com.starkinhon.battlegroundsmod.power.Tiers;
import com.starkinhon.battlegroundsmod.world.TimeChamber;
import com.starkinhon.battlegroundsmod.world.WorldSites;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// The Dragon Ball start, a chain of missions (Stark 2026-11-10: the start was still, "parece um jogo pra refletir sobre a
// vida"; he picked all three: an opening fight, Bulma's missions with an arrow on the HUD, and the radar leading to the
// balls). The first mission sends the player to Bulma at Capsule Corp (Raditz no longer falls from the sky, Stark
// 2026-11-11), and from there each mission leads to one place of the map and its own activity (DbActivities): the Saibamen, the first ball with
// the radar, Roshi's training, the book's arenas, Gohan's flight, Korin, the Time Chamber, the gravity machine, Cell's
// ring, Babidi's ship, Buu's house, and Freeza last. Each pays Poder (a part of the player's tier) and some items.
// Only with progression on. Kept per player in its persistent data; the HUD (client/QuestHud) gets it every second.
public final class DbQuests {
    private static final String STEP = "souls_dbq";
    private static final String COUNT = "souls_dbq_n";
    // Saves from before the Raditz mission went away count one mission too many: moved back once (see migrate).
    private static final String NO_RADITZ = "souls_dbq_sem_raditz";
    private static final String GRANDPA_BALL = "souls_dbq_esfera4";
    // The chain only starts when the player talks to Bulma (Stark 2026-11-12: no missions popping up when a game starts).
    private static final String STARTED = "souls_dbq_iniciada";

    // id (lang quest.battlegroundsmod.<id>), how many, the kind of place it points to (WorldSites, null for none), Poder as
    // a part of the tier, items
    private record Step(String id, int goal, String kind, double poder, Supplier<List<ItemStack>> items) {
    }

    @SafeVarargs
    private static Supplier<List<ItemStack>> items(Supplier<ItemStack>... stacks) {
        return () -> {
            List<ItemStack> list = new java.util.ArrayList<>();
            for (Supplier<ItemStack> s : stacks) {
                list.add(s.get());
            }
            return list;
        };
    }

    private static final List<Step> STEPS = List.of(
            new Step("bulma", 1, WorldSites.CAPSULE, 0.2, items(() -> new ItemStack(ModItems.DRAGON_RADAR.get()), () -> new ItemStack(ModItems.SCOUTER.get()))),
            new Step("saibamen", 3, null, 0.3, items(() -> new ItemStack(ModItems.SENZU.get(), 2))),
            new Step("esfera", 1, null, 0.3, items()),
            new Step("kame", 1, WorldSites.KAME, 0.4, items()),
            new Step("livro", 2, null, 0.5, items(() -> new ItemStack(ModItems.SENZU.get(), 2))),
            new Step("gohan", 1, WorldSites.GOKU, 0.3, items()),
            new Step("karin", 1, WorldSites.KARIN, 0.3, items()),
            new Step("sala", 1, WorldSites.KARIN, 0.3, items()),
            new Step("nave", 60, WorldSites.WRECK, 0.4, items()),
            new Step("cell", 1, WorldSites.CELL_RING, 0.6, items(() -> new ItemStack(ModItems.SENZU.get(), 3))),
            new Step("babidi", 1, WorldSites.BABIDI, 0.8, items(() -> new ItemStack(ModItems.SENZU.get(), 3))),
            new Step("boo", 1, WorldSites.BUU, 0.3, items()),
            new Step("freeza", 1, null, 1.0, items(() -> new ItemStack(ModItems.SENZU.get(), 2))));

    private DbQuests() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(DbQuests::onTick);
        NeoForge.EVENT_BUS.addListener(DbQuests::onDeath);
        DbActivities.register();
    }

    static boolean inDragonBall(ServerPlayer player) {
        return player.level().dimension().equals(Universes.key("dragonball"));
    }

    // Born in the old Dragon Ball dimension, or in the normal world (where Dragon Ball is the first universe met), and
    // the chain started by talking to Bulma. Saves already past the first mission count as started.
    private static boolean on(ServerPlayer player) {
        String home = player.getData(com.starkinhon.battlegroundsmod.ModAttachments.UNIVERSE);
        return Progression.on(player) && ("dragonball".equals(home) || Universes.WORLD.equals(home)) && started(player);
    }

    private static boolean started(ServerPlayer player) {
        var tag = player.getPersistentData();
        return tag.getBoolean(STARTED) || tag.getInt(STEP) > 0;
    }

    // Talking to Bulma for the first time starts the chain (DbActivities.bulma).
    public static void start(ServerPlayer player) {
        if (Progression.on(player)) {
            var tag = player.getPersistentData();
            if (!tag.getBoolean(STARTED) && tag.getInt(STEP) == 0) {
                // a new chain, not an old save: nothing to migrate (else Bulma's step came back right after she gave the radar)
                tag.putBoolean(NO_RADITZ, true);
            }
            tag.putBoolean(STARTED, true);
        }
    }

    // Where the missions are shown: the old Dragon Ball dimension, and the normal world.
    private static boolean shown(ServerPlayer player) {
        return inDragonBall(player) || player.level().dimension() == Level.OVERWORLD;
    }

    // The arrow's target for the current mission: in the normal world the nearest place of that kind, built or not yet
    // (WorldSites.locate, looked up again every 15 s or when the mission changes); in the old dimension its fixed spot.
    private record Way(String kind, BlockPos pos, long at) {
    }

    private static final java.util.Map<java.util.UUID, Way> WAYS = new java.util.HashMap<>();

    private static BlockPos way(ServerPlayer player, Step step) {
        if (step.kind() == null) {
            return null;
        }
        if (player.level().dimension() != Level.OVERWORLD) {
            int[] p = DbActivities.place(player, step.kind());
            return p == null ? null : new BlockPos(p[0], 0, p[1]);
        }
        long now = player.serverLevel().getGameTime();
        Way cached = WAYS.get(player.getUUID());
        if (cached != null && cached.kind().equals(step.kind()) && now - cached.at() < 300) {
            return cached.pos();
        }
        BlockPos pos = WorldSites.locate(player.serverLevel(), step.kind(), player.blockPosition(), 6);
        WAYS.put(player.getUUID(), new Way(step.kind(), pos, now));
        return pos;
    }

    private static Step current(ServerPlayer player) {
        int i = player.getPersistentData().getInt(STEP);
        return i >= 0 && i < STEPS.size() ? STEPS.get(i) : null;
    }

    // How many missions the player finished, out of total() (power/Achievements).
    public static int done(ServerPlayer player) {
        return Math.max(0, Math.min(player.getPersistentData().getInt(STEP), STEPS.size()));
    }

    public static int total() {
        return STEPS.size();
    }

    public static boolean at(ServerPlayer player, String id) {
        Step step = current(player);
        return on(player) && step != null && step.id().equals(id);
    }

    // Progress on a mission (only when it is the current one).
    public static void advance(ServerPlayer player, String id, int amount) {
        if (!at(player, id)) {
            return;
        }
        Step step = current(player);
        int count = player.getPersistentData().getInt(COUNT) + amount;
        player.getPersistentData().putInt(COUNT, count);
        if (count >= step.goal()) {
            complete(player, step);
        } else {
            sync(player);
        }
    }

    private static void set(ServerPlayer player, String id, int count) {
        if (at(player, id) && player.getPersistentData().getInt(COUNT) != count) {
            player.getPersistentData().putInt(COUNT, count - 1);
            advance(player, id, 1);
        }
    }

    private static void complete(ServerPlayer player, Step step) {
        var tag = player.getPersistentData();
        tag.putInt(STEP, tag.getInt(STEP) + 1);
        tag.putInt(COUNT, 0);
        Progression.add(player, Progression.cost(Tiers.of(player)) * step.poder());
        for (ItemStack stack : step.items().get()) {
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
        player.connection.send(new ClientboundSetTitlesAnimationPacket(8, 50, 15));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("message.battlegroundsmod.missao_feita")
                .withStyle(s -> s.withColor(0xF2C94C).withBold(true))));
        Step next = current(player);
        player.connection.send(new ClientboundSetSubtitleTextPacket(next == null
                ? Component.translatable("message.battlegroundsmod.missao_fim")
                : Component.translatable("quest.battlegroundsmod." + next.id(), 0, next.goal())));
        Fx.sound(player.serverLevel(), player.position(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.3f);
        sync(player);
    }

    private static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) {
            return;
        }
        if (!on(player)) {
            return;
        }
        migrate(player);
        boolean here = shown(player);
        Step step = current(player);
        if (step != null) {
            switch (step.id()) {
                case "esfera" -> {
                    if (DragonBallWorld.hasAnyBall(player)) {
                        advance(player, "esfera", 1);
                    }
                }
                case "livro" -> set(player, "livro", ChallengeArenas.wins(player).size());
                case "gohan" -> {
                    if (Flight.knows(player)) {
                        advance(player, "gohan", 1);
                    }
                }
                case "sala" -> {
                    if (player.level().dimension().equals(TimeChamber.LEVEL)) {
                        advance(player, "sala", 1);
                    }
                }
                case "freeza" -> {
                    if (ChallengeArenas.wins(player).contains("freeza")) {
                        advance(player, "freeza", 1);
                    }
                }
                default -> {
                }
            }
        }
        if (here) {
            sync(player);
            SHOWN.add(player.getUUID());
        } else if (SHOWN.remove(player.getUUID())) {
            send(player, "", 0, 0, 0, 0, false);
        }
    }

    private static final java.util.Set<java.util.UUID> SHOWN = new java.util.HashSet<>();

    private static void migrate(ServerPlayer player) {
        var tag = player.getPersistentData();
        if (tag.getBoolean(NO_RADITZ)) {
            return;
        }
        tag.putBoolean(NO_RADITZ, true);
        if (tag.getInt(STEP) > 0) {
            tag.putInt(STEP, tag.getInt(STEP) - 1);
        } else {
            tag.putInt(COUNT, 0);
        }
    }

    private static void onDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType()).getPath();
        switch (id) {
            case "saibaman" -> advance(player, "saibamen", 1);
            default -> {
            }
        }
        DbActivities.onKill(player, event.getEntity());
    }

    // Gohan, after teaching the flight: his grandfather's four-star ball, once.
    public static void grandpaBall(ServerPlayer player) {
        if (player.getPersistentData().getBoolean(GRANDPA_BALL) || ModItems.DRAGON_BALLS.size() < 4) {
            return;
        }
        player.getPersistentData().putBoolean(GRANDPA_BALL, true);
        ItemStack ball = new ItemStack(ModItems.DRAGON_BALLS.get(3).get());
        if (!player.getInventory().add(ball)) {
            player.drop(ball, false);
        }
        player.sendSystemMessage(Component.translatable("npc.battlegroundsmod.gohan.esfera").withStyle(s -> s.withColor(0xF2C94C)));
    }

    // What the HUD shows: an activity going on (Roshi's milk run, the gravity machine...) comes first, then the mission
    // of the structure the player is in, then the chain's step.
    public static void sync(ServerPlayer player) {
        DbActivities.Hud over = DbActivities.hud(player);
        if (over == null) {
            // the mission of the structure the player is in (StructureMissions)
            over = StructureMissions.hud(player);
        }
        if (over != null) {
            send(player, over.id(), over.count(), over.goal(), over.x(), over.z(), over.way());
            return;
        }
        Step step = current(player);
        if (step == null || !on(player)) {
            send(player, "", 0, 0, 0, 0, false);
            return;
        }
        BlockPos to = shown(player) ? way(player, step) : null;
        send(player, step.id(), player.getPersistentData().getInt(COUNT), step.goal(), to == null ? 0 : to.getX(),
                to == null ? 0 : to.getZ(), to != null);
    }

    private static void send(ServerPlayer player, String id, int count, int goal, int x, int z, boolean way) {
        PacketDistributor.sendToPlayer(player, new ModPayloads.QuestSync(id, count, goal, x, z, way ? 1 : 0));
    }
}
