package com.starkinhon.battlegroundsmod.universe;

import java.util.List;
import java.util.function.Supplier;

import com.starkinhon.battlegroundsmod.ModEntities;
import com.starkinhon.battlegroundsmod.ModItems;
import com.starkinhon.battlegroundsmod.challenge.ChallengeArenas;
import com.starkinhon.battlegroundsmod.challenge.Universes;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.Progression;
import com.starkinhon.battlegroundsmod.power.Tiers;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.Db;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.DbCommon;
import com.starkinhon.battlegroundsmod.world.TimeChamber;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// The Dragon Ball start, a chain of missions (Stark 2026-11-10: the start was still, "parece um jogo pra refletir sobre a
// vida"; he picked all three: an opening fight, Bulma's missions with an arrow on the HUD, and the radar leading to the
// balls). Raditz comes down a little after arriving; beating him sends the player to Bulma at Capsule Corp, and from
// there each mission leads to one place of the map and its own activity (DbActivities): the Saibamen, the first ball with
// the radar, Roshi's training, the book's arenas, Gohan's flight, Korin, the Time Chamber, the gravity machine, Cell's
// ring, Babidi's ship, Buu's house, and Freeza last. Each pays Poder (a part of the player's tier) and some items.
// Only with progression on. Kept per player in its persistent data; the HUD (client/QuestHud) gets it every second.
public final class DbQuests {
    private static final String STEP = "souls_dbq";
    private static final String COUNT = "souls_dbq_n";
    private static final String RADITZ_AT = "souls_dbq_raditz";
    private static final String GRANDPA_BALL = "souls_dbq_esfera4";

    // id (lang quest.battlegroundsmod.<id>), how many, the place it points to, Poder as a part of the tier, items
    private record Step(String id, int goal, int x, int z, boolean way, double poder, Supplier<List<ItemStack>> items) {
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
            new Step("raditz", 1, 0, 0, false, 0.6, items(() -> new ItemStack(ModItems.SCOUTER.get()))),
            new Step("bulma", 1, DbActivities.BULMA_X, DbActivities.BULMA_Z, true, 0.2, items(() -> new ItemStack(ModItems.DRAGON_RADAR.get()))),
            new Step("saibamen", 3, 0, 0, false, 0.3, items(() -> new ItemStack(ModItems.SENZU.get(), 2))),
            new Step("esfera", 1, 0, 0, false, 0.3, items()),
            new Step("kame", 1, DbActivities.KAME_X, DbActivities.KAME_Z, true, 0.4, items()),
            new Step("livro", 2, 0, 0, false, 0.5, items(() -> new ItemStack(ModItems.SENZU.get(), 2))),
            new Step("gohan", 1, DbActivities.GOKU_X, DbActivities.GOKU_Z, true, 0.3, items()),
            new Step("karin", 1, -407, -87, true, 0.3, items()),
            new Step("sala", 1, -407, -87, true, 0.3, items()),
            new Step("nave", 60, DbActivities.WRECK_X, DbActivities.WRECK_Z, true, 0.4, items()),
            new Step("cell", 1, DbActivities.CELL_X, DbActivities.CELL_Z, true, 0.6, items(() -> new ItemStack(ModItems.SENZU.get(), 3))),
            new Step("babidi", 1, DbActivities.BABIDI_X, DbActivities.BABIDI_Z, true, 0.8, items(() -> new ItemStack(ModItems.Z_SWORD.get()))),
            new Step("boo", 1, DbActivities.BOO_X, DbActivities.BOO_Z, true, 0.3, items()),
            new Step("freeza", 1, 0, 0, false, 1.0, items(() -> new ItemStack(ModItems.MULTIVERSAL_CRYSTAL.get(), 2))));

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

    private static boolean on(ServerPlayer player) {
        return Progression.on(player) && "dragonball".equals(player.getData(com.starkinhon.battlegroundsmod.ModAttachments.UNIVERSE));
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
        boolean here = inDragonBall(player);
        Step step = current(player);
        if (step != null) {
            switch (step.id()) {
                case "raditz" -> {
                    if (here) {
                        raditz(player);
                    }
                }
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

    // The opening fight: a little after arriving Raditz comes down near the player; if he is gone without being beaten,
    // another comes a minute later.
    private static void raditz(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        var tag = player.getPersistentData();
        long at = tag.getLong(RADITZ_AT);
        if (at == 0) {
            tag.putLong(RADITZ_AT, now + 300);
            return;
        }
        if (now < at || !level.getEntities(ModEntities.RADITZ.get(), new AABB(player.blockPosition()).inflate(96), Entity::isAlive).isEmpty()) {
            return;
        }
        tag.putLong(RADITZ_AT, now + 1200);
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
        BlockPos spot = DbActivities.dryGround(level, (int) Math.floor(player.getX() + flat.x * 9), (int) Math.floor(player.getZ() + flat.z * 9), 6);
        if (spot == null) {
            return;
        }
        Entity raditz = ModEntities.RADITZ.get().spawn(level, spot, MobSpawnType.EVENT);
        if (raditz instanceof Mob mob) {
            mob.setTarget(player);
            mob.setPersistenceRequired();
        }
        Vec3 c = Vec3.atBottomCenterOf(spot);
        DbCommon.boom(level, c, 3.2f, Db.PURPLE, level.getRandom().nextInt(100));
        DbCommon.screen(level, c, 9);
        player.connection.send(new ClientboundSetTitlesAnimationPacket(6, 50, 15));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal("RADITZ").withStyle(s -> s.withColor(0xA060FF).withBold(true))));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("message.battlegroundsmod.raditz_chegou")));
        Fx.sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), 1.6f, 0.6f);
    }

    private static void onDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType()).getPath();
        switch (id) {
            case "raditz" -> advance(player, "raditz", 1);
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

    // What the HUD shows: an activity going on (Roshi's milk run, the gravity machine...) comes first.
    public static void sync(ServerPlayer player) {
        DbActivities.Hud over = DbActivities.hud(player);
        if (over != null) {
            send(player, over.id(), over.count(), over.goal(), over.x(), over.z(), over.way());
            return;
        }
        Step step = current(player);
        if (step == null || !on(player)) {
            send(player, "", 0, 0, 0, 0, false);
            return;
        }
        send(player, step.id(), player.getPersistentData().getInt(COUNT), step.goal(), step.x(), step.z(), step.way() && inDragonBall(player));
    }

    private static void send(ServerPlayer player, String id, int count, int goal, int x, int z, boolean way) {
        PacketDistributor.sendToPlayer(player, new ModPayloads.QuestSync(id, count, goal, x, z, way ? 1 : 0));
    }
}
