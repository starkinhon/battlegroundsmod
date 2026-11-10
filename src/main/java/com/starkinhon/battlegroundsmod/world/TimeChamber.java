package com.starkinhon.battlegroundsmod.world;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.challenge.Universes;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.Progression;

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
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// The Room of Spirit and Time (Stark, 2026-11-10): its own dimension (battlegroundsmod:sala_tempo), an endless white
// floor under a white sky, and the house of the anime on its tiled platform (tools/structures/dragonball2.py,
// time_chamber). The way in is the wooden door at the end of the corridor behind Kami's palace: standing on the pad in
// front of it for two seconds. The pad inside the house goes back.
// Training (Stark: "ficar mais forte numa gravidade alta"): off the platform the gravity is heavy, and heavier the further
// from the house (10x, 50x, 100x: slower, lower jumps, harder falls); moving around out there fills the Poder bar
// (Ki), faster in the heavier zones. Time runs differently: every 10 seconds inside is a day, counted on screen, and
// like in the anime one person only gets one year in there: at day 365 the room sends them back and won't take them again.
public final class TimeChamber {
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "sala_tempo"));
    private static final int FLOOR_Y = 2;                 // first air above the white floor; the platform's origin
    private static final int PAD_TICKS = 40;
    private static final int DAY_TICKS = 200;             // 10 s inside = one day
    public static final int YEAR = 365;
    private static final String DAYS = "battlegroundsmod_sala_ticks";
    private static final String DONE = "battlegroundsmod_sala_ano";
    private static final String KI = "battlegroundsmod_sala_ki";
    // Gravity zones: name, gravity multiplier, speed and jump changes, Poder per minute of training (in enemies of the
    // player's own tier).
    private static final String[] ZONE_NAME = {"10x", "50x", "100x"};
    private static final double[] GRAVITY = {1.0, 2.0, 3.0};
    private static final double[] SPEED = {-0.15, -0.3, -0.45};
    private static final double[] JUMP = {-0.1, -0.2, -0.3};
    private static final double[] RATE = {0.8, 1.6, 2.5};
    private static final ResourceLocation GRAVITY_ID = id("sala_gravidade");
    private static final ResourceLocation SPEED_ID = id("sala_velocidade");
    private static final ResourceLocation JUMP_ID = id("sala_pulo");

    private static final Map<UUID, Integer> ON_PAD = new HashMap<>();
    private static final Map<UUID, Vec3> LAST = new HashMap<>();
    private static final Map<UUID, Long> LAST_HIT = new HashMap<>();
    // Training dummies (Stark 2026-11-10): three in each gravity zone, on a light-blue ring, facing the house.
    private static final int[][] DUMMIES = {{-6, 38}, {0, 42}, {6, 38}, {-10, 95}, {0, 100}, {10, 95}, {-12, 165}, {0, 170}, {12, 165}};

    private TimeChamber() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, path);
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(TimeChamber::onServerTick);
        NeoForge.EVENT_BUS.addListener(TimeChamber::onHurt);
    }

    public static boolean in(ServerPlayer player) {
        return player.level().dimension() == LEVEL;
    }

    // The pad in front of the door at the end of the corridor behind Kami's palace (see kami_lookout).
    private static BlockPos doorPad() {
        return new BlockPos(DragonBallPlaces.LOOKOUT_X, DragonBallPlaces.LOOKOUT_Y, DragonBallPlaces.LOOKOUT_Z - 49);
    }

    private static boolean onPad(ServerPlayer player, BlockPos at) {
        return Math.abs(player.getX() - (at.getX() + 0.5)) < 0.9 && Math.abs(player.getZ() - (at.getZ() + 0.5)) < 0.9
                && Math.abs(player.getY() - (at.getY() + 1)) < 1.2;
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (player.isSpectator()) {
                continue;
            }
            if (in(player)) {
                inside(player);
            } else {
                clearGravity(player);
                LAST.remove(player.getUUID());
                ServerLevel level = player.serverLevel();
                boolean pad = DragonBallPlaces.MAP.equals(mapOf(level)) && onPad(player, doorPad())
                        && level.getBlockState(doorPad()).is(net.minecraft.world.level.block.Blocks.SEA_LANTERN);
                pad(player, pad, true);
            }
        }
    }

    private static String mapOf(ServerLevel level) {
        return level.getChunkSource().getGenerator() instanceof MapChunkGenerator drawn ? drawn.mapName() : null;
    }

    // Two seconds on a pad, then through.
    private static void pad(ServerPlayer player, boolean on, boolean goingIn) {
        if (!on) {
            ON_PAD.remove(player.getUUID());
            return;
        }
        int ticks = ON_PAD.merge(player.getUUID(), 1, Integer::sum);
        if (ticks < 0) {
            return;                                         // just arrived on it from the other side
        }
        if (ticks % 8 == 1) {
            Fx.burst(player.serverLevel(), ParticleTypes.END_ROD, player.position(), 8, 0.4, 0.8);
            player.displayClientMessage(Component.translatable(goingIn ? "message.battlegroundsmod.sala_entrando"
                    : "message.battlegroundsmod.sala_saindo", Math.max(0, (PAD_TICKS - ticks) / 20 + 1)), true);
        }
        if (ticks < PAD_TICKS) {
            return;
        }
        ON_PAD.put(player.getUUID(), -60);
        if (goingIn) {
            enter(player);
        } else {
            leave(player);
        }
    }

    private static void enter(ServerPlayer player) {
        if (player.getPersistentData().getBoolean(DONE)) {
            player.displayClientMessage(Component.translatable("message.battlegroundsmod.sala_fechada"), true);
            return;
        }
        ServerLevel room = player.server.getLevel(LEVEL);
        if (room == null) {
            return;
        }
        build(room);
        Fx.burst(player.serverLevel(), ParticleTypes.REVERSE_PORTAL, player.position().add(0, 1, 0), 60, 0.6, 0.6);
        player.teleportTo(room, 0.5, FLOOR_Y + 3, -1.5, 0f, 0f);
        player.fallDistance = 0;
        Fx.sound(room, player.position(), SoundEvents.BEACON_ACTIVATE, 0.8f, 1.2f);
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("message.battlegroundsmod.sala_titulo")
                .withStyle(s -> s.withColor(0xF2C94C).withBold(true))));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("message.battlegroundsmod.sala_sub",
                day(player), YEAR)));
    }

    private static void leave(ServerPlayer player) {
        clearGravity(player);
        ServerLevel back = player.server.getLevel(Universes.key("dragonball"));
        if (back == null) {
            back = player.server.overworld();
        }
        Fx.burst(player.serverLevel(), ParticleTypes.REVERSE_PORTAL, player.position().add(0, 1, 0), 60, 0.6, 0.6);
        player.teleportTo(back, DragonBallPlaces.LOOKOUT_X + 0.5, DragonBallPlaces.LOOKOUT_Y + 1,
                DragonBallPlaces.LOOKOUT_Z - 30 + 0.5, 0f, 0f);
        player.fallDistance = 0;
        Fx.sound(back, player.position(), SoundEvents.BEACON_DEACTIVATE, 0.8f, 1.2f);
    }

    // The platform and the house, put up the first time anyone comes in (the room's floor itself is the dimension's).
    private static void build(ServerLevel room) {
        BlockPos marker = new BlockPos(0, FLOOR_Y + 2, 0);
        if (!room.getBlockState(marker).isAir()) {
            return;
        }
        Build b = new Build(0, FLOOR_Y, 0, 365).template("dragonball/time_chamber", 0, 0, 0);
        for (Build.Put put : b.puts) {
            room.setBlock(put.pos(), put.state(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
        for (int[] d : DUMMIES) {
            for (int i = -3; i <= 3; i++) {
                for (int k = -3; k <= 3; k++) {
                    double r = Math.hypot(i, k);
                    if (r <= 3.2 && r > 2.2) {
                        room.setBlock(new BlockPos(d[0] + i, FLOOR_Y - 1, d[1] + k), net.minecraft.world.level.block.Blocks.LIGHT_BLUE_CONCRETE
                                .defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
            }
            var dummy = com.starkinhon.battlegroundsmod.ModEntities.DUMMY.get().create(room);
            if (dummy != null) {
                dummy.moveTo(d[0] + 0.5, FLOOR_Y, d[1] + 0.5, 180f, 0f);
                dummy.setYHeadRot(180f);
                dummy.setYBodyRot(180f);
                room.addFreshEntity(dummy);
            }
        }
    }

    private static int day(ServerPlayer player) {
        return player.getPersistentData().getInt(DAYS) / DAY_TICKS + 1;
    }

    // The gravity zone the player is in (0 = 10x, 1 = 50x, 2 = 100x), -1 on the platform.
    private static int zone(ServerPlayer player) {
        if (onPlatform(player)) {
            return -1;
        }
        double d = Math.hypot(player.getX(), player.getZ() + 1);
        return d < 60 ? 0 : d < 140 ? 1 : 2;
    }

    // Poder from training, in fractions of an enemy of the player's own tier; kept small until it adds up.
    private static void train(ServerPlayer player, double enemies) {
        if (!Progression.on(player)) {
            return;
        }
        PowerData data = player.getData(ModAttachments.POWER);
        var tag = player.getPersistentData();
        float ki = tag.getFloat(KI) + (float) (Progression.value(data.tier) * enemies);
        if (ki >= Math.max(0.01, Progression.value(data.tier) * 0.02)) {
            data.poder += ki;
            ki = 0;
            Progression.climb(player);
            Progression.sync(player);
        }
        tag.putFloat(KI, ki);
    }

    // Hitting a training dummy out in the gravity trains three times faster than walking (at most two hits a second).
    private static void onHurt(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player) || !in(player)
                || !(event.getEntity() instanceof com.starkinhon.battlegroundsmod.dummy.TrainingDummy)) {
            return;
        }
        int zone = zone(player);
        long now = player.serverLevel().getGameTime();
        Long last = LAST_HIT.get(player.getUUID());
        if (zone < 0 || (last != null && now - last < 10)) {
            return;
        }
        LAST_HIT.put(player.getUUID(), now);
        train(player, RATE[zone] / 60.0 * 1.5);
        Fx.burst(player.serverLevel(), ParticleTypes.CRIT, event.getEntity().position().add(0, 1, 0), 4 + zone * 3, 0.3, 0.2);
    }

    private static boolean onPlatform(ServerPlayer player) {
        return Math.abs(player.getX()) <= 38 && player.getZ() >= -16 && player.getZ() <= 27 && player.getY() < FLOOR_Y + 30;
    }

    private static void inside(ServerPlayer player) {
        // the pad home, in the house
        pad(player, onPad(player, new BlockPos(0, FLOOR_Y + 2, -9)), false);
        if (!in(player)) {
            return;
        }
        var tag = player.getPersistentData();
        int ticks = tag.getInt(DAYS) + 1;
        tag.putInt(DAYS, ticks);
        if (ticks / DAY_TICKS >= YEAR) {
            tag.putBoolean(DONE, true);
            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
            player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("message.battlegroundsmod.sala_ano")
                    .withStyle(s -> s.withColor(0xF2C94C).withBold(true))));
            leave(player);
            return;
        }
        int zone = zone(player);
        if (zone < 0) {
            clearGravity(player);
        } else {
            setGravity(player, zone);
        }
        if (player.tickCount % 20 != 0) {
            return;
        }
        // training: moving around in the heavy gravity fills the bar
        Vec3 now = player.position();
        Vec3 last = LAST.put(player.getUUID(), now);
        boolean trained = zone >= 0 && last != null && Math.hypot(now.x - last.x, now.z - last.z) > 0.8;
        PowerData data = player.getData(ModAttachments.POWER);
        Component bar = Progression.barName(data.power, player.getData(ModAttachments.UNIVERSE));
        if (trained) {
            train(player, RATE[zone] / 60.0);
        }
        Component line = zone < 0
                ? Component.translatable("message.battlegroundsmod.sala_hud_casa", day(player), YEAR)
                : Component.translatable("message.battlegroundsmod.sala_hud", day(player), YEAR, ZONE_NAME[zone], bar,
                        String.format("%.1f", data.poder), Math.round(Progression.cost(data.tier)));
        player.displayClientMessage(line, true);
        if (zone >= 0 && trained && player.getRandom().nextInt(3) == 0) {
            Fx.burst(player.serverLevel(), ParticleTypes.CLOUD, now, 3 + zone * 2, 0.3, 0.02);
        }
    }

    private static void setGravity(ServerPlayer player, int zone) {
        modify(player, Attributes.GRAVITY, GRAVITY_ID, GRAVITY[zone]);
        modify(player, Attributes.MOVEMENT_SPEED, SPEED_ID, SPEED[zone]);
        modify(player, Attributes.JUMP_STRENGTH, JUMP_ID, JUMP[zone]);
    }

    private static void modify(ServerPlayer player, Holder<Attribute> attribute, ResourceLocation id, double amount) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) {
            instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }

    private static void clearGravity(ServerPlayer player) {
        for (var pair : new Object[][] {{Attributes.GRAVITY, GRAVITY_ID}, {Attributes.MOVEMENT_SPEED, SPEED_ID},
                {Attributes.JUMP_STRENGTH, JUMP_ID}}) {
            @SuppressWarnings("unchecked")
            AttributeInstance instance = player.getAttribute((Holder<Attribute>) pair[0]);
            if (instance != null && instance.hasModifier((ResourceLocation) pair[1])) {
                instance.removeModifier((ResourceLocation) pair[1]);
            }
        }
    }
}
