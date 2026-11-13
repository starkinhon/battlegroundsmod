package com.starkinhon.battlegroundsmod.universe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.starkinhon.battlegroundsmod.ModEntities;
import com.starkinhon.battlegroundsmod.ModItems;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.power.Progression;
import com.starkinhon.battlegroundsmod.power.Tiers;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.Db;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.DbCommon;
import com.starkinhon.battlegroundsmod.world.NamekWorld;
import com.starkinhon.battlegroundsmod.world.NarutoSites;
import com.starkinhon.battlegroundsmod.world.NarutoStructGen;
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
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// Missions at the structures (Stark 2026-11-12, after finding the crashed ship in the desert: "entrei lá, apareceu uma
// missão no canto, treine por 120 segundos... é legal o cara ser recompensado por explorar as estruturas"). Not events
// out of nowhere: walking into a structure shows its name and its mission on screen and in the top-left corner (QuestHud,
// the place's name over the line), for as long as the player is there. Each place has its own thing to do:
// - Capsule Corp: bring Bulma 6 iron ingots and 3 redstone and she builds you a Cápsula Hoi-Poi (a house to throw on the
//   ground), daily (DbActivities.bulma);
// - Goku's house: Gohan teaches the flight (from Tier 9);
// - Korin's tower: climb it without flying: Korin gives the Sacred Water (Poder) and Senzu, once;
// - Kame House: the Turtle Training (the milk with the shell on the back, then a sparring with the old man) and he
//   teaches the Kamehameha as the player's own technique (DbActivities.roshi); after that the milk run is daily;
// - the crashed ship: the gravity machine (DbActivities.gravity);
// - Cell's ring: the Cell Games, with ring out (DbActivities.waves);
// - Raditz's crater: the pod's scouter beeps and three Saibamen come out of the ground; the pod opens after them, once;
// - Babidi's ship: its guardians; the first time beaten, Babidi's mark (MajinMark); crouching 3 s in the ship lets it go
//   or takes it back;
// - Buu's house: a sweet for Buu, a rare item of any universe, daily (DbActivities.boo);
// - Namek: Guru wakes the hidden power (once), and the village defended from Freeza's soldiers (daily);
// - Naruto: each structure shows what its activity is (universe/NarutoActivities).
public final class StructureMissions {
    // A place the player is in: its key (one per built place), mission id (lang quest.battlegroundsmod.<id>), count, goal.
    record Here(String key, String id, int count, int goal, int color) {
    }

    private static final Map<UUID, Here> HERE = new HashMap<>();
    // When each place was last announced to the player (game time), so going in and out doesn't spam titles.
    private static final Map<UUID, Map<String, Long>> GREETED = new HashMap<>();

    // Raditz's crater: the Saibamen out for a player, and the crater they came for.
    private record Pod(String key, BlockPos center, List<UUID> saibamen) {
    }

    private static final Map<UUID, Pod> PODS = new HashMap<>();
    private static final String PODS_DONE = "souls_raditz_feitos";

    // Korin's tower: climbing from the base without flying (the base height), and the Sacred Water once.
    private static final Map<UUID, Integer> CLIMB = new HashMap<>();
    private static final String KORIN_DONE = "souls_korin_subiu";
    private static final String GURU_DONE = "souls_guru_despertou";

    // Babidi's ship: seconds crouched in a row (3 = the mark is let go or taken back).
    private static final Map<UUID, Integer> CROUCH = new HashMap<>();

    private static final int GREET_AGAIN = 20 * 60 * 5;

    public static final int[] GURU = {-105, 90};
    public static final int[] VILLAGE = {85, -50};

    private StructureMissions() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(StructureMissions::onTick);
        NeoForge.EVENT_BUS.addListener(StructureMissions::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(StructureMissions::onClone);
        MajinMark.register();
        // /soul estruturadb <tipo>: over the nearest Dragon Ball place of a kind (capsula, goku, karin, cell, nave, raditz,
        // babidi, buu, kame), found or not yet built (testing; ops only). Namek's places are at fixed spots (GURU, VILLAGE).
        com.starkinhon.battlegroundsmod.command.SoulCommand.addSubcommand(() -> net.minecraft.commands.Commands.literal("estruturadb")
                .requires(s -> s.hasPermission(2))
                .then(net.minecraft.commands.Commands.argument("tipo", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(c -> {
                    ServerPlayer player = c.getSource().getPlayerOrException();
                    String kind = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "tipo");
                    ServerLevel level = player.server.overworld();
                    BlockPos at = WorldSites.locate(level, kind, player.blockPosition(), 24);
                    if (at == null) {
                        c.getSource().sendFailure(Component.translatable("message.battlegroundsmod.naruto.nenhuma", kind));
                        return 0;
                    }
                    player.teleportTo(level, at.getX() + 0.5, 200, at.getZ() + 30.5, 180, 30);
                    c.getSource().sendSuccess(() -> Component.translatable("message.battlegroundsmod.naruto.indo", kind, at.getX(), at.getZ()), false);
                    return 1;
                }).then(net.minecraft.commands.Commands.literal("centro").executes(c -> {
                    // /soul estruturadb <tipo> centro: onto the ground in the middle of the built place nearby
                    ServerPlayer player = c.getSource().getPlayerOrException();
                    String kind = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "tipo");
                    ServerLevel level = player.server.overworld();
                    WorldSites.Site site = WorldSites.near(level, kind, player.blockPosition(), 400);
                    if (site == null) {
                        c.getSource().sendFailure(Component.translatable("message.battlegroundsmod.naruto.nenhuma", kind));
                        return 0;
                    }
                    BlockPos ground = DbActivities.dryGround(level, site.x(), site.z(), 8);
                    BlockPos to = ground != null ? ground : site.pos().above(2);
                    player.teleportTo(level, to.getX() + 0.5, to.getY(), to.getZ() + 0.5, player.getYRot(), 20);
                    return 1;
                }))));
        // /soul falar: talk to the nearest NPC as a right click would (testing: the scripted click missed the short ones)
        com.starkinhon.battlegroundsmod.command.SoulCommand.addSubcommand(() -> net.minecraft.commands.Commands.literal("falar")
                .requires(s -> s.hasPermission(2)).executes(c -> {
                    ServerPlayer player = c.getSource().getPlayerOrException();
                    com.starkinhon.battlegroundsmod.npc.UniverseNpc npc = player.serverLevel().getNearestEntity(
                            com.starkinhon.battlegroundsmod.npc.UniverseNpc.class, net.minecraft.world.entity.ai.targeting.TargetingConditions.forNonCombat()
                                    .ignoreLineOfSight(), player, player.getX(), player.getY(), player.getZ(), player.getBoundingBox().inflate(8));
                    if (npc == null) {
                        return 0;
                    }
                    npc.getNavigation().stop();
                    npc.lookAt(player, 360, 360);
                    com.starkinhon.battlegroundsmod.npc.Npcs.talk(player, npc);
                    return 1;
                }));
        // /soul korin base|topo: the ground at the tower's foot, or Korin's floor up top
        com.starkinhon.battlegroundsmod.command.SoulCommand.addSubcommand(() -> net.minecraft.commands.Commands.literal("korin")
                .requires(s -> s.hasPermission(2))
                .then(net.minecraft.commands.Commands.argument("onde", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(c -> {
                    ServerPlayer player = c.getSource().getPlayerOrException();
                    ServerLevel level = player.serverLevel();
                    WorldSites.Site s = WorldSites.near(level, WorldSites.KARIN, player.blockPosition(), 400);
                    if (s == null) {
                        return 0;
                    }
                    if ("topo".equals(com.mojang.brigadier.arguments.StringArgumentType.getString(c, "onde"))) {
                        player.teleportTo(s.x() + 14.5, s.y() + WorldSites.KORIN_FLOOR + 1, s.z() + 3.5);
                        return 1;
                    }
                    // the first open spot over the ground on the side away from the palace
                    int x = s.x(), z = s.z() - 12;
                    for (int y = s.y() - 6; y < s.y() + 24; y++) {
                        BlockPos p = new BlockPos(x, y, z);
                        if (level.getBlockState(p.below()).isSolid() && level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir()) {
                            player.teleportTo(x + 0.5, y, z + 0.5);
                            return 1;
                        }
                    }
                    return 0;
                })));
        // /soul missaoponto: to the spot the running activity points at (Mestre Kame's milk, then back to him); testing
        com.starkinhon.battlegroundsmod.command.SoulCommand.addSubcommand(() -> net.minecraft.commands.Commands.literal("missaoponto")
                .requires(s -> s.hasPermission(2)).executes(c -> {
                    ServerPlayer player = c.getSource().getPlayerOrException();
                    DbActivities.Hud hud = DbActivities.hud(player);
                    if (hud == null || !hud.way()) {
                        return 0;
                    }
                    BlockPos to = DbActivities.dryGround(player.serverLevel(), hud.x(), hud.z(), 4);
                    if (to != null) {
                        player.teleportTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5);
                    }
                    return 1;
                }));
    }

    // ---------------------------------------------------------------- where the player is

    private static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 7) {
            return;
        }
        if (!Progression.on(player) || !player.isAlive()) {
            leave(player);
            return;
        }
        pod(player);
        korin(player);
        Here here = find(player);
        Here before = HERE.get(player.getUUID());
        if (here == null) {
            leave(player);
            return;
        }
        HERE.put(player.getUUID(), here);
        if (before == null || !before.key().equals(here.key())) {
            greet(player, here);
        }
        DbQuests.sync(player);
    }

    private static void leave(ServerPlayer player) {
        CROUCH.remove(player.getUUID());
        if (HERE.remove(player.getUUID()) != null) {
            DbQuests.sync(player);
        }
    }

    // The place's name as the title and "new mission" under it, once every few minutes per place.
    private static void greet(ServerPlayer player, Here here) {
        long now = player.serverLevel().getGameTime();
        Map<String, Long> seen = GREETED.computeIfAbsent(player.getUUID(), u -> new HashMap<>());
        Long last = seen.get(here.key());
        if (last != null && now - last < GREET_AGAIN) {
            return;
        }
        seen.put(here.key(), now);
        player.connection.send(new ClientboundSetTitlesAnimationPacket(8, 60, 16));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("quest.battlegroundsmod." + here.id() + ".titulo")
                .withStyle(s -> s.withColor(here.color()).withBold(true))));
        // the mission itself goes in the corner panel (QuestHud); the title only says there is one (a long line here
        // ran off both sides of the screen in the test)
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("message.battlegroundsmod.est_nova")
                .withStyle(s -> s.withColor(0xE6E0EE))));
        Fx.sound(player.serverLevel(), player.position(), SoundEvents.UI_TOAST_IN, 1f, 1f);
    }

    // What the HUD shows for the place the player is in (DbQuests.sync: after a running activity, before the chain).
    static DbActivities.Hud hud(ServerPlayer player) {
        Here here = HERE.get(player.getUUID());
        return here == null ? null : new DbActivities.Hud(here.id(), here.count(), here.goal(), 0, 0, false);
    }

    private static Here find(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition();
        if (level.dimension() == NamekWorld.LEVEL) {
            return namek(player);
        }
        if (level.dimension() != Level.OVERWORLD) {
            return null;
        }
        Here db = dragonBall(player, level, pos);
        if (db != null) {
            return db;
        }
        NarutoSites.Site site = NarutoSites.near(level, null, pos, 44);
        if (site != null) {
            String id = "est_" + site.kind();
            return new Here(site.id(), id, 0, 0, narutoColor(site.kind()));
        }
        return null;
    }

    private static int narutoColor(String kind) {
        return switch (kind) {
            case NarutoStructGen.FOLHA -> 0xE8B93A;
            case NarutoStructGen.MORTE, NarutoStructGen.COVIL -> 0x9AA45A;
            case NarutoStructGen.DESERTO -> 0xE0B070;
            case NarutoStructGen.NEVOA -> 0xB9C8D6;
            case NarutoStructGen.CHUVA -> 0x9FB6C8;
            case NarutoStructGen.MYOBOKU, NarutoStructGen.PICO -> 0xE6A14A;
            default -> 0xD22A22;
        };
    }

    // ---------------------------------------------------------------- Dragon Ball

    private static WorldSites.Site at(ServerLevel level, String kind, BlockPos pos, double r) {
        return WorldSites.near(level, kind, pos, r);
    }

    private static String key(WorldSites.Site site) {
        return site.kind() + "@" + site.x() + "," + site.z();
    }

    private static Here dragonBall(ServerPlayer player, ServerLevel level, BlockPos pos) {
        var tag = player.getPersistentData();
        WorldSites.Site s;
        if ((s = at(level, WorldSites.CAPSULE, pos, 40)) != null) {
            return new Here(key(s), DbActivities.dailyFree(player, DbActivities.CAPSULE_DAY) ? "est_capsula" : "est_capsula_feito",
                    DbActivities.count(player, net.minecraft.world.item.Items.IRON_INGOT), DbActivities.CAPSULE_IRON, 0x8FD3FF);
        }
        if ((s = at(level, WorldSites.GOKU, pos, 26)) != null) {
            if (Flight.knows(player)) {
                return null;
            }
            return new Here(key(s), Tiers.of(player) > Tiers.HUMANO - 1 ? "est_goku_fraco" : "est_goku", 0, 0, 0xF2A040);
        }
        if ((s = at(level, WorldSites.KARIN, pos, 24)) != null) {
            if (tag.getBoolean(KORIN_DONE)) {
                return new Here(key(s), "est_karin_feito", 0, 0, 0xE6D27A);
            }
            Integer from = CLIMB.get(player.getUUID());
            if (from != null) {
                int top = s.y() + WorldSites.KORIN_FLOOR;
                return new Here(key(s), "est_karin_subindo", Math.max(0, player.getBlockY() - from), Math.max(1, top - from), 0xE6D27A);
            }
            return new Here(key(s), "est_karin", 0, 0, 0xE6D27A);
        }
        if ((s = at(level, WorldSites.CELL_RING, pos, 36)) != null) {
            return new Here(key(s), DbActivities.dailyFree(player, "souls_cell_dia") ? "est_cell" : "est_cell_feito", 0, 0, 0x82BEAA);
        }
        if ((s = at(level, WorldSites.WRECK, pos, 18)) != null) {
            int used = tag.getLong("souls_gravidade_dia") == player.serverLevel().getGameTime() / 24000L ? tag.getInt("souls_gravidade_s") : 0;
            return new Here(key(s), used >= DbActivities.GRAVITY_SECONDS ? "est_nave_feito" : "est_nave", used,
                    DbActivities.GRAVITY_SECONDS, 0x6AC8FF);
        }
        if ((s = at(level, WorldSites.RADITZ, pos, 18)) != null) {
            if (podDone(player, key(s))) {
                return null;
            }
            Pod pod = PODS.get(player.getUUID());
            if (pod != null && pod.key().equals(key(s))) {
                return new Here(key(s), "est_raditz_luta", 3 - aliveCount(level, pod), 3, 0xD04A3A);
            }
            return new Here(key(s), "est_raditz", 0, 0, 0xD04A3A);
        }
        if ((s = at(level, WorldSites.BABIDI, pos, 26)) != null) {
            babidiCrouch(player, s);
            boolean free = DbActivities.dailyFree(player, "souls_babidi_dia");
            String id;
            if (free) {
                id = MajinMark.knows(player) ? "est_babidi" : "est_babidi_marca";
            } else if (MajinMark.knows(player)) {
                id = MajinMark.active(player) ? "est_babidi_soltar" : "est_babidi_aceitar";
            } else {
                id = "est_babidi_feito";
            }
            return new Here(key(s), id, 0, 0, 0xB04AD0);
        }
        if ((s = at(level, WorldSites.BUU, pos, 22)) != null) {
            return new Here(key(s), DbActivities.dailyFree(player, "souls_boo_dia") ? "est_buu" : "est_buu_feito", 0, 0, 0xF29AC8);
        }
        if ((s = at(level, WorldSites.KAME, pos, 26)) != null) {
            String id;
            if (!DbActivities.knowsKamehameha(player)) {
                id = "est_kame";
            } else {
                id = DbActivities.dailyFree(player, "souls_kame_dia") ? "est_kame_leite" : "est_kame_feito";
            }
            return new Here(key(s), id, 0, 0, 0xE8A040);
        }
        return null;
    }

    // ---------------------------------------------------------------- Namek

    private static Here namek(ServerPlayer player) {
        if (near(player, GURU, 18)) {
            if (player.getPersistentData().getBoolean(GURU_DONE)) {
                return new Here("namek/guru", "est_guru_feito", 0, 0, 0x7FD67A);
            }
            if (near(player, GURU, 8)) {
                guru(player);
                return new Here("namek/guru", "est_guru_feito", 0, 0, 0x7FD67A);
            }
            return new Here("namek/guru", "est_guru", 0, 0, 0x7FD67A);
        }
        if (near(player, VILLAGE, 28)) {
            return new Here("namek/vila", DbActivities.dailyFree(player, "souls_namek_dia") ? "est_vila" : "est_vila_feito", 0, 0, 0x7FD67A);
        }
        return null;
    }

    static boolean near(ServerPlayer player, int[] xz, double r) {
        double dx = player.getX() - xz[0], dz = player.getZ() - xz[1];
        return dx * dx + dz * dz < r * r;
    }

    // Guru lays his hand on your head: the hidden power wakes (a big part of a tier's Poder), once.
    private static void guru(ServerPlayer player) {
        player.getPersistentData().putBoolean(GURU_DONE, true);
        ServerLevel level = player.serverLevel();
        Vec3 head = player.position().add(0, 1.9, 0);
        Fx.burst(level, ParticleTypes.END_ROD, head, 80, 0.6, 0.12);
        Fx.burst(level, Fx.dust(0xF2C94C, 2f), player.position().add(0, 1, 0), 90, 0.8, 0.05);
        Fx.sound(level, player.position(), SoundEvents.BEACON_ACTIVATE, 1f, 0.8f);
        DbActivities.title(player, Component.translatable("message.battlegroundsmod.guru_titulo"),
                Component.translatable("message.battlegroundsmod.guru_sub"), 0x7FD67A);
        DbActivities.reward(player, 0.8);
        DbQuests.sync(player);
    }

    // ---------------------------------------------------------------- Korin's tower

    private static void korin(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        if (level.dimension() != Level.OVERWORLD || player.getPersistentData().getBoolean(KORIN_DONE)) {
            CLIMB.remove(player.getUUID());
            return;
        }
        WorldSites.Site s = at(level, WorldSites.KARIN, player.blockPosition(), 30);
        Integer from = CLIMB.get(player.getUUID());
        if (s == null) {
            CLIMB.remove(player.getUUID());
            return;
        }
        boolean flying = Flight.flying(player) || player.getAbilities().flying || player.isFallFlying();
        if (from == null) {
            // the climb starts on the ground at the foot of the tower
            if (!flying && player.onGround() && player.getBlockY() <= s.y() + 8 && near(player, new int[] {s.x(), s.z()}, 24)) {
                CLIMB.put(player.getUUID(), player.getBlockY());
            }
            return;
        }
        if (flying) {
            CLIMB.remove(player.getUUID());
            player.displayClientMessage(Component.translatable("message.battlegroundsmod.korin_sem_voar"), false);
            Fx.sound(level, player.position(), SoundEvents.VILLAGER_NO, 1f, 0.8f);
            return;
        }
        if (player.getBlockY() >= s.y() + WorldSites.KORIN_FLOOR) {
            CLIMB.remove(player.getUUID());
            player.getPersistentData().putBoolean(KORIN_DONE, true);
            DbActivities.title(player, Component.translatable("message.battlegroundsmod.korin_agua"),
                    Component.translatable("message.battlegroundsmod.korin_agua_sub"), 0xE6D27A);
            Fx.burst(level, ParticleTypes.SPLASH, player.position().add(0, 1, 0), 60, 0.5, 0.2);
            DbActivities.reward(player, 0.5, new ItemStack(ModItems.SENZU.get(), 2));
            DbQuests.sync(player);
        }
    }

    // ---------------------------------------------------------------- Raditz's crater

    private static boolean podDone(ServerPlayer player, String key) {
        String done = player.getPersistentData().getString(PODS_DONE);
        return (";" + done + ";").contains(";" + key + ";");
    }

    private static int aliveCount(ServerLevel level, Pod pod) {
        int n = 0;
        for (UUID id : pod.saibamen()) {
            if (level.getEntity(id) instanceof LivingEntity e && e.isAlive()) {
                n++;
            }
        }
        return n;
    }

    private static void pod(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Pod pod = PODS.get(player.getUUID());
        if (pod != null) {
            boolean away = level.dimension() != Level.OVERWORLD || player.distanceToSqr(Vec3.atCenterOf(pod.center())) > 48 * 48;
            if (away || !player.isAlive()) {
                for (UUID id : pod.saibamen()) {
                    Entity e = level.getEntity(id);
                    if (e != null) {
                        e.discard();
                    }
                }
                PODS.remove(player.getUUID());
                player.displayClientMessage(Component.translatable("message.battlegroundsmod.ondas_fugiu"), true);
                return;
            }
            for (UUID id : pod.saibamen()) {
                if (level.getEntity(id) instanceof Mob mob && mob.isAlive() && mob.getTarget() != player) {
                    mob.setTarget(player);
                }
            }
            if (aliveCount(level, pod) == 0) {
                PODS.remove(player.getUUID());
                String done = player.getPersistentData().getString(PODS_DONE);
                player.getPersistentData().putString(PODS_DONE, done.isEmpty() ? pod.key() : done + ";" + pod.key());
                DbActivities.title(player, Component.translatable("message.battlegroundsmod.raditz_livre"),
                        Component.translatable("message.battlegroundsmod.raditz_livre_sub"), 0xD04A3A);
                DbActivities.reward(player, 0.3, new ItemStack(ModItems.SENZU.get()));
                DbQuests.sync(player);
            }
            return;
        }
        if (level.dimension() != Level.OVERWORLD) {
            return;
        }
        WorldSites.Site s = at(level, WorldSites.RADITZ, player.blockPosition(), 8);
        if (s == null || podDone(player, key(s))) {
            return;
        }
        // the scouter in the pod beeps: three Saibamen come up out of the crater's ground around the player
        List<UUID> ids = new ArrayList<>();
        Vec3 look = player.getLookAngle();
        double base = Math.atan2(look.z, look.x);
        for (int i = 0; i < 3; i++) {
            double a = base + (i - 1) * 2.1;
            BlockPos spot = DbActivities.dryGround(level, (int) Math.floor(player.getX() + Math.cos(a) * 7),
                    (int) Math.floor(player.getZ() + Math.sin(a) * 7), 4);
            if (spot == null) {
                spot = player.blockPosition();
            }
            Entity e = ModEntities.SAIBAMAN.get().spawn(level, spot, MobSpawnType.EVENT);
            if (e instanceof Mob mob) {
                mob.setTarget(player);
                mob.setPersistenceRequired();
                ids.add(e.getUUID());
                Fx.burst(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, Vec3.atBottomCenterOf(spot), 14, 0.5, 0.03);
                Fx.burst(level, Fx.dust(0x3A6A2A, 1.6f), Vec3.atBottomCenterOf(spot), 30, 0.6, 0.08);
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        PODS.put(player.getUUID(), new Pod(key(s), s.pos(), ids));
        DbActivities.title(player, Component.translatable("message.battlegroundsmod.raditz_sinal"),
                Component.translatable("message.battlegroundsmod.raditz_sinal_sub"), 0xD04A3A);
        Fx.sound(level, player.position(), SoundEvents.NOTE_BLOCK_BIT.value(), 1f, 1.8f);
        DbCommon.boom(level, Vec3.atBottomCenterOf(s.pos()), 1.2f, Db.GOLD, level.getRandom().nextInt(100));
    }

    // The pod's chest (and anything to open in the crater) stays shut until the Saibamen are beaten.
    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().dimension() != Level.OVERWORLD
                || !Progression.on(player) || player.isCreative()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (!(level.getBlockEntity(event.getPos()) instanceof BaseContainerBlockEntity)) {
            return;
        }
        WorldSites.Site s = at(level, WorldSites.RADITZ, event.getPos(), 7);
        if (s == null || podDone(player, key(s))) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        player.displayClientMessage(Component.translatable("message.battlegroundsmod.raditz_trancado"), true);
    }

    // ---------------------------------------------------------------- dying keeps what was done

    // The player's persistent data is lost on respawn: what the structures remember (done once, used today) is carried
    // over, so dying neither resets a daily reward nor gives a once-only one again.
    private static final List<String> KEEP = List.of(PODS_DONE, KORIN_DONE, GURU_DONE, "souls_majin_solta", DbActivities.CAPSULE_DAY,
            "souls_boo_dia", "souls_cell_dia", "souls_babidi_dia", "souls_namek_dia", "souls_kame_dia", "souls_kame_feito",
            "souls_gravidade_dia", "souls_gravidade_s");

    private static void onClone(net.neoforged.neoforge.event.entity.player.PlayerEvent.Clone event) {
        var from = event.getOriginal().getPersistentData();
        var to = event.getEntity().getPersistentData();
        for (String key : KEEP) {
            if (from.contains(key)) {
                to.put(key, from.get(key).copy());
            }
        }
    }

    // ---------------------------------------------------------------- Babidi's ship: the mark

    private static void babidiCrouch(ServerPlayer player, WorldSites.Site s) {
        if (!MajinMark.knows(player) || DbActivities.fighting(player)) {
            CROUCH.remove(player.getUUID());
            return;
        }
        if (!player.isCrouching()) {
            CROUCH.remove(player.getUUID());
            return;
        }
        int n = CROUCH.merge(player.getUUID(), 1, Integer::sum);
        if (n == 3) {
            // one flip per crouch: staying down doesn't flip it back (stand up and crouch again for that)
            CROUCH.put(player.getUUID(), Integer.MIN_VALUE / 2);
            MajinMark.toggle(player);
        }
    }
}
