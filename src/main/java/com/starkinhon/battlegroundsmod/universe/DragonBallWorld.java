package com.starkinhon.battlegroundsmod.universe;

import java.util.ArrayList;
import java.util.List;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.starkinhon.battlegroundsmod.ModItems;
import com.starkinhon.battlegroundsmod.boss.SoulBoss;
import com.starkinhon.battlegroundsmod.challenge.Universes;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.power.Powers;
import com.starkinhon.battlegroundsmod.power.Progression;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// What fills the Dragon Ball universe besides its characters (Stark 2026-11-10: "items with a real advantage, fill the
// world, be creative"):
// - its common enemies drop its items now and then: Soldado do Freeza -> Scouter, Saibaman -> Senzu, Soldado da Red
//   Ribbon -> Cápsula Hoi-Poi;
// - any enemy beaten there may drop an Esfera do Dragão the player is still missing (SubAlmas much more often, three
//   times as often with the Radar in the inventory);
// - with the seven, Shenlong comes and grants one wish (universe/shenlong/ShenlongServer).
public final class DragonBallWorld {
    public static final String UNIVERSE = "dragonball";
    private static final double BALL_CHANCE = 0.03;
    private static final double BALL_CHANCE_SUB = 0.3;

    private DragonBallWorld() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(DragonBallWorld::onDeath);
        NeoForge.EVENT_BUS.addListener(DragonBallWorld::onServerTick);
    }

    private static boolean here(Entity entity) {
        return UNIVERSE.equals(UniverseBiomes.universeAt(entity.level(), entity.blockPosition()));
    }

    private static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || !here(victim) || !(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!(victim instanceof Enemy) && !(victim instanceof SoulBoss)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        // the creatures' own drops (Scouter, Senzu, Radar) are in their kit now (animal/dbmob/DbMobMoves.drop)
        boolean sub = victim instanceof SoulBoss boss && Powers.isSub(boss.powerData().power);
        double ball = (sub ? BALL_CHANCE_SUB : BALL_CHANCE) * (has(player, ModItems.DRAGON_RADAR.get()) ? 3 : 1);
        if (level.getRandom().nextDouble() < ball) {
            List<Item> missing = missing(player);
            List<Integer> available = new ArrayList<>();
            for (Item item : missing) {
                int star = ModItems.DRAGON_BALLS.indexOf(ModItems.DRAGON_BALLS.stream().filter(b -> b.get() == item).findFirst().orElse(null)) + 1;
                if (star >= 1 && !isClaimed(level, star)) {
                    available.add(star);
                }
            }
            if (!available.isEmpty()) {
                int star = available.get(level.getRandom().nextInt(available.size()));
                ItemStack stack = claimBall(level, star);
                if (!stack.isEmpty()) {
                    drop(level, victim, stack);
                    Fx.burst(level, ParticleTypes.END_ROD, victim.position().add(0, 1, 0), 30, 0.4, 0.1);
                    Fx.sound(level, victim.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 1.4f);
                }
            }
        }
    }

    private static void drop(ServerLevel level, LivingEntity victim, ItemStack stack) {
        ItemEntity entity = new ItemEntity(level, victim.getX(), victim.getY() + 0.5, victim.getZ(), stack);
        entity.setDefaultPickUpDelay();
        entity.setGlowingTag(true);
        level.addFreshEntity(entity);
    }

    // By item only: a ball carries its copy number (GEN_TAG), so a bare ItemStack never matches it component for component.
    private static boolean has(ServerPlayer player, Item item) {
        return slotOf(player, item) >= 0;
    }

    // The main inventory slot holding this item (any components), -1 if none.
    public static int slotOf(ServerPlayer player, Item item) {
        var items = player.getInventory().items;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).is(item)) {
                return i;
            }
        }
        return -1;
    }

    public static boolean isClaimed(ServerLevel level, int star) {
        if (star < 1 || star > 7) {
            return true;
        }
        return BallRegistry.get(level.getServer()).claimed[star - 1];
    }

    public static boolean tryClaim(ServerLevel level, int star) {
        if (star < 1 || star > 7) {
            return false;
        }
        BallRegistry reg = BallRegistry.get(level.getServer());
        if (reg.claimed[star - 1]) {
            return false;
        }
        reg.claim(star, now(level.getServer()));
        return true;
    }

    // ---------- a lost ball comes back (Stark 2026-11-20, "as duas coisas") ----------
    // Every new ball of a star gets the next copy number (BallRegistry.gen) written on it. A star nobody has seen for
    // LOST_TICKS (3 days of game: in an online player's inventory or on the ground in a loaded chunk) is freed and can
    // drop again; /soul esferas liberar frees them all at once. The old copy is not lost at that point: taken out of a
    // chest before a new one drops it simply holds its star again. Only once a newer copy exists does the old one turn
    // to stone where it is found, so there are never two of the same star.
    public static final String GEN_TAG = "souls_esfera_copia";
    private static final long LOST_TICKS = 3 * 24000L;
    private static final int CHECK_EVERY = 20;

    // Claims the star and makes its ball (empty when the star is taken).
    public static ItemStack claimBall(ServerLevel level, int star) {
        if (!tryClaim(level, star)) {
            return ItemStack.EMPTY;
        }
        return stamped(level.getServer(), star);
    }

    // The ball of a star just claimed, with the star's current copy number.
    public static ItemStack stamped(MinecraftServer server, int star) {
        ItemStack stack = new ItemStack(ModItems.DRAGON_BALLS.get(star - 1).get());
        long gen = BallRegistry.get(server).gen[star - 1];
        net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, stack,
                tag -> tag.putLong(GEN_TAG, gen));
        return stack;
    }

    private static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    // A ball in an inventory or on the ground, once a second (DragonBallItem): false when a newer copy of its star exists
    // (the caller turns it to stone). A ball with no copy number (from before this, /give, creative) counts as the first
    // copy; in a creative player's hands it is always left alone.
    public static boolean checkBall(ServerLevel level, ItemStack stack, Entity holder) {
        if (!(stack.getItem() instanceof com.starkinhon.battlegroundsmod.item.DragonBallItem ball)
                || level.getGameTime() % CHECK_EVERY != 0) {
            return true;
        }
        int star = ball.stars();
        if (star < 1 || star > 7) {
            return true;
        }
        var data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        boolean marked = data != null && data.contains(GEN_TAG);
        if (!marked && holder instanceof net.minecraft.world.entity.player.Player p && p.isCreative()) {
            return true;
        }
        BallRegistry reg = BallRegistry.get(level.getServer());
        long gen = marked ? data.copyTag().getLong(GEN_TAG) : 0;
        if (gen != reg.gen[star - 1]) {
            return false;
        }
        reg.seen(star, now(level.getServer()));
        return true;
    }

    // What a stale ball becomes.
    public static ItemStack stone() {
        return new ItemStack(net.minecraft.world.item.Items.STONE);
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 200 != 0) {
            return;
        }
        BallRegistry reg = BallRegistry.get(server);
        long now = now(server);
        for (int i = 0; i < 7; i++) {
            if (!reg.claimed[i]) {
                continue;
            }
            if (reg.seen[i] <= 0 || reg.seen[i] > now) {
                // a save from before this (or a world whose clock went back): the 3 days start now
                reg.seen[i] = now;
                reg.setDirty();
            } else if (now - reg.seen[i] > LOST_TICKS) {
                reg.claimed[i] = false;
                reg.setDirty();
                com.starkinhon.battlegroundsmod.BattlegroundsMod.LOGGER.info("Esfera de {} estrela(s) sumida há 3 dias: liberada", i + 1);
            }
        }
    }

    // /soul esferas liberar: every star can drop again right away (the old copies, if they come back first, keep theirs).
    public static LiteralArgumentBuilder<CommandSourceStack> ballsCommand() {
        return Commands.literal("esferas").requires(s -> s.hasPermission(2))
                .then(Commands.literal("liberar").executes(context -> {
                    releaseAll(context.getSource().getServer());
                    context.getSource().sendSuccess(() -> Component.translatable("message.battlegroundsmod.esferas_liberadas"), true);
                    return 1;
                }));
    }

    public static int claimRandom(ServerLevel level) {
        BallRegistry reg = BallRegistry.get(level.getServer());
        List<Integer> free = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            if (!reg.claimed[i]) {
                free.add(i + 1);
            }
        }
        if (free.isEmpty()) {
            return -1;
        }
        int star = free.get(level.getRandom().nextInt(free.size()));
        reg.claim(star, now(level.getServer()));
        return star;
    }

    public static void releaseAll(MinecraftServer server) {
        BallRegistry reg = BallRegistry.get(server);
        java.util.Arrays.fill(reg.claimed, false);
        reg.setDirty();
    }

    // Global per-world registry so only one Dragon Ball of each star (1..7) exists at any time.
    private static final class BallRegistry extends SavedData {
        final boolean[] claimed = new boolean[7];
        // the copy number of each star's newest ball, and the game time it was last seen
        final long[] gen = new long[7];
        final long[] seen = new long[7];

        void claim(int star, long now) {
            claimed[star - 1] = true;
            gen[star - 1]++;
            seen[star - 1] = now;
            setDirty();
        }

        // Held again (a freed star whose newest copy came back) and seen now; saved only now and then.
        void seen(int star, long now) {
            int i = star - 1;
            if (!claimed[i]) {
                claimed[i] = true;
                seen[i] = now;
                setDirty();
            } else if (now - seen[i] > 1200 || now < seen[i]) {
                seen[i] = now;
                setDirty();
            }
        }

        static BallRegistry get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(
                    new Factory<>(BallRegistry::new, BallRegistry::load, null), "battlegroundsmod_esferas_unicas");
        }

        static BallRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
            BallRegistry reg = new BallRegistry();
            int mask = tag.getInt("mask");
            for (int i = 0; i < 7; i++) {
                reg.claimed[i] = (mask & (1 << i)) != 0;
                reg.gen[i] = tag.getLong("gen" + i);
                reg.seen[i] = tag.getLong("seen" + i);
            }
            return reg;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            int mask = 0;
            for (int i = 0; i < 7; i++) {
                if (claimed[i]) {
                    mask |= (1 << i);
                }
            }
            tag.putInt("mask", mask);
            for (int i = 0; i < 7; i++) {
                tag.putLong("gen" + i, gen[i]);
                tag.putLong("seen" + i, seen[i]);
            }
            return tag;
        }
    }

    // The Esferas the player doesn't carry yet.
    public static List<Item> missing(ServerPlayer player) {
        List<Item> missing = new ArrayList<>();
        for (var ball : ModItems.DRAGON_BALLS) {
            if (!has(player, ball.get())) {
                missing.add(ball.get());
            }
        }
        return missing;
    }

    // Radar: which Esferas are missing, and where the nearest enemy is (an Esfera may be with it).
    public static void radar(ServerPlayer player) {
        List<Item> missing = missing(player);
        if (missing.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.battlegroundsmod.radar_all"), false);
            return;
        }
        StringBuilder stars = new StringBuilder();
        for (Item item : missing) {
            if (item instanceof com.starkinhon.battlegroundsmod.item.DragonBallItem ball) {
                stars.append(stars.length() == 0 ? "" : ", ").append(ball.stars());
            }
        }
        player.displayClientMessage(Component.translatable("message.battlegroundsmod.radar_missing", stars.toString()), false);
        // the balls Shenlong scattered over the world: the nearest one's way and distance
        int[] ball = com.starkinhon.battlegroundsmod.universe.shenlong.ShenlongServer.nearestScattered(player);
        if (ball != null) {
            Vec3 to = new Vec3(ball[0] + 0.5 - player.getX(), 0, ball[1] + 0.5 - player.getZ());
            player.displayClientMessage(Component.translatable("message.battlegroundsmod.radar_esfera", ball[2], Math.round(to.horizontalDistance()),
                    direction(to)), false);
        }
        LivingEntity nearest = null;
        for (LivingEntity entity : player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(96),
                e -> e instanceof SoulBoss && e.isAlive())) {
            if (nearest == null || entity.distanceToSqr(player) < nearest.distanceToSqr(player)) {
                nearest = entity;
            }
        }
        if (nearest != null) {
            Vec3 to = nearest.position().subtract(player.position());
            player.displayClientMessage(Component.translatable("message.battlegroundsmod.radar_target", nearest.getName(),
                    Math.round(to.horizontalDistance()), direction(to)), false);
        }
        Fx.sound(player.serverLevel(), player.position(), SoundEvents.NOTE_BLOCK_BIT.value(), 0.8f, 1.8f);
    }

    private static Component direction(Vec3 to) {
        double angle = Math.toDegrees(Math.atan2(-to.x, to.z));
        String[] names = {"sul", "sudoeste", "oeste", "noroeste", "norte", "nordeste", "leste", "sudeste"};
        int index = Math.floorMod((int) Math.round(angle / 45.0), 8);
        return Component.translatable("direction.battlegroundsmod." + names[index]);
    }

    // Whether the player carries at least one Dragon Ball (DbQuests' first ball).
    public static boolean hasAnyBall(ServerPlayer player) {
        for (var ball : ModItems.DRAGON_BALLS) {
            if (slotOf(player, ball.get()) >= 0) {
                return true;
            }
        }
        return false;
    }

    // With all seven: Shenlong comes (universe/shenlong/ShenlongServer, the cutscene and the wishes).
    public static void summon(ServerPlayer player) {
        com.starkinhon.battlegroundsmod.universe.shenlong.ShenlongServer.summon(player);
    }

    // The more wishes (Stark 2026-11-10: "mais pedido pro Shenlong"): a soul, a rival, eternal life for a while.
    public static final String IMMORTAL_TAG = "souls_imortal_ate";
    private static final int IMMORTAL_TICKS = 20 * 60 * 10;

    // "Vida eterna": 10 minutes of regeneration and resistance, and dying takes no Poder.
    public static void eternalLife(ServerPlayer player) {
        player.getPersistentData().putLong(IMMORTAL_TAG, player.serverLevel().getGameTime() + IMMORTAL_TICKS);
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, IMMORTAL_TICKS, 1, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, IMMORTAL_TICKS, 0, false, true));
    }

    // Whether the "imortal" wish still holds: dying then loses no Poder (Progression).
    public static boolean immortal(ServerPlayer player) {
        return player.getPersistentData().getLong(IMMORTAL_TAG) > player.serverLevel().getGameTime();
    }

    // "Alma": the soul of a Dragon Ball character the player already beat in its arena (Kuririn's if none yet).
    public static ItemStack wishedSoul(ServerPlayer player) {
        var spec = com.starkinhon.battlegroundsmod.skill.ChallengeSpec.get();
        var universe = spec.universe("dragonball");
        List<String> beaten = new java.util.ArrayList<>();
        if (universe != null) {
            for (var c : universe.desafios()) {
                if (com.starkinhon.battlegroundsmod.challenge.ChallengeArenas.wins(player).contains(c.id())) {
                    beaten.add(c.id());
                }
            }
        }
        String id = beaten.isEmpty() ? "kuririn" : beaten.get(player.getRandom().nextInt(beaten.size()));
        try {
            var soul = ModItems.SOULS.get(com.starkinhon.battlegroundsmod.boss.BossSpec.get(id).poder());
            if (soul != null) {
                return new ItemStack(soul.get());
            }
        } catch (RuntimeException ignored) {
        }
        return new ItemStack(ModItems.SENZU.get(), 5);
    }

    // "Rival": a Dragon Ball character comes down to fight, out in the world (the next one the player has not beaten
    // in the book, else any of them).
    public static boolean rival(ServerPlayer player) {
        var spec = com.starkinhon.battlegroundsmod.skill.ChallengeSpec.get();
        var universe = spec.universe("dragonball");
        if (universe == null || universe.desafios().isEmpty()) {
            return false;
        }
        String pick = null;
        for (var c : universe.desafios()) {
            if (!com.starkinhon.battlegroundsmod.challenge.ChallengeArenas.wins(player).contains(c.id())) {
                pick = c.id();
                break;
            }
        }
        if (pick == null) {
            pick = universe.desafios().get(player.getRandom().nextInt(universe.desafios().size())).id();
        }
        var key = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.starkinhon.battlegroundsmod.BattlegroundsMod.MODID, pick);
        if (!net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.containsKey(key)) {
            return false;
        }
        var type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(key);
        var at = player.blockPosition().relative(player.getDirection(), 8);
        at = player.serverLevel().getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at);
        var entity = type.spawn(player.serverLevel(), at, net.minecraft.world.entity.MobSpawnType.EVENT);
        if (entity instanceof net.minecraft.world.entity.Mob mob) {
            mob.setTarget(player);
            mob.setPersistenceRequired();
        }
        return entity != null;
    }

    // /soul desejo <id>: the wish without the menu, only while Shenlong's menu is up.
    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("desejo")
                .then(Commands.argument("desejo", StringArgumentType.word())
                        .executes(context -> {
                            com.starkinhon.battlegroundsmod.universe.shenlong.ShenlongServer.pick(context.getSource().getPlayerOrException(),
                                    StringArgumentType.getString(context, "desejo"));
                            return 1;
                        }));
    }
}
