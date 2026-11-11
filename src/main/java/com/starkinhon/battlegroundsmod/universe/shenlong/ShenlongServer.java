package com.starkinhon.battlegroundsmod.universe.shenlong;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.ModItems;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.item.DragonBallItem;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.Progression;
import com.starkinhon.battlegroundsmod.universe.DragonBallWorld;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// Shenlong in the game (Stark 2026-11-11, the approved preview forja/shenlong): with the seven balls, right click puts
// them down in front of the player and the cutscene plays for everyone near (client/fx/ShenlongScene, the same
// timeline as the preview). The summoner is held still and can't be hurt while it plays. At 22 s his own menu opens
// (client/ShenlongWishScreen); the wish comes back here, Shenlong answers, turns to light and the seven balls fly off,
// each its own way, and land far out in the world (Stark: "espalham pelo mundo"), to be hunted again with the radar.
public final class ShenlongServer {
    // The wishes of the menu (the preview's ui.js). "dominar" is the joke: the client answers it and nothing is spent.
    public static final List<String> WISHES = List.of("poder", "reviver", "alma", "imortal", "rival", "tesouro", "senzu", "amigo", "dominar");
    private static final double VIEW = 160;
    private static final int MENU_TICKS = (int) Math.round(Shen.T_MENU * 20);
    private static final int SCATTER_TICKS = (int) Math.round((29.2 - Shen.T_PICKED) * 20);
    private static final int END_TICKS = (int) Math.round((Shen.T_END - Shen.T_PICKED) * 20);
    // Where a scattered ball lands: this far from where Shenlong was called, in its own direction.
    private static final int SCATTER_MIN = 600, SCATTER_MAX = 1800;

    private static final class Call {
        final UUID summoner;
        final int summonerId;
        final ResourceKey<Level> dim;
        final Vec3 origin;
        final float yaw;
        final long start;
        String wish = "";
        long picked;
        boolean scattered;

        Call(ServerPlayer player, Vec3 origin, float yaw, long start) {
            this.summoner = player.getUUID();
            this.summonerId = player.getId();
            this.dim = player.level().dimension();
            this.origin = origin;
            this.yaw = yaw;
            this.start = start;
        }
    }

    private static final Map<UUID, Call> CALLS = new HashMap<>();

    private ShenlongServer() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ShenlongServer::onTick);
        NeoForge.EVENT_BUS.addListener(ShenlongServer::onDamage);
        NeoForge.EVENT_BUS.addListener(ShenlongServer::onDrops);
        NeoForge.EVENT_BUS.addListener(ShenlongServer::onPickup);
        NeoForge.EVENT_BUS.addListener(ShenlongServer::onClone);
    }

    public static boolean calling(ServerPlayer player) {
        return CALLS.containsKey(player.getUUID());
    }

    // With all seven: they go down in front of the player and Shenlong comes.
    public static void summon(ServerPlayer player) {
        List<net.minecraft.world.item.Item> missing = DragonBallWorld.missing(player);
        if (!missing.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.battlegroundsmod.esferas_faltam", 7 - missing.size()), true);
            return;
        }
        if (calling(player)) {
            return;
        }
        for (var ball : ModItems.DRAGON_BALLS) {
            int slot = player.getInventory().findSlotMatchingItem(new ItemStack(ball.get()));
            if (slot >= 0) {
                player.getInventory().removeItem(slot, 1);
            }
        }
        ServerLevel level = player.serverLevel();
        Call call = new Call(player, player.position(), player.getYRot(), level.getGameTime());
        CALLS.put(player.getUUID(), call);
        broadcast(level, call);
        com.starkinhon.battlegroundsmod.power.Achievements.grant(player, "shenlong");
    }

    // The wish from the menu (or /soul desejo), only while the menu is up.
    public static void pick(ServerPlayer player, String wish) {
        Call call = CALLS.get(player.getUUID());
        ServerLevel level = player.serverLevel();
        if (call == null || !call.wish.isEmpty() || level.getGameTime() - call.start < MENU_TICKS - 10
                || !WISHES.contains(wish) || "dominar".equals(wish)) {
            if (call == null) {
                player.displayClientMessage(Component.translatable("message.battlegroundsmod.wish_none"), true);
            }
            return;
        }
        if ("amigo".equals(wish) && friend(player) == null) {
            // nobody else in this world: the answer would be a lie; the client shows why and the menu stays
            player.displayClientMessage(Component.translatable("message.battlegroundsmod.shenlong.sem_amigo"), true);
            return;
        }
        call.wish = wish;
        call.picked = level.getGameTime();
        broadcast(level, call);
        grant(player, wish);
    }

    private static void broadcast(ServerLevel level, Call call) {
        ModPayloads.ShenlongState state = new ModPayloads.ShenlongState(call.summonerId, call.origin.x, call.origin.y, call.origin.z, call.yaw,
                call.start, call.wish, call.picked);
        for (ServerPlayer p : level.players()) {
            if (p.getUUID().equals(call.summoner) || p.position().distanceTo(call.origin) < VIEW) {
                PacketDistributor.sendToPlayer(p, state);
            }
        }
    }

    private static void end(MinecraftServer server, Call call) {
        ServerLevel level = server.getLevel(call.dim);
        if (level != null) {
            ModPayloads.ShenlongState stop = new ModPayloads.ShenlongState(-1, call.origin.x, call.origin.y, call.origin.z, call.yaw, call.start, "", 0);
            for (ServerPlayer p : level.players()) {
                if (p.getUUID().equals(call.summoner) || p.position().distanceTo(call.origin) < VIEW + 32) {
                    PacketDistributor.sendToPlayer(p, stop);
                }
            }
        }
    }

    private static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (!CALLS.isEmpty()) {
            for (var it = CALLS.values().iterator(); it.hasNext();) {
                Call call = it.next();
                ServerLevel level = server.getLevel(call.dim);
                ServerPlayer player = server.getPlayerList().getPlayer(call.summoner);
                if (level == null) {
                    it.remove();
                    continue;
                }
                long now = level.getGameTime();
                boolean gone = player == null || player.isRemoved() || player.level() != level;
                if (gone) {
                    // he left: the dragon goes, the balls still scatter (no wish)
                    scatter(level, call);
                    end(server, call);
                    it.remove();
                    continue;
                }
                // held still where he called him
                player.setDeltaMovement(Vec3.ZERO);
                if (player.position().distanceToSqr(call.origin) > 0.01) {
                    player.teleportTo(call.origin.x, call.origin.y, call.origin.z);
                }
                if (!call.wish.isEmpty()) {
                    long since = now - call.picked;
                    if (!call.scattered && since >= SCATTER_TICKS) {
                        scatter(level, call);
                    }
                    if (since >= END_TICKS) {
                        end(server, call);
                        it.remove();
                    }
                }
            }
        }
        if (server.getTickCount() % 40 == 0) {
            land(server);
        }
    }

    private static void onDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && calling(player)) {
            event.setCanceled(true);
        }
    }

    // ---------------- the wishes ----------------

    private static void grant(ServerPlayer player, String wish) {
        switch (wish) {
            case "poder" -> {
                PowerData data = player.getData(ModAttachments.POWER);
                double rest = Progression.cost(data.tier) - data.poder;
                if (rest <= 0 || !Progression.on(player)) {
                    give(player, new ItemStack(ModItems.SENZU.get(), 5));
                } else {
                    Progression.add(player, rest);
                }
            }
            case "reviver" -> revive(player);
            case "alma" -> give(player, DragonBallWorld.wishedSoul(player));
            case "imortal" -> DragonBallWorld.eternalLife(player);
            case "rival" -> {
                if (!DragonBallWorld.rival(player)) {
                    give(player, new ItemStack(ModItems.SENZU.get(), 5));
                }
            }
            case "tesouro" -> treasure(player);
            case "senzu" -> give(player, new ItemStack(ModItems.SENZU.get(), 5));
            case "amigo" -> {
                ServerPlayer friend = friend(player);
                if (friend != null) {
                    friend.teleportTo(player.serverLevel(), player.getX() + 1.5, player.getY(), player.getZ(), friend.getYRot(), friend.getXRot());
                    friend.displayClientMessage(Component.translatable("message.battlegroundsmod.shenlong.chamado", player.getDisplayName()), false);
                }
            }
            default -> {
            }
        }
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    // "Tesouro": gold, emeralds and diamonds at his feet, each landing on its own.
    private static void treasure(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Object[][] loot = {{Items.GOLD_INGOT, 24}, {Items.EMERALD, 12}, {Items.DIAMOND, 5}};
        for (Object[] l : loot) {
            int left = (int) l[1];
            while (left > 0) {
                int n = Math.min(left, 1 + level.getRandom().nextInt(4));
                left -= n;
                double a = level.getRandom().nextDouble() * Math.PI * 2, r = 0.6 + level.getRandom().nextDouble() * 1.6;
                ItemEntity e = new ItemEntity(level, player.getX() + Math.cos(a) * r, player.getY() + 0.4, player.getZ() + Math.sin(a) * r,
                        new ItemStack((net.minecraft.world.item.Item) l[0], n));
                e.setDeltaMovement(Math.cos(a) * 0.08, 0.25 + level.getRandom().nextDouble() * 0.15, Math.sin(a) * 0.08);
                e.setDefaultPickUpDelay();
                level.addFreshEntity(e);
            }
        }
        Fx.sound(level, player.position(), SoundEvents.AMETHYST_CLUSTER_BREAK, 1f, 1.2f);
    }

    // "Chamar um amigo": the nearest other player in the same world (any dimension of it).
    private static ServerPlayer friend(ServerPlayer player) {
        ServerPlayer best = null;
        double bestD = Double.MAX_VALUE;
        for (ServerPlayer p : player.server.getPlayerList().getPlayers()) {
            if (p == player || p.isSpectator() || !p.isAlive()) {
                continue;
            }
            double d = p.level() == player.level() ? p.distanceToSqr(player) : 1e18;
            if (best == null || d < bestD) {
                best = p;
                bestD = d;
            }
        }
        return best;
    }

    // ---------------- "Reviver": what the last death took ----------------
    private static final String LOST = "souls_reviver";

    // Progression calls it with the Poder the death took.
    public static void lostPoder(ServerPlayer player, double poder) {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("poder", poder);
        tag.put("itens", new ListTag());
        tag.putLong("morte", player.serverLevel().getGameTime());
        player.getPersistentData().put(LOST, tag);
    }

    // The items the death dropped: kept (each tagged on the ground, so one picked up again is not given twice).
    private static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        CompoundTag lost = player.getPersistentData().getCompound(LOST);
        if (lost.getLong("morte") != player.serverLevel().getGameTime()) {
            // this death took no Poder (Vida eterna, or progression off): only its items
            lost = new CompoundTag();
            lost.putDouble("poder", 0);
        }
        player.getPersistentData().put(LOST, lost);
        ListTag items = new ListTag();
        int k = 0;
        String death = player.getUUID() + ":" + player.serverLevel().getGameTime();
        for (ItemEntity drop : event.getDrops()) {
            ItemStack stack = drop.getItem();
            if (stack.isEmpty()) {
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.put("item", stack.save(player.registryAccess()));
            entry.putInt("k", k);
            items.add(entry);
            drop.getPersistentData().putString(LOST, death + ":" + k);
            k++;
        }
        lost.put("itens", items);
        lost.putString("id", death);
        lost.putDouble("x", player.getX());
        lost.putDouble("y", player.getY());
        lost.putDouble("z", player.getZ());
        lost.putString("dim", player.level().dimension().location().toString());
    }

    private static void onPickup(ItemEntityPickupEvent.Post event) {
        String mark = event.getItemEntity().getPersistentData().getString(LOST);
        if (mark.isEmpty() || event.getItemEntity().getItem().getCount() > 0) {
            return;
        }
        int cut = mark.lastIndexOf(':');
        String death = mark.substring(0, cut);
        int k = Integer.parseInt(mark.substring(cut + 1));
        ServerPlayer owner = event.getPlayer().getServer() == null ? null
                : event.getPlayer().getServer().getPlayerList().getPlayer(UUID.fromString(death.substring(0, death.indexOf(':'))));
        if (owner == null) {
            return;
        }
        CompoundTag lost = owner.getPersistentData().getCompound(LOST);
        if (!death.equals(lost.getString("id"))) {
            return;
        }
        ListTag items = lost.getList("itens", Tag.TAG_COMPOUND);
        for (int i = 0; i < items.size(); i++) {
            if (items.getCompound(i).getInt("k") == k) {
                items.remove(i);
                break;
            }
        }
    }

    // The tag survives the respawn (the new player is built from the old one).
    private static void onClone(PlayerEvent.Clone event) {
        CompoundTag lost = event.getOriginal().getPersistentData().getCompound(LOST);
        if (!lost.isEmpty()) {
            event.getEntity().getPersistentData().put(LOST, lost.copy());
        }
    }

    private static void revive(ServerPlayer player) {
        CompoundTag lost = player.getPersistentData().getCompound(LOST);
        if (lost.isEmpty()) {
            give(player, new ItemStack(ModItems.SENZU.get(), 5));
            return;
        }
        if (lost.getDouble("poder") > 0 && Progression.on(player)) {
            Progression.add(player, lost.getDouble("poder"));
        }
        // what is still lying where he died goes away first, so nothing comes back twice
        ServerLevel at = player.server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                ResourceLocation.parse(lost.getString("dim").isEmpty() ? "minecraft:overworld" : lost.getString("dim"))));
        String death = lost.getString("id");
        if (at != null && !death.isEmpty()) {
            Vec3 p = new Vec3(lost.getDouble("x"), lost.getDouble("y"), lost.getDouble("z"));
            for (ItemEntity e : at.getEntitiesOfClass(ItemEntity.class, new AABB(p, p).inflate(48),
                    e -> e.getPersistentData().getString(LOST).startsWith(death + ":"))) {
                e.discard();
            }
        }
        ListTag items = lost.getList("itens", Tag.TAG_COMPOUND);
        for (int i = 0; i < items.size(); i++) {
            ItemStack.parse(player.registryAccess(), items.getCompound(i).getCompound("item")).ifPresent(s -> give(player, s));
        }
        player.getPersistentData().remove(LOST);
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1, false, true));
    }

    // ---------------- the balls scattered over the world ----------------

    // Each ball off its own way (the same directions the client draws them flying), far out; it lands when someone
    // comes near (land), so it never sits in an unloaded chunk.
    private static void scatter(ServerLevel level, Call call) {
        if (call.scattered) {
            return;
        }
        call.scattered = true;
        Data data = data(level.getServer());
        double yaw = Math.toRadians(call.yaw);
        double[] fwd = {-Math.sin(yaw), 0, Math.cos(yaw)}, left = {Math.cos(yaw), 0, Math.sin(yaw)};
        var random = level.getRandom();
        for (int i = 0; i < 7; i++) {
            double[] d = Shen.scatterDir(i);
            double wx = d[0] * left[0] + d[2] * fwd[0], wz = d[0] * left[2] + d[2] * fwd[2];
            double dist = SCATTER_MIN + random.nextDouble() * (SCATTER_MAX - SCATTER_MIN);
            int x = (int) Math.floor(call.origin.x + wx * dist), z = (int) Math.floor(call.origin.z + wz * dist);
            CompoundTag ball = new CompoundTag();
            ball.putString("dim", call.dim.location().toString());
            ball.putInt("x", x);
            ball.putInt("z", z);
            ball.putInt("stars", i + 1);
            data.balls.add(ball);
        }
        data.setDirty();
    }

    // A scattered ball comes down where it fell once a player is near enough for its chunk to be there.
    private static void land(MinecraftServer server) {
        Data data = data(server);
        if (data.balls.isEmpty()) {
            return;
        }
        boolean changed = false;
        for (var it = data.balls.iterator(); it.hasNext();) {
            CompoundTag ball = it.next();
            ServerLevel level = server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                    ResourceLocation.parse(ball.getString("dim"))));
            if (level == null) {
                continue;
            }
            int x = ball.getInt("x"), z = ball.getInt("z");
            boolean near = false;
            for (ServerPlayer p : level.players()) {
                double dx = p.getX() - x, dz = p.getZ() - z;
                if (dx * dx + dz * dz < 64 * 64) {
                    near = true;
                    break;
                }
            }
            if (!near || !level.hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
            int stars = Math.max(1, Math.min(7, ball.getInt("stars")));
            ItemEntity e = new ItemEntity(level, x + 0.5, top.getY() + 0.3, z + 0.5, new ItemStack(ModItems.DRAGON_BALLS.get(stars - 1).get()));
            e.setDeltaMovement(Vec3.ZERO);
            e.setGlowingTag(true);
            e.setUnlimitedLifetime();
            level.addFreshEntity(e);
            Fx.sound(level, e.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 1.4f);
            it.remove();
            changed = true;
        }
        if (changed) {
            data.setDirty();
        }
    }

    // The nearest scattered ball still out there in this player's dimension (for the radar), or null.
    public static int[] nearestScattered(ServerPlayer player) {
        Data data = data(player.server);
        String dim = player.level().dimension().location().toString();
        int[] best = null;
        double bestD = Double.MAX_VALUE;
        for (CompoundTag ball : data.balls) {
            if (!dim.equals(ball.getString("dim"))) {
                continue;
            }
            double dx = ball.getInt("x") - player.getX(), dz = ball.getInt("z") - player.getZ();
            double d = dx * dx + dz * dz;
            if (d < bestD) {
                bestD = d;
                best = new int[] {ball.getInt("x"), ball.getInt("z"), ball.getInt("stars")};
            }
        }
        // a ball already on the ground near the player (landed, not picked up yet)
        for (ItemEntity e : player.serverLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(64),
                e -> e.getItem().getItem() instanceof DragonBallItem)) {
            double d = e.distanceToSqr(player);
            if (d < bestD) {
                bestD = d;
                best = new int[] {e.getBlockX(), e.getBlockZ(), ((DragonBallItem) e.getItem().getItem()).stars()};
            }
        }
        return best;
    }

    private static Data data(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null),
                "battlegroundsmod_esferas");
    }

    public static final class Data extends SavedData {
        final List<CompoundTag> balls = new ArrayList<>();

        static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data data = new Data();
            ListTag list = tag.getList("balls", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                data.balls.add(list.getCompound(i));
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag list = new ListTag();
            list.addAll(balls);
            tag.put("balls", list);
            return tag;
        }
    }
}
