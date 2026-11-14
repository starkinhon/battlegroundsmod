package com.starkinhon.battlegroundsmod.universe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import com.starkinhon.battlegroundsmod.ModEntities;
import com.starkinhon.battlegroundsmod.ModItems;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.npc.UniverseNpc;
import com.starkinhon.battlegroundsmod.power.PowerLogic;
import com.starkinhon.battlegroundsmod.power.Powers;
import com.starkinhon.battlegroundsmod.power.SoulSync;
import com.starkinhon.battlegroundsmod.power.Progression;
import com.starkinhon.battlegroundsmod.power.Tiers;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.Db;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.DbCommon;
import com.starkinhon.battlegroundsmod.world.NamekWorld;
import com.starkinhon.battlegroundsmod.world.TimeChamber;
import com.starkinhon.battlegroundsmod.world.WorldSites;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// One thing to do at each place of the Dragon Ball map (Stark 2026-11-10: "uma coisinha pra ter em cada estrutura, pra ter
// sentido elas existirem, senão fica só de decoração"):
// - Capsule Corp: Bulma, who gives the missions (DbQuests) and the radar;
// - Kame House: the Turtle Training the first time (the milk run with the turtle shell on the back, slow and without
//   flying, then a sparring with the old man down to half his life) and he teaches the Kamehameha (Powers.KAMEHAMEHA,
//   Stark 2026-11-12: the player's own technique, taught at a structure); after that the milk run is daily;
// - Goku's house: Gohan teaches the flight and gives his grandfather's four-star ball;
// - the crashed ship: the gravity machine, 50x gravity around it that trains Poder, two minutes a day;
// - Cell's ring: three waves of Cell Jr., daily; falling off the ring loses (ring out);
// - Babidi's ship: its guardians one after the other, Pui Pui, Yakon and Dabura, daily; the first time, the Majin mark;
// - Namek's village: Freeza's soldiers come in three waves, daily;
// - Buu's house: Buu wants sweets (cake, cookie, pumpkin pie, honey); give him one and he gives a rare item of any
//   universe, daily.
// What each place asks is shown on entering it (StructureMissions).
// The NPCs stand at fixed places and come back when a player is near and they are missing.
public final class DbActivities {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    public static final int BULMA_X = -131, BULMA_Z = -123;
    public static final int KAME_X = 412, KAME_Z = 88;
    public static final int GOKU_X = 323, GOKU_Z = -29;
    public static final int WRECK_X = -319, WRECK_Z = 57;
    public static final int CELL_X = -54, CELL_Z = -289;
    public static final int BABIDI_X = -239, BABIDI_Z = 95;
    public static final int BOO_X = -268, BOO_Z = -430;

    private static final int WRECK_RADIUS = 8;
    static final int GRAVITY_SECONDS = 120;
    private static final int MILK_TICKS = 20 * 50;
    // The Turtle Training: the milk run with the shell (slow) and then the sparring.
    private static final int TRAINING_TICKS = 20 * 90;
    private static final int SPAR_TICKS = 20 * 180;
    private static final int SPAR_WAIT = 20 * 3;
    private static final String SPAR_TAG = "souls_kame_treino";
    private static final net.minecraft.resources.ResourceLocation SPAR_HEALTH = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
            com.starkinhon.battlegroundsmod.BattlegroundsMod.MODID, "kame_treino");
    // Bulma's Hoi-Poi capsule: what she needs for one, daily.
    static final String CAPSULE_DAY = "souls_capsula_dia";
    static final int CAPSULE_IRON = 6;
    static final int CAPSULE_REDSTONE = 3;

    // An NPC and where it stands: a kind of place (WorldSites) and its offset from the place's center.
    private record Home(Supplier<? extends EntityType<UniverseNpc>> type, String kind, int dx, int dz) {
    }

    private static final List<Home> HOMES = List.of(
            new Home(ModEntities.NPC_BULMA, WorldSites.CAPSULE, 0, 0),
            new Home(ModEntities.NPC_MESTRE_KAME, WorldSites.KAME, 9, 7),
            new Home(ModEntities.NPC_GOHAN, WorldSites.GOKU, 12, 12),
            new Home(ModEntities.NPC_BOO, WorldSites.BUU, 12, 8));

    // Where a place of this kind is for this player: in the normal world the nearest one built (WorldSites), in the old
    // drawn Dragon Ball dimension its fixed spot. Null when there is none near.
    static int[] place(ServerPlayer player, String kind) {
        if (player.level().dimension() == Level.OVERWORLD) {
            WorldSites.Site site = WorldSites.near(player.level(), kind, player.blockPosition(), 260);
            return site == null ? null : new int[] {site.x(), site.z()};
        }
        if (!DbQuests.inDragonBall(player)) {
            return null;
        }
        return switch (kind) {
            case WorldSites.CAPSULE -> new int[] {BULMA_X, BULMA_Z};
            case WorldSites.KAME -> new int[] {KAME_X, KAME_Z};
            case WorldSites.GOKU -> new int[] {GOKU_X, GOKU_Z};
            case WorldSites.WRECK -> new int[] {WRECK_X, WRECK_Z};
            case WorldSites.CELL_RING -> new int[] {CELL_X, CELL_Z};
            case WorldSites.BABIDI -> new int[] {BABIDI_X, BABIDI_Z};
            case WorldSites.BUU -> new int[] {BOO_X, BOO_Z};
            case WorldSites.KARIN -> new int[] {-407, -87};
            default -> null;
        };
    }

    private static boolean nearPlace(ServerPlayer player, String kind, int dx, int dz, double r) {
        int[] p = place(player, kind);
        return p != null && near(player, p[0] + dx, p[1] + dz, r);
    }

    // The activities run in the old Dragon Ball dimension and anywhere in the normal world (the places are there).
    static boolean active(ServerPlayer player) {
        return DbQuests.inDragonBall(player) || player.level().dimension() == Level.OVERWORLD
                || player.level().dimension() == NamekWorld.LEVEL;
    }

    // What the HUD shows while an activity runs.
    public record Hud(String id, int count, int goal, int x, int z, boolean way) {
    }

    private static final class Milk {
        BlockPos spot;
        BlockPos home;
        long until;
        boolean delivered;
        // the first time: the Turtle Training (the shell on, no flying, then the sparring)
        boolean training;
    }

    // The sparring with Mestre Kame: his fighting body (the SubAlma mob), where the old man stands and the time limit.
    private static final class Spar {
        UUID kame;
        BlockPos home;
        long until;
    }

    private static final class Waves {
        final String kind;
        final List<List<EntityType<?>>> waves = new ArrayList<>();
        final List<UUID> alive = new ArrayList<>();
        int stage;
        long nextAt;
        final BlockPos center;

        Waves(String kind, BlockPos center) {
            this.kind = kind;
            this.center = center;
        }
    }

    private static final Map<UUID, Milk> MILK = new HashMap<>();
    private static final Map<UUID, Waves> WAVES = new HashMap<>();
    private static final Map<UUID, Boolean> IN_GRAVITY = new HashMap<>();
    private static final Map<UUID, Spar> SPAR = new HashMap<>();

    private DbActivities() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(DbActivities::onTick);
        NeoForge.EVENT_BUS.addListener(DbActivities::onIncoming);
        NeoForge.EVENT_BUS.addListener(DbActivities::onDeath);
        StructureMissions.register();
        MissionJournal.register();
    }

    private static long day(ServerPlayer player) {
        return player.serverLevel().getGameTime() / 24000L;
    }

    static boolean dailyFree(ServerPlayer player, String tag) {
        return !player.getPersistentData().contains(tag) || player.getPersistentData().getLong(tag) < day(player);
    }

    // For the mission journal (MissionJournal): how far the player is in Mestre Kame's milk run (0 none, 1 carrying the
    // milk, 2 delivered, 3 sparring with the old man), and whether a wave fight of a kind is running.
    static int kameStage(ServerPlayer player) {
        if (SPAR.containsKey(player.getUUID())) {
            return 3;
        }
        Milk milk = MILK.get(player.getUUID());
        return milk == null ? 0 : milk.delivered ? 2 : 1;
    }

    static int wavesStage(ServerPlayer player, String kind) {
        Waves w = WAVES.get(player.getUUID());
        return w == null || !w.kind.equals(kind) ? -1 : w.stage;
    }

    static void dailyUse(ServerPlayer player, String tag) {
        player.getPersistentData().putLong(tag, day(player));
    }

    static void reward(ServerPlayer player, double tierPart, ItemStack... items) {
        reward(player, null, tierPart, items);
    }

    // mission != null: a mission of the Diário de Missões was done, MISSÃO CONCLUÍDA comes on screen with what it gave
    // (in place of the vanilla title, unless a tier-up took the screen).
    static void reward(ServerPlayer player, String mission, double tierPart, ItemStack... items) {
        int tier = Tiers.of(player);
        List<String> got = new java.util.ArrayList<>();
        if (tierPart > 0) {
            got.add("__poder");
        }
        for (ItemStack stack : items) {
            got.add(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()) + " " + stack.getCount());
        }
        Progression.add(player, Progression.cost(Tiers.of(player)) * tierPart);
        Component[] last = LAST_TITLE.get(player.getUUID());
        if (Tiers.of(player) != tier && last != null && player.serverLevel().getGameTime() - lastTitleAt(player) <= 1) {
            // the tier-up title just took the screen over this one (the test: Buu's gift and the Grand Elder went unseen):
            // say it in the chat instead
            player.sendSystemMessage(Component.empty().append(last[0]).append(Component.literal(": ").withStyle(s -> s.withColor(0xAAAAAA)))
                    .append(last[1].copy().withStyle(s -> s.withColor(0xE6E0EE))));
        }
        for (ItemStack stack : items) {
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
        Fx.sound(player.serverLevel(), player.position(), SoundEvents.PLAYER_LEVELUP, 0.8f, 1.2f);
        if (mission != null) {
            MissionJournal.done(player, mission, got, Tiers.of(player) == tier);
        }
    }

    static void title(ServerPlayer player, Component title, Component sub, int color) {
        Component head = title.copy().withStyle(s -> s.withColor(color).withBold(true));
        player.connection.send(new ClientboundSetTitlesAnimationPacket(6, 40, 12));
        player.connection.send(new ClientboundSetTitleTextPacket(head));
        player.connection.send(new ClientboundSetSubtitleTextPacket(sub));
        LAST_TITLE.put(player.getUUID(), new Component[] {head, sub});
        LAST_TITLE_AT.put(player.getUUID(), player.serverLevel().getGameTime());
    }

    // The last title each player got and when (reward() moves it to the chat when a tier-up covers it).
    private static final Map<UUID, Component[]> LAST_TITLE = new HashMap<>();
    private static final Map<UUID, Long> LAST_TITLE_AT = new HashMap<>();

    private static long lastTitleAt(ServerPlayer player) {
        return LAST_TITLE_AT.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2);
    }

    // A dry block to stand on near (x, z), searching outwards up to radius, or null.
    public static BlockPos dryGround(ServerLevel level, int x, int z, int radius) {
        for (int r = 0; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r || !level.hasChunkAt(new BlockPos(x + dx, 0, z + dz))) {
                        continue;
                    }
                    BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x + dx, 0, z + dz));
                    if (top.getY() > level.getMinBuildHeight() + 1 && level.getFluidState(top.below()).isEmpty()
                            && level.getFluidState(top).isEmpty() && level.getBlockState(top.below()).isSolid()) {
                        return top;
                    }
                }
            }
        }
        return null;
    }

    private static boolean near(ServerPlayer player, int x, int z, double r) {
        double dx = player.getX() - x, dz = player.getZ() - z;
        return dx * dx + dz * dz < r * r;
    }

    private static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !Progression.on(player) || !active(player)) {
            if (event.getEntity() instanceof ServerPlayer p && IN_GRAVITY.remove(p.getUUID()) != null) {
                TimeChamber.clearGravity(p);
            }
            return;
        }
        if (player.tickCount % 100 == 0) {
            homes(player);
        }
        if (player.tickCount % 20 == 0) {
            milk(player);
            spar(player);
            gravity(player);
            waves(player);
        }
    }

    // ---------- the NPCs at their places ----------

    private static void homes(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        for (Home home : HOMES) {
            int[] place = place(player, home.kind());
            if (place == null || !near(player, place[0] + home.dx(), place[1] + home.dz(), 80)) {
                continue;
            }
            int hx = place[0] + home.dx(), hz = place[1] + home.dz();
            EntityType<UniverseNpc> type = home.type().get();
            if (type == ModEntities.NPC_MESTRE_KAME.get() && sparringNear(hx, hz)) {
                // the old man is fighting in his other body (the sparring)
                continue;
            }
            if (!level.getEntities(type, new AABB(hx - 48, level.getMinBuildHeight(), hz - 48, hx + 48,
                    level.getMaxBuildHeight(), hz + 48), Entity::isAlive).isEmpty()) {
                continue;
            }
            BlockPos at = dryGround(level, hx, hz, 10);
            if (at == null) {
                continue;
            }
            UniverseNpc npc = type.spawn(level, at, MobSpawnType.EVENT);
            if (npc != null) {
                npc.restrictTo(at, 8);
                npc.setCustomName(npc.getType().getDescription());
                npc.setCustomNameVisible(true);
            }
        }
        // the Gohan of older worlds still stands at the arrival point: he moves to Goku's house
        if (DbQuests.inDragonBall(player) && !near(player, GOKU_X, GOKU_Z, 200)) {
            for (UniverseNpc old : level.getEntities(ModEntities.NPC_GOHAN.get(), player.getBoundingBox().inflate(64), Entity::isAlive)) {
                old.discard();
            }
        }
    }

    // ---------- the NPCs' talk (Npcs.talk) ----------

    public static void bulma(ServerPlayer player, UniverseNpc npc) {
        DbQuests.start(player);
        if (DbQuests.at(player, "bulma")) {
            say(player, npc, Component.translatable("npc.battlegroundsmod.bulma.radar"));
            DbQuests.advance(player, "bulma", 1);
            // came with the capsule's parts too: she builds it right away (in the test the radar line ate the trade)
            if (!(count(player, Items.IRON_INGOT) >= CAPSULE_IRON && count(player, Items.REDSTONE) >= CAPSULE_REDSTONE)) {
                return;
            }
        }
        // the Hoi-Poi capsule: iron and redstone for a house in your pocket, one a day
        if (dailyFree(player, CAPSULE_DAY)) {
            if (count(player, Items.IRON_INGOT) >= CAPSULE_IRON && count(player, Items.REDSTONE) >= CAPSULE_REDSTONE) {
                take(player, Items.IRON_INGOT, CAPSULE_IRON);
                take(player, Items.REDSTONE, CAPSULE_REDSTONE);
                dailyUse(player, CAPSULE_DAY);
                say(player, npc, Component.translatable("npc.battlegroundsmod.bulma.capsula"));
                title(player, Component.translatable("message.battlegroundsmod.capsula_feita"), Component.empty(), 0x8FD3FF);
                Fx.burst(player.serverLevel(), ParticleTypes.CLOUD, npc.position().add(0, 1, 0), 30, 0.4, 0.05);
                reward(player, "capsula", 0.1, new ItemStack(ModItems.HOI_POI_CAPSULE.get()));
                return;
            }
            if (player.getRandom().nextInt(2) == 0) {
                say(player, npc, Component.translatable("npc.battlegroundsmod.bulma.pecas", CAPSULE_IRON, CAPSULE_REDSTONE));
                return;
            }
        }
        say(player, npc, Component.translatable("npc.battlegroundsmod.bulma.hint." + player.getRandom().nextInt(4)));
    }

    // The mission card the talk scene shows with this NPC (npc/Talks), or null when the NPC just talks (nothing to take
    // today, a step already running, the radar line of Bulma's chain).
    public static String talkMission(ServerPlayer player, String npc) {
        UUID id = player.getUUID();
        return switch (npc) {
            case "bulma" -> !DbQuests.at(player, "bulma") && dailyFree(player, CAPSULE_DAY) ? "capsula" : null;
            case "mestre_kame" -> !SPAR.containsKey(id) && !MILK.containsKey(id) && (!knowsKamehameha(player) || dailyFree(player, "souls_kame_dia"))
                    ? "kame" : null;
            case "boo" -> dailyFree(player, "souls_boo_dia") ? "buu" : null;
            case "gohan" -> Flight.knows(player) ? null : "goku";
            default -> null;
        };
    }

    // The answer in the talk scene went through: the mission was taken (or delivered).
    public static boolean talkTaken(ServerPlayer player, String mission) {
        return switch (mission) {
            case "capsula" -> !dailyFree(player, CAPSULE_DAY);
            case "kame" -> MILK.containsKey(player.getUUID());
            case "buu" -> !dailyFree(player, "souls_boo_dia");
            case "goku" -> Flight.knows(player);
            default -> false;
        };
    }

    // How many of an item the player carries, and taking some away.
    static int count(ServerPlayer player, Item item) {
        int n = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) {
                n += stack.getCount();
            }
        }
        return n;
    }

    private static void take(ServerPlayer player, Item item, int amount) {
        for (ItemStack stack : player.getInventory().items) {
            if (amount <= 0) {
                return;
            }
            if (stack.is(item)) {
                int t = Math.min(amount, stack.getCount());
                stack.shrink(t);
                amount -= t;
            }
        }
    }

    // Mestre Kame taught the Kamehameha (the technique is in the Livro das Almas).
    static boolean knowsKamehameha(ServerPlayer player) {
        return SoulSync.known(player, Powers.KAMEHAMEHA.id());
    }

    public static void roshi(ServerPlayer player, UniverseNpc npc) {
        if (SPAR.containsKey(player.getUUID())) {
            return;
        }
        Milk milk = MILK.get(player.getUUID());
        if (milk != null && milk.delivered) {
            MILK.remove(player.getUUID());
            player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            if (milk.training) {
                // the second part of the Turtle Training: the old man fights you himself
                say(player, npc, Component.translatable("npc.battlegroundsmod.mestre_kame.luta"));
                sparStart(player, npc);
                return;
            }
            boolean first = !player.getPersistentData().getBoolean("souls_kame_feito");
            player.getPersistentData().putBoolean("souls_kame_feito", true);
            dailyUse(player, "souls_kame_dia");
            say(player, npc, Component.translatable("npc.battlegroundsmod.mestre_kame.feito"));
            title(player, Component.translatable("message.battlegroundsmod.kame_feito"), Component.empty(), 0xE8A040);
            reward(player, "kame", first ? 0.35 : 0.15, new ItemStack(Items.MILK_BUCKET));
            DbQuests.advance(player, "kame", 1);
            return;
        }
        if (milk != null) {
            say(player, npc, Component.translatable("npc.battlegroundsmod.mestre_kame.vai"));
            return;
        }
        boolean training = !knowsKamehameha(player);
        if (!training && !dailyFree(player, "souls_kame_dia")) {
            say(player, npc, Component.translatable("npc.battlegroundsmod.mestre_kame.amanha"));
            return;
        }
        // the milk goes to a spot on the island the old man points at
        ServerLevel level = player.serverLevel();
        int[] island = place(player, WorldSites.KAME);
        if (island == null) {
            island = new int[] {npc.getBlockX() - 9, npc.getBlockZ() - 7};
        }
        BlockPos spot = null;
        for (int tries = 0; tries < 8 && spot == null; tries++) {
            double a = player.getRandom().nextDouble() * Math.PI * 2;
            spot = dryGround(level, island[0] + (int) Math.round(Math.cos(a) * 22), island[1] + (int) Math.round(Math.sin(a) * 22), 6);
        }
        if (spot == null) {
            spot = npc.blockPosition().offset(12, 0, 0);
        }
        int ticks = training ? TRAINING_TICKS : MILK_TICKS;
        milk = new Milk();
        milk.spot = spot;
        milk.home = npc.blockPosition();
        milk.until = level.getGameTime() + ticks;
        milk.training = training;
        MILK.put(player.getUUID(), milk);
        LOGGER.info("[missoes] Mestre Kame: leite {} para {} (treino={})", player.getScoreboardName(), spot, training);
        ItemStack bucket = new ItemStack(Items.MILK_BUCKET);
        if (!player.getInventory().add(bucket)) {
            player.drop(bucket, false);
        }
        if (training) {
            // the turtle shell on the back: heavy (slow), and no flying
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 1, false, false, true));
            Fx.sound(level, player.position(), SoundEvents.TURTLE_EGG_CRACK, 1f, 0.6f);
            say(player, npc, Component.translatable("npc.battlegroundsmod.mestre_kame.casco"));
            title(player, Component.translatable("message.battlegroundsmod.kame_tartaruga"), Component.translatable("message.battlegroundsmod.kame_casco",
                    ticks / 20), 0xE8A040);
        } else {
            say(player, npc, Component.translatable("npc.battlegroundsmod.mestre_kame.leite"));
            title(player, Component.translatable("message.battlegroundsmod.kame_treino"), Component.translatable("message.battlegroundsmod.kame_leve",
                    ticks / 20), 0xE8A040);
        }
        DbQuests.sync(player);
    }

    private static void milk(ServerPlayer player) {
        Milk milk = MILK.get(player.getUUID());
        if (milk == null) {
            return;
        }
        long now = player.serverLevel().getGameTime();
        if (now > milk.until) {
            MILK.remove(player.getUUID());
            player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            player.displayClientMessage(Component.translatable(milk.training ? "message.battlegroundsmod.kame_tempo_treino"
                    : "message.battlegroundsmod.kame_tempo"), false);
            DbQuests.sync(player);
            return;
        }
        if (milk.training && (Flight.flying(player) || player.isFallFlying() || player.getAbilities().flying && !player.isCreative())) {
            // the training is with the legs
            MILK.remove(player.getUUID());
            player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            player.displayClientMessage(Component.translatable("message.battlegroundsmod.kame_sem_voar"), false);
            Fx.sound(player.serverLevel(), player.position(), SoundEvents.VILLAGER_NO, 1f, 0.7f);
            DbQuests.sync(player);
            return;
        }
        // on the spot's column, a few blocks up or down still counts (Stark 2026-11-13 stood on it and climbed around it)
        double sdx = player.getX() - (milk.spot.getX() + 0.5), sdz = player.getZ() - (milk.spot.getZ() + 0.5);
        if (!milk.delivered && sdx * sdx + sdz * sdz < 3.5 * 3.5 && Math.abs(player.getY() - milk.spot.getY()) < 6) {
            int slot = player.getInventory().findSlotMatchingItem(new ItemStack(Items.MILK_BUCKET));
            if (slot < 0) {
                player.displayClientMessage(Component.translatable("message.battlegroundsmod.kame_sem_leite"), true);
                return;
            }
            player.getInventory().removeItem(slot, 1);
            milk.delivered = true;
            LOGGER.info("[missoes] Mestre Kame: leite entregue por {}", player.getScoreboardName());
            Fx.sound(player.serverLevel(), player.position(), SoundEvents.BUCKET_EMPTY, 1f, 1f);
            player.displayClientMessage(Component.translatable("message.battlegroundsmod.kame_volte"), true);
        }
    }

    public static void boo(ServerPlayer player, UniverseNpc npc) {
        boo(player, npc, null);
    }

    // pick: the sweet chosen in the talk scene (npc/Talks), taken from anywhere in the inventory; null = the one in hand.
    public static void boo(ServerPlayer player, UniverseNpc npc, Item pick) {
        ItemStack hand = player.getMainHandItem();
        if (pick != null) {
            hand = ItemStack.EMPTY;
            for (ItemStack stack : player.getInventory().items) {
                if (stack.is(pick)) {
                    hand = stack;
                    break;
                }
            }
        }
        boolean sweet = hand.is(Items.CAKE) || hand.is(Items.COOKIE) || hand.is(Items.PUMPKIN_PIE) || hand.is(Items.HONEY_BOTTLE);
        if (!sweet) {
            say(player, npc, Component.translatable("npc.battlegroundsmod.boo.quer." + player.getRandom().nextInt(3)));
            return;
        }
        if (!dailyFree(player, "souls_boo_dia")) {
            say(player, npc, Component.translatable("npc.battlegroundsmod.boo.cheio"));
            return;
        }
        hand.shrink(1);
        dailyUse(player, "souls_boo_dia");
        ItemStack gift = rare(player);
        say(player, npc, Component.translatable("npc.battlegroundsmod.boo.obrigado"));
        Fx.sound(player.serverLevel(), npc.position(), SoundEvents.GENERIC_EAT, 1f, 0.7f);
        // what he gave, on screen (in the test the gift went into the inventory without a word)
        title(player, Component.translatable("message.battlegroundsmod.boo_presente"), gift.getHoverName().copy()
                .append(gift.getCount() > 1 ? " x" + gift.getCount() : ""), 0xF5A3C7);
        reward(player, "buu", 0.1, gift);
        DbQuests.advance(player, "boo", 1);
    }

    // Buu's gift: a rare item of any universe of the mod (Stark 2026-11-12: "ele te dá um item raro, qualquer item do jogo").
    private static final List<Supplier<? extends Item>> RARE = List.of(
            ModItems.SCOUTER, ModItems.DRAGON_RADAR, ModItems.HIRAISHIN_KUNAI, ModItems.SAMEHADA,
            ModItems.KUSANAGI, ModItems.KUBIKIRIBOCHO, ModItems.PAPER_WINGS, ModItems.HASHIRAMA_CELL, ModItems.TOAD_SCROLL,
            ModItems.SHARINGAN, ModItems.GOJO_BLINDFOLD, ModItems.PLAYFUL_CLOUD, ModItems.INVERTED_SPEAR, ModItems.CURSED_DOLL,
            ModItems.HOLLOW_MASK, ModItems.ZANPAKUTO, ModItems.SOUL_CANDY, ModItems.NICHIRIN_SWORD, ModItems.WISTERIA,
            ModItems.TAMAYO_MEDICINE, ModItems.SOLDIER_PILL);

    private static ItemStack rare(ServerPlayer player) {
        int roll = player.getRandom().nextInt(100);
        if (roll < 15 && !ModItems.DRAGON_BALLS.isEmpty()) {
            return new ItemStack(ModItems.DRAGON_BALLS.get(player.getRandom().nextInt(ModItems.DRAGON_BALLS.size())).get());
        }
        if (roll < 25) {
            return new ItemStack(ModItems.SENZU.get(), 3);
        }
        return new ItemStack(RARE.get(player.getRandom().nextInt(RARE.size())).get());
    }

    private static void say(ServerPlayer player, UniverseNpc npc, Component line) {
        if (com.starkinhon.battlegroundsmod.npc.Talks.capture(line)) {
            return;
        }
        player.sendSystemMessage(Component.empty().append(npc.getType().getDescription().copy().withStyle(s -> s.withColor(0xF2C94C).withBold(true)))
                .append(Component.literal(": ").withStyle(s -> s.withColor(0xAAAAAA))).append(line.copy().withStyle(s -> s.withColor(0xE6E0EE))));
    }

    // ---------- the gravity machine in the crashed ship ----------

    private static void gravity(ServerPlayer player) {
        boolean inside = nearPlace(player, WorldSites.WRECK, 0, 0, WRECK_RADIUS);
        var tag = player.getPersistentData();
        long today = day(player);
        if (tag.getLong("souls_gravidade_dia") != today) {
            tag.putLong("souls_gravidade_dia", today);
            tag.putInt("souls_gravidade_s", 0);
        }
        int used = tag.getInt("souls_gravidade_s");
        if (!inside || used >= GRAVITY_SECONDS) {
            if (IN_GRAVITY.remove(player.getUUID()) != null) {
                TimeChamber.clearGravity(player);
                if (used >= GRAVITY_SECONDS && inside) {
                    player.displayClientMessage(Component.translatable("message.battlegroundsmod.gravidade_fim"), true);
                    MissionJournal.done(player, "nave", List.of("__poder"), false);
                }
                DbQuests.sync(player);
            }
            return;
        }
        if (IN_GRAVITY.put(player.getUUID(), true) == null) {
            title(player, Component.translatable("message.battlegroundsmod.gravidade"), Component.translatable("message.battlegroundsmod.gravidade_sub"), 0x6AC8FF);
        }
        TimeChamber.setGravity(player, 1);
        tag.putInt("souls_gravidade_s", used + 1);
        boolean moved = player.getDeltaMovement().horizontalDistanceSqr() > 0.002 || !player.onGround();
        TimeChamber.train(player, (moved ? 2.0 : 1.0) / 60.0);
        DbQuests.advance(player, "nave", 1);
        if (!DbQuests.at(player, "nave")) {
            DbQuests.sync(player);
        }
    }

    // ---------- Cell's ring and Babidi's ship: fights one after the other ----------

    private static void waves(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Waves w = WAVES.get(player.getUUID());
        if (w == null) {
            if (nearPlace(player, WorldSites.CELL_RING, 0, 0, 5) && dailyFree(player, "souls_cell_dia")) {
                // the Cell Games (mobs-db 2026-11-12): seven Cell Jr. at once around the ring
                List<EntityType<?>> seven = new java.util.ArrayList<>();
                for (int i = 0; i < 7; i++) {
                    seven.add(ModEntities.CELL_JR.get());
                }
                start(player, "cell", List.of(seven));
            } else if (nearPlace(player, WorldSites.BABIDI, 0, 0, 14) && dailyFree(player, "souls_babidi_dia")) {
                // the ship's floors (mobs-db 2026-11-12): Pui Pui, Yakon, Spopovich with Yamu, Dabura, and Babidi himself
                start(player, "babidi", List.of(List.of(ModEntities.PUI_PUI.get()), List.of(ModEntities.YAKON.get()),
                        List.of(ModEntities.db("spopovich"), ModEntities.db("yamu")), List.of(ModEntities.DABURA.get()), List.of(ModEntities.db("babidi"))));
            } else if (level.dimension() == NamekWorld.LEVEL && StructureMissions.near(player, StructureMissions.VILLAGE, 12)
                    && dailyFree(player, "souls_namek_dia")) {
                // Namek's village: Freeza's soldiers land to take the Dragon Balls
                start(player, "namek", List.of(
                        List.of(ModEntities.SOLDADO_FREEZA.get(), ModEntities.SOLDADO_FREEZA.get()),
                        List.of(ModEntities.SOLDADO_FREEZA.get(), ModEntities.SOLDADO_FREEZA.get(), ModEntities.SOLDADO_FREEZA.get()),
                        List.of(ModEntities.SOLDADO_FREEZA.get(), ModEntities.SOLDADO_FREEZA.get(), ModEntities.SOLDADO_FREEZA.get(),
                                ModEntities.SOLDADO_FREEZA.get())));
            }
            return;
        }
        int cx = w.center.getX(), cz = w.center.getZ();
        if (!near(player, cx, cz, 48) || !player.isAlive()) {
            end(level, w);
            WAVES.remove(player.getUUID());
            player.displayClientMessage(Component.translatable("message.battlegroundsmod.ondas_fugiu"), true);
            DbQuests.sync(player);
            return;
        }
        if ("cell".equals(w.kind) && ringOut(player)) {
            // the Cell Games have rules: off the ring, out of the games
            end(level, w);
            WAVES.remove(player.getUUID());
            dailyUse(player, "souls_cell_dia");
            title(player, Component.translatable("message.battlegroundsmod.cell_ringout"), Component.translatable("message.battlegroundsmod.cell_ringout_sub"),
                    0x82BEAA);
            Fx.sound(level, player.position(), SoundEvents.ANVIL_LAND, 0.7f, 0.6f);
            DbQuests.sync(player);
            return;
        }
        w.alive.removeIf(id -> !(level.getEntity(id) instanceof LivingEntity e) || !e.isAlive());
        for (UUID id : w.alive) {
            if (level.getEntity(id) instanceof Mob mob && mob.getTarget() != player) {
                mob.setTarget(player);
            }
        }
        if (!w.alive.isEmpty() || level.getGameTime() < w.nextAt) {
            return;
        }
        if (w.stage >= w.waves.size()) {
            WAVES.remove(player.getUUID());
            dailyUse(player, "souls_" + w.kind + "_dia");
            title(player, Component.translatable("message.battlegroundsmod." + w.kind + "_vitoria"), Component.empty(), color(w.kind));
            switch (w.kind) {
                case "cell" -> reward(player, "cell", 0.4, new ItemStack(ModItems.SENZU.get(), 2));
                case "namek" -> reward(player, "vila", 0.5, new ItemStack(ModItems.SENZU.get(), 3));
                default -> {
                    reward(player, "babidi", 0.6);
                    // the first time the guardians fall, Babidi marks you (StructureMissions, MajinMark)
                    MajinMark.grant(player);
                }
            }
            DbQuests.advance(player, w.kind, 1);
            DbQuests.sync(player);
            return;
        }
        List<EntityType<?>> wave = w.waves.get(w.stage);
        w.stage++;
        Component name = wave.get(0).getDescription();
        title(player, Component.translatable("message.battlegroundsmod." + w.kind + "_onda", w.stage, w.waves.size()), name.copy(),
                color(w.kind));
        Vec3 look = player.getLookAngle();
        double base = Math.atan2(look.z, look.x);
        for (int i = 0; i < wave.size(); i++) {
            // a big wave all round the player (the seven Cell Jr.), a small one in front
            double a = wave.size() >= 5 ? base + i * Math.PI * 2 / wave.size() : base + (i - (wave.size() - 1) / 2.0) * 0.6;
            BlockPos at = dryGround(level, (int) Math.floor(player.getX() + Math.cos(a) * 8), (int) Math.floor(player.getZ() + Math.sin(a) * 8), 5);
            if (at == null) {
                at = player.blockPosition();
            }
            Entity e = wave.get(i).spawn(level, at, MobSpawnType.EVENT);
            if (e instanceof Mob mob) {
                mob.setTarget(player);
                mob.setPersistenceRequired();
                w.alive.add(e.getUUID());
                DbCommon.boom(level, Vec3.atBottomCenterOf(at), 1.4f, "babidi".equals(w.kind) ? Db.RED : Db.GOLD, level.getRandom().nextInt(100));
            }
        }
        w.nextAt = level.getGameTime() + 40;
        DbQuests.sync(player);
    }

    private static void start(ServerPlayer player, String kind, List<List<EntityType<?>>> waves) {
        Waves w = new Waves(kind, player.blockPosition());
        w.waves.addAll(waves);
        w.nextAt = player.serverLevel().getGameTime() + 60;
        WAVES.put(player.getUUID(), w);
        title(player, Component.translatable("message.battlegroundsmod." + kind + "_titulo"), Component.translatable("message.battlegroundsmod." + kind + "_sub"),
                color(kind));
        Fx.sound(player.serverLevel(), player.position(), SoundEvents.RAID_HORN.value(), 0.6f, 1.3f);
        DbQuests.sync(player);
    }

    private static int color(String kind) {
        return switch (kind) {
            case "cell" -> 0x82BEAA;
            case "namek" -> 0x7FD67A;
            default -> 0xE04040;
        };
    }

    // Off Cell's ring: standing on the ground outside the square, or below the ring's floor (the stone tiles of the
    // cell_arena template: 53x53 around the center, its top two blocks over the place's floor).
    private static boolean ringOut(ServerPlayer player) {
        if (player.level().dimension() != Level.OVERWORLD || !player.onGround()) {
            return false;
        }
        WorldSites.Site ring = WorldSites.near(player.level(), WorldSites.CELL_RING, player.blockPosition(), 80);
        if (ring == null) {
            return false;
        }
        double dx = Math.abs(player.getX() - ring.x() - 0.5), dz = Math.abs(player.getZ() - ring.z() - 0.5);
        return Math.max(dx, dz) > 27 || player.getY() < ring.y() + 2.5;
    }

    // A wave fight going on for the player (StructureMissions keeps the mark's crouch out of it).
    static boolean fighting(ServerPlayer player) {
        return WAVES.containsKey(player.getUUID());
    }

    private static void end(ServerLevel level, Waves w) {
        for (UUID id : w.alive) {
            Entity e = level.getEntity(id);
            if (e != null) {
                e.discard();
            }
        }
    }

    static void onKill(ServerPlayer player, LivingEntity victim) {
    }

    // ---------- the Turtle Training's sparring with Mestre Kame ----------

    // The old man in his fighting body (the SubAlma mob, holding back): bring him down to half his life and he
    // teaches you the Kamehameha. Falling is not dying: he stops the fight.
    private static void sparStart(ServerPlayer player, UniverseNpc npc) {
        ServerLevel level = player.serverLevel();
        BlockPos home = npc.blockPosition();
        Vec3 at = npc.position();
        npc.discard();
        Entity e = ModEntities.KAME.get().spawn(level, BlockPos.containing(at), MobSpawnType.EVENT);
        if (!(e instanceof Mob kame)) {
            LOGGER.warn("[missoes] Mestre Kame: a luta não começou (o corpo de luta não nasceu em {})", at);
            return;
        }
        LOGGER.info("[missoes] Mestre Kame: luta com {}", player.getScoreboardName());
        kame.addTag(SPAR_TAG);
        kame.setPersistenceRequired();
        kame.setCustomName(Component.translatable("message.battlegroundsmod.kame_nome_treino"));
        kame.setCustomNameVisible(true);
        kame.setTarget(player);
        // A training for the player's tier (Stark 2026-11-13: tier 7 against a tier 9 took no damage even with Strength
        // 255, the Escudo ate every blow, and his Kamehameha ended it): he fights at the player's tier. And he waits 3 s
        // before the first blow, so the player can step back.
        kame.getData(com.starkinhon.battlegroundsmod.ModAttachments.POWER).tier = com.starkinhon.battlegroundsmod.power.Tiers.of(player);
        com.starkinhon.battlegroundsmod.combat.Stun.cooldown(kame, SPAR_WAIT);
        Spar spar = new Spar();
        spar.kame = kame.getUUID();
        spar.home = home;
        spar.until = level.getGameTime() + SPAR_TICKS;
        SPAR.put(player.getUUID(), spar);
        Fx.burst(level, ParticleTypes.CLOUD, at.add(0, 1, 0), 30, 0.5, 0.05);
        Fx.sound(level, at, SoundEvents.RAID_HORN.value(), 0.5f, 1.5f);
        title(player, Component.translatable("message.battlegroundsmod.kame_luta"), Component.translatable("message.battlegroundsmod.kame_luta_sub"), 0xE8A040);
        DbQuests.sync(player);
    }

    private static boolean sparringNear(int x, int z) {
        for (Spar spar : SPAR.values()) {
            double dx = spar.home.getX() - x, dz = spar.home.getZ() - z;
            if (dx * dx + dz * dz < 80 * 80) {
                return true;
            }
        }
        return false;
    }

    private static LivingEntity sparKame(ServerPlayer player) {
        Spar spar = SPAR.get(player.getUUID());
        return spar != null && player.serverLevel().getEntity(spar.kame) instanceof LivingEntity kame && kame.isAlive() ? kame : null;
    }

    private static void spar(ServerPlayer player) {
        Spar spar = SPAR.get(player.getUUID());
        if (spar == null) {
            return;
        }
        LivingEntity kame = sparKame(player);
        if (kame == null || !player.isAlive() || player.serverLevel().getGameTime() > spar.until
                || player.distanceToSqr(Vec3.atCenterOf(spar.home)) > 56 * 56) {
            sparEnd(player, false);
            return;
        }
        if (kame instanceof Mob mob && mob.getTarget() != player) {
            mob.setTarget(player);
        }
        // the player's tier, every tick (his own stats would put his file's tier back)
        kame.getData(com.starkinhon.battlegroundsmod.ModAttachments.POWER).tier = com.starkinhon.battlegroundsmod.power.Tiers.of(player);
        // a sparring for a beginner: about twice the player's life, not the SubAlma's whole tier (set once he has his
        // stats, after his first tick)
        var health = kame.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (health != null && !health.hasModifier(SPAR_HEALTH)) {
            double want = Math.max(40, player.getMaxHealth() * 2);
            health.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(SPAR_HEALTH,
                    Math.min(0, want / health.getValue() - 1), net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            kame.setHealth(kame.getMaxHealth());
            return;
        }
        if (kame.getHealth() <= kame.getMaxHealth() * 0.5f) {
            sparEnd(player, true);
            return;
        }
        DbQuests.sync(player);
    }

    private static void sparEnd(ServerPlayer player, boolean won) {
        Spar spar = SPAR.remove(player.getUUID());
        if (spar == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        Entity kame = level.getEntity(spar.kame);
        if (kame != null) {
            Fx.burst(level, ParticleTypes.CLOUD, kame.position().add(0, 1, 0), 30, 0.5, 0.05);
            kame.discard();
        }
        if (!won) {
            player.displayClientMessage(Component.translatable("message.battlegroundsmod.kame_perdeu"), false);
            DbQuests.sync(player);
            return;
        }
        // he teaches the Kamehameha: the player's own technique (Powers.KAMEHAMEHA), worn now if no soul is
        boolean soulless = !player.getData(ModAttachments.POWER).hasPower();
        if (soulless) {
            PowerLogic.select(player, Powers.KAMEHAMEHA);
        } else {
            SoulSync.touch(player, Powers.KAMEHAMEHA.id());
            player.displayClientMessage(Component.translatable("message.battlegroundsmod.kame_livro"), false);
        }
        player.getPersistentData().putBoolean("souls_kame_feito", true);
        dailyUse(player, "souls_kame_dia");
        player.connection.send(new ClientboundSetTitlesAnimationPacket(8, 70, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("message.battlegroundsmod.kame_aprendeu")
                .withStyle(s -> s.withColor(0x5AB8FF).withBold(true))));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("message.battlegroundsmod.kame_ensinou")));
        Fx.burst(level, Fx.dust(0x5AB8FF, 1.8f), player.position().add(0, 1, 0), 70, 0.7, 0.08);
        Fx.sound(level, player.position(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
        reward(player, 0.35);
        DbQuests.advance(player, "kame", 1);
        DbQuests.sync(player);
    }

    // The sparring hurts a third of a real fight, and the old man never goes below a third of his life (he gives up at half).
    private static void onIncoming(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) {
            return;
        }
        if (event.getSource().getEntity() instanceof LivingEntity attacker && attacker.getTags().contains(SPAR_TAG)) {
            event.setAmount(event.getAmount() * 0.3f);
        }
        if (victim.getTags().contains(SPAR_TAG)) {
            float floor = victim.getMaxHealth() * 0.34f;
            event.setAmount(Math.max(0, Math.min(event.getAmount(), victim.getHealth() - floor)));
        }
    }

    // Losing the sparring is not dying: the old man stops it (and his fighting body never dies for real).
    private static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide()) {
            return;
        }
        if (dead.getTags().contains(SPAR_TAG)) {
            event.setCanceled(true);
            dead.setHealth(dead.getMaxHealth() * 0.4f);
            return;
        }
        if (dead instanceof ServerPlayer player && SPAR.containsKey(player.getUUID())) {
            event.setCanceled(true);
            player.setHealth(Math.max(2f, player.getMaxHealth() * 0.3f));
            sparEnd(player, false);
        }
    }

    // The HUD while something runs: the milk's spot and time, the gravity's seconds, the wave.
    static Hud hud(ServerPlayer player) {
        LivingEntity kame = sparKame(player);
        if (kame != null) {
            int pct = Math.round(100 * kame.getHealth() / Math.max(1, kame.getMaxHealth()));
            return new Hud("kame_luta", pct, 50, 0, 0, false);
        }
        Milk milk = MILK.get(player.getUUID());
        if (milk != null) {
            int left = (int) Math.max(0, (milk.until - player.serverLevel().getGameTime()) / 20);
            String part = milk.training ? "kame_casco_" : "kame_";
            return milk.delivered ? new Hud(part + "volta", left, 0, milk.home.getX(), milk.home.getZ(), true)
                    : new Hud(part + "leite", left, 0, milk.spot.getX(), milk.spot.getZ(), true);
        }
        if (IN_GRAVITY.containsKey(player.getUUID())) {
            return new Hud("gravidade", player.getPersistentData().getInt("souls_gravidade_s"), GRAVITY_SECONDS, 0, 0, false);
        }
        Waves w = WAVES.get(player.getUUID());
        if (w != null) {
            return new Hud(w.kind + "_luta", w.stage, w.waves.size(), 0, 0, false);
        }
        return null;
    }
}
