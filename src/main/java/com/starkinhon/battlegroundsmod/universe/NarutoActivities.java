package com.starkinhon.battlegroundsmod.universe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.ModBlocks;
import com.starkinhon.battlegroundsmod.ModItems;
import com.starkinhon.battlegroundsmod.command.SoulCommand;
import com.starkinhon.battlegroundsmod.item.NarutoLootItems;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.world.NarutoSites;
import com.starkinhon.battlegroundsmod.world.NarutoStructGen;
import com.starkinhon.battlegroundsmod.world.PaperSealBlock;

import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// What to do at each Naruto structure (Stark 2026-11-12: "cada uma com a sua atividade", no mission NPCs, no story):
// - Training Ground 3 (Folha): the bell test. Take a bell from the middle stump and hold it 60 s inside the ground while
//   Renegade Ninjas and Ninken come for it (a hit can knock it out of your hands); both bells = double prize and danger.
// - Chunin Exam tower (Morte): the Sound Ninjas carry the Heaven or the Earth scroll; open both at the hall's altar and
//   the wall of Heaven and Earth comes down over the sealed chamber (a last wave tries to rob you). Closes again a day later.
// - Snake's Den (Morte): the giant snake sleeps in the nest under the roots; walk crouched or it wakes (3 days to come back).
// - Sealed Hideout (Wind Desert): tear the five seals off the rocks around within 60 s (each one lets out a shadow copy
//   of you) and the boulder over the cave breaks; inside, the puppeteer's workshop wakes. Sealed again after 3 days.
// - Swordsman's Grave (Mist Sea): pull the sword out and the mist closes; three waves of Mist swordsmen (a Mist hunter
//   in the third); beat them and the Kubikiribōchō is yours. Once per grave.
// - Angel's Tower (Rain): take the paper flower from the head's tongue and bring it down and away from the tower in 90 s
//   while the paper cranes come down at you. The poisonous salamander lives in the sewer pit, with gas at the bottom.
// - Valley of the End: at sunset touch a seal on a statue's head: Senju (wood clones from the lake, Hashirama's cell) or
//   Uchiha (crows and an Uchiha shadow, maybe the Sharingan); the other seal is out for the day. White Zetsu at night.
// - Sages' Spring (Myoboku): sit (crouched and still) on a meditation stone in the oil; stand up with the natural energy
//   bar in the green and the Sage Mode is yours for 3 min; past it, you turn to a stone toad for 10 s.
// - Sage's Peak (Myoboku): climb the needle; the giant warrior toad fights you at the top and, beaten, gives its scroll.
// Mobs another thread is making come in by their ids as soon as they exist; until then the nearest that exists stands in.
public final class NarutoActivities {
    private static final String RUN_TAG = "souls_naruto_run";
    private static final String ALLY_TAG = "souls_naruto_aliado";

    private NarutoActivities() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(NarutoActivities::onRightClick);
        NeoForge.EVENT_BUS.addListener(NarutoActivities::onBreak);
        NeoForge.EVENT_BUS.addListener(NarutoActivities::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(NarutoActivities::onServerTick);
        NeoForge.EVENT_BUS.addListener(NarutoActivities::onDeath);
        NeoForge.EVENT_BUS.addListener(NarutoActivities::onIncoming);
        NeoForge.EVENT_BUS.addListener(NarutoActivities::onDamage);
        // /soul estrutura <tipo>: to the nearest place of a kind (testing; ops only)
        SoulCommand.addSubcommand(() -> Commands.literal("estrutura").requires(s -> s.hasPermission(2))
                .then(Commands.argument("tipo", StringArgumentType.word()).executes(c -> {
                    ServerPlayer player = c.getSource().getPlayerOrException();
                    String kind = StringArgumentType.getString(c, "tipo");
                    BlockPos at = NarutoSites.locate(player.serverLevel(), kind, player.blockPosition(), 14);
                    if (at == null) {
                        c.getSource().sendFailure(Component.translatable("message.battlegroundsmod.naruto.nenhuma", kind));
                        return 0;
                    }
                    int y = player.serverLevel().getHeight(Heightmap.Types.MOTION_BLOCKING, at.getX(), at.getZ() + 40) + 30;
                    player.teleportTo(at.getX() + 0.5, Math.max(y, 140), at.getZ() + 40.5);
                    c.getSource().sendSuccess(() -> Component.translatable("message.battlegroundsmod.naruto.indo", kind, at.getX(), at.getZ()), false);
                    return 1;
                }).then(Commands.argument("marca", StringArgumentType.word()).executes(c -> {
                    // /soul estrutura <tipo> <marca>: onto a mark of the built place nearby (center, bell, altar, nest...)
                    ServerPlayer player = c.getSource().getPlayerOrException();
                    String kind = StringArgumentType.getString(c, "tipo");
                    String mark = StringArgumentType.getString(c, "marca");
                    NarutoSites.Site site = NarutoSites.near(player.serverLevel(), kind, player.blockPosition(), 400);
                    BlockPos at = site == null ? null : site.first(mark);
                    if (at == null) {
                        c.getSource().sendFailure(Component.translatable("message.battlegroundsmod.naruto.nenhuma", kind + " " + mark));
                        return 0;
                    }
                    // the nearest spot with two free blocks over a floor, beside the mark first (a mark can be a block
                    // in a wall or under a roof; climbing straight up went out through the roofs)
                    ServerLevel sl = player.serverLevel();
                    BlockPos feet = null;
                    for (int r = 0; r <= 4 && feet == null; r++) {
                        for (int dy = -1; dy <= 3 && feet == null; dy++) {
                            for (int dx = -r; dx <= r && feet == null; dx++) {
                                for (int dz = -r; dz <= r && feet == null; dz++) {
                                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                                        continue;
                                    }
                                    BlockPos p = at.offset(dx, dy, dz);
                                    if (sl.getBlockState(p).getCollisionShape(sl, p).isEmpty()
                                            && sl.getBlockState(p.above()).getCollisionShape(sl, p.above()).isEmpty()
                                            && !sl.getBlockState(p.below()).getCollisionShape(sl, p.below()).isEmpty()) {
                                        feet = p;
                                    }
                                }
                            }
                        }
                    }
                    if (feet == null) {
                        feet = at.above();
                    }
                    player.teleportTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
                    c.getSource().sendSuccess(() -> Component.translatable("message.battlegroundsmod.naruto.indo", kind + " " + mark, at.getX(), at.getZ()), false);
                    return 1;
                }))));
    }

    // ================================================================ helpers

    private static long day(Level level) {
        return level.getDayTime() / 24000L;
    }

    private static boolean dailyFree(ServerPlayer player, String tag) {
        return player.getPersistentData().getLong(tag) != day(player.level()) + 1;
    }

    private static void dailyUse(ServerPlayer player, String tag) {
        // stored as day + 1 so a fresh player (0) is free on day 0
        player.getPersistentData().putLong(tag, day(player.level()) + 1);
    }

    private static MutableComponent msg(String key, Object... args) {
        return Component.translatable("message.battlegroundsmod.naruto." + key, args);
    }

    private static void say(ServerPlayer player, String key, Object... args) {
        player.displayClientMessage(msg(key, args), true);
    }

    private static void title(ServerPlayer player, String key, String sub, int color) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(6, 50, 14));
        player.connection.send(new ClientboundSetTitleTextPacket(msg(key).withStyle(s -> s.withColor(color).withBold(true))));
        player.connection.send(new ClientboundSetSubtitleTextPacket(sub == null ? Component.empty() : msg(sub)));
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static EntityType<?> type(String id) {
        return BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, id)).orElse(null);
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, id)).orElse(null);
    }

    private static String typeId(Entity e) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
    }

    private static boolean near(Entity e, BlockPos p, double r) {
        return p != null && e.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5) < r * r;
    }

    private static boolean nearFlat(Entity e, BlockPos p, double r) {
        if (p == null) {
            return false;
        }
        double dx = e.getX() - p.getX() - 0.5, dz = e.getZ() - p.getZ() - 0.5;
        return dx * dx + dz * dz < r * r;
    }

    private static boolean anyPlayerNear(ServerLevel level, BlockPos p, double r) {
        for (ServerPlayer player : level.players()) {
            if (nearFlat(player, p, r)) {
                return true;
            }
        }
        return false;
    }

    // A free spot for a mob near a point: the first two air blocks at or above it, a few blocks around.
    private static BlockPos spotNear(ServerLevel level, BlockPos center, double minR, double maxR) {
        for (int tries = 0; tries < 12; tries++) {
            double a = level.random.nextDouble() * Math.PI * 2, d = minR + level.random.nextDouble() * (maxR - minR);
            int x = center.getX() + (int) Math.round(Math.cos(a) * d), z = center.getZ() + (int) Math.round(Math.sin(a) * d);
            for (int y = center.getY() - 3; y <= center.getY() + 8; y++) {
                BlockPos p = new BlockPos(x, y, z);
                if (level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir() && !level.getBlockState(p.below()).isAir()) {
                    return p;
                }
            }
        }
        return center;
    }

    // A mob by its id, or the stand-in while it doesn't exist yet; set on the player.
    private static Entity spawn(ServerLevel level, BlockPos at, ServerPlayer target, String... ids) {
        for (String id : ids) {
            EntityType<?> t = id.startsWith("minecraft:") ? BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(id)) : type(id);
            if (t == null) {
                continue;
            }
            Entity e = t.spawn(level, at, MobSpawnType.STRUCTURE);
            if (e == null) {
                continue;
            }
            e.addTag(RUN_TAG);
            if (e instanceof Mob mob && target != null) {
                mob.setTarget(target);
            }
            if (e instanceof Wolf wolf && target != null) {
                wolf.setPersistentAngerTarget(target.getUUID());
                wolf.startPersistentAngerTimer();
            }
            if (e instanceof Vex vex) {
                vex.setLimitedLife(20 * 90);
            }
            level.sendParticles(ParticleTypes.CLOUD, e.getX(), e.getY(0.5), e.getZ(), 12, 0.4, 0.5, 0.4, 0.02);
            return e;
        }
        return null;
    }

    // A shadow copy of the player (their armour and weapon), short-lived.
    private static Entity shadow(ServerLevel level, BlockPos at, ServerPlayer player, String nameKey) {
        Husk husk = EntityType.HUSK.spawn(level, at, MobSpawnType.EVENT);
        if (husk == null) {
            return null;
        }
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
                EquipmentSlot.MAINHAND}) {
            ItemStack copy = player.getItemBySlot(slot).copy();
            if (slot == EquipmentSlot.MAINHAND && copy.isEmpty()) {
                copy = new ItemStack(net.minecraft.world.item.Items.IRON_SWORD);
            }
            husk.setItemSlot(slot, copy);
            husk.setDropChance(slot, 0);
        }
        husk.setCustomName(msg(nameKey, player.getName()));
        husk.addTag(RUN_TAG);
        husk.setTarget(player);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, husk.getX(), husk.getY(0.5), husk.getZ(), 20, 0.3, 0.6, 0.3, 0.02);
        return husk;
    }

    private static void fill(ServerLevel level, BlockPos pos, ItemStack... stacks) {
        if (pos == null) {
            return;
        }
        if (!level.getBlockState(pos).is(Blocks.CHEST)) {
            level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 3);
        }
        if (level.getBlockEntity(pos) instanceof Container chest) {
            int slot = 4;
            for (ItemStack s : stacks) {
                if (!s.isEmpty() && slot < chest.getContainerSize()) {
                    chest.setItem(slot, s);
                    slot += 5;
                    slot %= chest.getContainerSize();
                }
            }
        }
    }

    // A paper seal back on its spot, stuck on the solid side next to it.
    private static void putSeal(ServerLevel level, BlockPos p) {
        Direction facing = Direction.UP;
        for (Direction d : new Direction[] {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
            if (level.getBlockState(p.relative(d.getOpposite())).isSolid()) {
                facing = d;
                break;
            }
        }
        level.setBlock(p, ModBlocks.PAPER_SEAL.get().defaultBlockState().setValue(PaperSealBlock.FACING, facing), 3);
    }

    private static void setAll(ServerLevel level, List<BlockPos> list, BlockState state, boolean burst) {
        for (BlockPos p : list) {
            level.setBlock(p, state, 3);
            if (burst) {
                level.sendParticles(ParticleTypes.POOF, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 2, 0.3, 0.3, 0.3, 0.02);
            }
        }
    }

    // ================================================================ per-place state (saved)

    private static CompoundTag state(ServerLevel level, NarutoSites.Site site) {
        Data data = data(level);
        data.setDirty();
        return data.sites.computeIfAbsent(site.id(), k -> new CompoundTag());
    }

    private static Data data(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null),
                "battlegroundsmod_naruto_atividades");
    }

    public static final class Data extends SavedData {
        final Map<String, CompoundTag> sites = new HashMap<>();

        static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data data = new Data();
            for (String key : tag.getAllKeys()) {
                data.sites.put(key, tag.getCompound(key));
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            sites.forEach(tag::put);
            return tag;
        }
    }

    // A place just finished building (world/NarutoSites).
    public static void built(ServerLevel level, NarutoSites.Site site) {
        if (NarutoStructGen.COVIL.equals(site.kind())) {
            BlockPos nest = site.first("nest");
            if (nest != null) {
                ItemStack[] loot = level.random.nextFloat() < 0.2f
                        ? new ItemStack[] {new ItemStack(ModItems.SNAKE_SKIN.get()), new ItemStack(ModItems.KUSANAGI.get())}
                        : new ItemStack[] {new ItemStack(ModItems.SNAKE_SKIN.get())};
                fill(level, nest.above(), loot);
                com.starkinhon.battlegroundsmod.world.StructureLoot.give(level, nest.above(), NarutoStructGen.COVIL);
            }
        }
    }

    // ================================================================ a challenge in progress (one per player)

    private static final class Run {
        final String type;
        final String site;
        final BlockPos center;
        final long end;
        final List<UUID> mobs = new ArrayList<>();
        int wave = 1;
        int bells = 1;
        int noBell;
        String side = "";

        Run(String type, String site, BlockPos center, long end) {
            this.type = type;
            this.site = site;
            this.center = center;
            this.end = end;
        }

        void add(Entity e) {
            if (e != null) {
                mobs.add(e.getUUID());
            }
        }

        boolean cleared(ServerLevel level) {
            for (UUID id : mobs) {
                Entity e = level.getEntity(id);
                if (e != null && e.isAlive()) {
                    return false;
                }
            }
            return true;
        }

        void despawn(ServerLevel level) {
            for (UUID id : mobs) {
                Entity e = level.getEntity(id);
                if (e != null && e.isAlive()) {
                    level.sendParticles(ParticleTypes.CLOUD, e.getX(), e.getY(0.5), e.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
                    e.discard();
                }
            }
            mobs.clear();
        }
    }

    private static final Map<UUID, Run> RUNS = new HashMap<>();

    // ================================================================ right click: bells, altar, sword, flower, seals

    private static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getEntity() instanceof ServerPlayer player)
                || player.level().dimension() != Level.OVERWORLD || player.isSpectator()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos pos = event.getPos();
        NarutoSites.Site site = NarutoSites.near(level, null, pos, 110);
        if (site == null) {
            return;
        }
        boolean handled = switch (site.kind()) {
            case NarutoStructGen.FOLHA -> bell(player, level, site, pos);
            case NarutoStructGen.MORTE -> altar(player, level, site, pos);
            case NarutoStructGen.NEVOA -> sword(player, level, site, pos);
            case NarutoStructGen.CHUVA -> flower(player, level, site, pos);
            case NarutoSites.VALE -> valeSeal(player, level, site, pos);
            default -> false;
        };
        if (handled) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    // ---------------------------------------------------------------- Folha: the bell test

    private static boolean bell(ServerPlayer player, ServerLevel level, NarutoSites.Site site, BlockPos pos) {
        if (!site.mark("bell").contains(pos) || !level.getBlockState(pos).is(Blocks.BELL)) {
            return false;
        }
        Run run = RUNS.get(player.getUUID());
        if (run != null && "guizo".equals(run.type) && run.site.equals(site.id())) {
            if (run.bells < 2) {
                run.bells = 2;
                takeBell(player, level, pos);
                for (int i = 0; i < 2; i++) {
                    run.add(spawn(level, spotNear(level, run.center, 14, 20), player, "ninja_renegado"));
                }
                run.add(spawn(level, spotNear(level, run.center, 14, 20), player, "ninken", "minecraft:wolf"));
                say(player, "guizo_dois");
            }
            return true;
        }
        if (run != null) {
            say(player, "ja_em_desafio");
            return true;
        }
        if (!dailyFree(player, "souls_guizo_dia")) {
            say(player, "guizo_amanha");
            return true;
        }
        BlockPos center = site.first("center");
        Run r = new Run("guizo", site.id(), center == null ? pos : center, level.getGameTime() + 1200);
        RUNS.put(player.getUUID(), r);
        takeBell(player, level, pos);
        for (int i = 0; i < 3; i++) {
            r.add(spawn(level, spotNear(level, r.center, 14, 22), player, "ninja_renegado"));
        }
        for (int i = 0; i < 2; i++) {
            r.add(spawn(level, spotNear(level, r.center, 16, 24), player, "ninken", "minecraft:wolf"));
        }
        title(player, "guizo_titulo", "guizo_sub", 0xE8B93A);
        level.playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1f, 1.4f);
        return true;
    }

    private static void takeBell(ServerPlayer player, ServerLevel level, BlockPos pos) {
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        ItemStack bell = new ItemStack(ModItems.GUIZO.get());
        CustomData.update(DataComponents.CUSTOM_DATA, bell, tag -> tag.putBoolean("souls_teste", true));
        give(player, bell);
        level.playSound(null, pos, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 1f, 1.6f);
    }

    private static boolean testBell(ItemStack s) {
        return s.is(ModItems.GUIZO.get()) && s.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBoolean("souls_teste");
    }

    private static int testBells(ServerPlayer player) {
        int n = 0;
        for (ItemStack s : player.getInventory().items) {
            if (testBell(s)) {
                n += s.getCount();
            }
        }
        if (testBell(player.getOffhandItem())) {
            n += player.getOffhandItem().getCount();
        }
        return n;
    }

    private static void restoreBells(ServerLevel level, NarutoSites.Site site) {
        for (BlockPos p : site.mark("bell")) {
            if (!level.getBlockState(p).isAir()) {
                continue;
            }
            BlockState bell = Blocks.BELL.defaultBlockState();
            for (Direction d : new Direction[] {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
                if (level.getBlockState(p.relative(d)).isSolid()) {
                    bell = bell.setValue(BellBlock.FACING, d).setValue(BellBlock.ATTACHMENT, BellAttachType.SINGLE_WALL);
                    break;
                }
            }
            level.setBlock(p, bell, 3);
        }
    }

    private static void bellTick(ServerPlayer player, ServerLevel level, Run run, NarutoSites.Site site) {
        long now = level.getGameTime();
        int held = testBells(player);
        run.noBell = held > 0 ? 0 : run.noBell + 1;
        boolean out = !nearFlat(player, run.center, 34);
        if (!player.isAlive() || out || run.noBell >= 20) {
            // lost: the bells go back to the stump
            removeTestBells(player, level, run.center);
            restoreBells(level, site);
            run.despawn(level);
            RUNS.remove(player.getUUID());
            say(player, out ? "guizo_saiu" : "guizo_perdeu");
            return;
        }
        int left = (int) Math.max(0, (run.end - now) / 20);
        player.displayClientMessage(msg(held > 0 ? "guizo_tempo" : "guizo_sem", left), true);
        if (now < run.end) {
            return;
        }
        // held to the end: the bells stay as trophies
        for (ItemStack s : player.getInventory().items) {
            if (testBell(s)) {
                CustomData.update(DataComponents.CUSTOM_DATA, s, tag -> tag.remove("souls_teste"));
            }
        }
        boolean both = run.bells >= 2 && held >= 2;
        give(player, new ItemStack(ModItems.SOLDIER_PILL.get(), both ? 4 : 2));
        if (level.random.nextFloat() < (both ? 0.6f : 0.3f)) {
            give(player, new ItemStack(ModItems.NINJA_HEADBAND.get()));
        }
        if (both && level.random.nextFloat() < 0.5f) {
            give(player, new ItemStack(ModItems.SUMMON_SCROLL.get()));
        }
        restoreBells(level, site);
        run.despawn(level);
        RUNS.remove(player.getUUID());
        dailyUse(player, "souls_guizo_dia");
        title(player, "guizo_passou", both ? "guizo_passou_dois" : null, 0xE8B93A);
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1f, 1.2f);
    }

    private static void removeTestBells(ServerPlayer player, ServerLevel level, BlockPos center) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (testBell(player.getInventory().getItem(i))) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(center).inflate(48), e -> testBell(e.getItem()))) {
            e.discard();
        }
    }

    // ---------------------------------------------------------------- Morte: the scrolls of Heaven and Earth

    private static boolean altar(ServerPlayer player, ServerLevel level, NarutoSites.Site site, BlockPos pos) {
        if (!site.mark("altar").contains(pos)) {
            return false;
        }
        CompoundTag st = state(level, site);
        if (st.getBoolean("aberta")) {
            say(player, "camara_aberta");
            return true;
        }
        int heaven = player.getInventory().findSlotMatchingItem(new ItemStack(ModItems.SCROLL_HEAVEN.get()));
        int earth = player.getInventory().findSlotMatchingItem(new ItemStack(ModItems.SCROLL_EARTH.get()));
        if (heaven < 0 || earth < 0) {
            say(player, heaven < 0 && earth < 0 ? "altar_vazio" : heaven < 0 ? "falta_ceu" : "falta_terra");
            return true;
        }
        if (!player.getAbilities().instabuild) {
            player.getInventory().removeItem(heaven, 1);
            player.getInventory().removeItem(player.getInventory().findSlotMatchingItem(new ItemStack(ModItems.SCROLL_EARTH.get())), 1);
        }
        setAll(level, site.mark("wall"), Blocks.AIR.defaultBlockState(), true);
        setAll(level, site.mark("glyph"), Blocks.AIR.defaultBlockState(), false);
        List<BlockPos> chests = site.mark("chest");
        for (int i = 0; i < chests.size(); i++) {
            List<ItemStack> loot = new ArrayList<>();
            loot.add(new ItemStack(ModItems.SOLDIER_PILL.get(), 2));
            if (level.random.nextFloat() < (i == 0 ? 0.35f : 0.15f)) {
                loot.add(new ItemStack(ModItems.HIRAISHIN_KUNAI.get()));
            }
            if (i == 1) {
                loot.add(new ItemStack(ModItems.NINJA_HEADBAND.get()));
            }
            fill(level, chests.get(i), loot.toArray(ItemStack[]::new));
        }
        // the summoning smoke and the last wave of Sound Ninjas, to rob you
        BlockPos center = site.first("center");
        level.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 120, 2.5, 1.5, 2.5, 0.05);
        Run run = RUNS.computeIfAbsent(player.getUUID(), k -> new Run("altar", site.id(), center == null ? pos : center, level.getGameTime() + 2400));
        for (int i = 0; i < 4; i++) {
            run.add(spawn(level, spotNear(level, center == null ? pos : center, 6, 10), player, "ninja_som", "ninja_renegado"));
        }
        st.putBoolean("aberta", true);
        st.putLong("aberta_dia", day(level));
        title(player, "ceu_terra", "ceu_terra_sub", 0xD6463A);
        level.playSound(null, pos, SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.BLOCKS, 1.2f, 0.8f);
        return true;
    }

    // ---------------------------------------------------------------- Mist: the sword in the grave

    private static boolean sword(ServerPlayer player, ServerLevel level, NarutoSites.Site site, BlockPos pos) {
        if (!site.mark("blade").contains(pos) && !site.mark("spine").contains(pos) && !site.mark("wrap").contains(pos)) {
            return false;
        }
        CompoundTag st = state(level, site);
        if (st.getBoolean("tirada") || RUNS.containsKey(player.getUUID())) {
            return true;
        }
        setAll(level, site.mark("blade"), Blocks.AIR.defaultBlockState(), true);
        setAll(level, site.mark("spine"), Blocks.AIR.defaultBlockState(), false);
        setAll(level, site.mark("wrap"), Blocks.AIR.defaultBlockState(), false);
        st.putBoolean("puxando", true);
        BlockPos grave = site.first("grave");
        Run run = new Run("espada", site.id(), grave == null ? pos : grave, Long.MAX_VALUE);
        RUNS.put(player.getUUID(), run);
        for (int i = 0; i < 2; i++) {
            run.add(spawn(level, spotNear(level, run.center, 6, 10), player, "espadachim_nevoa", "ninja_renegado"));
        }
        title(player, "espada_titulo", "espada_sub", 0xB9C8D6);
        level.playSound(null, pos, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 1f, 0.5f);
        return true;
    }

    private static void restoreSword(ServerLevel level, NarutoSites.Site site) {
        setAll(level, site.mark("blade"), Blocks.IRON_BLOCK.defaultBlockState(), false);
        setAll(level, site.mark("spine"), ModBlocks.RAIN_METAL.get().defaultBlockState(), false);
        setAll(level, site.mark("wrap"), Blocks.BLACK_WOOL.defaultBlockState(), false);
    }

    private static void swordTick(ServerPlayer player, ServerLevel level, Run run, NarutoSites.Site site) {
        if (!player.isAlive() || !nearFlat(player, run.center, 64)) {
            run.despawn(level);
            RUNS.remove(player.getUUID());
            restoreSword(level, site);
            state(level, site).remove("puxando");
            say(player, "espada_falhou");
            return;
        }
        // the mist closes: you see a few blocks around
        for (ServerPlayer p : level.players()) {
            if (nearFlat(p, run.center, 40)) {
                p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 50, 0, false, false));
            }
        }
        level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 1, player.getZ(), 16, 6, 1.5, 6, 0.01);
        if (!run.cleared(level)) {
            return;
        }
        run.mobs.clear();
        if (run.wave < 3) {
            run.wave++;
            int n = run.wave == 2 ? 3 : 2;
            for (int i = 0; i < n; i++) {
                run.add(spawn(level, spotNear(level, run.center, 6, 11), player, "espadachim_nevoa", "ninja_renegado"));
            }
            if (run.wave == 3) {
                BlockPos hunter = site.first("hunter");
                run.add(spawn(level, spotNear(level, hunter == null ? run.center : hunter, 1, 3), player, "cacador_nevoa", "ninja_renegado"));
            }
            say(player, "espada_onda", run.wave);
            return;
        }
        RUNS.remove(player.getUUID());
        CompoundTag st = state(level, site);
        st.remove("puxando");
        st.putBoolean("tirada", true);
        give(player, new ItemStack(ModItems.KUBIKIRIBOCHO.get()));
        player.removeEffect(MobEffects.BLINDNESS);
        title(player, "espada_tua", null, 0xB9C8D6);
        level.playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
    }

    // ---------------------------------------------------------------- Rain: the paper flower

    private static boolean flower(ServerPlayer player, ServerLevel level, NarutoSites.Site site, BlockPos pos) {
        BlockPos fl = site.first("flower");
        if (fl == null || pos.distManhattan(fl) > 2 || !level.getBlockState(fl).is(ModBlocks.PAPER_SEAL.get())) {
            return false;
        }
        if (RUNS.containsKey(player.getUUID())) {
            say(player, "ja_em_desafio");
            return true;
        }
        if (!dailyFree(player, "souls_flor_dia")) {
            say(player, "flor_amanha");
            return true;
        }
        level.setBlock(fl, Blocks.AIR.defaultBlockState(), 3);
        BlockPos tower = site.first("tower");
        Run run = new Run("flor", site.id(), tower == null ? fl : tower, level.getGameTime() + 1800);
        RUNS.put(player.getUUID(), run);
        for (int i = 0; i < 6; i++) {
            BlockPos at = fl.offset(level.random.nextInt(9) - 4, 2 + level.random.nextInt(5), level.random.nextInt(9) - 4);
            run.add(spawn(level, at, player, "garca_papel", "minecraft:vex"));
        }
        title(player, "flor_titulo", "flor_sub", 0x9FB6C8);
        level.playSound(null, fl, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1.5f, 0.6f);
        return true;
    }

    private static void flowerTick(ServerPlayer player, ServerLevel level, Run run, NarutoSites.Site site) {
        long now = level.getGameTime();
        BlockPos fl = site.first("flower");
        boolean away = !nearFlat(player, run.center, 26) && player.getY() <= run.center.getY() + 6;
        if (away) {
            RUNS.remove(player.getUUID());
            run.despawn(level);
            if (fl != null) {
                putSeal(level, fl);
            }
            dailyUse(player, "souls_flor_dia");
            give(player, new ItemStack(ModItems.PAPER_WINGS.get()));
            title(player, "flor_passou", null, 0x9FB6C8);
            level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1f, 1.1f);
            return;
        }
        if (!player.isAlive() || now >= run.end) {
            RUNS.remove(player.getUUID());
            run.despawn(level);
            if (fl != null) {
                putSeal(level, fl);
            }
            say(player, "flor_falhou");
            return;
        }
        player.displayClientMessage(msg("flor_tempo", (run.end - now) / 20), true);
    }

    // ---------------------------------------------------------------- Valley of the End: choose a side

    private static boolean valeSeal(ServerPlayer player, ServerLevel level, NarutoSites.Site site, BlockPos pos) {
        String side = null;
        BlockPos mark = null;
        for (String s : new String[] {"senju", "uchiha"}) {
            for (BlockPos m : site.mark(s)) {
                if (Math.abs(m.getX() - pos.getX()) <= 3 && Math.abs(m.getZ() - pos.getZ()) <= 3 && pos.getY() >= m.getY() - 2 && pos.getY() <= m.getY() + 6) {
                    side = s;
                    mark = m;
                }
            }
        }
        if (side == null) {
            return false;
        }
        long t = level.getDayTime() % 24000L;
        if (t < 11000 || t > 14000) {
            say(player, "selo_por_do_sol");
            return true;
        }
        CompoundTag st = state(level, site);
        if (st.getLong("lado_dia") == day(level) + 1 && !side.equals(st.getString("lado"))) {
            say(player, "selo_apagado");
            return true;
        }
        if (RUNS.containsKey(player.getUUID())) {
            say(player, "ja_em_desafio");
            return true;
        }
        if (!dailyFree(player, "souls_vale_dia")) {
            say(player, "vale_amanha");
            return true;
        }
        st.putString("lado", side);
        st.putLong("lado_dia", day(level) + 1);
        Run run = new Run("vale", site.id(), mark, level.getGameTime() + 20 * 240);
        run.side = side;
        RUNS.put(player.getUUID(), run);
        if ("senju".equals(side)) {
            BlockPos lake = new BlockPos(site.x(), level.getHeight(Heightmap.Types.MOTION_BLOCKING, site.x(), site.z()), site.z());
            for (int i = 0; i < 2; i++) {
                Entity e = spawn(level, spotNear(level, lake, 2, 8), player, "clone_madeira");
                if (e == null) {
                    e = spawn(level, spotNear(level, player.blockPosition(), 4, 8), player, "ninja_renegado");
                }
                run.add(e);
            }
            title(player, "senju_titulo", "senju_sub", 0x7FD67A);
        } else {
            for (int i = 0; i < 6; i++) {
                run.add(spawn(level, player.blockPosition().offset(level.random.nextInt(9) - 4, 3, level.random.nextInt(9) - 4), player,
                        "corvo_uchiha", "minecraft:vex"));
            }
            run.add(shadow(level, spotNear(level, player.blockPosition(), 3, 6), player, "sombra_uchiha"));
            title(player, "uchiha_titulo", "uchiha_sub", 0xD22A22);
        }
        level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1f, 0.6f);
        return true;
    }

    private static void valeTick(ServerPlayer player, ServerLevel level, Run run) {
        if (!player.isAlive() || !nearFlat(player, run.center, 96) || level.getGameTime() > run.end) {
            run.despawn(level);
            RUNS.remove(player.getUUID());
            say(player, "vale_falhou");
            return;
        }
        if (!run.cleared(level)) {
            return;
        }
        RUNS.remove(player.getUUID());
        dailyUse(player, "souls_vale_dia");
        if ("senju".equals(run.side)) {
            give(player, new ItemStack(ModItems.HASHIRAMA_CELL.get()));
            title(player, "senju_venceu", null, 0x7FD67A);
        } else if (level.random.nextFloat() < 0.4f) {
            give(player, new ItemStack(ModItems.SHARINGAN.get()));
            title(player, "uchiha_sharingan", null, 0xD22A22);
        } else {
            give(player, new ItemStack(ModItems.SOLDIER_PILL.get(), 2));
            title(player, "uchiha_venceu", null, 0xD22A22);
        }
    }

    // ================================================================ breaking: the five seals, the sealed things

    private static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)
                || level.dimension() != Level.OVERWORLD) {
            return;
        }
        BlockPos pos = event.getPos();
        NarutoSites.Site site = NarutoSites.near(level, null, pos, 110);
        if (site == null) {
            return;
        }
        CompoundTag st = state(level, site);
        boolean creative = player.getAbilities().instabuild;
        switch (site.kind()) {
            case NarutoStructGen.DESERTO -> {
                if (site.mark("seal").contains(pos) && event.getState().is(ModBlocks.PAPER_SEAL.get()) && !st.getBoolean("aberta")) {
                    seal(player, level, site, st, pos);
                } else if (!creative && !st.getBoolean("aberta") && (site.mark("boulder").contains(pos) || site.mark("boulder_seal").contains(pos))) {
                    event.setCanceled(true);
                    say(player, "rochedo_selado");
                }
            }
            case NarutoStructGen.MORTE -> {
                if (!creative && !st.getBoolean("aberta") && (site.mark("wall").contains(pos) || site.mark("glyph").contains(pos))) {
                    event.setCanceled(true);
                    say(player, "parede_selada");
                }
            }
            default -> {
            }
        }
    }

    private static void seal(ServerPlayer player, ServerLevel level, NarutoSites.Site site, CompoundTag st, BlockPos pos) {
        long now = level.getGameTime();
        if (now > st.getLong("selo_ate")) {
            st.putLong("selo_ate", now + 1200);
            st.putInt("selos", 0);
        }
        int n = st.getInt("selos") + 1;
        st.putInt("selos", n);
        Run run = RUNS.computeIfAbsent(player.getUUID(), k -> new Run("selos", site.id(), pos, now + 1200));
        run.add(shadow(level, spotNear(level, pos, 2, 5), player, "sombra"));
        if (n < 5) {
            title(player, "selo_n", null, 0xE0B070);
            player.connection.send(new ClientboundSetTitleTextPacket(msg("selo_n", n).withStyle(s -> s.withColor(0xE0B070).withBold(true))));
            player.connection.send(new ClientboundSetSubtitleTextPacket(msg("selo_tempo", (st.getLong("selo_ate") - now) / 20)));
            level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1.5f, 0.5f);
            return;
        }
        // the fifth: the barrier falls and the boulder breaks
        st.putBoolean("aberta", true);
        st.putLong("aberta_dia", day(level));
        st.putInt("selos", 0);
        st.putLong("selo_ate", 0);
        setAll(level, site.mark("boulder_seal"), Blocks.AIR.defaultBlockState(), false);
        for (BlockPos p : site.mark("boulder")) {
            level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            if (level.random.nextInt(3) == 0) {
                level.sendParticles(ParticleTypes.EXPLOSION, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 1, 0, 0, 0, 0);
            }
        }
        BlockPos boulder = site.first("boulder");
        if (boulder != null) {
            level.playSound(null, boulder, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 2f, 0.7f);
        }
        BlockPos hall = site.first("hall");
        if (hall != null) {
            run.add(spawn(level, hall.above(), player, "marionetista", "ninja_renegado"));
            for (int i = 0; i < 2; i++) {
                Entity puppet = spawn(level, spotNear(level, hall, 2, 6), player, "marionete");
                run.add(puppet);
            }
        }
        List<ItemStack> loot = new ArrayList<>();
        loot.add(new ItemStack(ModItems.PUPPET_SCROLL.get()));
        loot.add(new ItemStack(ModItems.POISON_POUCH.get(), 2));
        fill(level, site.first("chest"), loot.toArray(ItemStack[]::new));
        title(player, "selo_quebrou", "selo_quebrou_sub", 0xE0B070);
    }

    // ================================================================ every tick: challenges, the wild things, gliding

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        CompoundTag pd = player.getPersistentData();
        // Paper wings: slow fall and a push along the look while in the air
        if (pd.getLong("souls_asas_ate") > now && !player.onGround() && !player.isInWater() && !player.isFallFlying()) {
            Vec3 v = player.getDeltaMovement();
            if (v.y < -0.05 && now % 3 == 0) {
                Vec3 look = player.getLookAngle();
                Vec3 h = new Vec3(look.x, 0, look.z).normalize().scale(0.09);
                Vec3 nv = new Vec3(v.x + h.x, Math.max(v.y, -0.12), v.z + h.z);
                double sp = Math.sqrt(nv.x * nv.x + nv.z * nv.z);
                if (sp > 0.8) {
                    nv = new Vec3(nv.x * 0.8 / sp, nv.y, nv.z * 0.8 / sp);
                }
                player.setDeltaMovement(nv);
                player.hurtMarked = true;
                level.sendParticles(ParticleTypes.WHITE_ASH, player.getX(), player.getY(0.6), player.getZ(), 6, 0.6, 0.2, 0.6, 0.01);
            }
            player.fallDistance = 0;
        }
        // Sharingan: the enemies around glow
        if (pd.getLong("souls_sharingan_ate") > now && now % 20 == 0) {
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(24),
                    e -> e != player && e.isAlive() && (e instanceof Enemy || e instanceof Mob m && m.getTarget() == player))) {
                e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 30, 0, false, false));
            }
        }
        if (level.dimension() != Level.OVERWORLD) {
            return;
        }
        // the sage training runs every tick (the bar needs it); the rest every half second
        NarutoSites.Site spring = now % 2 == 0 ? NarutoSites.near(level, NarutoStructGen.MYOBOKU, player.blockPosition(), 40) : null;
        if (spring != null || pd.getInt("souls_fonte_barra") > 0) {
            sage(player, level, spring);
        }
        if (now % 10 != 0) {
            return;
        }
        Run run = RUNS.get(player.getUUID());
        if (run != null) {
            NarutoSites.Site site = null;
            for (NarutoSites.Site s : NarutoSites.known(level)) {
                if (s.id().equals(run.site)) {
                    site = s;
                }
            }
            if (site == null) {
                RUNS.remove(player.getUUID());
            } else if (now % 20 == 0 || "guizo".equals(run.type)) {
                switch (run.type) {
                    case "guizo" -> {
                        if (now % 20 == 0) {
                            bellTick(player, level, run, site);
                        }
                    }
                    case "espada" -> swordTick(player, level, run, site);
                    case "flor" -> flowerTick(player, level, run, site);
                    case "vale" -> valeTick(player, level, run);
                    default -> {
                        // the altar's and the seals' waves: they leave on their own when done or far
                        if (!player.isAlive() || now > run.end || run.cleared(level) || !nearFlat(player, run.center, 96)) {
                            if (!run.cleared(level) && (now > run.end || !nearFlat(player, run.center, 96))) {
                                run.despawn(level);
                            }
                            RUNS.remove(player.getUUID());
                        }
                    }
                }
            }
        }
        if (now % 40 != 0) {
            return;
        }
        NarutoSites.Site site = NarutoSites.near(level, null, player.blockPosition(), 100);
        if (site == null) {
            return;
        }
        switch (site.kind()) {
            case NarutoStructGen.COVIL -> snake(player, level, site);
            case NarutoStructGen.CHUVA -> pit(player, level, site);
            case NarutoStructGen.PICO -> peak(player, level, site);
            case NarutoSites.VALE -> zetsu(player, level, site);
            default -> {
            }
        }
    }

    // ---------------------------------------------------------------- the Snake's Den

    private static void snake(ServerPlayer player, ServerLevel level, NarutoSites.Site site) {
        BlockPos nest = site.first("nest");
        if (nest == null || !near(player, nest, 12) || player.isCreative()) {
            return;
        }
        CompoundTag st = state(level, site);
        if (st.getLong("cobra_dia") != 0 && day(level) < st.getLong("cobra_dia") + 3) {
            return;
        }
        // crouched and slow it keeps sleeping
        if (player.isShiftKeyDown() && !player.isSprinting()) {
            if (level.random.nextInt(4) == 0) {
                say(player, "cobra_dorme");
            }
            return;
        }
        EntityType<?> t = type("cobra_gigante");
        if (t == null) {
            return;
        }
        if (!level.getEntities(t, new AABB(nest).inflate(40), Entity::isAlive).isEmpty()) {
            return;
        }
        Entity snake = t.spawn(level, nest.above(), MobSpawnType.STRUCTURE);
        if (snake instanceof Mob mob) {
            mob.setTarget(player);
            mob.setPersistenceRequired();
        }
        st.putLong("cobra_dia", day(level));
        title(player, "cobra_acordou", null, 0x9AA45A);
        level.playSound(null, nest, SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.5f, 0.6f);
    }

    // ---------------------------------------------------------------- the sewer pit of the Rain tower

    private static void pit(ServerPlayer player, ServerLevel level, NarutoSites.Site site) {
        BlockPos pit = site.first("pit");
        if (pit == null || !nearFlat(player, pit, 11) || player.getY() > pit.getY() + 6) {
            return;
        }
        // the gas at the bottom
        if (player.getY() < pit.getY() + 2) {
            player.addEffect(new MobEffectInstance(MobEffects.POISON, 70, 0));
            level.sendParticles(ParticleTypes.SNEEZE, player.getX(), player.getY() + 0.6, player.getZ(), 10, 1.2, 0.4, 1.2, 0.01);
        }
        if (player.isCreative()) {
            return;
        }
        CompoundTag st = state(level, site);
        if (st.getLong("sal_dia") != 0 && day(level) < st.getLong("sal_dia") + 3) {
            return;
        }
        EntityType<?> t = type("salamandra_venenosa");
        if (t == null || !level.getEntities(t, new AABB(pit).inflate(30), Entity::isAlive).isEmpty()) {
            return;
        }
        Entity sal = t.spawn(level, pit, MobSpawnType.STRUCTURE);
        if (sal instanceof Mob mob) {
            mob.setTarget(player);
            mob.setPersistenceRequired();
        }
        st.putLong("sal_dia", day(level));
        title(player, "salamandra", null, 0x8FE04A);
    }

    // ---------------------------------------------------------------- the Sage's Peak

    private static void peak(ServerPlayer player, ServerLevel level, NarutoSites.Site site) {
        BlockPos top = site.first("top");
        if (top == null || !nearFlat(player, top, 9) || player.getY() < top.getY() - 1.5) {
            return;
        }
        CompoundTag pd = player.getPersistentData();
        if (pd.getBoolean("souls_pergaminho_sapo")) {
            return;
        }
        EntityType<?> t = type("sapo_gigante");
        if (t == null) {
            // until the toad exists, reaching the top is the test
            pd.putBoolean("souls_pergaminho_sapo", true);
            give(player, new ItemStack(ModItems.TOAD_SCROLL.get()));
            title(player, "sapo_reconhece", "sapo_reconhece_sub", 0xE6A14A);
            return;
        }
        if (!level.getEntities(t, new AABB(top).inflate(30), e -> e.isAlive() && e.getTags().contains("souls_naruto_pico")).isEmpty()) {
            return;
        }
        Entity toad = t.spawn(level, top, MobSpawnType.STRUCTURE);
        if (toad instanceof Mob mob) {
            mob.addTag("souls_naruto_pico");
            mob.setTarget(player);
            mob.setPersistenceRequired();
        }
        title(player, "sapo_desafio", null, 0xE6A14A);
    }

    // ---------------------------------------------------------------- the white Zetsu at night

    private static void zetsu(ServerPlayer player, ServerLevel level, NarutoSites.Site site) {
        long t = level.getDayTime() % 24000L;
        if (t < 13000 || t > 23000 || level.random.nextFloat() > 0.25f) {
            return;
        }
        EntityType<?> type = type("zetsu_branco");
        if (type == null) {
            return;
        }
        for (BlockPos root : site.mark("roots")) {
            if (nearFlat(player, root, 40) && level.getEntities(type, new AABB(root).inflate(48), Entity::isAlive).size() < 3) {
                Entity z = type.spawn(level, root, MobSpawnType.STRUCTURE);
                if (z != null) {
                    level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, z.getX(), z.getY(), z.getZ(), 30, 0.5, 0.2, 0.5, 0.01);
                }
                return;
            }
        }
    }

    // ---------------------------------------------------------------- the Sages' Spring: the natural energy bar

    private static final int BAR = 220;

    private static void sage(ServerPlayer player, ServerLevel level, NarutoSites.Site spring) {
        CompoundTag pd = player.getPersistentData();
        int bar = pd.getInt("souls_fonte_barra");
        boolean onStone = false;
        if (spring != null) {
            for (BlockPos m : spring.mark("stone")) {
                if (Math.abs(player.getX() - (m.getX() + 0.5)) < 1.9 && Math.abs(player.getZ() - (m.getZ() + 0.5)) < 1.9
                        && Math.abs(player.getY() - (m.getY() - 0.5)) < 1.0) {
                    onStone = true;
                }
            }
        }
        boolean still = player.isShiftKeyDown() && player.onGround() && player.getDeltaMovement().horizontalDistanceSqr() < 0.0004;
        boolean oil = false;
        Item oleo = item("oleo_sapo");
        if (oleo != null) {
            oil = player.getInventory().contains(new ItemStack(oleo));
        }
        int lo = oil ? 130 : 150, hi = oil ? 196 : 184;
        if (onStone && still) {
            if (bar == 0 && !dailyFree(player, "souls_fonte_dia")) {
                if (level.getGameTime() % 40 == 0) {
                    say(player, "fonte_amanha");
                }
                return;
            }
            bar += 2;
            pd.putInt("souls_fonte_barra", bar);
            if (bar % 6 == 0) {
                Vec3 at = player.position().add(0, 1, 0);
                double a = bar * 0.37;
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, at.x + Math.cos(a) * 1.6, at.y - 0.4 + (bar % 40) / 40.0, at.z + Math.sin(a) * 1.6, 1, 0, 0, 0, 0);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < 22; i++) {
                    int v = i * 10;
                    String color = v < bar ? (v >= lo && v < hi ? "§a" : v >= hi ? "§c" : "§e") : (v >= lo && v < hi ? "§2" : "§8");
                    sb.append(color).append("▮");
                }
                player.displayClientMessage(Component.literal(sb.toString()), true);
            }
            if (bar >= BAR) {
                // past the point: a stone toad for 10 s, like the pupils around
                pd.putInt("souls_fonte_barra", 0);
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 9, false, false));
                player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 200, 4, false, false));
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 2, false, false));
                level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK,
                        ModBlocks.STATUE_STONE.get().defaultBlockState()), player.getX(), player.getY(0.6), player.getZ(), 60, 0.4, 0.6, 0.4, 0.1);
                level.playSound(null, player.blockPosition(), SoundEvents.STONE_PLACE, SoundSource.PLAYERS, 1.5f, 0.5f);
                title(player, "virou_pedra", "virou_pedra_sub", 0xA3A097);
            }
            return;
        }
        if (bar <= 0) {
            return;
        }
        pd.putInt("souls_fonte_barra", 0);
        if (player.isShiftKeyDown() || bar < lo || !onStone) {
            say(player, "fonte_perdeu");
            return;
        }
        if (bar < hi) {
            dailyUse(player, "souls_fonte_dia");
            int d = 3600;
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, d, 1));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, d, 1));
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, d, 0));
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, d, 0));
            player.addEffect(new MobEffectInstance(MobEffects.JUMP, d, 1));
            player.connection.send(new ClientboundSetTitlesAnimationPacket(4, 50, 14));
            player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("technique.battlegroundsmod.modo_sabio.title")
                    .withStyle(s -> s.withColor(0xFF9A2E).withBold(true))));
            player.connection.send(new ClientboundSetSubtitleTextPacket(msg("sabio_3min")));
            level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY(1), player.getZ(), 60, 0.6, 0.8, 0.6, 0.04);
            level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1f, 1.3f);
        }
    }

    // ================================================================ the places that close again

    private static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 100 != 61) {
            return;
        }
        ServerLevel level = event.getServer().overworld();
        long today = day(level), now = level.getGameTime();
        Data data = data(level);
        for (NarutoSites.Site site : NarutoSites.known(level)) {
            CompoundTag st = data.sites.get(site.id());
            if (st == null) {
                continue;
            }
            switch (site.kind()) {
                case NarutoStructGen.MORTE -> {
                    if (st.getBoolean("aberta") && today > st.getLong("aberta_dia") && !anyPlayerNear(level, site.pos(), 56)) {
                        setAll(level, site.mark("wall"), Blocks.RED_CONCRETE.defaultBlockState(), false);
                        setAll(level, site.mark("glyph"), Blocks.GOLD_BLOCK.defaultBlockState(), false);
                        st.putBoolean("aberta", false);
                        data.setDirty();
                    }
                }
                case NarutoStructGen.DESERTO -> {
                    if (!st.getBoolean("aberta") && st.getInt("selos") > 0 && now > st.getLong("selo_ate")) {
                        // too slow: the seals come back
                        for (BlockPos p : site.mark("seal")) {
                            if (level.getBlockState(p).isAir()) {
                                putSeal(level, p);
                            }
                        }
                        st.putInt("selos", 0);
                        data.setDirty();
                        for (ServerPlayer p : level.players()) {
                            if (nearFlat(p, site.pos(), 80)) {
                                say(p, "selos_voltaram");
                            }
                        }
                    }
                    if (st.getBoolean("aberta") && today >= st.getLong("aberta_dia") + 3 && !anyPlayerNear(level, site.pos(), 72)) {
                        setAll(level, site.mark("boulder"), Blocks.TERRACOTTA.defaultBlockState(), false);
                        for (BlockPos p : site.mark("boulder_seal")) {
                            putSeal(level, p);
                        }
                        for (BlockPos p : site.mark("seal")) {
                            putSeal(level, p);
                        }
                        st.putBoolean("aberta", false);
                        data.setDirty();
                    }
                }
                default -> {
                }
            }
        }
        // the allies go home when their time is over
        for (Iterator<Map.Entry<UUID, long[]>> it = ALLIES.entrySet().iterator(); it.hasNext();) {
            Map.Entry<UUID, long[]> e = it.next();
            Entity ally = level.getEntity(e.getKey());
            if (ally == null || !ally.isAlive() || now > e.getValue()[0]) {
                if (ally != null && ally.isAlive()) {
                    level.sendParticles(ParticleTypes.CLOUD, ally.getX(), ally.getY(0.5), ally.getZ(), 30, 0.6, 0.8, 0.6, 0.03);
                    ally.discard();
                }
                it.remove();
            }
        }
    }

    // ================================================================ deaths and blows

    private static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level) || !(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        String id = typeId(victim);
        switch (id) {
            case "ninja_som" -> {
                // the Sound Ninjas of the forest carry one of the exam's scrolls
                if (level.dimension() == Level.OVERWORLD && level.random.nextFloat() < 0.3f) {
                    drop(level, victim, new ItemStack(level.random.nextBoolean() ? ModItems.SCROLL_HEAVEN.get() : ModItems.SCROLL_EARTH.get()));
                }
            }
            case "cobra_gigante" -> {
                drop(level, victim, new ItemStack(ModItems.SNAKE_SKIN.get(), 1 + level.random.nextInt(2)));
                if (level.random.nextFloat() < 0.25f) {
                    drop(level, victim, new ItemStack(ModItems.KUSANAGI.get()));
                }
            }
            case "salamandra_venenosa" -> drop(level, victim, new ItemStack(ModItems.POISON_POUCH.get(), 1 + level.random.nextInt(2)));
            default -> {
            }
        }
    }

    private static void drop(ServerLevel level, LivingEntity at, ItemStack stack) {
        ItemEntity e = new ItemEntity(level, at.getX(), at.getY() + 0.5, at.getZ(), stack);
        e.setDefaultPickUpDelay();
        e.setGlowingTag(true);
        level.addFreshEntity(e);
    }

    private static void onIncoming(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level)) {
            return;
        }
        Entity attacker = event.getSource().getEntity();
        // an ally never hurts its caller (nor the caller it)
        if (attacker != null && (isAllyOf(attacker, victim) || isAllyOf(victim, attacker))) {
            event.setCanceled(true);
            return;
        }
        // the Sharingan sees the blow coming
        if (victim instanceof ServerPlayer player && attacker != null && attacker != player
                && player.getPersistentData().getLong("souls_sharingan_ate") > level.getGameTime() && level.random.nextFloat() < 0.33f) {
            event.setCanceled(true);
            Vec3 away = player.position().subtract(attacker.position()).multiply(1, 0, 1).normalize();
            Vec3 side = new Vec3(-away.z, 0, away.x).scale(level.random.nextBoolean() ? 0.6 : -0.6);
            player.setDeltaMovement(side.add(away.scale(0.2)).add(0, 0.15, 0));
            player.hurtMarked = true;
            level.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY(0.6), player.getZ(), 10, 0.3, 0.4, 0.3, 0.1);
            level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS, 1f, 1.6f);
            return;
        }
        // the bell test: a hit can knock the bell out of your hands
        if (victim instanceof ServerPlayer player && attacker != null && attacker.getTags().contains(RUN_TAG)) {
            Run run = RUNS.get(player.getUUID());
            if (run != null && "guizo".equals(run.type) && level.random.nextFloat() < 0.15f) {
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    ItemStack s = player.getInventory().getItem(i);
                    if (testBell(s)) {
                        ItemStack one = s.split(1);
                        ItemEntity e = new ItemEntity(level, player.getX(), player.getY() + 1, player.getZ(), one);
                        e.setDeltaMovement((level.random.nextDouble() - 0.5) * 0.6, 0.35, (level.random.nextDouble() - 0.5) * 0.6);
                        e.setPickUpDelay(30);
                        e.setGlowingTag(true);
                        level.addFreshEntity(e);
                        say(player, "guizo_caiu");
                        break;
                    }
                }
            }
        }
        // the toad of the peak is beaten, not killed: it knows you now
        if (victim.getTags().contains("souls_naruto_pico") && attacker instanceof ServerPlayer player
                && victim.getHealth() - event.getAmount() < victim.getMaxHealth() * 0.35f) {
            event.setCanceled(true);
            victim.setHealth(victim.getMaxHealth());
            if (victim instanceof Mob mob) {
                mob.setTarget(null);
            }
            victim.removeTag("souls_naruto_pico");
            ALLIES.put(victim.getUUID(), new long[] {level.getGameTime() + 100});
            if (!player.getPersistentData().getBoolean("souls_pergaminho_sapo")) {
                player.getPersistentData().putBoolean("souls_pergaminho_sapo", true);
                give(player, new ItemStack(ModItems.TOAD_SCROLL.get()));
            }
            title(player, "sapo_reconhece", "sapo_reconhece_sub", 0xE6A14A);
        }
    }

    private static void onDamage(LivingDamageEvent.Post event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player) || event.getSource().getDirectEntity() != player) {
            return;
        }
        ItemStack weapon = player.getMainHandItem();
        CustomData data = weapon.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return;
        }
        int left = data.copyTag().getInt(NarutoLootItems.PoisonPouch.TAG);
        if (left <= 0) {
            return;
        }
        event.getEntity().addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1), player);
        CustomData.update(DataComponents.CUSTOM_DATA, weapon, tag -> {
            if (left <= 1) {
                tag.remove(NarutoLootItems.PoisonPouch.TAG);
            } else {
                tag.putInt(NarutoLootItems.PoisonPouch.TAG, left - 1);
            }
        });
    }

    // ================================================================ the items' powers

    public static void wings(ServerPlayer player, int ticks) {
        player.getPersistentData().putLong("souls_asas_ate", player.level().getGameTime() + ticks);
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, ticks, 0, false, false));
        player.serverLevel().sendParticles(ParticleTypes.WHITE_ASH, player.getX(), player.getY(1), player.getZ(), 80, 0.8, 0.6, 0.8, 0.05);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.5f, 1.2f);
    }

    public static void sharingan(ServerPlayer player, int ticks) {
        player.getPersistentData().putLong("souls_sharingan_ate", player.level().getGameTime() + ticks);
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, ticks, 0, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, 0, false, false));
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.ENDER_EYE_DEATH, SoundSource.PLAYERS, 1f, 0.6f);
        player.displayClientMessage(msg("sharingan_aberto"), true);
    }

    // allies: entity -> { end tick }; the owner in the entity's persistent data
    private static final Map<UUID, long[]> ALLIES = new HashMap<>();

    private static boolean isAllyOf(Entity ally, Entity owner) {
        return ally.getTags().contains(ALLY_TAG) && owner.getUUID().toString().equals(ally.getPersistentData().getString("souls_dono"));
    }

    // A summoned ally next to the player for some ticks; false when nothing could come.
    public static boolean summonAlly(ServerPlayer player, String id, int ticks) {
        ServerLevel level = player.serverLevel();
        Vec3 front = player.position().add(player.getLookAngle().multiply(1, 0, 1).normalize().scale(2.5));
        BlockPos at = BlockPos.containing(front);
        EntityType<?> t = type(id);
        if (t == null && "marionete".equals(id)) {
            t = EntityType.IRON_GOLEM;
        }
        if (t == null) {
            // the toad isn't here yet: it lands for a moment and crushes what is around
            level.sendParticles(ParticleTypes.CLOUD, front.x, front.y + 1, front.z, 120, 2.5, 1.5, 2.5, 0.08);
            level.sendParticles(ParticleTypes.EXPLOSION, front.x, front.y + 0.5, front.z, 4, 2, 0.3, 2, 0);
            level.playSound(null, at, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.5f, 0.5f);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at).inflate(6), e -> e != player && e.isAlive())) {
                e.hurt(player.damageSources().playerAttack(player), 14f);
                Vec3 push = e.position().subtract(front).multiply(1, 0, 1).normalize().scale(1.2);
                e.push(push.x, 0.6, push.z);
            }
            return true;
        }
        Entity e = t.spawn(level, at, MobSpawnType.MOB_SUMMONED);
        if (e == null) {
            SubCommon.warn(player, "naruto.invocacao_falhou");
            return false;
        }
        e.addTag(ALLY_TAG);
        e.getPersistentData().putString("souls_dono", player.getUUID().toString());
        if (e instanceof IronGolem golem) {
            golem.setPlayerCreated(true);
        }
        if (e instanceof Mob mob) {
            LivingEntity foe = player.getLastHurtByMob() != null ? player.getLastHurtByMob() : player.getLastHurtMob();
            mob.setTarget(foe != null && foe.isAlive() ? foe : null);
        }
        ALLIES.put(e.getUUID(), new long[] {level.getGameTime() + ticks});
        level.sendParticles(ParticleTypes.CLOUD, e.getX(), e.getY(0.5), e.getZ(), 60, 1, 1, 1, 0.05);
        level.playSound(null, at, SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1f, 0.8f);
        return true;
    }
}
