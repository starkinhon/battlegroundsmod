package com.starkinhon.battlegroundsmod.power.powers.freezaboss;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.starkinhon.battlegroundsmod.ModEntities;
import com.starkinhon.battlegroundsmod.boss.EndBossLoot;
import com.starkinhon.battlegroundsmod.combat.BlockRules;
import com.starkinhon.battlegroundsmod.combat.Ruin;
import com.starkinhon.battlegroundsmod.combat.RuinNet;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.command.SoulCommand;
import com.starkinhon.battlegroundsmod.power.Progression;
import com.starkinhon.battlegroundsmod.power.Tiers;
import com.starkinhon.battlegroundsmod.universe.DragonBallWorld;
import com.starkinhon.battlegroundsmod.universe.UniverseBiomes;

import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// Freeza's ship landing on the Dragon Ball land (Stark 2026-11-19): the end-game fight comes to a player of tier Universal
// or above who walks there, now and then, at most once a day for him. The sky warns first (the title, the hum), the ship
// comes down slowly from 150 blocks (drawn by the client from FzShipShape, EpicFx SHIP), lands on a flat spot 45-70 blocks
// away with a burst of dust, becomes real blocks, and Freeza in Full Power steps out of the door. When he dies or is gone
// the ship takes off again and every block it set goes with it. One ship at a time in the world.
public final class FzShip {
    private static final int TIER = 2;
    private static final int CHECK = 1200, CHANCE = 6;
    private static final int DESCENT = 220, STAY_EMPTY = 20 * 60 * 3;
    private static final double FROM = 150;
    private static final String DAY_TAG = "souls_nave_freeza_dia";

    private static Ship current;

    private static final class Ship {
        ServerLevel level;
        BlockPos at;
        double dx, dz;
        final List<BlockPos> placed = new ArrayList<>();
        final List<BlockState> states = new ArrayList<>();
        UUID freeza;
        boolean leaving;
    }

    private FzShip() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(FzShip::onPlayerTick);
        // /soul navefreeza: the ship now, over the player (testing; ops only)
        SoulCommand.addSubcommand(() -> Commands.literal("navefreeza").requires(s -> s.hasPermission(2)).executes(c -> {
            ServerPlayer player = c.getSource().getPlayerOrException();
            if (current != null) {
                c.getSource().sendFailure(Component.translatable("message.battlegroundsmod.nave_freeza.ja"));
                return 0;
            }
            return arrive(player) ? 1 : 0;
        }));
    }

    public static void resetStatics() {
        current = null;
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % CHECK != CHECK / 2 || current != null
                || !Progression.on(player) || player.isSpectator()
                || Tiers.of(player) > TIER) {
            return;
        }
        if (!DragonBallWorld.UNIVERSE.equals(UniverseBiomes.universeAt(player.level(), player.blockPosition()))
                || player.getPersistentData().getLong(DAY_TAG) == day(player.level()) + 1
                || player.getRandom().nextInt(CHANCE) != 0) {
            return;
        }
        if (arrive(player)) {
            player.getPersistentData().putLong(DAY_TAG, day(player.level()) + 1);
        }
    }

    private static long day(Level level) {
        return level.getDayTime() / 24000L;
    }

    // A flat dry spot 45-70 blocks from the player, on the Dragon Ball land, the ground loaded all around.
    private static BlockPos spot(ServerLevel level, ServerPlayer player) {
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (int i = 0; i < 24; i++) {
            double a = level.random.nextDouble() * Mth.TWO_PI, d = 45 + level.random.nextDouble() * 25;
            int x = Mth.floor(player.getX() + Math.cos(a) * d), z = Mth.floor(player.getZ() + Math.sin(a) * d);
            if (!level.hasChunk(x >> 4, z >> 4) || !level.hasChunk((x + 18) >> 4, (z + 18) >> 4) || !level.hasChunk((x - 18) >> 4, (z - 18) >> 4)
                    || !level.hasChunk((x + 18) >> 4, (z - 18) >> 4) || !level.hasChunk((x - 18) >> 4, (z + 18) >> 4)) {
                continue;
            }
            int g = Ruin.ground(level, x, z);
            BlockPos top = new BlockPos(x, g + 1, z);
            if (!level.getFluidState(top).isEmpty() || !level.getFluidState(top.below()).isEmpty()
                    || !DragonBallWorld.UNIVERSE.equals(UniverseBiomes.universeAt(level, top))) {
                continue;
            }
            double var = 0;
            boolean wet = false;
            for (int k = 0; k < 12; k++) {
                double b = k * Mth.TWO_PI / 12;
                for (double r : new double[] {7, 14}) {
                    int sx = Mth.floor(x + Math.cos(b) * r), sz = Mth.floor(z + Math.sin(b) * r);
                    int sg = Ruin.ground(level, sx, sz);
                    var += (sg - g) * (sg - g);
                    wet |= !level.getFluidState(new BlockPos(sx, sg + 1, sz)).isEmpty();
                }
            }
            if (!wet && var < bestScore) {
                bestScore = var;
                best = top;
            }
        }
        // too steep everywhere (a mountain range): no ship this time
        return bestScore <= 24 * 30 ? best : null;
    }

    private static boolean arrive(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos at = spot(level, player);
        if (at == null) {
            return false;
        }
        Ship ship = new Ship();
        ship.level = level;
        ship.at = at;
        double tx = player.getX() - (at.getX() + 0.5), tz = player.getZ() - (at.getZ() + 0.5), l = Math.max(1e-3, Math.sqrt(tx * tx + tz * tz));
        ship.dx = tx / l;
        ship.dz = tz / l;
        current = ship;
        Vec3 foot = new Vec3(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        RuinNet.send(level, RuinNet.SHIP, foot, new Vec3(ship.dx, FROM, ship.dz), DESCENT, 1, 0);
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(foot) < 260 * 260) {
                p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
                p.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("message.battlegroundsmod.nave_freeza.titulo")
                        .withStyle(s -> s.withColor(FreezaTotal.COLOR))));
                p.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("message.battlegroundsmod.nave_freeza.sub")));
                p.playNotifySound(SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 1f, 0.5f);
                p.playNotifySound(SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE, 0.6f, 0.4f);
            }
        }
        Tasks.addSystem(age -> tick(ship, age));
        return true;
    }

    private static boolean tick(Ship ship, int age) {
        ServerLevel level = ship.level;
        if (current != ship) {
            return true;
        }
        Vec3 foot = new Vec3(ship.at.getX() + 0.5, ship.at.getY(), ship.at.getZ() + 0.5);
        if (age < DESCENT) {
            if (age % 40 == 0) {
                level.playSound(null, ship.at, SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE, 6f, 0.5f);
            }
            if (age == DESCENT - 40) {
                // the engines blowing the ground: dust running out from under it
                RuinNet.ring(level, foot, 34, 5, false, 60, 0xB9A486);
                RuinNet.dust(level, foot, 18, 7, 90, 30, 0xC8B694);
            }
            return false;
        }
        if (age == DESCENT) {
            land(ship, foot);
            return false;
        }
        if (age == DESCENT + 30) {
            step(ship);
            return false;
        }
        if (age < DESCENT + 30 || age % 20 != 0) {
            return false;
        }
        Entity freeza = ship.freeza == null ? null : level.getEntity(ship.freeza);
        boolean gone = !(freeza instanceof LivingEntity f) || !f.isAlive();
        boolean empty = level.getNearestPlayer(foot.x, foot.y, foot.z, 160, false) == null;
        if (gone || (empty && age > DESCENT + STAY_EMPTY)) {
            if (!gone) {
                freeza.discard();
            }
            leave(ship, foot);
            return true;
        }
        return false;
    }

    private static void land(Ship ship, Vec3 foot) {
        ServerLevel level = ship.level;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        boolean breaks = BlockRules.canBreak(level);
        for (FzShipShape.Piece piece : FzShipShape.build(ship.dx, ship.dz)) {
            p.set(ship.at.getX() + piece.x(), ship.at.getY() + piece.y(), ship.at.getZ() + piece.z());
            if (!level.isLoaded(p)) {
                continue;
            }
            BlockState s = level.getBlockState(p);
            boolean free = s.isAir() || s.canBeReplaced() || (breaks && !BlockRules.keeps(s) && s.getDestroySpeed(level, p) >= 0);
            if (!free) {
                continue;
            }
            level.setBlock(p, piece.state(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            ship.placed.add(p.immutable());
            ship.states.add(piece.state());
        }
        level.playSound(null, ship.at, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 5f, 0.5f);
        level.playSound(null, ship.at, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 4f, 0.4f);
        RuinNet.quake(level, foot, 7, false, 0xFFFFFF);
        RuinNet.tremor(level, foot, 1.2f, 22, 40);
        RuinNet.ring(level, foot, 40, 6, false, 70, 0xB9A486);
        RuinNet.dust(level, foot, 24, 9, 110, 18, 0xC8B694);
    }

    // Freeza in Full Power at the doorway, facing the way out.
    private static void step(Ship ship) {
        ServerLevel level = ship.level;
        double r = FzShipShape.R_DOOR;
        BlockPos at = BlockPos.containing(ship.at.getX() + 0.5 + ship.dx * r, ship.at.getY() + FzShipShape.DOOR_Y, ship.at.getZ() + 0.5 + ship.dz * r);
        Entity e = ModEntities.FREEZA_TOTAL.get().spawn(level, at, MobSpawnType.EVENT);
        if (e == null) {
            leave(ship, new Vec3(ship.at.getX() + 0.5, ship.at.getY(), ship.at.getZ() + 0.5));
            return;
        }
        e.setYRot((float) (Mth.atan2(ship.dz, ship.dx) * Mth.RAD_TO_DEG) - 90);
        e.getPersistentData().putBoolean(EndBossLoot.WORLD_TAG, true);
        if (e instanceof Mob mob) {
            mob.setPersistenceRequired();
            ServerPlayer near = (ServerPlayer) level.getNearestPlayer(e, 120);
            if (near != null && !near.isCreative() && !near.isSpectator()) {
                mob.setTarget(near);
            }
        }
        ship.freeza = e.getUUID();
        level.playSound(null, at, SoundEvents.PISTON_EXTEND, SoundSource.HOSTILE, 3f, 0.5f);
    }

    // The ship's blocks go (those still as it set them) and it climbs back into the sky.
    private static void leave(Ship ship, Vec3 foot) {
        ServerLevel level = ship.level;
        current = null;
        for (int i = ship.placed.size() - 1; i >= 0; i--) {
            BlockPos p = ship.placed.get(i);
            if (level.isLoaded(p) && level.getBlockState(p) == ship.states.get(i)) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
        RuinNet.send(level, RuinNet.SHIP, foot, new Vec3(ship.dx, FROM, ship.dz), 160, -1, 0);
        RuinNet.dust(level, foot, 20, 8, 80, 14, 0xC8B694);
        level.playSound(null, ship.at, SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 6f, 0.5f);
    }
}
