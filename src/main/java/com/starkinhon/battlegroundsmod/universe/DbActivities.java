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
import com.starkinhon.battlegroundsmod.npc.UniverseNpc;
import com.starkinhon.battlegroundsmod.power.Progression;
import com.starkinhon.battlegroundsmod.power.Tiers;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.Db;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.DbCommon;
import com.starkinhon.battlegroundsmod.world.TimeChamber;
import com.starkinhon.battlegroundsmod.world.WorldSites;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// One thing to do at each place of the Dragon Ball map (Stark 2026-11-10: "uma coisinha pra ter em cada estrutura, pra ter
// sentido elas existirem, senão fica só de decoração"):
// - Capsule Corp: Bulma, who gives the missions (DbQuests) and the radar;
// - Kame House: Roshi's milk run (take the milk to the spot he says and come back before the time runs out), daily;
// - Goku's house: Gohan teaches the flight and gives his grandfather's four-star ball;
// - the crashed ship: the gravity machine, 50x gravity around it that trains Poder, two minutes a day;
// - Cell's ring: three waves of Cell Jr., daily;
// - Babidi's ship: its guardians one after the other, Pui Pui, Yakon and Dabura, daily;
// - Buu's house: Buu wants sweets (cake, cookie, pumpkin pie); give him one and he gives something rare, daily.
// The NPCs stand at fixed places and come back when a player is near and they are missing.
public final class DbActivities {
    public static final int BULMA_X = -131, BULMA_Z = -123;
    public static final int KAME_X = 412, KAME_Z = 88;
    public static final int GOKU_X = 323, GOKU_Z = -29;
    public static final int WRECK_X = -319, WRECK_Z = 57;
    public static final int CELL_X = -54, CELL_Z = -289;
    public static final int BABIDI_X = -239, BABIDI_Z = 95;
    public static final int BOO_X = -268, BOO_Z = -430;

    private static final int WRECK_RADIUS = 8;
    private static final int GRAVITY_SECONDS = 120;
    private static final int MILK_TICKS = 20 * 50;

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
        return DbQuests.inDragonBall(player) || player.level().dimension() == Level.OVERWORLD;
    }

    // What the HUD shows while an activity runs.
    public record Hud(String id, int count, int goal, int x, int z, boolean way) {
    }

    private static final class Milk {
        BlockPos spot;
        BlockPos home;
        long until;
        boolean delivered;
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

    private DbActivities() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(DbActivities::onTick);
    }

    private static long day(ServerPlayer player) {
        return player.serverLevel().getGameTime() / 24000L;
    }

    private static boolean dailyFree(ServerPlayer player, String tag) {
        return !player.getPersistentData().contains(tag) || player.getPersistentData().getLong(tag) < day(player);
    }

    private static void dailyUse(ServerPlayer player, String tag) {
        player.getPersistentData().putLong(tag, day(player));
    }

    private static void reward(ServerPlayer player, double tierPart, ItemStack... items) {
        Progression.add(player, Progression.cost(Tiers.of(player)) * tierPart);
        for (ItemStack stack : items) {
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
        Fx.sound(player.serverLevel(), player.position(), SoundEvents.PLAYER_LEVELUP, 0.8f, 1.2f);
    }

    private static void title(ServerPlayer player, Component title, Component sub, int color) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(6, 40, 12));
        player.connection.send(new ClientboundSetTitleTextPacket(title.copy().withStyle(s -> s.withColor(color).withBold(true))));
        player.connection.send(new ClientboundSetSubtitleTextPacket(sub));
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
            return;
        }
        say(player, npc, Component.translatable("npc.battlegroundsmod.bulma.hint." + player.getRandom().nextInt(4)));
    }

    public static void roshi(ServerPlayer player, UniverseNpc npc) {
        Milk milk = MILK.get(player.getUUID());
        if (milk != null && milk.delivered) {
            MILK.remove(player.getUUID());
            boolean first = !player.getPersistentData().getBoolean("souls_kame_feito");
            player.getPersistentData().putBoolean("souls_kame_feito", true);
            dailyUse(player, "souls_kame_dia");
            say(player, npc, Component.translatable("npc.battlegroundsmod.mestre_kame.feito"));
            title(player, Component.translatable("message.battlegroundsmod.kame_feito"), Component.empty(), 0xE8A040);
            reward(player, first ? 0.35 : 0.15, new ItemStack(Items.MILK_BUCKET));
            DbQuests.advance(player, "kame", 1);
            return;
        }
        if (milk != null) {
            say(player, npc, Component.translatable("npc.battlegroundsmod.mestre_kame.vai"));
            return;
        }
        if (!dailyFree(player, "souls_kame_dia")) {
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
        milk = new Milk();
        milk.spot = spot;
        milk.home = npc.blockPosition();
        milk.until = level.getGameTime() + MILK_TICKS;
        MILK.put(player.getUUID(), milk);
        ItemStack bucket = new ItemStack(Items.MILK_BUCKET);
        if (!player.getInventory().add(bucket)) {
            player.drop(bucket, false);
        }
        say(player, npc, Component.translatable("npc.battlegroundsmod.mestre_kame.leite"));
        title(player, Component.translatable("message.battlegroundsmod.kame_treino"), Component.translatable("message.battlegroundsmod.kame_leve",
                MILK_TICKS / 20), 0xE8A040);
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
            player.displayClientMessage(Component.translatable("message.battlegroundsmod.kame_tempo"), false);
            DbQuests.sync(player);
            return;
        }
        if (!milk.delivered && player.blockPosition().closerThan(milk.spot, 3)) {
            int slot = player.getInventory().findSlotMatchingItem(new ItemStack(Items.MILK_BUCKET));
            if (slot < 0) {
                player.displayClientMessage(Component.translatable("message.battlegroundsmod.kame_sem_leite"), true);
                return;
            }
            player.getInventory().removeItem(slot, 1);
            milk.delivered = true;
            Fx.sound(player.serverLevel(), player.position(), SoundEvents.BUCKET_EMPTY, 1f, 1f);
            player.displayClientMessage(Component.translatable("message.battlegroundsmod.kame_volte"), true);
        }
    }

    public static void boo(ServerPlayer player, UniverseNpc npc) {
        ItemStack hand = player.getMainHandItem();
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
        int roll = player.getRandom().nextInt(100);
        ItemStack gift;
        if (roll < 30) {
            gift = new ItemStack(ModItems.DRAGON_BALLS.get(player.getRandom().nextInt(ModItems.DRAGON_BALLS.size())).get());
        } else if (roll < 50) {
            gift = new ItemStack(ModItems.MULTIVERSAL_CRYSTAL.get());
        } else {
            gift = new ItemStack(ModItems.SENZU.get(), 3);
        }
        say(player, npc, Component.translatable("npc.battlegroundsmod.boo.obrigado"));
        Fx.sound(player.serverLevel(), npc.position(), SoundEvents.GENERIC_EAT, 1f, 0.7f);
        reward(player, 0.1, gift);
        DbQuests.advance(player, "boo", 1);
    }

    private static void say(ServerPlayer player, UniverseNpc npc, Component line) {
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
                start(player, "cell", List.of(List.of(ModEntities.CELL_JR.get(), ModEntities.CELL_JR.get()),
                        List.of(ModEntities.CELL_JR.get(), ModEntities.CELL_JR.get(), ModEntities.CELL_JR.get()),
                        List.of(ModEntities.CELL_JR.get(), ModEntities.CELL_JR.get(), ModEntities.CELL_JR.get(), ModEntities.CELL_JR.get())));
            } else if (nearPlace(player, WorldSites.BABIDI, 0, 0, 14) && dailyFree(player, "souls_babidi_dia")) {
                start(player, "babidi", List.of(List.of(ModEntities.PUI_PUI.get()), List.of(ModEntities.YAKON.get()), List.of(ModEntities.DABURA.get())));
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
            boolean cell = "cell".equals(w.kind);
            title(player, Component.translatable("message.battlegroundsmod." + w.kind + "_vitoria"), Component.empty(), cell ? 0x82BEAA : 0xE04040);
            reward(player, cell ? 0.4 : 0.6, cell ? new ItemStack(ModItems.SENZU.get(), 2) : new ItemStack(ModItems.MULTIVERSAL_CRYSTAL.get()));
            DbQuests.advance(player, w.kind, 1);
            DbQuests.sync(player);
            return;
        }
        List<EntityType<?>> wave = w.waves.get(w.stage);
        w.stage++;
        Component name = wave.get(0).getDescription();
        title(player, Component.translatable("message.battlegroundsmod." + w.kind + "_onda", w.stage, w.waves.size()), name.copy(),
                "cell".equals(w.kind) ? 0x82BEAA : 0xE04040);
        Vec3 look = player.getLookAngle();
        double base = Math.atan2(look.z, look.x);
        for (int i = 0; i < wave.size(); i++) {
            double a = base + (i - (wave.size() - 1) / 2.0) * 0.6;
            BlockPos at = dryGround(level, (int) Math.floor(player.getX() + Math.cos(a) * 8), (int) Math.floor(player.getZ() + Math.sin(a) * 8), 5);
            if (at == null) {
                at = player.blockPosition();
            }
            Entity e = wave.get(i).spawn(level, at, MobSpawnType.EVENT);
            if (e instanceof Mob mob) {
                mob.setTarget(player);
                mob.setPersistenceRequired();
                w.alive.add(e.getUUID());
                DbCommon.boom(level, Vec3.atBottomCenterOf(at), 1.4f, "cell".equals(w.kind) ? Db.GOLD : Db.RED, level.getRandom().nextInt(100));
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
                "cell".equals(kind) ? 0x82BEAA : 0xE04040);
        Fx.sound(player.serverLevel(), player.position(), SoundEvents.RAID_HORN.value(), 0.6f, 1.3f);
        DbQuests.sync(player);
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

    // The HUD while something runs: the milk's spot and time, the gravity's seconds, the wave.
    static Hud hud(ServerPlayer player) {
        Milk milk = MILK.get(player.getUUID());
        if (milk != null) {
            int left = (int) Math.max(0, (milk.until - player.serverLevel().getGameTime()) / 20);
            return milk.delivered ? new Hud("kame_volta", left, 0, milk.home.getX(), milk.home.getZ(), true)
                    : new Hud("kame_leite", left, 0, milk.spot.getX(), milk.spot.getZ(), true);
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
